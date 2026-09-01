package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GTCEuReflectionBridge {

    private static final Class<?> MULTIBLOCK_DEF_CLASS;
    private static final Class<?> COIL_WORKABLE_CLASS;
    private static final Class<?> LARGE_TURBINE_CLASS;
    private static final Class<?> I_TURBINE_CLASS;
    private static final Class<?> MUFFLER_PART_CLASS;
    private static final Class<?> MAINTENANCE_HATCH_CLASS;
    private static final Class<?> CONFIGURABLE_MAINT_HATCH_CLASS;

    private static final Field MACHINES_REGISTRY_FIELD;
    private static final Method REGISTRY_GET_METHOD;
    private static final Method GET_ID_METHOD;
    private static final Method GET_MACHINE_CLASS_METHOD;
    private static final Method GET_TIER_METHOD;
    private static final Method GET_BASE_ENERGY_METHOD;
    private static final Method GET_RECIPE_TYPES_METHOD;
    private static final Method GET_RECIPE_TYPE_METHOD;
    private static final Method IS_MULTIBLOCK_METHOD;
    private static final Method GET_RECIPE_MODIFIERS_METHOD;

    static {
        MULTIBLOCK_DEF_CLASS = loadClassQuietly("com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition");
        COIL_WORKABLE_CLASS = loadClassQuietly("com.gregtechceu.gtceu.common.machine.multiblock.electric.CoilWorkableElectricMultiblockMachine");
        LARGE_TURBINE_CLASS = loadClassQuietly("com.gregtechceu.gtceu.common.machine.multiblock.generator.LargeTurbineMachine");
        I_TURBINE_CLASS = loadClassQuietly("com.gregtechceu.gtceu.api.machine.feature.multiblock.ITurbineMachine");

        Class<?> mPart = loadClassQuietly("com.gregtechceu.gtceu.common.machine.multiblock.part.MufflerPartMachine");
        MUFFLER_PART_CLASS = mPart != null ? mPart : loadClassQuietly("com.gregtechceu.gtceu.common.machine.multiblock.part.MufflerHatchPartMachine");
        MAINTENANCE_HATCH_CLASS = loadClassQuietly("com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine");
        CONFIGURABLE_MAINT_HATCH_CLASS = loadClassQuietly("com.gregtechceu.gtceu.common.machine.multiblock.part.ConfigurableMaintenanceHatchPartMachine");

        MACHINES_REGISTRY_FIELD = findRegistryField();
        REGISTRY_GET_METHOD = findRegistryGetMethod();

        Class<?> machineDefClass = loadClassQuietly("com.gregtechceu.gtceu.api.machine.MachineDefinition");
        Class<?> targetDefClass = machineDefClass != null ? machineDefClass : MULTIBLOCK_DEF_CLASS;

        GET_ID_METHOD = findMethod(targetDefClass, "getId");
        GET_MACHINE_CLASS_METHOD = findMethod(targetDefClass, "getMachineClass");
        GET_TIER_METHOD = findMethod(targetDefClass, "getTier");
        GET_BASE_ENERGY_METHOD = findMethod(targetDefClass, "getBaseEnergyPerTick");
        GET_RECIPE_TYPES_METHOD = findMethod(targetDefClass, "getRecipeTypes");
        GET_RECIPE_TYPE_METHOD = findMethod(targetDefClass, "getRecipeType");
        IS_MULTIBLOCK_METHOD = findMethod(targetDefClass, "isMultiblock");
        GET_RECIPE_MODIFIERS_METHOD = findMethod(targetDefClass, "getRecipeModifiers");
    }

    private GTCEuReflectionBridge() {}

    public static Iterable<?> getMachinesRegistryIterable() {
        if (MACHINES_REGISTRY_FIELD == null) return null;
        try {
            Object registry = MACHINES_REGISTRY_FIELD.get(null);
            if (registry == null) return null;
            Method valuesMethod = registry.getClass().getMethod("values");
            Object result = valuesMethod.invoke(registry);
            if (result instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object getMachineDefinition(ResourceLocation id) {
        if (id == null || MACHINES_REGISTRY_FIELD == null) return null;
        try {
            Object registry = MACHINES_REGISTRY_FIELD.get(null);
            if (registry == null) return null;
            if (REGISTRY_GET_METHOD != null) {
                return REGISTRY_GET_METHOD.invoke(registry, id);
            }
            Method getMethod = registry.getClass().getMethod("get", ResourceLocation.class);
            return getMethod.invoke(registry, id);
        } catch (Throwable ignored) {}
        return null;
    }

    public static ResourceLocation getMachineId(Object def) {
        if (def == null) return null;
        return invokeMethodQuietly(GET_ID_METHOD, def, ResourceLocation.class);
    }

    public static Class<?> getMachineClass(Object def) {
        if (def == null) return null;
        return invokeMethodQuietly(GET_MACHINE_CLASS_METHOD, def, Class.class);
    }

    public static GTVoltageTier getMachineTier(Object def) {
        if (def == null) return null;
        Object val = invokeMethodQuietly(GET_TIER_METHOD, def, Object.class);
        if (val instanceof Number num) {
            int idx = num.intValue();
            return (idx > 0 && idx < GTVoltageTier.values().length) ? GTVoltageTier.values()[idx] : null;
        }
        if (val instanceof Enum<?> en) {
            try {
                GTVoltageTier parsed = GTVoltageTier.valueOf(en.name());
                return parsed != GTVoltageTier.ULV ? parsed : null;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static double getTurbineBaseEnergy(Object def, GTVoltageTier tier) {
        double defaultEnergy = tier != null ? (double) (tier.getVoltage() * 2L) : 1024.0;
        if (def == null) return defaultEnergy;
        Object val = invokeMethodQuietly(GET_BASE_ENERGY_METHOD, def, Object.class);
        if (val instanceof Number num && num.doubleValue() > 0) {
            return num.doubleValue();
        }
        return defaultEnergy;
    }

    public static List<Object> getRecipeTypes(Object def) {
        if (def == null) return Collections.emptyList();
        List<Object> list = new ArrayList<>();
        Object[] array = invokeMethodQuietly(GET_RECIPE_TYPES_METHOD, def, Object[].class);
        if (array != null) {
            Collections.addAll(list, array);
        }
        if (list.isEmpty()) {
            Object single = invokeMethodQuietly(GET_RECIPE_TYPE_METHOD, def, Object.class);
            if (single != null) {
                list.add(single);
            }
        }
        return list;
    }

    public static boolean isMultiblockDefinition(Object def) {
        if (def == null) return false;
        Boolean res = invokeMethodQuietly(IS_MULTIBLOCK_METHOD, def, Boolean.class);
        return Boolean.TRUE.equals(res);
    }

    public static Object[] getRecipeModifiers(Object def) {
        if (def == null) return null;
        return invokeMethodQuietly(GET_RECIPE_MODIFIERS_METHOD, def, Object[].class);
    }

    public static boolean isCoilWorkableClass(Class<?> cls) {
        return COIL_WORKABLE_CLASS != null && cls != null && COIL_WORKABLE_CLASS.isAssignableFrom(cls);
    }

    public static boolean isLargeTurbineClass(Class<?> cls) {
        return LARGE_TURBINE_CLASS != null && cls != null && LARGE_TURBINE_CLASS.isAssignableFrom(cls);
    }

    public static boolean isITurbineClass(Class<?> cls) {
        return I_TURBINE_CLASS != null && cls != null && I_TURBINE_CLASS.isAssignableFrom(cls);
    }

    public static int getDefaultParallel(Object def) {
        if (def == null) return 1;
        for (String mName : new String[]{"getParallelAmount", "getSteamParallel", "getBaseParallel", "getParallels", "getParallelCount", "getDefaultParallel"}) {
            Method m = findMethod(def.getClass(), mName);
            Object pVal = invokeMethodQuietly(m, def, Object.class);
            if (pVal instanceof Number num && num.intValue() > 1) {
                return num.intValue();
            }
        }
        return 1;
    }

    public static double getSteamDrainRate(Object def) {
        if (def == null) return 64.0;
        for (String mName : new String[]{"getSteamDrainRate", "getSteamDrain", "getSteamConsumption", "getSteamPerTick", "getSteamRate", "getBaseSteamRate", "getConversionRate"}) {
            Method m = findMethod(def.getClass(), mName);
            Object sVal = invokeMethodQuietly(m, def, Object.class);
            if (sVal instanceof Number num && num.doubleValue() > 0) {
                return num.doubleValue();
            }
        }
        return 64.0;
    }

    public static Object getBlockMachineDefinition(net.minecraft.world.level.block.Block block) {
        if (block == null) return null;
        try {
            Method m = block.getClass().getMethod("getDefinition");
            return m.invoke(block);
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isMufflerMachineClass(Class<?> cls) {
        return MUFFLER_PART_CLASS != null && cls != null && MUFFLER_PART_CLASS.isAssignableFrom(cls);
    }

    public static boolean isMaintenanceMachineClass(Class<?> cls) {
        return MAINTENANCE_HATCH_CLASS != null && cls != null && MAINTENANCE_HATCH_CLASS.isAssignableFrom(cls);
    }

    public static boolean isConfigurableMaintenanceMachineClass(Class<?> cls) {
        return CONFIGURABLE_MAINT_HATCH_CLASS != null && cls != null && CONFIGURABLE_MAINT_HATCH_CLASS.isAssignableFrom(cls);
    }

    private static Class<?> loadClassQuietly(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findRegistryField() {
        try {
            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            return gtRegistriesCls.getField("MACHINES");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findRegistryGetMethod() {
        try {
            if (MACHINES_REGISTRY_FIELD == null) return null;
            Object registry = MACHINES_REGISTRY_FIELD.get(null);
            if (registry != null) {
                return registry.getClass().getMethod("get", ResourceLocation.class);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Method findMethod(Class<?> targetClass, String methodName) {
        if (targetClass == null) return null;
        try {
            return targetClass.getMethod(methodName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeMethodQuietly(Method method, Object target, Class<T> returnType) {
        if (method == null || target == null) return null;
        try {
            Object result = method.invoke(target);
            if (result != null && returnType.isInstance(result)) {
                return (T) result;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
