package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class ScoreRegressionGuardTest {
    @Test(expected = IllegalStateException.class)
    public void metadataWriteCannotEraseScore() {
        GameData before = game(player("a", 10, 20));
        GameData after = game(player("a", 10, -1));
        ScoreRegressionGuard.requireMetadataPreservesScores(before, after);
    }

    @Test(expected = IllegalStateException.class)
    public void roundWriteCannotChangeAnotherRound() {
        GameData before = game(player("a", 10, 20));
        GameData after = game(player("a", 11, 21));
        ScoreRegressionGuard.requireOnlyRoundChanged(
                before, after, 1, Collections.singleton("a"));
    }

    @Test
    public void targetedCorrectionMayReplaceValidScore() {
        GameData before = game(player("a", 10, 20));
        GameData after = game(player("a", 11, 20));
        ScoreRegressionGuard.requireOnlyRoundChanged(
                before, after, 1, Collections.singleton("a"));
        assertEquals(Integer.valueOf(11), after.getPlayers().get(0).getScores().get(0));
    }

    @Test
    public void accountMayBeRemappedWithoutMovingPlayerScores() {
        Player beforeA = player("player-a", 10, 20);
        Player beforeB = player("player-b", 30, 40);
        beforeA.setUserId("user-1");
        Player afterA = player("player-a", 10, 20);
        Player afterB = player("player-b", 30, 40);
        afterB.setUserId("user-1");

        ScoreRegressionGuard.requireMetadataPreservesScores(
                game(beforeA, beforeB), game(afterA, afterB));
    }

    @Test
    public void accountMayBeUnmappedWithoutAffectingScores() {
        Player before = player("player-a", 10, 20);
        before.setUserId("user-1");
        Player after = player("player-a", 10, 20);

        ScoreRegressionGuard.requireMetadataPreservesScores(game(before), game(after));
    }

    private static GameData game(Player... players) {
        GameData data = new GameData();
        data.setPlayers(new ArrayList<>(Arrays.asList(players)));
        data.setNumPlayers(players.length);
        return data;
    }

    private static Player player(String id, Integer... scores) {
        Player player = new Player();
        player.setPlayerId(id);
        player.setName(id);
        player.setScores(new ArrayList<>(Arrays.asList(scores)));
        return player;
    }
}
