package com.example.rummypulse.ui.usermanagement;

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
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.data.UserRole;
import com.example.rummypulse.databinding.FragmentUserManagementBinding;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for managing users - only accessible by admin users
 * Allows viewing all users, promoting/demoting user roles, and deleting users (admin only)
 */
public class UserManagementFragment extends Fragment {

    private static final String TAG = "UserManagementFragment";
    private FragmentUserManagementBinding binding;
    private UserManagementViewModel userManagementViewModel;
    private UserManagementAdapter adapter;
    private LinearLayoutManager layoutManager;
    private Boolean pendingHiddenToast;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        userManagementViewModel = new ViewModelProvider(this).get(UserManagementViewModel.class);

        binding = FragmentUserManagementBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupRecyclerView();
        setupSearch();
        setupSwipeRefresh();
        observeViewModel();
        
        // Load users initially
        userManagementViewModel.loadAllUsers();

        return root;
    }

    private void setupRecyclerView() {
        adapter = new UserManagementAdapter(new ArrayList<>(), this::onUserClicked);
        layoutManager = new LinearLayoutManager(getContext());
        binding.recyclerViewUsers.setLayoutManager(layoutManager);
        binding.recyclerViewUsers.setAdapter(adapter);
        AppUserRoleSession.getInstance().getRole().observe(getViewLifecycleOwner(), role ->
                adapter.setAdminActionsEnabled(role == AppUserRoleSession.Role.ADMIN));
        binding.recyclerViewUsers.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0
                        && layoutManager != null
                        && layoutManager.findLastVisibleItemPosition()
                        >= Math.max(0, adapter.getItemCount() - 5)) {
                    userManagementViewModel.loadNextPage();
                }
            }
        });
    }

    private void setupSearch() {
        binding.inputUserSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable editable) {
                userManagementViewModel.setSearchQuery(
                        editable == null ? "" : editable.toString());
            }
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            Log.d(TAG, "Refreshing user list");
            userManagementViewModel.loadAllUsers();
        });
    }

    private void observeViewModel() {
        userManagementViewModel.getDisplayedUsers().observe(getViewLifecycleOwner(), users -> {
            if (users != null) {
                Log.d(TAG, "Displaying " + users.size() + " users");
                adapter.updateUsers(users);
                if (users.isEmpty()) {
                    showEmptyState(userManagementViewModel.isSearchActive());
                } else {
                    showUsersView();
                }
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

        userManagementViewModel.getLoadingMore().observe(getViewLifecycleOwner(), isLoading -> {
            binding.paginationProgressBar.setVisibility(
                    Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
        });

        userManagementViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "User management operation failed");
                if (adapter.getItemCount() == 0) {
                    showError(error);
                }
                com.example.rummypulse.utils.ModernToast.error(getContext(), "Error: " + error);
            }
        });

        userManagementViewModel.getRoleUpdateSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                com.example.rummypulse.utils.ModernToast.success(getContext(), "User role updated successfully");
            }
        });

        userManagementViewModel.getDeleteSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                com.example.rummypulse.utils.ModernToast.success(getContext(), "User deleted successfully");
            }
        });

        userManagementViewModel.getHiddenUpdateSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success && pendingHiddenToast != null) {
                com.example.rummypulse.utils.ModernToast.success(getContext(), getString(
                        pendingHiddenToast
                                ? R.string.user_management_hidden_success
                                : R.string.user_management_unhidden_success));
                pendingHiddenToast = null;
            }
        });
    }

    private void onUserClicked(AppUser user) {
        if (AppUserRoleSession.getInstance().peekRole() != AppUserRoleSession.Role.ADMIN) {
            return;
        }
        showUserActionsDialog(user);
    }

    private void showUserActionsDialog(AppUser user) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_user_actions, null, false);
        android.widget.ImageView profile =
                dialogView.findViewById(R.id.image_user_actions_profile);
        android.widget.TextView name =
                dialogView.findViewById(R.id.text_user_actions_name);
        android.widget.TextView email =
                dialogView.findViewById(R.id.text_user_actions_email);
        android.widget.TextView meta =
                dialogView.findViewById(R.id.text_user_actions_meta);
        android.widget.TextView selfNote =
                dialogView.findViewById(R.id.text_user_actions_self_note);
        MaterialButton changeRole =
                dialogView.findViewById(R.id.btn_user_action_change_role);
        MaterialButton hideUser =
                dialogView.findViewById(R.id.btn_user_action_hide);
        MaterialButton deleteUser =
                dialogView.findViewById(R.id.btn_user_action_delete);
        MaterialButton close =
                dialogView.findViewById(R.id.btn_user_action_close);

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .apply(new RequestOptions()
                            .circleCrop()
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(profile);
        } else {
            profile.setImageResource(R.drawable.ic_person);
        }

        name.setText(user.getDisplayName() != null ? user.getDisplayName() : "No Name");
        email.setText(user.getEmail() != null ? user.getEmail() : "No Email");
        String provider = user.getProvider() != null ? user.getProvider() : "Unknown";
        String lastLogin;
        if (user.getLastLoginAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            lastLogin = getString(R.string.user_management_last_login, sdf.format(user.getLastLoginAt()));
        } else {
            lastLogin = getString(R.string.user_management_last_login_never);
        }
        String metaText = user.getRole().getDisplayName()
                + " · "
                + getString(R.string.user_management_provider, provider)
                + " · "
                + lastLogin;
        if (user.isHidden()) {
            metaText += " · " + getString(R.string.user_management_hidden_badge);
        }
        meta.setText(metaText);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean isCurrentUser = currentUser != null
                && currentUser.getUid().equals(user.getUserId());

        if (isCurrentUser) {
            selfNote.setVisibility(View.VISIBLE);
            changeRole.setText(getString(R.string.user_management_current_user));
            changeRole.setEnabled(false);
            changeRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.neutral_gray)));
            changeRole.setTextColor(requireContext().getColor(R.color.text_secondary));
            hideUser.setVisibility(View.GONE);
            deleteUser.setVisibility(View.GONE);
        } else {
            selfNote.setVisibility(View.GONE);
            boolean admin = user.getRole() == UserRole.ADMIN_USER;
            changeRole.setText(admin
                    ? getString(R.string.user_management_action_demote)
                    : getString(R.string.user_management_action_promote));
            changeRole.setEnabled(true);
            if (admin) {
                changeRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.demote_button_color)));
            } else {
                changeRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.promote_button_color)));
            }
            changeRole.setTextColor(requireContext().getColor(R.color.text_white));
            hideUser.setVisibility(View.VISIBLE);
            hideUser.setText(user.isHidden()
                    ? getString(R.string.user_management_unhide_user)
                    : getString(R.string.user_management_hide_user));
            deleteUser.setVisibility(View.VISIBLE);
        }

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(
                        requireContext(), R.style.DarkDialogTheme)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();

        close.setOnClickListener(v -> dialog.dismiss());
        changeRole.setOnClickListener(v -> {
            dialog.dismiss();
            onRoleChangeClicked(user);
        });
        hideUser.setOnClickListener(v -> {
            dialog.dismiss();
            onHideClicked(user);
        });
        deleteUser.setOnClickListener(v -> {
            dialog.dismiss();
            onDeleteClicked(user);
        });

        dialog.show();
        styleDialogWindow(dialog);
    }

    private void styleDialogWindow(androidx.appcompat.app.AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int maxWidth = Math.round(420 * dm.density);
            int width = Math.min(Math.round(dm.widthPixels * 0.92f), maxWidth);
            dialog.getWindow().setLayout(
                    width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void onRoleChangeClicked(AppUser user) {
        // Safety check: Prevent current user from changing their own role
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(user.getUserId())) {
            com.example.rummypulse.utils.ModernToast.warning(getContext(), 
                "⚠️ You cannot change your own role for security reasons");
            return;
        }
        
        // Show confirmation dialog for role change
        String currentRole = user.getRole().getDisplayName();
        String newRole = user.getRole() == UserRole.ADMIN_USER ? 
            UserRole.REGULAR_USER.getDisplayName() : 
            UserRole.ADMIN_USER.getDisplayName();

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_action_confirmation, null, false);
        android.widget.ImageView icon =
                dialogView.findViewById(R.id.image_action_dialog_icon);
        android.widget.TextView title =
                dialogView.findViewById(R.id.text_action_dialog_title);
        android.widget.TextView subtitle =
                dialogView.findViewById(R.id.text_action_dialog_subtitle);
        android.widget.TextView message =
                dialogView.findViewById(R.id.text_action_dialog_message);
        com.google.android.material.button.MaterialButton cancel =
                dialogView.findViewById(R.id.btn_action_dialog_cancel);
        com.google.android.material.button.MaterialButton confirm =
                dialogView.findViewById(R.id.btn_action_dialog_confirm);
        icon.setImageResource(R.drawable.ic_moderator);
        title.setText(getString(R.string.user_management_change_role_title));
        subtitle.setText(user.getDisplayName());
        message.setText(getString(R.string.user_management_change_role_message, currentRole, newRole));
        cancel.setText(getString(R.string.user_management_keep_current_role));
        confirm.setText(getString(R.string.user_management_change_role_confirm));

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(
                        requireContext(), R.style.DarkDialogTheme)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            UserRole targetRole = user.getRole() == UserRole.ADMIN_USER
                    ? UserRole.REGULAR_USER : UserRole.ADMIN_USER;
            Log.d(TAG, "Changing user role to " + targetRole.getDisplayName());
            userManagementViewModel.updateUserRole(user.getUserId(), targetRole);
            dialog.dismiss();
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void onDeleteClicked(AppUser user) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(user.getUserId())) {
            com.example.rummypulse.utils.ModernToast.warning(getContext(),
                    "You cannot delete your own account");
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_action_confirmation, null, false);
        android.widget.ImageView icon =
                dialogView.findViewById(R.id.image_action_dialog_icon);
        android.widget.TextView title =
                dialogView.findViewById(R.id.text_action_dialog_title);
        android.widget.TextView subtitle =
                dialogView.findViewById(R.id.text_action_dialog_subtitle);
        android.widget.TextView message =
                dialogView.findViewById(R.id.text_action_dialog_message);
        com.google.android.material.card.MaterialCardView messageCard =
                dialogView.findViewById(R.id.card_action_dialog_message);
        com.google.android.material.button.MaterialButton cancel =
                dialogView.findViewById(R.id.btn_action_dialog_cancel);
        com.google.android.material.button.MaterialButton confirm =
                dialogView.findViewById(R.id.btn_action_dialog_confirm);

        int red = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.error_red);
        icon.setImageResource(R.drawable.ic_delete);
        icon.setBackgroundResource(R.drawable.view_access_icon_rejected_background);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(red));
        title.setText(getString(R.string.user_management_delete_user_title));
        subtitle.setText(user.getDisplayName());
        message.setText(getString(R.string.user_management_delete_user_message));
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                message, android.content.res.ColorStateList.valueOf(red));
        messageCard.setStrokeColor(red);
        cancel.setText(getString(R.string.user_management_keep_user));
        confirm.setText(getString(R.string.user_management_delete_user_confirm));
        confirm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(red));

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(
                        requireContext(), R.style.DarkDialogTheme)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            Log.d(TAG, "Deleting user account record");
            userManagementViewModel.deleteUser(user.getUserId());
            dialog.dismiss();
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void onHideClicked(AppUser user) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(user.getUserId())) {
            com.example.rummypulse.utils.ModernToast.warning(getContext(),
                    "You cannot hide your own account");
            return;
        }

        boolean hidden = user.isHidden();
        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_action_confirmation, null, false);
        android.widget.ImageView icon =
                dialogView.findViewById(R.id.image_action_dialog_icon);
        android.widget.TextView title =
                dialogView.findViewById(R.id.text_action_dialog_title);
        android.widget.TextView subtitle =
                dialogView.findViewById(R.id.text_action_dialog_subtitle);
        android.widget.TextView message =
                dialogView.findViewById(R.id.text_action_dialog_message);
        com.google.android.material.button.MaterialButton cancel =
                dialogView.findViewById(R.id.btn_action_dialog_cancel);
        com.google.android.material.button.MaterialButton confirm =
                dialogView.findViewById(R.id.btn_action_dialog_confirm);

        int accent = androidx.core.content.ContextCompat.getColor(
                requireContext(), R.color.warning_orange);
        icon.setImageResource(R.drawable.ic_visibility);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        title.setText(hidden
                ? getString(R.string.user_management_unhide_user_title)
                : getString(R.string.user_management_hide_user_title));
        subtitle.setText(user.getDisplayName());
        message.setText(hidden
                ? getString(R.string.user_management_unhide_user_message)
                : getString(R.string.user_management_hide_user_message));
        cancel.setText(getString(R.string.user_management_keep_user));
        confirm.setText(hidden
                ? getString(R.string.user_management_unhide_user_confirm)
                : getString(R.string.user_management_hide_user_confirm));
        confirm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(
                        requireContext(), R.style.DarkDialogTheme)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            pendingHiddenToast = !hidden;
            Log.d(TAG, pendingHiddenToast ? "Hiding user" : "Unhiding user");
            userManagementViewModel.updateUserHidden(user.getUserId(), pendingHiddenToast);
            dialog.dismiss();
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showUsersView() {
        binding.recyclerViewUsers.setVisibility(View.VISIBLE);
        binding.errorText.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
    }

    private void showEmptyState(boolean searching) {
        binding.recyclerViewUsers.setVisibility(View.GONE);
        binding.errorText.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyState.setText(searching
                ? getString(R.string.user_management_no_search_results)
                : getString(R.string.user_management_no_users));
        binding.progressBar.setVisibility(View.GONE);
    }

    private void showError(String error) {
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText(getString(R.string.error_with_message, error));
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
