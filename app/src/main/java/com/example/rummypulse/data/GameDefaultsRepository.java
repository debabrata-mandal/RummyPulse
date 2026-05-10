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
 * Singleton access to {@code gameDefaults/config}.
 */
public class GameDefaultsRepository {

    public static final String COLLECTION = "gameDefaults";
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
            cachedResolved = GameDefaults.resolvedFromFirestoreBean(raw);
        } else {
            cachedResolved = GameDefaults.resolvedFromFirestoreBean(null);
        }
    }

    public com.google.android.gms.tasks.Task<Void> saveDefaults(double pointValue, double gstPercent, long midGameIncrement) {
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
        map.put("defaultGstPercent", gstPercent);
        map.put("defaultMidGameNewPlayerScoreIncrement", midGameIncrement);
        map.put("updatedAt", FieldValue.serverTimestamp());
        map.put("updatedByUserId", uid);
        map.put("updatedByUserName", updatedByName);

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
                        patch.setDefaultGstPercent(gstPercent);
                        patch.setDefaultMidGameNewPlayerScoreIncrement(midGameIncrement);
                        patch.setUpdatedByUserId(uid);
                        patch.setUpdatedByUserName(updatedByName);
                        cachedResolved = GameDefaults.resolvedFromFirestoreBean(patch);
                    }
                    return null;
                });
    }
}
