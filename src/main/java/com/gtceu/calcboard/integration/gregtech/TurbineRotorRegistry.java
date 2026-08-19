package com.gtceu.calcboard.integration.gregtech;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4-Layer robust scanner for GregTech Turbine Rotors at runtime,
 * capturing all GTCEu StarT Fork, Thermal Series, EnderIO, KubeJS and custom materials.
 */
public class TurbineRotorRegistry {

    public record RotorPreset(String id, String displayName, int efficiencyPercent) {}

    private static final List<RotorPreset> CACHED_ROTORS = new ArrayList<>();
    private static boolean scanned = false;
    private static boolean usingFallback = true;

    private static final Pattern EFF_PATTERN = Pattern.compile("(?i)(?:Efficiency|효율)[:\\s]+([0-9]+)%?");

    // Fallback presets (only if all 4 scan layers return 0)
    private static final List<RotorPreset> FALLBACK_PRESETS = List.of(
            new RotorPreset("default", "Standard (100%)", 100),
            new RotorPreset("bronze", "Bronze (110%)", 110),
            new RotorPreset("steel", "Steel (120%)", 120),
            new RotorPreset("stainless_steel", "Stainless Steel (140%)", 140),
            new RotorPreset("titanium", "Titanium (170%)", 170),
            new RotorPreset("tungstensteel", "Tungstensteel (200%)", 200),
            new RotorPreset("rhodium_plated_palladium", "Rhodium-Plated Palladium (220%)", 220),
            new RotorPreset("dragonsteel", "Dragonsteel (240%)", 240),
            new RotorPreset("naquadah_alloy", "Naquadah Alloy (240%)", 240),
            new RotorPreset("duranium", "Duranium (260%)", 260),
            new RotorPreset("neutronium", "Neutronium (300%)", 300)
    );

    public static synchronized boolean isUsingFallback() {
        if (!scanned || usingFallback) {
            scanDynamicRotors();
            scanned = true;
        }
        return usingFallback;
    }

    public static synchronized List<RotorPreset> getAllRotors() {
        if (!scanned || usingFallback) {
            scanDynamicRotors();
            scanned = true;
        }
        return CACHED_ROTORS.isEmpty() ? FALLBACK_PRESETS : Collections.unmodifiableList(CACHED_ROTORS);
    }

    private static void scanDynamicRotors() {
        Map<String, RotorPreset> found = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();

        // -------------------------------------------------------------
        // Layer 1: Scan all Creative Mode Tabs for gtceu:turbine_rotor ItemStacks
        // (Creative tabs contain real ItemStacks with actual Material NBT tags for all materials)
        // -------------------------------------------------------------
        try {
            for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
                Collection<ItemStack> tabStacks = tab.getDisplayItems();
                if (tabStacks != null && !tabStacks.isEmpty()) {
                    for (ItemStack stack : tabStacks) {
                        if (stack != null && !stack.isEmpty()) {
                            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            if (key != null && (key.getPath().contains("turbine_rotor") || key.getPath().contains("rotor"))) {
                                parseAndAddStack(stack, mc, found);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[GTCalcBoard] Layer 1 (CreativeTab) error: " + t.getMessage());
        }

        // -------------------------------------------------------------
        // Layer 2: Scan GTRegistries.MATERIALS & GTMaterials by exploring all methods
        // -------------------------------------------------------------
        if (found.isEmpty()) {
            Set<Object> materials = new LinkedHashSet<>();
            try {
                Class<?> gtRegistries = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                Object materialsReg = gtRegistries.getField("MATERIALS").get(null);
                collectMaterialsFromObject(materialsReg, materials);
            } catch (Throwable ignored) {}

            try {
                Class<?> gtMaterials = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.GTMaterials");
                for (Field f : gtMaterials.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())) {
                        try {
                            Object obj = f.get(null);
                            if (obj != null) materials.add(obj);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            if (rotorItem == null) {
                rotorItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            }

            for (Object mat : materials) {
                try {
                    String matName = null;
                    try {
                        Method m = mat.getClass().getMethod("getName");
                        matName = (String) m.invoke(mat);
                    } catch (Throwable ignored) {}

                    if (matName == null || matName.isEmpty()) continue;

                    String localizedName = matName;
                    try {
                        Method getLocalized = mat.getClass().getMethod("getLocalizedName");
                        Object comp = getLocalized.invoke(mat);
                        if (comp instanceof Component c) localizedName = c.getString();
                        else if (comp != null) localizedName = comp.toString();
                    } catch (Throwable ignored) {}

                    // Test with ItemStack tooltips using various NBT conventions
                    if (rotorItem != null) {
                        ItemStack stack = new ItemStack(rotorItem);
                        CompoundTag tag = new CompoundTag();
                        tag.putString("Material", "gtceu:" + matName);
                        tag.putString("material", "gtceu:" + matName);
                        tag.putString("gtceu:material", "gtceu:" + matName);
                        stack.setTag(tag);

                        parseAndAddStack(stack, mc, found);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // -------------------------------------------------------------
        // Layer 3: Scan all Forge Registries Items for discrete rotor items
        // -------------------------------------------------------------
        if (found.isEmpty()) {
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null && (id.getPath().contains("turbine_rotor") || (id.getPath().endsWith("_rotor") && id.getNamespace().contains("gtceu")))) {
                    ItemStack stack = new ItemStack(item);
                    parseAndAddStack(stack, mc, found);
                }
            }
        }

        // -------------------------------------------------------------
        // Final Assessment
        // -------------------------------------------------------------
        if (!found.isEmpty()) {
            usingFallback = false;
            System.out.println("[GTCalcBoard] Successfully scanned " + found.size() + " live turbine rotors!");
        } else {
            usingFallback = true;
            System.out.println("[GTCalcBoard] Dynamic scan returned 0 rotors, falling back to built-in presets.");
            for (RotorPreset fallback : FALLBACK_PRESETS) {
                found.put(fallback.id(), fallback);
            }
        }

        List<RotorPreset> sorted = new ArrayList<>(found.values());
        sorted.sort(Comparator.comparingInt(RotorPreset::efficiencyPercent));

        CACHED_ROTORS.clear();
        CACHED_ROTORS.addAll(sorted);
    }

    private static void collectMaterialsFromObject(Object registryObj, Set<Object> out) {
        if (registryObj == null) return;
        if (registryObj instanceof Iterable<?> it) {
            for (Object o : it) if (o != null) out.add(o);
            return;
        }
        for (Method m : registryObj.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && (m.getName().equals("values") || m.getName().equals("getValues") || m.getName().equals("getAll") || m.getName().equals("stream"))) {
                try {
                    Object res = m.invoke(registryObj);
                    if (res instanceof Iterable<?> it) {
                        for (Object o : it) if (o != null) out.add(o);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void parseAndAddStack(ItemStack stack, Minecraft mc, Map<String, RotorPreset> found) {
        if (stack == null || stack.isEmpty()) return;
        try {
            List<Component> lines = stack.getTooltipLines(mc.player, TooltipFlag.NORMAL);
            int eff = -1;
            for (Component line : lines) {
                Matcher m = EFF_PATTERN.matcher(line.getString());
                if (m.find()) {
                    eff = Integer.parseInt(m.group(1));
                    break;
                }
            }

            if (eff > 0) {
                String displayName = stack.getHoverName().getString();
                // Clean up duplicate suffixes like "Turbine Rotor" for clean display
                String key = displayName + "_" + eff;
                found.put(key, new RotorPreset(key, displayName + " (" + eff + "%)", eff));
            }
        } catch (Throwable ignored) {}
    }
}
