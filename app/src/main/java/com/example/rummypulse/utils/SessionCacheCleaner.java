package com.example.rummypulse.utils;

import android.content.Context;
import android.util.Log;

import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.data.GameDefaultsRepository;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.PlayerLeaderboardRepository;
import com.example.rummypulse.data.sync.GameOperationDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Wipes session-scoped caches when the signed-in account changes so the next user never inherits
 * games, users, drafts, or offline Firestore data from the previous session.
 */
public final class SessionCacheCleaner {

    private static final String TAG = "SessionCacheCleaner";

    static final String PREFS_EDIT_ACCESS = "RummyPulse_EditAccess";
    static final String PREFS_ROUND_DRAFTS = "round_score_drafts";
    static final String PREFS_PENDING_ROUNDS = "pending_round_scores";
    static final String PREFS_MEMBERSHIP_BACKFILL = "rummypulse_membership_backfill";

    private SessionCacheCleaner() {
    }

    public static void clearAll(Context context) {
        Context appContext = context.getApplicationContext();
        Log.d(TAG, "Clearing session caches");

        GameRepository.getDashboardInstance().clearSessionState();
        AppUserRepository.clearSessionCaches();
        PlayerLeaderboardRepository.getInstance().clearSessionState();
        AppUserRoleSession.getInstance().clearSessionData();
        GameDefaultsRepository.getInstance(appContext).clearSessionCache();
        AuthStateManager.getInstance(appContext).clearAuthState();
        clearUserScopedPreferences(appContext);
        GameOperationDatabase.clearSessionData(appContext);
        clearFirestorePersistence();
    }

    private static void clearUserScopedPreferences(Context context) {
        context.getSharedPreferences(PREFS_EDIT_ACCESS, Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences(PREFS_ROUND_DRAFTS, Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences(PREFS_PENDING_ROUNDS, Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences(PREFS_MEMBERSHIP_BACKFILL, Context.MODE_PRIVATE).edit().clear().apply();
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
