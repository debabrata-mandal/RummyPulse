package com.example.rummypulse.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileNameValidatorTest {

    @Test
    public void acceptsConfiguredProfileNameFormat() {
        assertTrue(ProfileNameValidator.isValid("Card_King7"));
        assertTrue(ProfileNameValidator.isValid(" abc "));
        assertTrue(ProfileNameValidator.isValid("Debabrata M."));
        assertTrue(ProfileNameValidator.isValid("Debabrata M. 2"));
        assertTrue(ProfileNameValidator.isValid("Élodie R."));
    }

    @Test
    public void rejectsUnsupportedPunctuationAndInvalidLengths() {
        assertFalse(ProfileNameValidator.isValid("ab"));
        assertFalse(ProfileNameValidator.isValid("two/words"));
        assertFalse(ProfileNameValidator.isValid("name!"));
        assertFalse(ProfileNameValidator.isValid(null));
    }
}
