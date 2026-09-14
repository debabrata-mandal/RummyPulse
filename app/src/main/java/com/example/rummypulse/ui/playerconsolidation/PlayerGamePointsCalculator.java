package com.example.rummypulse.ui.playerconsolidation;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GamePointsCalculator;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.List;

public final class PlayerGamePointsCalculator {

    private PlayerGamePointsCalculator() {
    }

    public static PlayerGamePoints compute(GameItem game, Player player) {
        if (game == null || player == null) {
            return PlayerGamePoints.zero();
        }
        return compute(
                game.getPlayers(),
                player,
                game.getGamePointFactorAsDouble(),
                parseBoardAdjustmentPercent(game.getBoardAdjustmentPercentage()),
                game.getNumberOfPlayersAsInt());
    }

    /**
     * Same gamePoints math against the canonical {@link GameData}, so dashboard performance totals
     * match the Game Points a player already sees inside the game.
     */
    public static PlayerGamePoints compute(GameData game, Player player) {
        if (game == null || player == null) {
            return PlayerGamePoints.zero();
        }
        return compute(
                game.getPlayers(),
                player,
                game.getGamePointFactor(),
                game.getBoardAdjustmentPercent(),
                game.getNumPlayers());
    }

    private static PlayerGamePoints compute(
            List<Player> players,
            Player player,
            double gamePointFactor,
            double boardAdjustmentPercent,
            int declaredNumPlayers) {
        if (players == null || players.isEmpty()) {
            return PlayerGamePoints.zero();
        }

        List<Integer> playerScores = new ArrayList<>(players.size());
        for (Player p : players) {
            playerScores.add(p != null ? p.getTotalScore() : 0);
        }

        int playerScore = player.getTotalScore();
        GamePointsCalculator.PlayerGamePoints gamePoints = GamePointsCalculator.calculatePlayer(
                playerScores,
                playerScore,
                gamePointFactor,
                boardAdjustmentPercent,
                declaredNumPlayers);

        return new PlayerGamePoints(
                gamePoints.getPlayerScore(),
                gamePoints.getBaseGamePoints(),
                gamePoints.getBoardAdjustmentPoints(),
                gamePoints.getFinalGamePoints());
    }

    private static double parseBoardAdjustmentPercent(String boardAdjustmentPercentage) {
        if (boardAdjustmentPercentage == null || boardAdjustmentPercentage.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(boardAdjustmentPercentage);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static final class PlayerGamePoints {
        public final int playerScore;
        public final double baseGamePoints;
        public final double boardAdjustmentPoints;
        public final double finalGamePoints;

        public PlayerGamePoints(int playerScore, double baseGamePoints, double boardAdjustmentPoints, double finalGamePoints) {
            this.playerScore = playerScore;
            this.baseGamePoints = baseGamePoints;
            this.boardAdjustmentPoints = boardAdjustmentPoints;
            this.finalGamePoints = finalGamePoints;
        }

        static PlayerGamePoints zero() {
            return new PlayerGamePoints(0, 0, 0, 0);
        }
    }
}
