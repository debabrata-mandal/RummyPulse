package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.GameRepository;
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
        selectedEntryIds.setValue(new HashSet<>());
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
        } else {
            for (GamePlayerEntry member : group.getMembers()) {
                updated.add(member.getEntryId());
            }
        }
        selectedEntryIds.setValue(updated);
    }

    public int getSelectedEntryCount() {
        Set<String> ids = selectedEntryIds.getValue();
        return ids != null ? ids.size() : 0;
    }

    public boolean canLinkSelected() {
        return getSelectedEntryCount() >= 2;
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
        playerGroups.setValue(merged);
        publishDerivedLists(merged);
    }

    public void resetConsolidation(List<GameItem> selectedGames) {
        List<ConsolidatedPlayerGroup> groups = PlayerConsolidationEngine.buildInitialGroups(selectedGames);
        selectedEntryIds.setValue(new HashSet<>());
        playerGroups.setValue(groups);
        publishDerivedLists(groups);
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
