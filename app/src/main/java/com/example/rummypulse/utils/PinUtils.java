package com.example.rummypulse.utils;

import java.util.Random;

public final class PinUtils {

    private PinUtils() {
    }

    /** Generates a 4-digit PIN, never {@code 0000}. */
    public static String generatePin() {
        Random random = new Random();
        String pin;
        do {
            pin = String.format("%04d", random.nextInt(10000));
        } while ("0000".equals(pin));
        return pin;
    }
}
