package com.example.rummypulse.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

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

        // Single transaction: avoids race between "get" and "set" and keeps create vs update atomic.
        db.runTransaction((Transaction transaction) -> {
                    DocumentSnapshot snapshot = transaction.get(userRef);
                    if (!snapshot.exists()) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("userId", userId);
                        userData.put("provider", provider);
                        userData.put("role", UserRole.REGULAR_USER.getValue());
                        userData.put("email", firebaseUser.getEmail());
                        userData.put("displayName", firebaseUser.getDisplayName());
                        userData.put("photoUrl", firebaseUser.getPhotoUrl() != null
                                ? firebaseUser.getPhotoUrl().toString() : null);
                        userData.put("createdAt", FieldValue.serverTimestamp());
                        userData.put("lastLoginAt", FieldValue.serverTimestamp());
                        transaction.set(userRef, userData);
                    } else {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lastLoginAt", FieldValue.serverTimestamp());
                        updates.put("photoUrl", firebaseUser.getPhotoUrl() != null
                                ? firebaseUser.getPhotoUrl().toString() : null);
                        transaction.update(userRef, updates);
                    }
                    return null;
                })
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "appUser transaction succeeded for " + userId);
                    getUserById(userId, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "appUser create/update failed. Ensure Firestore rules allow authenticated "
                            + "users to read/write appUser/{theirUid}. Error: " + e.getMessage(), e);
                    if (e instanceof FirebaseFirestoreException) {
                        Log.e(TAG, "Firestore error code: " + ((FirebaseFirestoreException) e).getCode());
                    }
                    if (callback != null) {
                        callback.onFailure(e);
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
        appUser.setPhotoUrl(document.getString("photoUrl"));
        appUser.setCreatedAt(document.getDate("createdAt"));
        appUser.setLastLoginAt(document.getDate("lastLoginAt"));
        return appUser;
    }
    
    /**
     * Get authentication provider from FirebaseUser
     * Maps Firebase provider IDs to readable names
     */
    public static String getProviderName(FirebaseUser firebaseUser) {
        if (firebaseUser == null) {
            return "unknown";
        }
        for (UserInfo userInfo : firebaseUser.getProviderData()) {
            String providerId = userInfo.getProviderId();
            if (FirebaseAuthProvider.PROVIDER_ID.equals(providerId)) {
                continue;
            }
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
        return "unknown";
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
