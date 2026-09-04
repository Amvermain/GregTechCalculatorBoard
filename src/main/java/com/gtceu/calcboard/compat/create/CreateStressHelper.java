package com.gtceu.calcboard.compat.create;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CreateStressHelper {

    public record KineticStats(double capacityPerRpm, int rpm, double totalSu, int greateTier) {}

    public record DiscoveredGenerator(
            ResourceLocation id,
            Block block,
            double capacity,
            int rpm,
            double totalSu,
            int greateTier,
            boolean isSteamEngine
    ) {}

    private static List<DiscoveredGenerator> cachedGenerators = null;

    private static final Method GET_CAPACITY_METHOD;
    private static final Method GET_IMPACT_METHOD;
    private static final Object RPM_REGISTRY;
    private static final Method REGISTRY_GET_METHOD;
    private static final Method RPM_VALUE_METHOD;

    private static final Class<?> TIERED_BLOCK_CLASS;
    private static final Method GET_TIER_METHOD;
    private static final List<Class<?>> STEAM_ENGINE_CLASSES;

    static {
        Method getCap = null;
        Method getImp = null;
        Object rpmReg = null;
        Method regGet = null;
        Method rpmVal = null;
        try {
            Class<?> bsvClass = Class.forName("com.simibubi.create.api.stress.BlockStressValues");
            getCap = bsvClass.getMethod("getCapacity", Block.class);
            getImp = bsvClass.getMethod("getImpact", Block.class);

            Field rpmField = bsvClass.getField("RPM");
            rpmReg = rpmField.get(null);

            Class<?> registryClass = Class.forName("com.simibubi.create.api.registry.SimpleRegistry");
            regGet = registryClass.getMethod("get", Object.class);

            Class<?> genRpmClass = Class.forName("com.simibubi.create.api.stress.BlockStressValues$GeneratedRpm");
            rpmVal = genRpmClass.getMethod("value");
        } catch (Throwable ignored) {
        }
        GET_CAPACITY_METHOD = getCap;
        GET_IMPACT_METHOD = getImp;
        RPM_REGISTRY = rpmReg;
        REGISTRY_GET_METHOD = regGet;
        RPM_VALUE_METHOD = rpmVal;

        Class<?> tbClass = null;
        Method getTier = null;
        try {
            tbClass = Class.forName("electrolyte.greate.content.kinetics.simpleRelays.ITieredBlock");
            getTier = tbClass.getMethod("getTier");
        } catch (Throwable ignored) {
        }
        TIERED_BLOCK_CLASS = tbClass;
        GET_TIER_METHOD = getTier;

        List<Class<?>> steamClasses = new ArrayList<>();
        String[] classNames = {
                "com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock",
                "com.teammoeg.steampowered.content.engine.SteamEngineBlock",
                "com.teammoeg.steampowered.content.engine.SteamEngineFlywheelBlock"
        };
        for (String name : classNames) {
            try {
                steamClasses.add(Class.forName(name));
            } catch (Throwable ignored) {
            }
        }
        STEAM_ENGINE_CLASSES = Collections.unmodifiableList(steamClasses);
    }

    private CreateStressHelper() {}

    @SuppressWarnings("deprecation")
    public static Block findBlock(ResourceLocation id) {
        if (id == null) return null;
        try {
            if (BuiltInRegistries.BLOCK != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                return BuiltInRegistries.BLOCK.get(id);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static double getCapacity(Block block, double fallback) {
        if (block == null || GET_CAPACITY_METHOD == null) return fallback;
        try {
            double cap = (double) GET_CAPACITY_METHOD.invoke(null, block);
            return cap > 0.0 ? cap : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static double getImpact(Block block, double fallback) {
        if (block == null || GET_IMPACT_METHOD == null) return fallback;
        try {
            double impact = (double) GET_IMPACT_METHOD.invoke(null, block);
            return impact > 0.0 ? impact : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static int getGeneratedRpm(Block block, int fallback) {
        if (block == null || RPM_REGISTRY == null || REGISTRY_GET_METHOD == null || RPM_VALUE_METHOD == null) {
            return fallback;
        }
        try {
            Object rpmObj = REGISTRY_GET_METHOD.invoke(RPM_REGISTRY, block);
            if (rpmObj != null) {
                return (int) RPM_VALUE_METHOD.invoke(rpmObj);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static int getGreateTier(Block block) {
        if (block == null || TIERED_BLOCK_CLASS == null || GET_TIER_METHOD == null) return -1;
        if (!TIERED_BLOCK_CLASS.isInstance(block)) return -1;
        try {
            return (int) GET_TIER_METHOD.invoke(block);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static double calculateSteamConsumption(int tier) {
        if (tier < 0) {
            return 320.0;
        }
        if (tier == 0) {
            return 80.0;
        }
        return 16.0 * Math.pow(4, tier - 1) * 20.0;
    }

    public static double calculateSteamEngineTotalSu(int tier) {
        if (tier < 0) {
            return 2048.0;
        }
        if (tier == 0) {
            return 32.0;
        }
        return 128.0 * Math.pow(4, tier - 1);
    }

    public static KineticStats deduceGeneratorStats(ResourceLocation itemId, double fallbackCapacityPerRpm, int fallbackRpm) {
        Block block = findBlock(itemId);
        boolean isSteam = isSteamEngine(block);
        int tier = getGreateTier(block);
        if (isSteam) {
            double totalSu = calculateSteamEngineTotalSu(tier);
            int rpm = (tier < 0) ? 64 : 16;
            double cap = totalSu / rpm;
            return new KineticStats(cap, rpm, totalSu, tier);
        }
        double cap = getCapacity(block, fallbackCapacityPerRpm);
        int rpm = getGeneratedRpm(block, fallbackRpm);
        double totalSu = cap * rpm;
        return new KineticStats(cap, rpm, totalSu, tier);
    }

    public static boolean isSteamEngine(Block block) {
        if (block == null || STEAM_ENGINE_CLASSES.isEmpty()) return false;
        for (Class<?> cls : STEAM_ENGINE_CLASSES) {
            if (cls.isInstance(block)) return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    public static synchronized List<DiscoveredGenerator> discoverGenerators() {
        if (cachedGenerators != null) {
            return cachedGenerators;
        }
        List<DiscoveredGenerator> list = new ArrayList<>();
        if (BuiltInRegistries.BLOCK == null) {
            cachedGenerators = Collections.emptyList();
            return cachedGenerators;
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.asItem() == net.minecraft.world.item.Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;

            boolean isSteam = isSteamEngine(block);
            int tier = getGreateTier(block);

            if (isSteam) {
                double totalSu = calculateSteamEngineTotalSu(tier);
                int rpm = (tier < 0) ? 64 : 16;
                double cap = totalSu / rpm;
                list.add(new DiscoveredGenerator(id, block, cap, rpm, totalSu, tier, true));
                continue;
            }

            double cap = getCapacity(block, 0.0);
            if (cap <= 0.0) continue;

            int rpm = getGeneratedRpm(block, 16);
            double totalSu = cap * rpm;
            list.add(new DiscoveredGenerator(id, block, cap, rpm, totalSu, tier, false));
        }
        cachedGenerators = Collections.unmodifiableList(list);
        return cachedGenerators;
    }
}
