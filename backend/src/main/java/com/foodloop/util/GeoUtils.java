package com.foodloop.util;

import java.math.BigDecimal;

/** Geographic helpers. The same formula is used by the Python AI service. */
public final class GeoUtils {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {}

    /** Great-circle distance in km (haversine). */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double haversineKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        return haversineKm(lat1.doubleValue(), lon1.doubleValue(), lat2.doubleValue(), lon2.doubleValue());
    }
}
