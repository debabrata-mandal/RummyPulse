package com.example.rummypulse.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Streams every player's pre-aggregated performance so the dashboard can rank them.
 *
 * <p>One collection listener covers all reporting periods, because each document already carries
 * every bucket. The initial attach costs one read per player who has completed a game; after that
 * only documents that actually change are billed, which is the handful of players in a finished
 * game. Ranking itself happens in {@code Leaderboard} on the client.
 */
public class PlayerLeaderboardRepository {

    private static volatile PlayerLeaderboardRepository instance;

    private final FirebaseFirestore db;
    private final MutableLiveData<List<PlayerStats>> allStats = new MutableLiveData<>();
    private ListenerRegistration registration;

    private PlayerLeaderboardRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Shared across every screen that ranks players, so the dashboard and the player ranking
     * screen attach to one listener instead of each paying a read per player.
     */
    public static PlayerLeaderboardRepository getInstance() {
        if (instance == null) {
            synchronized (PlayerLeaderboardRepository.class) {
                if (instance == null) {
                    instance = new PlayerLeaderboardRepository();
                }
            }
        }
        return instance;
    }

    public LiveData<List<PlayerStats>> getAllStats() {
        return allStats;
    }

    /** Attaches the collection listener, reusing it if one is already running. */
    public void start() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            stop();
            allStats.setValue(new ArrayList<>());
            return;
        }
        if (registration != null) {
            return;
        }
        registration = db.collection(FirestoreCollections.PLAYER_STATS)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        System.out.println("PlayerLeaderboardRepository: Failed to load stats: "
                                + error.getMessage());
                        return;
                    }
                    if (snapshot == null) {
                        return;
                    }
                    List<PlayerStats> stats = new ArrayList<>(snapshot.size());
                    for (QueryDocumentSnapshot document : snapshot) {
                        PlayerStats parsed = document.toObject(PlayerStats.class);
                        if (parsed.getUserId() == null) {
                            parsed.setUserId(document.getId());
                        }
                        stats.add(parsed);
                    }
                    allStats.setValue(stats);
                });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
