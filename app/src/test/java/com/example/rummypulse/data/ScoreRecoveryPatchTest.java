package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ScoreRecoveryPatchTest {
    @Test
    public void recoveryFillsOnlyMissingValues() {
        GameData current = game(player("a", -1, 22), player("b", 15, 33));
        Map<String, Integer> history = new LinkedHashMap<>();
        history.put("a", 10);
        history.put("b", 99);

        GameData restored = ScoreRecoveryPatch.restoreMissing(current, 1, history);

        assertEquals(Integer.valueOf(10), restored.getPlayers().get(0).getScores().get(0));
        assertEquals(Integer.valueOf(15), restored.getPlayers().get(1).getScores().get(0));
        assertEquals(Integer.valueOf(22), restored.getPlayers().get(0).getScores().get(1));
    }

    @Test(expected = IllegalStateException.class)
    public void recoveryRejectsChangedRoster() {
        GameData current = game(player("a", -1), player("b", 15));
        Map<String, Integer> history = new LinkedHashMap<>();
        history.put("a", 10);
        ScoreRecoveryPatch.restoreMissing(current, 1, history);
    }

    @Test
    public void recoveryIgnoresPlayerDeletedAfterHistoryWasWritten() {
        GameData current = game(player("a", -1), player("b", 15));
        Map<String, Integer> history = new LinkedHashMap<>();
        history.put("a", 10);
        history.put("b", 15);
        history.put("deleted-player", 40);

        GameData restored = ScoreRecoveryPatch.restoreMissing(current, 1, history);

        assertEquals(2, restored.getPlayers().size());
        assertEquals(Integer.valueOf(10), restored.getPlayers().get(0).getScores().get(0));
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
