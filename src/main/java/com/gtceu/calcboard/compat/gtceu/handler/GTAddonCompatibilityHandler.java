package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
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
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import com.gtceu.calcboard.compat.start.StarTTurbineHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles GTCEu machine addon compatibility, categories, installation lifecycles, and tooltips.
 * Delegated to GTEnergyHatchCalculator, GTCombustionAddonHelper, and GTAddonLifecycleHandler.
 */
public final class GTAddonCompatibilityHandler {

    private GTAddonCompatibilityHandler() {}

    public static final ResourceLocation DISTILLATION_TOWER_ID = ResourceLocation.tryParse("gtceu:distillation_tower");

    public static boolean isMufflerAddon(MachineAddon addon) {
        return GTMufflerMaintenanceHelper.isMufflerAddon(addon);
    }

    private static boolean isMaintenanceAddonCompatible(RecipeNode node, MachineAddon addon) {
        return GTMufflerMaintenanceHelper.isMaintenanceAddonCompatible(node, addon);
    }

    public static boolean isDistillationTower(ResourceLocation id) {
        return DISTILLATION_TOWER_ID != null && DISTILLATION_TOWER_ID.equals(id);
    }

    public static boolean isDistillationTower(RecipeNode node) {
        if (node == null) return false;
        if (isDistillationTower(node.getMachineIcon())) return true;
        if (isDistillationTower(node.getMultiblockWorkstation())) return true;
        if (isDistillationTower(node.getRecipeCategoryId())) return true;
        return false;
    }

    public static boolean supportsAddons(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.NONE) return false;
        if (GTCombustionHelper.isCombustionFamily(node)) {
            return node.isMultiblock();
        }
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

        if (GTCombustionHelper.isCombustionFamily(node)) {
            if (node.isMultiblock()) {
                List<AddonCategory> cats = new ArrayList<>();
                cats.add(AddonCategory.MULTIBLOCK_TRAIT);
                cats.add(AddonCategory.MAINTENANCE);
                cats.add(AddonCategory.HATCH_BUS);
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of(AddonCategory.CUSTOM);
        }

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
            return resolveMultiblockApplicableCategories(node);
        }

        List<AddonCategory> cats = new ArrayList<>();
        if (node.hasThreading()) {
            cats.add(AddonCategory.THREADING);
        }
        cats.add(AddonCategory.CUSTOM);
        return cats;
    }

    private static List<AddonCategory> resolveMultiblockApplicableCategories(RecipeNode node) {
        ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        var def = mbId != null ? MultiblockStructureCatalog.getStructure(mbId) : null;
        boolean isSteamMb = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || (mbId != null && MultiblockDetector.isSteamMultiblock(mbId));

        List<AddonCategory> cats = new ArrayList<>();
        if (!isSteamMb && !node.isGenerator() && node.getEnergyType() != EnergyType.NONE && node.getEnergyType() != EnergyType.KINETIC_SU
                && (def == null || def.supportsAbility("INPUT_ENERGY") || def.supportsAbility("SUBSTATION_INPUT_ENERGY") || def.supportsAbility("INPUT_LASER") || def.energyHatchSlotCount() > 0 || def.allowedAbilities().isEmpty() || node.getEnergyType() != EnergyType.NONE)) {
            cats.add(AddonCategory.ENERGY_HATCH);
        }
        if (def == null || !def.allowedAbilities().isEmpty() || def.inputBusSlotCount() > 0 || def.outputBusSlotCount() > 0 || def.inputHatchSlotCount() > 0 || def.outputHatchSlotCount() > 0 || !node.getInputs().isEmpty() || !node.getOutputs().isEmpty() || node.isMultiblock()) {
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
        boolean supportsPar = mbId != null
                ? (MultiblockDetector.supportsParallelHatch(mbId) || (def != null && def.supportsAbility("PARALLEL_HATCH")))
                : MultiblockDetector.supportsParallelHatch(null, node.getAvailableWorkstations());
        if (!isSteamMb && supportsPar) {
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

    public static boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;

        if (GTPowerCalculator.isBoilerRecipe(node)) {
            if (!node.isMultiblock()) return false;
            return addon.getCategory() == AddonCategory.MAINTENANCE || addon.getCategory() == AddonCategory.HATCH_BUS;
        }

        if (GTCombustionHelper.isCombustionFamily(node)) {
            if (!node.isMultiblock()) return false;
            if (addon.getCategory() == AddonCategory.MAINTENANCE || addon.getCategory() == AddonCategory.HATCH_BUS) return true;
            if (addon.getCategory() == AddonCategory.MULTIBLOCK_TRAIT) {
                return GTCombustionAddonHelper.isCombustionBoostCompatible(node, addon);
            }
            return false;
        }

        if (node.isTurbine()) {
            if (!node.isMultiblock()) return false;
            if (addon.getCategory() == AddonCategory.ROTOR || addon.getCategory() == AddonCategory.MAINTENANCE || addon.getCategory() == AddonCategory.HATCH_BUS) return true;
            if (addon.getCategory() == AddonCategory.MULTIBLOCK_TRAIT && StarTTurbineHelper.isStarTTrait(addon)) {
                return StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
            }
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
            ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
            if (mbId != null) {
                var def = MultiblockStructureCatalog.getStructure(mbId);
                if (def != null && def.coilSlotCount() == 0) return false;
            }
            return true;
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            if (!node.isMultiblock()) return false;
            ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
            if (mbId != null) {
                var def = MultiblockStructureCatalog.getStructure(mbId);
                return MultiblockDetector.supportsParallelHatch(mbId) || (def != null && def.supportsAbility("PARALLEL_HATCH"));
            }
            return MultiblockDetector.supportsParallelHatch(null, node.getAvailableWorkstations());
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            return isMaintenanceAddonCompatible(node, addon);
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            return isEnergyHatchCompatible(node, addon, isGen, isFusion);
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            return isHatchBusCompatible(node, addon);
        }
        if (addon.getCategory().equals(AddonCategory.THREADING)) {
            return node.hasThreading();
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            return isMultiblockTraitCompatible(node, addon, isGen);
        }

        return true;
    }

    private static boolean isEnergyHatchCompatible(RecipeNode node, MachineAddon addon, boolean isGen, boolean isFusion) {
        if ((!node.isMultiblock() && !isFusion) || isGen) return false;
        if (isFusion && addon instanceof GTEnergyHatchAddon eh && eh.getTier() != node.getTargetTier()) {
            return false;
        }
        ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        if (mbId == null) return true;
        MultiblockStructureDef def = MultiblockStructureCatalog.getStructure(mbId);
        if (def == null) return true;
        if (!matchesEnergyHatchAbilities(def, addon, node, mbId, isGen)) return false;
        if (def.energyHatchSlotCount() == 0 && (MultiblockDetector.isSteamMultiblock(mbId) || isGen)) return false;
        return true;
    }

    private static boolean matchesEnergyHatchAbilities(MultiblockStructureDef def, MachineAddon addon, RecipeNode node, ResourceLocation mbId, boolean isGen) {
        if (addon instanceof GTEnergyHatchAddon eh) {
            if (eh.isLaser()) {
                return def.supportsAbility("INPUT_LASER") || def.allowedAbilities().isEmpty();
            }
            if (eh.isSubstation()) {
                return def.supportsAbility("SUBSTATION_INPUT_ENERGY") || def.allowedAbilities().isEmpty();
            }
            if (def.supportsAbility("INPUT_ENERGY") || def.energyHatchSlotCount() > 0 || def.allowedAbilities().isEmpty()) {
                return true;
            }
            boolean isSteam = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || MultiblockDetector.isSteamMultiblock(mbId);
            return !isSteam && !isGen && node.getEnergyType() != EnergyType.NONE && node.getEnergyType() != EnergyType.KINETIC_SU;
        }
        if (def.allowedAbilities().isEmpty()) return true;
        return def.supportsAbility("INPUT_ENERGY") || def.supportsAbility("INPUT_LASER") || def.supportsAbility("SUBSTATION_INPUT_ENERGY");
    }

    private static boolean isHatchBusCompatible(RecipeNode node, MachineAddon addon) {
        if (!node.isMultiblock()) return false;
        ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        if (mbId != null && !matchesHatchStructure(mbId, addon)) {
            return false;
        }
        if (isDistillationTower(node) && !isDistillationTowerHatchCompatible(addon)) {
            return false;
        }
        return true;
    }

    private static boolean matchesHatchStructure(ResourceLocation mbId, MachineAddon addon) {
        MultiblockStructureDef def = MultiblockStructureCatalog.getStructure(mbId);
        if (def == null) return true;
        if (addon.getItemIcon() != null && def.isCandidateBlock(addon.getItemIcon())) {
            return true;
        }
        if (def.allowedAbilities() == null || def.allowedAbilities().isEmpty()) {
            return true;
        }
        if (addon instanceof GTHatchAddon gh && !gh.getAbilities().isEmpty()) {
            return gh.getAbilities().stream().allMatch(def::supportsAbility);
        }
        return matchesHatchTypeAbilities(def, resolveHatchType(addon));
    }

    private static boolean matchesHatchTypeAbilities(MultiblockStructureDef def, GTHatchAddon.HatchType type) {
        return switch (type) {
            case ITEM_INPUT -> def.supportsAbility("IMPORT_ITEMS") || def.supportsAbility("STEAM_IMPORT_ITEMS");
            case ITEM_OUTPUT -> def.supportsAbility("EXPORT_ITEMS") || def.supportsAbility("STEAM_EXPORT_ITEMS");
            case FLUID_INPUT -> def.supportsAbility("IMPORT_FLUIDS") || def.supportsAbility("STEAM_IMPORT_FLUIDS");
            case FLUID_OUTPUT -> def.supportsAbility("EXPORT_FLUIDS") || def.supportsAbility("STEAM_EXPORT_FLUIDS");
            case DUAL_INPUT -> def.supportsAbility("IMPORT_ITEMS") || def.supportsAbility("IMPORT_FLUIDS");
            case DUAL_OUTPUT -> def.supportsAbility("EXPORT_ITEMS") || def.supportsAbility("EXPORT_FLUIDS");
            default -> true;
        };
    }

    private static boolean isDistillationTowerHatchCompatible(MachineAddon addon) {
        if (addon instanceof GTHatchAddon h) {
            boolean isFluidOut = h.getHatchType() == GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == GTHatchAddon.HatchType.DUAL_OUTPUT;
            return !isFluidOut || h.getSlotCapacity() <= 1;
        }
        String path = addon.getId().toLowerCase(Locale.ROOT);
        boolean isMultiFluid = path.contains("4x") || path.contains("9x") || path.contains("16x") || path.contains("quadruple") || path.contains("nonuple") || path.contains("hexadecimal") || path.contains("multi_fluid");
        boolean isOutput = path.contains("output") || path.contains("export");
        return !(isMultiFluid && isOutput);
    }

    private static boolean isMultiblockTraitCompatible(RecipeNode node, MachineAddon addon, boolean isGen) {
        if (!node.isMultiblock()) return false;
        if (StarTTurbineHelper.isStarTTrait(addon)) {
            return node.isTurbine() && StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
        }
        if (node.isTurbine()) return false;
        if (addon.getId().equals("gtceu:batch_processing")) {
            ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
            boolean supportsBatch = mbId != null
                    ? MultiblockDetector.supportsBatchMode(mbId)
                    : MultiblockDetector.supportsBatchMode(null, node.getAvailableWorkstations());
            return !isGen && node.isMultiblock() && supportsBatch;
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
            if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().equals(target)) return true;
            return false;
        }
        return false;
    }

    public static boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory() == AddonCategory.CUSTOM || addon.getCategory() == AddonCategory.THERMAL_AUGMENT) {
            return true;
        }
        if (isDistillationTower(node) && !canInstallDistillationTowerHatch(node, addon)) {
            return false;
        }
        if (node.isMultiblock() && !canInstallMultiblockAddon(node, addon)) {
            return false;
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR && !node.isTurbine() && !MachineAddon.isTurbineMachine(node)) {
            return false;
        }
        return true;
    }

    private static boolean canInstallDistillationTowerHatch(RecipeNode node, MachineAddon addon) {
        if (addon instanceof GTHatchAddon h) {
            boolean isFluidOut = h.getHatchType() == GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == GTHatchAddon.HatchType.DUAL_OUTPUT;
            if (!isFluidOut) return true;
            if (h.getSlotCapacity() > 1) return false;
            int reqFluidOut = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            long currentInstalled = node.getAddons().stream()
                    .filter(a -> a instanceof GTHatchAddon gh && (gh.getHatchType() == GTHatchAddon.HatchType.FLUID_OUTPUT || gh.getHatchType() == GTHatchAddon.HatchType.DUAL_OUTPUT))
                    .count();
            return reqFluidOut == 0 || currentInstalled < reqFluidOut;
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            String path = addon.getId().toLowerCase(Locale.ROOT);
            boolean isMulti = path.contains("4x") || path.contains("9x") || path.contains("16x") || path.contains("quadruple") || path.contains("nonuple") || path.contains("hexadecimal") || path.contains("multi_fluid");
            boolean isOut = path.contains("output") || path.contains("export");
            if (isMulti && isOut) return false;
        }
        return true;
    }

    private static boolean canInstallMultiblockAddon(RecipeNode node, MachineAddon addon) {
        ResourceLocation mbWs = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        if (mbWs == null) return true;
        MultiblockStructureDef def = MultiblockStructureCatalog.getStructure(mbWs);
        if (def == null) return true;
        if (addon.getCategory() == MachineAddon.Category.COIL && def.coilSlotCount() == 0 && !MultiblockDetector.isCoilMultiblock(mbWs)) return false;
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE && def.maintenanceSlotCount() == 0 && !def.supportsAbility("MAINTENANCE") && def.allowedAbilities() != null && !def.allowedAbilities().isEmpty()) return false;
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH && def.energyHatchSlotCount() == 0 && !def.supportsAbility("INPUT_ENERGY") && !def.supportsAbility("SUBSTATION_INPUT_ENERGY") && (MultiblockDetector.isSteamMultiblock(mbWs) || node.isGenerator())) return false;
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS && !isHatchBusCompatible(node, addon)) return false;
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
        return GTWorkstationSelector.getPreferredMultiblockWorkstation(node, availableWorkstations);
    }

    public static void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        GTAddonLifecycleHandler.onAddonInstalled(node, addon);
    }

    public static void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        GTAddonLifecycleHandler.onAddonRemoved(node, addon);
    }

    public static boolean isCombustionBoostAddon(MachineAddon addon) {
        return GTCombustionAddonHelper.isCombustionBoostAddon(addon);
    }

    public static boolean isCoolantAddon(MachineAddon addon) {
        return GTCombustionAddonHelper.isCoolantAddon(addon);
    }

    public static boolean isOxidizerAddon(MachineAddon addon) {
        return GTCombustionAddonHelper.isOxidizerAddon(addon);
    }

    public static int getMaxAllowedEnergyHatches(RecipeNode node) {
        return GTEnergyHatchCalculator.getMaxAllowedEnergyHatches(node);
    }

    public static GTVoltageTier getPrimaryEnergyHatchTier(RecipeNode node) {
        return GTEnergyHatchCalculator.getPrimaryEnergyHatchTier(node);
    }

    public static boolean requiresEnergyHatch(RecipeNode node) {
        return GTEnergyHatchCalculator.requiresEnergyHatch(node);
    }

    public static void updateNodeTierFromEnergyHatches(RecipeNode node) {
        GTEnergyHatchCalculator.updateNodeTierFromEnergyHatches(node);
    }

    public static long getMaxEUtCapacity(RecipeNode node) {
        return GTEnergyHatchCalculator.getMaxEUtCapacity(node);
    }

    public static long getOverclockVoltage(RecipeNode node) {
        return GTEnergyHatchCalculator.getOverclockVoltage(node);
    }

    public static boolean hasEnergyHatch(RecipeNode node) {
        return GTEnergyHatchCalculator.hasEnergyHatch(node);
    }

    public static void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
    }
}
