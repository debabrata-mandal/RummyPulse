package com.example.rummypulse.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds {@code playerStats_v2/{userId}} updates exclusively for approved games.
 *
 * <p>Normal game synchronization never calls this class. The administrator approval transaction
 * calculates every affected user's delta, updates their statistics, creates the approved-game
 * record and removes the active game atomically.</p>
 */
public final class PlayerStatsRecorder {

    private PlayerStatsRecorder() {
    }

    /** One approved game's change for one user and the reporting period it belongs to. */
    public static final class PeriodDelta {
        public final PlayerStats.Bucket bucket;
        public final String monthKey;
        public final String weekKey;
        public final String displayName;

        PeriodDelta(PlayerStats.Bucket bucket, String monthKey, String weekKey,
                String displayName) {
            this.bucket = bucket;
            this.monthKey = monthKey;
            this.weekKey = weekKey;
            this.displayName = displayName;
        }
    }

    /**
     * Returns one complete statistics delta per linked user in an approved game.
     *
     * <p>The approval transaction deletes the source active game. Consequently, a successfully
     * committed game cannot be approved a second time, while Firestore transaction retries safely
     * reconstruct this map from scratch.</p>
     */
    public static Map<String, PeriodDelta> deltasForApproval(GameData gameData) {
        return deltasForApproval(gameData, new Date());
    }

    static Map<String, PeriodDelta> deltasForApproval(GameData gameData, Date approvedAt) {
        List<Player> players = safePlayers(gameData);
        if (players.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Integer> scores = new ArrayList<>(players.size());
        for (Player player : players) {
            scores.add(player == null ? 0 : player.getTotalScore());
        }
        GamePointsCalculator.Result gamePoints = GamePointsCalculator.calculate(
                scores,
                gameData.getGamePointFactor(),
                gameData.getBoardAdjustmentPercent(),
                gameData.getNumPlayers());

        Date periodInstant = approvedAt == null ? new Date() : approvedAt;
        String monthKey = PlayerStatsKeys.monthKey(periodInstant);
        String weekKey = PlayerStatsKeys.weekKey(periodInstant);
        Map<String, PeriodDelta> deltas = new LinkedHashMap<>();
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            if (player == null || isNullOrEmpty(player.getUserId())) {
                continue;
            }
            if (deltas.containsKey(player.getUserId())) {
                // Defensive: a user should only ever hold one player row in a game.
                continue;
            }
            GamePointsCalculator.PlayerGamePoints result =
                    gamePoints.getPlayerResults().get(index);
            deltas.put(player.getUserId(), new PeriodDelta(
                    new PlayerStats.Bucket(
                            1L,
                            result.getFinalGamePoints() > 0 ? 1L : 0L,
                            result.getFinalGamePoints(),
                            result.getBaseGamePoints(),
                            result.getBoardAdjustmentPoints()),
                    monthKey,
                    weekKey,
                    player.getName()));
        }
        return deltas;
    }

    /** Adds one game's deltas to a transaction-local batch, retaining separate period entries. */
    public static void appendApprovalDeltas(
            Map<String, List<PeriodDelta>> destination,
            Map<String, PeriodDelta> gameDeltas) {
        if (destination == null || gameDeltas == null) {
            return;
        }
        for (Map.Entry<String, PeriodDelta> entry : gameDeltas.entrySet()) {
            destination.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                    .add(entry.getValue());
        }
    }

    /** Builds one user's document after applying all games in the current approval transaction. */
    public static Map<String, Object> buildStatsDocument(
            PlayerStats existing, String userId, List<PeriodDelta> deltas) {
        PlayerStats stats = existing == null ? new PlayerStats() : existing;
        PlayerStats.Bucket allTime = stats.allTimeOrEmpty();
        Map<String, PlayerStats.Bucket> months = copyBuckets(stats.getMonths());
        Map<String, PlayerStats.Bucket> weeks = copyBuckets(stats.getWeeks());
        String displayName = stats.getDisplayName();

        if (deltas != null) {
            for (PeriodDelta delta : deltas) {
                if (delta == null) {
                    continue;
                }
                allTime = add(allTime, delta.bucket);
                mergeInto(months, delta.monthKey, delta.bucket);
                mergeInto(weeks, delta.weekKey, delta.bucket);
                if (!isNullOrEmpty(delta.displayName)) {
                    displayName = delta.displayName;
                }
            }
        }

        Map<String, Object> document = new HashMap<>();
        document.put("schemaVersion", GameDataSchema.CURRENT_VERSION);
        document.put("userId", userId);
        if (!isNullOrEmpty(displayName)) {
            document.put("displayName", displayName);
        }
        document.put("allTime", allTime.toFirestoreMap());
        document.put("months", prune(months, PlayerStats.MONTH_RETENTION));
        document.put("weeks", prune(weeks, PlayerStats.WEEK_RETENTION));
        document.put("updatedAt", FieldValue.serverTimestamp());
        return document;
    }

    public static DocumentReference statsRef(FirebaseFirestore db, String userId) {
        return db.collection(FirestoreCollections.PLAYER_STATS).document(userId);
    }

    public static PlayerStats readStats(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        return snapshot.toObject(PlayerStats.class);
    }

    private static List<Player> safePlayers(GameData gameData) {
        if (gameData == null || gameData.getPlayers() == null) {
            return Collections.emptyList();
        }
        return gameData.getPlayers();
    }

    private static Map<String, PlayerStats.Bucket> copyBuckets(
            Map<String, PlayerStats.Bucket> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static void mergeInto(
            Map<String, PlayerStats.Bucket> buckets, String key, PlayerStats.Bucket delta) {
        if (isNullOrEmpty(key)) {
            return;
        }
        PlayerStats.Bucket current = buckets.get(key);
        buckets.put(key, add(current == null ? new PlayerStats.Bucket() : current, delta));
    }

    /** Keeps only the newest reporting periods and drops empty buckets. */
    private static Map<String, Object> prune(
            Map<String, PlayerStats.Bucket> buckets, int retention) {
        List<String> keys = new ArrayList<>(new TreeSet<>(buckets.keySet()));
        Map<String, Object> result = new HashMap<>();
        int start = Math.max(0, keys.size() - retention);
        for (int index = start; index < keys.size(); index++) {
            PlayerStats.Bucket bucket = buckets.get(keys.get(index));
            if (bucket == null || isZero(bucket)) {
                continue;
            }
            result.put(keys.get(index), bucket.toFirestoreMap());
        }
        return result;
    }

    private static PlayerStats.Bucket add(PlayerStats.Bucket base, PlayerStats.Bucket delta) {
        if (delta == null) {
            return base;
        }
        return new PlayerStats.Bucket(
                base.getGames() + delta.getGames(),
                base.getWins() + delta.getWins(),
                base.getFinalGamePoints() + delta.getFinalGamePoints(),
                base.getBaseGamePoints() + delta.getBaseGamePoints(),
                base.getBoardAdjustmentPoints() + delta.getBoardAdjustmentPoints());
    }

    private static boolean isZero(PlayerStats.Bucket bucket) {
        return bucket.getGames() == 0
                && bucket.getWins() == 0
                && Math.abs(bucket.getFinalGamePoints()) < 0.005
                && Math.abs(bucket.getBaseGamePoints()) < 0.005
                && Math.abs(bucket.getBoardAdjustmentPoints()) < 0.005;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
