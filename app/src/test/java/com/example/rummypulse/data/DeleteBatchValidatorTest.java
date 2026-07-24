package com.example.rummypulse.data;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class DeleteBatchValidatorTest {

    @Test
    public void acceptsDeletionWithinSafeWriteLimit() {
        DeleteBatchValidator.validateSelectionCount(100);
        DeleteBatchValidator.validateWriteCount(100, 250);
    }

    @Test
    public void rejectsEmptyOrOversizedSelection() {
        assertThrows(IllegalArgumentException.class,
                () -> DeleteBatchValidator.validateSelectionCount(0));
        assertThrows(IllegalArgumentException.class,
                () -> DeleteBatchValidator.validateSelectionCount(101));
    }

    @Test
    public void rejectsDeletionThatWouldExceedWriteLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> DeleteBatchValidator.validateWriteCount(100, 251));
    }
}
