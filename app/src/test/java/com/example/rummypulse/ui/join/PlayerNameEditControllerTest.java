package com.example.rummypulse.ui.join;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.data.Player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerNameEditControllerTest {
    @Test
    public void programmaticBindingDoesNotEnqueueRename() {
        PlayerNameEditController controller = new PlayerNameEditController("p1");
        List<String> renames = new ArrayList<>();

        controller.bind(controller::onTextChanged);

        assertFalse(controller.commit(game(), "Restored name", sink(renames)));
        assertTrue(renames.isEmpty());
    }

    @Test
    public void editingOnePlayerEnqueuesOnlyThatStablePlayerId() {
        GameData reordered = game();
        reordered.setPlayers(new ArrayList<>(Arrays.asList(
                GameDataSchema.findPlayer(reordered, "p2"),
                GameDataSchema.findPlayer(reordered, "p1"))));
        GameDataSchema.normalize(reordered);
        PlayerNameEditController first = new PlayerNameEditController("p1");
        PlayerNameEditController second = new PlayerNameEditController("p2");
        List<String> renames = new ArrayList<>();

        first.onTextChanged();
        assertTrue(first.commit(reordered, "  Alpha  ", sink(renames)));
        assertFalse(second.commit(reordered, "Alpha", sink(renames)));

        assertEquals(Arrays.asList("p1:Alpha"), renames);
    }

    @Test
    public void unchangedEmptyAndMappedNamesDoNotEnqueue() {
        GameData game = game();
        List<String> renames = new ArrayList<>();
        PlayerNameEditController unchanged = new PlayerNameEditController("p1");
        PlayerNameEditController empty = new PlayerNameEditController("p1");
        PlayerNameEditController mapped = new PlayerNameEditController("p2");

        unchanged.onTextChanged();
        empty.onTextChanged();
        mapped.onTextChanged();

        assertFalse(unchanged.commit(game, "  Debu ", sink(renames)));
        assertFalse(empty.commit(game, "   ", sink(renames)));
        assertFalse(mapped.commit(game, "Wrong restored name", sink(renames)));
        assertTrue(renames.isEmpty());
    }

    private static PlayerNameEditController.RenameSink sink(List<String> renames) {
        return (playerId, name) -> renames.add(playerId + ":" + name);
    }

    private static GameData game() {
        Player first = player("p1", "Debu", null, 10);
        Player second = player("p2", "Lebu", "u2", 20);
        GameData game = new GameData();
        game.setPlayers(new ArrayList<>(Arrays.asList(first, second)));
        game.setNumPlayers(2);
        GameDataSchema.normalize(game);
        return game;
    }

    private static Player player(String playerId, String name, String userId, int score) {
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setName(name);
        player.setUserId(userId);
        player.setScores(new ArrayList<>(Arrays.asList(score, -1)));
        return player;
    }
}
