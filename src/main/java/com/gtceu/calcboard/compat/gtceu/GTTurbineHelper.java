package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.helper.TurbineRotorHelper;
import com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.resources.ResourceLocation;

/**
 * Helper utility encapsulating GregTech Modern turbine mechanics,
 * rotor holder throughput capacity, decoupled dynamo tiers, rotor wear/lifespan,
 * and flow deficit evaluations (RFC-006).
 */
public final class GTTurbineHelper {

    private GTTurbineHelper() {}

    /**
     * Checks whether the given node represents a GregTech Turbine (Singleblock or Multiblock).
     */
    public static boolean isTurbine(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.KINETIC_SU) return false;
        if (node.getEnergyTypeOverride() != null && node.getEnergyTypeOverride() != EnergyType.ELECTRIC_EU && node.getEnergyTypeOverride() != EnergyType.NONE) return false;

        // Coil multiblocks (EBF, CHEF, Pyrolyse, etc.) are smelting furnaces and never turbines
        if (MultiblockDetector.isCoilMultiblock(node.getMachineIcon()) || MultiblockDetector.isCoilRecipeCategory(node.getRecipeCategoryId())) {
            return false;
        }

        ResourceLocation recipeCategoryId = node.getRecipeCategoryId();
        if (recipeCategoryId != null && MultiblockDetector.isTurbineRecipeCategory(recipeCategoryId)) {
            return true;
        }

        ResourceLocation mbWs = node.getMultiblockWorkstation();
        if (mbWs != null && MultiblockDetector.isTurbineMachine(mbWs)) {
            return true;
        }

        ResourceLocation machineIcon = node.getMachineIcon();
        if (machineIcon != null && MultiblockDetector.isTurbineMachine(machineIcon)) {
            return true;
        }

        if (node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)) {
            return true;
        }

        return false;
    }

    /**
     * Checks whether the given node represents a GTCEu Large Multiblock Turbine.
     */
    public static boolean isLargeTurbine(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.KINETIC_SU) {
            return false;
        }
        if (node.getEnergyTypeOverride() != null && node.getEnergyTypeOverride() != EnergyType.ELECTRIC_EU && node.getEnergyTypeOverride() != EnergyType.NONE) {
            return false;
        }
        if (!isTurbine(node)) return false;
        if (node.isMultiblock()) return true;

        ResourceLocation machineIcon = node.getMachineIcon();
        if (machineIcon != null && MultiblockDetector.isTurbineMachine(machineIcon) && MultiblockDetector.isMultiblock(machineIcon)) {
            return true;
        }

        if (hasRotorAddon(node)) {
            return true;
        }

        return false;
    }

    /**
     * Checks whether the node has a rotor addon equipped or non-standard rotor properties.
     */
    public static boolean hasRotorAddon(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.KINETIC_SU || (node.getEnergyTypeOverride() != null && node.getEnergyTypeOverride() != EnergyType.ELECTRIC_EU)) return false;
        if (!isTurbine(node)) return false;
        String rName = node.getRotorName();
        int rPow = node.getRotorPower();
        return node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"))
                || (rPow > 0 && rPow != 100);
    }

    /**
     * Deductively identifies the base voltage tier for this turbine type from GTCEu Machine Definitions or Recipe Category.
     */
    public static GTVoltageTier getTurbineBaseTier(RecipeNode node) {
        if (node == null) return GTVoltageTier.HV;
        ResourceLocation machineIcon = node.getMachineIcon();
        if (machineIcon != null) {
            GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(machineIcon);
            if (tier != null) return tier;
        }
        ResourceLocation recipeCategoryId = node.getRecipeCategoryId();
        if (recipeCategoryId != null) {
            GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(recipeCategoryId);
            if (tier != null) return tier;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(ws);
                if (tier != null) return tier;
            }
        }
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return GTVoltageTier.HV;
        return GTVoltageTier.HV;
    }

    /**
     * Base production for a turbine machine (in EU/t).
     */
    public static double getTurbineBaseProduction(RecipeNode node) {
        if (node == null) return 1024.0;
        ResourceLocation machineIcon = node.getMachineIcon();
        if (machineIcon != null) {
            Double prod = MultiblockDetector.getTurbineBaseProduction(machineIcon);
            if (prod != null) return prod;
        }
        ResourceLocation recipeCategoryId = node.getRecipeCategoryId();
        if (recipeCategoryId != null) {
            Double prod = MultiblockDetector.getTurbineBaseProduction(recipeCategoryId);
            if (prod != null) return prod;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                Double prod = MultiblockDetector.getTurbineBaseProduction(ws);
                if (prod != null) return prod;
            }
        }
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        return baseTier != null ? (double) (baseTier.getVoltage() * 2L) : 1024.0;
    }


    private static GTVoltageTier clampToMinimumRotorHolderTier(GTVoltageTier tier, GTVoltageTier minTier) {
        GTVoltageTier effectiveMin = (minTier != null && minTier.ordinal() >= GTVoltageTier.HV.ordinal())
                ? minTier
                : GTVoltageTier.HV;
        if (tier == null) return effectiveMin;
        return tier.ordinal() < effectiveMin.ordinal() ? effectiveMin : tier;
    }

    private static GTVoltageTier clampToMinimumDynamoTier(GTVoltageTier tier) {
        GTVoltageTier minTier = GTVoltageTier.LV;
        if (tier == null) return minTier;
        return tier.ordinal() < minTier.ordinal() ? minTier : tier;
    }

    public static GTVoltageTier getRotorHolderTier(RecipeNode node) {
        if (node == null) return GTVoltageTier.HV;
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        GTVoltageTier targetTier = node.getTargetTier();
        GTVoltageTier customTier = node.getProperties().get(GTCEuProperties.ROTOR_HOLDER_TIER);

        if (targetTier != null) {
            GTVoltageTier effective = clampToMinimumRotorHolderTier(targetTier, baseTier);
            if (customTier != effective) {
                node.getProperties().set(GTCEuProperties.ROTOR_HOLDER_TIER, effective);
            }
            return effective;
        }

        if (customTier != null) {
            return clampToMinimumRotorHolderTier(customTier, baseTier);
        }

        return clampToMinimumRotorHolderTier(baseTier, baseTier);
    }

    public static void setRotorHolderTier(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return;
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        GTVoltageTier clamped = clampToMinimumRotorHolderTier(tier, baseTier);
        node.getProperties().set(GTCEuProperties.ROTOR_HOLDER_TIER, clamped);
        node.setTargetTier(clamped);
    }

    public static GTVoltageTier getDynamoTier(RecipeNode node) {
        if (node == null) return GTVoltageTier.HV;
        GTVoltageTier customTier = node.getProperties().get(GTCEuProperties.DYNAMO_HATCH_TIER);
        if (customTier != null) {
            return clampToMinimumDynamoTier(customTier);
        }

        GTVoltageTier targetTier = node.getTargetTier();
        if (targetTier != null && targetTier.ordinal() >= GTVoltageTier.LV.ordinal()) {
            return targetTier;
        }

        GTVoltageTier baseTier = getTurbineBaseTier(node);
        return clampToMinimumDynamoTier(baseTier);
    }

    public static void setDynamoTier(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return;
        node.getProperties().set(GTCEuProperties.DYNAMO_HATCH_TIER, clampToMinimumDynamoTier(tier));
    }

    public static int getDynamoAmperage(RecipeNode node) {
        if (node == null) return 16;
        if (node.getProperties().has(GTCEuProperties.DYNAMO_AMPERAGE)) {
            return Math.max(1, node.getProperties().get(GTCEuProperties.DYNAMO_AMPERAGE));
        }
        for (MachineAddon addon : node.getAddons()) {
            if (addon instanceof GTEnergyHatchAddon eh) {
                return Math.max(1, eh.getAmperage());
            }
        }
        return 16;
    }

    public static void setDynamoAmperage(RecipeNode node, int amperage) {
        if (node == null) return;
        node.getProperties().set(GTCEuProperties.DYNAMO_AMPERAGE, Math.max(1, amperage));
    }

    public static boolean supportsTurbineBoost(RecipeNode node) {
        if (node == null || !isTurbine(node)) return false;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter != null && adapter.supportsBoosterControl(node);
    }

    public static boolean isLubricantBoost(RecipeNode node) {
        return node != null && supportsTurbineBoost(node) && Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LUBRICANT_BOOST));
    }

    public static void setLubricantBoost(RecipeNode node, boolean boost) {
        if (node == null || !supportsTurbineBoost(node)) return;
        node.getProperties().set(GTCEuProperties.LUBRICANT_BOOST, boost);
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) adapter.syncBoosterInputs(node);
    }

    public static boolean isCoolantBoost(RecipeNode node) {
        return node != null && supportsTurbineBoost(node) && Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.COOLANT_BOOST));
    }

    public static void setCoolantBoost(RecipeNode node, boolean boost) {
        if (node == null || !supportsTurbineBoost(node)) return;
        node.getProperties().set(GTCEuProperties.COOLANT_BOOST, boost);
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) adapter.syncBoosterInputs(node);
    }

    public static void cycleTurbineBoost(RecipeNode node, int direction) {
        if (node == null || !supportsTurbineBoost(node)) return;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) adapter.cycleBooster(node, direction);
    }

    public static double getTurbineBoostMultiplier(RecipeNode node) {
        if (node == null || !isTurbine(node)) return 1.0;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter instanceof com.gtceu.calcboard.compat.start.StarTModAdapter) {
            return com.gtceu.calcboard.compat.start.StarTTurbineHelper.getTurbineBoostMultiplier(node);
        }
        return 1.0;
    }

    /**
     * Calculates the rotor holder efficiency bonus for a large turbine based on decoupled holder tier.
     */
    public static int getTurbineHolderEfficiencyBonus(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return 0;
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        GTVoltageTier holderTier = getRotorHolderTier(node);
        if (baseTier == null || holderTier == null) return 0;
        int delta = holderTier.ordinal() - baseTier.ordinal();
        return Math.max(0, delta * 10);
    }

    /**
     * Calculates the combined total turbine efficiency (%) according to GTCEu Modern formulas.
     * Total = max(100, RotorEfficiency * (100 + HolderBonus) / 100).
     */
    public static int getTotalTurbineEfficiency(RecipeNode node) {
        if (node == null) return 100;
        int rEff = 100;
        boolean foundRotor = false;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.ROTOR) {
                if (a.getRotorEfficiency() > 0 && a.getRotorEfficiency() != 100) {
                    rEff = a.getRotorEfficiency();
                } else if (a.getDurationMultiplier() > 0) {
                    rEff = (int) Math.round(a.getDurationMultiplier() * 100.0);
                } else {
                    rEff = a.getRotorEfficiency();
                }
                foundRotor = true;
                break;
            }
        }
        if (!foundRotor && node.getRotorEfficiency() > 0) {
            rEff = node.getRotorEfficiency();
        }
        int holderBonus = getTurbineHolderEfficiencyBonus(node);
        int holderEff = 100 + holderBonus;
        return Math.max(100, (int) Math.round((double) (rEff * holderEff) / 100.0));
    }

    /**
     * Calculates base capacity of the rotor holder at the given voltage tier.
     */
    public static double getNodeRotorHolderBaseCapacity(RecipeNode node, GTVoltageTier tier) {
        if (tier == null) return getTurbineBaseProduction(node);
        int tierIndex = tier.ordinal();
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        int delta = tierIndex - (baseTier != null ? baseTier.ordinal() : GTVoltageTier.EV.ordinal());
        double baseProd = getTurbineBaseProduction(node);
        if (delta >= 0) {
            return baseProd * (1L << delta);
        } else {
            return Math.max(baseProd / 8.0, baseProd / (1L << (-delta)));
        }
    }

    public static final int[] STANDARD_AMPERAGES = {1, 2, 4, 16, 64};

    public static void cycleRotorHolderTier(RecipeNode node, int direction) {
        if (node == null) return;
        GTVoltageTier cur = getRotorHolderTier(node);
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        GTVoltageTier effectiveMin = (baseTier != null && baseTier.ordinal() >= GTVoltageTier.HV.ordinal())
                ? baseTier
                : GTVoltageTier.HV;
        int minIdx = effectiveMin.ordinal();
        GTVoltageTier[] tiers = GTVoltageTier.values();
        int availableCount = tiers.length - minIdx;
        if (availableCount <= 0) return;

        int curOffset = Math.max(0, cur.ordinal() - minIdx);
        int nextOffset = (curOffset + direction + availableCount) % availableCount;
        setRotorHolderTier(node, tiers[minIdx + nextOffset]);
    }

    public static void cycleDynamoTier(RecipeNode node, int direction) {
        if (node == null) return;
        GTVoltageTier cur = getDynamoTier(node);
        int minIdx = GTVoltageTier.LV.ordinal(); // Lowest dynamo hatch in GT is LV
        GTVoltageTier[] tiers = GTVoltageTier.values();
        int availableCount = tiers.length - minIdx;
        if (availableCount <= 0) return;

        int curOffset = Math.max(0, cur.ordinal() - minIdx);
        int nextOffset = (curOffset + direction + availableCount) % availableCount;
        setDynamoTier(node, tiers[minIdx + nextOffset]);
    }

    public static void cycleDynamoAmperage(RecipeNode node, int direction) {
        if (node == null) return;
        int curAmps = getDynamoAmperage(node);
        int curIdx = 0;
        for (int i = 0; i < STANDARD_AMPERAGES.length; i++) {
            if (STANDARD_AMPERAGES[i] == curAmps) {
                curIdx = i;
                break;
            }
        }
        int nextIdx = (curIdx + direction + STANDARD_AMPERAGES.length) % STANDARD_AMPERAGES.length;
        setDynamoAmperage(node, STANDARD_AMPERAGES[nextIdx]);
    }

    /**
     * Calculates maximum throughput EU/t of the rotor holder.
     */
    public static double getNodeRotorHolderMaxEUt(RecipeNode node, GTVoltageTier tier, int rotorPower) {
        double baseCap = getNodeRotorHolderBaseCapacity(node, tier);
        double powerMultiplier = (rotorPower > 0 ? rotorPower : 100) / 100.0;
        return Math.floor(baseCap * powerMultiplier);
    }

    /**
     * Calculates maximum dynamo hatch capacity (Voltage * Amperage).
     */
    public static double getDynamoMaxCapacity(RecipeNode node) {
        if (node == null) return Double.MAX_VALUE;
        GTVoltageTier dynamoTier = getDynamoTier(node);
        int amps = getDynamoAmperage(node);
        if (dynamoTier == null) dynamoTier = GTVoltageTier.EV;
        return (double) dynamoTier.getVoltage() * amps;
    }

    /**
     * Fallback static rotor holder base capacity (EV = 4,096 EU/t base).
     */
    public static double getRotorHolderBaseCapacity(GTVoltageTier tier) {
        if (tier == null) return 4096.0;
        int tierIndex = tier.ordinal();
        int delta = tierIndex - GTVoltageTier.EV.ordinal();
        if (delta >= 0) {
            return 4096.0 * (1L << delta);
        } else {
            return Math.max(512.0, 4096.0 / (1L << (-delta)));
        }
    }

    public static double getRotorHolderMaxEUt(GTVoltageTier tier, int rotorPower) {
        double baseCap = getRotorHolderBaseCapacity(tier);
        double powerMultiplier = (rotorPower > 0 ? rotorPower : 100) / 100.0;
        return Math.floor(baseCap * powerMultiplier);
    }

    public static double getRotorHolderMaxEUt(GTVoltageTier tier) {
        return getRotorHolderMaxEUt(tier, 130);
    }

    public static double getRotorMaterialMaxEUt(String rotorName) {
        return TurbineRotorHelper.getRotorStats(rotorName).durability();
    }

    /**
     * Calculates the maximum generator EU/t capacity based on min(RotorHolderCap, DynamoCap).
     */
    public static double getGeneratorMaxEUt(RecipeNode node) {
        if (node == null || !node.isGenerator()) return Double.MAX_VALUE;
        if (!isLargeTurbine(node)) return Double.MAX_VALUE;

        String rName = node.getRotorName();
        int rPower = node.getRotorPower();
        int activePower = rPower > 0 ? rPower : (rName != null && !rName.isEmpty() && !rName.startsWith("Standard") ? TurbineRotorHelper.getRotorStats(rName).power() : 100);
        for (MachineAddon addon : node.getAddons()) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }
        if (activePower <= 0) activePower = 100;

        GTVoltageTier holderTier = getRotorHolderTier(node);
        double holderCap = getNodeRotorHolderMaxEUt(node, holderTier, activePower);
        double dynamoCap = getDynamoMaxCapacity(node);
        return Math.min(holderCap, dynamoCap);
    }

    /**
     * Calculates the unconstrained theoretical maximum EU/t capacity of the equipped rotor and holder.
     */
    public static double getRotorHolderCapacity(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return Double.MAX_VALUE;
        String rName = node.getRotorName();
        int rPower = node.getRotorPower();
        int activePower = rPower > 0 ? rPower : (rName != null && !rName.isEmpty() && !rName.startsWith("Standard") ? TurbineRotorHelper.getRotorStats(rName).power() : 100);
        for (MachineAddon addon : node.getAddons()) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }
        if (activePower <= 0) activePower = 100;
        GTVoltageTier holderTier = getRotorHolderTier(node);
        return getNodeRotorHolderMaxEUt(node, holderTier, activePower);
    }

    /**
     * Evaluates whether the dynamo hatch capacity is strictly limiting the turbine's throughput below holder capacity.
     */
    public static boolean isDynamoBottleneck(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return false;
        double holderCap = getRotorHolderCapacity(node);
        double dynamoCap = getDynamoMaxCapacity(node);
        return dynamoCap < holderCap;
    }

    /**
     * Calculates the theoretical maximum parallel supported by the rotor holder if not constrained by dynamo.
     */
    public static int getRotorHolderMaxParallel(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return 1;
        double holderCap = getRotorHolderCapacity(node);
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt <= 0.0 || holderCap >= Double.MAX_VALUE) return Math.max(1, node.getParallel());
        int multiplier = GTPlasmaTurbineModel.isPlasmaTurbine(node) ? GTPlasmaTurbineModel.getModel(node).getParallelMultiplier() : 1;
        return (int) Math.max(1, Math.ceil(holderCap / recipeEUt)) * multiplier;
    }

    /**
     * Calculates effective turbine parallel count based on final capacity cap.
     */
    public static int getEffectiveTurbineParallel(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return Math.max(1, node != null ? node.getParallel() : 1);
        if (!hasRotorAddon(node)) return Math.max(1, node.getParallel());
        double cap = getGeneratorMaxEUt(node);
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt <= 0 || cap >= Double.MAX_VALUE) return Math.max(1, node.getParallel());
        int multiplier = GTPlasmaTurbineModel.isPlasmaTurbine(node) ? GTPlasmaTurbineModel.getModel(node).getParallelMultiplier() : 1;
        int calculated = (int) Math.max(1, Math.ceil(cap / recipeEUt)) * multiplier;
        return Math.max(calculated, node.getParallel());
    }

    /**
     * Automatically calculates and tunes optimal turbine parallel count.
     */
    public static void autoCalculateTurbineParallel(RecipeNode node) {
        if (node == null || !node.isGenerator() || !isLargeTurbine(node)) {
            if (node != null) node.setParallel(1);
            return;
        }
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt <= 0) return;

        String rName = node.getRotorName();
        int rEff = node.getRotorEfficiency();
        int rPow = node.getRotorPower();
        boolean hasRotor = node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"));
        if (!hasRotor && rEff <= 100 && (rPow <= 100 || rPow == 0)) {
            return;
        }

        int activePower = rPow > 0 && rPow != 100 ? rPow : TurbineRotorHelper.getRotorStats(rName).power();
        for (MachineAddon addon : node.getAddons()) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }

        double capFinal = getGeneratorMaxEUt(node);
        int multiplier = GTPlasmaTurbineModel.isPlasmaTurbine(node) ? GTPlasmaTurbineModel.getModel(node).getParallelMultiplier() : 1;
        node.setParallel((int) Math.max(1, Math.ceil(capFinal / recipeEUt)) * multiplier);
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) adapter.syncBoosterInputs(node);
    }


    /**
     * Calculates the rotor durability loss per second based on machine throughput utilization.
     */
    public static double calculateRotorWearPerSecond(RecipeNode node) {
        if (node == null || !node.isGenerator() || !isLargeTurbine(node)) return 0.0;
        if (!hasRotorAddon(node)) return 0.0;

        int activePower = node.getRotorPower() > 0 ? node.getRotorPower() : 100;
        double capHolder = getNodeRotorHolderMaxEUt(node, getRotorHolderTier(node), activePower);
        if (capHolder <= 0) return 0.0;

        double actualGen = GTPowerCalculator.computeSingleMachinePower(node);
        double ratio = Math.min(1.0, actualGen / capHolder);
        double lossPerSec = 20.0 * ratio; // 20 damage/sec at 100% capacity
        node.getProperties().set(GTCEuProperties.ROTOR_WEAR_PER_SEC, lossPerSec);
        return lossPerSec;
    }

    /**
     * Calculates total operating lifespan in hours for the equipped turbine rotor.
     */
    public static double calculateRotorLifespanHours(RecipeNode node) {
        if (node == null) return Double.POSITIVE_INFINITY;
        String rName = node.getRotorName();
        double durability = TurbineRotorHelper.getRotorStats(rName).durability();
        if (durability <= 0) durability = 100_000.0;
        double wearPerSec = calculateRotorWearPerSecond(node);
        if (wearPerSec <= 0.0) return Double.POSITIVE_INFINITY;
        return durability / (wearPerSec * 3600.0);
    }

    /**
     * Calculates the hourly rotor replacement consumption rate for all machines in the node.
     */
    public static double calculateRotorReplacementRatePerHour(RecipeNode node) {
        if (node == null) return 0.0;
        double lifespanHours = calculateRotorLifespanHours(node);
        if (lifespanHours <= 0.0 || Double.isInfinite(lifespanHours)) return 0.0;
        return (1.0 / lifespanHours) * node.getMachineCount();
    }

    /**
     * Evaluates whether a turbine node has any connected input with insufficient flow (< 100%).
     */
    public static boolean hasTurbineFlowDeficit(RecipeNode node, FlowGraph graph) {
        if (node == null || graph == null || !isTurbine(node)) return false;
        for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
            FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(node, inIdx);
            if (stats != null && stats.isConnected() && stats.isInputDeficit()) {
                return true;
            }
        }
        return false;
    }
}
