package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Helper class for analyzing and extracting Fusion Reflector specs across GTCEu Modern, GCyM, and Star Technology.
 */
public class ReflectorHelper {

    /**
     * Reflector specification record containing tier level.
     */
    public record ReflectorStats(int tier) {
        public static final ReflectorStats DEFAULT = new ReflectorStats(1);
    }

    public static final TagKey<Item> TAG_GT_REFLECTORS = TagKey.create(Registries.ITEM, ResourceLocation.tryParse("gtceu:fusion_reflectors"));
    public static final TagKey<Item> TAG_FORGE_REFLECTORS = TagKey.create(Registries.ITEM, ResourceLocation.tryParse("forge:fusion_reflectors"));
    public static final TagKey<Item> TAG_START_REFLECTORS = TagKey.create(Registries.ITEM, ResourceLocation.tryParse("start_core:fusion_reflectors"));

    /**
     * Extracts reflector specs from an ItemStack via NBT tags or runtime reflection.
     */
    public static ReflectorStats getReflectorStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        if (stack.hasTag()) {
            var tag = stack.getTag();
            if (tag != null) {
                if (tag.contains("ReflectorTier")) return new ReflectorStats(tag.getInt("ReflectorTier"));
                if (tag.contains("reflector_tier")) return new ReflectorStats(tag.getInt("reflector_tier"));
                if (tag.contains("Tier")) return new ReflectorStats(tag.getInt("Tier"));
            }
        }

        Item item = stack.getItem();
        ReflectorStats stats = inspectReflectorObject(item, 0);
        if (stats != null) return stats;

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block != null) {
                stats = inspectReflectorObject(block, 0);
                if (stats != null) return stats;
            }
        }

        return null;
    }

    /**
     * Recursively inspects object methods and fields to deductively extract reflector tier.
     */
    private static ReflectorStats inspectReflectorObject(Object obj, int depth) {
        if (obj == null || depth > 2) return null;
        Class<?> clazz = obj.getClass();

        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0) {
                String name = m.getName().toLowerCase();
                if (name.equals("getreflectortier") || name.equals("gettier") || name.equals("getfusionreflectortier") || name.equals("reflectortier")) {
                    try {
                        Object res = m.invoke(obj);
                        if (res instanceof Number num && num.intValue() > 0) {
                            return new ReflectorStats(num.intValue());
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        for (Field f : clazz.getFields()) {
            String name = f.getName().toLowerCase();
            if (name.equals("reflectortier") || name.equals("tier") || name.equals("fusionreflectortier")) {
                try {
                    Object res = f.get(obj);
                    if (res instanceof Number num && num.intValue() > 0) {
                        return new ReflectorStats(num.intValue());
                    }
                } catch (Throwable ignored) {}
            }
            if (depth < 2 && (name.contains("reflector") || name.contains("type"))) {
                try {
                    Object fieldObj = f.get(obj);
                    if (fieldObj != null && fieldObj != obj) {
                        ReflectorStats nested = inspectReflectorObject(fieldObj, depth + 1);
                        if (nested != null) return nested;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (depth < 2) {
            for (Method m : clazz.getMethods()) {
                if (m.getParameterCount() == 0) {
                    String name = m.getName().toLowerCase();
                    if (name.equals("getreflectortype") || name.equals("getreflector")) {
                        try {
                            Object res = m.invoke(obj);
                            if (res != null && res != obj) {
                                ReflectorStats nested = inspectReflectorObject(res, depth + 1);
                                if (nested != null) return nested;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }

        return null;
    }

    /**
     * Converts a valid reflector ItemStack into a GTReflectorAddon instance.
     */
    public static GTReflectorAddon parseReflectorItem(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) return null;

        ReflectorStats stats = getReflectorStats(stack);
        if (stats == null || stats.tier() <= 0) return null;

        String name = stack.getHoverName().getString();
        String desc = Component.translatable("gui.gtcalcboard.addon.reflector_desc", stats.tier()).getString();

        GTReflectorAddon addon = new GTReflectorAddon(id.toString(), name.isEmpty() ? id.toString() : name, desc, id, stats.tier());
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu / Star Technology Reflector Item [" + id + "]");
        return addon;
    }

    /**
     * Discovers and registers built-in and registry-registered fusion reflectors.
     */
    public static void discoverGTCEuReflectors(List<MachineAddon> collector) {
        for (int t = 1; t <= 5; t++) {
            ResourceLocation id = ResourceLocation.tryParse("gtceu:fusion_reflector_t" + t);
            String name = Component.translatable("gui.gtcalcboard.addon.reflector_tier_name", t).getString();
            String desc = Component.translatable("gui.gtcalcboard.addon.reflector_desc", t).getString();
            GTReflectorAddon addon = new GTReflectorAddon("gtceu:reflector_tier_" + t, name, desc, id, t);
            addon.setDiscoverySource("GTCEu Built-in Reflector Specification");
            if (collector.stream().noneMatch(a -> a.getId().equals(addon.getId()))) {
                collector.add(addon);
            }
        }

        if (ForgeRegistries.ITEMS != null) {
            try {
                for (var item : ForgeRegistries.ITEMS) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) continue;

                    ItemStack stack = new ItemStack(item);
                    ReflectorStats stats = getReflectorStats(stack);
                    if (stats != null && stats.tier() > 0) {
                        GTReflectorAddon addon = parseReflectorItem(stack, id);
                        if (addon != null && collector.stream().noneMatch(a -> a.getId().equals(addon.getId()))) {
                            collector.add(addon);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}

