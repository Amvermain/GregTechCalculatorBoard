package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.catalog.MachineAddon;
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
        boolean isME,
        Set<String> abilities
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
                            if (stats.abilities() != null) {
                                addon.setAbilities(stats.abilities());
                            }
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
        boolean isSteam = path.contains("steam_") || path.startsWith("lp_steam_") || path.startsWith("hp_steam_") || path.startsWith("steam_");
        Set<String> abilities = new LinkedHashSet<>();

        // 1. Try to extract official PartAbility from machine definition via reflection
        if (machineDef != null) {
            try {
                for (Method m : machineDef.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && (m.getName().contains("PartAbilities") || m.getName().contains("Abilities"))) {
                        Object res = m.invoke(machineDef);
                        extractAbilitiesFromObject(res, abilities);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (isSteam) {
            if (path.contains("input_bus") || path.contains("import_bus")) {
                hatchType = HatchType.ITEM_INPUT;
                slotCapacity = 4;
                if (abilities.isEmpty()) abilities.add("STEAM_IMPORT_ITEMS");
            } else if (path.contains("output_bus") || path.contains("export_bus")) {
                hatchType = HatchType.ITEM_OUTPUT;
                slotCapacity = 4;
                if (abilities.isEmpty()) abilities.add("STEAM_EXPORT_ITEMS");
            } else if (path.contains("input_hatch") || path.contains("fluid_import") || path.equals("steam_hatch")) {
                hatchType = HatchType.FLUID_INPUT;
                slotCapacity = 1;
                if (abilities.isEmpty()) abilities.add("STEAM_IMPORT_FLUIDS");
            } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
                hatchType = HatchType.FLUID_OUTPUT;
                slotCapacity = 1;
                if (abilities.isEmpty()) abilities.add("STEAM_EXPORT_FLUIDS");
            }
        } else if (path.contains("dual") && (path.contains("hatch") || path.contains("bus"))) {
            hatchType = path.contains("output") || path.contains("export") ? HatchType.DUAL_OUTPUT : HatchType.DUAL_INPUT;
            slotCapacity = path.contains("4x") ? 4 : path.contains("9x") ? 9 : path.contains("16x") ? 16 : 4;
            if (abilities.isEmpty()) {
                if (hatchType == HatchType.DUAL_INPUT) {
                    abilities.add("IMPORT_ITEMS");
                    abilities.add("IMPORT_FLUIDS");
                } else {
                    abilities.add("EXPORT_ITEMS");
                    abilities.add("EXPORT_FLUIDS");
                }
            }
        } else if (path.contains("pattern_provider") || path.contains("pattern")) {
            hatchType = HatchType.ME_PATTERN_PROVIDER;
            slotCapacity = 16;
            isME = true;
            if (abilities.isEmpty()) {
                abilities.add("IMPORT_ITEMS");
                abilities.add("IMPORT_FLUIDS");
            }
        } else if (path.contains("input_bus") || path.contains("import_bus")) {
            hatchType = HatchType.ITEM_INPUT;
            slotCapacity = getBusSlotCount(tier);
            if (abilities.isEmpty()) abilities.add("IMPORT_ITEMS");
        } else if (path.contains("output_bus") || path.contains("export_bus")) {
            hatchType = HatchType.ITEM_OUTPUT;
            slotCapacity = getBusSlotCount(tier);
            if (abilities.isEmpty()) abilities.add("EXPORT_ITEMS");
        } else if (path.contains("input_hatch") || path.contains("fluid_import")) {
            hatchType = HatchType.FLUID_INPUT;
            slotCapacity = extractMultiHatchSlots(path);
            if (abilities.isEmpty()) abilities.add("IMPORT_FLUIDS");
        } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
            hatchType = HatchType.FLUID_OUTPUT;
            slotCapacity = extractMultiHatchSlots(path);
            if (abilities.isEmpty()) abilities.add("EXPORT_FLUIDS");
        }

        if (hatchType == null) {
            return null;
        }

        long baseTank = isSteam ? ((path.contains("hp_") || path.contains("high_pressure")) ? 16000L : 8000L) : calculateTankCapacityMB(tier);
        long tankCapacityMB = hatchType.isFluid() ? baseTank * slotCapacity : 0L;

        GTHatchStats stats = new GTHatchStats(hatchType, tier, slotCapacity, tankCapacityMB, isME, Collections.unmodifiableSet(abilities));
        STATS_CACHE.put(id, stats);
        return stats;
    }

    private static void extractAbilitiesFromObject(Object obj, Set<String> abilities) {
        if (obj == null) return;
        if (obj instanceof Enum<?> e) {
            abilities.add(e.name().toUpperCase(Locale.ROOT));
        } else if (obj instanceof Iterable<?> it) {
            for (Object item : it) extractAbilitiesFromObject(item, abilities);
        } else if (obj.getClass().isArray()) {
            for (Object item : (Object[]) obj) extractAbilitiesFromObject(item, abilities);
        } else {
            try {
                Method mGetName = obj.getClass().getMethod("getName");
                Object nameRes = mGetName.invoke(obj);
                if (nameRes != null) {
                    abilities.add(nameRes.toString().trim().toUpperCase(Locale.ROOT));
                    return;
                }
            } catch (Throwable ignored) {}
            try {
                Method mName = obj.getClass().getMethod("name");
                Object nameRes = mName.invoke(obj);
                if (nameRes != null) {
                    abilities.add(nameRes.toString().trim().toUpperCase(Locale.ROOT));
                    return;
                }
            } catch (Throwable ignored) {}

            String str = obj.toString().toUpperCase(Locale.ROOT);
            for (String token : str.split("[,\\s\\[\\]()]+")) {
                if (!token.isBlank() && (token.contains("IMPORT") || token.contains("EXPORT") || token.contains("STEAM") || token.contains("ENERGY") || token.contains("MAINTENANCE") || token.contains("PARALLEL"))) {
                    abilities.add(token);
                }
            }
        }
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
        if (stats.abilities().contains("STEAM_IMPORT_ITEMS") || stats.abilities().contains("STEAM_EXPORT_ITEMS")
                || stats.abilities().contains("STEAM_IMPORT_FLUIDS") || stats.abilities().contains("STEAM_EXPORT_FLUIDS")) {
            if (stats.hatchType().isFluid()) {
                return String.format(Locale.ROOT, "Steam Fluid Hatch (%,d mB)", stats.tankCapacityMB());
            } else {
                return String.format(Locale.ROOT, "Steam Item Bus (%d Slots)", stats.slotCapacity());
            }
        }
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
        // 1. Steam Buses and Hatches
        registerDefaultHatch(collector, "gtceu:lp_steam_input_bus", "LP Steam Input Bus", "Steam Item Bus (4 Slots)", HatchType.ITEM_INPUT, GTVoltageTier.ULV, 4, 0L, false, Set.of("STEAM_IMPORT_ITEMS"));
        registerDefaultHatch(collector, "gtceu:hp_steam_input_bus", "HP Steam Input Bus", "Steam Item Bus (4 Slots)", HatchType.ITEM_INPUT, GTVoltageTier.ULV, 4, 0L, false, Set.of("STEAM_IMPORT_ITEMS"));
        registerDefaultHatch(collector, "gtceu:lp_steam_output_bus", "LP Steam Output Bus", "Steam Item Bus (4 Slots)", HatchType.ITEM_OUTPUT, GTVoltageTier.ULV, 4, 0L, false, Set.of("STEAM_EXPORT_ITEMS"));
        registerDefaultHatch(collector, "gtceu:hp_steam_output_bus", "HP Steam Output Bus", "Steam Item Bus (4 Slots)", HatchType.ITEM_OUTPUT, GTVoltageTier.ULV, 4, 0L, false, Set.of("STEAM_EXPORT_ITEMS"));
        registerDefaultHatch(collector, "gtceu:lp_steam_input_hatch", "LP Steam Input Hatch", "Steam Fluid Hatch (8,000 mB)", HatchType.FLUID_INPUT, GTVoltageTier.ULV, 1, 8000L, false, Set.of("STEAM_IMPORT_FLUIDS"));
        registerDefaultHatch(collector, "gtceu:hp_steam_input_hatch", "HP Steam Input Hatch", "Steam Fluid Hatch (16,000 mB)", HatchType.FLUID_INPUT, GTVoltageTier.ULV, 1, 16000L, false, Set.of("STEAM_IMPORT_FLUIDS"));
        registerDefaultHatch(collector, "gtceu:lp_steam_output_hatch", "LP Steam Output Hatch", "Steam Fluid Hatch (8,000 mB)", HatchType.FLUID_OUTPUT, GTVoltageTier.ULV, 1, 8000L, false, Set.of("STEAM_EXPORT_FLUIDS"));
        registerDefaultHatch(collector, "gtceu:hp_steam_output_hatch", "HP Steam Output Hatch", "Steam Fluid Hatch (16,000 mB)", HatchType.FLUID_OUTPUT, GTVoltageTier.ULV, 1, 16000L, false, Set.of("STEAM_EXPORT_FLUIDS"));
        registerDefaultHatch(collector, "gtceu:steam_hatch", "Steam Input Hatch", "Steam Fluid Hatch (16,000 mB)", HatchType.FLUID_INPUT, GTVoltageTier.ULV, 1, 16000L, false, Set.of("STEAM_IMPORT_FLUIDS"));

        // 2. Electric Voltage Tier Hatches (ULV ~ MAX)
        GTVoltageTier[] tiers = GTVoltageTier.values();

        for (GTVoltageTier tier : tiers) {
            String tStr = tier.name().toLowerCase(Locale.ROOT);

            // 1. Standard Input Bus
            int busSlots = getBusSlotCount(tier);
            registerDefaultHatch(collector, "gtceu:" + tStr + "_input_bus",
                tier.getName() + " Input Bus",
                tier.getName() + " Input Bus (" + busSlots + " Slots)",
                HatchType.ITEM_INPUT, tier, busSlots, 0L, false, Set.of("IMPORT_ITEMS"));

            // 2. Standard Output Bus
            registerDefaultHatch(collector, "gtceu:" + tStr + "_output_bus",
                tier.getName() + " Output Bus",
                tier.getName() + " Output Bus (" + busSlots + " Slots)",
                HatchType.ITEM_OUTPUT, tier, busSlots, 0L, false, Set.of("EXPORT_ITEMS"));

            // 3. Standard 1x Input Hatch
            long tankMB = calculateTankCapacityMB(tier);
            registerDefaultHatch(collector, "gtceu:" + tStr + "_input_hatch",
                tier.getName() + " Input Hatch",
                String.format(Locale.ROOT, "%s Input Hatch (1x, %,d mB)", tier.getName(), tankMB),
                HatchType.FLUID_INPUT, tier, 1, tankMB, false, Set.of("IMPORT_FLUIDS"));

            // 4. Standard 1x Output Hatch
            registerDefaultHatch(collector, "gtceu:" + tStr + "_output_hatch",
                tier.getName() + " Output Hatch",
                String.format(Locale.ROOT, "%s Output Hatch (1x, %,d mB)", tier.getName(), tankMB),
                HatchType.FLUID_OUTPUT, tier, 1, tankMB, false, Set.of("EXPORT_FLUIDS"));

            // 5. 4x Multi-Fluid Input Hatch (EV+)
            if (tier.ordinal() >= GTVoltageTier.EV.ordinal()) {
                registerDefaultHatch(collector, "gtceu:" + tStr + "_input_hatch_4x",
                    tier.getName() + " 4x Input Hatch",
                    String.format(Locale.ROOT, "%s 4x Input Hatch (4x, %,d mB)", tier.getName(), tankMB * 4),
                    HatchType.FLUID_INPUT, tier, 4, tankMB * 4, false, Set.of("IMPORT_FLUIDS"));

                registerDefaultHatch(collector, "gtceu:" + tStr + "_output_hatch_4x",
                    tier.getName() + " 4x Output Hatch",
                    String.format(Locale.ROOT, "%s 4x Output Hatch (4x, %,d mB)", tier.getName(), tankMB * 4),
                    HatchType.FLUID_OUTPUT, tier, 4, tankMB * 4, false, Set.of("EXPORT_FLUIDS"));
            }

            // 6. 9x Multi-Fluid Hatch (IV+)
            if (tier.ordinal() >= GTVoltageTier.IV.ordinal()) {
                registerDefaultHatch(collector, "gtceu:" + tStr + "_input_hatch_9x",
                    tier.getName() + " 9x Input Hatch",
                    String.format(Locale.ROOT, "%s 9x Input Hatch (9x, %,d mB)", tier.getName(), tankMB * 9),
                    HatchType.FLUID_INPUT, tier, 9, tankMB * 9, false, Set.of("IMPORT_FLUIDS"));

                registerDefaultHatch(collector, "gtceu:" + tStr + "_output_hatch_9x",
                    tier.getName() + " 9x Output Hatch",
                    String.format(Locale.ROOT, "%s 9x Output Hatch (9x, %,d mB)", tier.getName(), tankMB * 9),
                    HatchType.FLUID_OUTPUT, tier, 9, tankMB * 9, false, Set.of("EXPORT_FLUIDS"));
            }

            // 7. 16x Multi-Fluid Hatch (LuV+)
            if (tier.ordinal() >= GTVoltageTier.LuV.ordinal()) {
                registerDefaultHatch(collector, "gtceu:" + tStr + "_input_hatch_16x",
                    tier.getName() + " 16x Input Hatch",
                    String.format(Locale.ROOT, "%s 16x Input Hatch (16x, %,d mB)", tier.getName(), tankMB * 16),
                    HatchType.FLUID_INPUT, tier, 16, tankMB * 16, false, Set.of("IMPORT_FLUIDS"));

                registerDefaultHatch(collector, "gtceu:" + tStr + "_output_hatch_16x",
                    tier.getName() + " 16x Output Hatch",
                    String.format(Locale.ROOT, "%s 16x Output Hatch (16x, %,d mB)", tier.getName(), tankMB * 16),
                    HatchType.FLUID_OUTPUT, tier, 16, tankMB * 16, false, Set.of("EXPORT_FLUIDS"));
            }
        }

        // 8. ME Dual Input Hatch & ME Pattern Provider
        registerDefaultHatch(collector, "gtceu:me_dual_input_hatch",
            "ME Dual Input Hatch",
            "ME Integrated Item & Fluid Input Hatch (16 Slots)",
            HatchType.DUAL_INPUT, GTVoltageTier.IV, 16, 10000000L, true, Set.of("IMPORT_ITEMS", "IMPORT_FLUIDS"));

        registerDefaultHatch(collector, "gtceu:me_pattern_provider_hatch",
            "ME Pattern Provider Hatch",
            "AE2 Direct Pattern Automation Interface",
            HatchType.ME_PATTERN_PROVIDER, GTVoltageTier.IV, 36, 10000000L, true, Set.of("IMPORT_ITEMS", "IMPORT_FLUIDS"));
    }

    private static void registerDefaultHatch(List<MachineAddon> collector, String idStr, String name, String desc,
                                            HatchType hatchType, GTVoltageTier tier, int slotCapacity, long tankCapacityMB,
                                            boolean isME, Set<String> abilities) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) return;
        GTHatchAddon hatch = new GTHatchAddon(
            id.toString(),
            name,
            desc,
            id,
            hatchType,
            tier,
            slotCapacity,
            tankCapacityMB,
            isME
        );
        hatch.setAbilities(abilities);
        if (!containsAddonId(collector, hatch.getId())) collector.add(hatch);
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

