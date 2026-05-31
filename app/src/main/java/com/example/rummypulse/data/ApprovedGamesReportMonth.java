package com.example.rummypulse.data;

import com.google.firebase.Timestamp;

import java.util.List;

/**
 * One Firestore document in {@code approvedGamesReport_v2} (document id: {@code yyyy-MM}).
 */
public class ApprovedGamesReportMonth {

    private String monthYear;
    private List<PointValueReport> pointValueReports;
    private Timestamp lastBuiltAt;

    public ApprovedGamesReportMonth() {
    }

    public ApprovedGamesReportMonth(String monthYear, List<PointValueReport> pointValueReports, Timestamp lastBuiltAt) {
        this.monthYear = monthYear;
        this.pointValueReports = pointValueReports;
        this.lastBuiltAt = lastBuiltAt;
    }

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

    public Timestamp getLastBuiltAt() {
        return lastBuiltAt;
    }

    public void setLastBuiltAt(Timestamp lastBuiltAt) {
        this.lastBuiltAt = lastBuiltAt;
    }

    public MonthlyPointValueReport toMonthlyPointValueReport() {
        return new MonthlyPointValueReport(monthYear, pointValueReports != null ? pointValueReports : new java.util.ArrayList<>());
    }
}
