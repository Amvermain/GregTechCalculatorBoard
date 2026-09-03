package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public final class GTCEuReflectionBridge {

    private static final Class<?> MULTIBLOCK_DEF_CLASS;
    private static final List<Class<?>> MULTIBLOCK_MACHINE_CLASSES;
    private static final Class<?> COIL_WORKABLE_CLASS;
    private static final Class<?> LARGE_TURBINE_CLASS;
    private static final Class<?> I_TURBINE_CLASS;
    private static final Class<?> MUFFLER_PART_CLASS;
    private static final Class<?> MAINTENANCE_HATCH_CLASS;
    private static final Class<?> CONFIGURABLE_MAINT_HATCH_CLASS;
    private static final List<Class<?>> STEAM_MACHINE_CLASSES;

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
    private static final Method GET_RECIPE_MODIFIER_METHOD;
    private static final Field PAGINATED_TOOLTIPS_FIELD;
    private static final Method IS_GENERATOR_METHOD;
    private static final Field GENERATOR_FIELD;
    private static final Map<Object, String> RECIPE_MODIFIER_NAMES;
    private static final Map<String, Method> METHOD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        MULTIBLOCK_DEF_CLASS = loadClassQuietly("com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition");
        List<Class<?>> mbList = new ArrayList<>();
        for (String cName : new String[]{
                "com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController",
                "com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine",
                "com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine",
                "com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine",
                "com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiMachine",
                "com.gregtechceu.gtceu.api.machine.MultiblockMachine",
                "com.gregtechceu.gtceu.api.machine.multiblock.MultiblockMachine",
                "com.gregtechceu.gtceu.api.machine.IMultiblockMachine"
        }) {
            Class<?> loaded = loadClassQuietly(cName);
            if (loaded != null) mbList.add(loaded);
        }
        MULTIBLOCK_MACHINE_CLASSES = Collections.unmodifiableList(mbList);

        List<Class<?>> steamList = new ArrayList<>();
        for (String cName : new String[]{
                "com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine",
                "com.gregtechceu.gtceu.api.machine.steam.SteamWorkableTieredMachine",
                "com.gregtechceu.gtceu.api.machine.steam.SteamProgressMachine",
                "com.gregtechceu.gtceu.api.machine.steam.SteamMinerMachine",
                "com.gregtechceu.gtceu.api.machine.steam.SteamBoilerMachine"
        }) {
            Class<?> loaded = loadClassQuietly(cName);
            if (loaded != null) steamList.add(loaded);
        }
        STEAM_MACHINE_CLASSES = Collections.unmodifiableList(steamList);

        Class<?> coilCls = loadClassQuietly("com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine");
        if (coilCls == null) {
            coilCls = loadClassQuietly("com.gregtechceu.gtceu.common.machine.multiblock.electric.CoilWorkableElectricMultiblockMachine");
        }
        COIL_WORKABLE_CLASS = coilCls;
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
        GET_RECIPE_MODIFIER_METHOD = findMethod(targetDefClass, "getRecipeModifier");
        PAGINATED_TOOLTIPS_FIELD = findField(targetDefClass, "paginatedTooltips");
        Class<?> mbDefCls = MULTIBLOCK_DEF_CLASS != null ? MULTIBLOCK_DEF_CLASS : targetDefClass;
        IS_GENERATOR_METHOD = findMethod(mbDefCls, "isGenerator");
        GENERATOR_FIELD = findField(mbDefCls, "generator");

        Map<Object, String> modNames = new IdentityHashMap<>();
        Class<?> gtRecipeModifiersCls = loadClassQuietly("com.gregtechceu.gtceu.common.data.GTRecipeModifiers");
        if (gtRecipeModifiersCls != null) {
            for (Field f : gtRecipeModifiersCls.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    try {
                        Object mod = f.get(null);
                        if (mod != null) {
                            modNames.put(mod, f.getName().toUpperCase(Locale.ROOT));
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
        RECIPE_MODIFIER_NAMES = Collections.unmodifiableMap(modNames);
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

    public record TurbineSpecs(GTVoltageTier tier, double baseEnergy) {}

    public static TurbineSpecs deductTurbineSpecs(Object def) {
        if (def == null) {
            return new TurbineSpecs(GTVoltageTier.HV, 1024.0);
        }

        GTVoltageTier foundTier = null;
        double foundBaseEnergy = 0.0;

        if (PAGINATED_TOOLTIPS_FIELD != null) {
            try {
                Object raw = PAGINATED_TOOLTIPS_FIELD.get(def);
                if (raw instanceof List<?> pages) {
                    for (Object pageObj : pages) {
                        if (pageObj instanceof List<?> compList) {
                            for (Object compObj : compList) {
                                if (compObj instanceof net.minecraft.network.chat.Component comp) {
                                    if (comp.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                                        String key = tc.getKey();
                                        if ("gtceu.universal.tooltip.base_production_eut".equals(key) && tc.getArgs().length > 0) {
                                            Object arg = tc.getArgs()[0];
                                            if (arg instanceof Number num) {
                                                foundBaseEnergy = num.doubleValue();
                                                long voltage = Math.round(foundBaseEnergy / 2.0);
                                                foundTier = GTVoltageTier.fromVoltage(voltage);
                                            }
                                        } else if ("gtceu.multiblock.turbine.efficiency_tooltip".equals(key) && tc.getArgs().length > 0) {
                                            if (foundTier == null) {
                                                Object arg = tc.getArgs()[0];
                                                String tierName = (arg instanceof net.minecraft.network.chat.Component c) ? c.getString() : String.valueOf(arg);
                                                foundTier = GTVoltageTier.fromName(tierName);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (foundTier == null) {
            foundTier = getMachineTier(def);
        }

        if (foundTier == null || foundBaseEnergy <= 0.0) {
            ResourceLocation id = getMachineId(def);
            if (id != null) {
                if (foundTier == null) {
                    foundTier = com.gtceu.calcboard.api.catalog.MultiblockDetector.getTurbineBaseTier(id);
                }
                if (foundBaseEnergy <= 0.0) {
                    foundBaseEnergy = com.gtceu.calcboard.api.catalog.MultiblockDetector.getTurbineBaseProduction(id);
                }
            }
        }

        if (foundTier == null) foundTier = GTVoltageTier.HV;
        if (foundBaseEnergy <= 0.0) foundBaseEnergy = (double) (foundTier.getVoltage() * 2L);

        return new TurbineSpecs(foundTier, foundBaseEnergy);
    }

    public static boolean isGenerator(Object def) {
        if (def == null) return false;
        if (IS_GENERATOR_METHOD != null) {
            Boolean res = invokeMethodQuietly(IS_GENERATOR_METHOD, def, Boolean.class);
            if (res != null) return res;
        }
        if (GENERATOR_FIELD != null) {
            try {
                return GENERATOR_FIELD.getBoolean(def);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean hasTurbineSignature(Object def) {
        if (def == null) return false;
        if (isGenerator(def)) return true;

        if (PAGINATED_TOOLTIPS_FIELD != null) {
            try {
                Object raw = PAGINATED_TOOLTIPS_FIELD.get(def);
                if (raw instanceof List<?> pages) {
                    for (Object pageObj : pages) {
                        if (pageObj instanceof List<?> compList) {
                            for (Object compObj : compList) {
                                if (compObj instanceof net.minecraft.network.chat.Component comp) {
                                    if (comp.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                                        String key = tc.getKey();
                                        if ("gtceu.multiblock.turbine.efficiency_tooltip".equals(key)
                                                || "gtceu.universal.tooltip.base_production_eut".equals(key)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        String modName = getRecipeModifierName(def);
        if (modName != null && modName.toUpperCase(Locale.ROOT).contains("TURBINE")) {
            return true;
        }

        Class<?> mCls = getMachineClass(def);
        if (mCls != null && (isLargeTurbineClass(mCls) || isITurbineClass(mCls))) {
            return true;
        }

        ResourceLocation id = getMachineId(def);
        return id != null && com.gtceu.calcboard.api.catalog.MultiblockDetector.isTurbine(id);
    }

    public static boolean isSteamMachine(Object def) {
        if (def == null) return false;
        Class<?> mCls = getMachineClass(def);
        if (mCls != null) {
            for (Class<?> steamCls : STEAM_MACHINE_CLASSES) {
                if (steamCls.isAssignableFrom(mCls)) {
                    return true;
                }
            }
        }
        for (Class<?> steamCls : STEAM_MACHINE_CLASSES) {
            if (steamCls.isInstance(def)) {
                return true;
            }
        }
        return false;
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
        if (MULTIBLOCK_DEF_CLASS != null && MULTIBLOCK_DEF_CLASS.isInstance(def)) return true;
        Boolean res = invokeMethodQuietly(IS_MULTIBLOCK_METHOD, def, Boolean.class);
        return Boolean.TRUE.equals(res);
    }

    public static List<Object> getRecipeModifiers(Object def) {
        if (def == null) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        Object rawMod = invokeMethodQuietly(GET_RECIPE_MODIFIER_METHOD, def, Object.class);
        if (rawMod == null) {
            rawMod = invokeMethodQuietly(GET_RECIPE_MODIFIERS_METHOD, def, Object.class);
        }
        if (rawMod == null) {
            Method m = findMethod(def.getClass(), "getRecipeModifier");
            if (m == null) m = findMethod(def.getClass(), "getRecipeModifiers");
            if (m != null) rawMod = invokeMethodQuietly(m, def, Object.class);
        }
        if (rawMod == null) {
            Field modField = findField(def.getClass(), "recipeModifier");
            if (modField != null) {
                try {
                    modField.setAccessible(true);
                    rawMod = modField.get(def);
                } catch (Throwable ignored) {}
            }
        }
        flattenRecipeModifiers(rawMod, result);
        return result;
    }

    private static void flattenRecipeModifiers(Object modifier, List<Object> collector) {
        if (modifier == null) return;
        if (modifier instanceof Object[] arr) {
            for (Object m : arr) {
                flattenRecipeModifiers(m, collector);
            }
            return;
        }
        if (modifier instanceof Iterable<?> iterable) {
            for (Object m : iterable) {
                flattenRecipeModifiers(m, collector);
            }
            return;
        }
        try {
            Method modifiersMethod = findMethod(modifier.getClass(), "modifiers");
            if (modifiersMethod != null) {
                Object innerArr = modifiersMethod.invoke(modifier);
                if (innerArr != null && innerArr != modifier) {
                    flattenRecipeModifiers(innerArr, collector);
                    return;
                }
            }
            Field modifiersField = findField(modifier.getClass(), "modifiers");
            if (modifiersField != null) {
                modifiersField.setAccessible(true);
                Object innerArr = modifiersField.get(modifier);
                if (innerArr != null && innerArr != modifier) {
                    flattenRecipeModifiers(innerArr, collector);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        collector.add(modifier);
    }

    public static String getRecipeModifierName(Object modifier) {
        if (modifier == null) return null;
        String name = RECIPE_MODIFIER_NAMES.get(modifier);
        if (name != null) return name;
        String starTName = com.gtceu.calcboard.compat.start.StarTReflectionBridge.getRecipeModifierName(modifier);
        if (starTName != null) return starTName;
        try {
            Method mGetId = findMethod(modifier.getClass(), "getId");
            if (mGetId != null) {
                Object id = mGetId.invoke(modifier);
                if (id != null) return id.toString().toUpperCase(Locale.ROOT);
            }
            Field idField = findField(modifier.getClass(), "id");
            if (idField != null) {
                idField.setAccessible(true);
                Object id = idField.get(modifier);
                if (id != null) return id.toString().toUpperCase(Locale.ROOT);
            }
            Field delegateField = findField(modifier.getClass(), "delegate");
            if (delegateField != null) {
                delegateField.setAccessible(true);
                Object delegate = delegateField.get(modifier);
                if (delegate != null && delegate != modifier) {
                    String delName = getRecipeModifierName(delegate);
                    if (delName != null) return delName;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isMultiblockClass(Class<?> cls) {
        if (cls == null) return false;
        for (Class<?> mbCls : MULTIBLOCK_MACHINE_CLASSES) {
            if (mbCls.isAssignableFrom(cls)) return true;
        }
        return false;
    }

    public static boolean isCoilWorkableClass(Class<?> cls) {
        if (cls == null || COIL_WORKABLE_CLASS == null) return false;
        return COIL_WORKABLE_CLASS.isAssignableFrom(cls);
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
        if (targetClass == null || methodName == null) return null;
        String key = targetClass.getName() + "#" + methodName;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> current = targetClass;
        while (current != null && current != Object.class) {
            try {
                Method m = current.getDeclaredMethod(methodName);
                m.setAccessible(true);
                METHOD_CACHE.put(key, m);
                return m;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
        return null;
    }

    private static Field findField(Class<?> targetClass, String fieldName) {
        if (targetClass == null || fieldName == null) return null;
        Class<?> current = targetClass;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
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
