package com.example.rummypulse.ui.usermanagement;

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.utils.ProfileAvatarLoader;
import com.example.rummypulse.R;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.UserRole;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying users in the User Management screen.
 *
 * <p>Admin actions open from a card tap rather than inline buttons on every row.
 */
public class UserManagementAdapter extends RecyclerView.Adapter<UserManagementAdapter.UserViewHolder> {

    private List<AppUser> users;
    private final OnUserClickListener userClickListener;
    private boolean adminActionsEnabled;

    public interface OnUserClickListener {
        void onUserClicked(AppUser user);
    }

    public UserManagementAdapter(List<AppUser> users, OnUserClickListener userClickListener) {
        this.users = users;
        this.userClickListener = userClickListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setAdminActionsEnabled(boolean enabled) {
        if (adminActionsEnabled != enabled) {
            adminActionsEnabled = enabled;
            notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
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
        holder.bind(user, adminActionsEnabled, userClickListener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final ImageView profileImageView;
        private final TextView nameTextView;
        private final TextView profileNameTextView;
        private final TextView emailTextView;
        private final TextView roleTextView;
        private final TextView providerTextView;
        private final TextView lastLoginTextView;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImageView = itemView.findViewById(R.id.imageViewUserProfile);
            nameTextView = itemView.findViewById(R.id.textViewUserName);
            profileNameTextView = itemView.findViewById(R.id.textViewUserProfileName);
            emailTextView = itemView.findViewById(R.id.textViewUserEmail);
            roleTextView = itemView.findViewById(R.id.textViewUserRole);
            providerTextView = itemView.findViewById(R.id.textViewUserProvider);
            lastLoginTextView = itemView.findViewById(R.id.textViewLastLogin);
        }

        public void bind(
                AppUser user,
                boolean adminActionsEnabled,
                OnUserClickListener listener) {
            if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                ProfileAvatarLoader.loadCircle(
                        profileImageView,
                        user.getPhotoUrl(),
                        user.getProfileVersion());
            } else {
                profileImageView.setImageResource(R.drawable.ic_person);
            }

            String profileName = firstNonEmpty(user.getProfileName(), user.getDisplayName());
            if (profileName == null) profileName = itemView.getContext().getString(
                    R.string.unknown_user);
            String actualName = firstNonEmpty(user.getActualName(), profileName);
            nameTextView.setText(adminActionsEnabled ? actualName : profileName);
            if (adminActionsEnabled && firstNonEmpty(user.getActualName(), null) != null) {
                profileNameTextView.setVisibility(View.VISIBLE);
                profileNameTextView.setText(itemView.getContext().getString(
                        R.string.user_management_profile_name, profileName));
            } else {
                profileNameTextView.setVisibility(View.GONE);
            }
            if (adminActionsEnabled) {
                String contact = contactText(user.getEmail(), user.getPhoneNumber());
                emailTextView.setVisibility(contact.isEmpty() ? View.GONE : View.VISIBLE);
                emailTextView.setText(contact);
            } else {
                emailTextView.setVisibility(View.GONE);
            }

            String roleText = user.getRole().getDisplayName();
            if (user.getRole() == UserRole.ADMIN_USER) {
                roleTextView.setTextColor(itemView.getContext().getColor(R.color.admin_role_color));
                roleTextView.setText(itemView.getContext().getString(
                        R.string.user_management_role_admin_prefix, roleText));
            } else {
                roleTextView.setTextColor(itemView.getContext().getColor(R.color.regular_role_color));
                roleTextView.setText(itemView.getContext().getString(
                        R.string.user_management_role_user_prefix, roleText));
            }

            itemView.setAlpha(user.isHidden() ? 0.72f : 1f);

            providerTextView.setText(user.isHidden()
                    ? itemView.getContext().getString(
                            R.string.user_management_provider,
                            user.getProvider() != null ? user.getProvider() : "Unknown")
                            + " · "
                            + itemView.getContext().getString(R.string.user_management_hidden_badge)
                    : itemView.getContext().getString(
                            R.string.user_management_provider,
                            user.getProvider() != null ? user.getProvider() : "Unknown"));

            if (user.getLastLoginAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                lastLoginTextView.setText(itemView.getContext().getString(
                        R.string.user_management_last_login, sdf.format(user.getLastLoginAt())));
            } else {
                lastLoginTextView.setText(itemView.getContext().getString(
                        R.string.user_management_last_login_never));
            }

            boolean clickable = adminActionsEnabled && listener != null;
            itemView.setClickable(clickable);
            itemView.setFocusable(clickable);
            if (clickable) {
                TypedArray attrs = itemView.getContext().obtainStyledAttributes(
                        new int[] {android.R.attr.selectableItemBackground});
                Drawable ripple = attrs.getDrawable(0);
                attrs.recycle();
                itemView.setForeground(ripple);
                itemView.setOnClickListener(v -> listener.onUserClicked(user));
            } else {
                itemView.setForeground(null);
                itemView.setOnClickListener(null);
            }
        }

        private static String firstNonEmpty(String first, String second) {
            if (first != null && !first.trim().isEmpty()) return first.trim();
            if (second != null && !second.trim().isEmpty()) return second.trim();
            return null;
        }

        private static String contactText(String email, String phone) {
            String safeEmail = email == null ? "" : email.trim();
            String safePhone = phone == null ? "" : phone.trim();
            if (safeEmail.isEmpty()) return safePhone;
            if (safePhone.isEmpty()) return safeEmail;
            return safeEmail + " · " + safePhone;
        }
    }
}
