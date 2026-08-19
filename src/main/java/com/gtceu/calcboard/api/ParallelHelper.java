package com.gtceu.calcboard.api;

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
 * Dynamically queries GTCEu Modern's MachineDefinition, MetaMachine, and IParallelHatch API
 * to extract exact parallel processing capacities directly from running code without any name or tooltip parsing.
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
            return ParallelStats.DEFAULT;
        }

        // 1. Direct NBT property check
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
     * Extracts exact parallel stats from a Block using GTCEu's MetaMachineBlock API.
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

        return ParallelStats.DEFAULT;
    }

    /**
     * Extracts exact parallel stats from a GTCEu MachineDefinition by creating a proxy MetaMachine
     * and calling its native getCurrentParallel() / getMaxParallel() methods.
     */
    public static ParallelStats extractStatsFromMachineDef(Object machineDef) {
        if (machineDef == null) return null;

        try {
            // 1. Primary: Instantiate MetaMachine via dummy holder proxy and invoke getCurrentParallel()
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

            // 2. Secondary: Direct getter on MachineDefinition if present
            int maxPar = extractInt(machineDef, "getCurrentParallel", "getMaxParallel", "getParallel", "getMaxParallelAmount");
            boolean isAbsolute = extractBoolean(machineDef, "isAbsolute", "isExact", "isFixedEnergy", "isPowerConstant");
            if (maxPar > 0) {
                return new ParallelStats(maxPar, isAbsolute);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Calls native instance methods (getCurrentParallel, getMaxParallel, isAbsolute) on a MetaMachine object.
     */
    public static ParallelStats extractStatsFromMachineInstance(Object machine) {
        if (machine == null) return null;

        // 1. Parallel capacity getter: getCurrentParallel(), getMaxParallel()
        int maxPar = extractInt(machine, "getCurrentParallel", "getMaxParallel", "getParallel", "getMaxParallelAmount");

        // 2. Direct boolean queries on MetaMachine methods
        boolean isAbsolute = extractBoolean(machine, "isAbsolute", "isExact", "isFixedEnergy", "isPowerConstant", "isEnergyFree", "hasFixedEnergy");

        // 3. Inspect declared fields for maxParallel, currentParallel, isAbsolute
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

        // 4. Pure Bytecode / RecipeModifier method inspection:
        // In vanilla GTCEu, ParallelHatchPartMachine implements standard recipe EUt scaling.
        // Specialized hatches (such as Absolute Mastery Hatches) override modifyRecipe to disable energy consumption.
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
