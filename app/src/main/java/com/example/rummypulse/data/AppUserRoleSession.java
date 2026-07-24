package com.example.rummypulse.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

/**
 * Publishes the signed-in user's cached role immediately and refreshes it with a one-time Firestore
 * server read. The cached role controls UI only; Firestore security rules remain authoritative.
 */
public final class AppUserRoleSession {

    private static final String TAG = "AppUserRoleSession";
    private static final String CACHE_NAME = "app_user_role_cache";
    private static final String CACHE_KEY_PREFIX = "role_";
    private static final long MIN_REFRESH_INTERVAL_MS = 60_000L;

    public enum Role {
        /** No cached role exists and the first server read is still pending. */
        UNKNOWN,
        ADMIN,
        NON_ADMIN
    }

    private static AppUserRoleSession instance;

    private final MutableLiveData<Role> role = new MutableLiveData<>(Role.NON_ADMIN);
    private SharedPreferences preferences;
    private String currentUid;
    private String refreshUid;
    private boolean refreshInProgress;
    private boolean forceRefreshQueued;
    private long lastRefreshStartedAt;
    private int sessionGeneration;

    private AppUserRoleSession() {}

    public static synchronized AppUserRoleSession getInstance() {
        if (instance == null) {
            instance = new AppUserRoleSession();
        }
        return instance;
    }

    public synchronized void initialize(Context context) {
        if (preferences == null) {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE);
        }
    }

    public LiveData<Role> getRole() {
        return role;
    }

    @Nullable
    public Role peekRole() {
        return role.getValue();
    }

    /**
     * Binds to the current account, publishes its cached role, and starts one background refresh.
     * Repeated calls for the same account do not reset the visible role.
     */
    public void startForCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            stop();
            return;
        }

        String uid = user.getUid();
        synchronized (this) {
            if (!uid.equals(currentUid)) {
                currentUid = uid;
                sessionGeneration++;
                refreshInProgress = false;
                refreshUid = null;
                forceRefreshQueued = false;
                lastRefreshStartedAt = 0L;
                Role cachedRole = readCachedRole(uid);
                publishRole(cachedRole != null ? cachedRole : Role.UNKNOWN);
                Log.d(TAG, cachedRole != null
                        ? "Published cached role for " + uid
                        : "No cached role for " + uid);
            }
        }
        refreshForCurrentUser();
    }

    /**
     * Refreshes the current role from the Firestore server. Rapid resumes share one read and are
     * rate-limited so permission dialogs or activity transitions do not cause repeated reads.
     */
    public void refreshForCurrentUser() {
        refreshForCurrentUser(false);
    }

    /**
     * Forces a refresh when initialization has just created a previously missing user document.
     */
    public void refreshForCurrentUser(boolean force) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            stop();
            return;
        }

        String uid = user.getUid();
        final int generation;
        synchronized (this) {
            if (!uid.equals(currentUid)) {
                currentUid = uid;
                sessionGeneration++;
                refreshInProgress = false;
                refreshUid = null;
                forceRefreshQueued = false;
                lastRefreshStartedAt = 0L;
                Role cachedRole = readCachedRole(uid);
                publishRole(cachedRole != null ? cachedRole : Role.UNKNOWN);
            }

            long elapsed = SystemClock.elapsedRealtime() - lastRefreshStartedAt;
            if (refreshInProgress && uid.equals(refreshUid)) {
                if (force) {
                    forceRefreshQueued = true;
                }
                Log.d(TAG, "Role refresh already in progress for " + uid);
                return;
            }
            if (!force && lastRefreshStartedAt > 0L
                    && elapsed >= 0 && elapsed < MIN_REFRESH_INTERVAL_MS) {
                Log.d(TAG, "Skipping duplicate role refresh for " + uid);
                return;
            }

            refreshInProgress = true;
            refreshUid = uid;
            lastRefreshStartedAt = SystemClock.elapsedRealtime();
            generation = sessionGeneration;
        }

        long startedAt = SystemClock.elapsedRealtime();
        FirebaseFirestore.getInstance()
                .collection(FirestoreCollections.APP_USER)
                .document(uid)
                .get(Source.SERVER)
                .addOnCompleteListener(task -> {
                    boolean runQueuedRefresh;
                    synchronized (AppUserRoleSession.this) {
                        if (uid.equals(refreshUid)) {
                            refreshInProgress = false;
                            refreshUid = null;
                        }
                        if (generation != sessionGeneration || !uid.equals(currentUid)) {
                            Log.d(TAG, "Ignoring role result for an inactive account");
                            return;
                        }
                        runQueuedRefresh = forceRefreshQueued;
                        forceRefreshQueued = false;
                    }

                    long elapsed = SystemClock.elapsedRealtime() - startedAt;
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Role refresh failed after " + elapsed
                                + " ms; retaining cached UI role", task.getException());
                        if (readCachedRole(uid) == null) {
                            publishRole(Role.NON_ADMIN);
                        }
                    } else {
                        DocumentSnapshot snapshot = task.getResult();
                        if (snapshot == null || !snapshot.exists()) {
                            Log.w(TAG, "No appUser document found for " + uid);
                            if (readCachedRole(uid) == null) {
                                publishRole(Role.UNKNOWN);
                            }
                        } else {
                            UserRole userRole = UserRole.fromString(snapshot.getString("role"));
                            Role refreshedRole = userRole == UserRole.ADMIN_USER
                                    ? Role.ADMIN
                                    : Role.NON_ADMIN;
                            cacheRole(uid, refreshedRole);
                            publishRole(refreshedRole);
                            Log.d(TAG, "Role refreshed from server in " + elapsed + " ms: "
                                    + refreshedRole);
                        }
                    }

                    if (runQueuedRefresh) {
                        refreshForCurrentUser(true);
                    }
                });
    }

    public synchronized boolean hasCachedRoleForCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && readCachedRole(user.getUid()) != null;
    }

    /**
     * Resets the active UI role on sign-out. Per-user cache entries are retained for fast startup
     * if the same account signs in again.
     */
    public synchronized void stop() {
        sessionGeneration++;
        currentUid = null;
        refreshUid = null;
        refreshInProgress = false;
        forceRefreshQueued = false;
        lastRefreshStartedAt = 0L;
        publishRole(Role.NON_ADMIN);
    }

    /**
     * Forces the next start to bind the current account again.
     */
    public synchronized void resetBinding() {
        sessionGeneration++;
        currentUid = null;
        refreshUid = null;
        refreshInProgress = false;
        forceRefreshQueued = false;
        lastRefreshStartedAt = 0L;
    }

    @Nullable
    private Role readCachedRole(String uid) {
        SharedPreferences cache = preferences;
        if (cache == null) {
            Log.w(TAG, "Role cache read before initialization");
            return null;
        }
        String value = cache.getString(CACHE_KEY_PREFIX + uid, null);
        if (Role.ADMIN.name().equals(value)) {
            return Role.ADMIN;
        }
        if (Role.NON_ADMIN.name().equals(value)) {
            return Role.NON_ADMIN;
        }
        return null;
    }

    private void cacheRole(String uid, Role refreshedRole) {
        SharedPreferences cache = preferences;
        if (cache == null) {
            Log.w(TAG, "Role cache write before initialization");
            return;
        }
        cache.edit()
                .putString(CACHE_KEY_PREFIX + uid, refreshedRole.name())
                .apply();
    }

    private void publishRole(Role newRole) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            role.setValue(newRole);
        } else {
            role.postValue(newRole);
        }
    }
}
