package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.ModCompatHelper;
import com.gtceu.calcboard.api.MultiblockDetector;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class GTCEuCapabilityScanner {

    public static void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        if (!ModCompatHelper.isGTLoaded()) {
            return;
        }

        try {
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

                        List<Object> recipeTypesList = new java.util.ArrayList<>();
                        try {
                            Method mGetRecipeTypes = def.getClass().getMethod("getRecipeTypes");
                            Object rTypes = mGetRecipeTypes.invoke(def);
                            if (rTypes instanceof Object[] arr) {
                                for (Object rt : arr) {
                                    if (rt != null) recipeTypesList.add(rt);
                                }
                            } else if (rTypes instanceof Iterable<?> it) {
                                for (Object rt : it) {
                                    if (rt != null) recipeTypesList.add(rt);
                                }
                            }
                        } catch (Throwable ignored) {}

                        if (recipeTypesList.isEmpty()) {
                            try {
                                Method mGetRecipeType = def.getClass().getMethod("getRecipeType");
                                Object rt = mGetRecipeType.invoke(def);
                                if (rt != null) recipeTypesList.add(rt);
                            } catch (Throwable ignored) {}
                        }

                        for (Object rt : recipeTypesList) {
                            ResourceLocation catId = MultiblockDetector.extractRecipeTypeId(rt);
                            if (catId != null) {
                                boolean isMb = MultiblockDetector.inspectAndRegisterMachine(mId, def, catId);
                                boolean usesCoils = MultiblockDetector.isCoilMultiblock(mId);
                                boolean isTurbine = MultiblockDetector.isTurbineMachine(mId);
                                boolean isSteam = isSteamDefinition(def, mId);
                                boolean isHp = isHighPressureDefinition(def, mId);

                                GTVoltageTier turbineTier = isTurbine ? MultiblockDetector.getTurbineBaseTier(mId) : null;
                                double turbineBaseEnergy = isTurbine ? (MultiblockDetector.getTurbineBaseProduction(mId) != null ? MultiblockDetector.getTurbineBaseProduction(mId) : 4096.0) : 0.0;

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

                                // Share workstations between large_/extreme_ and base categories (e.g. chemical_reactor <-> large_chemical_reactor)
                                ResourceLocation relatedCatId = getRelatedRecipeCategory(catId);
                                if (relatedCatId != null && !relatedCatId.equals(catId)) {
                                    CategoryCapabilityMatrix.CategoryBuilder relB = matrix.getOrCreateBuilder(relatedCatId);
                                    relB.addWorkstation(mId, isMb);
                                    if (usesCoils) relB.canUseCoils = true;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public static ResourceLocation getRelatedRecipeCategory(ResourceLocation catId) {
        if (catId == null) return null;
        String path = catId.getPath().toLowerCase(Locale.ROOT);
        String ns = catId.getNamespace();
        if (path.equals("large_chemical_reactor") || path.equals("extreme_chemical_reactor") || path.equals("incomprehensible_chemical_reactor")) {
            return ResourceLocation.tryParse(ns + ":chemical_reactor");
        } else if (path.equals("chemical_reactor")) {
            return ResourceLocation.tryParse(ns + ":large_chemical_reactor");
        } else if (path.startsWith("large_")) {
            return ResourceLocation.tryParse(ns + ":" + path.substring(6));
        } else if (path.startsWith("mega_")) {
            return ResourceLocation.tryParse(ns + ":" + path.substring(5));
        }
        return null;
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
