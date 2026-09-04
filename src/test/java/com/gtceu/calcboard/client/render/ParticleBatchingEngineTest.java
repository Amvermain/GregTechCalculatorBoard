package com.gtceu.calcboard.client.render;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.render.ParticleBatchingEngine;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ParticleBatchingEngineTest {

    @BeforeEach
    void setUp() {
        ParticleBatchingEngine.clearCache();
    }

    @Test
    @DisplayName("RFC-020: Batch multiplier clamps cycle durations below 1.0s")
    void testComputeBatchMultiplier() {
        Assertions.assertEquals(20, ParticleBatchingEngine.computeBatchMultiplier(0.05));
        Assertions.assertEquals(10, ParticleBatchingEngine.computeBatchMultiplier(0.1));
        Assertions.assertEquals(3, ParticleBatchingEngine.computeBatchMultiplier(0.4));
        Assertions.assertEquals(2, ParticleBatchingEngine.computeBatchMultiplier(0.5));
        Assertions.assertEquals(1, ParticleBatchingEngine.computeBatchMultiplier(1.0));
        Assertions.assertEquals(1, ParticleBatchingEngine.computeBatchMultiplier(2.5));
        Assertions.assertEquals(1, ParticleBatchingEngine.computeBatchMultiplier(0.0));
        Assertions.assertEquals(1, ParticleBatchingEngine.computeBatchMultiplier(-0.5));
    }

    @Test
    @DisplayName("RFC-020: Batch animation duration scales cycle to at least 1.0s")
    void testComputeBatchAnimationDuration() {
        Assertions.assertEquals(1.0, ParticleBatchingEngine.computeBatchAnimationDuration(0.05), 1e-4);
        Assertions.assertEquals(1.2, ParticleBatchingEngine.computeBatchAnimationDuration(0.4), 1e-4);
        Assertions.assertEquals(1.0, ParticleBatchingEngine.computeBatchAnimationDuration(0.5), 1e-4);
        Assertions.assertEquals(1.0, ParticleBatchingEngine.computeBatchAnimationDuration(1.0), 1e-4);
        Assertions.assertEquals(2.5, ParticleBatchingEngine.computeBatchAnimationDuration(2.5), 1e-4);
    }

    @Test
    @DisplayName("RFC-020: Buffer charge duration accurately evaluates B / Q_in")
    void testComputeBufferChargeDuration() {
        Assertions.assertEquals(10.0, ParticleBatchingEngine.computeBufferChargeDuration(100.0, 10.0), 1e-4);
        Assertions.assertEquals(2.5, ParticleBatchingEngine.computeBufferChargeDuration(50.0, 20.0), 1e-4);
        Assertions.assertEquals(0.0, ParticleBatchingEngine.computeBufferChargeDuration(0.0, 10.0), 1e-4);
        Assertions.assertEquals(0.0, ParticleBatchingEngine.computeBufferChargeDuration(100.0, 0.0), 1e-4);
    }

    @Test
    @DisplayName("RFC-020: Buffer animation period matches charge duration in ms with minimum 1.0s clamp")
    void testComputeBufferAnimationPeriodMs() {
        Assertions.assertEquals(2500.0f, ParticleBatchingEngine.computeBufferAnimationPeriodMs(2.5), 1e-3f);
        Assertions.assertEquals(5000.0f, ParticleBatchingEngine.computeBufferAnimationPeriodMs(5.0), 1e-3f);
        Assertions.assertEquals(1000.0f, ParticleBatchingEngine.computeBufferAnimationPeriodMs(0.4), 1e-3f);
        Assertions.assertEquals(1600.0f, ParticleBatchingEngine.computeBufferAnimationPeriodMs(0.0), 1e-3f);
        Assertions.assertEquals(1600.0f, ParticleBatchingEngine.computeBufferAnimationPeriodMs(-1.0), 1e-3f);
    }

    @Test
    @DisplayName("RFC-020: Badge label correctly produces Mx or Bx annotations")
    void testGetBadgeLabel() {
        Assertions.assertEquals("20x", ParticleBatchingEngine.getBadgeLabel(20, false));
        Assertions.assertEquals("3x", ParticleBatchingEngine.getBadgeLabel(3, false));
        Assertions.assertNull(ParticleBatchingEngine.getBadgeLabel(1, false));
        Assertions.assertEquals("Bx", ParticleBatchingEngine.getBadgeLabel(1, true));
        Assertions.assertEquals("Bx", ParticleBatchingEngine.getBadgeLabel(5, true));
    }

    @Test
    @DisplayName("RFC-020: Node batch multiplier caching and invalidation work deterministically")
    void testNodeBatchMultiplierCaching() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor", 2, 30, GTVoltageTier.LV);
        Assertions.assertEquals(10, ParticleBatchingEngine.getBatchMultiplier(node));

        ParticleBatchingEngine.invalidateNode(node.getId());
        Assertions.assertEquals(10, ParticleBatchingEngine.getBatchMultiplier(node));

        node.setReroute(true);
        Assertions.assertEquals(1, ParticleBatchingEngine.getBatchMultiplier(node));
    }
}
