package com.foodloop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class UnitConverter {
    private UnitConverter() {}

    /** quantity (in any unit) x kgPerUnit, rounded to 2 decimals. */
    public static BigDecimal toKg(BigDecimal quantity, BigDecimal kgPerUnit) {
        return quantity.multiply(kgPerUnit).setScale(2, RoundingMode.HALF_UP);
    }
}
