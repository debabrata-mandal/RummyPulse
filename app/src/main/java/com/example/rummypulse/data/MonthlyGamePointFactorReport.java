package com.example.rummypulse.data;

import java.util.List;
import java.util.Locale;

public class MonthlyGamePointFactorReport {
    private String monthYear; // Format: "September 2024"
    private List<GamePointFactorReport> gamePointFactorReports;

    public MonthlyGamePointFactorReport() {
        // Default constructor required for Firebase
    }

    public MonthlyGamePointFactorReport(String monthYear, List<GamePointFactorReport> gamePointFactorReports) {
        this.monthYear = monthYear;
        this.gamePointFactorReports = gamePointFactorReports;
    }

    // Getters and setters
    public String getMonthYear() {
        return monthYear;
    }

    public void setMonthYear(String monthYear) {
        this.monthYear = monthYear;
    }

    public List<GamePointFactorReport> getGamePointFactorReports() {
        return gamePointFactorReports;
    }

    public void setGamePointFactorReports(List<GamePointFactorReport> gamePointFactorReports) {
        this.gamePointFactorReports = gamePointFactorReports;
    }

    // Helper methods for display
    public int getTotalGamesForMonth() {
        if (gamePointFactorReports == null) return 0;
        return gamePointFactorReports.stream().mapToInt(GamePointFactorReport::getTotalGames).sum();
    }

    public double getTotalBoardAdjustmentForMonth() {
        if (gamePointFactorReports == null) return 0.0;
        return gamePointFactorReports.stream().mapToDouble(GamePointFactorReport::getTotalBoardPoints).sum();
    }

    public int getTotalPlayersForMonth() {
        if (gamePointFactorReports == null) return 0;
        return gamePointFactorReports.stream().mapToInt(GamePointFactorReport::getTotalPlayers).sum();
    }

    public String getFormattedMonthlyBoardAdjustment() {
        return String.format(Locale.getDefault(), "%.0f GP", getTotalBoardAdjustmentForMonth());
    }

}
