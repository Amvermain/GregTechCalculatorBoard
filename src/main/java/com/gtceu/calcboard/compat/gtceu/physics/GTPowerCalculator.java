package com.gtceu.calcboard.compat.gtceu.physics;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.util.NumberFormatUtil;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
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
                    if (node.getParallel() == 1) {
                        double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                        if (cap < Double.MAX_VALUE) {
                            return cap * boost;
                        }
                    }
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
        if (node.hasPowerConstantAddon()) {
            return computeOverclock(node, node.getTargetTier(), false).eut() * node.getParallel();
        }
        return computeOverclock(node, node.getTargetTier(), false).eut() * computeEffectiveParallel(node);
    }

    public static double computeCombustionPower(RecipeNode node) {
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt <= 0.0) {
            return 0.0;
        }
        long baseVoltage = GTCombustionHelper.getBaseCombustionVoltage(node);
        if (baseVoltage <= 0L && node.getTargetTier() != null) {
            baseVoltage = node.getTargetTier().getVoltage();
        }
        int baseParallels = (int) Math.max(1, Math.floor((double) baseVoltage / recipeEUt));
        int userPar = Math.max(1, node.getParallel());
        double mult = GTCombustionHelper.getCombustionPowerMultiplier(node);
        int parallelMult = GTCombustionHelper.getCombustionParallelMultiplier(node);

        if (GTCombustionHelper.isLargeCombustionEngine(node) || GTCombustionHelper.isExtremeCombustionEngine(node)) {
            return recipeEUt * baseParallels * parallelMult * userPar * mult;
        }
        return recipeEUt * baseParallels * userPar * mult;
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
        int maxTierDelta = resolveMaxTierDelta(node);
        int effectivePar = node.hasPowerConstantAddon() ? node.getParallel() : computeEffectiveParallel(node);
        double combinedEutMult = node.getCombinedEutMultiplier();
        double threadingPowerMult = node.hasThreading() ? node.getThreadingConfig().getFinalPowerMultiplier() : 1.0;
        long maxCapacity = GTAddonCompatibilityHandler.getMaxEUtCapacity(node);

        double baseDuration = node.getBaseDurationTicks();
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
            double nextTotalEUt = nextEUt * effectivePar * subtickParallel * combinedEutMult * threadingPowerMult;
            if (nextTotalEUt > maxCapacity) {
                break;
            }

            boolean stepPerfect = (i < ebfPerfectOCs) || (node.getOverclockMode() == OverclockMode.PERFECT);
            double stepSpeedFactor = stepPerfect ? 4.0 : speedFactor;
            double stepDurationFactor = 1.0 / stepSpeedFactor;

            if (allowSubtick) {
                if (isSubticking || runningDuration * stepDurationFactor < 1.0) {
                    double nextParallel = subtickParallel * stepSpeedFactor;
                    if (nextParallel > maxParallels) {
                        break;
                    }
                    subtickParallel = nextParallel;
                    isSubticking = true;
                } else {
                    runningDuration *= stepDurationFactor;
                    durationMultiplier *= stepDurationFactor;
                }
            } else {
                if (runningDuration * stepDurationFactor < 1.0) {
                    break;
                }
                runningDuration *= stepDurationFactor;
                durationMultiplier *= stepDurationFactor;
            }

            currentEUt = nextEUt;
            performedOcs++;
        }

        double ocDurationTicks = Math.max(1.0, (int) (baseDuration * durationMultiplier));
        return new OverclockMode.OverclockResult(ocDurationTicks, currentEUt, subtickParallel, performedOcs);
    }

    private static int resolveMaxTierDelta(RecipeNode node) {
        if (node.isFusion()) {
            return node.getTierDelta() + GTFusionHelper.getReflectorOverclockDelta(node);
        }
        int maxTierDelta = node.getTierDelta();
        long maxCapacity = GTAddonCompatibilityHandler.getMaxEUtCapacity(node);
        if (maxCapacity >= Long.MAX_VALUE || node.getRecipeTier() == null) {
            return maxTierDelta;
        }
        GTVoltageTier capacityTier = GTVoltageTier.getMaxTierProvided(maxCapacity);
        int capacityDelta = capacityTier.ordinal() - node.getRecipeTier().ordinal();
        if (node.getRecipeTier() == GTVoltageTier.ULV) {
            capacityDelta--;
        }
        return Math.max(maxTierDelta, Math.max(0, capacityDelta));
    }

    private static double calculateFinalDuration(RecipeNode node, OverclockMode.OverclockResult baseRes, boolean isGenerator) {
        double duration;
        if (isGenerator && GTTurbineHelper.isLargeTurbine(node)) {
            duration = calculateLargeTurbineDuration(node, baseRes);
        } else {
            duration = Math.max(1.0, (int) (baseRes.durationTicks() * node.getCombinedDurationMultiplier()));
        }
        if (node.hasThreading()) {
            duration = Math.max(1.0, duration * node.getThreadingConfig().getFinalDurationMultiplier());
        }
        return duration;
    }

    private static double calculateFinalEut(RecipeNode node, OverclockMode.OverclockResult baseRes, boolean isGenerator) {
        double eut;
        if (isGenerator && GTTurbineHelper.isLargeTurbine(node)) {
            eut = baseRes.eut();
        } else {
            eut = Math.max(1.0, baseRes.eut() * node.getCombinedEutMultiplier());
        }
        if (node.hasThreading()) {
            eut = Math.max(1.0, eut * node.getThreadingConfig().getFinalPowerMultiplier());
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
                par = getEffectiveCombustionParallel(node) * node.getCombinedParallelMultiplier();
            } else if (isGTGenerator(node)) {
                par = getEffectiveSingleblockParallel(node) * node.getCombinedParallelMultiplier();
            } else {
                par = Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
            }
        } else {
            int base = node.isMultiblock() ? getDefaultParallel(node) : 1;
            int effectiveBase = Math.max(base, node.getParallel() > 1 ? node.getParallel() : 1);
            if (isCoilParallelNode(node)) {
                int coilPar = 0;
                for (MachineAddon addon : node.getAddons()) {
                    if (addon instanceof GTCoilAddon coil) {
                        coilPar = Math.max(coilPar, coil.getSmelterParallel());
                    } else if (addon.getCategory() == MachineAddon.Category.COIL) {
                        if (addon.getSmelterParallel() > 0) {
                            coilPar = Math.max(coilPar, addon.getSmelterParallel());
                        } else {
                            var stats = CoilHelper.getCoilStats(addon.getId());
                            if (stats != null && stats.smelterParallel() > 0) {
                                coilPar = Math.max(coilPar, stats.smelterParallel());
                            }
                        }
                    }
                }
                int nonCoilParallelMultiplier = 1;
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() != MachineAddon.Category.COIL) {
                        nonCoilParallelMultiplier *= a.getParallelMultiplier();
                    }
                }
                int baseSmelterPar = coilPar > 0 ? coilPar : effectiveBase;
                par = Math.max(1, baseSmelterPar * nonCoilParallelMultiplier);
            } else {
                par = Math.max(1, effectiveBase * node.getCombinedParallelMultiplier());
            }
        }
        if (node.hasThreading()) {
            par *= node.getThreadingConfig().getEffectiveParallels();
        }
        return par;
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
                if (addon instanceof GTCoilAddon coil) {
                    return coil.getSmelterParallel();
                } else if (addon.getCategory() == MachineAddon.Category.COIL) {
                    if (addon.getSmelterParallel() > 0) {
                        return addon.getSmelterParallel();
                    } else {
                        var stats = CoilHelper.getCoilStats(addon.getId());
                        if (stats != null && stats.smelterParallel() > 0) {
                            return stats.smelterParallel();
                        }
                    }
                }
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
            return Math.max(1, node.getParallel());
        }
        long baseVoltage = GTCombustionHelper.getBaseCombustionVoltage(node);
        if (baseVoltage <= 0L && node.getTargetTier() != null) {
            baseVoltage = node.getTargetTier().getVoltage();
        }
        int baseParallels = (int) Math.max(1, Math.floor((double) baseVoltage / recipeEUt));
        int userPar = Math.max(1, node.getParallel());
        return baseParallels * userPar;
    }

    public static int getEffectiveSingleblockParallel(RecipeNode node) {
        if (!isGTGenerator(node) || GTTurbineHelper.isLargeTurbine(node)) return Math.max(1, node.getParallel());
        if (node.getParallel() > 1) return node.getParallel();
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt > 0 && node.getTargetTier() != null && recipeEUt < node.getTargetTier().getVoltage()) {
            return (int) Math.max(1, Math.floor((double) node.getTargetTier().getVoltage() / recipeEUt));
        }
        return Math.max(1, node.getParallel());
    }

    public static int getMaxParallelCapacity(RecipeNode node) {
        if (node == null) return 1;
        if (node.isGenerator() || GTTurbineHelper.isTurbine(node)) {
            double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
            double recipeEUt = Math.abs(node.getBaseEUt());
            if (recipeEUt <= 0.0 || cap >= Double.MAX_VALUE) return Math.max(1, node.getParallel());
            int multiplier = com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(node)
                    ? com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.getModel(node).getParallelMultiplier()
                    : 1;
            return (int) Math.max(1, Math.ceil(cap / recipeEUt)) * multiplier;
        }

        // Processing machine
        int hatchAndHardware = getHatchAndHardwareParallelLimit(node);
        int energyLimit = Integer.MAX_VALUE;
        long maxCapacity = GTAddonCompatibilityHandler.getMaxEUtCapacity(node);
        if (maxCapacity > 0 && maxCapacity < Long.MAX_VALUE) {
            OverclockMode.OverclockResult oc = computeOverclock(node, node.getTargetTier(), false);
            double singleRecipeEUt = oc.eut() * node.getCombinedEutMultiplier()
                    * (node.hasThreading() ? node.getThreadingConfig().getFinalPowerMultiplier() : 1.0);
            if (singleRecipeEUt > 0.0) {
                energyLimit = (int) Math.max(1, Math.floor((double) maxCapacity / singleRecipeEUt));
            }
        }

        int maxPar = Math.min(hatchAndHardware, energyLimit);
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

    private static final java.util.Set<ResourceLocation> MACERATOR_CATEGORIES = java.util.Set.of(
            ResourceLocation.tryParse("gtceu:macerator"),
            ResourceLocation.tryParse("gtceu:macerator_recipes")
    );

    private static boolean isMaceratorCategory(ResourceLocation catId) {
        if (catId == null) return false;
        return MACERATOR_CATEGORIES.contains(catId) || catId.getPath().equals("macerator");
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

        if (isMaceratorCategory(node.getRecipeCategoryId())) {
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
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));

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
                    double wearPerSec = GTTurbineHelper.calculateRotorWearPerSecond(node);
                    double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
                    double replacementRate = GTTurbineHelper.calculateRotorReplacementRatePerHour(node);

                    tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_wear_rate").getString() + ": §c-%,.2f dmg/s", wearPerSec)));
                    if (!Double.isInfinite(lifespanHours) && lifespanHours > 0) {
                        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_lifespan").getString() + ": §e%,.2f h", lifespanHours)));
                        if (replacementRate > 0) {
                            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_replacement_rate").getString() + ": §6%,.4f /h", replacementRate)));
                        }
                    }
                }
            }

            if (node.getEfficiency() < 0.999) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§e⚡ Rotor Efficiency: §f%.1f%%", node.getEfficiency() * 100.0)));
            }
        } else {
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Consumption: §e%,.2f EU/t", totEUt)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Current: §e%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        }
        return tooltipLines;
    }
}
