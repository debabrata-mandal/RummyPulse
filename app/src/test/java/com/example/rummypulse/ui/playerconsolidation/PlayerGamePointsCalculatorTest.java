package com.example.rummypulse.ui.playerconsolidation;

import static org.junit.Assert.assertEquals;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlayerGamePointsCalculatorTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a GameItem using the constructor that accepts a players list.
     * creationDateTime is null intentionally — GameItem.calculateAge() catches
     * the parse exception and returns "Unknown", which is safe for unit tests.
     */
    private static GameItem makeGame(String gamePointFactor, String boardAdjustmentPercentage,
                                     String numberOfPlayers, List<Player> players) {
        return new GameItem(
                "g1",           // gameId
                "1234",         // gamePin
                "0",            // totalScore field (unused by the calculator)
                gamePointFactor,
                null,           // creationDateTime — intentionally null, safe
                "Active",       // gameStatus
                numberOfPlayers,
                boardAdjustmentPercentage,
                "0",            // boardPoints
                players
        );
    }

    private static Player player(String name, Integer... scores) {
        return new Player(name, Arrays.asList(scores), null);
    }

    /** Assert all four PlayerGamePoints fields in one call. */
    private static void assertGamePoints(
            PlayerGamePointsCalculator.PlayerGamePoints result,
            int expectedPlayerScore,
            double expectedGross,
            double expectedBoardAdjustmentPoints,
            double expectedNet) {
        assertEquals("playerScore", expectedPlayerScore, result.playerScore);
        assertEquals("baseGamePoints", expectedGross, result.baseGamePoints, 0.001);
        assertEquals("boardAdjustmentPoints",     expectedBoardAdjustmentPoints,  result.boardAdjustmentPoints,     0.001);
        assertEquals("finalGamePoints",   expectedNet,      result.finalGamePoints,   0.001);
    }

    // -----------------------------------------------------------------------
    // Guard-clause / boundary tests (return zero)
    // -----------------------------------------------------------------------

    @Test
    public void compute_nullGame_returnsZeroGamePoints() {
        Player alice = player("Alice", 10);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute((GameItem) null, alice);

        assertGamePoints(result, 0, 0.0, 0.0, 0.0);
    }

    @Test
    public void compute_nullGameData_returnsZeroGamePoints() {
        Player alice = player("Alice", 10);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute((GameData) null, alice);

        assertGamePoints(result, 0, 0.0, 0.0, 0.0);
    }

    @Test
    public void compute_nullPlayer_returnsZeroGamePoints() {
        GameItem game = makeGame("2.0", "10", "3",
                Collections.singletonList(player("Alice", 10)));

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, null);

        assertGamePoints(result, 0, 0.0, 0.0, 0.0);
    }

    @Test
    public void compute_emptyPlayersList_returnsZeroGamePoints() {
        GameItem game = makeGame("2.0", "10", "3", Collections.emptyList());
        Player alice = player("Alice", 10);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, alice);

        assertGamePoints(result, 0, 0.0, 0.0, 0.0);
    }

    // -----------------------------------------------------------------------
    // Calculation tests
    // -----------------------------------------------------------------------

    /**
     * Three players: Alice=10, Bob=20, Charlie=30. gamePointFactor=2, boardAdjustment=0.
     * totalScore=60, numPlayers=3.
     *
     * Alice (winner, lowest score):
     *   gross = round((60 - 10*3) * 2.0) = round(60.0) = 60
     *   boardAdjustmentPoints = round(60 * 0 / 100) = 0
     *   net = 60
     */
    @Test
    public void compute_winner_positiveGrossNoBoardAdjustment() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "0", "3", players);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, players.get(0)); // Alice

        assertGamePoints(result, 10, 60.0, 0.0, 60.0);
    }

    /**
     * Three players: Alice=10, Bob=20, Charlie=30. gamePointFactor=2, boardAdjustment=10.
     *
     * Charlie (loser, highest score):
     *   gross = round((60 - 30*3) * 2.0) = round(-60.0) = -60
     *   baseGamePoints <= 0 → boardAdjustmentPoints = 0, final = -60
     */
    @Test
    public void compute_loser_negativeGrossNoBoardAdjustment() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "10", "3", players);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, players.get(2)); // Charlie

        assertGamePoints(result, 30, -60.0, 0.0, -60.0);
    }

    /**
     * Alice wins with boardAdjustment=10%:
     *   gross = 60, boardAdjustmentPoints = round(60 * 10 / 100) = 6, net = 54.
     */
    @Test
    public void compute_winner_boardAdjustmentAppliedOnPositiveGross() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "10", "3", players);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, players.get(0)); // Alice

        assertGamePoints(result, 10, 60.0, 6.0, 54.0);
    }

    /**
     * gamePointFactor = "0" → getGamePointFactorAsDouble() = 0.0.
     * baseGamePoints = round(30 * 0.0) = 0. Not > 0, so boardAdjustmentPoints=0 and net=0.
     */
    @Test
    public void compute_zeroGamePointFactor_allAmountsAreZero() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("0", "10", "3", players);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, players.get(0)); // Alice

        assertGamePoints(result, 10, 0.0, 0.0, 0.0);
    }

    /**
     * boardAdjustmentPercentage = null → parseBoardAdjustmentPercent returns 0.
     * Alice wins: gross=60, boardAdjustmentPoints=0, net=60.
     */
    @Test
    public void compute_nullBoardAdjustmentPercentage_treatedAsZeroPercent() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", null, "3", players);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, players.get(0)); // Alice

        assertGamePoints(result, 10, 60.0, 0.0, 60.0);
    }

    /**
     * boardAdjustmentPercentage = "" → parseBoardAdjustmentPercent returns 0.
     */
    @Test
    public void compute_emptyBoardAdjustmentPercentage_treatedAsZeroPercent() {
        List<Player> players = Arrays.asList(
                player("Alice",   10),
                player("Bob",     20),
                player("Charlie", 30));
        GameItem game = makeGame("2.0", "", "3", players);

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, players.get(0)); // Alice

        assertGamePoints(result, 10, 60.0, 0.0, 60.0);
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

        PlayerGamePointsCalculator.PlayerGamePoints result =
                PlayerGamePointsCalculator.compute(game, players.get(0)); // Alice

        // numPlayers falls back to players.size()=3: gross = round((60-10*3)*2) = 60
        assertGamePoints(result, 10, 60.0, 0.0, 60.0);
    }

    @Test
    public void compute_identityAnonymization_doesNotChangeAnyGamePoints() {
        Player alice = player("Alice", 10, 20);
        alice.setUserId("user-a");
        List<Player> players = Arrays.asList(
                alice,
                player("Bob", 30, 40),
                player("Charlie", 5, 15));
        GameItem game = makeGame("2.0", "10", "3", players);

        PlayerGamePointsCalculator.PlayerGamePoints before =
                PlayerGamePointsCalculator.compute(game, alice);
        alice.setUserId(null);
        alice.setName("Deleted player");
        PlayerGamePointsCalculator.PlayerGamePoints after =
                PlayerGamePointsCalculator.compute(game, alice);

        assertGamePoints(after, before.playerScore, before.baseGamePoints,
                before.boardAdjustmentPoints, before.finalGamePoints);
        assertEquals(3, game.getPlayers().size());
    }
}
