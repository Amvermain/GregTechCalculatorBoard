package com.gtceu.calcboard.client.render;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.WireAnimationMode;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WireAnimationModeTest {

    @Test
    public void testWireAnimationModeEnumCycle() {
        WireAnimationMode mode = WireAnimationMode.RATE_MODULATED;
        mode = mode.next();
        Assertions.assertEquals(WireAnimationMode.UNIFORM_LEGACY, mode);
        mode = mode.next();
        Assertions.assertEquals(WireAnimationMode.DISABLED, mode);
        mode = mode.next();
        Assertions.assertEquals(WireAnimationMode.RATE_MODULATED, mode);
    }

    @Test
    public void testComputeEffectivePulseTimeInUniformMode() {
        float baseTime = 0.45f;
        float result = ConnectionRenderer.computeEffectivePulseTime(baseTime, 0.5f, WireAnimationMode.UNIFORM_LEGACY);
        Assertions.assertEquals(baseTime, result, 1e-4f);
    }

    @Test
    public void testComputeEffectivePulseTimeInRateModulatedMode() {
        float ratioZero = 0.0f;
        float skipResult = ConnectionRenderer.computeEffectivePulseTime(0.5f, ratioZero, WireAnimationMode.RATE_MODULATED);
        Assertions.assertTrue(skipResult < 0.0f);

        float halfRatio = 0.5f;
        float movingTime = ConnectionRenderer.computeEffectivePulseTime(0.25f, halfRatio, WireAnimationMode.RATE_MODULATED);
        Assertions.assertEquals(0.5f, movingTime, 1e-4f);

        float stalledTime = ConnectionRenderer.computeEffectivePulseTime(0.75f, halfRatio, WireAnimationMode.RATE_MODULATED);
        Assertions.assertEquals(1.0f, stalledTime, 1e-4f);

        float fullRatio = 1.0f;
        float fullTime = ConnectionRenderer.computeEffectivePulseTime(0.8f, fullRatio, WireAnimationMode.RATE_MODULATED);
        Assertions.assertEquals(0.8f, fullTime, 1e-4f);
    }

    @Test
    public void testComputePulseRgb() {
        float[] rgb = new float[3];

        ConnectionRenderer.computePulseRgb(0.5f, WireAnimationMode.UNIFORM_LEGACY, rgb);
        Assertions.assertEquals(1.0f, rgb[0], 1e-4f);
        Assertions.assertEquals(1.0f, rgb[1], 1e-4f);
        Assertions.assertEquals(1.0f, rgb[2], 1e-4f);

        ConnectionRenderer.computePulseRgb(1.0f, WireAnimationMode.RATE_MODULATED, rgb);
        Assertions.assertEquals(0.22f, rgb[0], 1e-2f);
        Assertions.assertEquals(0.74f, rgb[1], 1e-2f);
        Assertions.assertEquals(0.97f, rgb[2], 1e-2f);

        ConnectionRenderer.computePulseRgb(0.5f, WireAnimationMode.RATE_MODULATED, rgb);
        Assertions.assertEquals(0.96f, rgb[0], 1e-2f);
        Assertions.assertEquals(0.62f, rgb[1], 1e-2f);
        Assertions.assertEquals(0.04f, rgb[2], 1e-2f);

        ConnectionRenderer.computePulseRgb(0.0f, WireAnimationMode.RATE_MODULATED, rgb);
        Assertions.assertEquals(0.94f, rgb[0], 1e-2f);
        Assertions.assertEquals(0.27f, rgb[1], 1e-2f);
        Assertions.assertEquals(0.27f, rgb[2], 1e-2f);
    }

    @Test
    public void testBoardManagerCycleIntegration() {
        BoardManager bm = BoardManager.getInstance();
        bm.resetToDefault();
        Assertions.assertEquals(WireAnimationMode.RATE_MODULATED, bm.getWireAnimationMode());
        Assertions.assertTrue(bm.isShowWirePulseAnimation());

        bm.cycleWireAnimationMode();
        Assertions.assertEquals(WireAnimationMode.UNIFORM_LEGACY, bm.getWireAnimationMode());
        Assertions.assertTrue(bm.isShowWirePulseAnimation());

        bm.cycleWireAnimationMode();
        Assertions.assertEquals(WireAnimationMode.DISABLED, bm.getWireAnimationMode());
        Assertions.assertFalse(bm.isShowWirePulseAnimation());

        bm.cycleWireAnimationMode();
        Assertions.assertEquals(WireAnimationMode.RATE_MODULATED, bm.getWireAnimationMode());
        Assertions.assertTrue(bm.isShowWirePulseAnimation());
    }
}
