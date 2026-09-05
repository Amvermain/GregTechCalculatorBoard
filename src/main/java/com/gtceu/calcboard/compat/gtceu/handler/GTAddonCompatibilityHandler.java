package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.compat.gtceu.physics.GTTurbinePhysics;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import com.gtceu.calcboard.compat.start.StarTTurbineHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles GTCEu machine addon compatibility, categories, installation lifecycles, and tooltips.
 */
public final class GTAddonCompatibilityHandler {

    private GTAddonCompatibilityHandler() {}

    public static boolean isMufflerAddon(MachineAddon addon) {
        if (addon == null) return false;
        String id = addon.getId().toLowerCase(Locale.ROOT);
        return id.contains("muffler") || id.contains("muffler_hatch");
    }

    public static boolean isDistillationTower(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("distillation_tower")) return true;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("distillation_tower")) return true;
        return false;
    }

    public static boolean supportsAddons(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.NONE) return false;
        if (GTPowerCalculator.isBoilerRecipe(node)) {
            return node.isMultiblock();
        }
        if (node.isTurbine()) {
            return node.isMultiblock();
        }
        return node.isMultiblock() || node.hasMultiblockOption() || node.canUseCoils() || node.isFusion() || node.getRequiredReflectorTier() > 0;
    }

    public static List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        if (node == null) return List.of();

        if (GTPowerCalculator.isBoilerRecipe(node)) {
            if (node.isMultiblock()) {
                List<AddonCategory> cats = new ArrayList<>();
                cats.add(AddonCategory.MAINTENANCE);
                cats.add(AddonCategory.HATCH_BUS);
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of();
        }

        boolean isTurbine = node.isTurbine();
        boolean isFusion = com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper.isFusion(node);
        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || isFusion;

        if (isTurbine) {
            if (node.isMultiblock()) {
                List<AddonCategory> cats = new ArrayList<>();
                cats.add(AddonCategory.ROTOR);
                cats.add(AddonCategory.MAINTENANCE);
                if (GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                    cats.add(AddonCategory.MULTIBLOCK_TRAIT);
                }
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of();
        }

        if (isFusion) {
            List<AddonCategory> cats = new ArrayList<>();
            cats.add(AddonCategory.REFLECTOR);
            cats.add(AddonCategory.ENERGY_HATCH);
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        if (node.getRecipeCategoryId() != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap != CategoryCapability.DEFAULT) {
                return cap.getActiveCategoriesForNode(node);
            }
        }

        if (isMb) {
            ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
            var def = mbId != null ? MultiblockStructureCatalog.getStructure(mbId) : null;
            boolean isSteamMb = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || (mbId != null && MultiblockDetector.isSteamMultiblock(mbId));

            List<AddonCategory> cats = new ArrayList<>();
            if (!isSteamMb && !node.isGenerator() && node.getEnergyType() != EnergyType.NONE && node.getEnergyType() != EnergyType.KINETIC_SU
                    && (def == null || def.supportsAbility("INPUT_ENERGY") || def.supportsAbility("SUBSTATION_INPUT_ENERGY") || def.supportsAbility("INPUT_LASER") || def.energyHatchSlotCount() > 0 || def.allowedAbilities().isEmpty() || node.getEnergyType() != EnergyType.NONE)) {
                cats.add(AddonCategory.ENERGY_HATCH);
            }
            if (def == null || !def.allowedAbilities().isEmpty() || def.inputBusSlotCount() > 0 || def.outputBusSlotCount() > 0 || def.inputHatchSlotCount() > 0 || def.outputHatchSlotCount() > 0 || !node.getInputs().isEmpty() || !node.getOutputs().isEmpty() || isMb) {
                cats.add(AddonCategory.HATCH_BUS);
            }
            boolean supportsCoil = false;
            if (def != null) {
                supportsCoil = def.supportsAbility("HEATING_COILS")
                        || def.coilSlotCount() > 0
                        || MultiblockDetector.isCoilMultiblock(mbId)
                        || (GTCEuCoilModifierHelper.getCoilMachineSpec(mbId).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC);
            } else {
                supportsCoil = MultiblockDetector.isCoilMultiblock(mbId)
                        || (GTCEuCoilModifierHelper.getCoilMachineSpec(mbId).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC);
            }
            if (supportsCoil) {
                cats.add(AddonCategory.COIL);
            }
            if (!isSteamMb && (MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations()) || (def != null && def.supportsAbility("PARALLEL_HATCH")))) {
                cats.add(AddonCategory.PARALLEL);
            }
            if (!isSteamMb && (def == null || def.supportsAbility("MAINTENANCE") || def.maintenanceSlotCount() > 0 || node.getEnergyType() != EnergyType.NONE)) {
                cats.add(AddonCategory.MAINTENANCE);
            }
            if (node.hasThreading()) {
                cats.add(AddonCategory.THREADING);
            }
            cats.add(AddonCategory.MULTIBLOCK_TRAIT);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        List<AddonCategory> cats = new ArrayList<>();
        if (node.hasThreading()) {
            cats.add(AddonCategory.THREADING);
        }
        cats.add(AddonCategory.CUSTOM);
        return cats;
    }

    public static boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;

        if (GTPowerCalculator.isBoilerRecipe(node)) {
            if (!node.isMultiblock()) return false;
            return addon.getCategory() == AddonCategory.MAINTENANCE || addon.getCategory() == AddonCategory.HATCH_BUS;
        }

        if (node.isTurbine()) {
            if (!node.isMultiblock()) return false;
            if (addon.getCategory() == AddonCategory.ROTOR || addon.getCategory() == AddonCategory.MAINTENANCE || addon.getCategory() == AddonCategory.HATCH_BUS) {
                return true;
            }
            if (addon.getCategory() == AddonCategory.MULTIBLOCK_TRAIT && StarTTurbineHelper.isStarTTrait(addon)) {
                return StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
            }
            return false;
        }

        List<AddonCategory> applicable = getApplicableAddonCategories(node);
        if (!applicable.contains(addon.getCategory())) {
            return false;
        }

        boolean isGen = node.isGenerator();
        boolean isFusion = com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper.isFusion(node);

        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return node.isTurbine() && node.isMultiblock();
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            return isFusion;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            if (isGen || !node.canUseCoils() || !node.isMultiblock()) return false;
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = MultiblockStructureCatalog.getStructure(mbId);
                if (def != null && def.coilSlotCount() == 0) return false;
            }
            return true;
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            return node.isMultiblock() && MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            if (!node.isMultiblock()) return false;
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = MultiblockStructureCatalog.getStructure(mbId);
                if (def != null) {
                    if (isMufflerAddon(addon)) {
                        boolean hasMuffler = def.parts().stream().anyMatch(p -> p != null && p.itemId() != null && p.itemId().getPath().contains("muffler"));
                        if (!hasMuffler) return false;
                    } else {
                        if (def.maintenanceSlotCount() == 0 && !def.supportsAbility("MAINTENANCE")) return false;
                    }
                }
            }
            return true;
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            if ((!node.isMultiblock() && !isFusion) || isGen) return false;
            if (isFusion && addon instanceof GTEnergyHatchAddon eh) {
                if (eh.getTier() != node.getTargetTier()) {
                    return false;
                }
            }
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = MultiblockStructureCatalog.getStructure(mbId);
                if (def != null) {
                    if (addon instanceof GTEnergyHatchAddon eh) {
                        if (eh.isLaser()) {
                            if (!def.allowedAbilities().isEmpty() && !def.supportsAbility("INPUT_LASER")) return false;
                        } else if (eh.isSubstation()) {
                            if (!def.allowedAbilities().isEmpty() && !def.supportsAbility("SUBSTATION_INPUT_ENERGY")) return false;
                        } else {
                            if (!def.allowedAbilities().isEmpty() && !def.supportsAbility("INPUT_ENERGY")) return false;
                        }
                    } else if (!def.allowedAbilities().isEmpty() && !def.supportsAbility("INPUT_ENERGY") && !def.supportsAbility("INPUT_LASER") && !def.supportsAbility("SUBSTATION_INPUT_ENERGY")) {
                        return false;
                    }
                    if (def.energyHatchSlotCount() == 0 && (MultiblockDetector.isSteamMultiblock(mbId) || isGen)) return false;
                }
            }
            return true;
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            if (!node.isMultiblock()) return false;
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = MultiblockStructureCatalog.getStructure(mbId);
                if (def != null) {
                    // 1. Candidate block direct match
                    if (addon.getItemIcon() != null && def.isCandidateBlock(addon.getItemIcon())) {
                        return true;
                    }

                    // 2. PartAbility matching
                    if (def.allowedAbilities() != null && !def.allowedAbilities().isEmpty()) {
                        if (addon instanceof GTHatchAddon gh && !gh.getAbilities().isEmpty()) {
                            for (String reqAbility : gh.getAbilities()) {
                                if (!def.supportsAbility(reqAbility)) {
                                    return false;
                                }
                            }
                        } else {
                            var type = resolveHatchType(addon);
                            switch (type) {
                                case ITEM_INPUT -> {
                                    if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("STEAM_IMPORT_ITEMS")) return false;
                                }
                                case ITEM_OUTPUT -> {
                                    if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("STEAM_EXPORT_ITEMS")) return false;
                                }
                                case FLUID_INPUT -> {
                                    if (!def.supportsAbility("IMPORT_FLUIDS") && !def.supportsAbility("STEAM_IMPORT_FLUIDS")) return false;
                                }
                                case FLUID_OUTPUT -> {
                                    if (!def.supportsAbility("EXPORT_FLUIDS") && !def.supportsAbility("STEAM_EXPORT_FLUIDS")) return false;
                                }
                                case DUAL_INPUT -> {
                                    if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("IMPORT_FLUIDS")) return false;
                                }
                                case DUAL_OUTPUT -> {
                                    if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("EXPORT_FLUIDS")) return false;
                                }
                                default -> {}
                            }
                        }
                    }
                }
            }
            if (isDistillationTower(node)) {
                if (addon instanceof GTHatchAddon h) {
                    if ((h.getHatchType() == GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == GTHatchAddon.HatchType.DUAL_OUTPUT) && h.getSlotCapacity() > 1) {
                        return false;
                    }
                } else {
                    String path = addon.getId().toLowerCase(Locale.ROOT);
                    if ((path.contains("4x") || path.contains("9x") || path.contains("16x") || path.contains("quadruple") || path.contains("nonuple") || path.contains("hexadecimal") || path.contains("multi_fluid")) && (path.contains("output") || path.contains("export"))) {
                        return false;
                    }
                }
            }
            return true;
        }
        if (addon.getCategory().equals(AddonCategory.THREADING)) {
            return node.hasThreading();
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            if (StarTTurbineHelper.isStarTTrait(addon)) {
                return node.isTurbine() && StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
            }
            if (node.isTurbine()) return false;
            if (addon.getId().equals("gtceu:batch_processing")) {
                return !isGen && node.isMultiblock() && MultiblockDetector.supportsBatchMode(node.getMachineIcon(), node.getAvailableWorkstations());
            }
            if (addon.getId().equals("gtceu:throughput_boosting")) {
                return !isGen && node.isMultiblock() && MultiblockDetector.supportsThroughputBoosting(node.getMachineIcon());
            }
            if (addon.getId().equals("gtceu:bulk_processing")) {
                return !isGen && node.isMultiblock() && MultiblockDetector.supportsBulkProcessing(node.getMachineIcon());
            }
            if (addon.getId().equals("gtceu:overpressure_autoclave")) {
                return !isGen && node.isMultiblock() && MultiblockDetector.supportsOverpressure(node.getMachineIcon());
            }
            if (addon.getItemIcon() != null) {
                ResourceLocation target = addon.getItemIcon();
                if (node.getMachineIcon() != null && node.getMachineIcon().equals(target)) return true;
                if (node.getAvailableWorkstations().contains(target)) return true;
                if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().equals(target)) return true;
                return false;
            }
            return false;
        }

        return true;
    }

    public static boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory() == AddonCategory.CUSTOM || addon.getCategory() == AddonCategory.THERMAL_AUGMENT) {
            return true;
        }
        if (isDistillationTower(node)) {
            if (addon instanceof GTHatchAddon h) {
                if (h.getHatchType() == GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == GTHatchAddon.HatchType.DUAL_OUTPUT) {
                    if (h.getSlotCapacity() > 1) {
                        return false;
                    }
                    int reqFluidOut = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
                    long currentInstalled = node.getAddons().stream()
                            .filter(a -> a instanceof GTHatchAddon gh && (gh.getHatchType() == GTHatchAddon.HatchType.FLUID_OUTPUT || gh.getHatchType() == GTHatchAddon.HatchType.DUAL_OUTPUT))
                            .count();
                    if (reqFluidOut > 0 && currentInstalled >= reqFluidOut) {
                        return false;
                    }
                }
            } else if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                String path = addon.getId().toLowerCase(Locale.ROOT);
                if ((path.contains("4x") || path.contains("9x") || path.contains("16x") || path.contains("quadruple") || path.contains("nonuple") || path.contains("hexadecimal") || path.contains("multi_fluid")) && (path.contains("output") || path.contains("export"))) {
                    return false;
                }
            }
        }
        if (node.isMultiblock()) {
            ResourceLocation mbWs = node.getMachineIcon();
            if (mbWs == null) mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                var def = MultiblockStructureCatalog.getStructure(mbWs);
                if (def != null) {
                    if (addon.getCategory() == MachineAddon.Category.COIL && def.coilSlotCount() == 0 && !MultiblockDetector.isCoilMultiblock(mbWs)) return false;
                    if (addon.getCategory() == MachineAddon.Category.MAINTENANCE && def.maintenanceSlotCount() == 0 && !def.supportsAbility("MAINTENANCE") && def.allowedAbilities() != null && !def.allowedAbilities().isEmpty()) return false;
                    if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH && def.energyHatchSlotCount() == 0 && !def.supportsAbility("INPUT_ENERGY") && !def.supportsAbility("SUBSTATION_INPUT_ENERGY") && (MultiblockDetector.isSteamMultiblock(mbWs) || node.isGenerator())) return false;
                    if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                        if (addon.getItemIcon() != null && def.isCandidateBlock(addon.getItemIcon())) {
                            // Directly matched candidate block
                        } else if (def.allowedAbilities() != null && !def.allowedAbilities().isEmpty()) {
                            if (addon instanceof GTHatchAddon gh && !gh.getAbilities().isEmpty()) {
                                for (String reqAbility : gh.getAbilities()) {
                                    if (!def.supportsAbility(reqAbility)) return false;
                                }
                            } else {
                                var type = resolveHatchType(addon);
                                switch (type) {
                                    case ITEM_INPUT -> { if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("STEAM_IMPORT_ITEMS")) return false; }
                                    case ITEM_OUTPUT -> { if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("STEAM_EXPORT_ITEMS")) return false; }
                                    case FLUID_INPUT -> { if (!def.supportsAbility("IMPORT_FLUIDS") && !def.supportsAbility("STEAM_IMPORT_FLUIDS")) return false; }
                                    case FLUID_OUTPUT -> { if (!def.supportsAbility("EXPORT_FLUIDS") && !def.supportsAbility("STEAM_EXPORT_FLUIDS")) return false; }
                                    case DUAL_INPUT -> { if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("IMPORT_FLUIDS")) return false; }
                                    case DUAL_OUTPUT -> { if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("EXPORT_FLUIDS")) return false; }
                                    default -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR && !node.isTurbine() && !MachineAddon.isTurbineMachine(node)) {
            return false;
        }
        return true;
    }

    public static GTHatchAddon.HatchType resolveHatchType(MachineAddon addon) {
        if (addon instanceof GTHatchAddon gh) {
            return gh.getHatchType();
        }
        if (addon == null || addon.getId() == null) return GTHatchAddon.HatchType.ITEM_INPUT;
        String idStr = addon.getId().toLowerCase(Locale.ROOT);
        if (idStr.contains("me_pattern_provider") || idStr.contains("pattern_provider")) return GTHatchAddon.HatchType.ME_PATTERN_PROVIDER;
        if (idStr.contains("dual_input") || idStr.contains("stocking_input") || idStr.contains("stocking_bus")) return GTHatchAddon.HatchType.DUAL_INPUT;
        if (idStr.contains("dual_output")) return GTHatchAddon.HatchType.DUAL_OUTPUT;
        if (idStr.contains("input_hatch") || idStr.contains("fluid_import") || idStr.contains("multi_fluid_input")) return GTHatchAddon.HatchType.FLUID_INPUT;
        if (idStr.contains("output_hatch") || idStr.contains("fluid_export") || idStr.contains("multi_fluid_output")) return GTHatchAddon.HatchType.FLUID_OUTPUT;
        if (idStr.contains("input_bus") || idStr.contains("import_bus") || idStr.contains("item_import")) return GTHatchAddon.HatchType.ITEM_INPUT;
        if (idStr.contains("output_bus") || idStr.contains("export_bus") || idStr.contains("item_export")) return GTHatchAddon.HatchType.ITEM_OUTPUT;
        return GTHatchAddon.HatchType.ITEM_INPUT;
    }

    public static ResourceLocation getPreferredMultiblockWorkstation(RecipeNode node, List<ResourceLocation> availableWorkstations) {
        if (node == null || availableWorkstations == null || availableWorkstations.isEmpty()) return null;

        if (com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper.isFusion(node)) {
            GTVoltageTier minTier = com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper.getMinFusionVoltageTier(node);

            for (ResourceLocation ws : availableWorkstations) {
                if (ws != null) {
                    GTVoltageTier wsTier = GTCEuModAdapter.extractVoltageTierFromIcon(ws);
                    if (wsTier == minTier) {
                        return ws;
                    }
                }
            }

            ResourceLocation bestHigher = null;
            GTVoltageTier bestTier = null;
            for (ResourceLocation ws : availableWorkstations) {
                if (ws != null) {
                    GTVoltageTier wsTier = GTCEuModAdapter.extractVoltageTierFromIcon(ws);
                    if (wsTier != null && wsTier.ordinal() >= minTier.ordinal()) {
                        if (bestTier == null || wsTier.ordinal() < bestTier.ordinal()) {
                            bestTier = wsTier;
                            bestHigher = ws;
                        }
                    }
                }
            }
            if (bestHigher != null) {
                return bestHigher;
            }
        }

        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (MultiblockDetector.isMultiblock(ws) && ws.getPath().equalsIgnoreCase(catId.getPath())) {
                    return ws;
                }
            }
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                String path = ws.getPath().toLowerCase(Locale.ROOT);
                if (path.startsWith("large_")) {
                    return ws;
                }
            }
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                String path = ws.getPath().toLowerCase(Locale.ROOT);
                if (!path.startsWith("mega_") && !path.startsWith("extreme_") && !path.startsWith("incomprehensible_")
                        && !path.startsWith("advanced_") && !path.startsWith("yielding_") && !path.startsWith("super_")
                        && !path.startsWith("supreme_") && !path.startsWith("nyinsane_")) {
                    return ws;
                }
            }
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                return ws;
            }
        }
        return null;
    }

    public static void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (!node.isMultiblock() && addon.getCategory() != AddonCategory.CUSTOM && addon.getCategory() != AddonCategory.THERMAL_AUGMENT) {
            node.setMultiblock(true);
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            }
        }
        applyAddonInstallation(node, addon);
        node.markOverclockDirty();
    }

    private static void applyAddonInstallation(RecipeNode node, MachineAddon addon) {
        if (addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT) {
            if (addon.getId().contains("upgrade_kit") || addon.getId().contains("tier_kit")) {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && (a.getId().contains("upgrade_kit") || a.getId().contains("tier_kit")));
            }
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int power = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            node.setRotorEfficiency(eff);
            node.setRotorPower(power);
            node.setRotorName(addon.getName());
            node.getAddons().add(addon);
            GTTurbinePhysics.autoCalculateTurbineParallel(node);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
            MachineAddon tailored = addon.forMachine(node);
            node.getAddons().add(tailored);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.REFLECTOR);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            if (isMufflerAddon(addon)) {
                node.getAddons().removeIf(GTAddonCompatibilityHandler::isMufflerAddon);
            } else {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.MAINTENANCE && !isMufflerAddon(a));
            }
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            List<MachineAddon> existing = new ArrayList<>();
            for (MachineAddon a : node.getAddons()) {
                if (a.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                    existing.add(a);
                }
            }
            if (existing.size() >= 2) {
                node.getAddons().remove(existing.get(0));
            }
            node.getAddons().add(addon);
            updateNodeTierFromEnergyHatches(node);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            node.getAddons().add(addon);
            return;
        }
        node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
        node.getAddons().add(addon);
    }

    public static void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            updateNodeTierFromEnergyHatches(node);
            if (com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isTurbine(node)) {
                List<GTEnergyHatchAddon> remaining = node.getAddons().stream()
                        .filter(a -> a instanceof GTEnergyHatchAddon)
                        .map(a -> (GTEnergyHatchAddon) a)
                        .toList();
                if (!remaining.isEmpty()) {
                    com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoTier(node, remaining.get(0).getTier());
                    int totalAmps = remaining.stream().mapToInt(GTEnergyHatchAddon::getAmperage).sum();
                    com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoAmperage(node, totalAmps);
                } else {
                    com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoTier(node, node.getTargetTier() != null ? node.getTargetTier() : GTVoltageTier.EV);
                    com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoAmperage(node, 1);
                }
            }
        } else if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            GTTurbinePhysics.autoCalculateTurbineParallel(node);
        } else if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
        }
        node.markOverclockDirty();
    }

    public static void updateNodeTierFromEnergyHatches(RecipeNode node) {
        if (node == null) return;
        List<GTEnergyHatchAddon> hatches = new ArrayList<>();
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof GTEnergyHatchAddon eh) {
                hatches.add(eh);
            }
        }
        if (hatches.isEmpty()) {
            if (node.getRecipeTier() != null) {
                node.setTargetTier(node.getRecipeTier());
            }
            return;
        }

        long totalEUtCapacity = 0;
        GTVoltageTier maxSingleHatchTier = GTVoltageTier.ULV;

        for (var h : hatches) {
            totalEUtCapacity += (long) h.getTier().getVoltage() * h.getAmperage();
            if (h.getTier().ordinal() > maxSingleHatchTier.ordinal()) {
                maxSingleHatchTier = h.getTier();
            }
        }

        if (hatches.size() == 2 && hatches.get(0).getTier() == hatches.get(1).getTier()) {
            GTVoltageTier base = hatches.get(0).getTier();
            GTVoltageTier dualTier = base.ordinal() < GTVoltageTier.MAX.ordinal()
                    ? GTVoltageTier.getByIndex(base.ordinal() + 1)
                    : base;
            node.setTargetTier(dualTier);
            return;
        }

        node.setTargetTier(maxSingleHatchTier);

        if (com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isTurbine(node)) {
            GTEnergyHatchAddon primary = hatches.get(0);
            com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoTier(node, primary.getTier());
            int totalAmps = hatches.stream().mapToInt(GTEnergyHatchAddon::getAmperage).sum();
            com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoAmperage(node, totalAmps);
        }
    }

    public static long getMaxEUtCapacity(RecipeNode node) {
        if (node == null) return Long.MAX_VALUE;
        List<GTEnergyHatchAddon> hatches = new ArrayList<>();
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof GTEnergyHatchAddon eh) {
                hatches.add(eh);
            }
        }
        if (!hatches.isEmpty()) {
            long total = 0;
            for (var h : hatches) {
                total += (long) h.getTier().getVoltage() * h.getAmperage();
            }
            return total;
        }
        return Long.MAX_VALUE;
    }

    public static void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
    }
}
