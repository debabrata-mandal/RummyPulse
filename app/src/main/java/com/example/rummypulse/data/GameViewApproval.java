package com.example.rummypulse.data;

import com.google.firebase.Timestamp;

public class GameViewApproval {
    private String gameId;
    private String userId;
    private String userDisplayName;
    private Timestamp requestedAt;
    private Timestamp lastUpdatedAt;
    private String status;

    public GameViewApproval() {
        // Required for Firestore
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public void setUserDisplayName(String userDisplayName) {
        this.userDisplayName = userDisplayName;
    }

    public Timestamp getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Timestamp requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Timestamp getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Timestamp lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public GameViewApprovalStatus getStatusEnum() {
        return GameViewApprovalStatus.fromFirestore(status);
    }
}
