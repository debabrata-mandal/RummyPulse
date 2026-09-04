package com.example.rummypulse.data;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Bucket keys for {@link PlayerStats}.
 *
 * <p>Keys are always derived in {@link #ZONE} so a device travelling across timezones keeps
 * reporting the same bucket for the same game, and so the editor recording a game and the player
 * reading it never disagree about which month or week it belongs to.
 */
public final class PlayerStatsKeys {

    /** Fixed reporting zone. The app settles in INR and plays on IST evenings. */
    public static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Kolkata");

    private PlayerStatsKeys() {
    }

    public static String monthKey(Date instant) {
        Calendar calendar = calendarAt(instant);
        return String.format(
                Locale.US,
                "%04d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1);
    }

    /** ISO-8601 week key, e.g. {@code 2026-W36}. */
    public static String weekKey(Date instant) {
        Calendar calendar = calendarAt(instant);
        int week = calendar.get(Calendar.WEEK_OF_YEAR);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        // A late-December date can fall in week 1 of the next ISO year, and an early-January date
        // can fall in week 52/53 of the previous one.
        if (month == Calendar.JANUARY && week >= 52) {
            year--;
        } else if (month == Calendar.DECEMBER && week == 1) {
            year++;
        }
        return String.format(Locale.US, "%04d-W%02d", year, week);
    }

    public static String currentMonthKey() {
        return monthKey(new Date());
    }

    public static String previousMonthKey() {
        Calendar calendar = calendarAt(new Date());
        calendar.add(Calendar.MONTH, -1);
        return monthKey(calendar.getTime());
    }

    public static String currentWeekKey() {
        return weekKey(new Date());
    }

    private static Calendar calendarAt(Date instant) {
        Calendar calendar = Calendar.getInstance(ZONE, Locale.US);
        // ISO-8601: weeks start Monday and the first week is the one holding at least 4 days.
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setMinimalDaysInFirstWeek(4);
        calendar.setTime(instant == null ? new Date() : instant);
        return calendar;
    }
}
