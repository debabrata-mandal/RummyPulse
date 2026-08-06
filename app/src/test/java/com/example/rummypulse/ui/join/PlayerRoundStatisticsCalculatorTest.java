package com.example.rummypulse.ui.join;

import static org.junit.Assert.assertEquals;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.Player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class PlayerRoundStatisticsCalculatorTest {
    @Test
    public void calculate_usesExactCategoryBoundaries() {
        Player player = player(null, null, -1, 0, 1, 39, 40, 41, 80, 81, 120);

        PlayerRoundStatistics result = PlayerRoundStatisticsCalculator.calculate(player);

        assertEquals(1, result.getMadeGameCount());
        assertEquals(1, result.getPackedCount());
        assertEquals(2, result.getFullHandCount());
    }

    @Test
    public void calculate_countsRepeatedQualifyingRoundsAndIgnoresMissingScores() {
        Player player = player(null, 0, 0, 40, 40, 81, 99, -1, null);

        PlayerRoundStatistics result = PlayerRoundStatisticsCalculator.calculate(player);

        assertEquals(2, result.getMadeGameCount());
        assertEquals(2, result.getPackedCount());
        assertEquals(2, result.getFullHandCount());
    }

    @Test
    public void calculate_excludesMidGameCatchUpScores() {
        Player player = player(4, 1, 1, 601, 0, 40, 81, -1);

        PlayerRoundStatistics result = PlayerRoundStatisticsCalculator.calculate(player);

        assertEquals(1, result.getMadeGameCount());
        assertEquals(1, result.getPackedCount());
        assertEquals(1, result.getFullHandCount());
    }

    @Test
    public void calculate_excludesRoundOneBackfillAndPostCompletionJoin() {
        Player roundOneJoin = player(1, 601, 0, 40, 81);
        Player completedJoin = player(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 900);

        PlayerRoundStatistics roundOneResult =
                PlayerRoundStatisticsCalculator.calculate(roundOneJoin);
        PlayerRoundStatistics completedResult =
                PlayerRoundStatisticsCalculator.calculate(completedJoin);

        assertEquals(1, roundOneResult.getMadeGameCount());
        assertEquals(1, roundOneResult.getPackedCount());
        assertEquals(1, roundOneResult.getFullHandCount());
        assertEquals(0, completedResult.getMadeGameCount());
        assertEquals(0, completedResult.getPackedCount());
        assertEquals(0, completedResult.getFullHandCount());
    }

    @Test
    public void calculate_usesGameCatchUpMetadataForLegacyPlayer() {
        Player legacyPlayer = player(null, 1, 451, 15, 0, 40, 81, -1);
        GameData gameData = new GameData();
        gameData.setMidGameJoinActiveRound(3);
        gameData.setMidGameJoinBackfillScore(451);

        PlayerRoundStatistics result =
                PlayerRoundStatisticsCalculator.calculate(legacyPlayer, gameData);

        assertEquals(1, result.getMadeGameCount());
        assertEquals(1, result.getPackedCount());
        assertEquals(1, result.getFullHandCount());
    }

    @Test
    public void calculate_recognizesLegacyCatchUpPrefixWithoutMetadata() {
        Player legacyPlayer = player(null, 1, 451, 15, 0, 40, 81, -1);

        PlayerRoundStatistics result =
                PlayerRoundStatisticsCalculator.calculate(legacyPlayer);

        assertEquals(1, result.getMadeGameCount());
        assertEquals(1, result.getPackedCount());
        assertEquals(1, result.getFullHandCount());
    }

    @Test
    public void calculate_handlesMissingPlayerAndScores() {
        Player withoutScores = new Player();

        PlayerRoundStatistics nullResult = PlayerRoundStatisticsCalculator.calculate(null);
        PlayerRoundStatistics emptyResult =
                PlayerRoundStatisticsCalculator.calculate(withoutScores);

        assertEquals(0, nullResult.getMadeGameCount());
        assertEquals(0, nullResult.getPackedCount());
        assertEquals(0, nullResult.getFullHandCount());
        assertEquals(0, emptyResult.getMadeGameCount());
        assertEquals(0, emptyResult.getPackedCount());
        assertEquals(0, emptyResult.getFullHandCount());
    }

    private static Player player(Integer joinRound, Integer... scores) {
        Player player = new Player();
        player.setScores(new ArrayList<>(Arrays.asList(scores)));
        player.setMidGameJoinActiveRound(joinRound);
        return player;
    }
}
