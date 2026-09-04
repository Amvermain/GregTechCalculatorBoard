package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.start.StarTReflectionBridge;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic runtime reflection helper for GTCEu Modern coil-based recipe modifiers.
 * Replaces heuristic name/path matching with direct Java class hierarchy inspection,
 * GTRecipeModifiers function object matching, and custom machine field deduction.
 */
public final class GTCEuCoilModifierHelper {

    private GTCEuCoilModifierHelper() {}

    private static boolean isStarTCoilReactor() {
        return ModCompatHelper.isStarTLoaded();
    }

    public enum CoilMachineKind {
        BLAST_FURNACE,
        PYROLYSE_OVEN,
        CRACKING_UNIT,
        CHEMICAL_REACTOR,
        MULTI_SMELTER,
        CUSTOM_COIL_MULTIBLOCK,
        GENERIC
    }

    public record CustomCoilMultiplier(
            double durationMultiplier,
            double energyMultiplier,
            int parallelMultiplier,
            int baseParallel
    ) {
        public static final CustomCoilMultiplier DEFAULT = new CustomCoilMultiplier(0.0, 0.0, 0, 1);
    }

    public record CoilMachineSpec(
            CoilMachineKind kind,
            CustomCoilMultiplier customMultiplier
    ) {
        public static final CoilMachineSpec GENERIC = new CoilMachineSpec(CoilMachineKind.GENERIC, CustomCoilMultiplier.DEFAULT);
    }

    private static final Class<?> COIL_WORKABLE_CLS;
    private static final Map<ResourceLocation, CoilMachineSpec> SPEC_CACHE = new ConcurrentHashMap<>();

    static {
        Class<?> cls = null;
        try {
            cls = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine");
        } catch (Throwable t) {
            try {
                cls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.electric.CoilWorkableElectricMultiblockMachine");
            } catch (Throwable ignored) {}
        }
        COIL_WORKABLE_CLS = cls;
    }

    public static CoilMachineSpec getCoilMachineSpec(ResourceLocation machineId) {
        if (machineId == null) return CoilMachineSpec.GENERIC;
        return SPEC_CACHE.computeIfAbsent(machineId, GTCEuCoilModifierHelper::inspectMachineDefinition);
    }

    public static void clearCache() {
        SPEC_CACHE.clear();
    }

    private static CoilMachineSpec inspectMachineDefinition(ResourceLocation machineId) {
        try {
            Object def = GTCEuReflectionBridge.getMachineDefinition(machineId);
            if (def == null) return inspectFallback(machineId);

            // 1. Inspect registered recipeModifiers function objects
            CoilMachineKind kindFromModifiers = inspectRecipeModifiers(def);
            if (kindFromModifiers != null && kindFromModifiers != CoilMachineKind.GENERIC) {
                return new CoilMachineSpec(kindFromModifiers, CustomCoilMultiplier.DEFAULT);
            }

            // 2. Inspect MetaMachine class hierarchy
            Class<?> machineClass = extractMachineClass(def);
            if (machineClass != null) {
                CoilMachineKind kindFromClass = classifyByMachineClass(machineClass);
                if (kindFromClass == CoilMachineKind.CUSTOM_COIL_MULTIBLOCK) {
                    CustomCoilMultiplier multiplier = extractCustomMultiplier(def, machineClass);
                    return new CoilMachineSpec(CoilMachineKind.CUSTOM_COIL_MULTIBLOCK, multiplier);
                } else if (kindFromClass != CoilMachineKind.GENERIC) {
                    return new CoilMachineSpec(kindFromClass, CustomCoilMultiplier.DEFAULT);
                }
            }
        } catch (Throwable ignored) {}

        return inspectFallback(machineId);
    }

    private static CoilMachineKind inspectRecipeModifiers(Object def) {
        try {
            List<Object> modifiers = GTCEuReflectionBridge.getRecipeModifiers(def);
            if (modifiers != null) {
                for (Object mod : modifiers) {
                    CoilMachineKind k = classifyModifierObject(mod);
                    if (k != null) return k;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static CoilMachineKind classifyModifierObject(Object modifier) {
        if (modifier == null) return null;
        String modId = GTCEuReflectionBridge.getRecipeModifierName(modifier);
        if (modId != null) {
            if ("EBF_OC".equals(modId) || "ELECTRIC_BLAST_FURNACE".equals(modId)) return CoilMachineKind.BLAST_FURNACE;
            if ("PYROLYSE_OVEN_OC".equals(modId) || "PYROLYSE_OVEN".equals(modId)) return CoilMachineKind.PYROLYSE_OVEN;
            if ("CRACKER_OC".equals(modId) || "CRACKING_UNIT".equals(modId)) return CoilMachineKind.CRACKING_UNIT;
            if ("CHEMICAL_REACTOR_OC".equals(modId) || "CHEMICAL_PLANT".equals(modId)) {
                return isStarTCoilReactor() ? CoilMachineKind.CHEMICAL_REACTOR : CoilMachineKind.GENERIC;
            }
            if ("MULTI_SMELLTER_PARALLEL".equals(modId) || "MULTI_SMELTER_PARALLEL".equals(modId) || "MULTI_SMELTER".equals(modId)) return CoilMachineKind.MULTI_SMELTER;
        }
        return null;
    }

    private static Class<?> extractMachineClass(Object def) {
        return GTCEuReflectionBridge.getMachineClass(def);
    }

    private static CoilMachineKind classifyByMachineClass(Class<?> machineClass) {
        if (machineClass == null) return CoilMachineKind.GENERIC;
        if (COIL_WORKABLE_CLS != null && COIL_WORKABLE_CLS.isAssignableFrom(machineClass)) {
            String name = machineClass.getSimpleName().toLowerCase(Locale.ROOT);
            if (name.contains("blastfurnace") || name.contains("ebf")) return CoilMachineKind.BLAST_FURNACE;
            if (name.contains("pyrolyse")) return CoilMachineKind.PYROLYSE_OVEN;
            if (name.contains("cracking") || name.contains("cracker")) return CoilMachineKind.CRACKING_UNIT;
            if (name.contains("chemical") || name.contains("reactor") || name.contains("plant")) {
                return isStarTCoilReactor() ? CoilMachineKind.CHEMICAL_REACTOR : CoilMachineKind.GENERIC;
            }
            if (name.contains("smelter")) return CoilMachineKind.MULTI_SMELTER;
            return CoilMachineKind.CUSTOM_COIL_MULTIBLOCK;
        }

        return CoilMachineKind.GENERIC;
    }

    private static CustomCoilMultiplier extractCustomMultiplier(Object def, Class<?> machineClass) {
        double durationMult = 0.0;
        double energyMult = 0.0;
        int parMult = 0;
        int basePar = 1;

        try {
            for (Field f : machineClass.getDeclaredFields()) {
                String fName = f.getName().toLowerCase(Locale.ROOT);
                f.setAccessible(true);
                if (fName.contains("durationmultiplier") || fName.contains("speedmultiplier")) {
                    durationMult = f.getDouble(null);
                } else if (fName.contains("energymultiplier") || fName.contains("eutmultiplier")) {
                    energyMult = f.getDouble(null);
                } else if (fName.contains("parallelmultiplier") || fName.contains("parallelperlevel")) {
                    parMult = f.getInt(null);
                } else if (fName.contains("baseparallel") || fName.contains("baseparallelism")) {
                    basePar = f.getInt(null);
                }
            }
        } catch (Throwable ignored) {}

        return new CustomCoilMultiplier(durationMult, energyMult, parMult, basePar);
    }

    private static CoilMachineSpec inspectFallback(ResourceLocation id) {
        if (id == null) return CoilMachineSpec.GENERIC;
        String path = id.getPath().toLowerCase(Locale.ROOT);

        if (path.contains("blast") || path.contains("ebf") || path.contains("abs") || path.contains("alloy_blast")) {
            return new CoilMachineSpec(CoilMachineKind.BLAST_FURNACE, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("pyrolyse")) {
            return new CoilMachineSpec(CoilMachineKind.PYROLYSE_OVEN, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("cracker") || path.contains("cracking") || path.contains("super_cracker")) {
            return new CoilMachineSpec(CoilMachineKind.CRACKING_UNIT, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("chemical") || path.contains("lcr") || path.contains("ecr") || path.contains("icr")) {
            return isStarTCoilReactor()
                    ? new CoilMachineSpec(CoilMachineKind.CHEMICAL_REACTOR, CustomCoilMultiplier.DEFAULT)
                    : CoilMachineSpec.GENERIC;
        }
        if (path.contains("smelter")) {
            return new CoilMachineSpec(CoilMachineKind.MULTI_SMELTER, CustomCoilMultiplier.DEFAULT);
        }

        return CoilMachineSpec.GENERIC;
    }

    public static void applyCoilModifiers(RecipeNode node, MachineAddon coilAddon) {
        if (node == null || coilAddon == null) return;

        ResourceLocation targetId = node.getMachineIcon();
        if (targetId == null) {
            targetId = node.getMultiblockWorkstation();
        }
        if (targetId == null && node.getRecipeCategoryId() != null) {
            var cap = com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap.defaultWorkstation() != null) {
                targetId = cap.defaultWorkstation();
            } else {
                targetId = node.getRecipeCategoryId();
            }
        }
        if (targetId == null) return;

        CoilMachineSpec spec = getCoilMachineSpec(targetId);
        int reqTemp = node.getRecipeTemperature();
        int coilTemp = coilAddon.getCoilTemperature();
        int pyroSpeed = coilAddon.getPyrolyseSpeedPercent();
        int crackEnergy = coilAddon.getCrackingEnergyPercent();
        int chemSpeed = coilAddon.getChemicalSpeedPercent();
        int chemEnergy = coilAddon.getChemicalEnergyPercent();
        int smelterPar = coilAddon.getSmelterParallel();

        switch (spec.kind()) {
            case BLAST_FURNACE -> {
                if (reqTemp > 0 && coilTemp > reqTemp) {
                    int excessTemp = coilTemp - reqTemp;
                    int tiersAbove = excessTemp / 900;
                    coilAddon.setEutMultiplier(Math.pow(0.95, tiersAbove));
                } else {
                    coilAddon.setEutMultiplier(1.0);
                }
                coilAddon.setDurationMultiplier(1.0);
                coilAddon.setParallelMultiplier(1);
            }
            case PYROLYSE_OVEN -> {
                coilAddon.setDurationMultiplier(100.0 / Math.max(1, pyroSpeed));
                coilAddon.setEutMultiplier(1.0);
                coilAddon.setParallelMultiplier(1);
            }
            case CRACKING_UNIT -> {
                coilAddon.setDurationMultiplier(1.0);
                coilAddon.setEutMultiplier(crackEnergy / 100.0);
                coilAddon.setParallelMultiplier(1);
            }
            case CHEMICAL_REACTOR -> {
                coilAddon.setDurationMultiplier(100.0 / Math.max(1, chemSpeed));
                coilAddon.setEutMultiplier(chemEnergy / 100.0);
                coilAddon.setParallelMultiplier(1);
            }
            case MULTI_SMELTER -> {
                coilAddon.setParallelMultiplier(Math.max(1, smelterPar));
                coilAddon.setDurationMultiplier(1.0);
                coilAddon.setEutMultiplier(1.0);
            }
            case CUSTOM_COIL_MULTIBLOCK -> {
                CustomCoilMultiplier cm = spec.customMultiplier();
                int coilLevel = Math.max(1, (coilTemp - 1800) / 900);
                double durMult = (cm.durationMultiplier() > 0) ? Math.max(0.01, 1.0 - (coilLevel * cm.durationMultiplier())) : 1.0;
                double eutMult = (cm.energyMultiplier() > 0) ? Math.max(0.01, 1.0 - (coilLevel * cm.energyMultiplier())) : 1.0;
                int par = (cm.parallelMultiplier() > 0) ? (cm.baseParallel() + (coilLevel * cm.parallelMultiplier())) : 1;

                coilAddon.setDurationMultiplier(durMult);
                coilAddon.setEutMultiplier(eutMult);
                coilAddon.setParallelMultiplier(par);
            }
            case GENERIC -> {
                if (reqTemp > 0 && coilTemp > reqTemp) {
                    int excessTemp = coilTemp - reqTemp;
                    int tiersAbove = excessTemp / 900;
                    coilAddon.setEutMultiplier(Math.pow(0.95, tiersAbove));
                } else {
                    coilAddon.setEutMultiplier(1.0);
                }
                coilAddon.setDurationMultiplier(1.0);
                coilAddon.setParallelMultiplier(1);
            }
        }
    }
}

