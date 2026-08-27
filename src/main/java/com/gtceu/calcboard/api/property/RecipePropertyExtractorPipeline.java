package com.gtceu.calcboard.api.property;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                    store.set(NodeProperties.EBF_TEMPERATURE, temp);
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
                if (categoryId != null && "gtceu".equals(categoryId.getNamespace())) return true;
                return backingRecipe != null && backingRecipe.getClass().getName().contains("GTRecipe");
            }

            @Override
            public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
                long euToStart = 0L;
                int minReflectorTier = 0;

                if (recipeDataTag != null) {
                    if (recipeDataTag.contains("eu_to_start")) euToStart = recipeDataTag.getLong("eu_to_start");
                    else if (recipeDataTag.contains("start_eu")) euToStart = recipeDataTag.getLong("start_eu");

                    if (recipeDataTag.contains("min_reflector_tier")) minReflectorTier = recipeDataTag.getInt("min_reflector_tier");
                    else if (recipeDataTag.contains("reflector_tier")) minReflectorTier = recipeDataTag.getInt("reflector_tier");
                    else if (recipeDataTag.contains("min_reflector")) minReflectorTier = recipeDataTag.getInt("min_reflector");
                    else if (recipeDataTag.contains("reflector")) minReflectorTier = recipeDataTag.getInt("reflector");
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
                                    if (euToStart <= 0 && cName.contains("Fusion")) {
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

                if (euToStart > 0) {
                    store.set(NodeProperties.FUSION_START_EU, euToStart);
                }
                if (minReflectorTier > 0) {
                    store.set(NodeProperties.REQUIRED_REFLECTOR_TIER, minReflectorTier);
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
                        store.set(NodeProperties.CLEANROOM_TYPE, cleanroom);
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
                return categoryId != null && (categoryId.getNamespace().equals("create") || categoryId.getNamespace().equals("createaddition"));
            }

            @Override
            public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
                if (!store.has(NodeProperties.KINETIC_RPM)) {
                    store.set(NodeProperties.KINETIC_RPM, 32);
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
