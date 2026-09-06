package com.example.rummypulse.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.rummypulse.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Ends both the Google account session and the Firebase Auth session. Callers must wait for the
 * returned task before opening {@link com.example.rummypulse.LoginActivity}, otherwise the login
 * screen may still see the previous Firebase user and skip straight back into the app.
 */
public final class AccountSignOut {

    private AccountSignOut() {
    }

    @NonNull
    public static Task<Void> signOut(@NonNull Context context) {
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(
                context.getApplicationContext(),
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build());
        FirebaseAuth.getInstance().signOut();
        return googleSignInClient.signOut();
    }
}
