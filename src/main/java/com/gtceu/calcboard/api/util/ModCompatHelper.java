package com.gtceu.calcboard.api.util;

import com.gtceu.calcboard.api.model.RecipeNode;
import net.neoforged.fml.ModList;

/**
 * Utility helper that provides soft dependency checks to isolate optional mod integrations
 * and prevent ClassNotFoundExceptions or unexpected crashes when optional mods are absent.
 */
public class ModCompatHelper {

    private static final java.util.Map<String, Boolean> TEST_OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setTestOverride(String modId, boolean loaded) {
        TEST_OVERRIDES.put(modId, loaded);
    }

    public static void clearTestOverrides() {
        TEST_OVERRIDES.clear();
    }

    public static boolean isGTLoaded() {
        return isModLoaded("gtceu");
    }

    public static boolean isThermalLoaded() {
        return isModLoaded("thermal") || isModLoaded("thermal_expansion") || isModLoaded("thermal_foundation");
    }

    public static boolean isSysteamsLoaded() {
        return isModLoaded("systeams");
    }

    public static boolean isCreateLoaded() {
        return isModLoaded("create");
    }

    public static boolean isCreateNewAgeLoaded() {
        return isModLoaded("create_new_age");
    }

    public static boolean isCreateAdditionsLoaded() {
        return isModLoaded("createaddition");
    }

    public static boolean isCreateVintageImprovementsLoaded() {
        return isModLoaded("vintageimprovements") || isModLoaded("vintage_improvements") || isModLoaded("create_vintage_improvements") || isModLoaded("vintage");
    }

    public static boolean isCreateFamilyNamespace(String namespace) {
        if (namespace == null) return false;
        String lower = namespace.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("create")
                || lower.equals("createaddition")
                || lower.equals("create_new_age")
                || lower.equals("vintageimprovements")
                || lower.equals("vintage_improvements")
                || lower.equals("create_vintage_improvements")
                || lower.equals("vintage")
                || lower.equals("create_enchantment_industry")
                || lower.equals("create_dd")
                || lower.equals("createbigcannons")
                || lower.equals("create_sa")
                || lower.equals("create_connected")
                || lower.equals("createsifter")
                || lower.equals("sliceanddice")
                || lower.startsWith("create_")
                || lower.startsWith("create-");
    }

    public static boolean isCreateMachine(RecipeNode node) {
        if (node == null) return false;
        if (node.getEnergyTypeOverride() == com.gtceu.calcboard.api.type.EnergyType.KINETIC_SU) return true;
        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace().toLowerCase(java.util.Locale.ROOT);
            if (ns.equals("minecraft") || ns.equals("emi") || ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start")) {
                return false;
            }
            if (isCreateFamilyNamespace(ns)) {
                return true;
            }
        }
        if (node.getRecipeCategoryId() != null) {
            String ns = node.getRecipeCategoryId().getNamespace().toLowerCase(java.util.Locale.ROOT);
            if (ns.equals("minecraft") || ns.equals("emi") || ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start")) {
                return false;
            }
            if (isCreateFamilyNamespace(ns)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmiLoaded() {
        return isModLoaded("emi");
    }

    public static boolean isJeiLoaded() {
        return isModLoaded("jei") || isModLoaded("jei_plus_plus") || isModLoaded("jeiplusplus") || isModLoaded("justenoughcalculation") || isModLoaded("rei");
    }

    public static boolean isRecipeViewerLoaded() {
        return isEmiLoaded() || isJeiLoaded();
    }

    public static boolean isBoMSupported() {
        return isRecipeViewerLoaded();
    }

    public static boolean isEnderIOLoaded() {
        return isModLoaded("enderio");
    }

    public static boolean isModLoaded(String modId) {
        if (TEST_OVERRIDES.containsKey(modId)) {
            return Boolean.TRUE.equals(TEST_OVERRIDES.get(modId));
        }
        try {
            ModList list = ModList.get();
            if (list != null && list.isLoaded("neoforge")) {
                return list.isLoaded(modId);
            }
            return true;
        } catch (Throwable t) {
            // In JUnit headless test environment without Forge ModList initialized, return true by default
            return true;
        }
    }
}

