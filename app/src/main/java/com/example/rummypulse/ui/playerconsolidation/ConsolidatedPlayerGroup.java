package com.example.rummypulse.ui.playerconsolidation;

import java.util.ArrayList;
import java.util.List;

public class ConsolidatedPlayerGroup {

    private final String groupId;
    private String displayName;
    private final List<GamePlayerEntry> members;

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
}
