package com.example.rummypulse.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.data.Player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class GameDataCopiesTest {

    // ---------------------------------------------------------------------------
    // Test 1: deepCopy(null) returns null
    // ---------------------------------------------------------------------------

    @Test
    public void deepCopy_nullSource_returnsNull() {
        assertNull(GameDataCopies.deepCopy(null));
    }

    // ---------------------------------------------------------------------------
    // Test 2: deepCopy copies scalar / reference fields into the copy
    // Note: GameDataSchema.normalize() always sets schemaVersion to
    // GameDataSchema.CURRENT_VERSION and does not touch any of the other fields
    // verified here, so assertions remain stable regardless of normalization.
    // ---------------------------------------------------------------------------

    @Test
    public void deepCopy_copiesScalarFields() {
        GameData source = new GameData();
        source.setNumPlayers(4);
        source.setPointValue(2.5);
        source.setGstPercent(18.0);
        source.setLastUpdated(null);          // Timestamp omitted; null is the safe default
        source.setVersion("v1.2");
        source.setGameStatus("Approved");     // Only "Approved"/"Rejected" round-trip via getter
        source.setMidGameJoinActiveRound(3);
        source.setMidGameJoinBackfillScore(75);
        source.setPlayers(new ArrayList<>());

        GameData copy = GameDataCopies.deepCopy(source);

        assertNotNull(copy);
        assertEquals(4, copy.getNumPlayers());
        assertEquals(2.5, copy.getPointValue(), 0.0001);
        assertEquals(18.0, copy.getGstPercent(), 0.0001);
        assertNull(copy.getLastUpdated());
        assertEquals("v1.2", copy.getVersion());
        assertEquals("Approved", copy.getGameStatus());
        assertEquals(Integer.valueOf(3), copy.getMidGameJoinActiveRound());
        assertEquals(Integer.valueOf(75), copy.getMidGameJoinBackfillScore());
        // normalize() always stamps CURRENT_VERSION on the copy
        assertEquals(Integer.valueOf(GameDataSchema.CURRENT_VERSION), copy.getSchemaVersion());
    }

    // ---------------------------------------------------------------------------
    // Test 3: mutating the copy's players list does NOT affect the source list
    // ---------------------------------------------------------------------------

    @Test
    public void deepCopy_playersListIsolatedFromSource() {
        GameData source = new GameData();
        source.setPlayers(new ArrayList<>(Arrays.asList(
                playerWithId("p1", "Alice"),
                playerWithId("p2", "Bob")
        )));

        GameData copy = GameDataCopies.deepCopy(source);

        // Remove a player from the copy's list
        copy.getPlayers().remove(0);

        // The source list must be untouched
        assertEquals(2, source.getPlayers().size());
    }

    // ---------------------------------------------------------------------------
    // Test 4: mutating a copied player's scores list does NOT affect the source
    // ---------------------------------------------------------------------------

    @Test
    public void deepCopy_playerScoresIsolatedFromSource() {
        Player p1 = playerWithId("p1", "Alice");
        p1.setScores(new ArrayList<>(Arrays.asList(10, 20, 30)));

        GameData source = new GameData();
        source.setPlayers(new ArrayList<>(Arrays.asList(p1)));

        GameData copy = GameDataCopies.deepCopy(source);

        // Mutate the copy's first player's score list
        copy.getPlayers().get(0).getScores().add(99);

        // Source player's scores must be unchanged
        assertEquals(3, source.getPlayers().get(0).getScores().size());
        assertEquals(Arrays.asList(10, 20, 30), source.getPlayers().get(0).getScores());
    }

    // ---------------------------------------------------------------------------
    // Test 5: copyPlayer copies all fields; scores list is a distinct object
    // ---------------------------------------------------------------------------

    @Test
    public void copyPlayer_copiesAllFields() {
        Player source = new Player();
        source.setPlayerId("player-1");
        source.setName("Charlie");
        source.setScores(new ArrayList<>(Arrays.asList(5, 15, 25)));
        source.setRandomNumber(42);
        source.setUserId("user-123");
        source.setIsCreator(true);

        Player copy = GameDataCopies.copyPlayer(source);

        assertEquals("player-1", copy.getPlayerId());
        assertEquals("Charlie", copy.getName());
        assertEquals(Arrays.asList(5, 15, 25), copy.getScores());
        assertEquals(Integer.valueOf(42), copy.getRandomNumber());
        assertEquals("user-123", copy.getUserId());
        assertEquals(Boolean.TRUE, copy.getIsCreator());
        // The scores list must be a distinct object so mutation is isolated
        assertNotSame(source.getScores(), copy.getScores());
    }

    // ---------------------------------------------------------------------------
    // Test 6: deepCopy with a null players list in the source produces an empty
    //         (non-null) players list in the copy
    // ---------------------------------------------------------------------------

    @Test
    public void deepCopy_nullPlayersInSource_producesEmptyListInCopy() {
        GameData source = new GameData();
        // players is intentionally left unset (null)

        GameData copy = GameDataCopies.deepCopy(source);

        assertNotNull(copy.getPlayers());
        assertTrue(copy.getPlayers().isEmpty());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static Player playerWithId(String playerId, String name) {
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setName(name);
        player.setScores(new ArrayList<>());
        return player;
    }
}
