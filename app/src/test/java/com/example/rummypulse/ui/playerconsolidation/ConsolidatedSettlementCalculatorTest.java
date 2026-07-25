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
    public void calculate_addsContributionAsSettlementRecipient() {
        List<ConsolidatedPlayerGroup> groups = Arrays.asList(
                group("Amit", -1_000),
                group("Priya", 900));

        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(groups, 100);

        assertEquals(ConsolidatedSettlementCalculator.Status.SUCCESS, result.getStatus());
        assertEquals(2, result.getPayments().size());
        assertPayment(result.getPayments().get(0), "Amit", "Priya", 90_000);
        assertPayment(result.getPayments().get(1), "Amit", "Contribution", 10_000);
        assertEquals(100_000, result.getPlayerPaymentTotalPaise());
    }

    @Test
    public void calculate_eachDebtorPaysFullBalanceAndContributionIsCollected() {
        List<ConsolidatedPlayerGroup> groups = Arrays.asList(
                group("Amit", -700),
                group("Rahul", -300),
                group("Priya", 600),
                group("Sneha", 300));

        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(groups, 100);

        assertEquals(ConsolidatedSettlementCalculator.Status.SUCCESS, result.getStatus());
        assertEquals(3, result.getPayments().size());
        assertPayment(result.getPayments().get(0), "Amit", "Priya", 60_000);
        assertPayment(result.getPayments().get(1), "Rahul", "Sneha", 30_000);
        assertPayment(result.getPayments().get(2), "Amit", "Contribution", 10_000);
        assertEquals(100_000, result.getPlayerPaymentTotalPaise());

        long amitTotalPaise = result.getPayments().stream()
                .filter(payment -> payment.getDebtor().equals("Amit"))
                .mapToLong(SettlementPayment::getAmountPaise)
                .sum();
        long rahulTotalPaise = result.getPayments().stream()
                .filter(payment -> payment.getDebtor().equals("Rahul"))
                .mapToLong(SettlementPayment::getAmountPaise)
                .sum();
        assertEquals(70_000, amitTotalPaise);
        assertEquals(30_000, rahulTotalPaise);
    }

    @Test
    public void calculate_roundsCashPaymentsToWholeRupees() {
        List<ConsolidatedPlayerGroup> groups = Arrays.asList(
                group("Sudip", -506.40),
                group("Priya", 426.44));

        ConsolidatedSettlementCalculator.Result result =
                ConsolidatedSettlementCalculator.calculate(groups, 79.96);

        assertEquals(ConsolidatedSettlementCalculator.Status.SUCCESS, result.getStatus());
        assertEquals(2, result.getPayments().size());
        assertPayment(result.getPayments().get(0), "Sudip", "Priya", 42_600);
        assertPayment(result.getPayments().get(1), "Sudip", "Contribution", 8_000);
        assertEquals(50_600, result.getPlayerPaymentTotalPaise());
        assertTrue(result.getPayments().stream()
                .allMatch(payment -> payment.getAmountPaise() % 100 == 0));
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
