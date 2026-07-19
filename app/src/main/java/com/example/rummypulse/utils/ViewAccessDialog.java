package com.example.rummypulse.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.rummypulse.R;

/**
 * Clear, blocking dialog when view access is pending, rejected, or failed.
 */
public final class ViewAccessDialog {

    public enum Type {
        PENDING,
        REJECTED,
        ERROR
    }

    private ViewAccessDialog() {
    }

    public static void show(Context context, Type type, @Nullable String errorDetail,
                            @Nullable Runnable onDismiss) {
        if (context == null) {
            return;
        }
        Dialog dialog = new Dialog(context, R.style.DarkDialogTheme);
        dialog.setContentView(R.layout.dialog_view_access_status);
        dialog.setCancelable(true);

        TextView icon = dialog.findViewById(R.id.text_view_access_dialog_icon);
        TextView title = dialog.findViewById(R.id.text_view_access_dialog_title);
        TextView message = dialog.findViewById(R.id.text_view_access_dialog_message);
        Button ok = dialog.findViewById(R.id.btn_view_access_dialog_ok);

        switch (type) {
            case REJECTED:
                icon.setText("🚫");
                title.setText(R.string.view_access_dialog_rejected_title);
                message.setText(R.string.view_access_dialog_rejected_message);
                break;
            case ERROR:
                icon.setText("⚠️");
                title.setText(R.string.view_access_dialog_error_title);
                if (errorDetail != null && !errorDetail.trim().isEmpty()) {
                    message.setText(errorDetail.trim());
                } else {
                    message.setText(R.string.view_access_check_failed);
                }
                break;
            case PENDING:
            default:
                icon.setText("⏳");
                title.setText(R.string.view_access_dialog_pending_title);
                message.setText(R.string.view_access_dialog_pending_message);
                break;
        }

        ok.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int maxPx = context.getResources().getDimensionPixelSize(R.dimen.dialog_create_game_max_width);
            int widthPx = Math.min((int) (dm.widthPixels * 0.92f), maxPx);
            window.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    public static void showPending(Context context, @Nullable Runnable onDismiss) {
        show(context, Type.PENDING, null, onDismiss);
    }

    public static void showRejected(Context context, @Nullable Runnable onDismiss) {
        show(context, Type.REJECTED, null, onDismiss);
    }

    public static void showError(Context context, @Nullable String detail, @Nullable Runnable onDismiss) {
        show(context, Type.ERROR, detail, onDismiss);
    }
}
