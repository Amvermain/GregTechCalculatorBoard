package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class ReflectorHelper {

    public record ReflectorStats(int tier) {
        public static final ReflectorStats DEFAULT = new ReflectorStats(1);
    }

    public static final TagKey<Item> TAG_GT_REFLECTORS = TagKey.create(Registries.ITEM, ResourceLocation.tryParse("gtceu:fusion_reflectors"));
    public static final TagKey<Item> TAG_FORGE_REFLECTORS = TagKey.create(Registries.ITEM, ResourceLocation.tryParse("forge:fusion_reflectors"));
    public static final TagKey<Item> TAG_START_REFLECTORS = TagKey.create(Registries.ITEM, ResourceLocation.tryParse("start_core:fusion_reflectors"));

    public static ReflectorStats getReflectorStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        ReflectorStats nbtStats = extractStatsFromNbt(stack);
        if (nbtStats != null) return nbtStats;

        Item item = stack.getItem();
        ReflectorStats stats = inspectReflectorObject(item, 0);
        if (stats != null) return stats;

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block != null) {
                stats = inspectReflectorObject(block, 0);
                if (stats != null) return stats;
            }
        }

        return null;
    }

    private static ReflectorStats extractStatsFromNbt(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;

        if (tag.contains("ReflectorTier")) return new ReflectorStats(tag.getInt("ReflectorTier"));
        if (tag.contains("reflector_tier")) return new ReflectorStats(tag.getInt("reflector_tier"));
        if (tag.contains("Tier")) return new ReflectorStats(tag.getInt("Tier"));
        return null;
    }

    private static ReflectorStats inspectReflectorObject(Object obj, int depth) {
        if (obj == null || depth > 2) return null;
        Class<?> clazz = obj.getClass();

        ReflectorStats mStats = inspectMethodsForTier(clazz, obj);
        if (mStats != null) return mStats;

        ReflectorStats fStats = inspectFieldsForTier(clazz, obj, depth);
        if (fStats != null) return fStats;

        if (depth < 2) {
            return inspectNestedReflectorMethods(clazz, obj, depth);
        }

        return null;
    }

    private static ReflectorStats inspectMethodsForTier(Class<?> clazz, Object obj) {
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (name.equals("getreflectortier") || name.equals("gettier") || name.equals("getfusionreflectortier") || name.equals("reflectortier")) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(obj);
                        if (res instanceof Number num && num.intValue() > 0) {
                            return new ReflectorStats(num.intValue());
                        }
                    } catch (ReflectiveOperationException | LinkageError ignored) {}
                }
            }
        }
        return null;
    }

    private static ReflectorStats inspectFieldsForTier(Class<?> clazz, Object obj, int depth) {
        for (Field f : clazz.getFields()) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.equals("reflectortier") || name.equals("tier") || name.equals("fusionreflectortier")) {
                try {
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res instanceof Number num && num.intValue() > 0) {
                        return new ReflectorStats(num.intValue());
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
            if (depth < 2 && (name.contains("reflector") || name.contains("type"))) {
                try {
                    f.setAccessible(true);
                    Object fieldObj = f.get(obj);
                    if (fieldObj != null && fieldObj != obj) {
                        ReflectorStats nested = inspectReflectorObject(fieldObj, depth + 1);
                        if (nested != null) return nested;
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
        }
        return null;
    }

    private static ReflectorStats inspectNestedReflectorMethods(Class<?> clazz, Object obj, int depth) {
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (name.equals("getreflectortype") || name.equals("getreflector")) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(obj);
                        if (res != null && res != obj) {
                            ReflectorStats nested = inspectReflectorObject(res, depth + 1);
                            if (nested != null) return nested;
                        }
                    } catch (ReflectiveOperationException | LinkageError ignored) {}
                }
            }
        }
        return null;
    }

    public static GTReflectorAddon parseReflectorItem(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) return null;

        ReflectorStats stats = getReflectorStats(stack);
        if (stats == null || stats.tier() <= 0) return null;

        String name = stack.getHoverName().getString();
        String desc = Component.translatable("gui.gtcalcboard.addon.reflector_desc", stats.tier()).getString();

        GTReflectorAddon addon = new GTReflectorAddon(id.toString(), name.isEmpty() ? id.toString() : name, desc, id, stats.tier());
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("GTCEu / Star Technology Reflector Item [" + id + "]");
        return addon;
    }

    public static void discoverGTCEuReflectors(List<MachineAddon> collector) {
        if (collector == null) return;
        Set<Integer> discoveredTiers = new HashSet<>();

        if (ForgeRegistries.ITEMS != null) {
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null) continue;

                ItemStack stack = new ItemStack(item);
                ReflectorStats stats = getReflectorStats(stack);
                if (stats != null && stats.tier() > 0) {
                    GTReflectorAddon addon = parseReflectorItem(stack, id);
                    if (addon != null && collector.stream().noneMatch(a -> a.getId().equals(addon.getId()))) {
                        collector.add(addon);
                        discoveredTiers.add(stats.tier());
                    }
                }
            }
        }

        for (int t = 1; t <= 5; t++) {
            if (!discoveredTiers.contains(t)) {
                ResourceLocation id = ResourceLocation.tryParse("gtceu:fusion_reflector_t" + t);
                String name = Component.translatable("gui.gtcalcboard.addon.reflector_tier_name", t).getString();
                String desc = Component.translatable("gui.gtcalcboard.addon.reflector_desc", t).getString();
                GTReflectorAddon addon = new GTReflectorAddon("gtceu:reflector_tier_" + t, name, desc, id, t);
                addon.setDiscoverySource("GTCEu Built-in Reflector Specification");
                if (collector.stream().noneMatch(a -> a.getId().equals(addon.getId()))) {
                    collector.add(addon);
                }
            }
        }
    }

    public static List<MachineAddon> getAllReflectors() {
        List<MachineAddon> list = new ArrayList<>();
        List<MachineAddon> catalogReflectors = com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().getAddonsByCategory(MachineAddon.Category.REFLECTOR);
        if (catalogReflectors != null && !catalogReflectors.isEmpty()) {
            list.addAll(catalogReflectors);
        }

        if (list.isEmpty()) {
            list.addAll(getStandardReflectors());
        }

        deduplicateFallbackReflectors(list);

        list.sort((a, b) -> {
            int tierA = (a instanceof GTReflectorAddon ra) ? ra.getReflectorTier() : 0;
            int tierB = (b instanceof GTReflectorAddon rb) ? rb.getReflectorTier() : 0;
            if (tierA != tierB) return Integer.compare(tierA, tierB);
            boolean aSynth = a.getId().startsWith("gtceu:reflector_tier_");
            boolean bSynth = b.getId().startsWith("gtceu:reflector_tier_");
            if (aSynth != bSynth) return aSynth ? 1 : -1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return list;
    }

    private static void deduplicateFallbackReflectors(List<MachineAddon> list) {
        boolean hasRealReflectors = list.stream().anyMatch(a -> !a.getId().startsWith("gtceu:reflector_tier_"));
        if (!hasRealReflectors) return;

        Set<Integer> realTiers = new HashSet<>();
        for (MachineAddon a : list) {
            if (!a.getId().startsWith("gtceu:reflector_tier_") && a instanceof GTReflectorAddon ra) {
                realTiers.add(ra.getReflectorTier());
            }
        }
        list.removeIf(a -> a.getId().startsWith("gtceu:reflector_tier_") && a instanceof GTReflectorAddon ra && realTiers.contains(ra.getReflectorTier()));
    }

    public static List<Integer> getAvailableReflectorTiers() {
        List<Integer> tiers = new ArrayList<>();
        tiers.add(0);
        List<MachineAddon> all = getAllReflectors();
        for (MachineAddon a : all) {
            if (a instanceof GTReflectorAddon ra && ra.getReflectorTier() > 0) {
                if (!tiers.contains(ra.getReflectorTier())) {
                    tiers.add(ra.getReflectorTier());
                }
            }
        }
        if (tiers.size() == 1) {
            for (int t = 1; t <= 5; t++) tiers.add(t);
        }
        Collections.sort(tiers);
        return tiers;
    }

    public static List<MachineAddon> getStandardReflectors() {
        List<MachineAddon> list = new ArrayList<>();
        for (int t = 1; t <= 5; t++) {
            ResourceLocation id = ResourceLocation.tryParse("gtceu:fusion_reflector_t" + t);
            String name = Component.translatable("gui.gtcalcboard.addon.reflector_tier_name", t).getString();
            String desc = Component.translatable("gui.gtcalcboard.addon.reflector_desc", t).getString();
            GTReflectorAddon addon = new GTReflectorAddon("gtceu:reflector_tier_" + t, name, desc, id, t);
            addon.setDiscoverySource("GTCEu Standard Reflector Specification");
            list.add(addon);
        }
        return list;
    }

    public static MachineAddon getReflectorForTier(int tier) {
        if (tier <= 0) return null;
        List<MachineAddon> all = getAllReflectors();
        MachineAddon fallback = null;
        for (MachineAddon a : all) {
            if (a instanceof GTReflectorAddon ra && ra.getReflectorTier() == tier) {
                if (!a.getId().startsWith("gtceu:reflector_tier_")) {
                    return a;
                }
                if (fallback == null) fallback = a;
            }
        }
        if (fallback != null) return fallback;
        ResourceLocation id = ResourceLocation.tryParse("gtceu:fusion_reflector_t" + tier);
        String name = Component.translatable("gui.gtcalcboard.addon.reflector_tier_name", tier).getString();
        String desc = Component.translatable("gui.gtcalcboard.addon.reflector_desc", tier).getString();
        GTReflectorAddon addon = new GTReflectorAddon("gtceu:reflector_tier_" + tier, name, desc, id, tier);
        addon.setDiscoverySource("GTCEu Standard Reflector Specification");
        return addon;
    }

    public static void installReflector(RecipeNode node, int tier) {
        if (node == null) return;
        node.getAddons().removeIf(a -> a instanceof GTReflectorAddon || a.getCategory() == MachineAddon.Category.REFLECTOR);
        if (tier > 0) {
            MachineAddon addon = getReflectorForTier(tier);
            if (addon != null) {
                node.getAddons().add(addon.copy());
            }
        }
    }

    public static void cycleReflector(RecipeNode node) {
        if (node == null) return;
        List<Integer> availableTiers = getAvailableReflectorTiers();
        int curTier = node.getInstalledReflectorTier();
        int curIdx = availableTiers.indexOf(curTier);
        if (curIdx < 0) curIdx = 0;
        int nextIdx = (curIdx + 1) % availableTiers.size();
        installReflector(node, availableTiers.get(nextIdx));
    }
}

