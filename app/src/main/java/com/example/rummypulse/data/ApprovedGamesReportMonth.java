package com.example.rummypulse.data;

import com.google.firebase.Timestamp;

import java.util.List;

/**
 * One Firestore document in {@code approvedGamesReport_v2} (document id: {@code yyyy-MM}).
 */
public class ApprovedGamesReportMonth {

    private Integer schemaVersion;
    private String monthYear;
    private List<GamePointFactorReport> gamePointFactorReports;
    private Timestamp lastBuiltAt;

    public ApprovedGamesReportMonth() {
    }

    public ApprovedGamesReportMonth(String monthYear, List<GamePointFactorReport> gamePointFactorReports, Timestamp lastBuiltAt) {
        this.schemaVersion = GameDataSchema.CURRENT_VERSION;
        this.monthYear = monthYear;
        this.gamePointFactorReports = gamePointFactorReports;
        this.lastBuiltAt = lastBuiltAt;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

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

    public Timestamp getLastBuiltAt() {
        return lastBuiltAt;
    }

    public void setLastBuiltAt(Timestamp lastBuiltAt) {
        this.lastBuiltAt = lastBuiltAt;
    }

    public MonthlyGamePointFactorReport toMonthlyGamePointFactorReport() {
        return new MonthlyGamePointFactorReport(monthYear, gamePointFactorReports != null ? gamePointFactorReports : new java.util.ArrayList<>());
    }
}
