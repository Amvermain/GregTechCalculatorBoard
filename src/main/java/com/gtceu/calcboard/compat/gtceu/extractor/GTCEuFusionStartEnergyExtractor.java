package com.gtceu.calcboard.compat.gtceu.extractor;

import com.gtceu.calcboard.api.property.IRecipePropertyExtractor;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Extracts Fusion Reactor start energy (EU to start) and reflector tiers for GTCEu recipes.
 */
public class GTCEuFusionStartEnergyExtractor implements IRecipePropertyExtractor {

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public boolean matches(Object backingRecipe, ResourceLocation categoryId) {
        if (categoryId != null) {
            String ns = categoryId.getNamespace().toLowerCase(Locale.ROOT);
            String path = categoryId.getPath().toLowerCase(Locale.ROOT);
            if (ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start") || ns.equals("star_technology") || ns.equals("start")) {
                return true;
            }
            if (path.contains("fusion") || path.contains("reflector")) {
                return true;
            }
        }
        return backingRecipe != null && backingRecipe.getClass().getName().contains("GTRecipe");
    }

    @Override
    public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
        long euToStart = extractEuFromTag(recipeDataTag);
        int minReflectorTier = extractReflectorFromTag(recipeDataTag);

        if (backingRecipe != null) {
            long reflectionEu = extractEuFromReflection(backingRecipe);
            if (reflectionEu > 0 && euToStart <= 0) {
                euToStart = reflectionEu;
            }
            int reflectionRefl = extractReflectorFromReflection(backingRecipe);
            if (reflectionRefl > 0 && minReflectorTier <= 0) {
                minReflectorTier = reflectionRefl;
            }
        }

        if (minReflectorTier <= 0 && categoryId != null) {
            String path = categoryId.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("reflector")) {
                if (path.contains("ii") || path.endsWith("_2")) minReflectorTier = 2;
                else if (path.contains("iii") || path.endsWith("_3")) minReflectorTier = 3;
                else if (path.contains("iv") || path.endsWith("_4")) minReflectorTier = 4;
                else minReflectorTier = 1;
            }
        }

        if (euToStart > 0) {
            store.setById("fusion_start_eu", euToStart);
        }
        if (minReflectorTier > 0) {
            store.setById("required_reflector_tier", minReflectorTier);
        }
    }

    private long extractEuFromTag(CompoundTag recipeDataTag) {
        if (recipeDataTag == null) return 0L;
        for (String key : recipeDataTag.getAllKeys()) {
            String k = key.toLowerCase(Locale.ROOT);
            if (k.contains("start") && (k.contains("eu") || k.contains("energy"))) {
                try {
                    return recipeDataTag.getLong(key);
                } catch (Throwable t) {
                    return recipeDataTag.getInt(key);
                }
            }
        }
        return 0L;
    }

    private int extractReflectorFromTag(CompoundTag recipeDataTag) {
        if (recipeDataTag == null) return 0;
        for (String key : recipeDataTag.getAllKeys()) {
            String k = key.toLowerCase(Locale.ROOT);
            if (k.contains("reflector")) {
                return recipeDataTag.getInt(key);
            }
        }
        return 0;
    }

    private long extractEuFromReflection(Object backingRecipe) {
        try {
            Method mConds = backingRecipe.getClass().getMethod("conditions");
            Object conds = mConds.invoke(backingRecipe);
            if (conds instanceof List<?> condList) {
                for (Object c : condList) {
                    if (c == null) continue;
                    String cName = c.getClass().getName();
                    if (cName.contains("Fusion") || cName.contains("Reflector")) {
                        long val = readNumericMember(c, "getEuToStart", "euToStart");
                        if (val > 0) return val;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    private int extractReflectorFromReflection(Object backingRecipe) {
        try {
            Method mConds = backingRecipe.getClass().getMethod("conditions");
            Object conds = mConds.invoke(backingRecipe);
            if (conds instanceof List<?> condList) {
                for (Object c : condList) {
                    if (c == null) continue;
                    String cName = c.getClass().getName();
                    if (cName.contains("Reflector") || cName.contains("Fusion")) {
                        long val = readNumericMember(c, "getReflectorTier", "reflectorTier");
                        if (val <= 0) val = readNumericMember(c, "getMinReflectorTier", "minReflectorTier");
                        if (val > 0) return (int) val;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private long readNumericMember(Object target, String methodName, String fieldName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object res = m.invoke(target);
            if (res instanceof Number num) return num.longValue();
        } catch (Throwable ignored) {}
        try {
            Field f = target.getClass().getField(fieldName);
            Object res = f.get(target);
            if (res instanceof Number num) return num.longValue();
        } catch (Throwable ignored) {}
        return 0L;
    }
}
