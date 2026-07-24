package com.example.rummypulse.data;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ApprovalBatchValidatorTest {

    @Test
    public void tenValidCompletedGamesFitAtomicLimits() {
        ApprovalBatchValidator.validateSelectionCount(10);
        ApprovalBatchValidator.validateWriteCount(10, 10);
    }

    @Test
    public void completedGameDataPassesValidation() {
        GameData data = completedGameData();
        GameDataWrapper wrapper = new GameDataWrapper();
        wrapper.setData(data);

        assertSame(data, ApprovalBatchValidator.validateGameData("game-1", wrapper));
    }

    @Test
    public void missingGameDataFailsValidation() {
        assertThrows(
                IllegalStateException.class,
                () -> ApprovalBatchValidator.validateGameData("missing-game", null));
    }

    @Test
    public void incompleteGameFailsValidation() {
        GameData data = new GameData();
        data.setPlayers(Collections.singletonList(
                new Player("Player", Collections.singletonList(10), 1)));
        GameDataWrapper wrapper = new GameDataWrapper();
        wrapper.setData(data);

        assertThrows(
                IllegalStateException.class,
                () -> ApprovalBatchValidator.validateGameData("active-game", wrapper));
    }

    @Test
    public void oversizedSelectionFailsBeforeFirestoreWork() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApprovalBatchValidator.validateSelectionCount(
                        ApprovalBatchValidator.MAX_GAMES_PER_TRANSACTION + 1));
    }

    @Test
    public void excessiveCleanupWritesFailBeforeCommit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApprovalBatchValidator.validateWriteCount(100, 151));
    }

    private static GameData completedGameData() {
        GameData data = new GameData();
        data.setNumPlayers(2);
        data.setPlayers(Arrays.asList(
                new Player("One", Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 1),
                new Player("Two", Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 2)));
        return data;
    }
}
