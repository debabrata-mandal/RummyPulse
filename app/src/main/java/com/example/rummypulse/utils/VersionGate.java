package com.example.rummypulse.utils;

import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.BuildConfig;
import com.example.rummypulse.MinimumVersionActivity;
import com.example.rummypulse.R;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Enforces cached mandatory-update configuration without delaying startup, then refreshes Remote
 * Config in the background. Network failures fail open so an outage cannot prevent app startup.
 */
public final class VersionGate {

    private static final String TAG = "VersionGate";
    private static final long FETCH_INTERVAL_SECONDS = 60L * 60L;
    private static final long FETCH_TIMEOUT_SECONDS = 10L;

    private static final Object REFRESH_LOCK = new Object();
    private static boolean refreshInProgress;
    private static WeakReference<AppCompatActivity> latestActivity = new WeakReference<>(null);

    public static final String KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code";
    public static final String KEY_UPDATE_URL = "update_url";

    private VersionGate() {}

    /**
     * Checks the last activated Remote Config value synchronously.
     *
     * @return true when the activity was redirected to the mandatory-update screen.
     */
    public static boolean redirectIfCachedVersionRequiresUpdate(
            @NonNull AppCompatActivity activity) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG build: skipping cached minimum-version gate");
            return false;
        }

        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        long startedAt = SystemClock.elapsedRealtime();
        boolean redirected = redirectIfUpdateRequired(activity, remoteConfig);
        Log.d(TAG, "Cached version check completed in "
                + (SystemClock.elapsedRealtime() - startedAt) + " ms");
        return redirected;
    }

    /**
     * Starts one process-wide fetch-and-activate operation. Repeated calls update the activity that
     * should receive a newly discovered mandatory-update redirect without starting another fetch.
     */
    public static void refreshInBackground(@NonNull AppCompatActivity activity) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG build: skipping background Remote Config refresh");
            return;
        }

        synchronized (REFRESH_LOCK) {
            latestActivity = new WeakReference<>(activity);
            if (refreshInProgress) {
                Log.d(TAG, "Remote Config refresh already running");
                return;
            }
            refreshInProgress = true;
        }

        long startedAt = SystemClock.elapsedRealtime();
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
                .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                .build();

        Map<String, Object> defaults = new HashMap<>();
        defaults.put(KEY_MIN_SUPPORTED_VERSION_CODE, 0L);
        defaults.put(
                KEY_UPDATE_URL,
                activity.getString(R.string.default_apk_download_page));

        remoteConfig.setConfigSettingsAsync(settings)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Remote Config settings failed; continuing", task.getException());
                    }
                    return remoteConfig.setDefaultsAsync(defaults);
                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Remote Config defaults failed; continuing", task.getException());
                    }
                    return remoteConfig.fetchAndActivate();
                })
                .addOnCompleteListener(task -> {
                    synchronized (REFRESH_LOCK) {
                        refreshInProgress = false;
                    }

                    long elapsed = SystemClock.elapsedRealtime() - startedAt;
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Background Remote Config refresh failed after "
                                + elapsed + " ms; keeping app available", task.getException());
                        return;
                    }

                    Log.d(TAG, "Background Remote Config refresh completed in "
                            + elapsed + " ms; activated=" + task.getResult());
                    AppCompatActivity target;
                    synchronized (REFRESH_LOCK) {
                        target = latestActivity.get();
                    }
                    if (target == null || target.isFinishing() || target.isDestroyed()) {
                        return;
                    }
                    target.runOnUiThread(() -> {
                        if (!target.isFinishing() && !target.isDestroyed()) {
                            redirectIfUpdateRequired(target, remoteConfig);
                        }
                    });
                });
    }

    private static boolean redirectIfUpdateRequired(
            @NonNull AppCompatActivity activity,
            @NonNull FirebaseRemoteConfig remoteConfig) {
        long minimumVersionCode = remoteConfig.getLong(KEY_MIN_SUPPORTED_VERSION_CODE);
        String updateUrl = remoteConfig.getString(KEY_UPDATE_URL);
        int currentVersionCode = BuildConfig.VERSION_CODE;
        Log.d(TAG, "Resolved app versionCode=" + currentVersionCode
                + " minimum=" + minimumVersionCode);

        if (minimumVersionCode <= 0 || currentVersionCode >= minimumVersionCode) {
            return false;
        }

        Log.w(TAG, "Version blocked: current=" + currentVersionCode
                + " minimum=" + minimumVersionCode);
        Intent intent = new Intent(activity, MinimumVersionActivity.class);
        intent.putExtra(MinimumVersionActivity.EXTRA_UPDATE_URL, updateUrl);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }
}
