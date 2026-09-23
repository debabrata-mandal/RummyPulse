package com.example.rummypulse.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Repository class to handle appUser_v2 collection operations in Firestore.
 */
public class AppUserRepository {
    private static final String TAG = "AppUserRepository";
    public static final int USER_PAGE_SIZE = 50;
    /** Keep the player picker directory warm across game screens for six hours. */
    private static final long USER_DIRECTORY_CACHE_TTL_MS = 6L * 60L * 60L * 1000L;
    /** Throttles lightweight profileVersion delta queries on app resume. */
    public static final long PROFILE_DELTA_REFRESH_THROTTLE_MS = 30L * 60L * 1000L;

    private static final Object SYNC_LOCK = new Object();
    private static final Map<String, List<AppUserCallback>> IN_FLIGHT_SYNCS = new HashMap<>();
    private static final Object DIRECTORY_LOCK = new Object();
    private static List<AppUser> cachedUserDirectory;
    private static long cachedUserDirectoryAt;
    private static List<UsersCallback> inFlightDirectoryCallbacks;
    private static long lastProfileDeltaRefreshAt;
    private static boolean profileDeltaRefreshInFlight;
    private static final List<DirectoryChangeListener> DIRECTORY_CHANGE_LISTENERS =
            new CopyOnWriteArrayList<>();

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
        createOrUpdateUser(firebaseUser, provider, null, false, callback);
    }

    public void createOrUpdateUser(
            FirebaseUser firebaseUser,
            String provider,
            @Nullable ProfileOverrides overrides,
            boolean forceProfileVersionRefresh,
            @Nullable AppUserCallback callback) {
        if (firebaseUser == null) {
            notifyFailure(callback, new IllegalArgumentException("FirebaseUser cannot be null"));
            return;
        }

        String userId = firebaseUser.getUid();
        synchronized (SYNC_LOCK) {
            List<AppUserCallback> waiting = IN_FLIGHT_SYNCS.get(userId);
            if (waiting != null) {
                if (callback != null) {
                    waiting.add(callback);
                }
                Log.d(TAG, "Joining in-flight appUser synchronization");
                return;
            }

            waiting = new ArrayList<>();
            if (callback != null) {
                waiting.add(callback);
            }
            IN_FLIGHT_SYNCS.put(userId, waiting);
        }

        String googleDisplayName = overrides != null ? overrides.displayName : firebaseUser.getDisplayName();
        String photoUrl = overrides != null ? overrides.photoUrl
                : firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null;
        Map<String, Object> request = new HashMap<>();
        request.put("provider", provider);
        request.put("googleDisplayName", googleDisplayName);
        request.put("email", firebaseUser.getEmail());
        request.put("photoUrl", photoUrl);
        request.put("forceProfileVersionRefresh", forceProfileVersionRefresh);
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("syncMyIdentity").call(request)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null
                                ? task.getException()
                                : new IllegalStateException("Profile synchronization failed");
                    }
                    return db.collection(FirestoreCollections.APP_USER).document(userId).get();
                })
                .addOnSuccessListener(snapshot -> {
                    try {
                        completeSyncSuccess(userId, documentToAppUser(snapshot));
                    } catch (Exception exception) {
                        completeSyncFailure(userId, exception);
                    }
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "Private profile synchronization failed", exception);
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
     * Updates a role with one write and returns a minimal local result without rereading the user.
     */
    public void updateUserRole(String userId, UserRole newRole, AppUserCallback callback) {
        db.collection(FirestoreCollections.APP_USER).document(userId)
                .update("role", newRole.getValue())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "User role updated with operations: reads=0 writes=1");
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
     * Hides or restores a user in the directory. Hidden users stay in Firestore but are omitted
     * from player-mapping pickers. Clears the cached user directory on success.
     */
    public void updateUserHidden(String userId, boolean hidden, AppUserCallback callback) {
        db.collection(FirestoreCollections.APP_USER).document(userId)
                .update("hidden", hidden)
                .addOnSuccessListener(unused -> {
                    invalidateUserDirectoryCache();
                    Log.d(TAG, "User hidden flag updated with operations: reads=0 writes=1"
                            + " hidden=" + hidden);
                    AppUser updated = new AppUser();
                    updated.setUserId(userId);
                    updated.setHidden(hidden);
                    if (callback != null) {
                        callback.onSuccess(updated);
                    }
                })
                .addOnFailureListener(exception -> notifyFailure(callback, exception));
    }

    public void createManagedProfile(
            String actualName, String profileName, String email,
            String phoneNumber, AppUserCallback callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("actualName", actualName);
        request.put("profileName", profileName);
        request.put("email", email);
        request.put("phoneNumber", phoneNumber);
        callManagedProfileFunction("adminCreateManagedProfile", request, callback);
    }

    public void updateManagedProfile(
            String userId, String actualName, String profileName, String email,
            String phoneNumber, AppUserCallback callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId);
        request.put("actualName", actualName);
        request.put("profileName", profileName);
        request.put("email", email);
        request.put("phoneNumber", phoneNumber);
        callManagedProfileFunction("adminUpdateManagedProfile", request, callback);
    }

    private void callManagedProfileFunction(
            String functionName, Map<String, Object> request, AppUserCallback callback) {
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable(functionName).call(request)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null
                                ? task.getException()
                                : new IllegalStateException("Managed profile update failed");
                    }
                    Object raw = task.getResult().getData();
                    if (!(raw instanceof Map)) {
                        throw new IllegalStateException("Managed profile response is invalid");
                    }
                    Object userId = ((Map<?, ?>) raw).get("userId");
                    if (!(userId instanceof String)) {
                        throw new IllegalStateException("Managed profile id is missing");
                    }
                    return db.collection(FirestoreCollections.APP_USER)
                            .document((String) userId).get();
                })
                .addOnSuccessListener(snapshot -> {
                    invalidateUserDirectoryCache();
                    try {
                        callback.onSuccess(documentToAppUser(snapshot));
                    } catch (Exception exception) {
                        notifyFailure(callback, exception);
                    }
                })
                .addOnFailureListener(exception -> notifyFailure(callback, exception));
    }

    /** Records the signed-in user's current safe-play acknowledgment. */
    public Task<Void> acceptSafePlayPolicy(String userId, int policyVersion) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("safePlayPolicyVersion", policyVersion);
        updates.put("safePlayAcceptedAt", FieldValue.serverTimestamp());
        return db.collection(FirestoreCollections.APP_USER)
                .document(userId)
                .update(updates);
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

        query.get().addOnCompleteListener(task -> handleUsersPage(task, boundedSize, false, callback));
    }

    /** Loads one user-management page with admin-only Google identity fields joined by UID. */
    public void getAdminUsersPage(@Nullable DocumentSnapshot after, int pageSize,
            UsersPageCallback callback) {
        int boundedSize = Math.max(1, Math.min(pageSize, USER_PAGE_SIZE));
        Query query = db.collection(FirestoreCollections.APP_USER)
                .orderBy(FieldPath.documentId()).limit(boundedSize);
        if (after != null) {
            query = query.startAfter(after);
        }
        query.get().addOnCompleteListener(task -> handleUsersPage(task, boundedSize, true, callback));
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

    /** Loads the shared directory and joins admin-only Google identity fields by UID. */
    public void getUsersCachedForAdmin(UsersCallback callback) {
        getUsersCached(new UsersCallback() {
            @Override
            public void onSuccess(List<AppUser> users) {
                List<AppUser> adminUsers = new ArrayList<>();
                for (AppUser user : users) {
                    adminUsers.add(copyPublicUser(user));
                }
                enrichWithPrivateIdentities(adminUsers, callback);
            }

            @Override
            public void onFailure(Exception exception) {
                callback.onFailure(exception);
            }
        });
    }

    /**
     * Fetches only users whose profileVersion changed since this device last saw them. Intended for
     * resume-time refresh without reloading the full directory.
     */
    public void refreshProfileChangesSince(@Nullable ProfileChangesCallback callback) {
        long now = System.currentTimeMillis();
        synchronized (DIRECTORY_LOCK) {
            if (cachedUserDirectory == null) {
                notifyProfileChanges(callback, Collections.emptyList());
                return;
            }
            if (now - lastProfileDeltaRefreshAt < PROFILE_DELTA_REFRESH_THROTTLE_MS) {
                notifyProfileChanges(callback, Collections.emptyList());
                return;
            }
            if (profileDeltaRefreshInFlight) {
                return;
            }
            profileDeltaRefreshInFlight = true;
            lastProfileDeltaRefreshAt = now;
        }

        long sinceVersion = getCachedMaxProfileVersion();
        db.collection(FirestoreCollections.APP_USER)
                .whereGreaterThan("profileVersion", sinceVersion)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AppUser> changedUsers = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            try {
                                changedUsers.add(documentToAppUser(document));
                            } catch (Exception exception) {
                                Log.e(TAG, "Error converting profile delta document", exception);
                            }
                        }
                    }
                    if (!changedUsers.isEmpty()) {
                        patchUsersInDirectoryCache(changedUsers);
                        notifyDirectoryChangeListeners(changedUsers);
                    }
                    completeProfileDeltaRefresh();
                    notifyProfileChanges(callback, changedUsers);
                    Log.d(TAG, "Profile delta refresh: reads=" + changedUsers.size()
                            + " sinceVersion=" + sinceVersion);
                })
                .addOnFailureListener(exception -> {
                    Log.w(TAG, "Profile delta refresh failed", exception);
                    completeProfileDeltaRefresh();
                    notifyProfileChanges(callback, Collections.emptyList());
                });
    }

    public static void addDirectoryChangeListener(DirectoryChangeListener listener) {
        if (listener != null) {
            DIRECTORY_CHANGE_LISTENERS.add(listener);
        }
    }

    public static void removeDirectoryChangeListener(DirectoryChangeListener listener) {
        DIRECTORY_CHANGE_LISTENERS.remove(listener);
    }

    private static void completeProfileDeltaRefresh() {
        synchronized (DIRECTORY_LOCK) {
            profileDeltaRefreshInFlight = false;
        }
    }

    private static void notifyProfileChanges(
            @Nullable ProfileChangesCallback callback,
            List<AppUser> changedUsers) {
        if (callback != null) {
            callback.onComplete(new ArrayList<>(changedUsers));
        }
    }

    private static void notifyDirectoryChangeListeners(List<AppUser> changedUsers) {
        for (DirectoryChangeListener listener : DIRECTORY_CHANGE_LISTENERS) {
            listener.onUsersUpdated(changedUsers);
        }
    }

    private static long getCachedMaxProfileVersion() {
        synchronized (DIRECTORY_LOCK) {
            if (cachedUserDirectory == null || cachedUserDirectory.isEmpty()) {
                return 0L;
            }
            long maxVersion = 0L;
            for (AppUser user : cachedUserDirectory) {
                if (user != null) {
                    maxVersion = Math.max(maxVersion, user.getProfileVersion());
                }
            }
            return maxVersion;
        }
    }

    private static void patchUsersInDirectoryCache(List<AppUser> changedUsers) {
        synchronized (DIRECTORY_LOCK) {
            if (cachedUserDirectory == null || changedUsers.isEmpty()) {
                return;
            }
            Map<String, AppUser> changedById = new HashMap<>();
            for (AppUser user : changedUsers) {
                if (user != null && user.getUserId() != null) {
                    changedById.put(user.getUserId(), user);
                }
            }
            List<AppUser> patched = new ArrayList<>(cachedUserDirectory.size());
            boolean replaced = false;
            for (AppUser user : cachedUserDirectory) {
                if (user == null || user.getUserId() == null) {
                    patched.add(user);
                    continue;
                }
                AppUser changed = changedById.remove(user.getUserId());
                if (changed != null) {
                    patched.add(changed);
                    replaced = true;
                } else {
                    patched.add(user);
                }
            }
            patched.addAll(changedById.values());
            if (replaced || !changedById.isEmpty()) {
                cachedUserDirectory = patched;
            }
        }
    }

    private static void patchUserInDirectoryCache(AppUser updatedUser) {
        if (updatedUser == null || updatedUser.getUserId() == null) {
            return;
        }
        patchUsersInDirectoryCache(Collections.singletonList(updatedUser));
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
            boolean includePrivateIdentity,
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
                Log.e(TAG, "Error converting appUser document", exception);
            }
        }
        DocumentSnapshot nextCursor = documents.isEmpty()
                ? null
                : documents.get(documents.size() - 1);
        boolean hasMore = documents.size() == pageSize;
        Log.d(TAG, "Loaded bounded user page: reads=" + documents.size()
                + " hasMore=" + hasMore);
        if (!includePrivateIdentity) {
            callback.onSuccess(new UsersPage(users, nextCursor, hasMore));
            return;
        }
        enrichWithPrivateIdentities(users, new UsersCallback() {
            @Override
            public void onSuccess(List<AppUser> enrichedUsers) {
                callback.onSuccess(new UsersPage(enrichedUsers, nextCursor, hasMore));
            }

            @Override
            public void onFailure(Exception exception) {
                callback.onFailure(exception);
            }
        });
    }

    private void enrichWithPrivateIdentities(List<AppUser> users, UsersCallback callback) {
        if (users == null || users.isEmpty()) {
            callback.onSuccess(users == null ? new ArrayList<>() : users);
            return;
        }
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (AppUser user : users) {
            reads.add(db.collection(FirestoreCollections.APP_USER_IDENTITY)
                    .document(user.getUserId()).get());
        }
        Tasks.whenAllComplete(reads).addOnSuccessListener(completed -> {
            for (int index = 0; index < completed.size(); index++) {
                Task<?> task = completed.get(index);
                if (!task.isSuccessful() || !(task.getResult() instanceof DocumentSnapshot)) {
                    continue;
                }
                DocumentSnapshot identity = (DocumentSnapshot) task.getResult();
                users.get(index).setEmail(identity.getString("email"));
                users.get(index).setGoogleDisplayName(identity.getString("googleDisplayName"));
                users.get(index).setActualName(identity.getString("actualName"));
                users.get(index).setPhoneNumber(identity.getString("phoneNumber"));
            }
            callback.onSuccess(users);
        }).addOnFailureListener(callback::onFailure);
    }

    private static AppUser copyPublicUser(AppUser source) {
        AppUser copy = new AppUser(source.getUserId(), source.getProvider(), source.getRole(),
                null, source.getDisplayName(), source.getPhotoUrl());
        copy.setProfileName(source.getProfileName());
        copy.setProfileType(source.getProfileType());
        copy.setActualName(source.getActualName());
        copy.setPhoneNumber(source.getPhoneNumber());
        copy.setProfileVersion(source.getProfileVersion());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setLastLoginAt(source.getLastLoginAt());
        copy.setSafePlayPolicyVersion(source.getSafePlayPolicyVersion());
        copy.setSafePlayAcceptedAt(source.getSafePlayAcceptedAt());
        copy.setHidden(source.isHidden());
        return copy;
    }

    private AppUser documentToAppUser(DocumentSnapshot document) {
        AppUser appUser = new AppUser();
        appUser.setUserId(document.getString("userId"));
        if (appUser.getUserId() == null) {
            appUser.setUserId(document.getId());
        }
        appUser.setProvider(document.getString("provider"));
        appUser.setProfileType(document.getString("profileType"));
        appUser.setRole(UserRole.fromString(document.getString("role")));
        appUser.setEmail(document.getString("email"));
        appUser.setDisplayName(document.getString("displayName"));
        appUser.setProfileName(document.getString("profileName"));
        appUser.setPhotoUrl(document.getString("photoUrl"));
        Long profileVersion = document.getLong("profileVersion");
        appUser.setProfileVersion(profileVersion != null ? profileVersion : 0L);
        appUser.setCreatedAt(document.getDate("createdAt"));
        appUser.setLastLoginAt(document.getDate("lastLoginAt"));
        Long safePlayPolicyVersion = document.getLong("safePlayPolicyVersion");
        appUser.setSafePlayPolicyVersion(safePlayPolicyVersion == null
                ? null
                : safePlayPolicyVersion.intValue());
        appUser.setSafePlayAcceptedAt(document.getDate("safePlayAcceptedAt"));
        Boolean hidden = document.getBoolean("hidden");
        appUser.setHidden(hidden != null && hidden);
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
            callbacks = IN_FLIGHT_SYNCS.remove(userId);
        }
        patchUserInDirectoryCache(appUser);
        notifyDirectoryChangeListeners(Collections.singletonList(appUser));
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

    /** Clears process-wide user-directory and sync caches on sign-out. */
    public static void clearSessionCaches() {
        synchronized (SYNC_LOCK) {
            IN_FLIGHT_SYNCS.clear();
        }
        synchronized (DIRECTORY_LOCK) {
            cachedUserDirectory = null;
            cachedUserDirectoryAt = 0;
            inFlightDirectoryCallbacks = null;
            lastProfileDeltaRefreshAt = 0L;
            profileDeltaRefreshInFlight = false;
        }
    }

    private static void notifyFailure(@Nullable AppUserCallback callback, Exception exception) {
        if (callback != null) {
            callback.onFailure(exception != null ? exception : new Exception("Unknown Firestore error"));
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

    public interface ProfileChangesCallback {
        void onComplete(List<AppUser> changedUsers);
    }

    public interface DirectoryChangeListener {
        void onUsersUpdated(List<AppUser> changedUsers);
    }

    public static final class ProfileOverrides {
        @Nullable public final String displayName;
        @Nullable public final String photoUrl;

        public ProfileOverrides(@Nullable String displayName, @Nullable String photoUrl) {
            this.displayName = displayName;
            this.photoUrl = photoUrl;
        }
    }

}
