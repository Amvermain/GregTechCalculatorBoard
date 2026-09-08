package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EmiSlotChanceExtractor {

    private EmiSlotChanceExtractor() {}

    public record SlotChance(ResourceLocation id, double chance, double tierChanceBoost) {}

    public static void applySlotChance(IngredientStack stack, int index, List<SlotChance> chances, boolean[] used) {
        if (stack == null || stack.getId() == null) return;
        ResourceLocation id = stack.getId();
        if (index < chances.size() && !used[index] && id.equals(chances.get(index).id())) {
            stack.setChance(chances.get(index).chance());
            stack.setTierChanceBoost(chances.get(index).tierChanceBoost());
            used[index] = true;
            return;
        }
        for (int j = 0; j < chances.size(); j++) {
            if (used[j] || !id.equals(chances.get(j).id())) continue;
            stack.setChance(chances.get(j).chance());
            stack.setTierChanceBoost(chances.get(j).tierChanceBoost());
            used[j] = true;
            return;
        }
    }

    public static List<SlotChance> extractSlotChances(EmiRecipe recipe, boolean isInput) {
        List<SlotChance> list = new ArrayList<>();
        if (recipe == null) return list;
        Object backing = EmiRecipeConverter.unwrapBackingRecipe(recipe);
        if (backing == null) backing = recipe.getBackingRecipe();
        if (backing == null) return list;

        String key = isInput ? "inputs" : "outputs";

        if (ModCompatHelper.isGTLoaded() && com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.isGTRecipe(backing)) {
            List<SlotChance> gtChances = extractGTRecipeSlotChances(backing, key);
            if (!gtChances.isEmpty()) return gtChances;
        }

        if (!isInput) {
            List<SlotChance> createChances = extractCreateRollableChances(backing);
            if (!createChances.isEmpty()) return createChances;
        }

        if (ModCompatHelper.isGTLoaded() && backing.getClass().getName().contains("GTRecipe")) {
            list.addAll(extractGTFallbackSlotChances(backing, key));
        }
        return list;
    }

    private static List<SlotChance> extractGTRecipeSlotChances(Object backing, String key) {
        List<SlotChance> list = new ArrayList<>();
        List<IngredientStack> gtStacks = com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.extractGTRecipeContents(backing, key);
        if (gtStacks != null && !gtStacks.isEmpty()) {
            for (IngredientStack is : gtStacks) {
                if (is != null && is.getId() != null) {
                    list.add(new SlotChance(is.getId(), is.getChance(), is.getTierChanceBoost()));
                }
            }
        }
        String tickKey = "inputs".equalsIgnoreCase(key) ? "tickInputs" : "tickOutputs";
        List<IngredientStack> gtTickStacks = com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.extractGTRecipeContents(backing, tickKey);
        if (gtTickStacks != null && !gtTickStacks.isEmpty()) {
            for (IngredientStack is : gtTickStacks) {
                if (is != null && is.getId() != null) {
                    list.add(new SlotChance(is.getId(), is.getChance(), is.getTierChanceBoost()));
                }
            }
        }
        return list;
    }

    private static List<SlotChance> extractCreateRollableChances(Object backing) {
        List<SlotChance> list = new ArrayList<>();
        try {
            Method m = backing.getClass().getMethod("getRollableResults");
            Object res = m.invoke(backing);
            if (!(res instanceof List<?> rollableList)) return list;
            for (Object po : rollableList) {
                SlotChance sc = parseCreateRollableSlot(po);
                if (sc != null) list.add(sc);
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private static SlotChance parseCreateRollableSlot(Object po) {
        if (po == null) return null;
        try {
            Method getStackM = po.getClass().getMethod("getStack");
            Method getChanceM = po.getClass().getMethod("getChance");
            Object stackObj = getStackM.invoke(po);
            Object chanceObj = getChanceM.invoke(po);
            if (stackObj instanceof net.minecraft.world.item.ItemStack is && chanceObj instanceof Number n) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(is.getItem());
                if (id != null) {
                    double ch = Math.max(0.0, Math.min(1.0, n.doubleValue()));
                    return new SlotChance(id, ch, 0.0);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static List<SlotChance> extractGTFallbackSlotChances(Object backing, String key) {
        List<SlotChance> list = new ArrayList<>();
        try {
            Field slotsField = getGTField(backing, key);
            if (slotsField == null) return list;
            Object slotsObj = slotsField.get(backing);
            if (!(slotsObj instanceof Map<?, ?> slotMap)) return list;
            for (Object listObj : slotMap.values()) {
                if (listObj instanceof List<?> contentList) {
                    parseGTContentList(contentList, list);
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private static Field getGTField(Object backing, String name) {
        try {
            return backing.getClass().getField(name);
        } catch (Throwable ignored) {
            try {
                Field f = backing.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private static void parseGTContentList(List<?> contentList, List<SlotChance> target) {
        for (Object contentObj : contentList) {
            SlotChance sc = parseGTContentSlot(contentObj);
            if (sc != null) target.add(sc);
        }
    }

    private static SlotChance parseGTContentSlot(Object contentObj) {
        if (contentObj == null) return null;
        double chance = extractGTContentChance(contentObj);
        double boost = extractGTContentBoost(contentObj);
        ResourceLocation resId = extractContentResourceId(contentObj);
        return resId != null ? new SlotChance(resId, chance, boost) : null;
    }

    private static double extractGTContentChance(Object contentObj) {
        double chance = 1.0;
        try {
            Field f = contentObj.getClass().getField("chance");
            Object v = f.get(contentObj);
            if (v instanceof Number n) chance = n.doubleValue();
        } catch (Throwable ignored) {
            chance = invokeChanceMethod(contentObj);
        }
        if (chance > 1.0) chance = chance / 10000.0;
        return Math.max(0.0, Math.min(1.0, chance));
    }

    private static double invokeChanceMethod(Object contentObj) {
        for (String mName : new String[]{"chance", "getChance"}) {
            try {
                Method m = contentObj.getClass().getMethod(mName);
                Object v = m.invoke(contentObj);
                if (v instanceof Number n) return n.doubleValue();
            } catch (Throwable ignored) {}
        }
        return 1.0;
    }

    private static double extractGTContentBoost(Object contentObj) {
        double boost = 0.0;
        try {
            Field f = contentObj.getClass().getField("tierChanceBoost");
            Object v = f.get(contentObj);
            if (v instanceof Number n) boost = n.doubleValue();
        } catch (Throwable ignored) {
            boost = invokeBoostMethod(contentObj);
        }
        if (Math.abs(boost) > 1.0) boost = boost / 10000.0;
        return boost;
    }

    private static double invokeBoostMethod(Object contentObj) {
        for (String mName : new String[]{"tierChanceBoost", "getTierChanceBoost"}) {
            try {
                Method m = contentObj.getClass().getMethod(mName);
                Object v = m.invoke(contentObj);
                if (v instanceof Number n) return n.doubleValue();
            } catch (Throwable ignored) {}
        }
        return 0.0;
    }

    private static ResourceLocation extractContentResourceId(Object contentObj) {
        if (contentObj == null) return null;
        try {
            Object inner = contentObj;
            for (int depth = 0; depth < 5 && inner != null; depth++) {
                ResourceLocation direct = resolveDirectResourceId(inner);
                if (direct != null) return direct;

                if (inner instanceof List<?> list && !list.isEmpty()) {
                    inner = list.get(0);
                    continue;
                }

                Object next = probeMethods(inner);
                if (next == null) {
                    next = probeFields(inner);
                }
                if (next == null || next == inner) break;
                inner = next;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ResourceLocation resolveDirectResourceId(Object inner) {
        if (inner instanceof net.minecraft.world.item.ItemStack is) {
            return is.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(is.getItem());
        }
        if (inner instanceof net.minecraft.world.item.Item it) {
            return ForgeRegistries.ITEMS.getKey(it);
        }
        if (inner instanceof net.minecraft.world.item.crafting.Ingredient ing) {
            net.minecraft.world.item.ItemStack[] items = ing.getItems();
            if (items != null && items.length > 0 && !items[0].isEmpty()) {
                return ForgeRegistries.ITEMS.getKey(items[0].getItem());
            }
        }
        if (inner instanceof Fluid fl) {
            return ForgeRegistries.FLUIDS.getKey(fl);
        }
        if (inner instanceof net.minecraft.world.item.ItemStack[] arr) {
            if (arr.length > 0 && !arr[0].isEmpty()) {
                return ForgeRegistries.ITEMS.getKey(arr[0].getItem());
            }
        }
        if (inner.getClass().getName().contains("FluidStack")) {
            try {
                Method gm = inner.getClass().getMethod("getFluid");
                Object flObj = gm.invoke(inner);
                if (flObj instanceof Fluid fl) {
                    return ForgeRegistries.FLUIDS.getKey(fl);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object probeMethods(Object inner) {
        String[] methodNames = {"content", "getContent", "getInner", "getStack", "getItems", "getMatchingStacks", "getItemStack", "getFluid", "getRawFluid", "getIngredient", "inner"};
        for (String mName : methodNames) {
            try {
                Method m = inner.getClass().getMethod(mName);
                Object next = m.invoke(inner);
                if (next != null && next != inner) return next;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object probeFields(Object inner) {
        String[] fieldNames = {"content", "inner", "stack", "itemStack", "ingredient", "fluid"};
        for (String fName : fieldNames) {
            try {
                Field f = getGTField(inner, fName);
                if (f != null) {
                    Object next = f.get(inner);
                    if (next != null && next != inner) return next;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
