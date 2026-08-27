package com.gtceu.calcboard.compat.gtceu.helper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Deterministic runtime reflection scanner for GTCEu Modern BlockPattern and TraceabilityPredicate.
 * Extracts all supported PartAbility types and candidate structure blocks without heuristics.
 */
public final class GTCEuPatternScanner {

    private GTCEuPatternScanner() {}

    public record PatternScanResult(
            Set<String> allowedAbilities,
            Set<ResourceLocation> candidateBlocks,
            int maxEnergyHatches,
            int maxMaintenanceHatches,
            int maxParallelHatches
    ) {
        public static final PatternScanResult EMPTY = new PatternScanResult(Set.of(), Set.of(), 0, 0, 0);
    }

    public static PatternScanResult scanPattern(Object machineDef) {
        if (machineDef == null) return PatternScanResult.EMPTY;

        try {
            Set<String> abilities = new HashSet<>();
            Set<ResourceLocation> candidateBlocks = new HashSet<>();
            int maxEnergy = 0;
            int maxMaint = 0;
            int maxParallel = 0;

            Object pattern = extractBlockPattern(machineDef);
            if (pattern != null) {
                // 1. Scan pattern predicate map (where clauses)
                Map<?, ?> predicatesMap = extractPredicatesMap(pattern);
                if (predicatesMap != null) {
                    for (Object predicateObj : predicatesMap.values()) {
                        if (predicateObj == null) continue;
                        inspectPredicate(predicateObj, abilities, candidateBlocks);
                    }
                }
            }

            // 2. Deduce abilities from MultiblockMachineDefinition recipeTypes and generator properties
            enrichFromMachineDefinition(machineDef, abilities);

            return new PatternScanResult(
                    Collections.unmodifiableSet(abilities),
                    Collections.unmodifiableSet(candidateBlocks),
                    maxEnergy,
                    maxMaint,
                    maxParallel
            );
        } catch (Throwable ignored) {
            return PatternScanResult.EMPTY;
        }
    }

    private static Object extractBlockPattern(Object machineDef) {
        try {
            for (Method m : machineDef.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && (m.getName().equals("getPattern") || m.getName().equals("getStructurePattern"))) {
                    m.setAccessible(true);
                    Object res = m.invoke(machineDef);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Field f : machineDef.getClass().getDeclaredFields()) {
                if (f.getName().toLowerCase(Locale.ROOT).contains("pattern")) {
                    f.setAccessible(true);
                    Object val = f.get(machineDef);
                    if (val instanceof Function<?, ?> func) {
                        try {
                            @SuppressWarnings("unchecked")
                            Function<Object, Object> pFunc = (Function<Object, Object>) func;
                            Object res = pFunc.apply(machineDef);
                            if (res != null) return res;
                        } catch (Throwable ignored) {}
                    } else if (val != null) {
                        return val;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static Map<?, ?> extractPredicatesMap(Object pattern) {
        if (pattern == null) return null;
        for (Field f : pattern.getClass().getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                try {
                    Object map = f.get(pattern);
                    if (map instanceof Map<?, ?> m && !m.isEmpty()) {
                        return m;
                    }
                } catch (Throwable ignored) {}
            }
        }
        for (Method m : pattern.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && Map.class.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    Object map = m.invoke(pattern);
                    if (map instanceof Map<?, ?> resMap && !resMap.isEmpty()) {
                        return resMap;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static void inspectPredicate(Object pred, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (pred == null) return;
        Class<?> cls = pred.getClass();
        String clsName = cls.getName().toLowerCase(Locale.ROOT);

        // 1. Check for PartAbility fields/methods
        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object val = f.get(pred);
                if (val != null) {
                    extractAbilityFromObject(val, abilities);
                    extractBlocksFromObject(val, candidateBlocks);
                }
            } catch (Throwable ignored) {}
        }

        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() == 0) {
                try {
                    String mName = m.getName().toLowerCase(Locale.ROOT);
                    if (mName.contains("ability") || mName.contains("abilities") || mName.contains("part")) {
                        m.setAccessible(true);
                        Object res = m.invoke(pred);
                        extractAbilityFromObject(res, abilities);
                    } else if (mName.contains("block") || mName.contains("candidate") || mName.contains("state")) {
                        m.setAccessible(true);
                        Object res = m.invoke(pred);
                        extractBlocksFromObject(res, candidateBlocks);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Recursively inspect inner / chained predicates (e.g. OrPredicate, AndPredicate)
        if (clsName.contains("or") || clsName.contains("and") || clsName.contains("composite") || clsName.contains("chain")) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().isArray() || Collection.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Object container = f.get(pred);
                        if (container instanceof Object[] arr) {
                            for (Object inner : arr) inspectPredicate(inner, abilities, candidateBlocks);
                        } else if (container instanceof Iterable<?> it) {
                            for (Object inner : it) inspectPredicate(inner, abilities, candidateBlocks);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void extractAbilityFromObject(Object obj, Set<String> abilities) {
        if (obj == null) return;
        if (obj instanceof Enum<?> e) {
            abilities.add(e.name().toUpperCase(Locale.ROOT));
        } else if (obj instanceof Iterable<?> it) {
            for (Object item : it) extractAbilityFromObject(item, abilities);
        } else if (obj.getClass().isArray()) {
            for (Object item : (Object[]) obj) extractAbilityFromObject(item, abilities);
        } else {
            try {
                Method mGetName = obj.getClass().getMethod("getName");
                Object nameRes = mGetName.invoke(obj);
                if (nameRes != null) {
                    abilities.add(nameRes.toString().trim().toUpperCase(Locale.ROOT));
                }
            } catch (Throwable ignored) {}
            try {
                Method mName = obj.getClass().getMethod("name");
                Object nameRes = mName.invoke(obj);
                if (nameRes != null) {
                    abilities.add(nameRes.toString().trim().toUpperCase(Locale.ROOT));
                }
            } catch (Throwable ignored) {}

            String str = obj.toString().toUpperCase(Locale.ROOT);
            if (str.contains("INPUT_ENERGY") || str.contains("IMPORT_ITEMS") || str.contains("EXPORT_ITEMS")
                    || str.contains("IMPORT_FLUIDS") || str.contains("EXPORT_FLUIDS") || str.contains("MAINTENANCE")
                    || str.contains("PARALLEL_HATCH") || str.contains("ROTOR_HOLDER") || str.contains("MUFFLER")
                    || str.contains("LASER") || str.contains("SUBSTATION") || str.contains("OPTICAL")
                    || str.contains("COMPUTATION") || str.contains("STEAM")
                    || str.contains("THREADING") || str.contains("THREAD") || str.contains("HELIX")) {
                for (String token : str.split("[,\\s\\[\\]()]+")) {
                    if (!token.isBlank()) abilities.add(token);
                }
            }
        }
    }

    private static void extractBlocksFromObject(Object obj, Set<ResourceLocation> candidateBlocks) {
        if (obj == null) return;
        if (obj instanceof Block b) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            if (id != null && !id.getPath().equals("air")) candidateBlocks.add(id);
        } else if (obj instanceof BlockState bs) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bs.getBlock());
            if (id != null && !id.getPath().equals("air")) candidateBlocks.add(id);
        } else if (obj instanceof Supplier<?> s) {
            try {
                Object res = s.get();
                extractBlocksFromObject(res, candidateBlocks);
            } catch (Throwable ignored) {}
        } else if (obj instanceof Iterable<?> it) {
            for (Object item : it) extractBlocksFromObject(item, candidateBlocks);
        } else if (obj.getClass().isArray()) {
            for (Object item : (Object[]) obj) extractBlocksFromObject(item, candidateBlocks);
        } else {
            String objName = obj.getClass().getName().toLowerCase(Locale.ROOT);
            if (objName.contains("startthreading") || objName.contains("threadingstats")) {
                try {
                    Class<?> statBlocksCls = Class.forName("com.startechnology.start_core.machine.threading.StarTThreadingStatBlocks");
                    Field fStatBlocks = statBlocksCls.getField("statBlocks");
                    Object list = fStatBlocks.get(null);
                    if (list instanceof Iterable<?> it) {
                        for (Object entry : it) {
                            if (entry instanceof Supplier<?> sup) {
                                Object b = sup.get();
                                if (b instanceof Block block) {
                                    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                                    if (id != null) candidateBlocks.add(id);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void enrichFromMachineDefinition(Object machineDef, Set<String> abilities) {
        if (machineDef == null) return;
        try {
            boolean hasRecipeTypes = false;
            for (Method m : machineDef.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && (m.getName().equals("getRecipeTypes") || m.getName().equals("getRecipeType"))) {
                    m.setAccessible(true);
                    Object rTypes = m.invoke(machineDef);
                    if (rTypes != null) {
                        if (rTypes.getClass().isArray() && ((Object[]) rTypes).length > 0) {
                            hasRecipeTypes = true;
                        } else if (rTypes instanceof Collection<?> col && !col.isEmpty()) {
                            hasRecipeTypes = true;
                        } else if (!rTypes.getClass().isArray()) {
                            hasRecipeTypes = true;
                        }
                    }
                }
            }

            boolean isGen = false;
            for (Method m : machineDef.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && (m.getName().equals("isGenerator") || m.getName().equals("isGeneratorMachine"))) {
                    m.setAccessible(true);
                    Object res = m.invoke(machineDef);
                    if (res instanceof Boolean b && b) {
                        isGen = true;
                    }
                }
            }

            // Check if machine definition or modifiers indicate threading capability
            try {
                Method mModifiers = machineDef.getClass().getMethod("getRecipeModifiers");
                Object mods = mModifiers.invoke(machineDef);
                if (mods != null && mods.toString().toUpperCase(Locale.ROOT).contains("THREADING")) {
                    abilities.add("THREADING");
                }
            } catch (Throwable ignored) {}

            if (hasRecipeTypes) {
                abilities.add("IMPORT_ITEMS");
                abilities.add("EXPORT_ITEMS");
                abilities.add("IMPORT_FLUIDS");
                abilities.add("EXPORT_FLUIDS");
                abilities.add("MAINTENANCE");
                abilities.add("PARALLEL_HATCH");
                if (isGen) {
                    abilities.add("OUTPUT_ENERGY");
                } else {
                    abilities.add("INPUT_ENERGY");
                }
            }
        } catch (Throwable ignored) {}
    }
}
