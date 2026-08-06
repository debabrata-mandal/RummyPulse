package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class GameIntegrityValidatorTest {
    @Test
    public void roundTenCannotHideEarlierHole() {
        GameData data = game(
                player("A", 1, 2, 3, 4, 5, 6, 7, -1, 9, 10),
                player("B", 1, 2, 3, 4, 5, 6, 7, -1, 9, 10));

        GameIntegrityResult result = GameIntegrityValidator.validate(data);

        assertFalse(result.isComplete());
        assertEquals(8, result.getFirstMissingRound());
        assertTrue(result.hasLaterRoundConflict());
        assertEquals(Arrays.asList(8), result.getMissingRounds());
    }

    @Test
    public void allPlayersNeedAllTenValidScores() {
        GameData complete = game(
                player("A", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                player("B", 9, 8, 7, 6, 5, 4, 3, 2, 1, 0));
        assertTrue(GameIntegrityValidator.validate(complete).isComplete());

        complete.getPlayers().get(1).getScores().set(4, null);
        assertFalse(GameIntegrityValidator.validate(complete).isComplete());
    }

    private static GameData game(Player... players) {
        GameData data = new GameData();
        data.setPlayers(new ArrayList<>(Arrays.asList(players)));
        data.setNumPlayers(players.length);
        return data;
    }

    private static Player player(String name, Integer... scores) {
        Player player = new Player();
        player.setPlayerId(name.toLowerCase());
        player.setName(name);
        player.setScores(new ArrayList<>(Arrays.asList(scores)));
        return player;
    }
}
