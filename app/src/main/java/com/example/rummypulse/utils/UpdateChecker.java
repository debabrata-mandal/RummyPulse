package com.example.rummypulse.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class to check for app updates from GitHub releases
 */
public class UpdateChecker {
    
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/YOUR_USERNAME/RummyPulse/releases/latest";
    
    private final Context context;
    
    public UpdateChecker(Context context) {
        this.context = context;
    }
    
    /**
     * Check for updates and show dialog if newer version is available
     */
    public void checkForUpdates() {
        Log.d(TAG, "Checking for app updates...");
        new CheckUpdateTask().execute();
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
     * Compare version strings
     * Returns: -1 if current < latest, 0 if equal, 1 if current > latest
     */
    private int compareVersions(String currentVersion, String latestVersion) {
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
    }
    
    /**
     * Show update available dialog
     */
    private void showUpdateDialog(String latestVersion, String downloadUrl, String releaseNotes) {
        if (!(context instanceof Activity)) {
            Log.w(TAG, "Context is not an Activity, cannot show dialog");
            return;
        }
        
        Activity activity = (Activity) context;
        
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                .setTitle("🚀 Update Available")
                .setMessage("A new version (" + latestVersion + ") is available!\n\n" +
                           "Current version: " + getCurrentVersion() + "\n" +
                           "Latest version: " + latestVersion + "\n\n" +
                           "What's new:\n" + releaseNotes)
                .setPositiveButton("Update Now", (dialog, which) -> {
                    downloadAndInstallUpdate(downloadUrl);
                })
                .setNegativeButton("Later", (dialog, which) -> {
                    com.example.rummypulse.utils.ModernToast.info(context, "You can update later from Settings");
                })
                .setCancelable(false)
                .show();
        });
    }
    
    /**
     * Download and install update
     */
    private void downloadAndInstallUpdate(String downloadUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            
            com.example.rummypulse.utils.ModernToast.progress(context, 
                "Downloading update... Please install when download completes");
                
        } catch (Exception e) {
            Log.e(TAG, "Error opening download URL", e);
            com.example.rummypulse.utils.ModernToast.error(context, "Error opening download. Please update manually.");
        }
    }
    
    /**
     * AsyncTask to check for updates in background
     */
    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {
        
        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
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
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String latestVersion = jsonResponse.getString("tag_name");
                    String releaseNotes = jsonResponse.getString("body");
                    String downloadUrl = jsonResponse.getJSONArray("assets")
                                                   .getJSONObject(0)
                                                   .getString("browser_download_url");
                    
                    return new UpdateInfo(latestVersion, releaseNotes, downloadUrl);
                } else {
                    Log.e(TAG, "HTTP error code: " + responseCode);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
            }
            
            return null;
        }
        
        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            if (updateInfo != null) {
                String currentVersion = getCurrentVersion();
                
                if (compareVersions(currentVersion, updateInfo.version) < 0) {
                    Log.d(TAG, "Update available: " + currentVersion + " -> " + updateInfo.version);
                    showUpdateDialog(updateInfo.version, updateInfo.downloadUrl, updateInfo.releaseNotes);
                } else {
                    Log.d(TAG, "App is up to date: " + currentVersion);
                }
            } else {
                Log.w(TAG, "Could not check for updates");
            }
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
