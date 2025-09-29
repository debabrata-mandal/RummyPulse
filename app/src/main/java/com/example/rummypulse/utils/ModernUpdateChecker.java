package com.example.rummypulse.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import com.example.rummypulse.R;

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
    
    private final Context context;
    private final ExecutorService executor;
    
    public ModernUpdateChecker(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Check for updates and show dialog if newer version is available
     */
    public void checkForUpdates() {
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
                    Toast.makeText(context, "💡 You can check for updates anytime in Settings", 
                                 Toast.LENGTH_SHORT).show();
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
     * Download and install update
     */
    private void downloadUpdate(String downloadUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            
            Toast.makeText(context, 
                "📥 Opening download... Please install when complete", 
                Toast.LENGTH_LONG).show();
                
        } catch (Exception e) {
            Log.e(TAG, "Error opening download URL", e);
            openGitHubReleases();
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
            
            Toast.makeText(context, 
                "🌐 Opening GitHub releases page", 
                Toast.LENGTH_SHORT).show();
                
        } catch (Exception e) {
            Log.e(TAG, "Error opening GitHub releases", e);
            Toast.makeText(context, "❌ Please check for updates manually", 
                         Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
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
