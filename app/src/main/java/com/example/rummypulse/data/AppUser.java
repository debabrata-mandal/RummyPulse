package com.example.rummypulse.data;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

/**
 * Data model for the appUser collection in Firestore
 * Stores user authentication provider information and role
 */
public class AppUser {
    private String userId;
    private String provider;
    private UserRole role;
    private String email;
    private String displayName;
    private String photoUrl;
    @ServerTimestamp
    private Date createdAt;
    @ServerTimestamp
    private Date lastLoginAt;

    // Default constructor required for Firestore
    public AppUser() {}

    public AppUser(String userId, String provider, UserRole role, String email, String displayName) {
        this.userId = userId;
        this.provider = provider;
        this.role = role;
        this.email = email;
        this.displayName = displayName;
    }
    
    public AppUser(String userId, String provider, UserRole role, String email, String displayName, String photoUrl) {
        this.userId = userId;
        this.provider = provider;
        this.role = role;
        this.email = email;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Date lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    @Override
    public String toString() {
        return "AppUser{" +
                "userId='" + userId + '\'' +
                ", provider='" + provider + '\'' +
                ", role=" + role +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", photoUrl='" + photoUrl + '\'' +
                ", createdAt=" + createdAt +
                ", lastLoginAt=" + lastLoginAt +
                '}';
    }
}
