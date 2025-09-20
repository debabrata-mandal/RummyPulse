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

public class GameRepository {
    private static final String GAMES_COLLECTION = "games";
    private static final String GAME_DATA_COLLECTION = "gameData";
    
    private FirebaseFirestore db;
    private MutableLiveData<List<GameItem>> gameItemsLiveData;
    private MutableLiveData<String> errorLiveData;

    public GameRepository() {
        db = FirebaseFirestore.getInstance();
        gameItemsLiveData = new MutableLiveData<>();
        errorLiveData = new MutableLiveData<>();
    }

    public LiveData<List<GameItem>> getGameItems() {
        return gameItemsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public void loadAllGames() {
        // First, get all game IDs from the games collection, ordered by newest first
        db.collection(GAMES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> gameIds = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        gameIds.add(document.getId());
                    }
                    
                    if (gameIds.isEmpty()) {
                        gameItemsLiveData.setValue(new ArrayList<>());
                        return;
                    }
                    
                    // Now load game data for each game ID
                    loadGameDataForIds(gameIds);
                })
                .addOnFailureListener(e -> {
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
                                            
                                            GameItem gameItem = convertToGameItem(gameId, pin, gameData, gameDataWrapper.getLastUpdated());
                                            gameItemsArray[index] = gameItem;
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

    private GameItem convertToGameItem(String gameId, String pin, GameData gameData, com.google.firebase.Timestamp lastUpdated) {
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
        String creationDateTime = formatTimestamp(lastUpdated);
        
        // Get game status
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
                gstAmountStr
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
        // This would update a status field in the gameData collection
        // For now, we'll just reload the data
        loadAllGames();
    }
}
