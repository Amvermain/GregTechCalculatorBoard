package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.DynamicAddonCrawler;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ParallelHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import com.gtceu.calcboard.compat.gtceu.helper.TurbineRotorHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles dynamic discovery of GregTech CEu Modern coils, turbine rotors,
 * parallel hatches, maintenance hatches, fusion reflectors, and multiblock traits.
 */
public class GTCEuAddonCrawler {

    public static void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        // 1. Built-in GT Multiblock Traits & Configurable Maintenance Hatch modes
        addBuiltinTraits(collector);

        // 2. Discover standard GT coils, rotors, and fusion reflectors via helpers
        try {
            TurbineRotorHelper.discoverGTCEuRotors(collector);
            CoilHelper.discoverGTCEuCoils(collector);
            ReflectorHelper.discoverGTCEuReflectors(collector);
        } catch (Throwable ignored) {}

        // 3. Scan active recipe stacks (e.g. custom material rotors, parts with NBT)
        if (recipeOutputStacks != null) {
            for (ItemStack s : recipeOutputStacks) {
                if (s == null || s.isEmpty()) continue;
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                if (id == null) continue;

                MachineAddon rotor = TurbineRotorHelper.parseTurbineRotor(s, id);
                if (rotor != null && !containsAddonId(collector, rotor.getId())) {
                    collector.add(rotor);
                }
                MachineAddon coil = CoilHelper.parseCoilBlock(s, id);
                if (coil != null && !containsAddonId(collector, coil.getId())) {
                    collector.add(coil);
                }
                MachineAddon parallel = ParallelHelper.parseParallelHatch(s, id);
                if (parallel != null && !containsAddonId(collector, parallel.getId())) {
                    collector.add(parallel);
                }
                MachineAddon reflector = ReflectorHelper.parseReflectorItem(s, id);
                if (reflector != null && !containsAddonId(collector, reflector.getId())) {
                    collector.add(reflector);
                }
            }
        }

        // 4. Registry crawl for GT & Addon hatches, coils, reflectors, rotors
        if (ForgeRegistries.ITEMS != null) {
            Map<Item, ItemStack> nbtItemSamples = new HashMap<>();
            java.util.Set<Item> activeRecipeItems = new java.util.HashSet<>();
            if (recipeOutputStacks != null) {
                for (ItemStack s : recipeOutputStacks) {
                    if (s != null && !s.isEmpty()) {
                        activeRecipeItems.add(s.getItem());
                        if (s.hasTag()) {
                            nbtItemSamples.put(s.getItem(), s);
                        }
                    }
                }
            }

            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null) continue;
                String ns = id.getNamespace();
                if (!ns.equals("gtceu") && !ns.equals("kubejs")) continue;

                if (DynamicAddonCrawler.isItemDisabledOrHidden(item, activeRecipeItems)) {
                    continue;
                }

                ItemStack stack = nbtItemSamples.getOrDefault(item, new ItemStack(item));

                MachineAddon coil = CoilHelper.parseCoilBlock(stack, id);
                if (coil != null && !containsAddonId(collector, coil.getId())) {
                    collector.add(coil);
                    continue;
                }

                MachineAddon parallel = ParallelHelper.parseParallelHatch(stack, id);
                if (parallel != null && !containsAddonId(collector, parallel.getId())) {
                    collector.add(parallel);
                    continue;
                }

                MachineAddon rotor = TurbineRotorHelper.parseTurbineRotor(stack, id);
                if (rotor != null && !containsAddonId(collector, rotor.getId())) {
                    collector.add(rotor);
                    continue;
                }

                MachineAddon reflector = ReflectorHelper.parseReflectorItem(stack, id);
                if (reflector != null && !containsAddonId(collector, reflector.getId())) {
                    collector.add(reflector);
                    continue;
                }

                if (isMaintenanceHatchItem(item, id)) {
                    List<MachineAddon> mAddons = parseMaintenanceHatches(stack, id);
                    for (MachineAddon addon : mAddons) {
                        if (addon != null && !containsAddonId(collector, addon.getId())) {
                            collector.add(addon);
                        }
                    }
                }
            }
        }
    }

    public static boolean containsAddonId(List<MachineAddon> list, String id) {
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }

    public static void addBuiltinTraits(List<MachineAddon> list) {
        MachineAddon boost = new MachineAddon("gtceu:throughput_boosting", "gui.gtcalcboard.addon.throughput_boosting", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.throughput_boosting.desc", ResourceLocation.tryParse("gtceu:pyrolyse_oven"));
        boost.setParallelMultiplier(4);
        boost.setDurationMultiplier(1.6);
        boost.setEutMultiplier(0.95);
        boost.setDiscoverySource("GTCEu Multiblock Trait Specification [gtceu:pyrolyse_oven]");
        if (!containsAddonId(list, boost.getId())) list.add(boost);

        MachineAddon batch = new MachineAddon("gtceu:batch_processing", "gui.gtcalcboard.addon.batch_processing", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.batch_processing.desc", null);
        batch.setParallelMultiplier(16);
        batch.setDurationMultiplier(13.0);
        batch.setEutMultiplier(1.0);
        batch.setDiscoverySource("GTCEu Multiblock Trait Specification");
        if (!containsAddonId(list, batch.getId())) list.add(batch);

        MachineAddon overpressure = new MachineAddon("gtceu:overpressure_autoclave", "gui.gtcalcboard.addon.overpressure_autoclave", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.overpressure_autoclave.desc", ResourceLocation.tryParse("gtceu:autoclave"));
        overpressure.setParallelMultiplier(8);
        overpressure.setDurationMultiplier(1.5);
        overpressure.setEutMultiplier(1.25);
        overpressure.setDiscoverySource("GTCEu Multiblock Trait Specification [gtceu:autoclave]");
        if (!containsAddonId(list, overpressure.getId())) list.add(overpressure);

        MachineAddon cmhFast = new MachineAddon("gtceu:configurable_maintenance_hatch_fast", "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhFast.setDurationMultiplier(0.9);
        cmhFast.setEutMultiplier(1.0);
        cmhFast.setDiscoverySource("GTCEu Configurable Maintenance Hatch (Fast Mode)");
        if (!containsAddonId(list, cmhFast.getId())) list.add(cmhFast);

        MachineAddon cmhEco = new MachineAddon("gtceu:configurable_maintenance_hatch_eco", "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhEco.setDurationMultiplier(1.1);
        cmhEco.setEutMultiplier(1.0);
        cmhEco.setDiscoverySource("GTCEu Configurable Maintenance Hatch (Eco Mode)");
        if (!containsAddonId(list, cmhEco.getId())) list.add(cmhEco);

        // Include Star Technology turbine boosting traits in catalog
        com.gtceu.calcboard.compat.start.StarTAddonCrawler.addBuiltinStarTTraits(list);
    }

    public static boolean isMaintenanceHatchItem(Item item, ResourceLocation id) {
        if (item instanceof BlockItem bi) {
            Block b = bi.getBlock();
            try {
                Method mGetDef = b.getClass().getMethod("getDefinition");
                Object def = mGetDef.invoke(b);
                if (def != null) {
                    Class<?> maintPartCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine");
                    Method mGetMachineClass = def.getClass().getMethod("getMachineClass");
                    Class<?> mCls = (Class<?>) mGetMachineClass.invoke(def);
                    if (mCls != null && maintPartCls.isAssignableFrom(mCls)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean isConfigurableMaintenanceHatch(Item item) {
        if (item instanceof BlockItem bi) {
            Block b = bi.getBlock();
            try {
                Method mGetDef = b.getClass().getMethod("getDefinition");
                Object def = mGetDef.invoke(b);
                if (def != null) {
                    Class<?> cfgMaintCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.ConfigurableMaintenanceHatchPartMachine");
                    Method mGetMachineClass = def.getClass().getMethod("getMachineClass");
                    Class<?> mCls = (Class<?>) mGetMachineClass.invoke(def);
                    if (mCls != null && cfgMaintCls.isAssignableFrom(mCls)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static List<MachineAddon> parseMaintenanceHatches(ItemStack stack, ResourceLocation id) {
        List<MachineAddon> list = new ArrayList<>();

        if (isConfigurableMaintenanceHatch(stack.getItem())) {
            MachineAddon fast = new MachineAddon(id + "_fast",
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_fast").getString(),
                    MachineAddon.Category.MAINTENANCE,
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_fast.desc").getString(),
                    id);
            fast.setDurationMultiplier(0.9);
            fast.setEutMultiplier(1.0);
            fast.setItemStackSample(stack);
            fast.setDiscoverySource("GTCEu Configurable Maintenance Hatch Definition (Fast Mode) [" + id + "]");
            list.add(fast);

            MachineAddon eco = new MachineAddon(id + "_eco",
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_eco").getString(),
                    MachineAddon.Category.MAINTENANCE,
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_eco.desc").getString(),
                    id);
            eco.setDurationMultiplier(1.1);
            eco.setEutMultiplier(1.0);
            eco.setItemStackSample(stack);
            eco.setDiscoverySource("GTCEu Configurable Maintenance Hatch Definition (Eco Mode) [" + id + "]");
            list.add(eco);
        } else {
            String name = stack.getHoverName().getString();
            String desc = Component.translatable("gui.gtcalcboard.addon.maintenance_hatch_desc").getString();
            MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.toString() : name, MachineAddon.Category.MAINTENANCE, desc, id);
            addon.setDurationMultiplier(1.0);
            addon.setEutMultiplier(1.0);
            addon.setItemStackSample(stack);
            addon.setDiscoverySource("GTCEu Maintenance Hatch Definition [" + id + "]");
            list.add(addon);
        }

        return list;
    }
}
