package com.gtceu.calcboard.compat.gtceu.helper;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic runtime helper for evaluating native GTCEu machine overclock capabilities.
 * Inspects machine definitions, recipe modifiers, and environment configurations without heuristic path matching.
 */
public final class GTCEuOverclockHelper {

    private static final ResourceLocation LCR_ID = ResourceLocation.tryParse("gtceu:large_chemical_reactor");

    private GTCEuOverclockHelper() {}

    public static boolean hasNativePerfectOverclock(ResourceLocation machineId) {
        if (machineId == null) return false;

        if (isLargeChemicalReactor(machineId)) {
            return !GTCEuCoilModifierHelper.isStarTCoilReactor();
        }

        Object def = GTCEuReflectionBridge.getMachineDefinition(machineId);
        return def != null && hasNativePerfectOverclock(machineId, def);
    }

    public static boolean hasNativePerfectOverclock(ResourceLocation id, Object def) {
        if (def == null) return hasNativePerfectOverclock(id);
        if (isLargeChemicalReactor(id)) {
            return !GTCEuCoilModifierHelper.isStarTCoilReactor();
        }

        List<Object> modifiers = GTCEuReflectionBridge.getRecipeModifiers(def);
        if (modifiers == null || modifiers.isEmpty()) return false;

        for (Object mod : modifiers) {
            if (isPerfectOverclockModifier(mod)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPerfectOverclockModifier(Object modifier) {
        if (modifier == null) return false;

        String modName = GTCEuReflectionBridge.getRecipeModifierName(modifier);
        if (modName != null && !modName.isBlank()) {
            return isPerfectOverclockModifierName(modName);
        }

        String desc = extractModifierDescriptor(modifier);
        return isPerfectOverclockDescriptor(desc);
    }

    public static boolean isPerfectOverclockModifierName(String name) {
        if (name == null || name.isBlank()) return false;
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("NON_PERFECT")
                || upper.contains("NONPERFECT")
                || upper.contains("NOT_PERFECT")
                || upper.contains("IMPERFECT")
                || upper.contains("EBF")) {
            return false;
        }
        return upper.equals("OC_PERFECT")
                || upper.equals("OC_PERFECT_SUBTICK")
                || upper.contains("OC_PERFECT")
                || upper.contains("PERFECT_OC")
                || upper.contains("PERFECT_OVERCLOCK");
    }

    public static boolean isLargeChemicalReactor(ResourceLocation machineId) {
        return LCR_ID != null && LCR_ID.equals(machineId);
    }

    private static boolean isPerfectOverclockDescriptor(String desc) {
        if (desc == null || desc.isBlank()) return false;
        String lower = desc.toLowerCase(Locale.ROOT);
        if (lower.contains("non_perfect")
                || lower.contains("nonperfect")
                || lower.contains("not_perfect")
                || lower.contains("imperfect")
                || lower.contains("ebf")) {
            return false;
        }
        return lower.contains("perfect_overclock")
                || lower.contains("oc_perfect")
                || lower.contains("perfectoverclock");
    }

    private static String extractModifierDescriptor(Object modifier) {
        if (modifier == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(modifier.getClass().getName()).append(' ').append(modifier).append(' ');
        for (Field f : modifier.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            Class<?> type = f.getType();
            if (type == String.class || ResourceLocation.class.isAssignableFrom(type)) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(modifier);
                    if (val != null) {
                        sb.append(val).append(' ');
                    }
                } catch (Throwable ignored) {}
            }
        }
        return sb.toString();
    }
}
