package com.example.rummypulse;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserRepository;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private AppUserRepository appUserRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize AppUser Repository
        appUserRepository = new AppUserRepository();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("740379545501-g1h84gk3d1hrq0egmpp78tulcuk0igbc.apps.googleusercontent.com")
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

        // Check if user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already signed in, update appUser and go to main activity
            String provider = AppUserRepository.getProviderName(currentUser);
            appUserRepository.createOrUpdateUser(currentUser, provider, new AppUserRepository.AppUserCallback() {
                @Override
                public void onSuccess(AppUser appUser) {
                    Log.d(TAG, "AppUser updated on app startup: " + appUser.toString());
                    startMainActivity();
                }
                
                @Override
                public void onFailure(Exception exception) {
                    Log.e(TAG, "Failed to update AppUser on app startup", exception);
                    // Still proceed to main activity
                    startMainActivity();
                }
            });
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
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e);
                showError("Google Sign-In failed. Please try again.");
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
                                // Create or update user in appUser collection
                                String provider = AppUserRepository.getProviderName(user);
                                appUserRepository.createOrUpdateUser(user, provider, new AppUserRepository.AppUserCallback() {
                                    @Override
                                    public void onSuccess(AppUser appUser) {
                                        Log.d(TAG, "AppUser created/updated successfully: " + appUser.toString());
                                        
                                        // Welcome message
                                        Toast.makeText(LoginActivity.this, 
                                            "Welcome, " + user.getDisplayName() + "!", 
                                            Toast.LENGTH_SHORT).show();
                                        
                                        // Go to main activity
                                        startMainActivity();
                                    }
                                    
                                    @Override
                                    public void onFailure(Exception exception) {
                                        Log.e(TAG, "Failed to create/update AppUser", exception);
                                        
                                        // Still proceed to main activity even if appUser creation fails
                                        // Welcome message
                                        Toast.makeText(LoginActivity.this, 
                                            "Welcome, " + user.getDisplayName() + "!", 
                                            Toast.LENGTH_SHORT).show();
                                        
                                        // Go to main activity
                                        startMainActivity();
                                    }
                                });
                            } else {
                                // Go to main activity even if user is null (shouldn't happen)
                                startMainActivity();
                            }
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            showError("Authentication failed. Please try again.");
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void resetUI() {
        binding.signInButton.setEnabled(true);
        binding.progressBar.setVisibility(View.GONE);
    }
}
