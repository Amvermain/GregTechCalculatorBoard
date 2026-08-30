package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for GTCEu Modern heating coils.
 * Deductively extracts coil stats and temperature via direct CoilBlock and ICoilType API interaction.
 */
public class CoilHelper {

    private static final Map<String, CoilStats> STATS_CACHE = new ConcurrentHashMap<>();

    static {
        STATS_CACHE.put("gtceu:cupronickel_coil_block", new CoilStats(1800, 100, 100, 100, 100, 16));
        STATS_CACHE.put("gtceu:kanthal_coil_block", new CoilStats(2700, 100, 90, 125, 95, 32));
        STATS_CACHE.put("gtceu:nichrome_coil_block", new CoilStats(3600, 150, 80, 150, 90, 64));
        STATS_CACHE.put("gtceu:rtm_alloy_coil_block", new CoilStats(4500, 200, 70, 175, 85, 128));
        STATS_CACHE.put("gtceu:hssg_coil_block", new CoilStats(5400, 250, 60, 200, 80, 256));
        STATS_CACHE.put("gtceu:naquadah_coil_block", new CoilStats(7200, 300, 50, 225, 75, 2048));
        STATS_CACHE.put("gtceu:trinium_coil_block", new CoilStats(9001, 350, 40, 250, 70, 4096));
        STATS_CACHE.put("gtceu:tritanium_coil_block", new CoilStats(10800, 400, 30, 275, 65, 8192));
    }

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

    public static boolean isHeatingCoil(ResourceLocation id) {
        if (id == null) return false;
        CoilStats stats = getCoilStats(id.toString());
        return stats != null && stats.temperature() > 0;
    }

    public static boolean isHeatingCoil(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CoilStats stats = getCoilStats(stack);
        return stats != null && stats.temperature() > 0;
    }

    public static boolean isHeatingCoil(Block block) {
        if (block == null) return false;
        CoilStats stats = getCoilStats(block);
        return stats != null && stats.temperature() > 0;
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
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
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
                Block block = ForgeRegistries.BLOCKS.getValue(id);
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
            return null;
        }

        int heat = extractInt(coilTypeObj, "getCoilTemperature", "getTemperature");
        if (heat <= 0) {
            return null;
        }

        int rawTier = extractInt(coilTypeObj, "getTier");
        int rawLevel = extractInt(coilTypeObj, "getLevel");
        int tier = (rawTier >= 0) ? rawTier : Math.max(0, (heat - 1800) / 900);
        int level = (rawLevel > 0) ? rawLevel : (tier == 0 ? 1 : Math.max(1, tier));

        // 1. Pyrolyse Oven: tier == 0 ? 75 : 50 * (tier + 1)
        int pyroSpeed = (tier == 0) ? 75 : (50 * (tier + 1));

        // 2. Cracker: discount = tier <= 9 ? tier * 0.1 : 0.9 + (tier - 9) * 0.025
        double crackDiscount = tier <= 9 ? (tier * 0.1) : (0.9 + (tier - 9) * 0.025);
        int crackEnergyPercent = Math.max(1, (int) Math.floor((1.0 - crackDiscount) * 100.0 + 0.0001));

        // 3. Chemical Reactor: speed = 75 + tier * 25, energy = max(50, 100 - tier * 5)
        int chemSpeed = 75 + (tier * 25);
        int chemEnergy = Math.max(50, 100 - (tier * 5));

        // 4. Multi Smelter: parallel = tier-based exponential scaling (Tier 0: 16, Tier 1: 32, Tier 2: 64, Tier 3: 128, ...)
        int rawSmelterPar = extractInt(coilTypeObj, "getSmelterParallel", "getSmelterMaxParallel", "getMaxParallel", "getParallelMultiplier");
        int smelterPar = (rawSmelterPar > 0) ? rawSmelterPar : (int) (16 * Math.pow(2, Math.max(0, tier)));

        return new CoilStats(heat, pyroSpeed, crackEnergyPercent, chemSpeed, chemEnergy, smelterPar);
    }

    private static Object resolveCoilTypeObject(Object obj) {
        if (obj == null) return null;

        for (Class<?> iface : obj.getClass().getInterfaces()) {
            if (iface.getName().endsWith("ICoilType") || iface.getSimpleName().equals("ICoilType")) {
                return obj;
            }
        }

        try {
            Method m = obj.getClass().getMethod("getCoilTemperature");
            if (m.getParameterCount() == 0) return obj;
        } catch (Throwable ignored) {}

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

    public static GTCoilAddon parseCoilBlock(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) return null;

        CoilStats stats = getCoilStats(stack);
        if (stats == null || stats.temperature() <= 0) {
            return null;
        }

        String name = stack.getHoverName().getString();
        String desc = String.format("Heat: %d K\nSmelter: %dx Par\nPyrolyse: %d%% Spd\nCracking: %d%% Energy\nChemical Reactor: %d%% Spd, %d%% Energy",
                stats.temperature(), stats.smelterParallel(), stats.pyrolyseSpeedPercent(), stats.crackingEnergyPercent(), stats.chemicalSpeedPercent(), stats.chemicalEnergyPercent());

        GTCoilAddon addon = new GTCoilAddon(id.toString(), name.isEmpty() ? id.toString() : name, desc, id, stats);
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu ICoilType Block API [" + id + "]");
        return addon;
    }

    public static void discoverGTCEuCoils(java.util.List<MachineAddon> list) {
        try {
            for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : ForgeRegistries.BLOCKS.getEntries()) {
                ResourceLocation id = entry.getKey().location();
                if (id == null) continue;
                String path = id.getPath().toLowerCase(Locale.ROOT);
                if (!path.contains("coil")) continue;

                Block block = entry.getValue();
                CoilStats stats = extractStatsFromBlockObject(block);
                if (stats != null && stats.temperature() > 0) {
                    ItemStack stack = new ItemStack(block.asItem());
                    String displayName = !stack.isEmpty() ? stack.getHoverName().getString() : block.getName().getString();

                    String desc = String.format("Heat: %d K\nSmelter: %dx Par\nPyrolyse: %d%% Spd\nCracking: %d%% Energy\nChemical Reactor: %d%% Spd, %d%% Energy",
                            stats.temperature(), stats.smelterParallel(), stats.pyrolyseSpeedPercent(), stats.crackingEnergyPercent(), stats.chemicalSpeedPercent(), stats.chemicalEnergyPercent());

                    GTCoilAddon addon = new GTCoilAddon(id.toString(), displayName, desc, id, stats);
                    if (!stack.isEmpty()) {
                        addon.setItemStackSample(stack);
                    }
                    addon.setDiscoverySource("GTCEu ICoilType Block API [" + id + "]");

                    if (list.stream().noneMatch(a -> a.getId().equals(addon.getId()))) {
                        list.add(addon);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static MachineAddon tailorCoilAddon(MachineAddon source, RecipeNode node) {
        if (source == null || source.getCategory() != MachineAddon.Category.COIL) {
            return source != null ? source.copy() : null;
        }
        MachineAddon cp = source.copy();
        if (node == null) return cp;

        GTCEuCoilModifierHelper.applyCoilModifiers(node, cp);
        return cp;
    }
}

