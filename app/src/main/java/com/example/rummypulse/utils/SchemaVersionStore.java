package com.example.rummypulse.utils;

import android.content.Context;

import com.example.rummypulse.data.GameDataSchema;

/** Device-local record that schema-v3 cleanup and server verification have completed. */
public final class SchemaVersionStore {

    private static final String PREFS = "rummypulse_schema_gate";
    private static final String KEY_PREPARED_VERSION = "prepared_version";
    private static final String KEY_CACHE_CLEARED_VERSION = "cache_cleared_version";

    private SchemaVersionStore() {
    }

    public static boolean isPrepared(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_PREPARED_VERSION, 0) == GameDataSchema.CURRENT_VERSION;
    }

    public static boolean isCacheCleared(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_CACHE_CLEARED_VERSION, 0) == GameDataSchema.CURRENT_VERSION;
    }

    public static void markCacheCleared(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_CACHE_CLEARED_VERSION, GameDataSchema.CURRENT_VERSION)
                .apply();
    }

    public static void markPrepared(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_PREPARED_VERSION, GameDataSchema.CURRENT_VERSION)
                .apply();
    }
}
