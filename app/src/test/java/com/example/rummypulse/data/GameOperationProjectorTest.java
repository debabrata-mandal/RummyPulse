package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
        assertEquals("u2", reordered.getPlayers().get(0).getUserId());
        assertEquals(Integer.valueOf(20), reordered.getPlayers().get(0).getScores().get(0));
        assertEquals("p1", reordered.getPlayers().get(1).getPlayerId());
        assertEquals("u1", reordered.getPlayers().get(1).getUserId());
    }

    @Test
    public void duplicateMapping_isRejectedFromProjectedState() {
        GameData game = game();
        try {
            GameOperationProjector.apply(
                    game,
                    GameOperationType.MAP_USER,
                    "p2",
                    GameOperationPayload.mapping("u1", "Debu", "Debu"));
            fail("Expected duplicate mapping rejection");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "That user is already linked to another player.",
                    expected.getMessage());
        }
    }

    @Test
    public void transfer_movesMappingAtomicallyWithoutChangingScores() {
        GameData transferred = GameOperationProjector.apply(
                game(),
                GameOperationType.TRANSFER_MAPPING,
                "p2",
                GameOperationPayload.transfer("p1", "u1", "Debu", "Debu"));

        assertNull(GameDataSchema.findPlayer(transferred, "p1").getUserId());
        assertEquals("u1", GameDataSchema.findPlayer(transferred, "p2").getUserId());
        assertEquals(
                Integer.valueOf(10),
                GameDataSchema.findPlayer(transferred, "p1").getScores().get(0));
        assertEquals(
                Integer.valueOf(20),
                GameDataSchema.findPlayer(transferred, "p2").getScores().get(0));
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

    private static GameData game() {
        Player first = player("p1", "Debu", "u1", 10);
        Player second = player("p2", "Lebu", "u2", 20);
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
