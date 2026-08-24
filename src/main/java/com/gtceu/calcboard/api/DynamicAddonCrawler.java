package com.gtceu.calcboard.api;

import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.TurbineRotorHelper;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dynamically queries GTCEu Modern, Thermal Series, and addon item registries
 * to discover Parallel Hatches, Maintenance Hatches, Turbine Rotors, Heating Coils, and Multiblock Traits
 * using official mod APIs, reflection, and deterministic NBT data.
 */
public class DynamicAddonCrawler {

    public static List<MachineAddon> getBuiltinTraits() {
        List<MachineAddon> list = new ArrayList<>();
        for (com.gtceu.calcboard.compat.IModAdapter adapter : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.discoverAddons(list, Collections.emptyList());
            } catch (Throwable ignored) {}
        }
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

    public static List<ItemStack> collectAllActiveItemStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        Set<Item> seenItemsWithoutTag = new HashSet<>();
        Set<String> seenItemsWithTag = new HashSet<>();

        java.util.function.Consumer<ItemStack> collector = is -> {
            if (is == null || is.isEmpty()) return;
            if (is.hasTag()) {
                String key = ForgeRegistries.ITEMS.getKey(is.getItem()) + "#" + is.getTag().toString();
                if (seenItemsWithTag.add(key)) {
                    stacks.add(is);
                }
            } else {
                if (seenItemsWithoutTag.add(is.getItem())) {
                    stacks.add(is);
                }
            }
        };

        // 1. If EMI is loaded, scan EMI recipe outputs
        try {
            var emiRecipeManager = dev.emi.emi.api.EmiApi.getRecipeManager();
            if (emiRecipeManager != null && emiRecipeManager.getRecipes() != null && !emiRecipeManager.getRecipes().isEmpty()) {
                for (dev.emi.emi.api.recipe.EmiRecipe emiRecipe : emiRecipeManager.getRecipes()) {
                    if (emiRecipe != null && emiRecipe.getOutputs() != null) {
                        for (dev.emi.emi.api.stack.EmiStack es : emiRecipe.getOutputs()) {
                            if (es != null && !es.isEmpty()) {
                                ItemStack is = es.getItemStack();
                                if (is != null && !is.isEmpty()) {
                                    if (!is.hasTag() && es.getNbt() != null) {
                                        is = is.copy();
                                        is.setTag(es.getNbt().copy());
                                    }
                                    collector.accept(is);
                                }
                            }
                        }
                    }
                }
                return stacks;
            }
        } catch (Throwable ignored) {}

        // 2. Fallback: Minecraft Level RecipeManager
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            try {
                var recipeManager = mc.level.getRecipeManager();
                if (recipeManager != null) {
                    for (Recipe<?> r : recipeManager.getRecipes()) {
                        try {
                            ItemStack res = r.getResultItem(mc.level.registryAccess());
                            if (res != null && !res.isEmpty()) {
                                collector.accept(res);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }

        return stacks;
    }

    public static void extractRecipeOutputs(Object recipe, List<ItemStack> outputStacks) {
        if (recipe == null) return;
        if (recipe instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe) {
            try {
                if (emiRecipe.getOutputs() != null) {
                    for (dev.emi.emi.api.stack.EmiStack es : emiRecipe.getOutputs()) {
                        extractItemStacks(es, outputStacks);
                    }
                }
            } catch (Throwable ignored) {}
            return;
        }
        if (recipe.getClass().getName().contains("GTRecipe")) {
            try {
                Field outputsField = recipe.getClass().getField("outputs");
                Object outs = outputsField.get(recipe);
                if (outs != null) {
                    extractItemStacks(outs, outputStacks);
                }
            } catch (Throwable ignored) {
                try {
                    Method outputsMethod = recipe.getClass().getMethod("outputs");
                    Object outs = outputsMethod.invoke(recipe);
                    if (outs != null) {
                        extractItemStacks(outs, outputStacks);
                    }
                } catch (Throwable ignored2) {}
            }
            return;
        }
        if (recipe instanceof net.minecraft.world.item.crafting.Recipe<?> r) {
            Minecraft mc = Minecraft.getInstance();
            try {
                ItemStack res = mc != null && mc.level != null ? r.getResultItem(mc.level.registryAccess()) : r.getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
                if (res != null && !res.isEmpty()) {
                    outputStacks.add(res);
                }
            } catch (Throwable ignored) {
                try {
                    ItemStack res = r.getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
                    if (res != null && !res.isEmpty()) {
                        outputStacks.add(res);
                    }
                } catch (Throwable ignored2) {}
            }
            return;
        }
        // Fallback for custom recipe types
        try {
            var getOutputsM = recipe.getClass().getMethod("getOutputs");
            Object outs = getOutputsM.invoke(recipe);
            if (outs != null) {
                extractItemStacks(outs, outputStacks);
            }
        } catch (Throwable ignored) {}
        try {
            var getResultM = recipe.getClass().getMethod("getResultItem");
            Object res = getResultM.invoke(recipe);
            if (res instanceof ItemStack is && !is.isEmpty()) {
                outputStacks.add(is);
            }
        } catch (Throwable ignored) {}
    }

    public static List<MachineAddon> crawlFastRegistries() {
        long startNanos = System.nanoTime();
        List<MachineAddon> result = new ArrayList<>();

        for (com.gtceu.calcboard.compat.IModAdapter adapter : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.discoverAddons(result, Collections.emptyList());
            } catch (Throwable ignored) {}
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Crawler] Fast Track: Loaded {} registry addons in {}ms.",
                result.size(), elapsedMs
        );
        return result;
    }

    public static boolean isItemDisabledOrHidden(Item item, Set<Item> activeRecipeItems) {
        if (item == null || item == net.minecraft.world.item.Items.AIR) return true;

        // 1. Check if item has any active crafting recipes
        if (activeRecipeItems != null && !activeRecipeItems.isEmpty()) {
            if (!activeRecipeItems.contains(item)) {
                return true; // No active recipe producing this item
            }
        }

        // 2. Check common hidden from recipe viewer tags (c:hidden_from_recipe_viewers, forge:hidden_from_recipe_viewers, etc.)
        try {
            var itemReg = net.minecraft.core.registries.Registries.ITEM;
            var holder = ForgeRegistries.ITEMS.getHolder(item);
            if (holder.isPresent()) {
                var h = holder.get();
                var hiddenTag1 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("c:hidden_from_recipe_viewers"));
                var hiddenTag2 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("forge:hidden_from_recipe_viewers"));
                var hiddenTag3 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("c:hidden_from_recipe_viewer"));
                var hiddenTag4 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("forge:hidden_from_recipe_viewer"));
                var hiddenTag5 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("emi:hidden_from_recipe_viewers"));
                var hiddenTag6 = net.minecraft.tags.TagKey.create(itemReg, ResourceLocation.tryParse("emi:hidden_from_recipe_viewer"));
                if (h.containsTag(hiddenTag1) || h.containsTag(hiddenTag2) || h.containsTag(hiddenTag3) || h.containsTag(hiddenTag4) || h.containsTag(hiddenTag5) || h.containsTag(hiddenTag6)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static List<MachineAddon> crawlExhaustiveRecipesParallel(java.util.function.DoubleConsumer progressCallback) {
        long startNanos = System.nanoTime();
        List<MachineAddon> extraAddons = Collections.synchronizedList(new ArrayList<>());
        try {
            if (progressCallback != null) progressCallback.accept(0.1);
            List<ItemStack> activeStacks = collectAllActiveItemStacks();
            if (progressCallback != null) progressCallback.accept(0.5);
            for (com.gtceu.calcboard.compat.IModAdapter adapter : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
                try {
                    adapter.discoverAddons(extraAddons, activeStacks);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        } finally {
            if (progressCallback != null) {
                progressCallback.accept(1.0);
            }
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Crawler] Exhaustive Track: Completed scan in {}ms, total {} addons.",
                elapsedMs, extraAddons.size()
        );
        return extraAddons;
    }

    public static List<MachineAddon> crawlDynamicAddons() {
        return crawlFastRegistries();
    }

    public static List<MachineAddon> crawlAllAddons() {
        return crawlFastRegistries();
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
                java.lang.reflect.Method mGetInner = obj.getClass().getMethod("getInner");
                Object inner = mGetInner.invoke(obj);
                if (inner != null && inner != obj) extractItemStacks(inner, outputStacks);
            } catch (Throwable ignored) {}
            try {
                java.lang.reflect.Method mGetItems = obj.getClass().getMethod("getItems");
                Object items = mGetItems.invoke(obj);
                if (items != null && items != obj) extractItemStacks(items, outputStacks);
            } catch (Throwable ignored) {}
        }
        if (clName.contains("Content")) {
            try {
                java.lang.reflect.Method mGetContent = obj.getClass().getMethod("getContent");
                Object content = mGetContent.invoke(obj);
                if (content != null && content != obj) extractItemStacks(content, outputStacks);
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> cl = obj.getClass();
            for (java.lang.reflect.Field f : cl.getFields()) {
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
            for (java.lang.reflect.Method m : cl.getMethods()) {
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

    public static MachineAddon parseTurbineRotor(ItemStack stack, ResourceLocation id) {
        return TurbineRotorHelper.parseTurbineRotor(stack, id);
    }

    public static MachineAddon parseThermalAugment(ItemStack stack, ResourceLocation id) {
        return ThermalAugmentHelper.parseThermalAugment(stack, id);
    }

    public static MachineAddon parseThermalAugmentTag(net.minecraft.nbt.CompoundTag rootTag, String name, ResourceLocation id) {
        return ThermalAugmentHelper.parseThermalAugmentTag(rootTag, name, id);
    }

    public static MachineAddon parseCoilBlock(ItemStack stack, ResourceLocation id) {
        return CoilHelper.parseCoilBlock(stack, id);
    }
}
