package com.example.rummypulse.ui.usermanagement;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.UserRole;

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
