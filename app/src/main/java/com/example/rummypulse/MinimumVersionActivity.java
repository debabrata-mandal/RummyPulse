package com.example.rummypulse;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen blocking UI when the installed build is below the Remote Config minimum version.
 */
public class MinimumVersionActivity extends AppCompatActivity {

    public static final String EXTRA_UPDATE_URL = "update_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minimum_version);

        TextView message = findViewById(R.id.text_minimum_version_message);
        message.setText(R.string.minimum_version_message);

        Button updateButton = findViewById(R.id.button_minimum_version_update);
        updateButton.setOnClickListener(v -> {
            UpdateProgressActivity.startFetchLatest(this,
                getIntent().getStringExtra(EXTRA_UPDATE_URL),
                true);
            finish();
        });

        Button exitButton = findViewById(R.id.button_minimum_version_exit);
        exitButton.setOnClickListener(v -> finishAffinity());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }
}
