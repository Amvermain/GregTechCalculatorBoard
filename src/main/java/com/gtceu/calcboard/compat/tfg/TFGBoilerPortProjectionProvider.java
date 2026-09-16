package com.gtceu.calcboard.compat.tfg;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.ProjectedPort;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.spi.extension.IPortProjectionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dynamic port projection provider for TFG Large Boilers.
 * Projects non-linear water consumption, active booster fluid inputs, and steam output.
 */
public final class TFGBoilerPortProjectionProvider implements IPortProjectionProvider {

    private static final TFGBoilerPortProjectionProvider INSTANCE = new TFGBoilerPortProjectionProvider();

    private TFGBoilerPortProjectionProvider() {}

    public static TFGBoilerPortProjectionProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public List<ProjectedPort> projectInputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (node == null) return Collections.emptyList();
        if (!TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return IPortProjectionProvider.super.projectInputPorts(node, baseSpec);
        }

        List<ProjectedPort> ports = new ArrayList<>();
        appendCoreFuelInputs(baseSpec, ports);
        appendWaterInput(node, ports);
        appendBoosterInput(node, ports);
        return Collections.unmodifiableList(ports);
    }

    @Override
    public List<ProjectedPort> projectOutputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (node == null) return Collections.emptyList();
        if (!TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return IPortProjectionProvider.super.projectOutputPorts(node, baseSpec);
        }

        List<ProjectedPort> ports = new ArrayList<>();
        double steamRateSec = TFGBoilerPhysics.getSteamRatePerSec(node);
        double durSec = computeEffectiveDurationSec(node);
        int parallel = Math.max(1, node.getParallel());
        double steamBatch = (steamRateSec * durSec) / parallel;
        ports.add(ProjectedPort.ofCore(IngredientStack.fluid(TFGBoilerPhysics.STEAM, "Steam", steamBatch), 0));

        appendNonSteamOutputs(baseSpec, ports);
        return Collections.unmodifiableList(ports);
    }

    private static void appendCoreFuelInputs(RecipeSpec baseSpec, List<ProjectedPort> ports) {
        if (baseSpec == null || baseSpec.baseInputs() == null) return;
        int coreIdx = 0;
        for (IngredientStack in : baseSpec.baseInputs()) {
            if (in == null) continue;
            if (in.isFluid() && (TFGBoilerPhysics.STANDARD_WATER.equals(in.getId())
                    || TFGBoilerPhysics.DISTILLED_WATER.equals(in.getId())
                    || TFGBoilerPhysics.isBoosterFluid(in.getId()))) {
                continue;
            }
            ports.add(ProjectedPort.ofCore(in, coreIdx++));
        }
    }

    private static void appendWaterInput(RecipeNode node, List<ProjectedPort> ports) {
        int waterTier = node.getProperties().get(TFGBoilerProperties.WATER_TIER);
        var waterId = (waterTier == 1) ? TFGBoilerPhysics.DISTILLED_WATER : TFGBoilerPhysics.STANDARD_WATER;
        String waterName = (waterTier == 1) ? "Distilled Water" : "Water";
        double waterRateSec = TFGBoilerPhysics.getWaterConsumptionPerSec(node);
        double durSec = computeEffectiveDurationSec(node);
        int parallel = Math.max(1, node.getParallel());
        double waterBatch = (waterRateSec * durSec) / parallel;
        ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(waterId, waterName, waterBatch), "tfg:water_supply"));
    }

    private static void appendBoosterInput(RecipeNode node, List<ProjectedPort> ports) {
        TFGBoilerPhysics.BoosterFluid booster = TFGBoilerPhysics.getActiveBooster(node);
        if (booster.index() <= 0 || booster.fluidId() == null) return;
        double boosterRateSec = booster.consumptionMbPerSec();
        double durSec = computeEffectiveDurationSec(node);
        int parallel = Math.max(1, node.getParallel());
        double boosterBatch = (boosterRateSec * durSec) / parallel;
        ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(booster.fluidId(), TFGBoilerPhysics.resolveBoosterName(booster), boosterBatch), "tfg:booster"));
    }

    private static void appendNonSteamOutputs(RecipeSpec baseSpec, List<ProjectedPort> ports) {
        if (baseSpec == null || baseSpec.baseOutputs() == null) return;
        int outIdx = 1;
        for (IngredientStack out : baseSpec.baseOutputs()) {
            if (out == null) continue;
            if (out.isFluid() && TFGBoilerPhysics.STEAM.equals(out.getId())) {
                continue;
            }
            ports.add(ProjectedPort.ofCore(out, outIdx++));
        }
    }

    private static double computeEffectiveDurationSec(RecipeNode node) {
        double baseTicks = node.getBaseDurationTicks() > 0 ? node.getBaseDurationTicks() : 100.0;
        double speed = TFGBoilerPhysics.getBoilerSpeedMultiplier(node);
        if (speed <= 0.0) speed = 1.0;
        return Math.max(0.05, (baseTicks / speed) / 20.0);
    }

    @Override
    public List<IngredientStack> sanitizeLegacyCoreInputs(RecipeNode node, List<IngredientStack> savedInputs) {
        if (savedInputs == null) return Collections.emptyList();
        if (!TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return IPortProjectionProvider.super.sanitizeLegacyCoreInputs(node, savedInputs);
        }
        List<IngredientStack> result = new ArrayList<>();
        for (IngredientStack in : savedInputs) {
            if (in != null && in.isFluid()) {
                if (TFGBoilerPhysics.STANDARD_WATER.equals(in.getId())
                        || TFGBoilerPhysics.DISTILLED_WATER.equals(in.getId())
                        || TFGBoilerPhysics.isBoosterFluid(in.getId())) {
                    continue;
                }
            }
            if (in != null) {
                result.add(in.copy());
            }
        }
        return result;
    }
}
