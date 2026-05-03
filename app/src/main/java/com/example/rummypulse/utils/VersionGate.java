package com.example.rummypulse.utils;

import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.BuildConfig;
import com.example.rummypulse.MinimumVersionActivity;
import com.example.rummypulse.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Fetches Firebase Remote Config and blocks the app when the installed {@link BuildConfig#VERSION_CODE}
 * is below {@code min_supported_version_code}. Fails open on fetch errors so a bad network or RC
 * outage does not brick the app.
 */
public final class VersionGate {

    private static final String TAG = "VersionGate";

    /** After a successful gate, {@link MainActivity} may skip a redundant RC fetch within this window. */
    private static final long SKIP_REMOTE_FETCH_WINDOW_MS = 5 * 60 * 1000L;

    private static final Object GATE_SKIP_LOCK = new Object();
    private static long lastGatePassedElapsedRealtimeMs;
    private static int lastGatePassedVersionCode = -1;

    public static final String KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code";
    public static final String KEY_UPDATE_URL = "update_url";

    private VersionGate() {}

    /**
     * After {@link AppCompatActivity#setContentView} with a loading layout, call this and run
     * {@code onAllowed} on the main thread when the user may proceed.
     */
    public static void runWhenAllowed(@NonNull final AppCompatActivity activity, @NonNull final Runnable onAllowed) {
        runWhenAllowed(activity, onAllowed, false);
    }

    /**
     * @param skipRemoteConfigFetchIfRecent when true, skips fetch/activate if this process recently passed
     *                                      the version gate for the same {@link BuildConfig#VERSION_CODE}
     *                                      (e.g. MainActivity immediately after LoginActivity).
     */
    public static void runWhenAllowed(
            @NonNull final AppCompatActivity activity,
            @NonNull final Runnable onAllowed,
            boolean skipRemoteConfigFetchIfRecent) {
        final FirebaseRemoteConfig rc = FirebaseRemoteConfig.getInstance();

        Map<String, Object> defaults = new HashMap<>();
        defaults.put(KEY_MIN_SUPPORTED_VERSION_CODE, 0L);
        defaults.put(KEY_UPDATE_URL, activity.getString(R.string.default_apk_download_page));

        if (skipRemoteConfigFetchIfRecent && canSkipRemoteConfigFetch()) {
            Log.d(TAG, "Skipping Remote Config fetch/activate (recent gate pass for same versionCode)");
            rc.setDefaultsAsync(defaults)
                    .addOnCompleteListener(activity, task -> applyGate(activity, rc, onAllowed));
            return;
        }

        rc.setDefaultsAsync(defaults)
                .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Remote Config setDefaults failed; continuing with fetch", task.getException());
                        }
                        // fetch(0) ignores the SDK throttle (e.g. 3600s in release). Without this, a device can keep
                        // an old min_supported_version_code for up to an hour after you publish a higher minimum.
                        rc.fetch(0L)
                                .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> fetchTask) {
                                        if (!fetchTask.isSuccessful()) {
                                            Log.w(TAG, "Remote Config fetch failed; allowing app", fetchTask.getException());
                                            recordGatePassedForSkip();
                                            activity.runOnUiThread(onAllowed);
                                            return;
                                        }
                                        rc.activate()
                                                .addOnCompleteListener(activity, new OnCompleteListener<Boolean>() {
                                                    @Override
                                                    public void onComplete(@NonNull Task<Boolean> activateTask) {
                                                        if (!activateTask.isSuccessful()) {
                                                            Log.w(TAG, "Remote Config activate failed; allowing app",
                                                                    activateTask.getException());
                                                            recordGatePassedForSkip();
                                                            activity.runOnUiThread(onAllowed);
                                                            return;
                                                        }
                                                        applyGate(activity, rc, onAllowed);
                                                    }
                                                });
                                    }
                                });
                    }
                });
    }

    private static boolean canSkipRemoteConfigFetch() {
        synchronized (GATE_SKIP_LOCK) {
            if (lastGatePassedVersionCode != BuildConfig.VERSION_CODE) {
                return false;
            }
            long elapsed = SystemClock.elapsedRealtime() - lastGatePassedElapsedRealtimeMs;
            return elapsed >= 0 && elapsed < SKIP_REMOTE_FETCH_WINDOW_MS;
        }
    }

    private static void recordGatePassedForSkip() {
        synchronized (GATE_SKIP_LOCK) {
            lastGatePassedElapsedRealtimeMs = SystemClock.elapsedRealtime();
            lastGatePassedVersionCode = BuildConfig.VERSION_CODE;
        }
    }

    private static void applyGate(@NonNull AppCompatActivity activity, FirebaseRemoteConfig rc, @NonNull Runnable onAllowed) {
        long minCode = rc.getLong(KEY_MIN_SUPPORTED_VERSION_CODE);
        String updateUrl = rc.getString(KEY_UPDATE_URL);
        int current = BuildConfig.VERSION_CODE;
        Log.d(TAG, "Resolved: app versionCode=" + current
                + " remote min_supported_version_code=" + minCode);
        if (minCode > 0 && current < minCode) {
            Log.w(TAG, "Version blocked: current=" + current + " min=" + minCode);
            Intent intent = new Intent(activity, MinimumVersionActivity.class);
            intent.putExtra(MinimumVersionActivity.EXTRA_UPDATE_URL, updateUrl);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
            return;
        }
        recordGatePassedForSkip();
        activity.runOnUiThread(onAllowed);
    }
}
