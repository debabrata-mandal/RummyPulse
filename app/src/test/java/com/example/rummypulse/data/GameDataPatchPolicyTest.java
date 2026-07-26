package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class GameDataPatchPolicyTest {
    @Test
    public void staleRenamePreservesLatestScores() {
        GameData latest = game(player("Old name", "user-1", 11, 20, 30, 40));
        GameData requested = game(player("New name", "user-1", 11, 20, 30));

        GameData merged = GameDataPatchPolicy.preserveLatestScores(requested, latest);

        assertEquals("New name", merged.getPlayers().get(0).getName());
        assertEquals(Arrays.asList(20, 30, 40), merged.getPlayers().get(0).getScores());
    }

    @Test
    public void reorderKeepsScoresWithPlayerIdentity() {
        Player firstLatest = player("First", "user-1", 11, 10, 20);
        Player secondLatest = player("Second", "user-2", 12, 30, 40);
        GameData latest = game(firstLatest, secondLatest);
        GameData requested = game(
                player("Second", "user-2", 12, 30),
                player("First", "user-1", 11, 10));

        GameData merged = GameDataPatchPolicy.preserveLatestScores(requested, latest);

        assertEquals("Second", merged.getPlayers().get(0).getName());
        assertEquals(Arrays.asList(30, 40), merged.getPlayers().get(0).getScores());
        assertEquals("First", merged.getPlayers().get(1).getName());
        assertEquals(Arrays.asList(10, 20), merged.getPlayers().get(1).getScores());
    }

    private static GameData game(Player... players) {
        GameData data = new GameData();
        data.setPlayers(new ArrayList<>(Arrays.asList(players)));
        data.setNumPlayers(players.length);
        data.setVersion("1");
        return data;
    }

    private static Player player(String name, String userId, int random, Integer... scores) {
        Player player = new Player();
        player.setName(name);
        player.setUserId(userId);
        player.setRandomNumber(random);
        player.setScores(new ArrayList<>(Arrays.asList(scores)));
        return player;
    }
}
