package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

public class PlayerMappingPatchTest {

    @Test
    public void apply_changesIdentityWithoutChangingScores() {
        Player first = new Player("Player 1", new ArrayList<>(Arrays.asList(10, 25, 0)), 11);
        Player second = new Player("Player 2", new ArrayList<>(Arrays.asList(20, 30, 5)), 12);
        GameData game = new GameData();
        game.setPlayers(new ArrayList<>(Arrays.asList(first, second)));
        game.setNumPlayers(2);

        PlayerMappingPatch.apply(game, 0, "Amit", "user-amit");

        assertEquals("Amit", first.getName());
        assertEquals("user-amit", first.getUserId());
        assertEquals(Arrays.asList(10, 25, 0), first.getScores());
        assertEquals(Arrays.asList(20, 30, 5), second.getScores());
    }

    @Test
    public void findPlayerIndex_survivesBackgroundReorder() {
        Player first = new Player("Player 1", new ArrayList<>(Arrays.asList(10)), 11);
        Player second = new Player("Player 2", new ArrayList<>(Arrays.asList(20)), 12);
        GameData game = new GameData();
        game.setPlayers(new ArrayList<>(Arrays.asList(second, first)));

        int found = PlayerMappingPatch.findPlayerIndex(
                game, 0, "Player 1", 11, null);

        assertEquals(1, found);
    }

    @Test
    public void clear_removesOnlyMappingAndPreservesScores() {
        Player player = new Player(
                "Amit", new ArrayList<>(Arrays.asList(10, 25, 0)), 11);
        player.setUserId("user-amit");
        GameData game = new GameData();
        game.setPlayers(new ArrayList<>(Arrays.asList(player)));

        PlayerMappingPatch.clear(game, 0);

        assertEquals(null, player.getUserId());
        assertEquals("Amit", player.getName());
        assertEquals(Arrays.asList(10, 25, 0), player.getScores());
    }
}
