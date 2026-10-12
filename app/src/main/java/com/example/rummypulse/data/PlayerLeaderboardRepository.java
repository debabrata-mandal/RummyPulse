package com.example.rummypulse.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streams every player's pre-aggregated performance so the dashboard can rank them.
 *
 * <p>Each document already carries every reporting period, and ranking happens in
 * {@code Leaderboard} on the client. A cold start seeds the list from Firestore's on-device copy
 * and listens only to documents whose {@code updatedAt} is newer, so it is billed for the players in
 * games approved since the last session rather than one read per player. A full collection listener
 * is used when that copy cannot be trusted, which is also what drops stats of deleted accounts.
 */
public class PlayerLeaderboardRepository {

    private static volatile PlayerLeaderboardRepository instance;

    private final FirebaseFirestore db;
    private final MutableLiveData<List<PlayerStats>> allStats = new MutableLiveData<>();
    private final Map<String, PlayerStats> statsByUserId = new LinkedHashMap<>();
    private ListenerRegistration registration;
    private boolean seeding;
    /** Bumped by {@link #stop()} so a cache read finishing afterwards does not attach a listener. */
    private int generation;

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

    /** Attaches the stats listener, reusing it if one is already running. */
    public void start() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            stop();
            statsByUserId.clear();
            allStats.setValue(new ArrayList<>());
            return;
        }
        if (registration != null || seeding) {
            return;
        }
        if (!DeviceCacheReloadStore.canSeedFromDeviceCache(
                DeviceCacheReloadStore.PLAYER_STATS, System.currentTimeMillis())) {
            attachFullListener();
            return;
        }
        seeding = true;
        int startedGeneration = generation;
        db.collection(FirestoreCollections.PLAYER_STATS).get(Source.CACHE)
                .addOnCompleteListener(task -> {
                    if (startedGeneration != generation) {
                        return;
                    }
                    seeding = false;
                    List<PlayerStats> cached = task.isSuccessful()
                            ? parse(task.getResult())
                            : new ArrayList<>();
                    long newestUpdatedAt = newestUpdatedAtMillis(cached);
                    if (cached.isEmpty() || newestUpdatedAt <= 0L) {
                        attachFullListener();
                        return;
                    }
                    statsByUserId.clear();
                    for (PlayerStats stats : cached) {
                        statsByUserId.put(stats.getUserId(), stats);
                    }
                    publish();
                    attachChangeListener(new Timestamp(
                            new Date(DeviceCachePolicy.deltaSince(newestUpdatedAt))));
                });
    }

    public void stop() {
        generation++;
        seeding = false;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    /** Re-reads every stats document, e.g. after an account and its stats were deleted. */
    public void requireFullReload() {
        DeviceCacheReloadStore.clear(DeviceCacheReloadStore.PLAYER_STATS);
        stop();
        start();
    }

    /** Detaches the stats listener and clears cached values on sign-out. */
    public void clearSessionState() {
        stop();
        statsByUserId.clear();
        DeviceCacheReloadStore.clear(DeviceCacheReloadStore.PLAYER_STATS);
        allStats.postValue(new ArrayList<>());
    }

    private void attachFullListener() {
        registration = db.collection(FirestoreCollections.PLAYER_STATS)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        System.out.println("PlayerLeaderboardRepository: Failed to load stats");
                        return;
                    }
                    if (snapshot == null) {
                        return;
                    }
                    statsByUserId.clear();
                    for (PlayerStats stats : parse(snapshot)) {
                        statsByUserId.put(stats.getUserId(), stats);
                    }
                    publish();
                    if (!snapshot.getMetadata().isFromCache()) {
                        DeviceCacheReloadStore.recordFullReload(
                                DeviceCacheReloadStore.PLAYER_STATS, System.currentTimeMillis());
                    }
                });
    }

    private void attachChangeListener(Timestamp since) {
        registration = db.collection(FirestoreCollections.PLAYER_STATS)
                .whereGreaterThan("updatedAt", since)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        System.out.println("PlayerLeaderboardRepository: Failed to load stat changes");
                        return;
                    }
                    if (snapshot == null || snapshot.getDocumentChanges().isEmpty()) {
                        return;
                    }
                    for (DocumentChange change : snapshot.getDocumentChanges()) {
                        DocumentSnapshot document = change.getDocument();
                        // updatedAt only moves forward, so leaving this query means deletion.
                        if (change.getType() == DocumentChange.Type.REMOVED) {
                            statsByUserId.remove(document.getId());
                            continue;
                        }
                        PlayerStats stats = parse(document);
                        if (stats != null) {
                            statsByUserId.put(stats.getUserId(), stats);
                        }
                    }
                    publish();
                });
    }

    private void publish() {
        allStats.setValue(new ArrayList<>(statsByUserId.values()));
    }

    private static List<PlayerStats> parse(QuerySnapshot snapshot) {
        List<PlayerStats> stats = new ArrayList<>();
        if (snapshot == null) {
            return stats;
        }
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            PlayerStats parsed = parse(document);
            if (parsed != null) {
                stats.add(parsed);
            }
        }
        return stats;
    }

    private static PlayerStats parse(DocumentSnapshot document) {
        PlayerStats parsed = document.toObject(PlayerStats.class);
        if (parsed != null && parsed.getUserId() == null) {
            parsed.setUserId(document.getId());
        }
        return parsed;
    }

    private static long newestUpdatedAtMillis(List<PlayerStats> stats) {
        long newest = 0L;
        for (PlayerStats item : stats) {
            if (item.getUpdatedAt() != null) {
                newest = Math.max(newest, item.getUpdatedAt().toDate().getTime());
            }
        }
        return newest;
    }
}
