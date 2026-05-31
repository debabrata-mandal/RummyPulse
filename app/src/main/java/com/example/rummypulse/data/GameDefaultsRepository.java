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
 * For server-side enforcement when non-admins may write point/increment but not contribution or
 * display-intermediate, Firestore rules should allow writes only if the caller is
 * {@code appUser_v2/{uid}.role == "admin_user"} or {@code defaultGstPercent} and
 * {@code displayIntermediateCalculation} are unchanged on merge updates.
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

    public long getMidGameIncrementOrFallback() {
        return cachedResolved.getDefaultMidGameNewPlayerScoreIncrement();
    }

    public boolean isDisplayIntermediateCalculationEnabled() {
        return cachedResolved.isDisplayIntermediateCalculation();
    }

    /** Updates in-memory flag immediately (e.g. when the switch is toggled). */
    public void setDisplayIntermediateCalculationCached(boolean enabled) {
        cachedResolved.setDisplayIntermediateCalculation(enabled);
    }

    /** Merge only {@code displayIntermediateCalculation} to Firestore. */
    public com.google.android.gms.tasks.Task<Void> saveDisplayIntermediateCalculation(boolean enabled) {
        setDisplayIntermediateCalculationCached(enabled);
        Map<String, Object> map = new HashMap<>();
        map.put("displayIntermediateCalculation", enabled);
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
            if (snapshot.contains("displayIntermediateCalculation")) {
                raw.setDisplayIntermediateCalculation(snapshot.getBoolean("displayIntermediateCalculation"));
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
     * @param gstPercentOrNull when non-null, written as {@code defaultGstPercent}; when null, that field is omitted from the merge so the server value is preserved (non-admin contribution saves).
     * @param displayIntermediateOrNull when non-null, written as {@code displayIntermediateCalculation}; when null, omitted (non-admin saves).
     */
    public com.google.android.gms.tasks.Task<Void> saveDefaults(double pointValue, long midGameIncrement,
            @Nullable Boolean displayIntermediateOrNull, @Nullable Double gstPercentOrNull) {
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
        map.put("defaultPointValue", pointValue);
        if (gstPercentOrNull != null) {
            map.put("defaultGstPercent", gstPercentOrNull);
        }
        map.put("defaultMidGameNewPlayerScoreIncrement", midGameIncrement);
        if (displayIntermediateOrNull != null) {
            map.put("displayIntermediateCalculation", displayIntermediateOrNull);
        }
        map.put("updatedAt", FieldValue.serverTimestamp());
        map.put("updatedByUserId", uid);
        map.put("updatedByUserName", updatedByName);

        final double gstForFailurePatch = gstPercentOrNull != null
                ? gstPercentOrNull
                : cachedResolved.getDefaultGstPercent();
        final boolean displayForFailurePatch = displayIntermediateOrNull != null
                ? displayIntermediateOrNull
                : cachedResolved.isDisplayIntermediateCalculation();

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
                        patch.setDefaultPointValue(pointValue);
                        patch.setDefaultGstPercent(gstForFailurePatch);
                        patch.setDefaultMidGameNewPlayerScoreIncrement(midGameIncrement);
                        patch.setDisplayIntermediateCalculation(displayForFailurePatch);
                        patch.setUpdatedByUserId(uid);
                        patch.setUpdatedByUserName(updatedByName);
                        cachedResolved = GameDefaults.resolvedFromFirestoreBean(patch);
                    }
                    return null;
                });
    }
}
