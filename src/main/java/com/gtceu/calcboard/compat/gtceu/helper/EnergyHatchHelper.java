package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonLifecycleHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EnergyHatchHelper {

    private static final Map<ResourceLocation, EnergyHatchStats> STATS_CACHE = new ConcurrentHashMap<>();
    private static final EnergyHatchStats NULL_STATS = new EnergyHatchStats(null, 0, false, false, false);
    private static final Map<ResourceLocation, EnergyHatchStats> KNOWN_HATCH_SPECS;

    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field MACHINES_FIELD;
    private static final Class<?> HOLDER_CLS;
    private static final Class<?> ENERGY_HATCH_PART_CLS;
    private static final Class<?> LASER_HATCH_PART_CLS;
    private static final Method PART_ABILITY_IS_APPLICABLE;
    private static final Object ABILITY_INPUT_ENERGY;
    private static final Object ABILITY_SUBSTATION_INPUT_ENERGY;
    private static final Object ABILITY_INPUT_LASER;
    private static final Object ABILITY_OUTPUT_ENERGY;
    private static final Object ABILITY_SUBSTATION_OUTPUT_ENERGY;
    private static final Object ABILITY_OUTPUT_LASER;
    private static volatile Object DUMMY_HOLDER_PROXY = null;

    private static final ClassValue<Field> IO_FIELD_CACHE = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            Class<?> curr = type;
            while (curr != null && curr != Object.class) {
                Field f = findDeclaredFieldSafe(curr, "io");
                if (f != null) return f;
                curr = curr.getSuperclass();
            }
            return null;
        }
    };

    private static final ClassValue<Method> BLOCK_GETTER_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            Method m = findPublicNoArgMethodSafe(type, "getBlock");
            return m != null ? m : findPublicNoArgMethodSafe(type, "get");
        }
    };

    private static final ClassValue<Method> BLOCK_DEF_GETTER_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            Method m = findPublicNoArgMethodSafe(type, "getDefinition");
            return m != null ? m : findPublicNoArgMethodSafe(type, "getMachineDefinition");
        }
    };

    private static final ClassValue<Method> CREATE_MACHINE_METHOD_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            for (Method m : type.getMethods()) {
                if (m.getParameterCount() == 1 && (m.getName().equals("createMetaMachine") || m.getName().equals("createMachine"))) {
                    m.setAccessible(true);
                    return m;
                }
            }
            return null;
        }
    };

    private static Field findDeclaredFieldSafe(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findPublicNoArgMethodSafe(Class<?> cls, String name) {
        try {
            Method m = cls.getMethod(name);
            if (m.getParameterCount() == 0) {
                m.setAccessible(true);
                return m;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    static {
        KNOWN_HATCH_SPECS = buildKnownHatchSpecs();

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

        Class<?> ehCls = null;
        try {
            ehCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        Class<?> lhCls = null;
        try {
            lhCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        Method mApplicable = null;
        Object inEnergy = null;
        Object subInEnergy = null;
        Object inLaser = null;
        Object outEnergy = null;
        Object subOutEnergy = null;
        Object outLaser = null;
        try {
            Class<?> abilityCls = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.PartAbility", false, cl);
            mApplicable = abilityCls.getMethod("isApplicable", Block.class);
            inEnergy = getStaticFieldValue(abilityCls, "INPUT_ENERGY");
            subInEnergy = getStaticFieldValue(abilityCls, "SUBSTATION_INPUT_ENERGY");
            inLaser = getStaticFieldValue(abilityCls, "INPUT_LASER");
            outEnergy = getStaticFieldValue(abilityCls, "OUTPUT_ENERGY");
            subOutEnergy = getStaticFieldValue(abilityCls, "SUBSTATION_OUTPUT_ENERGY");
            outLaser = getStaticFieldValue(abilityCls, "OUTPUT_LASER");
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        GT_REGISTRIES_CLS = gtRegs;
        MACHINES_FIELD = mField;
        HOLDER_CLS = hCls;
        ENERGY_HATCH_PART_CLS = ehCls;
        LASER_HATCH_PART_CLS = lhCls;
        PART_ABILITY_IS_APPLICABLE = mApplicable;
        ABILITY_INPUT_ENERGY = inEnergy;
        ABILITY_SUBSTATION_INPUT_ENERGY = subInEnergy;
        ABILITY_INPUT_LASER = inLaser;
        ABILITY_OUTPUT_ENERGY = outEnergy;
        ABILITY_SUBSTATION_OUTPUT_ENERGY = subOutEnergy;
        ABILITY_OUTPUT_LASER = outLaser;
    }

    private static Object getStaticFieldValue(Class<?> cls, String fieldName) {
        try {
            Field f = cls.getField(fieldName);
            return f.get(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Map<ResourceLocation, EnergyHatchStats> buildKnownHatchSpecs() {
        Map<ResourceLocation, EnergyHatchStats> map = new HashMap<>();
        GTVoltageTier[] tiers = GTVoltageTier.values();
        int[] standardHighAmps = new int[]{4, 16};
        int[] laserAmps = new int[]{256, 1024, 4096};
        int[] dreamLinkAmps = new int[]{64, 256, 1024, 4096, 16384};

        for (GTVoltageTier tier : tiers) {
            String t = tier.name().toLowerCase(Locale.ROOT);
            putSpec(map, "gtceu:" + t + "_energy_input_hatch", new EnergyHatchStats(tier, 2, false, false, false));
            putSpec(map, "gtceu:" + t + "_energy_output_hatch", new EnergyHatchStats(tier, 2, false, false, true));

            for (int amp : standardHighAmps) {
                putSpec(map, "gtceu:" + t + "_energy_input_hatch_" + amp + "a", new EnergyHatchStats(tier, amp, false, false, false));
                putSpec(map, "gtceu:" + t + "_energy_output_hatch_" + amp + "a", new EnergyHatchStats(tier, amp, false, false, true));
            }

            putSpec(map, "gtceu:" + t + "_substation_input_hatch_64a", new EnergyHatchStats(tier, 64, false, true, false));
            putSpec(map, "gtceu:" + t + "_substation_output_hatch_64a", new EnergyHatchStats(tier, 64, false, true, true));

            putSpec(map, "gtceu:" + t + "_laser_target_hatch", new EnergyHatchStats(tier, 256, true, false, false));
            putSpec(map, "gtceu:" + t + "_laser_source_hatch", new EnergyHatchStats(tier, 256, true, false, true));
            for (int amp : laserAmps) {
                EnergyHatchStats laserIn = new EnergyHatchStats(tier, amp, true, false, false);
                EnergyHatchStats laserOut = new EnergyHatchStats(tier, amp, true, false, true);
                putSpec(map, "gtceu:" + t + "_laser_target_hatch_" + amp + "a", laserIn);
                putSpec(map, "gtceu:" + t + "_" + amp + "a_laser_target_hatch", laserIn);
                putSpec(map, "gtceu:" + t + "_laser_source_hatch_" + amp + "a", laserOut);
                putSpec(map, "gtceu:" + t + "_" + amp + "a_laser_source_hatch", laserOut);
            }

            for (int amp : dreamLinkAmps) {
                EnergyHatchStats dreamIn = new EnergyHatchStats(tier, amp, false, false, false);
                putSpec(map, "start_core:" + t + "_" + amp + "a_dream_link_energy_hatch", dreamIn);
                putSpec(map, "start_core:" + t + "_" + amp + "a_dream_link_hatch", dreamIn);
            }
        }
        return Map.copyOf(map);
    }

    private static void putSpec(Map<ResourceLocation, EnergyHatchStats> map, String idStr, EnergyHatchStats stats) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id != null) {
            map.put(id, stats);
        }
    }

    public record EnergyHatchStats(GTVoltageTier tier, int amperage, boolean isLaser, boolean isSubstation, boolean isDynamo) {
        public EnergyHatchStats(GTVoltageTier tier, int amperage, boolean isLaser, boolean isSubstation) {
            this(tier, amperage, isLaser, isSubstation, false);
        }
    }

    public static void discoverGTCEuEnergyHatches(List<MachineAddon> collector) {
        if (collector == null) return;

        boolean registrySuccess = scanHatchesFromMachinesRegistry(collector);
        registrySuccess |= scanHatchesFromItemRegistry(collector);

        boolean hasInput = collector.stream().anyMatch(a -> a instanceof GTEnergyHatchAddon eh && !eh.isDynamo());
        boolean hasDynamo = collector.stream().anyMatch(a -> a instanceof GTEnergyHatchAddon eh && eh.isDynamo());
        if (!registrySuccess || !hasInput || !hasDynamo) {
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

            GTEnergyHatchAddon addon = new GTEnergyHatchAddon(
                    id.toString(), name, desc, id,
                    stats.tier(), stats.amperage(), stats.isLaser(), stats.isSubstation(), false, stats.isDynamo()
            );
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
            if (id == null) continue;

            Item item = entry.getValue();
            EnergyHatchStats stats = resolveStatsForItem(item, id);
            if (stats == null) continue;

            ItemStack stack = new ItemStack(item);
            String name = stack.getHoverName().getString();
            String desc = formatHatchDescription(stats);

            GTEnergyHatchAddon addon = new GTEnergyHatchAddon(
                    id.toString(), name, desc, id,
                    stats.tier(), stats.amperage(), stats.isLaser(), stats.isSubstation(), false, stats.isDynamo()
            );
            addon.setItemStackSample(stack);
            addon.setDiscoverySource("Item Registry [" + id + "]");
            if (!containsAddonId(collector, addon.getId())) {
                collector.add(addon);
                foundAny = true;
            }
        }
        return foundAny;
    }

    private static EnergyHatchStats resolveStatsForItem(Item item, ResourceLocation id) {
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            Object def = extractMachineDefFromBlock(block);
            if (def != null) {
                EnergyHatchStats defStats = extractStatsFromMachineDef(def, id);
                if (defStats != null) {
                    STATS_CACHE.put(id, defStats);
                    return defStats;
                }
            }
        }
        return getEnergyHatchStats(id, true);
    }

    private static Object extractMachineDefFromBlock(Block block) {
        if (block == null) return null;
        Method m = BLOCK_DEF_GETTER_CACHE.get(block.getClass());
        if (m == null) return null;
        try {
            return m.invoke(block);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean isLikelyEnergyHatchPath(String path) {
        if (path == null || path.isEmpty()) return false;
        ResourceLocation gtId = ResourceLocation.tryParse(path.contains(":") ? path : "gtceu:" + path);
        if (gtId != null && getEnergyHatchStats(gtId, false) != null) {
            return true;
        }
        ResourceLocation startId = ResourceLocation.tryParse(path.contains(":") ? path : "start_core:" + path);
        return startId != null && getEnergyHatchStats(startId, false) != null;
    }

    private static String formatHatchDescription(EnergyHatchStats stats) {
        if (stats.isDynamo()) {
            return stats.isLaser()
                    ? String.format(Locale.ROOT, "Laser Source Output (%s, %,dA)", stats.tier().getName(), stats.amperage())
                    : String.format(Locale.ROOT, "Dynamo Output Hatch (%s, %,dA)", stats.tier().getName(), stats.amperage());
        }
        return stats.isLaser()
                ? String.format(Locale.ROOT, "Laser Target Input (%s, %,dA)", stats.tier().getName(), stats.amperage())
                : String.format(Locale.ROOT, "Energy Input Hatch (%s, %,dA)", stats.tier().getName(), stats.amperage());
    }

    public static ResourceLocation getDefaultHatchIdForTier(GTVoltageTier tier) {
        return getDefaultHatchIdForTier(tier, false);
    }

    public static ResourceLocation getDefaultHatchIdForTier(GTVoltageTier tier, boolean isDynamo) {
        if (tier == null) return null;
        String suffix = isDynamo ? "_energy_output_hatch" : "_energy_input_hatch";
        return ResourceLocation.tryParse("gtceu:" + tier.name().toLowerCase(Locale.ROOT) + suffix);
    }

    public static boolean installDefaultEnergyHatch(RecipeNode node, GTVoltageTier targetTier) {
        if (node == null || targetTier == null || !node.isMultiblock()) return false;
        if (!GTEnergyHatchCalculator.requiresEnergyHatch(node)) return false;

        node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH);

        boolean isDynamo = node.isGenerator();
        ResourceLocation hatchId = getDefaultHatchIdForTier(targetTier, isDynamo);
        if (hatchId == null) return false;

        EnergyHatchStats stats = getEnergyHatchStats(hatchId, true);
        int amp = stats != null ? stats.amperage() : 2;
        String name = isDynamo
                ? targetTier.getName() + " Dynamo Hatch (" + amp + "A)"
                : targetTier.getName() + " Energy Input Hatch (" + amp + "A)";
        String desc = formatHatchDescription(new EnergyHatchStats(targetTier, amp, false, false, isDynamo));

        GTEnergyHatchAddon addon = new GTEnergyHatchAddon(
                hatchId.toString(), name, desc, hatchId, targetTier, amp, false, false, false, isDynamo
        );
        GTAddonLifecycleHandler.onAddonInstalled(node, addon);
        node.setTargetTier(targetTier);
        node.markOverclockDirty();
        return true;
    }

    private static void registerDefaultHatches(List<MachineAddon> collector) {
        registerDefaultDirectionalHatches(collector, false);
        registerDefaultDirectionalHatches(collector, true);
    }

    private static void registerDefaultDirectionalHatches(List<MachineAddon> collector, boolean isDynamo) {
        GTVoltageTier[] tiers = GTVoltageTier.values();
        String dirToken = isDynamo ? "output" : "input";
        String labelBase = isDynamo ? "Dynamo Hatch" : "Energy Input Hatch";

        for (GTVoltageTier tier : tiers) {
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_energy_" + dirToken + "_hatch");
            String name = tier.getName() + " " + labelBase + " (2A)";
            String desc = formatHatchDescription(new EnergyHatchStats(tier, 2, false, false, isDynamo));
            addDefaultHatchIfAbsent(collector, new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, 2, false, false, false, isDynamo));
        }

        int[] standardAmps = new int[]{4, 16};
        for (int amp : standardAmps) {
            for (GTVoltageTier tier : tiers) {
                if (tier.ordinal() < GTVoltageTier.EV.ordinal()) continue;
                String tierLower = tier.name().toLowerCase(Locale.ROOT);
                ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_energy_" + dirToken + "_hatch_" + amp + "a");
                String name = tier.getName() + " " + amp + "A " + labelBase;
                String desc = formatHatchDescription(new EnergyHatchStats(tier, amp, false, false, isDynamo));
                addDefaultHatchIfAbsent(collector, new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, amp, false, false, false, isDynamo));
            }
        }

        String subLabel = isDynamo ? "64A Substation Dynamo Hatch" : "64A Substation Input Hatch";
        for (GTVoltageTier tier : tiers) {
            if (tier.ordinal() < GTVoltageTier.IV.ordinal()) continue;
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_substation_" + dirToken + "_hatch_64a");
            String name = tier.getName() + " " + subLabel;
            String desc = formatHatchDescription(new EnergyHatchStats(tier, 64, false, true, isDynamo));
            addDefaultHatchIfAbsent(collector, new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, 64, false, true, false, isDynamo));
        }

        registerDefaultLaserHatches(collector, tiers, isDynamo);
    }

    private static void registerDefaultLaserHatches(List<MachineAddon> collector, GTVoltageTier[] tiers, boolean isDynamo) {
        int[] laserAmps = new int[]{256, 1024, 4096};
        String kind = isDynamo ? "source" : "target";
        String label = isDynamo ? "Laser Source Hatch" : "Laser Target Hatch";
        for (GTVoltageTier tier : tiers) {
            if (tier.ordinal() < GTVoltageTier.IV.ordinal()) continue;
            String tierLower = tier.name().toLowerCase(Locale.ROOT);
            for (int amp : laserAmps) {
                ResourceLocation id = ResourceLocation.tryParse("gtceu:" + tierLower + "_laser_" + kind + "_hatch_" + amp + "a");
                String name = String.format(Locale.ROOT, "%s %,dA %s", tier.getName(), amp, label);
                String desc = formatHatchDescription(new EnergyHatchStats(tier, amp, true, false, isDynamo));
                addDefaultHatchIfAbsent(collector, new GTEnergyHatchAddon(id.toString(), name, desc, id, tier, amp, true, false, false, isDynamo));

                ResourceLocation altId = ResourceLocation.tryParse("gtceu:" + tierLower + "_" + amp + "a_laser_" + kind + "_hatch");
                addDefaultHatchIfAbsent(collector, new GTEnergyHatchAddon(altId.toString(), name, desc, altId, tier, amp, true, false, false, isDynamo));
            }
        }
    }

    private static void addDefaultHatchIfAbsent(List<MachineAddon> collector, GTEnergyHatchAddon addon) {
        if (!containsAddonId(collector, addon.getId())) {
            collector.add(addon);
        }
    }

    public static EnergyHatchStats getEnergyHatchStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        EnergyHatchStats stats = resolveStatsForItem(stack.getItem(), id);
        return (stats != null && !stats.isDynamo()) ? stats : null;
    }

    public static EnergyHatchStats getEnergyHatchStats(ResourceLocation id) {
        return getEnergyHatchStats(id, false);
    }

    public static EnergyHatchStats getEnergyHatchStats(ResourceLocation id, boolean allowDynamo) {
        if (id == null) return null;
        EnergyHatchStats cached = STATS_CACHE.get(id);
        if (cached != null) {
            if (cached == NULL_STATS) return null;
            return (!allowDynamo && cached.isDynamo()) ? null : cached;
        }

        EnergyHatchStats registryStats = queryStatsFromGTRegistries(id);
        if (registryStats != null) {
            STATS_CACHE.put(id, registryStats);
            return (!allowDynamo && registryStats.isDynamo()) ? null : registryStats;
        }

        EnergyHatchStats known = KNOWN_HATCH_SPECS.get(id);
        if (known != null) {
            STATS_CACHE.put(id, known);
            return (!allowDynamo && known.isDynamo()) ? null : known;
        }

        STATS_CACHE.put(id, NULL_STATS);
        return null;
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

    private static EnergyHatchStats extractStatsFromMachineDef(Object def, ResourceLocation id) {
        if (def == null) return null;

        Block block = extractBlockFromMachineDef(def);
        boolean inEnergy = isAbilityApplicable(ABILITY_INPUT_ENERGY, block);
        boolean inSub = isAbilityApplicable(ABILITY_SUBSTATION_INPUT_ENERGY, block);
        boolean inLaser = isAbilityApplicable(ABILITY_INPUT_LASER, block);
        boolean outEnergy = isAbilityApplicable(ABILITY_OUTPUT_ENERGY, block);
        boolean outSub = isAbilityApplicable(ABILITY_SUBSTATION_OUTPUT_ENERGY, block);
        boolean outLaser = isAbilityApplicable(ABILITY_OUTPUT_LASER, block);
        boolean matchedAbility = inEnergy || inSub || inLaser || outEnergy || outSub || outLaser;

        Object machine = instantiateDummyMachine(def, getOrCreateDummyHolderProxy());
        String simpleName = machine != null ? machine.getClass().getSimpleName() : def.getClass().getSimpleName();
        boolean isEnergyPartClass = isEnergyOrLaserPartMachine(machine, simpleName);

        EnergyHatchStats knownSpec = id != null ? KNOWN_HATCH_SPECS.get(id) : null;
        if (!matchedAbility && !isEnergyPartClass && knownSpec == null) {
            return null;
        }

        boolean isDynamo = resolveIsDynamo(matchedAbility, outEnergy, outSub, outLaser, machine, simpleName, knownSpec);
        boolean isLaser = inLaser || outLaser
                || (LASER_HATCH_PART_CLS != null && machine != null && LASER_HATCH_PART_CLS.isInstance(machine))
                || "LaserHatchPartMachine".equals(simpleName)
                || "LaserTargetHatchPartMachine".equals(simpleName)
                || "LaserSourceHatchPartMachine".equals(simpleName)
                || "SubstationLaserTargetPartMachine".equals(simpleName)
                || (knownSpec != null && knownSpec.isLaser());
        boolean isSubstation = inSub || outSub
                || "SubstationLaserTargetPartMachine".equals(simpleName)
                || "SubstationLaserTarget".equals(simpleName)
                || (knownSpec != null && knownSpec.isSubstation());

        GTVoltageTier tier = resolveTier(def, machine, knownSpec);
        int amp = resolveAmperage(def, machine, knownSpec, isLaser, isSubstation);

        return new EnergyHatchStats(tier, amp, isLaser, isSubstation, isDynamo);
    }

    private static boolean isEnergyOrLaserPartMachine(Object machine, String simpleName) {
        if (machine != null) {
            if (ENERGY_HATCH_PART_CLS != null && ENERGY_HATCH_PART_CLS.isInstance(machine)) return true;
            if (LASER_HATCH_PART_CLS != null && LASER_HATCH_PART_CLS.isInstance(machine)) return true;
        }
        return "EnergyHatchPartMachine".equals(simpleName)
                || "LaserHatchPartMachine".equals(simpleName)
                || "LaserTargetHatchPartMachine".equals(simpleName)
                || "LaserSourceHatchPartMachine".equals(simpleName)
                || "SubstationLaserTargetPartMachine".equals(simpleName)
                || "SubstationLaserTarget".equals(simpleName);
    }

    private static boolean resolveIsDynamo(
            boolean matchedAbility, boolean outEnergy, boolean outSub, boolean outLaser,
            Object machine, String simpleName, EnergyHatchStats knownSpec) {
        if (matchedAbility) {
            return outEnergy || outSub || outLaser;
        }
        if ("LaserSourceHatchPartMachine".equals(simpleName)) {
            return true;
        }
        Boolean ioOut = extractIoDirectionIsOut(machine);
        if (ioOut != null) {
            return ioOut;
        }
        if (machine != null && extractBoolean(machine, "isExport", "isOutput", "isDynamo")) {
            return true;
        }
        return knownSpec != null && knownSpec.isDynamo();
    }

    private static Boolean extractIoDirectionIsOut(Object machine) {
        if (machine == null) return null;
        try {
            Method mGetIo = machine.getClass().getMethod("getIo");
            Object ioObj = mGetIo.invoke(machine);
            if (ioObj instanceof Enum<?> e) {
                return "OUT".equals(e.name());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        Field fIo = IO_FIELD_CACHE.get(machine.getClass());
        if (fIo != null) {
            try {
                Object ioObj = fIo.get(machine);
                if (ioObj instanceof Enum<?> e) {
                    return "OUT".equals(e.name());
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return null;
    }

    private static GTVoltageTier resolveTier(Object def, Object machine, EnergyHatchStats knownSpec) {
        int tierInt = extractTierIndex(def);
        if (tierInt < 0 && machine != null) {
            tierInt = extractTierIndex(machine);
        }
        if (tierInt >= 0 && tierInt < GTVoltageTier.values().length) {
            return GTVoltageTier.values()[tierInt];
        }
        return knownSpec != null ? knownSpec.tier() : GTVoltageTier.LV;
    }

    private static int extractTierIndex(Object target) {
        if (target == null) return -1;
        try {
            Method m = target.getClass().getMethod("getTier");
            if (m.getParameterCount() == 0) {
                m.setAccessible(true);
                Object val = m.invoke(target);
                if (val instanceof Number n) {
                    return n.intValue();
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return -1;
    }

    private static int resolveAmperage(Object def, Object machine, EnergyHatchStats knownSpec, boolean isLaser, boolean isSubstation) {
        int amp = 0;
        if (machine != null) {
            amp = extractInt(machine, "getAmperage", "getMaxAmperage", "getAmps", "getInAmperage", "getOutAmperage");
            if (amp <= 0) {
                amp = extractAmperageFromFields(machine);
            }
        }
        if (amp <= 0) {
            amp = extractInt(def, "getAmperage", "getMaxAmperage", "getAmps");
        }
        if (amp <= 0 && knownSpec != null) {
            amp = knownSpec.amperage();
        }
        if (amp <= 0) {
            amp = isLaser ? 256 : (isSubstation ? 64 : 2);
        }
        return amp;
    }

    private static int extractAmperageFromFields(Object machine) {
        Class<?> curr = machine.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                int val = tryReadAmperageField(machine, f);
                if (val > 0) return val;
            }
            curr = curr.getSuperclass();
        }
        return 0;
    }

    private static int tryReadAmperageField(Object machine, Field f) {
        try {
            f.setAccessible(true);
            String fName = f.getName();
            if ("amperage".equals(fName) || "maxAmperage".equals(fName)) {
                Object val = f.get(machine);
                if (val instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            }
            if ("energyContainer".equals(fName) || "buffer".equals(fName)) {
                Object container = f.get(machine);
                if (container != null) {
                    int inAmp = extractInt(container, "getInputAmperage", "getOutputAmperage", "getAmperage");
                    if (inAmp > 0) return inAmp;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return 0;
    }

    private static Block extractBlockFromMachineDef(Object def) {
        if (def == null) return null;
        Method m = BLOCK_GETTER_CACHE.get(def.getClass());
        if (m == null) return null;
        try {
            Object res = m.invoke(def);
            if (res instanceof Block b) return b;
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    private static boolean isAbilityApplicable(Object abilityObj, Block block) {
        if (abilityObj == null || block == null || PART_ABILITY_IS_APPLICABLE == null) return false;
        try {
            Object res = PART_ABILITY_IS_APPLICABLE.invoke(abilityObj, block);
            return Boolean.TRUE.equals(res);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Object instantiateDummyMachine(Object def, Object dummyHolder) {
        if (def == null || dummyHolder == null) return null;
        Method m = CREATE_MACHINE_METHOD_CACHE.get(def.getClass());
        if (m == null) return null;
        try {
            return m.invoke(def, dummyHolder);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static MachineAddon parseEnergyHatch(ItemStack stack, ResourceLocation id) {
        if (id == null) return null;
        EnergyHatchStats stats = stack != null && !stack.isEmpty()
                ? resolveStatsForItem(stack.getItem(), id)
                : getEnergyHatchStats(id, true);
        if (stats == null) return null;

        String name = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : formatDisplayName(id);
        String desc = formatHatchDescription(stats);

        GTEnergyHatchAddon addon = new GTEnergyHatchAddon(
                id.toString(), name, desc, id,
                stats.tier(), stats.amperage(), stats.isLaser(), stats.isSubstation(), false, stats.isDynamo()
        );
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
