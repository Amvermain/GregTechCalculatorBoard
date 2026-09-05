package com.gtceu.calcboard.client.util;

import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.client.gui.util.TargetRateParser;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

public class TargetRateParserTest {

    @Test
    public void testStandardDecimalRates() {
        OptionalDouble res1 = TargetRateParser.parseRate("1.0/s", false, RateTimeUnit.PER_SECOND);
        assertTrue(res1.isPresent());
        assertEquals(1.0, res1.getAsDouble(), 0.0001);

        OptionalDouble res2 = TargetRateParser.parseRate("0.0833/s", false, RateTimeUnit.PER_SECOND);
        assertTrue(res2.isPresent());
        assertEquals(0.0833, res2.getAsDouble(), 0.0001);

        OptionalDouble res3 = TargetRateParser.parseRate("60/min", false, RateTimeUnit.PER_SECOND);
        assertTrue(res3.isPresent());
        assertEquals(1.0, res3.getAsDouble(), 0.0001);
    }

    @Test
    public void testReciprocalFractionRates() {
        OptionalDouble res1 = TargetRateParser.parseRate("1/12s", false, RateTimeUnit.PER_SECOND);
        assertTrue(res1.isPresent());
        assertEquals(1.0 / 12.0, res1.getAsDouble(), 0.0001);

        OptionalDouble res2 = TargetRateParser.parseRate("1/60s", false, RateTimeUnit.PER_SECOND);
        assertTrue(res2.isPresent());
        assertEquals(1.0 / 60.0, res2.getAsDouble(), 0.0001);

        OptionalDouble res3 = TargetRateParser.parseRate("2/5s", false, RateTimeUnit.PER_SECOND);
        assertTrue(res3.isPresent());
        assertEquals(2.0 / 5.0, res3.getAsDouble(), 0.0001);

        OptionalDouble res4 = TargetRateParser.parseRate("1/2min", false, RateTimeUnit.PER_SECOND);
        assertTrue(res4.isPresent());
        assertEquals(1.0 / 120.0, res4.getAsDouble(), 0.0001);
    }

    @Test
    public void testFluidUnitRates() {
        OptionalDouble res1 = TargetRateParser.parseRate("1000mB/s", true, RateTimeUnit.PER_SECOND);
        assertTrue(res1.isPresent());
        assertEquals(1000.0, res1.getAsDouble(), 0.0001);

        OptionalDouble res2 = TargetRateParser.parseRate("1B/s", true, RateTimeUnit.PER_SECOND);
        assertTrue(res2.isPresent());
        assertEquals(1000.0, res2.getAsDouble(), 0.0001);

        OptionalDouble res3 = TargetRateParser.parseRate("60B/min", true, RateTimeUnit.PER_SECOND);
        assertTrue(res3.isPresent());
        assertEquals(1000.0, res3.getAsDouble(), 0.0001);
    }

    @Test
    public void testInvalidRates() {
        assertFalse(TargetRateParser.parseRate("", false, RateTimeUnit.PER_SECOND).isPresent());
        assertFalse(TargetRateParser.parseRate("   ", false, RateTimeUnit.PER_SECOND).isPresent());
        assertFalse(TargetRateParser.parseRate("abc", false, RateTimeUnit.PER_SECOND).isPresent());
        assertFalse(TargetRateParser.parseRate("-5/s", false, RateTimeUnit.PER_SECOND).isPresent());
        assertFalse(TargetRateParser.parseRate("0/s", false, RateTimeUnit.PER_SECOND).isPresent());
        assertFalse(TargetRateParser.parseRate("1/0s", false, RateTimeUnit.PER_SECOND).isPresent());
    }
}
