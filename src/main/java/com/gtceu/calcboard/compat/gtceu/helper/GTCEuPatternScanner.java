package com.gtceu.calcboard.compat.gtceu.helper;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateStates;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;

public final class GTCEuPatternScanner {

    private GTCEuPatternScanner() {}

    private static final Field BLOCK_MATCHES_FIELD;
    private static final Field CANDIDATES_FIELD;
    private static final Method PART_ABILITY_IS_APPLICABLE;
    private static final Map<Object, String> KNOWN_PART_ABILITIES = new IdentityHashMap<>();
    private static final Field STAT_BLOCKS_FIELD;

    static {
        Field fMatches = null;
        try {
            fMatches = BlockPattern.class.getDeclaredField("blockMatches");
            fMatches.setAccessible(true);
        } catch (Throwable ignored) {}
        BLOCK_MATCHES_FIELD = fMatches;

        Field fCand = null;
        try {
            fCand = SimplePredicate.class.getField("candidates");
        } catch (Throwable ignored) {}
        CANDIDATES_FIELD = fCand;

        Method mIsApplicable = null;
        try {
            Class<?> partAbilityCls = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.PartAbility");
            mIsApplicable = partAbilityCls.getMethod("isApplicable", Block.class);
            for (Field f : partAbilityCls.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) && partAbilityCls.isAssignableFrom(f.getType())) {
                    Object abilityObj = f.get(null);
                    if (abilityObj != null) {
                        KNOWN_PART_ABILITIES.put(abilityObj, f.getName().toUpperCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {}
        PART_ABILITY_IS_APPLICABLE = mIsApplicable;

        Field fStat = null;
        try {
            Class<?> statBlocksCls = Class.forName("com.startechnology.start_core.machine.threading.StarTThreadingStatBlocks");
            fStat = statBlocksCls.getField("statBlocks");
        } catch (Throwable ignored) {}
        STAT_BLOCKS_FIELD = fStat;
    }

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
        if (!(machineDef instanceof MultiblockMachineDefinition multiDef)) {
            return PatternScanResult.EMPTY;
        }

        Set<String> abilities = new HashSet<>();
        Set<ResourceLocation> candidateBlocks = new HashSet<>();

        scanPatternFactory(multiDef, abilities, candidateBlocks);
        enrichFromMachineDefinition(multiDef, abilities);

        return new PatternScanResult(
                Collections.unmodifiableSet(abilities),
                Collections.unmodifiableSet(candidateBlocks),
                0, 0, 0
        );
    }

    private static void scanPatternFactory(MultiblockMachineDefinition multiDef, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (multiDef.getPatternFactory() == null) return;
        BlockPattern pattern = multiDef.getPatternFactory().get();
        if (pattern == null) return;

        TraceabilityPredicate[][][] matches = extractBlockMatches(pattern);
        if (matches != null) {
            scanGrid(matches, abilities, candidateBlocks);
        }
    }

    private static TraceabilityPredicate[][][] extractBlockMatches(BlockPattern pattern) {
        if (BLOCK_MATCHES_FIELD == null) return null;
        try {
            return (TraceabilityPredicate[][][]) BLOCK_MATCHES_FIELD.get(pattern);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void scanGrid(TraceabilityPredicate[][][] grid, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        for (TraceabilityPredicate[][] plane : grid) {
            if (plane != null) scanPlane(plane, abilities, candidateBlocks);
        }
    }

    private static void scanPlane(TraceabilityPredicate[][] plane, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        for (TraceabilityPredicate[] row : plane) {
            if (row != null) scanRow(row, abilities, candidateBlocks);
        }
    }

    private static void scanRow(TraceabilityPredicate[] row, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        for (TraceabilityPredicate pred : row) {
            if (pred != null) scanTraceabilityPredicate(pred, abilities, candidateBlocks);
        }
    }

    private static void scanTraceabilityPredicate(TraceabilityPredicate pred, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        scanSimpleList(pred.common, abilities, candidateBlocks);
        scanSimpleList(pred.limited, abilities, candidateBlocks);
    }

    private static void scanSimpleList(List<SimplePredicate> list, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (list == null) return;
        for (SimplePredicate sp : list) {
            if (sp != null) scanSimplePredicate(sp, abilities, candidateBlocks);
        }
    }

    private static void scanSimplePredicate(SimplePredicate sp, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (sp.type != null && !sp.type.isBlank()) {
            abilities.add(sp.type.toUpperCase(Locale.ROOT));
        }

        if (sp instanceof PredicateBlocks pb) {
            scanBlocksArray(pb.blocks, abilities, candidateBlocks);
            return;
        }

        if (sp instanceof PredicateStates ps) {
            scanStatesArray(ps.states, abilities, candidateBlocks);
            return;
        }

        scanCandidatesSupplier(sp, abilities, candidateBlocks);
    }

    private static void scanBlocksArray(Block[] blocks, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (blocks == null) return;
        for (Block b : blocks) {
            scanBlock(b, abilities, candidateBlocks);
        }
    }

    private static void scanStatesArray(BlockState[] states, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (states == null) return;
        for (BlockState bs : states) {
            if (bs != null) scanBlock(bs.getBlock(), abilities, candidateBlocks);
        }
    }

    private static void scanCandidatesSupplier(SimplePredicate sp, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (CANDIDATES_FIELD == null) return;
        try {
            Object rawSupplier = CANDIDATES_FIELD.get(sp);
            if (!(rawSupplier instanceof Supplier<?> supplier)) return;
            Object rawInfos = supplier.get();
            if (!(rawInfos instanceof Object[] arr)) return;

            for (Object item : arr) {
                if (item != null) scanCandidateItem(item, abilities, candidateBlocks);
            }
        } catch (Throwable ignored) {}
    }

    private static void scanCandidateItem(Object item, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (item instanceof Block b) {
            scanBlock(b, abilities, candidateBlocks);
            return;
        }
        if (item instanceof BlockState bs) {
            scanBlock(bs.getBlock(), abilities, candidateBlocks);
            return;
        }
        try {
            Method mGetBlockState = item.getClass().getMethod("getBlockState");
            Object bState = mGetBlockState.invoke(item);
            if (bState instanceof BlockState bs) {
                scanBlock(bs.getBlock(), abilities, candidateBlocks);
            }
        } catch (Throwable ignored) {}
    }

    private static void scanBlock(Block b, Set<String> abilities, Set<ResourceLocation> candidateBlocks) {
        if (b == null) return;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        if (id != null && !id.getPath().equals("air")) {
            candidateBlocks.add(id);
        }

        if (GTCEuAPI.HEATING_COILS.containsKey(b)) {
            abilities.add("HEATING_COILS");
        }

        matchPartAbilities(b, abilities);
    }

    private static void matchPartAbilities(Block b, Set<String> abilities) {
        if (PART_ABILITY_IS_APPLICABLE == null || KNOWN_PART_ABILITIES.isEmpty()) return;
        for (Map.Entry<Object, String> entry : KNOWN_PART_ABILITIES.entrySet()) {
            try {
                Object isApp = PART_ABILITY_IS_APPLICABLE.invoke(entry.getKey(), b);
                if (isApp instanceof Boolean bool && bool) {
                    abilities.add(entry.getValue());
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void enrichFromMachineDefinition(MultiblockMachineDefinition multiDef, Set<String> abilities) {
        if (GTCEuReflectionBridge.isCoilWorkableClass(GTCEuReflectionBridge.getMachineClass(multiDef))) {
            abilities.add("HEATING_COILS");
        }
        enrichModifierAbilities(multiDef, abilities);
        enrichIoAbilities(multiDef, abilities);
        enrichStarTThreading(multiDef, abilities);
    }

    private static void enrichModifierAbilities(MultiblockMachineDefinition multiDef, Set<String> abilities) {
        List<Object> modifiers = GTCEuReflectionBridge.getRecipeModifiers(multiDef);
        if (modifiers == null || modifiers.isEmpty()) return;
        for (Object mod : modifiers) {
            if (mod == null) continue;
            String modId = GTCEuReflectionBridge.getRecipeModifierName(mod);
            if (modId == null) continue;
            if ("PARALLEL_HATCH".equals(modId) || "ABSOLUTE_PARALLEL".equals(modId)) {
                abilities.add("PARALLEL_HATCH");
            }
            if ("BATCH_MODE".equals(modId)) abilities.add("BATCH_MODE");
            if ("EBF_OC".equals(modId) || "EBF_OVERCLOCK".equals(modId) || "ELECTRIC_BLAST_FURNACE".equals(modId)
                    || "HELL_FORGE_OC".equals(modId)
                    || "CRACKER_OC".equals(modId) || "CRACKER_OVERCLOCK".equals(modId) || "CRACKING_UNIT".equals(modId)
                    || "PYROLYSE_OVEN_OC".equals(modId) || "PYROLYSE_OVEN_OVERCLOCK".equals(modId) || "PYROLYSE_OVEN".equals(modId)) {
                abilities.add("HEATING_COILS");
            }
            if ("MULTI_SMELLTER_PARALLEL".equals(modId) || "MULTI_SMELTER_PARALLEL".equals(modId) || "MULTI_SMELTER".equals(modId)) {
                abilities.add("COIL_PARALLEL");
                abilities.add("HEATING_COILS");
            }
            if ("CHEMICAL_REACTOR_OC".equals(modId) || "CHEMICAL_REACTOR_OVERCLOCK".equals(modId) || "CHEMICAL_PLANT".equals(modId)
                    || "VACUUM_CHEMICAL_REACTION_CHAMBER".equals(modId)) {
                abilities.add("HEATING_COILS");
            }
            if ("THROUGHPUT_BOOSTING".equals(modId)) abilities.add("THROUGHPUT_BOOSTING");
            if ("OVERPRESSURE".equals(modId)) abilities.add("OVERPRESSURE");
            if ("BULK_PROCESSING".equals(modId)) abilities.add("BULK_PROCESSING");
            if ("THREADING".equals(modId) || "THREADING_MACHINE".equals(modId)) abilities.add("THREADING");
            if ("REFLECTOR_FUSION_REACTOR".equals(modId)) abilities.add("REFLECTOR");
        }
    }

    private static void enrichIoAbilities(MultiblockMachineDefinition multiDef, Set<String> abilities) {
        if (multiDef.getRecipeTypes() == null || multiDef.getRecipeTypes().length == 0) return;

        abilities.add("IMPORT_ITEMS");
        abilities.add("EXPORT_ITEMS");
        abilities.add("IMPORT_FLUIDS");
        abilities.add("EXPORT_FLUIDS");
        abilities.add("MAINTENANCE");

        if (multiDef.isGenerator()) {
            abilities.add("OUTPUT_ENERGY");
        } else {
            abilities.add("INPUT_ENERGY");
        }
    }

    private static void enrichStarTThreading(MultiblockMachineDefinition multiDef, Set<String> abilities) {
        if (STAT_BLOCKS_FIELD == null) return;
        String clsName = multiDef.getClass().getName().toLowerCase(Locale.ROOT);
        if (!clsName.contains("threading")) return;
        abilities.add("THREADING");
    }
}
