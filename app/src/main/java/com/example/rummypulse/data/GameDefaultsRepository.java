package com.example.rummypulse.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.rummypulse.utils.CurrentUserProfileSession;
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
        return mergeFlag("showDashboardLeaderboard", enabled);
    }

    /** Merge only {@code showDashboardLeaderboardGamePoints} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveShowDashboardLeaderboardGamePoints(boolean enabled) {
        return mergeFlag("showDashboardLeaderboardGamePoints", enabled);
    }

    /** Writes one boolean field, then re-reads so the cache reflects the stored document. */
    private com.google.android.gms.tasks.Task<Void> mergeFlag(String field, boolean enabled) {
        Map<String, Object> map = new HashMap<>();
        map.put("schemaVersion", GameDataSchema.CURRENT_VERSION);
        map.put(field, enabled);
        return saveAndReload(map);
    }

    /** Merge only {@code showLiveGamePoints} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveShowLiveGamePoints(boolean enabled) {
        Map<String, Object> map = new HashMap<>();
        map.put("schemaVersion", GameDataSchema.CURRENT_VERSION);
        map.put("showLiveGamePoints", enabled);
        return saveAndReload(map);
    }

    /** Merge only {@code showDashboardApprovalCounts} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveShowDashboardApprovalCounts(boolean enabled) {
        Map<String, Object> map = new HashMap<>();
        map.put("schemaVersion", GameDataSchema.CURRENT_VERSION);
        map.put("showDashboardApprovalCounts", enabled);
        return saveAndReload(map);
    }

    private com.google.android.gms.tasks.Task<Void> saveAndReload(Map<String, Object> updates) {
        com.google.firebase.firestore.DocumentReference configRef =
                db.collection(COLLECTION).document(DOCUMENT_ID);
        return configRef.get()
                .continueWithTask(readTask -> {
                    if (!readTask.isSuccessful() || readTask.getResult() == null) {
                        Exception error = readTask.getException() != null
                                ? readTask.getException()
                                : new IllegalStateException("Could not load game defaults");
                        return Tasks.forException(error);
                    }
                    Map<String, Object> write = new HashMap<>(updates);
                    write.put("schemaVersion", GameDataSchema.CURRENT_VERSION);
                    if (!readTask.getResult().exists()) {
                        write.putIfAbsent("defaultGamePointFactor",
                                cachedResolved.getDefaultGamePointFactor());
                        write.putIfAbsent("defaultBoardAdjustmentPercent",
                                cachedResolved.getDefaultBoardAdjustmentPercent());
                        write.putIfAbsent("defaultMidGameNewPlayerScoreIncrement",
                                cachedResolved.getDefaultMidGameNewPlayerScoreIncrement());
                    }
                    return configRef.set(write, SetOptions.merge());
                })
                .continueWithTask(writeTask -> {
                    if (!writeTask.isSuccessful()) {
                        Exception error = writeTask.getException() != null
                                ? writeTask.getException()
                                : new IllegalStateException("Could not save game defaults");
                        return Tasks.forException(error);
                    }
                    return configRef.get();
                })
                .continueWithTask(readTask -> {
                    if (!readTask.isSuccessful() || readTask.getResult() == null) {
                        Exception error = readTask.getException() != null
                                ? readTask.getException()
                                : new IllegalStateException("Could not reload saved defaults");
                        return Tasks.forException(error);
                    }
                    applySnapshot(readTask.getResult());
                    return Tasks.forResult((Void) null);
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
        String currentPublicName = CurrentUserProfileSession.getDisplayName();
        final String updatedByName = currentPublicName != null ? currentPublicName : "Player";

        Map<String, Object> map = new HashMap<>();
        map.put("schemaVersion", GameDataSchema.CURRENT_VERSION);
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

        return saveAndReload(map);
    }
}
