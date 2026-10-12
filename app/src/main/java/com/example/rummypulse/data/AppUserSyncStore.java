package com.example.rummypulse.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.rummypulse.BuildConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Remembers each account's last successful syncMyIdentity call across process restarts. Only a
 * hash of the identity fingerprint is stored, and it includes the app version so the first start
 * after an update always runs a full sync.
 */
public final class AppUserSyncStore {

    private static final String PREFS_NAME = "app_user_sync_cache";
    private static final String KEY_FINGERPRINT_PREFIX = "fingerprint_";
    private static final String KEY_SYNCED_AT_PREFIX = "synced_at_";

    @Nullable
    private static SharedPreferences preferences;

    private AppUserSyncStore() {}

    public static synchronized void initialize(Context context) {
        if (preferences == null) {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    static synchronized boolean hasRecentSync(String uid, String fingerprint, long nowMillis) {
        if (preferences == null || uid == null) {
            return false;
        }
        String stored = preferences.getString(KEY_FINGERPRINT_PREFIX + uid, null);
        if (stored == null || !stored.equals(versionedHash(fingerprint))) {
            return false;
        }
        return AppUserSyncPolicy.canReuseRecentSync(
                preferences.getLong(KEY_SYNCED_AT_PREFIX + uid, 0L), nowMillis);
    }

    static synchronized void recordSync(String uid, String fingerprint, long nowMillis) {
        if (preferences == null || uid == null) {
            return;
        }
        preferences.edit()
                .putString(KEY_FINGERPRINT_PREFIX + uid, versionedHash(fingerprint))
                .putLong(KEY_SYNCED_AT_PREFIX + uid, nowMillis)
                .apply();
    }

    static synchronized void clear() {
        if (preferences != null) {
            preferences.edit().clear().apply();
        }
    }

    private static String versionedHash(String fingerprint) {
        String value = BuildConfig.VERSION_CODE + "\u0000" + fingerprint;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
