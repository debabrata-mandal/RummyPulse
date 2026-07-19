package com.example.rummypulse.data;

import com.google.firebase.Timestamp;

public class GameAuth {
    private String gameId;
    private String pin;
    private Timestamp createdAt;
    private String version;
    private String creatorName;
    private String creatorUserId;
    /** AI or user-facing game title; stored only on {@code games_v2} documents. May be empty string. */
    private String displayName;
    /** Increments on each edit-access transfer; invalidates prior PINs and offline saves. */
    private Long pinGeneration;
    /** Firebase Auth UID of the user who currently holds edit access. */
    private String activeEditorUserId;
    /** Display name of the active editor. */
    private String activeEditorName;
    /** Denormalized dashboard fields (readable from {@code games_v2} without {@code gameData_v2}). */
    private Double dashboardPointValue;
    private Integer dashboardNumPlayers;
    private Double dashboardGstPercent;
    private String dashboardGameStatus;

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

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getCreatorUserId() {
        return creatorUserId;
    }

    public void setCreatorUserId(String creatorUserId) {
        this.creatorUserId = creatorUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getPinGeneration() {
        return pinGeneration;
    }

    public void setPinGeneration(Long pinGeneration) {
        this.pinGeneration = pinGeneration;
    }

    /** Returns {@code pinGeneration} when set and positive, otherwise {@code 1}. */
    public long getPinGenerationOrDefault() {
        if (pinGeneration != null && pinGeneration > 0) {
            return pinGeneration;
        }
        return 1L;
    }

    public String getActiveEditorUserId() {
        return activeEditorUserId;
    }

    public void setActiveEditorUserId(String activeEditorUserId) {
        this.activeEditorUserId = activeEditorUserId;
    }

    public String getActiveEditorName() {
        return activeEditorName;
    }

    public void setActiveEditorName(String activeEditorName) {
        this.activeEditorName = activeEditorName;
    }

    public Double getDashboardPointValue() {
        return dashboardPointValue;
    }

    public void setDashboardPointValue(Double dashboardPointValue) {
        this.dashboardPointValue = dashboardPointValue;
    }

    public Integer getDashboardNumPlayers() {
        return dashboardNumPlayers;
    }

    public void setDashboardNumPlayers(Integer dashboardNumPlayers) {
        this.dashboardNumPlayers = dashboardNumPlayers;
    }

    public Double getDashboardGstPercent() {
        return dashboardGstPercent;
    }

    public void setDashboardGstPercent(Double dashboardGstPercent) {
        this.dashboardGstPercent = dashboardGstPercent;
    }

    public String getDashboardGameStatus() {
        return dashboardGameStatus;
    }

    public void setDashboardGameStatus(String dashboardGameStatus) {
        this.dashboardGameStatus = dashboardGameStatus;
    }

    public boolean hasDashboardSummary() {
        return dashboardPointValue != null
                && dashboardNumPlayers != null
                && dashboardGstPercent != null;
    }
}
