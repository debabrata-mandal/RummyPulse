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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
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
        private final TextView emailTextView;
        private final TextView roleTextView;
        private final TextView providerTextView;
        private final TextView lastLoginTextView;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImageView = itemView.findViewById(R.id.imageViewUserProfile);
            nameTextView = itemView.findViewById(R.id.textViewUserName);
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
    }
}
