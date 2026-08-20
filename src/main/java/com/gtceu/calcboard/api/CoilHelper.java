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
            return null;
        }

        int heat = extractInt(coilTypeObj, "getCoilTemperature", "getTemperature");
        if (heat <= 0) {
            return null;
        }

        int tier = extractInt(coilTypeObj, "getTier", "getLevel");
        int level = tier > 0 ? tier : Math.max(0, (heat - 1800) / 900);

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

    public static MachineAddon parseCoilBlock(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) return null;

        CoilStats stats = getCoilStats(stack);
        if (stats == null || stats.temperature() <= 0) {
            return null;
        }

        String name = stack.getHoverName().getString();
        String desc = String.format("♨ Heat: %d K\n• Smelter: %dx Par\n• Pyrolyse: %d%% Spd\n• Cracking: %d%% Energy\n• Chemical Reactor: %d%% Spd, %d%% Energy",
                stats.temperature(), stats.smelterParallel(), stats.pyrolyseSpeedPercent(), stats.crackingEnergyPercent(), stats.chemicalSpeedPercent(), stats.chemicalEnergyPercent());

        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.toString() : name, MachineAddon.Category.COIL, desc, id);
        addon.setItemStackSample(stack);
        addon.setCoilTemperature(stats.temperature());
        addon.setPyrolyseSpeedPercent(stats.pyrolyseSpeedPercent());
        addon.setCrackingEnergyPercent(stats.crackingEnergyPercent());
        addon.setChemicalSpeedPercent(stats.chemicalSpeedPercent());
        addon.setChemicalEnergyPercent(stats.chemicalEnergyPercent());
        addon.setSmelterParallel(stats.smelterParallel());
        return addon;
    }

    public static void discoverGTCEuCoils(java.util.List<MachineAddon> list) {
        try {
            for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
                Block block = entry.getValue();
                ResourceLocation id = entry.getKey().location();

                CoilStats stats = extractStatsFromBlockObject(block);
                if (stats != null && stats.temperature() > 0) {
                    ItemStack stack = new ItemStack(block.asItem());
                    String displayName = !stack.isEmpty() ? stack.getHoverName().getString() : block.getName().getString();

                    String desc = String.format("♨ Heat: %d K\n• Smelter: %dx Par\n• Pyrolyse: %d%% Spd\n• Cracking: %d%% Energy\n• Chemical Reactor: %d%% Spd, %d%% Energy",
                            stats.temperature(), stats.smelterParallel(), stats.pyrolyseSpeedPercent(), stats.crackingEnergyPercent(), stats.chemicalSpeedPercent(), stats.chemicalEnergyPercent());

                    MachineAddon addon = new MachineAddon(id.toString(), displayName, MachineAddon.Category.COIL, desc, id);
                    if (!stack.isEmpty()) {
                        addon.setItemStackSample(stack);
                    }
                    addon.setCoilTemperature(stats.temperature());
                    addon.setPyrolyseSpeedPercent(stats.pyrolyseSpeedPercent());
                    addon.setCrackingEnergyPercent(stats.crackingEnergyPercent());
                    addon.setChemicalSpeedPercent(stats.chemicalSpeedPercent());
                    addon.setChemicalEnergyPercent(stats.chemicalEnergyPercent());
                    addon.setSmelterParallel(stats.smelterParallel());

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

        int reqTemp = node.getRecipeTemperature();
        ResourceLocation catId = node.getRecipeCategoryId();
        java.util.List<ResourceLocation> wsList = node.getAvailableWorkstations();
        ResourceLocation icon = node.getMachineIcon();

        // 1. EBF (Electric Blast Furnace): 5% EU discount per 900K excess temperature above requirement
        if (reqTemp > 0 || (catId != null && catId.equals(ResourceLocation.tryParse("gtceu:electric_blast_furnace")))) {
            if (reqTemp > 0 && source.getCoilTemperature() > reqTemp) {
                int excessTemp = source.getCoilTemperature() - reqTemp;
                int tiersAbove = excessTemp / 900;
                cp.setEutMultiplier(Math.pow(0.95, tiersAbove));
            } else {
                cp.setEutMultiplier(1.0);
            }
            cp.setDurationMultiplier(1.0);
            cp.setParallelMultiplier(1);
            return cp;
        }

        // 2. Pyrolyse Oven: Speed % -> Duration (100 / Speed)
        if ((catId != null && catId.equals(ResourceLocation.tryParse("gtceu:pyrolyse_oven")))
                || (icon != null && icon.equals(ResourceLocation.tryParse("gtceu:pyrolyse_oven")))
                || wsList.contains(ResourceLocation.tryParse("gtceu:pyrolyse_oven"))) {
            cp.setDurationMultiplier(100.0 / Math.max(1, source.getPyrolyseSpeedPercent()));
            cp.setEutMultiplier(1.0);
            cp.setParallelMultiplier(1);
            return cp;
        }

        // 3. Cracking Unit: Energy % -> EU/t (Energy / 100)
        if ((catId != null && (catId.equals(ResourceLocation.tryParse("gtceu:cracker")) || catId.equals(ResourceLocation.tryParse("gtceu:cracking_unit"))))
                || (icon != null && (icon.equals(ResourceLocation.tryParse("gtceu:cracker")) || icon.equals(ResourceLocation.tryParse("gtceu:cracking_unit"))))
                || wsList.contains(ResourceLocation.tryParse("gtceu:cracker")) || wsList.contains(ResourceLocation.tryParse("gtceu:cracking_unit"))) {
            cp.setDurationMultiplier(1.0);
            cp.setEutMultiplier(source.getCrackingEnergyPercent() / 100.0);
            cp.setParallelMultiplier(1);
            return cp;
        }

        // 4. Large Chemical Reactor / Chemical Reactor: Speed %, Energy %
        if ((catId != null && (catId.equals(ResourceLocation.tryParse("gtceu:large_chemical_reactor")) || catId.equals(ResourceLocation.tryParse("gtceu:chemical_reactor"))))
                || (icon != null && (icon.equals(ResourceLocation.tryParse("gtceu:large_chemical_reactor")) || icon.equals(ResourceLocation.tryParse("gtceu:chemical_reactor"))))
                || wsList.contains(ResourceLocation.tryParse("gtceu:large_chemical_reactor")) || wsList.contains(ResourceLocation.tryParse("gtceu:chemical_reactor"))) {
            cp.setDurationMultiplier(100.0 / Math.max(1, source.getChemicalSpeedPercent()));
            cp.setEutMultiplier(source.getChemicalEnergyPercent() / 100.0);
            cp.setParallelMultiplier(1);
            return cp;
        }

        // 5. Multi Smelter / Alloy Smelter: Max Parallel
        if ((catId != null && (catId.equals(ResourceLocation.tryParse("gtceu:multi_smelter")) || catId.equals(ResourceLocation.tryParse("gtceu:alloy_smelter")) || catId.equals(ResourceLocation.tryParse("minecraft:smelting")) || catId.equals(ResourceLocation.tryParse("minecraft:blasting"))))
                || (icon != null && (icon.equals(ResourceLocation.tryParse("gtceu:multi_smelter")) || icon.equals(ResourceLocation.tryParse("gtceu:alloy_smelter"))))
                || wsList.contains(ResourceLocation.tryParse("gtceu:multi_smelter")) || wsList.contains(ResourceLocation.tryParse("gtceu:alloy_smelter"))) {
            cp.setParallelMultiplier(Math.max(1, source.getSmelterParallel()));
            cp.setDurationMultiplier(1.0);
            cp.setEutMultiplier(1.0);
            return cp;
        }

        cp.setDurationMultiplier(1.0);
        cp.setEutMultiplier(1.0);
        cp.setParallelMultiplier(1);
        return cp;
    }
}
