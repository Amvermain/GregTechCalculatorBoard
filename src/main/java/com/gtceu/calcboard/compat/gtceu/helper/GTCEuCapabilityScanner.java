package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
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
            if (iterable == null) return;

            for (Object def : iterable) {
                processMachineDefinition(def, matrix);
            }
        } catch (Throwable ignored) {}
    }

    private static void processMachineDefinition(Object def, CategoryCapabilityMatrix matrix) {
        if (def == null) return;
        try {
            Method mGetId = def.getClass().getMethod("getId");
            ResourceLocation mId = (ResourceLocation) mGetId.invoke(def);
            if (mId == null) return;

            List<Object> recipeTypesList = extractRecipeTypes(def);
            for (Object rt : recipeTypesList) {
                registerMachineRecipeType(mId, def, rt, matrix);
            }
        } catch (Throwable ignored) {}
    }

    private static List<Object> extractRecipeTypes(Object def) {
        List<Object> recipeTypesList = new java.util.ArrayList<>();
        try {
            Method mGetRecipeTypes = def.getClass().getMethod("getRecipeTypes");
            Object rTypes = mGetRecipeTypes.invoke(def);
            collectRecipeTypes(rTypes, recipeTypesList);
        } catch (Throwable ignored) {}

        if (recipeTypesList.isEmpty()) {
            try {
                Method mGetRecipeType = def.getClass().getMethod("getRecipeType");
                Object rt = mGetRecipeType.invoke(def);
                if (rt != null) recipeTypesList.add(rt);
            } catch (Throwable ignored) {}
        }
        return recipeTypesList;
    }

    private static void collectRecipeTypes(Object rTypes, List<Object> collector) {
        if (rTypes instanceof Object[] arr) {
            for (Object rt : arr) {
                if (rt != null) collector.add(rt);
            }
        } else if (rTypes instanceof Iterable<?> it) {
            for (Object rt : it) {
                if (rt != null) collector.add(rt);
            }
        }
    }

    private static void registerMachineRecipeType(ResourceLocation mId, Object def, Object rt, CategoryCapabilityMatrix matrix) {
        ResourceLocation catId = MultiblockDetector.extractRecipeTypeId(rt);
        if (catId == null) return;

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
        configureSteamOptions(b, mId, isSteam, isHp);
        configureTurbineOptions(b, catId, isTurbine, turbineTier, turbineBaseEnergy);
        shareRelatedWorkstations(matrix, catId, mId, isMb, usesCoils);
    }

    private static void configureSteamOptions(CategoryCapabilityMatrix.CategoryBuilder b, ResourceLocation mId, boolean isSteam, boolean isHp) {
        if (!isSteam) return;
        if (isHp) {
            b.hasHighPressureSteamOption = true;
            b.highPressureWorkstation = mId;
        } else {
            b.hasLowPressureSteamOption = true;
            b.lowPressureWorkstation = mId;
        }
    }

    private static void configureTurbineOptions(CategoryCapabilityMatrix.CategoryBuilder b, ResourceLocation catId, boolean isTurbine, GTVoltageTier turbineTier, double turbineBaseEnergy) {
        if (!isTurbine || !MultiblockDetector.isTurbineRecipeCategory(catId)) return;
        b.isTurbine = true;
        if (turbineTier != null) b.turbineBaseTier = turbineTier;
        if (turbineBaseEnergy > 0) b.turbineBaseProduction = turbineBaseEnergy;
    }

    private static void shareRelatedWorkstations(CategoryCapabilityMatrix matrix, ResourceLocation catId, ResourceLocation mId, boolean isMb, boolean usesCoils) {
        if (!isMb) return;
        ResourceLocation relatedCatId = getRelatedRecipeCategory(catId);
        if (relatedCatId == null || relatedCatId.equals(catId)) return;

        CategoryCapabilityMatrix.CategoryBuilder relB = matrix.getOrCreateBuilder(relatedCatId);
        relB.addWorkstation(mId, true);
        if (usesCoils) relB.canUseCoils = true;
    }

    public static ResourceLocation getRelatedRecipeCategory(ResourceLocation catId) {
        if (catId == null) return null;
        String path = catId.getPath().toLowerCase(Locale.ROOT);
        String ns = catId.getNamespace();
        if (path.equals("large_chemical_reactor") || path.equals("extreme_chemical_reactor") || path.equals("incomprehensible_chemical_reactor")) {
            return ResourceLocation.tryParse(ns + ":chemical_reactor");
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
        if (def != null) {
            try {
                Method mIsHp = def.getClass().getMethod("isHighPressure");
                Object res = mIsHp.invoke(def);
                if (res instanceof Boolean b) return b;
            } catch (Throwable ignored) {}
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
        return true;
    }
}

