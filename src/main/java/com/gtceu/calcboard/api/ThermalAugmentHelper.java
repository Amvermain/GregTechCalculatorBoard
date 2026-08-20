package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Deductively parses and extracts performance modifiers from Thermal / KubeJS Augments.
 */
public class ThermalAugmentHelper {

    public static MachineAddon parseThermalAugment(ItemStack stack, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        CompoundTag augTag = null;
        if (stack.hasTag()) {
            if (stack.getTag().contains("AugmentData")) {
                augTag = stack.getTag().getCompound("AugmentData");
            } else if (hasAnyAugmentKey(stack.getTag())) {
                augTag = stack.getTag();
            }
        }

        if (augTag == null) {
            // Deductive Reflection: Query IAugmentItem API from CoFH / Thermal
            try {
                for (java.lang.reflect.Method m : stack.getItem().getClass().getMethods()) {
                    if (m.getName().equalsIgnoreCase("getAugmentData") || m.getName().equalsIgnoreCase("getAugmentTag")) {
                        if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) {
                            Object res = m.invoke(stack.getItem(), stack);
                            if (res instanceof CompoundTag ct) {
                                augTag = ct;
                                break;
                            }
                        } else if (m.getParameterCount() == 0) {
                            Object res = m.invoke(stack.getItem());
                            if (res instanceof CompoundTag ct) {
                                augTag = ct;
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            if (augTag == null) {
                Class<?> cl = stack.getItem().getClass();
                while (cl != null && cl != Object.class) {
                    for (java.lang.reflect.Field f : cl.getDeclaredFields()) {
                        try {
                            f.setAccessible(true);
                            Object fVal = f.get(stack.getItem());
                            if (fVal instanceof CompoundTag ct) {
                                if (ct.contains("AugmentData")) {
                                    augTag = ct.getCompound("AugmentData");
                                    break;
                                } else if (hasAnyAugmentKey(ct)) {
                                    augTag = ct;
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (augTag != null) break;
                    cl = cl.getSuperclass();
                }
            }
        }

        if (augTag == null) {
            return null;
        }

        MachineAddon addon = parseThermalAugmentTag(augTag, stack.getHoverName().getString(), id);
        if (addon != null) {
            addon.setItemStackSample(stack);
        }
        return addon;
    }

    private static boolean hasAnyAugmentKey(CompoundTag tag) {
        return tag.contains("Scale") || tag.contains("BaseMod") || tag.contains("DynScale") || tag.contains("DynamoScale") || tag.contains("Parallel") || tag.contains("Factor") || tag.contains("Tier")
                || tag.contains("DynamoPower") || tag.contains("DynPower") || tag.contains("PowerMod") || tag.contains("EnergyMod") || tag.contains("ProcessPower") || tag.contains("MachinePower") || tag.contains("SteamMod")
                || tag.contains("DynamoEnergy") || tag.contains("DynEnergy") || tag.contains("SpeedMod") || tag.contains("FuelMod") || tag.contains("EfficiencyMod") || tag.contains("ProcessEnergy") || tag.contains("MachineSpeed") || tag.contains("MachineEnergy");
    }

    public static MachineAddon parseThermalAugmentTag(CompoundTag augTag, String name, ResourceLocation id) {
        if (augTag == null) {
            return null;
        }

        if (augTag.contains("AugmentData")) {
            augTag = augTag.getCompound("AugmentData");
        }

        int parallel = extractTagInt(augTag, 1, "Scale", "BaseMod", "DynScale", "DynamoScale");

        double eutMult = 1.0;
        if (augTag.contains("DynamoPower") || augTag.contains("DynPower")) {
            double dynPower = extractTagRawNumber(augTag, "DynamoPower", "DynPower");
            eutMult = 1.0 + dynPower;
        } else if (augTag.contains("PowerMod") || augTag.contains("EnergyMod") || augTag.contains("ProcessPower")) {
            eutMult = extractTagNumber(augTag, "PowerMod", "EnergyMod", "ProcessPower");
        }

        double durMult = 1.0;
        if (augTag.contains("DynamoEnergy") || augTag.contains("DynEnergy") || augTag.contains("SpeedMod") || augTag.contains("FuelMod") || augTag.contains("EfficiencyMod") || augTag.contains("ProcessEnergy")) {
            durMult = extractTagNumber(augTag, "DynamoEnergy", "DynEnergy", "SpeedMod", "FuelMod", "EfficiencyMod", "ProcessEnergy");
        }

        // Exclude passive storage / RF / fluid augments that have no rate/power effects
        if (parallel <= 1 && eutMult == 1.0 && durMult == 1.0) {
            return null;
        }

        String desc = "";
        if (parallel > 1) {
            desc = String.format("⚡ %dx Scale Factor", parallel);
        } else if (eutMult != 1.0 && durMult != 1.0) {
            desc = String.format("⚡ Max Output: +%d%% (%.2fx) | ⏱ Fuel: %.2fx", (int) Math.round((eutMult - 1.0) * 100), eutMult, durMult);
        } else if (durMult != 1.0) {
            desc = String.format("⏱ Fuel Energy: %.2fx (%+d%%)", durMult, (int) Math.round((durMult - 1.0) * 100));
        } else if (eutMult != 1.0) {
            desc = String.format("⚡ Max Output: +%d%% (%.2fx)", (int) Math.round((eutMult - 1.0) * 100), eutMult);
        }

        String addonId = id.toString() + "#" + augTag.toString().hashCode();

        MachineAddon addon = new MachineAddon(addonId, name == null || name.isEmpty() ? id.getPath() : name, MachineAddon.Category.THERMAL_AUGMENT, desc, id);
        addon.setParallelMultiplier(parallel);
        addon.setEutMultiplier(eutMult);
        addon.setDurationMultiplier(durMult);
        return addon;
    }

    public static double extractTagRawNumber(CompoundTag tag, String... keys) {
        for (String k : keys) {
            if (tag.contains(k)) {
                try {
                    double val = tag.getDouble(k);
                    if (val != 0) return val;
                } catch (Throwable ignored) {}
                try {
                    float val = tag.getFloat(k);
                    if (val != 0) return val;
                } catch (Throwable ignored) {}
                try {
                    int val = tag.getInt(k);
                    if (val != 0) return val;
                } catch (Throwable ignored) {}
            }
        }
        return 0.0;
    }

    public static double extractTagNumber(CompoundTag tag, String... keys) {
        for (String k : keys) {
            if (tag.contains(k)) {
                try {
                    double val = tag.getDouble(k);
                    if (val != 0) return val;
                } catch (Throwable ignored) {}
                try {
                    float val = tag.getFloat(k);
                    if (val != 0) return val;
                } catch (Throwable ignored) {}
                try {
                    int val = tag.getInt(k);
                    if (val != 0) return val;
                } catch (Throwable ignored) {}
            }
        }
        return 1.0;
    }

    public static int extractTagInt(CompoundTag tag, int def, String... keys) {
        for (String k : keys) {
            if (tag.contains(k)) {
                try {
                    float val = tag.getFloat(k);
                    if (val > 0) return Math.round(val);
                } catch (Throwable ignored) {}
                try {
                    int val = tag.getInt(k);
                    if (val > 0) return val;
                } catch (Throwable ignored) {}
                try {
                    double val = tag.getDouble(k);
                    if (val > 0) return (int) Math.round(val);
                } catch (Throwable ignored) {}
            }
        }
        return def;
    }

    public static boolean isThermalMachine(RecipeNode node) {
        if (node == null) return false;

        // 0. Recipe Category ID namespace check
        if (node.getRecipeCategoryId() != null) {
            String ns = node.getRecipeCategoryId().getNamespace().toLowerCase();
            if (ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("systeams") || ns.equals("cofh_core")) {
                return true;
            }
        }

        // 1. Tag-based detection on machine icon & available workstations
        if (node.getMachineIcon() != null && isThermalTaggedItem(node.getMachineIcon())) {
            return true;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (isThermalTaggedItem(ws)) {
                return true;
            }
        }

        // 2. Namespace fallback
        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace().toLowerCase();
            if (ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("systeams") || ns.equals("cofh_core")) {
                return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                String ns = ws.getNamespace().toLowerCase();
                if (ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("systeams") || ns.equals("cofh_core")) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isThermalTaggedItem(ResourceLocation id) {
        if (id == null) return false;
        try {
            if (net.minecraftforge.registries.ForgeRegistries.ITEMS == null) return false;
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                var holder = net.minecraftforge.registries.ForgeRegistries.ITEMS.getHolder(item);
                if (holder.isPresent()) {
                    var h = holder.get();
                    var itemReg = net.minecraft.core.registries.Registries.ITEM;
                    var dynTag1 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("thermal:dynamos"));
                    var dynTag2 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("systeams:dynamos"));
                    var machTag1 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("thermal:machines"));
                    var machTag2 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("systeams:machines"));
                    if (h.containsTag(dynTag1) || h.containsTag(dynTag2) || h.containsTag(machTag1) || h.containsTag(machTag2)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
