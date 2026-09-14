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

    private static final class ScanContext {
        final Set<String> abilities = new HashSet<>();
        final Set<ResourceLocation> candidateBlocks = new HashSet<>();
        final Set<Block> coilBlocks = new HashSet<>();
        final Set<SimplePredicate> visitedPredicates = Collections.newSetFromMap(new IdentityHashMap<>());
        int maxEnergyHatches = 0;
        int maxMaintenanceHatches = 0;
        int maxParallelHatches = 0;
    }

    public static PatternScanResult scanPattern(Object machineDef) {
        if (!(machineDef instanceof MultiblockMachineDefinition multiDef)) {
            return PatternScanResult.EMPTY;
        }

        ScanContext ctx = new ScanContext();
        scanPatternFactory(multiDef, ctx);
        enrichFromMachineDefinition(multiDef, ctx.abilities);

        if (ctx.coilBlocks.size() > 1) {
            ctx.abilities.add("HEATING_COILS");
        }

        int finalMaxEnergy = ctx.maxEnergyHatches;
        if (finalMaxEnergy == 0 && ctx.abilities.contains("INPUT_ENERGY")) {
            finalMaxEnergy = 2;
        }

        return new PatternScanResult(
                Collections.unmodifiableSet(ctx.abilities),
                Collections.unmodifiableSet(ctx.candidateBlocks),
                finalMaxEnergy,
                ctx.maxMaintenanceHatches,
                ctx.maxParallelHatches
        );
    }

    private static void scanPatternFactory(MultiblockMachineDefinition multiDef, ScanContext ctx) {
        if (multiDef.getPatternFactory() == null) return;
        BlockPattern pattern = multiDef.getPatternFactory().get();
        if (pattern == null) return;

        TraceabilityPredicate[][][] matches = extractBlockMatches(pattern);
        if (matches != null) {
            scanGrid(matches, ctx);
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

    private static void scanGrid(TraceabilityPredicate[][][] grid, ScanContext ctx) {
        for (TraceabilityPredicate[][] plane : grid) {
            if (plane != null) scanPlane(plane, ctx);
        }
    }

    private static void scanPlane(TraceabilityPredicate[][] plane, ScanContext ctx) {
        for (TraceabilityPredicate[] row : plane) {
            if (row != null) scanRow(row, ctx);
        }
    }

    private static void scanRow(TraceabilityPredicate[] row, ScanContext ctx) {
        for (TraceabilityPredicate pred : row) {
            if (pred != null) scanTraceabilityPredicate(pred, ctx);
        }
    }

    private static void scanTraceabilityPredicate(TraceabilityPredicate pred, ScanContext ctx) {
        scanSimpleList(pred.common, ctx);
        scanSimpleList(pred.limited, ctx);
    }

    private static void scanSimpleList(List<SimplePredicate> list, ScanContext ctx) {
        if (list == null) return;
        for (SimplePredicate sp : list) {
            if (sp != null) scanSimplePredicate(sp, ctx);
        }
    }

    private static void scanSimplePredicate(SimplePredicate sp, ScanContext ctx) {
        if (sp == null || !ctx.visitedPredicates.add(sp)) return;

        if (sp.type != null && !sp.type.isBlank()) {
            String typeUpper = sp.type.toUpperCase(Locale.ROOT);
            ctx.abilities.add(typeUpper);
            checkTypeLimit(typeUpper, sp.maxCount, ctx);
        }

        List<Block> blocks = collectPredicateBlocks(sp);
        for (Block b : blocks) {
            scanBlock(b, ctx);
        }

        checkBlocksLimit(blocks, sp.maxCount, ctx);
    }

    private static void checkTypeLimit(String typeUpper, int maxCount, ScanContext ctx) {
        if (maxCount <= 0) return;
        if (typeUpper.contains("INPUT_ENERGY") || typeUpper.equals("INPUT_LASER")) {
            ctx.maxEnergyHatches = ctx.maxEnergyHatches == 0 ? maxCount : Math.min(ctx.maxEnergyHatches, maxCount);
        } else if (typeUpper.equals("MAINTENANCE")) {
            ctx.maxMaintenanceHatches = ctx.maxMaintenanceHatches == 0 ? maxCount : Math.min(ctx.maxMaintenanceHatches, maxCount);
        } else if (typeUpper.equals("PARALLEL_HATCH")) {
            ctx.maxParallelHatches = ctx.maxParallelHatches == 0 ? maxCount : Math.min(ctx.maxParallelHatches, maxCount);
        }
    }

    private static void checkBlocksLimit(List<Block> blocks, int maxCount, ScanContext ctx) {
        if (maxCount <= 0 || blocks.isEmpty()) return;
        boolean isEnergy = false;
        boolean isMaint = false;
        boolean isParallel = false;

        for (Block b : blocks) {
            if (!isEnergy && isEnergyInputBlock(b)) isEnergy = true;
            if (!isMaint && isMaintenanceBlock(b)) isMaint = true;
            if (!isParallel && isParallelHatchBlock(b)) isParallel = true;
        }

        if (isEnergy) {
            ctx.maxEnergyHatches = ctx.maxEnergyHatches == 0 ? maxCount : Math.min(ctx.maxEnergyHatches, maxCount);
        }
        if (isMaint) {
            ctx.maxMaintenanceHatches = ctx.maxMaintenanceHatches == 0 ? maxCount : Math.min(ctx.maxMaintenanceHatches, maxCount);
        }
        if (isParallel) {
            ctx.maxParallelHatches = ctx.maxParallelHatches == 0 ? maxCount : Math.min(ctx.maxParallelHatches, maxCount);
        }
    }

    private static boolean isEnergyInputBlock(Block b) {
        if (PART_ABILITY_IS_APPLICABLE == null || KNOWN_PART_ABILITIES.isEmpty() || b == null) return false;
        for (Map.Entry<Object, String> entry : KNOWN_PART_ABILITIES.entrySet()) {
            String name = entry.getValue();
            if (name.contains("INPUT_ENERGY") || name.equals("INPUT_LASER")) {
                try {
                    Object res = PART_ABILITY_IS_APPLICABLE.invoke(entry.getKey(), b);
                    if (res instanceof Boolean bool && bool) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static boolean isMaintenanceBlock(Block b) {
        if (PART_ABILITY_IS_APPLICABLE == null || KNOWN_PART_ABILITIES.isEmpty() || b == null) return false;
        for (Map.Entry<Object, String> entry : KNOWN_PART_ABILITIES.entrySet()) {
            if ("MAINTENANCE".equals(entry.getValue())) {
                try {
                    Object res = PART_ABILITY_IS_APPLICABLE.invoke(entry.getKey(), b);
                    if (res instanceof Boolean bool && bool) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static boolean isParallelHatchBlock(Block b) {
        if (PART_ABILITY_IS_APPLICABLE == null || KNOWN_PART_ABILITIES.isEmpty() || b == null) return false;
        for (Map.Entry<Object, String> entry : KNOWN_PART_ABILITIES.entrySet()) {
            if ("PARALLEL_HATCH".equals(entry.getValue())) {
                try {
                    Object res = PART_ABILITY_IS_APPLICABLE.invoke(entry.getKey(), b);
                    if (res instanceof Boolean bool && bool) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static List<Block> collectPredicateBlocks(SimplePredicate sp) {
        List<Block> list = new ArrayList<>();
        if (sp instanceof PredicateBlocks pb && pb.blocks != null) {
            for (Block b : pb.blocks) {
                if (b != null) list.add(b);
            }
            return list;
        }
        if (sp instanceof PredicateStates ps && ps.states != null) {
            for (BlockState state : ps.states) {
                if (state != null && state.getBlock() != null) list.add(state.getBlock());
            }
            return list;
        }
        extractCandidatesSupplierBlocks(sp, list);
        return list;
    }

    private static void extractCandidatesSupplierBlocks(SimplePredicate sp, List<Block> list) {
        if (CANDIDATES_FIELD == null) return;
        try {
            Object rawSupplier = CANDIDATES_FIELD.get(sp);
            if (!(rawSupplier instanceof Supplier<?> supplier)) return;
            Object rawInfos = supplier.get();
            if (rawInfos instanceof Object[] arr) {
                appendBlocksFromCandidateArray(arr, list);
            }
        } catch (Throwable ignored) {}
    }

    private static void appendBlocksFromCandidateArray(Object[] arr, List<Block> list) {
        for (Object item : arr) {
            Block b = extractBlockFromCandidate(item);
            if (b != null) list.add(b);
        }
    }

    private static Block extractBlockFromCandidate(Object item) {
        if (item == null) return null;
        try {
            Method mGetBlockState = item.getClass().getMethod("getBlockState");
            Object bState = mGetBlockState.invoke(item);
            if (bState instanceof BlockState bs) return bs.getBlock();
        } catch (Throwable ignored) {}
        try {
            Field fState = item.getClass().getField("blockState");
            Object bState = fState.get(item);
            if (bState instanceof BlockState bs) return bs.getBlock();
        } catch (Throwable ignored) {}
        try {
            Method mStack = item.getClass().getMethod("getItemStackForm");
            Object stackObj = mStack.invoke(item);
            if (stackObj instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
                return Block.byItem(stack.getItem());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void scanBlock(Block b, ScanContext ctx) {
        if (b == null) return;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        if (id != null && !id.getPath().equals("air")) {
            ctx.candidateBlocks.add(id);
        }

        if (GTCEuAPI.HEATING_COILS.containsKey(b)) {
            ctx.coilBlocks.add(b);
        }

        matchPartAbilities(b, ctx.abilities);
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
            if ("LIQUEFACTION".equals(modId) || "LIQUEFACTION_TOWER".equals(modId)) {
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
