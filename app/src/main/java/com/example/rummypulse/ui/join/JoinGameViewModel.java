package com.example.rummypulse.ui.join;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataWrapper;
import com.example.rummypulse.data.GameAuth;
import com.example.rummypulse.data.Player;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoinGameViewModel extends AndroidViewModel {

    private FirebaseFirestore db;
    private MutableLiveData<GameData> gameData = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<String> successMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> editAccessGranted = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> gamePin = new MutableLiveData<>();

    public JoinGameViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
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

    public void grantEditAccess() {
        editAccessGranted.setValue(true);
    }

    public void joinGame(String gameId, boolean requestEditAccess) {
        joinGame(gameId, requestEditAccess, null);
    }

    public void joinGame(String gameId, boolean requestEditAccess, String enteredPin) {
        if (TextUtils.isEmpty(gameId)) {
            errorMessage.setValue("Please enter a Game ID");
            return;
        }

        if (gameId.length() != 9) {
            errorMessage.setValue("Game ID must be 9 characters");
            return;
        }

        // Start loading
        System.out.println("Starting loading for game: " + gameId);
        isLoading.setValue(true);

        // Add a small delay to make spinner visible for testing
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // First, check if the game exists in the games collection
            db.collection("games").document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Game exists, extract PIN and fetch the game data
                        try {
                            GameAuth gameAuth = documentSnapshot.toObject(GameAuth.class);
                            if (gameAuth != null && gameAuth.getPin() != null) {
                                gamePin.setValue(gameAuth.getPin());
                                
                                // If requesting edit access, verify PIN
                                if (requestEditAccess && enteredPin != null) {
                                    if (gameAuth.getPin().equals(enteredPin)) {
                                        editAccessGranted.setValue(true);
                                        // No success message - edit access indication is clear from UI
                                    } else {
                                        // Don't set isLoading to false - let fetchGameData handle it
                                        // This allows the existing game data to remain visible
                                        errorMessage.setValue("Incorrect PIN. Please try again.");
                                        editAccessGranted.setValue(false);
                                        // Still fetch game data in view mode
                                        fetchGameData(gameId, false);
                                        return;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Error extracting PIN: " + e.getMessage());
                        }
                        fetchGameData(gameId, requestEditAccess);
                    } else {
                        isLoading.setValue(false);
                        errorMessage.setValue("Game not found. Please check the Game ID.");
                    }
                })
                .addOnFailureListener(e -> {
                    isLoading.setValue(false);
                    errorMessage.setValue("Failed to connect to server. Please try again.");
                });
        }, 500); // 500ms delay to make spinner visible
    }

    private void fetchGameData(String gameId, boolean requestEditAccess) {
        db.collection("gameData").document(gameId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                isLoading.setValue(false); // Stop loading
                if (documentSnapshot.exists()) {
                    try {
                        GameDataWrapper wrapper = documentSnapshot.toObject(GameDataWrapper.class);
                        if (wrapper != null && wrapper.getData() != null) {
                            GameData data = wrapper.getData();
                            gameData.setValue(data);
                            
                            // Only show success message if edit access was just granted
                            Boolean editAccess = editAccessGranted.getValue();
                            if (editAccess != null && editAccess) {
                                // Edit access already granted, don't show any additional message
                                // The access granted message was already shown during PIN verification
                            }
                            // No success message for view mode - it's obvious from the UI
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
                isLoading.setValue(false); // Stop loading
                errorMessage.setValue("Failed to load game data. Please try again.");
            });
    }

    public void requestEditAccess(String gameId) {
        if (TextUtils.isEmpty(gameId)) {
            errorMessage.setValue("Please enter a Game ID first");
            return;
        }

        // For now, we'll grant edit access without PIN verification
        // In a real implementation, you would prompt for PIN here
        editAccessGranted.setValue(true);
        successMessage.setValue("Edit access granted! You can now modify the game.");
    }

    public void saveGameData(String gameId, GameData updatedGameData) {
        if (TextUtils.isEmpty(gameId) || updatedGameData == null) {
            errorMessage.setValue("Invalid game data");
            return;
        }

        // Create clean data structure with only essential fields (no calculated values)
        Map<String, Object> cleanGameData = new HashMap<>();
        cleanGameData.put("numPlayers", updatedGameData.getNumPlayers());
        cleanGameData.put("pointValue", updatedGameData.getPointValue());
        cleanGameData.put("gstPercent", updatedGameData.getGstPercent());
        cleanGameData.put("players", updatedGameData.getPlayers());
        cleanGameData.put("version", updatedGameData.getVersion());
        // Don't save calculated fields: gameStatus, totalScore, gstAmount

        // Create the game data wrapper
        Map<String, Object> gameDataDoc = new HashMap<>();
        gameDataDoc.put("data", cleanGameData);
        gameDataDoc.put("lastUpdated", com.google.firebase.firestore.FieldValue.serverTimestamp());
        gameDataDoc.put("version", "1.0");

        // Save to Firebase
        db.collection("gameData").document(gameId)
            .set(gameDataDoc)
            .addOnSuccessListener(aVoid -> {
                // Update the local game data to trigger UI refresh
                gameData.setValue(updatedGameData);
                // Don't show success toast for auto-saves - real-time sync handles it
            })
            .addOnFailureListener(e -> {
                errorMessage.setValue("Failed to save game data: " + e.getMessage());
            });
    }

    public void validatePinAndGrantEditAccess(String gameId, String enteredPin) {
        if (TextUtils.isEmpty(gameId) || TextUtils.isEmpty(enteredPin)) {
            errorMessage.setValue("Please enter both Game ID and PIN");
            return;
        }

        // Fetch the PIN from the games collection
        db.collection("games").document(gameId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Map<String, Object> data = documentSnapshot.getData();
                    if (data != null && data.containsKey("pin")) {
                        String storedPin = (String) data.get("pin");
                        if (enteredPin.equals(storedPin)) {
                            editAccessGranted.setValue(true);
                            successMessage.setValue("✅ PIN verified! Edit access granted.");
                        } else {
                            errorMessage.setValue("❌ Invalid PIN. Please try again.");
                        }
                    } else {
                        errorMessage.setValue("❌ PIN not found for this game.");
                    }
                } else {
                    errorMessage.setValue("❌ Game not found.");
                }
            })
            .addOnFailureListener(e -> {
                errorMessage.setValue("❌ Failed to verify PIN. Please try again.");
            });
    }

    public void clearMessages() {
        errorMessage.setValue(null);
        successMessage.setValue(null);
        editAccessGranted.setValue(false);
    }
}
