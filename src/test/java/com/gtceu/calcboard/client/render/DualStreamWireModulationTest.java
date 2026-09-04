package com.gtceu.calcboard.client.render;

import com.gtceu.calcboard.client.gui.render.ParticleBatchingEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DualStreamWireModulationTest {

    @Test
    @DisplayName("RFC-020: Incoming pulse frequency scales linearly with deficit from 1.0Hz to 4.0Hz")
    void testPulseFrequencyModulation() {
        Assertions.assertEquals(1.0f, ParticleBatchingEngine.computePulseFrequency(1.0f), 1e-4f);
        Assertions.assertEquals(2.5f, ParticleBatchingEngine.computePulseFrequency(0.5f), 1e-4f);
        Assertions.assertEquals(4.0f, ParticleBatchingEngine.computePulseFrequency(0.0f), 1e-4f);
        Assertions.assertEquals(1.0f, ParticleBatchingEngine.computePulseFrequency(1.5f), 1e-4f);
        Assertions.assertEquals(4.0f, ParticleBatchingEngine.computePulseFrequency(-0.2f), 1e-4f);
    }

    @Test
    @DisplayName("RFC-020: Incoming pulse alpha remains 1.0 for satisfied wires and oscillates in [0.4, 1.0] for deficit wires")
    void testPulseAlphaModulation() {
        Assertions.assertEquals(1.0f, ParticleBatchingEngine.computePulseAlpha(1.0f, 0.25f), 1e-4f);
        Assertions.assertEquals(1.0f, ParticleBatchingEngine.computePulseAlpha(1.2f, 0.75f), 1e-4f);

        float alphaAtZero = ParticleBatchingEngine.computePulseAlpha(0.5f, 0.0f);
        Assertions.assertEquals(0.4f, alphaAtZero, 1e-4f);

        for (int i = 0; i <= 100; i++) {
            float t = i * 0.02f;
            float alpha = ParticleBatchingEngine.computePulseAlpha(0.2f, t);
            Assertions.assertTrue(alpha >= 0.4f - 1e-4f && alpha <= 1.0f + 1e-4f);
        }
    }

    @Test
    @DisplayName("RFC-020: Outgoing particle flow speed derates proportionally with producer efficiency")
    void testOutgoingSpeedDerating() {
        float baseSpeed = 100.0f;
        Assertions.assertEquals(100.0f, ParticleBatchingEngine.computeDeratedSpeed(baseSpeed, 1.0f), 1e-4f);
        Assertions.assertEquals(50.0f, ParticleBatchingEngine.computeDeratedSpeed(baseSpeed, 0.5f), 1e-4f);
        Assertions.assertEquals(0.0f, ParticleBatchingEngine.computeDeratedSpeed(baseSpeed, 0.0f), 1e-4f);
        Assertions.assertEquals(100.0f, ParticleBatchingEngine.computeDeratedSpeed(baseSpeed, 1.5f), 1e-4f);
    }

    @Test
    @DisplayName("RFC-020: Outgoing travel period stretches inversely with efficiency with min clamp")
    void testOutgoingTravelPeriod() {
        Assertions.assertEquals(1600.0f, ParticleBatchingEngine.computeDeratedTravelPeriodMs(1.0f), 1e-3f);
        Assertions.assertEquals(3200.0f, ParticleBatchingEngine.computeDeratedTravelPeriodMs(0.5f), 1e-3f);
        Assertions.assertEquals(16000.0f, ParticleBatchingEngine.computeDeratedTravelPeriodMs(0.1f), 1e-3f);
        Assertions.assertEquals(32000.0f, ParticleBatchingEngine.computeDeratedTravelPeriodMs(0.0f), 1e-3f);
    }
}
