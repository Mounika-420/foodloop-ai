package com.foodloop.util;

import java.security.SecureRandom;
import java.time.Year;

public final class DonationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private DonationCodeGenerator() {
    }

    public static String generate(int year) {
        return String.format("FL-%d-%06d", year, RANDOM.nextInt(1_000_000));
    }

    public static String generate() {
        return generate(Year.now().getValue());
    }
}