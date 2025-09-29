package com.example.rummypulse;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.ImageView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserManager;
import com.example.rummypulse.utils.AuthStateManager;
import com.example.rummypulse.utils.ModernUpdateChecker;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ModernUpdateChecker updateChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Create auth state listener
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    // User is signed out, redirect to login
                    android.util.Log.d("MainActivity", "User signed out, redirecting to login");
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
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
                android.util.Log.w("MainActivity", "This might be due to force stop - expected user: " + 
                    authStateManager.getBackedUpUserEmail());
                
                // Show a toast to inform user about session restoration
                android.widget.Toast.makeText(this, 
                    "Session was interrupted. Please sign in again.", 
                    android.widget.Toast.LENGTH_LONG).show();
            } else {
                android.util.Log.d("MainActivity", "No current user found, redirecting to login");
            }
            
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        } else {
            // User is authenticated, ensure backup state is updated
            android.util.Log.d("MainActivity", "User authenticated: " + currentUser.getEmail());
            authStateManager.saveAuthState(currentUser);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        // Floating Action Button removed
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_dashboard, R.id.nav_home, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
        
        // Handle navigation item clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_sign_out) {
                signOut();
                return true;
            } else if (item.getItemId() == R.id.nav_app_info) {
                showAppInfoDialog();
                drawer.closeDrawers();
                return true;
            } else if (item.getItemId() == R.id.nav_home) {
                // Check admin status before allowing access to Review screen
                AppUserManager.getInstance().isCurrentUserAdmin(new AppUserManager.AdminCheckCallback() {
                    @Override
                    public void onResult(boolean isAdmin) {
                        if (isAdmin) {
                            // Admin user - allow access
                            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                            if (handled) {
                                drawer.closeDrawers();
                            }
                        } else {
                            // Non-admin user - show access denied message
                            drawer.closeDrawers();
                            android.widget.Toast.makeText(MainActivity.this, 
                                "🔒 Access Denied: Admin privileges required for Review screen", 
                                android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
                return true;
            } else if (item.getItemId() == R.id.nav_user_management) {
                // Check admin status before allowing access to User Management screen
                AppUserManager.getInstance().isCurrentUserAdmin(new AppUserManager.AdminCheckCallback() {
                    @Override
                    public void onResult(boolean isAdmin) {
                        if (isAdmin) {
                            // Admin user - allow access
                            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                            if (handled) {
                                drawer.closeDrawers();
                            }
                        } else {
                            // Non-admin user - show access denied message
                            drawer.closeDrawers();
                            android.widget.Toast.makeText(MainActivity.this, 
                                "🔒 Access Denied: Admin privileges required for Users", 
                                android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
                return true;
            } else {
                // Handle other navigation items with default behavior
                boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                if (handled) {
                    drawer.closeDrawers();
                }
                return handled;
            }
        });
        
        // Update navigation header with user info
        updateNavigationHeader(navigationView, currentUser);
        
        // Update menu icons based on admin status
        updateMenuIcons(navigationView);
        
        // Initialize and check for app updates
        initializeUpdateChecker();
    }


    @Override
    protected void onStart() {
        super.onStart();
        // Add auth listener when activity starts
        if (mAuth != null && mAuthListener != null) {
            mAuth.addAuthStateListener(mAuthListener);
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
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void updateNavigationHeader(NavigationView navigationView, FirebaseUser user) {
        android.view.View headerView = navigationView.getHeaderView(0);
        TextView nameTextView = headerView.findViewById(R.id.nav_header_title);
        TextView emailTextView = headerView.findViewById(R.id.nav_header_subtitle);
        
        if (user != null) {
            String displayName = user.getDisplayName();
            String email = user.getEmail();
            
            // Set email first
            emailTextView.setText(email != null ? email : "");
            
            // Check if user is admin and update name accordingly
            AppUserManager.getInstance().isCurrentUserAdmin(new AppUserManager.AdminCheckCallback() {
                @Override
                public void onResult(boolean isAdmin) {
                    String finalDisplayName = displayName != null ? displayName : "User";
                    if (isAdmin) {
                        finalDisplayName += " (Admin)";
                    }
                    nameTextView.setText(finalDisplayName);
                }
            });
        }
    }
    
    private void updateMenuIcons(NavigationView navigationView) {
        AppUserManager.getInstance().isCurrentUserAdmin(new AppUserManager.AdminCheckCallback() {
            @Override
            public void onResult(boolean isAdmin) {
                android.view.Menu menu = navigationView.getMenu();
                android.view.MenuItem reviewMenuItem = menu.findItem(R.id.nav_home);
                android.view.MenuItem userManagementMenuItem = menu.findItem(R.id.nav_user_management);
                
                if (reviewMenuItem != null) {
                    if (isAdmin) {
                        // Admin user - show normal games dashboard icon
                        reviewMenuItem.setIcon(R.drawable.ic_games_dashboard);
                        reviewMenuItem.setTitle("Review");
                    } else {
                        // Non-admin user - show lock icon
                        reviewMenuItem.setIcon(R.drawable.ic_lock);
                        reviewMenuItem.setTitle("Review 🔒");
                    }
                }
                
                if (userManagementMenuItem != null) {
                    if (isAdmin) {
                        // Admin user - show normal users icon
                        userManagementMenuItem.setIcon(R.drawable.ic_people);
                        userManagementMenuItem.setTitle("Users");
                    } else {
                        // Non-admin user - show lock icon
                        userManagementMenuItem.setIcon(R.drawable.ic_lock);
                        userManagementMenuItem.setTitle("Users 🔒");
                    }
                }
            }
        });
    }

    /**
     * Initialize update checker and check for new versions
     */
    private void initializeUpdateChecker() {
        updateChecker = new ModernUpdateChecker(this);
        
        // Check for updates with a slight delay to not interfere with app startup
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.util.Log.d("MainActivity", "Checking for app updates...");
            updateChecker.checkForUpdates();
        }, 2000); // 2 second delay
    }

    private void signOut() {
        // Clear authentication backup state
        AuthStateManager.getInstance(this).clearAuthState();
        
        mAuth.signOut();
        
        android.util.Log.d("MainActivity", "User signed out manually");
        
        // Redirect to login activity
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Show app information dialog
     */
    private void showAppInfoDialog() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            long versionCode = packageInfo.getLongVersionCode();
            
            FirebaseUser currentUser = mAuth.getCurrentUser();
            String userEmail = currentUser != null ? currentUser.getEmail() : "Not signed in";
            
            String appInfo = "📱 RummyPulse\n\n" +
                           "🏷️ Version: " + versionName + "\n" +
                           "🔢 Build: " + versionCode + "\n" +
                           "👤 User: " + userEmail + "\n" +
                           "🔧 Auto-Update: Enabled\n\n" +
                           "🚀 Built with Firebase & Android\n" +
                           "💡 Developed by Debabrata Mandal\n\n" +
                           "📅 " + new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                   .format(new java.util.Date());
            
            // Create dialog with dark theme
            AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("ℹ️ App Information")
                .setMessage(appInfo)
                .setPositiveButton("Check for Updates", (dialogInterface, which) -> {
                    if (updateChecker != null) {
                        android.widget.Toast.makeText(this, "Checking for updates...", 
                                                    android.widget.Toast.LENGTH_SHORT).show();
                        updateChecker.checkForUpdates();
                    }
                })
                .setNegativeButton("OK", null)
                .create();
                
            // Apply dark theme styling
            dialog.show();
            
            // Style the buttons to match app theme
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.accent_blue));
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getColor(R.color.text_secondary));
            }
                
        } catch (PackageManager.NameNotFoundException e) {
            android.util.Log.e("MainActivity", "Error getting package info", e);
            
            AlertDialog errorDialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("ℹ️ App Information")
                .setMessage("📱 RummyPulse\n\n" +
                           "🏷️ Version: Unknown\n" +
                           "👤 User: " + (mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : "Not signed in") + "\n" +
                           "🔧 Auto-Update: Enabled")
                .setPositiveButton("OK", null)
                .create();
                
            errorDialog.show();
            
            // Style the button
            if (errorDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                errorDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.accent_blue));
            }
        }
    }
}