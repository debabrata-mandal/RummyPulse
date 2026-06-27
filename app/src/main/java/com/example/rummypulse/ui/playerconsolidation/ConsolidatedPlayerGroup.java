package com.example.rummypulse.ui.playerconsolidation;

import java.util.ArrayList;
import java.util.List;

public class ConsolidatedPlayerGroup {

    private final String groupId;
    private String displayName;
    private final List<GamePlayerEntry> members;
    private double netAdjustment;

    public ConsolidatedPlayerGroup(String groupId, String displayName, List<GamePlayerEntry> members) {
        this.groupId = groupId;
        this.displayName = displayName;
        this.members = new ArrayList<>(members);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<GamePlayerEntry> getMembers() {
        return members;
    }

    public double getNetAdjustment() {
        return netAdjustment;
    }

    public void setNetAdjustment(double netAdjustment) {
        this.netAdjustment = netAdjustment;
    }

    public void applyNetAdjustmentDelta(double delta) {
        this.netAdjustment += delta;
    }

    public double getTotalGrossAmount() {
        double sum = 0;
        for (GamePlayerEntry member : members) {
            sum += member.getGrossAmount();
        }
        return sum;
    }

    public double getTotalContribution() {
        double sum = 0;
        for (GamePlayerEntry member : members) {
            sum += member.getGstPaid();
        }
        return sum;
    }

    public double getTotalNetAmount() {
        double sum = 0;
        for (GamePlayerEntry member : members) {
            sum += member.getNetAmount();
        }
        return sum;
    }

    public double getAdjustedNetAmount() {
        return getTotalNetAmount() + netAdjustment;
    }
}
