package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnergyHatchHelper {

    private static final Map<ResourceLocation, EnergyHatchStats> STATS_CACHE = new ConcurrentHashMap<>();
    private static final EnergyHatchStats NULL_STATS = new EnergyHatchStats(null, 0, false, false);
    private static final Pattern AMP_PATTERN = Pattern.compile("(\\d+)[aA]");

    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field MACHINES_FIELD;
    private static final Class<?> HOLDER_CLS;
    private static volatile Object DUMMY_HOLDER_PROXY = null;

    static {
        ClassLoader cl = EnergyHatchHelper.class.getClassLoader();
        Class<?> gtRegs = null;
        Field mField = null;
        try {
            gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false, cl);
            mField = gtRegs.getField("MACHINES");
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        Class<?> hCls = null;
        try {
            hCls = Class.forName("com.gregtechceu.gtceu.api.machine.IMachineBlockEntity", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        GT_REGISTRIES_CLS = gtRegs;
        MACHINES_FIELD = mField;
        HOLDER_CLS = hCls;
    }

    public record EnergyHatchStats(GTVoltageTier tier, int amperage, boolean isLaser, boolean isSubstation) {}

    public static void discoverGTCEuEnergyHatches(List<MachineAddon> collector) {
        if (collector == null) return;

        boolean registrySuccess = scanHatchesFromMachinesRegistry(collector);
        registrySuccess |= scanHatchesFromItemRegistry(collector);

        if (!registrySuccess || collector.stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH)) {
            registerDefaultHatches(collector);
        }
    }

    private static boolean scanHatchesFromMachinesRegistry(List<MachineAddon> collector) {
        if (MACHINES_FIELD == null) return false;
        boolean foundAny = false;
        try {
            Object machinesRegistry = MACHINES_FIELD.get(null);
            if (machinesRegistry instanceof Iterable<?> iterable) {
                for (Object machineDef : iterable) {
                    if (machineDef == null) continue;
                    if (processMachineDefHatch(machineDef, collector)) {
                        foundAny = true;
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return foundAny;
    }

    private static boolean processMachineDefHatch(Object machineDef, List<MachineAddon> collector) {
        try {
            Method mGetId = machineDef.getClass().getMethod("getId");
            mGetId.setAccessible(true);
            ResourceLocation id = (ResourceLocation) mGetId.invoke(machineDef);
            if (id == null) return false;

            EnergyHatchStats stats = extractStatsFromMachineDef(machineDef, id);
            if (stats == null) return false;

            ItemStack stack = getItemStackForDef(machineDef, id);
            String name = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : formatDisplayName(id);
            String desc = formatHatchDescription(stats);

            GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, stats.tier(), stats.amperage(), stats.isLaser(), stats.isSubstation(), false);
            if (stack != null) addon.setItemStackSample(stack);
            addon.setDiscoverySource("GTCEu Machine Registry [" + id + "]");
            if (!containsAddonId(collector, addon.getId())) {
                collector.add(addon);
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return false;
    }

    private static boolean scanHatchesFromItemRegistry(List<MachineAddon> collector) {
        if (ForgeRegistries.ITEMS == null) return false;
        boolean foundAny = false;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (id == null || !isLikelyEnergyHatchPath(id.getPath().toLowerCase(Locale.ROOT))) continue;

            EnergyHatchStats stats = getEnergyHatchStats(id);
            if (stats != null) {
                Item item = entry.getValue();
                ItemStack stack = new ItemStack(item);
                String name = stack.getHoverName().getString();
                String desc = formatHatchDescription(stats);

                GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, stats.tier(), stats.amperage(), stats.isLaser(), stats.isSubstation(), false);
                addon.setItemStackSample(stack);
                addon.setDiscoverySource("Item Registry [" + id + "]");
                if (!containsAddonId(collector, addon.getId())) {
                    collector.add(addon);
                    foundAny = true;
                }
            }
        }
        return foundAny;
    }

    private static boolean isLikelyEnergyHatchPath(String path) {
        if (path.contains("output") || path.contains("dynamo") || path.contains("source") || path.contains("emitter") || path.contains("cover")) {
            return false;
        }
        return path.contains("energy_hatch") || path.contains("energy_input_hatch") || path.contains("power_hatch")
                || path.contains("laser_target") || (path.contains("dream_link") && path.contains("hatch"));
    }

    private static String formatHatchDescription(EnergyHatchStats stats) {
        return stats.isLaser()
                ? String.format(Locale.ROOT, "Laser Target Input (%s, %,dA)", stats.tier().getName(), stats.amperage())
                : String.format(Locale.ROOT, "Energy Input Hatch (%s, %,dA)", stats.tier().getName(), stats.amperage());
    }

    private static void registerDefaultHatches(List<MachineAddon> collector) {
        GTVoltageTier[] tiers = GTVoltageTier.values();

        for (GTVoltageTier tier : tiers) {
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_energy_input_hatch");
            String name = tier.getName() + " Energy Input Hatch (2A)";
            String desc = String.format(Locale.ROOT, "Energy Input Hatch (%s, 2A)", tier.getName());

            GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, 2, false, false, false);
            if (!containsAddonId(collector, addon.getId())) collector.add(addon);
        }

        for (GTVoltageTier tier : tiers) {
            if (tier.ordinal() < GTVoltageTier.EV.ordinal()) continue;
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_energy_input_hatch_4a");
            String name = tier.getName() + " 4A Energy Input Hatch";
            String desc = String.format(Locale.ROOT, "Energy Input Hatch (%s, 4A)", tier.getName());

            GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, 4, false, false, false);
            if (!containsAddonId(collector, addon.getId())) collector.add(addon);
        }

        for (GTVoltageTier tier : tiers) {
            if (tier.ordinal() < GTVoltageTier.EV.ordinal()) continue;
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_energy_input_hatch_16a");
            String name = tier.getName() + " 16A Energy Input Hatch";
            String desc = String.format(Locale.ROOT, "Energy Input Hatch (%s, 16A)", tier.getName());

            GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, 16, false, false, false);
            if (!containsAddonId(collector, addon.getId())) collector.add(addon);
        }

        for (GTVoltageTier tier : tiers) {
            if (tier.ordinal() < GTVoltageTier.IV.ordinal()) continue;
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_substation_input_hatch_64a");
            String name = tier.getName() + " 64A Substation Input Hatch";
            String desc = String.format(Locale.ROOT, "Substation Input Hatch (%s, 64A)", tier.getName());

            GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, 64, false, true, false);
            if (!containsAddonId(collector, addon.getId())) collector.add(addon);
        }

        int[] laserAmps = new int[]{256, 1024, 4096};
        for (GTVoltageTier tier : tiers) {
            if (tier.ordinal() < GTVoltageTier.IV.ordinal()) continue;
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            for (int amp : laserAmps) {
                ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_laser_target_hatch_" + amp + "a");
                String name = String.format(Locale.ROOT, "%s %,dA Laser Target Hatch", tier.getName(), amp);
                String desc = String.format(Locale.ROOT, "Laser Target Input (%s, %,dA)", tier.getName(), amp);

                GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, amp, true, false, false);
                if (!containsAddonId(collector, addon.getId())) collector.add(addon);
            }
        }
    }

    public static EnergyHatchStats getEnergyHatchStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null ? getEnergyHatchStats(id) : null;
    }

    public static EnergyHatchStats getEnergyHatchStats(ResourceLocation id) {
        if (id == null) return null;
        EnergyHatchStats cached = STATS_CACHE.get(id);
        if (cached != null) {
            return cached == NULL_STATS ? null : cached;
        }

        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (isDisqualifiedHatchPath(path)) {
            STATS_CACHE.put(id, NULL_STATS);
            return null;
        }

        EnergyHatchStats registryStats = queryStatsFromGTRegistries(id);
        if (registryStats != null) {
            STATS_CACHE.put(id, registryStats);
            return registryStats;
        }

        GTVoltageTier tier = parseVoltageTier(path);
        int amperage = parseAmperageFromPath(path);
        boolean isLaser = path.contains("laser");
        boolean isSubstation = path.contains("substation");

        EnergyHatchStats stats = new EnergyHatchStats(tier, amperage, isLaser, isSubstation);
        STATS_CACHE.put(id, stats);
        return stats;
    }

    private static boolean isDisqualifiedHatchPath(String path) {
        if (path.contains("core") || path.contains("crystal") || path.contains("detector") ||
            path.contains("module") || path.contains("creative") || path.contains("dynamo") ||
            path.contains("output") || path.contains("source") || path.contains("emitter") ||
            path.contains("sensor") || path.contains("cover") || path.contains("cell") ||
            path.contains("battery") || path.contains("storage") || path.contains("wire") ||
            path.contains("cable") || path.contains("transformer")) {
            return true;
        }
        return !path.contains("energy_input_hatch") && !path.contains("laser_target_hatch") &&
                !path.contains("substation_input_hatch") && !path.contains("energy_hatch") &&
                !path.contains("power_hatch") && !(path.contains("dream_link") && path.contains("hatch"));
    }

    private static EnergyHatchStats queryStatsFromGTRegistries(ResourceLocation id) {
        if (MACHINES_FIELD == null) return null;
        try {
            Object machinesRegistry = MACHINES_FIELD.get(null);
            if (machinesRegistry != null) {
                Method mGet = machinesRegistry.getClass().getMethod("get", ResourceLocation.class);
                mGet.setAccessible(true);
                Object def = mGet.invoke(machinesRegistry, id);
                if (def != null) {
                    return extractStatsFromMachineDef(def, id);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    private static int parseAmperageFromPath(String path) {
        Matcher m = AMP_PATTERN.matcher(path);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return path.contains("laser") ? 256 : 2;
    }

    private static EnergyHatchStats extractStatsFromMachineDef(Object def, ResourceLocation id) {
        if (def == null) return null;
        String path = id != null ? id.getPath().toLowerCase(Locale.ROOT) : "";

        if (path.contains("dynamo") || path.contains("output") || path.contains("source")) {
            return null;
        }

        Object dummyHolder = getOrCreateDummyHolderProxy();
        Object machine = null;
        if (dummyHolder != null) {
            for (Method m : def.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && (m.getName().equals("createMetaMachine") || m.getName().equals("createMachine"))) {
                    try {
                        m.setAccessible(true);
                        machine = m.invoke(def, dummyHolder);
                        if (machine != null) break;
                    } catch (ReflectiveOperationException | LinkageError ignored) {}
                }
            }
        }

        String machineClsName = machine != null ? machine.getClass().getName() : def.getClass().getName();
        boolean isEnergyHatch = machineClsName.contains("EnergyHatchPartMachine") || machineClsName.contains("LaserTargetHatchPartMachine") || machineClsName.contains("SubstationLaserTarget");
        if (!isEnergyHatch && !path.contains("energy_input_hatch") && !path.contains("laser_target_hatch")) {
            return null;
        }

        if (machine != null) {
            boolean isExport = extractBoolean(machine, "isExport", "isOutput", "isDynamo");
            if (isExport) return null;
        }

        int tierInt = extractInt(def, "getTier");
        if (tierInt == 0 && machine != null) {
            tierInt = extractInt(machine, "getTier");
        }
        GTVoltageTier tier = (tierInt >= 0 && tierInt < GTVoltageTier.values().length)
                ? GTVoltageTier.values()[tierInt]
                : parseVoltageTier(path);

        int amp = 0;
        if (machine != null) {
            amp = extractInt(machine, "getAmperage", "getMaxAmperage", "getAmps", "getInAmperage");
        }
        if (amp <= 0) {
            amp = extractInt(def, "getAmperage", "getMaxAmperage", "getAmps");
        }
        if (amp <= 0) {
            amp = parseAmperageFromPath(path);
        }

        boolean isLaser = machineClsName.contains("Laser") || path.contains("laser");
        boolean isSubstation = machineClsName.contains("Substation") || path.contains("substation");

        return new EnergyHatchStats(tier, amp, isLaser, isSubstation);
    }

    public static MachineAddon parseEnergyHatch(ItemStack stack, ResourceLocation id) {
        if (id == null) return null;
        EnergyHatchStats stats = getEnergyHatchStats(id);
        if (stats == null) return null;

        String name = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : formatDisplayName(id);
        String desc = formatHatchDescription(stats);

        GTEnergyHatchAddon addon = new GTEnergyHatchAddon(id.toString(), name, desc, id, stats.tier(), stats.amperage(), stats.isLaser(), stats.isSubstation(), false);
        if (stack != null && !stack.isEmpty()) {
            addon.setItemStackSample(stack);
        }
        return addon;
    }

    private static ItemStack getItemStackForDef(Object def, ResourceLocation id) {
        try {
            Method mAsItem = def.getClass().getMethod("asItem");
            Object itemObj = mAsItem.invoke(def);
            if (itemObj instanceof Item item && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        } catch (ReflectiveOperationException ignored) {}

        if (ForgeRegistries.ITEMS != null) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }
        return null;
    }

    private static GTVoltageTier parseVoltageTier(String path) {
        GTVoltageTier[] tiers = GTVoltageTier.values().clone();
        Arrays.sort(tiers, (a, b) -> Integer.compare(b.name().length(), a.name().length()));
        for (GTVoltageTier tier : tiers) {
            String nameLower = tier.name().toLowerCase(Locale.ROOT);
            if (path.startsWith(nameLower + "_") || path.contains("_" + nameLower + "_") || path.endsWith("_" + nameLower) || path.contains(nameLower)) {
                return tier;
            }
        }
        return GTVoltageTier.LV;
    }

    private static String formatDisplayName(ResourceLocation id) {
        String path = id.getPath();
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static Object getOrCreateDummyHolderProxy() {
        if (DUMMY_HOLDER_PROXY != null) {
            return DUMMY_HOLDER_PROXY;
        }
        if (HOLDER_CLS == null) return null;

        synchronized (EnergyHatchHelper.class) {
            if (DUMMY_HOLDER_PROXY == null) {
                try {
                    DUMMY_HOLDER_PROXY = Proxy.newProxyInstance(
                            HOLDER_CLS.getClassLoader(),
                            new Class<?>[]{HOLDER_CLS},
                            (proxy, method, args) -> {
                                Class<?> ret = method.getReturnType();
                                if (ret == boolean.class) return false;
                                if (ret == int.class || ret == byte.class || ret == short.class) return 0;
                                if (ret == long.class) return 0L;
                                if (ret == float.class) return 0f;
                                if (ret == double.class) return 0d;
                                return null;
                            }
                    );
                } catch (IllegalArgumentException | SecurityException ignored) {}
            }
        }
        return DUMMY_HOLDER_PROXY;
    }

    private static int extractInt(Object target, String... methodNames) {
        if (target == null) return 0;
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object val = m.invoke(target);
                    if (val instanceof Number n && n.intValue() > 0) {
                        return n.intValue();
                    }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return 0;
    }

    private static boolean extractBoolean(Object target, String... methodNames) {
        if (target == null) return false;
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object val = m.invoke(target);
                    if (val instanceof Boolean b) {
                        return b;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return false;
    }

    private static boolean containsAddonId(List<MachineAddon> list, String id) {
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }
}

