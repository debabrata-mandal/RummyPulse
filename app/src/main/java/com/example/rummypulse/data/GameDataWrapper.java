package com.example.rummypulse.data;

import com.google.firebase.Timestamp;
import java.util.List;

public class GameDataWrapper {
    private GameData data;
    private Timestamp lastUpdated;
    private String version;

    public GameDataWrapper() {
        // Default constructor required for Firestore
    }

    public GameDataWrapper(GameData data, Timestamp lastUpdated, String version) {
        this.data = data;
        this.lastUpdated = lastUpdated;
        this.version = version;
    }

    // Getters and setters
    public GameData getData() {
        return data;
    }

    public void setData(GameData data) {
        this.data = data;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
