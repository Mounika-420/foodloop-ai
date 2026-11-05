package com.foodloop.util;

import com.foodloop.entity.enums.Unit;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class UnitConverter {

    private UnitConverter() {
    }

    public static BigDecimal toKg(Unit unit, BigDecimal quantity, BigDecimal kgPerUnit) {
        if (unit == null || quantity == null) {
            throw new IllegalArgumentException("unit and quantity are required");
        }
        BigDecimal kg;
        switch (unit) {
            case KG, LITRE -> kg = quantity;
            case PORTIONS, PIECES -> {
                if (kgPerUnit == null) {
                    throw new IllegalArgumentException("kgPerUnit is required for " + unit);
                }
                kg = quantity.multiply(kgPerUnit);
            }
            default -> throw new IllegalArgumentException("Unsupported unit " + unit);
        }
        return kg.setScale(2, RoundingMode.HALF_UP);
    }
}