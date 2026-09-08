package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Helper utility for combustion generator machines across singleblock and multiblock tiers.
 * Manages GTCEu Modern registered machine identifiers, legacy saved board migration,
 * multiblock oxidizer boost compatibility, and singleblock hardware addon isolation.
 */
public final class GTCombustionHelper {

    public static final ResourceLocation LV_COMBUSTION = ResourceLocation.tryParse("gtceu:lv_combustion");
    public static final ResourceLocation MV_COMBUSTION = ResourceLocation.tryParse("gtceu:mv_combustion");
    public static final ResourceLocation HV_COMBUSTION = ResourceLocation.tryParse("gtceu:hv_combustion");

    public static final ResourceLocation LV_COMBUSTION_GENERATOR = LV_COMBUSTION;
    public static final ResourceLocation MV_COMBUSTION_GENERATOR = MV_COMBUSTION;
    public static final ResourceLocation HV_COMBUSTION_GENERATOR = HV_COMBUSTION;
    public static final ResourceLocation COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:combustion_generator");

    public static final ResourceLocation LEGACY_LV_COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:lv_combustion_generator");
    public static final ResourceLocation LEGACY_MV_COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:mv_combustion_generator");
    public static final ResourceLocation LEGACY_HV_COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:hv_combustion_generator");

    public static final ResourceLocation LARGE_COMBUSTION_ENGINE = ResourceLocation.tryParse("gtceu:large_combustion_engine");
    public static final ResourceLocation EXTREME_COMBUSTION_ENGINE = ResourceLocation.tryParse("gtceu:extreme_combustion_engine");

    public static final ResourceLocation START_T1_COMBUSTION = ResourceLocation.tryParse("start_core:luv_combustion_module");
    public static final ResourceLocation START_T2_COMBUSTION = ResourceLocation.tryParse("start_core:zpm_combustion_module");
    public static final ResourceLocation START_T3_ROCKET = ResourceLocation.tryParse("start_core:uv_combustion_module");
    public static final ResourceLocation START_T4_ROCKET = ResourceLocation.tryParse("start_core:uev_combustion_module");
    public static final ResourceLocation START_T3_COMBUSTION = START_T3_ROCKET;
    public static final ResourceLocation START_T4_COMBUSTION = START_T4_ROCKET;
    public static final ResourceLocation START_MCF = ResourceLocation.tryParse("start_core:modular_combustion_frame");

    public static final ResourceLocation OXYGEN = ResourceLocation.tryParse("gtceu:oxygen");
    public static final ResourceLocation LIQUID_OXYGEN = ResourceLocation.tryParse("gtceu:liquid_oxygen");
    public static final ResourceLocation LUBRICANT = ResourceLocation.tryParse("gtceu:lubricant");
    public static final ResourceLocation TUNGSTEN_DISULFIDE = ResourceLocation.tryParse("gtceu:tungsten_disulfide");
    public static final ResourceLocation WHITE_FUMING_NITRIC_ACID = ResourceLocation.tryParse("gtceu:white_fuming_nitric_acid");
    public static final ResourceLocation RED_FUMING_NITRIC_ACID = ResourceLocation.tryParse("gtceu:red_fuming_nitric_acid");
    public static final ResourceLocation DIOXYGEN_DIFLUORIDE = ResourceLocation.tryParse("gtceu:dioxygen_difluoride");
    public static final ResourceLocation FERROCENIUM_SUPEROXIDE = ResourceLocation.tryParse("gtceu:ferrocenium_superoxide");
    public static final ResourceLocation DISTILLED_WATER = ResourceLocation.tryParse("gtceu:distilled_water");
    public static final ResourceLocation DEIONIZED_WATER = ResourceLocation.tryParse("gtceu:deionized_water");

    public static final Set<ResourceLocation> COMBUSTION_AUXILIARY_FLUIDS = Set.of(
            OXYGEN,
            LIQUID_OXYGEN,
            LUBRICANT,
            TUNGSTEN_DISULFIDE,
            WHITE_FUMING_NITRIC_ACID,
            RED_FUMING_NITRIC_ACID,
            DIOXYGEN_DIFLUORIDE,
            FERROCENIUM_SUPEROXIDE,
            DISTILLED_WATER,
            DEIONIZED_WATER
    );

    private static final Set<ResourceLocation> SINGLEBLOCK_COMBUSTION_GENERATORS = Set.of(
            LV_COMBUSTION,
            MV_COMBUSTION,
            HV_COMBUSTION,
            LEGACY_LV_COMBUSTION_GENERATOR,
            LEGACY_MV_COMBUSTION_GENERATOR,
            LEGACY_HV_COMBUSTION_GENERATOR,
            COMBUSTION_GENERATOR
    );

    private static final Set<ResourceLocation> COMBUSTION_ENGINES = Set.of(
            LARGE_COMBUSTION_ENGINE,
            EXTREME_COMBUSTION_ENGINE,
            START_T1_COMBUSTION,
            START_T2_COMBUSTION,
            START_T3_ROCKET,
            START_T4_ROCKET,
            START_MCF
    );

    private static final Set<ResourceLocation> START_COMBUSTION_MODULES = Set.of(
            START_T1_COMBUSTION,
            START_T2_COMBUSTION
    );

    private static final Set<ResourceLocation> START_ROCKET_MODULES = Set.of(
            START_T3_ROCKET,
            START_T4_ROCKET
    );

    private static final Set<ResourceLocation> START_MODULES = Set.of(
            START_T1_COMBUSTION,
            START_T2_COMBUSTION,
            START_T3_ROCKET,
            START_T4_ROCKET
    );

    private static final ResourceLocation COMBUSTION_CATEGORY_ID = ResourceLocation.tryParse("gtceu:combustion_generator");

    private GTCombustionHelper() {}

    public static boolean isCombustionFamily(RecipeNode node) {
        if (node == null) {
            return false;
        }
        if (isCombustionEngine(node)) {
            return true;
        }
        if (node.getMachineIcon() != null && isSingleblockCombustionGenerator(node.getMachineIcon())) {
            return true;
        }
        return COMBUSTION_CATEGORY_ID.equals(node.getRecipeCategoryId());
    }

    public static boolean isSingleblockCombustionGenerator(ResourceLocation icon) {
        return icon != null && SINGLEBLOCK_COMBUSTION_GENERATORS.contains(icon);
    }

    public static boolean isCombustionMachine(ResourceLocation icon) {
        return isCombustionEngine(icon) || isSingleblockCombustionGenerator(icon);
    }

    public static boolean isCombustionEngine(ResourceLocation icon) {
        return icon != null && COMBUSTION_ENGINES.contains(icon);
    }

    public static boolean isCombustionEngine(RecipeNode node) {
        if (node == null) {
            return false;
        }
        if (node.getMachineIcon() != null && isCombustionEngine(node.getMachineIcon())) return true;
        if (node.getMultiblockWorkstation() != null && isCombustionEngine(node.getMultiblockWorkstation())) return true;
        return isLargeCombustionEngine(node) || isExtremeCombustionEngine(node) || isStarTModule(node);
    }

    public static boolean isLargeCombustionEngine(RecipeNode node) {
        if (node == null) return false;
        if (LARGE_COMBUSTION_ENGINE.equals(node.getMachineIcon())) {
            return true;
        }
        if (isStarTModule(node) || isModularCombustionFrame(node)) {
            return false;
        }
        if (LARGE_COMBUSTION_ENGINE.equals(node.getMultiblockWorkstation())) {
            return true;
        }
        return COMBUSTION_CATEGORY_ID.equals(node.getRecipeCategoryId()) && node.isMultiblock() && node.getTargetTier() == com.gtceu.calcboard.api.type.GTVoltageTier.EV;
    }

    public static boolean isExtremeCombustionEngine(RecipeNode node) {
        if (node == null) return false;
        if (EXTREME_COMBUSTION_ENGINE.equals(node.getMachineIcon())) {
            return true;
        }
        if (isStarTModule(node) || isModularCombustionFrame(node)) {
            return false;
        }
        if (EXTREME_COMBUSTION_ENGINE.equals(node.getMultiblockWorkstation())) {
            return true;
        }
        return COMBUSTION_CATEGORY_ID.equals(node.getRecipeCategoryId()) && node.isMultiblock() && node.getTargetTier() == com.gtceu.calcboard.api.type.GTVoltageTier.IV;
    }

    public static boolean isStarTCombustionModule(RecipeNode node) {
        if (node == null) return false;
        return (node.getMachineIcon() != null && START_COMBUSTION_MODULES.contains(node.getMachineIcon()))
                || (node.getMultiblockWorkstation() != null && START_COMBUSTION_MODULES.contains(node.getMultiblockWorkstation()));
    }

    public static boolean isStarTRocketModule(RecipeNode node) {
        if (node == null) return false;
        return (node.getMachineIcon() != null && START_ROCKET_MODULES.contains(node.getMachineIcon()))
                || (node.getMultiblockWorkstation() != null && START_ROCKET_MODULES.contains(node.getMultiblockWorkstation()));
    }

    public static boolean isStarTModule(RecipeNode node) {
        if (node == null) return false;
        return (node.getMachineIcon() != null && START_MODULES.contains(node.getMachineIcon()))
                || (node.getMultiblockWorkstation() != null && START_MODULES.contains(node.getMultiblockWorkstation()));
    }

    public static boolean isModularCombustionFrame(RecipeNode node) {
        if (node == null) return false;
        return START_MCF.equals(node.getMachineIcon()) || START_MCF.equals(node.getMultiblockWorkstation());
    }

    public static boolean isCombustionMultiblock(com.gtceu.calcboard.api.type.GTVoltageTier tier) {
        return tier != null && tier.ordinal() >= com.gtceu.calcboard.api.type.GTVoltageTier.EV.ordinal();
    }

    public static com.gtceu.calcboard.api.type.GTVoltageTier getMinCombustionTier() {
        return com.gtceu.calcboard.api.type.GTVoltageTier.LV;
    }

    public static com.gtceu.calcboard.api.type.GTVoltageTier getMaxCombustionTier() {
        if (hasStarTCombustionModules()) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.ZPM;
        }
        return com.gtceu.calcboard.api.type.GTVoltageTier.IV;
    }

    public static boolean hasStarTCombustionModules() {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS != null
                && net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(START_T1_COMBUSTION);
    }

    public static java.util.List<com.gtceu.calcboard.api.type.GTVoltageTier> getAvailableCombustionTiers() {
        java.util.List<com.gtceu.calcboard.api.type.GTVoltageTier> list = new java.util.ArrayList<>();
        list.add(com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        list.add(com.gtceu.calcboard.api.type.GTVoltageTier.MV);
        list.add(com.gtceu.calcboard.api.type.GTVoltageTier.HV);
        list.add(com.gtceu.calcboard.api.type.GTVoltageTier.EV);
        list.add(com.gtceu.calcboard.api.type.GTVoltageTier.IV);
        if (hasStarTCombustionModules()) {
            list.add(com.gtceu.calcboard.api.type.GTVoltageTier.LuV);
            list.add(com.gtceu.calcboard.api.type.GTVoltageTier.ZPM);
        }
        return list;
    }

    public static ResourceLocation getCombustionMachineForTier(com.gtceu.calcboard.api.type.GTVoltageTier tier) {
        if (tier == null) return null;
        return switch (tier) {
            case LV -> LV_COMBUSTION_GENERATOR;
            case MV -> MV_COMBUSTION_GENERATOR;
            case HV -> HV_COMBUSTION_GENERATOR;
            case EV -> LARGE_COMBUSTION_ENGINE;
            case IV -> EXTREME_COMBUSTION_ENGINE;
            case LuV -> hasStarTCombustionModules() ? START_T1_COMBUSTION : null;
            case ZPM -> hasStarTCombustionModules() ? START_T2_COMBUSTION : null;
            default -> null;
        };
    }

    public static com.gtceu.calcboard.api.type.GTVoltageTier getCombustionTierForMachine(ResourceLocation icon) {
        if (icon == null) return null;
        if (LV_COMBUSTION.equals(icon) || LEGACY_LV_COMBUSTION_GENERATOR.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.LV;
        if (MV_COMBUSTION.equals(icon) || LEGACY_MV_COMBUSTION_GENERATOR.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.MV;
        if (HV_COMBUSTION.equals(icon) || LEGACY_HV_COMBUSTION_GENERATOR.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.HV;
        if (LARGE_COMBUSTION_ENGINE.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.EV;
        if (EXTREME_COMBUSTION_ENGINE.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.IV;
        if (START_T1_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.LuV;
        if (START_T2_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.ZPM;
        if (START_T3_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.UV;
        if (START_T4_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.UEV;
        return null;
    }

    public static ResourceLocation normalizeMachineIcon(ResourceLocation icon) {
        if (icon == null) return null;
        if (LEGACY_LV_COMBUSTION_GENERATOR.equals(icon)) return LV_COMBUSTION;
        if (LEGACY_MV_COMBUSTION_GENERATOR.equals(icon)) return MV_COMBUSTION;
        if (LEGACY_HV_COMBUSTION_GENERATOR.equals(icon)) return HV_COMBUSTION;
        return icon;
    }

    public static boolean syncCombustionMachine(RecipeNode node, com.gtceu.calcboard.api.type.GTVoltageTier targetTier) {
        if (node == null || targetTier == null) return false;
        ResourceLocation targetMachine = getCombustionMachineForTier(targetTier);
        if (targetMachine == null) return false;

        ResourceLocation oldIcon = node.getMachineIcon();
        node.setTargetTier(targetTier);
        node.setMachineIcon(targetMachine);
        node.setMultiblock(isCombustionMultiblock(targetTier));
        node.setGenerator(true);

        String resolvedName = com.gtceu.calcboard.api.bom.BOMDisplayNameResolver.resolve(targetMachine, null);
        if (resolvedName != null && !resolvedName.isBlank()) {
            node.setName(resolvedName);
        }

        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            adapter.onMachineIconChanged(node, oldIcon, targetMachine);
        }
        syncCombustionInputs(node);
        return true;
    }

    public static double getCombustionPowerMultiplier(RecipeNode node) {
        if (node == null) {
            return 1.0;
        }
        if (isLargeCombustionEngine(node)) {
            return Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.OXYGEN_BOOST)) ? 1.5 : 1.0;
        }
        if (isExtremeCombustionEngine(node)) {
            return Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LIQUID_OXYGEN_BOOST)) ? 2.0 : 1.0;
        }
        if (isStarTCombustionModule(node) || isStarTRocketModule(node)) {
            return getStarTModulePowerMultiplier(node) * getFrameCoolantMultiplier(node);
        }
        if (isModularCombustionFrame(node)) {
            return getFrameCoolantMultiplier(node);
        }
        return 1.0;
    }

    public static int getCombustionParallelMultiplier(RecipeNode node) {
        if (node == null) {
            return 1;
        }
        if (isLargeCombustionEngine(node) && Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.OXYGEN_BOOST))) {
            return 2;
        }
        if (isExtremeCombustionEngine(node) && Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LIQUID_OXYGEN_BOOST))) {
            return 2;
        }
        if ((isStarTCombustionModule(node) || isStarTRocketModule(node)) && isStarTModuleBoosted(node)) {
            return 2;
        }
        return 1;
    }

    public static long getBaseCombustionVoltage(RecipeNode node) {
        if (node == null || node.getMachineIcon() == null) {
            return 0L;
        }
        ResourceLocation id = node.getMachineIcon();
        if (LARGE_COMBUSTION_ENGINE.equals(id)) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.EV.getVoltage();
        }
        if (EXTREME_COMBUSTION_ENGINE.equals(id)) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.IV.getVoltage();
        }
        if (START_T1_COMBUSTION.equals(id)) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.LuV.getVoltage();
        }
        if (START_T2_COMBUSTION.equals(id)) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.ZPM.getVoltage();
        }
        if (START_T3_ROCKET.equals(id)) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.UV.getVoltage();
        }
        if (START_T4_ROCKET.equals(id)) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.UEV.getVoltage();
        }
        return 0L;
    }

    private static double getStarTModulePowerMultiplier(RecipeNode node) {
        ResourceLocation id = node.getMachineIcon();
        boolean boosted = isStarTModuleBoosted(node);
        if (START_T1_COMBUSTION.equals(id)) {
            return boosted ? 5.0 : 1.0;
        }
        if (START_T2_COMBUSTION.equals(id)) {
            return boosted ? 6.0 : 1.0;
        }
        if (START_T3_ROCKET.equals(id)) {
            return boosted ? 8.0 : 2.0;
        }
        if (START_T4_ROCKET.equals(id)) {
            return boosted ? 12.0 : 2.0;
        }
        return 1.0;
    }

    private static boolean isStarTModuleBoosted(RecipeNode node) {
        String oxidizer = node.getProperties().get(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE);
        return oxidizer != null && !oxidizer.isEmpty() && !"none".equalsIgnoreCase(oxidizer);
    }

    public static double getFrameCoolantMultiplier(RecipeNode node) {
        String coolant = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
        if ("deionized_water".equalsIgnoreCase(coolant)) {
            return 1.4;
        }
        if ("distilled_water".equalsIgnoreCase(coolant)) {
            return 1.2;
        }
        if (isModularCombustionFrame(node)) {
            return 0.9;
        }
        return 1.0;
    }

    public static boolean isOxygenBoosted(RecipeNode node) {
        return node != null && Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.OXYGEN_BOOST));
    }

    public static boolean isLiquidOxygenBoosted(RecipeNode node) {
        return node != null && Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LIQUID_OXYGEN_BOOST));
    }

    public static boolean isOxidizerBoosted(RecipeNode node) {
        return node != null && isStarTModuleBoosted(node);
    }

    public static boolean isCoolantBoosted(RecipeNode node) {
        if (node == null) return false;
        String coolant = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
        return coolant != null && !coolant.isEmpty() && !"none".equalsIgnoreCase(coolant);
    }

    public static String getOxidizerDisplayName(String oxidizer) {
        if (oxidizer == null || "none".equalsIgnoreCase(oxidizer)) return "None";
        return switch (oxidizer.toLowerCase(java.util.Locale.ROOT)) {
            case "white_fuming_nitric_acid" -> "WFNA";
            case "red_fuming_nitric_acid" -> "RFNA";
            case "dioxygen_difluoride" -> "O₂F₂";
            case "ferrocenium_superoxide" -> "FcSO₂";
            default -> oxidizer;
        };
    }

    public static String getCoolantDisplayName(String coolant) {
        if (coolant == null || "none".equalsIgnoreCase(coolant)) return "None";
        return switch (coolant.toLowerCase(java.util.Locale.ROOT)) {
            case "distilled_water" -> "Distilled (+20%)";
            case "deionized_water" -> "Deionized (+40%)";
            default -> coolant;
        };
    }

    public static double getCombustionAuxiliaryRate(RecipeNode node, ResourceLocation fluidId) {
        if (node == null || fluidId == null || !COMBUSTION_AUXILIARY_FLUIDS.contains(fluidId) || !isCombustionEngine(node)) {
            return 0.0;
        }
        if (isLargeCombustionEngine(node)) {
            return (OXYGEN.equals(fluidId) && isOxygenBoosted(node)) ? 20.0 : 0.0;
        }
        if (isExtremeCombustionEngine(node)) {
            return (LIQUID_OXYGEN.equals(fluidId) && isLiquidOxygenBoosted(node)) ? 80.0 : 0.0;
        }
        if (isStarTCombustionModule(node) || isStarTRocketModule(node)) {
            double coolantRate = getCoolantRate(node, fluidId);
            if (coolantRate > 0.0) {
                return coolantRate;
            }
            return getStarTModuleAuxiliaryRate(node, fluidId);
        }
        if (isModularCombustionFrame(node)) {
            return getCoolantRate(node, fluidId);
        }
        return 0.0;
    }

    private static double getCoolantRate(RecipeNode node, ResourceLocation fluidId) {
        String coolant = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
        if ("distilled_water".equalsIgnoreCase(coolant) && DISTILLED_WATER.equals(fluidId)) {
            return 500000.0 / 3600.0;
        }
        if ("deionized_water".equalsIgnoreCase(coolant) && DEIONIZED_WATER.equals(fluidId)) {
            return 500000.0 / 3600.0;
        }
        return 0.0;
    }

    public static ResourceLocation getExpectedLubricantFluid(RecipeNode node) {
        if (node == null || node.getMachineIcon() == null) return null;
        ResourceLocation icon = node.getMachineIcon();
        if (START_T1_COMBUSTION.equals(icon) || START_T2_COMBUSTION.equals(icon)) return LUBRICANT;
        if (START_T3_ROCKET.equals(icon) || START_T4_ROCKET.equals(icon)) return TUNGSTEN_DISULFIDE;
        return null;
    }

    public static ResourceLocation getExpectedOxidizerFluid(RecipeNode node) {
        if (node == null || node.getMachineIcon() == null) return null;
        ResourceLocation icon = node.getMachineIcon();
        if (START_T1_COMBUSTION.equals(icon)) return WHITE_FUMING_NITRIC_ACID;
        if (START_T2_COMBUSTION.equals(icon)) return RED_FUMING_NITRIC_ACID;
        if (START_T3_ROCKET.equals(icon)) return DIOXYGEN_DIFLUORIDE;
        if (START_T4_ROCKET.equals(icon)) return FERROCENIUM_SUPEROXIDE;
        return null;
    }

    public static String getExpectedOxidizerAddonId(RecipeNode node) {
        if (node == null || node.getMachineIcon() == null) return null;
        ResourceLocation icon = node.getMachineIcon();
        if (START_T1_COMBUSTION.equals(icon)) return "start_core:t1_oxidizer_boost";
        if (START_T2_COMBUSTION.equals(icon)) return "start_core:t2_oxidizer_boost";
        if (START_T3_ROCKET.equals(icon)) return "start_core:t3_oxidizer_boost";
        if (START_T4_ROCKET.equals(icon)) return "start_core:t4_oxidizer_boost";
        return null;
    }

    public static String getExpectedOxidizerPropertyType(RecipeNode node) {
        if (node == null || node.getMachineIcon() == null) return null;
        ResourceLocation icon = node.getMachineIcon();
        if (START_T1_COMBUSTION.equals(icon)) return "white_fuming_nitric_acid";
        if (START_T2_COMBUSTION.equals(icon)) return "red_fuming_nitric_acid";
        if (START_T3_ROCKET.equals(icon)) return "dioxygen_difluoride";
        if (START_T4_ROCKET.equals(icon)) return "ferrocenium_superoxide";
        return null;
    }

    private static double getStarTModuleAuxiliaryRate(RecipeNode node, ResourceLocation fluidId) {
        ResourceLocation icon = node.getMachineIcon();
        if (START_T1_COMBUSTION.equals(icon)) {
            if (LUBRICANT.equals(fluidId)) return 100.0 / 3.6;
            if (WHITE_FUMING_NITRIC_ACID.equals(fluidId) && isStarTModuleBoosted(node)) return 324.0 / 3.6;
        } else if (START_T2_COMBUSTION.equals(icon)) {
            if (LUBRICANT.equals(fluidId)) return 200.0 / 3.6;
            if (RED_FUMING_NITRIC_ACID.equals(fluidId) && isStarTModuleBoosted(node)) return 432.0 / 3.6;
        } else if (START_T3_ROCKET.equals(icon)) {
            if (TUNGSTEN_DISULFIDE.equals(fluidId)) return 200.0 / 3.6;
            if (DIOXYGEN_DIFLUORIDE.equals(fluidId) && isStarTModuleBoosted(node)) return 756.0 / 3.6;
        } else if (START_T4_ROCKET.equals(icon)) {
            if (TUNGSTEN_DISULFIDE.equals(fluidId)) return 400.0 / 3.6;
            if (FERROCENIUM_SUPEROXIDE.equals(fluidId) && isStarTModuleBoosted(node)) return 864.0 / 3.6;
        }
        return 0.0;
    }

    public static void syncCombustionInputs(RecipeNode node) {
        if (node == null || !isCombustionFamily(node)) {
            return;
        }
        removeCombustionAuxiliaryInputs(node);
        if (!isCombustionEngine(node)) {
            return;
        }

        double durSec = Math.max(0.05, node.getBaseDurationTicks() / 20.0);
        int parallel = Math.max(1, GTPowerCalculator.computeEffectiveParallel(node));

        if (isLargeCombustionEngine(node)) {
            if (isOxygenBoosted(node)) {
                double batchAmount = (20.0 * durSec) / parallel;
                node.addInput(IngredientStack.fluid(OXYGEN, "Oxygen", batchAmount));
            }
        } else if (isExtremeCombustionEngine(node)) {
            if (isLiquidOxygenBoosted(node)) {
                double batchAmount = (80.0 * durSec) / parallel;
                node.addInput(IngredientStack.fluid(LIQUID_OXYGEN, "Liquid Oxygen", batchAmount));
            }
        } else if (isStarTCombustionModule(node) || isStarTRocketModule(node)) {
            syncStarTModuleInputs(node, durSec, parallel);
        } else if (isModularCombustionFrame(node)) {
            syncCoolantInput(node, durSec, parallel);
        }
        node.markOverclockDirty();
    }

    public static void removeCombustionAuxiliaryInputs(RecipeNode node) {
        if (node == null) return;
        node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && COMBUSTION_AUXILIARY_FLUIDS.contains(in.getId()));
    }

    private static void syncStarTModuleInputs(RecipeNode node, double durSec, int parallel) {
        ResourceLocation icon = node.getMachineIcon();
        if (START_T1_COMBUSTION.equals(icon)) {
            node.addInput(IngredientStack.fluid(LUBRICANT, "Lubricant", ((100.0 / 3.6) * durSec) / parallel));
            if (isStarTModuleBoosted(node)) {
                node.addInput(IngredientStack.fluid(WHITE_FUMING_NITRIC_ACID, "White Fuming Nitric Acid", ((324.0 / 3.6) * durSec) / parallel));
            }
        } else if (START_T2_COMBUSTION.equals(icon)) {
            node.addInput(IngredientStack.fluid(LUBRICANT, "Lubricant", ((200.0 / 3.6) * durSec) / parallel));
            if (isStarTModuleBoosted(node)) {
                node.addInput(IngredientStack.fluid(RED_FUMING_NITRIC_ACID, "Red Fuming Nitric Acid", ((432.0 / 3.6) * durSec) / parallel));
            }
        } else if (START_T3_ROCKET.equals(icon)) {
            node.addInput(IngredientStack.fluid(TUNGSTEN_DISULFIDE, "Tungsten Disulfide", ((200.0 / 3.6) * durSec) / parallel));
            if (isStarTModuleBoosted(node)) {
                node.addInput(IngredientStack.fluid(DIOXYGEN_DIFLUORIDE, "Dioxygen Difluoride", ((756.0 / 3.6) * durSec) / parallel));
            }
        } else if (START_T4_ROCKET.equals(icon)) {
            node.addInput(IngredientStack.fluid(TUNGSTEN_DISULFIDE, "Tungsten Disulfide", ((400.0 / 3.6) * durSec) / parallel));
            if (isStarTModuleBoosted(node)) {
                node.addInput(IngredientStack.fluid(FERROCENIUM_SUPEROXIDE, "Ferrocenium Superoxide", ((864.0 / 3.6) * durSec) / parallel));
            }
        }
        syncCoolantInput(node, durSec, parallel);
    }

    private static void syncCoolantInput(RecipeNode node, double durSec, int parallel) {
        String coolant = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
        double coolantRate = 500000.0 / 3600.0;
        double batchAmount = (coolantRate * durSec) / parallel;
        if ("distilled_water".equalsIgnoreCase(coolant)) {
            node.addInput(IngredientStack.fluid(DISTILLED_WATER, "Distilled Water", batchAmount));
        } else if ("deionized_water".equalsIgnoreCase(coolant)) {
            node.addInput(IngredientStack.fluid(DEIONIZED_WATER, "Deionized Water", batchAmount));
        }
    }

    public static void ensureCombustionInputs(RecipeNode node) {
        if (node == null || !isCombustionEngine(node)) {
            return;
        }
        if (areCombustionInputsOutOfSync(node)) {
            syncCombustionInputs(node);
        }
    }

    public static boolean areCombustionInputsOutOfSync(RecipeNode node) {
        if (node == null || !isCombustionEngine(node)) {
            return false;
        }
        if (isLargeCombustionEngine(node)) {
            return isOxygenBoosted(node) != hasAuxiliaryFluid(node, OXYGEN);
        }
        if (isExtremeCombustionEngine(node)) {
            return isLiquidOxygenBoosted(node) != hasAuxiliaryFluid(node, LIQUID_OXYGEN);
        }
        if (isStarTCombustionModule(node) || isStarTRocketModule(node)) {
            ResourceLocation expectedLube = getExpectedLubricantFluid(node);
            if (expectedLube != null && !hasAuxiliaryFluid(node, expectedLube)) {
                return true;
            }
            ResourceLocation expectedOx = getExpectedOxidizerFluid(node);
            boolean hasOx = expectedOx != null && hasAuxiliaryFluid(node, expectedOx);
            if (isStarTModuleBoosted(node) != hasOx) {
                return true;
            }
        }
        boolean hasCoolant = hasAuxiliaryFluid(node, DISTILLED_WATER) || hasAuxiliaryFluid(node, DEIONIZED_WATER);
        return isCoolantBoosted(node) != hasCoolant;
    }

    private static boolean hasAuxiliaryFluid(RecipeNode node, ResourceLocation fluidId) {
        for (IngredientStack in : node.getInputs()) {
            if (in.isFluid() && fluidId.equals(in.getId())) {
                return true;
            }
        }
        return false;
    }
}
