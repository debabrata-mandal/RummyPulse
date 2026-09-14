package com.example.rummypulse.data;

import com.google.firebase.firestore.Exclude;

import java.util.List;
import java.util.Locale;

public class GamePointFactorReport {
    private double gamePointFactor; // Factor such as 0.15 or 0.25.
    private int totalGames;
    private double totalBoardPoints;
    private int totalPlayers;
    /**
     * The games this row was aggregated from. Available while a report is being built, but never
     * persisted: the totals above are all the Reports UI reads, so storing whole game documents
     * inside the monthly report only duplicated {@code approvedGames_v2}.
     */
    private List<ApprovedGameData> games;

    public GamePointFactorReport() {
        // Default constructor required for Firebase
    }

    public GamePointFactorReport(double gamePointFactor, int totalGames, double totalBoardPoints,
                           int totalPlayers, List<ApprovedGameData> games) {
        this.gamePointFactor = gamePointFactor;
        this.totalGames = totalGames;
        this.totalBoardPoints = totalBoardPoints;
        this.totalPlayers = totalPlayers;
        this.games = games;
    }

    // Getters and setters
    public double getGamePointFactor() {
        return gamePointFactor;
    }

    public void setGamePointFactor(double gamePointFactor) {
        this.gamePointFactor = gamePointFactor;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(int totalGames) {
        this.totalGames = totalGames;
    }

    public double getTotalBoardPoints() {
        return totalBoardPoints;
    }

    public void setTotalBoardPoints(double totalBoardPoints) {
        this.totalBoardPoints = totalBoardPoints;
    }

    public int getTotalPlayers() {
        return totalPlayers;
    }

    public void setTotalPlayers(int totalPlayers) {
        this.totalPlayers = totalPlayers;
    }

    @Exclude
    public List<ApprovedGameData> getGames() {
        return games;
    }

    @Exclude
    public void setGames(List<ApprovedGameData> games) {
        this.games = games;
    }

    // Helper methods for display
    public String getFormattedGamePointFactor() {
        return String.format(Locale.getDefault(), "%.2f×", gamePointFactor);
    }

    public String getFormattedBoardPoints() {
        return String.format(Locale.getDefault(), "%.0f GP", totalBoardPoints);
    }

    public double getAverageBoardPointsPerGame() {
        return totalGames > 0 ? totalBoardPoints / totalGames : 0.0;
    }

    public double getAveragePlayersPerGame() {
        return totalGames > 0 ? (double) totalPlayers / totalGames : 0.0;
    }

}
