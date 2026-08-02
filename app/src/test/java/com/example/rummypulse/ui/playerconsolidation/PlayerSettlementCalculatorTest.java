package com.example.rummypulse.ui.playerconsolidation;

import static org.junit.Assert.assertEquals;

import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlayerSettlementCalculatorTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a GameItem using the constructor that accepts a players list.
     * creationDateTime is null intentionally — GameItem.calculateAge() catches
     * the parse exception and returns "Unknown", which is safe for unit tests.
     */
    private static GameItem makeGame(String pointValue, String gstPercentage,
                                     String numberOfPlayers, List<Player> players) {
        return new GameItem(
                "g1",           // gameId
                "1234",         // gamePin
                "0",            // totalScore field (unused by the calculator)
                pointValue,
                null,           // creationDateTime — intentionally null, safe
                "Active",       // gameStatus
                numberOfPlayers,
                gstPercentage,
                "0",            // gstAmount
                players
        );
    }

    private static Player player(String name, Integer... scores) {
        return new Player(name, Arrays.asList(scores), null);
    }

    /** Assert all four PlayerSettlement fields in one call. */
    private static void assertSettlement(
            PlayerSettlementCalculator.PlayerSettlement result,
            int expectedPlayerScore,
            double expectedGross,
            double expectedGstPaid,
            double expectedNet) {
        assertEquals("playerScore", expectedPlayerScore, result.playerScore);
        assertEquals("grossAmount", expectedGross, result.grossAmount, 0.001);
        assertEquals("gstPaid",     expectedGstPaid,  result.gstPaid,     0.001);
        assertEquals("netAmount",   expectedNet,      result.netAmount,   0.001);
    }

    // -----------------------------------------------------------------------
    // Guard-clause / boundary tests (return zero)
    // -----------------------------------------------------------------------

    @Test
    public void compute_nullGame_returnsZeroSettlement() {
        Player alice = player("Alice", 10);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(null, alice);

        assertSettlement(result, 0, 0.0, 0.0, 0.0);
    }

    @Test
    public void compute_nullPlayer_returnsZeroSettlement() {
        GameItem game = makeGame("2.0", "10", "3",
                Collections.singletonList(player("Alice", 10)));

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, null);

        assertSettlement(result, 0, 0.0, 0.0, 0.0);
    }

    @Test
    public void compute_emptyPlayersList_returnsZeroSettlement() {
        GameItem game = makeGame("2.0", "10", "3", Collections.emptyList());
        Player alice = player("Alice", 10);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, alice);

        assertSettlement(result, 0, 0.0, 0.0, 0.0);
    }

    // -----------------------------------------------------------------------
    // Calculation tests
    // -----------------------------------------------------------------------

    /**
     * Three players: Alice=10, Bob=20, Charlie=30. pointValue=2, gst=0.
     * totalScore=60, numPlayers=3.
     *
     * Alice (winner, lowest score):
     *   gross = round((60 - 10*3) * 2.0) = round(60.0) = 60
     *   gstPaid = round(60 * 0 / 100) = 0
     *   net = 60
     */
    @Test
    public void compute_winner_positiveGrossNoGst() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "0", "3", players);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, players.get(0)); // Alice

        assertSettlement(result, 10, 60.0, 0.0, 60.0);
    }

    /**
     * Three players: Alice=10, Bob=20, Charlie=30. pointValue=2, gst=10.
     *
     * Charlie (loser, highest score):
     *   gross = round((60 - 30*3) * 2.0) = round(-60.0) = -60
     *   grossAmount <= 0 → gstPaid = 0, net = -60 (GST is never charged on losses)
     */
    @Test
    public void compute_loser_negativeGrossNoGst() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "10", "3", players);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, players.get(2)); // Charlie

        assertSettlement(result, 30, -60.0, 0.0, -60.0);
    }

    /**
     * Alice wins with gst=10%:
     *   gross = 60, gstPaid = round(60 * 10 / 100) = 6, net = 54.
     */
    @Test
    public void compute_winner_gstAppliedOnPositiveGross() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "10", "3", players);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, players.get(0)); // Alice

        assertSettlement(result, 10, 60.0, 6.0, 54.0);
    }

    /**
     * pointValue = "0" → getPointValueAsDouble() = 0.0.
     * grossAmount = round(30 * 0.0) = 0. Not > 0, so gstPaid=0 and net=0.
     */
    @Test
    public void compute_zeroPointValue_allAmountsAreZero() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("0", "10", "3", players);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, players.get(0)); // Alice

        assertSettlement(result, 10, 0.0, 0.0, 0.0);
    }

    /**
     * gstPercentage = null → parseGstPercent returns 0 → no GST deducted.
     * Alice wins: gross=60, gstPaid=0, net=60.
     */
    @Test
    public void compute_nullGstPercentage_treatedAsZeroPercent() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", null, "3", players);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, players.get(0)); // Alice

        assertSettlement(result, 10, 60.0, 0.0, 60.0);
    }

    /**
     * gstPercentage = "" → parseGstPercent returns 0 → no GST deducted.
     */
    @Test
    public void compute_emptyGstPercentage_treatedAsZeroPercent() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "", "3", players);

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, players.get(0)); // Alice

        assertSettlement(result, 10, 60.0, 0.0, 60.0);
    }

    /**
     * numberOfPlayers = "0" → getNumberOfPlayersAsInt() returns 0 (<=0),
     * so the calculator falls back to players.size() = 3.
     * Produces the same result as when numberOfPlayers is explicitly "3".
     */
    @Test
    public void compute_invalidNumberOfPlayers_fallsBackToListSize() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "0", "0", players); // "0" triggers fallback

        PlayerSettlementCalculator.PlayerSettlement result =
                PlayerSettlementCalculator.compute(game, players.get(0)); // Alice

        // numPlayers falls back to players.size()=3: gross = round((60-10*3)*2) = 60
        assertSettlement(result, 10, 60.0, 0.0, 60.0);
    }
}
