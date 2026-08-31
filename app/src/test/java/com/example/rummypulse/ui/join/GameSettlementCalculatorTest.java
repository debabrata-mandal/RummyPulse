package com.example.rummypulse.ui.join;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.ui.playerconsolidation.ConsolidatedSettlementCalculator;
import com.example.rummypulse.ui.playerconsolidation.SettlementPayment;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GameSettlementCalculatorTest {

    @Test
    public void calculate_matchesLargestDebtAndCredit() {
        List<GameSettlementCalculator.PlayerNetBalance> balances = Arrays.asList(
                balance("Amit", -700),
                balance("Rahul", -300),
                balance("Priya", 600),
                balance("Sneha", 400));

        ConsolidatedSettlementCalculator.Result result =
                GameSettlementCalculator.calculate(balances, 0);

        assertEquals(ConsolidatedSettlementCalculator.Status.SUCCESS, result.getStatus());
        assertEquals(3, result.getPayments().size());
    }

    @Test
    public void calculate_addsContributionAsSettlementRecipient() {
        List<GameSettlementCalculator.PlayerNetBalance> balances = Arrays.asList(
                balance("Amit", -1_000),
                balance("Priya", 900));

        ConsolidatedSettlementCalculator.Result result =
                GameSettlementCalculator.calculate(balances, 100);

        assertEquals(ConsolidatedSettlementCalculator.Status.SUCCESS, result.getStatus());
        assertEquals(2, result.getPayments().size());
        assertPayment(result.getPayments().get(0), "Amit", "Priya", 90_000);
        assertPayment(result.getPayments().get(1), "Amit", "Contribution", 10_000);
    }

    @Test
    public void calculate_allZero_returnsAllSettled() {
        ConsolidatedSettlementCalculator.Result result =
                GameSettlementCalculator.calculate(
                        Arrays.asList(balance("Amit", 0), balance("Priya", 0)),
                        0);

        assertEquals(
                ConsolidatedSettlementCalculator.Status.ALL_SETTLED,
                result.getStatus());
        assertTrue(result.getPayments().isEmpty());
    }

    @Test
    public void formatPayerBreakdown_listsOutgoingPayments() {
        List<SettlementPayment> payments = Arrays.asList(
                new SettlementPayment("Amit", "Priya", 60_000),
                new SettlementPayment("Amit", "Contribution", 10_000));

        assertEquals(
                "→ Priya ₹600 · → Contribution ₹100",
                GameSettlementCalculator.formatPayerBreakdown("Amit", payments));
    }

    @Test
    public void formatReceiverBreakdown_listsIncomingPayments() {
        List<SettlementPayment> payments = Arrays.asList(
                new SettlementPayment("Amit", "Priya", 60_000),
                new SettlementPayment("Rahul", "Priya", 30_000));

        assertEquals(
                "← Amit ₹600 · ← Rahul ₹300",
                GameSettlementCalculator.formatReceiverBreakdown("Priya", payments));
    }

    @Test
    public void formatBreakdown_returnsNullWhenNoMatchingPayments() {
        assertNull(GameSettlementCalculator.formatPayerBreakdown(
                "Nobody", Collections.emptyList()));
        assertNull(GameSettlementCalculator.formatReceiverBreakdown(
                "Nobody", Collections.emptyList()));
    }

    private static GameSettlementCalculator.PlayerNetBalance balance(
            String name, double netAmount) {
        return new GameSettlementCalculator.PlayerNetBalance(name, netAmount);
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
