package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges a player/metadata edit into the latest server copy without allowing a
 * stale client snapshot to replace scores that were committed later.
 */
public final class GameDataPatchPolicy {
    private GameDataPatchPolicy() {
    }

    public static GameData preserveLatestScores(GameData requested, GameData latest) {
        if (requested == null || requested.getPlayers() == null
                || latest == null || latest.getPlayers() == null) {
            throw new IllegalArgumentException("Game data and players are required.");
        }

        List<Player> mergedPlayers = new ArrayList<>(requested.getPlayers().size());
        Set<Integer> matchedLatest = new HashSet<>();
        for (int requestedIndex = 0;
                requestedIndex < requested.getPlayers().size();
                requestedIndex++) {
            Player requestedPlayer = requested.getPlayers().get(requestedIndex);
            int latestIndex = findMatchingPlayer(
                    requestedPlayer, requestedIndex, latest.getPlayers(), matchedLatest);
            Player merged = copyPlayer(requestedPlayer);
            if (latestIndex >= 0) {
                List<Integer> latestScores = latest.getPlayers().get(latestIndex).getScores();
                merged.setScores(latestScores == null
                        ? new ArrayList<>()
                        : new ArrayList<>(latestScores));
                matchedLatest.add(latestIndex);
            }
            mergedPlayers.add(merged);
        }

        GameData merged = copyGameShell(requested);
        merged.setPlayers(mergedPlayers);
        merged.setNumPlayers(mergedPlayers.size());
        return merged;
    }

    static int findMatchingPlayer(Player requested, int requestedIndex,
            List<Player> latestPlayers, Set<Integer> excluded) {
        for (int i = 0; i < latestPlayers.size(); i++) {
            if (!excluded.contains(i) && sameStableIdentity(requested, latestPlayers.get(i))) {
                return i;
            }
        }
        for (int i = 0; i < latestPlayers.size(); i++) {
            if (!excluded.contains(i) && sameName(requested, latestPlayers.get(i))) {
                return i;
            }
        }
        if (requestedIndex < latestPlayers.size()
                && !excluded.contains(requestedIndex)
                && !hasStableIdentity(requested)
                && !hasStableIdentity(latestPlayers.get(requestedIndex))) {
            return requestedIndex;
        }
        return -1;
    }

    static boolean sameStableIdentity(Player left, Player right) {
        if (left == null || right == null) {
            return false;
        }
        if (!isBlank(left.getPlayerId()) && left.getPlayerId().equals(right.getPlayerId())) {
            return true;
        }
        if (!isBlank(left.getUserId()) && left.getUserId().equals(right.getUserId())) {
            return true;
        }
        return left.getRandomNumber() != null
                && left.getRandomNumber().equals(right.getRandomNumber());
    }

    static boolean hasStableIdentity(Player player) {
        return player != null
                && (!isBlank(player.getPlayerId())
                || !isBlank(player.getUserId())
                || player.getRandomNumber() != null);
    }

    private static boolean sameName(Player left, Player right) {
        return left != null && right != null
                && left.getName() != null
                && left.getName().equals(right.getName());
    }

    static Player copyPlayer(Player original) {
        Player copy = new Player();
        copy.setPlayerId(original.getPlayerId());
        copy.setName(original.getName());
        copy.setScores(original.getScores() == null
                ? new ArrayList<>()
                : new ArrayList<>(original.getScores()));
        copy.setRandomNumber(original.getRandomNumber());
        copy.setUserId(original.getUserId());
        copy.setIsCreator(original.getIsCreator());
        return copy;
    }

    static GameData copyGameShell(GameData original) {
        GameData copy = new GameData();
        copy.setSchemaVersion(original.getSchemaVersion());
        copy.setNumPlayers(original.getNumPlayers());
        copy.setPointValue(original.getPointValue());
        copy.setGstPercent(original.getGstPercent());
        copy.setLastUpdated(original.getLastUpdated());
        copy.setVersion(original.getVersion());
        copy.setGameStatus(original.getGameStatus());
        copy.setMidGameJoinActiveRound(original.getMidGameJoinActiveRound());
        copy.setMidGameJoinBackfillScore(original.getMidGameJoinBackfillScore());
        return copy;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
