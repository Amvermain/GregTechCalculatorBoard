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
        LIQUEFACTION_TOWER,
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

            Class<?> machineClass = extractMachineClass(def);
            boolean isCoilCandidate = (machineClass != null && GTCEuReflectionBridge.isCoilWorkableClass(machineClass))
                    || GTCEuReflectionBridge.isMultiblockDefinition(def);
            if (!isCoilCandidate) {
                return CoilMachineSpec.GENERIC;
            }

            // 1. Inspect registered recipeModifiers function objects
            CoilMachineKind kindFromModifiers = inspectRecipeModifiers(def);
            if (kindFromModifiers != null && kindFromModifiers != CoilMachineKind.GENERIC) {
                return new CoilMachineSpec(kindFromModifiers, CustomCoilMultiplier.DEFAULT);
            }

            // 1.5. Inspect machine's registered RecipeTypes
            CoilMachineKind kindFromRecipeTypes = inspectRecipeTypes(def);
            if (kindFromRecipeTypes != null && kindFromRecipeTypes != CoilMachineKind.GENERIC) {
                return new CoilMachineSpec(kindFromRecipeTypes, CustomCoilMultiplier.DEFAULT);
            }

            // 2. Inspect MetaMachine class hierarchy
            if (machineClass != null) {
                CoilMachineKind kindFromClass = classifyByMachineClass(machineClass);
                if (kindFromClass == CoilMachineKind.CUSTOM_COIL_MULTIBLOCK) {
                    CustomCoilMultiplier multiplier = extractCustomMultiplier(def, machineClass);
                    if (multiplier.durationMultiplier() > 0 || multiplier.energyMultiplier() > 0 || multiplier.parallelMultiplier() > 0) {
                        return new CoilMachineSpec(CoilMachineKind.CUSTOM_COIL_MULTIBLOCK, multiplier);
                    }
                    return new CoilMachineSpec(CoilMachineKind.GENERIC, CustomCoilMultiplier.DEFAULT);
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

    public static CoilMachineKind classifyModifierObject(Object modifier) {
        if (modifier == null) return null;
        String modId = GTCEuReflectionBridge.getRecipeModifierName(modifier);
        if (modId != null && !modId.isBlank()) {
            CoilMachineKind directKind = matchModifierDescriptor(modId.toLowerCase(Locale.ROOT));
            if (directKind != null) return directKind;
        }

        String desc = extractModifierDescriptor(modifier);
        return matchModifierDescriptor(desc);
    }

    private static String extractModifierDescriptor(Object modifier) {
        StringBuilder sb = new StringBuilder();
        collectDescriptor(modifier, sb, 0);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void collectDescriptor(Object obj, StringBuilder sb, int depth) {
        if (obj == null || depth > 2) return;
        sb.append(obj.getClass().getName()).append(' ').append(obj).append(' ');
        for (Field f : obj.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val != null && val != obj) {
                    sb.append(val.getClass().getName()).append(' ').append(val).append(' ');
                    if (depth < 1 && (val.getClass().getName().contains("rhino") || val.getClass().getName().contains("kubejs"))) {
                        collectDescriptor(val, sb, depth + 1);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static CoilMachineKind matchModifierDescriptor(String desc) {
        if (desc.contains("pyrolyse") || desc.contains("liquefaction")) return CoilMachineKind.PYROLYSE_OVEN;
        if (desc.contains("ebf") || desc.contains("blastfurnace") || desc.contains("blast_furnace")) return CoilMachineKind.BLAST_FURNACE;
        if (desc.contains("cracker") || desc.contains("cracking")) return CoilMachineKind.CRACKING_UNIT;
        if (desc.contains("smelter") || desc.contains("multismelter")) return CoilMachineKind.MULTI_SMELTER;
        if (desc.contains("chemical") || desc.contains("chemicalreactor")) {
            return isStarTCoilReactor() ? CoilMachineKind.CHEMICAL_REACTOR : CoilMachineKind.GENERIC;
        }
        return null;
    }

    private static CoilMachineKind inspectRecipeTypes(Object def) {
        try {
            List<Object> recipeTypes = GTCEuReflectionBridge.getRecipeTypes(def);
            if (recipeTypes != null) {
                for (Object rt : recipeTypes) {
                    ResourceLocation rtId = com.gtceu.calcboard.api.catalog.MultiblockDetector.extractRecipeTypeId(rt);
                    if (rtId == null) continue;
                    String path = rtId.getPath().toLowerCase(Locale.ROOT);
                    if (path.contains("pyrolyse") || path.contains("liquefaction")) return CoilMachineKind.PYROLYSE_OVEN;
                    if (path.contains("blast") || path.contains("ebf")) return CoilMachineKind.BLAST_FURNACE;
                    if (path.contains("cracker") || path.contains("cracking")) return CoilMachineKind.CRACKING_UNIT;
                    if (path.contains("smelter")) return CoilMachineKind.MULTI_SMELTER;
                    if (path.contains("chemical") && isStarTCoilReactor()) return CoilMachineKind.CHEMICAL_REACTOR;
                }
            }
        } catch (Throwable ignored) {}
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

        if (path.contains("pyrolyse") || path.contains("liquefaction") || path.contains("nuclear") || path.contains("fuel_factory")) {
            return new CoilMachineSpec(CoilMachineKind.PYROLYSE_OVEN, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("electric_blast_furnace") || path.contains("blast_furnace") || path.contains("ebf") || path.contains("abs") || path.contains("alloy_blast")) {
            return new CoilMachineSpec(CoilMachineKind.BLAST_FURNACE, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("cracker") || path.contains("cracking") || path.contains("super_cracker")) {
            return new CoilMachineSpec(CoilMachineKind.CRACKING_UNIT, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("large_chemical") || path.contains("lcr") || path.contains("ecr") || path.contains("icr")) {
            return isStarTCoilReactor()
                    ? new CoilMachineSpec(CoilMachineKind.CHEMICAL_REACTOR, CustomCoilMultiplier.DEFAULT)
                    : CoilMachineSpec.GENERIC;
        }
        if (path.contains("multi_smelter") || path.contains("multismelter")) {
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
            case PYROLYSE_OVEN, LIQUEFACTION_TOWER -> {
                double speedMult;
                if (pyroSpeed > 0 && (pyroSpeed != 100 || coilTemp >= 2700)) {
                    speedMult = pyroSpeed / 100.0;
                } else if (coilTemp < 2700) {
                    speedMult = 0.75;
                } else {
                    int tiersAboveKanthal = Math.max(0, (coilTemp - 2700) / 900);
                    speedMult = 1.0 + (0.50 * tiersAboveKanthal);
                }
                coilAddon.setDurationMultiplier(1.0 / Math.max(0.01, speedMult));
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

