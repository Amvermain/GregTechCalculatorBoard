package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.AddonCategory;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.MultiblockDetector;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * GTCEu-specific multiblock structure part resolution, dynamic hatch sizing,
 * coil/helix configuration, and casing reduction calculator.
 */
public final class GTCEuBOMHelper {

    private GTCEuBOMHelper() {}

    public static List<MultiblockStructurePart> resolveGTMultiblockParts(RecipeNode node, boolean dualLowerTierEnergyHatches) {
        if (node == null) return List.of();

        ResourceLocation machineId = node.getMachineIcon();
        if (machineId == null && !node.getAvailableWorkstations().isEmpty()) {
            machineId = node.getAvailableWorkstations().get(0);
        }
        if (machineId == null) return List.of();

        int reqFluidIn = (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
        int reqItemIn = (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
        int reqFluidOut = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
        int reqItemOut = (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();

        boolean isDT = machineId.getPath().contains("distillation_tower");
        for (MachineAddon addon : node.getAddons()) {
            if (addon instanceof GTHatchAddon gh) {
                int cap = gh.getSlotCapacity();
                switch (gh.getHatchType()) {
                    case FLUID_OUTPUT -> {
                        if (!isDT) {
                            reqFluidOut = Math.max(1, 1 + Math.max(0, reqFluidOut - cap));
                        }
                    }
                    case ITEM_OUTPUT -> reqItemOut = Math.max(1, 1 + Math.max(0, reqItemOut - cap));
                    case FLUID_INPUT -> reqFluidIn = Math.max(1, 1 + Math.max(0, reqFluidIn - cap));
                    case ITEM_INPUT -> reqItemIn = Math.max(1, 1 + Math.max(0, reqItemIn - cap));
                    case DUAL_OUTPUT -> {
                        if (!isDT) {
                            reqFluidOut = Math.max(1, 1 + Math.max(0, reqFluidOut - cap));
                        }
                        reqItemOut = Math.max(1, 1 + Math.max(0, reqItemOut - cap));
                    }
                    case DUAL_INPUT -> {
                        reqFluidIn = Math.max(1, 1 + Math.max(0, reqFluidIn - cap));
                        reqItemIn = Math.max(1, 1 + Math.max(0, reqItemIn - cap));
                    }
                    case ME_PATTERN_PROVIDER -> {
                        reqFluidIn = 1;
                        reqItemIn = 1;
                    }
                    default -> {}
                }
            }
        }

        MultiblockStructureDef def = MultiblockStructureCatalog.getMatchingStructure(machineId, reqFluidOut, reqItemOut, reqFluidIn, reqItemIn);
        if (def == null) {
            for (ResourceLocation ws : node.getAvailableWorkstations()) {
                if (ws != null) {
                    def = MultiblockStructureCatalog.getMatchingStructure(ws, reqFluidOut, reqItemOut, reqFluidIn, reqItemIn);
                    if (def != null) break;
                }
            }
        }

        return resolveMachineParts(node, def, dualLowerTierEnergyHatches);
    }

    private static List<MultiblockStructurePart> resolveMachineParts(RecipeNode node, MultiblockStructureDef def, boolean dualLowerTierEnergyHatches) {
        List<MultiblockStructurePart> list = new ArrayList<>();
        GTVoltageTier tier = node.getTargetTier() != null ? node.getTargetTier() : GTVoltageTier.LV;

        int fluidInCount = (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
        int fluidOutCount = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
        int itemInCount = (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
        int itemOutCount = (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();

        if (def == null) {
            ResourceLocation machineId = node.getMachineIcon();
            if (machineId != null) {
                String controllerName = node.getName() != null && !node.getName().isBlank() ? node.getName() : machineId.getPath();
                list.add(new MultiblockStructurePart(machineId, controllerName, 1, PartCategory.CONTROLLER));
            }
            ResourceLocation ehId = resolveEnergyHatchId(tier, dualLowerTierEnergyHatches);
            if (ehId != null) {
                list.add(new MultiblockStructurePart(ehId, formatDisplayName(ehId), dualLowerTierEnergyHatches ? 2 : 1, PartCategory.HATCH_BUS));
            }
            for (MachineAddon addon : node.getAddons()) {
                if (addon != null && addon.getItemIcon() != null) {
                    list.add(new MultiblockStructurePart(addon.getItemIcon(), addon.getName(), 1, PartCategory.HATCH_BUS));
                }
            }
            return list;
        }

        MachineAddon equippedCoil = null;
        for (MachineAddon addon : node.getAddons()) {
            if (addon != null && addon.getCategory() == MachineAddon.Category.COIL) {
                equippedCoil = addon;
                break;
            }
        }

        MachineAddon equippedMaint = null;
        MachineAddon equippedMuffler = null;
        for (MachineAddon addon : node.getAddons()) {
            if (addon != null && addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
                if (addon.getId() != null && addon.getId().toLowerCase(Locale.ROOT).contains("muffler")) {
                    equippedMuffler = addon;
                } else if (equippedMaint == null) {
                    equippedMaint = addon;
                }
            }
        }

        boolean handledEnergyHatch = false;
        boolean handledItemIn = false;
        boolean handledItemOut = false;
        boolean handledFluidIn = false;
        boolean handledFluidOut = false;
        boolean handledMaint = false;

        List<MachineAddon> equippedEnergyHatches = new ArrayList<>();
        for (MachineAddon addon : node.getAddons()) {
            if (addon != null && addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                equippedEnergyHatches.add(addon);
            }
        }

        boolean handledParallel = false;
        MachineAddon equippedParallel = null;
        for (MachineAddon addon : node.getAddons()) {
            if (addon != null && addon.getCategory() == MachineAddon.Category.PARALLEL) {
                equippedParallel = addon;
                break;
            }
        }

        boolean handledHelixes = false;
        Map<com.gtceu.calcboard.api.GTThreadingHelix, Integer> equippedHelixes = null;
        if (node.hasThreading() && node.getThreadingConfig() != null && node.getThreadingConfig().getTotalHelixCount() > 0) {
            equippedHelixes = node.getThreadingConfig().getHelixCounts();
        }

        List<GTHatchAddon> equippedCustomHatches = new ArrayList<>();
        for (MachineAddon addon : node.getAddons()) {
            if (addon instanceof GTHatchAddon gh) {
                equippedCustomHatches.add(gh);
            } else if (addon != null && addon.getCategory() == AddonCategory.HATCH_BUS) {
                var stats = GTHatchHelper.extractStatsFromMachineDef(null, addon.getItemIcon());
                if (stats != null) {
                    equippedCustomHatches.add(new GTHatchAddon(
                        addon.getId(), addon.getName(), addon.getDescription(), addon.getItemIcon(),
                        stats.hatchType(), stats.tier(), stats.slotCapacity(), stats.tankCapacityMB(), stats.isME()
                    ));
                } else {
                    equippedCustomHatches.add(new GTHatchAddon(
                        addon.getId(), addon.getName(), addon.getDescription(), addon.getItemIcon()
                    ));
                }
            }
        }

        boolean hasCustomItemIn = false;
        boolean hasCustomItemOut = false;
        boolean hasCustomFluidIn = false;
        boolean hasCustomFluidOut = false;
        int neededItemIn = itemInCount;
        int neededItemOut = itemOutCount;
        int neededFluidIn = fluidInCount;
        int neededFluidOut = fluidOutCount;

        Map<ResourceLocation, Integer> customHatchCounts = new LinkedHashMap<>();
        Map<ResourceLocation, String> customHatchNames = new LinkedHashMap<>();

        for (GTHatchAddon h : equippedCustomHatches) {
            if (h.getItemIcon() != null) {
                customHatchCounts.put(h.getItemIcon(), customHatchCounts.getOrDefault(h.getItemIcon(), 0) + 1);
                customHatchNames.put(h.getItemIcon(), h.getName());
            }

            int cap = h.getSlotCapacity();
            switch (h.getHatchType()) {
                case ITEM_INPUT -> {
                    hasCustomItemIn = true;
                    neededItemIn = Math.max(0, neededItemIn - cap);
                }
                case ITEM_OUTPUT -> {
                    hasCustomItemOut = true;
                    neededItemOut = Math.max(0, neededItemOut - cap);
                }
                case FLUID_INPUT -> {
                    hasCustomFluidIn = true;
                    neededFluidIn = Math.max(0, neededFluidIn - cap);
                }
                case FLUID_OUTPUT -> {
                    hasCustomFluidOut = true;
                    neededFluidOut = Math.max(0, neededFluidOut - cap);
                }
                case DUAL_INPUT -> {
                    hasCustomItemIn = true;
                    hasCustomFluidIn = true;
                    neededItemIn = Math.max(0, neededItemIn - cap);
                    neededFluidIn = Math.max(0, neededFluidIn - cap);
                }
                case DUAL_OUTPUT -> {
                    hasCustomItemOut = true;
                    hasCustomFluidOut = true;
                    neededItemOut = Math.max(0, neededItemOut - cap);
                    neededFluidOut = Math.max(0, neededFluidOut - cap);
                }
                case ME_PATTERN_PROVIDER -> {
                    hasCustomItemIn = true;
                    hasCustomFluidIn = true;
                    neededItemIn = 0;
                    neededFluidIn = 0;
                }
                default -> {}
            }
        }

        for (MultiblockStructurePart part : def.parts()) {
            if (part == null) continue;
            String path = part.itemId() != null ? part.itemId().getPath().toLowerCase(Locale.ROOT) : "";

            if (path.contains("parallel")) {
                if (equippedParallel != null && equippedParallel.getItemIcon() != null) {
                    handledParallel = true;
                    list.add(new MultiblockStructurePart(
                        equippedParallel.getItemIcon(),
                        equippedParallel.getName(),
                        part.amount(),
                        PartCategory.HATCH_BUS
                    ));
                } else {
                    list.add(part);
                }
            } else if (path.contains("helix")) {
                if (equippedHelixes != null && !equippedHelixes.isEmpty()) {
                    if (!handledHelixes) {
                        handledHelixes = true;
                        int totalEquipped = 0;
                        for (var entry : equippedHelixes.entrySet()) {
                            if (entry.getValue() > 0) {
                                totalEquipped += entry.getValue();
                                list.add(new MultiblockStructurePart(
                                    entry.getKey().getId(),
                                    entry.getKey().getEnglishName(),
                                    entry.getValue(),
                                    PartCategory.COIL
                                ));
                            }
                        }
                        if (totalEquipped < part.amount()) {
                            list.add(new MultiblockStructurePart(part.itemId(), part.displayName(), part.amount() - totalEquipped, PartCategory.COIL));
                        }
                    }
                } else {
                    list.add(part);
                }
            } else if (part.category() == PartCategory.COIL || CoilHelper.isHeatingCoil(part.itemId())) {
                if (equippedCoil != null && equippedCoil.getItemIcon() != null) {
                    list.add(new MultiblockStructurePart(
                        equippedCoil.getItemIcon(),
                        equippedCoil.getName(),
                        part.amount(),
                        PartCategory.COIL
                    ));
                } else {
                    list.add(part);
                }
            } else if ((path.contains("energy") && path.contains("hatch")) || (path.contains("power") && path.contains("hatch")) || path.contains("laser_target") || path.contains("laser_source")) {
                handledEnergyHatch = true;
                if (!equippedEnergyHatches.isEmpty()) {
                    Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
                    Map<ResourceLocation, String> names = new LinkedHashMap<>();
                    for (MachineAddon h : equippedEnergyHatches) {
                        if (h.getItemIcon() != null) {
                            counts.put(h.getItemIcon(), counts.getOrDefault(h.getItemIcon(), 0) + 1);
                            names.put(h.getItemIcon(), h.getName());
                        }
                    }
                    for (var entry : counts.entrySet()) {
                        list.add(new MultiblockStructurePart(
                            entry.getKey(),
                            names.get(entry.getKey()),
                            entry.getValue(),
                            PartCategory.HATCH_BUS
                        ));
                    }
                } else {
                    ResourceLocation ehId = resolveEnergyHatchId(tier, dualLowerTierEnergyHatches);
                    int count = dualLowerTierEnergyHatches ? Math.max(2, part.amount() * 2) : part.amount();
                    if (ehId != null) {
                        list.add(new MultiblockStructurePart(
                            ehId,
                            formatDisplayName(ehId),
                            count,
                            PartCategory.HATCH_BUS
                        ));
                    } else {
                        list.add(part);
                    }
                }
            } else if (path.contains("input_bus") || path.contains("import_bus") || path.contains("item_import")) {
                handledItemIn = true;
                boolean isSteamBus = path.contains("steam") || def.supportsAbility("STEAM_IMPORT_ITEMS");
                if (hasCustomItemIn) {
                    if (neededItemIn > 0) {
                        ResourceLocation busId = isSteamBus ? part.itemId() : resolveInputBusId(tier);
                        list.add(new MultiblockStructurePart(busId != null ? busId : part.itemId(), formatDisplayName(busId != null ? busId : part.itemId()), neededItemIn, PartCategory.HATCH_BUS));
                    }
                } else {
                    int count = Math.max(part.amount(), itemInCount);
                    ResourceLocation busId = isSteamBus ? part.itemId() : resolveInputBusId(tier);
                    if (busId != null) {
                        list.add(new MultiblockStructurePart(busId, formatDisplayName(busId), count, PartCategory.HATCH_BUS));
                    } else {
                        list.add(new MultiblockStructurePart(part.itemId(), part.displayName(), count, PartCategory.HATCH_BUS));
                    }
                }
            } else if (path.contains("output_bus") || path.contains("export_bus") || path.contains("item_export")) {
                handledItemOut = true;
                boolean isSteamBus = path.contains("steam") || def.supportsAbility("STEAM_EXPORT_ITEMS");
                if (hasCustomItemOut) {
                    if (neededItemOut > 0) {
                        ResourceLocation busId = isSteamBus ? part.itemId() : resolveOutputBusId(tier);
                        list.add(new MultiblockStructurePart(busId != null ? busId : part.itemId(), formatDisplayName(busId != null ? busId : part.itemId()), neededItemOut, PartCategory.HATCH_BUS));
                    }
                } else {
                    int count = Math.max(part.amount(), itemOutCount);
                    ResourceLocation busId = isSteamBus ? part.itemId() : resolveOutputBusId(tier);
                    if (busId != null) {
                        list.add(new MultiblockStructurePart(busId, formatDisplayName(busId), count, PartCategory.HATCH_BUS));
                    } else {
                        list.add(new MultiblockStructurePart(part.itemId(), part.displayName(), count, PartCategory.HATCH_BUS));
                    }
                }
            } else if (path.contains("input_hatch") || path.contains("fluid_import")) {
                handledFluidIn = true;
                boolean isSteamHatch = path.contains("steam") || def.supportsAbility("STEAM_IMPORT_FLUIDS");
                if (hasCustomFluidIn) {
                    if (neededFluidIn > 0) {
                        ResourceLocation hatchId = isSteamHatch ? part.itemId() : resolveInputHatchId(tier);
                        list.add(new MultiblockStructurePart(hatchId != null ? hatchId : part.itemId(), formatDisplayName(hatchId != null ? hatchId : part.itemId()), neededFluidIn, PartCategory.HATCH_BUS));
                    }
                } else {
                    int count = Math.max(part.amount(), fluidInCount);
                    ResourceLocation hatchId = isSteamHatch ? part.itemId() : resolveInputHatchId(tier);
                    if (hatchId != null) {
                        list.add(new MultiblockStructurePart(hatchId, formatDisplayName(hatchId), count, PartCategory.HATCH_BUS));
                    } else {
                        list.add(new MultiblockStructurePart(part.itemId(), part.displayName(), count, PartCategory.HATCH_BUS));
                    }
                }
            } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
                handledFluidOut = true;
                boolean isSteamHatch = path.contains("steam") || def.supportsAbility("STEAM_EXPORT_FLUIDS");
                if (hasCustomFluidOut) {
                    if (neededFluidOut > 0) {
                        ResourceLocation hatchId = isSteamHatch ? part.itemId() : resolveOutputHatchId(tier);
                        list.add(new MultiblockStructurePart(hatchId != null ? hatchId : part.itemId(), formatDisplayName(hatchId != null ? hatchId : part.itemId()), neededFluidOut, PartCategory.HATCH_BUS));
                    }
                } else {
                    int count = Math.max(part.amount(), fluidOutCount);
                    ResourceLocation hatchId = isSteamHatch ? part.itemId() : resolveOutputHatchId(tier);
                    if (hatchId != null) {
                        list.add(new MultiblockStructurePart(hatchId, formatDisplayName(hatchId), count, PartCategory.HATCH_BUS));
                    } else {
                        list.add(new MultiblockStructurePart(part.itemId(), part.displayName(), count, PartCategory.HATCH_BUS));
                    }
                }
            } else if (path.contains("maintenance")) {
                handledMaint = true;
                if (equippedMaint != null && equippedMaint.getItemIcon() != null) {
                    list.add(new MultiblockStructurePart(
                        equippedMaint.getItemIcon(),
                        equippedMaint.getName(),
                        part.amount(),
                        PartCategory.HATCH_BUS
                    ));
                } else {
                    ResourceLocation defMaint = ResourceLocation.tryParse("gtceu:maintenance_hatch");
                    list.add(new MultiblockStructurePart(
                        defMaint != null ? defMaint : part.itemId(),
                        resolveDisplayName(defMaint != null ? defMaint : part.itemId(), "Maintenance Hatch"),
                        part.amount(),
                        PartCategory.HATCH_BUS
                    ));
                }
            } else if (path.contains("muffler")) {
                if (equippedMuffler != null && equippedMuffler.getItemIcon() != null) {
                    list.add(new MultiblockStructurePart(
                        equippedMuffler.getItemIcon(),
                        equippedMuffler.getName(),
                        part.amount(),
                        PartCategory.HATCH_BUS
                    ));
                } else {
                    ResourceLocation mId = resolveMufflerHatchId(tier);
                    if (mId != null) {
                        list.add(new MultiblockStructurePart(
                            mId,
                            resolveDisplayName(mId, formatDisplayName(mId)),
                            part.amount(),
                            PartCategory.HATCH_BUS
                        ));
                    } else {
                        list.add(part);
                    }
                }
            } else {
                list.add(part);
            }
        }

        for (var entry : customHatchCounts.entrySet()) {
            list.add(new MultiblockStructurePart(
                entry.getKey(),
                customHatchNames.get(entry.getKey()),
                entry.getValue(),
                PartCategory.HATCH_BUS
            ));
        }

        if (!handledHelixes && equippedHelixes != null && !equippedHelixes.isEmpty()) {
            for (var entry : equippedHelixes.entrySet()) {
                if (entry.getValue() > 0) {
                    list.add(new MultiblockStructurePart(
                        entry.getKey().getId(),
                        entry.getKey().getEnglishName(),
                        entry.getValue(),
                        PartCategory.COIL
                    ));
                }
            }
        }

        if (!handledParallel && equippedParallel != null && equippedParallel.getItemIcon() != null) {
            list.add(new MultiblockStructurePart(
                equippedParallel.getItemIcon(),
                equippedParallel.getName(),
                1,
                PartCategory.HATCH_BUS
            ));
        }

        int baseHatchCount = 0;
        for (MultiblockStructurePart p : def.parts()) {
            if (p != null && p.category() == PartCategory.HATCH_BUS) {
                baseHatchCount += p.amount();
            }
        }

        // Automatic fallback: Add Energy Hatches if not already handled in def.parts
        if (!handledEnergyHatch) {
            if (!equippedEnergyHatches.isEmpty()) {
                Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
                Map<ResourceLocation, String> names = new LinkedHashMap<>();
                for (MachineAddon h : equippedEnergyHatches) {
                    if (h.getItemIcon() != null) {
                        counts.put(h.getItemIcon(), counts.getOrDefault(h.getItemIcon(), 0) + 1);
                        names.put(h.getItemIcon(), h.getName());
                    }
                }
                for (var entry : counts.entrySet()) {
                    list.add(new MultiblockStructurePart(
                        entry.getKey(),
                        names.get(entry.getKey()),
                        entry.getValue(),
                        PartCategory.HATCH_BUS
                    ));
                }
            } else if (baseHatchCount == 0 && !node.isGenerator() && node.getEnergyType() != com.gtceu.calcboard.api.EnergyType.NONE && node.getEnergyType() != com.gtceu.calcboard.api.EnergyType.KINETIC_SU && (node.getSteamMode() == null || !node.getSteamMode().isSteam()) && !MultiblockDetector.isSteamMultiblock(def.controllerId())) {
                ResourceLocation ehId = resolveEnergyHatchId(tier, dualLowerTierEnergyHatches);
                int count = dualLowerTierEnergyHatches ? 2 : 1;
                if (ehId != null) {
                    list.add(new MultiblockStructurePart(
                        ehId,
                        formatDisplayName(ehId),
                        count,
                        PartCategory.HATCH_BUS
                    ));
                }
            }
        }

        // Automatic fallback: Add missing required Input Bus if not already handled
        if (!handledItemIn && neededItemIn > 0 && !hasCustomItemIn) {
            boolean isSteamBus = def.supportsAbility("STEAM_IMPORT_ITEMS");
            ResourceLocation busId = isSteamBus ? ResourceLocation.tryParse("gtceu:lp_steam_input_bus") : resolveInputBusId(tier);
            if (busId != null) {
                list.add(new MultiblockStructurePart(busId, formatDisplayName(busId), neededItemIn, PartCategory.HATCH_BUS));
            }
        }

        // Automatic fallback: Add missing required Output Bus if not already handled
        if (!handledItemOut && neededItemOut > 0 && !hasCustomItemOut) {
            boolean isSteamBus = def.supportsAbility("STEAM_EXPORT_ITEMS");
            ResourceLocation busId = isSteamBus ? ResourceLocation.tryParse("gtceu:lp_steam_output_bus") : resolveOutputBusId(tier);
            if (busId != null) {
                list.add(new MultiblockStructurePart(busId, formatDisplayName(busId), neededItemOut, PartCategory.HATCH_BUS));
            }
        }

        // Automatic fallback: Add missing required Input Hatch if not already handled
        if (!handledFluidIn && neededFluidIn > 0 && !hasCustomFluidIn) {
            boolean isSteamHatch = def.supportsAbility("STEAM_IMPORT_FLUIDS");
            ResourceLocation hatchId = isSteamHatch ? ResourceLocation.tryParse("gtceu:lp_steam_input_hatch") : resolveInputHatchId(tier);
            if (hatchId != null) {
                list.add(new MultiblockStructurePart(hatchId, formatDisplayName(hatchId), neededFluidIn, PartCategory.HATCH_BUS));
            }
        }

        // Automatic fallback: Add missing required Output Hatch if not already handled
        if (!handledFluidOut && neededFluidOut > 0 && !hasCustomFluidOut) {
            boolean isSteamHatch = def.supportsAbility("STEAM_EXPORT_FLUIDS");
            ResourceLocation hatchId = isSteamHatch ? ResourceLocation.tryParse("gtceu:lp_steam_output_hatch") : resolveOutputHatchId(tier);
            if (hatchId != null) {
                list.add(new MultiblockStructurePart(hatchId, formatDisplayName(hatchId), neededFluidOut, PartCategory.HATCH_BUS));
            }
        }

        // Automatic fallback: Add Maintenance Hatch if not already handled
        if (!handledMaint) {
            if (equippedMaint != null && equippedMaint.getItemIcon() != null) {
                list.add(new MultiblockStructurePart(
                    equippedMaint.getItemIcon(),
                    equippedMaint.getName(),
                    1,
                    PartCategory.HATCH_BUS
                ));
            } else if (baseHatchCount == 0 && node.getEnergyType() != com.gtceu.calcboard.api.EnergyType.NONE && (node.getSteamMode() == null || !node.getSteamMode().isSteam()) && !MultiblockDetector.isSteamMultiblock(def.controllerId())) {
                ResourceLocation defMaint = ResourceLocation.tryParse("gtceu:maintenance_hatch");
                if (defMaint != null) {
                    list.add(new MultiblockStructurePart(
                        defMaint,
                        resolveDisplayName(defMaint, "Maintenance Hatch"),
                        1,
                        PartCategory.HATCH_BUS
                    ));
                }
            }
        }

        // Adjust primary structural casing count based on extra hatches added
        int resolvedHatchCount = 0;
        for (MultiblockStructurePart p : list) {
            if (p != null && p.category() == PartCategory.HATCH_BUS) {
                resolvedHatchCount += p.amount();
            }
        }

        int extraHatches = resolvedHatchCount - baseHatchCount;
        if (extraHatches > 0) {
            int maxCasingIdx = -1;
            int maxCasingAmount = 0;
            for (int i = 0; i < list.size(); i++) {
                MultiblockStructurePart p = list.get(i);
                if (p != null && p.category() == PartCategory.CASING) {
                    if ((def.isCandidateBlock(p.itemId()) || isReplaceableCasing(p.itemId())) && p.amount() > maxCasingAmount) {
                        maxCasingAmount = p.amount();
                        maxCasingIdx = i;
                    }
                }
            }

            if (maxCasingIdx >= 0) {
                MultiblockStructurePart primaryCasing = list.get(maxCasingIdx);
                int reducedAmount = Math.max(0, primaryCasing.amount() - extraHatches);
                if (reducedAmount > 0) {
                    list.set(maxCasingIdx, new MultiblockStructurePart(
                        primaryCasing.itemId(),
                        primaryCasing.displayName(),
                        reducedAmount,
                        PartCategory.CASING
                    ));
                } else {
                    list.remove(maxCasingIdx);
                }
            }
        }

        return list;
    }

    public static ResourceLocation resolveEnergyHatchId(GTVoltageTier tier, boolean dualLowerTier) {
        GTVoltageTier target = tier != null ? tier : GTVoltageTier.LV;
        if (dualLowerTier && target.ordinal() > 0) {
            target = GTVoltageTier.getByIndex(target.ordinal() - 1);
        }
        String prefix = target.getName().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse("gtceu:" + prefix + "_energy_input_hatch");
    }

    public static ResourceLocation resolveInputBusId(GTVoltageTier tier) {
        GTVoltageTier target = tier != null ? tier : GTVoltageTier.LV;
        String prefix = target.getName().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse("gtceu:" + prefix + "_input_bus");
    }

    public static ResourceLocation resolveOutputBusId(GTVoltageTier tier) {
        GTVoltageTier target = tier != null ? tier : GTVoltageTier.LV;
        String prefix = target.getName().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse("gtceu:" + prefix + "_output_bus");
    }

    public static ResourceLocation resolveInputHatchId(GTVoltageTier tier) {
        GTVoltageTier target = tier != null ? tier : GTVoltageTier.LV;
        String prefix = target.getName().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse("gtceu:" + prefix + "_input_hatch");
    }

    public static ResourceLocation resolveOutputHatchId(GTVoltageTier tier) {
        GTVoltageTier target = tier != null ? tier : GTVoltageTier.LV;
        String prefix = target.getName().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse("gtceu:" + prefix + "_output_hatch");
    }

    public static ResourceLocation resolveMufflerHatchId(GTVoltageTier tier) {
        GTVoltageTier target = (tier == null || tier.ordinal() < GTVoltageTier.LV.ordinal()) ? GTVoltageTier.LV : tier;
        String prefix = target.getName().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse("gtceu:" + prefix + "_muffler_hatch");
    }

    public static boolean isReplaceableCasing(ResourceLocation itemId) {
        if (itemId == null) return false;
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("pipe") || path.contains("frame") || path.contains("glass")
                || path.contains("gearbox") || path.contains("firebox") || path.contains("coil")
                || path.contains("filter") || path.contains("grate") || path.contains("muffler")
                || path.contains("rotor") || path.contains("turbine")) {
            return false;
        }
        return path.contains("casing") || path.contains("wall") || path.contains("plating") || path.contains("hull");
    }

    private static String resolveDisplayName(ResourceLocation id, String fallback) {
        if (id == null) return fallback;
        try {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
            }
        } catch (Throwable ignored) {}
        return fallback != null && !fallback.isBlank() ? fallback : formatDisplayName(id);
    }

    private static String formatDisplayName(ResourceLocation id) {
        if (id == null) return "";
        return MultiblockStructureCatalog.formatMachineName(id.getPath());
    }
}