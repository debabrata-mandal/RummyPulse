package com.example.rummypulse.ui.playerconsolidation;

public class ConsolidationTotals {

    private final double totalNet;
    private final double totalBoardPoints;
    private final double totalGrossWinnings;
    private final double netPlayerBalance;

    public ConsolidationTotals(double totalNet, double totalBoardPoints) {
        this(totalNet, totalBoardPoints, 0, totalNet + totalBoardPoints);
    }

    public ConsolidationTotals(double totalNet, double totalBoardPoints,
                               double totalGrossWinnings, double netPlayerBalance) {
        this.totalNet = totalNet;
        this.totalBoardPoints = totalBoardPoints;
        this.totalGrossWinnings = totalGrossWinnings;
        this.netPlayerBalance = netPlayerBalance;
    }

    public double getTotalNet() {
        return totalNet;
    }

    public double getTotalBoardPoints() {
        return totalBoardPoints;
    }

    public double getTotalGrossWinnings() {
        return totalGrossWinnings;
    }

    public double getNetPlayerBalance() {
        return netPlayerBalance;
    }

    public static ConsolidationTotals fromGroups(java.util.List<ConsolidatedPlayerGroup> groups) {
        double net = 0;
        double boardAdjustment = 0;
        double grossWinnings = 0;
        int entryCount = 0;
        if (groups != null) {
            for (ConsolidatedPlayerGroup group : groups) {
                net += group.getTotalFinalGamePoints();
                boardAdjustment += group.getTotalBoardPoints();
                grossWinnings += Math.max(0, group.getTotalBaseGamePoints());
                entryCount += group.getMembers().size();
            }
        }
        double playerBalance = net + boardAdjustment;
        if (Math.abs(playerBalance) <= entryCount * 0.5) {
            playerBalance = 0;
        }
        return new ConsolidationTotals(
                net, boardAdjustment, grossWinnings, playerBalance);
    }
}
