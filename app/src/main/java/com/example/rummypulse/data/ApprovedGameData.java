package com.example.rummypulse.data;

import com.google.firebase.Timestamp;
import java.util.List;
import java.util.Map;

public class ApprovedGameData {
    private String gameId;
    private int numPlayers;
    private double pointValue;
    private double gstPercent;
    private Map<String, Integer> playerScores; // Player name -> Total score
    private Timestamp approvedAt;
    private String version;
    private String gstAmount;
    private String gameStatus;
    private String creationDateTime;

    public ApprovedGameData() {
        // Default constructor required for Firestore
    }

    public ApprovedGameData(String gameId, int numPlayers, double pointValue, double gstPercent, 
                           Map<String, Integer> playerScores, Timestamp approvedAt, String version, 
                           String gstAmount, String gameStatus, String creationDateTime) {
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.pointValue = pointValue;
        this.gstPercent = gstPercent;
        this.playerScores = playerScores;
        this.approvedAt = approvedAt;
        this.version = version;
        this.gstAmount = gstAmount;
        this.gameStatus = gameStatus;
        this.creationDateTime = creationDateTime;
    }

    // Getters and setters
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public int getNumPlayers() {
        return numPlayers;
    }

    public void setNumPlayers(int numPlayers) {
        this.numPlayers = numPlayers;
    }

    public double getPointValue() {
        return pointValue;
    }

    public void setPointValue(double pointValue) {
        this.pointValue = pointValue;
    }

    public double getGstPercent() {
        return gstPercent;
    }

    public void setGstPercent(double gstPercent) {
        this.gstPercent = gstPercent;
    }

    public Map<String, Integer> getPlayerScores() {
        return playerScores;
    }

    public void setPlayerScores(Map<String, Integer> playerScores) {
        this.playerScores = playerScores;
    }

    public Timestamp getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Timestamp approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }


    public String getGstAmount() {
        return gstAmount;
    }

    public void setGstAmount(String gstAmount) {
        this.gstAmount = gstAmount;
    }

    public String getGameStatus() {
        return gameStatus;
    }

    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }

    public String getCreationDateTime() {
        return creationDateTime;
    }

    public void setCreationDateTime(String creationDateTime) {
        this.creationDateTime = creationDateTime;
    }

    // Helper methods
    public int getTotalGameScore() {
        if (playerScores == null) return 0;
        return playerScores.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double getGstAmountAsDouble() {
        try {
            return Double.parseDouble(gstAmount);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
