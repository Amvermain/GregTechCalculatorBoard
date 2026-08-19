package com.gtceu.calcboard.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically queries GTCEu Modern's IParallelHatch and ParallelHatchPartMachine API
 * to extract exact parallel processing capacities and absolute/energy scaling flags without regex parsing.
 */
public class ParallelHelper {

    private static final Map<String, ParallelStats> STATS_CACHE = new ConcurrentHashMap<>();

    public record ParallelStats(int maxParallel, boolean isAbsolute) {
        public static final ParallelStats DEFAULT = new ParallelStats(4, false);
    }

    private static final java.util.regex.Pattern PARALLEL_TOOLTIP_PATTERN = java.util.regex.Pattern.compile(
            "(?:최대\\s*)?([0-9]+)\\s*(?:x\\s*)?(?:병렬|Parallel|Parallels|개\\s*레시피|recipes\\s*at\\s*once)",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts exact parallel stats from an ItemStack or ResourceLocation.
     */
    public static ParallelStats getParallelStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ParallelStats.DEFAULT;
        }

        // 1. Primary: Extract directly from in-game ItemStack tooltip (matches Star Tech / pack custom tuning like 4x Elite Parallel)
        String tooltipText = extractTooltipText(stack);
        if (!tooltipText.isEmpty()) {
            boolean isAbs = tooltipText.contains("절대") || tooltipText.toLowerCase().contains("absolute");
            java.util.regex.Matcher m = PARALLEL_TOOLTIP_PATTERN.matcher(tooltipText);
            if (m.find()) {
                try {
                    int p = Integer.parseInt(m.group(1));
                    if (p > 0) {
                        return new ParallelStats(p, isAbs);
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Check NBT
        if (stack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag.contains("Parallel")) {
                int p = tag.getInt("Parallel");
                if (p > 0) return new ParallelStats(p, tag.getBoolean("IsAbsolute"));
            } else if (tag.contains("MaxParallel")) {
                int p = tag.getInt("MaxParallel");
                if (p > 0) return new ParallelStats(p, tag.getBoolean("IsAbsolute"));
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

    private static String extractTooltipText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            var lines = stack.getTooltipLines(null, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            if (lines != null) {
                StringBuilder sb = new StringBuilder();
                for (var line : lines) {
                    sb.append(line.getString()).append(" ");
                }
                return sb.toString().trim();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * Extracts exact parallel stats from a Block using GTCEu's IParallelHatch / ParallelHatchPartMachine API.
     */
    public static ParallelStats getParallelStats(Block block) {
        if (block == null) {
            return ParallelStats.DEFAULT;
        }

        ParallelStats stats = extractStatsFromObject(block);
        if (stats != null) {
            return stats;
        }

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
        if (id != null) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                ParallelStats stats = extractStatsFromObject(block);
                if (stats != null) return stats;
            }

            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item instanceof BlockItem bi) {
                ParallelStats stats = extractStatsFromObject(bi.getBlock());
                if (stats != null) return stats;
            }
        }

        // Derive from tier progression: EV=4, IV=16, LuV=64, ZPM=256, UV=1024, UHV=4096, UEV=16384, UIV=65536, UXV=262144, OpV=1048576, MAX=2147483647
        boolean isAbs = identifier.contains("absolute") || identifier.contains("절대");
        int par = deriveParallelFromTier(identifier);
        return new ParallelStats(par, isAbs);
    }

    /**
     * Invokes GTCEu IParallelHatch getters or MetaMachine methods via reflection.
     */
    public static ParallelStats extractStatsFromObject(Object target) {
        if (target == null) return null;

        Object machineObj = target;

        // Try getting MetaMachine / PartMachine from block entity or block
        try {
            for (Method m : target.getClass().getMethods()) {
                if ((m.getName().equalsIgnoreCase("getMetaMachine") || m.getName().equalsIgnoreCase("getMachine") || m.getName().equalsIgnoreCase("getPartMachine")) && m.getParameterCount() == 0) {
                    Object res = m.invoke(target);
                    if (res != null) {
                        machineObj = res;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Check if object implements IParallelHatch or has getMaxParallel / getCurrentParallel
        int maxPar = extractInt(machineObj, "getMaxParallel", "getCurrentParallel", "getParallel", "getMaxParallelAmount");
        boolean isAbsolute = extractBoolean(machineObj, "isAbsolute", "isExact", "isFixedEnergy");

        // If tier is accessible, compute 4^(tier - EV)
        if (maxPar <= 0) {
            int tier = extractTierIndex(machineObj);
            if (tier >= 4) { // EV=4, IV=5, LuV=6, ZPM=7, UV=8, UHV=9, UEV=10, UIV=11, UXV=12, OpV=13, MAX=14
                int shift = tier - 3; // EV -> 1 (4^1=4), IV -> 2 (4^2=16), etc.
                maxPar = 1 << (shift * 2);
            }
        }

        if (maxPar > 0) {
            return new ParallelStats(maxPar, isAbsolute);
        }

        return null;
    }

    private static int deriveParallelFromTier(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("max") || lower.contains("creative")) return 2147483647;
        if (lower.contains("opv")) return 1048576;
        if (lower.contains("uxv")) return 262144;
        if (lower.contains("uiv")) return 65536;
        if (lower.contains("uev")) return 16384;
        if (lower.contains("uhv")) return 4096;
        if (lower.contains("uv")) return 1024;
        if (lower.contains("zpm")) return 256;
        if (lower.contains("luv")) return 64;
        if (lower.contains("iv")) return 16;
        if (lower.contains("ev")) return 4;
        return 4;
    }

    private static int extractTierIndex(Object obj) {
        if (obj == null) return -1;
        try {
            for (String mName : new String[]{"getTier", "getVoltageTier", "getHatchTier"}) {
                Method m = obj.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    Object res = m.invoke(obj);
                    if (res instanceof Number n) return n.intValue();
                    if (res instanceof Enum<?> e) return e.ordinal();
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static int extractInt(Object target, String... methodNames) {
        if (target == null) return 0;
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    Object val = m.invoke(target);
                    if (val instanceof Number n) {
                        return n.intValue();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static boolean extractBoolean(Object target, String... methodNames) {
        if (target == null) return false;
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    Object val = m.invoke(target);
                    if (val instanceof Boolean b) {
                        return b;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
