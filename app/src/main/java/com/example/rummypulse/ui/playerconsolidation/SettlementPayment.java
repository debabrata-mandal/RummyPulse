package com.example.rummypulse.ui.playerconsolidation;

import java.util.Objects;

public final class SettlementPayment {

    private final String debtor;
    private final String creditor;
    private final long amountPaise;

    public SettlementPayment(String debtor, String creditor, long amountPaise) {
        this.debtor = debtor;
        this.creditor = creditor;
        this.amountPaise = amountPaise;
    }

    public String getDebtor() {
        return debtor;
    }

    public String getCreditor() {
        return creditor;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public double getAmount() {
        return amountPaise / 100.0;
    }

    public String getPaymentId() {
        return debtor + "\u0000" + creditor + "\u0000" + amountPaise;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SettlementPayment)) {
            return false;
        }
        SettlementPayment payment = (SettlementPayment) other;
        return amountPaise == payment.amountPaise
                && Objects.equals(debtor, payment.debtor)
                && Objects.equals(creditor, payment.creditor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(debtor, creditor, amountPaise);
    }
}
