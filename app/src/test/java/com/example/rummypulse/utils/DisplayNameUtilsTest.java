package com.example.rummypulse.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DisplayNameUtilsTest {

    @Test
    public void firstName_null_returnsEmpty() {
        assertEquals("", DisplayNameUtils.firstName(null));
    }

    @Test
    public void firstName_emptyString_returnsEmpty() {
        assertEquals("", DisplayNameUtils.firstName(""));
    }

    @Test
    public void firstName_blankString_returnsEmpty() {
        assertEquals("", DisplayNameUtils.firstName("   "));
    }

    @Test
    public void firstName_fullNameWithSpace_returnsFirstToken() {
        assertEquals("John", DisplayNameUtils.firstName("John Doe"));
    }

    @Test
    public void firstName_emailWithDotInLocalPart_returnsLocalPartBeforeFirstDot() {
        assertEquals("john", DisplayNameUtils.firstName("john.doe@example.com"));
    }

    @Test
    public void firstName_emailNoDotInLocalPart_returnsFullLocalPart() {
        assertEquals("johndoe", DisplayNameUtils.firstName("johndoe@example.com"));
    }

    @Test
    public void firstName_paddedWhitespace_returnsTrimmedFirstToken() {
        assertEquals("Alice", DisplayNameUtils.firstName("  Alice  "));
    }

    @Test
    public void firstName_singleWordNoAtNoSpace_returnsWord() {
        assertEquals("Charlie", DisplayNameUtils.firstName("Charlie"));
    }
}
