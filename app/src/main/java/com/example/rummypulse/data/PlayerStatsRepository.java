package com.example.rummypulse.data;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Exposes the signed-in user's pre-aggregated performance.
 *
 * <p>One document listener serves every reporting period the dashboard offers, so switching between
 * All Time, this month, last month and this week is a purely local operation.
 */
public class PlayerStatsRepository {

    private final FirebaseFirestore db;
    private final MutableLiveData<PlayerStats> statsLiveData = new MutableLiveData<>();
    private ListenerRegistration registration;
    private String listeningForUserId;

    public PlayerStatsRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public LiveData<PlayerStats> getStats() {
        return statsLiveData;
    }

    /** Attaches the listener for the current user, reusing it if already listening. */
    public void start() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            stop();
            statsLiveData.setValue(null);
            return;
        }
        if (registration != null && user.getUid().equals(listeningForUserId)) {
            return;
        }
        stop();
        listeningForUserId = user.getUid();
        registration = db.collection(FirestoreCollections.PLAYER_STATS)
                .document(user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        System.out.println(
                                "PlayerStatsRepository: Failed to load stats: " + error.getMessage());
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        // No games recorded yet; the dashboard renders zeroes.
                        statsLiveData.setValue(emptyFor(listeningForUserId));
                        return;
                    }
                    PlayerStats stats = snapshot.toObject(PlayerStats.class);
                    statsLiveData.setValue(
                            stats == null ? emptyFor(listeningForUserId) : stats);
                });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        listeningForUserId = null;
    }

    @NonNull
    private static PlayerStats emptyFor(String userId) {
        PlayerStats stats = new PlayerStats();
        stats.setUserId(userId);
        return stats;
    }
}
