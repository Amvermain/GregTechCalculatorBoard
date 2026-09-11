package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MultiblockDetectorConcurrencyTest {

    @Test
    @DisplayName("REQ-040-01: MultiblockDetector and TurbineCatalog concurrent read/write thread safety")
    void testConcurrentReadWrite() throws Exception {
        int threadCount = 8;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        ResourceLocation id = ResourceLocation.tryParse("test_mod:machine_" + threadIndex + "_" + i);
                        ResourceLocation catId = ResourceLocation.tryParse("test_mod:cat_" + threadIndex + "_" + i);

                        MultiblockDetector.registerMultiblock(id);
                        MultiblockDetector.registerCoilMultiblock(id, catId);
                        MultiblockDetector.registerTurbine(id, catId, GTVoltageTier.HV, 1024.0);
                        MultiblockDetector.registerBatchModeMultiblock(id);
                        MultiblockDetector.registerThroughputBoostingMultiblock(id);
                        MultiblockDetector.registerBulkProcessingMultiblock(id);
                        MultiblockDetector.registerOverpressureMultiblock(id);
                        MultiblockDetector.registerCoilParallelMultiblock(id);
                        MultiblockDetector.registerParallelHatchMultiblock(id);
                        MultiblockDetector.registerLaserHatchMultiblock(id);
                        MultiblockDetector.registerSteamMultiblock(id, 8, 100.0);

                        TurbineCatalog.registerTurbineTierAndProduction(id, GTVoltageTier.EV, 2048.0);

                        boolean isMb = MultiblockDetector.isMultiblock(id);
                        boolean isCoil = MultiblockDetector.isCoilMultiblock(id);
                        boolean isTurbine = MultiblockDetector.isTurbine(id);
                        Double prod = TurbineCatalog.getTurbineBaseProduction(id);

                        Assertions.assertTrue(isMb);
                        Assertions.assertTrue(isCoil);
                        Assertions.assertTrue(isTurbine);
                        Assertions.assertNotNull(prod);
                        Assertions.assertTrue(prod > 0.0);
                    }
                } catch (Throwable ex) {
                    failed.set(true);
                    throw new RuntimeException(ex);
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        Assertions.assertFalse(failed.get(), "Concurrent access to MultiblockDetector/TurbineCatalog must not throw exceptions");
    }

    @Test
    @DisplayName("REQ-040-02: MultiblockStructureCatalog invalidateTextCaches cleans structure caches")
    void testInvalidateTextCaches() {
        Assertions.assertDoesNotThrow(MultiblockStructureCatalog::invalidateTextCaches);
    }

    @Test
    @DisplayName("REQ-040-01: MultiblockDetector.reinitialize preserves baseline turbine models and production values")
    void testReinitializePreservesBaselineTurbines() {
        try {
            MultiblockDetector.reinitialize();
            ResourceLocation spt = ResourceLocation.tryParse("gtceu:supreme_plasma_turbine");
            ResourceLocation lst = ResourceLocation.tryParse("gtceu:large_steam_turbine");

            Assertions.assertTrue(MultiblockDetector.isTurbineMachine(spt), "Supreme plasma turbine must be recognized after reinitialize");
            Assertions.assertTrue(MultiblockDetector.isTurbineMachine(lst), "Large steam turbine must be recognized after reinitialize");
            Assertions.assertEquals(98304.0, TurbineCatalog.getTurbineBaseProduction(spt), "Supreme plasma turbine base production must be preserved as 98304 EU/t");
            Assertions.assertEquals(1024.0, TurbineCatalog.getTurbineBaseProduction(lst), "Large steam turbine base production must be preserved as 1024 EU/t");
        } finally {
            com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
        }
    }
}
