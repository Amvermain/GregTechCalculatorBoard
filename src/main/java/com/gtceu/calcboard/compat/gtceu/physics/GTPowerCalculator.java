package com.gtceu.calcboard.compat.gtceu.physics;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeAddonHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.util.NumberFormatUtil;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator;
import com.gtceu.calcboard.compat.start.helper.RecipeNodeThreadingHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Encapsulates GTCEu overclocking formulas, power calculation (EU/t), parallel computation, and energy tooltips.
 */
public final class GTPowerCalculator {

    private GTPowerCalculator() {}

    public static boolean isBoilerRecipe(RecipeNode node) {
        return GTBoilerPhysics.isBoilerRecipe(node);
    }

    public static boolean isLiquidBoilerRecipe(RecipeNode node) {
        return GTBoilerPhysics.isLiquidBoilerRecipe(node);
    }

    public static double getBoilerSpeedMultiplier(RecipeNode node) {
        return GTBoilerPhysics.getBoilerSpeedMultiplier(node);
    }

    public static boolean isLargeBoilerRecipe(RecipeNode node) {
        return GTBoilerPhysics.isLargeBoilerRecipe(node);
    }

    public static EnergyType getEnergyType(RecipeNode node) {
        if (node == null) return EnergyType.ELECTRIC_EU;
        if (isBoilerRecipe(node) || isLiquidBoilerRecipe(node)) {
            return EnergyType.HEAT_OR_SELF;
        }
        if (node.getMachineIcon() != null && (node.getMachineIcon().getNamespace().equals("gtceu") || node.getMachineIcon().getNamespace().equals("start"))) {
            if (node.getBaseEUt() > 0.0 || node.isGenerator()) {
                return EnergyType.ELECTRIC_EU;
            }
        }
        if (node.getBaseEUt() <= 0.0 && !node.isGenerator()) {
            return EnergyType.NONE;
        }
        return EnergyType.ELECTRIC_EU;
    }

    public static double computeSingleMachinePower(RecipeNode node) {
        if (!node.isOperational()) return 0.0;
        if (node.isModule()) {
            return node.getBaseEUt();
        }
        if (node.isGenerator()) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                double turbineBoost = GTTurbineHelper.getTurbineBoostMultiplier(node);
                double genericAddonMult = node.getAddons().stream()
                        .filter(a -> a.getCategory() != MachineAddon.Category.MULTIBLOCK_TRAIT)
                        .mapToDouble(MachineAddon::getEutMultiplier)
                        .reduce(1.0, (a, b) -> a * b);
                double boost = genericAddonMult * turbineBoost;
                if (GTTurbineHelper.hasRotorAddon(node)) {
                    double rawGen = computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
                    double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                    return Math.min(rawGen, cap) * boost;
                }
                return computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node) * boost;
            } else if (GTCombustionHelper.isCombustionEngine(node)) {
                return computeCombustionPower(node);
            } else if (isGTGenerator(node) && !node.isMultiblock()) {
                double rawGen = computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
                double cap = (double) node.getTargetTier().getVoltage() * node.getParallel();
                return Math.min(rawGen, cap);
            }
            return computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
        }
        return computeOverclock(node, node.getTargetTier(), false).eut() * computePowerEffectiveParallel(node);
    }

    public static double computeCombustionPower(RecipeNode node) {
        if (GTCombustionHelper.isModularCombustionFrame(node)) {
            return GTCombustionHelper.computeMCFTotalPower(node);
        }
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt <= 0.0) {
            return 0.0;
        }
        long baseVoltage = GTCombustionHelper.getBaseCombustionVoltage(node);
        if (baseVoltage <= 0L && node.getTargetTier() != null) {
            baseVoltage = node.getTargetTier().getVoltage();
        }
        int baseParallels = (int) Math.max(1, Math.floor((double) baseVoltage / recipeEUt));
        double mult = GTCombustionHelper.getCombustionPowerMultiplier(node);
        int parallelMult = GTCombustionHelper.getCombustionParallelMultiplier(node);

        if (GTCombustionHelper.isLargeCombustionEngine(node) || GTCombustionHelper.isExtremeCombustionEngine(node)) {
            return recipeEUt * baseParallels * parallelMult * mult;
        }
        return recipeEUt * baseParallels * mult;
    }

    public static OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        if (node.isModule()) {
            double duration = Math.max(1.0, node.getBaseDurationTicks());
            return new OverclockMode.OverclockResult(duration, node.getBaseEUt(), 1.0, 0);
        }

        if (node.getEnergyType() == EnergyType.NONE) {
            double durationTicks = Math.max(1.0, (int) (node.getBaseDurationTicks() * node.getCombinedDurationMultiplier()));
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            double durationTicks = Math.max(1.0, (int) (node.getBaseDurationTicks() * node.getSteamMode().getDurationMultiplier() * node.getCombinedDurationMultiplier()));
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            double boilerSpeed = getBoilerSpeedMultiplier(node);
            double durationTicks = Math.max(1.0, node.getBaseDurationTicks() / boilerSpeed);
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        OverclockMode.OverclockResult baseRes = isGenerator
                ? new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0)
                : calculateElectricOverclock(node);

        double finalDuration = calculateFinalDuration(node, baseRes, isGenerator);
        double finalEut = calculateFinalEut(node, baseRes, isGenerator);

        return new OverclockMode.OverclockResult(finalDuration, finalEut, baseRes.batchesPerTick(), baseRes.overclocks());
    }

    private static OverclockMode.OverclockResult calculateElectricOverclock(RecipeNode node) {
        GTVoltageTier reqTier = node.getRecipeTier();
        if (reqTier == null && node.getBaseEUt() > 0) {
            reqTier = GTVoltageTier.getTierForVoltage((long) Math.ceil(node.getBaseEUt()));
        }
        GTVoltageTier targetTier = node.getTargetTier();
        if (!node.isGenerator() && node.getEnergyType() == EnergyType.ELECTRIC_EU && reqTier != null && targetTier != null) {
            if (targetTier.ordinal() < reqTier.ordinal()) {
                return new OverclockMode.OverclockResult(node.getBaseDurationTicks(), 0.0, 1.0, 0);
            }
        }

        int maxTierDelta = resolveMaxTierDelta(node);
        int effectivePar = computePowerEffectiveParallel(node);
        double combinedEutMult = node.getCombinedEutMultiplier();
        double threadingPowerMult = node.hasThreading() ? RecipeNodeThreadingHelper.getThreadingConfig(node).getFinalPowerMultiplier() : 1.0;
        long maxVoltage = GTAddonCompatibilityHandler.getOverclockVoltage(node);

        double baseDuration = resolvePreOverclockBaseDuration(node);
        double currentEUt = node.getBaseEUt();
        double durationMultiplier = 1.0;
        int performedOcs = 0;

        double energyFactor = node.isFusion() ? 2.0 : node.getOverclockMode().getEnergyFactor();
        double speedFactor = node.isFusion() ? 2.0 : node.getOverclockMode().getSpeedFactor();
        double durationFactor = 1.0 / speedFactor;

        boolean allowSubtick = node.isMultiblock();
        double subtickParallel = 1.0;
        boolean isSubticking = false;
        int maxParallels = getHatchAndHardwareParallelLimit(node);
        double runningDuration = baseDuration;

        int ebfPerfectOCs = node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_PERFECT_OC_COUNT);

        for (int i = 0; i < maxTierDelta; i++) {
            double nextEUt = currentEUt * energyFactor;
            if (nextEUt > maxVoltage) {
                break;
            }
            double nextTotalEUt = nextEUt * effectivePar * subtickParallel * combinedEutMult * threadingPowerMult;
            if (nextTotalEUt > maxVoltage) {
                break;
            }

            boolean stepPerfect = (i < ebfPerfectOCs) || (node.getOverclockMode() == OverclockMode.PERFECT);
            double stepSpeedFactor = stepPerfect ? 4.0 : speedFactor;
            double nextDuration = Math.floor(runningDuration / stepSpeedFactor);

            if (!allowSubtick) {
                if (nextDuration < 1.0) {
                    break;
                }
                runningDuration = nextDuration;
            } else if (isSubticking || nextDuration < 1.0) {
                double nextParallel = subtickParallel * stepSpeedFactor;
                if (nextParallel > maxParallels) {
                    break;
                }
                subtickParallel = nextParallel;
                isSubticking = true;
            } else {
                runningDuration = nextDuration;
            }

            currentEUt = nextEUt;
            performedOcs++;
        }

        double ocDurationTicks = Math.max(1.0, runningDuration);
        return new OverclockMode.OverclockResult(ocDurationTicks, currentEUt, subtickParallel, performedOcs);
    }

    private static int resolveMaxTierDelta(RecipeNode node) {
        if (node.isFusion()) {
            return node.getTierDelta() + GTFusionHelper.getReflectorOverclockDelta(node);
        }
        int maxTierDelta = node.getTierDelta();
        long maxVoltage = GTAddonCompatibilityHandler.getOverclockVoltage(node);
        if (maxVoltage >= Long.MAX_VALUE || node.getRecipeTier() == null) {
            return maxTierDelta;
        }
        GTVoltageTier capacityTier = GTVoltageTier.getMaxTierProvided(maxVoltage);
        int capacityDelta = capacityTier.ordinal() - node.getRecipeTier().ordinal();
        if (node.getRecipeTier() == GTVoltageTier.ULV) {
            capacityDelta--;
        }
        return Math.max(maxTierDelta, Math.max(0, capacityDelta));
    }

    public static boolean isSequentialPostOcAddon(MachineAddon addon) {
        if (addon == null || addon.getId() == null) return false;
        String id = addon.getId();
        return "gtceu:throughput_boosting".equals(id)
                || id.startsWith("gtceu:bulk_processing")
                || "gtceu:batch_processing".equals(id)
                || "gtceu:batch_mode".equals(id);
    }

    public static boolean hasThroughputBoosting(RecipeNode node) {
        if (node == null) return false;
        for (MachineAddon a : node.getAddons()) {
            if ("gtceu:throughput_boosting".equals(a.getId())) {
                return true;
            }
        }
        return false;
    }

    public static double getBulkingDurationMultiplier(RecipeNode node) {
        if (node == null) return 0.0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getId() != null && a.getId().startsWith("gtceu:bulk_processing")) {
                return a.getDurationMultiplier();
            }
        }
        return 0.0;
    }

    public static boolean hasBatchModeAddon(RecipeNode node) {
        if (node == null) return false;
        for (MachineAddon a : node.getAddons()) {
            if ("gtceu:batch_processing".equals(a.getId()) || "gtceu:batch_mode".equals(a.getId())) {
                return true;
            }
        }
        return false;
    }

    public static double resolvePreOverclockBaseDuration(RecipeNode node) {
        double maintDurationMult = getMaintenanceDurationMultiplier(node);
        double maintDuration = maintDurationMult != 1.0
                ? Math.max(1.0, Math.round(node.getBaseDurationTicks() * maintDurationMult))
                : node.getBaseDurationTicks();
        double coilDurationMult = getCoilDurationMultiplier(node);
        return Math.max(1.0, Math.floor(maintDuration * coilDurationMult + 1e-9));
    }

    public static double getMaintenanceDurationMultiplier(RecipeNode node) {
        if (node == null) return 1.0;
        double mult = 1.0;
        for (MachineAddon a : node.getAddons()) {
            if (a != null && a.getCategory() == MachineAddon.Category.MAINTENANCE) {
                mult *= a.getDurationMultiplier();
            }
        }
        return mult;
    }

    public static double getCoilDurationMultiplier(RecipeNode node) {
        if (node == null) return 1.0;
        double mult = 1.0;
        for (MachineAddon a : node.getAddons()) {
            if (a != null && a.getCategory() == MachineAddon.Category.COIL) {
                mult *= a.getDurationMultiplier();
            }
        }
        return mult;
    }

    public static double getNonSequentialDurationMultiplier(RecipeNode node) {
        if (node == null) return 1.0;
        double mult = 1.0;
        for (MachineAddon a : node.getAddons()) {
            if (a != null
                    && a.getCategory() != MachineAddon.Category.COIL
                    && a.getCategory() != MachineAddon.Category.MAINTENANCE
                    && !isSequentialPostOcAddon(a)) {
                mult *= a.getDurationMultiplier();
            }
        }
        return mult;
    }

    public static int computeBatchMultiplierFromDuration(double dur) {
        if (dur <= 0.0 || dur >= 100.0) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(100.0 / dur));
    }

    public static double calculatePreBatchDuration(RecipeNode node, OverclockMode.OverclockResult baseRes) {
        if (node == null || baseRes == null) return 1.0;
        double nonSeqMult = getNonSequentialDurationMultiplier(node);
        double dur = Math.max(1.0, Math.floor(baseRes.durationTicks() * nonSeqMult + 1e-9));

        if (hasThroughputBoosting(node)) {
            dur = Math.max(1.0, Math.floor(dur * 1.6 + 1e-9));
        }
        double bulkMult = getBulkingDurationMultiplier(node);
        if (bulkMult > 0.0) {
            dur = Math.max(1.0, Math.floor(dur * bulkMult + 1e-9));
        }
        if (node.hasThreading()) {
            dur = Math.max(1.0, dur * RecipeNodeThreadingHelper.getThreadingConfig(node).getFinalDurationMultiplier());
        }
        return dur;
    }

    public static double calculatePreBatchDuration(RecipeNode node) {
        if (node == null) return 1.0;
        OverclockMode.OverclockResult baseRes = calculateElectricOverclock(node);
        return calculatePreBatchDuration(node, baseRes);
    }

    public static int computeBatchMultiplier(RecipeNode node) {
        if (!hasBatchModeAddon(node)) {
            return 1;
        }
        OverclockMode.OverclockResult baseRes = calculateElectricOverclock(node);
        double preBatchDur = calculatePreBatchDuration(node, baseRes);
        return computeBatchMultiplierFromDuration(preBatchDur);
    }

    private static double calculateFinalDuration(RecipeNode node, OverclockMode.OverclockResult baseRes, boolean isGenerator) {
        if (isGenerator && GTTurbineHelper.isLargeTurbine(node)) {
            return calculateLargeTurbineDuration(node, baseRes);
        }
        double dur = calculatePreBatchDuration(node, baseRes);
        if (hasBatchModeAddon(node)) {
            dur *= computeBatchMultiplierFromDuration(dur);
        }
        return Math.max(1.0, dur);
    }

    private static double calculateFinalEut(RecipeNode node, OverclockMode.OverclockResult baseRes, boolean isGenerator) {
        double eut;
        if (isGenerator && GTTurbineHelper.isLargeTurbine(node)) {
            eut = baseRes.eut();
        } else {
            eut = Math.max(1.0, baseRes.eut() * node.getCombinedEutMultiplier());
        }
        if (node.hasThreading()) {
            eut = Math.max(1.0, eut * RecipeNodeThreadingHelper.getThreadingConfig(node).getFinalPowerMultiplier());
        }
        return eut;
    }

    private static double calculateLargeTurbineDuration(RecipeNode node, OverclockMode.OverclockResult baseRes) {
        int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
        double rMult = (node.getRotorEfficiency() > 0 ? node.getRotorEfficiency() : 100) / 100.0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.ROTOR) {
                rMult = a.getDurationMultiplier();
                break;
            }
        }
        double rotorEffMult = Math.max(1.0, rMult * (1.0 + (holderBonus / 100.0)));
        double otherMult = 1.0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() != MachineAddon.Category.ROTOR) {
                otherMult *= a.getDurationMultiplier();
            }
        }
        return Math.max(1.0, baseRes.durationTicks() * rotorEffMult * otherMult);
    }

    public static int computeEffectiveParallel(RecipeNode node) {
        int par;
        if (node.isGenerator()) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                par = GTTurbineHelper.getEffectiveTurbineParallel(node) * node.getCombinedParallelMultiplier();
            } else if (GTCombustionHelper.isCombustionEngine(node)) {
                int boostMult = GTCombustionHelper.getCombustionParallelMultiplier(node);
                int addonMult = node.getCombinedParallelMultiplier();
                par = getEffectiveCombustionParallel(node) * Math.max(boostMult, addonMult);
            } else if (isGTGenerator(node) && !node.isMultiblock()) {
                par = getEffectiveSingleblockParallel(node) * node.getCombinedParallelMultiplier();
            } else {
                par = computeGeneratorMultiblockParallel(node);
            }
        } else {
            int powerConstantMult = NodeAddonHelper.getPowerConstantParallelMultiplier(node.getAddons());
            if (com.gtceu.calcboard.compat.start.StarTHellForgeHelper.isHellForge(node)) {
                powerConstantMult *= com.gtceu.calcboard.compat.start.StarTHellForgeHelper.computeInfernalParallel(node);
            }
            int powerConsumingPar = computePowerConsumingParallel(node);
            par = Math.max(1, powerConsumingPar * powerConstantMult);
            if (hasBatchModeAddon(node)) {
                par *= computeBatchMultiplier(node);
            }
        }
        return par;
    }

    /**
     * Computes the effective electrical parallel capacity that actively consumes power.
     * Excludes power-constant traits such as throughput boosting, bulking, and batch mode.
     *
     * @param node the recipe node to evaluate
     * @return the power-consuming parallel count, capped by energy hatch capacity
     */
    public static int computePowerConsumingParallel(RecipeNode node) {
        if (node == null) return 1;
        int par;
        int base = node.isMultiblock() ? getDefaultParallel(node) : 1;
        int effectiveBase = Math.max(base, node.getParallel() > 1 ? node.getParallel() : 1);
        if (isCoilParallelNode(node)) {
            int coilPar = 0;
            for (MachineAddon addon : node.getAddons()) {
                coilPar = Math.max(coilPar, extractCoilSmelterParallel(addon));
            }
            int nonCoilPowerConsumingMult = 1;
            for (MachineAddon a : node.getAddons()) {
                if (a.getCategory() != MachineAddon.Category.COIL && !a.isPowerConstant()) {
                    nonCoilPowerConsumingMult *= a.getParallelMultiplier();
                }
            }
            int baseSmelterPar = coilPar > 0 ? coilPar : effectiveBase;
            par = Math.max(1, baseSmelterPar * nonCoilPowerConsumingMult);
        } else {
            int powerConsumingMult = NodeAddonHelper.getPowerConsumingParallelMultiplier(node.getAddons());
            int powerConsumingPar = Math.max(1, effectiveBase * powerConsumingMult);
            par = calculateEnergyParallelCap(node, powerConsumingPar);
        }
        if (node.hasThreading()) {
            par *= RecipeNodeThreadingHelper.getThreadingConfig(node).getEffectiveParallels();
        }
        return par;
    }

    public static int extractCoilSmelterParallel(MachineAddon addon) {
        if (addon instanceof GTCoilAddon coil) {
            return coil.getSmelterParallel();
        }
        if (addon.getCategory() != MachineAddon.Category.COIL) {
            return 0;
        }
        if (addon.getSmelterParallel() > 0) {
            return addon.getSmelterParallel();
        }
        var stats = CoilHelper.getCoilStats(addon.getId());
        return (stats != null && stats.smelterParallel() > 0) ? stats.smelterParallel() : 0;
    }

    private static int calculateEnergyParallelCap(RecipeNode node, int defaultCap) {
        if (isCoilParallelNode(node) || node.getEnergyType() != EnergyType.ELECTRIC_EU) {
            return defaultCap;
        }
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return defaultCap;
        }
        if (node.isMultiblock() && GTEnergyHatchCalculator.requiresEnergyHatch(node) && !GTEnergyHatchCalculator.hasEnergyHatch(node)) {
            return defaultCap;
        }
        double singleRecipeEUt = node.getBaseEUt();
        if (node.hasThreading()) {
            singleRecipeEUt *= RecipeNodeThreadingHelper.getThreadingConfig(node).getFinalPowerMultiplier();
        }
        if (singleRecipeEUt <= 0.0) {
            return defaultCap;
        }
        long maxVoltage = GTAddonCompatibilityHandler.getOverclockVoltage(node);
        if (maxVoltage <= 0 || maxVoltage == Long.MAX_VALUE) {
            return defaultCap;
        }
        int energyParCap = (int) Math.max(1, Math.floor((double) maxVoltage / singleRecipeEUt));
        return Math.min(defaultCap, energyParCap);
    }

    /**
     * Computes the effective parallel factor used strictly for power and overclocking calculations,
     * factoring out constant-power parallel multipliers (e.g. Throughput Boosting) and batch processing.
     *
     * @param node the recipe node to evaluate
     * @return the power-effective parallel count (minimum 1)
     */
    public static int computePowerEffectiveParallel(RecipeNode node) {
        if (node == null) return 1;
        if (node.isGenerator()) {
            return computeEffectiveParallel(node);
        }
        return computePowerConsumingParallel(node);
    }

    public static boolean isCoilParallelNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && MultiblockDetector.isCoilParallelMultiblock(node.getMachineIcon())) {
            return true;
        }
        if (node.getMultiblockWorkstation() != null && MultiblockDetector.isCoilParallelMultiblock(node.getMultiblockWorkstation())) {
            return true;
        }
        return false;
    }

    public static int getDefaultParallel(RecipeNode node) {
        if (node == null) return 1;
        if (node.isMultiblock() && isCoilParallelNode(node)) {
            for (MachineAddon addon : node.getAddons()) {
                int coilPar = extractCoilSmelterParallel(addon);
                if (coilPar > 0) return coilPar;
            }
        }
        return MultiblockDetector.getDefaultParallel(node);
    }

    public static void autoTuneParallel(RecipeNode node) {
        if (node.isGenerator() && GTTurbineHelper.isLargeTurbine(node)) {
            GTTurbineHelper.autoCalculateTurbineParallel(node);
        } else if (node.getParallel() <= 1) {
            int defPar = getDefaultParallel(node);
            if (defPar > 1) {
                node.setParallel(defPar);
            }
        }
    }

    public static boolean isGTGenerator(RecipeNode node) {
        if (!node.isGenerator()) return false;
        if (node.getEnergyType() != EnergyType.ELECTRIC_EU) return false;
        if (node.getRecipeCategoryId() != null && (node.getRecipeCategoryId().getNamespace().equals("gtceu") || node.getRecipeCategoryId().getNamespace().equals("start"))) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && (ws.getNamespace().equals("gtceu") || ws.getNamespace().equals("start"))) return true;
        }
        if (node.getMachineIcon() != null && (node.getMachineIcon().getNamespace().equals("gtceu") || node.getMachineIcon().getNamespace().equals("start"))) return true;
        return false;
    }

    public static int getEffectiveCombustionParallel(RecipeNode node) {
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt <= 0.0) {
            return 1;
        }
        long baseVoltage = GTCombustionHelper.getBaseCombustionVoltage(node);
        if (baseVoltage <= 0L && node.getTargetTier() != null) {
            baseVoltage = node.getTargetTier().getVoltage();
        }
        return (int) Math.max(1, Math.floor((double) baseVoltage / recipeEUt));
    }

    private static int computeGeneratorMultiblockParallel(RecipeNode node) {
        int rawPar = Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
        if (!com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator.hasEnergyHatch(node)) {
            return rawPar;
        }
        double singleRecipeEUt = Math.abs(node.getBaseEUt()) * node.getCombinedEutMultiplier();
        if (singleRecipeEUt <= 0.0) {
            return rawPar;
        }
        long dynamoCap = com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator.getMaxEUtCapacity(node);
        if (dynamoCap <= 0 || dynamoCap >= Long.MAX_VALUE) {
            return rawPar;
        }
        int maxDynamoPar = (int) Math.max(1, Math.floor((double) dynamoCap / singleRecipeEUt));
        return Math.min(rawPar, maxDynamoPar);
    }

    public static int getEffectiveSingleblockParallel(RecipeNode node) {
        if (!isGTGenerator(node) || node.isMultiblock() || GTTurbineHelper.isLargeTurbine(node)) return Math.max(1, node.getParallel());
        if (node.getParallel() > 1) return node.getParallel();
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt > 0 && node.getTargetTier() != null && recipeEUt < node.getTargetTier().getVoltage()) {
            return (int) Math.max(1, Math.floor((double) node.getTargetTier().getVoltage() / recipeEUt));
        }
        return Math.max(1, node.getParallel());
    }

    public static int getMaxParallelCapacity(RecipeNode node) {
        if (node == null) return 1;
        if (GTCombustionHelper.isCombustionEngine(node)) {
            return 1;
        }
        if (node.isGenerator() || GTTurbineHelper.isTurbine(node)) {
            if (node.isMultiblock() && !GTTurbineHelper.isTurbine(node)
                    && com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator.hasEnergyHatch(node)) {
                return getGeneratorMultiblockMaxParallel(node);
            }
            double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
            double recipeEUt = Math.abs(node.getBaseEUt());
            if (recipeEUt <= 0.0 || cap >= Double.MAX_VALUE) return Math.max(1, node.getParallel());
            return (int) Math.max(1, Math.ceil(cap / recipeEUt));
        }

        // Processing machine
        int hatchAndHardware = getHatchAndHardwareParallelLimit(node);
        int energyLimit = Integer.MAX_VALUE;
        long maxVoltage = GTAddonCompatibilityHandler.getOverclockVoltage(node);
        if (maxVoltage > 0 && maxVoltage < Long.MAX_VALUE && !isCoilParallelNode(node)) {
            double singleRecipeEUt = node.getBaseEUt()
                    * (node.hasThreading() ? RecipeNodeThreadingHelper.getThreadingConfig(node).getFinalPowerMultiplier() : 1.0);
            if (singleRecipeEUt > 0.0) {
                energyLimit = (int) Math.max(1, Math.floor((double) maxVoltage / singleRecipeEUt));
            }
        }

        int maxPar = Math.min(hatchAndHardware, energyLimit);
        int baseLimit = Math.max(1, maxPar == Integer.MAX_VALUE ? node.getParallel() : maxPar);
        int powerConstantMult = NodeAddonHelper.getPowerConstantParallelMultiplier(node.getAddons());
        if (com.gtceu.calcboard.compat.start.StarTHellForgeHelper.isHellForge(node)) {
            powerConstantMult *= com.gtceu.calcboard.compat.start.StarTHellForgeHelper.computeInfernalParallel(node);
        }
        return baseLimit * powerConstantMult * computeBatchMultiplier(node);
    }

    private static int getGeneratorMultiblockMaxParallel(RecipeNode node) {
        int hatchAndHardware = getHatchAndHardwareParallelLimit(node);
        double singleRecipeEUt = Math.abs(node.getBaseEUt()) * node.getCombinedEutMultiplier();
        long dynamoCap = com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator.getMaxEUtCapacity(node);
        int dynamoLimit = (singleRecipeEUt > 0.0 && dynamoCap < Long.MAX_VALUE)
                ? (int) Math.max(1, Math.floor((double) dynamoCap / singleRecipeEUt))
                : Integer.MAX_VALUE;
        int maxPar = Math.min(hatchAndHardware, dynamoLimit);
        return Math.max(1, maxPar == Integer.MAX_VALUE ? node.getParallel() : maxPar);
    }

    public static int getHatchAndHardwareParallelLimit(RecipeNode node) {
        if (node == null) return 1;
        int hatchLimit = Integer.MAX_VALUE;
        boolean hasParallelHatch = false;
        for (MachineAddon addon : node.getAddons()) {
            if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon ph) {
                hatchLimit = ph.getParallelMultiplier();
                hasParallelHatch = true;
                break;
            }
        }
        if (!hasParallelHatch && !node.isMultiblock()) {
            hatchLimit = 1;
        }

        int hardwareLimit = Integer.MAX_VALUE;
        if (node.isMultiblock() && MultiblockDetector.isCoilParallelMultiblock(node)) {
            int coilSmelterPar = CoilHelper.getInstalledCoilSmelterParallel(node);
            if (coilSmelterPar > 0) {
                hardwareLimit = coilSmelterPar;
            }
        }
        return Math.min(hatchLimit, hardwareLimit);
    }

    public static boolean hasParallelHatch(RecipeNode node) {
        if (node == null) return false;
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon
                    || a.getCategory() == MachineAddon.Category.PARALLEL) {
                return true;
            }
        }
        return false;
    }

    public static final java.util.Set<ResourceLocation> MACERATOR_CATEGORIES = java.util.Set.of(
            ResourceLocation.tryParse("gtceu:macerator"),
            ResourceLocation.tryParse("gtceu:macerator_recipes"),
            ResourceLocation.tryParse("gtceu:ore_crushing"),
            ResourceLocation.tryParse("gtceu:macerator_recycling")
    );

    private static final java.util.Set<ResourceLocation> MACERATOR_MACHINES;

    static {
        java.util.Set<ResourceLocation> machines = new java.util.HashSet<>();
        machines.add(ResourceLocation.tryParse("gtceu:lp_steam_macerator"));
        machines.add(ResourceLocation.tryParse("gtceu:hp_steam_macerator"));
        machines.add(ResourceLocation.tryParse("gtceu:large_macerator"));
        for (GTVoltageTier tier : GTVoltageTier.values()) {
            machines.add(ResourceLocation.tryParse("gtceu:" + tier.getName().toLowerCase(java.util.Locale.ROOT) + "_macerator"));
        }
        MACERATOR_MACHINES = java.util.Collections.unmodifiableSet(machines);
    }

    /**
     * Checks if the given recipe category identifier corresponds to a macerator processing line.
     *
     * @param catId the recipe category identifier
     * @return true if the category is a recognized macerator category
     */
    public static boolean isMaceratorCategory(ResourceLocation catId) {
        if (catId == null) return false;
        if (MACERATOR_CATEGORIES.contains(catId)) return true;
        if ("gtceu".equals(catId.getNamespace())) {
            String path = catId.getPath();
            return "macerator".equals(path) || "ore_crushing".equals(path) || "macerator_recycling".equals(path);
        }
        return false;
    }

    /**
     * Checks if the given item or machine identifier is a recognized macerator machine.
     *
     * @param id the resource location of the machine
     * @return true if the machine is a macerator
     */
    public static boolean isMaceratorMachine(ResourceLocation id) {
        if (id == null) return false;
        return MACERATOR_MACHINES.contains(id);
    }

    /**
     * Determines whether the specified recipe node operates as a macerator,
     * inspecting recipe category, machine icon, and available workstations.
     *
     * @param node the recipe node to inspect
     * @return true if the node is classified as a macerator
     */
    public static boolean isMaceratorNode(RecipeNode node) {
        if (node == null) return false;
        if (isMaceratorCategory(node.getRecipeCategoryId())) {
            return true;
        }
        if (node.getMachineIcon() != null && isMaceratorMachine(node.getMachineIcon())) {
            return true;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (isMaceratorMachine(ws)) {
                return true;
            }
        }
        return false;
    }

    public static double computeEffectiveOutputChance(RecipeNode node, int outputIndex, double defaultChance) {
        if (node == null || outputIndex < 0 || outputIndex >= node.getOutputs().size()) return defaultChance;
        IngredientStack out = node.getOutputs().get(outputIndex);
        if (outputIndex == 0) {
            return out.getChance() >= 1.0 ? 1.0 : out.getEffectiveChance(node.getTierDelta());
        }

        if (node.isMultiblock() && MultiblockDetector.isSteamOreFactory(node)) {
            return out.getEffectiveChance(node.getTierDelta());
        }

        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return 0.0;
        }

        if (isMaceratorNode(node)) {
            GTVoltageTier curTier = node.getTargetTier();
            if (curTier == null) curTier = GTVoltageTier.LV;
            int curTierIdx = curTier.ordinal();

            GTVoltageTier reqTier = (node.getRecipeTier() != null && node.getRecipeTier().ordinal() > GTVoltageTier.HV.ordinal())
                    ? node.getRecipeTier()
                    : GTVoltageTier.HV;

            if (curTierIdx < reqTier.ordinal()) {
                return 0.0;
            }

            int extraTiers = curTierIdx - reqTier.ordinal();
            double boost = out.getTierChanceBoost();
            return Math.min(1.0, Math.max(0.0, out.getChance() + extraTiers * boost));
        }

        if (out.getChance() >= 1.0) return 1.0;

        return defaultChance;
    }

    public static List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        if (node == null) return tooltipLines;

        if (!node.isOperational()) {
            List<Component> warnings = node.getOperationalWarnings(null);
            if (!warnings.isEmpty()) {
                tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.node_warning.inactive").getString()));
                for (Component warning : warnings) {
                    tooltipLines.add(Component.literal("§c❌ ").append(warning));
                }
            }
        }

        if (node.getEnergyType() == EnergyType.NONE) {
            tooltipLines.add(Component.literal("§7- " + Component.translatable("gui.gtcalcboard.energy_passive").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.2fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            return tooltipLines;
        }

        if (isBoilerRecipe(node) || node.isLiquidBoilerRecipe()) {
            GTBoilerTier boilerTier = GTBoilerTier.getBoilerTier(node);
            tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.boiler_title").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Boiler Type: %s%s", boilerTier.getFormatCode(), boilerTier.getDisplayName())));
            if (boilerTier.isMultiblock()) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Throttle: §b%d%%", node.getBoilerThrottle())));
            }
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Speed Multiplier: §e%.2fx", getBoilerSpeedMultiplier(node))));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.2fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            return tooltipLines;
        }

        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.steam_machine_title").getString()));
            double steamRate = node.getBaseEUt() * 2.0 * 20.0 * node.getMachineCount();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Steam Consumption: §b♨ %,.1f L/s", steamRate)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Operating Mode: %s%s", node.getSteamMode().getFormatCode(), node.getSteamMode().getDisplayName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§8* Steam Ratio: 1 EU = 2 mB Steam (Speed: %s)", node.getSteamMode() == com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE ? "0.5x" : "1.0x")));
            return tooltipLines;
        }

        if (node.isFusion()) {
            int fTier = node.getFusionTier();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§d⚛ Fusion Reactor Mk%d", fTier)));
            long startEU = node.getEuToStart();
            if (startEU > 0) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Start Ignition Energy: §e%,d EU §7(§f%s EU§7)", startEU, NumberFormatUtil.formatCompactNumber(startEU))));
            }
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = node.getMinFusionVoltageTier();
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Running Power: §c%,.2f EU/t", totEUt)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Current: §e%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Minimum Voltage Tier: §f%s", node.getMinFusionVoltageTier().getName())));
            if (node.getEfficiency() < 0.999) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§c⚠ %s: %.1f%%", Component.translatable("gui.gtcalcboard.tooltip.bottleneck_eff").getString(), node.getEfficiency() * 100.0)));
            }
            return tooltipLines;
        }

        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.total_gen").getString()));
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Generation: §a+%,.2f EU/t", totEUt)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Current: §a+%,.4fA %s", amps, tier.getName())));
            double dispCps = GTCombustionHelper.isCombustionEngine(node)
                    ? (1.0 / Math.max(0.05, node.getEffectiveDurationSeconds())) * node.getMachineCount()
                    : node.getEffectiveCyclesPerSecond();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), dispCps)));

            if (GTTurbineHelper.isTurbine(node)) {
                GTVoltageTier holderTier = GTTurbineHelper.getRotorHolderTier(node);
                GTVoltageTier dynamoTier = GTTurbineHelper.getDynamoTier(node);
                int dynamoAmps = GTTurbineHelper.getDynamoAmperage(node);
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Holder: §b%s §7| Dynamo: §e%s §7(%dA)",
                        holderTier.getName(), dynamoTier.getName(), dynamoAmps)));

                if (GTTurbineHelper.isCoolantBoost(node)) {
                    tooltipLines.add(Component.literal("§b❄ " + Component.translatable("gui.gtcalcboard.boost_coolant_active").getString() + " §a(+50%)"));
                } else if (GTTurbineHelper.isLubricantBoost(node)) {
                    tooltipLines.add(Component.literal("§e~ " + Component.translatable("gui.gtcalcboard.boost_lubricant_active").getString() + " §a(+25%)"));
                }

                if (GTTurbineHelper.hasRotorAddon(node)) {
                    appendTurbineRotorWearTooltip(node, tooltipLines);
                }

                if (node.getEfficiency() < 0.999) {
                    tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§e⚡ Rotor Efficiency: §f%.1f%%", node.getEfficiency() * 100.0)));
                }
            } else if (GTCombustionHelper.isCombustionEngine(node)) {
                if (GTCombustionHelper.isOxygenBoosted(node)) {
                    tooltipLines.add(Component.literal("§b💨 " + Component.translatable("gui.gtcalcboard.addon.oxygen_boost").getString() + " §a(+50% EU/t, 2x Fuel)"));
                } else if (GTCombustionHelper.isLiquidOxygenBoosted(node)) {
                    tooltipLines.add(Component.literal("§b💨 " + Component.translatable("gui.gtcalcboard.addon.liquid_oxygen_boost").getString() + " §a(+100% EU/t, 2x Fuel)"));
                }

                if (GTCombustionHelper.isOxidizerBoosted(node)) {
                    String ox = node.getProperties().get(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE);
                    tooltipLines.add(Component.literal("§b💨 " + Component.translatable("gui.gtcalcboard.tooltip.oxidizer_boost").getString() + ": §e" + GTCombustionHelper.getOxidizerDisplayName(ox) + " §a(2x Fuel, Amp Boost)"));
                }
                if (GTCombustionHelper.isCoolantBoosted(node)) {
                    String cl = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
                    tooltipLines.add(Component.literal("§b❄ " + Component.translatable("gui.gtcalcboard.tooltip.coolant_boost").getString() + ": §e" + GTCombustionHelper.getCoolantDisplayName(cl)));
                }
            }
        } else {
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Consumption: §e%,.2f EU/t", totEUt)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Current: §e%,.4fA %s", amps, tier.getName())));
            if (node.isCompoundNode()) {
                double totalCycleSec = com.gtceu.calcboard.api.model.NodePerformanceHelper.computeCompoundCycleDurationTicks(node) / 20.0;
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7/ §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), totalCycleSec, node.getEffectiveCyclesPerSecond())));
            } else {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            }
            if (com.gtceu.calcboard.compat.start.StarTHellForgeHelper.isHellForge(node)) {
                appendHellForgeTooltip(node, tooltipLines);
            }
        }
        return tooltipLines;
    }

    private static void appendHellForgeTooltip(RecipeNode node, List<Component> tooltipLines) {
        var fluid = com.gtceu.calcboard.compat.start.StarTHellForgeHelper.getSelectedFluid(node);
        int curTemp = com.gtceu.calcboard.compat.start.StarTHellForgeHelper.getOperatingTemperature(node);
        int infPar = com.gtceu.calcboard.compat.start.StarTHellForgeHelper.computeInfernalParallel(node);
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§6🔥 " + Component.translatable("gui.gtcalcboard.hellforge.crucible_heat").getString() + ": §f%,d MK §7(%s) §a[%dx]", curTemp, fluid.getDisplayName(), infPar)));
    }

    private static void appendTurbineRotorWearTooltip(RecipeNode node, List<Component> tooltipLines) {
        double wearPerSec = GTTurbineHelper.calculateRotorWearPerSecond(node);
        double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
        double replacementRate = GTTurbineHelper.calculateRotorReplacementRatePerHour(node);

        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_wear_rate").getString() + ": §c-%,.2f dmg/s", wearPerSec)));
        if (Double.isInfinite(lifespanHours) || lifespanHours <= 0) {
            return;
        }
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_lifespan").getString() + ": §e%,.2f h", lifespanHours)));
        if (replacementRate > 0) {
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_replacement_rate").getString() + ": §6%,.4f /h", replacementRate)));
        }
    }
}
