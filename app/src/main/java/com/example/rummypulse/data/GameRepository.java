package com.example.rummypulse.data;

import android.content.Context;
import android.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class GameRepository {
    private static final String GAMES_COLLECTION = "games";
    private static final String GAME_DATA_COLLECTION = "gameData";
    private static final String APPROVED_GAMES_COLLECTION = "approvedGames";
    private static final String APPROVED_GAMES_REPORT_COLLECTION = "approvedGamesReport";
    
    private FirebaseFirestore db;
    private MutableLiveData<List<GameItem>> gameItemsLiveData;
    private MutableLiveData<String> errorLiveData;
    private MutableLiveData<Double> totalApprovedGstLiveData;
    private MutableLiveData<Integer> approvedGamesCountLiveData;
    private MutableLiveData<List<MonthlyPointValueReport>> reportsSummariesLiveData;
    
    // Firestore listeners for real-time updates (used by Dashboard)
    private com.google.firebase.firestore.ListenerRegistration gamesListener;
    private com.google.firebase.firestore.ListenerRegistration approvedGamesListener;
    private java.util.Map<String, com.google.firebase.firestore.ListenerRegistration> gameDataListeners = new java.util.HashMap<>();
    
    // Store game items and game IDs order for maintaining consistency
    private java.util.Map<String, GameItem> gameItemsMap = new java.util.HashMap<>();
    private List<String> gameIdsOrder = new ArrayList<>();
    
    // Track seen games
    private Set<String> seenGameIds = new HashSet<>();
    private Context appContext;

    public GameRepository() {
        db = FirebaseFirestore.getInstance();
        gameItemsLiveData = new MutableLiveData<>();
        errorLiveData = new MutableLiveData<>();
        totalApprovedGstLiveData = new MutableLiveData<>();
        approvedGamesCountLiveData = new MutableLiveData<>();
        reportsSummariesLiveData = new MutableLiveData<>();
    }
    
    /**
     * Set application context
     */
    public void setContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public LiveData<List<GameItem>> getGameItems() {
        return gameItemsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Double> getTotalApprovedGst() {
        return totalApprovedGstLiveData;
    }

    public LiveData<Integer> getApprovedGamesCount() {
        return approvedGamesCountLiveData;
    }

    public LiveData<List<MonthlyPointValueReport>> getReportsSummaries() {
        return reportsSummariesLiveData;
    }

    /**
     * Load all games with real-time listener (for Dashboard)
     * This method sets up a real-time listener that automatically updates when data changes
     */
    public void loadAllGamesWithRealtimeListener() {
        System.out.println("GameRepository: Setting up real-time listener for games collection");
        
        // Remove existing listener if any
        if (gamesListener != null) {
            gamesListener.remove();
        }
        
        // Set up real-time listener for games collection
        gamesListener = db.collection(GAMES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        System.out.println("GameRepository: Error listening to games collection: " + error.getMessage());
                        errorLiveData.setValue("Failed to load games: " + error.getMessage());
                        return;
                    }
                    
                    if (querySnapshot != null) {
                        System.out.println("GameRepository: Real-time update received, found " + querySnapshot.size() + " documents");
                        List<String> gameIds = new ArrayList<>();
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            gameIds.add(document.getId());
                        }
                        
                        if (gameIds.isEmpty()) {
                            System.out.println("GameRepository: No games found in database");
                            clearGameDataListenersAndResetGameListState();
                            return;
                        }
                        
                        System.out.println("GameRepository: Found " + gameIds.size() + " game IDs, loading game data");
                        // Now load game data for each game ID with real-time listeners
                        loadGameDataForIdsWithListeners(gameIds);
                    }
                });
    }
    
    /**
     * Load all games with one-time fetch (for Review screen - manual refresh only)
     * This method fetches data once and does not set up real-time listeners.
     * Uses {@link Source#SERVER} first so local persistence cannot keep a deleted last game in the list.
     */
    public void loadAllGames() {
        System.out.println("GameRepository: Fetching games collection (manual refresh, prefer server)");
        com.google.firebase.firestore.Query gamesQuery = db.collection(GAMES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        gamesQuery.get(Source.SERVER)
                .addOnSuccessListener(this::applyGamesQuerySnapshotForReview)
                .addOnFailureListener(error -> {
                    System.out.println("GameRepository: Server games fetch failed, falling back to default: " + error.getMessage());
                    gamesQuery.get()
                            .addOnSuccessListener(this::applyGamesQuerySnapshotForReview)
                            .addOnFailureListener(e2 -> {
                                System.out.println("GameRepository: Error fetching games collection: " + e2.getMessage());
                                errorLiveData.setValue("Failed to load games: " + e2.getMessage());
                            });
                });
    }

    private void applyGamesQuerySnapshotForReview(QuerySnapshot querySnapshot) {
        if (querySnapshot == null) {
            return;
        }
        System.out.println("GameRepository: Fetched " + querySnapshot.size() + " documents");
        List<String> gameIds = new ArrayList<>();
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            gameIds.add(document.getId());
        }
        if (gameIds.isEmpty()) {
            System.out.println("GameRepository: No games found in database");
            resetGameListStateForEmptyQuery();
            return;
        }
        System.out.println("GameRepository: Found " + gameIds.size() + " game IDs, loading game data");
        loadGameDataForIds(gameIds);
    }
    
    /**
     * Remove listeners when repository is no longer needed
     */
    public void removeListeners() {
        if (gamesListener != null) {
            gamesListener.remove();
            gamesListener = null;
        }
        if (approvedGamesListener != null) {
            approvedGamesListener.remove();
            approvedGamesListener = null;
        }
        // Remove all gameData listeners
        for (com.google.firebase.firestore.ListenerRegistration listener : gameDataListeners.values()) {
            listener.remove();
        }
        gameDataListeners.clear();
    }

    /**
     * Clears in-memory game list state when the games collection has no documents (one-shot query / Review).
     * Prevents stale in-flight {@link #fetchGameData} callbacks from repopulating the UI from old {@link #gameIdsOrder}.
     */
    private void resetGameListStateForEmptyQuery() {
        gameItemsMap.clear();
        gameIdsOrder.clear();
        gameItemsLiveData.setValue(new ArrayList<>());
    }

    /**
     * Removes per-game listeners and clears state when the realtime games snapshot is empty (Dashboard).
     */
    private void clearGameDataListenersAndResetGameListState() {
        for (com.google.firebase.firestore.ListenerRegistration listener : gameDataListeners.values()) {
            listener.remove();
        }
        gameDataListeners.clear();
        gameItemsMap.clear();
        gameIdsOrder.clear();
        gameItemsLiveData.setValue(new ArrayList<>());
    }

    /**
     * Load game data with real-time listeners (for Dashboard)
     */
    private void loadGameDataForIdsWithListeners(List<String> gameIds) {
        // Update the game IDs order
        gameIdsOrder = new ArrayList<>(gameIds);
        
        // Remove listeners and data for games that are no longer in the list
        java.util.Set<String> currentGameIds = new java.util.HashSet<>(gameIds);
        java.util.Iterator<java.util.Map.Entry<String, com.google.firebase.firestore.ListenerRegistration>> iterator = 
            gameDataListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<String, com.google.firebase.firestore.ListenerRegistration> entry = iterator.next();
            if (!currentGameIds.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
                gameItemsMap.remove(entry.getKey());
                System.out.println("Removed listener and data for gameData: " + entry.getKey());
            }
        }
        
        // Setup listeners for each game
        for (String gameId : gameIds) {
            setupGameDataListener(gameId);
        }
        
        // Trigger initial update
        updateGameItemsList();
    }

    /**
     * Load game data with one-time fetch (for Review screen)
     */
    private void loadGameDataForIds(List<String> gameIds) {
        // Update the game IDs order
        gameIdsOrder = new ArrayList<>(gameIds);

        // Clear previous game items
        gameItemsMap.clear();
        // Push empty list immediately so the UI does not keep the previous LiveData value when all fetches resolve to "missing".
        updateGameItemsList();

        // Fetch game data for each game
        for (String gameId : gameIds) {
            fetchGameData(gameId);
        }
    }

    /** Drops a row when server no longer has gameData / games for this id (e.g. deleted while cache still listed it). */
    private void removeStaleGameRowForReview(String gameId) {
        if (gameId == null) {
            return;
        }
        gameItemsMap.remove(gameId);
        updateGameItemsList();
    }

    private void fetchGameData(String gameId) {
        System.out.println("Fetching game data for: " + gameId);
        com.google.firebase.firestore.DocumentReference dataRef = db.collection(GAME_DATA_COLLECTION).document(gameId);
        dataRef.get(Source.SERVER)
                .addOnSuccessListener(snapshot -> onGameDataSnapshotForReviewFetch(gameId, snapshot))
                .addOnFailureListener(error -> {
                    System.out.println("GameData server fetch failed for " + gameId + ", fallback: " + error.getMessage());
                    dataRef.get()
                            .addOnSuccessListener(snapshot -> onGameDataSnapshotForReviewFetch(gameId, snapshot))
                            .addOnFailureListener(error2 ->
                                    System.out.println("Error fetching gameData for " + gameId + ": " + error2.getMessage()));
                });
    }

    private void onGameDataSnapshotForReviewFetch(String gameId, DocumentSnapshot documentSnapshot) {
        if (!gameIdsOrder.contains(gameId)) {
            return;
        }
        if (documentSnapshot == null || !documentSnapshot.exists()) {
            System.out.println("GameData document doesn't exist for " + gameId);
            removeStaleGameRowForReview(gameId);
            return;
        }
        try {
            GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
            if (gameDataWrapper == null || gameDataWrapper.getData() == null) {
                removeStaleGameRowForReview(gameId);
                return;
            }
            GameData gameData = gameDataWrapper.getData();
            attachReviewGameAuthFetch(gameId, gameDataWrapper, gameData);
        } catch (Exception e) {
            System.out.println("Error deserializing game data for " + gameId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void attachReviewGameAuthFetch(String gameId, GameDataWrapper gameDataWrapper, GameData gameData) {
        com.google.firebase.firestore.DocumentReference authRef = db.collection(GAMES_COLLECTION).document(gameId);
        authRef.get(Source.SERVER)
                .addOnSuccessListener(authSnapshot -> onAuthSnapshotForReviewFetch(gameId, gameDataWrapper, gameData, authSnapshot))
                .addOnFailureListener(e -> authRef.get()
                        .addOnSuccessListener(authSnapshot -> onAuthSnapshotForReviewFetch(gameId, gameDataWrapper, gameData, authSnapshot))
                        .addOnFailureListener(e2 ->
                                System.out.println("Error fetching games auth for " + gameId + ": " + e2.getMessage())));
    }

    private void onAuthSnapshotForReviewFetch(String gameId, GameDataWrapper gameDataWrapper, GameData gameData,
                                                DocumentSnapshot authSnapshot) {
        if (!gameIdsOrder.contains(gameId)) {
            return;
        }
        if (authSnapshot == null || !authSnapshot.exists()) {
            removeStaleGameRowForReview(gameId);
            return;
        }
        GameAuth gameAuth = authSnapshot.toObject(GameAuth.class);
        String pin = gameAuth != null ? gameAuth.getPin() : "0000";
        String creatorName = gameAuth != null ? gameAuth.getCreatorName() : null;
        String creatorUserId = gameAuth != null ? gameAuth.getCreatorUserId() : null;
        String gameDisplayName = gameDisplayNameFromAuth(gameAuth);

        com.google.firebase.Timestamp createdAt = gameAuth != null ? gameAuth.getCreatedAt() : gameDataWrapper.getLastUpdated();

        if (creatorUserId != null && !creatorUserId.isEmpty()) {
            db.collection("appUser")
                    .document(creatorUserId)
                    .get()
                    .addOnSuccessListener(userSnapshot -> {
                        if (!gameIdsOrder.contains(gameId)) {
                            return;
                        }
                        String creatorPhotoUrl = null;
                        if (userSnapshot.exists()) {
                            creatorPhotoUrl = userSnapshot.getString("photoUrl");
                        }
                        GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, creatorPhotoUrl, creatorUserId, gameDisplayName);
                        putGameItemIfStillInLoad(gameId, gameItem);
                    })
                    .addOnFailureListener(e -> {
                        if (!gameIdsOrder.contains(gameId)) {
                            return;
                        }
                        GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, creatorUserId, gameDisplayName);
                        putGameItemIfStillInLoad(gameId, gameItem);
                    });
        } else {
            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, null, gameDisplayName);
            putGameItemIfStillInLoad(gameId, gameItem);
        }
    }
    
    /**
     * Setup real-time listener for a specific game (for Dashboard)
     */
    private void setupGameDataListener(String gameId) {
        // If listener already exists, don't create duplicate
        if (gameDataListeners.containsKey(gameId)) {
            System.out.println("Listener already exists for gameData: " + gameId);
            return;
        }
        
        System.out.println("Setting up real-time listener for gameData: " + gameId);
        
        com.google.firebase.firestore.ListenerRegistration listener = db.collection(GAME_DATA_COLLECTION)
                .document(gameId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        System.out.println("Error listening to gameData for " + gameId + ": " + error.getMessage());
                        return;
                    }
                    
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        try {
                            GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                            if (gameDataWrapper != null && gameDataWrapper.getData() != null) {
                                GameData gameData = gameDataWrapper.getData();
                                // Also get the auth data for PIN
                                db.collection(GAMES_COLLECTION)
                                        .document(gameId)
                                        .get()
                                        .addOnSuccessListener(authSnapshot -> {
                                            GameAuth gameAuth = authSnapshot.toObject(GameAuth.class);
                                            String pin = gameAuth != null ? gameAuth.getPin() : "0000";
                                            String creatorName = gameAuth != null ? gameAuth.getCreatorName() : null;
                                            String creatorUserId = gameAuth != null ? gameAuth.getCreatorUserId() : null;
                                            String gameDisplayName = gameDisplayNameFromAuth(gameAuth);
                                            
                                            // Use createdAt from games collection instead of lastUpdated from gameData collection
                                            com.google.firebase.Timestamp createdAt = gameAuth != null ? gameAuth.getCreatedAt() : gameDataWrapper.getLastUpdated();
                                            
                                            // Fetch creator's photo URL from appUser collection
                                            if (creatorUserId != null && !creatorUserId.isEmpty()) {
                                                db.collection("appUser")
                                                        .document(creatorUserId)
                                                        .get()
                                                        .addOnSuccessListener(userSnapshot -> {
                                                            String creatorPhotoUrl = null;
                                                            if (userSnapshot.exists()) {
                                                                creatorPhotoUrl = userSnapshot.getString("photoUrl");
                                                            }
                                                            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, creatorPhotoUrl, creatorUserId, gameDisplayName);
                                                            
                                                            if (gameItem != null) {
                                                                gameItemsMap.put(gameId, gameItem);
                                                                
                                                                // Check if this is a new game
                                                                checkAndNotifyNewGame(gameItem);
                                                                
                                                                updateGameItemsList();
                                                            }
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            // If fetching photo fails, create game item without photo
                                                            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, creatorUserId, gameDisplayName);
                                                            if (gameItem != null) {
                                                                gameItemsMap.put(gameId, gameItem);
                                                                
                                                                // Check if this is a new game
                                                                checkAndNotifyNewGame(gameItem);
                                                                
                                                                updateGameItemsList();
                                                            }
                                                        });
                                            } else {
                                                // No creator user ID, create game item without photo
                                                GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, null, gameDisplayName);
                                                if (gameItem != null) {
                                                    gameItemsMap.put(gameId, gameItem);
                                                    
                                                    // Check if this is a new game
                                                    checkAndNotifyNewGame(gameItem);
                                                    
                                                    updateGameItemsList();
                                                }
                                            }
                                        });
                            }
                        } catch (Exception e) {
                            System.out.println("Error deserializing game data for " + gameId + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("GameData document doesn't exist yet for " + gameId);
                    }
                });
        
        gameDataListeners.put(gameId, listener);
    }

    /** Ignores late one-shot fetch callbacks after {@link #gameIdsOrder} no longer includes the id. */
    private void putGameItemIfStillInLoad(String gameId, GameItem gameItem) {
        if (gameItem == null || !gameIdsOrder.contains(gameId)) {
            return;
        }
        gameItemsMap.put(gameId, gameItem);
        updateGameItemsList();
    }
    
    private void updateGameItemsList() {
        List<GameItem> gameItems = new ArrayList<>();
        // Maintain the order from gameIdsOrder
        for (String gameId : gameIdsOrder) {
            GameItem item = gameItemsMap.get(gameId);
            if (item != null) {
                gameItems.add(item);
            }
        }
        System.out.println("Updating game items list with " + gameItems.size() + " games");
        gameItemsLiveData.setValue(gameItems);
    }

    private static String gameDisplayNameFromAuth(GameAuth auth) {
        if (auth == null || auth.getDisplayName() == null) {
            return "";
        }
        return auth.getDisplayName();
    }

    private GameItem convertToGameItem(String gameId, String pin, GameData gameData, com.google.firebase.Timestamp createdAt, String creatorName, String creatorPhotoUrl, String creatorUserId, String gameDisplayName) {
        // Calculate total score
        int totalScore = gameData.getTotalScore();
        
        // Format point value (convert from double to string with 2 decimal places)
        String pointValueStr = String.format("%.2f", gameData.getPointValue());
        
        // Format GST percentage
        String gstPercentageStr = String.format("%.0f", gameData.getGstPercent());
        
        // Calculate GST amount
        double gstAmount = gameData.getGstAmount();
        String gstAmountStr = String.format("%.0f", gstAmount);
        
        // Format creation date (from timestamp)
        String creationDateTime = formatTimestamp(createdAt);
        
        // Get game status (no need to filter "Approved" status since approved games are deleted from games collection)
        String gameStatus = gameData.getGameStatus();
        
        // Get number of players
        String numberOfPlayers = String.valueOf(gameData.getNumPlayers());

        GameItem gameItem = new GameItem(
                gameId,
                pin,
                String.valueOf(totalScore),
                pointValueStr,
                creationDateTime,
                gameStatus,
                numberOfPlayers,
                gstPercentageStr,
                gstAmountStr,
                creatorName,
                gameData.getPlayers()
        );
        
        // Set creator photo URL and user ID
        gameItem.setCreatorPhotoUrl(creatorPhotoUrl);
        gameItem.setCreatorUserId(creatorUserId);
        gameItem.setGameDisplayName(gameDisplayName != null ? gameDisplayName : "");
        
        return gameItem;
    }

    private String formatTimestamp(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) {
            return "Unknown";
        }
        
        java.util.Date date = timestamp.toDate();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }
    
    /**
     * Check if a game is new
     */
    private void checkAndNotifyNewGame(GameItem game) {
        // Skip if no context available or game is completed
        if (appContext == null || game.isCompleted() || "Completed".equals(game.getGameStatus())) {
            return;
        }
        
        // Check if this is a new game we haven't seen before
        if (!seenGameIds.contains(game.getGameId())) {
            android.util.Log.d("GameRepository", "New game detected in repository: " + game.getGameId() + 
                " created by: " + game.getCreatorName() + " (ID: " + game.getCreatorUserId() + ")");
            
            // Mark as seen
            seenGameIds.add(game.getGameId());
            
            android.util.Log.d("GameRepository", "New game detected: " + game.getGameId());
        }
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

    public void deleteGame(String gameId) {
        // Delete from both collections
        db.collection(GAMES_COLLECTION).document(gameId).delete()
                .addOnSuccessListener(aVoid -> {
                    db.collection(GAME_DATA_COLLECTION).document(gameId).delete()
                            .addOnSuccessListener(aVoid1 -> {
                                // Reload the list after deletion
                                loadAllGames();
                            })
                            .addOnFailureListener(e -> {
                                errorLiveData.setValue("Failed to delete game data: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to delete game: " + e.getMessage());
                });
    }

    public void updateGameStatus(String gameId, String newStatus) {
        // Update the game status in the original gameData collection
        db.collection(GAME_DATA_COLLECTION)
                .document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                        if (gameDataWrapper != null && gameDataWrapper.getData() != null) {
                            GameData gameData = gameDataWrapper.getData();
                            gameData.setGameStatus(newStatus);
                            
                            // Update the wrapper with new timestamp
                            gameDataWrapper.setLastUpdated(com.google.firebase.Timestamp.now());
                            gameDataWrapper.setData(gameData);
                            
                            // Save back to Firestore
                            db.collection(GAME_DATA_COLLECTION)
                                    .document(gameId)
                                    .set(gameDataWrapper)
                                    .addOnSuccessListener(aVoid -> {
                                        // Reload the games list to reflect the change
                                        loadAllGames();
                                    })
                                    .addOnFailureListener(e -> {
                                        errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                });
    }

    /**
     * Update point value and contribution (GST) percent for a game. Contribution amount in the UI
     * is derived on reload via {@link GameData#getGstAmount()}.
     *
     * @param onSuccess optional; runs on the main thread after Firestore write succeeds (before list refresh completes).
     */
    public void updateGameEconomics(String gameId, double pointValue, double gstPercent, Runnable onSuccess) {
        if (gameId == null || gameId.isEmpty()) {
            errorLiveData.setValue("Cannot update game: missing game id");
            return;
        }
        db.collection(GAME_DATA_COLLECTION)
                .document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        errorLiveData.setValue("Game data not found");
                        return;
                    }
                    GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                    if (gameDataWrapper == null || gameDataWrapper.getData() == null) {
                        errorLiveData.setValue("Failed to load game data for update");
                        return;
                    }
                    GameData gameData = gameDataWrapper.getData();
                    gameData.setPointValue(pointValue);
                    gameData.setGstPercent(gstPercent);
                    gameDataWrapper.setLastUpdated(com.google.firebase.Timestamp.now());
                    gameDataWrapper.setData(gameData);

                    db.collection(GAME_DATA_COLLECTION)
                            .document(gameId)
                            .set(gameDataWrapper)
                            .addOnSuccessListener(aVoid -> {
                                loadAllGames();
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            })
                            .addOnFailureListener(e ->
                                    errorLiveData.setValue("Failed to update game: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        errorLiveData.setValue("Failed to load game data: " + e.getMessage()));
    }

    public void approveGame(GameItem gameItem) {
        approveGame(gameItem, null);
    }

    /**
     * @param onAfterFullSuccess optional; runs after game is written to approvedGames and removed from games/gameData
     */
    public void approveGame(GameItem gameItem, Runnable onAfterFullSuccess) {
        // Get the original game data to extract player information
        db.collection(GAME_DATA_COLLECTION)
                .document(gameItem.getGameId())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        try {
                            GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                            if (gameDataWrapper != null && gameDataWrapper.getData() != null) {
                                GameData gameData = gameDataWrapper.getData();
                                
                                // Create simplified player scores map (name -> total score)
                                Map<String, Integer> playerScores = new HashMap<>();
                                if (gameData.getPlayers() != null) {
                                    for (Player player : gameData.getPlayers()) {
                                        playerScores.put(player.getName(), player.getTotalScore());
                                    }
                                }
                                
                                // Create approved game data without PIN
                                ApprovedGameData approvedGameData = new ApprovedGameData(
                                        gameItem.getGameId(),
                                        gameData.getNumPlayers(),
                                        gameData.getPointValue(),
                                        gameData.getGstPercent(),
                                        playerScores,
                                        com.google.firebase.Timestamp.now(),
                                        gameDataWrapper.getVersion(),
                                        gameItem.getGstAmount(),
                                        gameItem.getGameStatus(),
                                        gameItem.getCreationDateTime()
                                );
                                
                                // Save to approved games collection
                                db.collection(APPROVED_GAMES_COLLECTION)
                                        .document(gameItem.getGameId())
                                        .set(approvedGameData)
                                        .addOnSuccessListener(aVoid -> {
                                            deleteApprovedGame(gameItem.getGameId(), onAfterFullSuccess);
                                        })
                                        .addOnFailureListener(e -> {
                                            errorLiveData.setValue("Failed to approve game: " + e.getMessage());
                                        });
                            } else {
                                errorLiveData.setValue("Failed to load game data for approval");
                            }
                        } catch (Exception e) {
                            errorLiveData.setValue("Error processing game data: " + e.getMessage());
                        }
                    } else {
                        errorLiveData.setValue("Game data not found for approval");
                    }
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to load game data: " + e.getMessage());
                });
    }

    /**
     * Approve every completed game in the list, one after another (avoids overlapping reloads).
     */
    public void approveAllCompletedGames(List<GameItem> games, Runnable onAllComplete) {
        if (games == null || games.isEmpty()) {
            if (onAllComplete != null) {
                onAllComplete.run();
            }
            return;
        }
        List<GameItem> completed = new ArrayList<>();
        for (GameItem g : games) {
            if (g != null && g.isCompleted()) {
                completed.add(g);
            }
        }
        if (completed.isEmpty()) {
            errorLiveData.setValue("No completed games to approve.");
            return;
        }
        approveSequentially(completed, 0, onAllComplete);
    }

    private void approveSequentially(List<GameItem> list, int index, Runnable onAllComplete) {
        if (index >= list.size()) {
            if (onAllComplete != null) {
                onAllComplete.run();
            }
            return;
        }
        approveGame(list.get(index), () -> approveSequentially(list, index + 1, onAllComplete));
    }

    private void deleteApprovedGame(String gameId, Runnable onAfterSuccess) {
        // Delete from both collections after successful approval
        db.collection(GAMES_COLLECTION).document(gameId).delete()
                .addOnSuccessListener(aVoid -> {
                    // After deleting from games collection, delete from gameData collection
                    db.collection(GAME_DATA_COLLECTION).document(gameId).delete()
                            .addOnSuccessListener(aVoid1 -> {
                                // Reload the list after deletion
                                loadAllGames();
                                // Reload approved games to update total GST
                                loadApprovedGames();
                                System.out.println("Successfully approved and deleted game: " + gameId);
                                if (onAfterSuccess != null) {
                                    onAfterSuccess.run();
                                }
                            })
                            .addOnFailureListener(e -> {
                                errorLiveData.setValue("Failed to delete approved game data: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to delete approved game: " + e.getMessage());
                });
    }

    private void updateGameStatusInOriginal(String gameId, String newStatus) {
        // Update the game status in the original gameData collection
        db.collection(GAME_DATA_COLLECTION)
                .document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                        if (gameDataWrapper != null && gameDataWrapper.getData() != null) {
                            GameData gameData = gameDataWrapper.getData();
                            gameData.setGameStatus(newStatus);
                            
                            // Update the wrapper with new timestamp
                            gameDataWrapper.setLastUpdated(com.google.firebase.Timestamp.now());
                            gameDataWrapper.setData(gameData);
                            
                            // Save back to Firestore
                            db.collection(GAME_DATA_COLLECTION)
                                    .document(gameId)
                                    .set(gameDataWrapper)
                                    .addOnSuccessListener(aVoid -> {
                                        // Reload the games list to reflect the change
                                        loadAllGames();
                                    })
                                    .addOnFailureListener(e -> {
                                        errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                });
    }

    /**
     * Load approved games with real-time listener (for Dashboard)
     */
    public void loadApprovedGamesWithRealtimeListener() {
        System.out.println("GameRepository: Setting up real-time listener for approved games collection");
        
        // Remove existing listener if any
        if (approvedGamesListener != null) {
            approvedGamesListener.remove();
        }
        
        // Set up real-time listener for approved games collection
        approvedGamesListener = db.collection(APPROVED_GAMES_COLLECTION)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        System.out.println("GameRepository: Error listening to approved games: " + error.getMessage());
                        errorLiveData.setValue("Failed to load approved games: " + error.getMessage());
                        totalApprovedGstLiveData.setValue(0.0);
                        approvedGamesCountLiveData.setValue(0);
                        return;
                    }
                    
                    if (querySnapshot != null) {
                        double totalGst = 0.0;
                        int approvedCount = 0;
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            try {
                                ApprovedGameData approvedGame = document.toObject(ApprovedGameData.class);
                                if (approvedGame != null) {
                                    totalGst += approvedGame.getGstAmountAsDouble();
                                    approvedCount++;
                                }
                            } catch (Exception e) {
                                System.out.println("Error parsing approved game: " + e.getMessage());
                            }
                        }
                        totalApprovedGstLiveData.setValue(totalGst);
                        approvedGamesCountLiveData.setValue(approvedCount);
                        System.out.println("Total approved GST amount: ₹" + String.format("%.0f", totalGst));
                        System.out.println("Total approved games count: " + approvedCount);
                    }
                });
    }
    
    /**
     * Load approved games with one-time fetch (for Review screen - manual refresh only)
     */
    public void loadApprovedGames() {
        System.out.println("GameRepository: Fetching approved games collection (manual refresh)");
        
        // One-time fetch for approved games collection
        db.collection(APPROVED_GAMES_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null) {
                        double totalGst = 0.0;
                        int approvedCount = 0;
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            try {
                                ApprovedGameData approvedGame = document.toObject(ApprovedGameData.class);
                                if (approvedGame != null) {
                                    totalGst += approvedGame.getGstAmountAsDouble();
                                    approvedCount++;
                                }
                            } catch (Exception e) {
                                System.out.println("Error parsing approved game: " + e.getMessage());
                            }
                        }
                        totalApprovedGstLiveData.setValue(totalGst);
                        approvedGamesCountLiveData.setValue(approvedCount);
                        System.out.println("Total approved GST amount: ₹" + String.format("%.0f", totalGst));
                        System.out.println("Total approved games count: " + approvedCount);
                    }
                })
                .addOnFailureListener(error -> {
                    System.out.println("GameRepository: Error fetching approved games: " + error.getMessage());
                    errorLiveData.setValue("Failed to load approved games: " + error.getMessage());
                    totalApprovedGstLiveData.setValue(0.0);
                    approvedGamesCountLiveData.setValue(0);
                });
    }

    /**
     * Loads pre-aggregated month documents (one read per month doc, no composite indexes).
     */
    public void loadReportsFromSavedSummaries() {
        db.collection(APPROVED_GAMES_REPORT_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Pair<String, MonthlyPointValueReport>> tagged = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            ApprovedGamesReportMonth month = document.toObject(ApprovedGamesReportMonth.class);
                            if (month != null) {
                                tagged.add(new Pair<>(document.getId(), month.toMonthlyPointValueReport()));
                            }
                        } catch (Exception e) {
                            System.out.println("Error parsing approvedGamesReport doc: " + e.getMessage());
                        }
                    }
                    tagged.sort((a, b) -> b.first.compareTo(a.first));
                    List<MonthlyPointValueReport> out = new ArrayList<>();
                    for (Pair<String, MonthlyPointValueReport> p : tagged) {
                        out.add(p.second);
                    }
                    reportsSummariesLiveData.setValue(out);
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to load reports: " + e.getMessage());
                    reportsSummariesLiveData.setValue(new ArrayList<>());
                });
    }

    /**
     * Rebuilds {@code approvedGamesReport/{yyyy-MM}} for the selected calendar month.
     * Uses a full collection read without {@code orderBy} (no composite index), then filters in memory
     * by {@link ReportAggregator#yearMonthKey(ApprovedGameData)} so games without {@code approvedAt}
     * still align with month grouping.
     */
    public void rebuildApprovedGamesReportForMonth(int year, int monthZeroBased,
                                                   Runnable onSuccess, Consumer<String> onFailure) {
        if (monthZeroBased < Calendar.JANUARY || monthZeroBased > Calendar.DECEMBER) {
            if (onFailure != null) {
                onFailure.accept("Invalid report month");
            }
            return;
        }
        final String yyyyMm = String.format(Locale.US, "%04d-%02d", year, monthZeroBased + 1);
        db.collection(APPROVED_GAMES_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ApprovedGameData> monthGames = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            ApprovedGameData g = document.toObject(ApprovedGameData.class);
                            if (g != null && yyyyMm.equals(ReportAggregator.yearMonthKey(g))) {
                                monthGames.add(g);
                            }
                        } catch (Exception e) {
                            System.out.println("Error parsing approved game: " + e.getMessage());
                        }
                    }
                    MonthlyPointValueReport report = ReportAggregator.buildMonthlyPointValueReport(yyyyMm, monthGames);
                    ApprovedGamesReportMonth doc = new ApprovedGamesReportMonth(
                            report.getMonthYear(),
                            report.getPointValueReports(),
                            Timestamp.now());
                    db.collection(APPROVED_GAMES_REPORT_COLLECTION)
                            .document(yyyyMm)
                            .set(doc)
                            .addOnSuccessListener(aVoid -> {
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (onFailure != null) {
                                    onFailure.accept(e.getMessage());
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (onFailure != null) {
                        onFailure.accept(e.getMessage());
                    }
                });
    }

    /**
     * Rebuilds {@code approvedGamesReport/{yyyy-MM}} for the current calendar month.
     */
    public void rebuildApprovedGamesReportForCurrentMonth(Runnable onSuccess, Consumer<String> onFailure) {
        Calendar cal = Calendar.getInstance();
        rebuildApprovedGamesReportForMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                onSuccess, onFailure);
    }

    /**
     * TODO: Remove this after initial backfill — reads all {@code approvedGames} and writes one summary
     * document per calendar month (batched, max 450 writes per batch).
     */
    public void rebuildAllApprovedGamesReports(Runnable onSuccess, Consumer<String> onFailure) {
        db.collection(APPROVED_GAMES_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ApprovedGameData> all = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            ApprovedGameData g = document.toObject(ApprovedGameData.class);
                            if (g != null) {
                                all.add(g);
                            }
                        } catch (Exception e) {
                            System.out.println("Error parsing approved game: " + e.getMessage());
                        }
                    }
                    Map<String, List<ApprovedGameData>> byMonth = new HashMap<>();
                    for (ApprovedGameData g : all) {
                        String key = ReportAggregator.yearMonthKey(g);
                        if (key == null) {
                            continue;
                        }
                        byMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(g);
                    }
                    if (byMonth.isEmpty()) {
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        return;
                    }
                    List<Map.Entry<String, List<ApprovedGameData>>> entries = new ArrayList<>(byMonth.entrySet());
                    commitReportsBatch(entries, 0, onSuccess, onFailure);
                })
                .addOnFailureListener(e -> {
                    if (onFailure != null) {
                        onFailure.accept(e.getMessage());
                    }
                });
    }

    private void commitReportsBatch(List<Map.Entry<String, List<ApprovedGameData>>> entries, int start,
            Runnable onSuccess, Consumer<String> onFailure) {
        if (start >= entries.size()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }
        int end = Math.min(start + 450, entries.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            Map.Entry<String, List<ApprovedGameData>> e = entries.get(i);
            MonthlyPointValueReport report = ReportAggregator.buildMonthlyPointValueReport(e.getKey(), e.getValue());
            ApprovedGamesReportMonth doc = new ApprovedGamesReportMonth(
                    report.getMonthYear(),
                    report.getPointValueReports(),
                    Timestamp.now());
            batch.set(db.collection(APPROVED_GAMES_REPORT_COLLECTION).document(e.getKey()), doc);
        }
        int nextStart = end;
        batch.commit()
                .addOnSuccessListener(aVoid -> commitReportsBatch(entries, nextStart, onSuccess, onFailure))
                .addOnFailureListener(e -> {
                    if (onFailure != null) {
                        onFailure.accept(e.getMessage());
                    }
                });
    }

}
