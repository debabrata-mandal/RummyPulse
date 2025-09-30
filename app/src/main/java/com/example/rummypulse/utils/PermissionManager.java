package com.example.rummypulse.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.rummypulse.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized permission manager for handling all app permissions at startup
 */
public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    
    // Permission request codes
    public static final int REQUEST_STORAGE_PERMISSION = 1001;
    public static final int REQUEST_INSTALL_PERMISSION = 1002;
    public static final int REQUEST_ALL_PERMISSIONS = 1003;
    
    // Required permissions for different Android versions
    private static final String[] STORAGE_PERMISSIONS = {
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    };
    
    // Permissions that are always required regardless of Android version
    private static final String[] ALWAYS_REQUIRED_PERMISSIONS = {
        // Add any permissions that are always needed here
    };
    
    private final Activity activity;
    private PermissionCallback callback;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied(List<String> deniedPermissions);
        void onPermissionsExplained();
    }
    
    public PermissionManager(Activity activity) {
        this.activity = activity;
        this.timeoutHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Check and request all necessary permissions for the app
     */
    public void checkAndRequestAllPermissions(PermissionCallback callback) {
        this.callback = callback;
        
        List<String> permissionsToRequest = new ArrayList<>();
        
        // Always check for required permissions regardless of version
        for (String permission : ALWAYS_REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        // Check storage permissions based on Android version
        // For Android 10+ (API 29+), external storage access is more restricted
        // For downloads, we might need different approaches
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 9 and below - need explicit storage permissions
            for (String permission : STORAGE_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(activity, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // Android 10 - still might need WRITE_EXTERNAL_STORAGE for some operations
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        // Android 11+ (API 30+) - Scoped storage, no explicit storage permissions needed for app-specific directories
        
        // Check install permission (Android 8.0+)
        boolean needsInstallPermission = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            needsInstallPermission = !activity.getPackageManager().canRequestPackageInstalls();
        }
        
        Log.d(TAG, "Permission check - Android API: " + Build.VERSION.SDK_INT + 
                   ", Storage permissions needed: " + permissionsToRequest.size() + 
                   ", Install permission needed: " + needsInstallPermission);
        
        if (permissionsToRequest.isEmpty() && !needsInstallPermission) {
            // All permissions already granted
            Log.d(TAG, "All permissions already granted for API " + Build.VERSION.SDK_INT);
            if (callback != null) {
                callback.onPermissionsGranted();
            }
            return;
        }
        
        // Show explanation dialog first
        showPermissionExplanationDialog(permissionsToRequest, needsInstallPermission);
    }
    
    /**
     * Simple permission request - no explanations, just request directly
     */
    private void showPermissionExplanationDialog(List<String> permissionsToRequest, boolean needsInstallPermission) {
        // Skip explanation dialog - directly request permissions
        if (callback != null) {
            callback.onPermissionsExplained();
        }
        requestPermissions(permissionsToRequest, needsInstallPermission);
    }
    
    /**
     * Request the actual permissions
     */
    private void requestPermissions(List<String> permissionsToRequest, boolean needsInstallPermission) {
        if (!permissionsToRequest.isEmpty()) {
            // Request storage permissions first
            Log.d(TAG, "Requesting storage permissions: " + permissionsToRequest);
            ActivityCompat.requestPermissions(activity, 
                permissionsToRequest.toArray(new String[0]), 
                REQUEST_STORAGE_PERMISSION);
        } else if (needsInstallPermission) {
            // Go directly to install permission
            requestInstallPermission();
        } else {
            // All done
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
    
    /**
     * Request install unknown apps permission (Android 8.0+)
     */
    private void requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Requesting install permission");
            
            // Directly open settings without explanation dialog
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
            
            // Start timeout - if user doesn't grant permission, close app
            startPermissionTimeout();
        } else {
            // Not needed for older Android versions
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }

    /**
     * Close app immediately - no confirmation dialog
     */
    private void showExitConfirmationDialog() {
        // No dialog - just close immediately
        closeApp();
    }

    /**
     * Start timeout for permission granting
     */
    private void startPermissionTimeout() {
        // Cancel any existing timeout
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
        
        // Create new timeout runnable
        timeoutRunnable = () -> {
            Log.w(TAG, "Permission timeout reached - closing app");
            if (!areAllPermissionsGranted()) {
                // Timeout reached - close silently
                closeApp();
            }
        };
        
        // Start 10-second timeout
        timeoutHandler.postDelayed(timeoutRunnable, 10000);
        Log.d(TAG, "Started 10-second permission timeout");
    }

    /**
     * Cancel permission timeout (called when permissions are granted)
     */
    private void cancelPermissionTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
            Log.d(TAG, "Cancelled permission timeout");
        }
    }

    /**
     * Close the app completely when permissions are denied
     */
    private void closeApp() {
        Log.w(TAG, "Closing app due to denied permissions");
        
        // Cancel any pending timeouts
        cancelPermissionTimeout();
        
        // Close app immediately - no messages
        activity.finishAffinity(); // Close all activities
        System.exit(0); // Force close the process
    }
    
    /**
     * Handle permission request results
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                handleStoragePermissionResult(permissions, grantResults);
                break;
            default:
                Log.w(TAG, "Unknown permission request code: " + requestCode);
                break;
        }
    }
    
    /**
     * Handle activity results (for install permission)
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            handleInstallPermissionResult();
        }
    }
    
    /**
     * Handle storage permission results
     */
    private void handleStoragePermissionResult(String[] permissions, int[] grantResults) {
        List<String> deniedPermissions = new ArrayList<>();
        
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permissions[i]);
            }
        }
        
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "Storage permissions granted");
            cancelPermissionTimeout(); // Cancel timeout since permissions are granted
            
            // Check if we still need install permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
                !activity.getPackageManager().canRequestPackageInstalls()) {
                requestInstallPermission();
            } else {
                if (callback != null) {
                    callback.onPermissionsGranted();
                }
            }
        } else {
            Log.w(TAG, "Storage permissions denied: " + deniedPermissions + " - closing app");
            closeApp();
        }
    }
    
    /**
     * Handle install permission result
     */
    private void handleInstallPermissionResult() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (activity.getPackageManager().canRequestPackageInstalls()) {
                Log.d(TAG, "MANDATORY install permission granted");
                cancelPermissionTimeout(); // Cancel timeout since permission is granted
                // Permissions granted - no toast needed
                if (callback != null) {
                    callback.onPermissionsGranted();
                }
            } else {
                Log.w(TAG, "Install permission denied - closing app");
                closeApp();
            }
        } else {
            // Not needed for older Android versions
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
    
    
    /**
     * Check if all required permissions are granted
     */
    public boolean areAllPermissionsGranted() {
        // Check always required permissions
        for (String permission : ALWAYS_REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        
        // Check storage permissions based on Android version
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 9 and below - need explicit storage permissions
            for (String permission : STORAGE_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(activity, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // Android 10 - check WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        // Android 11+ - no explicit storage permissions needed
        
        // Check install permission for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get a user-friendly status of permissions
     */
    public String getPermissionStatus() {
        if (areAllPermissionsGranted()) {
            return "✅ All permissions granted - Auto-updates enabled";
        } else {
            return "⚠️ Some permissions missing - Manual updates may be required";
        }
    }
}
