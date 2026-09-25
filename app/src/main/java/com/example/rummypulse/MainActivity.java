package com.example.rummypulse;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;

import java.util.List;
import java.util.Locale;

import com.example.rummypulse.utils.LanguagePreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.PlayerLeaderboardRepository;
import com.example.rummypulse.ui.home.GameItem;
import com.example.rummypulse.service.AccountDeletionGateway;
import com.example.rummypulse.service.FirebaseAccountDeletionService;
import com.example.rummypulse.service.ProfileNameService;
import com.example.rummypulse.utils.AuthStateManager;
import com.example.rummypulse.utils.AccountSignOut;
import com.example.rummypulse.utils.CurrentUserProfileSession;
import com.example.rummypulse.utils.PendingProfileOverrides;
import com.example.rummypulse.utils.ProfileAvatarLoader;
import com.example.rummypulse.utils.ProfileSyncHelper;
import com.example.rummypulse.utils.ProfileNameValidator;
import com.example.rummypulse.utils.SessionCacheCleaner;
import com.example.rummypulse.utils.SafePlayPolicyStore;
import com.example.rummypulse.utils.ModernToast;
import com.example.rummypulse.utils.ModernUpdateChecker;
import com.example.rummypulse.utils.VersionGate;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.Observer;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.rummypulse.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final int NAV_HEADER_BASE_TOP_PADDING_DP = 24;

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ModernUpdateChecker updateChecker;
    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private NavController navController;
    private AppUserRoleSession.Role currentNavigationRole = AppUserRoleSession.Role.UNKNOWN;
    private boolean reviewNeedsAttention = false;
    private boolean initialAppUserSyncCompleted;
    private boolean hasStartedOnce;
    private boolean profilePromptShown;
    private boolean accountDeletionInProgress;
    private GoogleSignInClient accountDeletionGoogleClient;
    private androidx.appcompat.app.AlertDialog accountDeletionProgressDialog;
    private final AccountDeletionGateway accountDeletionGateway =
            new FirebaseAccountDeletionService();
    private final ActivityResultLauncher<Intent> accountDeletionReauthLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> handleAccountDeletionReauthentication(result.getData()));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long startupStartedAt = SystemClock.elapsedRealtime();
        if (VersionGate.redirectIfCachedVersionRequiresUpdate(this)) {
            return;
        }
        continueMainOnCreateAfterVersionGate(savedInstanceState);
        android.util.Log.d("MainActivity", "Main UI initialized in "
                + (SystemClock.elapsedRealtime() - startupStartedAt) + " ms");
        VersionGate.refreshInBackground(this);
    }

    private void continueMainOnCreateAfterVersionGate(Bundle savedInstanceState) {
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Create auth state listener
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    if (accountDeletionInProgress) {
                        return;
                    }
                    android.util.Log.d("MainActivity", "User signed out, redirecting to login");
                    SessionCacheCleaner.clearAll(MainActivity.this);
                    Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
                    loginIntent.putExtra(LoginActivity.EXTRA_REQUIRE_LOGIN, true);
                    loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(loginIntent);
                    finish();
                }
            }
        };

        // Check if user is authenticated with force stop detection
        FirebaseUser currentUser = mAuth.getCurrentUser();
        AuthStateManager authStateManager = AuthStateManager.getInstance(this);

        if (currentUser == null) {
            // Check if this might be due to force stop
            if (authStateManager.shouldBeAuthenticated()) {
                android.util.Log.w("MainActivity", "User should be authenticated but Firebase Auth shows null");
                android.util.Log.w("MainActivity",
                        "This might be due to force stop or an interrupted session");

                // Show a toast to inform user about session restoration
                com.example.rummypulse.utils.ModernToast.warning(this,
                    "Session was interrupted. Please sign in again.");
            } else {
                android.util.Log.d("MainActivity", "No current user found, redirecting to login");
            }

            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (!SafePlayPolicyStore.hasCurrentAcceptance(this, currentUser.getUid())) {
            Intent policyIntent = new Intent(this, SafePlayPolicyActivity.class);
            policyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(policyIntent);
            finish();
            return;
        }
        android.util.Log.d("MainActivity", "User authenticated");
        authStateManager.saveAuthState(currentUser);
        AppUserRoleSession.getInstance().startForCurrentUser(false);
        ensureAppUserDocument(currentUser);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_dashboard, R.id.nav_home, R.id.nav_reports, R.id.nav_game_defaults,
                R.id.nav_player_consolidation, R.id.nav_player_ranking)
                .setOpenableLayout(drawerLayout)
                .build();
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        navController = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        AppUserRoleSession.getInstance().getRole().observe(this, new Observer<AppUserRoleSession.Role>() {
            @Override
            public void onChanged(AppUserRoleSession.Role role) {
                applyReviewMenuIconsFromRole(role);
                updateNavigationRoleBadge(role);
            }
        });
        applyNavigationMenuVisuals();
        configureNavigationFooter();
        observeReviewAttention();
        
        // Handle navigation item clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_profile) {
                showProfileNameDialog(false);
                drawerLayout.closeDrawers();
                return true;
            } else if (item.getItemId() == R.id.nav_voice_settings) {
                showVoiceSettingsDialog();
                drawerLayout.closeDrawers();
                return true;
            } else if (item.getItemId() == R.id.nav_share_app) {
                shareApp();
                drawerLayout.closeDrawers();
                return true;
            } else if (item.getItemId() == R.id.nav_app_info) {
                showAppInfoDialog();
                drawerLayout.closeDrawers();
                return true;
            } else if (item.getItemId() == R.id.nav_home) {
                AppUserRoleSession.Role r = AppUserRoleSession.getInstance().peekRole();
                if (r == AppUserRoleSession.Role.ADMIN) {
                    boolean handled = navigateFromDrawer(item);
                    if (handled) {
                        drawerLayout.closeDrawers();
                    }
                } else if (r == AppUserRoleSession.Role.NON_ADMIN) {
                    drawerLayout.closeDrawers();
                    com.example.rummypulse.utils.ModernToast.error(MainActivity.this,
                            "🔒 Access Denied: Admin privileges required for Review screen");
                } else {
                    drawerLayout.closeDrawers();
                    com.example.rummypulse.utils.ModernToast.warning(MainActivity.this,
                            "Still checking your access. Try again in a moment.");
                }
                return true;
            } else if (item.getItemId() == R.id.nav_user_management) {
                boolean handled = navigateFromDrawer(item);
                if (handled) {
                    drawerLayout.closeDrawers();
                }
                return handled;
            } else {
                boolean handled = navigateFromDrawer(item);
                if (handled) {
                    drawerLayout.closeDrawers();
                }
                return handled;
            }
        });
        
        // Update navigation header with user info
        updateNavigationHeader(navigationView, currentUser);
        updateNavigationRoleBadge(AppUserRoleSession.getInstance().peekRole());

        // Check for GitHub-distributed updates without requiring installer access at startup.
        initializeUpdateChecker();
    }

    /**
     * Every drawer item is a top level destination, so each tap should land on a clean stack.
     * NavigationUI's own options save the stack above the start destination and restore it on the
     * way back, which is meant for bottom navigation tabs. Here it means the ranking screen the
     * dashboard leaderboard pushed on top of the dashboard gets resurrected the next time the user
     * taps Dashboard.
     */
    private boolean navigateFromDrawer(MenuItem item) {
        if (navController == null || navController.getCurrentDestination() == null) {
            return false;
        }
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false, false)
                .setEnterAnim(androidx.navigation.ui.R.anim.nav_default_enter_anim)
                .setExitAnim(androidx.navigation.ui.R.anim.nav_default_exit_anim)
                .setPopEnterAnim(androidx.navigation.ui.R.anim.nav_default_pop_enter_anim)
                .setPopExitAnim(androidx.navigation.ui.R.anim.nav_default_pop_exit_anim)
                .build();
        try {
            navController.navigate(item.getItemId(), null, options);
            item.setChecked(true);
            return true;
        } catch (IllegalArgumentException e) {
            android.util.Log.w("MainActivity", "Drawer destination not reachable: " + item.getTitle(), e);
            return false;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        refreshRemoteProfileChanges();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Add auth listener when activity starts
        if (mAuth != null && mAuthListener != null) {
            mAuth.addAuthStateListener(mAuthListener);
            if (hasStartedOnce && initialAppUserSyncCompleted) {
                AppUserRoleSession.getInstance().refreshForCurrentUser();
            }
            hasStartedOnce = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Remove auth listener when activity stops to prevent memory leaks
        if (mAuth != null && mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up update checker resources
        if (updateChecker != null) {
            updateChecker.cleanup();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void ensureAppUserDocument(FirebaseUser user) {
        String provider = AppUserRepository.getProviderName(user);
        AppUserRepository.ProfileOverrides overrides = PendingProfileOverrides.consumeOverrides();
        boolean forceProfileVersionRefresh =
                PendingProfileOverrides.consumeForceProfileVersionRefresh();
        if (overrides != null) {
            CurrentUserProfileSession.applyOverrides(
                    overrides.displayName,
                    overrides.photoUrl);
        }
        ProfileSyncHelper.reloadAndSync(
                user,
                provider,
                overrides,
                forceProfileVersionRefresh,
                new AppUserRepository.AppUserCallback() {
                    @Override
                    public void onSuccess(AppUser appUser) {
                        android.util.Log.d("MainActivity", "appUser document synced");
                        initialAppUserSyncCompleted = true;
                        CurrentUserProfileSession.update(appUser);
                        if (navigationView != null) {
                            FirebaseUser currentUser = mAuth.getCurrentUser();
                            if (currentUser != null) {
                                updateNavigationHeader(navigationView, currentUser);
                            }
                        }
                        maybePromptForProfileName(appUser);
                        AppUserRoleSession.getInstance()
                                .applyVerifiedRole(appUser.getUserId(), appUser.getRole());
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        initialAppUserSyncCompleted = true;
                        android.util.Log.w("MainActivity",
                                "appUser sync failed — user may be missing from Users list until next successful sync",
                                exception);
                    }
                });
    }

    private void refreshRemoteProfileChanges() {
        new AppUserRepository().refreshProfileChangesSince(changedUsers -> {
            if (changedUsers == null || changedUsers.isEmpty() || navigationView == null) {
                return;
            }
            FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
            if (currentUser == null) {
                return;
            }
            for (AppUser changedUser : changedUsers) {
                if (changedUser != null
                        && currentUser.getUid().equals(changedUser.getUserId())) {
                    CurrentUserProfileSession.update(changedUser);
                    updateNavigationHeader(navigationView, currentUser);
                    break;
                }
            }
        });
    }

    private void updateNavigationHeader(NavigationView navigationView, FirebaseUser user) {
        android.view.View headerView = navigationView.getHeaderView(0);
        applyNavigationHeaderInsets(headerView);
        TextView nameTextView = headerView.findViewById(R.id.nav_header_title);
        TextView subtitleTextView = headerView.findViewById(R.id.nav_header_subtitle);
        TextView roleBadgeTextView = headerView.findViewById(R.id.nav_header_role_badge);
        ImageView profileImageView = headerView.findViewById(R.id.imageView);

        if (user != null) {
            String displayName = CurrentUserProfileSession.getDisplayName();
            String phone = user.getPhoneNumber();

            String nameLine;
            if (displayName != null && !displayName.trim().isEmpty()) {
                nameLine = displayName.trim();
            } else if (phone != null && !phone.trim().isEmpty()) {
                nameLine = phone.trim();
            } else {
                nameLine = getString(R.string.nav_header_name_fallback);
            }
            nameTextView.setText(nameLine);
            subtitleTextView.setText(R.string.nav_profile_subtitle);

            String photoUrl = CurrentUserProfileSession.getPhotoUrl();
            if (photoUrl == null && user.getPhotoUrl() != null) {
                photoUrl = user.getPhotoUrl().toString();
            }
            if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                ProfileAvatarLoader.loadCircle(
                        profileImageView,
                        photoUrl,
                        CurrentUserProfileSession.getProfileVersion());
            } else {
                profileImageView.setImageResource(R.drawable.ic_rummy_pulse_logo);
            }
        } else {
            nameTextView.setText(R.string.nav_header_title);
            subtitleTextView.setText(R.string.app_name);
            roleBadgeTextView.setText(R.string.nav_role_checking);
            profileImageView.setImageResource(R.drawable.ic_rummy_pulse_logo);
        }
    }

    private void maybePromptForProfileName(AppUser appUser) {
        if (profilePromptShown || appUser == null || !appUser.isProfileNameNeedsConfirmation()) {
            return;
        }
        profilePromptShown = true;
        showProfileNameDialog(true);
    }

    private void showProfileNameDialog(boolean onboarding) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_profile_name, null, false);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.layout_profile_name);
        TextInputEditText nameInput = dialogView.findViewById(R.id.edit_profile_name);
        MaterialButton skip = dialogView.findViewById(R.id.btn_profile_name_skip);
        MaterialButton save = dialogView.findViewById(R.id.btn_profile_name_save);
        String currentName = CurrentUserProfileSession.getProfileName();
        if (currentName != null) {
            nameInput.setText(currentName);
            nameInput.setSelection(currentName.length());
        }
        if (onboarding) {
            skip.setVisibility(View.GONE);
        } else {
            skip.setText(android.R.string.cancel);
        }

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                        .setView(dialogView)
                        .setCancelable(!onboarding)
                        .create();
        skip.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String value = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
            if (!ProfileNameValidator.isValid(value)) {
                nameLayout.setError(getString(R.string.profile_name_invalid));
                return;
            }
            nameLayout.setError(null);
            saveProfileName(value, nameLayout, skip, save, dialog);
        });
        dialog.show();
    }

    private void saveProfileName(String profileName, TextInputLayout nameLayout,
            MaterialButton skip, MaterialButton save, androidx.appcompat.app.AlertDialog dialog) {
        skip.setEnabled(false);
        save.setEnabled(false);
        save.setText(R.string.profile_name_saving);
        new ProfileNameService().save(profileName, new ProfileNameService.Callback() {
            @Override
            public void onSuccess(String savedProfileName, String displayName) {
                CurrentUserProfileSession.applyPublicProfile(savedProfileName, displayName);
                FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
                if (navigationView != null && user != null) {
                    updateNavigationHeader(navigationView, user);
                }
                dialog.dismiss();
                ModernToast.success(MainActivity.this, getString(R.string.profile_name_saved));
            }

            @Override
            public void onFailure(String message) {
                skip.setEnabled(true);
                save.setEnabled(true);
                save.setText(R.string.profile_name_save);
                nameLayout.setError(message);
            }
        });
    }

    private void applyNavigationHeaderInsets(android.view.View headerView) {
        if (headerView == null) {
            return;
        }

        int baseTopPadding = dpToPx(NAV_HEADER_BASE_TOP_PADDING_DP);
        headerView.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && insets.getDisplayCutout() != null) {
                topInset = Math.max(topInset, insets.getDisplayCutout().getSafeInsetTop());
            }
            view.setPadding(view.getPaddingLeft(), baseTopPadding + topInset,
                    view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
        headerView.requestApplyInsets();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void applyNavigationMenuVisuals() {
        if (navigationView == null) {
            return;
        }

        Menu menu = navigationView.getMenu();
        MenuItem appInfoItem = menu.findItem(R.id.nav_app_info);
        MenuItem voiceItem = menu.findItem(R.id.nav_voice_settings);

        if (voiceItem != null) {
            voiceItem.setTitle(R.string.menu_voice_announcements);
        }
        if (appInfoItem != null) {
            appInfoItem.setTitle("App Info");
        }
    }

    private void configureNavigationFooter() {
        TextView signOutFooter = findViewById(R.id.nav_footer_sign_out);
        TextView deleteAccountFooter = findViewById(R.id.nav_footer_delete_account);
        TextView versionFooter = findViewById(R.id.nav_footer_version);
        if (signOutFooter != null) {
            signOutFooter.setOnClickListener(v -> signOut());
        }
        if (deleteAccountFooter != null) {
            deleteAccountFooter.setOnClickListener(v -> showDeleteAccountConfirmation());
        }
        if (versionFooter != null) {
            versionFooter.setText(getString(R.string.nav_app_version, getAppVersionName()));
        }
    }

    private void shareApp() {
        String downloadUrl = getString(R.string.share_app_download_url);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text, downloadUrl));

        try {
            startActivity(Intent.createChooser(
                    shareIntent,
                    getString(R.string.share_app_chooser_title)));
        } catch (android.content.ActivityNotFoundException exception) {
            com.example.rummypulse.utils.ModernToast.error(
                    this,
                    getString(R.string.share_app_no_handler));
        }
    }

    private void updateNavigationRoleBadge(AppUserRoleSession.Role role) {
        if (navigationView == null || navigationView.getHeaderCount() == 0) {
            return;
        }
        android.view.View headerView = navigationView.getHeaderView(0);
        TextView roleBadgeTextView = headerView.findViewById(R.id.nav_header_role_badge);
        if (roleBadgeTextView == null) {
            return;
        }
        if (role == AppUserRoleSession.Role.ADMIN) {
            roleBadgeTextView.setText(R.string.nav_role_admin);
            roleBadgeTextView.setBackgroundResource(R.drawable.navigation_role_badge_admin);
        } else if (role == AppUserRoleSession.Role.NON_ADMIN) {
            roleBadgeTextView.setText(R.string.nav_role_player);
            roleBadgeTextView.setBackgroundResource(R.drawable.navigation_role_badge_player);
        } else {
            roleBadgeTextView.setText(R.string.nav_role_checking);
            roleBadgeTextView.setBackgroundResource(R.drawable.navigation_role_badge_checking);
        }
    }

    private String getAppVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName != null ? packageInfo.versionName : "1.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }
    
    private void applyReviewMenuIconsFromRole(AppUserRoleSession.Role role) {
        currentNavigationRole = role != null ? role : AppUserRoleSession.Role.UNKNOWN;
        if (navigationView == null) {
            return;
        }
        android.view.Menu menu = navigationView.getMenu();
        android.view.MenuItem reviewMenuItem = menu.findItem(R.id.nav_home);
        android.view.MenuItem userManagementMenuItem = menu.findItem(R.id.nav_user_management);

        if (reviewMenuItem != null) {
            if (role == AppUserRoleSession.Role.ADMIN) {
                reviewMenuItem.setIcon(R.drawable.ic_games_dashboard);
                reviewMenuItem.setTitle(buildReviewMenuTitle(reviewNeedsAttention));
            } else {
                reviewMenuItem.setIcon(R.drawable.ic_lock);
                reviewMenuItem.setTitle(R.string.menu_game_review);
            }
        }

        if (userManagementMenuItem != null) {
            userManagementMenuItem.setIcon(R.drawable.ic_people);
            userManagementMenuItem.setTitle(R.string.menu_user_management);
        }
    }

    private void observeReviewAttention() {
        GameRepository.getDashboardInstance().getGameItems().observe(this, this::updateReviewAttentionFromGames);
    }

    private void updateReviewAttentionFromGames(List<GameItem> gameItems) {
        boolean hasAttention = false;
        if (gameItems != null) {
            for (GameItem gameItem : gameItems) {
                if (gameItem != null
                        && (gameItem.isCompleted() || gameItem.getPendingViewRequestCount() > 0)) {
                    hasAttention = true;
                    break;
                }
            }
        }
        if (reviewNeedsAttention != hasAttention) {
            reviewNeedsAttention = hasAttention;
            applyReviewMenuIconsFromRole(currentNavigationRole);
        }
    }

    private CharSequence buildReviewMenuTitle(boolean showAttention) {
        String label = getString(R.string.menu_game_review);
        if (!showAttention) {
            return label;
        }

        String attentionLabel = label + "  •";
        SpannableString title = new SpannableString(attentionLabel);
        int markerStart = attentionLabel.length() - 1;
        title.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.warning_orange)),
                markerStart,
                attentionLabel.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return title;
    }

    /**
     * Initialize update checker and check for new versions
     */
    private void initializeUpdateChecker() {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "DEBUG build: skipping automatic update check");
            return;
        }

        updateChecker = new ModernUpdateChecker(this);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.util.Log.d("MainActivity", "Checking for app updates...");
            boolean isAdmin = AppUserRoleSession.getInstance().peekRole() == AppUserRoleSession.Role.ADMIN;
            if (isAdmin) {
                android.util.Log.d("MainActivity", "Admin user detected - skipping auto-update check");
            } else {
                android.util.Log.d("MainActivity", "Regular user - proceeding with auto-update check");
            }
            updateChecker.checkForUpdates(isAdmin);
        }, 2000);
    }

    private void signOut() {
        AccountSignOut.signOut(this).addOnCompleteListener(task -> {
            SessionCacheCleaner.clearAll(MainActivity.this, () -> {
                android.util.Log.d("MainActivity", "User signed out manually");
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.putExtra(LoginActivity.EXTRA_REQUIRE_LOGIN, true);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }

    private void showDeleteAccountConfirmation() {
        if (accountDeletionInProgress) {
            return;
        }
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_action_confirmation, null, false);
        ImageView icon = dialogView.findViewById(R.id.image_action_dialog_icon);
        TextView title = dialogView.findViewById(R.id.text_action_dialog_title);
        TextView subtitle = dialogView.findViewById(R.id.text_action_dialog_subtitle);
        TextView message = dialogView.findViewById(R.id.text_action_dialog_message);
        com.google.android.material.card.MaterialCardView messageCard =
                dialogView.findViewById(R.id.card_action_dialog_message);
        MaterialButton cancel = dialogView.findViewById(R.id.btn_action_dialog_cancel);
        MaterialButton confirm = dialogView.findViewById(R.id.btn_action_dialog_confirm);

        int red = ContextCompat.getColor(this, R.color.error_red);
        icon.setImageResource(R.drawable.ic_delete);
        icon.setBackgroundResource(R.drawable.view_access_icon_rejected_background);
        icon.setImageTintList(ColorStateList.valueOf(red));
        title.setText(R.string.account_delete_title);
        subtitle.setText(R.string.account_delete_subtitle);
        message.setText(R.string.account_delete_message);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                message, ColorStateList.valueOf(red));
        messageCard.setStrokeColor(red);
        cancel.setText(R.string.account_delete_keep);
        confirm.setText(R.string.account_delete_confirm);
        confirm.setBackgroundTintList(ColorStateList.valueOf(red));

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            beginAccountDeletionReauthentication();
        });
        dialog.show();
    }

    private void beginAccountDeletionReauthentication() {
        FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
        if (user == null) {
            ModernToast.error(this, getString(R.string.account_delete_reauthenticate_failed));
            return;
        }
        accountDeletionInProgress = true;
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        accountDeletionGoogleClient = GoogleSignIn.getClient(this, options);
        accountDeletionGoogleClient.signOut().addOnCompleteListener(task -> {
            try {
                accountDeletionReauthLauncher.launch(
                        accountDeletionGoogleClient.getSignInIntent());
            } catch (ActivityNotFoundException exception) {
                accountDeletionInProgress = false;
                ModernToast.error(
                        this, getString(R.string.account_delete_google_unavailable));
            }
        });
    }

    private void handleAccountDeletionReauthentication(Intent data) {
        if (!accountDeletionInProgress) {
            return;
        }
        try {
            GoogleSignInAccount account = GoogleSignIn
                    .getSignedInAccountFromIntent(data)
                    .getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                throw new IllegalStateException("Google identity token was unavailable");
            }
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                throw new IllegalStateException("Firebase user was unavailable");
            }
            String googleDisplayName = account.getDisplayName();
            String expectedFullName = googleDisplayName != null
                    && !googleDisplayName.trim().isEmpty()
                    ? googleDisplayName.trim()
                    : user.getDisplayName();
            AuthCredential credential = GoogleAuthProvider.getCredential(
                    account.getIdToken(), null);
            user.reauthenticate(credential)
                    .addOnSuccessListener(unused ->
                            showAccountNameConfirmation(expectedFullName))
                    .addOnFailureListener(error -> failAccountDeletion(
                            R.string.account_delete_reauthenticate_failed));
        } catch (Exception exception) {
            accountDeletionInProgress = false;
            ModernToast.info(this, getString(R.string.account_delete_cancelled));
        }
    }

    private void showAccountNameConfirmation(String expectedFullName) {
        String verifiedName = expectedFullName == null ? "" : expectedFullName.trim();
        if (verifiedName.isEmpty()) {
            failAccountDeletion(R.string.account_delete_name_unavailable);
            return;
        }

        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_account_name_confirmation, null, false);
        TextView expectedName = dialogView.findViewById(
                R.id.text_account_delete_expected_name);
        TextInputLayout nameLayout = dialogView.findViewById(
                R.id.layout_account_delete_name);
        TextInputEditText nameInput = dialogView.findViewById(
                R.id.edit_account_delete_name);
        MaterialButton cancel = dialogView.findViewById(
                R.id.btn_account_delete_name_cancel);
        MaterialButton confirm = dialogView.findViewById(
                R.id.btn_account_delete_name_confirm);

        expectedName.setText(getString(
                R.string.account_delete_name_confirmation_expected, verifiedName));
        confirm.setEnabled(false);

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();

        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // No-op.
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                boolean matches = verifiedName.equals(text.toString().trim());
                confirm.setEnabled(matches);
                nameLayout.setError(text.length() > 0 && !matches
                        ? getString(R.string.account_delete_name_mismatch)
                        : null);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // No-op.
            }
        });
        cancel.setOnClickListener(v -> {
            accountDeletionInProgress = false;
            dialog.dismiss();
            ModernToast.info(this, getString(R.string.account_delete_cancelled));
        });
        confirm.setOnClickListener(v -> {
            if (!verifiedName.equals(String.valueOf(nameInput.getText()).trim())) {
                return;
            }
            confirm.setEnabled(false);
            dialog.dismiss();
            deleteReauthenticatedAccount();
        });
        dialog.show();
        nameInput.requestFocus();
    }

    private void deleteReauthenticatedAccount() {
        showAccountDeletionProgress();
        accountDeletionGateway.deleteMyAccount(new AccountDeletionGateway.Callback() {
            @Override
            public void onSuccess() {
                if (accountDeletionProgressDialog != null) {
                    accountDeletionProgressDialog.dismiss();
                }
                if (accountDeletionGoogleClient != null) {
                    accountDeletionGoogleClient.revokeAccess()
                            .addOnCompleteListener(task -> finishDeletedAccountLocally());
                } else {
                    finishDeletedAccountLocally();
                }
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                failAccountDeletion(R.string.account_delete_failed);
            }
        });
    }

    private void showAccountDeletionProgress() {
        View progress = getLayoutInflater().inflate(
                R.layout.dialog_account_deletion_progress, null, false);
        accountDeletionProgressDialog =
                new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                        .setView(progress)
                        .setCancelable(false)
                        .create();
        accountDeletionProgressDialog.show();
    }

    private void failAccountDeletion(int messageResource) {
        if (accountDeletionProgressDialog != null) {
            accountDeletionProgressDialog.dismiss();
            accountDeletionProgressDialog = null;
        }
        accountDeletionInProgress = false;
        ModernToast.error(this, getString(messageResource));
    }

    private void finishDeletedAccountLocally() {
        if (mAuth != null) {
            mAuth.signOut();
        }
        SessionCacheCleaner.clearAll(this);
        ModernToast.success(this, getString(R.string.account_delete_success));
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_REQUIRE_LOGIN, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Show voice settings dialog with language and mute options
     */
    private void showVoiceSettingsDialog() {
        boolean isMuted = LanguagePreferenceManager.isMuted(this);
        String currentLanguage = LanguagePreferenceManager.loadLanguagePreference(this).getLanguage();

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_announcements);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setCancelable(true);

        TextView textCurrentStatus = dialog.findViewById(R.id.text_current_status);
        View btnClose = dialog.findViewById(R.id.btn_close);
        MaterialButton btnBengali = dialog.findViewById(R.id.btn_bengali);
        MaterialButton btnEnglish = dialog.findViewById(R.id.btn_english);
        MaterialButton btnMuteToggle = dialog.findViewById(R.id.btn_mute_toggle);

        String languageText = currentLanguage.equals("bn")
                ? getString(R.string.voice_announcements_language_bengali)
                : getString(R.string.voice_announcements_language_english);
        String muteText = isMuted
                ? getString(R.string.voice_announcements_status_muted)
                : getString(R.string.voice_announcements_status_enabled);
        textCurrentStatus.setText(getString(
                R.string.voice_announcements_status_format, languageText, muteText));

        styleVoiceLanguageButton(btnBengali, currentLanguage.equals("bn"));
        styleVoiceLanguageButton(btnEnglish, !currentLanguage.equals("bn"));

        if (isMuted) {
            btnMuteToggle.setText(R.string.voice_announcements_unmute);
            btnMuteToggle.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.view_mint)));
        } else {
            btnMuteToggle.setText(R.string.voice_announcements_mute);
            btnMuteToggle.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.view_coral)));
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnBengali.setOnClickListener(v -> {
            switchLanguage(new Locale("bn", "IN"));
            dialog.dismiss();
        });

        btnEnglish.setOnClickListener(v -> {
            switchLanguage(Locale.US);
            dialog.dismiss();
        });

        btnMuteToggle.setOnClickListener(v -> {
            toggleMuteVoice();
            dialog.dismiss();
        });

        dialog.show();
        applySettingsDialogWidth(dialog);
    }

    private void styleVoiceLanguageButton(MaterialButton button, boolean selected) {
        if (selected) {
            button.setText(button.getId() == R.id.btn_bengali
                    ? R.string.voice_announcements_language_bengali_selected
                    : R.string.voice_announcements_language_english_selected);
            button.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            button.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.view_violet)));
            button.setStrokeWidth(0);
        } else {
            button.setText(button.getId() == R.id.btn_bengali
                    ? R.string.voice_announcements_language_bengali
                    : R.string.voice_announcements_language_english);
            button.setTextColor(ContextCompat.getColor(this, R.color.view_text_primary));
            button.setBackgroundTintList(ColorStateList.valueOf(0x24111424));
            button.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.view_stroke)));
            button.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.view_button_stroke_width));
        }
    }

    /**
     * Switch TTS language globally
     */
    private void switchLanguage(Locale locale) {
        // Save the preference (also unmutes)
        LanguagePreferenceManager.saveLanguagePreference(this, locale);
    }
    
    /**
     * Toggle mute/unmute voice announcements globally
     */
    private void toggleMuteVoice() {
        boolean currentlyMuted = LanguagePreferenceManager.isMuted(this);
        boolean newMutedState = !currentlyMuted;
        
        // Toggle muted state
        LanguagePreferenceManager.setMuted(this, newMutedState);
    }

    /**
     * Show app information dialog
     */
    private void showAppInfoDialog() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            long versionCode = BuildConfig.VERSION_CODE;
            
            FirebaseUser currentUser = mAuth.getCurrentUser();
            
            String currentDate = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                   .format(new java.util.Date());
            
            // Create custom dialog
            android.app.Dialog dialog = new android.app.Dialog(this);
            dialog.setContentView(R.layout.dialog_app_info);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.setCancelable(true);
            
            // Get views
            android.widget.TextView textVersion = dialog.findViewById(R.id.text_version);
            android.widget.TextView textBuild = dialog.findViewById(R.id.text_build);
            android.widget.TextView textDate = dialog.findViewById(R.id.text_date);
            android.widget.TextView textUpdateStatus = dialog.findViewById(R.id.text_update_status);
            View btnClose = dialog.findViewById(R.id.btn_close);
            MaterialButton btnPrivacyPolicy = dialog.findViewById(R.id.btn_privacy_policy);
            MaterialButton btnGamePointsPolicy =
                    dialog.findViewById(R.id.btn_game_points_policy);
            MaterialButton btnCheckUpdates = dialog.findViewById(R.id.btn_check_updates);
            
            // Set values
            textVersion.setText(versionName);
            textBuild.setText(String.valueOf(versionCode));
            textDate.setText(currentDate);
            textUpdateStatus.setText(BuildConfig.DEBUG
                    ? R.string.app_info_update_status_debug
                    : R.string.app_info_update_status_release);
            
            // Set button listeners
            btnClose.setOnClickListener(v -> dialog.dismiss());

            btnPrivacyPolicy.setOnClickListener(v -> {
                Intent privacyPolicyIntent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.app_info_privacy_policy_url)));
                try {
                    startActivity(privacyPolicyIntent);
                } catch (ActivityNotFoundException e) {
                    com.example.rummypulse.utils.ModernToast.error(
                            this, getString(R.string.app_info_privacy_policy_open_error));
                }
            });

            btnGamePointsPolicy.setOnClickListener(v -> {
                Intent policyIntent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.game_points_policy_url)));
                try {
                    startActivity(policyIntent);
                } catch (ActivityNotFoundException e) {
                    com.example.rummypulse.utils.ModernToast.error(
                            this, getString(R.string.game_points_policy_open_error));
                }
            });
            
            btnCheckUpdates.setOnClickListener(v -> {
                dialog.dismiss();
                if (updateChecker == null) {
                    updateChecker = new ModernUpdateChecker(this);
                }
                if (updateChecker != null) {
                    com.example.rummypulse.utils.ModernToast.info(this, "Checking for updates...");
                    updateChecker.forceCheckForUpdates();
                }
            });
            
            dialog.show();
            applySettingsDialogWidth(dialog);
                
        } catch (PackageManager.NameNotFoundException e) {
            android.util.Log.e("MainActivity", "Error getting package info", e);
            
            // Fallback to simple dialog
            com.example.rummypulse.utils.ModernToast.error(this, "Unable to retrieve app information");
        }
    }

    private void applySettingsDialogWidth(android.app.Dialog dialog) {
        if (dialog.getWindow() == null) {
            return;
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.dialog_create_game_max_width);
        int width = Math.min(Math.round(dm.widthPixels * 0.92f), maxWidth);
        dialog.getWindow().setLayout(
                width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
