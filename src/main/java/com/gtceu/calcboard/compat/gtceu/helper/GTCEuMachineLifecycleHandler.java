package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.start.helper.RecipeNodeThreadingHelper;
import com.gtceu.calcboard.compat.start.model.NodeThreadingConfig;
import com.gtceu.calcboard.compat.start.model.StarTProperties;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class GTCEuMachineLifecycleHandler {

    private GTCEuMachineLifecycleHandler() {}

    public static void onMachineIconChanged(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        if (node == null || newIcon == null) return;

        ResourceLocation normalized = GTCombustionHelper.normalizeMachineIcon(newIcon);
        if (normalized != null && !normalized.equals(node.getMachineIcon())) {
            node.setMachineIcon(normalized);
            return;
        }

        if (GTCombustionHelper.isSingleblockCombustionGenerator(newIcon)) {
            node.setMultiblock(false);
            node.setGenerator(true);
            if (node.getSteamMode().isSteam()) {
                node.setSteamMode(SteamMode.NONE);
            }
        } else if (MultiblockDetector.isSteamMultiblock(newIcon)) {
            node.setMultiblock(true);
            int defPar = MultiblockDetector.getDefaultParallel(newIcon);
            node.setParallel(Math.max(1, defPar));
            node.setSteamMode(SteamMode.HIGH_PRESSURE);
        } else if (MultiblockDetector.isMultiblock(newIcon) || node.isFusion() || GTCombustionHelper.isCombustionEngine(newIcon)) {
            node.setMultiblock(true);
            if (node.getSteamMode().isSteam()) {
                node.setSteamMode(SteamMode.NONE);
            }
            int defPar = MultiblockDetector.getDefaultParallel(newIcon);
            if (defPar > 1 && node.getParallel() <= 1) {
                node.setParallel(defPar);
            }
        } else {
            node.setMultiblock(false);
            if (node.getParallel() > 1 && oldIcon != null && MultiblockDetector.isMultiblock(oldIcon)) {
                node.setParallel(1);
            }
        }

        if (oldIcon != null && !oldIcon.equals(newIcon)) {
            purgeIncompatibleAddons(node, oldIcon, newIcon);
        }

        applyMachinePresets(node, oldIcon, newIcon);
        boolean wasCombustion = GTCombustionHelper.isCombustionMachine(oldIcon);
        boolean isCombustion = GTCombustionHelper.isCombustionFamily(node);
        if (wasCombustion || isCombustion) {
            if (wasCombustion && !isCombustion) {
                GTCombustionHelper.removeCombustionAuxiliaryInputs(node);
            } else {
                GTCombustionHelper.syncCombustionInputs(node);
            }
        }
    }

    public static void purgeIncompatibleAddons(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        if (!MultiblockDetector.isCoilMultiblock(newIcon) && !node.canUseCoils()) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
        }

        if (!MultiblockDetector.supportsParallelHatch(newIcon, null, null)) {
            boolean hadParAddon = node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
            if (hadParAddon || GTCombustionHelper.isCombustionEngine(newIcon)) {
                int defPar = MultiblockDetector.getDefaultParallel(newIcon);
                node.setParallel(Math.max(1, defPar));
                node.setCustomParallel(0);
            }
        }

        if (!MultiblockDetector.supportsTurbineRotor(newIcon, null) && !MultiblockDetector.isTurbineMachine(newIcon)) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            node.setRotorName(null);
        }

        if (!MultiblockDetector.supportsLaserHatch(newIcon, null)) {
            node.getAddons().removeIf(a -> a instanceof GTEnergyHatchAddon eh && eh.isLaser());
        }

        if (MultiblockDetector.getMaxHelixCount(newIcon) == 0) {
            RecipeNodeThreadingHelper.setThreadingConfig(node, null);
        } else {
            NodeThreadingConfig cfg = node.getProperties().get(StarTProperties.THREADING_CONFIG);
            if (cfg != null) {
                cfg.setMaxHelixCapacity(MultiblockDetector.getMaxHelixCount(newIcon));
            }
        }

        if (!node.isFusion() && node.getRequiredReflectorTier() <= 0) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.REFLECTOR);
        }

        if (!MultiblockDetector.supportsThroughputBoosting(newIcon)) {
            node.getAddons().removeIf(a -> a.getId() != null && a.getId().equals("gtceu:throughput_boosting"));
        }
        if (!MultiblockDetector.supportsBulkProcessing(newIcon)) {
            node.getAddons().removeIf(a -> a.getId() != null && a.getId().equals("gtceu:bulk_processing"));
        }
        if (!MultiblockDetector.supportsOverpressure(newIcon)) {
            node.getAddons().removeIf(a -> a.getId() != null && a.getId().equals("gtceu:overpressure_autoclave"));
        }
        if (!GTCombustionHelper.isLargeCombustionEngine(node)) {
            node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, false);
            node.getAddons().removeIf(a -> "gtceu:oxygen_boost".equals(a.getId()));
        }
        if (!GTCombustionHelper.isExtremeCombustionEngine(node)) {
            node.getProperties().set(GTCEuProperties.LIQUID_OXYGEN_BOOST, false);
            node.getAddons().removeIf(a -> "gtceu:liquid_oxygen_boost".equals(a.getId()));
        }
        if (!GTCombustionHelper.isStarTCombustionModule(node) && !GTCombustionHelper.isStarTRocketModule(node)) {
            node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "none");
            node.getAddons().removeIf(GTAddonCompatibilityHandler::isOxidizerAddon);
        }
        if (!GTCombustionHelper.isModularCombustionFrame(node) && !GTCombustionHelper.isStarTCombustionModule(node) && !GTCombustionHelper.isStarTRocketModule(node)) {
            node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "none");
            node.getAddons().removeIf(GTAddonCompatibilityHandler::isCoolantAddon);
        }
        if (!GTCombustionHelper.isCombustionEngine(node) || !node.isMultiblock()) {
            node.getAddons().removeIf(GTAddonCompatibilityHandler::isCombustionBoostAddon);
        }
    }

    public static void applyMachinePresets(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        boolean isGen = GTCombustionHelper.isCombustionMachine(newIcon)
                || GTTurbineHelper.isTurbineMachine(newIcon)
                || isNodeRecipeGenerator(node);
        boolean wasGen = GTCombustionHelper.isCombustionMachine(oldIcon)
                || GTTurbineHelper.isTurbineMachine(oldIcon);

        if (isGen) {
            node.setGenerator(true);
        } else if (wasGen) {
            node.setGenerator(false);
        }

        if (MultiblockDetector.isTurbineMachine(newIcon)) {
            node.setGenerator(true);
            GTVoltageTier baseTier = MultiblockDetector.getTurbineBaseTier(newIcon);
            if (baseTier != null && (node.getTargetTier() == null || (MultiblockDetector.requiresMinimumBaseTier(newIcon) && node.getTargetTier().ordinal() < baseTier.ordinal()))) {
                node.setTargetTier(baseTier);
            }
            int defPar = MultiblockDetector.getDefaultParallel(newIcon);
            configureTurbineParallel(node, oldIcon, defPar);
        }

        if (oldIcon != null && !oldIcon.equals(newIcon)) {
            ensureAddonIfSupported(node, "gtceu:throughput_boosting", MultiblockDetector.supportsThroughputBoosting(newIcon));
            ensureAddonIfSupported(node, "gtceu:overpressure_autoclave", MultiblockDetector.supportsOverpressure(newIcon));
        }

        GTVoltageTier iconTier = GTCEuWorkstationResolver.extractVoltageTierFromIcon(newIcon);
        if (iconTier != null && !node.isTurbine()) {
            GTVoltageTier effectiveTier = GTCEuWorkstationResolver.sanitizeTargetTier(node, iconTier);
            node.setTargetTier(effectiveTier);
            if (node.isFusion()) {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH
                        && a instanceof GTEnergyHatchAddon eh
                        && eh.getTier() != effectiveTier);
            }
        }

        if (node.getRecipeCategoryId() != null && GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId())) {
            String newNs = newIcon.getNamespace().toLowerCase(Locale.ROOT);
            if (newNs.equals("gtceu") || newNs.contains("start")) {
                node.setBaseEUt(4.0);
                node.setRecipeTier(GTVoltageTier.LV);
                if (node.getTargetTier() == null || node.getTargetTier() == GTVoltageTier.ULV) {
                    node.setTargetTier(GTVoltageTier.LV);
                }
                node.setBaseDurationTicks(128.0);
                node.setEnergyType(EnergyType.ELECTRIC_EU);
            } else if (newNs.equals("minecraft")) {
                node.setBaseEUt(0.0);
                node.setRecipeTier(GTVoltageTier.ULV);
                node.setTargetTier(GTVoltageTier.ULV);
                node.setBaseDurationTicks(200.0);
                node.setEnergyType(EnergyType.NONE);
            }
        }
    }

    private static void configureTurbineParallel(RecipeNode node, ResourceLocation oldIcon, int defPar) {
        if (oldIcon != null && MultiblockDetector.isTurbineMachine(oldIcon)) {
            if (GTTurbineHelper.hasRotorAddon(node)) {
                GTTurbineHelper.autoCalculateTurbineParallel(node);
                return;
            }
            node.setParallel(Math.max(1, defPar));
            return;
        }
        if (defPar > 1) {
            node.setParallel(defPar);
        }
    }

    private static void ensureAddonIfSupported(RecipeNode node, String addonId, boolean isSupported) {
        if (!isSupported) return;
        boolean hasAddon = node.getAddons().stream().anyMatch(a -> addonId.equals(a.getId()));
        if (hasAddon) return;
        MachineAddon addon = MachineAddonCatalog.getInstance().getAddon(addonId);
        if (addon != null) {
            node.addAddon(addon);
        }
    }

    private static boolean isNodeRecipeGenerator(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId == null) return false;
        return MultiblockDetector.isTurbineRecipeCategory(catId)
                || GTCombustionHelper.COMBUSTION_GENERATOR.equals(catId)
                || ResourceLocation.tryParse("gtceu:combustion_generator_fuels").equals(catId);
    }

    public static void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        if (newMode != null && newMode.isSteam()) {
            double durTicks = node.getBaseDurationTicks() * newMode.getDurationMultiplier();
            double baseEu = (node.getBaseEUt() > 0) ? node.getBaseEUt() : 4.0;
            double steamAmountPerBatch;
            if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), newMode);
                int parallel = Math.max(1, node.getParallel());
                steamAmountPerBatch = (steamRatePerTick * durTicks) / parallel;
            } else {
                steamAmountPerBatch = (baseEu * 2.0) * durTicks;
            }

            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            node.getInputs().add(IngredientStack.fluid(steamId, "Steam", steamAmountPerBatch));

            updateSteamWorkstationIcon(node, newMode);
        } else if (oldMode != null && oldMode.isSteam()) {
            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            if (!node.isMultiblock() && !MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                ResourceLocation sbWs = node.getWorkstationForTier(node.getTargetTier());
                if (sbWs == null) {
                    sbWs = node.getSingleblockWorkstation();
                }
                if (sbWs != null) {
                    node.setMachineIcon(sbWs);
                }
            }
        }
    }

    private static void updateSteamWorkstationIcon(RecipeNode node, SteamMode newMode) {
        if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon()) || node.getRecipeCategoryId() == null) {
            return;
        }
        CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
        ResourceLocation icon = resolveSteamWorkstationIcon(node.getRecipeCategoryId(), cap, newMode);
        if (icon != null) {
            node.setMachineIcon(icon);
        }
    }

    private static ResourceLocation resolveSteamWorkstationIcon(ResourceLocation catId, CategoryCapability cap, SteamMode mode) {
        if (mode == SteamMode.LOW_PRESSURE) {
            if (cap != null && cap.lowPressureWorkstation() != null) return cap.lowPressureWorkstation();
            if (GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(catId)) return ResourceLocation.tryParse("gtceu:lp_steam_furnace");
        } else if (mode == SteamMode.HIGH_PRESSURE) {
            if (cap != null && cap.highPressureWorkstation() != null) return cap.highPressureWorkstation();
            if (GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(catId)) return ResourceLocation.tryParse("gtceu:hp_steam_furnace");
        }
        return null;
    }

    public static double computeSteamRate(RecipeNode node, IngredientStack stack) {
        if (node == null || stack == null || node.getSteamMode() == null || !node.getSteamMode().isSteam()) {
            return -1.0;
        }
        if (!"gtceu:steam".equals(stack.getId().toString())) {
            return -1.0;
        }
        if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
            double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), node.getSteamMode());
            return steamRatePerTick * 20.0 * node.getMachineCount();
        }
        return (node.getBaseEUt() * 2.0 * 20.0) * node.getMachineCount();
    }

    public static double computeSingleMachineSteamRate(RecipeNode node, IngredientStack stack) {
        if (node == null || stack == null || node.getSteamMode() == null || !node.getSteamMode().isSteam()) {
            return -1.0;
        }
        if (!"gtceu:steam".equals(stack.getId().toString())) {
            return -1.0;
        }
        if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
            double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), node.getSteamMode());
            return steamRatePerTick * 20.0;
        }
        return node.getBaseEUt() * 2.0 * 20.0;
    }

    public static double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (!isInput || stack == null || !stack.isFluid() || stack.getId() == null) {
            return defaultRate;
        }
        double steamRate = computeSteamRate(node, stack);
        if (steamRate >= 0.0) {
            return steamRate;
        }
        if (!GTCombustionHelper.COMBUSTION_AUXILIARY_FLUIDS.contains(stack.getId())) {
            return defaultRate;
        }
        double boostRate = GTCombustionHelper.getCombustionAuxiliaryRate(node, stack.getId());
        if (boostRate > 0.0) {
            if (!node.isOperational()) {
                return 0.0;
            }
            return boostRate * node.getMachineCount();
        }
        return defaultRate;
    }

    public static double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (!isInput || stack == null || !stack.isFluid() || stack.getId() == null) {
            return defaultRate;
        }
        double steamRate = computeSingleMachineSteamRate(node, stack);
        if (steamRate >= 0.0) {
            return steamRate;
        }
        if (!GTCombustionHelper.COMBUSTION_AUXILIARY_FLUIDS.contains(stack.getId())) {
            return defaultRate;
        }
        double boostRate = GTCombustionHelper.getCombustionAuxiliaryRate(node, stack.getId());
        if (boostRate > 0.0) {
            if (!node.isOperational()) {
                return 0.0;
            }
            return boostRate;
        }
        return defaultRate;
    }
}
