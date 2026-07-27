package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * Converts legacy array-backed game data into the stable schema used by editor operations.
 */
public final class GameDataSchema {
    public static final int CURRENT_VERSION = 2;

    private GameDataSchema() {
    }

    /**
     * Assigns missing immutable IDs and rebuilds the index/order representation.
     * Existing IDs are never changed.
     */
    public static boolean normalize(GameData data) {
        if (data == null) {
            throw new IllegalArgumentException("Game data is required.");
        }
        List<Player> players = data.getPlayers();
        if (players == null) {
            players = new ArrayList<>();
        }
        boolean changed = data.getSchemaVersion() == null
                || data.getSchemaVersion() != CURRENT_VERSION;
        Set<String> usedIds = new HashSet<>();
        Map<String, Player> indexed = new LinkedHashMap<>();
        List<String> order = new ArrayList<>(players.size());
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            Player player = players.get(playerIndex);
            if (player == null) {
                throw new IllegalStateException("The player list contains an empty player.");
            }
            String playerId = clean(player.getPlayerId());
            if (playerId == null) {
                playerId = legacyPlayerId(player, playerIndex);
                player.setPlayerId(playerId);
                changed = true;
            } else if (usedIds.contains(playerId)) {
                throw new IllegalStateException("Duplicate immutable player ID: " + playerId);
            }
            usedIds.add(playerId);
            indexed.put(playerId, player);
            order.add(playerId);
        }
        if (!order.equals(data.getPlayerOrder())
                || data.getPlayersById() == null
                || data.getPlayersById().size() != indexed.size()) {
            changed = true;
        }
        data.setSchemaVersion(CURRENT_VERSION);
        data.setPlayersById(indexed);
        data.setPlayerOrder(order);
        data.setPlayers(new ArrayList<>(players));
        return changed;
    }

    public static Player findPlayer(GameData data, String playerId) {
        String wanted = clean(playerId);
        if (data == null || wanted == null) {
            return null;
        }
        if (data.getPlayersById() != null) {
            Player direct = data.getPlayersById().get(wanted);
            if (direct != null) {
                return direct;
            }
        }
        if (data.getPlayers() != null) {
            for (Player player : data.getPlayers()) {
                if (player != null && wanted.equals(player.getPlayerId())) {
                    return player;
                }
            }
        }
        return null;
    }

    public static int findPlayerIndex(GameData data, String playerId) {
        if (data == null || data.getPlayers() == null) {
            return -1;
        }
        for (int index = 0; index < data.getPlayers().size(); index++) {
            Player player = data.getPlayers().get(index);
            if (player != null && playerId != null && playerId.equals(player.getPlayerId())) {
                return index;
            }
        }
        return -1;
    }

    public static List<String> orderedPlayerIds(GameData data) {
        normalize(data);
        return new ArrayList<>(data.getPlayerOrder());
    }

    public static Map<String, Object> toFirestoreData(GameData data) {
        normalize(data);
        Map<String, Object> clean = new HashMap<>();
        clean.put("schemaVersion", CURRENT_VERSION);
        clean.put("numPlayers", data.getPlayersById().size());
        clean.put("pointValue", data.getPointValue());
        clean.put("gstPercent", data.getGstPercent());
        clean.put("playersById", data.getPlayersById());
        clean.put("playerOrder", data.getPlayerOrder());
        clean.put("version", data.getVersion());
        if (data.getMidGameJoinActiveRound() != null) {
            clean.put("midGameJoinActiveRound", data.getMidGameJoinActiveRound());
        }
        if (data.getMidGameJoinBackfillScore() != null) {
            clean.put("midGameJoinBackfillScore", data.getMidGameJoinBackfillScore());
        }
        return clean;
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String legacyPlayerId(Player player, int originalIndex) {
        String stablePart;
        if (clean(player.getUserId()) != null) {
            stablePart = "user:" + player.getUserId();
        } else if (player.getRandomNumber() != null) {
            stablePart = "random:" + player.getRandomNumber();
        } else {
            stablePart = "name:" + String.valueOf(player.getName())
                    + ":position:" + originalIndex;
        }
        return UUID.nameUUIDFromBytes(
                ("rummypulse-legacy-player:" + stablePart)
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
