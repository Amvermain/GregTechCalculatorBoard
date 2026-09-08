package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Single Source of Truth (SSOT) analyzer for intrinsic GTCEu machine capabilities and archetypes.
 * Evaluates machine classes, recipe modifiers, and definitions deterministically
 * through positive contract deduction without guessing from block items or UI recipe pages.
 */
public final class GTCEuMachineAnalyzer {

    public record MachineCapabilities(
            ResourceLocation id,
            GTMachineArchetype archetype,
            boolean isMultiblock,
            GTVoltageTier turbineTier,
            double turbineBaseEnergy,
            boolean supportsParallelHatch,
            boolean supportsBatchMode,
            boolean supportsThroughputBoosting,
            boolean supportsBulkProcessing,
            boolean supportsOverpressure,
            boolean supportsLaserHatch,
            double steamDrainRate,
            int defaultParallel,
            Set<String> allowedAbilities
    ) {
        public MachineCapabilities(
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
        ) {
            this(
                    id,
                    resolveFallbackArchetype(isTurbine, isCoilWorkable, isSteam),
                    isMultiblock,
                    turbineTier,
                    turbineBaseEnergy,
                    supportsParallelHatch,
                    supportsBatchMode,
                    supportsThroughputBoosting,
                    supportsBulkProcessing,
                    supportsOverpressure,
                    supportsLaserHatch,
                    steamDrainRate,
                    defaultParallel,
                    allowedAbilities
            );
        }

        private static GTMachineArchetype resolveFallbackArchetype(boolean turbine, boolean coil, boolean steam) {
            if (turbine) return GTMachineArchetype.TURBINE;
            if (coil) return GTMachineArchetype.COIL_HEATED;
            if (steam) return GTMachineArchetype.STEAM_MACHINE;
            return GTMachineArchetype.STANDARD_PROCESSING;
        }

        public boolean isTurbine() {
            return archetype == GTMachineArchetype.TURBINE;
        }

        public boolean isCoilWorkable() {
            return archetype == GTMachineArchetype.COIL_HEATED;
        }

        public boolean isCombustion() {
            return archetype == GTMachineArchetype.COMBUSTION_GENERATOR;
        }

        public boolean isSteam() {
            return archetype == GTMachineArchetype.STEAM_MACHINE;
        }

        public boolean isFusion() {
            return archetype == GTMachineArchetype.FUSION_REACTOR;
        }

        public boolean isThreading() {
            return archetype == GTMachineArchetype.THREADED_SYNTHESIS;
        }
    }

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

        GTMachineArchetype archetype = deductArchetype(id, def, mCls, scannedAbilities, isMb);

        GTVoltageTier turbineTier = null;
        double baseEnergy = 0.0;
        if (archetype == GTMachineArchetype.TURBINE) {
            GTCEuReflectionBridge.TurbineSpecs specs = GTCEuReflectionBridge.deductTurbineSpecs(def);
            turbineTier = specs.tier();
            baseEnergy = specs.baseEnergy();
        }

        boolean isSteam = archetype == GTMachineArchetype.STEAM_MACHINE;
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
        if (archetype == GTMachineArchetype.COIL_HEATED) abilities.add("HEATING_COILS");
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
                archetype,
                isMb,
                turbineTier,
                baseEnergy,
                supportsParallelHatch,
                supportsBatchMode,
                supportsThroughputBoosting,
                supportsBulkProcessing,
                supportsOverpressure,
                supportsLaserHatch,
                steamDrainRate,
                innatePar,
                Collections.unmodifiableSet(abilities)
        );
    }

    public static GTMachineArchetype deductArchetype(
            ResourceLocation id,
            Object def,
            Class<?> mCls,
            Set<String> abilities,
            boolean isMb
    ) {
        boolean isGen = GTCEuReflectionBridge.isGenerator(def);

        if (isGen) {
            if (isTurbineContract(mCls, abilities, def)) {
                return GTMachineArchetype.TURBINE;
            }
            if (isCombustionContract(mCls, id)) {
                return GTMachineArchetype.COMBUSTION_GENERATOR;
            }
            return GTMachineArchetype.STANDARD_PROCESSING;
        }

        if (isMb) {
            if (isCoilContract(mCls, abilities, id)) {
                return GTMachineArchetype.COIL_HEATED;
            }
            if (isFusionContract(abilities, id)) {
                return GTMachineArchetype.FUSION_REACTOR;
            }
            if (isThreadedContract(abilities, id)) {
                return GTMachineArchetype.THREADED_SYNTHESIS;
            }
        }

        if (isSteamContract(def, id)) {
            return GTMachineArchetype.STEAM_MACHINE;
        }

        return GTMachineArchetype.STANDARD_PROCESSING;
    }

    private static boolean isTurbineContract(Class<?> mCls, Set<String> abilities, Object def) {
        if (GTCEuReflectionBridge.isLargeTurbineClass(mCls) || GTCEuReflectionBridge.isITurbineClass(mCls)) {
            return true;
        }
        return abilities.contains("ROTOR_HOLDER") && GTCEuReflectionBridge.hasTurbineSignature(def);
    }

    private static boolean isCombustionContract(Class<?> mCls, ResourceLocation id) {
        if (mCls != null && GTCEuReflectionBridge.isCombustionMachineClass(mCls)) {
            return true;
        }
        return GTCombustionHelper.isCombustionEngine(id) || GTCombustionHelper.isSingleblockCombustionGenerator(id);
    }

    private static boolean isCoilContract(Class<?> mCls, Set<String> abilities, ResourceLocation id) {
        if (mCls != null && GTCEuReflectionBridge.isCoilWorkableClass(mCls)) {
            return true;
        }
        if (abilities.contains("HEATING_COILS")) {
            return true;
        }
        if (GTCEuCoilModifierHelper.getCoilMachineSpec(id).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC) {
            return true;
        }
        return MultiblockDetector.isCoilMultiblock(id);
    }

    private static boolean isFusionContract(Set<String> abilities, ResourceLocation id) {
        if (abilities.contains("REFLECTOR")) return true;
        String path = id != null ? id.getPath().toLowerCase(Locale.ROOT) : "";
        return path.endsWith("fusion_reactor") || path.contains("auxiliary_fusion");
    }

    private static boolean isThreadedContract(Set<String> abilities, ResourceLocation id) {
        if (abilities.contains("THREADING") || abilities.contains("HELIX")) return true;
        return MultiblockDetector.isThreadingMultiblock(id);
    }

    private static boolean isSteamContract(Object def, ResourceLocation id) {
        return MultiblockDetector.isSteamMultiblock(id) || GTCEuReflectionBridge.isSteamMachine(def);
    }

    private static MachineCapabilities emptyCapabilities(ResourceLocation id) {
        return new MachineCapabilities(
                id, GTMachineArchetype.STANDARD_PROCESSING, false, null, 0.0,
                false, false, false, false, false, false, 0.0, 1, Collections.emptySet()
        );
    }
}
