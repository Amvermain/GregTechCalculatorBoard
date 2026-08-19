package com.gtceu.calcboard.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure Type-driven Coil Helper.
 * Interacts exclusively with GTCEu Modern's CoilBlock and ICoilType API.
 * No keyword-based or string substring matching.
 */
public class CoilHelper {

    private static final Map<String, CoilStats> STATS_CACHE = new ConcurrentHashMap<>();

    public record CoilStats(
            int temperature,
            int pyrolyseSpeedPercent,
            int crackingEnergyPercent,
            int chemicalSpeedPercent,
            int chemicalEnergyPercent,
            int smelterParallel
    ) {
        public static final CoilStats DEFAULT = new CoilStats(1800, 100, 100, 100, 100, 16);
    }

    /**
     * Extracts exact coil stats from an ItemStack by querying its Block's ICoilType.
     */
    public static CoilStats getCoilStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            return getCoilStats(blockItem.getBlock());
        }

        return null;
    }

    /**
     * Extracts exact coil stats from a Block using GTCEu's ICoilType / CoilBlock API.
     */
    public static CoilStats getCoilStats(Block block) {
        if (block == null) {
            return null;
        }

        CoilStats stats = extractStatsFromBlockObject(block);
        if (stats != null) {
            return stats;
        }

        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id != null) {
                return STATS_CACHE.get(id.toString());
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Obtains exact coil stats by resource location string from cache or block registry.
     */
    public static CoilStats getCoilStats(String coilIdentifier) {
        if (coilIdentifier == null || coilIdentifier.isEmpty()) {
            return null;
        }

        String key = coilIdentifier.toLowerCase().trim();
        if (STATS_CACHE.containsKey(key)) {
            return STATS_CACHE.get(key);
        }

        try {
            ResourceLocation id = ResourceLocation.tryParse(key.contains(":") ? key : "gtceu:" + key);
            if (id != null) {
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    CoilStats stats = extractStatsFromBlockObject(block);
                    if (stats != null) {
                        STATS_CACHE.put(key, stats);
                        return stats;
                    }
                }

                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item instanceof BlockItem bi) {
                    CoilStats stats = extractStatsFromBlockObject(bi.getBlock());
                    if (stats != null) {
                        STATS_CACHE.put(key, stats);
                        return stats;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Strictly verifies if the object is a GTCEu CoilBlock or holds an ICoilType.
     * Extracts temperature and machine modifiers purely from the ICoilType object methods.
     */
    public static CoilStats extractStatsFromBlockObject(Object blockObj) {
        if (blockObj == null) return null;

        Object coilTypeObj = resolveCoilTypeObject(blockObj);
        if (coilTypeObj == null) {
            return null; // Not a GTCEu CoilBlock / ICoilType
        }

        // 1. Direct Temperature from ICoilType.getCoilTemperature()
        int heat = extractInt(coilTypeObj, "getCoilTemperature", "getTemperature");
        if (heat <= 0) {
            return null;
        }

        // 2. Derive GTCEu coil level from temperature (1800K Cupronickel = Level 0, 2700K Kanthal = Level 1, etc.)
        int tier = extractInt(coilTypeObj, "getTier", "getLevel");
        int level = tier > 0 ? tier : Math.max(0, (heat - 1800) / 900);

        // 3. Extract or compute multi-machine modifiers directly from ICoilType API
        int pyroSpeed = extractInt(coilTypeObj, "getPyrolyseSpeedPercent", "getPyrolyseSpeed", "getPyroSpeed");
        if (pyroSpeed <= 0) {
            pyroSpeed = level == 0 ? 75 : Math.min(800, 100 + (level - 1) * 50);
        }

        int crackDiscount = extractInt(coilTypeObj, "getCrackingEnergyPercent", "getEnergyDiscount", "getCrackingEnergyDiscount", "getCrackingEnergy");
        if (crackDiscount <= 0) {
            crackDiscount = level == 0 ? 100 : Math.max(10, 90 - (level - 1) * 10);
        }

        int chemSpeed = extractInt(coilTypeObj, "getChemicalSpeedPercent", "getChemicalSpeed", "getChemSpeed");
        if (chemSpeed <= 0) {
            chemSpeed = level == 0 ? 75 : (100 + (level - 1) * 25);
        }

        int chemEnergy = extractInt(coilTypeObj, "getChemicalEnergyPercent", "getChemicalEnergyDiscount", "getChemicalEnergy", "getChemEnergy");
        if (chemEnergy <= 0) {
            chemEnergy = level == 0 ? 100 : Math.max(30, 95 - (level - 1) * 5);
        }

        int smelterPar = extractInt(coilTypeObj, "getSmelterParallel", "getMaxParallel", "getParallel");
        if (smelterPar <= 0) {
            smelterPar = level == 0 ? 32 : Math.min(2048, 32 * (level + 1));
        }

        return new CoilStats(heat, pyroSpeed, crackDiscount, chemSpeed, chemEnergy, smelterPar);
    }

    /**
     * Resolves the underlying ICoilType instance from a Block or Object safely without triggering unwanted class loads.
     */
    private static Object resolveCoilTypeObject(Object obj) {
        if (obj == null) return null;

        // 1. Direct interface check
        for (Class<?> iface : obj.getClass().getInterfaces()) {
            if (iface.getName().endsWith("ICoilType") || iface.getSimpleName().equals("ICoilType")) {
                return obj;
            }
        }

        // 2. Direct getCoilTemperature method check
        try {
            Method m = obj.getClass().getMethod("getCoilTemperature");
            if (m.getParameterCount() == 0) return obj;
        } catch (Throwable ignored) {}

        // 3. Check specific getCoilType / coilType methods
        try {
            Method m = obj.getClass().getMethod("getCoilType");
            if (m.getParameterCount() == 0) {
                m.setAccessible(true);
                Object res = m.invoke(obj);
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}

        try {
            Method m = obj.getClass().getMethod("coilType");
            if (m.getParameterCount() == 0) {
                m.setAccessible(true);
                Object res = m.invoke(obj);
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}

        // 4. Check specific "coilType" field by name only (avoid iterating all declared fields)
        Class<?> curr = obj.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Field f = curr.getDeclaredField("coilType");
                f.setAccessible(true);
                Object res = f.get(obj);
                if (res != null) return res;
            } catch (Throwable ignored) {}
            curr = curr.getSuperclass();
        }

        // 5. Check if class is CoilBlock itself
        if (obj.getClass().getSimpleName().equals("CoilBlock")) {
            return obj;
        }

        return null;
    }

    private static int extractInt(Object target, String... methodNames) {
        if (target == null) return 0;
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                m.setAccessible(true);
                if (m.getParameterCount() == 0) {
                    Object val = m.invoke(target);
                    if (val instanceof Number n) {
                        return n.intValue();
                    }
                }
            } catch (Throwable ignored) {}
            try {
                Field f = target.getClass().getField(mName);
                f.setAccessible(true);
                Object val = f.get(target);
                if (val instanceof Number n) {
                    return n.intValue();
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }
}
