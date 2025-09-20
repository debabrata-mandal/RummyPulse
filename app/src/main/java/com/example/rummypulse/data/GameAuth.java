package com.example.rummypulse.data;

import com.google.firebase.Timestamp;

public class GameAuth {
    private String gameId;
    private String pin;
    private Timestamp createdAt;
    private String version;

    public GameAuth() {
        // Default constructor required for Firestore
    }

    public GameAuth(String gameId, String pin, Timestamp createdAt, String version) {
        this.gameId = gameId;
        this.pin = pin;
        this.createdAt = createdAt;
        this.version = version;
    }

    // Getters and setters
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
