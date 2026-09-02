package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.DynamicAddonCrawler;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.catalog.MachineAddon;
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
import java.util.Locale;
import java.util.Map;

/**
 * Handles dynamic discovery of GregTech CEu Modern coils, turbine rotors,
 * parallel hatches, maintenance hatches, fusion reflectors, and multiblock traits.
 */
public class GTCEuAddonCrawler {

    public static void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (MachineAddon a : collector) {
            if (a != null && a.getId() != null) seenIds.add(a.getId());
        }

        // 1. Built-in GT Multiblock Traits & Configurable Maintenance Hatch modes
        addBuiltinTraits(collector, seenIds);

        // 2. Discover standard GT coils, rotors, energy hatches, hatches & buses, parallel hatches, and fusion reflectors via helpers
        try {
            TurbineRotorHelper.discoverGTCEuRotors(collector);
            CoilHelper.discoverGTCEuCoils(collector);
            ReflectorHelper.discoverGTCEuReflectors(collector);
            com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.discoverGTCEuEnergyHatches(collector);
            com.gtceu.calcboard.compat.gtceu.helper.GTHatchHelper.discoverGTCEuHatches(collector);
            ParallelHelper.discoverGTCEuParallelHatches(collector);
            for (MachineAddon a : collector) {
                if (a != null && a.getId() != null) seenIds.add(a.getId());
            }
        } catch (Throwable ignored) {}

        // 3. Scan active recipe stacks (e.g. custom material rotors, parts with NBT)
        if (recipeOutputStacks != null) {
            for (ItemStack s : recipeOutputStacks) {
                if (s == null || s.isEmpty()) continue;
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                if (id == null) continue;

                MachineAddon rotor = TurbineRotorHelper.parseTurbineRotor(s, id);
                if (rotor != null && seenIds.add(rotor.getId())) {
                    collector.add(rotor);
                }
                MachineAddon coil = CoilHelper.parseCoilBlock(s, id);
                if (coil != null && seenIds.add(coil.getId())) {
                    collector.add(coil);
                }
                MachineAddon parallel = ParallelHelper.parseParallelHatch(s, id);
                if (parallel != null && seenIds.add(parallel.getId())) {
                    collector.add(parallel);
                }
                MachineAddon energyHatch = com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.parseEnergyHatch(s, id);
                if (energyHatch != null && seenIds.add(energyHatch.getId())) {
                    collector.add(energyHatch);
                }
                MachineAddon reflector = ReflectorHelper.parseReflectorItem(s, id);
                if (reflector != null && seenIds.add(reflector.getId())) {
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

                String path = id.getPath();
                if (!path.contains("coil") && !path.contains("parallel") && !path.contains("hatch")
                        && !path.contains("rotor") && !path.contains("reflector") && !path.contains("maintenance")
                        && !path.contains("laser") && !path.contains("bus")) {
                    continue;
                }

                if (DynamicAddonCrawler.isItemDisabledOrHidden(item, activeRecipeItems)) {
                    continue;
                }

                ItemStack stack = nbtItemSamples.getOrDefault(item, new ItemStack(item));

                MachineAddon coil = CoilHelper.parseCoilBlock(stack, id);
                if (coil != null && seenIds.add(coil.getId())) {
                    collector.add(coil);
                    continue;
                }

                MachineAddon parallel = ParallelHelper.parseParallelHatch(stack, id);
                if (parallel != null && seenIds.add(parallel.getId())) {
                    collector.add(parallel);
                    continue;
                }

                MachineAddon energyHatch = com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.parseEnergyHatch(stack, id);
                if (energyHatch != null && seenIds.add(energyHatch.getId())) {
                    collector.add(energyHatch);
                    continue;
                }

                MachineAddon rotor = TurbineRotorHelper.parseTurbineRotor(stack, id);
                if (rotor != null && seenIds.add(rotor.getId())) {
                    collector.add(rotor);
                    continue;
                }

                MachineAddon reflector = ReflectorHelper.parseReflectorItem(stack, id);
                if (reflector != null && seenIds.add(reflector.getId())) {
                    collector.add(reflector);
                    continue;
                }

                if (isMaintenanceHatchItem(item, id)) {
                    List<MachineAddon> mAddons = parseMaintenanceHatches(stack, id);
                    for (MachineAddon addon : mAddons) {
                        if (addon != null && seenIds.add(addon.getId())) {
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
        addBuiltinTraits(list, null);
    }

    private static void tryAddTrait(List<MachineAddon> list, java.util.Set<String> seenIds, MachineAddon addon) {
        if (addon == null || addon.getId() == null) return;
        if (seenIds != null) {
            if (seenIds.add(addon.getId())) list.add(addon);
        } else if (!containsAddonId(list, addon.getId())) {
            list.add(addon);
        }
    }

    public static void addBuiltinTraits(List<MachineAddon> list, java.util.Set<String> seenIds) {
        MachineAddon boost = new MachineAddon("gtceu:throughput_boosting", "gui.gtcalcboard.addon.throughput_boosting", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.throughput_boosting.desc", ResourceLocation.tryParse("gtceu:pyrolyse_oven"));
        boost.setParallelMultiplier(4);
        boost.setDurationMultiplier(1.6);
        boost.setEutMultiplier(0.95);
        boost.setDiscoverySource("GTCEu Multiblock Trait Specification [gtceu:pyrolyse_oven]");
        tryAddTrait(list, seenIds, boost);

        MachineAddon batch = new MachineAddon("gtceu:batch_processing", "gui.gtcalcboard.addon.batch_processing", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.batch_processing.desc", null);
        batch.setParallelMultiplier(1);
        batch.setDurationMultiplier(1.0);
        batch.setEutMultiplier(1.0);
        batch.setDiscoverySource("GTCEu Multiblock Trait Specification");
        tryAddTrait(list, seenIds, batch);

        MachineAddon bulk = new MachineAddon("gtceu:bulk_processing", "gui.gtcalcboard.addon.bulk_processing", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.bulk_processing.desc", null);
        bulk.setParallelMultiplier(16);
        bulk.setDurationMultiplier(13.0);
        bulk.setEutMultiplier(1.0);
        bulk.setDiscoverySource("GTCEu / StarT Multiblock Trait Specification");
        tryAddTrait(list, seenIds, bulk);

        MachineAddon overpressure = new MachineAddon("gtceu:overpressure_autoclave", "gui.gtcalcboard.addon.overpressure_autoclave", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.overpressure_autoclave.desc", ResourceLocation.tryParse("gtceu:autoclave"));
        overpressure.setParallelMultiplier(8);
        overpressure.setDurationMultiplier(1.5);
        overpressure.setEutMultiplier(1.25);
        overpressure.setDiscoverySource("GTCEu Multiblock Trait Specification [gtceu:autoclave]");
        tryAddTrait(list, seenIds, overpressure);

        MachineAddon maint = new MachineAddon("gtceu:maintenance_hatch", "gui.gtcalcboard.addon.maintenance_hatch", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.maintenance_hatch.desc", ResourceLocation.tryParse("gtceu:maintenance_hatch"));
        maint.setDurationMultiplier(1.0);
        maint.setEutMultiplier(1.0);
        maint.setDiscoverySource("GTCEu Maintenance Hatch Specification");
        tryAddTrait(list, seenIds, maint);

        MachineAddon autoMaint = new MachineAddon("gtceu:auto_maintenance_hatch", "gui.gtcalcboard.addon.auto_maintenance_hatch", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.auto_maintenance_hatch.desc", ResourceLocation.tryParse("gtceu:auto_maintenance_hatch"));
        autoMaint.setDurationMultiplier(1.0);
        autoMaint.setEutMultiplier(1.0);
        autoMaint.setDiscoverySource("GTCEu Auto Maintenance Hatch Specification");
        tryAddTrait(list, seenIds, autoMaint);

        MachineAddon cleanMaint = new MachineAddon("gtceu:cleaning_maintenance_hatch", "gui.gtcalcboard.addon.cleaning_maintenance_hatch", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.cleaning_maintenance_hatch.desc", ResourceLocation.tryParse("gtceu:cleaning_maintenance_hatch"));
        cleanMaint.setDurationMultiplier(1.0);
        cleanMaint.setEutMultiplier(1.0);
        cleanMaint.setDiscoverySource("GTCEu Cleaning Maintenance Hatch Specification");
        tryAddTrait(list, seenIds, cleanMaint);

        MachineAddon cmhFast = new MachineAddon("gtceu:configurable_maintenance_hatch_fast", "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhFast.setDurationMultiplier(0.9);
        cmhFast.setEutMultiplier(1.0);
        cmhFast.setDiscoverySource("GTCEu Configurable Maintenance Hatch (Fast Mode)");
        tryAddTrait(list, seenIds, cmhFast);

        MachineAddon cmhEco = new MachineAddon("gtceu:configurable_maintenance_hatch_eco", "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhEco.setDurationMultiplier(1.1);
        cmhEco.setEutMultiplier(1.0);
        cmhEco.setDiscoverySource("GTCEu Configurable Maintenance Hatch (Eco Mode)");
        tryAddTrait(list, seenIds, cmhEco);

        // Register Muffler Hatches (LV ~ MAX)
        for (GTVoltageTier tier : GTVoltageTier.values()) {
            if (tier.ordinal() < GTVoltageTier.LV.ordinal()) continue;
            String prefix = tier.getName().toLowerCase(Locale.ROOT);
            ResourceLocation mId = ResourceLocation.tryParse("gtceu:" + prefix + "_muffler_hatch");
            if (mId != null) {
                String mName = tier.getName() + " Muffler Hatch";
                MachineAddon muffler = new MachineAddon(
                    mId.toString(),
                    mName,
                    MachineAddon.Category.MAINTENANCE,
                    "gui.gtcalcboard.addon.muffler_hatch_desc",
                    mId
                );
                muffler.setDurationMultiplier(1.0);
                muffler.setEutMultiplier(1.0);
                muffler.setDiscoverySource("GTCEu Muffler Hatch Specification [" + tier.getName() + "]");
                if (!containsAddonId(list, muffler.getId())) list.add(muffler);
            }
        }

        // Include Star Technology turbine boosting traits in catalog
        com.gtceu.calcboard.compat.start.StarTAddonCrawler.addBuiltinStarTTraits(list);
    }

    public static boolean isMufflerHatchItem(Item item, ResourceLocation id) {
        if (item instanceof BlockItem bi) {
            Object def = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getBlockMachineDefinition(bi.getBlock());
            if (def != null) {
                Class<?> mCls = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineClass(def);
                if (com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.isMufflerMachineClass(mCls)) {
                    return true;
                }
            }
        }
        var stats = com.gtceu.calcboard.compat.gtceu.helper.GTHatchHelper.extractStatsFromMachineDef(null, id);
        return stats != null && stats.abilities() != null && stats.abilities().contains("MUFFLER");
    }

    public static boolean isMaintenanceHatchItem(Item item, ResourceLocation id) {
        if (isMufflerHatchItem(item, id)) {
            return true;
        }
        if (item instanceof BlockItem bi) {
            Object def = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getBlockMachineDefinition(bi.getBlock());
            if (def != null) {
                Class<?> mCls = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineClass(def);
                if (com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.isMaintenanceMachineClass(mCls)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isConfigurableMaintenanceHatch(Item item) {
        if (item instanceof BlockItem bi) {
            Object def = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getBlockMachineDefinition(bi.getBlock());
            if (def != null) {
                Class<?> mCls = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineClass(def);
                return com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.isConfigurableMaintenanceMachineClass(mCls);
            }
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
        } else if (isMufflerHatchItem(stack.getItem(), id)) {
            String name = stack.getHoverName().getString();
            String desc = Component.translatable("gui.gtcalcboard.addon.muffler_hatch_desc").getString();
            MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.toString() : name, MachineAddon.Category.MAINTENANCE, desc, id);
            addon.setDurationMultiplier(1.0);
            addon.setEutMultiplier(1.0);
            addon.setItemStackSample(stack);
            addon.setDiscoverySource("GTCEu Muffler Hatch Definition [" + id + "]");
            list.add(addon);
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

