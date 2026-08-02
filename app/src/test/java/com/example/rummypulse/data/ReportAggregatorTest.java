package com.example.rummypulse.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReportAggregatorTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Creates an ApprovedGameData with approvedAt == null so yearMonthKey falls
     *  through to creationDateTime only — keeps tests Firebase-free. */
    private static ApprovedGameData game(String creationDateTime, double pointValue,
                                         int numPlayers, String gstAmount) {
        ApprovedGameData g = new ApprovedGameData();
        g.setCreationDateTime(creationDateTime);
        g.setPointValue(pointValue);
        g.setNumPlayers(numPlayers);
        g.setGstAmount(gstAmount);
        // approvedAt defaults to null; no Timestamp import needed
        return g;
    }

    // ---------------------------------------------------------------------------
    // yearMonthKey — null / empty / invalid inputs
    // ---------------------------------------------------------------------------

    @Test
    public void yearMonthKey_nullGame_returnsNull() {
        assertNull(ReportAggregator.yearMonthKey(null));
    }

    @Test
    public void yearMonthKey_nullCreationDateTime_returnsNull() {
        // approvedAt is null and creationDateTime is null → should return null
        ApprovedGameData g = new ApprovedGameData();
        assertNull(ReportAggregator.yearMonthKey(g));
    }

    @Test
    public void yearMonthKey_emptyCreationDateTime_returnsNull() {
        assertNull(ReportAggregator.yearMonthKey(game("", 1.0, 2, "0")));
    }

    @Test
    public void yearMonthKey_invalidCreationDateTime_returnsNull() {
        assertNull(ReportAggregator.yearMonthKey(game("not-a-date", 1.0, 2, "0")));
    }

    // ---------------------------------------------------------------------------
    // yearMonthKey — correct key extraction
    // ---------------------------------------------------------------------------

    @Test
    public void yearMonthKey_validDate_returnsCorrectYearMonthKey() {
        String key = ReportAggregator.yearMonthKey(game("2024-03-15 10:30:00", 1.0, 4, "18"));
        assertEquals("2024-03", key);
    }

    @Test
    public void yearMonthKey_january_monthPaddedCorrectly() {
        // Month 1 must be zero-padded to two digits
        String key = ReportAggregator.yearMonthKey(game("2024-01-05 08:00:00", 0.5, 3, "9"));
        assertEquals("2024-01", key);
    }

    @Test
    public void yearMonthKey_december_returnsCorrectKey() {
        // Calendar.MONTH is 0-based; December == 11 → +1 == 12
        String key = ReportAggregator.yearMonthKey(game("2023-12-31 23:59:59", 2.0, 6, "36"));
        assertEquals("2023-12", key);
    }

    // ---------------------------------------------------------------------------
    // displayMonthForYearMonth
    // ---------------------------------------------------------------------------

    @Test
    public void displayMonthForYearMonth_validKey_returnsFormattedString() {
        assertEquals("March 2024", ReportAggregator.displayMonthForYearMonth("2024-03"));
    }

    @Test
    public void displayMonthForYearMonth_invalidKey_returnsOriginalString() {
        // ParseException → method must return the raw input unchanged
        assertEquals("bad-key", ReportAggregator.displayMonthForYearMonth("bad-key"));
    }

    // ---------------------------------------------------------------------------
    // aggregateAll — empty / skip-invalid
    // ---------------------------------------------------------------------------

    @Test
    public void aggregateAll_emptyInput_returnsEmptyList() {
        List<MonthlyPointValueReport> result = ReportAggregator.aggregateAll(Collections.emptyList());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void aggregateAll_gamesWithInvalidCreationDateTime_areSkipped() {
        List<ApprovedGameData> games = Arrays.asList(
                game("2024-06-01 09:00:00", 1.0, 2, "10"),
                game(null,                  1.0, 2, "10"),   // null → skipped
                game("",                    1.0, 2, "10"),   // empty → skipped
                game("garbage",             1.0, 2, "10")    // unparseable → skipped
        );
        List<MonthlyPointValueReport> result = ReportAggregator.aggregateAll(games);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getTotalGamesForMonth());
    }

    // ---------------------------------------------------------------------------
    // aggregateAll — grouping by month
    // ---------------------------------------------------------------------------

    @Test
    public void aggregateAll_gamesInTwoMonths_produceTwoMonthlyReports() {
        List<ApprovedGameData> games = Arrays.asList(
                game("2024-03-01 10:00:00", 1.0, 2, "10"),
                game("2024-03-20 14:00:00", 1.0, 2, "10"),
                game("2024-04-05 09:00:00", 1.0, 2, "10")
        );
        List<MonthlyPointValueReport> result = ReportAggregator.aggregateAll(games);
        assertEquals(2, result.size());

        // Verify both expected month labels are present
        List<String> labels = new ArrayList<>();
        for (MonthlyPointValueReport r : result) labels.add(r.getMonthYear());
        assertTrue(labels.contains("March 2024"));
        assertTrue(labels.contains("April 2024"));
    }

    // ---------------------------------------------------------------------------
    // aggregateAll — descending sort (newest month first)
    // ---------------------------------------------------------------------------

    @Test
    public void aggregateAll_multipleMonths_sortedNewestFirst() {
        List<ApprovedGameData> games = Arrays.asList(
                game("2024-01-10 10:00:00", 1.0, 2, "5"),
                game("2024-03-05 10:00:00", 1.0, 2, "5"),
                game("2024-02-20 10:00:00", 1.0, 2, "5")
        );
        List<MonthlyPointValueReport> result = ReportAggregator.aggregateAll(games);
        assertEquals(3, result.size());
        assertEquals("March 2024",    result.get(0).getMonthYear());
        assertEquals("February 2024", result.get(1).getMonthYear());
        assertEquals("January 2024",  result.get(2).getMonthYear());
    }

    @Test
    public void aggregateAll_crossYearBoundary_newerYearFirst() {
        List<ApprovedGameData> games = Arrays.asList(
                game("2023-11-15 10:00:00", 1.0, 2, "5"),
                game("2024-01-03 10:00:00", 1.0, 2, "5")
        );
        List<MonthlyPointValueReport> result = ReportAggregator.aggregateAll(games);
        assertEquals(2, result.size());
        assertEquals("January 2024",   result.get(0).getMonthYear());
        assertEquals("November 2023",  result.get(1).getMonthYear());
    }

    // ---------------------------------------------------------------------------
    // buildMonthlyPointValueReport — grouping by point value
    // ---------------------------------------------------------------------------

    @Test
    public void buildMonthlyPointValueReport_differentPointValues_separateGroups() {
        List<ApprovedGameData> games = Arrays.asList(
                game("2024-05-01 10:00:00", 0.10, 3, "8"),
                game("2024-05-10 11:00:00", 0.25, 4, "18"),
                game("2024-05-20 12:00:00", 0.10, 2, "6")
        );
        MonthlyPointValueReport report =
                ReportAggregator.buildMonthlyPointValueReport("2024-05", games);

        assertEquals("May 2024", report.getMonthYear());
        assertEquals(2, report.getPointValueReports().size());

        // Sorted ascending by point value
        assertEquals(0.10, report.getPointValueReports().get(0).getPointValue(), 0.0001);
        assertEquals(0.25, report.getPointValueReports().get(1).getPointValue(), 0.0001);
    }

    // ---------------------------------------------------------------------------
    // buildMonthlyPointValueReport — aggregate totals
    // ---------------------------------------------------------------------------

    @Test
    public void buildMonthlyPointValueReport_correctTotalsForSinglePointValue() {
        List<ApprovedGameData> games = Arrays.asList(
                game("2024-07-01 09:00:00", 1.0, 3, "15.0"),
                game("2024-07-15 10:00:00", 1.0, 5, "25.0")
        );
        MonthlyPointValueReport report =
                ReportAggregator.buildMonthlyPointValueReport("2024-07", games);

        assertEquals(1, report.getPointValueReports().size());
        PointValueReport pv = report.getPointValueReports().get(0);

        assertEquals(2,    pv.getTotalGames());
        assertEquals(8,    pv.getTotalPlayers());           // 3 + 5
        assertEquals(40.0, pv.getTotalGstCollected(), 0.001); // 15 + 25
        assertEquals(2,    pv.getGames().size());
    }
}
