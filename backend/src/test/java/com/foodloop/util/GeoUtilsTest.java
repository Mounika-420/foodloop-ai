package com.foodloop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {
    @Test
    void samepointIsZero() {
        assertEquals(0.0, GeoUtils.haversineKm(11.0074, 76.95, 11.0074, 76.95), 1e-9);
    }

    @Test
    void rsPuramToSinganallurIsAboutNineKm() {
        double d = GeoUtils.haversineKm(11.0074, 76.95, 10.999, 77.03);
        assertEquals(8.7, d, 0.5);
    }

    @Test
    void isSymmetric() {
        assertEquals(GeoUtils.haversineKm(11.0, 76.9, 11.1, 77.1), GeoUtils.haversineKm(11.1, 77.1, 11.0, 76.9), 1e-9);
    }
}
