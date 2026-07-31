package com.example.rummypulse.ui.playerconsolidation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlayerConsolidationEngineTest {

    @Test
    public void buildInitialGroups_combinesSameUserAcrossGames() {
        List<ConsolidatedPlayerGroup> groups =
                PlayerConsolidationEngine.buildInitialGroups(Arrays.asList(
                        game("game-1", player("Debabrata", "user-1")),
                        game("game-2", player("Deb", "user-1"))));

        assertEquals(1, groups.size());
        assertEquals("Debabrata", groups.get(0).getDisplayName());
        assertEquals(2, groups.get(0).getMembers().size());
        assertEquals("game-1", groups.get(0).getMembers().get(0).getGameId());
        assertEquals("game-2", groups.get(0).getMembers().get(1).getGameId());
    }

    @Test
    public void buildInitialGroups_keepsDifferentUsersSeparateWhenNamesMatch() {
        List<ConsolidatedPlayerGroup> groups =
                PlayerConsolidationEngine.buildInitialGroups(Arrays.asList(
                        game("game-1", player("Sam", "user-1")),
                        game("game-2", player("Sam", "user-2"))));

        assertEquals(2, groups.size());
        assertNotEquals(
                groups.get(0).getMembers().get(0).getUserId(),
                groups.get(1).getMembers().get(0).getUserId());
    }

    @Test
    public void buildInitialGroups_keepsUnmappedMatchingNamesSeparate() {
        List<ConsolidatedPlayerGroup> groups =
                PlayerConsolidationEngine.buildInitialGroups(Arrays.asList(
                        game("game-1", player("Arjun", null)),
                        game("game-2", player("Arjun", null))));

        assertEquals(2, groups.size());
        assertEquals(1, groups.get(0).getMembers().size());
        assertEquals(1, groups.get(1).getMembers().size());
    }

    @Test
    public void refreshGroups_tracksMappedUserAfterPlayerRename() {
        List<ConsolidatedPlayerGroup> initial =
                PlayerConsolidationEngine.buildInitialGroups(Arrays.asList(
                        game("game-1", player("Debabrata", "user-1")),
                        game("game-2", player("Deb", "user-1"))));

        PlayerConsolidationEngine.RefreshResult refreshed =
                PlayerConsolidationEngine.refreshGroupsFromGames(
                        initial,
                        Arrays.asList(
                                game("game-1", player("Debabrata Mandal", "user-1")),
                                game("game-2", player("D. Mandal", "user-1"))));

        assertEquals(1, refreshed.getGroups().size());
        assertEquals(2, refreshed.getGroups().get(0).getMembers().size());
        assertEquals(
                "Debabrata Mandal",
                refreshed.getGroups().get(0).getMembers().get(0).getPlayerName());
        assertEquals(
                "D. Mandal",
                refreshed.getGroups().get(0).getMembers().get(1).getPlayerName());
    }

    private static GameItem game(String gameId, Player... players) {
        GameItem game = new GameItem();
        game.setGameId(gameId);
        game.setPointValue("1");
        game.setGstPercentage("0");
        game.setGstAmount("0");
        game.setPlayers(Arrays.asList(players));
        return game;
    }

    private static Player player(String name, String userId) {
        Player player = new Player(name, Collections.singletonList(10), 1);
        player.setUserId(userId);
        return player;
    }
}
