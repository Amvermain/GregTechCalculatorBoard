package com.gtceu.calcboard.compat.start;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated reflection bridge for Star Technology Core runtime integration.
 * Evaluates StarT recipe modifiers and machine capabilities deterministically
 * without polluting GTCEu core reflection classes.
 */
public final class StarTReflectionBridge {

    private static final Map<Object, String> START_RECIPE_MODIFIER_NAMES;
    private static final boolean STAR_T_LOADED;

    static {
        Map<Object, String> modNames = new IdentityHashMap<>();
        Class<?> startTModifiersCls = loadClassQuietly("com.startechnology.start_core.recipe.StarTRecipeModifiers");
        STAR_T_LOADED = startTModifiersCls != null;

        if (startTModifiersCls != null) {
            for (Field f : startTModifiersCls.getFields()) {
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
        START_RECIPE_MODIFIER_NAMES = Collections.unmodifiableMap(modNames);
    }

    private StarTReflectionBridge() {}

    public static boolean isStarTLoaded() {
        return STAR_T_LOADED;
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
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
