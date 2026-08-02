package com.example.rummypulse.ui.playerconsolidation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.ui.home.GameItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PlayerConsolidationViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private PlayerConsolidationViewModel viewModel;

    @Before
    public void setUp() {
        GameRepository mockRepo = mock(GameRepository.class);
        viewModel = new PlayerConsolidationViewModel(mockRepo);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static GameItem gameItem(String id) {
        GameItem item = new GameItem();
        item.setGameId(id);
        return item;
    }

    private static GamePlayerEntry entry(String entryId, String gameId, String playerName) {
        return new GamePlayerEntry(entryId, gameId, "TestGame", playerName,
                null, 0, 0.0, 0.0, 0.0);
    }

    /**
     * Directly injects groups into the ViewModel's private {@code playerGroups} LiveData so that
     * tests for confirmMappings / canUnlinkSelected / getFullySelectedGroups can operate without
     * going through initializeConsolidation (which uses android.text.TextUtils).
     */
    @SuppressWarnings("unchecked")
    private void injectPlayerGroups(List<ConsolidatedPlayerGroup> groups) throws Exception {
        Field field = PlayerConsolidationViewModel.class.getDeclaredField("playerGroups");
        field.setAccessible(true);
        MutableLiveData<List<ConsolidatedPlayerGroup>> liveData =
                (MutableLiveData<List<ConsolidatedPlayerGroup>>) field.get(viewModel);
        liveData.setValue(groups);
    }

    // ── toggleGameSelection ──────────────────────────────────────────────────

    @Test
    public void toggleGameSelection_addsIdWhenNotPreviouslySelected() {
        viewModel.toggleGameSelection("game1");

        Set<String> ids = viewModel.getSelectedGameIds().getValue();
        assertTrue(ids != null && ids.contains("game1"));
    }

    @Test
    public void toggleGameSelection_removesIdWhenAlreadySelected() {
        viewModel.toggleGameSelection("game1");
        viewModel.toggleGameSelection("game1");

        Set<String> ids = viewModel.getSelectedGameIds().getValue();
        assertTrue(ids == null || !ids.contains("game1"));
    }

    @Test
    public void toggleGameSelection_ignoresNullArgument() {
        viewModel.toggleGameSelection(null);
        assertEquals(0, viewModel.getSelectedCount());
    }

    // ── getSelectedCount / canProceed ─────────────────────────────────────────

    @Test
    public void canProceed_falseWhenOnlyOneGameSelected() {
        viewModel.toggleGameSelection("g1");
        assertFalse(viewModel.canProceed());
    }

    @Test
    public void canProceed_trueWhenTwoOrMoreGamesSelected() {
        viewModel.toggleGameSelection("g1");
        viewModel.toggleGameSelection("g2");

        assertEquals(2, viewModel.getSelectedCount());
        assertTrue(viewModel.canProceed());
    }

    // ── getSelectedGames ──────────────────────────────────────────────────────

    @Test
    public void getSelectedGames_returnsOnlyItemsWhoseIdIsSelected() {
        viewModel.toggleGameSelection("g1");
        viewModel.toggleGameSelection("g3");

        List<GameItem> all = Arrays.asList(gameItem("g1"), gameItem("g2"), gameItem("g3"));
        List<GameItem> result = viewModel.getSelectedGames(all);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(i -> "g1".equals(i.getGameId())));
        assertTrue(result.stream().anyMatch(i -> "g3".equals(i.getGameId())));
        assertFalse(result.stream().anyMatch(i -> "g2".equals(i.getGameId())));
    }

    // ── pruneUnavailableGameSelections ────────────────────────────────────────

    @Test
    public void pruneUnavailableGameSelections_removesIdsAbsentFromAvailableList() {
        viewModel.toggleGameSelection("g1");
        viewModel.toggleGameSelection("g2");

        viewModel.pruneUnavailableGameSelections(Collections.singletonList(gameItem("g1")));

        Set<String> ids = viewModel.getSelectedGameIds().getValue();
        assertTrue(ids != null && ids.contains("g1"));
        assertFalse(ids != null && ids.contains("g2"));
    }

    // ── confirmMappings / editMappings ────────────────────────────────────────

    @Test
    public void confirmMappings_setsMappingsConfirmedTrueWhenGroupsNonEmpty() throws Exception {
        GamePlayerEntry member = entry("e1", "g1", "Alice");
        ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Collections.singletonList(member));
        injectPlayerGroups(Collections.singletonList(group));

        viewModel.confirmMappings();

        assertTrue(Boolean.TRUE.equals(viewModel.getMappingsConfirmed().getValue()));
    }

    @Test
    public void confirmMappings_doesNothingWhenGroupListIsEmpty() {
        // playerGroups starts as empty list — confirmMappings must be a no-op
        viewModel.confirmMappings();
        assertFalse(Boolean.TRUE.equals(viewModel.getMappingsConfirmed().getValue()));
    }

    @Test
    public void editMappings_setsMappingsConfirmedFalseAfterConfirm() throws Exception {
        GamePlayerEntry member = entry("e1", "g1", "Alice");
        ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Collections.singletonList(member));
        injectPlayerGroups(Collections.singletonList(group));
        viewModel.confirmMappings();

        viewModel.editMappings();

        assertFalse(Boolean.TRUE.equals(viewModel.getMappingsConfirmed().getValue()));
    }

    // ── toggleGroupSelection / getSelectedEntryCount ──────────────────────────

    @Test
    public void toggleGroupSelection_selectsEveryMemberOfGroup() {
        GamePlayerEntry e1 = entry("e1", "g1", "Alice");
        GamePlayerEntry e2 = entry("e2", "g1", "Bob");
        ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Arrays.asList(e1, e2));

        viewModel.toggleGroupSelection(group);

        assertEquals(2, viewModel.getSelectedEntryCount());
    }

    @Test
    public void toggleGroupSelection_deselectsGroupWhenAllMembersAlreadySelected() {
        GamePlayerEntry e1 = entry("e1", "g1", "Alice");
        GamePlayerEntry e2 = entry("e2", "g1", "Bob");
        ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Arrays.asList(e1, e2));

        viewModel.toggleGroupSelection(group); // select all
        viewModel.toggleGroupSelection(group); // deselect all

        assertEquals(0, viewModel.getSelectedEntryCount());
    }

    // ── canLinkSelected ───────────────────────────────────────────────────────

    @Test
    public void canLinkSelected_trueWhenTwoEntriesSelectedFromSeparateSingleMemberGroups()
            throws Exception {
        GamePlayerEntry e1 = entry("e1", "g1", "Alice");
        GamePlayerEntry e2 = entry("e2", "g2", "Bob");
        ConsolidatedPlayerGroup grp1 = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Collections.singletonList(e1));
        ConsolidatedPlayerGroup grp2 = new ConsolidatedPlayerGroup(
                "grp2", "Bob", Collections.singletonList(e2));
        injectPlayerGroups(Arrays.asList(grp1, grp2));

        viewModel.toggleGroupSelection(grp1);
        viewModel.toggleGroupSelection(grp2);

        // selectedEntryCount == 2, and canUnlinkSelected is false (two groups selected, not one)
        assertTrue(viewModel.canLinkSelected());
    }

    // ── canUnlinkSelected ─────────────────────────────────────────────────────

    @Test
    public void canUnlinkSelected_trueWhenExactlyOneFullySelectedGroupHasMultipleMembers()
            throws Exception {
        GamePlayerEntry e1 = entry("e1", "g1", "Alice");
        GamePlayerEntry e2 = entry("e2", "g2", "Alice");
        ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Arrays.asList(e1, e2));
        injectPlayerGroups(Collections.singletonList(group));

        viewModel.toggleGroupSelection(group);

        assertTrue(viewModel.canUnlinkSelected());
    }

    @Test
    public void canUnlinkSelected_falseWhenSelectedGroupHasOnlyOneMember() throws Exception {
        GamePlayerEntry e1 = entry("e1", "g1", "Alice");
        ConsolidatedPlayerGroup group = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Collections.singletonList(e1));
        injectPlayerGroups(Collections.singletonList(group));

        viewModel.toggleGroupSelection(group);

        assertFalse(viewModel.canUnlinkSelected());
    }

    // ── getFullySelectedGroups ────────────────────────────────────────────────

    @Test
    public void getFullySelectedGroups_includesOnlyGroupsWhereAllMembersAreSelected()
            throws Exception {
        GamePlayerEntry e1 = entry("e1", "g1", "Alice");
        GamePlayerEntry e2 = entry("e2", "g2", "Bob");
        ConsolidatedPlayerGroup grp1 = new ConsolidatedPlayerGroup(
                "grp1", "Alice", Collections.singletonList(e1));
        ConsolidatedPlayerGroup grp2 = new ConsolidatedPlayerGroup(
                "grp2", "Bob", Collections.singletonList(e2));
        injectPlayerGroups(Arrays.asList(grp1, grp2));

        viewModel.toggleGroupSelection(grp1); // select only grp1

        List<ConsolidatedPlayerGroup> fullySelected = viewModel.getFullySelectedGroups();
        assertEquals(1, fullySelected.size());
        assertEquals("grp1", fullySelected.get(0).getGroupId());
    }
}
