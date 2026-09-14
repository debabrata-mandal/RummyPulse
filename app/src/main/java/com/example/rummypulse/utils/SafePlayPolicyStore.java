package com.example.rummypulse.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.rummypulse.data.SafePlayPolicy;

/** UID-scoped local cache for a server-confirmed safe-play policy acceptance. */
public final class SafePlayPolicyStore {

    private static final String PREFERENCES = "rummypulse_safe_play_policy";
    private static final String ACCEPTED_VERSION_PREFIX = "accepted_version_";

    private SafePlayPolicyStore() {
    }

    public static boolean hasCurrentAcceptance(Context context, String userId) {
        if (context == null || isNullOrEmpty(userId)) {
            return false;
        }
        return preferences(context).getInt(key(userId), 0) >= SafePlayPolicy.CURRENT_VERSION;
    }

    /** Call only after Firestore confirms acceptance or returns a current accepted profile. */
    public static void cacheCurrentAcceptance(Context context, String userId) {
        if (context == null || isNullOrEmpty(userId)) {
            return;
        }
        preferences(context).edit()
                .putInt(key(userId), SafePlayPolicy.CURRENT_VERSION)
                .apply();
    }

    public static void clearAll(Context context) {
        if (context != null) {
            preferences(context).edit().clear().apply();
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String key(String userId) {
        return ACCEPTED_VERSION_PREFIX + userId;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
