package com.example.rummypulse;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.utils.ModernUpdateChecker;

/**
 * Full-screen blocking UI when the installed build is below the Remote Config minimum version.
 */
public class MinimumVersionActivity extends AppCompatActivity {

    public static final String EXTRA_UPDATE_URL = "update_url";

    private ModernUpdateChecker updateChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minimum_version);

        updateChecker = new ModernUpdateChecker(this);

        TextView message = findViewById(R.id.text_minimum_version_message);
        message.setText(R.string.minimum_version_message);

        Button updateButton = findViewById(R.id.button_minimum_version_update);
        updateButton.setOnClickListener(v ->
                updateChecker.downloadLatestReleaseApkOrFallback(this::openUpdateUrl));

        Button exitButton = findViewById(R.id.button_minimum_version_exit);
        exitButton.setOnClickListener(v -> finishAffinity());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }

    private void openUpdateUrl() {
        String url = getIntent().getStringExtra(EXTRA_UPDATE_URL);
        if (url == null) {
            url = "";
        }
        url = url.trim();
        if (url.isEmpty()) {
            url = getString(R.string.default_apk_download_page);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.default_apk_download_page))));
        }
    }
}
