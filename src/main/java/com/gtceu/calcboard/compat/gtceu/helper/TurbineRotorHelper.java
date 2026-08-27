package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for GTCEu Modern turbine rotors.
 * Dynamically queries TurbineRotorBehaviour and RotorProperty to extract efficiency, power, durability, and max EU/t.
 */
public class TurbineRotorHelper {

    private static final Map<String, RotorStats> STATS_CACHE = new ConcurrentHashMap<>();

    public record RotorStats(int efficiency, int power, double durability) {
        public static final RotorStats DEFAULT = new RotorStats(100, 100, 1600.0);
    }

    /**
     * Obtains exact rotor stats directly from GTCEu's TurbineRotorBehaviour or RotorProperty.
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

        RotorStats stats = STATS_CACHE.computeIfAbsent(key, TurbineRotorHelper::computeRotorStats);
        return stats != null ? stats : RotorStats.DEFAULT;
    }

    /**
     * Extracts exact rotor stats from an ItemStack using GTCEu's TurbineRotorBehaviour API.
     */
    public static RotorStats getRotorStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
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
                RotorStats stats = getRotorStats(mat);
                if (stats != null && stats.efficiency() > 0) {
                    return stats;
                }
            }
        }

        return null;
    }

    private static RotorStats computeRotorStats(String matName) {
        try {
            Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            if (rotorItem == null) {
                rotorItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            }

            Class<?> behaviourCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");

            if (rotorItem != null) {
                RotorStats stats = queryFromItemBehaviour(rotorItem, behaviourCls, matName);
                if (stats != null) {
                    return stats;
                }
            }

            RotorStats fallbackStats = queryFromMaterialRegistry(matName);
            if (fallbackStats != null) {
                return fallbackStats;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static RotorStats queryFromItemBehaviour(Item rotorItem, Class<?> behaviourCls, String matName) {
        try {
            ItemStack stack = new ItemStack(rotorItem);
            CompoundTag tag = new CompoundTag();
            CompoundTag partStats = new CompoundTag();
            partStats.putString("Material", matName.contains(":") ? matName : "gtceu:" + matName);
            tag.put("GT.PartStats", partStats);
            stack.setTag(tag);

            Method getBehaviourMethod = behaviourCls.getMethod("getBehaviour", ItemStack.class);
            Object behaviour = getBehaviourMethod.invoke(null, stack);

            if (behaviour != null) {
                Method effM = behaviourCls.getMethod("getRotorEfficiency", ItemStack.class);
                Method pwrM = behaviourCls.getMethod("getRotorPower", ItemStack.class);
                Method durM = behaviourCls.getMethod("getPartMaxDurability", ItemStack.class);

                Number eNum = (Number) effM.invoke(behaviour, stack);
                Number pNum = (Number) pwrM.invoke(behaviour, stack);
                Number dNum = (Number) durM.invoke(behaviour, stack);

                int eff = (eNum != null && eNum.intValue() > 0) ? eNum.intValue() : 0;
                int power = (pNum != null && pNum.intValue() > 0) ? pNum.intValue() : 100;
                double durability = (dNum != null && dNum.doubleValue() > 0) ? dNum.doubleValue() : 1600.0;

                if (eff > 0) {
                    return new RotorStats(eff, power, durability);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static RotorStats queryFromMaterialRegistry(String matName) {
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
                                    RotorStats st = extractStatsFromRotorProperty(prop);
                                    if (st != null) return st;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static RotorStats extractStatsFromRotorProperty(Object prop) {
        if (prop == null) return null;
        try {
            Method getEffM = prop.getClass().getMethod("getEfficiency");
            Method getPwrM = prop.getClass().getMethod("getPower");
            Method getDurM = prop.getClass().getMethod("getDurability");

            getEffM.setAccessible(true);
            getPwrM.setAccessible(true);
            getDurM.setAccessible(true);

            double ev = ((Number) getEffM.invoke(prop)).doubleValue();
            double pv = ((Number) getPwrM.invoke(prop)).doubleValue();
            double dv = ((Number) getDurM.invoke(prop)).doubleValue();

            int eff = (ev <= 10.0) ? (int) Math.round(ev * 100.0) : (ev <= 1000.0 ? (int) Math.round(ev) : (int) Math.round(ev / 100.0));
            int power = (pv <= 10.0) ? (int) Math.round(pv * 100.0) : (int) Math.round(pv);
            double durability = dv > 0 ? dv : 1600.0;

            return new RotorStats(eff > 0 ? eff : 100, power > 0 ? power : 100, durability);
        } catch (Throwable ignored) {}
        return null;
    }

    public static GTRotorAddon parseTurbineRotor(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String matId = null;
        if (stack.hasTag() && stack.getTag().contains("GT.PartStats")) {
            matId = stack.getTag().getCompound("GT.PartStats").getString("Material");
        }
        String cleanMatName = matId;
        if (cleanMatName != null && cleanMatName.contains(":")) {
            cleanMatName = cleanMatName.substring(cleanMatName.indexOf(":") + 1);
        }

        RotorStats stats = getRotorStats(stack);
        if (stats == null || stats.efficiency() <= 0) {
            return null;
        }

        String addonId = (cleanMatName != null && !cleanMatName.isEmpty()) ? "gtceu:rotor_" + cleanMatName : id.toString();

        int eff = stats.efficiency() > 0 ? stats.efficiency() : 100;
        int power = stats.power() > 0 ? stats.power() : 100;
        double dynamicMaxEUt = stats.durability() > 0 ? stats.durability() : 1600.0;

        String name = stack.getHoverName().getString();
        if ((name.isEmpty() || name.contains("%s") || name.contains("item.")) && cleanMatName != null) {
            name = formatMatName(cleanMatName) + " Turbine Rotor";
        }

        String desc = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
        GTRotorAddon addon = new GTRotorAddon(addonId, name.isEmpty() ? id.toString() : name, desc, id, eff, power, dynamicMaxEUt);
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu TurbineRotorBehaviour API [" + id + "]");
        return addon;
    }

    public static void discoverGTCEuRotors(java.util.List<MachineAddon> list) {
        try {
            Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            if (rotorItem == null) {
                rotorItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            }

            Class<?> propertyKeyCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey");
            Class<?> materialCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.Material");
            Class<?> behaviourCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");

            Object rotorKey = propertyKeyCls.getField("ROTOR").get(null);

            Method hasPropertyMethod = materialCls.getMethod("hasProperty", propertyKeyCls);
            Method getPropertyMethod = materialCls.getMethod("getProperty", propertyKeyCls);
            Method getBehaviourMethod = behaviourCls.getMethod("getBehaviour", ItemStack.class);
            Method setPartMaterialMethod = behaviourCls.getMethod("setPartMaterial", ItemStack.class, materialCls);
            Method getNameMethod = materialCls.getMethod("getName");

            Method effM = null;
            Method pwrM = null;
            Method durM = null;
            try { effM = behaviourCls.getMethod("getRotorEfficiency", ItemStack.class); } catch (Throwable ignored) {}
            try { pwrM = behaviourCls.getMethod("getRotorPower", ItemStack.class); } catch (Throwable ignored) {}
            try { durM = behaviourCls.getMethod("getPartMaxDurability", ItemStack.class); } catch (Throwable ignored) {}

            java.util.List<Object> allMaterials = new java.util.ArrayList<>();
            collectMaterialsFromGTRegistries(allMaterials, materialCls);

            String rotorPattern = net.minecraft.network.chat.Component.translatable("item.gtceu.turbine_rotor").getString();
            if (rotorPattern.isEmpty() || rotorPattern.contains("item.")) {
                rotorPattern = "%s Turbine Rotor";
            } else if (!rotorPattern.contains("%s")) {
                rotorPattern = "%s " + rotorPattern;
            }

            for (Object mat : allMaterials) {
                try {
                    Boolean hasRotor = false;
                    try {
                        hasRotor = (Boolean) hasPropertyMethod.invoke(mat, rotorKey);
                    } catch (Throwable ignored) {}

                    Object prop = null;
                    try {
                        prop = getPropertyMethod.invoke(mat, rotorKey);
                    } catch (Throwable ignored) {}

                    if ((hasRotor == null || !hasRotor) && prop == null) {
                        continue;
                    }

                    String matName = (String) getNameMethod.invoke(mat);
                    if (matName == null || matName.isEmpty()) continue;

                    String cleanMatName = matName;
                    if (cleanMatName.contains(":")) {
                        cleanMatName = cleanMatName.substring(cleanMatName.indexOf(":") + 1);
                    }
                    if (cleanMatName.equalsIgnoreCase("standard") || cleanMatName.equalsIgnoreCase("none") || cleanMatName.equalsIgnoreCase("dummy")) {
                        continue;
                    }

                    ItemStack stack = rotorItem != null ? new ItemStack(rotorItem) : ItemStack.EMPTY;
                    Object behaviour = null;
                    if (!stack.isEmpty()) {
                        try {
                            behaviour = getBehaviourMethod.invoke(null, stack);
                            if (behaviour != null) {
                                setPartMaterialMethod.invoke(behaviour, stack, mat);
                            }
                        } catch (Throwable ignored) {}

                        if (!stack.hasTag() || !stack.getTag().contains("GT.PartStats")) {
                            CompoundTag tag = stack.getOrCreateTag();
                            CompoundTag partStats = new CompoundTag();
                            partStats.putString("Material", matName.contains(":") ? matName : "gtceu:" + matName);
                            tag.put("GT.PartStats", partStats);
                            stack.setTag(tag);
                        }
                    }

                    int eff = 0;
                    int power = 100;
                    double durability = 0.0;

                    if (behaviour != null && !stack.isEmpty()) {
                        try {
                            if (effM != null) {
                                Number eNum = (Number) effM.invoke(behaviour, stack);
                                if (eNum != null && eNum.intValue() > 0) eff = eNum.intValue();
                            }
                            if (pwrM != null) {
                                Number pNum = (Number) pwrM.invoke(behaviour, stack);
                                if (pNum != null && pNum.intValue() > 0) power = pNum.intValue();
                            }
                            if (durM != null) {
                                Number dNum = (Number) durM.invoke(behaviour, stack);
                                if (dNum != null && dNum.doubleValue() > 0) durability = dNum.doubleValue();
                            }
                        } catch (Throwable ignored) {}
                    }

                    if (eff <= 0 && prop != null) {
                        try {
                            Method getEffMethod = prop.getClass().getMethod("getEfficiency");
                            Number effNum = (Number) getEffMethod.invoke(prop);
                            if (effNum != null) {
                                double ev = effNum.doubleValue();
                                if (ev <= 10.0) eff = (int) Math.round(ev * 100.0);
                                else if (ev <= 1000.0) eff = (int) Math.round(ev);
                                else eff = (int) Math.round(ev / 100.0);
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Method getPwrMethod = prop.getClass().getMethod("getPower");
                            Number pwrNum = (Number) getPwrMethod.invoke(prop);
                            if (pwrNum != null) {
                                double pv = pwrNum.doubleValue();
                                if (pv <= 10.0) power = (int) Math.round(pv * 100.0);
                                else if (pv <= 1000.0) power = (int) Math.round(pv);
                                else power = (int) Math.round(pv / 100.0);
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Method getDurMethod = prop.getClass().getMethod("getDurability");
                            Number durNum = (Number) getDurMethod.invoke(prop);
                            if (durNum != null && durNum.doubleValue() > 0) {
                                durability = durNum.doubleValue();
                            }
                        } catch (Throwable ignored) {}
                    }

                    boolean hasRotorProperty = (hasRotor != null && hasRotor) || prop != null;
                    boolean isRotorEligible = hasRotorProperty && (eff > 0);

                    if (isRotorEligible) {
                        if (eff <= 0) eff = 100;
                        if (power <= 0) power = 100;

                        String matDisplayName = formatMatName(cleanMatName);
                        try {
                            Method getLocalizedNameMethod = mat.getClass().getMethod("getLocalizedName");
                            Object comp = getLocalizedNameMethod.invoke(mat);
                            if (comp instanceof net.minecraft.network.chat.Component c) {
                                matDisplayName = c.getString();
                            }
                        } catch (Throwable ignored) {}

                        String displayName = String.format(rotorPattern, matDisplayName);
                        String desc = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
                        GTRotorAddon rAddon = new GTRotorAddon("gtceu:rotor_" + cleanMatName, displayName, desc, ResourceLocation.tryParse("gtceu:turbine_rotor"), eff, power, durability > 0 ? durability : 1600.0);
                        if (!stack.isEmpty()) {
                            rAddon.setItemStackSample(stack);
                        }
                        rAddon.setDiscoverySource("GTCEu Material Rotor Registry [" + cleanMatName + "]");

                        if (list.stream().noneMatch(a -> a.getId().equals(rAddon.getId()) || a.getName().equalsIgnoreCase(rAddon.getName()))) {
                            list.add(rAddon);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Creative Tab search
            try {
                for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB) {
                    try {
                        java.util.Collection<ItemStack> items = tab.getDisplayItems();
                        if (items != null) {
                            for (ItemStack s : items) {
                                if (s != null && !s.isEmpty()) {
                                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                                    if (id != null && id.getPath().equals("turbine_rotor")) {
                                        GTRotorAddon rotor = parseTurbineRotor(s, id);
                                        if (rotor != null && list.stream().noneMatch(a -> a.getId().equals(rotor.getId()))) {
                                            list.add(rotor);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void collectMaterialsFromGTRegistries(java.util.List<Object> allMaterials, Class<?> materialCls) {
        try {
            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            java.lang.reflect.Field materialsField = gtRegistriesCls.getField("MATERIALS");
            Object materialsRegistry = materialsField.get(null);

            if (materialsRegistry != null) {
                Iterable<?> iterable = MultiblockDetector.getRegistryIterable(materialsRegistry);
                if (iterable != null) {
                    for (Object mat : iterable) {
                        if (mat != null && materialCls.isInstance(mat) && !allMaterials.contains(mat)) {
                            allMaterials.add(mat);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String formatMatName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}

