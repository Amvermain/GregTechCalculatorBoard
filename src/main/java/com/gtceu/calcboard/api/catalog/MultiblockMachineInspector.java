package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTThreadingHelix;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Low-level machine reflection inspector and modifier analyzer for multiblock detection.
 */
public final class MultiblockMachineInspector {

    private static final Class<?> COIL_WORKABLE_CLS;
    private static final Class<?> THREADING_CAPABLE_CLS;
    private static final Class<?> GT_MODIFIERS_CLS;
    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field RECIPE_TYPES_FIELD;

    static {
        ClassLoader cl = MultiblockMachineInspector.class.getClassLoader();
        Class<?> coilCls = null;
        try {
            coilCls = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            try {
                coilCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.electric.CoilWorkableElectricMultiblockMachine", false, cl);
            } catch (ReflectiveOperationException | LinkageError ignored2) {}
        }
        COIL_WORKABLE_CLS = coilCls;

        Class<?> threadCls = null;
        try {
            threadCls = Class.forName("com.startechnology.start_core.machine.threading.StarTThreadingCapableMachine", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        THREADING_CAPABLE_CLS = threadCls;

        Class<?> modCls = null;
        try {
            modCls = Class.forName("com.gregtechceu.gtceu.api.recipe.modifier.GTRecipeModifiers", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        GT_MODIFIERS_CLS = modCls;

        Class<?> gtRegs = null;
        Field rtField = null;
        try {
            gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false, cl);
            rtField = gtRegs.getField("RECIPE_TYPES");
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        GT_REGISTRIES_CLS = gtRegs;
        RECIPE_TYPES_FIELD = rtField;
    }

    private MultiblockMachineInspector() {}

    public static boolean inspectAndRegisterMachine(ResourceLocation id, Object def, ResourceLocation recipeCategoryId) {
        if (id == null) return false;
        if (def == null) {
            return registerFallbackFromCatalog(id);
        }

        Class<?> cls = def.getClass();
        Class<?> mCls = extractMachineClass(cls, def);
        boolean isMb = isMultiblockDefinition(cls, def, mCls, id);

        if (isMb) {
            MultiblockDetector.registerMultiblock(id);
            detectAndRegisterCoilMultiblock(id, mCls, recipeCategoryId);
            detectAndRegisterParallelAndBatch(id, def, cls);
            detectAndRegisterLaserHatch(id);
            detectAndRegisterThreading(id, def, cls, mCls);
        }

        return isMb;
    }

    private static boolean registerFallbackFromCatalog(ResourceLocation id) {
        if (!MultiblockDetector.isMultiblock(id) && MultiblockStructureCatalog.getStructure(id) == null) {
            return false;
        }
        MultiblockDetector.registerMultiblock(id);
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct != null && defStruct.supportsAbility("PARALLEL_HATCH")) {
            MultiblockDetector.registerParallelHatchController(id);
        }
        return true;
    }

    public static Class<?> extractMachineClass(Class<?> cls, Object def) {
        try {
            Method mGetMachineClass = cls.getMethod("getMachineClass");
            mGetMachineClass.setAccessible(true);
            return (Class<?>) mGetMachineClass.invoke(def);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean isMultiblockDefinition(Class<?> cls, Object def, Class<?> mCls, ResourceLocation id) {
        String clsName = cls.getName().toLowerCase(Locale.ROOT);
        String simpleName = cls.getSimpleName().toLowerCase(Locale.ROOT);
        if (simpleName.contains("multiblock") || clsName.contains("multiblock")) {
            return true;
        }

        try {
            Method mIsMb = cls.getMethod("isMultiblock");
            mIsMb.setAccessible(true);
            Object res = mIsMb.invoke(def);
            if (res instanceof Boolean b && b) return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        if (mCls != null) {
            String mClsName = mCls.getName().toLowerCase(Locale.ROOT);
            if (mClsName.contains("multiblock") || mClsName.contains("controller")) {
                return true;
            }
        }

        return MultiblockStructureCatalog.getStructure(id) != null;
    }

    public static void detectAndRegisterCoilMultiblock(ResourceLocation id, Class<?> mCls, ResourceLocation recipeCategoryId) {
        IModAdapter adapter = id != null ? ModAdapterRegistry.getAdapterForMod(id.getNamespace()) : null;
        boolean adapterCoil = adapter != null && adapter.isCoilMultiblock(id);
        if (isCoilMachineClass(mCls)
                || isCoilFromCatalog(id)
                || adapterCoil) {
            MultiblockDetector.registerCoilMultiblock(id, recipeCategoryId);
        }
    }

    public static boolean isCoilMachineClass(Class<?> mCls) {
        if (mCls == null || COIL_WORKABLE_CLS == null) return false;
        return COIL_WORKABLE_CLS.isAssignableFrom(mCls);
    }

    public static boolean isCoilFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        return defStruct != null && defStruct.supportsAbility("HEATING_COILS") && defStruct.coilSlotCount() > 0;
    }

    public static void detectAndRegisterParallelAndBatch(ResourceLocation id, Object def, Class<?> cls) {
        if (MultiblockDetector.isTurbineMachine(id)) return;

        boolean supportsParallel = hasParallelModifier(cls, def) || hasParallelFromCatalog(id);
        boolean supportsBatch = hasBatchModifier(cls, def);

        if (supportsParallel) MultiblockDetector.registerParallelHatchController(id);
        if (supportsBatch) MultiblockDetector.registerBatchModeController(id);
    }

    public static boolean hasParallelModifier(Class<?> cls, Object def) {
        return hasRecipeModifier(cls, def, "PARALLEL_HATCH");
    }

    public static boolean hasBatchModifier(Class<?> cls, Object def) {
        return hasRecipeModifier(cls, def, "BATCH_MODE");
    }

    public static boolean hasRecipeModifier(Class<?> cls, Object def, String targetName) {
        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() != 0 || !isRecipeModifierGetter(m.getName())) continue;
            try {
                m.setAccessible(true);
                Object modifiers = m.invoke(def);
                if (modifiers != null && containsRecipeModifier(modifiers, targetName)) {
                    return true;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return false;
    }

    private static boolean isRecipeModifierGetter(String name) {
        return name.equals("getRecipeModifiers") || name.equals("getRecipeModifier") || name.equals("recipeModifiers");
    }

    public static boolean hasParallelFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        return defStruct != null && defStruct.supportsAbility("PARALLEL_HATCH");
    }

    public static void detectAndRegisterLaserHatch(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct != null && (defStruct.supportsAbility("INPUT_LASER") || defStruct.supportsAbility("LASER_TARGET_HATCH") || defStruct.supportsAbility("LASER_SOURCE_HATCH"))) {
            MultiblockDetector.registerLaserHatchController(id);
        }
    }

    public static void detectAndRegisterThreading(ResourceLocation id, Object def, Class<?> cls, Class<?> mCls) {
        boolean isThreading = isThreadingMachineClass(mCls) || hasThreadingModifier(cls, def) || isThreadingFromCatalog(id);
        if (!isThreading) return;

        int detectedHelixCount = detectHelixCountFromCatalog(id);
        MultiblockDetector.registerThreadingMultiblock(id, detectedHelixCount > 0 ? detectedHelixCount : 8);
    }

    public static boolean isThreadingMachineClass(Class<?> mCls) {
        if (mCls == null) return false;
        if (THREADING_CAPABLE_CLS != null && THREADING_CAPABLE_CLS.isAssignableFrom(mCls)) {
            return true;
        }
        String mClsName = mCls.getName().toLowerCase(Locale.ROOT);
        return mClsName.contains("threadingcapable") || mClsName.contains("startthreading");
    }

    public static boolean hasThreadingModifier(Class<?> cls, Object def) {
        try {
            Method mGetModifiers = cls.getMethod("getRecipeModifiers");
            mGetModifiers.setAccessible(true);
            Object modifiers = mGetModifiers.invoke(def);
            if (modifiers != null) {
                String modStr = modifiers.toString().toLowerCase(Locale.ROOT);
                return modStr.contains("threading_machine") || modStr.contains("startrecipemodifiers") || modStr.contains("threading");
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return false;
    }

    public static boolean isThreadingFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct == null) return false;

        if (defStruct.supportsAbility("THREADING") || defStruct.supportsAbility("THREADING_HELIX")) {
            return true;
        }
        return defStruct.candidateBlocks().stream().anyMatch(b ->
                isHelixPart(b) || (b != null && "start_core".equals(b.getNamespace()) && "threading_controller".equals(b.getPath())));
    }

    public static int detectHelixCountFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct == null) return 0;

        int count = 0;
        for (var part : defStruct.parts()) {
            if (part != null && part.itemId() != null && isHelixPart(part.itemId())) {
                count = Math.max(count, part.amount());
            }
        }
        return count;
    }

    public static boolean isHelixPart(ResourceLocation itemId) {
        if (itemId == null) return false;
        return GTThreadingHelix.fromId(itemId) != null
                || GTThreadingHelix.fromId(itemId.toString()) != null;
    }

    public static ResourceLocation extractRecipeTypeId(Object rt) {
        if (rt == null) return null;
        if (rt instanceof ResourceLocation rl) return rl;

        ResourceLocation loc = extractFromForgeRecipeTypes(rt);
        if (loc != null) return loc;

        ResourceLocation refLoc = extractRecipeTypeIdViaReflection(rt);
        if (refLoc != null) return refLoc;

        String str = rt.toString();
        if (str != null && str.contains(":")) {
            return ResourceLocation.tryParse(str);
        }
        return null;
    }

    private static ResourceLocation extractFromForgeRecipeTypes(Object rt) {
        if (rt instanceof net.minecraft.world.item.crafting.RecipeType<?> rType && net.minecraftforge.registries.ForgeRegistries.RECIPE_TYPES != null) {
            ResourceLocation loc = net.minecraftforge.registries.ForgeRegistries.RECIPE_TYPES.getKey(rType);
            if (loc != null && !loc.getPath().equals("air")) return loc;
        }
        return null;
    }

    private static ResourceLocation extractRecipeTypeIdViaReflection(Object rt) {
        ResourceLocation fromField = extractRegistryNameFromField(rt);
        if (fromField != null) return fromField;

        ResourceLocation fromMethod = extractRegistryNameFromMethod(rt);
        if (fromMethod != null) return fromMethod;

        return extractRegistryNameFromGTRegistry(rt);
    }

    private static ResourceLocation extractRegistryNameFromField(Object rt) {
        try {
            Field f = rt.getClass().getField("registryName");
            f.setAccessible(true);
            Object val = f.get(rt);
            if (val instanceof ResourceLocation rl) return rl;
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    private static ResourceLocation extractRegistryNameFromMethod(Object rt) {
        for (String mName : new String[]{"getRegistryName", "getId"}) {
            try {
                Method m = rt.getClass().getMethod(mName);
                m.setAccessible(true);
                Object idVal = m.invoke(rt);
                if (idVal instanceof ResourceLocation rl) return rl;
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return null;
    }

    private static ResourceLocation extractRegistryNameFromGTRegistry(Object rt) {
        if (RECIPE_TYPES_FIELD == null) return null;
        try {
            Object recipeTypesReg = RECIPE_TYPES_FIELD.get(null);
            if (recipeTypesReg != null) {
                Method mGetKey = recipeTypesReg.getClass().getMethod("getKey", Object.class);
                mGetKey.setAccessible(true);
                Object k = mGetKey.invoke(recipeTypesReg, rt);
                if (k instanceof ResourceLocation rl) return rl;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    public static Iterable<?> getRegistryIterable(Object registry) {
        if (registry == null) return null;
        if (registry instanceof Iterable<?> iterable) {
            return iterable;
        }
        try {
            Method valuesMethod = registry.getClass().getMethod("values");
            valuesMethod.setAccessible(true);
            Object result = valuesMethod.invoke(registry);
            if (result instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    public static boolean containsRecipeModifier(Object modifiersObj, String targetName) {
        if (modifiersObj == null || targetName == null || GT_MODIFIERS_CLS == null) return false;
        try {
            Field f = GT_MODIFIERS_CLS.getField(targetName);
            f.setAccessible(true);
            Object targetModifier = f.get(null);
            if (targetModifier != null) {
                return containsModifierObject(modifiersObj, targetModifier);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return false;
    }

    private static boolean containsModifierObject(Object obj, Object target) {
        if (obj == null || target == null) return false;
        if (obj == target || obj.equals(target)) return true;

        if (obj instanceof Object[] arr) {
            for (Object item : arr) {
                if (containsModifierObject(item, target)) return true;
            }
            return false;
        }

        if (obj instanceof Iterable<?> it) {
            for (Object item : it) {
                if (containsModifierObject(item, target)) return true;
            }
            return false;
        }

        return inspectModifierFields(obj, target);
    }

    private static boolean inspectModifierFields(Object obj, Object target) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null && val != obj && isMatchingModifierValue(val, target)) {
                        return true;
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean isMatchingModifierValue(Object val, Object target) {
        if (val == target || val.equals(target)) return true;
        if (val instanceof Object[] || val instanceof Iterable<?>) {
            return containsModifierObject(val, target);
        }
        return false;
    }
}
