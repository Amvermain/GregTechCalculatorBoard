package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CoilHelper {

    private static final Map<String, CoilStats> STATS_CACHE = new ConcurrentHashMap<>();

    static {
        STATS_CACHE.put("gtceu:cupronickel_coil_block", new CoilStats(1800, 100, 100, 100, 100, 16));
        STATS_CACHE.put("gtceu:kanthal_coil_block", new CoilStats(2700, 100, 90, 125, 95, 32));
        STATS_CACHE.put("gtceu:nichrome_coil_block", new CoilStats(3600, 150, 80, 150, 90, 64));
        STATS_CACHE.put("gtceu:rtm_alloy_coil_block", new CoilStats(4500, 200, 70, 175, 85, 128));
        STATS_CACHE.put("gtceu:hssg_coil_block", new CoilStats(5400, 250, 60, 200, 80, 256));
        STATS_CACHE.put("gtceu:naquadah_coil_block", new CoilStats(7200, 300, 50, 225, 75, 2048));
        STATS_CACHE.put("gtceu:trinium_coil_block", new CoilStats(9001, 350, 40, 250, 70, 4096));
        STATS_CACHE.put("gtceu:tritanium_coil_block", new CoilStats(10800, 400, 30, 275, 65, 8192));
    }

    public record CoilStats(
            int temperature,
            int pyrolyseSpeedPercent,
            int crackingEnergyPercent,
            int chemicalSpeedPercent,
            int chemicalEnergyPercent,
            int smelterParallel
    ) {
        public static final CoilStats DEFAULT = new CoilStats(1800, 100, 100, 100, 100, 16);
    }

    public static boolean isHeatingCoil(ResourceLocation id) {
        if (id == null) return false;
        CoilStats stats = getCoilStats(id.toString());
        return stats != null && stats.temperature() > 0;
    }

    public static boolean isHeatingCoil(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CoilStats stats = getCoilStats(stack);
        return stats != null && stats.temperature() > 0;
    }

    public static boolean isHeatingCoil(Block block) {
        if (block == null) return false;
        CoilStats stats = getCoilStats(block);
        return stats != null && stats.temperature() > 0;
    }

    public static CoilStats getCoilStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            return getCoilStats(blockItem.getBlock());
        }

        return null;
    }

    public static CoilStats getCoilStats(Block block) {
        if (block == null) {
            return null;
        }

        CoilStats stats = extractStatsFromBlockObject(block);
        if (stats != null) {
            return stats;
        }

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null ? STATS_CACHE.get(id.toString()) : null;
    }

    public static CoilStats getCoilStats(String coilIdentifier) {
        if (coilIdentifier == null || coilIdentifier.isEmpty()) {
            return null;
        }

        String key = sanitizeCoilKey(coilIdentifier);
        if (STATS_CACHE.containsKey(key)) {
            return STATS_CACHE.get(key);
        }

        CoilStats computed = computeCoilStatsFromRegistry(key);
        if (computed != null) {
            STATS_CACHE.put(key, computed);
            return computed;
        }

        return null;
    }

    private static String sanitizeCoilKey(String identifier) {
        return identifier.toLowerCase().trim();
    }

    private static CoilStats computeCoilStatsFromRegistry(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key.contains(":") ? key : "gtceu:" + key);
        if (id == null) return null;

        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block != null && block != Blocks.AIR) {
            CoilStats stats = extractStatsFromBlockObject(block);
            if (stats != null) return stats;
        }

        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item instanceof BlockItem bi) {
            return extractStatsFromBlockObject(bi.getBlock());
        }

        return null;
    }

    public static CoilStats extractStatsFromBlockObject(Object blockObj) {
        if (blockObj == null) return null;

        Object coilTypeObj = resolveCoilTypeObject(blockObj);
        if (coilTypeObj == null) {
            return null;
        }

        int heat = extractInt(coilTypeObj, "getCoilTemperature", "getTemperature");
        if (heat <= 0) {
            return null;
        }

        int rawTier = extractInt(coilTypeObj, "getTier");
        int tier = (rawTier >= 0) ? rawTier : Math.max(0, (heat - 1800) / 900);

        int pyroSpeed = (tier == 0) ? 75 : (50 * (tier + 1));
        double crackDiscount = tier <= 9 ? (tier * 0.1) : (0.9 + (tier - 9) * 0.025);
        int crackEnergyPercent = Math.max(1, (int) Math.floor((1.0 - crackDiscount) * 100.0 + 0.0001));
        int chemSpeed = 75 + (tier * 25);
        int chemEnergy = Math.max(50, 100 - (tier * 5));

        int rawSmelterPar = extractInt(coilTypeObj, "getSmelterParallel", "getSmelterMaxParallel", "getMaxParallel", "getParallelMultiplier");
        int smelterPar = (rawSmelterPar > 0) ? rawSmelterPar : (int) (16 * Math.pow(2, Math.max(0, tier)));

        return new CoilStats(heat, pyroSpeed, crackEnergyPercent, chemSpeed, chemEnergy, smelterPar);
    }

    private static Object resolveCoilTypeObject(Object obj) {
        if (obj == null) return null;

        for (Class<?> iface : obj.getClass().getInterfaces()) {
            if (iface.getName().endsWith("ICoilType") || iface.getSimpleName().equals("ICoilType")) {
                return obj;
            }
        }

        Object methodResult = inspectCoilMethods(obj);
        if (methodResult != null) return methodResult;

        Object fieldResult = inspectCoilFields(obj);
        if (fieldResult != null) return fieldResult;

        if (obj.getClass().getSimpleName().equals("CoilBlock")) {
            return obj;
        }

        return null;
    }

    private static Object inspectCoilMethods(Object obj) {
        try {
            Method m = obj.getClass().getMethod("getCoilTemperature");
            if (m.getParameterCount() == 0) return obj;
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        for (String mName : new String[]{"getCoilType", "coilType"}) {
            try {
                Method m = obj.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object res = m.invoke(obj);
                    if (res != null) return res;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return null;
    }

    private static Object inspectCoilFields(Object obj) {
        Class<?> curr = obj.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Field f = curr.getDeclaredField("coilType");
                f.setAccessible(true);
                Object res = f.get(obj);
                if (res != null) return res;
            } catch (ReflectiveOperationException | LinkageError ignored) {}
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static int extractInt(Object target, String... methodNames) {
        if (target == null) return 0;
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object val = m.invoke(target);
                    if (val instanceof Number n) {
                        return n.intValue();
                    }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
            try {
                Field f = target.getClass().getField(mName);
                f.setAccessible(true);
                Object val = f.get(target);
                if (val instanceof Number n) {
                    return n.intValue();
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return 0;
    }

    public static GTCoilAddon parseCoilBlock(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) return null;

        CoilStats stats = getCoilStats(stack);
        if (stats == null || stats.temperature() <= 0) {
            return null;
        }

        String name = stack.getHoverName().getString();
        String desc = formatCoilDescription(stats);

        GTCoilAddon addon = new GTCoilAddon(id.toString(), name.isEmpty() ? id.toString() : name, desc, id, stats);
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu ICoilType Block API [" + id + "]");
        return addon;
    }

    private static String formatCoilDescription(CoilStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Heat: %d K\nSmelter: %dx Par\nPyrolyse: %d%% Spd\nCracking: %d%% Energy",
                stats.temperature(), stats.smelterParallel(), stats.pyrolyseSpeedPercent(), stats.crackingEnergyPercent()));
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isStarTLoaded() || com.gtceu.calcboard.compat.start.StarTReflectionBridge.isStarTLoaded()) {
            sb.append(String.format(Locale.ROOT, "\nChemical Reactor: %d%% Spd, %d%% Energy",
                    stats.chemicalSpeedPercent(), stats.chemicalEnergyPercent()));
        }
        return sb.toString();
    }

    public static void discoverGTCEuCoils(List<MachineAddon> list) {
        if (list == null || ForgeRegistries.BLOCKS == null) return;

        for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : ForgeRegistries.BLOCKS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (id == null) continue;
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (!path.contains("coil")) continue;

            Block block = entry.getValue();
            CoilStats stats = extractStatsFromBlockObject(block);
            if (stats != null && stats.temperature() > 0) {
                ItemStack stack = new ItemStack(block.asItem());
                String displayName = !stack.isEmpty() ? stack.getHoverName().getString() : block.getName().getString();
                String desc = formatCoilDescription(stats);

                GTCoilAddon addon = new GTCoilAddon(id.toString(), displayName, desc, id, stats);
                if (!stack.isEmpty()) {
                    addon.setItemStackSample(stack);
                }
                addon.setDiscoverySource("GTCEu ICoilType Block API [" + id + "]");

                if (list.stream().noneMatch(a -> a.getId().equals(addon.getId()))) {
                    list.add(addon);
                }
            }
        }
    }

    public static MachineAddon tailorCoilAddon(MachineAddon source, RecipeNode node) {
        if (source == null || source.getCategory() != MachineAddon.Category.COIL) {
            return source != null ? source.copy() : null;
        }
        MachineAddon cp = source.copy();
        if (node == null) return cp;

        GTCEuCoilModifierHelper.applyCoilModifiers(node, cp);
        return cp;
    }

    public static final List<String> STANDARD_COIL_IDS = List.of(
            "gtceu:cupronickel_coil_block",
            "gtceu:kanthal_coil_block",
            "gtceu:nichrome_coil_block",
            "gtceu:rtm_alloy_coil_block",
            "gtceu:hssg_coil_block",
            "gtceu:naquadah_coil_block",
            "gtceu:trinium_coil_block",
            "gtceu:tritanium_coil_block"
    );

    public static List<MachineAddon> getStandardCoils() {
        List<MachineAddon> list = new ArrayList<>();
        for (String idStr : STANDARD_COIL_IDS) {
            ResourceLocation id = ResourceLocation.tryParse(idStr);
            CoilStats stats = STATS_CACHE.getOrDefault(idStr, CoilStats.DEFAULT);
            String name = formatCoilName(idStr);
            String desc = formatCoilDescription(stats);
            GTCoilAddon addon = new GTCoilAddon(idStr, name, desc, id, stats);
            addon.setDiscoverySource("GTCEu Standard Coil Specification");
            list.add(addon);
        }
        return list;
    }

    private static String formatCoilName(String idStr) {
        if (idStr.contains("cupronickel")) return "Cupronickel";
        if (idStr.contains("kanthal")) return "Kanthal";
        if (idStr.contains("nichrome")) return "Nichrome";
        if (idStr.contains("rtm")) return "RTM Alloy";
        if (idStr.contains("hssg")) return "HSS-G";
        if (idStr.contains("naquadah")) return "Naquadah";
        if (idStr.contains("trinium")) return "Trinium";
        if (idStr.contains("tritanium")) return "Tritanium";
        return "Coil";
    }

    public static List<MachineAddon> getAllCoils() {
        Map<String, MachineAddon> map = new java.util.LinkedHashMap<>();
        List<MachineAddon> catalogCoils = com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().getAddonsByCategory(MachineAddon.Category.COIL);
        if (catalogCoils != null) {
            for (MachineAddon c : catalogCoils) {
                if (c != null && c.getId() != null) {
                    map.putIfAbsent(c.getId(), c);
                }
            }
        }

        List<MachineAddon> stdCoils = getStandardCoils();
        for (MachineAddon std : stdCoils) {
            if (std != null && std.getId() != null) {
                map.putIfAbsent(std.getId(), std);
            }
        }

        List<MachineAddon> list = new ArrayList<>(map.values());
        list.sort((a, b) -> {
            int tempA = (a instanceof GTCoilAddon ca) ? ca.getCoilTemperature() : getCoilStats(a.getId()).temperature();
            int tempB = (b instanceof GTCoilAddon cb) ? cb.getCoilTemperature() : getCoilStats(b.getId()).temperature();
            if (tempA <= 0) tempA = 1800;
            if (tempB <= 0) tempB = 1800;
            if (tempA != tempB) return Integer.compare(tempA, tempB);
            return a.getName().compareTo(b.getName());
        });

        return list;
    }

    public static String getCoilShortLabel(MachineAddon coil) {
        if (coil == null) return "Coil";
        int temp = (coil instanceof GTCoilAddon ca) ? ca.getCoilTemperature() : getCoilStats(coil.getId()).temperature();
        if (temp <= 0) temp = 1800;

        String formattedTemp = String.format(Locale.ROOT, "%.1fk", temp / 1000.0);

        String name = coil.getName();
        if (name == null || name.isBlank()) {
            name = coil.getId();
            if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        }
        name = name.replace(" Coil Block", "").replace(" Alloy", "").replace(" Coil", "").replace(" Block", "").trim();

        if (name.equalsIgnoreCase("Cupronickel")) name = "Cupro";
        else if (name.equalsIgnoreCase("Kanthal")) name = "Kanth";
        else if (name.equalsIgnoreCase("Nichrome")) name = "Nich";
        else if (name.equalsIgnoreCase("RTM")) name = "RTM";
        else if (name.equalsIgnoreCase("HSS-G")) name = "HSSG";
        else if (name.equalsIgnoreCase("Naquadah")) name = "Naq";
        else if (name.equalsIgnoreCase("Trinium")) name = "Trin";
        else if (name.equalsIgnoreCase("Tritanium")) name = "Trit";

        return name + " " + formattedTemp;
    }

    public static MachineAddon getInstalledCoil(RecipeNode node) {
        if (node == null) return null;
        for (MachineAddon a : node.getAddons()) {
            if (a != null && a.getCategory() == MachineAddon.Category.COIL) {
                return a;
            }
        }
        return null;
    }

    public static int getInstalledCoilTemperature(RecipeNode node) {
        if (node == null) return 0;
        MachineAddon installed = getInstalledCoil(node);
        if (installed instanceof GTCoilAddon c) {
            return c.getCoilTemperature();
        } else if (installed != null) {
            CoilStats stats = getCoilStats(installed.getId());
            if (stats != null && stats.temperature() > 0) return stats.temperature();
        }
        if (node.isMultiblock() && (MultiblockDetector.isCoilMultiblock(node.getMachineIcon()) || MultiblockDetector.isCoilRecipeCategory(node.getRecipeCategoryId()))) {
            return 1800;
        }
        return 0;
    }

    public static int getInstalledCoilSmelterParallel(RecipeNode node) {
        if (node == null) return 16;
        MachineAddon installed = getInstalledCoil(node);
        if (installed != null) {
            CoilStats stats = getCoilStats(installed.getId());
            if (stats != null && stats.smelterParallel() > 0) {
                return stats.smelterParallel();
            }
        }
        return 16;
    }

    public static MachineAddon getCoilForTemperature(int requiredTemp) {
        List<MachineAddon> coils = getAllCoils();
        for (MachineAddon c : coils) {
            int temp = (c instanceof GTCoilAddon gtCoil) ? gtCoil.getCoilTemperature() : getCoilStats(c.getId()).temperature();
            if (temp >= requiredTemp) {
                return c;
            }
        }
        return !coils.isEmpty() ? coils.get(coils.size() - 1) : null;
    }

    public static void installCoil(RecipeNode node, MachineAddon coilAddon) {
        if (node == null) return;
        node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
        if (coilAddon != null) {
            MachineAddon tailored = tailorCoilAddon(coilAddon, node);
            node.getAddons().add(tailored != null ? tailored : coilAddon.copy());
        }
    }

    public static void cycleCoil(RecipeNode node) {
        if (node == null) return;
        List<MachineAddon> coils = getAllCoils();
        if (coils.isEmpty()) return;

        int curTemp = getInstalledCoilTemperature(node);
        int nextIdx = 0;
        for (int i = 0; i < coils.size(); i++) {
            int temp = (coils.get(i) instanceof GTCoilAddon gtCoil) ? gtCoil.getCoilTemperature() : getCoilStats(coils.get(i).getId()).temperature();
            if (temp == curTemp) {
                nextIdx = (i + 1) % coils.size();
                break;
            }
        }
        installCoil(node, coils.get(nextIdx));
    }
}

