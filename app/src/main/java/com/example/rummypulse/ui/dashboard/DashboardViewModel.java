package com.example.rummypulse.ui.dashboard;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.FirestoreCollections;
import com.example.rummypulse.utils.DisplayNameUtils;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.GameViewApprovalRepository;
import com.example.rummypulse.data.GameCreationPolicy;
import com.example.rummypulse.ui.home.GameItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.rummypulse.utils.PinUtils;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class DashboardViewModel extends ViewModel {

    private final GameRepository gameRepository;
    private final MutableLiveData<List<GameItem>> mInProgressGames;
    private final MutableLiveData<List<GameItem>> mCompletedGames;
    private final MutableLiveData<String> mActiveGamesCount;
    private final MutableLiveData<String> mCompletedGamesCount;
    private final MutableLiveData<String> newGameCreated;
    private final MutableLiveData<GameCreationData> gameCreationEvent;
    private final MutableLiveData<GameCreationState> gameCreationState;
    private final java.util.Set<String> seenGameIds = new java.util.HashSet<>();
    private final Handler creationHandler = new Handler(Looper.getMainLooper());
    private CreationRequest activeCreationRequest;
    private Runnable creationSlowNotice;
    private boolean creationInProgress;
    private boolean retryQueued;

    public enum GameCreationStatus {
        IDLE,
        CREATING,
        SLOW,
        RETRY_QUEUED,
        SUCCESS,
        ERROR
    }

    public static final class GameCreationState {
        public final GameCreationStatus status;
        public final String message;

        private GameCreationState(GameCreationStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }
    
    // Helper class to hold game creation data
    public static class GameCreationData {
        public final String gameId;
        public final String creatorName;
        public final double pointValue;
        
        public GameCreationData(String gameId, String creatorName, double pointValue) {
            this.gameId = gameId;
            this.creatorName = creatorName;
            this.pointValue = pointValue;
        }
    }

    private static final class CreationRequest {
        final String requestId;
        final String gameId;
        final String pin;
        final String creatorUserId;
        final String creatorName;
        final double pointValue;
        final double gstPercentage;
        final String displayName;

        CreationRequest(
                String requestId,
                String gameId,
                String pin,
                String creatorUserId,
                String creatorName,
                double pointValue,
                double gstPercentage,
                String displayName) {
            this.requestId = requestId;
            this.gameId = gameId;
            this.pin = pin;
            this.creatorUserId = creatorUserId;
            this.creatorName = creatorName;
            this.pointValue = pointValue;
            this.gstPercentage = gstPercentage;
            this.displayName = displayName;
        }
    }

    public DashboardViewModel() {
        gameRepository = GameRepository.getDashboardInstance();
        mInProgressGames = new MutableLiveData<>();
        mCompletedGames = new MutableLiveData<>();
        mActiveGamesCount = new MutableLiveData<>();
        mCompletedGamesCount = new MutableLiveData<>();
        newGameCreated = new MutableLiveData<>();
        gameCreationEvent = new MutableLiveData<>();
        gameCreationState = new MutableLiveData<>(
                new GameCreationState(GameCreationStatus.IDLE, null));
        
        // Observe all games and filter for in-progress and completed ones
        gameRepository.getGameItems().observeForever(allGames -> {
            if (allGames != null) {
                List<GameItem> inProgressGames = new ArrayList<>();
                List<GameItem> completedGames = new ArrayList<>();
                
                // Get current user ID
                String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() 
                    : null;
                
                for (GameItem game : allGames) {
                    if (game.isCompleted() || "Completed".equals(game.getGameStatus())) {
                        completedGames.add(game);
                    } else {
                        inProgressGames.add(game);
                        
                        // Check if this is a new game that the current user did NOT create
                        if (!seenGameIds.contains(game.getGameId())) {
                            android.util.Log.d("DashboardViewModel", "New game detected: " + game.getGameId() + 
                                " created by: " + game.getCreatorName() + " (ID: " + game.getCreatorUserId() + ")");
                            
                            // Mark as seen
                            seenGameIds.add(game.getGameId());
                            
                            // Check if this is from a different user
                            boolean isDifferentUser = false;
                            String reason = "";
                            
                            if (game.getCreatorUserId() == null) {
                                isDifferentUser = true;
                                reason = "creator ID is missing";
                            } else if (currentUserId != null && !currentUserId.equals(game.getCreatorUserId())) {
                                isDifferentUser = true;
                                reason = "different user";
                            } else {
                                reason = "user is creator";
                            }
                            
                            if (isDifferentUser) {
                                String creatorName = game.getCreatorName() != null ? game.getCreatorName() : "Someone";
                                double pointValue = parsePointValue(game.getPointValue());
                                
                                android.util.Log.d("DashboardViewModel", "New game from another user: " + game.getGameId() + 
                                    " created by " + creatorName + " (Reason: " + reason + ")");
                                
                                gameCreationEvent.setValue(new GameCreationData(
                                    game.getGameId(), 
                                    creatorName, 
                                    pointValue
                                ));
                            } else {
                                android.util.Log.d("DashboardViewModel", "Game created by current user - " + reason + ". " +
                                    "Current user: " + currentUserId + ", Creator: " + game.getCreatorUserId());
                            }
                        }
                    }
                }
                
                mInProgressGames.setValue(inProgressGames);
                mCompletedGames.setValue(completedGames);
                
                // Update separate counts
                int activeCount = inProgressGames.size();
                int completedCount = completedGames.size();
                
                // Set active games count
                if (activeCount == 0) {
                    mActiveGamesCount.setValue("Active Games");
                } else if (activeCount == 1) {
                    mActiveGamesCount.setValue("Active Games (1)");
                } else {
                    mActiveGamesCount.setValue("Active Games (" + activeCount + ")");
                }
                
                // Set completed games count
                if (completedCount == 0) {
                    mCompletedGamesCount.setValue("Completed Games");
                } else if (completedCount == 1) {
                    mCompletedGamesCount.setValue("Completed Games (1)");
                } else {
                    mCompletedGamesCount.setValue("Completed Games (" + completedCount + ")");
                }
            } else {
                mInProgressGames.setValue(new ArrayList<>());
                mCompletedGames.setValue(new ArrayList<>());
                mActiveGamesCount.setValue("Active Games");
                mCompletedGamesCount.setValue("Completed Games");
            }
        });
        
        // Load games initially
        loadGames();
    }

    public LiveData<List<GameItem>> getInProgressGames() {
        return mInProgressGames;
    }

    public LiveData<List<GameItem>> getCompletedGames() {
        return mCompletedGames;
    }

    public LiveData<String> getActiveGamesCount() {
        return mActiveGamesCount;
    }

    public LiveData<String> getCompletedGamesCount() {
        return mCompletedGamesCount;
    }

    public LiveData<String> getNewGameCreated() {
        return newGameCreated;
    }
    
    public void clearNewGameCreated() {
        newGameCreated.setValue(null);
    }
    
    public LiveData<GameCreationData> getGameCreationEvent() {
        return gameCreationEvent;
    }

    public LiveData<GameCreationState> getGameCreationState() {
        return gameCreationState;
    }

    public boolean isGameCreationInProgress() {
        return creationInProgress;
    }

    public void resetGameCreationState() {
        if (!creationInProgress) {
            activeCreationRequest = null;
            retryQueued = false;
            gameCreationState.setValue(new GameCreationState(GameCreationStatus.IDLE, null));
        }
    }
    
    public void clearGameCreationEvent() {
        gameCreationEvent.setValue(null);
    }

    public void loadGames() {
        gameRepository.loadAllGamesWithRealtimeListener();
    }

    public void refreshCreatorDashboardRows() {
        gameRepository.refreshCreatorDashboardRows();
    }

    public void joinGame(GameItem game) {
        joinGame(game, "player"); // Default to player
    }
    
    public void joinGame(GameItem game, String joinType) {
        // TODO: Implement join game functionality
        // This could involve:
        // 1. Adding current user to the game as specified role (player/moderator)
        // 2. Navigating to game screen
        // 3. Updating game state
        // joinType can be "player" or "moderator"
    }

    public void createNewGame(double pointValue, double gstPercentage) {
        createNewGame(pointValue, gstPercentage, null);
    }

    public void createNewGame(double pointValue, double gstPercentage, String optionalDisplayName) {
        if (creationInProgress) {
            return;
        }
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            gameCreationState.setValue(new GameCreationState(
                    GameCreationStatus.ERROR,
                    "Your session is unavailable. Sign in again and retry."));
            return;
        }
        String creatorName = currentUser.getDisplayName() != null
                ? currentUser.getDisplayName()
                : currentUser.getEmail();
        activeCreationRequest = new CreationRequest(
                UUID.randomUUID().toString(),
                generateGameId(),
                PinUtils.generatePin(),
                currentUser.getUid(),
                creatorName != null ? creatorName : "User",
                pointValue,
                gstPercentage,
                optionalDisplayName != null ? optionalDisplayName.trim() : "");
        retryQueued = false;
        runCreationTransaction(activeCreationRequest);
    }

    public void retryGameCreation() {
        if (activeCreationRequest == null) {
            gameCreationState.setValue(new GameCreationState(
                    GameCreationStatus.ERROR,
                    "Nothing to retry. Enter the game details again."));
            return;
        }
        if (creationInProgress) {
            retryQueued = true;
            gameCreationState.setValue(new GameCreationState(
                    GameCreationStatus.RETRY_QUEUED,
                    "Retry ready. Waiting for the current attempt to finish safely…"));
            return;
        }
        retryQueued = false;
        runCreationTransaction(activeCreationRequest);
    }

    private void runCreationTransaction(CreationRequest request) {
        creationInProgress = true;
        gameCreationState.setValue(new GameCreationState(
                GameCreationStatus.CREATING,
                "Creating game securely…"));
        scheduleCreationSlowNotice(request);

        Map<String, Object> initialGameData = new HashMap<>();
        initialGameData.put("numPlayers", 2);
        initialGameData.put("pointValue", request.pointValue);
        initialGameData.put("gstPercent", request.gstPercentage);
        List<Map<String, Object>> players = new ArrayList<>();
        Map<String, Object> creatorPlayer = new HashMap<>();
        String creatorPlayerName = DisplayNameUtils.firstName(request.creatorName);
        if (creatorPlayerName.isEmpty()) {
            creatorPlayerName = "You";
        }
        creatorPlayer.put("name", creatorPlayerName);
        creatorPlayer.put("scores", new ArrayList<>(java.util.Collections.nCopies(10, -1)));
        creatorPlayer.put("randomNumber", null);
        creatorPlayer.put("isCreator", true); // Mark as creator
        creatorPlayer.put("userId", request.creatorUserId);
        players.add(creatorPlayer);
        Map<String, Object> player2 = new HashMap<>();
        player2.put("name", "Player 2");
        player2.put("scores", new ArrayList<>(java.util.Collections.nCopies(10, -1)));
        player2.put("randomNumber", null);
        player2.put("isCreator", false);
        player2.put("userId", null);
        players.add(player2);
        initialGameData.put("players", players);
        initialGameData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        Map<String, Object> authData = new HashMap<>();
        authData.put("gameId", request.gameId);
        authData.put("pin", request.pin);
        authData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        authData.put("creatorUserId", request.creatorUserId);
        authData.put("creatorName", request.creatorName);
        authData.put("version", "1.0");
        authData.put("displayName", request.displayName);
        authData.put("pinGeneration", 1L);
        authData.put("activeEditorUserId", request.creatorUserId);
        authData.put("activeEditorName", request.creatorName);
        authData.put("dashboardPointValue", request.pointValue);
        authData.put("dashboardNumPlayers", 2);
        authData.put("dashboardGstPercent", request.gstPercentage);
        authData.put("dashboardGameStatus", "R1");
        authData.put("creationRequestId", request.requestId);
        authData.put("initializationStatus",
                GameCreationPolicy.INITIALIZATION_PENDING);

        Map<String, Object> gameDataDoc = new HashMap<>();
        gameDataDoc.put("data", initialGameData);
        gameDataDoc.put("lastUpdated", com.google.firebase.firestore.FieldValue.serverTimestamp());
        gameDataDoc.put("version", "1.0");
        gameDataDoc.put("editGeneration", 1L);

        Map<String, Object> creatorApproval = new HashMap<>();
        creatorApproval.put("gameId", request.gameId);
        creatorApproval.put("userId", request.creatorUserId);
        creatorApproval.put("userDisplayName", request.creatorName);
        creatorApproval.put("status", "approved");
        creatorApproval.put("requestedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        creatorApproval.put("lastUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference authRef =
                db.collection(FirestoreCollections.GAMES).document(request.gameId);
        com.google.firebase.firestore.DocumentReference dataRef =
                db.collection(FirestoreCollections.GAME_DATA).document(request.gameId);
        com.google.firebase.firestore.DocumentReference approvalRef =
                db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                        .document(GameViewApprovalRepository.documentId(
                                request.gameId, request.creatorUserId));

        // The deployed rules authorize gameData/approval creation by reading the
        // already-existing games_v2 document. Bootstrap that authorization document
        // first, but keep it hidden until the atomic finalization transaction commits.
        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot existing =
                            transaction.get(authRef);
                    if (existing.exists()) {
                        String existingRequest = existing.getString("creationRequestId");
                        if (request.requestId.equals(existingRequest)) {
                            return request.gameId;
                        }
                        throw new IllegalStateException(
                                "Generated game id already exists. Please retry.");
                    }
                    transaction.set(authRef, authData);
                    return request.gameId;
                })
                .addOnSuccessListener(gameId -> finalizeCreationTransaction(
                        request, authRef, dataRef, approvalRef,
                        gameDataDoc, creatorApproval))
                .addOnFailureListener(error -> handleCreationFailure(request, error));
    }

    private void finalizeCreationTransaction(
            CreationRequest request,
            com.google.firebase.firestore.DocumentReference authRef,
            com.google.firebase.firestore.DocumentReference dataRef,
            com.google.firebase.firestore.DocumentReference approvalRef,
            Map<String, Object> gameDataDoc,
            Map<String, Object> creatorApproval) {
        FirebaseFirestore.getInstance().runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot auth =
                            transaction.get(authRef);
                    if (!auth.exists()
                            || !request.requestId.equals(
                            auth.getString("creationRequestId"))
                            || !request.creatorUserId.equals(
                            auth.getString("creatorUserId"))) {
                        throw new IllegalStateException(
                                "Game initialization ownership changed. Please retry.");
                    }
                    transaction.set(dataRef, gameDataDoc);
                    transaction.set(approvalRef, creatorApproval);
                    transaction.update(authRef, "initializationStatus",
                            GameCreationPolicy.INITIALIZATION_READY);
                    return request.gameId;
                })
                .addOnSuccessListener(gameId -> handleCreationSuccess(request))
                .addOnFailureListener(error -> handleCreationFailure(request, error));
    }

    private void scheduleCreationSlowNotice(CreationRequest request) {
        cancelCreationSlowNotice();
        creationSlowNotice = () -> {
            if (creationInProgress && activeCreationRequest == request) {
                gameCreationState.setValue(new GameCreationState(
                        GameCreationStatus.SLOW,
                        "The network is taking longer than expected. You can queue one safe retry."));
            }
        };
        creationHandler.postDelayed(
                creationSlowNotice,
                GameCreationPolicy.SLOW_NETWORK_NOTICE_MS);
    }

    private void handleCreationSuccess(CreationRequest request) {
        if (activeCreationRequest != request) {
            return;
        }
        cancelCreationSlowNotice();
        creationInProgress = false;
        retryQueued = false;
        gameCreationState.setValue(new GameCreationState(
                GameCreationStatus.SUCCESS,
                "Game created successfully."));
        newGameCreated.setValue(request.gameId);
        gameCreationEvent.setValue(new GameCreationData(
                request.gameId, request.creatorName, request.pointValue));
        android.util.Log.d("GameCreation", "Atomic game creation committed: "
                + request.gameId);
    }

    private void handleCreationFailure(CreationRequest request, Exception error) {
        if (activeCreationRequest != request) {
            return;
        }
        cancelCreationSlowNotice();
        creationInProgress = false;
        android.util.Log.e("GameCreation", "Atomic game creation failed", error);
        if (retryQueued) {
            retryQueued = false;
            runCreationTransaction(request);
            return;
        }
        gameCreationState.setValue(new GameCreationState(
                GameCreationStatus.ERROR,
                creationErrorMessage(error)));
    }

    private static String creationErrorMessage(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code =
                    ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return "Could not reach the server. Check your internet connection and retry.";
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Permission denied while creating the game. Sign in again or contact support.";
            }
        }
        String detail = error.getMessage();
        return detail != null ? detail : "Game creation failed. Please retry.";
    }

    private void cancelCreationSlowNotice() {
        if (creationSlowNotice != null) {
            creationHandler.removeCallbacks(creationSlowNotice);
            creationSlowNotice = null;
        }
    }

    private String generateGameId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < 9; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return result.toString();
    }
    
    /**
     * Parse point value from string to double
     */
    private double parsePointValue(String pointValueStr) {
        if (pointValueStr == null || pointValueStr.isEmpty()) {
            return 0.0;
        }
        try {
            // Remove currency symbol if present
            String cleaned = pointValueStr.replace("₹", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        cancelCreationSlowNotice();
        // Clean up listeners when ViewModel is destroyed
        if (gameRepository != null) {
            gameRepository.removeListeners();
        }
    }
}
