package com.example.rummypulse.utils;

import android.content.Intent;
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

    public static final String KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code";
    public static final String KEY_UPDATE_URL = "update_url";

    private VersionGate() {}

    /**
     * After {@link AppCompatActivity#setContentView} with a loading layout, call this and run
     * {@code onAllowed} on the main thread when the user may proceed.
     */
    public static void runWhenAllowed(@NonNull final AppCompatActivity activity, @NonNull final Runnable onAllowed) {
        final FirebaseRemoteConfig rc = FirebaseRemoteConfig.getInstance();

        Map<String, Object> defaults = new HashMap<>();
        defaults.put(KEY_MIN_SUPPORTED_VERSION_CODE, 0L);
        defaults.put(KEY_UPDATE_URL, activity.getString(R.string.default_apk_download_page));

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
        activity.runOnUiThread(onAllowed);
    }
}
