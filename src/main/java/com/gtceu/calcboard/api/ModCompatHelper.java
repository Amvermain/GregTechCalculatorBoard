package com.gtceu.calcboard.api;

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
