package com.example.rummypulse.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.example.rummypulse.R;

import java.io.File;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modern update checker using ExecutorService instead of deprecated AsyncTask
 */
public class ModernUpdateChecker {
    
    private static final String TAG = "ModernUpdateChecker";
    // GitHub repository URL for debabrata-mandal
    private static final String GITHUB_API_URL = "https://api.github.com/repos/debabrata-mandal/RummyPulse/releases/latest";
    private static final int REQUEST_INSTALL_PERMISSION = 1001;
    
    private final Context context;
    private final ExecutorService executor;
    private DownloadManager downloadManager;
    private long downloadId = -1;
    private BroadcastReceiver downloadReceiver;
    
    public ModernUpdateChecker(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        setupDownloadReceiver();
    }
    
    /**
     * Check for updates and show dialog if newer version is available
     */
    public void checkForUpdates() {
        checkForUpdates(false);
    }
    
    /**
     * Check for updates and show dialog if newer version is available
     * @param isAdminUser If true, skips auto-update check (admin users don't need auto-updates)
     */
    public void checkForUpdates(boolean isAdminUser) {
        if (isAdminUser) {
            Log.d(TAG, "Skipping auto-update check for admin user");
            return;
        }
        
        Log.d(TAG, "Checking for app updates...");
        
        executor.execute(() -> {
            UpdateInfo updateInfo = fetchLatestVersionInfo();
            
            if (updateInfo != null) {
                String currentVersion = getCurrentVersion();
                
                if (compareVersions(currentVersion, updateInfo.version) < 0) {
                    Log.d(TAG, "Update available: " + currentVersion + " -> " + updateInfo.version);
                    showUpdateDialog(updateInfo);
                } else {
                    Log.d(TAG, "App is up to date: " + currentVersion);
                }
            } else {
                Log.w(TAG, "Could not check for updates");
            }
        });
    }
    
    /**
     * Force check for updates regardless of admin status (for manual checks)
     */
    public void forceCheckForUpdates() {
        Log.d(TAG, "Force checking for app updates (manual check)...");
        
        executor.execute(() -> {
            UpdateInfo updateInfo = fetchLatestVersionInfo();
            
            if (updateInfo != null) {
                String currentVersion = getCurrentVersion();
                
                if (compareVersions(currentVersion, updateInfo.version) < 0) {
                    Log.d(TAG, "Update available: " + currentVersion + " -> " + updateInfo.version);
                    showUpdateDialog(updateInfo);
                } else {
                    Log.d(TAG, "App is up to date: " + currentVersion);
                    // Show a message even when up to date for manual checks
                    if (context instanceof Activity) {
                        ((Activity) context).runOnUiThread(() -> {
                            ModernToast.success(context, "✅ You're running the latest version!");
                        });
                    }
                }
            } else {
                Log.w(TAG, "Could not check for updates");
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        ModernToast.error(context, "❌ Unable to check for updates. Please try again later.");
                    });
                }
            }
        });
    }
    
    /**
     * Fetch latest version information from GitHub API
     */
    private UpdateInfo fetchLatestVersionInfo() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                connection.disconnect();
                
                return parseUpdateInfo(response.toString());
            } else {
                Log.e(TAG, "HTTP error code: " + responseCode);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking for updates", e);
        }
        
        return null;
    }
    
    /**
     * Parse JSON response to extract update information
     */
    private UpdateInfo parseUpdateInfo(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            
            String latestVersion = jsonObject.getString("tag_name");
            // Remove 'v' prefix if present (e.g., v1.2.0 -> 1.2.0)
            if (latestVersion.startsWith("v")) {
                latestVersion = latestVersion.substring(1);
            }
            
            String releaseNotes = jsonObject.optString("body", "No release notes available");
            String downloadUrl = null;
            
            // Look for APK asset in release
            if (jsonObject.has("assets") && jsonObject.getJSONArray("assets").length() > 0) {
                for (int i = 0; i < jsonObject.getJSONArray("assets").length(); i++) {
                    JSONObject asset = jsonObject.getJSONArray("assets").getJSONObject(i);
                    String assetName = asset.getString("name");
                    if (assetName.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url");
                        break;
                    }
                }
            }
            
            return new UpdateInfo(latestVersion, releaseNotes, downloadUrl);
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing update info", e);
            return null;
        }
    }
    
    /**
     * Get current app version
     */
    private String getCurrentVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting current version", e);
            return "1.0.0";
        }
    }
    
    /**
     * Compare version strings (semantic versioning)
     */
    private int compareVersions(String currentVersion, String latestVersion) {
        try {
            String[] currentParts = currentVersion.split("\\.");
            String[] latestParts = latestVersion.split("\\.");
            
            int maxLength = Math.max(currentParts.length, latestParts.length);
            
            for (int i = 0; i < maxLength; i++) {
                int currentPart = i < currentParts.length ? 
                    Integer.parseInt(currentParts[i]) : 0;
                int latestPart = i < latestParts.length ? 
                    Integer.parseInt(latestParts[i]) : 0;
                    
                if (currentPart < latestPart) return -1;
                if (currentPart > latestPart) return 1;
            }
            
            return 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error comparing versions", e);
            return 0;
        }
    }
    
    /**
     * Show update available dialog on UI thread
     */
    private void showUpdateDialog(UpdateInfo updateInfo) {
        if (!(context instanceof Activity)) {
            Log.w(TAG, "Context is not an Activity, cannot show dialog");
            return;
        }
        
        Activity activity = (Activity) context;
        
        activity.runOnUiThread(() -> {
            String currentVersion = getCurrentVersion();
            
            // Create dialog with dark theme
            AlertDialog dialog = new AlertDialog.Builder(activity, R.style.DarkAlertDialog)
                .setTitle("🚀 Update Available")
                .setMessage("A new version is available!\n\n" +
                           "📱 Current: v" + currentVersion + "\n" +
                           "✨ Latest: v" + updateInfo.version + "\n\n" +
                           "📝 What's new:\n" + formatReleaseNotes(updateInfo.releaseNotes))
                .setPositiveButton("Update Now", (dialogInterface, which) -> {
                    if (updateInfo.downloadUrl != null) {
                        downloadUpdate(updateInfo.downloadUrl);
                    } else {
                        openGitHubReleases();
                    }
                })
                .setNegativeButton("Later", (dialogInterface, which) -> {
                    ModernToast.info(context, "💡 You can check for updates anytime in Settings");
                })
                .setCancelable(true)
                .create();
                
            // Show dialog and apply button styling
            dialog.show();
            
            // Style the buttons to match app theme
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    activity.getColor(R.color.accent_blue));
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    activity.getColor(R.color.text_secondary));
            }
        });
    }
    
    /**
     * Format release notes for better display
     */
    private String formatReleaseNotes(String releaseNotes) {
        if (releaseNotes == null || releaseNotes.trim().isEmpty()) {
            return "• Bug fixes and improvements";
        }
        
        // Limit length and clean up formatting
        if (releaseNotes.length() > 200) {
            releaseNotes = releaseNotes.substring(0, 200) + "...";
        }
        
        return releaseNotes.trim();
    }
    
    /**
     * Setup download receiver to handle APK installation after download
     */
    private void setupDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                Log.d(TAG, "Download broadcast received for ID: " + id + " (our ID: " + downloadId + ")");
                if (id == downloadId) {
                    Log.d(TAG, "Download completed for our request - processing...");
                    handleDownloadComplete();
                }
            }
        };
    }

    /**
     * Download and install update using DownloadManager
     */
    private void downloadUpdate(String downloadUrl) {
        try {
            // Check network connectivity first
            if (!isNetworkAvailable()) {
                showDownloadError("No internet connection available. Please check your network and try again.", true);
                return;
            }

            // Permissions are now handled at app startup, so we can directly start download
            Log.d(TAG, "Starting download - permissions should already be granted");
            startApkDownload(downloadUrl);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            showDownloadError("Failed to start download: " + e.getMessage(), true);
        }
    }

    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (connectivityManager == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = connectivityManager.getActiveNetwork();
                if (network == null) return false;
                
                android.net.NetworkCapabilities capabilities = 
                    connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && 
                       (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network availability", e);
            return true; // Assume network is available if we can't check
        }
    }


    /**
     * Start APK download using DownloadManager
     */
    private void startApkDownload(String downloadUrl) {
        try {
            Log.d(TAG, "Starting APK download from: " + downloadUrl);
            
            // Store download URL for retry purposes
            context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_download_url", downloadUrl)
                .apply();

            // Register download receiver with Android 15 compatibility
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ requires explicit export flag
                context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(downloadReceiver, filter);
            }

            // Create download request
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("RummyPulse Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            // Use internal app directory - guaranteed to work without permissions
            String fileName = "RummyPulse_update_" + System.currentTimeMillis() + ".apk";
            
            // Always use internal app directory - no permissions needed on any Android version
            request.setDestinationInExternalFilesDir(context, null, fileName);
            Log.d(TAG, "Download destination (API " + Build.VERSION.SDK_INT + "): Internal app directory/" + fileName);
            
            // Allow download over mobile data and WiFi
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | 
                                         DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(false);
            request.setAllowedOverMetered(true);

            // Add headers to mimic browser request
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0");
            request.addRequestHeader("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");

            // Additional configuration for Android 15
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.setRequiresCharging(false);
                request.setRequiresDeviceIdle(false);
            }

            Log.d(TAG, "DownloadManager request configured for API " + Build.VERSION.SDK_INT);
            Log.d(TAG, "Download URL: " + downloadUrl);
            Log.d(TAG, "Download destination: " + context.getExternalFilesDir(null) + "/" + fileName);

            // Start download
            downloadId = downloadManager.enqueue(request);
            Log.d(TAG, "Download enqueued with ID: " + downloadId);
            
            ModernToast.progress(context, 
                "📥 Download started! Check notification for progress");
                
            // Start polling for download status as backup to BroadcastReceiver
            startDownloadStatusPolling();
            
            // Show a follow-up message after a delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                ModernToast.info(context, 
                    "⏳ Download in progress... Installation will start automatically when complete");
            }, 3000);
                
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting download - API " + Build.VERSION.SDK_INT, e);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showDownloadError("Download failed. Using app-specific storage should not require permissions on Android " + Build.VERSION.SDK_INT, true);
            } else {
                showDownloadError("Permission denied. Please check storage permissions.", false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            showDownloadError("Failed to start download: " + e.getMessage(), true);
        }
    }

    /**
     * Start polling for download status as backup to BroadcastReceiver
     */
    private void startDownloadStatusPolling() {
        if (downloadId == -1) return;
        
        Log.d(TAG, "Starting download status polling for ID: " + downloadId);
        
        // Poll every 2 seconds
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable pollRunnable = new Runnable() {
            int pollCount = 0;
            final int maxPolls = 60; // Poll for max 2 minutes
            
            @Override
            public void run() {
                pollCount++;
                Log.d(TAG, "Polling download status... attempt " + pollCount + "/" + maxPolls);
                
                if (pollCount > maxPolls) {
                    Log.w(TAG, "Download polling timeout reached");
                    ModernToast.warning(context, "⏰ Download taking too long - check notification");
                    return;
                }
                
                try {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor cursor = downloadManager.query(query);
                    
                    if (cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = cursor.getInt(statusIndex);
                        
                        int bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        
                        long bytesDownloaded = cursor.getLong(bytesDownloadedIndex);
                        long bytesTotal = cursor.getLong(bytesTotalIndex);
                        
                        Log.d(TAG, "Download status: " + status + ", Progress: " + bytesDownloaded + "/" + bytesTotal);
                        
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            Log.d(TAG, "✅ Download completed via polling! Triggering installation...");
                            cursor.close();
                            handleDownloadComplete();
                            return;
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            Log.e(TAG, "❌ Download failed via polling");
                            cursor.close();
                            handleDownloadComplete();
                            return;
                        } else if (status == DownloadManager.STATUS_RUNNING) {
                            if (bytesTotal > 0) {
                                int progress = (int) ((bytesDownloaded * 100) / bytesTotal);
                                Log.d(TAG, "📥 Download progress: " + progress + "%");
                            }
                        }
                    }
                    cursor.close();
                    
                    // Continue polling
                    handler.postDelayed(this, 2000);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error polling download status", e);
                    handler.postDelayed(this, 2000);
                }
            }
        };
        
        // Start polling after 5 seconds
        handler.postDelayed(pollRunnable, 5000);
    }

    /**
     * Handle download completion and start installation
     */
    private void handleDownloadComplete() {
        try {
            // Query download status
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            
            Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    String localUri = cursor.getString(uriIndex);
                    
                    Log.d(TAG, "✅ Download completed successfully! Local URI: " + localUri);
                    ModernToast.success(context, "✅ Download complete! Starting installation...");
                    
                    if (localUri != null) {
                        installApk(localUri);
                    } else {
                        Log.e(TAG, "Download successful but local URI is null");
                        ModernToast.error(context, "❌ Download completed but file not found");
                        showDownloadError("Download completed but file location is unknown", true);
                    }
                } else {
                    // Get detailed error information
                    String errorMessage = getDownloadErrorMessage(cursor, status);
                    Log.e(TAG, "❌ Download failed with status: " + status + " - " + errorMessage);
                    ModernToast.error(context, "❌ Download failed: " + getSimpleErrorMessage(status));
                    showDownloadError(errorMessage, true);
                }
            } else {
                Log.e(TAG, "Download query returned no results");
                showDownloadError("Unable to check download status", true);
            }
            cursor.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling download completion", e);
            showDownloadError("Error processing download: " + e.getMessage(), true);
        } finally {
            // Unregister receiver
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering receiver", e);
            }
        }
    }

    /**
     * Get detailed error message for download failure
     */
    private String getDownloadErrorMessage(Cursor cursor, int status) {
        try {
            switch (status) {
                case DownloadManager.STATUS_FAILED:
                    int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    if (reasonIndex >= 0) {
                        int reason = cursor.getInt(reasonIndex);
                        return getFailureReason(reason);
                    }
                    return "Download failed for unknown reason";
                    
                case DownloadManager.STATUS_PAUSED:
                    int pauseReasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    if (pauseReasonIndex >= 0) {
                        int pauseReason = cursor.getInt(pauseReasonIndex);
                        return getPauseReason(pauseReason);
                    }
                    return "Download paused";
                    
                case DownloadManager.STATUS_PENDING:
                    return "Download is pending";
                    
                case DownloadManager.STATUS_RUNNING:
                    return "Download is still running";
                    
                default:
                    return "Unknown download status: " + status;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting download error message", e);
            return "Unable to determine download error";
        }
    }

    /**
     * Get human-readable failure reason
     */
    private String getFailureReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "Cannot resume download";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Storage device not found";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "File already exists";
            case DownloadManager.ERROR_FILE_ERROR:
                return "File system error";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "HTTP data error";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Too many redirects";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Unhandled HTTP response code";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "Unknown download error (code: " + reason + ")";
        }
    }

    /**
     * Get human-readable pause reason
     */
    private String getPauseReason(int reason) {
        switch (reason) {
            case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                return "Waiting for WiFi connection";
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                return "Waiting for network connection";
            case DownloadManager.PAUSED_WAITING_TO_RETRY:
                return "Waiting to retry download";
            case DownloadManager.PAUSED_UNKNOWN:
            default:
                return "Download paused (reason: " + reason + ")";
        }
    }

    /**
     * Get simple error message for Toast display
     */
    private String getSimpleErrorMessage(int status) {
        switch (status) {
            case DownloadManager.STATUS_FAILED:
                return "Network or storage error";
            case DownloadManager.STATUS_PAUSED:
                return "Download paused";
            default:
                return "Unknown error";
        }
    }

    /**
     * Show download error with detailed message and options
     */
    private void showDownloadError(String errorMessage, boolean showRetryOption) {
        if (!(context instanceof Activity)) {
            ModernToast.error(context, "❌ " + errorMessage);
            return;
        }

        Activity activity = (Activity) context;
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.DarkAlertDialog)
                .setTitle("❌ Download Failed")
                .setMessage("Update download failed:\n\n" + errorMessage + "\n\nWhat would you like to do?")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

            if (showRetryOption) {
                builder.setPositiveButton("Try Again", (dialog, which) -> {
                    // Get the last download URL from shared preferences if available
                    String lastUrl = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                        .getString("last_download_url", null);
                    if (lastUrl != null) {
                        startApkDownload(lastUrl);
                    } else {
                        // Re-check for updates
                        checkForUpdates();
                    }
                });
            }

            builder.setNeutralButton("Manual Download", (dialog, which) -> openGitHubReleases());

            AlertDialog dialog = builder.create();
            dialog.show();

            // Style the buttons
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    activity.getColor(R.color.accent_blue));
            }
        });
    }

    /**
     * Install APK and delete file after installation
     */
    private void installApk(String localUri) {
        try {
            File apkFile = new File(Uri.parse(localUri).getPath());
            
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: " + localUri);
                return;
            }

            // Create install intent
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7.0+
                apkUri = FileProvider.getUriForFile(context, 
                    context.getPackageName() + ".fileprovider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            
            // Start installation
            context.startActivity(installIntent);
            
            ModernToast.info(context, 
                "🚀 Opening installer... APK will be auto-deleted after installation");
                
            Log.d(TAG, "✅ Installation intent started for: " + apkFile.getPath());

            // Schedule APK deletion after a delay (to allow installation to complete)
            executor.execute(() -> {
                try {
                    // Wait for installation to start
                    Thread.sleep(5000);
                    
                    // Delete APK file
                    if (apkFile.exists() && apkFile.delete()) {
                        Log.d(TAG, "APK file deleted successfully: " + apkFile.getPath());
                    } else {
                        Log.w(TAG, "Failed to delete APK file: " + apkFile.getPath());
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "Sleep interrupted", e);
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting APK file", e);
                }
            });
                
        } catch (Exception e) {
            Log.e(TAG, "Error installing APK", e);
            ModernToast.error(context, "❌ Installation failed. Please install manually.");
        }
    }
    
    /**
     * Open GitHub releases page as fallback
     */
    private void openGitHubReleases() {
        try {
            String releasesUrl = GITHUB_API_URL.replace("/releases/latest", "/releases");
            releasesUrl = releasesUrl.replace("api.github.com/repos", "github.com");
            
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            
            ModernToast.info(context, 
                "🌐 Opening GitHub releases page");
                
        } catch (Exception e) {
            Log.e(TAG, "Error opening GitHub releases", e);
            ModernToast.error(context, "❌ Please check for updates manually");
        }
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        
        // Unregister download receiver if still registered
        try {
            if (downloadReceiver != null) {
                context.unregisterReceiver(downloadReceiver);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering receiver during cleanup", e);
        }
    }

    
    /**
     * Data class to hold update information
     */
    private static class UpdateInfo {
        final String version;
        final String releaseNotes;
        final String downloadUrl;
        
        UpdateInfo(String version, String releaseNotes, String downloadUrl) {
            this.version = version;
            this.releaseNotes = releaseNotes;
            this.downloadUrl = downloadUrl;
        }
    }
}
