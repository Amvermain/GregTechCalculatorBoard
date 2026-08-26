package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.ModCompatHelper;
import com.gtceu.calcboard.api.MultiblockDetector;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.Locale;

public class GTCEuCapabilityScanner {

    public static void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        if (!ModCompatHelper.isGTLoaded()) {
            return;
        }

        try {
            Class<?> multiblockDefCls = null;
            try {
                multiblockDefCls = Class.forName("com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition");
            } catch (Throwable ignored) {}

            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
            Iterable<?> iterable = MultiblockDetector.getRegistryIterable(machinesRegistry);

            if (iterable != null) {
                for (Object def : iterable) {
                    if (def == null) continue;
                    try {
                        Method mGetId = def.getClass().getMethod("getId");
                        ResourceLocation mId = (ResourceLocation) mGetId.invoke(def);
                        if (mId == null) continue;

                        boolean isMb = (multiblockDefCls != null && multiblockDefCls.isInstance(def))
                                || MultiblockDetector.isMultiblock(mId);
                        boolean usesCoils = MultiblockDetector.isCoilMultiblock(mId);
                        boolean isTurbine = MultiblockDetector.isTurbineMachine(mId);

                        boolean isSteam = isSteamDefinition(def, mId);
                        boolean isHp = isHighPressureDefinition(def, mId);

                        GTVoltageTier turbineTier = isTurbine ? MultiblockDetector.getTurbineBaseTier(mId) : null;
                        double turbineBaseEnergy = isTurbine ? (MultiblockDetector.getTurbineBaseProduction(mId) != null ? MultiblockDetector.getTurbineBaseProduction(mId) : 4096.0) : 0.0;

                        Method mGetRecipeType = def.getClass().getMethod("getRecipeTypes");
                        Object rTypes = mGetRecipeType.invoke(def);
                        if (rTypes instanceof Object[] arr) {
                            for (Object rt : arr) {
                                if (rt != null) {
                                    ResourceLocation catId = MultiblockDetector.extractRecipeTypeId(rt);
                                    if (catId != null) {
                                        CategoryCapabilityMatrix.CategoryBuilder b = matrix.getOrCreateBuilder(catId);
                                        b.addWorkstation(mId, isMb);
                                        if (usesCoils) b.canUseCoils = true;
                                        if (isSteam) {
                                            if (isHp) {
                                                b.hasHighPressureSteamOption = true;
                                                b.highPressureWorkstation = mId;
                                            } else {
                                                b.hasLowPressureSteamOption = true;
                                                b.lowPressureWorkstation = mId;
                                            }
                                        }
                                        if (isTurbine) {
                                            b.isTurbine = true;
                                            if (turbineTier != null) b.turbineBaseTier = turbineTier;
                                            if (turbineBaseEnergy > 0) b.turbineBaseProduction = turbineBaseEnergy;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isSteamDefinition(Object def, ResourceLocation id) {
        if (id != null && MultiblockDetector.isSteamMultiblock(id)) {
            return true;
        }
        if (def == null) {
            if (id != null) {
                String path = id.getPath().toLowerCase(Locale.ROOT);
                return path.startsWith("lp_steam_") || path.startsWith("hp_steam_") || path.startsWith("steam_");
            }
            return false;
        }
        try {
            Class<?> steamCls = Class.forName("com.gregtechceu.gtceu.api.machine.SteamMachineDefinition");
            if (steamCls.isInstance(def)) return true;
        } catch (Throwable ignored) {}
        try {
            Class<?> steamMbCls = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.SteamMultiblockMachineDefinition");
            if (steamMbCls.isInstance(def)) return true;
        } catch (Throwable ignored) {}
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            return path.startsWith("lp_steam_") || path.startsWith("hp_steam_") || path.startsWith("steam_");
        }
        return false;
    }

    public static boolean isHighPressureDefinition(Object def, ResourceLocation id) {
        if (id != null && MultiblockDetector.isSteamMultiblock(id)) {
            return true;
        }
        if (def == null) {
            if (id != null) {
                String path = id.getPath().toLowerCase(Locale.ROOT);
                if (path.startsWith("hp_steam_") || path.contains("_hp_") || path.contains("high_pressure") || path.startsWith("steam_")) {
                    return true;
                }
                if (path.startsWith("lp_steam_") || path.contains("_lp_") || path.contains("low_pressure")) {
                    return false;
                }
            }
            return true;
        }
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.startsWith("hp_steam_") || path.contains("_hp_") || path.contains("high_pressure") || path.startsWith("steam_")) {
                return true;
            }
            if (path.startsWith("lp_steam_") || path.contains("_lp_") || path.contains("low_pressure")) {
                return false;
            }
        }
        try {
            Method mIsHp = def.getClass().getMethod("isHighPressure");
            Object res = mIsHp.invoke(def);
            if (res instanceof Boolean b) return b;
        } catch (Throwable ignored) {}
        return true;
    }
}
