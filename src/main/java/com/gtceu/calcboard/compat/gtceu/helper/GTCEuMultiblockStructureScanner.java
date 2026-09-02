package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GTCEuMultiblockStructureScanner {

    private static final Map<Item, String> ITEM_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, String> BLOCK_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, ResourceLocation> ITEM_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, ResourceLocation> BLOCK_ID_CACHE = new ConcurrentHashMap<>();

    private static volatile Method mGetItemStackCached = null;
    private static volatile Method mGetBlockStateCached = null;
    private static volatile boolean reflectionMethodsInitialized = false;

    public static MultiblockStructureDef scanSingle(ResourceLocation controllerId) {
        if (!ModCompatHelper.isGTLoaded() || controllerId == null) {
            return null;
        }

        MultiblockStructureDef existing = MultiblockStructureCatalog.getStructureCached(controllerId);
        if (existing != null) {
            return existing;
        }

        try {
            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
            Method mGet = machinesRegistry.getClass().getMethod("get", ResourceLocation.class);
            Object def = mGet.invoke(machinesRegistry, controllerId);
            if (def == null) return null;
            String defClsName = def.getClass().getName();
            if (!defClsName.contains("MultiblockMachineDefinition")) return null;

            Method mGetMatchingShapes = def.getClass().getMethod("getMatchingShapes");
            List<?> shapes = (List<?>) mGetMatchingShapes.invoke(def);
            if (shapes == null || shapes.isEmpty()) return null;

            List<MultiblockStructureDef> variants = new ArrayList<>();
            Method mGetBlocks = null;
            for (Object shape : shapes) {
                if (shape == null) continue;
                if (mGetBlocks == null) mGetBlocks = shape.getClass().getMethod("getBlocks");
                Object[][][] blockGrid = (Object[][][]) mGetBlocks.invoke(shape);
                if (blockGrid == null) continue;

                MultiblockStructureDef sDef = parseShapeToDef(controllerId, blockGrid);
                if (sDef != null) {
                    variants.add(sDef);
                }
            }

            if (variants.isEmpty()) return null;

            variants.sort(Comparator.comparingInt(d -> d.parts().stream().mapToInt(MultiblockStructurePart::amount).sum()));

            MultiblockStructureDef largest = variants.get(variants.size() - 1);
            int maxCoil = variants.stream().mapToInt(MultiblockStructureDef::coilSlotCount).max().orElse(0);
            int maxEnergy = variants.stream().mapToInt(MultiblockStructureDef::energyHatchSlotCount).max().orElse(0);
            int maxInBus = variants.stream().mapToInt(MultiblockStructureDef::inputBusSlotCount).max().orElse(0);
            int maxOutBus = variants.stream().mapToInt(MultiblockStructureDef::outputBusSlotCount).max().orElse(0);
            int maxInHatch = variants.stream().mapToInt(MultiblockStructureDef::inputHatchSlotCount).max().orElse(0);
            int maxOutHatch = variants.stream().mapToInt(MultiblockStructureDef::outputHatchSlotCount).max().orElse(0);
            int maxMaint = variants.stream().mapToInt(MultiblockStructureDef::maintenanceSlotCount).max().orElse(0);

            GTCEuPatternScanner.PatternScanResult patternRes = GTCEuPatternScanner.scanPattern(def);
            Class<?> mCls = GTCEuReflectionBridge.getMachineClass(def);
            boolean supportsCoilAbility = (mCls != null && GTCEuReflectionBridge.isCoilWorkableClass(mCls))
                    || MultiblockDetector.isCoilMultiblock(controllerId)
                    || (GTCEuCoilModifierHelper.getCoilMachineSpec(controllerId).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC);
            if (!supportsCoilAbility) {
                maxCoil = 0;
            }

            Set<ResourceLocation> allCandidates = new HashSet<>(patternRes.candidateBlocks());
            for (MultiblockStructurePart p : largest.parts()) {
                if (p != null && p.itemId() != null) {
                    allCandidates.add(p.itemId());
                }
            }

            MultiblockStructureDef canonicalDef = new MultiblockStructureDef(
                    largest.controllerId(),
                    largest.controllerName(),
                    largest.parts(),
                    maxCoil,
                    maxEnergy,
                    maxInBus,
                    maxOutBus,
                    maxInHatch,
                    maxOutHatch,
                    maxMaint,
                    patternRes.allowedAbilities(),
                    Collections.unmodifiableSet(allCandidates)
            );

            MultiblockStructureCatalog.registerStructure(canonicalDef, variants);
            return canonicalDef;
        } catch (Throwable ignored) {}
        return null;
    }

    public static void scan() {
        if (!ModCompatHelper.isGTLoaded()) {
            return;
        }

        try {
            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
            Method mIterator = machinesRegistry.getClass().getMethod("iterator");
            Iterator<?> it = (Iterator<?>) mIterator.invoke(machinesRegistry);

            Method mGetId = null;
            Method mGetMatchingShapes = null;
            Method mGetBlocks = null;
            int count = 0;

            while (it.hasNext()) {
                Object def = it.next();
                if (def == null) continue;
                String defClsName = def.getClass().getName();
                if (!defClsName.contains("MultiblockMachineDefinition")) continue;

                try {
                    if (mGetId == null) mGetId = def.getClass().getMethod("getId");
                    ResourceLocation controllerId = (ResourceLocation) mGetId.invoke(def);
                    if (controllerId == null) continue;

                    if (mGetMatchingShapes == null) mGetMatchingShapes = def.getClass().getMethod("getMatchingShapes");
                    List<?> shapes = (List<?>) mGetMatchingShapes.invoke(def);
                    if (shapes == null || shapes.isEmpty()) continue;

                    List<MultiblockStructureDef> variants = new ArrayList<>();
                    for (Object shape : shapes) {
                        if (shape == null) continue;
                        if (mGetBlocks == null) mGetBlocks = shape.getClass().getMethod("getBlocks");
                        Object[][][] blockGrid = (Object[][][]) mGetBlocks.invoke(shape);
                        if (blockGrid == null) continue;

                        MultiblockStructureDef sDef = parseShapeToDef(controllerId, blockGrid);
                        if (sDef != null) {
                            variants.add(sDef);
                        }
                    }

                    if (variants.isEmpty()) continue;

                    variants.sort(Comparator.comparingInt(d -> d.parts().stream().mapToInt(MultiblockStructurePart::amount).sum()));

                    MultiblockStructureDef largest = variants.get(variants.size() - 1);
                    int maxCoil = variants.stream().mapToInt(MultiblockStructureDef::coilSlotCount).max().orElse(0);
                    int maxEnergy = variants.stream().mapToInt(MultiblockStructureDef::energyHatchSlotCount).max().orElse(0);
                    int maxInBus = variants.stream().mapToInt(MultiblockStructureDef::inputBusSlotCount).max().orElse(0);
                    int maxOutBus = variants.stream().mapToInt(MultiblockStructureDef::outputBusSlotCount).max().orElse(0);
                    int maxInHatch = variants.stream().mapToInt(MultiblockStructureDef::inputHatchSlotCount).max().orElse(0);
                    int maxOutHatch = variants.stream().mapToInt(MultiblockStructureDef::outputHatchSlotCount).max().orElse(0);
                    int maxMaint = variants.stream().mapToInt(MultiblockStructureDef::maintenanceSlotCount).max().orElse(0);

                    GTCEuPatternScanner.PatternScanResult patternRes = GTCEuPatternScanner.scanPattern(def);
                    Class<?> mCls = GTCEuReflectionBridge.getMachineClass(def);
                    boolean supportsCoilAbility = mCls != null && GTCEuReflectionBridge.isCoilWorkableClass(mCls);
                    if (!supportsCoilAbility) {
                        maxCoil = 0;
                    }

                    Set<ResourceLocation> scanCandidates = new HashSet<>(patternRes.candidateBlocks());
                    for (MultiblockStructurePart p : largest.parts()) {
                        if (p != null && p.itemId() != null) {
                            scanCandidates.add(p.itemId());
                        }
                    }

                    MultiblockStructureDef canonicalDef = new MultiblockStructureDef(
                            largest.controllerId(),
                            largest.controllerName(),
                            largest.parts(),
                            maxCoil,
                            maxEnergy,
                            maxInBus,
                            maxOutBus,
                            maxInHatch,
                            maxOutHatch,
                            maxMaint,
                            patternRes.allowedAbilities(),
                            Collections.unmodifiableSet(scanCandidates)
                    );

                    MultiblockStructureCatalog.registerStructure(canonicalDef, variants);

                    // Yield CPU every 3 multiblocks to maintain silky smooth 60+ FPS on render thread
                    if ((++count % 3) == 0) {
                        Thread.yield();
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static MultiblockStructureDef parseShapeToDef(ResourceLocation controllerId, Object[][][] blockGrid) {
        try {
            Map<ResourceLocation, Integer> partCounts = new LinkedHashMap<>();
            Map<ResourceLocation, String> partNames = new HashMap<>();

            for (Object[][] plane : blockGrid) {
                if (plane == null) continue;
                for (Object[] row : plane) {
                    if (row == null) continue;
                    for (Object bInfo : row) {
                        if (bInfo == null) continue;
                        try {
                            if (!reflectionMethodsInitialized) {
                                try {
                                    mGetItemStackCached = bInfo.getClass().getMethod("getItemStackForm");
                                } catch (Throwable ignored) {}
                                try {
                                    mGetBlockStateCached = bInfo.getClass().getMethod("getBlockState");
                                } catch (Throwable ignored) {}
                                reflectionMethodsInitialized = true;
                            }

                            ItemStack itemStack = null;
                            if (mGetItemStackCached != null) {
                                try {
                                    itemStack = (ItemStack) mGetItemStackCached.invoke(bInfo);
                                } catch (Throwable ignored) {}
                            }

                            ResourceLocation itemId = null;
                            String name = "";

                            if (itemStack != null && !itemStack.isEmpty()) {
                                itemId = ITEM_ID_CACHE.computeIfAbsent(itemStack.getItem(), ForgeRegistries.ITEMS::getKey);
                                if (itemId != null) {
                                    name = ITEM_NAME_CACHE.computeIfAbsent(itemStack.getItem(), itm -> itm.getDescription().getString());
                                }
                            } else if (mGetBlockStateCached != null) {
                                Object bState = mGetBlockStateCached.invoke(bInfo);
                                if (bState instanceof BlockState bs) {
                                    Block blk = bs.getBlock();
                                    itemId = BLOCK_ID_CACHE.computeIfAbsent(blk, ForgeRegistries.BLOCKS::getKey);
                                    if (itemId != null) {
                                        name = BLOCK_NAME_CACHE.computeIfAbsent(blk, b -> b.getName().getString());
                                    }
                                }
                            }

                            if (itemId != null && !itemId.getPath().equals("air")) {
                                partCounts.merge(itemId, 1, Integer::sum);
                                if (!name.isEmpty() && !partNames.containsKey(itemId)) {
                                    partNames.put(itemId, name);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (partCounts.isEmpty()) return null;

            List<MultiblockStructurePart> parts = new ArrayList<>();
            int coilSlots = 0;
            int energyHatchSlots = 0;
            int inputBusSlots = 0;
            int outputBusSlots = 0;
            int inputHatchSlots = 0;
            int outputHatchSlots = 0;
            int maintenanceSlots = 0;

            for (Map.Entry<ResourceLocation, Integer> entry : partCounts.entrySet()) {
                ResourceLocation pId = entry.getKey();
                int amount = entry.getValue();
                String pName = partNames.getOrDefault(pId, MultiblockStructureCatalog.formatMachineName(pId.getPath()));
                PartCategory category = MultiblockStructureCatalog.classifyPart(pId);

                String path = pId.getPath().toLowerCase(Locale.ROOT);
                if (category == PartCategory.COIL) {
                    coilSlots = Math.max(coilSlots, amount);
                } else if ((path.contains("energy") && path.contains("hatch")) || (path.contains("power") && path.contains("hatch")) || path.contains("laser_target") || path.contains("laser_source")) {
                    energyHatchSlots = Math.max(energyHatchSlots, amount);
                } else if (path.contains("input_bus") || path.contains("import_bus")) {
                    inputBusSlots = Math.max(inputBusSlots, amount);
                } else if (path.contains("output_bus") || path.contains("export_bus")) {
                    outputBusSlots = Math.max(outputBusSlots, amount);
                } else if (path.contains("input_hatch") || path.contains("fluid_import")) {
                    inputHatchSlots = Math.max(inputHatchSlots, amount);
                } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
                    outputHatchSlots = Math.max(outputHatchSlots, amount);
                } else if (path.contains("maintenance")) {
                    maintenanceSlots = Math.max(maintenanceSlots, amount);
                }

                if (!pId.equals(controllerId)) {
                    parts.add(new MultiblockStructurePart(pId, pName, amount, category));
                }
            }

            String controllerName = MultiblockStructureCatalog.formatMachineName(controllerId.getPath());
            parts.add(0, new MultiblockStructurePart(controllerId, controllerName, 1, PartCategory.CONTROLLER));

            if (!MultiblockDetector.isCoilMultiblock(controllerId)
                    && GTCEuCoilModifierHelper.getCoilMachineSpec(controllerId).kind() == GTCEuCoilModifierHelper.CoilMachineKind.GENERIC) {
                coilSlots = 0;
            }

            return new MultiblockStructureDef(
                    controllerId,
                    controllerName,
                    parts,
                    coilSlots,
                    energyHatchSlots,
                    inputBusSlots,
                    outputBusSlots,
                    inputHatchSlots,
                    outputHatchSlots,
                    maintenanceSlots
            );
        } catch (Throwable ignored) {
            return null;
        }
    }
}

