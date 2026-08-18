package com.example.rummypulse.ui.usermanagement;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.rummypulse.R;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserManager;
import com.example.rummypulse.data.UserRole;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying users in the User Management screen
 */
public class UserManagementAdapter extends RecyclerView.Adapter<UserManagementAdapter.UserViewHolder> {

    private List<AppUser> users;
    private OnRoleChangeClickListener roleChangeClickListener;
    private OnDeleteClickListener deleteClickListener;

    public interface OnRoleChangeClickListener {
        void onRoleChangeClicked(AppUser user);
    }

    public interface OnDeleteClickListener {
        void onDeleteClicked(AppUser user);
    }

    public UserManagementAdapter(
            List<AppUser> users,
            OnRoleChangeClickListener roleChangeListener,
            OnDeleteClickListener deleteListener) {
        this.users = users;
        this.roleChangeClickListener = roleChangeListener;
        this.deleteClickListener = deleteListener;
    }

    public void updateUsers(List<AppUser> newUsers) {
        this.users = newUsers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_management, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        AppUser user = users.get(position);
        holder.bind(user, roleChangeClickListener, deleteClickListener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final ImageView profileImageView;
        private final TextView nameTextView;
        private final TextView emailTextView;
        private final TextView roleTextView;
        private final TextView providerTextView;
        private final TextView lastLoginTextView;
        private final MaterialButton roleChangeButton;
        private final MaterialButton deleteButton;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImageView = itemView.findViewById(R.id.imageViewUserProfile);
            nameTextView = itemView.findViewById(R.id.textViewUserName);
            emailTextView = itemView.findViewById(R.id.textViewUserEmail);
            roleTextView = itemView.findViewById(R.id.textViewUserRole);
            providerTextView = itemView.findViewById(R.id.textViewUserProvider);
            lastLoginTextView = itemView.findViewById(R.id.textViewLastLogin);
            roleChangeButton = itemView.findViewById(R.id.buttonChangeRole);
            deleteButton = itemView.findViewById(R.id.buttonDeleteUser);
        }

        public void bind(
                AppUser user,
                OnRoleChangeClickListener roleListener,
                OnDeleteClickListener deleteListener) {
            // Load profile image with Glide
            if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(user.getPhotoUrl())
                    .apply(new RequestOptions()
                        .circleCrop()
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(profileImageView);
            } else {
                profileImageView.setImageResource(R.drawable.ic_person);
            }

            nameTextView.setText(user.getDisplayName() != null ? user.getDisplayName() : "No Name");
            emailTextView.setText(user.getEmail() != null ? user.getEmail() : "No Email");

            String roleText = user.getRole().getDisplayName();
            roleTextView.setText(roleText);

            if (user.getRole() == UserRole.ADMIN_USER) {
                roleTextView.setTextColor(itemView.getContext().getColor(R.color.admin_role_color));
                roleTextView.setText("🔑 " + roleText);
            } else {
                roleTextView.setTextColor(itemView.getContext().getColor(R.color.regular_role_color));
                roleTextView.setText("👤 " + roleText);
            }

            providerTextView.setText("Provider: "
                    + (user.getProvider() != null ? user.getProvider() : "Unknown"));

            if (user.getLastLoginAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                lastLoginTextView.setText("Last login: " + sdf.format(user.getLastLoginAt()));
            } else {
                lastLoginTextView.setText("Last login: Never");
            }

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            boolean isCurrentUser = currentUser != null
                    && currentUser.getUid().equals(user.getUserId());

            AppUserManager.getInstance().isCurrentUserAdmin(new AppUserManager.AdminCheckCallback() {
                @Override
                public void onResult(boolean isAdmin) {
                    if (!isAdmin) {
                        roleChangeButton.setVisibility(View.GONE);
                        deleteButton.setVisibility(View.GONE);
                        return;
                    }

                    roleChangeButton.setVisibility(View.VISIBLE);
                    configureRoleChangeButton(user, isCurrentUser, roleListener);
                    configureDeleteButton(isCurrentUser, user, deleteListener);
                }
            });
        }

        private void configureRoleChangeButton(
                AppUser user,
                boolean isCurrentUser,
                OnRoleChangeClickListener listener) {
            if (isCurrentUser) {
                roleChangeButton.setText("Current user");
                roleChangeButton.setEnabled(false);
                roleChangeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    itemView.getContext().getColor(R.color.neutral_gray)));
                roleChangeButton.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
                roleChangeButton.setOnClickListener(null);
                return;
            }

            String buttonText = user.getRole() == UserRole.ADMIN_USER ? "Demote" : "Promote";
            roleChangeButton.setText(buttonText);
            roleChangeButton.setEnabled(true);

            if (user.getRole() == UserRole.ADMIN_USER) {
                roleChangeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    itemView.getContext().getColor(R.color.demote_button_color)));
            } else {
                roleChangeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    itemView.getContext().getColor(R.color.promote_button_color)));
            }

            roleChangeButton.setTextColor(itemView.getContext().getColor(R.color.text_white));
            roleChangeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRoleChangeClicked(user);
                }
            });
        }

        private void configureDeleteButton(
                boolean isCurrentUser,
                AppUser user,
                OnDeleteClickListener listener) {
            if (isCurrentUser) {
                deleteButton.setVisibility(View.GONE);
                deleteButton.setOnClickListener(null);
                return;
            }

            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setEnabled(true);
            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClicked(user);
                }
            });
        }
    }
}
