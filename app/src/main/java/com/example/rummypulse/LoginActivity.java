package com.example.rummypulse;

import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.gms.tasks.OnCompleteListener;
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

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_gate_loading);
        VersionGate.runWhenAllowed(this, () -> setupLoginUi(savedInstanceState));
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
        // Show loading state
        binding.signInButton.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);
        
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
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
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            
                            if (user != null) {
                                Log.d(TAG, "Firebase Auth successful for user: " + user.getEmail());
                                AuthStateManager.getInstance(LoginActivity.this).saveAuthState(user);
                                com.example.rummypulse.utils.ModernToast.success(LoginActivity.this,
                                        "Welcome, " + user.getDisplayName() + "!");
                                startMainActivity();
                            } else {
                                startMainActivity();
                            }
                        } else {
                            Exception ex = task.getException();
                            Log.w(TAG, "signInWithCredential:failure", ex);
                            String detail = ex != null && ex.getMessage() != null ? ex.getMessage() : "unknown";
                            showError("Sign-in failed: " + truncateForToast(detail));
                            resetUI();
                        }
                    }
                });
    }

    private void startMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        com.example.rummypulse.utils.ModernToast.error(this, message);
    }

    private void resetUI() {
        binding.signInButton.setEnabled(true);
        binding.progressBar.setVisibility(View.GONE);
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
