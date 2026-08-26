package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for discovering, indexing, and calculating specifications for GTCEu Modern
 * Input/Output Hatches, Item Buses, Multi-Fluid Hatches, and ME Automation Hatches.
 * Uses official GTCEu MachineDefinition / MetaMachine reflection and deterministic data structures.
 */
public class GTHatchHelper {

    private static final Map<ResourceLocation, GTHatchStats> STATS_CACHE = new ConcurrentHashMap<>();

    public record GTHatchStats(
        HatchType hatchType,
        GTVoltageTier tier,
        int slotCapacity,
        long tankCapacityMB,
        boolean isME
    ) {}

    public static void discoverGTCEuHatches(List<MachineAddon> collector) {
        if (collector == null) return;

        boolean registrySuccess = false;
        try {
            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
            if (machinesRegistry instanceof Iterable<?> iterable) {
                for (Object machineDef : iterable) {
                    if (machineDef == null) continue;
                    try {
                        Method mGetId = machineDef.getClass().getMethod("getId");
                        ResourceLocation id = (ResourceLocation) mGetId.invoke(machineDef);
                        if (id == null) continue;

                        GTHatchStats stats = extractStatsFromMachineDef(machineDef, id);
                        if (stats != null) {
                            registrySuccess = true;
                            ItemStack stack = getItemStackForDef(machineDef, id);
                            String name = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : formatDisplayName(id);
                            String desc = formatHatchDescription(stats);

                            GTHatchAddon addon = new GTHatchAddon(
                                id.toString(),
                                name,
                                desc,
                                id,
                                stats.hatchType(),
                                stats.tier(),
                                stats.slotCapacity(),
                                stats.tankCapacityMB(),
                                stats.isME()
                            );
                            if (stack != null) addon.setItemStackSample(stack);
                            addon.setDiscoverySource("GTCEu Machine Registry [" + id + "]");
                            if (!containsAddonId(collector, addon.getId())) {
                                collector.add(addon);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Fallback / Headless test environment suite
        if (!registrySuccess || collector.stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.HATCH_BUS)) {
            registerDefaultHatches(collector);
        }
    }

    public static GTHatchStats extractStatsFromMachineDef(Object machineDef, ResourceLocation id) {
        if (id == null) return null;
        if (STATS_CACHE.containsKey(id)) return STATS_CACHE.get(id);

        String path = id.getPath().toLowerCase(Locale.ROOT);

        // Exclude energy hatches (handled by GTEnergyHatchAddon)
        if (path.contains("energy") || path.contains("substation") || path.contains("laser_target") || path.contains("laser_source")) {
            return null;
        }

        // Exclude maintenance hatches (handled by Maintenance Category)
        if (path.contains("maintenance")) {
            return null;
        }

        HatchType hatchType = null;
        GTVoltageTier tier = extractTierFromPath(path);
        int slotCapacity = 1;
        boolean isME = path.contains("me_") || path.contains("ae2") || path.contains("pattern");

        if (path.contains("dual") && (path.contains("hatch") || path.contains("bus"))) {
            hatchType = path.contains("output") || path.contains("export") ? HatchType.DUAL_OUTPUT : HatchType.DUAL_INPUT;
            slotCapacity = path.contains("4x") ? 4 : path.contains("9x") ? 9 : path.contains("16x") ? 16 : 4;
        } else if (path.contains("pattern_provider") || path.contains("pattern")) {
            hatchType = HatchType.ME_PATTERN_PROVIDER;
            slotCapacity = 16;
            isME = true;
        } else if (path.contains("input_bus") || path.contains("import_bus")) {
            hatchType = HatchType.ITEM_INPUT;
            slotCapacity = getBusSlotCount(tier);
        } else if (path.contains("output_bus") || path.contains("export_bus")) {
            hatchType = HatchType.ITEM_OUTPUT;
            slotCapacity = getBusSlotCount(tier);
        } else if (path.contains("input_hatch") || path.contains("fluid_import")) {
            hatchType = HatchType.FLUID_INPUT;
            slotCapacity = extractMultiHatchSlots(path);
        } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
            hatchType = HatchType.FLUID_OUTPUT;
            slotCapacity = extractMultiHatchSlots(path);
        }

        if (hatchType == null) {
            return null;
        }

        long baseTank = calculateTankCapacityMB(tier);
        long tankCapacityMB = hatchType.isFluid() ? baseTank * slotCapacity : 0L;

        GTHatchStats stats = new GTHatchStats(hatchType, tier, slotCapacity, tankCapacityMB, isME);
        STATS_CACHE.put(id, stats);
        return stats;
    }

    public static int getBusSlotCount(GTVoltageTier tier) {
        if (tier == null) return 1;
        return switch (tier) {
            case ULV -> 1;
            case LV, MV -> 4;
            case HV, EV -> 9;
            default -> 16; // IV ~ MAX
        };
    }

    public static long calculateTankCapacityMB(GTVoltageTier tier) {
        if (tier == null) return 16000L;
        int ord = tier.ordinal();
        return 8000L * (1L << Math.min(15, ord));
    }

    private static int extractMultiHatchSlots(String path) {
        if (path.contains("16x") || path.contains("hexadecatuple")) return 16;
        if (path.contains("9x") || path.contains("nonuple")) return 9;
        if (path.contains("4x") || path.contains("quad")) return 4;
        return 1;
    }

    private static GTVoltageTier extractTierFromPath(String path) {
        for (GTVoltageTier t : GTVoltageTier.values()) {
            if (path.startsWith(t.name().toLowerCase(Locale.ROOT) + "_") ||
                path.contains("_" + t.name().toLowerCase(Locale.ROOT) + "_") ||
                path.endsWith("_" + t.name().toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        return GTVoltageTier.LV;
    }

    private static String formatHatchDescription(GTHatchStats stats) {
        if (stats == null) return "";
        if (stats.isME()) {
            return "ME Automation Hatch (" + stats.slotCapacity() + " Slots)";
        }
        if (stats.hatchType().isFluid()) {
            return String.format(Locale.ROOT, "%s Fluid Hatch (%dx, %,d mB)",
                stats.tier().getName(), stats.slotCapacity(), stats.tankCapacityMB());
        } else {
            return String.format(Locale.ROOT, "%s Item Bus (%d Slots)",
                stats.tier().getName(), stats.slotCapacity());
        }
    }

    private static void registerDefaultHatches(List<MachineAddon> collector) {
        GTVoltageTier[] tiers = GTVoltageTier.values();

        for (GTVoltageTier tier : tiers) {
            String tStr = tier.name().toLowerCase(Locale.ROOT);

            // 1. Standard Input Bus
            ResourceLocation inBusId = ResourceLocation.tryParse("gtceu:" + tStr + "_input_bus");
            int busSlots = getBusSlotCount(tier);
            GTHatchAddon inBus = new GTHatchAddon(
                inBusId.toString(),
                tier.getName() + " Input Bus",
                tier.getName() + " Input Bus (" + busSlots + " Slots)",
                inBusId,
                HatchType.ITEM_INPUT,
                tier,
                busSlots,
                0L,
                false
            );
            if (!containsAddonId(collector, inBus.getId())) collector.add(inBus);

            // 2. Standard Output Bus
            ResourceLocation outBusId = ResourceLocation.tryParse("gtceu:" + tStr + "_output_bus");
            GTHatchAddon outBus = new GTHatchAddon(
                outBusId.toString(),
                tier.getName() + " Output Bus",
                tier.getName() + " Output Bus (" + busSlots + " Slots)",
                outBusId,
                HatchType.ITEM_OUTPUT,
                tier,
                busSlots,
                0L,
                false
            );
            if (!containsAddonId(collector, outBus.getId())) collector.add(outBus);

            // 3. Standard 1x Input Hatch
            ResourceLocation inHatchId = ResourceLocation.tryParse("gtceu:" + tStr + "_input_hatch");
            long tankMB = calculateTankCapacityMB(tier);
            GTHatchAddon inHatch = new GTHatchAddon(
                inHatchId.toString(),
                tier.getName() + " Input Hatch",
                String.format(Locale.ROOT, "%s Input Hatch (1x, %,d mB)", tier.getName(), tankMB),
                inHatchId,
                HatchType.FLUID_INPUT,
                tier,
                1,
                tankMB,
                false
            );
            if (!containsAddonId(collector, inHatch.getId())) collector.add(inHatch);

            // 4. Standard 1x Output Hatch
            ResourceLocation outHatchId = ResourceLocation.tryParse("gtceu:" + tStr + "_output_hatch");
            GTHatchAddon outHatch = new GTHatchAddon(
                outHatchId.toString(),
                tier.getName() + " Output Hatch",
                String.format(Locale.ROOT, "%s Output Hatch (1x, %,d mB)", tier.getName(), tankMB),
                outHatchId,
                HatchType.FLUID_OUTPUT,
                tier,
                1,
                tankMB,
                false
            );
            if (!containsAddonId(collector, outHatch.getId())) collector.add(outHatch);

            // 5. 4x Multi-Fluid Input Hatch (EV+)
            if (tier.ordinal() >= GTVoltageTier.EV.ordinal()) {
                ResourceLocation in4xId = ResourceLocation.tryParse("gtceu:" + tStr + "_input_hatch_4x");
                GTHatchAddon in4x = new GTHatchAddon(
                    in4xId.toString(),
                    tier.getName() + " 4x Input Hatch",
                    String.format(Locale.ROOT, "%s 4x Input Hatch (4x, %,d mB)", tier.getName(), tankMB * 4),
                    in4xId,
                    HatchType.FLUID_INPUT,
                    tier,
                    4,
                    tankMB * 4,
                    false
                );
                if (!containsAddonId(collector, in4x.getId())) collector.add(in4x);

                ResourceLocation out4xId = ResourceLocation.tryParse("gtceu:" + tStr + "_output_hatch_4x");
                GTHatchAddon out4x = new GTHatchAddon(
                    out4xId.toString(),
                    tier.getName() + " 4x Output Hatch",
                    String.format(Locale.ROOT, "%s 4x Output Hatch (4x, %,d mB)", tier.getName(), tankMB * 4),
                    out4xId,
                    HatchType.FLUID_OUTPUT,
                    tier,
                    4,
                    tankMB * 4,
                    false
                );
                if (!containsAddonId(collector, out4x.getId())) collector.add(out4x);
            }

            // 6. 9x Multi-Fluid Hatch (IV+)
            if (tier.ordinal() >= GTVoltageTier.IV.ordinal()) {
                ResourceLocation in9xId = ResourceLocation.tryParse("gtceu:" + tStr + "_input_hatch_9x");
                GTHatchAddon in9x = new GTHatchAddon(
                    in9xId.toString(),
                    tier.getName() + " 9x Input Hatch",
                    String.format(Locale.ROOT, "%s 9x Input Hatch (9x, %,d mB)", tier.getName(), tankMB * 9),
                    in9xId,
                    HatchType.FLUID_INPUT,
                    tier,
                    9,
                    tankMB * 9,
                    false
                );
                if (!containsAddonId(collector, in9x.getId())) collector.add(in9x);

                ResourceLocation out9xId = ResourceLocation.tryParse("gtceu:" + tStr + "_output_hatch_9x");
                GTHatchAddon out9x = new GTHatchAddon(
                    out9xId.toString(),
                    tier.getName() + " 9x Output Hatch",
                    String.format(Locale.ROOT, "%s 9x Output Hatch (9x, %,d mB)", tier.getName(), tankMB * 9),
                    out9xId,
                    HatchType.FLUID_OUTPUT,
                    tier,
                    9,
                    tankMB * 9,
                    false
                );
                if (!containsAddonId(collector, out9x.getId())) collector.add(out9x);
            }

            // 7. 16x Multi-Fluid Hatch (LuV+)
            if (tier.ordinal() >= GTVoltageTier.LuV.ordinal()) {
                ResourceLocation in16xId = ResourceLocation.tryParse("gtceu:" + tStr + "_input_hatch_16x");
                GTHatchAddon in16x = new GTHatchAddon(
                    in16xId.toString(),
                    tier.getName() + " 16x Input Hatch",
                    String.format(Locale.ROOT, "%s 16x Input Hatch (16x, %,d mB)", tier.getName(), tankMB * 16),
                    in16xId,
                    HatchType.FLUID_INPUT,
                    tier,
                    16,
                    tankMB * 16,
                    false
                );
                if (!containsAddonId(collector, in16x.getId())) collector.add(in16x);

                ResourceLocation out16xId = ResourceLocation.tryParse("gtceu:" + tStr + "_output_hatch_16x");
                GTHatchAddon out16x = new GTHatchAddon(
                    out16xId.toString(),
                    tier.getName() + " 16x Output Hatch",
                    String.format(Locale.ROOT, "%s 16x Output Hatch (16x, %,d mB)", tier.getName(), tankMB * 16),
                    out16xId,
                    HatchType.FLUID_OUTPUT,
                    tier,
                    16,
                    tankMB * 16,
                    false
                );
                if (!containsAddonId(collector, out16x.getId())) collector.add(out16x);
            }
        }

        // 8. ME Dual Input Hatch & ME Pattern Provider
        ResourceLocation meDualId = ResourceLocation.tryParse("gtceu:me_dual_input_hatch");
        GTHatchAddon meDual = new GTHatchAddon(
            meDualId.toString(),
            "ME Dual Input Hatch",
            "ME Integrated Item & Fluid Input Hatch (16 Slots)",
            meDualId,
            HatchType.DUAL_INPUT,
            GTVoltageTier.IV,
            16,
            10000000L,
            true
        );
        if (!containsAddonId(collector, meDual.getId())) collector.add(meDual);

        ResourceLocation mePatternId = ResourceLocation.tryParse("gtceu:me_pattern_provider_hatch");
        GTHatchAddon mePattern = new GTHatchAddon(
            mePatternId.toString(),
            "ME Pattern Provider Hatch",
            "AE2 Direct Pattern Automation Interface",
            mePatternId,
            HatchType.ME_PATTERN_PROVIDER,
            GTVoltageTier.IV,
            36,
            10000000L,
            true
        );
        if (!containsAddonId(collector, mePattern.getId())) collector.add(mePattern);
    }

    private static ItemStack getItemStackForDef(Object machineDef, ResourceLocation id) {
        if (id == null) return ItemStack.EMPTY;
        try {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) return new ItemStack(item);
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    private static boolean containsAddonId(List<MachineAddon> collector, String id) {
        if (collector == null || id == null) return false;
        for (MachineAddon a : collector) {
            if (a != null && id.equals(a.getId())) return true;
        }
        return false;
    }

    private static String formatDisplayName(ResourceLocation id) {
        if (id == null) return "";
        String raw = id.getPath();
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
