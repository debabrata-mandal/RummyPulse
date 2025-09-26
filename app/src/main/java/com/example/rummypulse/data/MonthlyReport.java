package com.example.rummypulse.data;

import java.util.List;

public class MonthlyReport {
    private String monthYear; // Format: "September 2024"
    private int totalGames;
    private double totalGstCollected;
    private double totalPointValue;
    private int totalPlayers;
    private List<ApprovedGameData> games;

    public MonthlyReport() {
        // Default constructor required for Firebase
    }

    public MonthlyReport(String monthYear, int totalGames, double totalGstCollected, 
                        double totalPointValue, int totalPlayers, List<ApprovedGameData> games) {
        this.monthYear = monthYear;
        this.totalGames = totalGames;
        this.totalGstCollected = totalGstCollected;
        this.totalPointValue = totalPointValue;
        this.totalPlayers = totalPlayers;
        this.games = games;
    }

    // Getters and setters
    public String getMonthYear() {
        return monthYear;
    }

    public void setMonthYear(String monthYear) {
        this.monthYear = monthYear;
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

    public double getTotalPointValue() {
        return totalPointValue;
    }

    public void setTotalPointValue(double totalPointValue) {
        this.totalPointValue = totalPointValue;
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
    public String getFormattedGstAmount() {
        return "₹" + String.format("%.0f", totalGstCollected);
    }

    public String getFormattedPointValue() {
        return "₹" + String.format("%.2f", totalPointValue);
    }

    public double getAverageGstPerGame() {
        return totalGames > 0 ? totalGstCollected / totalGames : 0.0;
    }

    public double getAveragePlayersPerGame() {
        return totalGames > 0 ? (double) totalPlayers / totalGames : 0.0;
    }
}
