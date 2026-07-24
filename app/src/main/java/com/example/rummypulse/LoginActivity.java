package com.example.rummypulse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.example.rummypulse.utils.AuthStateManager;
import com.example.rummypulse.utils.VersionGate;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    private static final long SIGN_IN_SLOW_NETWORK_MS = 15_000L;

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private final Handler loginHandler = new Handler(Looper.getMainLooper());
    private Runnable slowNetworkNotice;
    private boolean googleSignInInProgress;
    private long startupStartedAt;
    private long googleSignInStartedAt;
    private AuthAttempt authAttempt;

    private static final class AuthAttempt {
        final String idToken;
        final Task<AuthResult> task;
        final long startedAt;
        boolean retryQueued;

        AuthAttempt(String idToken, Task<AuthResult> task, long startedAt) {
            this.idToken = idToken;
            this.task = task;
            this.startedAt = startedAt;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startupStartedAt = SystemClock.elapsedRealtime();
        if (VersionGate.redirectIfCachedVersionRequiresUpdate(this)) {
            return;
        }
        setupLoginUi(savedInstanceState);
        Log.d(TAG, "Login UI shown in "
                + (SystemClock.elapsedRealtime() - startupStartedAt) + " ms");
        VersionGate.refreshInBackground(this);
    }

    private void setupLoginUi(Bundle savedInstanceState) {
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Web client ID must match Firebase (merged from app/google-services.json).
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Set click listener for Google Sign-In button
        binding.signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn();
            }
        });
        binding.retryButton.setOnClickListener(v -> retrySignIn());

        // Check if user is already signed in with better logging
        FirebaseUser currentUser = mAuth.getCurrentUser();
        AuthStateManager authStateManager = AuthStateManager.getInstance(this);
        
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: " + currentUser.getEmail());
            authStateManager.saveAuthState(currentUser);
            startMainActivity();
        } else {
            Log.d(TAG, "No user currently signed in");
            
            // Check if user should be authenticated (might be force stop issue)
            if (authStateManager.shouldBeAuthenticated()) {
                Log.w(TAG, "User should be authenticated but isn't - possible force stop issue");
                Log.w(TAG, "Expected user: " + authStateManager.getBackedUpUserEmail());
                
                // Show message about session interruption
                com.example.rummypulse.utils.ModernToast.warning(this, 
                    "Your session was interrupted. Please sign in again.");
            }
        }
    }

    private void signIn() {
        if (googleSignInInProgress || hasPendingAuthAttempt()) {
            return;
        }
        googleSignInInProgress = true;
        googleSignInStartedAt = SystemClock.elapsedRealtime();
        // Show loading state
        binding.signInButton.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginStatus.setText(R.string.login_status_choose_account);
        binding.loginStatus.setVisibility(View.VISIBLE);
        binding.retryButton.setVisibility(View.GONE);
        
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            googleSignInInProgress = false;
            Log.d(TAG, "Google sign-in returned in "
                    + (SystemClock.elapsedRealtime() - googleSignInStartedAt) + " ms");
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                String idToken = account.getIdToken();
                if (idToken == null || idToken.isEmpty()) {
                    Log.e(TAG, "Google account has no ID token (check Web client / SHA-1 in Firebase).");
                    showError("Sign-in could not get a secure token. Check network or Firebase app setup.");
                    resetUI();
                    return;
                }
                firebaseAuthWithGoogle(idToken);
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed status=" + e.getStatusCode(), e);
                showError(messageForGoogleSignInFailure(e.getStatusCode()));
                resetUI();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (hasPendingAuthAttempt()) {
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        long startedAt = SystemClock.elapsedRealtime();
        authAttempt = new AuthAttempt(
                idToken,
                mAuth.signInWithCredential(credential),
                startedAt);
        observeAuthAttempt(authAttempt);
    }

    private void observeAuthAttempt(@NonNull AuthAttempt attempt) {
        showFirebaseAuthInProgress();
        scheduleSlowNetworkNotice(attempt);
        attempt.task.addOnCompleteListener(this, task -> handleAuthComplete(attempt, task));
    }

    private void handleAuthComplete(
            @NonNull AuthAttempt completedAttempt,
            @NonNull Task<AuthResult> task) {
        if (completedAttempt != authAttempt) {
            return;
        }
        cancelSlowNetworkNotice();
        Log.d(TAG, "Firebase authentication completed in "
                + (SystemClock.elapsedRealtime() - completedAttempt.startedAt) + " ms");
        authAttempt = null;

        if (task.isSuccessful()) {
            Log.d(TAG, "signInWithCredential:success");
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                Log.d(TAG, "Firebase Auth successful for user: " + user.getEmail());
                AuthStateManager.getInstance(LoginActivity.this).saveAuthState(user);
                com.example.rummypulse.utils.ModernToast.success(
                        LoginActivity.this,
                        "Welcome, " + user.getDisplayName() + "!");
            }
            startMainActivity();
            return;
        }

        Exception exception = task.getException();
        Log.w(TAG, "signInWithCredential:failure", exception);
        if (completedAttempt.retryQueued) {
            binding.loginStatus.setText(R.string.login_status_retrying);
            firebaseAuthWithGoogle(completedAttempt.idToken);
            return;
        }

        String detail = exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : "unknown";
        showError("Sign-in failed: " + truncateForToast(detail));
        showRetryState(R.string.login_status_failed);
    }

    private void scheduleSlowNetworkNotice(@NonNull AuthAttempt attempt) {
        cancelSlowNetworkNotice();
        slowNetworkNotice = () -> {
            if (authAttempt != attempt || attempt.task.isComplete()) {
                return;
            }
            binding.loginStatus.setText(R.string.login_status_slow_network);
            binding.loginStatus.setVisibility(View.VISIBLE);
            binding.retryButton.setEnabled(true);
            binding.retryButton.setVisibility(View.VISIBLE);
            Log.w(TAG, "Firebase authentication exceeded "
                    + SIGN_IN_SLOW_NETWORK_MS + " ms");
        };
        loginHandler.postDelayed(slowNetworkNotice, SIGN_IN_SLOW_NETWORK_MS);
    }

    private void cancelSlowNetworkNotice() {
        if (slowNetworkNotice != null) {
            loginHandler.removeCallbacks(slowNetworkNotice);
            slowNetworkNotice = null;
        }
    }

    private void retrySignIn() {
        if (hasPendingAuthAttempt()) {
            authAttempt.retryQueued = true;
            binding.loginStatus.setText(R.string.login_status_retry_queued);
            binding.retryButton.setEnabled(false);
            return;
        }
        signIn();
    }

    private boolean hasPendingAuthAttempt() {
        return authAttempt != null && !authAttempt.task.isComplete();
    }

    private void showFirebaseAuthInProgress() {
        binding.signInButton.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginStatus.setText(R.string.login_status_signing_in);
        binding.loginStatus.setVisibility(View.VISIBLE);
        binding.retryButton.setVisibility(View.GONE);
    }

    private void startMainActivity() {
        Log.d(TAG, "Opening MainActivity "
                + (SystemClock.elapsedRealtime() - startupStartedAt)
                + " ms after LoginActivity start");
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        com.example.rummypulse.utils.ModernToast.error(this, message);
    }

    private void resetUI() {
        googleSignInInProgress = false;
        cancelSlowNetworkNotice();
        binding.signInButton.setEnabled(true);
        binding.progressBar.setVisibility(View.GONE);
        binding.loginStatus.setVisibility(View.GONE);
        binding.retryButton.setVisibility(View.GONE);
        binding.retryButton.setEnabled(true);
    }

    private void showRetryState(int statusText) {
        cancelSlowNetworkNotice();
        binding.signInButton.setEnabled(true);
        binding.progressBar.setVisibility(View.GONE);
        binding.loginStatus.setText(statusText);
        binding.loginStatus.setVisibility(View.VISIBLE);
        binding.retryButton.setEnabled(true);
        binding.retryButton.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        cancelSlowNetworkNotice();
        super.onDestroy();
    }

    private static String truncateForToast(String message) {
        if (message == null) {
            return "";
        }
        int max = 180;
        return message.length() <= max ? message : message.substring(0, max) + "…";
    }

    /**
     * User-visible hint for {@link ApiException#getStatusCode()} from Google Sign-In.
     */
    private static String messageForGoogleSignInFailure(int statusCode) {
        // Status codes: https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes
        switch (statusCode) {
            case 12501: // SIGN_IN_CANCELLED
                return "Sign-in was cancelled.";
            case com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR:
                return "Network error. Check connection and try again.";
            case ConnectionResult.DEVELOPER_ERROR:
                return "Sign-in setup error (code 10). Add this app’s SHA-1 in Firebase Console → Project settings → Your apps.";
            case com.google.android.gms.common.api.CommonStatusCodes.INTERNAL_ERROR:
                return "Google Play services error. Update Play services and try again.";
            default:
                return "Google Sign-In failed (code " + statusCode + "). Try again or check Firebase / network.";
        }
    }
}
