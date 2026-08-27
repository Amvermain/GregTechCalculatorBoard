package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for GTCEu Modern parallel control hatches.
 * Dynamically queries MachineDefinition, MetaMachine, and IParallelHatch APIs to extract exact parallel capacity.
 */
public class ParallelHelper {

    private static final Map<String, ParallelStats> STATS_CACHE = new ConcurrentHashMap<>();
    private static Object DUMMY_HOLDER_PROXY = null;

    public record ParallelStats(int maxParallel, boolean isAbsolute) {
        public static final ParallelStats DEFAULT = new ParallelStats(4, false);
    }

    /**
     * Extracts exact parallel stats from an ItemStack by querying GTCEu machine definitions directly.
     */
    public static ParallelStats getParallelStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

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

        return null;
    }

    /**
     * Extracts exact parallel stats from a Block using GTCEu's MetaMachineBlock API.
     */
    public static ParallelStats getParallelStats(Block block) {
        if (block == null) {
            return null;
        }

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

        return null;
    }

    /**
     * Obtains exact parallel stats by identifier string.
     */
    public static ParallelStats getParallelStats(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }

        String key = identifier.toLowerCase().trim();
        if (key.startsWith("gtceu:")) {
            key = key.substring("gtceu:".length());
        }
        if (STATS_CACHE.containsKey(key)) {
            return STATS_CACHE.get(key);
        }
        ParallelStats computed = computeParallelStats(key);
        if (computed != null) {
            STATS_CACHE.put(key, computed);
        }
        return computed;
    }

    private static ParallelStats computeParallelStats(String identifier) {
        ResourceLocation id = ResourceLocation.tryParse(identifier.contains(":") ? identifier : "gtceu:" + identifier);

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

        return null;
    }

    public static ParallelStats extractStatsFromMachineDef(Object machineDef) {
        if (machineDef == null) return null;

        try {
            Object dummyHolder = getOrCreateDummyHolderProxy();
            if (dummyHolder != null) {
                for (Method m : machineDef.getClass().getMethods()) {
                    if (m.getParameterCount() == 1 && (m.getName().equals("createMetaMachine") || m.getName().equals("createMachine"))) {
                        try {
                            Object machine = m.invoke(machineDef, dummyHolder);
                            if (machine != null) {
                                ParallelStats stats = extractStatsFromMachineInstance(machine);
                                if (stats != null) {
                                    return stats;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            int maxPar = extractInt(machineDef, "getCurrentParallel", "getMaxParallel", "getParallel", "getMaxParallelAmount");
            boolean isAbsolute = extractBoolean(machineDef, "isAbsolute", "isExact", "isFixedEnergy", "isPowerConstant");
            if (maxPar > 0) {
                return new ParallelStats(maxPar, isAbsolute);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static ParallelStats extractStatsFromMachineInstance(Object machine) {
        if (machine == null) return null;

        int maxPar = extractInt(machine, "getCurrentParallel", "getMaxParallel", "getParallel", "getMaxParallelAmount");
        boolean isAbsolute = extractBoolean(machine, "isAbsolute", "isExact", "isFixedEnergy", "isPowerConstant", "isEnergyFree", "hasFixedEnergy");

        Class<?> curr = machine.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                String fName = f.getName().toLowerCase();
                try {
                    f.setAccessible(true);
                    if (maxPar <= 0 && (fName.equals("maxparallel") || fName.equals("currentparallel") || fName.equals("parallel"))) {
                        Object v = f.get(machine);
                        if (v instanceof Number n && n.intValue() > 0) {
                            maxPar = n.intValue();
                        }
                    }
                    if (!isAbsolute && (fName.equals("isabsolute") || fName.equals("isfixedenergy") || fName.equals("powerconstant") || fName.equals("energyfree"))) {
                        Object v = f.get(machine);
                        if (v instanceof Boolean b && b) {
                            isAbsolute = true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            curr = curr.getSuperclass();
        }

        if (!isAbsolute) {
            try {
                Class<?> baseHatchCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine");
                if (machine.getClass() != baseHatchCls) {
                    for (Method m : machine.getClass().getMethods()) {
                        if (m.getName().equals("modifyRecipe") && m.getDeclaringClass() != baseHatchCls) {
                            isAbsolute = true;
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (maxPar > 0) {
            return new ParallelStats(maxPar, isAbsolute);
        }

        return null;
    }

    private static Object getOrCreateDummyHolderProxy() {
        if (DUMMY_HOLDER_PROXY != null) {
            return DUMMY_HOLDER_PROXY;
        }
        try {
            Class<?> holderCls = Class.forName("com.gregtechceu.gtceu.api.machine.IMachineBlockEntity");
            DUMMY_HOLDER_PROXY = Proxy.newProxyInstance(
                    holderCls.getClassLoader(),
                    new Class<?>[]{holderCls},
                    (proxy, method, args) -> {
                        Class<?> ret = method.getReturnType();
                        if (ret == boolean.class) return false;
                        if (ret == int.class || ret == byte.class || ret == short.class) return 0;
                        if (ret == long.class) return 0L;
                        if (ret == float.class) return 0f;
                        if (ret == double.class) return 0d;
                        return null;
                    }
            );
            return DUMMY_HOLDER_PROXY;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int extractInt(Object target, String... methodNames) {
        if (target == null) return 0;
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object val = m.invoke(target);
                    if (val instanceof Number n && n.intValue() > 0) {
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
                    m.setAccessible(true);
                    Object val = m.invoke(target);
                    if (val instanceof Boolean b) {
                        return b;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void discoverGTCEuParallelHatches(java.util.List<MachineAddon> collector) {
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

                        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
                        if (!path.contains("parallel")) continue;

                        ParallelStats stats = extractStatsFromMachineDef(machineDef);
                        if (stats == null) {
                            stats = getParallelStats(id.toString());
                        }

                        if (stats != null && stats.maxParallel() > 1) {
                            registrySuccess = true;
                            ItemStack stack = getItemStackForDef(machineDef, id);
                            String name = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : formatDisplayName(id);
                            String desc = stats.isAbsolute()
                                    ? net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.absolute_parallel_hatch_desc", String.valueOf(stats.maxParallel())).getString()
                                    : net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(stats.maxParallel())).getString();

                            GTParallelHatchAddon addon = new GTParallelHatchAddon(id.toString(), name, desc, id, stats.maxParallel(), stats.isAbsolute());
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

        if (!registrySuccess || collector.stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.PARALLEL)) {
            registerDefaultParallelHatches(collector);
        }
    }

    public static void registerDefaultParallelHatches(java.util.List<MachineAddon> collector) {
        Object[][] tiers = new Object[][]{
                {"ev", "EV", 4},
                {"iv", "IV", 16},
                {"luv", "LuV", 64},
                {"zpm", "ZPM", 256},
                {"uv", "UV", 1024},
                {"uhv", "UHV", 4096},
                {"uev", "UEV", 16384},
                {"uiv", "UIV", 65536},
                {"uxv", "UXV", 262144},
                {"opv", "OpV", 1048576},
                {"max", "MAX", 4194304}
        };

        for (Object[] t : tiers) {
            String code = (String) t[0];
            String tierName = (String) t[1];
            int par = (int) t[2];

            ResourceLocation id = ResourceLocation.tryParse("gtceu:" + code + "_parallel_hatch");
            String name = tierName + " Parallel Control Hatch";
            String desc = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(par)).getString();

            GTParallelHatchAddon addon = new GTParallelHatchAddon(id.toString(), name, desc, id, par, false);
            if (!containsAddonId(collector, addon.getId())) {
                collector.add(addon);
            }
        }
    }

    private static boolean containsAddonId(java.util.List<MachineAddon> list, String id) {
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }

    private static ItemStack getItemStackForDef(Object machineDef, ResourceLocation id) {
        try {
            Method mAsItem = machineDef.getClass().getMethod("asItem");
            Object itemObj = mAsItem.invoke(machineDef);
            if (itemObj instanceof net.minecraft.world.item.Item item) {
                return new ItemStack(item);
            }
        } catch (Throwable ignored) {}

        if (ForgeRegistries.ITEMS != null) {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }
        return null;
    }

    private static String formatDisplayName(ResourceLocation id) {
        if (id == null) return "";
        String[] parts = id.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static GTParallelHatchAddon parseParallelHatch(ItemStack stack, ResourceLocation id) {
        ParallelStats stats = getParallelStats(stack);
        if (stats == null || stats.maxParallel() <= 1) {
            return null;
        }

        String name = stack.getHoverName().getString();
        int parallel = stats.maxParallel();
        boolean isAbsolute = stats.isAbsolute();

        String desc = isAbsolute
                ? net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.absolute_parallel_hatch_desc", String.valueOf(parallel)).getString()
                : net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(parallel)).getString();

        GTParallelHatchAddon addon = new GTParallelHatchAddon(id.toString(), name.isEmpty() ? id.getPath() : name, desc, id, parallel, isAbsolute);
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu MetaMachine Parallel Hatch API [" + id + "]");
        return addon;
    }
}

