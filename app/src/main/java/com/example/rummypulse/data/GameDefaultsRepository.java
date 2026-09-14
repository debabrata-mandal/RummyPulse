package com.example.rummypulse.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton access to {@code gameDefaults_v2/config}.
 * <p>
 * For server-side enforcement when non-admins may write point/increment but not boardAdjustment or
 * display-intermediate, Firestore rules should allow writes only if the caller is
 * {@code appUser_v2/{uid}.role == "admin_user"} or {@code defaultBoardAdjustmentPercent} and
 * {@code showLiveGamePoints} are unchanged on merge updates.
 */
public class GameDefaultsRepository {

    public static final String COLLECTION = FirestoreCollections.GAME_DEFAULTS;
    public static final String DOCUMENT_ID = "config";

    private static volatile GameDefaultsRepository instance;
    private final FirebaseFirestore db;
    private volatile GameDefaults cachedResolved = GameDefaults.resolvedFromFirestoreBean(null);

    private GameDefaultsRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static GameDefaultsRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (GameDefaultsRepository.class) {
                if (instance == null) {
                    instance = new GameDefaultsRepository();
                }
            }
        }
        return instance;
    }

    /** For tests or process death — not normally needed. */
    public static void clearInstanceForTests() {
        synchronized (GameDefaultsRepository.class) {
            instance = null;
        }
    }

    public GameDefaults getCachedResolved() {
        return cachedResolved;
    }

    /** Resets in-memory defaults so the next account does not inherit the previous org settings. */
    public void clearSessionCache() {
        cachedResolved = GameDefaults.resolvedFromFirestoreBean(null);
    }

    public long getMidGameIncrementOrFallback() {
        return cachedResolved.getDefaultMidGameNewPlayerScoreIncrement();
    }

    public boolean isShowLiveGamePointsEnabled() {
        return cachedResolved.isShowLiveGamePoints();
    }

    public boolean isShowDashboardApprovalCountsEnabled() {
        return cachedResolved.isShowDashboardApprovalCounts();
    }

    /** Updates in-memory flag immediately (e.g. when the switch is toggled). */
    public void setShowLiveGamePointsCached(boolean enabled) {
        cachedResolved.setShowLiveGamePoints(enabled);
    }

    public void setShowDashboardApprovalCountsCached(boolean enabled) {
        cachedResolved.setShowDashboardApprovalCounts(enabled);
    }

    public boolean isShowDashboardLeaderboardEnabled() {
        return cachedResolved.isShowDashboardLeaderboard();
    }

    public boolean isShowDashboardLeaderboardGamePointsEnabled() {
        return cachedResolved.isShowDashboardLeaderboardGamePoints();
    }

    /** See {@link GameDefaults#isLeaderboardGamePointsVisible()}: both switches must be on. */
    public boolean isLeaderboardGamePointsVisible() {
        return cachedResolved.isLeaderboardGamePointsVisible();
    }

    public void setShowDashboardLeaderboardCached(boolean enabled) {
        cachedResolved.setShowDashboardLeaderboard(enabled);
    }

    public void setShowDashboardLeaderboardGamePointsCached(boolean enabled) {
        cachedResolved.setShowDashboardLeaderboardGamePoints(enabled);
    }

    /** Merge only {@code showDashboardLeaderboard} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveShowDashboardLeaderboard(boolean enabled) {
        setShowDashboardLeaderboardCached(enabled);
        return mergeFlag("showDashboardLeaderboard", enabled);
    }

    /** Merge only {@code showDashboardLeaderboardGamePoints} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveShowDashboardLeaderboardGamePoints(boolean enabled) {
        setShowDashboardLeaderboardGamePointsCached(enabled);
        return mergeFlag("showDashboardLeaderboardGamePoints", enabled);
    }

    /** Writes one boolean field, then re-reads so the cache reflects the stored document. */
    private com.google.android.gms.tasks.Task<Void> mergeFlag(String field, boolean enabled) {
        Map<String, Object> map = new HashMap<>();
        map.put("schemaVersion", GameDataSchema.CURRENT_VERSION);
        map.put(field, enabled);
        return db.collection(COLLECTION).document(DOCUMENT_ID)
                .set(map, SetOptions.merge())
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        return e != null
                                ? Tasks.forException(e)
                                : Tasks.forException(new IllegalStateException("set failed"));
                    }
                    return db.collection(COLLECTION).document(DOCUMENT_ID).get();
                })
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        applySnapshot((DocumentSnapshot) task.getResult());
                    }
                    return null;
                });
    }

    /** Merge only {@code showLiveGamePoints} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveShowLiveGamePoints(boolean enabled) {
        setShowLiveGamePointsCached(enabled);
        Map<String, Object> map = new HashMap<>();
        map.put("showLiveGamePoints", enabled);
        return db.collection(COLLECTION).document(DOCUMENT_ID)
                .set(map, SetOptions.merge())
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        return e != null ? Tasks.forException(e) : Tasks.forException(new IllegalStateException("set failed"));
                    }
                    return db.collection(COLLECTION).document(DOCUMENT_ID).get();
                })
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        applySnapshot((DocumentSnapshot) task.getResult());
                    }
                    return null;
                });
    }

    /** Merge only {@code showDashboardApprovalCounts} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveShowDashboardApprovalCounts(boolean enabled) {
        setShowDashboardApprovalCountsCached(enabled);
        Map<String, Object> map = new HashMap<>();
        map.put("showDashboardApprovalCounts", enabled);
        return db.collection(COLLECTION).document(DOCUMENT_ID)
                .set(map, SetOptions.merge())
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        return e != null ? Tasks.forException(e) : Tasks.forException(new IllegalStateException("set failed"));
                    }
                    return db.collection(COLLECTION).document(DOCUMENT_ID).get();
                })
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        applySnapshot((DocumentSnapshot) task.getResult());
                    }
                    return null;
                });
    }

    public void refreshFromServer(@Nullable Runnable onComplete) {
        refreshFromServer(onComplete, null);
    }

    /**
     * @param onFailure optional; called when the read fails (e.g. permission denied).
     */
    public void refreshFromServer(@Nullable Runnable onComplete, @Nullable java.util.function.Consumer<Exception> onFailure) {
        db.collection(COLLECTION).document(DOCUMENT_ID)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        applySnapshot(task.getResult());
                    } else {
                        cachedResolved = GameDefaults.resolvedFromFirestoreBean(null);
                        if (onFailure != null && task.getException() != null) {
                            onFailure.accept(task.getException());
                        }
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }

    private void applySnapshot(DocumentSnapshot snapshot) {
        if (snapshot != null && snapshot.exists()) {
            GameDefaults raw = snapshot.toObject(GameDefaults.class);
            if (raw == null) {
                raw = new GameDefaults();
            }
            if (snapshot.contains("showLiveGamePoints")) {
                raw.setShowLiveGamePoints(snapshot.getBoolean("showLiveGamePoints"));
            }
            if (snapshot.contains("showDashboardApprovalCounts")) {
                raw.setShowDashboardApprovalCounts(snapshot.getBoolean("showDashboardApprovalCounts"));
            }
            if (snapshot.contains("showDashboardLeaderboard")) {
                raw.setShowDashboardLeaderboard(snapshot.getBoolean("showDashboardLeaderboard"));
            }
            if (snapshot.contains("showDashboardLeaderboardGamePoints")) {
                raw.setShowDashboardLeaderboardGamePoints(
                        snapshot.getBoolean("showDashboardLeaderboardGamePoints"));
            }
            cachedResolved = GameDefaults.resolvedFromFirestoreBean(raw);
        } else {
            cachedResolved = GameDefaults.resolvedFromFirestoreBean(null);
        }
    }

    /** Apply a realtime snapshot of gameDefaults_v2/config (e.g. from JoinGameActivity listener). */
    public void applyConfigSnapshot(DocumentSnapshot snapshot) {
        applySnapshot(snapshot);
    }

    /**
     * @param boardAdjustmentPercentOrNull when non-null, written as {@code defaultBoardAdjustmentPercent}; when null, that field is omitted from the merge so the server value is preserved (non-admin boardAdjustment saves).
     * @param displayIntermediateOrNull when non-null, written as {@code showLiveGamePoints}; when null, omitted (non-admin saves).
     * @param showDashboardApprovalCountsOrNull when non-null, written as {@code showDashboardApprovalCounts}; when null, omitted.
     */
    public com.google.android.gms.tasks.Task<Void> saveDefaults(double gamePointFactor, long midGameIncrement,
            @Nullable Boolean displayIntermediateOrNull,
            @Nullable Boolean showDashboardApprovalCountsOrNull,
            @Nullable Double boardAdjustmentPercentOrNull) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final String uid = user != null ? user.getUid() : "";
        final String updatedByName;
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            updatedByName = user.getDisplayName();
        } else if (user != null && user.getEmail() != null) {
            updatedByName = user.getEmail();
        } else {
            updatedByName = "";
        }

        Map<String, Object> map = new HashMap<>();
        map.put("defaultGamePointFactor", gamePointFactor);
        if (boardAdjustmentPercentOrNull != null) {
            map.put("defaultBoardAdjustmentPercent", boardAdjustmentPercentOrNull);
        }
        map.put("defaultMidGameNewPlayerScoreIncrement", midGameIncrement);
        if (displayIntermediateOrNull != null) {
            map.put("showLiveGamePoints", displayIntermediateOrNull);
        }
        if (showDashboardApprovalCountsOrNull != null) {
            map.put("showDashboardApprovalCounts", showDashboardApprovalCountsOrNull);
        }
        map.put("updatedAt", FieldValue.serverTimestamp());
        map.put("updatedByUserId", uid);
        map.put("updatedByUserName", updatedByName);

        final double boardAdjustmentForFailurePatch = boardAdjustmentPercentOrNull != null
                ? boardAdjustmentPercentOrNull
                : cachedResolved.getDefaultBoardAdjustmentPercent();
        final boolean displayForFailurePatch = displayIntermediateOrNull != null
                ? displayIntermediateOrNull
                : cachedResolved.isShowLiveGamePoints();
        final boolean countsForFailurePatch = showDashboardApprovalCountsOrNull != null
                ? showDashboardApprovalCountsOrNull
                : cachedResolved.isShowDashboardApprovalCounts();

        // Re-fetch after set so updatedAt (serverTimestamp) is materialized in the snapshot.
        return db.collection(COLLECTION).document(DOCUMENT_ID)
                .set(map, SetOptions.merge())
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        return e != null ? Tasks.forException(e) : Tasks.forException(new IllegalStateException("set failed"));
                    }
                    return db.collection(COLLECTION).document(DOCUMENT_ID).get();
                })
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        applySnapshot((DocumentSnapshot) task.getResult());
                    } else {
                        GameDefaults patch = new GameDefaults();
                        patch.setSchemaVersion(GameDataSchema.CURRENT_VERSION);
                        patch.setDefaultGamePointFactor(gamePointFactor);
                        patch.setDefaultBoardAdjustmentPercent(boardAdjustmentForFailurePatch);
                        patch.setDefaultMidGameNewPlayerScoreIncrement(midGameIncrement);
                        patch.setShowLiveGamePoints(displayForFailurePatch);
                        patch.setShowDashboardApprovalCounts(countsForFailurePatch);
                        patch.setUpdatedByUserId(uid);
                        patch.setUpdatedByUserName(updatedByName);
                        cachedResolved = GameDefaults.resolvedFromFirestoreBean(patch);
                    }
                    return null;
                });
    }
}
