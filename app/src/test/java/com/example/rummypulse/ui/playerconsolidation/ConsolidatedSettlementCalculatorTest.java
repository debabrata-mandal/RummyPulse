package com.example.rummypulse.ui.playerconsolidation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConsolidatedSettlementCalculatorTest {

    @Test
    public void calculate_matchesLargestDebtAndCredit() {
        List<ConsolidatedPlayerGroup> groups = Arrays.asList(
                group("Amit", -700),
                group("Rahul", -300),
                group("Priya", 600),
                group("Sneha", 400));

        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(groups, 0);

        assertEquals(ConsolidatedSettlementCalculator.Status.SUCCESS, result.getStatus());
        assertEquals(3, result.getPayments().size());
        assertPayment(result.getPayments().get(0), "Amit", "Priya", 60_000);
        assertPayment(result.getPayments().get(1), "Rahul", "Sneha", 30_000);
        assertPayment(result.getPayments().get(2), "Amit", "Sneha", 10_000);
        assertEquals(100_000, result.getPlayerPaymentTotalPaise());
    }

    @Test
    public void calculate_acceptsContributionAsNonPlayerBalanceGap() {
        List<ConsolidatedPlayerGroup> groups = Arrays.asList(
                group("Amit", -1_000),
                group("Priya", 900));

        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(groups, 100);

        assertEquals(ConsolidatedSettlementCalculator.Status.SUCCESS, result.getStatus());
        assertEquals(1, result.getPayments().size());
        assertPayment(result.getPayments().get(0), "Amit", "Priya", 90_000);
    }

    @Test
    public void calculate_acceptsSmallPerEntryRoundingDrift() {
        List<ConsolidatedPlayerGroup> groups = Arrays.asList(
                group("Amit", -1_000),
                group("Priya", 899.50));

        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(groups, 100);

        assertEquals(
                ConsolidatedSettlementCalculator.Status.SUCCESS,
                result.getStatus());
    }

    @Test
    public void calculate_rejectsGapBeyondRoundingTolerance() {
        List<ConsolidatedPlayerGroup> groups = Arrays.asList(
                group("Amit", -1_000),
                group("Priya", 850));

        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(groups, 100);

        assertEquals(ConsolidatedSettlementCalculator.Status.UNBALANCED_INPUT,
                result.getStatus());
    }

    @Test
    public void calculate_allZero_returnsAllSettled() {
        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(
                        Arrays.asList(group("Amit", 0), group("Priya", 0)),
                        0);

        assertEquals(
                ConsolidatedSettlementCalculator.Status.ALL_SETTLED,
                result.getStatus());
        assertTrue(result.getPayments().isEmpty());
    }

    private static ConsolidatedPlayerGroup group(String name, double balance) {
        ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                name,
                name,
                Collections.emptyList());
        group.setNetAdjustment(balance);
        return group;
    }

    private static void assertPayment(SettlementPayment payment,
                                      String debtor,
                                      String creditor,
                                      long amountPaise) {
        assertEquals(debtor, payment.getDebtor());
        assertEquals(creditor, payment.getCreditor());
        assertEquals(amountPaise, payment.getAmountPaise());
    }
}
