package com.example.rummypulse.data;

import java.util.List;

public class PointValueReport {
    private double pointValue; // Point value like 0.15, 0.25, etc.
    private int totalGames;
    private double totalGstCollected;
    private int totalPlayers;
    private List<ApprovedGameData> games;

    public PointValueReport() {
        // Default constructor required for Firebase
    }

    public PointValueReport(double pointValue, int totalGames, double totalGstCollected, 
                           int totalPlayers, List<ApprovedGameData> games) {
        this.pointValue = pointValue;
        this.totalGames = totalGames;
        this.totalGstCollected = totalGstCollected;
        this.totalPlayers = totalPlayers;
        this.games = games;
    }

    // Getters and setters
    public double getPointValue() {
        return pointValue;
    }

    public void setPointValue(double pointValue) {
        this.pointValue = pointValue;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(int totalGames) {
        this.totalGames = totalGames;
    }

    public double getTotalGstCollected() {
        return totalGstCollected;
    }

    public void setTotalGstCollected(double totalGstCollected) {
        this.totalGstCollected = totalGstCollected;
    }

    public int getTotalPlayers() {
        return totalPlayers;
    }

    public void setTotalPlayers(int totalPlayers) {
        this.totalPlayers = totalPlayers;
    }

    public List<ApprovedGameData> getGames() {
        return games;
    }

    public void setGames(List<ApprovedGameData> games) {
        this.games = games;
    }

    // Helper methods for display
    public String getFormattedPointValue() {
        return "₹" + String.format("%.2f", pointValue);
    }

    public String getFormattedGstAmount() {
        return "₹" + String.format("%.0f", totalGstCollected);
    }

    public double getAverageGstPerGame() {
        return totalGames > 0 ? totalGstCollected / totalGames : 0.0;
    }

    public double getAveragePlayersPerGame() {
        return totalGames > 0 ? (double) totalPlayers / totalGames : 0.0;
    }

    public String getGamesText() {
        return totalGames + " Game" + (totalGames != 1 ? "s" : "");
    }
}
