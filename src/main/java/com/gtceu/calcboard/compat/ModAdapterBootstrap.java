package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import com.gtceu.calcboard.compat.createdieselgenerators.CreateDieselGeneratorsModAdapter;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeModAdapter;
import com.gtceu.calcboard.compat.greate.GreateModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.start.StarTModAdapter;
import com.gtceu.calcboard.compat.systeams.SysteamsModAdapter;
import com.gtceu.calcboard.compat.thermal.ThermalModAdapter;
import com.gtceu.calcboard.compat.vanilla.VanillaModAdapter;

/**
 * Bootstrap registrar that loads concrete mod compatibility adapters into the pure domain SPI registry.
 */
public final class ModAdapterBootstrap {

    private ModAdapterBootstrap() {}

    public static void registerBuiltinAdapters() {
        ModAdapterRegistry.register(new SysteamsModAdapter());               // Priority 110
        ModAdapterRegistry.register(new StarTModAdapter());                  // Priority 105
        ModAdapterRegistry.register(new CreateNewAgeModAdapter());           // Priority 105
        ModAdapterRegistry.register(new ThermalModAdapter());                // Priority 100
        ModAdapterRegistry.register(new GTCEuModAdapter());                  // Priority 100
        ModAdapterRegistry.register(new GreateModAdapter());                 // Priority 95
        ModAdapterRegistry.register(new CreateDieselGeneratorsModAdapter()); // Priority 95
        ModAdapterRegistry.register(new CreateModAdapter());                 // Priority 90
        VanillaModAdapter vanilla = new VanillaModAdapter();
        ModAdapterRegistry.register(vanilla);                                // Priority 0
        ModAdapterRegistry.setFallbackAdapter(vanilla);
    }
}
