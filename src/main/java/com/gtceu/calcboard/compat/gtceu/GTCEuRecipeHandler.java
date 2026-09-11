package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeDetails;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTBoilerPhysics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles GTCEu recipe reflection and detail extraction (EUt, duration, voltage tier, ebf temp, steam boilers).
 * Delegated to GTCEuIngredientParser and GTCEuRecipeDetailExtractor.
 */
public class GTCEuRecipeHandler {

    private static final String GT_RECIPE_CLASS_NAME = "com.gregtechceu.gtceu.api.recipe.GTRecipe";
    private static final String[] RECIPE_BACKING_FIELDS = {
            "recipe", "gtRecipe", "backingRecipe", "backing", "originalRecipe", "delegate", "value"
    };
    private static final String[] DURATION_METHOD_NAMES = {"getDuration", "duration", "getDurationTicks"};
    private static final String[] EUt_METHOD_NAMES = {"getInputEUt", "getOutputEUt", "inputEUt", "outputEUt"};

    private static final ClassValue<Boolean> GT_SHAPE_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return computeGTRecipeShape(type);
        }
    };

    private static final ClassValue<List<Field>> BACKING_FIELDS_CACHE = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (String fieldName : RECIPE_BACKING_FIELDS) {
                Class<?> cur = type;
                while (cur != null && cur != Object.class) {
                    try {
                        Field field = cur.getDeclaredField(fieldName);
                        if (!Modifier.isStatic(field.getModifiers())) {
                            field.setAccessible(true);
                            fields.add(field);
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (SecurityException ignored) {
                        break;
                    }
                    cur = cur.getSuperclass();
                }
            }
            return fields;
        }
    };

    private static final ClassValue<Field> RECIPE_TYPE_FIELD_CACHE = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            try {
                Field f = type.getField("recipeType");
                f.setAccessible(true);
                return f;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    };

    private static final ClassValue<Field> INPUTS_FIELD_CACHE = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            try {
                Field f = type.getField("inputs");
                f.setAccessible(true);
                return f;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    };

    public static boolean isGTCategoryNamespace(String namespace) {
        if (namespace == null) return false;
        String ns = namespace.toLowerCase(Locale.ROOT);
        return ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start")
                || ns.equals("start") || ns.equals("star_technology");
    }

    public static boolean isGTRecipe(Object backing) {
        return matchesGTRecipeShape(unwrapRecipe(backing));
    }

    public static boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, RecipeDetails details) {
        if (backing == null && emiRecipeObj == null) return false;

        if (backing != null) {
            backing = unwrapRecipe(backing);
        }

        ResourceLocation catId = null;
        if (ModCompatHelper.isEmiLoaded()) {
            catId = EmiGTCEuHelper.getCategoryId(emiRecipeObj);
        }

        ResourceLocation recipeTypeId = null;
        if (backing != null && isGTRecipe(backing)) {
            Field recipeTypeField = RECIPE_TYPE_FIELD_CACHE.get(backing.getClass());
            if (recipeTypeField != null) {
                try {
                    Object rt = recipeTypeField.get(backing);
                    recipeTypeId = MultiblockDetector.extractRecipeTypeId(rt);
                } catch (ReflectiveOperationException ignored) {}
            }
        }

        boolean isGT = isGTRecipe(backing) || (catId != null && isGTCategoryNamespace(catId.getNamespace()));
        if (!isGT && backing == null) return false;

        boolean isGTBoiler = (catId != null && GTBoilerPhysics.isBoilerCategory(catId))
                || (recipeTypeId != null && GTBoilerPhysics.isBoilerCategory(recipeTypeId))
                || (ModCompatHelper.isEmiLoaded() && EmiGTCEuHelper.isBoiler(emiRecipeObj));

        if (isGTBoiler) {
            if (backing != null) {
                extractGTRecipeDetails(backing, details);
            }
            details.energyType = EnergyType.HEAT_OR_SELF;
            details.isGenerator = false;
            details.eut = 0.0;
            details.tier = GTVoltageTier.ULV;

            boolean isLiquidFuel = isLiquidFuelGTRecipe(backing);
            if (!isLiquidFuel && ModCompatHelper.isEmiLoaded()) {
                isLiquidFuel = EmiGTCEuHelper.isLiquidFuel(emiRecipeObj);
            }

            boolean isLargeBoiler = (catId != null && GTBoilerPhysics.isLargeBoilerCategory(catId))
                    || (recipeTypeId != null && GTBoilerPhysics.isLargeBoilerCategory(recipeTypeId))
                    || (ModCompatHelper.isEmiLoaded() && EmiGTCEuHelper.isLargeBoiler(emiRecipeObj));

            double baseSteamPerTick = isLargeBoiler ? 800.0 : (isLiquidFuel ? 15.0 : 6.0);
            double durationTicks = Math.max(1.0, details.durationTicks);
            double totalSteam = baseSteamPerTick * durationTicks;
            double totalWater = totalSteam / 160.0;

            details.overrideOutputs = true;
            details.customOutputs.clear();
            details.customOutputs.add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", totalSteam));

            if (ModCompatHelper.isEmiLoaded()) {
                EmiGTCEuHelper.enrichBucketOutputs(emiRecipeObj, details);
            }

            details.extraInputs.add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", totalWater));
            return true;
        }

        if (backing != null) {
            extractGTRecipeDetails(backing, details);
            return true;
        }
        return false;
    }

    private static boolean isLiquidFuelGTRecipe(Object backing) {
        if (backing == null) return false;
        Field inputsField = INPUTS_FIELD_CACHE.get(backing.getClass());
        if (inputsField != null) {
            try {
                Object inMap = inputsField.get(backing);
                if (inMap instanceof Map<?, ?> map && hasFluidCapabilityKey(map)) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        List<IngredientStack> inputs = extractGTRecipeContents(backing, "inputs");
        return inputs.stream().anyMatch(IngredientStack::isFluid);
    }

    private static boolean hasFluidCapabilityKey(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            if (key == null) continue;
            String kName = key.getClass().getSimpleName();
            if (kName.equals("FluidRecipeCapability") || key.toString().equalsIgnoreCase("fluid")) {
                return true;
            }
        }
        return false;
    }

    public static void extractGTRecipeDetails(Object backing, RecipeDetails details) {
        GTCEuRecipeDetailExtractor.extractGTRecipeDetails(backing, details);
    }

    public static double extractDuration(Object backing) {
        return GTCEuRecipeDetailExtractor.extractDuration(backing);
    }

    public static double extractOutputEUt(Object backing) {
        return GTCEuRecipeDetailExtractor.extractOutputEUt(backing);
    }

    public static double extractInputEUt(Object backing) {
        return GTCEuRecipeDetailExtractor.extractInputEUt(backing);
    }

    public static int extractRecipeTemperature(Object backing) {
        return GTCEuRecipeDetailExtractor.extractRecipeTemperature(backing);
    }

    public static CompoundTag extractRecipeDataTag(Object backing) {
        return GTCEuRecipeDetailExtractor.extractRecipeDataTag(backing);
    }

    public static List<IngredientStack> extractGTRecipeContents(Object gtRecipe, String fieldName) {
        return GTCEuIngredientParser.extractGTRecipeContents(gtRecipe, fieldName);
    }

    public static List<IngredientStack> extractTickIngredients(Object backing, String fieldName, double durationTicks) {
        return GTCEuIngredientParser.extractTickIngredients(backing, fieldName, durationTicks);
    }

    public static IngredientStack findMatchingTickIngredient(List<IngredientStack> tickIngredients, IngredientStack target) {
        return GTCEuIngredientParser.findMatchingTickIngredient(tickIngredients, target);
    }

    public static Object unwrapRecipe(Object backing) {
        if (backing == null) return null;

        Object cur = backing;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 8 && cur != null && visited.add(cur); i++) {
            if (matchesGTRecipeShape(cur)) return cur;

            Object next = readRecipeBackingField(cur);
            if (next == null || next == cur) break;
            cur = next;
        }

        return backing;
    }

    private static boolean matchesGTRecipeShape(Object candidate) {
        if (candidate == null) return false;
        return GT_SHAPE_CACHE.get(candidate.getClass());
    }

    private static boolean computeGTRecipeShape(Class<?> type) {
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            if (GT_RECIPE_CLASS_NAME.equals(cur.getName())) return true;
            cur = cur.getSuperclass();
        }

        if (hasField(type, "recipeType")
                && hasField(type, "duration")
                && hasField(type, "inputs")
                && hasField(type, "outputs")
                && hasField(type, "tickInputs")
                && hasField(type, "tickOutputs")) {
            return true;
        }

        return hasDurationSignal(type) && hasEUtSignal(type);
    }

    private static boolean hasDurationSignal(Class<?> type) {
        if (hasField(type, "duration") || hasField(type, "durationTicks")) return true;
        for (String mName : DURATION_METHOD_NAMES) {
            if (hasPublicMethod(type, mName)) return true;
        }
        return false;
    }

    private static boolean hasEUtSignal(Class<?> type) {
        for (String mName : EUt_METHOD_NAMES) {
            if (hasPublicMethod(type, mName)) return true;
        }
        return false;
    }

    private static boolean hasPublicMethod(Class<?> type, String methodName) {
        try {
            type.getMethod(methodName);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean hasField(Class<?> type, String fieldName) {
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            try {
                cur.getDeclaredField(fieldName);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (SecurityException ignored) {
                break;
            }
            cur = cur.getSuperclass();
        }
        return false;
    }

    private static Object readRecipeBackingField(Object holder) {
        for (Field field : BACKING_FIELDS_CACHE.get(holder.getClass())) {
            try {
                Object value = field.get(holder);
                if (value != null && value != holder) return value;
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static class EmiGTCEuHelper {
        private static ResourceLocation getCategoryId(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe && recipe.getCategory() != null) {
                return recipe.getCategory().getId();
            }
            return null;
        }

        private static boolean isBoiler(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
                if (er.getCategory() != null && GTBoilerPhysics.isBoilerCategory(er.getCategory().getId())) return true;
                if (er.getId() != null && GTBoilerPhysics.isBoilerCategory(er.getId())) return true;
            }
            return false;
        }

        private static boolean isLargeBoiler(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
                if (er.getCategory() != null && GTBoilerPhysics.isLargeBoilerCategory(er.getCategory().getId())) return true;
                if (er.getId() != null && GTBoilerPhysics.isLargeBoilerCategory(er.getId())) return true;
            }
            return false;
        }

        private static boolean isLiquidFuel(Object emiRecipeObj) {
            if (!(emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe) || emiRecipe.getInputs() == null) {
                return false;
            }
            for (var in : emiRecipe.getInputs()) {
                if (hasLiquidStack(in)) return true;
            }
            return false;
        }

        private static boolean hasLiquidStack(dev.emi.emi.api.stack.EmiIngredient in) {
            if (in == null || in.getEmiStacks() == null) return false;
            for (var st : in.getEmiStacks()) {
                if (isLiquidEmiStack(st)) return true;
            }
            return false;
        }

        private static boolean isLiquidEmiStack(dev.emi.emi.api.stack.EmiStack st) {
            if (st == null) return false;
            Object key = st.getKey();
            if (key instanceof net.minecraft.world.level.material.Fluid || (key != null && key.getClass().getSimpleName().endsWith("Fluid"))) {
                return true;
            }
            ResourceLocation sId = st.getId();
            if (sId != null && net.minecraftforge.registries.ForgeRegistries.FLUIDS.containsKey(sId)) {
                return true;
            }
            return st.getItemStack() != null
                    && st.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem bi
                    && bi.getFluid() != net.minecraft.world.level.material.Fluids.EMPTY;
        }

        private static void enrichBucketOutputs(Object emiRecipeObj, RecipeDetails details) {
            if (!(emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe) || emiRecipe.getInputs() == null) {
                return;
            }
            if (hasBucketInput(emiRecipe.getInputs())) {
                details.customOutputs.add(IngredientStack.item(ResourceLocation.tryParse("minecraft:bucket"), "Bucket", 1.0));
            }
        }

        private static boolean hasBucketInput(List<dev.emi.emi.api.stack.EmiIngredient> inputs) {
            for (var in : inputs) {
                if (in != null && in.getEmiStacks() != null && hasBucketStack(in.getEmiStacks())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasBucketStack(List<dev.emi.emi.api.stack.EmiStack> stacks) {
            for (var st : stacks) {
                if (st != null && st.getId() != null) {
                    String itemPath = st.getId().getPath();
                    if (itemPath.equals("lava_bucket") || itemPath.endsWith("_bucket")) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
