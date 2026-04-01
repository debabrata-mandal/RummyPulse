package com.example.rummypulse;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.utils.ModernUpdateChecker;
import com.example.rummypulse.utils.ModernToast;

/**
 * Full-screen blocking UI for APK download and install. Used for soft updates (from MainActivity)
 * and required updates (from {@link MinimumVersionActivity}).
 */
public class UpdateProgressActivity extends AppCompatActivity implements ModernUpdateChecker.DownloadUiCallbacks {

    public static final String EXTRA_DIRECT_DOWNLOAD_URL = "direct_download_url";
    public static final String EXTRA_FETCH_LATEST = "fetch_latest";
    public static final String EXTRA_FALLBACK_BROWSER_URL = "fallback_browser_url";
    public static final String EXTRA_STRICT_BLOCK = "strict_block";

    private ModernUpdateChecker updateChecker;
    private boolean strictBlock;
    private String fallbackBrowserUrl;
    private boolean leavingForInstaller;
    private boolean errorVisible;
    private boolean workInProgress;

    private LinearLayout layoutContent;
    private LinearLayout layoutError;
    private TextView textTitle;
    private TextView textStatus;
    private TextView textDetail;
    private ProgressBar progressIndeterminate;
    private ProgressBar progressHorizontal;
    private TextView textErrorMessage;

    /**
     * Soft update: APK URL already known (e.g. from update dialog).
     */
    public static void startDirect(Activity from, String downloadUrl, boolean strictBlock) {
        Intent i = new Intent(from, UpdateProgressActivity.class);
        i.putExtra(EXTRA_FETCH_LATEST, false);
        i.putExtra(EXTRA_DIRECT_DOWNLOAD_URL, downloadUrl);
        i.putExtra(EXTRA_STRICT_BLOCK, strictBlock);
        from.startActivity(i);
    }

    /**
     * Resolve latest APK via GitHub API, then download. {@code fallbackBrowserUrl} may be null to use
     * {@link R.string#default_apk_download_page}.
     */
    public static void startFetchLatest(Activity from, @Nullable String fallbackBrowserUrl, boolean strictBlock) {
        Intent i = new Intent(from, UpdateProgressActivity.class);
        i.putExtra(EXTRA_FETCH_LATEST, true);
        if (fallbackBrowserUrl != null) {
            i.putExtra(EXTRA_FALLBACK_BROWSER_URL, fallbackBrowserUrl);
        }
        i.putExtra(EXTRA_STRICT_BLOCK, strictBlock);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_progress);

        strictBlock = getIntent().getBooleanExtra(EXTRA_STRICT_BLOCK, false);
        fallbackBrowserUrl = getIntent().getStringExtra(EXTRA_FALLBACK_BROWSER_URL);
        if (fallbackBrowserUrl == null) {
            fallbackBrowserUrl = "";
        }
        fallbackBrowserUrl = fallbackBrowserUrl.trim();

        layoutContent = findViewById(R.id.layout_update_progress_content);
        layoutError = findViewById(R.id.layout_update_progress_error);
        textTitle = findViewById(R.id.text_update_progress_title);
        textStatus = findViewById(R.id.text_update_progress_status);
        textDetail = findViewById(R.id.text_update_progress_detail);
        progressIndeterminate = findViewById(R.id.progress_update_indeterminate);
        progressHorizontal = findViewById(R.id.progress_update_horizontal);
        textErrorMessage = findViewById(R.id.text_update_progress_error_message);

        findViewById(R.id.button_update_progress_retry).setOnClickListener(v -> retryDownload());
        findViewById(R.id.button_update_progress_browser).setOnClickListener(v -> openBrowserFallback());
        findViewById(R.id.button_update_progress_close).setOnClickListener(v -> closeOrExit());

        updateChecker = new ModernUpdateChecker(this);
        updateChecker.setDownloadUiCallbacks(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (errorVisible) {
                    closeOrExit();
                    return;
                }
                if (workInProgress) {
                    ModernToast.warning(UpdateProgressActivity.this, getString(R.string.update_progress_back_blocked));
                    return;
                }
                closeOrExit();
            }
        });

        boolean fetchLatest = getIntent().getBooleanExtra(EXTRA_FETCH_LATEST, false);
        if (fetchLatest) {
            workInProgress = true;
            textStatus.setText(R.string.update_progress_fetching);
            progressIndeterminate.setVisibility(View.VISIBLE);
            progressHorizontal.setVisibility(View.GONE);
            updateChecker.runFetchLatestApkDownload(this::onFetchLatestFailed);
        } else {
            String url = getIntent().getStringExtra(EXTRA_DIRECT_DOWNLOAD_URL);
            if (url == null || url.trim().isEmpty()) {
                showInlineError(getString(R.string.update_progress_error_title), false);
                return;
            }
            workInProgress = true;
            textStatus.setText(R.string.update_progress_downloading);
            progressIndeterminate.setVisibility(View.VISIBLE);
            progressHorizontal.setVisibility(View.GONE);
            updateChecker.startBlockingDownload(url.trim());
        }
    }

    private void onFetchLatestFailed() {
        runOnUiThread(() -> {
            workInProgress = false;
            openBrowserFallback();
            if (strictBlock) {
                finishAffinity();
            } else {
                finish();
            }
        });
    }

    private void retryDownload() {
        layoutError.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
        errorVisible = false;
        String url = getSharedPreferences("update_prefs", MODE_PRIVATE).getString("last_download_url", null);
        if (url == null || url.isEmpty()) {
            url = getIntent().getStringExtra(EXTRA_DIRECT_DOWNLOAD_URL);
        }
        if (url == null || url.trim().isEmpty()) {
            if (getIntent().getBooleanExtra(EXTRA_FETCH_LATEST, false)) {
                workInProgress = true;
                textStatus.setText(R.string.update_progress_fetching);
                progressIndeterminate.setVisibility(View.VISIBLE);
                updateChecker.runFetchLatestApkDownload(this::onFetchLatestFailed);
                return;
            }
            showInlineError(getString(R.string.update_progress_error_title), false);
            return;
        }
        workInProgress = true;
        textStatus.setText(R.string.update_progress_downloading);
        progressIndeterminate.setVisibility(View.VISIBLE);
        progressHorizontal.setVisibility(View.GONE);
        updateChecker.startBlockingDownload(url.trim());
    }

    private void openBrowserFallback() {
        String url = fallbackBrowserUrl;
        if (url == null || url.isEmpty()) {
            url = getString(R.string.default_apk_download_page);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            updateChecker.openReleasesInBrowser();
        }
    }

    private void closeOrExit() {
        if (strictBlock) {
            finishAffinity();
        } else {
            finish();
        }
    }

    private void showInlineError(String message, boolean allowRetry) {
        errorVisible = true;
        workInProgress = false;
        layoutContent.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        textErrorMessage.setText(message);
        findViewById(R.id.button_update_progress_retry).setVisibility(allowRetry ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onFetchStarted() {
        runOnUiThread(() -> {
            textStatus.setText(R.string.update_progress_fetching);
            progressIndeterminate.setVisibility(View.VISIBLE);
            progressHorizontal.setVisibility(View.GONE);
        });
    }

    @Override
    public void onDownloadStarted() {
        runOnUiThread(() -> {
            textStatus.setText(R.string.update_progress_downloading);
            progressIndeterminate.setVisibility(View.VISIBLE);
            progressHorizontal.setVisibility(View.GONE);
            textDetail.setText(getString(R.string.update_progress_connecting));
        });
    }

    @Override
    public void onDownloadProgress(int percent, long bytesSoFar, long bytesTotal) {
        runOnUiThread(() -> {
            if (percent >= 0 && bytesTotal > 0) {
                progressIndeterminate.setVisibility(View.GONE);
                progressHorizontal.setVisibility(View.VISIBLE);
                progressHorizontal.setIndeterminate(false);
                progressHorizontal.setProgress(Math.min(100, percent));
                textDetail.setText(getString(R.string.update_progress_bytes,
                    Formatter.formatFileSize(this, bytesSoFar),
                    Formatter.formatFileSize(this, bytesTotal)));
            } else {
                progressHorizontal.setVisibility(View.GONE);
                progressIndeterminate.setVisibility(View.VISIBLE);
                if (bytesSoFar > 0) {
                    textDetail.setText(getString(R.string.update_progress_bytes_unknown,
                        Formatter.formatFileSize(this, bytesSoFar)));
                } else {
                    textDetail.setText(getString(R.string.update_progress_connecting));
                }
            }
        });
    }

    @Override
    public void onInstalling() {
        runOnUiThread(() -> {
            textStatus.setText(R.string.update_progress_installing);
            progressIndeterminate.setVisibility(View.VISIBLE);
            progressHorizontal.setVisibility(View.GONE);
            textDetail.setText("");
        });
    }

    @Override
    public void onOpeningSystemInstaller() {
        runOnUiThread(() -> {
            leavingForInstaller = true;
            workInProgress = false;
            finish();
        });
    }

    @Override
    public void onError(String message, boolean allowRetry) {
        runOnUiThread(() -> showInlineError(message, allowRetry));
    }

    @Override
    protected void onDestroy() {
        if (updateChecker != null) {
            updateChecker.setDownloadUiCallbacks(null);
            if (!leavingForInstaller) {
                updateChecker.cleanup();
            }
        }
        super.onDestroy();
    }
}
