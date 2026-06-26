package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;

import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        result.add(new ConsolidatedPlayerGroup(UUID.randomUUID().toString(), resolvedName, mergedMembers));

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
                result.add(createGroup(remaining));
            }
        }

        return result;
    }

    public static List<GamePlayerSection> buildSections(List<ConsolidatedPlayerGroup> groups) {
        Map<String, GamePlayerSection> sectionsByGameId = new LinkedHashMap<>();
        for (ConsolidatedPlayerGroup group : groups) {
            for (GamePlayerEntry entry : group.getMembers()) {
                GamePlayerSection section = sectionsByGameId.get(entry.getGameId());
                if (section == null) {
                    section = new GamePlayerSection(entry.getGameId(), entry.getGameName());
                    sectionsByGameId.put(entry.getGameId(), section);
                }
                section.getEntries().add(entry);
            }
        }
        return new ArrayList<>(sectionsByGameId.values());
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
                entries.add(new GamePlayerEntry(
                        entryId,
                        gameId,
                        gameName,
                        playerName,
                        player.getUserId()));
            }
        }
        return entries;
    }

    private static ConsolidatedPlayerGroup createGroup(List<GamePlayerEntry> members) {
        String displayName = members.isEmpty() ? UNKNOWN_PLAYER : members.get(0).getPlayerName();
        return new ConsolidatedPlayerGroup(UUID.randomUUID().toString(), displayName, members);
    }

    private static ConsolidatedPlayerGroup copyGroup(ConsolidatedPlayerGroup group) {
        return new ConsolidatedPlayerGroup(group.getGroupId(), group.getDisplayName(), group.getMembers());
    }

    private static List<ConsolidatedPlayerGroup> copyGroups(List<ConsolidatedPlayerGroup> groups) {
        List<ConsolidatedPlayerGroup> copy = new ArrayList<>();
        for (ConsolidatedPlayerGroup group : groups) {
            copy.add(copyGroup(group));
        }
        return copy;
    }

    public static class GamePlayerSection {
        private final String gameId;
        private final String gameName;
        private final List<GamePlayerEntry> entries = new ArrayList<>();

        public GamePlayerSection(String gameId, String gameName) {
            this.gameId = gameId;
            this.gameName = gameName;
        }

        public String getGameId() {
            return gameId;
        }

        public String getGameName() {
            return gameName;
        }

        public List<GamePlayerEntry> getEntries() {
            return entries;
        }
    }
}
