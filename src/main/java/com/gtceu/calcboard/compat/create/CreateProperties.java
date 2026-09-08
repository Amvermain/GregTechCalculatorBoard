package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;

/**
 * Create Kinetic {@link NodePropertyKey} definitions registered to the core {@link NodeProperties} registry.
 */
public final class CreateProperties {

    public static final NodePropertyKey<Integer> KINETIC_RPM = NodeProperties.register(
            NodePropertyKey.ofInt("kinetic_rpm", 32)
    );

    public static final NodePropertyKey<Integer> BASE_GENERATOR_RPM = NodeProperties.register(
            NodePropertyKey.ofInt("base_generator_rpm", 0)
    );

    public static final NodePropertyKey<Integer> BOILER_SIZE_BLOCKS = NodeProperties.register(
            NodePropertyKey.ofInt("create_boiler_size_blocks", 72)
    );

    public static final NodePropertyKey<Integer> BOILER_HEAT_LEVEL = NodeProperties.register(
            NodePropertyKey.ofInt("create_boiler_heat_level", 0)
    );

    public static final NodePropertyKey<Integer> BOILER_WATER_MB_TICK = NodeProperties.register(
            NodePropertyKey.ofInt("create_boiler_water_mbt", 180)
    );

    public static final NodePropertyKey<Integer> BOILER_LEVEL = NodeProperties.register(
            NodePropertyKey.ofInt("create_boiler_level", 0)
    );

    public static final NodePropertyKey<Boolean> BOILER_WATER_MODE = NodeProperties.register(
            NodePropertyKey.ofBoolean("create_boiler_water_mode", true)
    );

    public static final NodePropertyKey<Boolean> IS_CREATE_BOILER = NodeProperties.register(
            NodePropertyKey.ofBoolean("is_create_boiler", false)
    );

    public static final NodePropertyKey<Integer> WINDMILL_SAILS = NodeProperties.register(
            NodePropertyKey.ofInt("windmill_sails", 8)
    );

    public enum BoilerBottleneck {
        NONE,
        SIZE,
        WATER,
        HEAT,
        INACTIVE
    }

    public static final int[] STANDARD_RPMS = {4, 8, 16, 32, 64, 128, 256};

    private CreateProperties() {}

    public static void init() {
        // Classloading triggers static initializer registration
    }

    public static boolean isCreateBoiler(com.gtceu.calcboard.api.model.RecipeNode node) {
        if (node == null || !node.isGenerator()) return false;
        if (node.getEnergyType() != com.gtceu.calcboard.api.type.EnergyType.KINETIC_SU) return false;
        if (Boolean.TRUE.equals(node.getProperties().get(IS_CREATE_BOILER))) return true;
        return CreateStressHelper.isSteamEngine(CreateStressHelper.findBlock(node.getMachineIcon()));
    }

    public static int getSizeLevel(int sizeBlocks) {
        return Math.max(0, Math.min(18, sizeBlocks / 4));
    }

    public static int getWaterLevel(int waterMbTick) {
        return Math.max(0, Math.min(18, waterMbTick / 10));
    }

    public static int calculateEffectiveLevel(int sizeBlocks, int heatLevel, int waterMbTick) {
        int sizeLvl = getSizeLevel(sizeBlocks);
        int waterLvl = getWaterLevel(waterMbTick);
        if (sizeLvl < 1 || waterLvl < 1) {
            return -1;
        }
        if (heatLevel <= 0) {
            return 0;
        }
        return Math.min(sizeLvl, Math.min(waterLvl, heatLevel));
    }

    public static BoilerBottleneck getBottleneck(int sizeBlocks, int heatLevel, int waterMbTick) {
        int sizeLvl = getSizeLevel(sizeBlocks);
        int waterLvl = getWaterLevel(waterMbTick);
        if (sizeLvl < 1 || waterLvl < 1) return BoilerBottleneck.INACTIVE;
        if (heatLevel <= 0) return BoilerBottleneck.NONE;
        int eff = Math.min(sizeLvl, Math.min(waterLvl, heatLevel));
        if (sizeLvl == eff && (waterLvl > eff || heatLevel > eff)) return BoilerBottleneck.SIZE;
        if (waterLvl == eff && (sizeLvl > eff || heatLevel > eff)) return BoilerBottleneck.WATER;
        if (heatLevel == eff && (sizeLvl > eff || waterLvl > eff)) return BoilerBottleneck.HEAT;
        return BoilerBottleneck.NONE;
    }

    public static void recalculateAndApplyBoiler(com.gtceu.calcboard.api.model.RecipeNode node) {
        if (node == null) return;
        int size = node.getProperties().get(BOILER_SIZE_BLOCKS);
        int heat = node.getProperties().get(BOILER_HEAT_LEVEL);
        int water = node.getProperties().get(BOILER_WATER_MB_TICK);

        int eff = calculateEffectiveLevel(size, heat, water);
        if (eff < 0) {
            node.getProperties().set(BOILER_LEVEL, 0);
            node.setBaseEUt(0.0);
            node.setRpm(0);
            node.getProperties().set(BASE_GENERATOR_RPM, 0);
            syncBoilerOutput(node, 0.0);
            syncBoilerInput(node, 0);
            return;
        }

        node.getProperties().set(BOILER_LEVEL, eff);
        double totalSu = eff == 0 ? 2048.0 : (eff * 16384.0);
        int rpm = eff == 0 ? 16 : 64;

        node.setBaseEUt(totalSu);
        node.setRpm(rpm);
        node.getProperties().set(BASE_GENERATOR_RPM, rpm);

        syncBoilerOutput(node, totalSu);
        syncBoilerInput(node, eff);
    }

    public static void applyBoilerLevel(com.gtceu.calcboard.api.model.RecipeNode node, int level) {
        if (node == null) return;
        int clamped = Math.max(0, Math.min(18, level));
        int targetSize = clamped == 0 ? 4 : (clamped * 4);
        int targetWater = clamped == 0 ? 10 : (clamped * 10);
        node.getProperties().set(BOILER_SIZE_BLOCKS, targetSize);
        node.getProperties().set(BOILER_HEAT_LEVEL, clamped);
        node.getProperties().set(BOILER_WATER_MB_TICK, targetWater);
        recalculateAndApplyBoiler(node);
    }

    public static void setBoilerSize(com.gtceu.calcboard.api.model.RecipeNode node, int sizeBlocks) {
        if (node == null) return;
        node.getProperties().set(BOILER_SIZE_BLOCKS, Math.max(4, Math.min(72, sizeBlocks)));
        recalculateAndApplyBoiler(node);
    }

    public static void setBoilerHeat(com.gtceu.calcboard.api.model.RecipeNode node, int heatLevel) {
        if (node == null) return;
        node.getProperties().set(BOILER_HEAT_LEVEL, Math.max(0, Math.min(18, heatLevel)));
        recalculateAndApplyBoiler(node);
    }

    public static void setBoilerWater(com.gtceu.calcboard.api.model.RecipeNode node, int waterMbTick) {
        if (node == null) return;
        node.getProperties().set(BOILER_WATER_MB_TICK, Math.max(10, Math.min(180, waterMbTick)));
        recalculateAndApplyBoiler(node);
    }

    private static void syncBoilerOutput(com.gtceu.calcboard.api.model.RecipeNode node, double totalSu) {
        if (node.getOutputs().isEmpty()) {
            node.addOutput(com.gtceu.calcboard.api.model.IngredientStack.stressUnit(totalSu));
        } else {
            node.getOutputs().set(0, com.gtceu.calcboard.api.model.IngredientStack.stressUnit(totalSu));
        }
    }

    private static void syncBoilerInput(com.gtceu.calcboard.api.model.RecipeNode node, int level) {
        double rate = level == 0 ? 200.0 : (level * 200.0);
        updateOrAddFluidInput(node, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), "Water", rate);
    }

    private static void updateOrAddFluidInput(com.gtceu.calcboard.api.model.RecipeNode node, net.minecraft.resources.ResourceLocation id, String name, double amount) {
        com.gtceu.calcboard.api.model.IngredientStack stack = com.gtceu.calcboard.api.model.IngredientStack.fluid(id, name, amount, 1.0);
        if (node.getInputs().isEmpty()) {
            node.addInput(stack);
        } else {
            node.getInputs().set(0, stack);
        }
    }

    public static void cycleBoilerLevel(com.gtceu.calcboard.api.model.RecipeNode node, int direction) {
        if (node == null) return;
        int current = node.getProperties().get(BOILER_LEVEL);
        int next = current + direction;
        if (next < 0) next = 18;
        else if (next > 18) next = 0;
        applyBoilerLevel(node, next);
    }

    public static void toggleBoilerFluidMode(com.gtceu.calcboard.api.model.RecipeNode node) {
        if (node == null) return;
        node.getProperties().set(BOILER_WATER_MODE, true);
        recalculateAndApplyBoiler(node);
    }

    public static int calculateTotalHeatFromAddons(com.gtceu.calcboard.api.model.RecipeNode node) {
        if (node == null) return 0;
        int heat = 0;
        for (com.gtceu.calcboard.api.catalog.MachineAddon addon : node.getAddons()) {
            if (addon instanceof com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon heater) {
                heat += heater.getHeatLevel();
            }
        }
        return Math.min(18, heat);
    }

    public static int getNextRpm(int currentRpm, int direction) {
        int closestIdx = 3;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < STANDARD_RPMS.length; i++) {
            int diff = Math.abs(STANDARD_RPMS[i] - currentRpm);
            if (diff < minDiff) {
                minDiff = diff;
                closestIdx = i;
            }
        }
        int nextIdx = closestIdx + direction;
        if (nextIdx < 0) nextIdx = STANDARD_RPMS.length - 1;
        else if (nextIdx >= STANDARD_RPMS.length) nextIdx = 0;
        return STANDARD_RPMS[nextIdx];
    }

    public static void cycleRpm(com.gtceu.calcboard.api.model.RecipeNode node, int direction) {
        if (node == null) return;
        node.setRpm(getNextRpm(node.getRpm(), direction));
    }

    public static boolean isWindmill(com.gtceu.calcboard.api.model.RecipeNode node) {
        if (node == null || !node.isGenerator()) return false;
        if (node.getProperties().has(WINDMILL_SAILS)) return true;
        return node.getMachineIcon() != null && "create".equals(node.getMachineIcon().getNamespace())
                && "windmill_bearing".equals(node.getMachineIcon().getPath());
    }

    public static void applyWindmillSails(com.gtceu.calcboard.api.model.RecipeNode node, int sails) {
        if (node == null) return;
        int clamped = Math.max(8, Math.min(128, sails));
        node.getProperties().set(WINDMILL_SAILS, clamped);
        CreateStressHelper.KineticStats stats = CreateStressHelper.calculateWindmillStats(clamped);
        node.setRpm(stats.rpm());
        node.getProperties().set(BASE_GENERATOR_RPM, stats.rpm());
        node.setBaseEUt(stats.totalSu());
        node.getOutputs().clear();
        node.addOutput(com.gtceu.calcboard.api.model.IngredientStack.stressUnit(stats.totalSu()));
    }
}
