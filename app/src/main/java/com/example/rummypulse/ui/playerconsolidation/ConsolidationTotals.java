package com.example.rummypulse.ui.playerconsolidation;

public class ConsolidationTotals {

    private final double totalNet;
    private final double totalContribution;

    public ConsolidationTotals(double totalNet, double totalContribution) {
        this.totalNet = totalNet;
        this.totalContribution = totalContribution;
    }

    public double getTotalNet() {
        return totalNet;
    }

    public double getTotalContribution() {
        return totalContribution;
    }

    public static ConsolidationTotals fromGroups(java.util.List<ConsolidatedPlayerGroup> groups) {
        double net = 0;
        double contribution = 0;
        if (groups != null) {
            for (ConsolidatedPlayerGroup group : groups) {
                net += group.getTotalNetAmount();
                contribution += group.getTotalContribution();
            }
        }
        return new ConsolidationTotals(net, contribution);
    }
}
