package com.gtceu.calcboard.api.property;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Registry and execution pipeline for {@link IRecipePropertyExtractor}.
 */
public final class RecipePropertyExtractorPipeline {

    private static final List<IRecipePropertyExtractor> EXTRACTORS = new ArrayList<>();

    static {
        // 1. GTCEu Electric Blast Furnace (EBF) Temperature Extractor
        register(new IRecipePropertyExtractor() {
            @Override
            public String getModId() {
                return "gtceu";
            }

            @Override
            public boolean matches(Object backingRecipe, ResourceLocation categoryId) {
                return (categoryId != null && categoryId.getNamespace().equals("gtceu"))
                        || (backingRecipe != null && backingRecipe.getClass().getName().contains("GTRecipe"));
            }

            @Override
            public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
                int temp = 0;
                if (recipeDataTag != null) {
                    if (recipeDataTag.contains("ebf_temp")) temp = recipeDataTag.getInt("ebf_temp");
                    else if (recipeDataTag.contains("temp")) temp = recipeDataTag.getInt("temp");
                    else if (recipeDataTag.contains("temperature")) temp = recipeDataTag.getInt("temperature");
                }
                if (temp > 0) {
                    store.setById("ebf_temperature", temp);
                }
            }
        });

        // 2. GTCEu Fusion Reactor Start Energy (EU to Start) Extractor (RFC-001 & RFC-002)
        register(new IRecipePropertyExtractor() {
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
                long euToStart = 0L;
                int minReflectorTier = 0;

                if (recipeDataTag != null) {
                    for (String key : recipeDataTag.getAllKeys()) {
                        String k = key.toLowerCase(Locale.ROOT);
                        if (k.contains("start") && (k.contains("eu") || k.contains("energy"))) {
                            try {
                                euToStart = recipeDataTag.getLong(key);
                            } catch (Throwable t) {
                                euToStart = (long) recipeDataTag.getInt(key);
                            }
                        } else if (k.contains("reflector")) {
                            minReflectorTier = recipeDataTag.getInt(key);
                        }
                    }
                }

                // Also check conditions via reflection if not found in data tag
                if (backingRecipe != null) {
                    try {
                        Method mConds = backingRecipe.getClass().getMethod("conditions");
                        Object conds = mConds.invoke(backingRecipe);
                        if (conds instanceof List<?> condList) {
                            for (Object c : condList) {
                                if (c != null) {
                                    String cName = c.getClass().getName();
                                    if (euToStart <= 0 && (cName.contains("Fusion") || cName.contains("Reflector"))) {
                                        try {
                                            Method mEu = c.getClass().getMethod("getEuToStart");
                                            Object res = mEu.invoke(c);
                                            if (res instanceof Number num && num.longValue() > 0) {
                                                euToStart = num.longValue();
                                            }
                                        } catch (Throwable ignored) {}
                                        try {
                                            Field fEu = c.getClass().getField("euToStart");
                                            Object res = fEu.get(c);
                                            if (res instanceof Number num && num.longValue() > 0) {
                                                euToStart = num.longValue();
                                            }
                                        } catch (Throwable ignored) {}
                                    }

                                    if (minReflectorTier <= 0 && (cName.contains("Reflector") || cName.contains("Fusion"))) {
                                        try {
                                            Method mRefl = c.getClass().getMethod("getReflectorTier");
                                            Object res = mRefl.invoke(c);
                                            if (res instanceof Number num && num.intValue() > 0) {
                                                minReflectorTier = num.intValue();
                                            }
                                        } catch (Throwable ignored) {}
                                        try {
                                            Method mRefl = c.getClass().getMethod("getMinReflectorTier");
                                            Object res = mRefl.invoke(c);
                                            if (res instanceof Number num && num.intValue() > 0) {
                                                minReflectorTier = num.intValue();
                                            }
                                        } catch (Throwable ignored) {}
                                        try {
                                            Field fRefl = c.getClass().getField("reflectorTier");
                                            Object res = fRefl.get(c);
                                            if (res instanceof Number num && num.intValue() > 0) {
                                                minReflectorTier = num.intValue();
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
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
        });

        // 3. GTCEu Cleanroom Condition Extractor
        register(new IRecipePropertyExtractor() {
            @Override
            public String getModId() {
                return "gtceu";
            }

            @Override
            public boolean matches(Object backingRecipe, ResourceLocation categoryId) {
                return (categoryId != null && categoryId.getNamespace().equals("gtceu"))
                        || (backingRecipe != null && backingRecipe.getClass().getName().contains("GTRecipe"));
            }

            @Override
            public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
                if (recipeDataTag != null && recipeDataTag.contains("cleanroom")) {
                    String cleanroom = recipeDataTag.getString("cleanroom");
                    if (cleanroom != null && !cleanroom.isEmpty()) {
                        store.setById("cleanroom_type", cleanroom);
                    }
                }
            }
        });

        // 4. Create Kinetic Speed / RPM Extractor
        register(new IRecipePropertyExtractor() {
            @Override
            public String getModId() {
                return "create";
            }

            @Override
            public boolean matches(Object backingRecipe, ResourceLocation categoryId) {
                return categoryId != null && com.gtceu.calcboard.api.util.ModCompatHelper.isCreateFamilyNamespace(categoryId.getNamespace());
            }

            @Override
            public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
                if (!store.hasById("kinetic_rpm")) {
                    store.setById("kinetic_rpm", 32);
                }
            }
        });
    }

    private RecipePropertyExtractorPipeline() {}

    public static synchronized void register(IRecipePropertyExtractor extractor) {
        if (extractor != null && !EXTRACTORS.contains(extractor)) {
            EXTRACTORS.add(extractor);
        }
    }

    public static List<IRecipePropertyExtractor> getExtractors() {
        return Collections.unmodifiableList(EXTRACTORS);
    }

    /**
     * Executes all matching extractors on the given recipe data and populates the store.
     */
    public static void extractAll(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
        if (store == null) return;
        for (IRecipePropertyExtractor extractor : EXTRACTORS) {
            try {
                if (extractor.matches(backingRecipe, categoryId)) {
                    extractor.extract(backingRecipe, recipeDataTag, categoryId, store);
                }
            } catch (Throwable ignored) {}
        }
    }
}
