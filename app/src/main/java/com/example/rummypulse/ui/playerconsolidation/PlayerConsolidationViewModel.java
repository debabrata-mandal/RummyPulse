package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerConsolidationViewModel extends ViewModel {

    private final GameRepository gameRepository;
    private final MutableLiveData<Set<String>> selectedGameIds = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<List<ConsolidatedPlayerGroup>> playerGroups = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Set<String>> selectedEntryIds = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<ConsolidationTotals> consolidationTotals =
            new MutableLiveData<>(new ConsolidationTotals(0, 0));

    private boolean consolidationInitialized;
    private String lastInitializedGameKey = "";
    private String lastSelectedGamesContentHash = "";
    private final List<String> groupSelectionOrder = new ArrayList<>();

    public enum RefreshOutcome {
        SKIPPED,
        REFRESHED,
        REFRESHED_WITH_MISSING_MEMBERS
    }

    public PlayerConsolidationViewModel() {
        gameRepository = new GameRepository();
        gameRepository.loadAllGamesWithRealtimeListener();
    }

    public LiveData<List<GameItem>> getGameItems() {
        return gameRepository.getGameItems();
    }

    public LiveData<Set<String>> getSelectedGameIds() {
        return selectedGameIds;
    }

    public LiveData<List<ConsolidatedPlayerGroup>> getPlayerGroups() {
        return playerGroups;
    }

    public LiveData<Set<String>> getSelectedEntryIds() {
        return selectedEntryIds;
    }

    public LiveData<ConsolidationTotals> getConsolidationTotals() {
        return consolidationTotals;
    }

    public void toggleGameSelection(String gameId) {
        if (gameId == null) {
            return;
        }
        Set<String> current = selectedGameIds.getValue();
        if (current == null) {
            current = new HashSet<>();
        }
        Set<String> updated = new HashSet<>(current);
        if (updated.contains(gameId)) {
            updated.remove(gameId);
        } else {
            updated.add(gameId);
        }
        selectedGameIds.setValue(updated);
    }

    public int getSelectedCount() {
        Set<String> ids = selectedGameIds.getValue();
        return ids != null ? ids.size() : 0;
    }

    public boolean canProceed() {
        return getSelectedCount() >= 2;
    }

    public List<GameItem> getSelectedGames(List<GameItem> allGames) {
        Set<String> ids = selectedGameIds.getValue();
        if (ids == null || ids.isEmpty() || allGames == null) {
            return new ArrayList<>();
        }
        List<GameItem> result = new ArrayList<>();
        for (GameItem game : allGames) {
            if (ids.contains(game.getGameId())) {
                result.add(game);
            }
        }
        return result;
    }

    public boolean hasActiveConsolidation() {
        return consolidationInitialized;
    }

    public void initializeConsolidation(List<GameItem> selectedGames) {
        String gameKey = buildGameKey(selectedGames);
        if (consolidationInitialized && gameKey.equals(lastInitializedGameKey)) {
            publishDerivedLists(playerGroups.getValue());
            return;
        }
        List<ConsolidatedPlayerGroup> groups = PlayerConsolidationEngine.buildInitialGroups(selectedGames);
        consolidationInitialized = true;
        lastInitializedGameKey = gameKey;
        lastSelectedGamesContentHash = computeSelectedGamesContentHash(selectedGames);
        selectedEntryIds.setValue(new HashSet<>());
        groupSelectionOrder.clear();
        playerGroups.setValue(groups);
        publishDerivedLists(groups);
    }

    public void toggleGroupSelection(ConsolidatedPlayerGroup group) {
        if (group == null || group.getMembers().isEmpty()) {
            return;
        }
        Set<String> current = selectedEntryIds.getValue();
        if (current == null) {
            current = new HashSet<>();
        }
        Set<String> updated = new HashSet<>(current);
        boolean fullySelected = true;
        for (GamePlayerEntry member : group.getMembers()) {
            if (!updated.contains(member.getEntryId())) {
                fullySelected = false;
                break;
            }
        }
        if (fullySelected) {
            for (GamePlayerEntry member : group.getMembers()) {
                updated.remove(member.getEntryId());
            }
            groupSelectionOrder.remove(group.getGroupId());
        } else {
            for (GamePlayerEntry member : group.getMembers()) {
                updated.add(member.getEntryId());
            }
            if (!groupSelectionOrder.contains(group.getGroupId())) {
                groupSelectionOrder.add(group.getGroupId());
            }
        }
        selectedEntryIds.setValue(updated);
    }

    public int getSelectedEntryCount() {
        Set<String> ids = selectedEntryIds.getValue();
        return ids != null ? ids.size() : 0;
    }

    public boolean canLinkSelected() {
        return getSelectedEntryCount() >= 2 && !canUnlinkSelected();
    }

    public boolean canUnlinkSelected() {
        List<ConsolidatedPlayerGroup> selectedGroups = getFullySelectedGroups();
        return selectedGroups.size() == 1 && selectedGroups.get(0).getMembers().size() >= 2;
    }

    @Nullable
    public ConsolidatedPlayerGroup getSelectedGroupForUnlink() {
        if (!canUnlinkSelected()) {
            return null;
        }
        return getFullySelectedGroups().get(0);
    }

    public List<ConsolidatedPlayerGroup> getFullySelectedGroups() {
        Set<String> selectedIds = selectedEntryIds.getValue();
        List<ConsolidatedPlayerGroup> groups = playerGroups.getValue();
        List<ConsolidatedPlayerGroup> result = new ArrayList<>();
        if (selectedIds == null || selectedIds.isEmpty() || groups == null) {
            return result;
        }
        for (ConsolidatedPlayerGroup group : groups) {
            if (group.getMembers().isEmpty()) {
                continue;
            }
            boolean fullySelected = true;
            for (GamePlayerEntry member : group.getMembers()) {
                if (!selectedIds.contains(member.getEntryId())) {
                    fullySelected = false;
                    break;
                }
            }
            if (fullySelected) {
                result.add(group);
            }
        }
        return result;
    }

    public boolean canTransferBetweenSelected() {
        return getFullySelectedGroups().size() == 2;
    }

    @Nullable
    public ConsolidatedPlayerGroup getDefaultTransferFromGroup() {
        List<ConsolidatedPlayerGroup> selected = getFullySelectedGroups();
        if (selected.size() != 2) {
            return null;
        }
        for (String groupId : groupSelectionOrder) {
            for (ConsolidatedPlayerGroup group : selected) {
                if (groupId.equals(group.getGroupId())) {
                    return group;
                }
            }
        }
        return selected.get(0);
    }

    @Nullable
    public ConsolidatedPlayerGroup getDefaultTransferToGroup() {
        ConsolidatedPlayerGroup from = getDefaultTransferFromGroup();
        List<ConsolidatedPlayerGroup> selected = getFullySelectedGroups();
        if (from == null || selected.size() != 2) {
            return null;
        }
        for (ConsolidatedPlayerGroup group : selected) {
            if (!group.getGroupId().equals(from.getGroupId())) {
                return group;
            }
        }
        return null;
    }

    public boolean applyTransfer(String fromGroupId, String toGroupId, double amount) {
        if (amount <= 0 || TextUtils.isEmpty(fromGroupId) || TextUtils.isEmpty(toGroupId)
                || fromGroupId.equals(toGroupId)) {
            return false;
        }
        List<ConsolidatedPlayerGroup> groups = playerGroups.getValue();
        if (groups == null) {
            return false;
        }
        ConsolidatedPlayerGroup fromGroup = null;
        ConsolidatedPlayerGroup toGroup = null;
        for (ConsolidatedPlayerGroup group : groups) {
            if (fromGroupId.equals(group.getGroupId())) {
                fromGroup = group;
            } else if (toGroupId.equals(group.getGroupId())) {
                toGroup = group;
            }
        }
        if (fromGroup == null || toGroup == null) {
            return false;
        }
        fromGroup.applyNetAdjustmentDelta(-amount);
        toGroup.applyNetAdjustmentDelta(amount);
        selectedEntryIds.setValue(new HashSet<>());
        groupSelectionOrder.clear();
        publishDerivedLists(groups);
        return true;
    }

    public List<String> getSelectedEntryNames() {
        Set<String> selectedIds = selectedEntryIds.getValue();
        if (selectedIds == null || selectedIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>();
        List<ConsolidatedPlayerGroup> groups = playerGroups.getValue();
        if (groups == null) {
            return names;
        }
        for (ConsolidatedPlayerGroup group : groups) {
            for (GamePlayerEntry entry : group.getMembers()) {
                if (selectedIds.contains(entry.getEntryId())) {
                    names.add(entry.getPlayerName());
                }
            }
        }
        return names;
    }

    public void mergeSelectedPlayers(String displayName) {
        Set<String> selectedIds = selectedEntryIds.getValue();
        List<ConsolidatedPlayerGroup> currentGroups = playerGroups.getValue();
        if (selectedIds == null || selectedIds.size() < 2 || currentGroups == null) {
            return;
        }
        List<ConsolidatedPlayerGroup> merged = PlayerConsolidationEngine.mergeGroups(
                currentGroups, selectedIds, displayName);
        selectedEntryIds.setValue(new HashSet<>());
        groupSelectionOrder.clear();
        playerGroups.setValue(merged);
        publishDerivedLists(merged);
    }

    public void unlinkSelectedGroup() {
        ConsolidatedPlayerGroup group = getSelectedGroupForUnlink();
        List<ConsolidatedPlayerGroup> currentGroups = playerGroups.getValue();
        if (group == null || currentGroups == null) {
            return;
        }
        List<ConsolidatedPlayerGroup> split = PlayerConsolidationEngine.splitGroup(
                currentGroups, group.getGroupId());
        selectedEntryIds.setValue(new HashSet<>());
        groupSelectionOrder.clear();
        playerGroups.setValue(split);
        publishDerivedLists(split);
    }

    public void resetConsolidation(List<GameItem> selectedGames) {
        List<ConsolidatedPlayerGroup> groups = PlayerConsolidationEngine.buildInitialGroups(selectedGames);
        selectedEntryIds.setValue(new HashSet<>());
        groupSelectionOrder.clear();
        lastSelectedGamesContentHash = computeSelectedGamesContentHash(selectedGames);
        playerGroups.setValue(groups);
        publishDerivedLists(groups);
    }

    public RefreshOutcome refreshConsolidationFromLatestGames(List<GameItem> allGames, boolean force) {
        if (!consolidationInitialized) {
            return RefreshOutcome.SKIPPED;
        }
        List<GameItem> selectedGames = getSelectedGames(allGames);
        if (selectedGames.isEmpty()) {
            return RefreshOutcome.SKIPPED;
        }

        String contentHash = computeSelectedGamesContentHash(selectedGames);
        if (!force && contentHash.equals(lastSelectedGamesContentHash)) {
            return RefreshOutcome.SKIPPED;
        }

        List<ConsolidatedPlayerGroup> currentGroups = playerGroups.getValue();
        PlayerConsolidationEngine.RefreshResult result =
                PlayerConsolidationEngine.refreshGroupsFromGames(currentGroups, selectedGames);
        lastSelectedGamesContentHash = contentHash;
        publishDerivedLists(result.getGroups());
        return result.hadMissingMembers()
                ? RefreshOutcome.REFRESHED_WITH_MISSING_MEMBERS
                : RefreshOutcome.REFRESHED;
    }

    private String computeSelectedGamesContentHash(List<GameItem> selectedGames) {
        if (selectedGames == null || selectedGames.isEmpty()) {
            return "";
        }
        List<GameItem> sorted = new ArrayList<>(selectedGames);
        sorted.sort(Comparator.comparing(game -> game.getGameId() != null ? game.getGameId() : ""));
        StringBuilder hash = new StringBuilder();
        for (GameItem game : sorted) {
            hash.append(game.getGameId()).append('|');
            hash.append(game.getGameStatus()).append('|');
            hash.append(game.getPointValue()).append('|');
            hash.append(game.getGstPercentage()).append('|');
            List<Player> players = game.getPlayers();
            if (players != null) {
                hash.append(players.size()).append(':');
                for (Player player : players) {
                    hash.append(player.getTotalScore()).append(',');
                    hash.append(player.getUserId()).append(',');
                    hash.append(player.getName()).append(',');
                }
            }
            hash.append(';');
        }
        return hash.toString();
    }

    private void publishDerivedLists(List<ConsolidatedPlayerGroup> groups) {
        if (groups == null) {
            consolidationTotals.setValue(new ConsolidationTotals(0, 0));
            return;
        }
        List<ConsolidatedPlayerGroup> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort(Comparator.comparing(group -> group.getDisplayName().toLowerCase()));
        playerGroups.setValue(sortedGroups);
        consolidationTotals.setValue(ConsolidationTotals.fromGroups(sortedGroups));
    }

    private String buildGameKey(List<GameItem> selectedGames) {
        if (selectedGames == null || selectedGames.isEmpty()) {
            return "";
        }
        List<String> ids = selectedGames.stream()
                .map(GameItem::getGameId)
                .sorted()
                .collect(Collectors.toList());
        return TextUtils.join(",", ids);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (gameRepository != null) {
            gameRepository.removeListeners();
        }
    }
}
