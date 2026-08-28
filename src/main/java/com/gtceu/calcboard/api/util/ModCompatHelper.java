package com.gtceu.calcboard.api.util;

import net.minecraftforge.fml.ModList;

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

    public static boolean isCreateLoaded() {
        return isModLoaded("create");
    }

    public static boolean isCreateNewAgeLoaded() {
        return isModLoaded("create_new_age");
    }

    public static boolean isCreateAdditionsLoaded() {
        return isModLoaded("createaddition");
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
            if (list != null) {
                return list.isLoaded(modId);
            }
            // In JUnit headless test environment without Forge ModList, return true by default
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

