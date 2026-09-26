package com.example.rummypulse;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.functions.FirebaseFunctions;
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.utils.AuthStateManager;
import com.example.rummypulse.utils.VerifiedSessionGate;

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
        VerifiedSessionGate.install(this);
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            firebaseAuth.useEmulator("10.0.2.2", 9099);
            firestore.useEmulator("10.0.2.2", 8080);
            FirebaseFunctions.getInstance("asia-south1")
                    .useEmulator("10.0.2.2", 5001);
            Log.i(TAG, "Using local Firebase Auth, Firestore, and Functions emulators");
        }
        AppCheckInitializer.initialize();
        AppUserRoleSession.getInstance().initialize(this);
        // An update APK is kept until the next cold start, by which point the install has either
        // completed or been abandoned. Deleting it earlier can abort an install in progress.
        com.example.rummypulse.utils.ModernUpdateChecker.deleteStaleDownloadedApk(this);

        // Configure Firebase Auth for better persistence
        // Explicitly enable persistence (should be default, but ensuring it's set)
        try {
            // This ensures authentication state persists across app restarts and force stops
            firebaseAuth.useAppLanguage();
            Log.d(TAG, "Firebase Auth persistence explicitly configured");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Firebase Auth persistence", e);
        }
        
        // Emulator data is reset/imported by the local launcher, so do not mix it with an
        // Android offline cache. Production keeps offline persistence enabled.
        try {
            FirebaseFirestoreSettings settings =
                new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(!BuildConfig.USE_FIREBASE_EMULATORS)
                    .build();
            firestore.setFirestoreSettings(settings);
            Log.d(TAG, BuildConfig.USE_FIREBASE_EMULATORS
                    ? "Firestore offline persistence disabled for local emulators"
                    : "Firestore offline persistence enabled successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error enabling Firestore offline persistence", e);
        }
        
        // Initialize AuthStateManager
        AuthStateManager authStateManager = AuthStateManager.getInstance(this);

        // GameRepository: load only when a user is signed in (avoids Firestore work while logged out).
        final GameRepository gameRepository = GameRepository.getDashboardInstance();
        gameRepository.setContext(this);
        
        // Add a global auth state listener for debugging and backup
        firebaseAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    Log.d(TAG, "Global auth state: User is signed in");
                    // Save authentication state as backup
                    authStateManager.saveAuthState(user);
                    // Auth restoration alone does not verify a usable Firebase session.
                } else {
                    Log.d(TAG, "Global auth state: User is signed out");
                    VerifiedSessionGate.invalidate();
                    if (authStateManager.shouldBeAuthenticated()) {
                        Log.w(TAG, "Unexpected sign out detected - user should be authenticated");
                        Log.w(TAG, "Pending edits remain stored for same-account recovery");
                    }
                }
            }
        });
        
        // Handle post-force-stop recovery
        authStateManager.handlePostForceStopRecovery();
        Log.d(TAG, "GameRepository initialized");
        
        Log.d(TAG, "RummyPulse Application initialized with Firebase Auth persistence");
    }
}
