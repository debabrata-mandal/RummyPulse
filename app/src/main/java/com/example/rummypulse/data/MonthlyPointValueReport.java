package com.example.rummypulse.data;

import java.util.List;

public class MonthlyPointValueReport {
    private String monthYear; // Format: "September 2024"
    private List<PointValueReport> pointValueReports; // Point value reports for this month

    public MonthlyPointValueReport() {
        // Default constructor required for Firebase
    }

    public MonthlyPointValueReport(String monthYear, List<PointValueReport> pointValueReports) {
        this.monthYear = monthYear;
        this.pointValueReports = pointValueReports;
    }

    // Getters and setters
    public String getMonthYear() {
        return monthYear;
    }

    public void setMonthYear(String monthYear) {
        this.monthYear = monthYear;
    }

    public List<PointValueReport> getPointValueReports() {
        return pointValueReports;
    }

    public void setPointValueReports(List<PointValueReport> pointValueReports) {
        this.pointValueReports = pointValueReports;
    }

    // Helper methods for display
    public int getTotalGamesForMonth() {
        if (pointValueReports == null) return 0;
        return pointValueReports.stream().mapToInt(PointValueReport::getTotalGames).sum();
    }

    public double getTotalGstForMonth() {
        if (pointValueReports == null) return 0.0;
        return pointValueReports.stream().mapToDouble(PointValueReport::getTotalGstCollected).sum();
    }

    public int getTotalPlayersForMonth() {
        if (pointValueReports == null) return 0;
        return pointValueReports.stream().mapToInt(PointValueReport::getTotalPlayers).sum();
    }

    public String getFormattedMonthlyGst() {
        return "₹" + String.format("%.0f", getTotalGstForMonth());
    }

    public String getMonthlyGamesText() {
        int total = getTotalGamesForMonth();
        return total + " Game" + (total != 1 ? "s" : "");
    }
}
