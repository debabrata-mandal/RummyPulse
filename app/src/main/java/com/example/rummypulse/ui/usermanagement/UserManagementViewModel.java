package com.example.rummypulse.ui.usermanagement;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.UserRole;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ViewModel for User Management functionality
 * Handles loading all users and updating user roles
 */
public class UserManagementViewModel extends ViewModel {

    private static final String TAG = "UserManagementViewModel";
    
    private final AppUserRepository appUserRepository;
    private final MutableLiveData<List<AppUser>> users;
    private final MutableLiveData<Boolean> loading;
    private final MutableLiveData<String> error;
    private final MutableLiveData<Boolean> roleUpdateSuccess;

    public UserManagementViewModel() {
        appUserRepository = new AppUserRepository();
        users = new MutableLiveData<>();
        loading = new MutableLiveData<>();
        error = new MutableLiveData<>();
        roleUpdateSuccess = new MutableLiveData<>();
    }

    public LiveData<List<AppUser>> getUsers() {
        return users;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getRoleUpdateSuccess() {
        return roleUpdateSuccess;
    }

    /**
     * Load all users from the database
     */
    public void loadAllUsers() {
        loading.setValue(true);
        error.setValue(null);
        
        Log.d(TAG, "Loading all users...");
        
        appUserRepository.getAllUsers(new AppUserRepository.AllUsersCallback() {
            @Override
            public void onSuccess(List<AppUser> userList) {
                Log.d(TAG, "Successfully loaded " + userList.size() + " users");
                
                // Sort users: Admins first, then regular users, both alphabetically by name
                Collections.sort(userList, new Comparator<AppUser>() {
                    @Override
                    public int compare(AppUser user1, AppUser user2) {
                        // First, compare by role (Admin comes before Regular)
                        boolean isAdmin1 = user1.getRole() == UserRole.ADMIN_USER;
                        boolean isAdmin2 = user2.getRole() == UserRole.ADMIN_USER;
                        
                        if (isAdmin1 && !isAdmin2) {
                            return -1; // user1 is admin, user2 is not - user1 comes first
                        } else if (!isAdmin1 && isAdmin2) {
                            return 1; // user2 is admin, user1 is not - user2 comes first
                        } else {
                            // Both have same role, sort alphabetically by name
                            String name1 = user1.getDisplayName() != null ? user1.getDisplayName() : "";
                            String name2 = user2.getDisplayName() != null ? user2.getDisplayName() : "";
                            return name1.compareToIgnoreCase(name2);
                        }
                    }
                });
                
                Log.d(TAG, "Users sorted: Admins first, then regular users (both alphabetically)");
                users.setValue(userList);
                loading.setValue(false);
            }

            @Override
            public void onFailure(Exception exception) {
                Log.e(TAG, "Failed to load users", exception);
                error.setValue("Failed to load users: " + exception.getMessage());
                loading.setValue(false);
            }
        });
    }

    /**
     * Update a user's role
     */
    public void updateUserRole(String userId, UserRole newRole) {
        loading.setValue(true);
        error.setValue(null);
        roleUpdateSuccess.setValue(false);
        
        Log.d(TAG, "Updating user role for userId: " + userId + " to " + newRole.getDisplayName());
        
        appUserRepository.updateUserRole(userId, newRole, new AppUserRepository.AppUserCallback() {
            @Override
            public void onSuccess(AppUser updatedUser) {
                Log.d(TAG, "Successfully updated user role: " + updatedUser.getDisplayName() + 
                          " to " + updatedUser.getRole().getDisplayName());
                loading.setValue(false);
                roleUpdateSuccess.setValue(true);
            }

            @Override
            public void onFailure(Exception exception) {
                Log.e(TAG, "Failed to update user role", exception);
                error.setValue("Failed to update user role: " + exception.getMessage());
                loading.setValue(false);
                roleUpdateSuccess.setValue(false);
            }
        });
    }
}
