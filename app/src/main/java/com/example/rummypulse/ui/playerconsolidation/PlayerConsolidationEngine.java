package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;

import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerConsolidationEngine {

    private static final String UNKNOWN_PLAYER = "Unknown Player";

    private PlayerConsolidationEngine() {
    }

    public static final class RefreshResult {
        private final List<ConsolidatedPlayerGroup> groups;
        private final boolean hadMissingMembers;

        public RefreshResult(List<ConsolidatedPlayerGroup> groups, boolean hadMissingMembers) {
            this.groups = groups;
            this.hadMissingMembers = hadMissingMembers;
        }

        public List<ConsolidatedPlayerGroup> getGroups() {
            return groups;
        }

        public boolean hadMissingMembers() {
            return hadMissingMembers;
        }
    }

    public static List<ConsolidatedPlayerGroup> buildInitialGroups(List<GameItem> games) {
        List<GamePlayerEntry> allEntries = flattenPlayers(games);
        List<ConsolidatedPlayerGroup> groups = new ArrayList<>();
        for (GamePlayerEntry entry : allEntries) {
            groups.add(createGroup(List.of(entry)));
        }
        return groups;
    }

    public static RefreshResult refreshGroupsFromGames(
            List<ConsolidatedPlayerGroup> currentGroups,
            List<GameItem> games) {
        if (currentGroups == null || currentGroups.isEmpty()) {
            return new RefreshResult(buildInitialGroups(games), false);
        }

        List<GamePlayerEntry> freshEntries = flattenPlayers(games);
        Map<String, GamePlayerEntry> freshById = new HashMap<>();
        Map<String, List<GamePlayerEntry>> freshByGameId = new LinkedHashMap<>();
        for (GamePlayerEntry entry : freshEntries) {
            freshById.put(entry.getEntryId(), entry);
            freshByGameId.computeIfAbsent(entry.getGameId(), key -> new ArrayList<>()).add(entry);
        }

        Set<String> assignedEntryIds = new HashSet<>();
        List<ConsolidatedPlayerGroup> refreshedGroups = new ArrayList<>();
        boolean hadMissingMembers = false;

        for (ConsolidatedPlayerGroup group : currentGroups) {
            List<GamePlayerEntry> updatedMembers = new ArrayList<>();
            for (GamePlayerEntry member : group.getMembers()) {
                GamePlayerEntry fresh = resolveFreshEntry(member, freshById, freshByGameId);
                if (fresh != null) {
                    updatedMembers.add(fresh);
                    assignedEntryIds.add(fresh.getEntryId());
                } else {
                    hadMissingMembers = true;
                }
            }
            if (updatedMembers.isEmpty()) {
                continue;
            }
            ConsolidatedPlayerGroup refreshed = new ConsolidatedPlayerGroup(
                    group.getGroupId(), group.getDisplayName(), updatedMembers);
            refreshed.setNetAdjustment(group.getNetAdjustment());
            refreshedGroups.add(refreshed);
        }

        for (GamePlayerEntry entry : freshEntries) {
            if (!assignedEntryIds.contains(entry.getEntryId())) {
                refreshedGroups.add(createGroup(List.of(entry)));
            }
        }

        return new RefreshResult(refreshedGroups, hadMissingMembers);
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

    static String buildEntryId(String gameId, Player player, int index) {
        if (!TextUtils.isEmpty(player.getUserId())) {
            return gameId + "::uid:" + player.getUserId();
        }
        String playerName = player.getName();
        if (TextUtils.isEmpty(playerName)) {
            playerName = UNKNOWN_PLAYER;
        }
        return gameId + "::idx:" + index + "::" + normalizeName(playerName);
    }

    private static GamePlayerEntry resolveFreshEntry(
            GamePlayerEntry existing,
            Map<String, GamePlayerEntry> freshById,
            Map<String, List<GamePlayerEntry>> freshByGameId) {
        GamePlayerEntry direct = freshById.get(existing.getEntryId());
        if (direct != null) {
            return direct;
        }

        String userId = existing.getUserId();
        if (!TextUtils.isEmpty(userId)) {
            for (GamePlayerEntry fresh : freshById.values()) {
                if (existing.getGameId().equals(fresh.getGameId()) && userId.equals(fresh.getUserId())) {
                    return fresh;
                }
            }
        }

        Integer legacyIndex = parseLegacyIndexEntryId(existing.getEntryId(), existing.getGameId());
        if (legacyIndex != null) {
            List<GamePlayerEntry> gameEntries = freshByGameId.get(existing.getGameId());
            if (gameEntries != null && legacyIndex >= 0 && legacyIndex < gameEntries.size()) {
                return gameEntries.get(legacyIndex);
            }
        }

        String normalizedExistingName = normalizeName(existing.getPlayerName());
        for (GamePlayerEntry fresh : freshById.values()) {
            if (existing.getGameId().equals(fresh.getGameId())
                    && normalizedExistingName.equals(normalizeName(fresh.getPlayerName()))) {
                return fresh;
            }
        }
        return null;
    }

    private static Integer parseLegacyIndexEntryId(String entryId, String gameId) {
        if (TextUtils.isEmpty(entryId) || TextUtils.isEmpty(gameId)) {
            return null;
        }
        if (!entryId.startsWith(gameId + "::")) {
            return null;
        }
        String suffix = entryId.substring(gameId.length() + 2);
        if (suffix.startsWith("uid:") || suffix.startsWith("idx:")) {
            return null;
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeName(String name) {
        if (TextUtils.isEmpty(name)) {
            return UNKNOWN_PLAYER.toLowerCase(Locale.US);
        }
        return name.trim().toLowerCase(Locale.US);
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
                String entryId = buildEntryId(gameId, player, i);
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
