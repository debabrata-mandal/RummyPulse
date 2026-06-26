package com.example.rummypulse.ui.playerconsolidation;

import androidx.annotation.Nullable;

public class GamePlayerEntry {

    private final String entryId;
    private final String gameId;
    private final String gameName;
    private final String playerName;
    @Nullable
    private final String userId;

    public GamePlayerEntry(String entryId, String gameId, String gameName, String playerName,
                           @Nullable String userId) {
        this.entryId = entryId;
        this.gameId = gameId;
        this.gameName = gameName;
        this.playerName = playerName;
        this.userId = userId;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getGameId() {
        return gameId;
    }

    public String getGameName() {
        return gameName;
    }

    public String getPlayerName() {
        return playerName;
    }

    @Nullable
    public String getUserId() {
        return userId;
    }
}
