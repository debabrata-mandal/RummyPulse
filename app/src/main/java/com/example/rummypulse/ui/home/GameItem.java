package com.example.rummypulse.ui.home;

public class GameItem {
    private String gameId;
    private String gamePin;
    private String totalScore;
    private String pointValue;
    private String creationDateTime;
    private String gameStatus;
    private String numberOfPlayers;
    private String gstPercentage;
    private String gstAmount;

    public GameItem() {
        // Default constructor for Firebase
    }

    public GameItem(String gameId, String gamePin, String totalScore, String pointValue, String creationDateTime, String gameStatus, 
                   String numberOfPlayers, String gstPercentage, String gstAmount) {
        this.gameId = gameId;
        this.gamePin = gamePin;
        this.totalScore = totalScore;
        this.pointValue = pointValue;
        this.creationDateTime = creationDateTime;
        this.gameStatus = gameStatus;
        this.numberOfPlayers = numberOfPlayers;
        this.gstPercentage = gstPercentage;
        this.gstAmount = gstAmount;
    }

    // Getters
    public String getGameId() { return gameId; }
    public String getGamePin() { return gamePin; }
    public String getTotalScore() { return totalScore; }
    public String getPointValue() { return pointValue; }
    public String getCreationDateTime() { return creationDateTime; }
    public String getGameStatus() { return gameStatus; }
    public String getNumberOfPlayers() { return numberOfPlayers; }
    public String getGstPercentage() { return gstPercentage; }
    public String getGstAmount() { return gstAmount; }

    // Setters
    public void setGameId(String gameId) { this.gameId = gameId; }
    public void setGamePin(String gamePin) { this.gamePin = gamePin; }
    public void setTotalScore(String totalScore) { this.totalScore = totalScore; }
    public void setPointValue(String pointValue) { this.pointValue = pointValue; }
    public void setCreationDateTime(String creationDateTime) { this.creationDateTime = creationDateTime; }
    public void setGameStatus(String gameStatus) { this.gameStatus = gameStatus; }
    public void setNumberOfPlayers(String numberOfPlayers) { this.numberOfPlayers = numberOfPlayers; }
    public void setGstPercentage(String gstPercentage) { this.gstPercentage = gstPercentage; }
    public void setGstAmount(String gstAmount) { this.gstAmount = gstAmount; }

    // Helper methods for better data handling
    public int getTotalScoreAsInt() {
        try {
            return Integer.parseInt(totalScore);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public double getPointValueAsDouble() {
        try {
            return Double.parseDouble(pointValue);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public double getGstAmountAsDouble() {
        try {
            return Double.parseDouble(gstAmount);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public int getNumberOfPlayersAsInt() {
        try {
            return Integer.parseInt(numberOfPlayers);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean isCompleted() {
        return "Completed".equals(gameStatus);
    }

    public boolean isActive() {
        return "Active".equals(gameStatus);
    }
}