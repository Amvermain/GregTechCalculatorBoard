package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.RecipeDetails;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Extracts GTCEu recipe runtime details including duration, EU/t, voltage tier, and temperature.
 */
public final class GTCEuRecipeDetailExtractor {

    private GTCEuRecipeDetailExtractor() {}

    private static final String[] DURATION_METHODS = {
            "getDuration", "duration", "getDurationTicks", "durationTicks", "getTime", "time", "getCookingTime", "cookingTime"
    };
    private static final String[] DURATION_FIELDS = {
            "duration", "durationTicks", "time", "cookingTime", "mDuration"
    };
    private static final String[] OUTPUT_EUT_METHODS = {"getOutputEUt", "outputEUt", "getEUtOutput"};
    private static final String[] INPUT_EUT_METHODS = {"getInputEUt", "inputEUt", "getEUtInput"};
    private static final String[] TEMP_METHODS = {"getTemperature", "getTemp", "getEbfTemp", "temperature", "temp"};
    private static final String[] TEMP_FIELDS = {"temperature", "temp", "ebfTemp", "blastFurnaceTemp"};
    private static final String[] DATA_METHODS = {"data", "getData"};
    private static final String[] ENERGY_METHODS = {"getEUt", "eut", "getAmount", "amount", "getContent", "content", "getValue", "value"};
    private static final String[] AMOUNT_METHODS = {"getContent", "content", "getAmount", "amount", "getValue", "value"};
    private static final String[] AMOUNT_FIELDS = {"content", "amount", "value"};

    private static final ClassValue<Method> DURATION_METHOD_CACHE = createMethodCache(DURATION_METHODS);
    private static final ClassValue<Field> DURATION_FIELD_CACHE = createFieldCache(DURATION_FIELDS);
    private static final ClassValue<Method> OUTPUT_EUT_CACHE = createMethodCache(OUTPUT_EUT_METHODS);
    private static final ClassValue<Method> INPUT_EUT_CACHE = createMethodCache(INPUT_EUT_METHODS);
    private static final ClassValue<Method> TEMP_METHOD_CACHE = createMethodCache(TEMP_METHODS);
    private static final ClassValue<Field> TEMP_FIELD_CACHE = createFieldCache(TEMP_FIELDS);
    private static final ClassValue<Method> DATA_METHOD_CACHE = createMethodCache(DATA_METHODS);
    private static final ClassValue<Field> DATA_FIELD_CACHE = createFieldCache(new String[]{"data"});
    private static final ClassValue<Method> ENERGY_METHOD_CACHE = createMethodCache(ENERGY_METHODS);
    private static final ClassValue<Method> AMOUNT_METHOD_CACHE = createMethodCache(AMOUNT_METHODS);
    private static final ClassValue<Field> AMOUNT_FIELD_CACHE = createFieldCache(AMOUNT_FIELDS);

    private static ClassValue<Method> createMethodCache(String[] names) {
        return new ClassValue<>() {
            @Override
            protected Method computeValue(Class<?> type) {
                for (String name : names) {
                    try {
                        Method m = type.getMethod(name);
                        m.setAccessible(true);
                        return m;
                    } catch (ReflectiveOperationException ignored) {}
                }
                return null;
            }
        };
    }

    private static ClassValue<Field> createFieldCache(String[] names) {
        return new ClassValue<>() {
            @Override
            protected Field computeValue(Class<?> type) {
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
        };
    }

    public static void extractGTRecipeDetails(Object backing, RecipeDetails details) {
        if (backing == null || details == null) return;

        backing = GTCEuRecipeHandler.unwrapRecipe(backing);

        double duration = extractDuration(backing);
        if (duration > 0.0) {
            details.durationTicks = duration;
        }

        double outputEUt = extractOutputEUt(backing);
        if (outputEUt > 0.0) {
            details.eut = outputEUt;
            details.tier = GTVoltageTier.getTierForVoltage((long) outputEUt);
            details.energyType = EnergyType.ELECTRIC_EU;
            details.isGenerator = true;
        } else {
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

        Method m = DURATION_METHOD_CACHE.get(backing.getClass());
        if (m != null) {
            try {
                Object res = m.invoke(backing);
                if (res instanceof Number num && num.doubleValue() > 0) {
                    return num.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        Field f = DURATION_FIELD_CACHE.get(backing.getClass());
        if (f != null) {
            try {
                Object res = f.get(backing);
                if (res instanceof Number num && num.doubleValue() > 0) {
                    return num.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) {}
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

        Method m = OUTPUT_EUT_CACHE.get(backing.getClass());
        if (m != null) {
            try {
                Object res = m.invoke(backing);
                double val = parseEnergyValue(res);
                if (val > 0.0) return val;
            } catch (ReflectiveOperationException ignored) {}
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

        Method m = INPUT_EUT_CACHE.get(backing.getClass());
        if (m != null) {
            try {
                Object res = m.invoke(backing);
                double val = parseEnergyValue(res);
                if (val > 0.0) return val;
            } catch (ReflectiveOperationException ignored) {}
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

    public static double parseEnergyValue(Object energyObj) {
        if (energyObj == null) return 0.0;
        if (energyObj instanceof Number num) return Math.max(0.0, num.doubleValue());

        double voltageAmperage = tryParseVoltageAmperage(energyObj);
        if (voltageAmperage > 0.0) return voltageAmperage;

        Method m = ENERGY_METHOD_CACHE.get(energyObj.getClass());
        if (m != null) {
            try {
                Object res = m.invoke(energyObj);
                if (res instanceof Number num && num.doubleValue() > 0) {
                    return num.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        return 0.0;
    }

    private record VoltageAmperageMethods(Method voltageMethod, Method amperageMethod) {}

    private static final ClassValue<VoltageAmperageMethods> VOLTAGE_AMPERAGE_METHODS_CACHE = new ClassValue<>() {
        @Override
        protected VoltageAmperageMethods computeValue(Class<?> type) {
            try {
                Method v = type.getMethod("voltage");
                Method a = type.getMethod("amperage");
                v.setAccessible(true);
                a.setAccessible(true);
                return new VoltageAmperageMethods(v, a);
            } catch (ReflectiveOperationException ignored) {}

            try {
                Method v = type.getMethod("getVoltage");
                Method a = type.getMethod("getAmperage");
                v.setAccessible(true);
                a.setAccessible(true);
                return new VoltageAmperageMethods(v, a);
            } catch (ReflectiveOperationException ignored) {}

            return new VoltageAmperageMethods(null, null);
        }
    };

    private static double tryParseVoltageAmperage(Object energyObj) {
        if (energyObj == null) return 0.0;
        VoltageAmperageMethods vam = VOLTAGE_AMPERAGE_METHODS_CACHE.get(energyObj.getClass());
        if (vam.voltageMethod() != null && vam.amperageMethod() != null) {
            try {
                long voltage = ((Number) vam.voltageMethod().invoke(energyObj)).longValue();
                long amperage = ((Number) vam.amperageMethod().invoke(energyObj)).longValue();
                if (voltage > 0) return (double) voltage * Math.max(1L, amperage);
            } catch (ReflectiveOperationException ignored) {}
        }
        return 0.0;
    }

    public static double inspectEnergyMap(Object backing, String... mapNames) {
        if (backing == null) return 0.0;

        for (String mapName : mapNames) {
            Object mapObj = readFieldOrMethod(backing, mapName);
            if (mapObj instanceof Map<?, ?> map) {
                double total = aggregateEnergyMapEntries(map);
                if (total > 0.0) return total;
            }
        }
        return 0.0;
    }

    private static double aggregateEnergyMapEntries(Map<?, ?> map) {
        double total = 0.0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            total += processEnergyEntry(entry);
        }
        return total;
    }

    private static double processEnergyEntry(Map.Entry<?, ?> entry) {
        Object key = entry.getKey();
        if (key == null) return 0.0;
        String keyStr = key.toString().toLowerCase(Locale.ROOT);
        String keyCls = key.getClass().getName().toLowerCase(Locale.ROOT);
        if (!keyStr.contains("eu") && !keyStr.contains("energy") && !keyCls.contains("eu") && !keyCls.contains("energy")) {
            return 0.0;
        }
        if (entry.getValue() instanceof List<?> list) {
            return sumContentAmounts(list);
        }
        return 0.0;
    }

    private static double sumContentAmounts(List<?> list) {
        double total = 0.0;
        for (Object contentObj : list) {
            double v = parseContentAmount(contentObj);
            if (v > 0) total += v;
        }
        return total;
    }

    private static final ClassValue<Map<String, Function<Object, Object>>> PROPERTY_EXTRACTORS_CACHE = new ClassValue<>() {
        @Override
        protected Map<String, Function<Object, Object>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static Object readFieldOrMethod(Object target, String name) {
        if (target == null || name == null) return null;
        Function<Object, Object> fn = PROPERTY_EXTRACTORS_CACHE.get(target.getClass())
                .computeIfAbsent(name, n -> resolveMemberExtractor(target.getClass(), n));
        return fn.apply(target);
    }

    private static Function<Object, Object> resolveMemberExtractor(Class<?> type, String name) {
        try {
            Method m = type.getMethod(name);
            m.setAccessible(true);
            return obj -> {
                try {
                    return m.invoke(obj);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            };
        } catch (ReflectiveOperationException ignored) {}

        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return obj -> {
                    try {
                        return f.get(obj);
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                };
            } catch (ReflectiveOperationException ignored) {}
            cur = cur.getSuperclass();
        }
        return obj -> null;
    }

    public static double parseContentAmount(Object contentObj) {
        if (contentObj == null) return 0.0;
        if (contentObj instanceof Number num) return num.doubleValue();

        Method m = AMOUNT_METHOD_CACHE.get(contentObj.getClass());
        if (m != null) {
            try {
                Object res = m.invoke(contentObj);
                if (res instanceof Number num) return num.doubleValue();
            } catch (ReflectiveOperationException ignored) {}
        }

        Field f = AMOUNT_FIELD_CACHE.get(contentObj.getClass());
        if (f != null) {
            try {
                Object res = f.get(contentObj);
                if (res instanceof Number num) return num.doubleValue();
            } catch (ReflectiveOperationException ignored) {}
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

        Method m = TEMP_METHOD_CACHE.get(backing.getClass());
        if (m != null) {
            try {
                Object res = m.invoke(backing);
                if (res instanceof Number num && num.intValue() > 0) {
                    return num.intValue();
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        Field f = TEMP_FIELD_CACHE.get(backing.getClass());
        if (f != null) {
            try {
                Object res = f.get(backing);
                if (res instanceof Number num && num.intValue() > 0) {
                    return num.intValue();
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        return 0;
    }

    public static CompoundTag extractRecipeDataTag(Object backing) {
        if (backing == null) return null;

        Method m = DATA_METHOD_CACHE.get(backing.getClass());
        if (m != null) {
            try {
                Object res = m.invoke(backing);
                if (res instanceof CompoundTag tag) return tag;
            } catch (ReflectiveOperationException ignored) {}
        }

        Field f = DATA_FIELD_CACHE.get(backing.getClass());
        if (f != null) {
            try {
                Object res = f.get(backing);
                if (res instanceof CompoundTag tag) return tag;
            } catch (ReflectiveOperationException ignored) {}
        }

        return null;
    }
}
