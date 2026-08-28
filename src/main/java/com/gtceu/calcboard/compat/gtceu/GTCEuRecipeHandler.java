package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles GTCEu recipe reflection and detail extraction (EUt, duration, voltage tier, ebf temp, steam boilers).
 */
public class GTCEuRecipeHandler {

    public static boolean isGTRecipe(Object backing) {
        if (backing == null) return false;
        Class<?> cur = backing.getClass();
        while (cur != null && cur != Object.class) {
            if (cur.getName().contains("GTRecipe")) return true;
            for (Class<?> iface : cur.getInterfaces()) {
                if (iface.getName().contains("GTRecipe")) return true;
            }
            cur = cur.getSuperclass();
        }

        try {
            backing.getClass().getMethod("getInputEUt");
            return true;
        } catch (Throwable ignored) {}
        try {
            backing.getClass().getMethod("getOutputEUt");
            return true;
        } catch (Throwable ignored) {}
        try {
            Field f = backing.getClass().getField("recipeType");
            if (f != null) return true;
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        if (backing == null && emiRecipeObj == null) return false;

        ResourceLocation catId = null;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            catId = EmiGTCEuHelper.getCategoryId(emiRecipeObj);
        }

        boolean isGT = isGTRecipe(backing) || (catId != null && (catId.getNamespace().equals("gtceu") || catId.getNamespace().equals("start_core") || catId.getNamespace().equals("gtceu_start") || catId.getNamespace().equals("start")));
        if (!isGT && backing == null) return false;

        boolean isGTBoiler = false;
        if (catId != null && (catId.getPath().contains("boiler") || catId.getPath().contains("steam_boiler"))) {
            isGTBoiler = true;
        }
        if (!isGTBoiler && com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            isGTBoiler = EmiGTCEuHelper.isBoiler(emiRecipeObj);
        }
        if (!isGTBoiler && backing != null && isGTRecipe(backing)) {
            if (catId != null && catId.getPath().contains("boiler")) {
                isGTBoiler = true;
            }
        }

        if (isGTBoiler) {
            if (backing != null) {
                extractGTRecipeDetails(backing, details);
            }
            details.energyType = EnergyType.HEAT_OR_SELF;
            details.isGenerator = false;
            details.eut = 0.0;
            details.tier = GTVoltageTier.ULV;

            boolean isLiquidFuel = false;
            if (backing != null) {
                try {
                    Field inputsField = backing.getClass().getField("inputs");
                    Object inMap = inputsField.get(backing);
                    if (inMap instanceof Map<?, ?> map) {
                        for (Object key : map.keySet()) {
                            if (key != null) {
                                String kName = key.getClass().getName().toLowerCase(Locale.ROOT);
                                if (kName.contains("fluid") || key.toString().toLowerCase(Locale.ROOT).contains("fluid")) {
                                    isLiquidFuel = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (!isLiquidFuel && com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
                isLiquidFuel = EmiGTCEuHelper.isLiquidFuel(emiRecipeObj);
            }

            boolean isLargeBoiler = false;
            if (catId != null && catId.getPath().contains("large_boiler")) {
                isLargeBoiler = true;
            }
            if (!isLargeBoiler && com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
                isLargeBoiler = EmiGTCEuHelper.isLargeBoiler(emiRecipeObj);
            }
            if (!isLargeBoiler && backing != null && isGTRecipe(backing)) {
                try {
                    Field recipeTypeField = backing.getClass().getField("recipeType");
                    Object rt = recipeTypeField.get(backing);
                    if (rt != null && rt.toString().toLowerCase(Locale.ROOT).contains("large_boiler")) {
                        isLargeBoiler = true;
                    }
                } catch (Throwable ignored) {}
            }

            double baseSteamPerTick;
            if (isLargeBoiler) {
                baseSteamPerTick = 800.0;
            } else {
                baseSteamPerTick = isLiquidFuel ? 15.0 : 6.0;
            }

            double durationTicks = Math.max(1.0, details.durationTicks);
            double totalSteam = baseSteamPerTick * durationTicks;
            double totalWater = totalSteam / 160.0; // 1 mB Water = 160 mB Steam

            details.overrideOutputs = true;
            details.customOutputs.clear();
            details.customOutputs.add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", totalSteam));

            if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
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

    private static class EmiGTCEuHelper {
        private static ResourceLocation getCategoryId(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe && recipe.getCategory() != null) {
                return recipe.getCategory().getId();
            }
            return null;
        }

        private static boolean isBoiler(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
                if (er.getId() != null && er.getId().getPath().contains("boiler")) return true;
                if (er.getCategory() != null && er.getCategory().getId() != null && er.getCategory().getId().getPath().contains("boiler")) return true;
            }
            return false;
        }

        private static boolean isLargeBoiler(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
                if (er.getId() != null && er.getId().getPath().contains("large_boiler")) return true;
                if (er.getCategory() != null && er.getCategory().getId() != null && er.getCategory().getId().getPath().contains("large_boiler")) return true;
            }
            return false;
        }

        private static boolean isLiquidFuel(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe && emiRecipe.getInputs() != null) {
                for (var in : emiRecipe.getInputs()) {
                    if (in != null && in.getEmiStacks() != null) {
                        for (var st : in.getEmiStacks()) {
                            if (st != null) {
                                Object key = st.getKey();
                                if (key instanceof net.minecraft.world.level.material.Fluid || (key != null && key.getClass().getName().contains("Fluid"))) {
                                    return true;
                                }
                                ResourceLocation sId = st.getId();
                                if (sId != null && net.minecraftforge.registries.ForgeRegistries.FLUIDS.containsKey(sId)) {
                                    return true;
                                }
                                if (st.getItemStack() != null && st.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem bi && bi.getFluid() != net.minecraft.world.level.material.Fluids.EMPTY) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        private static void enrichBucketOutputs(Object emiRecipeObj, EmiRecipeConverter.RecipeDetails details) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe && emiRecipe.getInputs() != null) {
                for (var in : emiRecipe.getInputs()) {
                    if (in != null && in.getEmiStacks() != null) {
                        for (var st : in.getEmiStacks()) {
                            if (st != null && st.getId() != null) {
                                String itemPath = st.getId().getPath();
                                if (itemPath.equals("lava_bucket") || itemPath.endsWith("_bucket")) {
                                    details.customOutputs.add(IngredientStack.item(ResourceLocation.tryParse("minecraft:bucket"), "Bucket", 1.0));
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void extractGTRecipeDetails(Object backing, EmiRecipeConverter.RecipeDetails details) {
        if (backing == null || details == null) return;

        double duration = extractDuration(backing);
        if (duration > 0.0) {
            details.durationTicks = duration;
        }

        // 1. Check generator (Output EU)
        double outputEUt = extractOutputEUt(backing);
        if (outputEUt > 0.0) {
            details.eut = outputEUt;
            details.tier = GTVoltageTier.getTierForVoltage((long) outputEUt);
            details.energyType = EnergyType.ELECTRIC_EU;
            details.isGenerator = true;
        } else {
            // 2. Check consumer (Input EU)
            double inputEUt = extractInputEUt(backing);
            if (inputEUt > 0.0) {
                details.eut = inputEUt;
                details.tier = GTVoltageTier.getTierForVoltage((long) inputEUt);
                details.energyType = EnergyType.ELECTRIC_EU;
                details.isGenerator = false;
            }
        }

        int recipeTemp = extractRecipeTemperature(backing);
        if (recipeTemp > 0) {
            details.backingRecipeTemp = recipeTemp;
        }
    }

    public static double extractDuration(Object backing) {
        if (backing == null) return 0.0;

        for (String mName : new String[]{"getDuration", "duration", "getDurationTicks", "durationTicks", "getTime", "time", "getCookingTime", "cookingTime"}) {
            try {
                Method m = backing.getClass().getMethod(mName);
                Object res = m.invoke(backing);
                if (res instanceof Number num && num.doubleValue() > 0) {
                    return num.doubleValue();
                }
            } catch (Throwable ignored) {}
        }

        Class<?> cur = backing.getClass();
        while (cur != null && cur != Object.class) {
            for (String fName : new String[]{"duration", "durationTicks", "time", "cookingTime", "mDuration"}) {
                try {
                    Field f = cur.getDeclaredField(fName);
                    f.setAccessible(true);
                    Object res = f.get(backing);
                    if (res instanceof Number num && num.doubleValue() > 0) {
                        return num.doubleValue();
                    }
                } catch (Throwable ignored) {}
            }
            cur = cur.getSuperclass();
        }

        CompoundTag tag = extractRecipeDataTag(backing);
        if (tag != null) {
            if (tag.contains("duration")) return tag.getDouble("duration");
            if (tag.contains("duration_ticks")) return tag.getDouble("duration_ticks");
            if (tag.contains("time")) return tag.getDouble("time");
        }

        return 0.0;
    }

    public static double extractOutputEUt(Object backing) {
        if (backing == null) return 0.0;

        for (String mName : new String[]{"getOutputEUt", "outputEUt", "getEUtOutput"}) {
            try {
                Method m = backing.getClass().getMethod(mName);
                Object res = m.invoke(backing);
                double val = parseEnergyValue(res);
                if (val > 0.0) return val;
            } catch (Throwable ignored) {}
        }

        double tickOutVal = inspectEnergyMap(backing, "tickOutputs", "tick_outputs", "outputs");
        if (tickOutVal > 0.0) return tickOutVal;

        CompoundTag tag = extractRecipeDataTag(backing);
        if (tag != null) {
            if (tag.contains("output_eut")) return tag.getDouble("output_eut");
            if (tag.contains("eut_output")) return tag.getDouble("eut_output");
        }

        return 0.0;
    }

    public static double extractInputEUt(Object backing) {
        if (backing == null) return 0.0;

        for (String mName : new String[]{"getInputEUt", "inputEUt", "getEUtInput"}) {
            try {
                Method m = backing.getClass().getMethod(mName);
                Object res = m.invoke(backing);
                double val = parseEnergyValue(res);
                if (val > 0.0) return val;
            } catch (Throwable ignored) {}
        }

        double tickInVal = inspectEnergyMap(backing, "tickInputs", "tick_inputs", "inputs");
        if (tickInVal > 0.0) return tickInVal;

        CompoundTag tag = extractRecipeDataTag(backing);
        if (tag != null) {
            if (tag.contains("eut")) return tag.getDouble("eut");
            if (tag.contains("EUt")) return tag.getDouble("EUt");
            if (tag.contains("eu_per_tick")) return tag.getDouble("eu_per_tick");
            if (tag.contains("input_eut")) return tag.getDouble("input_eut");
            if (tag.contains("voltage")) return tag.getDouble("voltage");
        }

        return 0.0;
    }

    private static double parseEnergyValue(Object energyObj) {
        if (energyObj == null) return 0.0;

        if (energyObj instanceof Number num) {
            return Math.max(0.0, num.doubleValue());
        }

        try {
            Method voltageMethod = energyObj.getClass().getMethod("voltage");
            Method amperageMethod = energyObj.getClass().getMethod("amperage");
            long voltage = ((Number) voltageMethod.invoke(energyObj)).longValue();
            long amperage = ((Number) amperageMethod.invoke(energyObj)).longValue();
            if (voltage > 0) {
                return (double) voltage * Math.max(1L, amperage);
            }
        } catch (Throwable ignored) {}

        try {
            Method voltageMethod = energyObj.getClass().getMethod("getVoltage");
            Method amperageMethod = energyObj.getClass().getMethod("getAmperage");
            long voltage = ((Number) voltageMethod.invoke(energyObj)).longValue();
            long amperage = ((Number) amperageMethod.invoke(energyObj)).longValue();
            if (voltage > 0) {
                return (double) voltage * Math.max(1L, amperage);
            }
        } catch (Throwable ignored) {}

        for (String mName : new String[]{"getEUt", "eut", "getAmount", "amount", "getContent", "content", "getValue", "value"}) {
            try {
                Method m = energyObj.getClass().getMethod(mName);
                Object res = m.invoke(energyObj);
                if (res instanceof Number num && num.doubleValue() > 0) {
                    return num.doubleValue();
                }
            } catch (Throwable ignored) {}
        }

        return 0.0;
    }

    private static double inspectEnergyMap(Object backing, String... mapNames) {
        if (backing == null) return 0.0;

        for (String mapName : mapNames) {
            Object mapObj = null;
            try {
                Method m = backing.getClass().getMethod(mapName);
                mapObj = m.invoke(backing);
            } catch (Throwable ignored) {
                Class<?> cur = backing.getClass();
                while (cur != null && cur != Object.class) {
                    try {
                        Field f = cur.getDeclaredField(mapName);
                        f.setAccessible(true);
                        mapObj = f.get(backing);
                        if (mapObj != null) break;
                    } catch (Throwable ignored2) {}
                    cur = cur.getSuperclass();
                }
            }

            if (mapObj instanceof Map<?, ?> map) {
                double total = 0.0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    if (key == null) continue;
                    String keyStr = key.toString().toLowerCase(Locale.ROOT);
                    String keyCls = key.getClass().getName().toLowerCase(Locale.ROOT);
                    if (keyStr.contains("eu") || keyStr.contains("energy") || keyCls.contains("eu") || keyCls.contains("energy")) {
                        Object listObj = entry.getValue();
                        if (listObj instanceof List<?> list) {
                            for (Object contentObj : list) {
                                if (contentObj == null) continue;
                                double v = parseContentAmount(contentObj);
                                if (v > 0) total += v;
                            }
                        }
                    }
                }
                if (total > 0.0) return total;
            }
        }

        return 0.0;
    }

    private static double parseContentAmount(Object contentObj) {
        if (contentObj == null) return 0.0;
        if (contentObj instanceof Number num) return num.doubleValue();

        for (String mName : new String[]{"getContent", "content", "getAmount", "amount", "getValue", "value"}) {
            try {
                Method m = contentObj.getClass().getMethod(mName);
                Object res = m.invoke(contentObj);
                if (res instanceof Number num) {
                    return num.doubleValue();
                }
            } catch (Throwable ignored) {}
        }

        Class<?> cur = contentObj.getClass();
        while (cur != null && cur != Object.class) {
            for (String fName : new String[]{"content", "amount", "value"}) {
                try {
                    Field f = cur.getDeclaredField(fName);
                    f.setAccessible(true);
                    Object res = f.get(contentObj);
                    if (res instanceof Number num) {
                        return num.doubleValue();
                    }
                } catch (Throwable ignored) {}
            }
            cur = cur.getSuperclass();
        }

        return 0.0;
    }

    public static int extractRecipeTemperature(Object backing) {
        if (backing == null) return 0;

        CompoundTag tag = extractRecipeDataTag(backing);
        if (tag != null) {
            if (tag.contains("ebf_temp")) return tag.getInt("ebf_temp");
            if (tag.contains("temp")) return tag.getInt("temp");
            if (tag.contains("temperature")) return tag.getInt("temperature");
            if (tag.contains("blast_furnace_temp")) return tag.getInt("blast_furnace_temp");
        }

        for (String mName : new String[]{"getTemperature", "getTemp", "getEbfTemp", "temperature", "temp"}) {
            try {
                Method m = backing.getClass().getMethod(mName);
                Object res = m.invoke(backing);
                if (res instanceof Number num && num.intValue() > 0) {
                    return num.intValue();
                }
            } catch (Throwable ignored) {}
        }

        Class<?> cur = backing.getClass();
        while (cur != null && cur != Object.class) {
            for (String fName : new String[]{"temperature", "temp", "ebfTemp", "blastFurnaceTemp"}) {
                try {
                    Field f = cur.getDeclaredField(fName);
                    f.setAccessible(true);
                    Object res = f.get(backing);
                    if (res instanceof Number num && num.intValue() > 0) {
                        return num.intValue();
                    }
                } catch (Throwable ignored) {}
            }
            cur = cur.getSuperclass();
        }

        return 0;
    }

    public static CompoundTag extractRecipeDataTag(Object backing) {
        if (backing == null) return null;
        try {
            Method m = backing.getClass().getMethod("data");
            Object res = m.invoke(backing);
            if (res instanceof CompoundTag tag) return tag;
        } catch (Throwable ignored) {}
        try {
            Method m = backing.getClass().getMethod("getData");
            Object res = m.invoke(backing);
            if (res instanceof CompoundTag tag) return tag;
        } catch (Throwable ignored) {}

        Class<?> cur = backing.getClass();
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField("data");
                f.setAccessible(true);
                Object res = f.get(backing);
                if (res instanceof CompoundTag tag) return tag;
            } catch (Throwable ignored) {}
            cur = cur.getSuperclass();
        }

        return null;
    }

    public static Object unwrapRecipe(Object backing) {
        if (backing == null) return null;
        Object cur = backing;
        for (int i = 0; i < 4 && cur != null; i++) {
            if (cur.getClass().getName().contains("GTRecipe")) return cur;
            Object next = null;
            for (String mName : new String[]{"value", "getRecipe", "getGTRecipe", "recipe", "getBackingRecipe", "backingRecipe"}) {
                try {
                    Method m = cur.getClass().getMethod(mName);
                    next = m.invoke(cur);
                    if (next != null && next != cur) break;
                } catch (Throwable ignored) {}
            }
            if (next == null) {
                for (String fName : new String[]{"value", "recipe", "gtRecipe", "backingRecipe", "backing"}) {
                    try {
                        Field f = null;
                        try { f = cur.getClass().getField(fName); } catch (Throwable ignored) {
                            f = cur.getClass().getDeclaredField(fName);
                            f.setAccessible(true);
                        }
                        if (f != null) {
                            next = f.get(cur);
                            if (next != null && next != cur) break;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (next == null || next == cur) break;
            cur = next;
        }
        return cur;
    }

    public static List<IngredientStack> extractGTRecipeContents(Object gtRecipe, String fieldName) {
        List<IngredientStack> result = new ArrayList<>();
        if (gtRecipe == null) return result;

        Object unwrapped = unwrapRecipe(gtRecipe);
        if (unwrapped == null) unwrapped = gtRecipe;

        try {
            Object mapObj = null;
            // 1. Try getter methods on unwrapped recipe
            for (String mName : new String[]{fieldName, "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1), "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) + "Contents", fieldName + "Contents"}) {
                try {
                    Method m = unwrapped.getClass().getMethod(mName);
                    Object res = m.invoke(unwrapped);
                    if (res instanceof Map<?, ?>) {
                        mapObj = res;
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Try fields on unwrapped recipe
            if (mapObj == null) {
                Class<?> cur = unwrapped.getClass();
                while (cur != null && cur != Object.class) {
                    try {
                        Field f = null;
                        try { f = cur.getField(fieldName); } catch (Throwable ignored) {
                            f = cur.getDeclaredField(fieldName);
                            f.setAccessible(true);
                        }
                        if (f != null) {
                            Object res = f.get(unwrapped);
                            if (res instanceof Map<?, ?>) {
                                mapObj = res;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                    cur = cur.getSuperclass();
                }
            }

            // 3. Try data sub-object (e.g. unwrapped.data / unwrapped.getData())
            if (mapObj == null) {
                Object dataObj = null;
                for (String mName : new String[]{"data", "getData", "getRecipeData"}) {
                    try {
                        Method m = unwrapped.getClass().getMethod(mName);
                        Object res = m.invoke(unwrapped);
                        if (res != null && res != unwrapped) {
                            dataObj = res;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
                if (dataObj == null) {
                    for (String fName : new String[]{"data", "recipeData", "mRecipeData"}) {
                        try {
                            Field f = null;
                            try { f = unwrapped.getClass().getField(fName); } catch (Throwable ignored) {
                                f = unwrapped.getClass().getDeclaredField(fName);
                                f.setAccessible(true);
                            }
                            if (f != null) {
                                Object res = f.get(unwrapped);
                                if (res != null && res != unwrapped) {
                                    dataObj = res;
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (dataObj != null) {
                    for (String mName : new String[]{fieldName, "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)}) {
                        try {
                            Method m = dataObj.getClass().getMethod(mName);
                            Object res = m.invoke(dataObj);
                            if (res instanceof Map<?, ?>) {
                                mapObj = res;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (mapObj == null) {
                        try {
                            Field f = null;
                            try { f = dataObj.getClass().getField(fieldName); } catch (Throwable ignored) {
                                f = dataObj.getClass().getDeclaredField(fieldName);
                                f.setAccessible(true);
                            }
                            if (f != null) {
                                Object res = f.get(dataObj);
                                if (res instanceof Map<?, ?>) {
                                    mapObj = res;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (mapObj instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object cap = entry.getKey();
                    Object contentList = entry.getValue();
                    if (contentList instanceof List<?> list) {
                        boolean isFluid = cap != null && cap.toString().toLowerCase(Locale.ROOT).contains("fluid");
                        boolean isInput = "inputs".equalsIgnoreCase(fieldName);
                        for (Object contentObj : list) {
                            IngredientStack is = parseGTContent(contentObj, isFluid);
                            if (is != null && is.getId() != null) {
                                if (isInput && com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isIgnoredInput(is.getId(), is.getChance())) {
                                    continue;
                                }
                                if (!isInput && com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(is.getId())) {
                                    continue;
                                }
                                result.add(is);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static IngredientStack parseGTContent(Object contentObj, boolean isFluid) {
        if (contentObj == null) return null;
        try {
            Object inner = contentObj;
            for (String mName : new String[]{"content", "getContent", "getInner", "getStack", "getItems", "getItemStack", "getFluid", "getIngredient", "inner"}) {
                try {
                    Method m = contentObj.getClass().getMethod(mName);
                    Object res = m.invoke(contentObj);
                    if (res != null && res != contentObj) {
                        inner = res;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (inner == contentObj) {
                for (String fName : new String[]{"content", "inner", "stack", "itemStack", "ingredient", "fluid"}) {
                    try {
                        Field f = null;
                        try { f = contentObj.getClass().getField(fName); } catch (Throwable ignored) {
                            f = contentObj.getClass().getDeclaredField(fName);
                            f.setAccessible(true);
                        }
                        if (f != null) {
                            Object res = f.get(contentObj);
                            if (res != null && res != contentObj) {
                                inner = res;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }

            double chance = 1.0;
            try {
                Field chanceField = null;
                try { chanceField = contentObj.getClass().getField("chance"); } catch (Throwable ignored) {
                    chanceField = contentObj.getClass().getDeclaredField("chance");
                    chanceField.setAccessible(true);
                }
                if (chanceField != null) {
                    Object val = chanceField.get(contentObj);
                    if (val instanceof Number n) chance = n.doubleValue();
                }
            } catch (Throwable ignored) {
                for (String mName : new String[]{"chance", "getChance", "chancePercent"}) {
                    try {
                        Method m = contentObj.getClass().getMethod(mName);
                        Object val = m.invoke(contentObj);
                        if (val instanceof Number n) {
                            chance = n.doubleValue();
                            break;
                        }
                    } catch (Throwable ignored2) {}
                }
            }
            if (chance > 1.0) {
                chance = chance / 10000.0; // GTCEu uses 10000 = 100%
            }
            chance = Math.max(0.0, Math.min(1.0, chance));

            double tierChanceBoost = 0.0;
            try {
                Field boostField = null;
                try { boostField = contentObj.getClass().getField("tierChanceBoost"); } catch (Throwable ignored) {
                    boostField = contentObj.getClass().getDeclaredField("tierChanceBoost");
                    boostField.setAccessible(true);
                }
                if (boostField != null) {
                    Object val = boostField.get(contentObj);
                    if (val instanceof Number n) tierChanceBoost = n.doubleValue();
                }
            } catch (Throwable ignored) {
                for (String mName : new String[]{"tierChanceBoost", "getTierChanceBoost", "tierBoost"}) {
                    try {
                        Method m = contentObj.getClass().getMethod(mName);
                        Object val = m.invoke(contentObj);
                        if (val instanceof Number n) {
                            tierChanceBoost = n.doubleValue();
                            break;
                        }
                    } catch (Throwable ignored2) {}
                }
            }
            if (tierChanceBoost > 1.0) {
                tierChanceBoost = tierChanceBoost / 10000.0;
            }
            tierChanceBoost = Math.max(0.0, tierChanceBoost);

            IngredientStack is = null;
            if (inner instanceof ItemStack stack) {
                if (stack.isEmpty()) return null;
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                String name = stack.getHoverName().getString();
                is = IngredientStack.item(id, name, stack.getCount(), (float) chance);
            } else if (inner instanceof net.minecraftforge.fluids.FluidStack fStack) {
                if (fStack.isEmpty()) return null;
                ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fStack.getFluid());
                String name = fStack.getDisplayName().getString();
                is = IngredientStack.fluid(id, name, fStack.getAmount(), (float) chance);
            } else if (inner instanceof net.minecraft.world.item.crafting.Ingredient ing) {
                var items = ing.getItems();
                if (items != null && items.length > 0 && !items[0].isEmpty()) {
                    ItemStack first = items[0];
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(first.getItem());
                    String name = first.getHoverName().getString();
                    long amount = 1;
                    try {
                        Method getAmountMethod = inner.getClass().getMethod("getAmount");
                        amount = ((Number) getAmountMethod.invoke(inner)).longValue();
                    } catch (Throwable ignored) {}
                    is = IngredientStack.item(id, name, amount, (float) chance);
                }
            } else if (inner != null && inner.getClass().getName().contains("FluidIngredient")) {
                try {
                    Method getStacksMethod = inner.getClass().getMethod("getStacks");
                    Object res = getStacksMethod.invoke(inner);
                    if (res instanceof net.minecraftforge.fluids.FluidStack[] fArray && fArray.length > 0) {
                        net.minecraftforge.fluids.FluidStack first = fArray[0];
                        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(first.getFluid());
                        String name = first.getDisplayName().getString();
                        is = IngredientStack.fluid(id, name, first.getAmount(), (float) chance);
                    }
                } catch (Throwable ignored) {}
            }

            if (is != null && tierChanceBoost > 0.0) {
                is.setTierChanceBoost(tierChanceBoost);
            }
            return is;
        } catch (Throwable ignored) {}
        return null;
    }
}




