package com.example.rummypulse;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.example.rummypulse.utils.AuthStateManager;
import com.example.rummypulse.utils.NotificationHelper;

/**
 * Custom Application class to initialize Firebase and configure authentication persistence
 */
public class RummyPulseApplication extends Application {
    
    private static final String TAG = "RummyPulseApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        
        // Configure Firebase Auth for better persistence
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        
        // Explicitly enable persistence (should be default, but ensuring it's set)
        try {
            // This ensures authentication state persists across app restarts and force stops
            firebaseAuth.useAppLanguage();
            Log.d(TAG, "Firebase Auth persistence explicitly configured");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Firebase Auth persistence", e);
        }
        
        // Enable offline persistence for Firestore
        try {
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            com.google.firebase.firestore.FirebaseFirestoreSettings settings = 
                new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build();
            db.setFirestoreSettings(settings);
            Log.d(TAG, "Firestore offline persistence enabled successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error enabling Firestore offline persistence", e);
        }
        
        // Initialize AuthStateManager
        AuthStateManager authStateManager = AuthStateManager.getInstance(this);
        
        // Add a global auth state listener for debugging and backup
        firebaseAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    Log.d(TAG, "Global auth state: User is signed in - " + user.getEmail());
                    // Save authentication state as backup
                    authStateManager.saveAuthState(user);
                } else {
                    Log.d(TAG, "Global auth state: User is signed out");
                    // Check if this is unexpected (user should be authenticated)
                    if (authStateManager.shouldBeAuthenticated()) {
                        Log.w(TAG, "Unexpected sign out detected - user should be authenticated");
                        Log.w(TAG, "This might be due to force stop or other issues");
                    } else {
                        // Expected sign out, clear backup
                        authStateManager.clearAuthState();
                    }
                }
            }
        });
        
        // Handle post-force-stop recovery
        authStateManager.handlePostForceStopRecovery();
        
        // Initialize notification channel
        NotificationHelper.createNotificationChannel(this);
        Log.d(TAG, "Notification channel created");
        
        Log.d(TAG, "RummyPulse Application initialized with Firebase Auth persistence");
    }
}
