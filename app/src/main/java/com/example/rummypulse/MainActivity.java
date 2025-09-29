package com.example.rummypulse;

import android.content.Intent;
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
            }
        });
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
}