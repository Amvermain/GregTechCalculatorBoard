package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reflection bridge for Star Technology Core recipe modifiers, recipe types, and machine capabilities.
 */
public final class StarTReflectionBridge {

    public static final ResourceLocation BACTERIAL_HARVESTER_RECIPE_ID = ResourceLocation.tryParse("gtceu:bacterial_hydrocarbon_harvester");
    public static final ResourceLocation BACTERIAL_HARVESTER_START_MACHINE_ID = ResourceLocation.tryParse("start_core:bacterial_hydrocarbon_harvester");
    public static final ResourceLocation BACTERIAL_HARVESTER_GT_MACHINE_ID = ResourceLocation.tryParse("gtceu:bacterial_hydrocarbon_harvester");

    public static final ResourceLocation HELLFORGE_RECIPE_ID = ResourceLocation.tryParse("gtceu:hellforge");
    public static final ResourceLocation HELLFORGE_HEATING_RECIPE_ID = ResourceLocation.tryParse("gtceu:hellforge_heating");
    public static final ResourceLocation HELLFORGE_START_RECIPE_ID = ResourceLocation.tryParse("start_core:hellforge");
    public static final ResourceLocation HELLFORGE_START_HEATING_RECIPE_ID = ResourceLocation.tryParse("start_core:hellforge_heating");
    public static final ResourceLocation HELLFORGE_START_MACHINE_ID = ResourceLocation.tryParse("start_core:hellforge");
    public static final ResourceLocation HELLFORGE_GT_MACHINE_ID = ResourceLocation.tryParse("gtceu:hellforge");
    public static final ResourceLocation BOOSTED_HELLFORGE_START_MACHINE_ID = ResourceLocation.tryParse("start_core:fornaxs_infernal_rotary_engine");
    public static final ResourceLocation BOOSTED_HELLFORGE_GT_MACHINE_ID = ResourceLocation.tryParse("gtceu:fornaxs_infernal_rotary_engine");

    private static final Set<String> START_NAMESPACES = Set.of(
            "start_core", "gtceu_start", "start", "star_technology"
    );

    private static final Set<ResourceLocation> KNOWN_START_RECIPE_TYPES = Set.of(
            ResourceLocation.tryParse("gtceu:hellforge"),
            ResourceLocation.tryParse("gtceu:hellforge_heating"),
            ResourceLocation.tryParse("start_core:hellforge"),
            ResourceLocation.tryParse("start_core:hellforge_heating"),
            ResourceLocation.tryParse("gtceu:bacterial_hydrocarbon_harvester"),
            ResourceLocation.tryParse("gtceu:large_rotor_machine"),
            ResourceLocation.tryParse("gtceu:gate_construction"),
            ResourceLocation.tryParse("gtceu:stargate_component_assembly"),
            ResourceLocation.tryParse("gtceu:reflector_fusion_reactor"),
            ResourceLocation.tryParse("gtceu:bacterial_breeding_vat"),
            ResourceLocation.tryParse("gtceu:bacterial_runic_mutator"),
            ResourceLocation.tryParse("gtceu:abyssal_containment"),
            ResourceLocation.tryParse("gtceu:gcrop_mutator"),
            ResourceLocation.tryParse("gtceu:gcrop_breeder"),
            ResourceLocation.tryParse("gtceu:gcrop_harvester"),
            ResourceLocation.tryParse("gtceu:void_mesh"),
            ResourceLocation.tryParse("gtceu:void_gas_collector"),
            ResourceLocation.tryParse("gtceu:genome_gathering"),
            ResourceLocation.tryParse("gtceu:genome_mixing"),
            ResourceLocation.tryParse("gtceu:genome_separating"),
            ResourceLocation.tryParse("gtceu:genome_insertion"),
            ResourceLocation.tryParse("gtceu:vacuum_chemical_reaction_chamber")
    );

    private static final Map<Object, String> START_RECIPE_MODIFIER_NAMES;
    private static final Set<ResourceLocation> START_RECIPE_TYPE_IDS;
    private static final boolean STAR_T_LOADED;
    private static final Class<?> I_BULKING_CLASS;

    static {
        Map<Object, String> modNames = new IdentityHashMap<>();
        Class<?> startTModifiersCls = loadClassQuietly("com.startechnology.start_core.recipe.StarTRecipeModifiers");
        STAR_T_LOADED = startTModifiersCls != null;

        collectModifiers(startTModifiersCls, modNames);
        START_RECIPE_MODIFIER_NAMES = Collections.unmodifiableMap(modNames);
        I_BULKING_CLASS = loadClassQuietly("com.startechnology.start_core.machine.bulking.IBulking");

        Set<ResourceLocation> recipeTypes = new HashSet<>(KNOWN_START_RECIPE_TYPES);
        Class<?> recipeTypesCls = loadClassQuietly("com.startechnology.start_core.recipe.StarTRecipeTypes");
        collectRecipeTypeIds(recipeTypesCls, recipeTypes);
        START_RECIPE_TYPE_IDS = Collections.unmodifiableSet(recipeTypes);
    }

    private StarTReflectionBridge() {}

    public static boolean isStarTLoaded() {
        return STAR_T_LOADED;
    }

    public static boolean isStarTNamespace(String namespace) {
        if (namespace == null) return false;
        return START_NAMESPACES.contains(namespace.toLowerCase(Locale.ROOT));
    }

    public static boolean isStarTRecipeType(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        if (isStarTNamespace(categoryId.getNamespace())) {
            return true;
        }
        return START_RECIPE_TYPE_IDS.contains(categoryId);
    }

    public static boolean isStarTMachine(ResourceLocation icon) {
        if (icon == null) return false;
        if (isStarTNamespace(icon.getNamespace())) {
            return true;
        }
        if (isBacterialHarvesterIcon(icon)) {
            return true;
        }
        return MultiblockDetector.isThreadingMultiblock(icon);
    }

    public static boolean isBacterialHarvester(RecipeNode node) {
        if (node == null) return false;
        if (BACTERIAL_HARVESTER_RECIPE_ID.equals(node.getRecipeCategoryId())) {
            return true;
        }
        return isBacterialHarvesterIcon(node.getMachineIcon());
    }

    public static boolean isBacterialHarvesterIcon(ResourceLocation icon) {
        if (icon == null) return false;
        return BACTERIAL_HARVESTER_START_MACHINE_ID.equals(icon) || BACTERIAL_HARVESTER_GT_MACHINE_ID.equals(icon);
    }

    public static boolean isBulkingMachineClass(Class<?> cls) {
        if (cls == null) return false;
        if (I_BULKING_CLASS != null && I_BULKING_CLASS.isAssignableFrom(cls)) {
            return true;
        }
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            String name = current.getName();
            if ("com.startechnology.start_core.machine.bulking.BulkingMachine".equals(name)
                    || "com.startechnology.start_core.machine.bulking.BulkingCoiledMachine".equals(name)
                    || "com.startechnology.start_core.machine.bulking.BulkingThreadedMachine".equals(name)) {
                return true;
            }
            for (Class<?> iface : current.getInterfaces()) {
                if ("com.startechnology.start_core.machine.bulking.IBulking".equals(iface.getName())) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public static String getRecipeModifierName(Object modifier) {
        if (modifier == null) return null;
        String name = START_RECIPE_MODIFIER_NAMES.get(modifier);
        if (name != null) return name;
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
        } catch (Throwable ignored) {}
        return null;
    }

    private static void collectRecipeTypeIds(Class<?> recipeTypesClass, Set<ResourceLocation> target) {
        if (recipeTypesClass == null) return;
        for (Field f : recipeTypesClass.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            try {
                Object val = f.get(null);
                if (val != null) {
                    ResourceLocation id = extractResourceLocation(val);
                    if (id != null) {
                        target.add(id);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static ResourceLocation extractResourceLocation(Object obj) {
        if (obj instanceof ResourceLocation rl) return rl;
        try {
            Field regNameField = findField(obj.getClass(), "registryName");
            if (regNameField != null) {
                regNameField.setAccessible(true);
                Object id = regNameField.get(obj);
                if (id instanceof ResourceLocation rl) return rl;
            }
            Method mGetId = findMethod(obj.getClass(), "getId");
            if (mGetId != null) {
                Object id = mGetId.invoke(obj);
                if (id instanceof ResourceLocation rl) return rl;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void collectModifiers(Class<?> modifiersClass, Map<Object, String> modNames) {
        if (modifiersClass == null) return;
        for (Field f : modifiersClass.getFields()) {
            registerStaticModifier(f, modNames);
        }
    }

    private static void registerStaticModifier(Field field, Map<Object, String> modNames) {
        if (!Modifier.isStatic(field.getModifiers())) return;
        try {
            Object mod = field.get(null);
            if (mod != null) {
                modNames.put(mod, field.getName().toUpperCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {}
    }

    private static Class<?> loadClassQuietly(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> targetClass, String methodName) {
        if (targetClass == null || methodName == null) return null;
        Class<?> current = targetClass;
        while (current != null && current != Object.class) {
            try {
                Method m = current.getDeclaredMethod(methodName);
                m.setAccessible(true);
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
                Field f = current.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
