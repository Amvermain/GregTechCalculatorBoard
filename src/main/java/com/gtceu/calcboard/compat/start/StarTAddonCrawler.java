package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.catalog.DynamicAddonCrawler;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.helper.ParallelHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class StarTAddonCrawler {

    public static void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (MachineAddon a : collector) {
            if (a != null && a.getId() != null) seenIds.add(a.getId());
        }

        addBuiltinStarTTraits(collector, seenIds);

        java.util.Set<Item> activeRecipeItems = new java.util.HashSet<>();
        if (recipeOutputStacks != null) {
            for (ItemStack s : recipeOutputStacks) {
                if (s != null && !s.isEmpty()) {
                    activeRecipeItems.add(s.getItem());
                }
            }
        }

        try {
            if (ForgeRegistries.ITEMS != null) {
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) continue;
                    String ns = id.getNamespace();
                    if (!ns.equals("start_core") && !ns.equals("gtceu_start")) continue;

                    String path = id.getPath();
                    if (!path.contains("parallel") && !path.contains("reflector") && !path.contains("maintenance") && !path.contains("hatch")) {
                        continue;
                    }

                    if (DynamicAddonCrawler.isItemDisabledOrHidden(item, activeRecipeItems)) {
                        continue;
                    }

                    ItemStack stack = new ItemStack(item);

                    MachineAddon parallel = ParallelHelper.parseParallelHatch(stack, id);
                    if (parallel != null && seenIds.add(parallel.getId())) {
                        collector.add(parallel);
                        continue;
                    }

                    MachineAddon reflector = ReflectorHelper.parseReflectorItem(stack, id);
                    if (reflector != null && seenIds.add(reflector.getId())) {
                        collector.add(reflector);
                        continue;
                    }

                    MachineAddon maint = parseStarTMaintenanceHatch(stack, id);
                    if (maint != null && seenIds.add(maint.getId())) {
                        collector.add(maint);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void tryAddTrait(List<MachineAddon> list, java.util.Set<String> seenIds, MachineAddon addon) {
        if (addon == null || addon.getId() == null) return;
        if (seenIds != null) {
            if (seenIds.add(addon.getId())) list.add(addon);
        } else if (!containsAddonId(list, addon.getId())) {
            list.add(addon);
        }
    }

    public static void addBuiltinStarTTraits(List<MachineAddon> list) {
        addBuiltinStarTTraits(list, null);
    }

    public static void addBuiltinStarTTraits(List<MachineAddon> list, java.util.Set<String> seenIds) {
        // Sterile Cleaning Maintenance Hatch (Star Technology Core)
        MachineAddon sterileMaint = new MachineAddon(
                "start_core:sterile_cleaning_maintenance_hatch",
                "gui.gtcalcboard.addon.sterile_cleaning_maintenance_hatch",
                MachineAddon.Category.MAINTENANCE,
                "gui.gtcalcboard.addon.sterile_cleaning_maintenance_hatch.desc",
                ResourceLocation.tryParse("start_core:sterile_cleaning_maintenance_hatch")
        );
        sterileMaint.setDurationMultiplier(1.0);
        sterileMaint.setEutMultiplier(1.0);
        sterileMaint.setDiscoverySource("Star Technology Sterile Cleaning Maintenance Hatch Specification");
        tryAddTrait(list, seenIds, sterileMaint);
    }

    public static MachineAddon parseStarTMaintenanceHatch(ItemStack stack, ResourceLocation id) {
        if (id == null) return null;
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        if (path.contains("maintenance") || path.contains("maint")) {
            String nameKey = "gui.gtcalcboard.addon." + path;
            String descKey = "gui.gtcalcboard.addon." + path + ".desc";
            MachineAddon addon = new MachineAddon(id.toString(), nameKey, MachineAddon.Category.MAINTENANCE, descKey, id);
            addon.setDurationMultiplier(1.0);
            addon.setEutMultiplier(1.0);
            addon.setDiscoverySource("Star Technology Maintenance Hatch Specification [" + id + "]");
            return addon;
        }
        return null;
    }

    private static boolean containsAddonId(List<MachineAddon> list, String id) {
        if (id == null) return false;
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }
}

