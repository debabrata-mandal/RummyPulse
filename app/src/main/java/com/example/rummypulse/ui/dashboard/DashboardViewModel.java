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
    private final MutableLiveData<String> mGamesCount;

    public DashboardViewModel() {
        gameRepository = new GameRepository();
        mInProgressGames = new MutableLiveData<>();
        mGamesCount = new MutableLiveData<>();
        
        // Observe all games and filter for in-progress ones
        gameRepository.getGameItems().observeForever(allGames -> {
            if (allGames != null) {
                List<GameItem> inProgressGames = new ArrayList<>();
                for (GameItem game : allGames) {
                    // Filter for in-progress games (not completed)
                    if (!game.isCompleted() && !"Completed".equals(game.getGameStatus())) {
                        inProgressGames.add(game);
                    }
                }
                mInProgressGames.setValue(inProgressGames);
                
                // Update games count text
                int count = inProgressGames.size();
                if (count == 0) {
                    mGamesCount.setValue("No active games available");
                } else if (count == 1) {
                    mGamesCount.setValue("1 active game available");
                } else {
                    mGamesCount.setValue(count + " active games available");
                }
            } else {
                mInProgressGames.setValue(new ArrayList<>());
                mGamesCount.setValue("Loading games...");
            }
        });
        
        // Load games initially
        loadGames();
    }

    public LiveData<List<GameItem>> getInProgressGames() {
        return mInProgressGames;
    }

    public LiveData<String> getGamesCount() {
        return mGamesCount;
    }

    public void loadGames() {
        gameRepository.loadAllGames();
    }

    public void joinGame(GameItem game) {
        // TODO: Implement join game functionality
        // This could involve:
        // 1. Adding current user to the game
        // 2. Navigating to game screen
        // 3. Updating game state
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
        
        // Create initial players array
        List<Map<String, Object>> players = new ArrayList<>();
        
        Map<String, Object> player1 = new HashMap<>();
        player1.put("name", "Player 1");
        player1.put("scores", new ArrayList<>(java.util.Collections.nCopies(10, -1)));
        player1.put("randomNumber", null);
        players.add(player1);
        
        Map<String, Object> player2 = new HashMap<>();
        player2.put("name", "Player 2");
        player2.put("scores", new ArrayList<>(java.util.Collections.nCopies(10, -1)));
        player2.put("randomNumber", null);
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
                    })
                    .addOnFailureListener(e -> {
                        // Handle error
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
}
