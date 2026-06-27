package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;

import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerConsolidationEngine {

    private static final String UNKNOWN_PLAYER = "Unknown Player";

    private PlayerConsolidationEngine() {
    }

    public static List<ConsolidatedPlayerGroup> buildInitialGroups(List<GameItem> games) {
        List<GamePlayerEntry> allEntries = flattenPlayers(games);
        List<ConsolidatedPlayerGroup> groups = new ArrayList<>();
        for (GamePlayerEntry entry : allEntries) {
            groups.add(createGroup(List.of(entry)));
        }
        return groups;
    }

    public static List<ConsolidatedPlayerGroup> mergeGroups(
            List<ConsolidatedPlayerGroup> groups,
            Set<String> entryIdsToMerge,
            String displayName) {
        if (entryIdsToMerge == null || entryIdsToMerge.size() < 2) {
            return copyGroups(groups);
        }

        List<ConsolidatedPlayerGroup> result = new ArrayList<>();
        List<GamePlayerEntry> mergedMembers = new ArrayList<>();
        Set<String> consumedGroupIds = new HashSet<>();

        for (ConsolidatedPlayerGroup group : groups) {
            boolean touchesMerge = false;
            for (GamePlayerEntry member : group.getMembers()) {
                if (entryIdsToMerge.contains(member.getEntryId())) {
                    touchesMerge = true;
                    break;
                }
            }
            if (touchesMerge) {
                consumedGroupIds.add(group.getGroupId());
                for (GamePlayerEntry member : group.getMembers()) {
                    if (entryIdsToMerge.contains(member.getEntryId())) {
                        mergedMembers.add(member);
                    }
                }
            }
        }

        if (mergedMembers.size() < 2) {
            return copyGroups(groups);
        }

        String resolvedName = TextUtils.isEmpty(displayName)
                ? mergedMembers.get(0).getPlayerName()
                : displayName.trim();
        ConsolidatedPlayerGroup mergedGroup =
                new ConsolidatedPlayerGroup(UUID.randomUUID().toString(), resolvedName, mergedMembers);
        double mergedAdjustment = 0;
        for (ConsolidatedPlayerGroup group : groups) {
            if (isFullyConsumed(group, entryIdsToMerge)) {
                mergedAdjustment += group.getNetAdjustment();
            }
        }
        mergedGroup.setNetAdjustment(mergedAdjustment);
        result.add(mergedGroup);

        for (ConsolidatedPlayerGroup group : groups) {
            if (!consumedGroupIds.contains(group.getGroupId())) {
                result.add(copyGroup(group));
                continue;
            }

            List<GamePlayerEntry> remaining = new ArrayList<>();
            for (GamePlayerEntry member : group.getMembers()) {
                if (!entryIdsToMerge.contains(member.getEntryId())) {
                    remaining.add(member);
                }
            }
            if (!remaining.isEmpty()) {
                ConsolidatedPlayerGroup remainder = createGroup(remaining);
                remainder.setNetAdjustment(group.getNetAdjustment());
                result.add(remainder);
            }
        }

        return result;
    }

    private static List<GamePlayerEntry> flattenPlayers(List<GameItem> games) {
        List<GamePlayerEntry> entries = new ArrayList<>();
        if (games == null) {
            return entries;
        }
        for (GameItem game : games) {
            List<Player> players = game.getPlayers();
            if (players == null || players.isEmpty()) {
                continue;
            }
            String gameId = game.getGameId() != null ? game.getGameId() : "";
            String gameName = game.getDashboardPrimaryLabel();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                String playerName = player.getName();
                if (TextUtils.isEmpty(playerName)) {
                    playerName = UNKNOWN_PLAYER;
                }
                String entryId = gameId + "::" + i;
                PlayerSettlementCalculator.PlayerSettlement settlement =
                        PlayerSettlementCalculator.compute(game, player);
                entries.add(new GamePlayerEntry(
                        entryId,
                        gameId,
                        gameName,
                        playerName,
                        player.getUserId(),
                        settlement.playerScore,
                        settlement.grossAmount,
                        settlement.gstPaid,
                        settlement.netAmount));
            }
        }
        return entries;
    }

    private static ConsolidatedPlayerGroup createGroup(List<GamePlayerEntry> members) {
        String displayName = members.isEmpty() ? UNKNOWN_PLAYER : members.get(0).getPlayerName();
        return new ConsolidatedPlayerGroup(UUID.randomUUID().toString(), displayName, members);
    }

    private static boolean isFullyConsumed(ConsolidatedPlayerGroup group, Set<String> entryIdsToMerge) {
        for (GamePlayerEntry member : group.getMembers()) {
            if (!entryIdsToMerge.contains(member.getEntryId())) {
                return false;
            }
        }
        return !group.getMembers().isEmpty();
    }

    private static ConsolidatedPlayerGroup copyGroup(ConsolidatedPlayerGroup group) {
        ConsolidatedPlayerGroup copy = new ConsolidatedPlayerGroup(
                group.getGroupId(), group.getDisplayName(), group.getMembers());
        copy.setNetAdjustment(group.getNetAdjustment());
        return copy;
    }

    private static List<ConsolidatedPlayerGroup> copyGroups(List<ConsolidatedPlayerGroup> groups) {
        List<ConsolidatedPlayerGroup> copy = new ArrayList<>();
        for (ConsolidatedPlayerGroup group : groups) {
            copy.add(copyGroup(group));
        }
        return copy;
    }
}
