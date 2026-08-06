package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Authoritative validator for game completion and score holes. */
public final class GameIntegrityValidator {
    public static final int ROUND_COUNT = 10;

    private GameIntegrityValidator() {}

    public static GameIntegrityResult validate(GameData data) {
        List<Player> players = data == null ? null : data.getPlayers();
        if (players == null || players.isEmpty()) {
            return new GameIntegrityResult(false, 1, new ArrayList<>(),
                    new ArrayList<>(), allRounds(), false);
        }
        int firstMissing = 0;
        Set<Integer> missingRounds = new LinkedHashSet<>();
        for (int round = 1; round <= ROUND_COUNT; round++) {
            for (Player player : players) {
                if (!hasValidScore(player, round)) {
                    missingRounds.add(round);
                    if (firstMissing == 0) firstMissing = round;
                }
            }
        }
        if (firstMissing == 0) {
            return new GameIntegrityResult(true, 0, new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(), false);
        }
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Player player : players) {
            if (!hasValidScore(player, firstMissing)) {
                ids.add(player == null ? "" : safe(player.getPlayerId()));
                names.add(player == null ? "Unknown player" : displayName(player));
            }
        }
        boolean laterConflict = false;
        for (int round = firstMissing + 1; round <= ROUND_COUNT && !laterConflict; round++) {
            for (Player player : players) {
                if (hasValidScore(player, round)) {
                    laterConflict = true;
                    break;
                }
            }
        }
        return new GameIntegrityResult(false, firstMissing, ids, names,
                new ArrayList<>(missingRounds), laterConflict);
    }

    public static boolean hasValidScore(Player player, int round1Based) {
        if (player == null || round1Based < 1 || round1Based > ROUND_COUNT
                || player.getScores() == null || player.getScores().size() < round1Based) {
            return false;
        }
        Integer score = player.getScores().get(round1Based - 1);
        return score != null && score >= 0;
    }

    private static String displayName(Player player) {
        String name = safe(player.getName());
        return name.isEmpty() ? "Unknown player" : name;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<Integer> allRounds() {
        List<Integer> rounds = new ArrayList<>();
        for (int round = 1; round <= ROUND_COUNT; round++) rounds.add(round);
        return rounds;
    }
}
