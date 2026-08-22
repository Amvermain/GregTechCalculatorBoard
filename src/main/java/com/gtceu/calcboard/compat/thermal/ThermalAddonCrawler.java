package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.ThermalAugmentHelper;
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
        if (recipeOutputStacks != null && !recipeOutputStacks.isEmpty()) {
            for (ItemStack s : recipeOutputStacks) {
                try {
                    if (s == null || s.isEmpty()) continue;
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                    if (id == null) continue;

                    MachineAddon aug = ThermalAugmentHelper.parseThermalAugment(s, id);
                    if (aug != null && !containsAddonId(collector, aug.getId())) {
                        collector.add(aug);
                    }
                } catch (Throwable ignored) {}
            }
            return;
        }

        // Headless / Test mode fallback when no recipes or EMI index are loaded
        if (ForgeRegistries.ITEMS != null) {
            for (Item item : ForgeRegistries.ITEMS) {
                try {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) continue;
                    String ns = id.getNamespace().toLowerCase();
                    String path = id.getPath().toLowerCase();
                    if (!ns.equals("thermal") && !ns.equals("thermal_expansion") && !ns.equals("thermal_foundation") && !ns.equals("thermal_innovation") && !ns.equals("cofh_core") && !path.contains("augment") && !path.contains("upgrade_kit")) {
                        continue;
                    }
                    ItemStack s = new ItemStack(item);
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
