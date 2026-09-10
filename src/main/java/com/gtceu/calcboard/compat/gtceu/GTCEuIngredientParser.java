package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.RecipeConversionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Parses and extracts ingredient stacks and contents from GTCEu recipes.
 */
public final class GTCEuIngredientParser {

    private GTCEuIngredientParser() {}

    private record ContentAccessors(
            Method innerMethod,
            Field innerField,
            Method chanceMethod,
            Field chanceField,
            Method boostMethod,
            Field boostField
    ) {}

    private static final String[] INNER_METHOD_NAMES = {
            "content", "getContent", "getInner", "getStack", "getItems", "getItemStack", "getFluid", "getIngredient", "inner"
    };
    private static final String[] INNER_FIELD_NAMES = {
            "content", "inner", "stack", "itemStack", "ingredient", "fluid"
    };
    private static final String[] CHANCE_METHOD_NAMES = {"chance", "getChance"};
    private static final String[] CHANCE_FIELD_NAMES = {"chance"};
    private static final String[] BOOST_METHOD_NAMES = {"tierChanceBoost", "getTierChanceBoost", "tierBoost"};
    private static final String[] BOOST_FIELD_NAMES = {"tierChanceBoost"};

    private static final ClassValue<ContentAccessors> ACCESSOR_CACHE = new ClassValue<>() {
        @Override
        protected ContentAccessors computeValue(Class<?> type) {
            Method innerM = findFirstMethod(type, INNER_METHOD_NAMES);
            Field innerF = (innerM == null) ? findFirstField(type, INNER_FIELD_NAMES) : null;
            Method chanceM = findFirstMethod(type, CHANCE_METHOD_NAMES);
            Field chanceF = findFirstField(type, CHANCE_FIELD_NAMES);
            Method boostM = findFirstMethod(type, BOOST_METHOD_NAMES);
            Field boostF = findFirstField(type, BOOST_FIELD_NAMES);
            return new ContentAccessors(innerM, innerF, chanceM, chanceF, boostM, boostF);
        }
    };

    private static final ClassValue<Function<Object, Object>> DATA_SUB_OBJECT_EXTRACTOR_CACHE = new ClassValue<>() {
        @Override
        protected Function<Object, Object> computeValue(Class<?> type) {
            for (String mName : new String[]{"data", "getData", "getRecipeData"}) {
                try {
                    Method m = type.getMethod(mName);
                    m.setAccessible(true);
                    return obj -> {
                        try {
                            return m.invoke(obj);
                        } catch (ReflectiveOperationException e) {
                            return null;
                        }
                    };
                } catch (ReflectiveOperationException ignored) {}
            }
            for (String fName : new String[]{"data", "recipeData", "mRecipeData"}) {
                Field f = findFirstField(type, new String[]{fName});
                if (f != null) {
                    return obj -> {
                        try {
                            return f.get(obj);
                        } catch (ReflectiveOperationException e) {
                            return null;
                        }
                    };
                }
            }
            return obj -> null;
        }
    };

    private static final String[] FIELD_NAMES_TO_CACHE = {"inputs", "outputs", "tickInputs", "tickOutputs"};

    private static final ClassValue<Map<String, Function<Object, Object>>> MAP_ACCESSORS_CACHE = new ClassValue<>() {
        @Override
        protected Map<String, Function<Object, Object>> computeValue(Class<?> type) {
            Map<String, Function<Object, Object>> map = new HashMap<>();
            for (String fieldName : FIELD_NAMES_TO_CACHE) {
                Function<Object, Object> fn = computeMapExtractor(type, fieldName);
                if (fn != null) {
                    map.put(fieldName, fn);
                }
            }
            return map;
        }
    };

    private static Function<Object, Object> computeMapExtractor(Class<?> type, String fieldName) {
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String[] methodCandidates = {fieldName, "get" + capitalized, "get" + capitalized + "Contents", fieldName + "Contents"};

        for (String mName : methodCandidates) {
            try {
                Method m = type.getMethod(mName);
                m.setAccessible(true);
                return obj -> {
                    try {
                        return m.invoke(obj);
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                };
            } catch (ReflectiveOperationException ignored) {}
        }

        Field f = findFirstField(type, new String[]{fieldName});
        if (f != null) {
            return obj -> {
                try {
                    return f.get(obj);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            };
        }
        return null;
    }

    private static final ClassValue<Method> INNER_AMOUNT_METHOD_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("getAmount");
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    };

    private static final ClassValue<Method> INNER_STACKS_METHOD_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("getStacks");
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    };

    private record DescriptorAccessors(Method getFluidMethod, Method getAmountMethod, Method getDisplayNameMethod) {}

    private static final ClassValue<DescriptorAccessors> DESCRIPTOR_ACCESSORS_CACHE = new ClassValue<>() {
        @Override
        protected DescriptorAccessors computeValue(Class<?> type) {
            Method fluidM = findFirstMethod(type, new String[]{"getFluid"});
            Method amountM = findFirstMethod(type, new String[]{"getAmount"});
            Method displayM = findFirstMethod(type, new String[]{"getDisplayName"});
            return new DescriptorAccessors(fluidM, amountM, displayM);
        }
    };

    private static Method findFirstMethod(Class<?> type, String[] names) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Field findFirstField(Class<?> type, String[] names) {
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            for (String name : names) {
                try {
                    Field f = cur.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (ReflectiveOperationException ignored) {}
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    public static List<IngredientStack> extractGTRecipeContents(Object gtRecipe, String fieldName) {
        List<IngredientStack> result = new ArrayList<>();
        if (gtRecipe == null) return result;

        Object unwrapped = GTCEuRecipeHandler.unwrapRecipe(gtRecipe);
        if (unwrapped == null) unwrapped = gtRecipe;

        Object mapObj = findContentMap(unwrapped, fieldName);
        if (mapObj instanceof Map<?, ?> map) {
            boolean isInput = fieldName != null && fieldName.toLowerCase(Locale.ROOT).contains("input");
            for (Object contentList : map.values()) {
                if (contentList instanceof List<?> list) {
                    parseAndCollectGTContents(list, isInput, result);
                }
            }
        }
        return result;
    }

    private static Object findContentMap(Object unwrapped, String fieldName) {
        Object mapObj = findMapOnObject(unwrapped, fieldName);
        if (mapObj != null) return mapObj;

        Object dataObj = findDataSubObject(unwrapped);
        if (dataObj != null) {
            return findMapOnObject(dataObj, fieldName);
        }
        return null;
    }

    private static Object findDataSubObject(Object target) {
        if (target == null) return null;
        Object res = DATA_SUB_OBJECT_EXTRACTOR_CACHE.get(target.getClass()).apply(target);
        return (res != null && res != target) ? res : null;
    }

    private static Object findMapOnObject(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        Function<Object, Object> extractor = MAP_ACCESSORS_CACHE.get(target.getClass()).get(fieldName);
        if (extractor == null) return null;
        Object res = extractor.apply(target);
        return (res instanceof Map<?, ?>) ? res : null;
    }

    public static void parseAndCollectGTContents(List<?> list, boolean isInput, List<IngredientStack> result) {
        if (list == null) return;
        for (Object contentObj : list) {
            IngredientStack is = parseGTContent(contentObj);
            if (is == null || is.getId() == null) continue;
            if (isInput && RecipeConversionHelper.isIgnoredInput(is.getId(), is.getChance())) continue;
            if (!isInput && RecipeConversionHelper.isDummyConditionMarker(is.getId())) continue;
            result.add(is);
        }
    }

    public static List<IngredientStack> extractTickIngredients(Object backing, String fieldName, double durationTicks) {
        List<IngredientStack> result = new ArrayList<>();
        if (backing == null) return result;
        Object unwrapped = GTCEuRecipeHandler.unwrapRecipe(backing);
        List<IngredientStack> raw = extractGTRecipeContents(unwrapped, fieldName);
        if (raw == null || raw.isEmpty()) return result;
        double multiplier = Math.max(1.0, durationTicks);
        for (IngredientStack stack : raw) {
            if (stack == null) continue;
            result.add(stack.withAmount(stack.getAmount() * multiplier));
        }
        return result;
    }

    public static IngredientStack findMatchingTickIngredient(List<IngredientStack> tickIngredients, IngredientStack target) {
        if (tickIngredients == null || target == null || target.getId() == null) return null;
        for (IngredientStack tick : tickIngredients) {
            if (tick != null && target.getId().equals(tick.getId()) && target.isFluid() == tick.isFluid()) {
                return tick;
            }
        }
        return null;
    }

    public static IngredientStack parseGTContent(Object contentObj) {
        if (contentObj == null) return null;
        try {
            ContentAccessors accessors = ACCESSOR_CACHE.get(contentObj.getClass());
            Object inner = resolveInner(contentObj, accessors);
            double chance = resolveChance(contentObj, accessors);
            double tierChanceBoost = resolveTierChanceBoost(contentObj, accessors);

            IngredientStack is = convertToIngredientStack(inner, chance);
            if (is != null && Math.abs(tierChanceBoost) > 0.00001) {
                is.setTierChanceBoost(tierChanceBoost);
            }
            return is;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object resolveInner(Object contentObj, ContentAccessors accessors) throws ReflectiveOperationException {
        if (accessors.innerMethod() != null) {
            Object res = accessors.innerMethod().invoke(contentObj);
            if (res != null && res != contentObj) return res;
        }
        if (accessors.innerField() != null) {
            Object res = accessors.innerField().get(contentObj);
            if (res != null && res != contentObj) return res;
        }
        return contentObj;
    }

    private static double resolveChance(Object contentObj, ContentAccessors accessors) {
        double chance = 1.0;
        try {
            if (accessors.chanceField() != null) {
                Object val = accessors.chanceField().get(contentObj);
                if (val instanceof Number n) chance = n.doubleValue();
            } else if (accessors.chanceMethod() != null) {
                Object val = accessors.chanceMethod().invoke(contentObj);
                if (val instanceof Number n) chance = n.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {}

        if (chance > 1.0) {
            chance = chance / 10000.0;
        }
        return Math.max(0.0, Math.min(1.0, chance));
    }

    private static double resolveTierChanceBoost(Object contentObj, ContentAccessors accessors) {
        double boost = 0.0;
        try {
            if (accessors.boostField() != null) {
                Object val = accessors.boostField().get(contentObj);
                if (val instanceof Number n) boost = n.doubleValue();
            } else if (accessors.boostMethod() != null) {
                Object val = accessors.boostMethod().invoke(contentObj);
                if (val instanceof Number n) boost = n.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {}

        if (Math.abs(boost) > 1.0) {
            boost = boost / 10000.0;
        }
        return boost;
    }

    private static IngredientStack convertToIngredientStack(Object inner, double chance) {
        if (inner instanceof ItemStack stack) {
            if (stack.isEmpty()) return null;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return IngredientStack.item(id, stack.getHoverName().getString(), stack.getCount(), (float) chance);
        }
        if (inner instanceof FluidStack fStack) {
            if (fStack.isEmpty()) return null;
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fStack.getFluid());
            return IngredientStack.fluid(id, fStack.getDisplayName().getString(), fStack.getAmount(), (float) chance);
        }
        if (inner instanceof net.minecraft.world.item.crafting.Ingredient ing) {
            return convertCraftingIngredient(inner, ing, chance);
        }
        if (inner != null && inner.getClass().getName().contains("FluidIngredient")) {
            return convertFluidIngredient(inner, chance);
        }
        if (inner instanceof IngredientStack existing) {
            IngredientStack copied = existing.copy();
            copied.setChance((float) chance);
            return copied;
        }
        return null;
    }

    private static IngredientStack convertCraftingIngredient(Object inner, net.minecraft.world.item.crafting.Ingredient ing, double chance) {
        ItemStack[] items = ing.getItems();
        if (items == null || items.length == 0 || items[0].isEmpty()) return null;

        ItemStack first = items[0];
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(first.getItem());
        long amount = 1;
        Method getAmountMethod = INNER_AMOUNT_METHOD_CACHE.get(inner.getClass());
        if (getAmountMethod != null) {
            try {
                amount = ((Number) getAmountMethod.invoke(inner)).longValue();
            } catch (ReflectiveOperationException ignored) {}
        }

        IngredientStack is = IngredientStack.item(id, first.getHoverName().getString(), amount, (float) chance);
        for (int i = 1; i < items.length; i++) {
            ItemStack alt = items[i];
            if (alt != null && !alt.isEmpty()) {
                ResourceLocation altId = ForgeRegistries.ITEMS.getKey(alt.getItem());
                if (altId != null && !is.getAlternatives().contains(altId)) {
                    is.getAlternatives().add(altId);
                }
            }
        }
        return is;
    }

    private static IngredientStack convertFluidIngredient(Object inner, double chance) {
        Method getStacksMethod = INNER_STACKS_METHOD_CACHE.get(inner.getClass());
        if (getStacksMethod != null) {
            try {
                Object res = getStacksMethod.invoke(inner);
                if (res instanceof FluidStack[] fArray && fArray.length > 0) {
                    return convertFluidStackArray(fArray, chance);
                }
                if (res instanceof Object[] ldArray && ldArray.length > 0) {
                    return convertFluidDescriptorArray(ldArray, chance);
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static IngredientStack convertFluidStackArray(FluidStack[] fArray, double chance) {
        FluidStack first = fArray[0];
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(first.getFluid());
        String name = "";
        try {
            name = first.getDisplayName().getString();
        } catch (Throwable ignored) {}
        if ((name == null || name.isEmpty()) && id != null) {
            name = formatFallbackName(id);
        }
        IngredientStack is = IngredientStack.fluid(id, name, first.getAmount(), (float) chance);
        for (int i = 1; i < fArray.length; i++) {
            FluidStack alt = fArray[i];
            if (alt != null && !alt.isEmpty()) {
                ResourceLocation altId = ForgeRegistries.FLUIDS.getKey(alt.getFluid());
                if (altId != null && !is.getAlternatives().contains(altId)) {
                    is.getAlternatives().add(altId);
                }
            }
        }
        return is;
    }

    private static IngredientStack convertFluidDescriptorArray(Object[] ldArray, double chance) {
        try {
            Object first = ldArray[0];
            DescriptorAccessors accessors = DESCRIPTOR_ACCESSORS_CACHE.get(first.getClass());
            if (accessors.getFluidMethod() == null) return null;

            Fluid fluid = (Fluid) accessors.getFluidMethod().invoke(first);
            ResourceLocation id = (fluid != null) ? ForgeRegistries.FLUIDS.getKey(fluid) : null;
            if (id == null) return null;

            long amount = 1;
            if (accessors.getAmountMethod() != null) {
                try {
                    amount = ((Number) accessors.getAmountMethod().invoke(first)).longValue();
                } catch (ReflectiveOperationException ignored) {}
            }

            String name = "";
            if (accessors.getDisplayNameMethod() != null) {
                try {
                    name = ((Component) accessors.getDisplayNameMethod().invoke(first)).getString();
                } catch (ReflectiveOperationException ignored) {}
            }
            if (name.isEmpty()) {
                name = formatFallbackName(id);
            }

            IngredientStack is = IngredientStack.fluid(id, name, amount, (float) chance);
            for (int i = 1; i < ldArray.length; i++) {
                Object alt = ldArray[i];
                if (alt == null) continue;
                try {
                    Fluid altFluid = (Fluid) accessors.getFluidMethod().invoke(alt);
                    ResourceLocation altId = (altFluid != null) ? ForgeRegistries.FLUIDS.getKey(altFluid) : null;
                    if (altId != null && !is.getAlternatives().contains(altId)) {
                        is.getAlternatives().add(altId);
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
            return is;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static String formatFallbackName(ResourceLocation id) {
        if (id == null) return "";
        String path = id.getPath();
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : path;
    }
}
