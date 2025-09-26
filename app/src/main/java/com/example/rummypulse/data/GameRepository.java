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
        System.out.println("GameRepository: Starting to load all games from Firebase");
        
        // First, get all game IDs from the games collection, ordered by newest first
        db.collection(GAMES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    System.out.println("GameRepository: Successfully queried games collection, found " + querySnapshot.size() + " documents");
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
                })
                .addOnFailureListener(e -> {
                    System.out.println("GameRepository: Failed to load games: " + e.getMessage());
                    errorLiveData.setValue("Failed to load games: " + e.getMessage());
                });
    }

    private void loadGameDataForIds(List<String> gameIds) {
        // Use arrays to maintain exact order and track completion
        GameItem[] gameItemsArray = new GameItem[gameIds.size()];
        boolean[] completedFlags = new boolean[gameIds.size()];
        int[] completedCount = {0};
        
        for (int i = 0; i < gameIds.size(); i++) {
            final String gameId = gameIds.get(i);
            final int index = i;
            
            db.collection(GAME_DATA_COLLECTION)
                    .document(gameId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
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
                                            
                                            // Use createdAt from games collection instead of lastUpdated from gameData collection
                                            com.google.firebase.Timestamp createdAt = gameAuth != null ? gameAuth.getCreatedAt() : gameDataWrapper.getLastUpdated();
                                            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt);
                                            if (gameItem != null) {
                                                gameItemsArray[index] = gameItem;
                                            }
                                            completedFlags[index] = true;
                                            
                                            completedCount[0]++;
                                            if (completedCount[0] == gameIds.size()) {
                                                // Convert array to list maintaining exact order
                                                List<GameItem> gameItems = new ArrayList<>();
                                                for (GameItem item : gameItemsArray) {
                                                    if (item != null) {
                                                        gameItems.add(item);
                                                    }
                                                }
                                                gameItemsLiveData.setValue(gameItems);
                                            }
                                        });
                            } else {
                                completedFlags[index] = true;
                                completedCount[0]++;
                                if (completedCount[0] == gameIds.size()) {
                                    List<GameItem> gameItems = new ArrayList<>();
                                    for (GameItem item : gameItemsArray) {
                                        if (item != null) {
                                            gameItems.add(item);
                                        }
                                    }
                                    gameItemsLiveData.setValue(gameItems);
                                }
                            }
                            } catch (Exception e) {
                                System.out.println("Error deserializing game data for " + gameId + ": " + e.getMessage());
                                e.printStackTrace();
                                completedFlags[index] = true;
                                completedCount[0]++;
                                if (completedCount[0] == gameIds.size()) {
                                    List<GameItem> gameItems = new ArrayList<>();
                                    for (GameItem item : gameItemsArray) {
                                        if (item != null) {
                                            gameItems.add(item);
                                        }
                                    }
                                    gameItemsLiveData.setValue(gameItems);
                                }
                            }
                        } else {
                            completedFlags[index] = true;
                            completedCount[0]++;
                            if (completedCount[0] == gameIds.size()) {
                                List<GameItem> gameItems = new ArrayList<>();
                                for (GameItem item : gameItemsArray) {
                                    if (item != null) {
                                        gameItems.add(item);
                                    }
                                }
                                gameItemsLiveData.setValue(gameItems);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        completedFlags[index] = true;
                        completedCount[0]++;
                        if (completedCount[0] == gameIds.size()) {
                            List<GameItem> gameItems = new ArrayList<>();
                            for (GameItem item : gameItemsArray) {
                                if (item != null) {
                                    gameItems.add(item);
                                }
                            }
                            gameItemsLiveData.setValue(gameItems);
                        }
                    });
        }
    }

    private GameItem convertToGameItem(String gameId, String pin, GameData gameData, com.google.firebase.Timestamp createdAt) {
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
        
        // Get game status
        String gameStatus = gameData.getGameStatus();
        
        // Skip approved games - they should not appear in active games list
        if ("Approved".equals(gameStatus)) {
            System.out.println("Skipping approved game: " + gameId);
            return null;
        }
        
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
        // Load all approved games and calculate total GST amount
        db.collection(APPROVED_GAMES_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
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
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to load approved games: " + e.getMessage());
                    totalApprovedGstLiveData.setValue(0.0);
                    approvedGamesCountLiveData.setValue(0);
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
