package com.example.rummypulse.ui.join;

import com.example.rummypulse.ui.playerconsolidation.ConsolidatedSettlementCalculator;
import com.example.rummypulse.ui.playerconsolidation.ConsolidatedPlayerGroup;
import com.example.rummypulse.ui.playerconsolidation.SettlementPayment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class GameSettlementCalculator {

    public static final String CONTRIBUTION_RECIPIENT =
            ConsolidatedSettlementCalculator.CONTRIBUTION_RECIPIENT;

    private GameSettlementCalculator() {
    }

    public static final class PlayerNetBalance {
        public final String name;
        public final double netAmount;

        public PlayerNetBalance(String name, double netAmount) {
            this.name = name;
            this.netAmount = netAmount;
        }
    }

    public static ConsolidatedSettlementCalculator.Result calculate(
            List<PlayerNetBalance> balances,
            double totalContribution) {
        List<ConsolidatedPlayerGroup> groups = new ArrayList<>();
        if (balances != null) {
            for (PlayerNetBalance balance : balances) {
                if (balance == null || balance.name == null || balance.name.isEmpty()) {
                    continue;
                }
                ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                        balance.name,
                        balance.name,
                        Collections.emptyList());
                group.setNetAdjustment(balance.netAmount);
                groups.add(group);
            }
        }
        return ConsolidatedSettlementCalculator.calculate(groups, totalContribution);
    }

    public static String formatPayerBreakdown(String playerName,
                                              List<SettlementPayment> payments) {
        if (playerName == null || payments == null || payments.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (SettlementPayment payment : payments) {
            if (payment == null || !playerName.equals(payment.getDebtor())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append("→ ")
                    .append(payment.getCreditor())
                    .append(" ₹")
                    .append(formatWholeRupees(payment.getAmount()));
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    public static String formatReceiverBreakdown(String playerName,
                                                 List<SettlementPayment> payments) {
        if (playerName == null || payments == null || payments.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (SettlementPayment payment : payments) {
            if (payment == null || !playerName.equals(payment.getCreditor())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append("← ")
                    .append(payment.getDebtor())
                    .append(" ₹")
                    .append(formatWholeRupees(payment.getAmount()));
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private static String formatWholeRupees(double amount) {
        return String.format(Locale.getDefault(), "%.0f", amount);
    }
}
