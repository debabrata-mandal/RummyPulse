package com.example.rummypulse.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository class to handle appUser collection operations in Firestore
 */
public class AppUserRepository {
    private static final String TAG = "AppUserRepository";
    private static final String COLLECTION_NAME = "appUser";
    
    private final FirebaseFirestore db;
    
    public AppUserRepository() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    /**
     * Create or update user in appUser collection
     * If user exists, only updates lastLoginAt timestamp
     * If user doesn't exist, creates new user with REGULAR_USER role
     * 
     * @param firebaseUser Firebase authenticated user
     * @param provider Authentication provider (e.g., "google.com", "microsoft.com", "facebook.com")
     * @param callback Callback to handle success/failure
     */
    public void createOrUpdateUser(FirebaseUser firebaseUser, String provider, AppUserCallback callback) {
        if (firebaseUser == null) {
            Log.e(TAG, "FirebaseUser is null");
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("FirebaseUser cannot be null"));
            }
            return;
        }
        
        String userId = firebaseUser.getUid();
        DocumentReference userRef = db.collection(COLLECTION_NAME).document(userId);
        
        // Check if user already exists
        userRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        // User exists, update lastLoginAt
                        updateLastLogin(userId, callback);
                    } else {
                        // User doesn't exist, create new user
                        createNewUser(firebaseUser, provider, callback);
                    }
                } else {
                    Log.e(TAG, "Error checking user existence", task.getException());
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            }
        });
    }
    
    /**
     * Create a new user in the appUser collection
     */
    private void createNewUser(FirebaseUser firebaseUser, String provider, AppUserCallback callback) {
        String userId = firebaseUser.getUid();
        String email = firebaseUser.getEmail();
        String displayName = firebaseUser.getDisplayName();
        
        // Create AppUser object
        AppUser appUser = new AppUser(userId, provider, UserRole.REGULAR_USER, email, displayName);
        
        // Convert to Map for Firestore
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", appUser.getUserId());
        userData.put("provider", appUser.getProvider());
        userData.put("role", appUser.getRole().getValue());
        userData.put("email", appUser.getEmail());
        userData.put("displayName", appUser.getDisplayName());
        userData.put("createdAt", FieldValue.serverTimestamp());
        userData.put("lastLoginAt", FieldValue.serverTimestamp());
        
        db.collection(COLLECTION_NAME).document(userId)
                .set(userData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "New user created successfully: " + userId);
                        if (callback != null) {
                            callback.onSuccess(appUser);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error creating new user", e);
                        if (callback != null) {
                            callback.onFailure(e);
                        }
                    }
                });
    }
    
    /**
     * Update lastLoginAt timestamp for existing user
     */
    private void updateLastLogin(String userId, AppUserCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastLoginAt", FieldValue.serverTimestamp());
        
        db.collection(COLLECTION_NAME).document(userId)
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "User lastLoginAt updated successfully: " + userId);
                        if (callback != null) {
                            // Fetch updated user data
                            getUserById(userId, callback);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error updating lastLoginAt", e);
                        if (callback != null) {
                            callback.onFailure(e);
                        }
                    }
                });
    }
    
    /**
     * Get user by ID from appUser collection
     */
    public void getUserById(String userId, AppUserCallback callback) {
        db.collection(COLLECTION_NAME).document(userId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                try {
                                    AppUser appUser = documentToAppUser(document);
                                    if (callback != null) {
                                        callback.onSuccess(appUser);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error converting document to AppUser", e);
                                    if (callback != null) {
                                        callback.onFailure(e);
                                    }
                                }
                            } else {
                                Log.d(TAG, "User not found: " + userId);
                                if (callback != null) {
                                    callback.onFailure(new Exception("User not found"));
                                }
                            }
                        } else {
                            Log.e(TAG, "Error getting user", task.getException());
                            if (callback != null) {
                                callback.onFailure(task.getException());
                            }
                        }
                    }
                });
    }
    
    /**
     * Update user role (Admin functionality)
     */
    public void updateUserRole(String userId, UserRole newRole, AppUserCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", newRole.getValue());
        
        db.collection(COLLECTION_NAME).document(userId)
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "User role updated successfully: " + userId + " -> " + newRole);
                        if (callback != null) {
                            getUserById(userId, callback);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error updating user role", e);
                        if (callback != null) {
                            callback.onFailure(e);
                        }
                    }
                });
    }
    
    /**
     * Convert Firestore document to AppUser object
     */
    private AppUser documentToAppUser(DocumentSnapshot document) {
        AppUser appUser = new AppUser();
        appUser.setUserId(document.getString("userId"));
        appUser.setProvider(document.getString("provider"));
        appUser.setRole(UserRole.fromString(document.getString("role")));
        appUser.setEmail(document.getString("email"));
        appUser.setDisplayName(document.getString("displayName"));
        appUser.setCreatedAt(document.getDate("createdAt"));
        appUser.setLastLoginAt(document.getDate("lastLoginAt"));
        return appUser;
    }
    
    /**
     * Get authentication provider from FirebaseUser
     * Maps Firebase provider IDs to readable names
     */
    public static String getProviderName(FirebaseUser firebaseUser) {
        if (firebaseUser == null || firebaseUser.getProviderData().isEmpty()) {
            return "unknown";
        }
        
        String providerId = firebaseUser.getProviderData().get(1).getProviderId(); // Skip firebase provider
        
        switch (providerId) {
            case "google.com":
                return "Google";
            case "microsoft.com":
                return "Microsoft";
            case "facebook.com":
                return "Facebook";
            case "twitter.com":
                return "Twitter";
            case "github.com":
                return "GitHub";
            case "apple.com":
                return "Apple";
            case "password":
                return "Email/Password";
            default:
                return providerId;
        }
    }
    
    /**
     * Get all users from appUser collection (Admin functionality)
     */
    public void getAllUsers(AllUsersCallback callback) {
        Log.d(TAG, "Fetching all users from appUser collection");
        
        db.collection(COLLECTION_NAME)
                .get()
                .addOnCompleteListener(new OnCompleteListener<com.google.firebase.firestore.QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<com.google.firebase.firestore.QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            com.google.firebase.firestore.QuerySnapshot querySnapshot = task.getResult();
                            if (querySnapshot != null) {
                                java.util.List<AppUser> users = new java.util.ArrayList<>();
                                
                                for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                                    try {
                                        AppUser appUser = documentToAppUser(document);
                                        users.add(appUser);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error converting document to AppUser: " + document.getId(), e);
                                    }
                                }
                                
                                Log.d(TAG, "Successfully loaded " + users.size() + " users");
                                if (callback != null) {
                                    callback.onSuccess(users);
                                }
                            } else {
                                Log.w(TAG, "QuerySnapshot is null");
                                if (callback != null) {
                                    callback.onFailure(new Exception("No data received"));
                                }
                            }
                        } else {
                            Log.e(TAG, "Error getting all users", task.getException());
                            if (callback != null) {
                                callback.onFailure(task.getException());
                            }
                        }
                    }
                });
    }
    
    /**
     * Callback interface for AppUser operations
     */
    public interface AppUserCallback {
        void onSuccess(AppUser appUser);
        void onFailure(Exception exception);
    }
    
    /**
     * Callback interface for multiple AppUser operations
     */
    public interface AllUsersCallback {
        void onSuccess(java.util.List<AppUser> users);
        void onFailure(Exception exception);
    }
}
