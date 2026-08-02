package com.example.rummypulse.ui.playerconsolidation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConsolidationAmountFormatterTest {

    // --- formatSignedAmount ---

    @Test
    public void formatSignedAmount_positive_returnsPlusRupeePrefix() {
        assertEquals("+₹100", ConsolidationAmountFormatter.formatSignedAmount(100.0));
    }

    @Test
    public void formatSignedAmount_negative_returnsRupeeWithNegativeValue() {
        assertEquals("₹-50", ConsolidationAmountFormatter.formatSignedAmount(-50.0));
    }

    @Test
    public void formatSignedAmount_zero_returnsRupeeZero() {
        assertEquals("₹0", ConsolidationAmountFormatter.formatSignedAmount(0.0));
    }

    @Test
    public void formatSignedAmount_roundsToNearestLong() {
        // 49.6 rounds to 50
        assertEquals("+₹50", ConsolidationAmountFormatter.formatSignedAmount(49.6));
        // -49.6 rounds to -50
        assertEquals("₹-50", ConsolidationAmountFormatter.formatSignedAmount(-49.6));
    }

    // --- formatAmount ---

    @Test
    public void formatAmount_stripsTrailingZeros_wholeNumber() {
        // 10.0 -> "10" after stripTrailingZeros
        assertEquals("₹10", ConsolidationAmountFormatter.formatAmount(10.0));
    }

    @Test
    public void formatAmount_keepsSignificantDecimalPlaces() {
        // 1.5 -> "1.5"
        assertEquals("₹1.5", ConsolidationAmountFormatter.formatAmount(1.5));
    }

    @Test
    public void formatAmount_roundsHalfUpAtTwoDecimalPlaces() {
        // 1.505 -> rounds to 1.51 with HALF_UP
        assertEquals("₹1.51", ConsolidationAmountFormatter.formatAmount(1.505));
    }

    // --- formatContribution ---

    @Test
    public void formatContribution_delegatesToFormatAmount() {
        // formatContribution must produce the same result as formatAmount for any input
        double amount = 75.25;
        assertEquals(
                ConsolidationAmountFormatter.formatAmount(amount),
                ConsolidationAmountFormatter.formatContribution(amount)
        );
    }
}
