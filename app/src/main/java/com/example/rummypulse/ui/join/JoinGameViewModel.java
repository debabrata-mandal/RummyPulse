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
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.data.GameDataWrapper;
import com.example.rummypulse.data.GameViewApproval;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.GameViewApprovalRepository;
import com.example.rummypulse.data.RoundScorePatch;
import com.example.rummypulse.data.ScoreHistoryEvent;
import com.example.rummypulse.data.ScoreRegressionGuard;
import com.example.rummypulse.data.ScoreRecoveryPatch;
import com.example.rummypulse.data.sync.GameOperationRepository;
import com.example.rummypulse.utils.PinUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;

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

    public interface RoundSaveCallback {
        void onSuccess();

        default void onSavedOffline() {
            onError("Round saved locally and is waiting for a connection.");
        }

        void onError(String message);
    }

    public interface RecoveryPreviewCallback {
        void onAvailable(RecoveryPreview preview);
        void onUnavailable(String message);
    }

    public static final class RecoveryPreview {
        private final int round1Based;
        private final String eventId;
        private final long expectedRevision;
        private final Map<String, Integer> scoresByPlayerId;

        RecoveryPreview(int round1Based, String eventId, long expectedRevision,
                Map<String, Integer> scoresByPlayerId) {
            this.round1Based = round1Based;
            this.eventId = eventId;
            this.expectedRevision = expectedRevision;
            this.scoresByPlayerId = new LinkedHashMap<>(scoresByPlayerId);
        }

        public int getRound1Based() { return round1Based; }
        public String getEventId() { return eventId; }
        public long getExpectedRevision() { return expectedRevision; }
        public Map<String, Integer> getScoresByPlayerId() {
            return new LinkedHashMap<>(scoresByPlayerId);
        }
    }

    public interface PlayerLinkCallback {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore db;
    private final GameViewApprovalRepository viewApprovalRepository;
    private final GameRepository gameRepository;
    private final GameOperationRepository operationRepository;
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
    private long latestGameRevision;
    private boolean roundSaveInProgress;

    public JoinGameViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
        viewApprovalRepository = new GameViewApprovalRepository();
        gameRepository = GameRepository.getDashboardInstance();
        operationRepository = GameOperationRepository.getInstance(application);
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

    public long getLatestGameRevision() {
        return latestGameRevision;
    }

    public boolean canSaveGameData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return GameEditAccessPolicy.canSaveGameData(
                editAccessGranted.getValue(),
                activeEditGeneration,
                gameAuth.getValue(),
                user != null ? user.getUid() : null);
    }

    public void clearEditSessionStale() {
        editSessionStale.setValue(null);
    }

    public void revokeEditAccessLocally() {
        activeEditGeneration = 0;
        editAccessGranted.setValue(false);
    }

    public void joinGame(String gameId, boolean requestEditAccess) {
        joinGameInternal(gameId, requestEditAccess, null, false, 0L);
    }

    public void joinGame(String gameId, boolean requestEditAccess, String enteredPin) {
        joinGameInternal(gameId, requestEditAccess, enteredPin, false, 0L);
    }

    public void joinGame(String gameId, boolean requestEditAccess, String enteredPin, boolean skipViewGate) {
        joinGameInternal(gameId, requestEditAccess, enteredPin, skipViewGate, 0L);
    }

    public void joinGameWithCachedEditSession(
            String gameId, String enteredPin, long cachedGeneration) {
        joinGameInternal(gameId, true, enteredPin, false, cachedGeneration);
    }

    private void joinGameInternal(String gameId, boolean requestEditAccess,
            String enteredPin, boolean skipViewGate, long cachedGeneration) {
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
                                    skipViewGate, cachedGeneration);
                        })
                        .addOnFailureListener(e -> {
                            if (requestEditAccess && cachedGeneration > 0
                                    && isConnectivityFailure(e)) {
                                restoreCachedEditorSessionAndLoad(
                                        gameId, enteredPin, cachedGeneration);
                            } else {
                                isLoading.setValue(false);
                                gameDisplayName.setValue(null);
                                gameAuth.setValue(null);
                                errorMessage.setValue(
                                        "Failed to connect to server. Please try again.");
                            }
                        }), 500);
    }

    private void proceedJoinAfterAuthLoaded(String gameId,
                                            GameAuth auth,
                                            boolean requestEditAccess,
                                            String enteredPin,
                                            boolean skipViewGate,
                                            long cachedGeneration) {
        if (skipViewGate || GameViewApprovalRepository.canBypassViewGate(auth)) {
            continueJoinAfterViewAccess(
                    gameId, requestEditAccess, enteredPin, cachedGeneration);
            return;
        }

        AppUserManager.getInstance().isCurrentUserAdmin(isAdmin -> {
            if (isAdmin) {
                continueJoinAfterViewAccess(
                        gameId, requestEditAccess, enteredPin, cachedGeneration);
                return;
            }
            viewApprovalRepository.resolveViewAccess(gameId,
                    new GameViewApprovalRepository.ViewAccessCallback() {
                        @Override
                        public void onResult(GameViewApprovalRepository.ViewAccessOutcome outcome) {
                            if (outcome == GameViewApprovalRepository.ViewAccessOutcome.GRANTED) {
                                continueJoinAfterViewAccess(
                                        gameId, requestEditAccess, enteredPin,
                                        cachedGeneration);
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
                                             String enteredPin,
                                             long cachedGeneration) {
        if (requestEditAccess && enteredPin != null) {
            GameAuth cachedAuth = gameAuth.getValue();
            if (cachedAuth != null
                    && enteredPin.equals(cachedAuth.getPin())
                    && tryRestoreActiveEditorSession(cachedAuth)) {
                fetchGameData(gameId);
                return;
            }
            claimEditAccess(gameId, enteredPin, new ClaimCallback() {
                @Override
                public void onSuccess(String pin, long pinGeneration) {
                    fetchGameData(gameId);
                }

                @Override
                public void onError(String message) {
                    if (cachedGeneration > 0 && isConnectivityFailure(message)) {
                        restoreCachedEditorSessionAndLoad(
                                gameId, enteredPin, cachedGeneration);
                    } else {
                        errorMessage.setValue(message);
                        editAccessGranted.setValue(false);
                        fetchGameData(gameId);
                    }
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

    private void restoreCachedEditorSessionAndLoad(
            String gameId, String pin, long cachedGeneration) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || cachedGeneration <= 0 || TextUtils.isEmpty(pin)) {
            isLoading.setValue(false);
            errorMessage.setValue("The saved offline edit session is unavailable.");
            return;
        }
        GameAuth cachedAuth = gameAuth.getValue();
        if (cachedAuth == null) cachedAuth = new GameAuth();
        cachedAuth.setPin(pin);
        cachedAuth.setPinGeneration(cachedGeneration);
        cachedAuth.setActiveEditorUserId(user.getUid());
        cachedAuth.setActiveEditorName(resolveEditorDisplayName(user));
        gameAuth.setValue(cachedAuth);
        activeEditGeneration = cachedGeneration;
        gamePin.setValue(pin);
        editAccessGranted.setValue(true);
        operationRepository.loadOfflineProjectedSnapshot(
                gameId, cachedGeneration, new GameOperationRepository.Callback() {
                    @Override
                    public void onStored(GameData projected) {
                        isLoading.setValue(false);
                        GameDataSchema.normalize(projected);
                        gameData.setValue(projected);
                    }

                    @Override
                    public void onError(String message) {
                        // Firestore's local cache is still a useful fallback when
                        // the Room snapshot predates this feature.
                        fetchGameData(gameId);
                    }
                });
    }

    private static boolean isConnectivityFailure(Exception error) {
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.UNAVAILABLE) {
            return true;
        }
        return isConnectivityFailure(error == null ? null : error.getMessage());
    }

    private static boolean isConnectivityFailure(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(java.util.Locale.US);
        return normalized.contains("unknown host")
                || normalized.contains("unable to resolve host")
                || normalized.contains("unavailable")
                || normalized.contains("network is unreachable")
                || normalized.contains("failed to connect");
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
            updates.put("lastEditorUserId", myUid);
            updates.put("lastEditorName", editorName);
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
                currentAuth.setLastEditorUserId(user.getUid());
                currentAuth.setLastEditorName(editorName);
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
        DocumentReference gameDataRef =
                db.collection(FirestoreCollections.GAME_DATA).document(gameId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(gameRef);
            DocumentSnapshot dataSnapshot = transaction.get(gameDataRef);
            if (!snapshot.exists() || !dataSnapshot.exists()) {
                throw new IllegalStateException("Game data is no longer available.");
            }

            GameAuth auth = snapshot.toObject(GameAuth.class);
            long currentGen = auth != null ? auth.getPinGenerationOrDefault() : 1L;
            long newGen = currentGen + 1;
            String newPin = PinUtils.generatePin();

            Map<String, Object> updates = new HashMap<>();
            updates.put("pin", newPin);
            updates.put("pinGeneration", newGen);
            if (auth != null && !TextUtils.isEmpty(auth.getActiveEditorUserId())) {
                updates.put("lastEditorUserId", auth.getActiveEditorUserId());
                updates.put("lastEditorName", auth.getActiveEditorName());
            }
            updates.put("activeEditorUserId", com.google.firebase.firestore.FieldValue.delete());
            updates.put("activeEditorName", com.google.firebase.firestore.FieldValue.delete());
            transaction.update(gameRef, updates);
            transaction.update(gameDataRef, "editGeneration", newGen);

            return new TransferResult(newPin, newGen);
        }).addOnSuccessListener(result -> {
            gamePin.setValue(result.newPin);
            activeEditGeneration = 0;
            editAccessGranted.setValue(false);
            GameAuth currentAuth = gameAuth.getValue();
            if (currentAuth != null) {
                currentAuth.setPin(result.newPin);
                currentAuth.setPinGeneration(result.newPinGeneration);
                if (!TextUtils.isEmpty(currentAuth.getActiveEditorUserId())) {
                    currentAuth.setLastEditorUserId(currentAuth.getActiveEditorUserId());
                    currentAuth.setLastEditorName(currentAuth.getActiveEditorName());
                }
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
                                GameDataSchema.normalize(wrapper.getData());
                                latestGameRevision = revisionOf(documentSnapshot);
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

    /**
     * Saves the player-to-user mapping and grants that user view access atomically.
     * The deterministic approval document ID makes this safe to repeat.
     */
    public void linkPlayerAndApproveViewAccess(
            String gameId,
            String playerId,
            String linkedPlayerName,
            String linkedUserId,
            String linkedUserDisplayName,
            PlayerLinkCallback callback) {
        if (TextUtils.isEmpty(gameId) || TextUtils.isEmpty(playerId)
                || TextUtils.isEmpty(linkedPlayerName)
                || TextUtils.isEmpty(linkedUserId)) {
            callback.onError("Invalid player mapping.");
            return;
        }
        FirebaseUser editor = FirebaseAuth.getInstance().getCurrentUser();
        if (!canSaveGameData() || editor == null) {
            callback.onError("Only the active editor can link a player.");
            return;
        }

        final long expectedGeneration = getActiveEditGeneration();
        final String expectedEditorUserId = editor.getUid();
        final DocumentReference gameRef =
                db.collection(FirestoreCollections.GAMES).document(gameId);
        final DocumentReference gameDataRef =
                db.collection(FirestoreCollections.GAME_DATA).document(gameId);
        final DocumentReference approvalRef =
                db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                        .document(GameViewApprovalRepository.documentId(
                                gameId, linkedUserId));

        db.runTransaction(transaction -> {
            DocumentSnapshot authSnapshot = transaction.get(gameRef);
            DocumentSnapshot currentDataSnapshot = transaction.get(gameDataRef);
            DocumentSnapshot approvalSnapshot = transaction.get(approvalRef);
            if (!authSnapshot.exists() || !currentDataSnapshot.exists()) {
                throw new IllegalStateException("Game data is no longer available.");
            }

            GameAuth remoteAuth = authSnapshot.toObject(GameAuth.class);
            if (remoteAuth == null
                    || !expectedEditorUserId.equals(remoteAuth.getActiveEditorUserId())
                    || remoteAuth.getPinGenerationOrDefault() != expectedGeneration) {
                throw new IllegalStateException(
                        "Edit access changed. Reopen the game before linking this player.");
            }
            Long remoteDataGeneration = currentDataSnapshot.getLong("editGeneration");
            long actualDataGeneration = remoteDataGeneration != null
                    && remoteDataGeneration > 0
                    ? remoteDataGeneration
                    : 1L;
            if (actualDataGeneration != expectedGeneration) {
                throw new IllegalStateException(
                        "Edit access changed. Reopen the game before linking this player.");
            }

            GameDataWrapper latestWrapper =
                    currentDataSnapshot.toObject(GameDataWrapper.class);
            GameData latestGameData =
                    latestWrapper != null ? latestWrapper.getData() : null;
            GameDataSchema.normalize(latestGameData);
            int latestPlayerIndex =
                    com.example.rummypulse.data.PlayerMappingPatch.findPlayerIndex(
                            latestGameData, playerId);
            if (latestPlayerIndex < 0) {
                throw new IllegalStateException(
                        "The player list changed. Refresh the game and retry.");
            }
            for (int i = 0; i < latestGameData.getPlayers().size(); i++) {
                if (i != latestPlayerIndex
                        && linkedUserId.equals(
                                latestGameData.getPlayers().get(i).getUserId())) {
                    throw new IllegalStateException(
                            "That user is already linked to another player.");
                }
            }
            com.example.rummypulse.data.PlayerMappingPatch.apply(
                    latestGameData,
                    latestPlayerIndex,
                    linkedPlayerName,
                    linkedUserId);
            Map<String, Object> latestGameDataDoc =
                    buildGameDataDocument(
                            latestGameData,
                            expectedGeneration,
                            revisionOf(currentDataSnapshot) + 1L);

            String approvalDisplayName = TextUtils.isEmpty(linkedUserDisplayName)
                    ? linkedUserId
                    : linkedUserDisplayName;
            Object requestedAt = approvalSnapshot.exists()
                    ? approvalSnapshot.get("requestedAt")
                    : null;
            if (requestedAt == null) {
                requestedAt = com.google.firebase.firestore.FieldValue.serverTimestamp();
            }

            Map<String, Object> approvalData = new HashMap<>();
            approvalData.put("gameId", gameId);
            approvalData.put("userId", linkedUserId);
            approvalData.put("userDisplayName", approvalDisplayName);
            approvalData.put("status", "approved");
            approvalData.put("requestedAt", requestedAt);
            approvalData.put("lastUpdatedAt",
                    com.google.firebase.firestore.FieldValue.serverTimestamp());

            Map<String, Object> mirroredApproval = new HashMap<>();
            mirroredApproval.put("userDisplayName", approvalDisplayName);
            mirroredApproval.put("status", "approved");
            mirroredApproval.put("requestedAt", requestedAt);
            mirroredApproval.put("lastUpdatedAt",
                    com.google.firebase.firestore.FieldValue.serverTimestamp());

            transaction.set(gameDataRef, latestGameDataDoc);
            transaction.set(approvalRef, approvalData);
            transaction.update(
                    gameRef,
                    GameViewApprovalRepository.PENDING_VIEW_REQUESTS_FIELD
                            + "." + linkedUserId,
                    mirroredApproval);
            return new SavedGameData(
                    latestGameData, revisionOf(currentDataSnapshot) + 1L);
        }).addOnSuccessListener(saved -> {
            latestGameRevision = saved.revision;
            gameData.setValue(saved.gameData);
            gameRepository.updateLocalDashboardFromGameData(gameId, saved.gameData);
            callback.onSuccess();
        }).addOnFailureListener(error -> {
            String message = error.getMessage();
            callback.onError(message == null || message.trim().isEmpty()
                    ? "Could not link this player. Check your connection and retry."
                    : message);
        });
    }

    /**
     * Clears a player mapping and revokes that user's view approval in one
     * transaction, including the mirrored request entry on the game document.
     */
    public void unlinkPlayerAndRevokeViewAccess(
            String gameId,
            String playerId,
            String linkedUserId,
            PlayerLinkCallback callback) {
        if (TextUtils.isEmpty(gameId) || TextUtils.isEmpty(playerId)
                || TextUtils.isEmpty(linkedUserId)) {
            callback.onError("Invalid player mapping.");
            return;
        }
        FirebaseUser editor = FirebaseAuth.getInstance().getCurrentUser();
        if (!canSaveGameData() || editor == null) {
            callback.onError("Only the active editor can unlink a player.");
            return;
        }

        final long expectedGeneration = getActiveEditGeneration();
        final String expectedEditorUserId = editor.getUid();
        final DocumentReference gameRef =
                db.collection(FirestoreCollections.GAMES).document(gameId);
        final DocumentReference gameDataRef =
                db.collection(FirestoreCollections.GAME_DATA).document(gameId);
        final DocumentReference approvalRef =
                db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                        .document(GameViewApprovalRepository.documentId(
                                gameId, linkedUserId));

        db.runTransaction(transaction -> {
            DocumentSnapshot authSnapshot = transaction.get(gameRef);
            DocumentSnapshot dataSnapshot = transaction.get(gameDataRef);
            validateEditorSnapshot(
                    authSnapshot,
                    dataSnapshot,
                    expectedEditorUserId,
                    expectedGeneration);

            GameDataWrapper latestWrapper = dataSnapshot.toObject(GameDataWrapper.class);
            GameData latestGameData =
                    latestWrapper != null ? latestWrapper.getData() : null;
            GameDataSchema.normalize(latestGameData);
            int latestPlayerIndex =
                    com.example.rummypulse.data.PlayerMappingPatch.findPlayerIndex(
                            latestGameData, playerId);
            if (latestPlayerIndex < 0
                    || !linkedUserId.equals(
                            latestGameData.getPlayers().get(latestPlayerIndex).getUserId())) {
                throw new IllegalStateException(
                        "The player mapping changed. Refresh the game and retry.");
            }
            com.example.rummypulse.data.PlayerMappingPatch.clear(
                    latestGameData, latestPlayerIndex);
            transaction.set(
                    gameDataRef,
                    buildGameDataDocument(
                            latestGameData,
                            expectedGeneration,
                            revisionOf(dataSnapshot) + 1L));
            transaction.delete(approvalRef);
            transaction.update(
                    gameRef,
                    GameViewApprovalRepository.PENDING_VIEW_REQUESTS_FIELD
                            + "." + linkedUserId,
                    com.google.firebase.firestore.FieldValue.delete());
            return new SavedGameData(
                    latestGameData, revisionOf(dataSnapshot) + 1L);
        }).addOnSuccessListener(saved -> {
            latestGameRevision = saved.revision;
            gameData.setValue(saved.gameData);
            gameRepository.updateLocalDashboardFromGameData(gameId, saved.gameData);
            callback.onSuccess();
        }).addOnFailureListener(error -> {
            String message = error.getMessage();
            callback.onError(message == null || message.trim().isEmpty()
                    ? "Could not remove this player link. Check your connection and retry."
                    : message);
        });
    }

    /**
     * Commits a completed round and its dashboard summary together. The transaction
     * verifies that this user still owns the same edit generation before writing.
     */
    public void saveCompletedRoundAtomically(String gameId, RoundScorePatch patch,
            RoundSaveCallback callback) {
        if (TextUtils.isEmpty(gameId) || patch == null) {
            callback.onError("Invalid game data.");
            return;
        }
        if (roundSaveInProgress) {
            callback.onError("This round is already being saved.");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (!canSaveGameData() || user == null) {
            callback.onError("Only the active editor can save round scores.");
            return;
        }

        final long expectedGeneration = getActiveEditGeneration();
        if (patch.getEditGeneration() != expectedGeneration) {
            callback.onError("Edit access changed. Reopen the game before saving.");
            return;
        }
        final String expectedEditorUserId = user.getUid();
        final DocumentReference gameRef =
                db.collection(FirestoreCollections.GAMES).document(gameId);
        final DocumentReference gameDataRef =
                db.collection(FirestoreCollections.GAME_DATA).document(gameId);
        roundSaveInProgress = true;
        db.runTransaction(transaction -> {
            DocumentSnapshot authSnapshot = transaction.get(gameRef);
            DocumentSnapshot dataSnapshot = transaction.get(gameDataRef);
            validateEditorSnapshot(
                    authSnapshot, dataSnapshot, expectedEditorUserId, expectedGeneration);
            GameDataWrapper latestWrapper = dataSnapshot.toObject(GameDataWrapper.class);
            GameData latest = latestWrapper != null ? latestWrapper.getData() : null;
            GameDataSchema.normalize(latest);
            GameData patched = patch.applyToLatest(latest);
            GameDataSchema.normalize(patched);
            long previousRevision = revisionOf(dataSnapshot);
            long nextRevision = previousRevision + 1L;
            HashSet<String> changedPlayers = new HashSet<>(latest.getPlayerOrder());
            ScoreRegressionGuard.requireOnlyRoundChanged(latest, patched,
                    patch.getRound1Based(), changedPlayers);
            Map<String, Object> gameDocument =
                    buildGameDataDocument(patched, expectedGeneration, nextRevision);
            gameDocument.put("lastOperationId", patch.getOperationId());
            transaction.set(gameDataRef, gameDocument);
            DocumentReference historyRef = db
                    .collection(FirestoreCollections.GAME_SCORE_HISTORY)
                    .document(gameId)
                    .collection("rounds")
                    .document(String.valueOf(patch.getRound1Based()))
                    .collection("events")
                    .document(patch.getOperationId());
            transaction.set(historyRef, ScoreHistoryEvent.create(
                    gameId, patch.getRound1Based(), patched, patch.getOperationId(),
                    patch.isCorrection() ? "ROUND_CORRECTION" : "ROUND_SAVE",
                    expectedEditorUserId, expectedGeneration, previousRevision, nextRevision));
            transaction.update(gameRef, buildDashboardSummary(patched));
            return new SavedGameData(patched, nextRevision);
        }).addOnSuccessListener(saved -> {
            roundSaveInProgress = false;
            latestGameRevision = saved.revision;
            gameData.setValue(saved.gameData);
            gameRepository.updateLocalDashboardFromGameData(gameId, saved.gameData);
            callback.onSuccess();
        }).addOnFailureListener(error -> {
            roundSaveInProgress = false;
            if (error instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) error).getCode()
                    == FirebaseFirestoreException.Code.UNAVAILABLE) {
                callback.onSavedOffline();
                return;
            }
            String message = error.getMessage();
            callback.onError(message == null || message.trim().isEmpty()
                    ? "Could not save this round. Check your connection and retry."
                    : message);
        });
    }

    public void loadRecoveryPreview(String gameId, int round1Based,
            RecoveryPreviewCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !canSaveGameData()) {
            callback.onUnavailable("Only the active editor can restore score history.");
            return;
        }
        db.collection(FirestoreCollections.GAME_SCORE_HISTORY)
                .document(gameId)
                .collection("rounds")
                .document(String.valueOf(round1Based))
                .collection("events")
                .orderBy("committedRevision", Query.Direction.DESCENDING)
                .limit(25)
                .get(Source.SERVER)
                .addOnSuccessListener(result -> {
                    if (result.isEmpty()) {
                        callback.onUnavailable("No immutable history exists for Round "
                                + round1Based + ". Enter the missing scores manually.");
                        return;
                    }
                    DocumentSnapshot event = null;
                    Map<String, Integer> scores = null;
                    for (DocumentSnapshot candidate : result.getDocuments()) {
                        Object rawScores = candidate.get("scoresByPlayerId");
                        if (rawScores instanceof Map) {
                            Map<String, Integer> candidateScores =
                                    parseHistoryScores((Map<?, ?>) rawScores);
                            if (ScoreHistoryEvent.isRecoveryComplete(candidateScores)) {
                                event = candidate;
                                scores = candidateScores;
                                break;
                            }
                        }
                    }
                    if (event == null || scores == null) {
                        callback.onUnavailable("No complete recovery record exists for Round "
                                + round1Based + ". Enter the missing scores manually.");
                        return;
                    }
                    callback.onAvailable(new RecoveryPreview(round1Based, event.getId(),
                            latestGameRevision, scores));
                })
                .addOnFailureListener(error -> callback.onUnavailable(
                        "Could not load score history: " + error.getMessage()));
    }

    public void restoreMissingRound(String gameId, RecoveryPreview preview,
            RoundSaveCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !canSaveGameData() || preview == null) {
            callback.onError("Only the active editor can restore score history.");
            return;
        }
        final String editorUserId = user.getUid();
        final long generation = getActiveEditGeneration();
        final String recoveryOperationId = UUID.randomUUID().toString();
        DocumentReference gameRef = db.collection(FirestoreCollections.GAMES).document(gameId);
        DocumentReference dataRef = db.collection(FirestoreCollections.GAME_DATA).document(gameId);
        DocumentReference sourceEventRef = db.collection(FirestoreCollections.GAME_SCORE_HISTORY)
                .document(gameId).collection("rounds")
                .document(String.valueOf(preview.round1Based)).collection("events")
                .document(preview.eventId);
        DocumentReference recoveryEventRef = db.collection(FirestoreCollections.GAME_SCORE_HISTORY)
                .document(gameId).collection("rounds")
                .document(String.valueOf(preview.round1Based)).collection("events")
                .document(recoveryOperationId);
        db.runTransaction(transaction -> {
            DocumentSnapshot authSnapshot = transaction.get(gameRef);
            DocumentSnapshot dataSnapshot = transaction.get(dataRef);
            DocumentSnapshot historySnapshot = transaction.get(sourceEventRef);
            validateEditorSnapshot(authSnapshot, dataSnapshot, editorUserId, generation);
            long currentRevision = revisionOf(dataSnapshot);
            if (currentRevision != preview.expectedRevision) {
                throw new IllegalStateException(
                        "Game data changed. Review the recovery again before restoring.");
            }
            if (!historySnapshot.exists()) {
                throw new IllegalStateException("The selected history record is unavailable.");
            }
            Object rawHistoryScores = historySnapshot.get("scoresByPlayerId");
            if (!(rawHistoryScores instanceof Map)) {
                throw new IllegalStateException("The selected history record is invalid.");
            }
            Map<String, Integer> historyScores = parseHistoryScores(
                    (Map<?, ?>) rawHistoryScores);
            GameDataWrapper wrapper = dataSnapshot.toObject(GameDataWrapper.class);
            GameData latest = wrapper == null ? null : wrapper.getData();
            GameData restored = ScoreRecoveryPatch.restoreMissing(
                    latest, preview.round1Based, historyScores);
            long nextRevision = currentRevision + 1L;
            Map<String, Object> document =
                    buildGameDataDocument(restored, generation, nextRevision);
            document.put("lastOperationId", recoveryOperationId);
            transaction.set(dataRef, document);
            transaction.set(recoveryEventRef, ScoreHistoryEvent.create(
                    gameId, preview.round1Based, restored, recoveryOperationId,
                    "RECOVERY", editorUserId, generation, currentRevision, nextRevision));
            transaction.update(gameRef, buildDashboardSummary(restored));
            return new SavedGameData(restored, nextRevision);
        }).addOnSuccessListener(saved -> {
            latestGameRevision = saved.revision;
            gameData.setValue(saved.gameData);
            gameRepository.updateLocalDashboardFromGameData(gameId, saved.gameData);
            callback.onSuccess();
        }).addOnFailureListener(error -> callback.onError(error.getMessage() == null
                ? "Recovery failed without changing the game." : error.getMessage()));
    }

    private static Map<String, Integer> parseHistoryScores(Map<?, ?> raw) {
        Map<String, Integer> parsed = new LinkedHashMap<>();
        if (raw == null) return parsed;
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                parsed.put((String) entry.getKey(), ((Number) entry.getValue()).intValue());
            }
        }
        return parsed;
    }

    public void applyPendingRoundLocally(RoundScorePatch patch) {
        GameData current = gameData.getValue();
        if (patch != null && current != null) {
            gameData.setValue(patch.applyToLatest(current));
        }
    }

    public void replaceLocalGameData(GameData localGameData) {
        if (localGameData != null) {
            gameData.setValue(localGameData);
        }
    }

    private static void validateEditorSnapshot(DocumentSnapshot authSnapshot,
            DocumentSnapshot dataSnapshot, String expectedEditorUserId,
            long expectedGeneration) {
        if (!authSnapshot.exists() || !dataSnapshot.exists()) {
            throw new IllegalStateException("Game data is no longer available.");
        }
        GameAuth remoteAuth = authSnapshot.toObject(GameAuth.class);
        if (remoteAuth == null
                || !expectedEditorUserId.equals(remoteAuth.getActiveEditorUserId())
                || remoteAuth.getPinGenerationOrDefault() != expectedGeneration) {
            throw new IllegalStateException(
                    "Edit access changed. Reopen the game before saving.");
        }
        Long remoteDataGeneration = dataSnapshot.getLong("editGeneration");
        long actualDataGeneration = remoteDataGeneration != null && remoteDataGeneration > 0
                ? remoteDataGeneration
                : 1L;
        if (actualDataGeneration != expectedGeneration) {
            throw new IllegalStateException(
                    "Edit access changed. Reopen the game before saving.");
        }
    }

    private static Map<String, Object> buildGameDataDocument(
            GameData updatedGameData, long editGeneration, long revision) {
        Map<String, Object> gameDataDoc = new HashMap<>();
        gameDataDoc.put("data", GameDataSchema.toFirestoreData(updatedGameData));
        gameDataDoc.put("lastUpdated",
                com.google.firebase.firestore.FieldValue.serverTimestamp());
        gameDataDoc.put("version", "2.0");
        gameDataDoc.put("editGeneration", editGeneration);
        gameDataDoc.put("revision", revision);
        return gameDataDoc;
    }

    private static Map<String, Object> buildDashboardSummary(GameData gameData) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("dashboardPointValue", gameData.getPointValue());
        summary.put("dashboardNumPlayers", gameData.getPlayers() != null
                ? gameData.getPlayers().size()
                : gameData.getNumPlayers());
        summary.put("dashboardGstPercent", gameData.getGstPercent());
        String status = gameData.getGameStatus();
        summary.put("dashboardGameStatus",
                status != null && !status.trim().isEmpty() ? status.trim() : "R1");
        return summary;
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
        fetchGameDataFromServer(gameId);
    }

    private void fetchGameDataFromServer(String gameId) {
        db.collection(FirestoreCollections.GAME_DATA).document(gameId)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    isLoading.setValue(false);
                    if (documentSnapshot.exists()) {
                        try {
                            GameDataWrapper wrapper = documentSnapshot.toObject(GameDataWrapper.class);
                            if (wrapper != null && wrapper.getData() != null) {
                                GameDataSchema.normalize(wrapper.getData());
                                long incomingRevision = revisionOf(documentSnapshot);
                                if (incomingRevision < latestGameRevision) {
                                    return;
                                }
                                latestGameRevision = incomingRevision;
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
                    errorMessage.setValue("Could not refresh from server. Check your connection and try again.");
                });
    }

    public void updateGameData(GameData newGameData) {
        if (newGameData != null) {
            GameDataSchema.normalize(newGameData);
            gameData.setValue(newGameData);
        }
    }

    public boolean updateGameDataFromSnapshot(GameData newGameData, long revision) {
        if (newGameData == null || revision < latestGameRevision) {
            return false;
        }
        GameDataSchema.normalize(newGameData);
        latestGameRevision = revision;
        gameData.setValue(newGameData);
        return true;
    }

    private static long revisionOf(DocumentSnapshot snapshot) {
        Long revision = snapshot == null ? null : snapshot.getLong("revision");
        return revision == null || revision < 0 ? 0L : revision;
    }

    private static final class SavedGameData {
        final GameData gameData;
        final long revision;

        SavedGameData(GameData gameData, long revision) {
            this.gameData = gameData;
            this.revision = revision;
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
