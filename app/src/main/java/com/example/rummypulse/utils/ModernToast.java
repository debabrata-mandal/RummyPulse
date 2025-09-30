package com.example.rummypulse.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.example.rummypulse.R;

/**
 * Modern toast message system with top positioning and better styling
 */
public class ModernToast {
    
    public enum ToastType {
        SUCCESS, ERROR, INFO, WARNING, PROGRESS
    }
    
    private static final int DURATION_SHORT = 3000; // 3 seconds
    private static final int DURATION_LONG = 5000;  // 5 seconds
    
    /**
     * Show a modern toast message at the top of the screen
     */
    public static void show(Context context, String message, ToastType type) {
        show(context, message, type, DURATION_SHORT);
    }
    
    /**
     * Show a modern toast message with custom duration
     */
    public static void show(Context context, String message, ToastType type, int duration) {
        if (context == null || message == null) return;
        
        // Debug logging
        android.util.Log.d("ModernToast", "=== TOAST REQUEST ===");
        android.util.Log.d("ModernToast", "Message: " + message);
        android.util.Log.d("ModernToast", "Type: " + type);
        android.util.Log.d("ModernToast", "Context: " + context.getClass().getSimpleName());
        android.util.Log.d("ModernToast", "Android Version: " + Build.VERSION.SDK_INT);
        
        // Ensure we run on UI thread
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                // For Android 11+ (API 30+), custom toast views are restricted
                // Use overlay approach for modern devices, fallback to styled toast for older devices
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context instanceof Activity) {
                    android.util.Log.d("ModernToast", "Using overlay toast for Android " + Build.VERSION.SDK_INT);
                    showOverlayToast((Activity) context, message, type, duration);
                } else {
                    android.util.Log.d("ModernToast", "Using custom toast for Android " + Build.VERSION.SDK_INT);
                    showCustomToast(context, message, type, duration);
                }
                
            } catch (Exception e) {
                android.util.Log.e("ModernToast", "Toast failed, using fallback", e);
                // Ultimate fallback to standard toast
                showStyledStandardToast(context, message, type);
            }
        });
    }
    
    /**
     * Show overlay toast for Android 11+ (more reliable)
     */
    private static void showOverlayToast(Activity activity, String message, ToastType type, int duration) {
        try {
            android.util.Log.d("ModernToast", "Creating overlay toast...");
            // Create overlay view
            LayoutInflater inflater = LayoutInflater.from(activity);
            View layout = inflater.inflate(R.layout.modern_toast_layout, null);
            android.util.Log.d("ModernToast", "Layout inflated successfully");
            
            // Get views
            TextView messageText = layout.findViewById(R.id.toast_message);
            ImageView iconView = layout.findViewById(R.id.toast_icon);
            View container = layout.findViewById(R.id.toast_container);
            
            // Set message
            messageText.setText(message);
            
            // Configure appearance
            configureToastAppearance(activity, container, iconView, messageText, type);
            
            // Add to activity's root view
            ViewGroup rootView = activity.findViewById(android.R.id.content);
            android.util.Log.d("ModernToast", "Root view: " + (rootView != null ? "Found" : "NULL"));
            if (rootView != null) {
                // Create frame layout for positioning
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                );
                params.gravity = Gravity.TOP;
                params.topMargin = 80; // 80dp from top (closer to top)
                params.leftMargin = 16;
                params.rightMargin = 16;
                
                layout.setLayoutParams(params);
                
                // Add subtle animation
                layout.setAlpha(0f);
                layout.setTranslationY(-50f);
                
                rootView.addView(layout);
                android.util.Log.d("ModernToast", "✅ Overlay toast added to root view!");
                
                // Animate in
                layout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .start();
                
                // Auto-remove after duration with fade out animation
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        // Animate out before removing
                        layout.animate()
                            .alpha(0f)
                            .translationY(-50f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                try {
                                    rootView.removeView(layout);
                                } catch (Exception e) {
                                    // View might already be removed
                                }
                            })
                            .start();
                    } catch (Exception e) {
                        // View might already be removed
                        try {
                            rootView.removeView(layout);
                        } catch (Exception ignored) {}
                    }
                }, duration);
            } else {
                // Fallback if no root view
                showStyledStandardToast(activity, message, type);
            }
            
        } catch (Exception e) {
            showStyledStandardToast(activity, message, type);
        }
    }
    
    /**
     * Show custom toast for older Android versions
     */
    private static void showCustomToast(Context context, String message, ToastType type, int duration) {
        try {
            // Create custom toast layout
            LayoutInflater inflater = LayoutInflater.from(context);
            View layout = inflater.inflate(R.layout.modern_toast_layout, null);
            
            // Get views
            TextView messageText = layout.findViewById(R.id.toast_message);
            ImageView iconView = layout.findViewById(R.id.toast_icon);
            View container = layout.findViewById(R.id.toast_container);
            
            // Set message
            messageText.setText(message);
            
            // Configure based on type
            configureToastAppearance(context, container, iconView, messageText, type);
            
            // Create and configure toast
            Toast toast = new Toast(context);
            toast.setDuration(duration > 3000 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
            toast.setView(layout);
            toast.setGravity(Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 100);
            
            // Show toast
            toast.show();
            
        } catch (Exception e) {
            showStyledStandardToast(context, message, type);
        }
    }
    
    /**
     * Styled standard toast as ultimate fallback
     */
     private static void showStyledStandardToast(Context context, String message, ToastType type) {
        try {
            String styledMessage = getStyledMessage(message, type);
            Toast toast = Toast.makeText(context, styledMessage, Toast.LENGTH_LONG);
            
            // Try to position at top, fallback to default if fails
            try {
                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 150);
            } catch (Exception e) {
                // Use default positioning if setGravity fails
            }
            
            toast.show();
            
            // Also log for debugging
            android.util.Log.d("ModernToast", "Showing toast: " + styledMessage);
            
        } catch (Exception e) {
            // Last resort - basic toast
            android.util.Log.e("ModernToast", "Toast failed, using basic fallback", e);
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
                // Even basic toast failed - give up gracefully
            }
        }
    }
    
    /**
     * Get styled message with emoji prefix
     */
    private static String getStyledMessage(String message, ToastType type) {
        String prefix;
        switch (type) {
            case SUCCESS: prefix = "✅ "; break;
            case ERROR: prefix = "❌ "; break;
            case WARNING: prefix = "⚠️ "; break;
            case PROGRESS: prefix = "📥 "; break;
            case INFO: 
            default: prefix = "ℹ️ "; break;
        }
        
        // Remove existing emoji if present
        String cleanMessage = message.replaceAll("^[\\p{So}\\p{Cn}]+\\s*", "");
        return prefix + cleanMessage;
    }
    
    /**
     * Configure toast appearance based on type
     */
    private static void configureToastAppearance(Context context, View container, 
                                               ImageView iconView, TextView messageText, ToastType type) {
        
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(24f); // Rounded corners
        
        int iconResource;
        int backgroundColor;
        int textColor = Color.WHITE;
        
        switch (type) {
            case SUCCESS:
                backgroundColor = Color.parseColor("#4CAF50"); // Green
                iconResource = android.R.drawable.ic_dialog_info; // You can replace with custom icons
                break;
                
            case ERROR:
                backgroundColor = Color.parseColor("#F44336"); // Red
                iconResource = android.R.drawable.ic_dialog_alert;
                break;
                
            case WARNING:
                backgroundColor = Color.parseColor("#FF9800"); // Orange
                iconResource = android.R.drawable.ic_dialog_alert;
                break;
                
            case PROGRESS:
                backgroundColor = Color.parseColor("#2196F3"); // Blue
                iconResource = android.R.drawable.ic_popup_sync;
                break;
                
            case INFO:
            default:
                backgroundColor = Color.parseColor("#607D8B"); // Blue Grey
                iconResource = android.R.drawable.ic_dialog_info;
                break;
        }
        
        // Set background color with transparency
        background.setColor(backgroundColor);
        background.setAlpha(240); // Slight transparency
        container.setBackground(background);
        
        // Set text color
        messageText.setTextColor(textColor);
        
        // Set icon
        iconView.setImageResource(iconResource);
        iconView.setColorFilter(textColor);
        
        // Add shadow effect
        container.setElevation(8f);
    }
    
    // Convenience methods for different toast types
    public static void success(Context context, String message) {
        show(context, message, ToastType.SUCCESS);
    }
    
    public static void error(Context context, String message) {
        show(context, message, ToastType.ERROR, DURATION_LONG);
    }
    
    public static void info(Context context, String message) {
        show(context, message, ToastType.INFO);
    }
    
    public static void warning(Context context, String message) {
        show(context, message, ToastType.WARNING);
    }
    
    public static void progress(Context context, String message) {
        show(context, message, ToastType.PROGRESS);
    }
}
