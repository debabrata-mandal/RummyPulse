package com.example.rummypulse.ui.usermanagement;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.rummypulse.R;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.UserRole;
import com.example.rummypulse.databinding.FragmentUserManagementBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for managing users - only accessible by admin users
 * Allows viewing all users and promoting/demoting user roles
 */
public class UserManagementFragment extends Fragment {

    private static final String TAG = "UserManagementFragment";
    private FragmentUserManagementBinding binding;
    private UserManagementViewModel userManagementViewModel;
    private UserManagementAdapter adapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        userManagementViewModel = new ViewModelProvider(this).get(UserManagementViewModel.class);

        binding = FragmentUserManagementBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupRecyclerView();
        setupSwipeRefresh();
        observeViewModel();
        
        // Load users initially
        userManagementViewModel.loadAllUsers();

        return root;
    }

    private void setupRecyclerView() {
        adapter = new UserManagementAdapter(new ArrayList<>(), this::onRoleChangeClicked);
        binding.recyclerViewUsers.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewUsers.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            Log.d(TAG, "Refreshing user list");
            userManagementViewModel.loadAllUsers();
        });
    }

    private void observeViewModel() {
        userManagementViewModel.getUsers().observe(getViewLifecycleOwner(), users -> {
            if (users != null) {
                Log.d(TAG, "Received " + users.size() + " users");
                adapter.updateUsers(users);
                showUsersView();
            }
        });

        userManagementViewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.swipeRefreshLayout.setRefreshing(isLoading);
            if (isLoading) {
                binding.progressBar.setVisibility(View.VISIBLE);
            } else {
                binding.progressBar.setVisibility(View.GONE);
            }
        });

        userManagementViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "Error: " + error);
                showError(error);
                Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });

        userManagementViewModel.getRoleUpdateSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                Toast.makeText(getContext(), "User role updated successfully", Toast.LENGTH_SHORT).show();
                // Refresh the list to show updated roles
                userManagementViewModel.loadAllUsers();
            }
        });
    }

    private void onRoleChangeClicked(AppUser user) {
        // Show confirmation dialog for role change
        String currentRole = user.getRole().getDisplayName();
        String newRole = user.getRole() == UserRole.ADMIN_USER ? 
            UserRole.REGULAR_USER.getDisplayName() : 
            UserRole.ADMIN_USER.getDisplayName();

        new AlertDialog.Builder(getContext())
            .setTitle("Change User Role")
            .setMessage("Change " + user.getDisplayName() + "'s role from " + 
                       currentRole + " to " + newRole + "?")
            .setPositiveButton("Yes", (dialog, which) -> {
                UserRole targetRole = user.getRole() == UserRole.ADMIN_USER ? 
                    UserRole.REGULAR_USER : UserRole.ADMIN_USER;
                
                Log.d(TAG, "Changing role for user: " + user.getDisplayName() + 
                          " to " + targetRole.getDisplayName());
                
                userManagementViewModel.updateUserRole(user.getUserId(), targetRole);
            })
            .setNegativeButton("No", null)
            .show();
    }

    private void showUsersView() {
        binding.recyclerViewUsers.setVisibility(View.VISIBLE);
        binding.errorText.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
    }

    private void showError(String error) {
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText("Error: " + error);
        binding.recyclerViewUsers.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
