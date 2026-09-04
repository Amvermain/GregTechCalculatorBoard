package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Single Source of Truth (SSOT) analyzer for intrinsic GTCEu machine capabilities.
 * Evaluates machine classes, recipe modifiers, and definitions deterministically
 * without guessing from block items or UI recipe pages.
 */
public final class GTCEuMachineAnalyzer {

    public record MachineCapabilities(
            ResourceLocation id,
            boolean isMultiblock,
            boolean isCoilWorkable,
            boolean isTurbine,
            GTVoltageTier turbineTier,
            double turbineBaseEnergy,
            boolean supportsParallelHatch,
            boolean supportsBatchMode,
            boolean supportsThroughputBoosting,
            boolean supportsBulkProcessing,
            boolean supportsOverpressure,
            boolean supportsLaserHatch,
            boolean isSteam,
            double steamDrainRate,
            int defaultParallel,
            Set<String> allowedAbilities
    ) {}

    private GTCEuMachineAnalyzer() {}

    public static MachineCapabilities analyze(ResourceLocation id, Object def) {
        if (id == null || def == null) {
            return emptyCapabilities(id);
        }

        Class<?> mCls = GTCEuReflectionBridge.getMachineClass(def);
        GTCEuPatternScanner.PatternScanResult patternRes = GTCEuPatternScanner.scanPattern(def);
        Set<String> scannedAbilities = patternRes.allowedAbilities();

        boolean isMb = GTCEuReflectionBridge.isMultiblockDefinition(def)
                || (mCls != null && GTCEuReflectionBridge.isMultiblockClass(mCls))
                || !scannedAbilities.isEmpty();

        boolean isCoil = isMb && (GTCEuReflectionBridge.isCoilWorkableClass(mCls)
                || scannedAbilities.contains("HEATING_COILS")
                || GTCEuCoilModifierHelper.getCoilMachineSpec(id).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC
                || MultiblockDetector.isCoilMultiblock(id));

        boolean isTurbine = isMb && !isCoil && (
                GTCEuReflectionBridge.isLargeTurbineClass(mCls)
                        || GTCEuReflectionBridge.isITurbineClass(mCls)
                        || (scannedAbilities.contains("ROTOR_HOLDER") && GTCEuReflectionBridge.hasTurbineSignature(def))
        );

        GTVoltageTier turbineTier = null;
        double baseEnergy = 0.0;
        if (isTurbine) {
            GTCEuReflectionBridge.TurbineSpecs specs = GTCEuReflectionBridge.deductTurbineSpecs(def);
            turbineTier = specs.tier();
            baseEnergy = specs.baseEnergy();
        }

        boolean isSteam = MultiblockDetector.isSteamMultiblock(id) || GTCEuReflectionBridge.isSteamMachine(def);

        double steamDrainRate = isSteam ? GTCEuReflectionBridge.getSteamDrainRate(def) : 0.0;
        int innatePar = GTCEuReflectionBridge.getDefaultParallel(def);
        if (isSteam && innatePar <= 1) {
            innatePar = 8;
        }

        boolean supportsParallelHatch = scannedAbilities.contains("PARALLEL_HATCH")
                || MultiblockDetector.supportsParallelHatch(id, (java.util.List<ResourceLocation>) null);
        boolean supportsBatchMode = scannedAbilities.contains("BATCH_MODE")
                || MultiblockDetector.supportsBatchMode(id, (java.util.List<ResourceLocation>) null);
        boolean supportsThroughputBoosting = scannedAbilities.contains("THROUGHPUT_BOOSTING")
                || MultiblockDetector.supportsThroughputBoosting(id);
        boolean supportsBulkProcessing = scannedAbilities.contains("BULK_PROCESSING")
                || MultiblockDetector.supportsBulkProcessing(id);
        boolean supportsOverpressure = scannedAbilities.contains("OVERPRESSURE")
                || MultiblockDetector.supportsOverpressure(id);
        boolean supportsCoilParallel = scannedAbilities.contains("COIL_PARALLEL")
                || MultiblockDetector.isCoilParallelMultiblock(id);
        boolean supportsLaserHatch = scannedAbilities.contains("INPUT_LASER")
                || scannedAbilities.contains("LASER_TARGET_HATCH")
                || scannedAbilities.contains("LASER_SOURCE_HATCH")
                || MultiblockDetector.supportsLaserHatch(id, null);

        Set<String> abilities = new HashSet<>(scannedAbilities);
        if (isCoil) abilities.add("HEATING_COILS");
        if (supportsCoilParallel) {
            abilities.add("COIL_PARALLEL");
            MultiblockDetector.registerCoilParallelMultiblock(id);
        }
        if (supportsParallelHatch) abilities.add("PARALLEL_HATCH");
        if (supportsBatchMode) abilities.add("BATCH_MODE");
        if (supportsThroughputBoosting) abilities.add("THROUGHPUT_BOOSTING");
        if (supportsBulkProcessing) abilities.add("BULK_PROCESSING");
        if (supportsOverpressure) abilities.add("OVERPRESSURE");

        return new MachineCapabilities(
                id,
                isMb,
                isCoil,
                isTurbine,
                turbineTier,
                baseEnergy,
                supportsParallelHatch,
                supportsBatchMode,
                supportsThroughputBoosting,
                supportsBulkProcessing,
                supportsOverpressure,
                supportsLaserHatch,
                isSteam,
                steamDrainRate,
                innatePar,
                Collections.unmodifiableSet(abilities)
        );
    }

    private static MachineCapabilities emptyCapabilities(ResourceLocation id) {
        return new MachineCapabilities(
                id, false, false, false, null, 0.0,
                false, false, false, false, false, false, false, 0.0, 1, Collections.emptySet()
        );
    }
}
