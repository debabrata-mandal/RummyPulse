package com.example.rummypulse.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileNameValidatorTest {

    @Test
    public void acceptsConfiguredProfileNameFormat() {
        assertTrue(ProfileNameValidator.isValid("Card_King7"));
        assertTrue(ProfileNameValidator.isValid(" abc "));
    }

    @Test
    public void rejectsSpacesPunctuationAndInvalidLengths() {
        assertFalse(ProfileNameValidator.isValid("ab"));
        assertFalse(ProfileNameValidator.isValid("two words"));
        assertFalse(ProfileNameValidator.isValid("name!"));
        assertFalse(ProfileNameValidator.isValid(null));
    }
}
