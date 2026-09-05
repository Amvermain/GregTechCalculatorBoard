package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class GTCombustionHelper {

    public static final ResourceLocation LV_COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:lv_combustion_generator");
    public static final ResourceLocation MV_COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:mv_combustion_generator");
    public static final ResourceLocation HV_COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:hv_combustion_generator");
    public static final ResourceLocation COMBUSTION_GENERATOR = ResourceLocation.tryParse("gtceu:combustion_generator");

    public static final ResourceLocation LARGE_COMBUSTION_ENGINE = ResourceLocation.tryParse("gtceu:large_combustion_engine");
    public static final ResourceLocation EXTREME_COMBUSTION_ENGINE = ResourceLocation.tryParse("gtceu:extreme_combustion_engine");

    public static final ResourceLocation START_T1_COMBUSTION = ResourceLocation.tryParse("start_core:luv_combustion_module");
    public static final ResourceLocation START_T2_COMBUSTION = ResourceLocation.tryParse("start_core:zpm_combustion_module");
    public static final ResourceLocation START_T3_COMBUSTION = ResourceLocation.tryParse("start_core:uv_combustion_module");
    public static final ResourceLocation START_T4_COMBUSTION = ResourceLocation.tryParse("start_core:uev_combustion_module");
    public static final ResourceLocation START_MCF = ResourceLocation.tryParse("start_core:modular_combustion_frame");

    private static final Set<ResourceLocation> SINGLEBLOCK_COMBUSTION_GENERATORS = Set.of(
            LV_COMBUSTION_GENERATOR,
            MV_COMBUSTION_GENERATOR,
            HV_COMBUSTION_GENERATOR,
            COMBUSTION_GENERATOR
    );

    private static final Set<ResourceLocation> COMBUSTION_ENGINES = Set.of(
            LARGE_COMBUSTION_ENGINE,
            EXTREME_COMBUSTION_ENGINE,
            START_T1_COMBUSTION,
            START_T2_COMBUSTION,
            START_T3_COMBUSTION,
            START_T4_COMBUSTION,
            START_MCF
    );

    private static final Set<ResourceLocation> START_MODULES = Set.of(
            START_T1_COMBUSTION,
            START_T2_COMBUSTION,
            START_T3_COMBUSTION,
            START_T4_COMBUSTION
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

    public static boolean isCombustionEngine(ResourceLocation icon) {
        return icon != null && COMBUSTION_ENGINES.contains(icon);
    }

    public static boolean isCombustionEngine(RecipeNode node) {
        if (node == null) {
            return false;
        }
        return isCombustionEngine(node.getMachineIcon());
    }

    public static boolean isLargeCombustionEngine(RecipeNode node) {
        return node != null && LARGE_COMBUSTION_ENGINE.equals(node.getMachineIcon());
    }

    public static boolean isExtremeCombustionEngine(RecipeNode node) {
        return node != null && EXTREME_COMBUSTION_ENGINE.equals(node.getMachineIcon());
    }

    public static boolean isStarTCombustionModule(RecipeNode node) {
        return node != null && START_MODULES.contains(node.getMachineIcon());
    }

    public static boolean isModularCombustionFrame(RecipeNode node) {
        return node != null && START_MCF.equals(node.getMachineIcon());
    }

    public static boolean isCombustionMultiblock(com.gtceu.calcboard.api.type.GTVoltageTier tier) {
        return tier != null && tier.ordinal() >= com.gtceu.calcboard.api.type.GTVoltageTier.EV.ordinal();
    }

    public static com.gtceu.calcboard.api.type.GTVoltageTier getMinCombustionTier() {
        return com.gtceu.calcboard.api.type.GTVoltageTier.LV;
    }

    public static com.gtceu.calcboard.api.type.GTVoltageTier getMaxCombustionTier() {
        if (hasStarTCombustionModules()) {
            return com.gtceu.calcboard.api.type.GTVoltageTier.UEV;
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
            list.add(com.gtceu.calcboard.api.type.GTVoltageTier.UV);
            list.add(com.gtceu.calcboard.api.type.GTVoltageTier.UEV);
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
            case UV -> hasStarTCombustionModules() ? START_T3_COMBUSTION : null;
            case UEV -> hasStarTCombustionModules() ? START_T4_COMBUSTION : null;
            default -> null;
        };
    }

    public static com.gtceu.calcboard.api.type.GTVoltageTier getCombustionTierForMachine(ResourceLocation icon) {
        if (icon == null) return null;
        if (LV_COMBUSTION_GENERATOR.equals(icon) || COMBUSTION_GENERATOR.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.LV;
        if (MV_COMBUSTION_GENERATOR.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.MV;
        if (HV_COMBUSTION_GENERATOR.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.HV;
        if (LARGE_COMBUSTION_ENGINE.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.EV;
        if (EXTREME_COMBUSTION_ENGINE.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.IV;
        if (START_T1_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.LuV;
        if (START_T2_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.ZPM;
        if (START_T3_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.UV;
        if (START_T4_COMBUSTION.equals(icon)) return com.gtceu.calcboard.api.type.GTVoltageTier.UEV;
        return null;
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
        if (isStarTCombustionModule(node)) {
            return getStarTModulePowerMultiplier(node);
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
        if (isStarTCombustionModule(node) && isStarTModuleBoosted(node)) {
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
            return 1920L;
        }
        if (EXTREME_COMBUSTION_ENGINE.equals(id)) {
            return 7680L;
        }
        if (START_T1_COMBUSTION.equals(id)) {
            return 30720L;
        }
        if (START_T2_COMBUSTION.equals(id)) {
            return 122880L;
        }
        if (START_T3_COMBUSTION.equals(id)) {
            return 491520L;
        }
        if (START_T4_COMBUSTION.equals(id)) {
            return 1966080L;
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
        if (START_T3_COMBUSTION.equals(id)) {
            return boosted ? 8.0 : 2.0;
        }
        if (START_T4_COMBUSTION.equals(id)) {
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
        return 0.9;
    }
}
