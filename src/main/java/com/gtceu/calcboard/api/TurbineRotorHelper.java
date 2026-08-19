package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically queries GTCEu Modern's TurbineRotorBehaviour and Material RotorProperty
 * to obtain exact turbine rotor efficiency, power, durability, and max EU/t without hardcoded tables.
 */
public class TurbineRotorHelper {

    private static final Map<String, RotorStats> STATS_CACHE = new ConcurrentHashMap<>();

    public record RotorStats(int efficiency, int power, double durability) {
        public static final RotorStats DEFAULT = new RotorStats(100, 100, 1600.0);
    }

    /**
     * Obtains exact rotor stats directly from GTCEu's TurbineRotorBehaviour or RotorProperty.
     *
     * @param rotorIdentifier Material name (e.g. "shellite", "damascus_steel"), resource location string, or rotor item name
     * @return Dynamically extracted RotorStats
     */
    public static RotorStats getRotorStats(String rotorIdentifier) {
        if (rotorIdentifier == null || rotorIdentifier.isEmpty()) {
            return RotorStats.DEFAULT;
        }

        String key = rotorIdentifier.toLowerCase().trim();
        if (key.startsWith("gtceu:rotor_")) {
            key = key.substring("gtceu:rotor_".length());
        }
        if (key.startsWith("gtceu:")) {
            key = key.substring("gtceu:".length());
        }

        return STATS_CACHE.computeIfAbsent(key, TurbineRotorHelper::computeRotorStats);
    }

    /**
     * Extracts exact rotor stats from an ItemStack using GTCEu's TurbineRotorBehaviour API.
     */
    public static RotorStats getRotorStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return RotorStats.DEFAULT;
        }

        try {
            Class<?> behaviourCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");
            Method getBehaviourMethod = behaviourCls.getMethod("getBehaviour", ItemStack.class);
            Object behaviour = getBehaviourMethod.invoke(null, stack);

            if (behaviour != null) {
                Method effM = behaviourCls.getMethod("getRotorEfficiency", ItemStack.class);
                Method pwrM = behaviourCls.getMethod("getRotorPower", ItemStack.class);
                Method durM = behaviourCls.getMethod("getPartMaxDurability", ItemStack.class);

                int eff = ((Number) effM.invoke(behaviour, stack)).intValue();
                int power = ((Number) pwrM.invoke(behaviour, stack)).intValue();
                double dur = ((Number) durM.invoke(behaviour, stack)).doubleValue();

                if (eff > 0 && power > 0) {
                    return new RotorStats(eff, power, dur > 0 ? dur : 1600.0);
                }
            }
        } catch (Throwable ignored) {}

        if (stack.hasTag() && stack.getTag().contains("GT.PartStats")) {
            String mat = stack.getTag().getCompound("GT.PartStats").getString("Material");
            if (!mat.isEmpty()) {
                return getRotorStats(mat);
            }
        }

        return RotorStats.DEFAULT;
    }

    private static RotorStats computeRotorStats(String matName) {
        int eff = 0;
        int power = 100;
        double durability = 0.0;

        try {
            Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            if (rotorItem == null) {
                rotorItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            }

            Class<?> behaviourCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");

            // 1. Create real GTCEu Turbine Rotor ItemStack with NBT PartStats
            if (rotorItem != null) {
                ItemStack stack = new ItemStack(rotorItem);
                CompoundTag tag = new CompoundTag();
                CompoundTag partStats = new CompoundTag();
                partStats.putString("Material", matName.contains(":") ? matName : "gtceu:" + matName);
                tag.put("GT.PartStats", partStats);
                stack.setTag(tag);

                try {
                    Method getBehaviourMethod = behaviourCls.getMethod("getBehaviour", ItemStack.class);
                    Object behaviour = getBehaviourMethod.invoke(null, stack);

                    if (behaviour != null) {
                        Method effM = behaviourCls.getMethod("getRotorEfficiency", ItemStack.class);
                        Method pwrM = behaviourCls.getMethod("getRotorPower", ItemStack.class);
                        Method durM = behaviourCls.getMethod("getPartMaxDurability", ItemStack.class);

                        Number eNum = (Number) effM.invoke(behaviour, stack);
                        if (eNum != null && eNum.intValue() > 0) eff = eNum.intValue();

                        Number pNum = (Number) pwrM.invoke(behaviour, stack);
                        if (pNum != null && pNum.intValue() > 0) power = pNum.intValue();

                        Number dNum = (Number) durM.invoke(behaviour, stack);
                        if (dNum != null && dNum.doubleValue() > 0) durability = dNum.doubleValue();
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Query GTRegistries.MATERIALS if stack reflection returned 0
            if (eff <= 0) {
                try {
                    Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                    Class<?> propertyKeyCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey");
                    Object rotorKey = propertyKeyCls.getField("ROTOR").get(null);
                    Object materialsRegistry = gtRegistriesCls.getField("MATERIALS").get(null);

                    if (materialsRegistry != null) {
                        for (Method m : materialsRegistry.getClass().getMethods()) {
                            if (m.getParameterCount() == 1 && (m.getParameterTypes()[0] == ResourceLocation.class || m.getParameterTypes()[0] == String.class)) {
                                try {
                                    Object param = m.getParameterTypes()[0] == String.class ? matName : ResourceLocation.tryParse(matName.contains(":") ? matName : "gtceu:" + matName);
                                    Object mat = m.invoke(materialsRegistry, param);
                                    if (mat != null) {
                                        Method getPropM = mat.getClass().getMethod("getProperty", propertyKeyCls);
                                        Object prop = getPropM.invoke(mat, rotorKey);
                                        if (prop != null) {
                                            Method getEffM = prop.getClass().getMethod("getEfficiency");
                                            Method getPwrM = prop.getClass().getMethod("getPower");
                                            Method getDurM = prop.getClass().getMethod("getDurability");

                                            double ev = ((Number) getEffM.invoke(prop)).doubleValue();
                                            double pv = ((Number) getPwrM.invoke(prop)).doubleValue();
                                            double dv = ((Number) getDurM.invoke(prop)).doubleValue();

                                            if (ev <= 10.0) {
                                                eff = (int) Math.round(ev * 100.0);
                                            } else if (ev <= 1000.0) {
                                                eff = (int) Math.round(ev);
                                            } else {
                                                eff = (int) Math.round(ev / 100.0);
                                            }

                                            if (pv <= 10.0) {
                                                power = (int) Math.round(pv * 100.0);
                                            } else {
                                                power = (int) Math.round(pv);
                                            }

                                            durability = dv;
                                            break;
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        if (eff <= 0) eff = 100;
        if (power <= 0) power = 100;
        if (durability <= 0) durability = 1600.0;

        return new RotorStats(eff, power, durability);
    }
}
