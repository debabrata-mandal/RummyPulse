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
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.example.rummypulse.R;
import com.example.rummypulse.UpdateProgressActivity;

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

    /** When set (e.g. by {@link UpdateProgressActivity}), download/install drives full-screen UI instead of toasts. */
    public interface DownloadUiCallbacks {
        void onFetchStarted();
        void onDownloadStarted();
        /** {@code percent} is 0–100, or {@code -1} if total size is unknown yet. */
        void onDownloadProgress(int percent, long bytesSoFar, long bytesTotal);
        void onInstalling();
        void onOpeningSystemInstaller();
        void onError(String message, boolean allowRetry);
    }

    private static final String TAG = "ModernUpdateChecker";
    // GitHub repository URL
    private static final String GITHUB_API_URL = "https://api.github.com/repos/debabrata-mandal/RummyPulse/releases/latest";
    private static final int REQUEST_INSTALL_PERMISSION = 1001;
    
    private final Context context;
    /** Used for DownloadManager, broadcasts, and APK path so downloads survive activity destroy (e.g. MinimumVersionActivity). */
    private final Context appContext;
    private final ExecutorService executor;
    private DownloadManager downloadManager;
    private long downloadId = -1;
    private BroadcastReceiver downloadReceiver;
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    /** Prevents double install when both BroadcastReceiver and polling see completion. */
    private volatile boolean downloadCompletionHandled;
    @Nullable
    private DownloadUiCallbacks downloadUiCallbacks;

    public ModernUpdateChecker(Context context) {
        this.context = context;
        this.appContext = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        setupDownloadReceiver();
    }

    public void setDownloadUiCallbacks(@Nullable DownloadUiCallbacks callbacks) {
        downloadUiCallbacks = callbacks;
    }

    private boolean useDownloadUi() {
        return downloadUiCallbacks != null;
    }

    private void postToUi(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    /**
     * Start download from {@link UpdateProgressActivity} (blocking UI already visible).
     */
    public void startBlockingDownload(String downloadUrl) {
        downloadUpdate(downloadUrl);
    }

    /**
     * Resolve latest GitHub release APK URL then download. {@code onNoApkOrFailure} runs on the main thread
     * when the API fails or has no APK asset.
     */
    public void runFetchLatestApkDownload(@Nullable Runnable onNoApkOrFailure) {
        if (useDownloadUi()) {
            postToUi(() -> downloadUiCallbacks.onFetchStarted());
        }
        executor.execute(() -> {
            UpdateInfo info = fetchLatestVersionInfo();
            if (!(context instanceof Activity)) {
                return;
            }
            Activity activity = (Activity) context;
            activity.runOnUiThread(() -> {
                if (info != null && info.downloadUrl != null) {
                    downloadUpdate(info.downloadUrl);
                } else {
                    if (onNoApkOrFailure != null) {
                        onNoApkOrFailure.run();
                    } else if (useDownloadUi()) {
                        postToUi(() -> downloadUiCallbacks.onError(
                            "Could not get the latest APK. Check your connection or open the download page.", true));
                    } else {
                        openGitHubReleases();
                    }
                }
            });
        });
    }

    /** Opens the GitHub releases page in a browser (same as manual download fallback). */
    public void openReleasesInBrowser() {
        openGitHubReleases();
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
     * Opens full-screen download/update UI and resolves the latest GitHub release APK there.
     * The {@code onFallback} runnable is no longer used (kept for call-site compatibility); pass a
     * fallback URL via {@link UpdateProgressActivity#startFetchLatest(Activity, String, boolean)} instead.
     */
    public void downloadLatestReleaseApkOrFallback(@SuppressWarnings("unused") @Nullable Runnable onFallback) {
        if (!(context instanceof Activity)) {
            Log.w(TAG, "downloadLatestReleaseApkOrFallback: context is not an Activity");
            if (onFallback != null) {
                new Handler(Looper.getMainLooper()).post(onFallback);
            }
            return;
        }
        UpdateProgressActivity.startFetchLatest((Activity) context, null, false);
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
                Log.e(TAG, "HTTP error code: " + responseCode + " for URL: " + GITHUB_API_URL);
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    Log.e(TAG, "GitHub repository or release not found. Please check the repository URL.");
                }
            }
            
        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "No internet connection or unable to reach GitHub", e);
        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Connection timeout while checking for updates", e);
        } catch (Exception e) {
            Log.e(TAG, "Error checking for updates: " + e.getMessage(), e);
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
            
            // Create custom dialog
            android.app.Dialog dialog = new android.app.Dialog(activity);
            dialog.setContentView(R.layout.dialog_update_available);
            dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.setCancelable(true);
            
            // Get views
            android.widget.TextView textCurrentVersion = dialog.findViewById(R.id.text_current_version);
            android.widget.TextView textLatestVersion = dialog.findViewById(R.id.text_latest_version);
            android.widget.TextView textReleaseNotes = dialog.findViewById(R.id.text_release_notes);
            android.widget.ImageButton btnClose = dialog.findViewById(R.id.btn_close);
            android.widget.Button btnUpdateNow = dialog.findViewById(R.id.btn_update_now);
            
            // Set values
            textCurrentVersion.setText("v" + currentVersion);
            textLatestVersion.setText("v" + updateInfo.version);
            textReleaseNotes.setText(formatReleaseNotes(updateInfo.releaseNotes));
            
            // Set button listeners
            btnClose.setOnClickListener(v -> {
                dialog.dismiss();
                ModernToast.info(context, "💡 You can check for updates anytime from App Info");
            });
            
            btnUpdateNow.setOnClickListener(v -> {
                dialog.dismiss();
                if (updateInfo.downloadUrl != null) {
                    UpdateProgressActivity.startDirect(activity, updateInfo.downloadUrl, false);
                } else {
                    openGitHubReleases();
                }
            });
            
            dialog.show();
        });
    }
    
    /**
     * Format release notes for better display
     */
    private String formatReleaseNotes(String releaseNotes) {
        if (releaseNotes == null || releaseNotes.trim().isEmpty()) {
            return "• Bug fixes and improvements";
        }
        
        // Clean up markdown formatting
        String formatted = releaseNotes
            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")  // Remove bold **text**
            .replaceAll("###+\\s*", "")                // Remove ### headers
            .replaceAll("\\*\\s*", "• ")               // Convert * to bullet points
            .replaceAll("-\\s*", "• ")                 // Convert - to bullet points
            .replaceAll("\\n\\n+", "\n")               // Remove extra newlines
            .trim();
        
        // Format date from technical format to user-friendly format
        // Pattern: Date: 2025-10-06T15:07:21+05:30 -> Released: Oct 06, 2025
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile(
            "(?i)Date:\\s*(\\d{4})-(\\d{2})-(\\d{2})T[^\\n]+");
        java.util.regex.Matcher dateMatcher = datePattern.matcher(formatted);
        if (dateMatcher.find()) {
            try {
                int year = Integer.parseInt(dateMatcher.group(1));
                int month = Integer.parseInt(dateMatcher.group(2));
                int day = Integer.parseInt(dateMatcher.group(3));
                
                String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                String monthName = month > 0 && month <= 12 ? monthNames[month - 1] : String.valueOf(month);
                
                String friendlyDate = "Released: " + monthName + " " + 
                                     String.format("%02d", day) + ", " + year;
                formatted = dateMatcher.replaceFirst(friendlyDate);
            } catch (Exception e) {
                // If parsing fails, just remove the date line
                formatted = dateMatcher.replaceFirst("");
            }
        }
        
        // Remove installation instructions section (not needed in update dialog)
        int installIndex = formatted.toLowerCase().indexOf("installation instructions");
        if (installIndex != -1) {
            formatted = formatted.substring(0, installIndex).trim();
        }
        
        // Also try to remove by numbered list pattern
        formatted = formatted.replaceAll("(?i)(installation instructions|install.*instructions)[\\s\\S]*", "").trim();
        
        // Remove technical commit info and build number
        formatted = formatted.replaceAll("(?i)Commit:\\s*[^\\n]+", "").trim();
        formatted = formatted.replaceAll("(?i)Build Number:\\s*[^\\n]+", "").trim();
        
        // Remove empty lines
        formatted = formatted.replaceAll("\\n\\s*\\n", "\n").trim();
        
        // Limit length if too long
        if (formatted.length() > 400) {
            formatted = formatted.substring(0, 400) + "...";
        }
        
        return formatted;
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
                if (useDownloadUi()) {
                    postToUi(() -> downloadUiCallbacks.onError(
                        "No internet connection. Check your network and try again.", true));
                } else {
                    showDownloadError("No internet connection available. Please check your network and try again.", true);
                }
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
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            
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
            
            // Cancel any previous downloads and polling
            cleanupPreviousDownload();
            
            // Store download URL for retry purposes
            appContext.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_download_url", downloadUrl)
                .apply();

            // Register with application context so completion still delivers if the Activity is destroyed.
            // DownloadManager sends this from the system process — must be RECEIVER_EXPORTED on API 33+.
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(downloadReceiver, filter);
            }

            // Create download request
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("RummyPulse Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            // Use internal app directory - guaranteed to work without permissions
            String fileName = "RummyPulse_update_" + System.currentTimeMillis() + ".apk";
            
            // Always use internal app directory - no permissions needed on any Android version
            request.setDestinationInExternalFilesDir(appContext, null, fileName);
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
            Log.d(TAG, "Download destination: " + appContext.getExternalFilesDir(null) + "/" + fileName);

            // Start download
            downloadId = downloadManager.enqueue(request);
            Log.d(TAG, "Download enqueued with ID: " + downloadId);

            if (useDownloadUi()) {
                postToUi(() -> downloadUiCallbacks.onDownloadStarted());
            } else {
                ModernToast.progress(context,
                    "📥 Download started! Check notification for progress");
                new Handler(Looper.getMainLooper()).postDelayed(() ->
                    ModernToast.info(context,
                        "⏳ Download in progress... Installation will start automatically when complete"), 3000);
            }

            startDownloadStatusPolling();

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

        stopPolling();

        Log.d(TAG, "Starting download status polling for ID: " + downloadId);

        final boolean fastUi = useDownloadUi();
        final int intervalMs = fastUi ? 450 : 2000;
        final int maxPolls = fastUi ? 2000 : 60;

        pollingHandler = new Handler(Looper.getMainLooper());
        pollingRunnable = new Runnable() {
            int pollCount = 0;

            @Override
            public void run() {
                pollCount++;
                Log.d(TAG, "Polling download status... attempt " + pollCount + "/" + maxPolls);

                if (pollCount > maxPolls) {
                    Log.w(TAG, "Download polling timeout reached");
                    if (fastUi) {
                        postToUi(() -> downloadUiCallbacks.onError(
                            "Download is taking too long. Check the system notification, or try again.", true));
                    } else {
                        ModernToast.warning(context, "⏰ Download taking too long - check notification");
                    }
                    stopPolling();
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
                            stopPolling();
                            handleDownloadComplete();
                            return;
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            Log.e(TAG, "❌ Download failed via polling");
                            cursor.close();
                            stopPolling();
                            handleDownloadComplete();
                            return;
                        } else if (fastUi && downloadUiCallbacks != null) {
                            int percent = -1;
                            if (bytesTotal > 0) {
                                percent = (int) ((bytesDownloaded * 100) / bytesTotal);
                            }
                            if (status == DownloadManager.STATUS_PENDING) {
                                postToUi(() -> downloadUiCallbacks.onDownloadProgress(-1, 0, 0));
                            } else if (status == DownloadManager.STATUS_RUNNING) {
                                final int p = percent;
                                final long soFar = bytesDownloaded;
                                final long total = bytesTotal;
                                postToUi(() -> downloadUiCallbacks.onDownloadProgress(p, soFar, total));
                            } else if (status == DownloadManager.STATUS_PAUSED) {
                                postToUi(() -> downloadUiCallbacks.onDownloadProgress(-1, bytesDownloaded, bytesTotal));
                            }
                        } else if (status == DownloadManager.STATUS_RUNNING) {
                            if (bytesTotal > 0) {
                                int progress = (int) ((bytesDownloaded * 100) / bytesTotal);
                                Log.d(TAG, "📥 Download progress: " + progress + "%");
                            }
                        }
                    }
                    cursor.close();

                    if (pollingHandler != null && pollingRunnable != null) {
                        pollingHandler.postDelayed(pollingRunnable, intervalMs);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error polling download status", e);
                    if (pollingHandler != null && pollingRunnable != null) {
                        pollingHandler.postDelayed(pollingRunnable, intervalMs);
                    }
                }
            }
        };

        long initialDelay = fastUi ? 150 : 5000;
        pollingHandler.postDelayed(pollingRunnable, initialDelay);
    }
    
    /**
     * Stop polling for download status
     */
    private void stopPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            Log.d(TAG, "Stopping download status polling");
            pollingHandler.removeCallbacks(pollingRunnable);
            pollingHandler = null;
            pollingRunnable = null;
        }
    }
    
    /**
     * Clean up any previous download before starting a new one
     */
    private void cleanupPreviousDownload() {
        // Stop any ongoing polling
        stopPolling();
        
        // Unregister any previous broadcast receiver
        try {
            if (downloadReceiver != null) {
                appContext.unregisterReceiver(downloadReceiver);
                Log.d(TAG, "Unregistered previous download receiver");
            }
        } catch (IllegalArgumentException e) {
            // Receiver wasn't registered, ignore
        }
        
        // Re-setup the receiver
        setupDownloadReceiver();
        
        // Cancel previous download if exists
        if (downloadId != -1) {
            try {
                downloadManager.remove(downloadId);
                Log.d(TAG, "Cancelled previous download ID: " + downloadId);
            } catch (Exception e) {
                Log.w(TAG, "Error cancelling previous download", e);
            }
            downloadId = -1;
        }
        downloadCompletionHandled = false;
    }

    /**
     * Handle download completion and start installation
     */
    private void handleDownloadComplete() {
        final long id = downloadId;
        if (id == -1) {
            Log.d(TAG, "handleDownloadComplete: no active download id");
            return;
        }
        synchronized (this) {
            if (downloadCompletionHandled) {
                Log.d(TAG, "handleDownloadComplete: already handled, skipping duplicate");
                return;
            }
            downloadCompletionHandled = true;
        }
        try {
            // Query download status
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);
            
            Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    String localUri = cursor.getString(uriIndex);
                    
                    Log.d(TAG, "✅ Download completed successfully! Local URI: " + localUri);
                    if (useDownloadUi()) {
                        postToUi(() -> downloadUiCallbacks.onInstalling());
                    } else {
                        ModernToast.success(context, "✅ Download complete! Starting installation...");
                    }

                    if (localUri != null) {
                        installApk(localUri, id);
                    } else {
                        Log.e(TAG, "Download successful but local URI is null");
                        ModernToast.error(context, "❌ Download completed but file not found");
                        showDownloadError("Download completed but file location is unknown", true);
                    }
                } else {
                    // Get detailed error information
                    String errorMessage = getDownloadErrorMessage(cursor, status);
                    Log.e(TAG, "❌ Download failed with status: " + status + " - " + errorMessage);
                    if (!useDownloadUi()) {
                        ModernToast.error(context, "❌ Download failed: " + getSimpleErrorMessage(status));
                    }
                    showDownloadError(errorMessage, true);
                    
                    // Clean up failed download file
                    cleanupFailedDownload(cursor);
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
            downloadId = -1;
            stopPolling();
            try {
                appContext.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering receiver", e);
            }
        }
    }

    /**
     * Clean up failed download file
     */
    private void cleanupFailedDownload(Cursor cursor) {
        try {
            int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            if (uriIndex >= 0) {
                String localUri = cursor.getString(uriIndex);
                if (localUri != null) {
                    File failedFile = new File(Uri.parse(localUri).getPath());
                    if (failedFile.exists() && failedFile.delete()) {
                        Log.d(TAG, "Cleaned up failed download file: " + failedFile.getPath());
                    }
                }
            }
            
            // Also remove from DownloadManager
            if (downloadId != -1) {
                downloadManager.remove(downloadId);
                Log.d(TAG, "Removed failed download from DownloadManager");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up failed download", e);
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
        if (useDownloadUi()) {
            postToUi(() -> downloadUiCallbacks.onError(errorMessage, showRetryOption));
            return;
        }
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
                    String lastUrl = appContext.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
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
     * Install APK and delete file after installation.
     *
     * @param localUri value from {@link DownloadManager#COLUMN_LOCAL_URI} (may be file or content)
     * @param dmId     download id for fallbacks
     */
    private void installApk(String localUri, long dmId) {
        try {
            Uri parsed = Uri.parse(localUri);
            Uri apkUri = null;
            File apkFile = null;

            if ("content".equalsIgnoreCase(parsed.getScheme())) {
                apkUri = parsed;
            } else if ("file".equalsIgnoreCase(parsed.getScheme()) && parsed.getPath() != null) {
                apkFile = new File(parsed.getPath());
            }

            if (apkUri == null && (apkFile == null || !apkFile.exists()) && dmId != -1) {
                try {
                    @SuppressWarnings("deprecation")
                    Uri legacy = downloadManager.getUriForDownloadedFile(dmId);
                    if (legacy != null) {
                        apkUri = legacy;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "getUriForDownloadedFile fallback failed", e);
                }
            }

            if (apkUri == null && apkFile != null && apkFile.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    apkUri = FileProvider.getUriForFile(appContext,
                        appContext.getPackageName() + ".fileprovider", apkFile);
                } else {
                    apkUri = Uri.fromFile(apkFile);
                }
            }

            if (apkUri == null) {
                Log.e(TAG, "Could not resolve install URI for: " + localUri);
                if (useDownloadUi()) {
                    postToUi(() -> downloadUiCallbacks.onError(
                        "Could not open the downloaded APK. Try opening the download page.", true));
                } else {
                    ModernToast.error(context, "❌ Could not open the downloaded APK. Try Manual Download.");
                }
                return;
            }

            if (useDownloadUi()) {
                postToUi(() -> downloadUiCallbacks.onOpeningSystemInstaller());
            }

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");

            appContext.startActivity(installIntent);

            if (!useDownloadUi()) {
                ModernToast.info(context,
                    "🚀 Opening installer... APK will be auto-deleted after installation");
            }

            Log.d(TAG, "✅ Installation intent started for: " + apkUri);

            final File fileToDelete = apkFile;
            if (fileToDelete != null && fileToDelete.exists()) {
                executor.execute(() -> {
                    try {
                        Thread.sleep(5000);
                        if (fileToDelete.exists() && fileToDelete.delete()) {
                            Log.d(TAG, "APK file deleted successfully: " + fileToDelete.getPath());
                        } else {
                            Log.w(TAG, "Failed to delete APK file: " + fileToDelete.getPath());
                        }
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Sleep interrupted", e);
                    } catch (Exception e) {
                        Log.e(TAG, "Error deleting APK file", e);
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error installing APK", e);
            if (useDownloadUi()) {
                postToUi(() -> downloadUiCallbacks.onError(
                    "Installation could not start. Open the download page and install manually.", true));
            } else {
                ModernToast.error(context, "❌ Installation failed. Please install manually.");
            }

            try {
                Uri parsed = Uri.parse(localUri);
                if ("file".equalsIgnoreCase(parsed.getScheme()) && parsed.getPath() != null) {
                    File f = new File(parsed.getPath());
                    if (f.exists() && f.delete()) {
                        Log.d(TAG, "Cleaned up APK file after installation error");
                    }
                }
            } catch (Exception cleanupError) {
                Log.w(TAG, "Failed to clean up APK after error", cleanupError);
            }
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
            appContext.startActivity(intent);
            
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

        // Do not unregister while a download is in flight — handleDownloadComplete does that.
        if (downloadId != -1) {
            return;
        }
        try {
            if (downloadReceiver != null) {
                appContext.unregisterReceiver(downloadReceiver);
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
