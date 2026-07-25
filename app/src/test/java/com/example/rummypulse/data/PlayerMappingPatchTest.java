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
}
