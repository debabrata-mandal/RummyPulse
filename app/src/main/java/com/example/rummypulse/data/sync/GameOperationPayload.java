package com.example.rummypulse.data.sync;

import com.example.rummypulse.data.Player;

import java.util.List;
import java.util.Map;

public final class GameOperationPayload {
    public String name;
    public String userId;
    public String userDisplayName;
    public String fromPlayerId;
    public Integer round1Based;
    public Boolean correction;
    public Map<String, Integer> scoresByPlayerId;
    public List<String> playerOrder;
    public Player player;

    public static GameOperationPayload rename(String name) {
        GameOperationPayload payload = new GameOperationPayload();
        payload.name = name;
        return payload;
    }

    public static GameOperationPayload mapping(
            String userId, String userDisplayName, String playerName) {
        GameOperationPayload payload = new GameOperationPayload();
        payload.userId = userId;
        payload.userDisplayName = userDisplayName;
        payload.name = playerName;
        return payload;
    }

    public static GameOperationPayload transfer(
            String fromPlayerId, String userId, String userDisplayName, String playerName) {
        GameOperationPayload payload =
                mapping(userId, userDisplayName, playerName);
        payload.fromPlayerId = fromPlayerId;
        return payload;
    }

    public static GameOperationPayload order(List<String> playerOrder) {
        GameOperationPayload payload = new GameOperationPayload();
        payload.playerOrder = playerOrder;
        return payload;
    }

    public static GameOperationPayload scores(
            int round1Based, Map<String, Integer> scoresByPlayerId) {
        return scores(round1Based, scoresByPlayerId, false);
    }

    public static GameOperationPayload scores(
            int round1Based, Map<String, Integer> scoresByPlayerId, boolean correction) {
        GameOperationPayload payload = new GameOperationPayload();
        payload.round1Based = round1Based;
        payload.scoresByPlayerId = scoresByPlayerId;
        payload.correction = correction;
        return payload;
    }

    public static GameOperationPayload player(Player player) {
        GameOperationPayload payload = new GameOperationPayload();
        payload.player = player;
        return payload;
    }
}
