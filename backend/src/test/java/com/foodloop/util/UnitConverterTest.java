package com.foodloop.util;

import com.foodloop.entity.enums.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitConverterTest {

    @Test
    void kgAndLitreAreOneToOne() {
        assertEquals(new BigDecimal("12.50"), UnitConverter.toKg(Unit.KG, new BigDecimal("12.5"), null));
        assertEquals(new BigDecimal("8.00"), UnitConverter.toKg(Unit.LITRE, new BigDecimal("8"), null));
    }

    @Test
    void portionsUseKgPerUnit() {
        assertEquals(new BigDecimal("4.00"), UnitConverter.toKg(Unit.PORTIONS, new BigDecimal("10"), new BigDecimal("0.400")));
    }

    @Test
    void piecesRoundToTwoDecimals() {
        assertEquals(new BigDecimal("0.33"), UnitConverter.toKg(Unit.PIECES, new BigDecimal("3"), new BigDecimal("0.111")));
    }

    @Test
    void portionsWithoutKgPerUnitFail() {
        assertThrows(IllegalArgumentException.class,
                () -> UnitConverter.toKg(Unit.PORTIONS, new BigDecimal("10"), null));
    }
}