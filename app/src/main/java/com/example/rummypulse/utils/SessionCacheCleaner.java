package com.example.rummypulse.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.data.GameDefaultsRepository;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.PlayerLeaderboardRepository;
import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Wipes disposable session caches without deleting durable unsynced game edits or drafts.
 */
public final class SessionCacheCleaner {

    private static final String TAG = "SessionCacheCleaner";

    static final String PREFS_MEMBERSHIP_BACKFILL = "rummypulse_membership_backfill";

    private SessionCacheCleaner() {
    }

    public static void clearAll(Context context) {
        clearAll(context, null);
    }

    /**
     * Clears session caches. When {@code onComplete} is supplied, it runs on the main thread after
     * Glide's disk cache has been wiped so the next sign-in cannot reuse stale avatar bytes.
     */
    public static void clearAll(Context context, @Nullable Runnable onComplete) {
        Context appContext = context.getApplicationContext();
        Log.d(TAG, "Clearing session caches");

        GameRepository.getDashboardInstance().clearSessionState();
        AppUserRepository.clearSessionCaches();
        PlayerLeaderboardRepository.getInstance().clearSessionState();
        AppUserRoleSession.getInstance().clearSessionData();
        GameDefaultsRepository.getInstance(appContext).clearSessionCache();
        AuthStateManager.getInstance(appContext).clearAuthState();
        CurrentUserProfileSession.clear();
        PendingProfileOverrides.clear();
        clearUserScopedPreferences(appContext);
        clearFirestorePersistence();
        clearGlideCaches(appContext, onComplete);
    }

    private static void clearUserScopedPreferences(Context context) {
        // Keep unsynced edits and their edit-session credentials for same-account recovery.
        context.getSharedPreferences(PREFS_MEMBERSHIP_BACKFILL, Context.MODE_PRIVATE).edit().clear().apply();
        SafePlayPolicyStore.clearAll(context);
    }

    private static void clearGlideCaches(Context context, @Nullable Runnable onComplete) {
        Context appContext = context.getApplicationContext();
        Glide.get(appContext).clearMemory();
        new Thread(() -> {
            Glide.get(appContext).clearDiskCache();
            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).post(onComplete);
            }
        }, "glide-cache-clear").start();
    }

    private static void clearFirestorePersistence() {
        try {
            FirebaseFirestore.getInstance()
                    .clearPersistence()
                    .addOnSuccessListener(unused ->
                            Log.d(TAG, "Firestore offline cache cleared"))
                    .addOnFailureListener(error ->
                            Log.w(TAG, "Could not clear Firestore offline cache", error));
        } catch (Exception exception) {
            Log.w(TAG, "Firestore clearPersistence failed", exception);
        }
    }
}
