package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class RoundScorePatchTest {
    @Test
    public void patchChangesOnlySelectedRoundOnLatestServerCopy() {
        GameData offline = game(
                player("A", "user-a", 11, 12, 20, 99),
                player("B", "user-b", 12, 8, 30, 88));
        RoundScorePatch patch = RoundScorePatch.fromGameData(offline, 1, true, 4);
        GameData latest = game(
                player("A", "user-a", 11, 40, 21, 31),
                player("B", "user-b", 12, 50, 22, 32));

        GameData merged = patch.applyToLatest(latest);

        assertEquals(Arrays.asList(12, 21, 31),
                merged.getPlayers().get(0).getScores().subList(0, 3));
        assertEquals(Arrays.asList(8, 22, 32),
                merged.getPlayers().get(1).getScores().subList(0, 3));
    }

    @Test
    public void serializedPatchSurvivesRestartAndIsIdempotent() {
        GameData source = game(
                player("A name", "user +/ café", 11, 10, 25),
                player("B", "user-b", 12, 20, 35));
        RoundScorePatch original = RoundScorePatch.fromGameData(source, 2, false, 7);
        RoundScorePatch restored = RoundScorePatch.deserialize(original.serialize());

        GameData once = restored.applyToLatest(source);
        GameData twice = restored.applyToLatest(once);

        assertEquals(7, restored.getEditGeneration());
        assertEquals(2, restored.getRound1Based());
        assertEquals(once.getPlayers().get(0).getScores(),
                twice.getPlayers().get(0).getScores());
        assertEquals(once.getPlayers().get(1).getScores(),
                twice.getPlayers().get(1).getScores());
    }

    @Test
    public void targetedCorrectionDoesNotOverwriteOtherPlayers() {
        GameData local = game(
                player("A", "user-a", 11, 12, 20),
                player("B", "user-b", 12, 30, 40));
        RoundScorePatch patch = RoundScorePatch.forPlayer(local, 1, 5, 0);
        GameData latest = game(
                player("A", "user-a", 11, 10, 20),
                player("B", "user-b", 12, 99, 40));

        GameData merged = patch.applyToLatest(latest);

        assertEquals(Integer.valueOf(12), merged.getPlayers().get(0).getScores().get(0));
        assertEquals(Integer.valueOf(99), merged.getPlayers().get(1).getScores().get(0));
    }

    @Test
    public void queuedCorrectionsForSameRoundAreCoalesced() {
        GameData firstLocal = game(
                player("A", "user-a", 11, 12),
                player("B", "user-b", 12, 30));
        GameData secondLocal = game(
                player("A", "user-a", 11, 12),
                player("B", "user-b", 12, 35));
        RoundScorePatch first =
                RoundScorePatch.forPlayer(firstLocal, 1, 5, 0);
        RoundScorePatch second =
                RoundScorePatch.forPlayer(secondLocal, 1, 5, 1);
        GameData latest = game(
                player("A", "user-a", 11, 10),
                player("B", "user-b", 12, 20));

        GameData merged = first.merge(second).applyToLatest(latest);

        assertEquals(Integer.valueOf(12), merged.getPlayers().get(0).getScores().get(0));
        assertEquals(Integer.valueOf(35), merged.getPlayers().get(1).getScores().get(0));
    }

    @Test(expected = IllegalStateException.class)
    public void missingPlayerKeepsPendingPatchInConflict() {
        GameData source = game(
                player("A", "user-a", 11, 10),
                player("B", "user-b", 12, 20));
        RoundScorePatch patch = RoundScorePatch.fromGameData(source, 1, false, 2);
        GameData changedRoster = game(player("A", "user-a", 11, 10));

        patch.applyToLatest(changedRoster);
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
