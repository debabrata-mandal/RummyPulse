package com.example.rummypulse.data.sync;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.data.Player;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure operation reducer shared by optimistic UI projection and Firestore transactions.
 */
public final class GameOperationProjector {
    private static final Gson GSON = new Gson();

    private GameOperationProjector() {
    }

    public static GameData apply(GameData source, PendingGameOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation is required.");
        }
        return apply(
                source,
                operation.operationType(),
                operation.playerId,
                GSON.fromJson(operation.payloadJson, GameOperationPayload.class));
    }

    public static GameData apply(
            GameData source,
            GameOperationType type,
            String playerId,
            GameOperationPayload payload) {
        GameData result = GameDataCopies.deepCopy(source);
        if (result == null) {
            throw new IllegalArgumentException("Game data is required.");
        }
        GameDataSchema.normalize(result);
        GameOperationPayload safePayload =
                payload == null ? new GameOperationPayload() : payload;
        switch (type) {
            case UPDATE_SCORE:
                applyScores(result, safePayload);
                break;
            case MAP_USER:
                applyMapping(result, playerId, safePayload, false);
                break;
            case UNMAP_USER:
                requirePlayer(result, playerId).setUserId(null);
                break;
            case TRANSFER_MAPPING:
                applyTransfer(result, playerId, safePayload);
                break;
            case SET_PLAYER_ORDER:
                applyOrder(result, safePayload.playerOrder);
                break;
            case RENAME_PLAYER:
                String name = clean(safePayload.name);
                if (name == null) {
                    throw new IllegalArgumentException("Player name is required.");
                }
                requirePlayer(result, playerId).setName(name);
                break;
            case ADD_PLAYER:
                applyAdd(result, safePayload.player);
                break;
            case DELETE_PLAYER:
                applyDelete(result, playerId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported game operation.");
        }
        GameDataSchema.normalize(result);
        result.setNumPlayers(result.getPlayers().size());
        return result;
    }

    public static String findPlayerIdMappedTo(
            GameData data, String userId, String excludingPlayerId) {
        String wanted = clean(userId);
        if (data == null || data.getPlayers() == null || wanted == null) {
            return null;
        }
        for (Player player : data.getPlayers()) {
            if (player != null
                    && wanted.equals(player.getUserId())
                    && (excludingPlayerId == null
                    || !excludingPlayerId.equals(player.getPlayerId()))) {
                return player.getPlayerId();
            }
        }
        return null;
    }

    private static void applyScores(GameData data, GameOperationPayload payload) {
        if (payload.round1Based == null
                || payload.round1Based < 1
                || payload.round1Based > 10
                || payload.scoresByPlayerId == null
                || payload.scoresByPlayerId.isEmpty()) {
            throw new IllegalArgumentException("Round scores are incomplete.");
        }
        for (Map.Entry<String, Integer> entry : payload.scoresByPlayerId.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Scores cannot be negative.");
            }
            Player player = requirePlayer(data, entry.getKey());
            List<Integer> scores = player.getScores();
            if (scores == null) {
                scores = new ArrayList<>();
                player.setScores(scores);
            }
            while (scores.size() < 10) {
                scores.add(-1);
            }
            scores.set(payload.round1Based - 1, entry.getValue());
        }
    }

    private static void applyMapping(
            GameData data,
            String playerId,
            GameOperationPayload payload,
            boolean allowExistingSource) {
        String userId = clean(payload.userId);
        String name = clean(payload.name);
        if (userId == null || name == null) {
            throw new IllegalArgumentException("Player mapping is incomplete.");
        }
        String existing = findPlayerIdMappedTo(data, userId, playerId);
        if (existing != null && !allowExistingSource) {
            throw new IllegalStateException("That user is already linked to another player.");
        }
        Player target = requirePlayer(data, playerId);
        target.setUserId(userId);
        target.setName(name);
    }

    private static void applyTransfer(
            GameData data, String targetPlayerId, GameOperationPayload payload) {
        String sourcePlayerId = clean(payload.fromPlayerId);
        String userId = clean(payload.userId);
        if (sourcePlayerId == null || sourcePlayerId.equals(targetPlayerId)
                || userId == null) {
            throw new IllegalArgumentException("Mapping transfer is incomplete.");
        }
        Player source = requirePlayer(data, sourcePlayerId);
        if (!userId.equals(source.getUserId())) {
            throw new IllegalStateException("The source mapping changed.");
        }
        String other = findPlayerIdMappedTo(data, userId, sourcePlayerId);
        if (other != null && !targetPlayerId.equals(other)) {
            throw new IllegalStateException("That user is already linked to another player.");
        }
        source.setUserId(null);
        applyMapping(data, targetPlayerId, payload, true);
    }

    private static void applyOrder(GameData data, List<String> order) {
        if (order == null || data.getPlayers() == null
                || order.size() != data.getPlayers().size()) {
            throw new IllegalArgumentException("Player order is incomplete.");
        }
        Set<String> expected = new HashSet<>();
        for (Player player : data.getPlayers()) {
            expected.add(player.getPlayerId());
        }
        if (new HashSet<>(order).size() != order.size()
                || !expected.equals(new HashSet<>(order))) {
            throw new IllegalArgumentException(
                    "Player order must contain every player exactly once.");
        }
        List<Player> ordered = new ArrayList<>(order.size());
        for (String orderedPlayerId : order) {
            ordered.add(requirePlayer(data, orderedPlayerId));
        }
        data.setPlayers(ordered);
    }

    private static void applyAdd(GameData data, Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player is required.");
        }
        Player added = GameDataCopies.copyPlayer(player);
        if (clean(added.getPlayerId()) == null) {
            added.setPlayerId(java.util.UUID.randomUUID().toString());
        }
        if (GameDataSchema.findPlayer(data, added.getPlayerId()) != null) {
            return;
        }
        List<Player> players = new ArrayList<>(data.getPlayers());
        players.add(added);
        data.setPlayers(players);
    }

    private static void applyDelete(GameData data, String playerId) {
        int index = GameDataSchema.findPlayerIndex(data, playerId);
        if (index < 0) {
            return;
        }
        List<Player> players = new ArrayList<>(data.getPlayers());
        players.remove(index);
        data.setPlayers(players);
    }

    private static Player requirePlayer(GameData data, String playerId) {
        Player player = GameDataSchema.findPlayer(data, playerId);
        if (player == null) {
            throw new IllegalStateException("The selected player no longer exists.");
        }
        return player;
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
