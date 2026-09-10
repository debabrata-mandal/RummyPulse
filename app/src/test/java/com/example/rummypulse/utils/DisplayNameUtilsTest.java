package com.example.rummypulse.utils;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    @Test
    public void initials_null_returnsQuestionMark() {
        assertEquals("?", DisplayNameUtils.initials(null));
    }

    @Test
    public void initials_blank_returnsQuestionMark() {
        assertEquals("?", DisplayNameUtils.initials("   "));
    }

    @Test
    public void initials_fullName_returnsFirstAndLastInitial() {
        assertEquals("JD", DisplayNameUtils.initials("John Doe"));
    }

    @Test
    public void initials_threeTokens_skipsTheMiddleName() {
        assertEquals("JD", DisplayNameUtils.initials("John Michael Doe"));
    }

    @Test
    public void initials_singleToken_returnsOneLetter() {
        assertEquals("C", DisplayNameUtils.initials("charlie"));
    }

    @Test
    public void initials_paddedWhitespace_isTrimmed() {
        assertEquals("AB", DisplayNameUtils.initials("  alice   brown  "));
    }

    @Test
    public void firstNameLastInitial_fullName_returnsFirstNameAndLastInitial() {
        assertEquals("Debabrata M", DisplayNameUtils.firstNameLastInitial("Debabrata Mandal"));
    }

    @Test
    public void firstNameLastInitial_threeTokens_usesLastTokenInitial() {
        assertEquals("John D", DisplayNameUtils.firstNameLastInitial("John Michael Doe"));
    }

    @Test
    public void firstNameLastInitial_singleToken_returnsToken() {
        assertEquals("Charlie", DisplayNameUtils.firstNameLastInitial("Charlie"));
    }

    @Test
    public void firstNameLastInitial_email_returnsLocalPartBeforeDot() {
        assertEquals("john", DisplayNameUtils.firstNameLastInitial("john.doe@example.com"));
    }

    @Test
    public void playerLabel_mappedUser_prefersAccountDisplayName() {
        Map<String, String> byUserId = new HashMap<>();
        byUserId.put("uid-1", "Debabrata Mandal");
        assertEquals(
                "Debabrata M",
                DisplayNameUtils.playerLabel("Debabrata", "uid-1", byUserId));
    }

    @Test
    public void playerLabel_unmappedUser_formatsStoredName() {
        assertEquals("John D", DisplayNameUtils.playerLabel("John Doe", null, Collections.emptyMap()));
    }
}
