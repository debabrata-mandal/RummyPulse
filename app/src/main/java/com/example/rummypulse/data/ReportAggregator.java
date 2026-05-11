package com.example.rummypulse.data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Groups {@link ApprovedGameData} the same way the Reports UI expects (month, then point value).
 */
public final class ReportAggregator {

    private ReportAggregator() {
    }

    /**
     * Stable key for Firestore document id, e.g. {@code 2026-05}. Uses {@code approvedAt} when set,
     * otherwise parses {@code creationDateTime}. Returns null if month cannot be determined.
     */
    public static String yearMonthKey(ApprovedGameData game) {
        if (game == null) {
            return null;
        }
        try {
            Calendar calendar = Calendar.getInstance();
            if (game.getApprovedAt() != null) {
                calendar.setTime(game.getApprovedAt().toDate());
            } else {
                String creation = game.getCreationDateTime();
                if (creation == null || creation.isEmpty()) {
                    return null;
                }
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                calendar.setTime(inputFormat.parse(creation));
            }
            return String.format(Locale.US, "%04d-%02d",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    public static String displayMonthForYearMonth(String yyyyMm) {
        try {
            SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM", Locale.US);
            Date d = keyFmt.parse(yyyyMm);
            return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(d);
        } catch (ParseException e) {
            return yyyyMm;
        }
    }

    /**
     * Full aggregation used by "Build all" and for in-memory preview if needed.
     */
    public static List<MonthlyPointValueReport> aggregateAll(List<ApprovedGameData> games) {
        Map<String, List<ApprovedGameData>> byMonth = new HashMap<>();
        for (ApprovedGameData g : games) {
            String k = yearMonthKey(g);
            if (k == null) {
                continue;
            }
            if (!byMonth.containsKey(k)) {
                byMonth.put(k, new ArrayList<>());
            }
            byMonth.get(k).add(g);
        }
        List<MonthlyPointValueReport> monthlyReports = new ArrayList<>();
        for (Map.Entry<String, List<ApprovedGameData>> e : byMonth.entrySet()) {
            monthlyReports.add(buildMonthlyPointValueReport(e.getKey(), e.getValue()));
        }
        sortMonthlyReportsDesc(monthlyReports);
        return monthlyReports;
    }

    /**
     * Build one month's report from games already belonging to that calendar month.
     */
    public static MonthlyPointValueReport buildMonthlyPointValueReport(String yyyyMm, List<ApprovedGameData> monthGames) {
        String displayMonthYear = displayMonthForYearMonth(yyyyMm);
        Map<Double, List<ApprovedGameData>> gamesByPointValue = new HashMap<>();
        for (ApprovedGameData game : monthGames) {
            double pointValue = game.getPointValue();
            if (!gamesByPointValue.containsKey(pointValue)) {
                gamesByPointValue.put(pointValue, new ArrayList<>());
            }
            gamesByPointValue.get(pointValue).add(game);
        }
        List<PointValueReport> pointValueReports = new ArrayList<>();
        for (Map.Entry<Double, List<ApprovedGameData>> pointEntry : gamesByPointValue.entrySet()) {
            double pointValue = pointEntry.getKey();
            List<ApprovedGameData> pointValueGames = pointEntry.getValue();
            int totalGames = pointValueGames.size();
            double totalGstCollected = 0.0;
            int totalPlayers = 0;
            for (ApprovedGameData game : pointValueGames) {
                totalGstCollected += game.getGstAmountAsDouble();
                totalPlayers += game.getNumPlayers();
            }
            pointValueReports.add(new PointValueReport(
                    pointValue,
                    totalGames,
                    totalGstCollected,
                    totalPlayers,
                    pointValueGames));
        }
        pointValueReports.sort((r1, r2) -> Double.compare(r1.getPointValue(), r2.getPointValue()));
        return new MonthlyPointValueReport(displayMonthYear, pointValueReports);
    }

    public static void sortMonthlyReportsDesc(List<MonthlyPointValueReport> monthlyReports) {
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthlyReports.sort((r1, r2) -> {
            try {
                Calendar cal1 = Calendar.getInstance();
                Calendar cal2 = Calendar.getInstance();
                cal1.setTime(monthYearFormat.parse(r1.getMonthYear()));
                cal2.setTime(monthYearFormat.parse(r2.getMonthYear()));
                return cal2.compareTo(cal1);
            } catch (Exception e) {
                return r2.getMonthYear().compareTo(r1.getMonthYear());
            }
        });
    }
}
