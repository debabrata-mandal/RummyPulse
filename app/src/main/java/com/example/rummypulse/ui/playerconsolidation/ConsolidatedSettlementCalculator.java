package com.example.rummypulse.ui.playerconsolidation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ConsolidatedSettlementCalculator {

    private ConsolidatedSettlementCalculator() {
    }

    public enum Status {
        SUCCESS,
        ALL_SETTLED,
        UNBALANCED_INPUT
    }

    public static final class Result {
        private final Status status;
        private final List<SettlementPayment> payments;
        private final long playerPaymentTotalPaise;

        private Result(Status status, List<SettlementPayment> payments,
                       long playerPaymentTotalPaise) {
            this.status = status;
            this.payments = payments;
            this.playerPaymentTotalPaise = playerPaymentTotalPaise;
        }

        public Status getStatus() {
            return status;
        }

        public List<SettlementPayment> getPayments() {
            return payments;
        }

        public long getPlayerPaymentTotalPaise() {
            return playerPaymentTotalPaise;
        }
    }

    public static Result calculate(List<ConsolidatedPlayerGroup> groups,
                                   double totalContribution) {
        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();
        long totalDebt = 0;
        long totalCredit = 0;

        if (groups != null) {
            for (ConsolidatedPlayerGroup group : groups) {
                if (group == null) {
                    continue;
                }
                long balance = toPaise(group.getAdjustedNetAmount());
                if (balance < 0) {
                    long debt = -balance;
                    debtors.add(new Balance(group.getDisplayName(), debt));
                    totalDebt += debt;
                } else if (balance > 0) {
                    creditors.add(new Balance(group.getDisplayName(), balance));
                    totalCredit += balance;
                }
            }
        }

        if (totalDebt == 0 && totalCredit == 0) {
            return new Result(Status.ALL_SETTLED, new ArrayList<>(), 0);
        }

        long contributionPaise = Math.max(0, toPaise(totalContribution));
        long contributionGap = totalDebt - totalCredit;
        long roundingTolerancePaise = calculateRoundingTolerancePaise(groups);
        if (contributionGap < 0
                || Math.abs(contributionGap - contributionPaise) > roundingTolerancePaise) {
            return new Result(Status.UNBALANCED_INPUT, new ArrayList<>(), 0);
        }

        Comparator<Balance> largestFirst = Comparator
                .comparingLong(Balance::getAmount)
                .reversed()
                .thenComparing(Balance::getName, String.CASE_INSENSITIVE_ORDER);
        List<SettlementPayment> payments = new ArrayList<>();
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            debtors.sort(largestFirst);
            creditors.sort(largestFirst);
            Balance debtor = debtors.get(0);
            Balance creditor = creditors.get(0);
            long amount = Math.min(debtor.amount, creditor.amount);
            if (amount > 0) {
                payments.add(new SettlementPayment(debtor.name, creditor.name, amount));
                debtor.amount -= amount;
                creditor.amount -= amount;
            }
            if (debtor.amount == 0) {
                debtors.remove(0);
            }
            if (creditor.amount == 0) {
                creditors.remove(0);
            }
        }

        return new Result(
                payments.isEmpty() ? Status.ALL_SETTLED : Status.SUCCESS,
                payments,
                totalCredit);
    }

    private static long toPaise(double amount) {
        return BigDecimal.valueOf(amount)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /*
     * Player gross amounts are currently rounded to whole rupees per game entry.
     * Each entry can therefore contribute at most 50 paise of cumulative drift.
     */
    private static long calculateRoundingTolerancePaise(
            List<ConsolidatedPlayerGroup> groups) {
        int entryCount = 0;
        if (groups != null) {
            for (ConsolidatedPlayerGroup group : groups) {
                if (group != null) {
                    entryCount += Math.max(1, group.getMembers().size());
                }
            }
        }
        return Math.max(1, entryCount * 50L);
    }

    private static final class Balance {
        private final String name;
        private long amount;

        private Balance(String name, long amount) {
            this.name = name;
            this.amount = amount;
        }

        private String getName() {
            return name;
        }

        private long getAmount() {
            return amount;
        }
    }
}
