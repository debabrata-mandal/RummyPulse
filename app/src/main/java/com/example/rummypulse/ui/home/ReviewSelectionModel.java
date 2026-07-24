package com.example.rummypulse.ui.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ReviewSelectionModel {

    private final Set<String> selectedGameIds = new LinkedHashSet<>();

    void setSelected(String gameId, boolean selected) {
        if (gameId == null || gameId.trim().isEmpty()) {
            return;
        }
        if (selected) {
            selectedGameIds.add(gameId);
        } else {
            selectedGameIds.remove(gameId);
        }
    }

    boolean isSelected(String gameId) {
        return selectedGameIds.contains(gameId);
    }

    void selectAll(Collection<String> availableGameIds) {
        selectedGameIds.clear();
        if (availableGameIds == null) {
            return;
        }
        for (String gameId : availableGameIds) {
            setSelected(gameId, true);
        }
    }

    void retainAvailable(Collection<String> availableGameIds) {
        if (availableGameIds == null) {
            selectedGameIds.clear();
            return;
        }
        selectedGameIds.retainAll(new LinkedHashSet<>(availableGameIds));
    }

    void clear() {
        selectedGameIds.clear();
    }

    int size() {
        return selectedGameIds.size();
    }

    boolean areAllSelected(Collection<String> availableGameIds) {
        return availableGameIds != null
                && !availableGameIds.isEmpty()
                && selectedGameIds.size() == availableGameIds.size()
                && selectedGameIds.containsAll(availableGameIds);
    }

    List<String> snapshot() {
        return new ArrayList<>(selectedGameIds);
    }
}
