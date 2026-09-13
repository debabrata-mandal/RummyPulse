package com.example.rummypulse.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Tracks a non-identifying hint that a Firebase session recently existed.
 * Firebase Authentication remains the only source of truth for authentication.
 */
public class AuthStateManager {
    
    private static final String TAG = "AuthStateManager";
    private static final String PREF_NAME = "auth_state_backup";
    private static final String LEGACY_KEY_USER_ID = "user_id";
    private static final String LEGACY_KEY_USER_EMAIL = "user_email";
    private static final String LEGACY_KEY_USER_NAME = "user_name";
    private static final String KEY_LAST_LOGIN = "last_login";
    private static final String KEY_IS_AUTHENTICATED = "is_authenticated";
    
    private static AuthStateManager instance;
    private final SharedPreferences prefs;
    
    private AuthStateManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        removeLegacyPersonalData();
    }
    
    public static synchronized AuthStateManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthStateManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Save a non-identifying recent-session hint.
     */
    public void saveAuthState(@NonNull FirebaseUser user) {
        SharedPreferences.Editor editor = prefs.edit();
        // Do not duplicate the Firebase UID, email address, or display name in preferences.
        editor.putLong(KEY_LAST_LOGIN, System.currentTimeMillis());
        editor.putBoolean(KEY_IS_AUTHENTICATED, true);
        editor.apply();
        
        Log.d(TAG, "Authentication state backed up");
    }

    private void removeLegacyPersonalData() {
        prefs.edit()
                .remove(LEGACY_KEY_USER_ID)
                .remove(LEGACY_KEY_USER_EMAIL)
                .remove(LEGACY_KEY_USER_NAME)
                .apply();
    }
    
    /**
     * Clear authentication state backup
     */
    public void clearAuthState() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        Log.d(TAG, "Authentication state backup cleared");
    }
    
    /**
     * Check if user should be authenticated based on backup state
     */
    public boolean shouldBeAuthenticated() {
        boolean isAuthenticated = prefs.getBoolean(KEY_IS_AUTHENTICATED, false);
        long lastLogin = prefs.getLong(KEY_LAST_LOGIN, 0);
        
        // Check if backup indicates user should be authenticated and it's recent (within 30 days)
        long thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000;
        boolean isRecent = (System.currentTimeMillis() - lastLogin) < thirtyDaysInMillis;
        
        Log.d(TAG, "Should be authenticated: " + isAuthenticated + ", Recent: " + isRecent);
        return isAuthenticated && isRecent;
    }
    
    /**
     * Handle authentication state recovery after force stop
     */
    public void handlePostForceStopRecovery() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (firebaseUser == null && shouldBeAuthenticated()) {
            Log.w(TAG, "Authentication state lost after force stop - user should be authenticated");
            Log.w(TAG, "Previous authentication state found");
            // In this case, we should redirect to login but show a message
            // that the session was restored
        } else if (firebaseUser != null) {
            Log.d(TAG, "Authentication state preserved after force stop");
            // Update backup state to reflect current state
            saveAuthState(firebaseUser);
        }
    }
}
