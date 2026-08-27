package com.gtceu.calcboard.api.util;

import net.minecraftforge.fml.ModList;

/**
 * Utility helper that provides soft dependency checks to isolate optional mod integrations
 * and prevent ClassNotFoundExceptions or unexpected crashes when optional mods are absent.
 */
public class ModCompatHelper {

    public static boolean isGTLoaded() {
        return isModLoaded("gtceu");
    }

    public static boolean isThermalLoaded() {
        return isModLoaded("thermal") || isModLoaded("thermal_expansion") || isModLoaded("thermal_foundation");
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
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }
}

