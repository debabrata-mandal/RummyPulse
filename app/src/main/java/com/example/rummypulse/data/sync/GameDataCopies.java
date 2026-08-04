package com.example.rummypulse.data.sync;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.data.Player;

import java.util.ArrayList;

public final class GameDataCopies {
    private GameDataCopies() {
    }

    public static GameData deepCopy(GameData source) {
        if (source == null) {
            return null;
        }
        GameData copy = new GameData();
        copy.setSchemaVersion(source.getSchemaVersion());
        copy.setNumPlayers(source.getNumPlayers());
        copy.setPointValue(source.getPointValue());
        copy.setGstPercent(source.getGstPercent());
        copy.setLastUpdated(source.getLastUpdated());
        copy.setVersion(source.getVersion());
        copy.setGameStatus(source.getGameStatus());
        copy.setMidGameJoinActiveRound(source.getMidGameJoinActiveRound());
        copy.setMidGameJoinBackfillScore(source.getMidGameJoinBackfillScore());
        ArrayList<Player> players = new ArrayList<>();
        if (source.getPlayers() != null) {
            for (Player player : source.getPlayers()) {
                players.add(copyPlayer(player));
            }
        }
        copy.setPlayers(players);
        GameDataSchema.normalize(copy);
        return copy;
    }

    public static Player copyPlayer(Player source) {
        Player copy = new Player();
        copy.setPlayerId(source.getPlayerId());
        copy.setName(source.getName());
        copy.setScores(source.getScores() == null
                ? new ArrayList<>()
                : new ArrayList<>(source.getScores()));
        copy.setRandomNumber(source.getRandomNumber());
        copy.setUserId(source.getUserId());
        copy.setIsCreator(source.getIsCreator());
        copy.setMidGameJoinActiveRound(source.getMidGameJoinActiveRound());
        return copy;
    }
}
