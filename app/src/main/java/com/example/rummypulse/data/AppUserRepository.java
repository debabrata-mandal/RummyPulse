package com.example.rummypulse.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository class to handle appUser_v2 collection operations in Firestore.
 */
public class AppUserRepository {
    private static final String TAG = "AppUserRepository";
    public static final int USER_PAGE_SIZE = 50;
    /** Keep the player picker directory warm across game screens for six hours. */
    private static final long USER_DIRECTORY_CACHE_TTL_MS = 6L * 60L * 60L * 1000L;

    private static final Object SYNC_LOCK = new Object();
    private static final Map<String, SyncCacheEntry> RECENT_SYNCS = new HashMap<>();
    private static final Map<String, List<AppUserCallback>> IN_FLIGHT_SYNCS = new HashMap<>();
    private static final Object DIRECTORY_LOCK = new Object();
    private static List<AppUser> cachedUserDirectory;
    private static long cachedUserDirectoryAt;
    private static List<UsersCallback> inFlightDirectoryCallbacks;

    private final FirebaseFirestore db;

    public AppUserRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Creates a missing user or conditionally synchronizes changed profile fields. Existing users
     * update lastLoginAt at most once every 24 hours. The transaction result is returned directly,
     * avoiding a post-transaction document read.
     */
    public void createOrUpdateUser(
            FirebaseUser firebaseUser,
            String provider,
            @Nullable AppUserCallback callback) {
        if (firebaseUser == null) {
            notifyFailure(callback, new IllegalArgumentException("FirebaseUser cannot be null"));
            return;
        }

        String userId = firebaseUser.getUid();
        long nowMillis = System.currentTimeMillis();
        String email = firebaseUser.getEmail();
        String displayName = firebaseUser.getDisplayName();
        String photoUrl = firebaseUser.getPhotoUrl() != null
                ? firebaseUser.getPhotoUrl().toString()
                : null;
        synchronized (SYNC_LOCK) {
            SyncCacheEntry recent = RECENT_SYNCS.get(userId);
            if (recent != null && !AppUserSyncPolicy.plan(
                    recent.appUser,
                    provider,
                    email,
                    displayName,
                    photoUrl,
                    nowMillis).hasUpdates()) {
                Log.d(TAG, "Skipping repeated appUser initialization for " + userId);
                if (callback != null) {
                    callback.onSuccess(recent.appUser);
                }
                return;
            }

            List<AppUserCallback> waiting = IN_FLIGHT_SYNCS.get(userId);
            if (waiting != null) {
                if (callback != null) {
                    waiting.add(callback);
                }
                Log.d(TAG, "Joining in-flight appUser synchronization for " + userId);
                return;
            }

            waiting = new ArrayList<>();
            if (callback != null) {
                waiting.add(callback);
            }
            IN_FLIGHT_SYNCS.put(userId, waiting);
        }

        DocumentReference userRef = db.collection(FirestoreCollections.APP_USER).document(userId);

        db.runTransaction((Transaction transaction) -> {
                    DocumentSnapshot snapshot = transaction.get(userRef);
                    if (!snapshot.exists()) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("userId", userId);
                        userData.put("provider", provider);
                        userData.put("role", UserRole.REGULAR_USER.getValue());
                        userData.put("email", email);
                        userData.put("displayName", displayName);
                        userData.put("photoUrl", photoUrl);
                        userData.put("createdAt", FieldValue.serverTimestamp());
                        userData.put("lastLoginAt", FieldValue.serverTimestamp());
                        transaction.set(userRef, userData);

                        AppUser created = new AppUser(
                                userId,
                                provider,
                                UserRole.REGULAR_USER,
                                email,
                                displayName,
                                photoUrl);
                        created.setCreatedAt(new Date(nowMillis));
                        created.setLastLoginAt(new Date(nowMillis));
                        return new SyncResult(created, true, true);
                    }

                    AppUser existing = documentToAppUser(snapshot);
                    AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                            existing,
                            provider,
                            email,
                            displayName,
                            photoUrl,
                            nowMillis);
                    Map<String, Object> updates = new HashMap<>();
                    if (plan.updateProvider) {
                        updates.put("provider", provider);
                        existing.setProvider(provider);
                    }
                    if (plan.updateEmail) {
                        updates.put("email", email);
                        existing.setEmail(email);
                    }
                    if (plan.updateDisplayName) {
                        updates.put("displayName", displayName);
                        existing.setDisplayName(displayName);
                    }
                    if (plan.updatePhotoUrl) {
                        updates.put("photoUrl", photoUrl);
                        existing.setPhotoUrl(photoUrl);
                    }
                    if (plan.updateLastLoginAt) {
                        updates.put("lastLoginAt", FieldValue.serverTimestamp());
                        existing.setLastLoginAt(new Date(nowMillis));
                    }
                    if (!updates.isEmpty()) {
                        transaction.update(userRef, updates);
                    }
                    return new SyncResult(existing, !updates.isEmpty(), false);
                })
                .addOnSuccessListener(result -> {
                    Log.d(TAG, "appUser sync complete for " + userId
                            + " operations: reads=1 writes=" + (result.wrote ? 1 : 0)
                            + " created=" + result.created);
                    completeSyncSuccess(userId, result.appUser);
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "appUser create/update failed for " + userId, exception);
                    if (exception instanceof FirebaseFirestoreException) {
                        Log.e(TAG, "Firestore error code: "
                                + ((FirebaseFirestoreException) exception).getCode());
                    }
                    completeSyncFailure(userId, exception);
                });
    }

    /**
     * Gets one user explicitly. Startup synchronization does not call this method.
     */
    public void getUserById(String userId, AppUserCallback callback) {
        db.collection(FirestoreCollections.APP_USER).document(userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        notifyFailure(callback, task.getException());
                        return;
                    }
                    DocumentSnapshot document = task.getResult();
                    if (document == null || !document.exists()) {
                        notifyFailure(callback, new Exception("User not found"));
                        return;
                    }
                    try {
                        callback.onSuccess(documentToAppUser(document));
                    } catch (Exception exception) {
                        notifyFailure(callback, exception);
                    }
                });
    }

    /**
     * Deletes a user document. Clears the cached user directory on success.
     */
    public void deleteUser(String userId, VoidCallback callback) {
        db.collection(FirestoreCollections.APP_USER).document(userId)
                .delete()
                .addOnSuccessListener(unused -> {
                    invalidateUserDirectoryCache();
                    Log.d(TAG, "User deleted with operations: reads=0 writes=1 for " + userId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(exception -> notifyVoidFailure(callback, exception));
    }

    /**
     * Updates a role with one write and returns a minimal local result without rereading the user.
     */
    public void updateUserRole(String userId, UserRole newRole, AppUserCallback callback) {
        db.collection(FirestoreCollections.APP_USER).document(userId)
                .update("role", newRole.getValue())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "User role updated with operations: reads=0 writes=1 for " + userId);
                    AppUser updated = new AppUser();
                    updated.setUserId(userId);
                    updated.setRole(newRole);
                    if (callback != null) {
                        callback.onSuccess(updated);
                    }
                })
                .addOnFailureListener(exception -> notifyFailure(callback, exception));
    }

    /**
     * Loads a bounded page ordered by document ID. Passing a null cursor starts a fresh listing.
     */
    public void getUsersPage(
            @Nullable DocumentSnapshot after,
            int pageSize,
            UsersPageCallback callback) {
        int boundedSize = Math.max(1, Math.min(pageSize, USER_PAGE_SIZE));
        Query query = db.collection(FirestoreCollections.APP_USER)
                .orderBy(FieldPath.documentId())
                .limit(boundedSize);
        if (after != null) {
            query = query.startAfter(after);
        }

        query.get().addOnCompleteListener(task -> handleUsersPage(task, boundedSize, callback));
    }

    /**
     * Returns the complete user directory from a process-wide six-hour cache. Concurrent callers
     * join the same paginated Firestore fetch, so opening several game/player dialogs never starts
     * duplicate reads.
     */
    public void getUsersCached(UsersCallback callback) {
        if (callback == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (DIRECTORY_LOCK) {
            if (cachedUserDirectory != null
                    && now - cachedUserDirectoryAt < USER_DIRECTORY_CACHE_TTL_MS) {
                callback.onSuccess(new ArrayList<>(cachedUserDirectory));
                return;
            }
            if (inFlightDirectoryCallbacks != null) {
                inFlightDirectoryCallbacks.add(callback);
                return;
            }
            inFlightDirectoryCallbacks = new ArrayList<>();
            inFlightDirectoryCallbacks.add(callback);
        }
        loadUserDirectoryPage(null, new ArrayList<>());
    }

    private void loadUserDirectoryPage(
            @Nullable DocumentSnapshot after,
            List<AppUser> accumulated) {
        getUsersPage(after, USER_PAGE_SIZE, new UsersPageCallback() {
            @Override
            public void onSuccess(UsersPage page) {
                accumulated.addAll(page.users);
                if (page.hasMore && page.nextCursor != null) {
                    loadUserDirectoryPage(page.nextCursor, accumulated);
                    return;
                }
                completeDirectorySuccess(accumulated);
            }

            @Override
            public void onFailure(Exception exception) {
                completeDirectoryFailure(exception);
            }
        });
    }

    private static void completeDirectorySuccess(List<AppUser> users) {
        List<UsersCallback> callbacks;
        List<AppUser> snapshot = new ArrayList<>(users);
        synchronized (DIRECTORY_LOCK) {
            cachedUserDirectory = snapshot;
            cachedUserDirectoryAt = System.currentTimeMillis();
            callbacks = inFlightDirectoryCallbacks;
            inFlightDirectoryCallbacks = null;
        }
        if (callbacks != null) {
            for (UsersCallback callback : callbacks) {
                callback.onSuccess(new ArrayList<>(snapshot));
            }
        }
    }

    private static void completeDirectoryFailure(Exception exception) {
        List<UsersCallback> callbacks;
        List<AppUser> stale;
        synchronized (DIRECTORY_LOCK) {
            callbacks = inFlightDirectoryCallbacks;
            inFlightDirectoryCallbacks = null;
            stale = cachedUserDirectory == null
                    ? null
                    : new ArrayList<>(cachedUserDirectory);
        }
        if (callbacks != null) {
            for (UsersCallback callback : callbacks) {
                if (stale != null) {
                    callback.onSuccess(new ArrayList<>(stale));
                } else {
                    callback.onFailure(exception != null
                            ? exception
                            : new Exception("Could not load users"));
                }
            }
        }
    }

    private void handleUsersPage(
            Task<QuerySnapshot> task,
            int pageSize,
            UsersPageCallback callback) {
        if (!task.isSuccessful()) {
            callback.onFailure(task.getException());
            return;
        }
        QuerySnapshot snapshot = task.getResult();
        List<AppUser> users = new ArrayList<>();
        List<DocumentSnapshot> documents = snapshot != null
                ? snapshot.getDocuments()
                : new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            try {
                users.add(documentToAppUser(document));
            } catch (Exception exception) {
                Log.e(TAG, "Error converting appUser " + document.getId(), exception);
            }
        }
        DocumentSnapshot nextCursor = documents.isEmpty()
                ? null
                : documents.get(documents.size() - 1);
        boolean hasMore = documents.size() == pageSize;
        Log.d(TAG, "Loaded bounded user page: reads=" + documents.size()
                + " hasMore=" + hasMore);
        callback.onSuccess(new UsersPage(users, nextCursor, hasMore));
    }

    private AppUser documentToAppUser(DocumentSnapshot document) {
        AppUser appUser = new AppUser();
        appUser.setUserId(document.getString("userId"));
        if (appUser.getUserId() == null) {
            appUser.setUserId(document.getId());
        }
        appUser.setProvider(document.getString("provider"));
        appUser.setRole(UserRole.fromString(document.getString("role")));
        appUser.setEmail(document.getString("email"));
        appUser.setDisplayName(document.getString("displayName"));
        appUser.setPhotoUrl(document.getString("photoUrl"));
        appUser.setCreatedAt(document.getDate("createdAt"));
        appUser.setLastLoginAt(document.getDate("lastLoginAt"));
        return appUser;
    }

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

    private static void completeSyncSuccess(String userId, AppUser appUser) {
        List<AppUserCallback> callbacks;
        synchronized (SYNC_LOCK) {
            RECENT_SYNCS.put(userId, new SyncCacheEntry(appUser));
            callbacks = IN_FLIGHT_SYNCS.remove(userId);
        }
        if (callbacks != null) {
            for (AppUserCallback callback : callbacks) {
                callback.onSuccess(appUser);
            }
        }
    }

    private static void completeSyncFailure(String userId, Exception exception) {
        List<AppUserCallback> callbacks;
        synchronized (SYNC_LOCK) {
            callbacks = IN_FLIGHT_SYNCS.remove(userId);
        }
        if (callbacks != null) {
            for (AppUserCallback callback : callbacks) {
                callback.onFailure(exception);
            }
        }
    }

    private static void invalidateUserDirectoryCache() {
        synchronized (DIRECTORY_LOCK) {
            cachedUserDirectory = null;
            cachedUserDirectoryAt = 0;
        }
    }

    private static void notifyFailure(@Nullable AppUserCallback callback, Exception exception) {
        if (callback != null) {
            callback.onFailure(exception != null ? exception : new Exception("Unknown Firestore error"));
        }
    }

    private static void notifyVoidFailure(@Nullable VoidCallback callback, Exception exception) {
        if (callback != null) {
            callback.onFailure(exception != null ? exception : new Exception("Unknown Firestore error"));
        }
    }

    private static final class SyncResult {
        final AppUser appUser;
        final boolean wrote;
        final boolean created;

        SyncResult(AppUser appUser, boolean wrote, boolean created) {
            this.appUser = appUser;
            this.wrote = wrote;
            this.created = created;
        }
    }

    private static final class SyncCacheEntry {
        final AppUser appUser;

        SyncCacheEntry(AppUser appUser) {
            this.appUser = appUser;
        }
    }

    public static final class UsersPage {
        public final List<AppUser> users;
        @Nullable public final DocumentSnapshot nextCursor;
        public final boolean hasMore;

        UsersPage(
                List<AppUser> users,
                @Nullable DocumentSnapshot nextCursor,
                boolean hasMore) {
            this.users = users;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }
    }

    public interface AppUserCallback {
        void onSuccess(AppUser appUser);
        void onFailure(Exception exception);
    }

    public interface UsersPageCallback {
        void onSuccess(UsersPage page);
        void onFailure(Exception exception);
    }

    public interface UsersCallback {
        void onSuccess(List<AppUser> users);
        void onFailure(Exception exception);
    }

    public interface VoidCallback {
        void onSuccess();
        void onFailure(Exception exception);
    }
}
