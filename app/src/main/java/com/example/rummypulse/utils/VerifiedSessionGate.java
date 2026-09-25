package com.example.rummypulse.utils;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.rummypulse.LoginActivity;
import com.example.rummypulse.R;
import com.example.rummypulse.data.sync.GameOperationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Blocks authenticated screens until Firebase confirms the current session. */
public final class VerifiedSessionGate {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.Map<Activity, AlertDialog> ACTIVE_DIALOGS = new java.util.WeakHashMap<>();
    private static String verifiedUid;
    private static long verifiedAt;
    private static int startedActivities;
    private static Runnable backgroundReset;

    private VerifiedSessionGate() {}

    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {
                startedActivities++;
                if (backgroundReset != null) MAIN.removeCallbacks(backgroundReset);
            }
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (startedActivities == 0) {
                    backgroundReset = VerifiedSessionGate::invalidate;
                    MAIN.postDelayed(backgroundReset, 700);
                }
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    public static void markVerified(FirebaseUser user) {
        if (user == null) return;
        verifiedUid = user.getUid();
        verifiedAt = SystemClock.elapsedRealtime();
    }

    public static void invalidate() {
        verifiedUid = null;
        verifiedAt = 0L;
    }

    public static boolean isVerified(String uid) {
        return uid != null && uid.equals(verifiedUid)
                && SystemClock.elapsedRealtime() - verifiedAt < 45 * 60 * 1000L;
    }

    public static void require(Activity activity, Runnable onVerified) {
        AlertDialog active = ACTIVE_DIALOGS.get(activity);
        if (active != null && active.isShowing()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && isVerified(user.getUid())) {
            onVerified.run();
            return;
        }
        if (user == null || !CurrentUserProfileSession.belongsTo(user.getUid())) {
            CurrentUserProfileSession.clear();
        }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.rgb(18, 18, 35));
        ProgressBar progress = new ProgressBar(activity);
        content.addView(progress);
        TextView message = new TextView(activity);
        message.setGravity(Gravity.CENTER);
        message.setText("Checking your session…");
        message.setTextColor(Color.WHITE);
        content.addView(message);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.DarkDialogTheme)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton("Retry", null)
                .setNegativeButton("Sign in", null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.rgb(18, 18, 35)));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        ACTIVE_DIALOGS.put(activity, dialog);
        dialog.setOnDismissListener(ignored -> ACTIVE_DIALOGS.remove(activity));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(android.view.View.GONE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(android.view.View.GONE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> verify(activity, dialog, progress, message, onVerified));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            dialog.dismiss();
            invalidate();
            AccountSignOut.signOut(activity).addOnCompleteListener(ignored -> {
                SessionCacheCleaner.clearAll(activity, () -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Intent login = new Intent(activity, LoginActivity.class);
                    login.putExtra(LoginActivity.EXTRA_REQUIRE_LOGIN, true);
                    login.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(login);
                    activity.finish();
                });
            });
        });
        verify(activity, dialog, progress, message, onVerified);
    }

    private static void verify(Activity activity, AlertDialog dialog, ProgressBar progress,
            TextView message, Runnable onVerified) {
        if (!dialog.isShowing()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            progress.setVisibility(android.view.View.GONE);
            message.setText("Sign in to continue. Your pending game changes are kept on this device.");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(android.view.View.GONE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(android.view.View.VISIBLE);
            return;
        }
        progress.setVisibility(android.view.View.VISIBLE);
        message.setText("Checking your session…");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(android.view.View.GONE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(android.view.View.GONE);
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (task.isSuccessful() && current != null && user.getUid().equals(current.getUid())) {
                GameOperationRepository.getInstance(activity).verifyQueueOwner(current.getUid(), allowed -> {
                    if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                    if (allowed) {
                        CurrentUserProfileSession.restoreCachedName(activity, current.getUid());
                        markVerified(current);
                        dialog.dismiss();
                        onVerified.run();
                    } else {
                        progress.setVisibility(android.view.View.GONE);
                        message.setText("Unsynced edits belong to the original account. Sign in with that account to recover them.");
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(android.view.View.VISIBLE);
                    }
                });
                return;
            }
            progress.setVisibility(android.view.View.GONE);
            message.setText("Your session could not be verified. Check your connection and retry. Pending changes are safe on this device.");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(android.view.View.VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(android.view.View.VISIBLE);
        });
    }
}
