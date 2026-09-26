package com.example.rummypulse.utils;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

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
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_session_check, null, false);
        Views views = new Views(content);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.DarkDialogTheme)
                .setView(content)
                .setCancelable(false)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        ACTIVE_DIALOGS.put(activity, dialog);
        dialog.setOnDismissListener(ignored -> ACTIVE_DIALOGS.remove(activity));
        views.retry.setOnClickListener(v -> verify(activity, dialog, views, onVerified));
        views.signIn.setOnClickListener(v -> {
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
        verify(activity, dialog, views, onVerified);
    }

    /** Holds the session screen views and applies each visual state. */
    private static final class Views {
        final ImageView icon;
        final ProgressBar progress;
        final TextView title;
        final TextView message;
        final MaterialButton retry;
        final MaterialButton signIn;

        Views(View root) {
            icon = root.findViewById(R.id.image_session_icon);
            progress = root.findViewById(R.id.progress_session);
            title = root.findViewById(R.id.text_session_title);
            message = root.findViewById(R.id.text_session_message);
            retry = root.findViewById(R.id.btn_session_retry);
            signIn = root.findViewById(R.id.btn_session_sign_in);
        }

        void showChecking() {
            apply(R.drawable.ic_shield, R.color.accent_blue_light, true,
                    R.string.session_check_title_checking, R.string.session_check_message_checking,
                    false, false);
        }

        void showSignedOut() {
            apply(R.drawable.ic_person, R.color.accent_blue_light, false,
                    R.string.session_check_title_signed_out, R.string.session_check_message_signed_out,
                    false, true);
        }

        void showWrongAccount() {
            apply(R.drawable.ic_lock, R.color.warning_orange, false,
                    R.string.session_check_title_wrong_account, R.string.session_check_message_wrong_account,
                    false, true);
        }

        void showFailed() {
            apply(R.drawable.ic_refresh, R.color.warning_orange, false,
                    R.string.session_check_title_failed, R.string.session_check_message_failed,
                    true, true);
        }

        private void apply(int iconRes, int iconTint, boolean loading, int titleRes, int messageRes,
                boolean showRetry, boolean showSignIn) {
            icon.setImageResource(iconRes);
            androidx.core.widget.ImageViewCompat.setImageTintList(icon,
                    ColorStateList.valueOf(ContextCompat.getColor(icon.getContext(), iconTint)));
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            title.setText(titleRes);
            message.setText(messageRes);
            retry.setVisibility(showRetry ? View.VISIBLE : View.GONE);
            signIn.setVisibility(showSignIn ? View.VISIBLE : View.GONE);
            // When Sign in is the only action, make it the filled primary button.
            styleSignIn(showSignIn && !showRetry);
        }

        private void styleSignIn(boolean primary) {
            android.content.Context ctx = signIn.getContext();
            int accent = ContextCompat.getColor(ctx, R.color.accent_blue);
            float density = ctx.getResources().getDisplayMetrics().density;
            if (primary) {
                signIn.setBackgroundTintList(ColorStateList.valueOf(accent));
                signIn.setTextColor(Color.WHITE);
                signIn.setIconTint(ColorStateList.valueOf(Color.WHITE));
                signIn.setStrokeWidth(0);
            } else {
                signIn.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                signIn.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
                signIn.setIconTint(ColorStateList.valueOf(
                        ContextCompat.getColor(ctx, R.color.accent_blue_light)));
                signIn.setStrokeColor(ColorStateList.valueOf(accent));
                signIn.setStrokeWidth(Math.round(1.5f * density));
            }
        }
    }

    private static void verify(Activity activity, AlertDialog dialog, Views views, Runnable onVerified) {
        if (!dialog.isShowing()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            views.showSignedOut();
            return;
        }
        views.showChecking();
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
                        views.showWrongAccount();
                    }
                });
                return;
            }
            views.showFailed();
        });
    }
}
