package com.example.rummypulse.data;

import android.text.TextUtils;
import android.util.Log;

import com.example.rummypulse.ui.playerconsolidation.PlayerSettlementCalculator;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maintains {@code playerStats_v2/{userId}} when a game reaches or leaves {@code Completed}.
 *
 * <p>What a game has already contributed is remembered in a {@code statsApplied} field on its own
 * {@code gameData_v2} document. Recording therefore applies a <em>delta</em> against that record
 * rather than a blind increment, which makes it safe to run repeatedly: a retried sync is a no-op,
 * a post-completion score correction adjusts the totals, and a game dropping back out of
 * {@code Completed} (because a player was added) subtracts itself again.
 *
 * <p>Keeping the record on the game rather than in a side collection means it is deleted with the
 * game at approval, so nothing accumulates. The cost is that approval is the last moment it can be
 * read, which is why the final apply happens inside the approval transaction itself — see
 * {@link #deltasForApproval}.
 *
 * <p>The period a game belongs to is fixed at first recording and stored alongside, so a correction
 * made in October still lands in September's numbers.
 */
public final class PlayerStatsRecorder {

    private static final String TAG = "PlayerStatsRecorder";
    private static final String COMPLETED_STATUS = "Completed";

    /** Field on {@code gameData_v2/{gameId}} holding what this game has already contributed. */
    public static final String APPLIED_FIELD = "statsApplied";
    private static final String APPLIED_BY_USER = "byUser";
    private static final String APPLIED_MONTH_KEY = "monthKey";
    private static final String APPLIED_WEEK_KEY = "weekKey";

    private PlayerStatsRecorder() {
    }

    /** One user's outstanding change, and the period it belongs to. */
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
     * @param detachedUserIds users who were unmapped or removed by the operation being applied and
     *                        so must be reconciled even though they no longer appear as players.
     */
    public static Task<Void> record(
            FirebaseFirestore db,
            String gameId,
            GameData gameData,
            Collection<String> detachedUserIds) {
        return recordInternal(db, gameId, gameData, detachedUserIds).continueWith(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Could not record game stats", task.getException());
            }
            return null;
        });
    }

    private static Task<Void> recordInternal(
            FirebaseFirestore db,
            String gameId,
            GameData gameData,
            Collection<String> detachedUserIds) {
        if (db == null || TextUtils.isEmpty(gameId) || gameData == null) {
            return Tasks.forResult(null);
        }
        DocumentReference dataRef =
                db.collection(FirestoreCollections.GAME_DATA).document(gameId);

        return db.runTransaction(transaction -> {
            DocumentSnapshot dataSnapshot = transaction.get(dataRef);
            if (!dataSnapshot.exists()) {
                // Approved between the operation committing and this running; the approval
                // transaction has already applied whatever was outstanding.
                return null;
            }

            Applied applied = Applied.from(dataSnapshot.get(APPLIED_FIELD));
            Map<String, PeriodDelta> deltas = deltasAgainst(
                    applied, gameData, detachedUserIds, isCompleted(gameData));
            if (deltas.isEmpty()) {
                return null;
            }

            // Every read must precede the first write.
            Map<String, DocumentSnapshot> statsSnapshots = new LinkedHashMap<>();
            for (String userId : deltas.keySet()) {
                statsSnapshots.put(userId, transaction.get(statsRef(db, userId)));
            }

            for (Map.Entry<String, PeriodDelta> entry : deltas.entrySet()) {
                String userId = entry.getKey();
                transaction.set(
                        statsRef(db, userId),
                        buildStatsDocument(
                                readStats(statsSnapshots.get(userId)),
                                userId,
                                Collections.singletonList(entry.getValue())));
            }
            transaction.update(dataRef, APPLIED_FIELD,
                    applied.withDeltasApplied(deltas).toFirestoreMap());
            return null;
        });
    }

    /**
     * The stats still outstanding for a game about to be archived, to be applied inside the
     * approval transaction.
     *
     * <p>Approval deletes {@code gameData_v2}, and with it the record of what the game already
     * contributed, so this is the last point at which a missed completion can be detected. In the
     * normal case the game was recorded when it completed and this returns nothing.
     *
     * <p>Completion is forced rather than derived because an administrative {@code gameStatus} of
     * {@code Approved} would otherwise read as "not completed" and reverse the stored totals.
     *
     * @param gameDataSnapshot the {@code gameData_v2} document read inside the approval transaction
     */
    public static Map<String, PeriodDelta> deltasForApproval(
            DocumentSnapshot gameDataSnapshot, GameData gameData) {
        if (gameDataSnapshot == null || gameData == null) {
            return Collections.emptyMap();
        }
        return deltasAgainst(
                Applied.from(gameDataSnapshot.get(APPLIED_FIELD)), gameData, null, true);
    }

    /**
     * Builds the stats document for one user after applying every outstanding delta. Multiple
     * deltas occur when a user appears in more than one game of an approval batch; they cannot be
     * summed because they may belong to different months.
     */
    public static Map<String, Object> buildStatsDocument(
            PlayerStats existing, String userId, List<PeriodDelta> deltas) {
        PlayerStats stats = existing == null ? new PlayerStats() : existing;
        PlayerStats.Bucket allTime = stats.allTimeOrEmpty();
        Map<String, PlayerStats.Bucket> months = copyBuckets(stats.getMonths());
        Map<String, PlayerStats.Bucket> weeks = copyBuckets(stats.getWeeks());
        String displayName = stats.getDisplayName();

        for (PeriodDelta delta : deltas) {
            allTime = add(allTime, delta.bucket);
            mergeInto(months, delta.monthKey, delta.bucket);
            mergeInto(weeks, delta.weekKey, delta.bucket);
            if (!TextUtils.isEmpty(delta.displayName)) {
                displayName = delta.displayName;
            }
        }

        Map<String, Object> document = new HashMap<>();
        document.put("userId", userId);
        if (!TextUtils.isEmpty(displayName)) {
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

    /**
     * True when the operation being applied could change recorded stats, i.e. the game is entering
     * or leaving {@code Completed}. Ordinary mid-game round saves return false and cost nothing.
     */
    public static boolean affectsStats(String previousStatus, GameData after) {
        return isCompletedStatus(previousStatus) || (after != null && isCompleted(after));
    }

    /**
     * Difference between what each user should have contributed and what the game has already
     * applied for them. Users whose position is unchanged are omitted, so a repeat run writes
     * nothing.
     */
    private static Map<String, PeriodDelta> deltasAgainst(
            Applied applied,
            GameData gameData,
            Collection<String> detachedUserIds,
            boolean completed) {
        Map<String, Contribution> targets = completed
                ? contributionsFor(gameData)
                : Collections.emptyMap();

        Set<String> userIds = new LinkedHashSet<>(targets.keySet());
        userIds.addAll(applied.byUser.keySet());
        for (Player player : safePlayers(gameData)) {
            if (player != null && !TextUtils.isEmpty(player.getUserId())) {
                userIds.add(player.getUserId());
            }
        }
        if (detachedUserIds != null) {
            for (String detached : detachedUserIds) {
                if (!TextUtils.isEmpty(detached)) {
                    userIds.add(detached);
                }
            }
        }

        String monthKey = applied.monthKey;
        String weekKey = applied.weekKey;
        if (TextUtils.isEmpty(monthKey) || TextUtils.isEmpty(weekKey)) {
            Date now = new Date();
            monthKey = PlayerStatsKeys.monthKey(now);
            weekKey = PlayerStatsKeys.weekKey(now);
        }

        Map<String, PeriodDelta> deltas = new LinkedHashMap<>();
        for (String userId : userIds) {
            Contribution target = targets.get(userId);
            PlayerStats.Bucket delta = subtract(
                    target == null ? null : target.bucket, applied.byUser.get(userId));
            if (isZero(delta)) {
                continue;
            }
            deltas.put(userId, new PeriodDelta(
                    delta, monthKey, weekKey, target == null ? null : target.playerName));
        }
        return deltas;
    }

    private static boolean isCompleted(GameData gameData) {
        return isCompletedStatus(gameData.getGameStatus());
    }

    private static boolean isCompletedStatus(String status) {
        return status != null && COMPLETED_STATUS.equalsIgnoreCase(status.trim());
    }

    private static List<Player> safePlayers(GameData gameData) {
        List<Player> players = gameData.getPlayers();
        return players == null ? Collections.emptyList() : players;
    }

    /**
     * One contribution per linked user. A win is receiving a positive net settlement — the same
     * rule the game UI uses when it shows a player as receiving money.
     */
    private static Map<String, Contribution> contributionsFor(GameData gameData) {
        List<Player> players = safePlayers(gameData);
        if (players.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Contribution> byUser = new LinkedHashMap<>();
        for (Player player : players) {
            if (player == null || TextUtils.isEmpty(player.getUserId())) {
                continue;
            }
            if (byUser.containsKey(player.getUserId())) {
                // Defensive: a user should only ever hold one player row in a game.
                Log.w(TAG, "Duplicate mapped user in game; keeping the first row.");
                continue;
            }
            PlayerSettlementCalculator.PlayerSettlement settlement =
                    PlayerSettlementCalculator.compute(gameData, player);
            byUser.put(player.getUserId(), new Contribution(
                    player.getName(),
                    new PlayerStats.Bucket(
                            1L,
                            settlement.netAmount > 0 ? 1L : 0L,
                            settlement.netAmount,
                            settlement.grossAmount,
                            settlement.gstPaid)));
        }
        return byUser;
    }

    private static Map<String, PlayerStats.Bucket> copyBuckets(
            Map<String, PlayerStats.Bucket> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static void mergeInto(
            Map<String, PlayerStats.Bucket> buckets, String key, PlayerStats.Bucket delta) {
        if (TextUtils.isEmpty(key)) {
            return;
        }
        PlayerStats.Bucket current = buckets.get(key);
        buckets.put(key, add(current == null ? new PlayerStats.Bucket() : current, delta));
    }

    /**
     * Keeps only the newest {@code retention} periods. Keys sort lexicographically in chronological
     * order, so keeping the tail keeps the newest.
     */
    private static Map<String, Object> prune(
            Map<String, PlayerStats.Bucket> buckets, int retention) {
        List<String> keys = new ArrayList<>(new TreeSet<>(buckets.keySet()));
        Map<String, Object> result = new HashMap<>();
        int start = Math.max(0, keys.size() - retention);
        for (int index = start; index < keys.size(); index++) {
            PlayerStats.Bucket bucket = buckets.get(keys.get(index));
            // Drop buckets that have been fully reversed back to nothing.
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
                base.getNetAmount() + delta.getNetAmount(),
                base.getGrossAmount() + delta.getGrossAmount(),
                base.getContributionPaid() + delta.getContributionPaid());
    }

    private static PlayerStats.Bucket subtract(
            PlayerStats.Bucket target, PlayerStats.Bucket recorded) {
        PlayerStats.Bucket left = target == null ? new PlayerStats.Bucket() : target;
        PlayerStats.Bucket right = recorded == null ? new PlayerStats.Bucket() : recorded;
        return new PlayerStats.Bucket(
                left.getGames() - right.getGames(),
                left.getWins() - right.getWins(),
                left.getNetAmount() - right.getNetAmount(),
                left.getGrossAmount() - right.getGrossAmount(),
                left.getContributionPaid() - right.getContributionPaid());
    }

    private static boolean isZero(PlayerStats.Bucket bucket) {
        return bucket.getGames() == 0
                && bucket.getWins() == 0
                && Math.abs(bucket.getNetAmount()) < 0.005
                && Math.abs(bucket.getGrossAmount()) < 0.005
                && Math.abs(bucket.getContributionPaid()) < 0.005;
    }

    private static long longOf(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static double doubleOf(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private static final class Contribution {
        final String playerName;
        final PlayerStats.Bucket bucket;

        Contribution(String playerName, PlayerStats.Bucket bucket) {
            this.playerName = playerName;
            this.bucket = bucket;
        }
    }

    /** What a game has already contributed, as stored on its {@code gameData_v2} document. */
    private static final class Applied {
        final Map<String, PlayerStats.Bucket> byUser;
        final String monthKey;
        final String weekKey;

        private Applied(
                Map<String, PlayerStats.Bucket> byUser, String monthKey, String weekKey) {
            this.byUser = byUser;
            this.monthKey = monthKey;
            this.weekKey = weekKey;
        }

        @SuppressWarnings("unchecked")
        static Applied from(Object raw) {
            Map<String, PlayerStats.Bucket> byUser = new LinkedHashMap<>();
            if (!(raw instanceof Map)) {
                return new Applied(byUser, null, null);
            }
            Map<String, Object> map = (Map<String, Object>) raw;
            Object users = map.get(APPLIED_BY_USER);
            if (users instanceof Map) {
                for (Map.Entry<String, Object> entry
                        : ((Map<String, Object>) users).entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        byUser.put(entry.getKey(),
                                toBucket((Map<String, Object>) entry.getValue()));
                    }
                }
            }
            Object month = map.get(APPLIED_MONTH_KEY);
            Object week = map.get(APPLIED_WEEK_KEY);
            return new Applied(
                    byUser,
                    month instanceof String ? (String) month : null,
                    week instanceof String ? (String) week : null);
        }

        private static PlayerStats.Bucket toBucket(Map<String, Object> map) {
            return new PlayerStats.Bucket(
                    longOf(map.get("games")),
                    longOf(map.get("wins")),
                    doubleOf(map.get("netAmount")),
                    doubleOf(map.get("grossAmount")),
                    doubleOf(map.get("contributionPaid")));
        }

        /** The new record after {@code deltas} land, dropping users who no longer contribute. */
        Applied withDeltasApplied(Map<String, PeriodDelta> deltas) {
            Map<String, PlayerStats.Bucket> updated = new LinkedHashMap<>(byUser);
            String month = monthKey;
            String week = weekKey;
            for (Map.Entry<String, PeriodDelta> entry : deltas.entrySet()) {
                PeriodDelta delta = entry.getValue();
                month = delta.monthKey;
                week = delta.weekKey;
                PlayerStats.Bucket current = updated.get(entry.getKey());
                PlayerStats.Bucket next = add(
                        current == null ? new PlayerStats.Bucket() : current, delta.bucket);
                if (isZero(next)) {
                    updated.remove(entry.getKey());
                } else {
                    updated.put(entry.getKey(), next);
                }
            }
            return new Applied(updated, month, week);
        }

        Map<String, Object> toFirestoreMap() {
            Map<String, Object> users = new HashMap<>();
            for (Map.Entry<String, PlayerStats.Bucket> entry : byUser.entrySet()) {
                users.put(entry.getKey(), entry.getValue().toFirestoreMap());
            }
            Map<String, Object> map = new HashMap<>();
            map.put(APPLIED_BY_USER, users);
            if (!TextUtils.isEmpty(monthKey)) {
                map.put(APPLIED_MONTH_KEY, monthKey);
            }
            if (!TextUtils.isEmpty(weekKey)) {
                map.put(APPLIED_WEEK_KEY, weekKey);
            }
            return map;
        }
    }
}
