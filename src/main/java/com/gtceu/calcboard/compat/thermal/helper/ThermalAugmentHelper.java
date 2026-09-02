package com.gtceu.calcboard.compat.thermal.helper;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.thermal.addon.ThermalAugmentAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class ThermalAugmentHelper {

    private static final Class<?> DYNAMO_BLOCK_ENTITY_CLS;
    private static final Class<?> MACHINE_BLOCK_ENTITY_CLS;
    private static final Class<?> THERMAL_CORE_CONFIG_CLS;

    static {
        ClassLoader cl = ThermalAugmentHelper.class.getClassLoader();
        Class<?> dynCls = null;
        try {
            dynCls = Class.forName("cofh.thermal.expansion.block.entity.dynamo.DynamoBlockEntity", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        DYNAMO_BLOCK_ENTITY_CLS = dynCls;

        Class<?> machCls = null;
        try {
            machCls = Class.forName("cofh.thermal.expansion.block.entity.machine.MachineBlockEntity", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        MACHINE_BLOCK_ENTITY_CLS = machCls;

        Class<?> cfgCls = null;
        try {
            cfgCls = Class.forName("cofh.thermal.core.config.ThermalCoreConfig", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        THERMAL_CORE_CONFIG_CLS = cfgCls;
    }

    public static MachineAddon parseThermalAugment(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        CompoundTag augTag = extractAugmentTag(stack);
        if (augTag == null) {
            return null;
        }

        MachineAddon addon = parseThermalAugmentTag(augTag, stack.getHoverName().getString(), id);
        if (addon != null) {
            addon.setItemStackSample(stack);
            addon.setDiscoverySource((stack.hasTag() ? "Active Recipe Output NBT (AugmentData)" : "Thermal IAugmentItem Reflection") + " [" + id + "]");
        }
        return addon;
    }

    private static CompoundTag extractAugmentTag(ItemStack stack) {
        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                if (tag.contains("AugmentData")) return tag.getCompound("AugmentData");
                if (hasAnyAugmentKey(tag)) return tag;
            }
        }

        CompoundTag methodTag = inspectAugmentMethods(stack);
        if (methodTag != null) return methodTag;

        return inspectAugmentFields(stack);
    }

    private static CompoundTag inspectAugmentMethods(ItemStack stack) {
        Item item = stack.getItem();
        for (Method m : item.getClass().getMethods()) {
            String mn = m.getName().toLowerCase(Locale.ROOT);
            if (mn.contains("augmentdata") || mn.contains("augmenttag") || mn.contains("augment") || mn.contains("data")) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) {
                    try {
                        Object res = m.invoke(item, stack);
                        if (res instanceof CompoundTag ct && hasAnyAugmentKey(ct)) return ct;
                    } catch (ReflectiveOperationException ignored) {}
                } else if (m.getParameterCount() == 0) {
                    try {
                        Object res = m.invoke(item);
                        if (res instanceof CompoundTag ct && hasAnyAugmentKey(ct)) return ct;
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }
        return null;
    }

    private static CompoundTag inspectAugmentFields(ItemStack stack) {
        Class<?> cl = stack.getItem().getClass();
        while (cl != null && cl != Object.class) {
            for (Field f : cl.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object fVal = f.get(stack.getItem());
                    if (fVal instanceof CompoundTag ct) {
                        if (ct.contains("AugmentData")) return ct.getCompound("AugmentData");
                        if (hasAnyAugmentKey(ct)) return ct;
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
            cl = cl.getSuperclass();
        }
        return null;
    }

    private static boolean hasAnyAugmentKey(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return false;
        return tag.contains("Scale") || tag.contains("BaseMod") || tag.contains("DynScale") || tag.contains("DynamoScale")
                || tag.contains("Parallel") || tag.contains("Factor") || tag.contains("Tier") || tag.contains("Level")
                || tag.contains("DynamoPower") || tag.contains("DynPower") || tag.contains("PowerMod") || tag.contains("EnergyMod")
                || tag.contains("ProcessPower") || tag.contains("MachinePower") || tag.contains("SteamMod")
                || tag.contains("DynamoEnergy") || tag.contains("DynEnergy") || tag.contains("SpeedMod") || tag.contains("FuelMod")
                || tag.contains("EfficiencyMod") || tag.contains("ProcessEnergy") || tag.contains("MachineEnergy")
                || tag.contains("ProcessSpeed") || tag.contains("Type") || tag.contains("AugmentData");
    }

    public static MachineAddon parseThermalAugmentTag(CompoundTag augTag, String name, ResourceLocation id) {
        if (augTag == null) return null;

        if (augTag.contains("AugmentData")) {
            augTag = augTag.getCompound("AugmentData");
        }

        int parallel = extractTagInt(augTag, 1, "Scale", "BaseMod", "Factor", "Tier", "Level", "DynScale", "DynamoScale", "MachineScale", "Parallel");
        double eutMult = calculateEutMultiplier(augTag);
        double durMult = calculateDurationMultiplier(augTag);

        if (parallel <= 1 && eutMult == 1.0 && durMult == 1.0) {
            return null;
        }

        boolean isKit = parallel > 1;
        ThermalAugmentAddon.AugmentTarget target = resolveAugmentTarget(augTag, id, isKit);
        String desc = formatAugmentDescription(isKit, parallel, eutMult, durMult);
        String addonId = id.toString() + (parallel > 1 ? "_scale_" + parallel : "");

        ThermalAugmentAddon addon = new ThermalAugmentAddon(addonId, name == null || name.isEmpty() ? id.getPath() : name, desc, id, parallel, durMult, eutMult, isKit, target);
        addon.setDiscoverySource("AugmentData NBT Tag [" + id + "]");
        return addon;
    }

    private static double calculateEutMultiplier(CompoundTag augTag) {
        if (augTag.contains("DynamoPower") || augTag.contains("DynPower")) {
            return 1.0 + extractTagRawNumber(augTag, "DynamoPower", "DynPower");
        } else if (augTag.contains("MachinePower") || augTag.contains("ProcessPower")) {
            return 1.0 + extractTagRawNumber(augTag, "MachinePower", "ProcessPower");
        } else if (augTag.contains("PowerMod") || augTag.contains("EnergyMod")) {
            return extractTagNumber(augTag, "PowerMod", "EnergyMod");
        }
        return 1.0;
    }

    private static double calculateDurationMultiplier(CompoundTag augTag) {
        if (augTag.contains("DynamoEnergy") || augTag.contains("DynEnergy")) {
            return extractTagNumber(augTag, "DynamoEnergy", "DynEnergy");
        } else if (augTag.contains("MachineSpeed") || augTag.contains("ProcessSpeed")) {
            double spd = extractTagRawNumber(augTag, "MachineSpeed", "ProcessSpeed");
            return spd > 0 ? 1.0 / (1.0 + spd) : 1.0;
        } else if (augTag.contains("SpeedMod")) {
            double spdMod = extractTagNumber(augTag, "SpeedMod");
            return spdMod > 0 ? 1.0 / spdMod : 1.0;
        } else if (augTag.contains("FuelMod")) {
            return extractTagNumber(augTag, "FuelMod");
        } else if (augTag.contains("EfficiencyMod") || augTag.contains("ProcessEnergy") || augTag.contains("MachineEnergy")) {
            return extractTagNumber(augTag, "EfficiencyMod", "ProcessEnergy", "MachineEnergy");
        }
        return 1.0;
    }

    private static ThermalAugmentAddon.AugmentTarget resolveAugmentTarget(CompoundTag augTag, ResourceLocation id, boolean isKit) {
        if (isKit) return ThermalAugmentAddon.AugmentTarget.ALL;

        String typeStr = augTag.getString("Type").toLowerCase(Locale.ROOT);
        String path = id != null ? id.getPath().toLowerCase(Locale.ROOT) : "";

        boolean hasDynamoKeys = augTag.contains("DynamoPower") || augTag.contains("DynPower") || augTag.contains("DynamoEnergy") || augTag.contains("DynEnergy") || augTag.contains("FuelMod");
        boolean hasMachineKeys = augTag.contains("MachinePower") || augTag.contains("ProcessPower") || augTag.contains("MachineSpeed") || augTag.contains("ProcessSpeed") || augTag.contains("MachineEnergy");

        if (hasDynamoKeys || typeStr.contains("dynamo") || typeStr.contains("fuel")
                || path.contains("dynamo") || path.contains("reaction_chamber") || path.contains("injector")
                || path.contains("flux_linkage")) {
            return ThermalAugmentAddon.AugmentTarget.DYNAMO;
        } else if (hasMachineKeys || typeStr.contains("machine") || typeStr.contains("process")
                || path.contains("machine") || path.contains("sieve") || path.contains("reclamation")
                || path.contains("catalyst") || path.contains("filter")) {
            return ThermalAugmentAddon.AugmentTarget.MACHINE;
        }

        return ThermalAugmentAddon.AugmentTarget.ALL;
    }

    private static String formatAugmentDescription(boolean isKit, int parallel, double eutMult, double durMult) {
        if (isKit) {
            return String.format(Locale.ROOT, "⚡ %dx Scale Factor", parallel);
        } else if (eutMult != 1.0 && durMult != 1.0) {
            return String.format(Locale.ROOT, "⚡ Max Output: +%d%% (%.2fx) | ⏱ Fuel: %.2fx", (int) Math.round((eutMult - 1.0) * 100), eutMult, durMult);
        } else if (durMult != 1.0) {
            return String.format(Locale.ROOT, "⏱ Fuel Energy: %.2fx (%+d%%)", durMult, (int) Math.round((durMult - 1.0) * 100));
        } else if (eutMult != 1.0) {
            return String.format(Locale.ROOT, "⚡ Max Output: +%d%% (%.2fx)", (int) Math.round((eutMult - 1.0) * 100), eutMult);
        }
        return "";
    }

    public static boolean isDynamoNode(RecipeNode node) {
        if (node == null) return false;
        if (node.isGenerator() || node.getBaseEUt() < 0) return true;

        if (node.getRecipeCategoryId() != null && isDynamoKeyword(node.getRecipeCategoryId().getPath())) {
            return true;
        }
        if (node.getMachineIcon() != null && isDynamoKeyword(node.getMachineIcon().getPath())) {
            return true;
        }
        if (node.getName() != null && isDynamoKeyword(node.getName())) {
            return true;
        }
        return false;
    }

    private static boolean isDynamoKeyword(String str) {
        String p = str.toLowerCase(Locale.ROOT);
        return p.contains("fuel") || p.contains("dynamo") || p.contains("lapidary") || p.contains("magmatic")
                || p.contains("numismatic") || p.contains("gourmand") || p.contains("compression")
                || p.contains("disenchantment") || p.contains("stirling");
    }

    public static double extractTagRawNumber(CompoundTag tag, String... keys) {
        if (tag == null) return 0.0;
        for (String k : keys) {
            if (tag.contains(k) && tag.get(k) instanceof NumericTag num) {
                return num.getAsDouble();
            }
        }
        return 0.0;
    }

    public static double extractTagNumber(CompoundTag tag, String... keys) {
        if (tag == null) return 1.0;
        for (String k : keys) {
            if (tag.contains(k) && tag.get(k) instanceof NumericTag num) {
                return num.getAsDouble();
            }
        }
        return 1.0;
    }

    public static int extractTagInt(CompoundTag tag, int def, String... keys) {
        if (tag == null) return def;
        for (String k : keys) {
            if (tag.contains(k) && tag.get(k) instanceof NumericTag num) {
                int val = (int) Math.round(num.getAsDouble());
                if (val > 0) return val;
            }
        }
        return def;
    }

    public static boolean isThermalMachine(RecipeNode node) {
        if (node == null) return false;

        ResourceLocation icon = node.getMachineIcon();
        if (icon != null && isThermalNamespaceOrTag(icon)) {
            return true;
        }

        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null && isThermalNamespace(catId.getNamespace())) {
            return true;
        }

        if (node.getAddons().stream().anyMatch(a -> a instanceof ThermalAugmentAddon || a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT || "thermal".equals(a.getModId()))) {
            return true;
        }

        if (node.getName() != null) {
            String nl = node.getName().toLowerCase(Locale.ROOT);
            if (isThermalDynamoFallbackName(nl)) {
                if (icon == null || !icon.getNamespace().equals("gtceu") || nl.contains("dynamo") || nl.contains("lapidary")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isThermalDynamoFallbackName(String nl) {
        return nl.contains("dynamo") || nl.contains("lapidary") || nl.contains("numismatic")
                || nl.contains("magmatic") || nl.contains("gourmand") || nl.contains("disenchantment")
                || nl.contains("stirling");
    }

    private static boolean isThermalNamespace(String ns) {
        String lower = ns.toLowerCase(Locale.ROOT);
        return lower.equals("thermal") || lower.equals("thermal_expansion") || lower.equals("thermal_foundation")
                || lower.equals("thermal_innovation") || lower.equals("thermal_extra") || lower.equals("cofh_core")
                || lower.equals("systeams");
    }

    private static boolean isThermalNamespaceOrTag(ResourceLocation icon) {
        return isThermalNamespace(icon.getNamespace()) || isThermalTaggedItem(icon);
    }

    public static boolean isThermalTaggedItem(ResourceLocation id) {
        if (id == null) return false;
        if (isThermalNamespace(id.getNamespace())) return true;
        return hasItemTag(id, "thermal:dynamos", "systeams:dynamos", "thermal:machines", "systeams:machines", "systeams:boilers");
    }

    public static boolean isDynamoItem(ResourceLocation id) {
        if (id == null) return false;
        if (hasItemTag(id, "thermal:dynamos", "systeams:dynamos", "forge:dynamos")) return true;
        return checkItemClassHierarchy(id, "Dynamo");
    }

    public static boolean isBoilerItem(ResourceLocation id) {
        if (id == null) return false;
        if (hasItemTag(id, "systeams:boilers", "thermal:boilers", "forge:boilers")) return true;
        return checkItemClassHierarchy(id, "Boiler");
    }

    private static boolean checkItemClassHierarchy(ResourceLocation id, String searchName) {
        if (id == null || ForgeRegistries.ITEMS == null) return false;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item != null) {
            Class<?> cur = item.getClass();
            while (cur != null && cur != Object.class) {
                if (cur.getSimpleName().contains(searchName) || cur.getName().contains(searchName)) return true;
                for (Class<?> iface : cur.getInterfaces()) {
                    if (iface.getSimpleName().contains(searchName) || iface.getName().contains(searchName)) return true;
                }
                cur = cur.getSuperclass();
            }
        }
        return false;
    }

    private static boolean hasItemTag(ResourceLocation id, String... tagIds) {
        if (id == null || ForgeRegistries.ITEMS == null) return false;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return false;

        var holderOpt = ForgeRegistries.ITEMS.getHolder(item);
        if (holderOpt.isPresent()) {
            var holder = holderOpt.get();
            var itemReg = Registries.ITEM;
            for (String t : tagIds) {
                TagKey<Item> tagKey = TagKey.create(itemReg, ResourceLocation.tryParse(t));
                if (holder.containsTag(tagKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isDynamoRecipe(Object backing) {
        if (backing == null) return false;
        Class<?> cl = backing.getClass();
        while (cl != null && cl != Object.class) {
            String name = cl.getName();
            if (name.equals("cofh.thermal.lib.util.recipes.ThermalFuel") ||
                name.equals("chiefarug.mods.systeams.recipe.SteamFuel") ||
                name.endsWith("Fuel") || name.endsWith("FuelRecipe") ||
                name.contains("DynamoFuel")) {
                return true;
            }
            for (Class<?> iface : cl.getInterfaces()) {
                String iname = iface.getName();
                if (iname.contains("Fuel") || iname.contains("Dynamo")) {
                    return true;
                }
            }
            cl = cl.getSuperclass();
        }
        return false;
    }

    public static boolean isBoilerRecipe(Object backing) {
        if (backing == null) return false;
        Class<?> cl = backing.getClass();
        while (cl != null && cl != Object.class) {
            String name = cl.getName();
            if (name.equals("chiefarug.mods.systeams.recipe.BoilingRecipe") ||
                name.contains("Boil") || name.contains("Boiler")) {
                return true;
            }
            cl = cl.getSuperclass();
        }
        return false;
    }

    public static double getThermalDynamoBasePowerRF(ResourceLocation dynamoId) {
        if (DYNAMO_BLOCK_ENTITY_CLS != null) {
            for (Field f : DYNAMO_BLOCK_ENTITY_CLS.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase("BASE_POWER") || f.getName().equalsIgnoreCase("DEFAULT_POWER")) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val instanceof Number num && num.doubleValue() > 0) {
                            return num.doubleValue();
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }

        if (THERMAL_CORE_CONFIG_CLS != null) {
            for (Field f : THERMAL_CORE_CONFIG_CLS.getDeclaredFields()) {
                if (f.getName().toLowerCase(Locale.ROOT).contains("dynamopower") || f.getName().toLowerCase(Locale.ROOT).contains("defaultpower")) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val instanceof Number num && num.doubleValue() > 0) {
                            return num.doubleValue();
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }

        return 200.0;
    }

    public static double getThermalMachineBasePowerRF(ResourceLocation machineId) {
        if (MACHINE_BLOCK_ENTITY_CLS != null) {
            for (Field f : MACHINE_BLOCK_ENTITY_CLS.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase("BASE_POWER") || f.getName().equalsIgnoreCase("DEFAULT_POWER")) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val instanceof Number num && num.doubleValue() > 0) {
                            return num.doubleValue();
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }
        return 20.0;
    }

    public static boolean isThermalUpgradeKit(MachineAddon addon) {
        if (addon == null) return false;
        if (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) return true;
        if (addon.isUpgradeTierKit()) return true;
        if (addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT) {
            return addon.getParallelMultiplier() > 1;
        }
        return false;
    }

    public static boolean canInstallThermalAddon(RecipeNode node, MachineAddon addon, BiPredicate<RecipeNode, MachineAddon> compatibilityChecker) {
        if (node == null || addon == null) return false;
        if (compatibilityChecker != null && !compatibilityChecker.test(node, addon)) return false;
        if (isThermalUpgradeKit(addon)) {
            boolean sameKitInstalled = node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
            return !sameKitInstalled;
        }
        long nonKitCount = node.getAddons().stream().filter(a -> !isThermalUpgradeKit(a)).count();
        return nonKitCount < 3;
    }

    public static void onThermalAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (isThermalUpgradeKit(addon)) {
            node.getAddons().removeIf(ThermalAugmentHelper::isThermalUpgradeKit);
        }
        node.getAddons().add(addon);
    }

    public static void handleInstallThermalAddon(RecipeNode node, MachineAddon addon, boolean shiftClick) {
        if (node == null || addon == null) return;
        if (isThermalUpgradeKit(addon)) {
            onThermalAddonInstalled(node, addon.copy());
        } else {
            long nonKitCount = node.getAddons().stream().filter(a -> !isThermalUpgradeKit(a)).count();
            if (shiftClick) {
                int toAdd = (int) (3 - nonKitCount);
                for (int k = 0; k < toAdd; k++) {
                    onThermalAddonInstalled(node, addon.copy());
                }
            } else {
                if (nonKitCount < 3) {
                    onThermalAddonInstalled(node, addon.copy());
                }
            }
        }
    }

    public static void handleUninstallThermalAddon(RecipeNode node, MachineAddon addon, BiConsumer<RecipeNode, MachineAddon> onRemovedCallback) {
        if (node == null || addon == null) return;
        if (isThermalUpgradeKit(addon)) {
            node.getAddons().removeIf(a -> a.getId().equals(addon.getId()) || isThermalUpgradeKit(a));
            if (onRemovedCallback != null) {
                onRemovedCallback.accept(node, addon);
            }
        } else {
            node.removeSingleAddon(addon.getId());
        }
    }

    public static void buildThermalAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
        if (isThermalUpgradeKit(addon)) {
            buildUpgradeKitTooltip(node, addon, isActiveAddon, tooltip);
        } else {
            buildRegularAugmentTooltip(node, addon, tooltip);
        }
    }

    private static void buildUpgradeKitTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        tooltip.add(Component.literal("§6").append(Component.translatable("gui.gtcalcboard.addon.thermal.upgrade_desc", addon.getParallelMultiplier())));
        if (isActiveAddon) {
            tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_kit")));
        } else {
            boolean isInst = node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
            if (isInst) {
                tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_kit")));
            } else {
                tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.install_kit")));
            }
        }
    }

    private static void buildRegularAugmentTooltip(RecipeNode node, MachineAddon addon, List<Component> tooltip) {
        long totalRegAugs = node.getAddons().stream().filter(a -> !isThermalUpgradeKit(a)).count();
        int targetCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();

        tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.addon.thermal.slots", totalRegAugs)));
        if (targetCount > 0) {
            tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.installed", targetCount)));
            if (totalRegAugs < 3) {
                tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.add_copy")));
            }
            tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_copy")));
        } else {
            if (totalRegAugs < 3) {
                tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.install")));
            } else {
                tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.thermal.slots_full")));
            }
        }
    }
}

