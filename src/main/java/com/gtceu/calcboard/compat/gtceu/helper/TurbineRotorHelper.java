package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TurbineRotorHelper {

    private static final Map<String, RotorStats> STATS_CACHE = new ConcurrentHashMap<>();

    private static final Class<?> BEHAVIOUR_CLS;
    private static final Method GET_BEHAVIOUR_METHOD;
    private static final Method GET_EFFICIENCY_METHOD;
    private static final Method GET_POWER_METHOD;
    private static final Method GET_DURABILITY_METHOD;
    private static final Method SET_PART_MATERIAL_METHOD;

    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field MATERIALS_REGISTRY_FIELD;
    private static final Class<?> PROPERTY_KEY_CLS;
    private static final Field ROTOR_KEY_FIELD;
    private static final Class<?> MATERIAL_CLS;
    private static final Method HAS_PROPERTY_METHOD;
    private static final Method GET_PROPERTY_METHOD;
    private static final Method GET_NAME_METHOD;
    private static final Method GET_LOCALIZED_NAME_METHOD;

    static {
        ClassLoader cl = TurbineRotorHelper.class.getClassLoader();
        Class<?> behCls = null;
        Method getBehM = null;
        Method getEffM = null;
        Method getPowM = null;
        Method getDurM = null;
        Method setMatM = null;

        try {
            behCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour", false, cl);
            getBehM = behCls.getMethod("getBehaviour", ItemStack.class);
            getEffM = behCls.getMethod("getRotorEfficiency", ItemStack.class);
            getPowM = behCls.getMethod("getRotorPower", ItemStack.class);
            getDurM = behCls.getMethod("getPartMaxDurability", ItemStack.class);
            setMatM = behCls.getMethod("setPartMaterial", ItemStack.class, Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.Material", false, cl));
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        BEHAVIOUR_CLS = behCls;
        GET_BEHAVIOUR_METHOD = getBehM;
        GET_EFFICIENCY_METHOD = getEffM;
        GET_POWER_METHOD = getPowM;
        GET_DURABILITY_METHOD = getDurM;
        SET_PART_MATERIAL_METHOD = setMatM;

        Class<?> gtRegs = null;
        Field matField = null;
        Class<?> propKeyCls = null;
        Field rotorKeyF = null;
        Class<?> matCls = null;
        Method hasPropM = null;
        Method getPropM = null;
        Method getNameM = null;
        Method getLocNameM = null;

        try {
            gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false, cl);
            matField = gtRegs.getField("MATERIALS");
            propKeyCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey", false, cl);
            rotorKeyF = propKeyCls.getField("ROTOR");
            matCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.Material", false, cl);
            hasPropM = matCls.getMethod("hasProperty", propKeyCls);
            getPropM = matCls.getMethod("getProperty", propKeyCls);
            getNameM = matCls.getMethod("getName");
            getLocNameM = matCls.getMethod("getLocalizedName");
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        GT_REGISTRIES_CLS = gtRegs;
        MATERIALS_REGISTRY_FIELD = matField;
        PROPERTY_KEY_CLS = propKeyCls;
        ROTOR_KEY_FIELD = rotorKeyF;
        MATERIAL_CLS = matCls;
        HAS_PROPERTY_METHOD = hasPropM;
        GET_PROPERTY_METHOD = getPropM;
        GET_NAME_METHOD = getNameM;
        GET_LOCALIZED_NAME_METHOD = getLocNameM;
    }

    public record RotorStats(int efficiency, int power, double durability) {
        public static final RotorStats DEFAULT = new RotorStats(100, 100, 1600.0);
    }

    public static RotorStats getRotorStats(String rotorIdentifier) {
        if (rotorIdentifier == null || rotorIdentifier.isEmpty()) {
            return RotorStats.DEFAULT;
        }

        String key = sanitizeRotorKey(rotorIdentifier);
        RotorStats stats = STATS_CACHE.computeIfAbsent(key, TurbineRotorHelper::computeRotorStats);
        return stats != null ? stats : RotorStats.DEFAULT;
    }

    private static String sanitizeRotorKey(String identifier) {
        String key = identifier.toLowerCase().trim();
        if (key.startsWith("gtceu:rotor_")) {
            return key.substring("gtceu:rotor_".length());
        }
        if (key.startsWith("gtceu:")) {
            return key.substring("gtceu:".length());
        }
        return key;
    }

    public static RotorStats getRotorStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        RotorStats stats = extractStatsFromBehaviour(stack);
        if (stats != null) {
            return stats;
        }

        return extractStatsFromItemStackTag(stack);
    }

    private static RotorStats extractStatsFromBehaviour(ItemStack stack) {
        if (GET_BEHAVIOUR_METHOD == null || GET_EFFICIENCY_METHOD == null || GET_POWER_METHOD == null || GET_DURABILITY_METHOD == null) {
            return null;
        }
        try {
            Object behaviour = GET_BEHAVIOUR_METHOD.invoke(null, stack);
            if (behaviour == null) {
                return null;
            }

            int eff = ((Number) GET_EFFICIENCY_METHOD.invoke(behaviour, stack)).intValue();
            int power = ((Number) GET_POWER_METHOD.invoke(behaviour, stack)).intValue();
            double dur = ((Number) GET_DURABILITY_METHOD.invoke(behaviour, stack)).doubleValue();

            if (eff > 0 && power > 0) {
                return new RotorStats(eff, power, dur > 0 ? dur : 1600.0);
            }
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    private static RotorStats extractStatsFromItemStackTag(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("GT.PartStats")) return null;

        String mat = tag.getCompound("GT.PartStats").getString("Material");
        if (mat.isEmpty()) return null;

        RotorStats stats = getRotorStats(mat);
        return (stats != null && stats.efficiency() > 0) ? stats : null;
    }

    private static RotorStats computeRotorStats(String matName) {
        Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
        if (rotorItem != null) {
            RotorStats stats = queryFromItemBehaviour(rotorItem, matName);
            if (stats != null) {
                return stats;
            }
        }

        return queryFromMaterialRegistry(matName);
    }

    private static RotorStats queryFromItemBehaviour(Item rotorItem, String matName) {
        if (GET_BEHAVIOUR_METHOD == null || GET_EFFICIENCY_METHOD == null || GET_POWER_METHOD == null || GET_DURABILITY_METHOD == null) {
            return null;
        }
        try {
            ItemStack stack = new ItemStack(rotorItem);
            CompoundTag tag = new CompoundTag();
            CompoundTag partStats = new CompoundTag();
            partStats.putString("Material", matName.contains(":") ? matName : "gtceu:" + matName);
            tag.put("GT.PartStats", partStats);
            stack.setTag(tag);

            Object behaviour = GET_BEHAVIOUR_METHOD.invoke(null, stack);
            if (behaviour == null) return null;

            Number eNum = (Number) GET_EFFICIENCY_METHOD.invoke(behaviour, stack);
            Number pNum = (Number) GET_POWER_METHOD.invoke(behaviour, stack);
            Number dNum = (Number) GET_DURABILITY_METHOD.invoke(behaviour, stack);

            int eff = (eNum != null && eNum.intValue() > 0) ? eNum.intValue() : 0;
            int power = (pNum != null && pNum.intValue() > 0) ? pNum.intValue() : 100;
            double durability = (dNum != null && dNum.doubleValue() > 0) ? dNum.doubleValue() : 1600.0;

            if (eff > 0) {
                return new RotorStats(eff, power, durability);
            }
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    private static RotorStats queryFromMaterialRegistry(String matName) {
        if (MATERIALS_REGISTRY_FIELD == null || PROPERTY_KEY_CLS == null || ROTOR_KEY_FIELD == null) {
            return null;
        }
        try {
            Object rotorKey = ROTOR_KEY_FIELD.get(null);
            Object materialsRegistry = MATERIALS_REGISTRY_FIELD.get(null);
            if (materialsRegistry == null) return null;

            for (Method m : materialsRegistry.getClass().getMethods()) {
                if (m.getParameterCount() != 1) continue;
                Class<?> pType = m.getParameterTypes()[0];
                if (pType != ResourceLocation.class && pType != String.class) continue;

                Object param = pType == String.class ? matName : ResourceLocation.tryParse(matName.contains(":") ? matName : "gtceu:" + matName);
                m.setAccessible(true);
                Object mat = m.invoke(materialsRegistry, param);
                if (mat != null) {
                    Method getPropM = GET_PROPERTY_METHOD != null ? GET_PROPERTY_METHOD : mat.getClass().getMethod("getProperty", PROPERTY_KEY_CLS);
                    getPropM.setAccessible(true);
                    Object prop = getPropM.invoke(mat, rotorKey);
                    if (prop != null) {
                        RotorStats st = extractStatsFromRotorProperty(prop);
                        if (st != null) return st;
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
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
        } catch (ReflectiveOperationException | LinkageError ignored) {}
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

        String desc = Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
        GTRotorAddon addon = new GTRotorAddon(addonId, name.isEmpty() ? id.toString() : name, desc, id, eff, power, dynamicMaxEUt);
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu TurbineRotorBehaviour API [" + id + "]");
        return addon;
    }

    public static void discoverGTCEuRotors(List<MachineAddon> list) {
        if (list == null) return;
        discoverRotorsFromMaterials(list);
        discoverRotorsFromCreativeTabs(list);
    }

    private static void discoverRotorsFromMaterials(List<MachineAddon> list) {
        if (ROTOR_KEY_FIELD == null || HAS_PROPERTY_METHOD == null || GET_NAME_METHOD == null) {
            return;
        }

        try {
            Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            Object rotorKey = ROTOR_KEY_FIELD.get(null);
            List<Object> allMaterials = new ArrayList<>();
            collectMaterialsFromGTRegistries(allMaterials);

            String rotorPattern = Component.translatable("item.gtceu.turbine_rotor").getString();
            if (rotorPattern.isEmpty() || rotorPattern.contains("item.")) {
                rotorPattern = "%s Turbine Rotor";
            } else if (!rotorPattern.contains("%s")) {
                rotorPattern = "%s " + rotorPattern;
            }

            for (Object mat : allMaterials) {
                processMaterialForRotor(mat, rotorItem, rotorKey, rotorPattern, list);
            }
        } catch (ReflectiveOperationException ignored) {}
    }

    private static void processMaterialForRotor(Object mat, Item rotorItem, Object rotorKey, String rotorPattern, List<MachineAddon> list) {
        try {
            Boolean hasRotor = (Boolean) HAS_PROPERTY_METHOD.invoke(mat, rotorKey);
            Object prop = GET_PROPERTY_METHOD != null ? GET_PROPERTY_METHOD.invoke(mat, rotorKey) : null;

            if ((hasRotor == null || !hasRotor) && prop == null) {
                return;
            }

            String matName = (String) GET_NAME_METHOD.invoke(mat);
            if (matName == null || matName.isEmpty()) return;

            String cleanMatName = matName.contains(":") ? matName.substring(matName.indexOf(":") + 1) : matName;
            if (cleanMatName.equalsIgnoreCase("standard") || cleanMatName.equalsIgnoreCase("none") || cleanMatName.equalsIgnoreCase("dummy")) {
                return;
            }

            ItemStack stack = createRotorItemStack(rotorItem, mat, matName);
            RotorStats stats = resolveMaterialRotorStats(stack, prop);
            if (stats == null || stats.efficiency() <= 0) return;

            String displayName = resolveMaterialDisplayName(mat, cleanMatName, rotorPattern);
            String desc = Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(stats.efficiency())).getString();
            GTRotorAddon rAddon = new GTRotorAddon("gtceu:rotor_" + cleanMatName, displayName, desc, ResourceLocation.tryParse("gtceu:turbine_rotor"), stats.efficiency(), stats.power(), stats.durability());
            if (!stack.isEmpty()) {
                rAddon.setItemStackSample(stack);
            }
            rAddon.setDiscoverySource("GTCEu Material Rotor Registry [" + cleanMatName + "]");

            if (list.stream().noneMatch(a -> a.getId().equals(rAddon.getId()) || a.getName().equalsIgnoreCase(rAddon.getName()))) {
                list.add(rAddon);
            }
        } catch (ReflectiveOperationException ignored) {}
    }

    private static ItemStack createRotorItemStack(Item rotorItem, Object mat, String matName) {
        if (rotorItem == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(rotorItem);
        if (GET_BEHAVIOUR_METHOD != null && SET_PART_MATERIAL_METHOD != null) {
            try {
                Object behaviour = GET_BEHAVIOUR_METHOD.invoke(null, stack);
                if (behaviour != null) {
                    SET_PART_MATERIAL_METHOD.invoke(behaviour, stack, mat);
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        if (!stack.hasTag() || !stack.getTag().contains("GT.PartStats")) {
            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag partStats = new CompoundTag();
            partStats.putString("Material", matName.contains(":") ? matName : "gtceu:" + matName);
            tag.put("GT.PartStats", partStats);
            stack.setTag(tag);
        }
        return stack;
    }

    private static RotorStats resolveMaterialRotorStats(ItemStack stack, Object prop) {
        if (!stack.isEmpty()) {
            RotorStats stats = extractStatsFromBehaviour(stack);
            if (stats != null && stats.efficiency() > 0) return stats;
        }
        if (prop != null) {
            return extractStatsFromRotorProperty(prop);
        }
        return null;
    }

    private static String resolveMaterialDisplayName(Object mat, String cleanMatName, String rotorPattern) {
        String matDisplayName = formatMatName(cleanMatName);
        if (GET_LOCALIZED_NAME_METHOD != null) {
            try {
                Object comp = GET_LOCALIZED_NAME_METHOD.invoke(mat);
                if (comp instanceof Component c) {
                    matDisplayName = c.getString();
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return String.format(rotorPattern, matDisplayName);
    }

    private static void discoverRotorsFromCreativeTabs(List<MachineAddon> list) {
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            Collection<ItemStack> items = tab.getDisplayItems();
            if (items == null) continue;
            for (ItemStack s : items) {
                if (s == null || s.isEmpty()) continue;
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

    private static void collectMaterialsFromGTRegistries(List<Object> allMaterials) {
        if (MATERIALS_REGISTRY_FIELD == null || MATERIAL_CLS == null) return;
        try {
            Object materialsRegistry = MATERIALS_REGISTRY_FIELD.get(null);
            if (materialsRegistry != null) {
                Iterable<?> iterable = MultiblockDetector.getRegistryIterable(materialsRegistry);
                if (iterable != null) {
                    for (Object mat : iterable) {
                        if (mat != null && MATERIAL_CLS.isInstance(mat) && !allMaterials.contains(mat)) {
                            allMaterials.add(mat);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {}
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


