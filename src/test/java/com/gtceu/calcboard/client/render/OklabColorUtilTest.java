package com.gtceu.calcboard.client.render;

import com.gtceu.calcboard.client.gui.util.OklabColorUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OklabColorUtilTest {

    @Test
    public void testEndpointsOklab() {
        int colorA = 0xFF00E5FF; // Cyan
        int colorB = 0xFF00E676; // Green

        Assertions.assertEquals(colorA, OklabColorUtil.interpolateOklab(colorA, colorB, 0.0f));
        Assertions.assertEquals(colorA, OklabColorUtil.interpolateOklab(colorA, colorB, -0.5f));
        Assertions.assertEquals(colorB, OklabColorUtil.interpolateOklab(colorA, colorB, 1.0f));
        Assertions.assertEquals(colorB, OklabColorUtil.interpolateOklab(colorA, colorB, 1.5f));
        Assertions.assertEquals(colorA, OklabColorUtil.interpolateOklab(colorA, colorA, 0.5f));
    }

    @Test
    public void testEndpointsOklch() {
        int colorA = 0xFFFFD700; // Gold
        int colorB = 0xFFB388FF; // Violet

        Assertions.assertEquals(colorA, OklabColorUtil.interpolateOklch(colorA, colorB, 0.0f));
        Assertions.assertEquals(colorA, OklabColorUtil.interpolateOklch(colorA, colorB, -0.1f));
        Assertions.assertEquals(colorB, OklabColorUtil.interpolateOklch(colorA, colorB, 1.0f));
        Assertions.assertEquals(colorB, OklabColorUtil.interpolateOklch(colorA, colorB, 1.1f));
    }

    @Test
    public void testAlphaInterpolation() {
        int transparentRed = 0x00FF0000;
        int opaqueBlue = 0xFF0000FF;

        int mid = OklabColorUtil.interpolateOklab(transparentRed, opaqueBlue, 0.5f);
        int alpha = (mid >> 24) & 0xFF;
        Assertions.assertEquals(128, alpha, 1);
    }

    @Test
    public void testOklabMidpointChannelsValid() {
        int cyan = 0xFF00E5FF;
        int green = 0xFF00E676;

        int mid = OklabColorUtil.interpolateOklab(cyan, green, 0.5f);
        int r = (mid >> 16) & 0xFF;
        int g = (mid >> 8) & 0xFF;
        int b = mid & 0xFF;

        Assertions.assertTrue(r >= 0 && r <= 255);
        Assertions.assertTrue(g >= 200 && g <= 255);
        Assertions.assertTrue(b >= 100 && b <= 255);
    }

    @Test
    public void testOklchMidpointPreservesChroma() {
        int cyan = 0xFF00E5FF;
        int green = 0xFF00E676;

        int midLch = OklabColorUtil.interpolateOklch(cyan, green, 0.5f);
        Assertions.assertEquals(0xFF, (midLch >> 24) & 0xFF);

        OklabColorUtil.Oklab lab = OklabColorUtil.sRgbToOklab(midLch);
        OklabColorUtil.Oklch lch = OklabColorUtil.oklabToOklch(lab);
        Assertions.assertTrue(lch.C() > 0.05f);
    }

    @Test
    public void testWireColorLutAndCache() {
        int defColor = 0xFF00E5FF;
        int matchedColor = 0xFF00E676;

        int[] lut1 = OklabColorUtil.getWireColorLut(defColor, matchedColor);
        Assertions.assertNotNull(lut1);
        Assertions.assertEquals(256, lut1.length);
        Assertions.assertEquals(defColor, lut1[0]);
        Assertions.assertEquals(matchedColor, lut1[255]);

        int[] lut2 = OklabColorUtil.getWireColorLut(defColor, matchedColor);
        Assertions.assertSame(lut1, lut2);

        int newMatched = 0xFFFFD700;
        int[] lut3 = OklabColorUtil.getWireColorLut(defColor, newMatched);
        Assertions.assertNotSame(lut1, lut3);
        Assertions.assertEquals(newMatched, lut3[255]);
    }

    @Test
    public void testGetInterpolatedWireColor() {
        int defColor = 0xFF00E5FF;
        int matchedColor = 0xFF00E676;

        Assertions.assertEquals(defColor, OklabColorUtil.getInterpolatedWireColor(defColor, matchedColor, 0.0f));
        Assertions.assertEquals(defColor, OklabColorUtil.getInterpolatedWireColor(defColor, matchedColor, -0.2f));
        Assertions.assertEquals(matchedColor, OklabColorUtil.getInterpolatedWireColor(defColor, matchedColor, 1.0f));
        Assertions.assertEquals(matchedColor, OklabColorUtil.getInterpolatedWireColor(defColor, matchedColor, 1.5f));

        int midColor = OklabColorUtil.getInterpolatedWireColor(defColor, matchedColor, 0.5f);
        int[] lut = OklabColorUtil.getWireColorLut(defColor, matchedColor);
        Assertions.assertEquals(lut[128], midColor);
    }

    @Test
    public void testDeficitSaturationInterpolation() {
        int defColor = 0xFF38BDF8; // Default Blue
        int matchedColor = 0xFF22C55E; // Matched Green

        float deficitRatio = 0.19f / 0.20f; // 95% satisfaction (5% deficit)
        int color95 = OklabColorUtil.getInterpolatedWireColor(defColor, matchedColor, deficitRatio);

        // Verify color95 is strictly an Oklab blend between defColor and matchedColor, NOT hardcoded red or amber
        Assertions.assertNotEquals(0xEF4444, color95 & 0x00FFFFFF);
        Assertions.assertNotEquals(0xF59E0B, color95 & 0x00FFFFFF);

        // At 95%, green channel should be very close to matchedColor's green
        int g95 = (color95 >> 8) & 0xFF;
        int gMatched = (matchedColor >> 8) & 0xFF;
        Assertions.assertTrue(Math.abs(g95 - gMatched) <= 10);
    }
}
