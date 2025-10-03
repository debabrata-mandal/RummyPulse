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

    public void joinGame(String gameId, boolean requestEditAccess) {
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
                            
                            if (requestEditAccess) {
                                successMessage.setValue("Game loaded successfully. You can now request edit access.");
                            } else {
                                successMessage.setValue("Game loaded successfully. You are in view mode.");
                            }
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
