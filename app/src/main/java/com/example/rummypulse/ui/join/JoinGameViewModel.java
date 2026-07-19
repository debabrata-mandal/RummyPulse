package com.example.rummypulse.ui.join;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.rummypulse.data.AppUserManager;
import com.example.rummypulse.data.FirestoreCollections;
import com.example.rummypulse.data.GameAuth;
import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataWrapper;
import com.example.rummypulse.data.GameViewApproval;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.GameViewApprovalRepository;
import com.example.rummypulse.utils.PinUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoinGameViewModel extends AndroidViewModel {

    public enum ViewAccessBlockedReason {
        PENDING,
        REJECTED,
        ERROR
    }

    public interface ClaimCallback {
        void onSuccess(String pin, long pinGeneration);

        void onError(String message);
    }

    public interface TransferCallback {
        void onSuccess(String newPin, long newPinGeneration);

        void onError(String message);
    }

    private final FirebaseFirestore db;
    private final GameViewApprovalRepository viewApprovalRepository;
    private final GameRepository gameRepository;
    private final MutableLiveData<GameData> gameData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> editAccessGranted = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> gamePin = new MutableLiveData<>();
    /** From {@code games_v2.displayName}; empty means show game ID in UI. */
    private final MutableLiveData<String> gameDisplayName = new MutableLiveData<>();
    private final MutableLiveData<Boolean> editSessionStale = new MutableLiveData<>();
    private final MutableLiveData<GameAuth> gameAuth = new MutableLiveData<>();
    private final MutableLiveData<ViewAccessBlockedReason> viewAccessBlocked = new MutableLiveData<>();
    private final MutableLiveData<List<GameViewApproval>> pendingViewRequests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> pendingViewRequestsError = new MutableLiveData<>();

    private ListenerRegistration pendingViewRequestsListener;

    /** {@code pinGeneration} held when edit access was last claimed on this device. */
    private long activeEditGeneration;

    public JoinGameViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
        viewApprovalRepository = new GameViewApprovalRepository();
        gameRepository = GameRepository.getDashboardInstance();
    }

    public LiveData<GameData> getGameData() {
        return gameData;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public LiveData<Boolean> getEditAccessGranted() {
        return editAccessGranted;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getGamePin() {
        return gamePin;
    }

    public LiveData<String> getGameDisplayName() {
        return gameDisplayName;
    }

    public LiveData<Boolean> getEditSessionStale() {
        return editSessionStale;
    }

    public LiveData<GameAuth> getGameAuth() {
        return gameAuth;
    }

    public LiveData<ViewAccessBlockedReason> getViewAccessBlocked() {
        return viewAccessBlocked;
    }

    public LiveData<List<GameViewApproval>> getPendingViewRequests() {
        return pendingViewRequests;
    }

    public LiveData<String> getPendingViewRequestsError() {
        return pendingViewRequestsError;
    }

    public void clearViewAccessBlocked() {
        viewAccessBlocked.setValue(null);
    }

    public long getActiveEditGeneration() {
        return activeEditGeneration > 0 ? activeEditGeneration : 1L;
    }

    public void clearEditSessionStale() {
        editSessionStale.setValue(null);
    }

    public void revokeEditAccessLocally() {
        activeEditGeneration = 0;
        editAccessGranted.setValue(false);
    }

    public void joinGame(String gameId, boolean requestEditAccess) {
        joinGame(gameId, requestEditAccess, null, false);
    }

    public void joinGame(String gameId, boolean requestEditAccess, String enteredPin) {
        joinGame(gameId, requestEditAccess, enteredPin, false);
    }

    public void joinGame(String gameId, boolean requestEditAccess, String enteredPin, boolean skipViewGate) {
        if (TextUtils.isEmpty(gameId)) {
            errorMessage.setValue("Please enter a Game ID");
            return;
        }

        if (gameId.length() != 9) {
            errorMessage.setValue("Game ID must be 9 characters");
            return;
        }

        isLoading.setValue(true);
        gameDisplayName.setValue(null);
        viewAccessBlocked.setValue(null);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                db.collection(FirestoreCollections.GAMES).document(gameId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (!documentSnapshot.exists()) {
                                isLoading.setValue(false);
                                gameDisplayName.setValue(null);
                                gameAuth.setValue(null);
                                errorMessage.setValue("Game not found. Please check the Game ID.");
                                return;
                            }

                            GameAuth auth = documentSnapshot.toObject(GameAuth.class);
                            gameAuth.setValue(auth);
                            applyGameAuthMetadata(documentSnapshot);

                            proceedJoinAfterAuthLoaded(gameId, auth, requestEditAccess, enteredPin,
                                    skipViewGate);
                        })
                        .addOnFailureListener(e -> {
                            isLoading.setValue(false);
                            gameDisplayName.setValue(null);
                            gameAuth.setValue(null);
                            errorMessage.setValue("Failed to connect to server. Please try again.");
                        }), 500);
    }

    private void proceedJoinAfterAuthLoaded(String gameId,
                                            GameAuth auth,
                                            boolean requestEditAccess,
                                            String enteredPin,
                                            boolean skipViewGate) {
        if (skipViewGate || GameViewApprovalRepository.canBypassViewGate(auth)) {
            continueJoinAfterViewAccess(gameId, requestEditAccess, enteredPin);
            return;
        }

        AppUserManager.getInstance().isCurrentUserAdmin(isAdmin -> {
            if (isAdmin) {
                continueJoinAfterViewAccess(gameId, requestEditAccess, enteredPin);
                return;
            }
            viewApprovalRepository.resolveViewAccess(gameId,
                    new GameViewApprovalRepository.ViewAccessCallback() {
                        @Override
                        public void onResult(GameViewApprovalRepository.ViewAccessOutcome outcome) {
                            if (outcome == GameViewApprovalRepository.ViewAccessOutcome.GRANTED) {
                                continueJoinAfterViewAccess(gameId, requestEditAccess, enteredPin);
                            } else if (outcome
                                    == GameViewApprovalRepository.ViewAccessOutcome.REJECTED) {
                                isLoading.setValue(false);
                                viewAccessBlocked.setValue(ViewAccessBlockedReason.REJECTED);
                            } else {
                                isLoading.setValue(false);
                                viewAccessBlocked.setValue(ViewAccessBlockedReason.PENDING);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            isLoading.setValue(false);
                            errorMessage.setValue(message);
                            viewAccessBlocked.setValue(ViewAccessBlockedReason.ERROR);
                        }
                    });
        });
    }

    private void continueJoinAfterViewAccess(String gameId,
                                             boolean requestEditAccess,
                                             String enteredPin) {
        if (requestEditAccess && enteredPin != null) {
            claimEditAccess(gameId, enteredPin, new ClaimCallback() {
                @Override
                public void onSuccess(String pin, long pinGeneration) {
                    fetchGameData(gameId);
                }

                @Override
                public void onError(String message) {
                    errorMessage.setValue(message);
                    editAccessGranted.setValue(false);
                    fetchGameData(gameId);
                }
            });
            return;
        }

        GameAuth auth = gameAuth.getValue();
        if (auth != null && tryRestoreActiveEditorSession(auth)) {
            fetchGameData(gameId);
            return;
        }

        fetchGameData(gameId);
    }

    /**
     * Restores edit mode when Firestore still lists this signed-in user as the active editor.
     * Covers app cache/data clears that wipe locally saved PINs while the server session remains.
     */
    private boolean tryRestoreActiveEditorSession(@NonNull GameAuth auth) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return false;
        }
        String activeEditor = auth.getActiveEditorUserId();
        if (activeEditor == null || !activeEditor.equals(user.getUid())) {
            return false;
        }

        activeEditGeneration = auth.getPinGenerationOrDefault();
        if (auth.getPin() != null) {
            gamePin.setValue(auth.getPin());
        }
        editAccessGranted.setValue(true);
        return true;
    }

    public void startPendingViewRequestsListener(String gameId) {
        stopPendingViewRequestsListener();
        if (TextUtils.isEmpty(gameId)) {
            return;
        }
        GameViewApprovalRepository.PendingRequestsCallback callback =
                new GameViewApprovalRepository.PendingRequestsCallback() {
                    @Override
                    public void onRequests(@NonNull List<GameViewApproval> requests) {
                        pendingViewRequestsError.postValue(null);
                        pendingViewRequests.postValue(requests);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        pendingViewRequestsError.postValue(message);
                    }
                };
        viewApprovalRepository.fetchPendingRequestsForGame(gameId, callback);
        pendingViewRequestsListener =
                viewApprovalRepository.listenPendingRequestsForGame(gameId, callback);
    }

    public void stopPendingViewRequestsListener() {
        if (pendingViewRequestsListener != null) {
            pendingViewRequestsListener.remove();
            pendingViewRequestsListener = null;
        }
        pendingViewRequests.setValue(new ArrayList<>());
        pendingViewRequestsError.setValue(null);
    }

    public void approveViewRequest(String gameId, String userId) {
        viewApprovalRepository.approveRequest(gameId, userId, new GameViewApprovalRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                successMessage.setValue("View access approved");
            }

            @Override
            public void onError(String message) {
                errorMessage.setValue(message);
            }
        });
    }

    public void rejectViewRequest(String gameId, String userId) {
        viewApprovalRepository.rejectRequest(gameId, userId, new GameViewApprovalRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                successMessage.setValue("View access rejected");
            }

            @Override
            public void onError(String message) {
                errorMessage.setValue(message);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopPendingViewRequestsListener();
    }

    private void applyGameAuthMetadata(DocumentSnapshot documentSnapshot) {
        try {
            GameAuth gameAuth = documentSnapshot.toObject(GameAuth.class);
            String display = "";
            if (gameAuth != null && gameAuth.getDisplayName() != null) {
                display = gameAuth.getDisplayName().trim();
            }
            gameDisplayName.setValue(display);
            if (gameAuth != null && gameAuth.getPin() != null) {
                gamePin.setValue(gameAuth.getPin());
            }
        } catch (Exception e) {
            System.out.println("Error extracting game auth metadata: " + e.getMessage());
        }
    }

    public void claimEditAccess(String gameId, String enteredPin, ClaimCallback callback) {
        if (TextUtils.isEmpty(gameId) || TextUtils.isEmpty(enteredPin)) {
            if (callback != null) {
                callback.onError("Please enter a valid PIN");
            }
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onError("You must be signed in to request edit access");
            }
            return;
        }

        DocumentReference gameRef = db.collection(FirestoreCollections.GAMES).document(gameId);
        String editorName = resolveEditorDisplayName(user);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(gameRef);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Game not found");
            }

            GameAuth auth = snapshot.toObject(GameAuth.class);
            if (auth == null || auth.getPin() == null) {
                throw new IllegalStateException("PIN not found for this game");
            }
            if (!auth.getPin().equals(enteredPin)) {
                throw new IllegalStateException("Incorrect PIN. Please try again.");
            }

            String activeEditor = auth.getActiveEditorUserId();
            String myUid = user.getUid();
            if (activeEditor != null && !activeEditor.equals(myUid)) {
                String editorLabel = auth.getActiveEditorName();
                if (!TextUtils.isEmpty(editorLabel)) {
                    throw new IllegalStateException(
                            "Someone else is editing (" + editorLabel + "). Ask them to transfer access.");
                }
                throw new IllegalStateException(
                        "Someone else is editing. Ask them to transfer access.");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("activeEditorUserId", myUid);
            updates.put("activeEditorName", editorName);
            transaction.update(gameRef, updates);

            return new ClaimResult(auth.getPin(), auth.getPinGenerationOrDefault());
        }).addOnSuccessListener(result -> {
            activeEditGeneration = result.pinGeneration;
            gamePin.setValue(result.pin);
            editAccessGranted.setValue(true);
            GameAuth currentAuth = gameAuth.getValue();
            if (currentAuth != null) {
                currentAuth.setActiveEditorUserId(user.getUid());
                currentAuth.setActiveEditorName(editorName);
                gameAuth.setValue(currentAuth);
            }
            if (callback != null) {
                callback.onSuccess(result.pin, result.pinGeneration);
            }
        }).addOnFailureListener(e -> {
            editAccessGranted.setValue(false);
            String message = e.getMessage();
            if (TextUtils.isEmpty(message)) {
                message = "Failed to claim edit access. Please try again.";
            }
            if (callback != null) {
                callback.onError(message);
            }
        });
    }

    public void transferEditAccess(String gameId, TransferCallback callback) {
        if (TextUtils.isEmpty(gameId)) {
            if (callback != null) {
                callback.onError("Invalid game ID");
            }
            return;
        }

        DocumentReference gameRef = db.collection(FirestoreCollections.GAMES).document(gameId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(gameRef);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Game not found");
            }

            GameAuth auth = snapshot.toObject(GameAuth.class);
            long currentGen = auth != null ? auth.getPinGenerationOrDefault() : 1L;
            long newGen = currentGen + 1;
            String newPin = PinUtils.generatePin();

            Map<String, Object> updates = new HashMap<>();
            updates.put("pin", newPin);
            updates.put("pinGeneration", newGen);
            updates.put("activeEditorUserId", com.google.firebase.firestore.FieldValue.delete());
            updates.put("activeEditorName", com.google.firebase.firestore.FieldValue.delete());
            transaction.update(gameRef, updates);

            return new TransferResult(newPin, newGen);
        }).addOnSuccessListener(result -> {
            gamePin.setValue(result.newPin);
            activeEditGeneration = 0;
            editAccessGranted.setValue(false);
            GameAuth currentAuth = gameAuth.getValue();
            if (currentAuth != null) {
                currentAuth.setActiveEditorUserId(null);
                currentAuth.setActiveEditorName(null);
                gameAuth.setValue(currentAuth);
            }
            if (callback != null) {
                callback.onSuccess(result.newPin, result.newPinGeneration);
            }
        }).addOnFailureListener(e -> {
            String message = e.getMessage();
            if (TextUtils.isEmpty(message)) {
                message = "Failed to transfer edit access. Please try again.";
            }
            if (callback != null) {
                callback.onError(message);
            }
        });
    }

    public void validateEditSessionOnReconnect(String gameId, long localPinGeneration) {
        validateEditSessionOnReconnect(gameId, localPinGeneration, null);
    }

    public void validateEditSessionOnReconnect(String gameId, long localPinGeneration, Runnable onStillValid) {
        if (TextUtils.isEmpty(gameId)) {
            return;
        }

        Boolean granted = editAccessGranted.getValue();
        if (granted == null || !granted) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String myUid = user != null ? user.getUid() : null;

        db.collection(FirestoreCollections.GAMES).document(gameId)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        return;
                    }
                    GameAuth auth = documentSnapshot.toObject(GameAuth.class);
                    if (auth == null) {
                        return;
                    }

                    long remoteGen = auth.getPinGenerationOrDefault();
                    String activeEditor = auth.getActiveEditorUserId();
                    boolean generationStale = localPinGeneration > 0 && localPinGeneration != remoteGen;
                    boolean editorMismatch = activeEditor != null
                            && myUid != null
                            && !activeEditor.equals(myUid);

                    if (generationStale || editorMismatch) {
                        activeEditGeneration = 0;
                        editAccessGranted.setValue(false);
                        editSessionStale.setValue(true);
                    } else if (onStillValid != null) {
                        onStillValid.run();
                    }
                });
    }

    private void fetchGameData(String gameId) {
        db.collection(FirestoreCollections.GAME_DATA).document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    isLoading.setValue(false);
                    if (documentSnapshot.exists()) {
                        try {
                            GameDataWrapper wrapper = documentSnapshot.toObject(GameDataWrapper.class);
                            if (wrapper != null && wrapper.getData() != null) {
                                gameData.setValue(wrapper.getData());
                            } else {
                                errorMessage.setValue("Game data is corrupted. Please try again.");
                            }
                        } catch (Exception e) {
                            errorMessage.setValue("Failed to parse game data. Please try again.");
                        }
                    } else {
                        errorMessage.setValue("Game data not found. Please check the Game ID.");
                    }
                })
                .addOnFailureListener(e -> {
                    isLoading.setValue(false);
                    errorMessage.setValue("Failed to load game data. Please try again.");
                });
    }

    public void saveGameData(String gameId, GameData updatedGameData) {
        if (TextUtils.isEmpty(gameId) || updatedGameData == null) {
            errorMessage.setValue("Invalid game data");
            return;
        }

        Map<String, Object> cleanGameData = new HashMap<>();
        cleanGameData.put("numPlayers", updatedGameData.getPlayers() != null
                ? updatedGameData.getPlayers().size()
                : updatedGameData.getNumPlayers());
        cleanGameData.put("pointValue", updatedGameData.getPointValue());
        cleanGameData.put("gstPercent", updatedGameData.getGstPercent());
        cleanGameData.put("players", updatedGameData.getPlayers());
        cleanGameData.put("version", updatedGameData.getVersion());

        Map<String, Object> gameDataDoc = new HashMap<>();
        gameDataDoc.put("data", cleanGameData);
        gameDataDoc.put("lastUpdated", com.google.firebase.firestore.FieldValue.serverTimestamp());
        gameDataDoc.put("version", "1.0");
        gameDataDoc.put("editGeneration", getActiveEditGeneration());

        gameRepository.updateLocalDashboardFromGameData(gameId, updatedGameData);
        gameRepository.syncDashboardSummaryForGame(gameId, updatedGameData);

        db.collection(FirestoreCollections.GAME_DATA).document(gameId)
                .set(gameDataDoc)
                .addOnSuccessListener(aVoid -> {
                    gameData.setValue(updatedGameData);
                    gameRepository.syncDashboardSummaryForGame(gameId, updatedGameData);
                })
                .addOnFailureListener(e ->
                        errorMessage.setValue("Failed to save game data: " + e.getMessage()));
    }

    public void clearMessages() {
        errorMessage.setValue(null);
        successMessage.setValue(null);
    }

    public void refreshGameData(String gameId) {
        if (TextUtils.isEmpty(gameId)) {
            errorMessage.setValue("Invalid game ID");
            return;
        }
        isLoading.setValue(true);
        fetchGameData(gameId);
    }

    public void updateGameData(GameData newGameData) {
        if (newGameData != null) {
            gameData.setValue(newGameData);
        }
    }

    private static String resolveEditorDisplayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim();
        }
        return "Editor";
    }

    private static final class ClaimResult {
        final String pin;
        final long pinGeneration;

        ClaimResult(String pin, long pinGeneration) {
            this.pin = pin;
            this.pinGeneration = pinGeneration;
        }
    }

    private static final class TransferResult {
        final String newPin;
        final long newPinGeneration;

        TransferResult(String newPin, long newPinGeneration) {
            this.newPin = newPin;
            this.newPinGeneration = newPinGeneration;
        }
    }
}
