package com.example.rummypulse.ui.playerconsolidation;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

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

        int totalScore = 0;
        for (Player p : players) {
            totalScore += p.getTotalScore();
        }

        int playerScore = player.getTotalScore();
        int numPlayers = declaredNumPlayers;
        if (numPlayers <= 0) {
            numPlayers = players.size();
        }

        double grossAmount = Math.round((totalScore - (double) playerScore * numPlayers) * pointValue);
        double gstPaid = 0;
        double netAmount = grossAmount;
        if (grossAmount > 0) {
            gstPaid = Math.round((grossAmount * gstPercent) / 100.0);
            netAmount = grossAmount - gstPaid;
        }

        return new PlayerSettlement(playerScore, grossAmount, gstPaid, netAmount);
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
