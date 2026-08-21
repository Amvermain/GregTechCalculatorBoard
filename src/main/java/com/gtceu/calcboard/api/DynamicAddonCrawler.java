package com.gtceu.calcboard.api;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamically queries GTCEu Modern, Thermal Series, and addon item registries
 * to discover Parallel Hatches, Maintenance Hatches, Turbine Rotors, Heating Coils, and Multiblock Traits
 * using official mod APIs, reflection, and deterministic NBT data.
 */
public class DynamicAddonCrawler {

    public static List<MachineAddon> getBuiltinTraits() {
        List<MachineAddon> list = new ArrayList<>();
        addBuiltinTraits(list);
        return list;
    }

    public static boolean isRecipeBakingComplete() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        try {
            var emiManager = dev.emi.emi.api.EmiApi.getRecipeManager();
            if (emiManager != null && emiManager.getRecipes() != null && !emiManager.getRecipes().isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            if (mc.level.getRecipeManager() != null && !mc.level.getRecipeManager().getRecipes().isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static List<MachineAddon> crawlDynamicAddons() {
        long startNanos = System.nanoTime();
        List<MachineAddon> result = new ArrayList<>();

        // 1. Static Registry Items (GTCEu Rotors from GTRegistries.MATERIALS, Coils from GTRegistries.COILS)
        MachineAddonCatalog.getInstance().setProgress(2, 4, "gui.gtcalcboard.loading_phase.2", "GTCEu Coils & Hatches");
        try {
            TurbineRotorHelper.discoverGTCEuRotors(result);
            CoilHelper.discoverGTCEuCoils(result);
        } catch (Throwable ignored) {}
        int gtStaticCount = result.size();

        // 2. Dynamic Recipe Outputs (Thermal Augments / KubeJS Kits with NBT)
        MachineAddonCatalog.getInstance().setProgress(3, 4, "gui.gtcalcboard.loading_phase.3", "Recipe Outputs & Augments (" + gtStaticCount + " found)");
        int dynamicAugmentCount = 0;
        Minecraft mc = Minecraft.getInstance();
        List<ItemStack> recipeOutputStacks = new ArrayList<>();
        if (mc != null && mc.level != null) {
            collectRecipeOutputStacks(mc, recipeOutputStacks);

            for (ItemStack s : recipeOutputStacks) {
                try {
                    if (s == null || s.isEmpty()) continue;
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                    if (id == null) continue;

                    MachineAddon aug = parseThermalAugment(s, id);
                    if (aug != null && !containsAddonId(result, aug.getId())) {
                        result.add(aug);
                        dynamicAugmentCount++;
                    }

                    MachineAddon rotor = parseTurbineRotor(s, id);
                    if (rotor != null && !containsAddonId(result, rotor.getId())) {
                        result.add(rotor);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 3. Forge Registry Crawl with NBT samples
        MachineAddonCatalog.getInstance().setProgress(4, 4, "gui.gtcalcboard.loading_phase.4", "Item Registry Cache (" + result.size() + " items)");
        crawlItemRegistry(recipeOutputStacks, result);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Crawler] Crawled {} total addons (GT Static: {}, Recipe Stacks: {}, Thermal Augments: {}) in {}ms.",
                result.size(), gtStaticCount, recipeOutputStacks.size(), dynamicAugmentCount, elapsedMs
        );

        return result;
    }

    public static List<MachineAddon> crawlAllAddons() {
        List<MachineAddon> result = new ArrayList<>(getBuiltinTraits());
        result.addAll(crawlDynamicAddons());
        return result;
    }

    public static void extractItemStacks(Object obj, List<ItemStack> outputStacks) {
        if (obj == null) return;
        if (obj instanceof ItemStack is) {
            if (!is.isEmpty()) {
                outputStacks.add(is);
            }
            return;
        }
        if (obj instanceof net.minecraft.world.item.crafting.Ingredient ing) {
            try {
                for (ItemStack is : ing.getItems()) {
                    if (is != null && !is.isEmpty()) {
                        outputStacks.add(is);
                    }
                }
            } catch (Throwable ignored) {}
            return;
        }
        if (obj instanceof dev.emi.emi.api.stack.EmiStack emiStack) {
            try {
                if (!emiStack.isEmpty()) {
                    ItemStack is = emiStack.getItemStack();
                    if (is != null && !is.isEmpty()) {
                        if (!is.hasTag() && emiStack.getNbt() != null) {
                            is = is.copy();
                            is.setTag(emiStack.getNbt().copy());
                        }
                        outputStacks.add(is);
                    }
                }
            } catch (Throwable ignored) {}
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object val : map.values()) {
                extractItemStacks(val, outputStacks);
            }
            return;
        }
        if (obj instanceof Iterable<?> iter) {
            for (Object item : iter) {
                extractItemStacks(item, outputStacks);
            }
            return;
        }
        if (obj instanceof Object[] arr) {
            for (Object item : arr) {
                extractItemStacks(item, outputStacks);
            }
            return;
        }

        // Direct SizedIngredient & Content resolution
        String clName = obj.getClass().getName();
        if (clName.contains("SizedIngredient")) {
            try {
                Method mGetInner = obj.getClass().getMethod("getInner");
                Object inner = mGetInner.invoke(obj);
                if (inner != null && inner != obj) extractItemStacks(inner, outputStacks);
            } catch (Throwable ignored) {}
            try {
                Method mGetItems = obj.getClass().getMethod("getItems");
                Object items = mGetItems.invoke(obj);
                if (items != null && items != obj) extractItemStacks(items, outputStacks);
            } catch (Throwable ignored) {}
        }
        if (clName.contains("Content")) {
            try {
                Method mGetContent = obj.getClass().getMethod("getContent");
                Object content = mGetContent.invoke(obj);
                if (content != null && content != obj) extractItemStacks(content, outputStacks);
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> cl = obj.getClass();
            for (Field f : cl.getFields()) {
                try {
                    String fn = f.getName();
                    if (fn.equals("content") || fn.equals("stack") || fn.equals("itemStack") || fn.equals("ingredient") || fn.equals("inner") || fn.equals("outputs")) {
                        Object fVal = f.get(obj);
                        if (fVal != null && fVal != obj) {
                            extractItemStacks(fVal, outputStacks);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            for (Method m : cl.getMethods()) {
                try {
                    if (m.getParameterCount() == 0) {
                        String mn = m.getName();
                        if (mn.equals("getContent") || mn.equals("getItems") || mn.equals("getMatchingStacks") || mn.equals("getInner") || mn.equals("getItemStack") || mn.equals("getOutputs") || mn.equals("getOutputsList") || mn.equals("getResults") || mn.equals("getResultItem") || mn.equals("item")) {
                            Object res = m.invoke(obj);
                            if (res != null && res != obj) {
                                extractItemStacks(res, outputStacks);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void collectRecipeOutputStacks(Minecraft mc, List<ItemStack> recipeOutputStacks) {
        try {
            var emiRecipeManager = dev.emi.emi.api.EmiApi.getRecipeManager();
            if (emiRecipeManager != null && emiRecipeManager.getRecipes() != null) {
                for (dev.emi.emi.api.recipe.EmiRecipe emiRecipe : emiRecipeManager.getRecipes()) {
                    if (emiRecipe == null) continue;
                    if (emiRecipe.getOutputs() != null) {
                        for (dev.emi.emi.api.stack.EmiStack out : emiRecipe.getOutputs()) {
                            if (out == null || out.isEmpty()) continue;
                            try {
                                ItemStack s = out.getItemStack();
                                if (s != null && !s.isEmpty()) {
                                    if (!s.hasTag() && out.getNbt() != null) {
                                        s = s.copy();
                                        s.setTag(out.getNbt().copy());
                                    }
                                    recipeOutputStacks.add(s);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    try {
                        extractItemStacks(emiRecipe, recipeOutputStacks);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Also scan RecipeManager directly for GTCEu / Crafting recipes
        try {
            if (mc.level != null && mc.level.getRecipeManager() != null) {
                for (Recipe<?> recipe : mc.level.getRecipeManager().getRecipes()) {
                    if (recipe == null) continue;
                    try {
                        ItemStack out = recipe.getResultItem(mc.level.registryAccess());
                        if (out != null && !out.isEmpty()) {
                            recipeOutputStacks.add(out);
                        }
                    } catch (Throwable ignored) {}
                    try {
                        extractItemStacks(recipe, recipeOutputStacks);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void crawlItemRegistry(List<ItemStack> recipeOutputStacks, List<MachineAddon> result) {
        try {
            if (ForgeRegistries.ITEMS == null || ForgeRegistries.ITEMS.isEmpty()) return;

            // Collect samples from recipe outputs for items that need NBT
            Map<Item, ItemStack> nbtItemSamples = new HashMap<>();
            for (ItemStack s : recipeOutputStacks) {
                if (s != null && s.hasTag()) {
                    nbtItemSamples.put(s.getItem(), s);
                }
            }

            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null) continue;

                ItemStack stack = nbtItemSamples.getOrDefault(item, new ItemStack(item));

                MachineAddon coil = parseCoilBlock(stack, id);
                if (coil != null && !containsAddonId(result, coil.getId())) {
                    result.add(coil);
                    continue;
                }

                MachineAddon parallel = parseParallelHatch(stack, id);
                if (parallel != null && !containsAddonId(result, parallel.getId())) {
                    result.add(parallel);
                    continue;
                }

                MachineAddon rotor = parseTurbineRotor(stack, id);
                if (rotor != null && !containsAddonId(result, rotor.getId())) {
                    result.add(rotor);
                    continue;
                }

                if (isMaintenanceHatchItem(item, id)) {
                    List<MachineAddon> mAddons = parseMaintenanceHatches(stack, id);
                    for (MachineAddon addon : mAddons) {
                        if (addon != null && !containsAddonId(result, addon.getId())) {
                            result.add(addon);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isMaintenanceHatchItem(Item item, ResourceLocation id) {
        if (item instanceof BlockItem bi) {
            Block b = bi.getBlock();
            try {
                Method mGetDef = b.getClass().getMethod("getDefinition");
                Object def = mGetDef.invoke(b);
                if (def != null) {
                    Class<?> maintPartCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine");
                    Method mGetMachineClass = def.getClass().getMethod("getMachineClass");
                    Class<?> mCls = (Class<?>) mGetMachineClass.invoke(def);
                    if (mCls != null && maintPartCls.isAssignableFrom(mCls)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean containsAddonId(List<MachineAddon> list, String id) {
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }

    private static void addBuiltinTraits(List<MachineAddon> list) {
        MachineAddon boost = new MachineAddon("gtceu:throughput_boosting", "gui.gtcalcboard.addon.throughput_boosting", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.throughput_boosting.desc", ResourceLocation.tryParse("gtceu:pyrolyse_oven"));
        boost.setParallelMultiplier(4);
        boost.setDurationMultiplier(1.6);
        boost.setEutMultiplier(0.95);
        list.add(boost);

        MachineAddon batch = new MachineAddon("gtceu:batch_processing", "gui.gtcalcboard.addon.batch_processing", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.batch_processing.desc", null);
        batch.setParallelMultiplier(16);
        batch.setDurationMultiplier(13.0);
        batch.setEutMultiplier(1.0);
        list.add(batch);

        MachineAddon overpressure = new MachineAddon("gtceu:overpressure_autoclave", "gui.gtcalcboard.addon.overpressure_autoclave", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.overpressure_autoclave.desc", ResourceLocation.tryParse("gtceu:autoclave"));
        overpressure.setParallelMultiplier(8);
        overpressure.setDurationMultiplier(1.5);
        overpressure.setEutMultiplier(1.25);
        list.add(overpressure);

        MachineAddon cmhFast = new MachineAddon("gtceu:configurable_maintenance_hatch_fast", "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhFast.setDurationMultiplier(0.9);
        cmhFast.setEutMultiplier(1.0);
        list.add(cmhFast);

        MachineAddon cmhEco = new MachineAddon("gtceu:configurable_maintenance_hatch_eco", "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhEco.setDurationMultiplier(1.1);
        cmhEco.setEutMultiplier(1.0);
        list.add(cmhEco);
    }

    private static MachineAddon parseParallelHatch(ItemStack stack, ResourceLocation id) {
        return ParallelHelper.parseParallelHatch(stack, id);
    }

    private static boolean isConfigurableMaintenanceHatch(Item item) {
        if (item instanceof BlockItem bi) {
            Block b = bi.getBlock();
            try {
                Method mGetDef = b.getClass().getMethod("getDefinition");
                Object def = mGetDef.invoke(b);
                if (def != null) {
                    Class<?> cfgMaintCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.part.ConfigurableMaintenanceHatchPartMachine");
                    Method mGetMachineClass = def.getClass().getMethod("getMachineClass");
                    Class<?> mCls = (Class<?>) mGetMachineClass.invoke(def);
                    if (mCls != null && cfgMaintCls.isAssignableFrom(mCls)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static List<MachineAddon> parseMaintenanceHatches(ItemStack stack, ResourceLocation id) {
        List<MachineAddon> list = new ArrayList<>();

        if (isConfigurableMaintenanceHatch(stack.getItem())) {
            MachineAddon fast = new MachineAddon(id + "_fast", 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_fast").getString(), 
                    MachineAddon.Category.MAINTENANCE, 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_fast.desc").getString(), 
                    id);
            fast.setDurationMultiplier(0.9);
            fast.setEutMultiplier(1.0);
            fast.setItemStackSample(stack);
            list.add(fast);

            MachineAddon eco = new MachineAddon(id + "_eco", 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_eco").getString(), 
                    MachineAddon.Category.MAINTENANCE, 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_eco.desc").getString(), 
                    id);
            eco.setDurationMultiplier(1.1);
            eco.setEutMultiplier(1.0);
            eco.setItemStackSample(stack);
            list.add(eco);
        } else {
            String name = stack.getHoverName().getString();
            String desc = Component.translatable("gui.gtcalcboard.addon.maintenance_hatch_desc").getString();
            MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.toString() : name, MachineAddon.Category.MAINTENANCE, desc, id);
            addon.setDurationMultiplier(1.0);
            addon.setEutMultiplier(1.0);
            addon.setItemStackSample(stack);
            list.add(addon);
        }

        return list;
    }

    public static MachineAddon parseTurbineRotor(ItemStack stack, ResourceLocation id) {
        return TurbineRotorHelper.parseTurbineRotor(stack, id);
    }

    public static MachineAddon parseThermalAugment(ItemStack stack, ResourceLocation id) {
        return ThermalAugmentHelper.parseThermalAugment(stack, id);
    }

    public static MachineAddon parseThermalAugmentTag(CompoundTag rootTag, String name, ResourceLocation id) {
        return ThermalAugmentHelper.parseThermalAugmentTag(rootTag, name, id);
    }

    public static MachineAddon parseCoilBlock(ItemStack stack, ResourceLocation id) {
        return CoilHelper.parseCoilBlock(stack, id);
    }
}
