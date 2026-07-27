package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class GameDataSchemaTest {
    @Test
    public void normalize_assignsDeterministicLegacyIdsAndPreservesOrder() {
        GameData first = legacyGame();
        GameData second = legacyGame();

        assertTrue(GameDataSchema.normalize(first));
        assertTrue(GameDataSchema.normalize(second));

        assertEquals(first.getPlayerOrder(), second.getPlayerOrder());
        assertEquals(2, first.getPlayersById().size());
        assertEquals(Integer.valueOf(2), first.getSchemaVersion());
        assertEquals(
                first.getPlayers().get(0).getPlayerId(),
                first.getPlayerOrder().get(0));
    }

    @Test
    public void normalize_neverChangesExistingPlayerId() {
        GameData game = legacyGame();
        game.getPlayers().get(0).setPlayerId("stable-player");

        GameDataSchema.normalize(game);
        assertEquals("stable-player", game.getPlayers().get(0).getPlayerId());
        assertFalse(GameDataSchema.orderedPlayerIds(game).isEmpty());
    }

    @Test
    public void schemaV2MapOnlyGame_calculatesRoundNineWithoutLegacyPlayersArray() {
        Player first = playerWithIdAndScores(
                "p1", "First", 10, 20, 30, 40, 10, 20, 30, 40, -1);
        Player second = playerWithIdAndScores(
                "p2", "Second", 5, 15, 25, 35, 5, 15, 25, 35, -1);
        Map<String, Player> playersById = new LinkedHashMap<>();
        playersById.put("p1", first);
        playersById.put("p2", second);

        GameData game = new GameData();
        game.setSchemaVersion(GameDataSchema.CURRENT_VERSION);
        game.setPlayersById(playersById);
        game.setPlayerOrder(new ArrayList<>(Arrays.asList("p2", "p1")));
        game.setNumPlayers(2);

        assertEquals("R9", game.getGameStatus());
        assertEquals("p2", game.getPlayers().get(0).getPlayerId());
        assertEquals(360, game.getTotalScore());
    }

    private static GameData legacyGame() {
        Player first = player("Player 1", "user-1", 21);
        Player second = player("Player 2", null, 22);
        GameData game = new GameData();
        game.setPlayers(new ArrayList<>(Arrays.asList(first, second)));
        game.setNumPlayers(2);
        return game;
    }

    private static Player player(String name, String userId, int randomNumber) {
        Player player = new Player();
        player.setName(name);
        player.setUserId(userId);
        player.setRandomNumber(randomNumber);
        player.setScores(new ArrayList<>(Arrays.asList(10, -1)));
        return player;
    }

    private static Player playerWithIdAndScores(
            String playerId, String name, Integer... scores) {
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setName(name);
        player.setScores(new ArrayList<>(Arrays.asList(scores)));
        return player;
    }
}
