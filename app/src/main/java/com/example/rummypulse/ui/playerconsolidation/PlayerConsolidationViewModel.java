package com.example.rummypulse.ui.playerconsolidation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerConsolidationViewModel extends ViewModel {

    private final GameRepository gameRepository;
    private final MutableLiveData<Set<String>> selectedGameIds = new MutableLiveData<>(new HashSet<>());

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

    @Override
    protected void onCleared() {
        super.onCleared();
        if (gameRepository != null) {
            gameRepository.removeListeners();
        }
    }
}
