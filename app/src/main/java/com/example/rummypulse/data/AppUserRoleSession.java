package com.example.rummypulse.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Observes {@code appUser/{uid}} for the signed-in user so Review / drawer can update when the
 * document appears after fast login (Main opens before Firestore sync finishes).
 */
public final class AppUserRoleSession {

    private static final String TAG = "AppUserRoleSession";
    private static final String COLLECTION = "appUser";
    private static final long UNKNOWN_TIMEOUT_MS = 12_000L;

    public enum Role {
        /** Document not yet visible or first snapshot pending. */
        UNKNOWN,
        ADMIN,
        NON_ADMIN
    }

    private static AppUserRoleSession instance;

    private final MutableLiveData<Role> role = new MutableLiveData<>(Role.NON_ADMIN);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ListenerRegistration registration;
    private String listeningUid;
    private Runnable unknownTimeout;

    private AppUserRoleSession() {}

    public static synchronized AppUserRoleSession getInstance() {
        if (instance == null) {
            instance = new AppUserRoleSession();
        }
        return instance;
    }

    public LiveData<Role> getRole() {
        return role;
    }

    @Nullable
    public Role peekRole() {
        return role.getValue();
    }

    /**
     * Start listening for the current Firebase user's appUser document. Safe to call repeatedly
     * (re-binds if uid changed). Call from Main after auth is confirmed.
     */
    public void startForCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cancelTimeout();
            stopListenerOnly();
            listeningUid = null;
            role.postValue(Role.NON_ADMIN);
            return;
        }
        String uid = user.getUid();
        if (uid.equals(listeningUid) && registration != null) {
            return;
        }
        stopListenerOnly();
        cancelTimeout();
        listeningUid = uid;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            role.setValue(Role.UNKNOWN);
        } else {
            role.postValue(Role.UNKNOWN);
        }

        DocumentReference ref = FirebaseFirestore.getInstance().collection(COLLECTION).document(uid);
        registration = ref.addSnapshotListener((DocumentSnapshot snapshot, FirebaseFirestoreException error) -> {
            if (error != null) {
                Log.w(TAG, "appUser snapshot error", error);
                role.postValue(Role.NON_ADMIN);
                cancelTimeout();
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            UserRole userRole = UserRole.fromString(snapshot.getString("role"));
            role.postValue(userRole == UserRole.ADMIN_USER ? Role.ADMIN : Role.NON_ADMIN);
            cancelTimeout();
        });
        scheduleUnknownTimeout();
    }

    private void scheduleUnknownTimeout() {
        cancelTimeout();
        unknownTimeout = () -> {
            Role current = role.getValue();
            if (current == Role.UNKNOWN) {
                Log.w(TAG, "appUser role still unknown after timeout; treating as non-admin for UI");
                role.postValue(Role.NON_ADMIN);
            }
        };
        mainHandler.postDelayed(unknownTimeout, UNKNOWN_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (unknownTimeout != null) {
            mainHandler.removeCallbacks(unknownTimeout);
            unknownTimeout = null;
        }
    }

    private void stopListenerOnly() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    /**
     * Stop listener and reset role (e.g. on sign-out).
     */
    public void stop() {
        cancelTimeout();
        stopListenerOnly();
        listeningUid = null;
        role.postValue(Role.NON_ADMIN);
    }

    /**
     * Force next {@link #startForCurrentUser()} to re-attach (e.g. after account switch).
     */
    public void resetBinding() {
        stopListenerOnly();
        listeningUid = null;
    }
}
