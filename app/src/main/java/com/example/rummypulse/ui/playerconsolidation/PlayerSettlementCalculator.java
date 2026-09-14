package com.example.rummypulse.ui.playerconsolidation;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GamePointsCalculator;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSettlementCalculator {

    private PlayerSettlementCalculator() {
    }

    public static PlayerSettlement compute(GameItem game, Player player) {
        if (game == null || player == null) {
            return PlayerSettlement.zero();
        }
        return compute(
                game.getPlayers(),
                player,
                game.getPointValueAsDouble(),
                parseGstPercent(game.getGstPercentage()),
                game.getNumberOfPlayersAsInt());
    }

    /**
     * Same settlement math against the canonical {@link GameData}, so dashboard performance totals
     * match the amounts a player already sees inside the game.
     */
    public static PlayerSettlement compute(GameData game, Player player) {
        if (game == null || player == null) {
            return PlayerSettlement.zero();
        }
        return compute(
                game.getPlayers(),
                player,
                game.getPointValue(),
                game.getGstPercent(),
                game.getNumPlayers());
    }

    private static PlayerSettlement compute(
            List<Player> players,
            Player player,
            double pointValue,
            double gstPercent,
            int declaredNumPlayers) {
        if (players == null || players.isEmpty()) {
            return PlayerSettlement.zero();
        }

        List<Integer> playerScores = new ArrayList<>(players.size());
        for (Player p : players) {
            playerScores.add(p != null ? p.getTotalScore() : 0);
        }

        int playerScore = player.getTotalScore();
        GamePointsCalculator.PlayerGamePoints gamePoints = GamePointsCalculator.calculatePlayer(
                playerScores,
                playerScore,
                pointValue,
                gstPercent,
                declaredNumPlayers);

        // Keep the current API stable until the UI and Firestore schema move to Game Points in
        // later parts of the migration.
        return new PlayerSettlement(
                gamePoints.getPlayerScore(),
                gamePoints.getBaseGamePoints(),
                gamePoints.getBoardAdjustmentPoints(),
                gamePoints.getFinalGamePoints());
    }

    private static double parseGstPercent(String gstPercentage) {
        if (gstPercentage == null || gstPercentage.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(gstPercentage);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static final class PlayerSettlement {
        public final int playerScore;
        public final double grossAmount;
        public final double gstPaid;
        public final double netAmount;

        public PlayerSettlement(int playerScore, double grossAmount, double gstPaid, double netAmount) {
            this.playerScore = playerScore;
            this.grossAmount = grossAmount;
            this.gstPaid = gstPaid;
            this.netAmount = netAmount;
        }

        static PlayerSettlement zero() {
            return new PlayerSettlement(0, 0, 0, 0);
        }
    }
}
