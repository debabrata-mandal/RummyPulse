package com.example.rummypulse.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PinUtilsTest {

    @Test
    public void generatePin_alwaysReturnsFourCharacters() {
        for (int i = 0; i < 100; i++) {
            String pin = PinUtils.generatePin();
            assertNotNull("PIN should not be null", pin);
            assertEquals("PIN must be exactly 4 characters, but was: " + pin, 4, pin.length());
        }
    }

    @Test
    public void generatePin_alwaysAllDigits() {
        for (int i = 0; i < 100; i++) {
            String pin = PinUtils.generatePin();
            assertNotNull("PIN should not be null", pin);
            assertTrue("PIN must contain only digits 0-9, but was: " + pin,
                    pin.matches("[0-9]{4}"));
        }
    }

    @Test
    public void generatePin_neverReturnsAllZeros() {
        for (int i = 0; i < 10000; i++) {
            String pin = PinUtils.generatePin();
            assertFalse("PIN must never be '0000' (iteration " + i + ")", "0000".equals(pin));
        }
    }

    @Test
    public void generatePin_alwaysInNumericRange0To9999() {
        for (int i = 0; i < 100; i++) {
            String pin = PinUtils.generatePin();
            assertNotNull("PIN should not be null", pin);
            int value = Integer.parseInt(pin);
            assertTrue("PIN numeric value must be >= 0, but was: " + value, value >= 0);
            assertTrue("PIN numeric value must be <= 9999, but was: " + value, value <= 9999);
        }
    }
}
