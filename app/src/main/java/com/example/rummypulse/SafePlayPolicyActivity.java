package com.example.rummypulse;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.SafePlayPolicy;
import com.example.rummypulse.databinding.ActivitySafePlayPolicyBinding;
import com.example.rummypulse.utils.AccountSignOut;
import com.example.rummypulse.utils.PendingProfileOverrides;
import com.example.rummypulse.utils.ProfileSyncHelper;
import com.example.rummypulse.utils.SafePlayPolicyStore;
import com.example.rummypulse.utils.SessionCacheCleaner;
import com.example.rummypulse.utils.VersionGate;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Blocks access to the app until the signed-in user accepts the current safe-play policy. */
public class SafePlayPolicyActivity extends AppCompatActivity {

    private ActivitySafePlayPolicyBinding binding;
    private final AppUserRepository appUserRepository = new AppUserRepository();
    private FirebaseUser currentUser;
    private boolean profileReady;
    private boolean requestInProgress;
    private boolean leavingActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (VersionGate.redirectIfCachedVersionRequiresUpdate(this)) {
            return;
        }

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            returnToLogin(false);
            return;
        }
        if (SafePlayPolicyStore.hasCurrentAcceptance(this, currentUser.getUid())) {
            openMainActivity();
            return;
        }

        binding = ActivitySafePlayPolicyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        configureActions();
        loadProfileAndPolicy();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                declineAndSignOut();
            }
        });
        VersionGate.refreshInBackground(this);
    }

    private void configureActions() {
        binding.checkboxNoValue.setOnCheckedChangeListener((button, checked) ->
                updateAcceptButton());
        binding.checkboxNoStakes.setOnCheckedChangeListener((button, checked) ->
                updateAcceptButton());
        binding.btnAccept.setOnClickListener(view -> acceptPolicy());
        binding.btnDecline.setOnClickListener(view -> declineAndSignOut());
        binding.btnDeclineLoading.setOnClickListener(view -> declineAndSignOut());
        binding.btnRetry.setOnClickListener(view -> loadProfileAndPolicy());
        binding.btnReadPolicy.setOnClickListener(view -> openPolicyPage());
    }

    private void loadProfileAndPolicy() {
        if (requestInProgress || currentUser == null) {
            return;
        }
        requestInProgress = true;
        profileReady = false;
        binding.policyContent.setVisibility(View.GONE);
        binding.loadingContainer.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnRetry.setVisibility(View.GONE);
        binding.textStatus.setText(R.string.safe_play_loading);

        AppUserRepository.ProfileOverrides overrides = PendingProfileOverrides.consumeOverrides();
        boolean forceProfileVersionRefresh =
                PendingProfileOverrides.consumeForceProfileVersionRefresh();
        if (overrides != null) {
            com.example.rummypulse.utils.CurrentUserProfileSession.applyOverrides(
                    overrides.displayName,
                    overrides.photoUrl);
        }
        ProfileSyncHelper.reloadAndSync(
                currentUser,
                AppUserRepository.getProviderName(currentUser),
                overrides,
                forceProfileVersionRefresh,
                new AppUserRepository.AppUserCallback() {
                    @Override
                    public void onSuccess(AppUser appUser) {
                        if (leavingActivity || isFinishing()) {
                            return;
                        }
                        requestInProgress = false;
                        if (SafePlayPolicy.hasCurrentAcceptance(appUser)) {
                            SafePlayPolicyStore.cacheCurrentAcceptance(
                                    SafePlayPolicyActivity.this, currentUser.getUid());
                            openMainActivity();
                            return;
                        }
                        profileReady = true;
                        binding.loadingContainer.setVisibility(View.GONE);
                        binding.policyContent.setVisibility(View.VISIBLE);
                        updateAcceptButton();
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        if (leavingActivity || isFinishing()) {
                            return;
                        }
                        requestInProgress = false;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.textStatus.setText(R.string.safe_play_load_error);
                        binding.btnRetry.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void acceptPolicy() {
        if (!profileReady || requestInProgress || currentUser == null
                || !allConfirmationsChecked()) {
            return;
        }
        requestInProgress = true;
        binding.acceptProgress.setVisibility(View.VISIBLE);
        binding.textAcceptanceError.setVisibility(View.GONE);
        updateAcceptButton();

        appUserRepository.acceptSafePlayPolicy(
                        currentUser.getUid(), SafePlayPolicy.CURRENT_VERSION)
                .addOnSuccessListener(unused -> {
                    if (leavingActivity || isFinishing()) {
                        return;
                    }
                    requestInProgress = false;
                    SafePlayPolicyStore.cacheCurrentAcceptance(
                            SafePlayPolicyActivity.this, currentUser.getUid());
                    openMainActivity();
                })
                .addOnFailureListener(exception -> {
                    if (leavingActivity || isFinishing()) {
                        return;
                    }
                    requestInProgress = false;
                    binding.acceptProgress.setVisibility(View.GONE);
                    binding.textAcceptanceError.setText(R.string.safe_play_acceptance_error);
                    binding.textAcceptanceError.setVisibility(View.VISIBLE);
                    updateAcceptButton();
                });
    }

    private void updateAcceptButton() {
        if (binding == null) {
            return;
        }
        binding.btnAccept.setEnabled(
                profileReady && !requestInProgress && allConfirmationsChecked());
    }

    private boolean allConfirmationsChecked() {
        return binding.checkboxNoValue.isChecked()
                && binding.checkboxNoStakes.isChecked();
    }

    private void openPolicyPage() {
        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.game_points_policy_url)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            com.example.rummypulse.utils.ModernToast.error(
                    this, getString(R.string.game_points_policy_open_error));
        }
    }

    private void declineAndSignOut() {
        if (leavingActivity) {
            return;
        }
        leavingActivity = true;
        setActionsEnabled(false);
        AccountSignOut.signOut(this).addOnCompleteListener(task -> {
            SessionCacheCleaner.clearAll(SafePlayPolicyActivity.this);
            returnToLogin(true);
        });
    }

    private void setActionsEnabled(boolean enabled) {
        if (binding == null) {
            return;
        }
        binding.btnAccept.setEnabled(enabled);
        binding.btnDecline.setEnabled(enabled);
        binding.btnDeclineLoading.setEnabled(enabled);
        binding.btnRetry.setEnabled(enabled);
        binding.btnReadPolicy.setEnabled(enabled);
    }

    private void openMainActivity() {
        if (leavingActivity) {
            return;
        }
        leavingActivity = true;
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void returnToLogin(boolean requireLogin) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_REQUIRE_LOGIN, requireLogin);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
