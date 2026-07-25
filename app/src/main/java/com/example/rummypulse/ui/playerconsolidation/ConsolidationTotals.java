package com.example.rummypulse.ui.playerconsolidation;

public class ConsolidationTotals {

    private final double totalNet;
    private final double totalContribution;
    private final double totalGrossWinnings;
    private final double netPlayerBalance;

    public ConsolidationTotals(double totalNet, double totalContribution) {
        this(totalNet, totalContribution, 0, totalNet + totalContribution);
    }

    public ConsolidationTotals(double totalNet, double totalContribution,
                               double totalGrossWinnings, double netPlayerBalance) {
        this.totalNet = totalNet;
        this.totalContribution = totalContribution;
        this.totalGrossWinnings = totalGrossWinnings;
        this.netPlayerBalance = netPlayerBalance;
    }

    public double getTotalNet() {
        return totalNet;
    }

    public double getTotalContribution() {
        return totalContribution;
    }

    public double getTotalGrossWinnings() {
        return totalGrossWinnings;
    }

    public double getNetPlayerBalance() {
        return netPlayerBalance;
    }

    public static ConsolidationTotals fromGroups(java.util.List<ConsolidatedPlayerGroup> groups) {
        double net = 0;
        double contribution = 0;
        double grossWinnings = 0;
        int entryCount = 0;
        if (groups != null) {
            for (ConsolidatedPlayerGroup group : groups) {
                net += group.getTotalNetAmount();
                contribution += group.getTotalContribution();
                grossWinnings += Math.max(0, group.getTotalGrossAmount());
                entryCount += group.getMembers().size();
            }
        }
        double playerBalance = net + contribution;
        if (Math.abs(playerBalance) <= entryCount * 0.5) {
            playerBalance = 0;
        }
        return new ConsolidationTotals(
                net, contribution, grossWinnings, playerBalance);
    }
}
