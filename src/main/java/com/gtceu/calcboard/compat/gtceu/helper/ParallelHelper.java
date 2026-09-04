package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParallelHelper {

    private static final Map<String, ParallelStats> STATS_CACHE = new ConcurrentHashMap<>();

    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field MACHINES_FIELD;
    private static final Class<?> HOLDER_CLS;
    private static final Class<?> BASE_HATCH_CLS;
    private static volatile Object DUMMY_HOLDER_PROXY = null;

    static {
        ClassLoader cl = ParallelHelper.class.getClassLoader();
        Class<?> gtRegs = null;
        Field mField = null;
        try {
            gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false, cl);
            mField = gtRegs.getField("MACHINES");
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        Class<?> hCls = null;
        try {
            hCls = Class.forName("com.gregtechceu.gtceu.api.machine.IMachineBlockEntity", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        Class<?> bhCls = null;
        try {
            bhCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        GT_REGISTRIES_CLS = gtRegs;
        MACHINES_FIELD = mField;
        HOLDER_CLS = hCls;
        BASE_HATCH_CLS = bhCls;
    }

    public record ParallelStats(int maxParallel, boolean isAbsolute) {
        public static final ParallelStats DEFAULT = new ParallelStats(4, false);
    }

    public static ParallelStats getParallelStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ParallelStats nbtStats = extractStatsFromNbt(stack);
        if (nbtStats != null) {
            return nbtStats;
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            return getParallelStats(blockItem.getBlock());
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null ? getParallelStats(id.toString()) : null;
    }

    private static ParallelStats extractStatsFromNbt(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;

        if (tag.contains("Parallel")) {
            int p = tag.getInt("Parallel");
            if (p > 0) return new ParallelStats(p, tag.getBoolean("IsAbsolute"));
        } else if (tag.contains("MaxParallel")) {
            int p = tag.getInt("MaxParallel");
            if (p > 0) return new ParallelStats(p, tag.getBoolean("IsAbsolute"));
        }
        return null;
    }

    public static ParallelStats getParallelStats(Block block) {
        if (block == null) {
            return null;
        }

        ParallelStats blockStats = extractStatsFromBlockClass(block);
        if (blockStats != null) {
            return blockStats;
        }

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null ? getParallelStats(id.toString()) : null;
    }

    private static ParallelStats extractStatsFromBlockClass(Block block) {
        for (Method m : block.getClass().getMethods()) {
            if ((m.getName().equalsIgnoreCase("getMachineDefinition") || m.getName().equalsIgnoreCase("getDefinition")) && m.getParameterCount() == 0) {
                try {
                    Object def = m.invoke(block);
                    if (def != null) {
                        ParallelStats s = extractStatsFromMachineDef(def);
                        if (s != null) return s;
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
        }
        return null;
    }

    public static ParallelStats getParallelStats(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }

        String key = sanitizeParallelKey(identifier);
        return STATS_CACHE.computeIfAbsent(key, ParallelHelper::computeParallelStats);
    }

    private static String sanitizeParallelKey(String identifier) {
        String key = identifier.toLowerCase().trim();
        return key.startsWith("gtceu:") ? key.substring("gtceu:".length()) : key;
    }

    private static ParallelStats computeParallelStats(String identifier) {
        ResourceLocation id = ResourceLocation.tryParse(identifier.contains(":") ? identifier : "gtceu:" + identifier);
        if (id == null || MACHINES_FIELD == null) {
            return null;
        }

        try {
            Object machinesRegistry = MACHINES_FIELD.get(null);
            if (machinesRegistry == null) return null;

            for (Method m : machinesRegistry.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == ResourceLocation.class) {
                    Object machineDef = m.invoke(machinesRegistry, id);
                    if (machineDef != null) {
                        return extractStatsFromMachineDef(machineDef);
                    }
                    break;
                }
            }
        } catch (ReflectiveOperationException ignored) {}

        return null;
    }

    public static ParallelStats extractStatsFromMachineDef(Object machineDef) {
        if (machineDef == null) return null;

        Object dummyHolder = getOrCreateDummyHolderProxy();
        if (dummyHolder != null) {
            for (Method m : machineDef.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && (m.getName().equals("createMetaMachine") || m.getName().equals("createMachine"))) {
                    try {
                        Object machine = m.invoke(machineDef, dummyHolder);
                        if (machine != null) {
                            ParallelStats stats = extractStatsFromMachineInstance(machine);
                            if (stats != null) return stats;
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }

        int maxPar = extractInt(machineDef, "getCurrentParallel", "getMaxParallel", "getParallel", "getMaxParallelAmount");
        boolean isAbsolute = extractBoolean(machineDef, "isAbsolute", "isExact", "isFixedEnergy", "isPowerConstant");
        return maxPar > 0 ? new ParallelStats(maxPar, isAbsolute) : null;
    }

    public static ParallelStats extractStatsFromMachineInstance(Object machine) {
        if (machine == null) return null;

        int maxPar = extractInt(machine, "getCurrentParallel", "getMaxParallel", "getParallel", "getMaxParallelAmount");
        boolean isAbsolute = extractBoolean(machine, "isAbsolute", "isExact", "isFixedEnergy", "isPowerConstant", "isEnergyFree", "hasFixedEnergy");

        maxPar = inspectInstanceFieldsForParallel(machine, maxPar);
        isAbsolute = inspectInstanceFieldsForAbsolute(machine, isAbsolute);

        if (!isAbsolute && BASE_HATCH_CLS != null && machine.getClass() != BASE_HATCH_CLS) {
            isAbsolute = checkRecipeModifierOverride(machine);
        }

        return maxPar > 0 ? new ParallelStats(maxPar, isAbsolute) : null;
    }

    private static int inspectInstanceFieldsForParallel(Object machine, int currentMaxPar) {
        if (currentMaxPar > 0) return currentMaxPar;
        Class<?> curr = machine.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                String fName = f.getName().toLowerCase(Locale.ROOT);
                if (fName.equals("maxparallel") || fName.equals("currentparallel") || fName.equals("parallel")) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(machine);
                        if (v instanceof Number n && n.intValue() > 0) {
                            return n.intValue();
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
            curr = curr.getSuperclass();
        }
        return currentMaxPar;
    }

    private static boolean inspectInstanceFieldsForAbsolute(Object machine, boolean currentAbsolute) {
        if (currentAbsolute) return true;
        Class<?> curr = machine.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                String fName = f.getName().toLowerCase(Locale.ROOT);
                if (fName.equals("isabsolute") || fName.equals("isfixedenergy") || fName.equals("powerconstant") || fName.equals("energyfree")) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(machine);
                        if (v instanceof Boolean b && b) {
                            return true;
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
            curr = curr.getSuperclass();
        }
        return currentAbsolute;
    }

    private static boolean checkRecipeModifierOverride(Object machine) {
        for (Method m : machine.getClass().getMethods()) {
            if (m.getName().equals("modifyRecipe") && m.getDeclaringClass() != BASE_HATCH_CLS) {
                return true;
            }
        }
        return false;
    }

    private static Object getOrCreateDummyHolderProxy() {
        if (DUMMY_HOLDER_PROXY != null) {
            return DUMMY_HOLDER_PROXY;
        }
        if (HOLDER_CLS == null) {
            return null;
        }
        synchronized (ParallelHelper.class) {
            if (DUMMY_HOLDER_PROXY == null) {
                try {
                    DUMMY_HOLDER_PROXY = Proxy.newProxyInstance(
                            HOLDER_CLS.getClassLoader(),
                            new Class<?>[]{HOLDER_CLS},
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
                } catch (IllegalArgumentException | SecurityException ignored) {}
            }
        }
        return DUMMY_HOLDER_PROXY;
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
            } catch (ReflectiveOperationException | LinkageError ignored) {}
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
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return false;
    }

    public static void discoverGTCEuParallelHatches(List<MachineAddon> collector) {
        if (collector == null) return;

        boolean registrySuccess = scanMachinesFromRegistry(collector);
        if (!registrySuccess || collector.stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.PARALLEL)) {
            registerDefaultParallelHatches(collector);
        }
    }

    private static boolean scanMachinesFromRegistry(List<MachineAddon> collector) {
        if (MACHINES_FIELD == null) return false;
        boolean foundAny = false;
        try {
            Object machinesRegistry = MACHINES_FIELD.get(null);
            if (machinesRegistry instanceof Iterable<?> iterable) {
                for (Object machineDef : iterable) {
                    if (machineDef == null) continue;
                    if (processParallelMachineDefinition(machineDef, collector)) {
                        foundAny = true;
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return foundAny;
    }

    private static boolean processParallelMachineDefinition(Object machineDef, List<MachineAddon> collector) {
        try {
            Method mGetId = machineDef.getClass().getMethod("getId");
            ResourceLocation id = (ResourceLocation) mGetId.invoke(machineDef);
            if (id == null) return false;

            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (!path.contains("parallel")) return false;

            ParallelStats stats = extractStatsFromMachineDef(machineDef);
            if (stats == null) {
                stats = getParallelStats(id.toString());
            }

            if (stats != null && stats.maxParallel() > 1) {
                ItemStack stack = getItemStackForDef(machineDef, id);
                String name = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : formatDisplayName(id);
                String desc = stats.isAbsolute()
                        ? Component.translatable("gui.gtcalcboard.addon.absolute_parallel_hatch_desc", String.valueOf(stats.maxParallel())).getString()
                        : Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(stats.maxParallel())).getString();

                GTParallelHatchAddon addon = new GTParallelHatchAddon(id.toString(), name, desc, id, stats.maxParallel(), stats.isAbsolute());
                if (stack != null) addon.setItemStackSample(stack);
                addon.setDiscoverySource("GTCEu Machine Registry [" + id + "]");
                if (!containsAddonId(collector, addon.getId())) {
                    collector.add(addon);
                }
                return true;
            }
        } catch (ReflectiveOperationException ignored) {}
        return false;
    }

    public static void registerDefaultParallelHatches(List<MachineAddon> collector) {
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
            String desc = Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(par)).getString();

            GTParallelHatchAddon addon = new GTParallelHatchAddon(id.toString(), name, desc, id, par, false);
            if (!containsAddonId(collector, addon.getId())) {
                collector.add(addon);
            }
        }
    }

    private static boolean containsAddonId(List<MachineAddon> list, String id) {
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }

    private static ItemStack getItemStackForDef(Object machineDef, ResourceLocation id) {
        try {
            Method mAsItem = machineDef.getClass().getMethod("asItem");
            Object itemObj = mAsItem.invoke(machineDef);
            if (itemObj instanceof Item item) {
                return new ItemStack(item);
            }
        } catch (ReflectiveOperationException ignored) {}

        if (ForgeRegistries.ITEMS != null) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
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
                ? Component.translatable("gui.gtcalcboard.addon.absolute_parallel_hatch_desc", String.valueOf(parallel)).getString()
                : Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(parallel)).getString();

        GTParallelHatchAddon addon = new GTParallelHatchAddon(id.toString(), name.isEmpty() ? id.getPath() : name, desc, id, parallel, isAbsolute);
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu MetaMachine Parallel Hatch API [" + id + "]");
        return addon;
    }
}

