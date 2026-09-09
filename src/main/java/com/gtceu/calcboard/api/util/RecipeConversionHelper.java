package com.gtceu.calcboard.api.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Universal utility helper for recipe conversion, input filtering, and name formatting.
 * Decouples mod compatibility adapters and core domain from external recipe viewers.
 */
public final class RecipeConversionHelper {

    private static final Class<?> CREATE_BASIN_BLOCK_CLASS;
    private static final Class<?> CREATE_BLAZE_BURNER_BLOCK_CLASS;

    static {
        Class<?> basinCls = null;
        Class<?> burnerCls = null;
        try {
            basinCls = Class.forName("com.simibubi.create.content.processing.basin.BasinBlock");
        } catch (Throwable ignored) {}
        try {
            burnerCls = Class.forName("com.simibubi.create.content.processing.burner.BlazeBurnerBlock");
        } catch (Throwable ignored) {}
        CREATE_BASIN_BLOCK_CLASS = basinCls;
        CREATE_BLAZE_BURNER_BLOCK_CLASS = burnerCls;
    }

    private RecipeConversionHelper() {}

    public static String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown Machine";
        String[] parts = raw.split("[_\\-.]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static boolean isIgnoredInput(ResourceLocation id, double chance) {
        if (id == null) return true;
        if (isDummyConditionMarker(id)) return true;
        if (isProgrammedCircuit(id)) return true;
        return chance <= 0.0;
    }

    public static boolean isProgrammedCircuit(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String ns = id.getNamespace().toLowerCase(Locale.ROOT);
        return ("gtceu".equals(ns) || "gtce".equals(ns) || "gregtech".equals(ns))
                && (path.equals("programmed_circuit") || path.equals("integrated_circuit") || path.startsWith("circuit_config"));
    }

    public static boolean isDummyConditionMarker(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);

        return path.endsWith("_marker") || path.endsWith("_marker_item") || path.endsWith("_marker_block")
                || path.startsWith("dimension_marker") || path.startsWith("biome_marker")
                || path.startsWith("planet_marker") || path.startsWith("environmental_marker")
                || path.startsWith("altitude_marker") || path.startsWith("temperature_marker");
    }

    public static boolean isIgnoredWorkstation(ResourceLocation id) {
        if (id == null) return true;
        if (isDummyConditionMarker(id)) return true;
        try {
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item instanceof net.minecraft.world.item.BlockItem bi) {
                net.minecraft.world.level.block.Block block = bi.getBlock();
                if (CREATE_BASIN_BLOCK_CLASS != null && CREATE_BASIN_BLOCK_CLASS.isInstance(block)) {
                    return true;
                }
                if (CREATE_BLAZE_BURNER_BLOCK_CLASS != null && CREATE_BLAZE_BURNER_BLOCK_CLASS.isInstance(block)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
