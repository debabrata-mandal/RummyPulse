package com.example.rummypulse.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Utility class to manage AppUser operations
 * Provides convenient methods for common AppUser tasks
 */
public class AppUserManager {
    private static final String TAG = "AppUserManager";
    private static AppUserManager instance;
    private final AppUserRepository repository;
    
    private AppUserManager() {
        this.repository = new AppUserRepository();
    }
    
    public static synchronized AppUserManager getInstance() {
        if (instance == null) {
            instance = new AppUserManager();
        }
        return instance;
    }
    
    /**
     * Get current user's AppUser data
     */
    public void getCurrentAppUser(AppUserRepository.AppUserCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            repository.getUserById(currentUser.getUid(), callback);
        } else {
            if (callback != null) {
                callback.onFailure(new Exception("No authenticated user"));
            }
        }
    }
    
    /**
     * Check if current user is admin
     */
    public void isCurrentUserAdmin(AdminCheckCallback callback) {
        getCurrentAppUser(new AppUserRepository.AppUserCallback() {
            @Override
            public void onSuccess(AppUser appUser) {
                boolean isAdmin = appUser.getRole() == UserRole.ADMIN_USER;
                if (callback != null) {
                    callback.onResult(isAdmin);
                }
            }
            
            @Override
            public void onFailure(Exception exception) {
                Log.e(TAG, "Failed to check admin status", exception);
                if (callback != null) {
                    callback.onResult(false); // Default to false on error
                }
            }
        });
    }
    
    /**
     * Update current user's role (requires admin privileges in production)
     */
    public void updateCurrentUserRole(UserRole newRole, AppUserRepository.AppUserCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            repository.updateUserRole(currentUser.getUid(), newRole, callback);
        } else {
            if (callback != null) {
                callback.onFailure(new Exception("No authenticated user"));
            }
        }
    }
    
    /**
     * Update any user's role by userId (admin function)
     */
    public void updateUserRole(String userId, UserRole newRole, AppUserRepository.AppUserCallback callback) {
        repository.updateUserRole(userId, newRole, callback);
    }
    
    /**
     * Get AppUser repository instance
     */
    public AppUserRepository getRepository() {
        return repository;
    }
    
    /**
     * Callback interface for admin check
     */
    public interface AdminCheckCallback {
        void onResult(boolean isAdmin);
    }
}
