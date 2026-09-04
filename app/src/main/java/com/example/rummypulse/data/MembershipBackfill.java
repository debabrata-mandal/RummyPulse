package com.example.rummypulse.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks whether this user has completed the one-off {@code memberUserIds} backfill.
 *
 * <p>Recorded per user id, because two accounts sharing a device each need their own pass: the
 * repair write is only permitted for games the signed-in user creates, edits, or administers.
 */
final class MembershipBackfill {

    /** Time allowed for the unfiltered listener to deliver and repair before scoping down. */
    static final long SETTLE_DELAY_MS = 12_000L;

    private static final String PREFS = "rummypulse_membership_backfill";
    private static final String KEY_PREFIX = "completed_";

    private MembershipBackfill() {
    }

    static boolean isComplete(Context context, String userId) {
        return prefs(context).getBoolean(KEY_PREFIX + userId, false);
    }

    static void markComplete(Context context, String userId) {
        prefs(context).edit().putBoolean(KEY_PREFIX + userId, true).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
