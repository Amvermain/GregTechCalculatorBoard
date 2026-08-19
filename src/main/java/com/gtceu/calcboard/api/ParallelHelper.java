package com.gtceu.calcboard.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically queries GTCEu Modern's MachineDefinition and Tier API
 * to extract exact parallel processing capacities using code calculations (without tooltip parsing).
 */
public class ParallelHelper {

    private static final Map<String, ParallelStats> STATS_CACHE = new ConcurrentHashMap<>();

    public record ParallelStats(int maxParallel, boolean isAbsolute) {
        public static final ParallelStats DEFAULT = new ParallelStats(4, false);
    }

    /**
     * Extracts exact parallel stats from an ItemStack or ResourceLocation.
     */
    public static ParallelStats getParallelStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ParallelStats.DEFAULT;
        }

        // Check NBT override if present
        if (stack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag != null) {
                if (tag.contains("Parallel")) {
                    int p = tag.getInt("Parallel");
                    if (p > 0) return new ParallelStats(p, tag.getBoolean("IsAbsolute"));
                } else if (tag.contains("MaxParallel")) {
                    int p = tag.getInt("MaxParallel");
                    if (p > 0) return new ParallelStats(p, tag.getBoolean("IsAbsolute"));
                }
            }
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            return getParallelStats(blockItem.getBlock());
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            return getParallelStats(id.toString());
        }

        return ParallelStats.DEFAULT;
    }

    /**
     * Extracts exact parallel stats from a Block using GTCEu's MachineDefinition / MetaMachineBlock API.
     */
    public static ParallelStats getParallelStats(Block block) {
        if (block == null) {
            return ParallelStats.DEFAULT;
        }

        // Try extracting MachineDefinition directly from MetaMachineBlock
        try {
            for (Method m : block.getClass().getMethods()) {
                if ((m.getName().equalsIgnoreCase("getMachineDefinition") || m.getName().equalsIgnoreCase("getDefinition")) && m.getParameterCount() == 0) {
                    Object def = m.invoke(block);
                    if (def != null) {
                        ParallelStats s = extractStatsFromMachineDef(def);
                        if (s != null) return s;
                    }
                }
            }
        } catch (Throwable ignored) {}

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null) {
            return getParallelStats(id.toString());
        }

        return ParallelStats.DEFAULT;
    }

    /**
     * Obtains exact parallel stats by identifier string.
     */
    public static ParallelStats getParallelStats(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return ParallelStats.DEFAULT;
        }

        String key = identifier.toLowerCase().trim();
        if (key.startsWith("gtceu:")) {
            key = key.substring("gtceu:".length());
        }
        return STATS_CACHE.computeIfAbsent(key, ParallelHelper::computeParallelStats);
    }

    private static ParallelStats computeParallelStats(String identifier) {
        ResourceLocation id = ResourceLocation.tryParse(identifier.contains(":") ? identifier : "gtceu:" + identifier);
        boolean isAbs = identifier.contains("absolute") || identifier.contains("절대");

        // 1. Direct GTCEu GTRegistries.MACHINES & MachineDefinition code inspection
        if (id != null) {
            try {
                Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
                if (machinesRegistry != null) {
                    for (Method m : machinesRegistry.getClass().getMethods()) {
                        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == ResourceLocation.class) {
                            Object machineDef = m.invoke(machinesRegistry, id);
                            if (machineDef != null) {
                                ParallelStats stats = extractStatsFromMachineDef(machineDef);
                                if (stats != null) return stats;
                            }
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Fallback code derivation from tier tokens
        int par = deriveParallelFromTier(identifier);
        return new ParallelStats(par, isAbs);
    }

    /**
     * Extracts exact parallel stats from a GTCEu MachineDefinition by reading its getTier() property.
     */
    public static ParallelStats extractStatsFromMachineDef(Object machineDef) {
        if (machineDef == null) return null;

        try {
            boolean isAbsolute = false;
            // Check ID for absolute
            try {
                Method getIdM = machineDef.getClass().getMethod("getId");
                Object idObj = getIdM.invoke(machineDef);
                if (idObj instanceof ResourceLocation rl && rl.getPath().contains("absolute")) {
                    isAbsolute = true;
                }
            } catch (Throwable ignored) {}

            // Direct getTier() method on MachineDefinition
            Method getTierM = machineDef.getClass().getMethod("getTier");
            Object tierObj = getTierM.invoke(machineDef);
            if (tierObj instanceof Number n) {
                int tier = n.intValue();
                int parallel = calculateParallelFromTier(tier);
                return new ParallelStats(parallel, isAbsolute);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Computes the exact GTCEu parallel multiplier based on the machine tier:
     * EV (4) -> 4x
     * IV (5) -> 16x
     * LuV (6) -> 64x
     * ZPM (7) -> 256x
     * UV (8) -> 1024x
     * UHV (9) -> 4096x
     * UEV (10) -> 16384x
     * UIV (11) -> 65536x
     * UXV (12) -> 262144x
     * OpV (13) -> 1048576x
     * MAX (14) -> 2147483647
     */
    public static int calculateParallelFromTier(int tier) {
        if (tier <= 4) return 4;         // EV (or lower fallback) -> 4
        if (tier == 5) return 16;        // IV -> 16
        if (tier == 6) return 64;        // LuV -> 64
        if (tier == 7) return 256;       // ZPM -> 256
        if (tier == 8) return 1024;      // UV -> 1024
        if (tier == 9) return 4096;      // UHV -> 4096
        if (tier == 10) return 16384;    // UEV -> 16,384
        if (tier == 11) return 65536;    // UIV -> 65,536
        if (tier == 12) return 262144;   // UXV -> 262,144
        if (tier == 13) return 1048576;  // OpV -> 1,048,576
        if (tier >= 14) return Integer.MAX_VALUE; // MAX / Creative
        return 4;
    }

    private static int deriveParallelFromTier(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("max") || lower.contains("creative")) return Integer.MAX_VALUE;
        if (lower.contains("opv")) return 1048576;
        if (lower.contains("uxv")) return 262144;
        if (lower.contains("uiv")) return 65536;
        if (lower.contains("uev")) return 16384;
        if (lower.contains("uhv")) return 4096;
        if (lower.contains("uv")) return 1024;
        if (lower.contains("zpm")) return 256;
        if (lower.contains("luv")) return 64;
        if (lower.contains("iv")) return 16;
        if (lower.contains("ev") || lower.contains("elite") || lower.contains("엘리트") || lower.contains("advanced")) return 4;
        return 4;
    }
}
