package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.RecipeNode;
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

    private static final Map<ResourceLocation, CoilMachineSpec> SPEC_CACHE = new ConcurrentHashMap<>();

    public static CoilMachineSpec getCoilMachineSpec(ResourceLocation machineId) {
        if (machineId == null) return CoilMachineSpec.GENERIC;
        return SPEC_CACHE.computeIfAbsent(machineId, GTCEuCoilModifierHelper::inspectMachineDefinition);
    }

    private static CoilMachineSpec inspectMachineDefinition(ResourceLocation machineId) {
        try {
            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
            Method mGet = machinesRegistry.getClass().getMethod("get", ResourceLocation.class);
            Object def = mGet.invoke(machinesRegistry, machineId);
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
            Method mGetModifiers = null;
            for (Method m : def.getClass().getMethods()) {
                if (m.getName().equals("getRecipeModifiers") && m.getParameterCount() == 0) {
                    mGetModifiers = m;
                    break;
                }
            }
            if (mGetModifiers != null) {
                mGetModifiers.setAccessible(true);
                Object modifiersObj = mGetModifiers.invoke(def);
                if (modifiersObj instanceof Object[] arr) {
                    for (Object mod : arr) {
                        CoilMachineKind k = classifyModifierObject(mod);
                        if (k != null) return k;
                    }
                } else if (modifiersObj instanceof Iterable<?> it) {
                    for (Object mod : it) {
                        CoilMachineKind k = classifyModifierObject(mod);
                        if (k != null) return k;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static CoilMachineKind classifyModifierObject(Object modifier) {
        if (modifier == null) return null;
        try {
            Class<?> gtModifiersCls = Class.forName("com.gregtechceu.gtceu.common.data.GTRecipeModifiers");
            for (Field f : gtModifiersCls.getFields()) {
                Object stdMod = f.get(null);
                if (stdMod != null && (stdMod == modifier || stdMod.equals(modifier))) {
                    String fName = f.getName().toUpperCase(Locale.ROOT);
                    if (fName.contains("ELECTRIC_BLAST_FURNACE") || fName.contains("EBF")) return CoilMachineKind.BLAST_FURNACE;
                    if (fName.contains("PYROLYSE_OVEN") || fName.contains("PYROLYSE")) return CoilMachineKind.PYROLYSE_OVEN;
                    if (fName.contains("CRACKING_UNIT") || fName.contains("CRACKING") || fName.contains("CRACKER")) return CoilMachineKind.CRACKING_UNIT;
                    if (fName.contains("CHEMICAL_PLANT") || fName.contains("CHEMICAL_REACTOR") || fName.contains("LCR")) return CoilMachineKind.CHEMICAL_REACTOR;
                    if (fName.contains("MULTI_SMELTER") || fName.contains("SMELTER")) return CoilMachineKind.MULTI_SMELTER;
                }
            }
        } catch (Throwable ignored) {}

        String modStr = modifier.getClass().getName().toLowerCase(Locale.ROOT) + " " + modifier.toString().toLowerCase(Locale.ROOT);
        if (modStr.contains("blast") || modStr.contains("ebf")) return CoilMachineKind.BLAST_FURNACE;
        if (modStr.contains("pyrolyse")) return CoilMachineKind.PYROLYSE_OVEN;
        if (modStr.contains("crack")) return CoilMachineKind.CRACKING_UNIT;
        if (modStr.contains("chemical")) return CoilMachineKind.CHEMICAL_REACTOR;
        if (modStr.contains("smelter")) return CoilMachineKind.MULTI_SMELTER;

        return null;
    }

    private static Class<?> extractMachineClass(Object def) {
        try {
            for (Field f : def.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(def);
                if (val instanceof Class<?> c) return c;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static CoilMachineKind classifyByMachineClass(Class<?> machineClass) {
        if (machineClass == null) return CoilMachineKind.GENERIC;
        String name = machineClass.getName().toLowerCase(Locale.ROOT);

        if (name.contains("electricblastfurnace") || name.contains("blastfurnace")) return CoilMachineKind.BLAST_FURNACE;
        if (name.contains("pyrolyseoven") || name.contains("pyrolyse")) return CoilMachineKind.PYROLYSE_OVEN;
        if (name.contains("crackingunit") || name.contains("cracker")) return CoilMachineKind.CRACKING_UNIT;
        if (name.contains("chemicalplant") || name.contains("chemicalreactor")) return CoilMachineKind.CHEMICAL_REACTOR;
        if (name.contains("multismelter") || name.contains("smeltermachine")) return CoilMachineKind.MULTI_SMELTER;

        try {
            Class<?> coilWorkableCls = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine");
            if (coilWorkableCls.isAssignableFrom(machineClass)) {
                return CoilMachineKind.CUSTOM_COIL_MULTIBLOCK;
            }
        } catch (Throwable ignored) {}

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

        if (path.contains("blast_furnace") || path.contains("ebf")) {
            return new CoilMachineSpec(CoilMachineKind.BLAST_FURNACE, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("pyrolyse")) {
            return new CoilMachineSpec(CoilMachineKind.PYROLYSE_OVEN, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("cracker") || path.contains("cracking")) {
            return new CoilMachineSpec(CoilMachineKind.CRACKING_UNIT, CustomCoilMultiplier.DEFAULT);
        }
        if (path.contains("chemical") || path.contains("lcr") || path.contains("ecr") || path.contains("icr")) {
            return new CoilMachineSpec(CoilMachineKind.CHEMICAL_REACTOR, CustomCoilMultiplier.DEFAULT);
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
            targetId = node.getRecipeCategoryId();
        }

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
