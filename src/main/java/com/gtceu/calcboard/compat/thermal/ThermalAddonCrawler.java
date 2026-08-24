package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.DynamicAddonCrawler;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Handles dynamic discovery of Thermal augments and KubeJS upgrade kits with AugmentData.
 */
public class ThermalAddonCrawler {

    public static void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        java.util.Map<Item, ItemStack> nbtItemSamples = new java.util.HashMap<>();

        java.util.Set<Item> activeRecipeItems = new java.util.HashSet<>();

        // 1. Scan active recipe output stacks (e.g. customized NBT kits or augments)
        if (recipeOutputStacks != null && !recipeOutputStacks.isEmpty()) {
            for (ItemStack s : recipeOutputStacks) {
                try {
                    if (s == null || s.isEmpty()) continue;
                    activeRecipeItems.add(s.getItem());
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                    if (id == null) continue;

                    if (s.hasTag()) {
                        nbtItemSamples.put(s.getItem(), s);
                    }

                    MachineAddon aug = ThermalAugmentHelper.parseThermalAugment(s, id);
                    if (aug != null && !containsAddonId(collector, aug.getId())) {
                        collector.add(aug);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. Scan Forge Item Registry with NBT samples fallback (Only when activeRecipeItems is available in Track 2)
        if (ForgeRegistries.ITEMS != null && !activeRecipeItems.isEmpty()) {
            for (Item item : ForgeRegistries.ITEMS) {
                try {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) continue;

                    // Filter out disabled or recipe-less dummy items
                    if (DynamicAddonCrawler.isItemDisabledOrHidden(item, activeRecipeItems)) {
                        continue;
                    }

                    String ns = id.getNamespace().toLowerCase();
                    String path = id.getPath().toLowerCase();

                    boolean isCandidate = ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("thermal_foundation")
                            || ns.equals("thermal_innovation") || ns.equals("thermal_extra") || ns.equals("cofh_core")
                            || ns.equals("systeams") || ns.equals("kubejs")
                            || path.contains("augment") || path.contains("upgrade_kit") || path.contains("integral_component")
                            || path.contains("reaction_chamber") || path.contains("injector") || path.contains("amplifier")
                            || path.contains("sieve") || path.contains("throttle");

                    if (!isCandidate) {
                        continue;
                    }

                    ItemStack s = nbtItemSamples.getOrDefault(item, new ItemStack(item));
                    MachineAddon aug = ThermalAugmentHelper.parseThermalAugment(s, id);
                    if (aug != null && !containsAddonId(collector, aug.getId())) {
                        collector.add(aug);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean containsAddonId(List<MachineAddon> list, String id) {
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }
}
