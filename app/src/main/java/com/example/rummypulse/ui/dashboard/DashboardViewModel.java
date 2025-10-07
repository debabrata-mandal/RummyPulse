package com.example.rummypulse.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.ui.home.GameItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DashboardViewModel extends ViewModel {

    private final GameRepository gameRepository;
    private final MutableLiveData<List<GameItem>> mInProgressGames;
    private final MutableLiveData<List<GameItem>> mCompletedGames;
    private final MutableLiveData<String> mActiveGamesCount;
    private final MutableLiveData<String> mCompletedGamesCount;
    private final MutableLiveData<String> newGameCreated;
    private final MutableLiveData<GameCreationData> gameCreationEvent;
    private final java.util.Set<String> seenGameIds = new java.util.HashSet<>();
    
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

    public DashboardViewModel() {
        gameRepository = new GameRepository();
        mInProgressGames = new MutableLiveData<>();
        mCompletedGames = new MutableLiveData<>();
        mActiveGamesCount = new MutableLiveData<>();
        mCompletedGamesCount = new MutableLiveData<>();
        newGameCreated = new MutableLiveData<>();
        gameCreationEvent = new MutableLiveData<>();
        
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
    
    public void clearGameCreationEvent() {
        gameCreationEvent.setValue(null);
    }

    public void loadGames() {
        gameRepository.loadAllGames();
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
        // Generate Game ID (9 characters, uppercase)
        String gameId = generateGameId();
        
        // Generate 4-digit PIN (avoid "0000")
        String pin = generatePin();
        
        // Get current user info
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String creatorUserId = currentUser != null ? currentUser.getUid() : "anonymous";
        String creatorName = currentUser != null ? 
            (currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail()) 
            : "Anonymous User";

        // Create initial game data (similar to HTML implementation)
        Map<String, Object> initialGameData = new HashMap<>();
        initialGameData.put("numPlayers", 2);
        initialGameData.put("pointValue", pointValue);
        initialGameData.put("gstPercent", gstPercentage);
        
        // Create initial players array with creator as first player
        List<Map<String, Object>> players = new ArrayList<>();
        
        // First player is the creator
        Map<String, Object> creatorPlayer = new HashMap<>();
        creatorPlayer.put("name", creatorName != null ? creatorName : "You");
        creatorPlayer.put("scores", new ArrayList<>(java.util.Collections.nCopies(10, -1)));
        creatorPlayer.put("randomNumber", null);
        creatorPlayer.put("isCreator", true); // Mark as creator
        creatorPlayer.put("userId", creatorUserId); // Store user ID for identification
        players.add(creatorPlayer);
        
        // Second player is a placeholder
        Map<String, Object> player2 = new HashMap<>();
        player2.put("name", "Player 2");
        player2.put("scores", new ArrayList<>(java.util.Collections.nCopies(10, -1)));
        player2.put("randomNumber", null);
        player2.put("isCreator", false);
        player2.put("userId", null);
        players.add(player2);
        
        initialGameData.put("players", players);

        // Save to Firebase Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Save authentication data to 'games' collection (with creator info)
        Map<String, Object> authData = new HashMap<>();
        authData.put("gameId", gameId);
        authData.put("pin", pin);
        authData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        authData.put("creatorUserId", creatorUserId);
        authData.put("creatorName", creatorName);
        authData.put("version", "1.0");
        
        // Add creation timestamp to game data as well for easy access
        initialGameData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        
        db.collection("games").document(gameId)
            .set(authData)
            .addOnSuccessListener(aVoid -> {
                // Save game data to 'gameData' collection
                Map<String, Object> gameDataDoc = new HashMap<>();
                gameDataDoc.put("data", initialGameData);
                gameDataDoc.put("lastUpdated", com.google.firebase.firestore.FieldValue.serverTimestamp());
                gameDataDoc.put("version", "1.0");
                
                db.collection("gameData").document(gameId)
                    .set(gameDataDoc)
                    .addOnSuccessListener(aVoid2 -> {
                        // Refresh the games list to show the new game
                        loadGames();
                        
                        // Trigger navigation to the new game with edit access
                        newGameCreated.setValue(gameId);
                        
                        // Trigger game creation event with game ID, creator name, and point value (NOT PIN for security)
                        gameCreationEvent.setValue(new GameCreationData(gameId, creatorName, pointValue));
                        
                        android.util.Log.d("GameCreation", "Game created successfully with creator: " + creatorName);
                    })
                    .addOnFailureListener(e -> {
                        // Handle error
                        android.util.Log.e("GameCreation", "Failed to create game", e);
                    });
            })
            .addOnFailureListener(e -> {
                // Handle error
            });
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

    private String generatePin() {
        Random random = new Random();
        String pin;
        
        do {
            pin = String.format("%04d", random.nextInt(10000));
        } while ("0000".equals(pin));
        
        return pin;
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
        // Clean up listeners when ViewModel is destroyed
        if (gameRepository != null) {
            gameRepository.removeListeners();
        }
    }
}
