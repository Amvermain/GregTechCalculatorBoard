package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.event.MachineAddonRegisterEvent;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

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
        try {
            com.gtceu.calcboard.api.event.MachineAddonRegisterEvent event = new com.gtceu.calcboard.api.event.MachineAddonRegisterEvent();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            for (MachineAddon custom : event.getRegisteredAddons()) {
                if (custom != null) {
                    list.add(custom);
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public static boolean isRecipeBakingComplete() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            try {
                return com.gtceu.calcboard.client.ClientLevelHelper.isRecipeBakingComplete();
            } catch (Throwable ignored) {}
        }
        return true;
    }

    public static List<ItemStack> collectAllActiveItemStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        Set<String> seenItemKeys = new HashSet<>();
        java.util.function.Consumer<ItemStack> collector = is -> {
            if (is == null || is.isEmpty()) return;
            Item item = is.getItem();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) return;
            String ns = key.getNamespace().toLowerCase(java.util.Locale.ROOT);
            String path = key.getPath().toLowerCase(java.util.Locale.ROOT);
            // Fast filter: only inspect items from relevant namespaces or addon keywords
            if (!ns.equals("gtceu") && !ns.equals("start_core") && !ns.equals("gtceu_start")
                    && !ns.equals("thermal") && !ns.equals("thermal_expansion") && !ns.equals("thermal_foundation")
                    && !ns.equals("thermal_innovation") && !ns.equals("thermal_extra") && !ns.equals("cofh_core")
                    && !ns.equals("systeams") && !ns.equals("kubejs")
                    && !path.contains("rotor") && !path.contains("augment")
                    && !path.contains("hatch") && !path.contains("coil")
                    && !path.contains("kit") && !path.contains("component")
                    && !path.contains("reflector")) {
                return;
            }
            String itemKey = key.toString() + (is.hasTag() ? "@" + is.getTag().toString() : "");
            if (seenItemKeys.add(itemKey)) {
                stacks.add(is);
            }
        };

        // 1. If EMI is loaded & baked, scan EMI recipe outputs
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            try {
                if (com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) {
                    EmiExtractor.scanEmiRecipes(collector);
                }
            } catch (Throwable ignored) {}
        }

        // 2. GTCEu Modern Machine Recipes (Assembler, Formers, etc.)
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isGTLoaded()) {
            try {
                Class<?> regClass = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                Object recipeTypes = regClass.getField("RECIPE_TYPES").get(null);
                if (recipeTypes instanceof Iterable<?> iterable) {
                    List<ItemStack> tempOutputs = new ArrayList<>();
                    for (Object rt : iterable) {
                        if (rt != null) {
                            try {
                                Method mGetRecipes = rt.getClass().getMethod("getRecipes");
                                Object recipes = mGetRecipes.invoke(rt);
                                if (recipes instanceof Collection<?> col) {
                                    for (Object r : col) {
                                        tempOutputs.clear();
                                        extractRecipeOutputs(r, tempOutputs);
                                        for (ItemStack out : tempOutputs) {
                                            collector.accept(out);
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Fallback: Minecraft Level RecipeManager
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            try {
                com.gtceu.calcboard.client.ClientLevelHelper.collectClientRecipes(collector);
            } catch (Throwable ignored) {}
        }

        return stacks;
    }

    public static void extractRecipeOutputs(Object recipe, List<ItemStack> outputStacks) {
        if (recipe == null) return;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            try {
                if (EmiExtractor.extractEmiRecipe(recipe, outputStacks)) {
                    return;
                }
            } catch (Throwable ignored) {}
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
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                try {
                    ItemStack res = com.gtceu.calcboard.client.ClientLevelHelper.getRecipeResultItem(r);
                    if (res != null && !res.isEmpty()) {
                        outputStacks.add(res);
                    }
                    return;
                } catch (Throwable ignored) {}
            }
            try {
                ItemStack res = r.getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
                if (res != null && !res.isEmpty()) {
                    outputStacks.add(res);
                }
            } catch (Throwable ignored) {}
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
        List<ItemStack> activeStacks = collectAllActiveItemStacks();

        for (com.gtceu.calcboard.compat.IModAdapter adapter : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.discoverAddons(result, activeStacks);
            } catch (Throwable ignored) {}
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Crawler] Fast Track: Loaded {} registry addons (active stacks: {}) in {}ms.",
                result.size(), activeStacks.size(), elapsedMs
        );
        return result;
    }

    private static volatile net.minecraft.tags.TagKey<Item>[] HIDDEN_TAGS = null;

    @SuppressWarnings("unchecked")
    private static net.minecraft.tags.TagKey<Item>[] getHiddenTags() {
        if (HIDDEN_TAGS == null) {
            synchronized (DynamicAddonCrawler.class) {
                if (HIDDEN_TAGS == null) {
                    try {
                        var reg = net.minecraft.core.registries.Registries.ITEM;
                        HIDDEN_TAGS = new net.minecraft.tags.TagKey[]{
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("c:hidden_from_recipe_viewers")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("forge:hidden_from_recipe_viewers")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("c:hidden_from_recipe_viewer")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("forge:hidden_from_recipe_viewer")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("emi:hidden_from_recipe_viewers")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("emi:hidden_from_recipe_viewer")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("jei:hidden_from_recipe_viewers")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("c:disabled")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("forge:disabled")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("c:disabled_items")),
                                net.minecraft.tags.TagKey.create(reg, ResourceLocation.tryParse("forge:disabled_items"))
                        };
                    } catch (Throwable t) {
                        HIDDEN_TAGS = new net.minecraft.tags.TagKey[0];
                    }
                }
            }
        }
        return HIDDEN_TAGS;
    }

    public static boolean isItemDisabledOrHidden(Item item, Set<Item> activeRecipeItems) {
        if (item == null || item == net.minecraft.world.item.Items.AIR) return true;

        // Check common hidden from recipe viewer tags (c:hidden_from_recipe_viewers, forge:hidden_from_recipe_viewers, c:disabled, etc.)
        try {
            var holder = ForgeRegistries.ITEMS.getHolder(item);
            if (holder.isPresent()) {
                var h = holder.get();
                for (var tag : getHiddenTags()) {
                    if (h.containsTag(tag)) return true;
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
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            try {
                if (EmiExtractor.extractEmiStack(obj, outputStacks)) {
                    return;
                }
            } catch (Throwable ignored) {}
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

    private static class EmiExtractor {
        private static void scanEmiRecipes(java.util.function.Consumer<ItemStack> collector) {
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
            }
        }

        private static boolean extractEmiRecipe(Object recipe, List<ItemStack> outputStacks) {
            if (recipe instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe) {
                if (emiRecipe.getOutputs() != null) {
                    for (dev.emi.emi.api.stack.EmiStack es : emiRecipe.getOutputs()) {
                        extractItemStacks(es, outputStacks);
                    }
                }
                return true;
            }
            return false;
        }

        private static boolean extractEmiStack(Object obj, List<ItemStack> outputStacks) {
            if (obj instanceof dev.emi.emi.api.stack.EmiStack emiStack) {
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
                return true;
            }
            return false;
        }
    }
}



