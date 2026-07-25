package com.example.rummypulse.ui.playerconsolidation;

public final class BalanceAdjustment {

    private final String adjustmentId;
    private final String fromGroupId;
    private final String toGroupId;
    private final String fromName;
    private final String toName;
    private final double amount;
    private final String reason;
    private final long createdAtMillis;

    public BalanceAdjustment(String adjustmentId, String fromGroupId, String toGroupId,
                             String fromName, String toName, double amount, String reason,
                             long createdAtMillis) {
        this.adjustmentId = adjustmentId;
        this.fromGroupId = fromGroupId;
        this.toGroupId = toGroupId;
        this.fromName = fromName;
        this.toName = toName;
        this.amount = amount;
        this.reason = reason;
        this.createdAtMillis = createdAtMillis;
    }

    public String getAdjustmentId() {
        return adjustmentId;
    }

    public String getFromGroupId() {
        return fromGroupId;
    }

    public String getToGroupId() {
        return toGroupId;
    }

    public String getFromName() {
        return fromName;
    }

    public String getToName() {
        return toName;
    }

    public double getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
