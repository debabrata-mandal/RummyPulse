package com.example.rummypulse.data;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Durable, idempotent intent to update one round. It contains only player
 * identities and the intended scores, never a complete stale game snapshot.
 */
public final class RoundScorePatch {
    private static final String FORMAT_VERSION = "1";

    private final int round1Based;
    private final boolean correction;
    private final long editGeneration;
    private final List<Entry> entries;

    public RoundScorePatch(int round1Based, boolean correction, long editGeneration,
            List<Entry> entries) {
        if (round1Based < 1 || round1Based > 10) {
            throw new IllegalArgumentException("Round must be between 1 and 10.");
        }
        if (editGeneration <= 0 || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Generation and player scores are required.");
        }
        this.round1Based = round1Based;
        this.correction = correction;
        this.editGeneration = editGeneration;
        this.entries = new ArrayList<>(entries);
    }

    public static RoundScorePatch fromGameData(GameData data, int round1Based,
            boolean correction, long editGeneration) {
        if (data == null || data.getPlayers() == null || data.getPlayers().isEmpty()) {
            throw new IllegalArgumentException("Game data must contain players.");
        }
        List<Entry> entries = new ArrayList<>(data.getPlayers().size());
        for (Player player : data.getPlayers()) {
            if (player == null || player.getScores() == null
                    || player.getScores().size() < round1Based
                    || player.getScores().get(round1Based - 1) == null
                    || player.getScores().get(round1Based - 1) < 0) {
                throw new IllegalArgumentException("Every player needs a score for this round.");
            }
            entries.add(new Entry(identityOf(player), player.getScores().get(round1Based - 1)));
        }
        return new RoundScorePatch(round1Based, correction, editGeneration, entries);
    }

    public static RoundScorePatch forPlayer(GameData data, int round1Based,
            long editGeneration, int playerIndex) {
        if (data == null || data.getPlayers() == null
                || playerIndex < 0 || playerIndex >= data.getPlayers().size()) {
            throw new IllegalArgumentException("The selected player is unavailable.");
        }
        Player player = data.getPlayers().get(playerIndex);
        if (player == null || player.getScores() == null
                || player.getScores().size() < round1Based
                || player.getScores().get(round1Based - 1) == null
                || player.getScores().get(round1Based - 1) < 0) {
            throw new IllegalArgumentException("The selected player needs a valid score.");
        }
        List<Entry> entries = new ArrayList<>(1);
        entries.add(new Entry(
                identityOf(player), player.getScores().get(round1Based - 1)));
        return new RoundScorePatch(round1Based, true, editGeneration, entries);
    }

    /**
     * Coalesces queued changes for the same round. Newer values win for the
     * same player while untouched queued player values remain pending.
     */
    public RoundScorePatch merge(RoundScorePatch newer) {
        if (newer == null || round1Based != newer.round1Based
                || editGeneration != newer.editGeneration) {
            throw new IllegalArgumentException(
                    "Only patches for the same round and edit session can be merged.");
        }
        Map<String, Entry> combined = new LinkedHashMap<>();
        for (Entry entry : entries) {
            combined.put(entry.identity, entry);
        }
        for (Entry entry : newer.entries) {
            combined.put(entry.identity, entry);
        }
        return new RoundScorePatch(
                round1Based,
                correction || newer.correction,
                editGeneration,
                new ArrayList<>(combined.values()));
    }

    public GameData applyToLatest(GameData latest) {
        if (latest == null || latest.getPlayers() == null) {
            throw new IllegalArgumentException("Latest game data is unavailable.");
        }
        GameData patched = GameDataPatchPolicy.copyGameShell(latest);
        List<Player> players = new ArrayList<>(latest.getPlayers().size());
        for (Player player : latest.getPlayers()) {
            players.add(GameDataPatchPolicy.copyPlayer(player));
        }

        Set<Integer> used = new HashSet<>();
        for (Entry entry : entries) {
            int index = findEntryPlayer(entry.identity, players, used);
            if (index < 0) {
                throw new IllegalStateException(
                        "The player list changed. Refresh the game before syncing scores.");
            }
            Player player = players.get(index);
            List<Integer> scores = player.getScores();
            if (scores == null) {
                scores = new ArrayList<>();
                player.setScores(scores);
            }
            while (scores.size() < 10) {
                scores.add(-1);
            }
            scores.set(round1Based - 1, entry.score);
            used.add(index);
        }
        patched.setPlayers(players);
        patched.setNumPlayers(players.size());
        return patched;
    }

    public int getRound1Based() {
        return round1Based;
    }

    public boolean isCorrection() {
        return correction;
    }

    public long getEditGeneration() {
        return editGeneration;
    }

    public String serialize() {
        StringBuilder value = new StringBuilder(FORMAT_VERSION)
                .append('|').append(round1Based)
                .append('|').append(correction ? '1' : '0')
                .append('|').append(editGeneration);
        for (Entry entry : entries) {
            value.append('|')
                    .append(URLEncoder.encode(entry.identity, StandardCharsets.UTF_8))
                    .append(',')
                    .append(entry.score);
        }
        return value.toString();
    }

    public static RoundScorePatch deserialize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Pending round is missing.");
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length < 5 || !FORMAT_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported pending-round format.");
        }
        try {
            int round = Integer.parseInt(parts[1]);
            boolean correction = "1".equals(parts[2]);
            long generation = Long.parseLong(parts[3]);
            List<Entry> entries = new ArrayList<>(parts.length - 4);
            for (int i = 4; i < parts.length; i++) {
                int separator = parts[i].lastIndexOf(',');
                if (separator <= 0) {
                    throw new IllegalArgumentException("Pending player score is invalid.");
                }
                String identity = URLDecoder.decode(
                        parts[i].substring(0, separator), StandardCharsets.UTF_8);
                int score = Integer.parseInt(parts[i].substring(separator + 1));
                entries.add(new Entry(identity, score));
            }
            return new RoundScorePatch(round, correction, generation, entries);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Pending round contains invalid numbers.", error);
        }
    }

    private static int findEntryPlayer(String identity, List<Player> players,
            Set<Integer> used) {
        for (int i = 0; i < players.size(); i++) {
            if (!used.contains(i) && identity.equals(identityOf(players.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static String identityOf(Player player) {
        if (player != null && player.getUserId() != null
                && !player.getUserId().trim().isEmpty()) {
            return "u:" + player.getUserId();
        }
        if (player != null && player.getRandomNumber() != null) {
            return "r:" + player.getRandomNumber();
        }
        if (player != null && player.getName() != null
                && !player.getName().trim().isEmpty()) {
            return "n:" + player.getName();
        }
        throw new IllegalArgumentException("A player has no durable identity.");
    }

    public static final class Entry {
        private final String identity;
        private final int score;

        public Entry(String identity, int score) {
            if (identity == null || identity.trim().isEmpty() || score < 0) {
                throw new IllegalArgumentException("Player identity and score are required.");
            }
            this.identity = identity;
            this.score = score;
        }

        public String getIdentity() {
            return identity;
        }

        public int getScore() {
            return score;
        }
    }
}
