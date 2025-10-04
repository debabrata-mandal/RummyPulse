package com.example.rummypulse.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameRepository {
    private static final String GAMES_COLLECTION = "games";
    private static final String GAME_DATA_COLLECTION = "gameData";
    private static final String APPROVED_GAMES_COLLECTION = "approvedGames";
    
    private FirebaseFirestore db;
    private MutableLiveData<List<GameItem>> gameItemsLiveData;
    private MutableLiveData<String> errorLiveData;
    private MutableLiveData<Double> totalApprovedGstLiveData;
    private MutableLiveData<Integer> approvedGamesCountLiveData;
    private MutableLiveData<List<ApprovedGameData>> approvedGamesForReportsLiveData;
    
    // Firestore listeners for real-time updates
    private com.google.firebase.firestore.ListenerRegistration gamesListener;
    private com.google.firebase.firestore.ListenerRegistration approvedGamesListener;
    private java.util.Map<String, com.google.firebase.firestore.ListenerRegistration> gameDataListeners = new java.util.HashMap<>();
    
    // Store game items and game IDs order for maintaining consistency
    private java.util.Map<String, GameItem> gameItemsMap = new java.util.HashMap<>();
    private List<String> gameIdsOrder = new ArrayList<>();

    public GameRepository() {
        db = FirebaseFirestore.getInstance();
        gameItemsLiveData = new MutableLiveData<>();
        errorLiveData = new MutableLiveData<>();
        totalApprovedGstLiveData = new MutableLiveData<>();
        approvedGamesCountLiveData = new MutableLiveData<>();
        approvedGamesForReportsLiveData = new MutableLiveData<>();
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

    public LiveData<List<ApprovedGameData>> getApprovedGamesForReports() {
        return approvedGamesForReportsLiveData;
    }

    public void loadAllGames() {
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
                            gameItemsLiveData.setValue(new ArrayList<>());
                            return;
                        }
                        
                        System.out.println("GameRepository: Found " + gameIds.size() + " game IDs, loading game data");
                        // Now load game data for each game ID
                        loadGameDataForIds(gameIds);
                    }
                });
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

    private void loadGameDataForIds(List<String> gameIds) {
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
                                            
                                            // Use createdAt from games collection instead of lastUpdated from gameData collection
                                            com.google.firebase.Timestamp createdAt = gameAuth != null ? gameAuth.getCreatedAt() : gameDataWrapper.getLastUpdated();
                                            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName);
                                            
                                            if (gameItem != null) {
                                                gameItemsMap.put(gameId, gameItem);
                                                updateGameItemsList();
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

    private GameItem convertToGameItem(String gameId, String pin, GameData gameData, com.google.firebase.Timestamp createdAt, String creatorName) {
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

        return new GameItem(
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
    }

    private String formatTimestamp(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) {
            return "Unknown";
        }
        
        java.util.Date date = timestamp.toDate();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
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

    public void approveGame(GameItem gameItem) {
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
                                Map<String, Integer> playerScores = new java.util.HashMap<>();
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
                                            // Delete from both original collections after successful approval
                                            deleteApprovedGame(gameItem.getGameId());
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

    private void deleteApprovedGame(String gameId) {
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

    public void loadApprovedGames() {
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

    public void loadApprovedGamesForReports() {
        // Load all approved games with full data for reports
        db.collection(APPROVED_GAMES_COLLECTION)
                .orderBy("approvedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ApprovedGameData> approvedGames = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            ApprovedGameData approvedGame = document.toObject(ApprovedGameData.class);
                            if (approvedGame != null) {
                                approvedGames.add(approvedGame);
                            }
                        } catch (Exception e) {
                            System.out.println("Error parsing approved game for reports: " + e.getMessage());
                        }
                    }
                    approvedGamesForReportsLiveData.setValue(approvedGames);
                    System.out.println("Loaded " + approvedGames.size() + " approved games for reports");
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to load approved games for reports: " + e.getMessage());
                    approvedGamesForReportsLiveData.setValue(new ArrayList<>());
                });
    }
}
