package com.example.rummypulse.data;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read/write helpers for {@code gameViewApprovals_v2}. Writes are minimized: one doc per user per game,
 * create-on-first-request only, no writes on retry.
 */
public class GameViewApprovalRepository {

    /** Denormalized index on {@code games_v2} so editors can list requests without collection-query rules. */
    static final String PENDING_VIEW_REQUESTS_FIELD = "pendingViewRequests";

    public enum ViewAccessOutcome {
        GRANTED,
        PENDING,
        REJECTED
    }

    public interface ViewAccessCallback {
        void onResult(ViewAccessOutcome outcome);

        void onError(String message);
    }

    public interface PendingRequestsCallback {
        void onRequests(@NonNull List<GameViewApproval> requests);

        void onError(@NonNull String message);
    }

    public interface SimpleCallback {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore db;

    public GameViewApprovalRepository() {
        this(FirebaseFirestore.getInstance());
    }

    GameViewApprovalRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public static String documentId(@NonNull String gameId, @NonNull String userId) {
        return gameId + "_" + userId;
    }

    public static boolean canBypassViewGate(@Nullable GameAuth auth) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return false;
        }
        String uid = user.getUid();
        if (AppUserRoleSession.getInstance().peekRole() == AppUserRoleSession.Role.ADMIN) {
            return true;
        }
        if (auth != null && uid.equals(auth.getCreatorUserId())) {
            return true;
        }
        if (auth != null && uid.equals(auth.getActiveEditorUserId())) {
            return true;
        }
        return false;
    }

    public void seedCreatorApproval(@NonNull String gameId,
                                  @NonNull String creatorUserId,
                                  @NonNull String creatorName) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("userId", creatorUserId);
        data.put("userDisplayName", creatorName);
        data.put("status", GameViewApprovalStatus.APPROVED.getFirestoreValue());
        data.put("requestedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        data.put("lastUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                .document(documentId(gameId, creatorUserId))
                .set(data);
    }

    public void resolveViewAccess(@NonNull String gameId, @Nullable ViewAccessCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onError("You must be signed in to view a game");
            }
            return;
        }

        String uid = user.getUid();
        DocumentReference ref = db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                .document(documentId(gameId, uid));

        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                GameViewApproval approval = snapshot.toObject(GameViewApproval.class);
                GameViewApprovalStatus status = approval != null
                        ? approval.getStatusEnum()
                        : GameViewApprovalStatus.REQUESTED;
                dispatchOutcome(status, callback);
                return;
            }
            createViewRequest(ref, gameId, user, callback);
        }).addOnFailureListener(e -> {
            // Firestore rules often deny get() when the doc does not exist yet (resource is null).
            // Create is allowed separately — attempt to raise the request anyway.
            createViewRequest(ref, gameId, user, callback);
        });
    }

    private void createViewRequest(@NonNull DocumentReference ref,
                                   @NonNull String gameId,
                                   @NonNull FirebaseUser user,
                                   @Nullable ViewAccessCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("userId", user.getUid());
        data.put("userDisplayName", resolveDisplayName(user));
        data.put("status", GameViewApprovalStatus.REQUESTED.getFirestoreValue());
        data.put("requestedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        data.put("lastUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        ref.set(data).addOnSuccessListener(aVoid -> {
            addPendingRequestToGameDoc(gameId, user.getUid(), resolveDisplayName(user));
            if (callback != null) {
                callback.onResult(ViewAccessOutcome.PENDING);
            }
        }).addOnFailureListener(e -> {
            if (callback != null) {
                callback.onError("Failed to request view access. Please try again.");
            }
        });
    }

    public void approveRequest(@NonNull String gameId,
                               @NonNull String userId,
                               @Nullable SimpleCallback callback) {
        updateStatus(gameId, userId, GameViewApprovalStatus.APPROVED, callback);
    }

    public void rejectRequest(@NonNull String gameId,
                              @NonNull String userId,
                              @Nullable SimpleCallback callback) {
        updateStatus(gameId, userId, GameViewApprovalStatus.REJECTED, callback);
    }

    private void updateStatus(@NonNull String gameId,
                              @NonNull String userId,
                              @NonNull GameViewApprovalStatus status,
                              @Nullable SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status.getFirestoreValue());
        updates.put("lastUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                .document(documentId(gameId, userId))
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    syncViewRequestStatusOnGameDoc(gameId, userId, status);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onError("Failed to update view request. Please try again.");
                    }
                });
    }

    @Nullable
    public ListenerRegistration listenPendingRequestsForGame(@NonNull String gameId,
                                                             @NonNull PendingRequestsCallback callback) {
        final List<GameViewApproval>[] fromGameDoc = new List[]{new ArrayList<>()};
        final List<GameViewApproval>[] fromCollection = new List[]{new ArrayList<>()};
        final boolean[] gameDocFailed = {false};
        final boolean[] collectionFailed = {false};
        final String[] creatorUserId = {null};

        Runnable emit = () -> {
            List<GameViewApproval> merged = mergeViewRequests(fromGameDoc[0], fromCollection[0]);
            merged = excludeCreatorViewRequest(merged, creatorUserId[0]);
            if (!merged.isEmpty() || (!gameDocFailed[0] || !collectionFailed[0])) {
                callback.onRequests(merged);
                return;
            }
            callback.onError("Failed to load view requests. Check Firestore rules for gameViewApprovals_v2.");
            callback.onRequests(new ArrayList<>());
        };

        ListenerRegistration gameListener = db.collection(FirestoreCollections.GAMES)
                .document(gameId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        android.util.Log.e("GameViewApproval", "Game doc pending listener: "
                                + error.getMessage());
                        gameDocFailed[0] = true;
                        emit.run();
                        return;
                    }
                    gameDocFailed[0] = false;
                    creatorUserId[0] = snapshot != null ? snapshot.getString("creatorUserId") : null;
                    fromGameDoc[0] = parseViewRequestsFromGameSnapshot(gameId, snapshot);
                    emit.run();
                });

        ListenerRegistration collectionListener = db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                .whereEqualTo("gameId", gameId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.w("GameViewApproval", "Collection pending listener: "
                                + error.getMessage());
                        collectionFailed[0] = true;
                        fromCollection[0] = new ArrayList<>();
                        emit.run();
                        return;
                    }
                    collectionFailed[0] = false;
                    fromCollection[0] = snapshots != null
                            ? filterViewRequestsFromCollection(snapshots.getDocuments(), creatorUserId[0])
                            : new ArrayList<>();
                    emit.run();
                });

        return () -> {
            gameListener.remove();
            collectionListener.remove();
        };
    }

    /**
     * One-shot load; merges {@code games_v2.pendingViewRequests} with collection query when permitted.
     */
    public void fetchPendingRequestsForGame(@NonNull String gameId,
                                            @NonNull PendingRequestsCallback callback) {
        final List<GameViewApproval>[] fromGameDoc = new List[]{new ArrayList<>()};
        final List<GameViewApproval>[] fromCollection = new List[]{new ArrayList<>()};
        final boolean[] gameDocFailed = {false};
        final boolean[] collectionFailed = {false};
        final String[] creatorUserId = {null};
        final int[] pending = {2};

        Runnable finish = () -> {
            if (pending[0] > 0) {
                return;
            }
            List<GameViewApproval> merged = mergeViewRequests(fromGameDoc[0], fromCollection[0]);
            merged = excludeCreatorViewRequest(merged, creatorUserId[0]);
            if (!merged.isEmpty() || (!gameDocFailed[0] || !collectionFailed[0])) {
                callback.onRequests(merged);
                return;
            }
            callback.onError("Failed to load view requests. Check Firestore rules for gameViewApprovals_v2.");
            callback.onRequests(new ArrayList<>());
        };

        db.collection(FirestoreCollections.GAMES)
                .document(gameId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    gameDocFailed[0] = false;
                    creatorUserId[0] = snapshot != null ? snapshot.getString("creatorUserId") : null;
                    fromGameDoc[0] = parseViewRequestsFromGameSnapshot(gameId, snapshot);
                    pending[0]--;
                    finish.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("GameViewApproval", "Game doc pending fetch: " + e.getMessage());
                    gameDocFailed[0] = true;
                    pending[0]--;
                    finish.run();
                });

        db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                .whereEqualTo("gameId", gameId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    collectionFailed[0] = false;
                    fromCollection[0] = filterViewRequestsFromCollection(
                            querySnapshot.getDocuments(), creatorUserId[0]);
                    pending[0]--;
                    finish.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("GameViewApproval", "Collection pending fetch: " + e.getMessage());
                    collectionFailed[0] = true;
                    fromCollection[0] = new ArrayList<>();
                    pending[0]--;
                    finish.run();
                });
    }

    private void addPendingRequestToGameDoc(@NonNull String gameId,
                                            @NonNull String userId,
                                            @NonNull String displayName) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("userDisplayName", displayName);
        entry.put("status", GameViewApprovalStatus.REQUESTED.getFirestoreValue());
        entry.put("requestedAt", FieldValue.serverTimestamp());

        Map<String, Object> update = new HashMap<>();
        update.put(PENDING_VIEW_REQUESTS_FIELD + "." + userId, entry);

        db.collection(FirestoreCollections.GAMES)
                .document(gameId)
                .update(update)
                .addOnFailureListener(e -> android.util.Log.w("GameViewApproval",
                        "Failed to mirror pending request on game doc: " + e.getMessage()));
    }

    private void syncViewRequestStatusOnGameDoc(@NonNull String gameId,
                                                @NonNull String userId,
                                                @NonNull GameViewApprovalStatus status) {
        Map<String, Object> update = new HashMap<>();
        update.put(PENDING_VIEW_REQUESTS_FIELD + "." + userId + ".status",
                status.getFirestoreValue());
        update.put(PENDING_VIEW_REQUESTS_FIELD + "." + userId + ".lastUpdatedAt",
                FieldValue.serverTimestamp());

        db.collection(FirestoreCollections.GAMES)
                .document(gameId)
                .update(update)
                .addOnFailureListener(e -> android.util.Log.w("GameViewApproval",
                        "Failed to sync view request status on game doc: " + e.getMessage()));
    }

    @NonNull
    private static List<GameViewApproval> parseViewRequestsFromGameSnapshot(
            @NonNull String gameId,
            @Nullable DocumentSnapshot snapshot) {
        List<GameViewApproval> list = new ArrayList<>();
        if (snapshot == null || !snapshot.exists()) {
            return list;
        }
        Object raw = snapshot.get(PENDING_VIEW_REQUESTS_FIELD);
        if (!(raw instanceof Map)) {
            return list;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> pendingByUser = (Map<String, Object>) raw;
        for (Map.Entry<String, Object> entry : pendingByUser.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) entry.getValue();
            Object statusObj = data.get("status");
            if (statusObj == null) {
                continue;
            }
            GameViewApproval approval = new GameViewApproval();
            approval.setGameId(gameId);
            approval.setUserId(entry.getKey());
            Object name = data.get("userDisplayName");
            approval.setUserDisplayName(name != null ? String.valueOf(name) : null);
            approval.setStatus(String.valueOf(statusObj));
            Object requestedAt = data.get("requestedAt");
            if (requestedAt instanceof Timestamp) {
                approval.setRequestedAt((Timestamp) requestedAt);
            }
            Object lastUpdatedAt = data.get("lastUpdatedAt");
            if (lastUpdatedAt instanceof Timestamp) {
                approval.setLastUpdatedAt((Timestamp) lastUpdatedAt);
            }
            list.add(approval);
        }
        sortViewRequests(list);
        return list;
    }

    @NonNull
    private static List<GameViewApproval> mergeViewRequests(
            @Nullable List<GameViewApproval> fromGameDoc,
            @Nullable List<GameViewApproval> fromCollection) {
        Map<String, GameViewApproval> byUserId = new HashMap<>();
        if (fromGameDoc != null) {
            for (GameViewApproval approval : fromGameDoc) {
                if (approval != null && approval.getUserId() != null) {
                    byUserId.put(approval.getUserId(), approval);
                }
            }
        }
        if (fromCollection != null) {
            for (GameViewApproval approval : fromCollection) {
                if (approval != null && approval.getUserId() != null) {
                    byUserId.put(approval.getUserId(), approval);
                }
            }
        }
        List<GameViewApproval> merged = new ArrayList<>(byUserId.values());
        sortViewRequests(merged);
        return merged;
    }

    @NonNull
    private static List<GameViewApproval> excludeCreatorViewRequest(
            @NonNull List<GameViewApproval> requests,
            @Nullable String creatorUserId) {
        if (creatorUserId == null || creatorUserId.isEmpty()) {
            return requests;
        }
        List<GameViewApproval> filtered = new ArrayList<>();
        for (GameViewApproval approval : requests) {
            if (approval != null && !creatorUserId.equals(approval.getUserId())) {
                filtered.add(approval);
            }
        }
        return filtered;
    }

    private static void sortViewRequests(@NonNull List<GameViewApproval> list) {
        Collections.sort(list, (a, b) -> {
            Timestamp ta = a.getRequestedAt();
            Timestamp tb = b.getRequestedAt();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return ta.compareTo(tb);
        });
    }

    @NonNull
    private static List<GameViewApproval> filterViewRequestsFromCollection(
            @NonNull List<DocumentSnapshot> documents,
            @Nullable String creatorUserId) {
        List<GameViewApproval> list = new ArrayList<>();
        for (DocumentSnapshot doc : documents) {
            GameViewApproval approval = doc.toObject(GameViewApproval.class);
            if (approval == null || approval.getStatus() == null || approval.getUserId() == null) {
                continue;
            }
            if (creatorUserId != null && creatorUserId.equals(approval.getUserId())) {
                continue;
            }
            list.add(approval);
        }
        sortViewRequests(list);
        return list;
    }

    private static String resolveQueryErrorMessage(@NonNull Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException ffe = (FirebaseFirestoreException) error;
            if (ffe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Permission denied loading view requests. Ensure Firestore rules allow "
                        + "admin and active editor to read gameViewApprovals_v2.";
            }
            if (ffe.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                return "Firestore index required for view requests. Check Logcat for the index URL.";
            }
        }
        String msg = error.getMessage();
        return msg != null ? msg : "Failed to load view requests";
    }

    public interface MyViewApprovalsCallback {
        void onStatuses(@NonNull Map<String, String> statusByGameId);
    }

    @Nullable
    public ListenerRegistration listenMyViewApprovals(@NonNull String userId,
                                                      @NonNull MyViewApprovalsCallback callback) {
        return db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                .whereEqualTo("userId", userId)
                .addSnapshotListener((snapshots, error) -> {
                    Map<String, String> map = new HashMap<>();
                    if (error == null && snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            GameViewApproval approval = doc.toObject(GameViewApproval.class);
                            if (approval != null && approval.getGameId() != null
                                    && approval.getStatus() != null) {
                                map.put(approval.getGameId(), approval.getStatus());
                            }
                        }
                    }
                    callback.onStatuses(map);
                });
    }

    public void deleteAllForGame(@NonNull String gameId) {
        Map<String, Object> clearPending = new HashMap<>();
        clearPending.put(PENDING_VIEW_REQUESTS_FIELD, FieldValue.delete());
        db.collection(FirestoreCollections.GAMES)
                .document(gameId)
                .update(clearPending)
                .addOnFailureListener(e -> android.util.Log.w("GameViewApproval",
                        "Failed to clear pending requests on game doc: " + e.getMessage()));

        db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                .whereEqualTo("gameId", gameId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        return;
                    }
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit();
                });
    }

    private static void dispatchOutcome(@NonNull GameViewApprovalStatus status,
                                        @Nullable ViewAccessCallback callback) {
        if (callback == null) {
            return;
        }
        switch (status) {
            case APPROVED:
                callback.onResult(ViewAccessOutcome.GRANTED);
                break;
            case REJECTED:
                callback.onResult(ViewAccessOutcome.REJECTED);
                break;
            case REQUESTED:
            default:
                callback.onResult(ViewAccessOutcome.PENDING);
                break;
        }
    }

    private static String resolveDisplayName(@NonNull FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim();
        }
        return "User";
    }
}
