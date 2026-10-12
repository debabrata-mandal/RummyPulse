package com.example.rummypulse.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Remembers when this install last read a whole collection from the server, which is what makes
 * Firestore's on-device copy of it trustworthy as a starting point for change-only queries.
 */
public final class DeviceCacheReloadStore {

    public static final String USER_DIRECTORY = "user_directory";
    public static final String PLAYER_STATS = "player_stats";

    private static final String PREFS_NAME = "device_cache_reloads";

    @Nullable
    private static SharedPreferences preferences;

    private DeviceCacheReloadStore() {}

    public static synchronized void initialize(Context context) {
        if (preferences == null) {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    static synchronized boolean canSeedFromDeviceCache(String collectionKey, long nowMillis) {
        return preferences != null && DeviceCachePolicy.canSeedFromDeviceCache(
                preferences.getLong(collectionKey, 0L), nowMillis);
    }

    static synchronized void recordFullReload(String collectionKey, long nowMillis) {
        if (preferences != null) {
            preferences.edit().putLong(collectionKey, nowMillis).apply();
        }
    }

    static synchronized void clear(String collectionKey) {
        if (preferences != null) {
            preferences.edit().remove(collectionKey).apply();
        }
    }
}
