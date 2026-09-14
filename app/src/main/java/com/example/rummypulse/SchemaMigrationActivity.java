package com.example.rummypulse;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.data.FirestoreCollections;
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.databinding.ActivitySchemaMigrationBinding;
import com.example.rummypulse.utils.SafePlayPolicyStore;
import com.example.rummypulse.utils.SchemaVersionStore;
import com.example.rummypulse.utils.SessionCacheCleaner;
import com.example.rummypulse.utils.VersionGate;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

/** Blocks normal app access until the production Firestore schema-v3 cutover is complete. */
public class SchemaMigrationActivity extends AppCompatActivity {

    private ActivitySchemaMigrationBinding binding;
    private boolean requestInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (VersionGate.redirectIfCachedVersionRequiresUpdate(this)) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            open(LoginActivity.class);
            return;
        }
        if (SchemaVersionStore.isPrepared(this)) {
            openAuthenticatedDestination(user);
            return;
        }
        binding = ActivitySchemaMigrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.buttonSchemaRetry.setOnClickListener(view -> checkSchema());
        binding.buttonSchemaExit.setOnClickListener(view -> finishAffinity());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
        checkSchema();
    }

    private void checkSchema() {
        if (requestInProgress) {
            return;
        }
        requestInProgress = true;
        showLoading();
        if (SchemaVersionStore.isCacheCleared(this)) {
            fetchSchemaMarker();
            return;
        }
        SessionCacheCleaner.clearForSchemaMigration(this)
                .addOnSuccessListener(unused -> {
                    SchemaVersionStore.markCacheCleared(this);
                    fetchSchemaMarker();
                })
                .addOnFailureListener(error -> showBlocked(R.string.schema_gate_cache_error));
    }

    private void fetchSchemaMarker() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreCollections.GAME_DEFAULTS)
                .document("config")
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    Long version = snapshot.getLong("schemaVersion");
                    if (version != null && version == GameDataSchema.CURRENT_VERSION) {
                        SchemaVersionStore.markPrepared(this);
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user == null) {
                            open(LoginActivity.class);
                        } else {
                            openAuthenticatedDestination(user);
                        }
                    } else {
                        showBlocked(R.string.schema_gate_pending);
                    }
                })
                .addOnFailureListener(error -> showBlocked(R.string.schema_gate_check_error));
    }

    private void showLoading() {
        binding.schemaProgress.setVisibility(View.VISIBLE);
        binding.textSchemaStatus.setText(R.string.schema_gate_checking);
        binding.buttonSchemaRetry.setVisibility(View.GONE);
    }

    private void showBlocked(int message) {
        requestInProgress = false;
        binding.schemaProgress.setVisibility(View.GONE);
        binding.textSchemaStatus.setText(message);
        binding.buttonSchemaRetry.setVisibility(View.VISIBLE);
    }

    private void openAuthenticatedDestination(FirebaseUser user) {
        Class<?> destination = SafePlayPolicyStore.hasCurrentAcceptance(this, user.getUid())
                ? MainActivity.class
                : SafePlayPolicyActivity.class;
        open(destination);
    }

    private void open(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
