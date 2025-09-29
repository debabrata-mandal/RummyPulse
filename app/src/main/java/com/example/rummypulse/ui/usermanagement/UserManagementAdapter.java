package com.example.rummypulse.ui.usermanagement;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.UserRole;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying users in the User Management screen
 */
public class UserManagementAdapter extends RecyclerView.Adapter<UserManagementAdapter.UserViewHolder> {

    private List<AppUser> users;
    private OnRoleChangeClickListener roleChangeClickListener;

    public interface OnRoleChangeClickListener {
        void onRoleChangeClicked(AppUser user);
    }

    public UserManagementAdapter(List<AppUser> users, OnRoleChangeClickListener listener) {
        this.users = users;
        this.roleChangeClickListener = listener;
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
        holder.bind(user, roleChangeClickListener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView emailTextView;
        private final TextView roleTextView;
        private final TextView providerTextView;
        private final TextView lastLoginTextView;
        private final Button roleChangeButton;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.textViewUserName);
            emailTextView = itemView.findViewById(R.id.textViewUserEmail);
            roleTextView = itemView.findViewById(R.id.textViewUserRole);
            providerTextView = itemView.findViewById(R.id.textViewUserProvider);
            lastLoginTextView = itemView.findViewById(R.id.textViewLastLogin);
            roleChangeButton = itemView.findViewById(R.id.buttonChangeRole);
        }

        public void bind(AppUser user, OnRoleChangeClickListener listener) {
            // Set user information
            nameTextView.setText(user.getDisplayName() != null ? user.getDisplayName() : "No Name");
            emailTextView.setText(user.getEmail() != null ? user.getEmail() : "No Email");
            
            // Set role with appropriate styling
            String roleText = user.getRole().getDisplayName();
            roleTextView.setText(roleText);
            
            // Color code the role
            if (user.getRole() == UserRole.ADMIN_USER) {
                roleTextView.setTextColor(itemView.getContext().getColor(R.color.admin_role_color));
                roleTextView.setText("🔑 " + roleText);
            } else {
                roleTextView.setTextColor(itemView.getContext().getColor(R.color.regular_role_color));
                roleTextView.setText("👤 " + roleText);
            }
            
            // Set provider
            providerTextView.setText("Provider: " + (user.getProvider() != null ? user.getProvider() : "Unknown"));
            
            // Set last login
            if (user.getLastLoginAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                lastLoginTextView.setText("Last login: " + sdf.format(user.getLastLoginAt()));
            } else {
                lastLoginTextView.setText("Last login: Never");
            }
            
            // Set up role change button
            String buttonText = user.getRole() == UserRole.ADMIN_USER ? 
                "Demote to Regular" : "Promote to Admin";
            roleChangeButton.setText(buttonText);
            
            // Set button styling based on action
            if (user.getRole() == UserRole.ADMIN_USER) {
                roleChangeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    itemView.getContext().getColor(R.color.demote_button_color)));
            } else {
                roleChangeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    itemView.getContext().getColor(R.color.promote_button_color)));
            }
            
            // Set text color to white for better contrast
            roleChangeButton.setTextColor(itemView.getContext().getColor(R.color.text_white));
            
            roleChangeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRoleChangeClicked(user);
                }
            });
        }
    }
}
