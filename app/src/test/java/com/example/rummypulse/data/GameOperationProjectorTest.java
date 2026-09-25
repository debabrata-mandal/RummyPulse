package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.example.rummypulse.data.sync.GameOperationPayload;
import com.example.rummypulse.data.sync.GameOperationProjector;
import com.example.rummypulse.data.sync.GameOperationType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class GameOperationProjectorTest {
    @Test
    public void reorder_keepsMappingAndScoresAttachedToPlayerId() {
        GameData game = game();

        GameData reordered = GameOperationProjector.apply(
                game,
                GameOperationType.SET_PLAYER_ORDER,
                null,
                GameOperationPayload.order(Arrays.asList("p2", "p1")));

        assertEquals("p2", reordered.getPlayers().get(0).getPlayerId());
        assertEquals("Lebu", reordered.getPlayers().get(0).getName());
        assertEquals("u2", reordered.getPlayers().get(0).getUserId());
        assertEquals(Integer.valueOf(20), reordered.getPlayers().get(0).getScores().get(0));
        assertEquals(Integer.valueOf(22), reordered.getPlayers().get(0).getRandomNumber());
        assertEquals(Boolean.FALSE, reordered.getPlayers().get(0).getIsCreator());
        assertEquals("p1", reordered.getPlayers().get(1).getPlayerId());
        assertEquals("Debu", reordered.getPlayers().get(1).getName());
        assertEquals("u1", reordered.getPlayers().get(1).getUserId());
        assertEquals(Integer.valueOf(10), reordered.getPlayers().get(1).getScores().get(0));
        assertEquals(Integer.valueOf(11), reordered.getPlayers().get(1).getRandomNumber());
        assertEquals(Boolean.TRUE, reordered.getPlayers().get(1).getIsCreator());
    }

    @Test
    public void duplicateMapping_isRejectedFromProjectedState() {
        GameData game = game();
        try {
            GameOperationProjector.apply(
                    game,
                    GameOperationType.MAP_USER,
                    "p2",
                    GameOperationPayload.mapping("u1", "Debu"));
            fail("Expected duplicate mapping rejection");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "That user is already linked to another player.",
                    expected.getMessage());
        }
    }

    @Test
    public void transferThatWouldUnmapAnotherPlayerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GameOperationProjector.apply(
                game(),
                GameOperationType.TRANSFER_MAPPING,
                "p2",
                GameOperationPayload.transfer("p1", "u1", "Debu")));
    }

    @Test
    public void unmap_clearsUserAndUsesUnknownPlayerName() {
        GameData unmapped = GameOperationProjector.apply(
                game(),
                GameOperationType.UNMAP_USER,
                "p1",
                new GameOperationPayload());

        Player player = GameDataSchema.findPlayer(unmapped, "p1");
        assertEquals(null, player.getUserId());
        assertEquals("Unknown", player.getName());
    }

    @Test
    public void scorePatch_usesPlayerIdAfterReorder() {
        GameData reordered = GameOperationProjector.apply(
                game(),
                GameOperationType.SET_PLAYER_ORDER,
                null,
                GameOperationPayload.order(Arrays.asList("p2", "p1")));
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("p1", 42);

        GameData scored = GameOperationProjector.apply(
                reordered,
                GameOperationType.UPDATE_SCORE,
                null,
                GameOperationPayload.scores(1, scores));

        assertEquals(
                Integer.valueOf(42),
                GameDataSchema.findPlayer(scored, "p1").getScores().get(0));
        assertEquals(
                Integer.valueOf(20),
                GameDataSchema.findPlayer(scored, "p2").getScores().get(0));
    }

    @Test
    public void unmappedPlayer_canBeAddedBeforeAnyScoreExists() {
        GameData game = game();
        for (Player player : game.getPlayers()) {
            player.setScores(new ArrayList<>(Arrays.asList(-1, -1)));
        }
        Player added = player("p3", "Temporary", null, -1);

        GameData updated = GameOperationProjector.apply(
                game,
                GameOperationType.ADD_PLAYER,
                "p3",
                GameOperationPayload.player(added));

        Player stored = GameDataSchema.findPlayer(updated, "p3");
        assertEquals("Unknown", stored.getName());
        assertEquals(null, stored.getUserId());
    }

    @Test
    public void unmappedPlayer_cannotBeAddedAfterScoreEntryStarts() {
        Player added = player("p3", "Temporary", null, -1);

        assertThrows(IllegalStateException.class, () -> GameOperationProjector.apply(
                game(),
                GameOperationType.ADD_PLAYER,
                "p3",
                GameOperationPayload.player(added)));
    }

    @Test
    public void scoreEntry_rejectsAnyUnmappedPlayer() {
        GameData game = game();
        game.getPlayers().get(1).setUserId(null);
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("p1", 42);

        assertThrows(IllegalStateException.class, () -> GameOperationProjector.apply(
                game,
                GameOperationType.UPDATE_SCORE,
                null,
                GameOperationPayload.scores(1, scores)));
    }

    private static GameData game() {
        Player first = player("p1", "Debu", "u1", 10);
        Player second = player("p2", "Lebu", "u2", 20);
        first.setRandomNumber(11);
        first.setIsCreator(true);
        second.setRandomNumber(22);
        second.setIsCreator(false);
        GameData game = new GameData();
        game.setPlayers(new ArrayList<>(Arrays.asList(first, second)));
        game.setNumPlayers(2);
        GameDataSchema.normalize(game);
        return game;
    }

    private static Player player(
            String playerId, String name, String userId, int score) {
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setName(name);
        player.setUserId(userId);
        player.setScores(new ArrayList<>(Arrays.asList(score, -1)));
        return player;
    }
}
