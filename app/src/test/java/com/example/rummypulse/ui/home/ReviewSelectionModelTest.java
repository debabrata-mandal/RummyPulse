package com.example.rummypulse.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ReviewSelectionModelTest {

    @Test
    public void selectingMultipleGamesKeepsInsertionOrder() {
        ReviewSelectionModel model = new ReviewSelectionModel();

        model.setSelected("game-b", true);
        model.setSelected("game-a", true);

        assertEquals(Arrays.asList("game-b", "game-a"), model.snapshot());
        assertEquals(2, model.size());
    }

    @Test
    public void refreshingListRetainsOnlyGamesStillVisible() {
        ReviewSelectionModel model = new ReviewSelectionModel();
        model.selectAll(Arrays.asList("game-a", "game-b", "game-c"));

        model.retainAvailable(Arrays.asList("game-b", "game-c", "game-d"));

        assertEquals(Arrays.asList("game-b", "game-c"), model.snapshot());
        assertFalse(model.isSelected("game-a"));
    }

    @Test
    public void selectAllAndClearReportExpectedState() {
        ReviewSelectionModel model = new ReviewSelectionModel();

        model.selectAll(Arrays.asList("game-a", "game-b"));
        assertTrue(model.areAllSelected(Arrays.asList("game-a", "game-b")));

        model.clear();
        assertEquals(0, model.size());
        assertFalse(model.areAllSelected(Arrays.asList("game-a", "game-b")));
    }
}
