package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.event.MachineAddonRegisterEvent;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class DynamicAddonCrawler {

    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field RECIPE_TYPES_FIELD;

    static {
        ClassLoader cl = DynamicAddonCrawler.class.getClassLoader();
        Class<?> gtRegs = null;
        Field rtField = null;
        try {
            gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false, cl);
            rtField = gtRegs.getField("RECIPE_TYPES");
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        GT_REGISTRIES_CLS = gtRegs;
        RECIPE_TYPES_FIELD = rtField;
    }

    private static volatile ILevelRecipeProvider levelRecipeProvider = new ILevelRecipeProvider() {
        @Override
        public boolean isRecipeBakingComplete() {
            return true;
        }

        @Override
        public void collectClientRecipes(Consumer<ItemStack> collector) {
        }

        @Override
        public ItemStack getRecipeResultItem(Recipe<?> recipe) {
            return recipe != null ? recipe.getResultItem(RegistryAccess.EMPTY) : ItemStack.EMPTY;
        }

        @Override
        public String getSelectedLanguage() {
            return "en_us";
        }
    };

    public static void setLevelRecipeProvider(ILevelRecipeProvider provider) {
        if (provider != null) {
            levelRecipeProvider = provider;
        }
    }

    public static ILevelRecipeProvider getLevelRecipeProvider() {
        return levelRecipeProvider;
    }

    public static List<MachineAddon> getBuiltinTraits() {
        List<MachineAddon> list = new ArrayList<>();
        for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
            adapter.discoverAddons(list, Collections.emptyList());
        }

        MachineAddonRegisterEvent event = new MachineAddonRegisterEvent();
        MinecraftForge.EVENT_BUS.post(event);
        for (MachineAddon custom : event.getRegisteredAddons()) {
            if (custom != null) {
                list.add(custom);
            }
        }
        return list;
    }

    public static boolean isRecipeBakingComplete() {
        return levelRecipeProvider.isRecipeBakingComplete();
    }

    public static List<ItemStack> collectAllActiveItemStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        Set<String> seenItemKeys = new HashSet<>();
        Consumer<ItemStack> collector = is -> collectFilteredItemStack(is, stacks, seenItemKeys);

        scanEmiOutputsIfLoaded(collector);
        scanGTOutputsIfLoaded(collector);
        scanClientRecipesIfPresent(collector);

        return stacks;
    }

    private static void collectFilteredItemStack(ItemStack is, List<ItemStack> stacks, Set<String> seenItemKeys) {
        if (is == null || is.isEmpty()) return;
        Item item = is.getItem();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) return;

        String ns = key.getNamespace().toLowerCase(Locale.ROOT);
        String path = key.getPath().toLowerCase(Locale.ROOT);

        if (!isRelevantAddonNamespaceOrKeyword(ns, path)) {
            return;
        }

        String itemKey = key.toString() + (is.hasTag() ? "@" + is.getTag().toString() : "");
        if (seenItemKeys.add(itemKey)) {
            stacks.add(is);
        }
    }

    private static boolean isRelevantAddonNamespaceOrKeyword(String ns, String path) {
        return ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start")
                || ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("thermal_foundation")
                || ns.equals("thermal_innovation") || ns.equals("thermal_extra") || ns.equals("cofh_core")
                || ns.equals("systeams") || ns.equals("kubejs")
                || path.contains("rotor") || path.contains("augment")
                || path.contains("hatch") || path.contains("coil")
                || path.contains("kit") || path.contains("component")
                || path.contains("reflector");
    }

    private static void scanEmiOutputsIfLoaded(Consumer<ItemStack> collector) {
        if (ModCompatHelper.isEmiLoaded()) {
            if (com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) {
                EmiExtractor.scanEmiRecipes(collector);
            }
        }
    }

    private static void scanGTOutputsIfLoaded(Consumer<ItemStack> collector) {
        if (!ModCompatHelper.isGTLoaded() || RECIPE_TYPES_FIELD == null) return;
        try {
            Object recipeTypes = RECIPE_TYPES_FIELD.get(null);
            if (recipeTypes instanceof Iterable<?> iterable) {
                scanGTRecipeTypes(iterable, collector);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    private static void scanGTRecipeTypes(Iterable<?> iterable, Consumer<ItemStack> collector) {
        List<ItemStack> tempOutputs = new ArrayList<>();
        for (Object rt : iterable) {
            if (rt == null) continue;
            processGTRecipeType(rt, tempOutputs, collector);
        }
    }

    private static void processGTRecipeType(Object rt, List<ItemStack> tempOutputs, Consumer<ItemStack> collector) {
        try {
            Method mGetRecipes = rt.getClass().getMethod("getRecipes");
            mGetRecipes.setAccessible(true);
            Object recipes = mGetRecipes.invoke(rt);
            if (recipes instanceof Collection<?> col) {
                processGTRecipeCollection(col, tempOutputs, collector);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    private static void processGTRecipeCollection(Collection<?> col, List<ItemStack> tempOutputs, Consumer<ItemStack> collector) {
        for (Object r : col) {
            tempOutputs.clear();
            extractRecipeOutputs(r, tempOutputs);
            for (ItemStack out : tempOutputs) {
                collector.accept(out);
            }
        }
    }

    private static void scanClientRecipesIfPresent(Consumer<ItemStack> collector) {
        levelRecipeProvider.collectClientRecipes(collector);
    }

    public static void extractRecipeOutputs(Object recipe, List<ItemStack> outputStacks) {
        if (recipe == null) return;

        if (ModCompatHelper.isEmiLoaded() && EmiExtractor.extractEmiRecipe(recipe, outputStacks)) {
            return;
        }

        if (recipe.getClass().getName().contains("GTRecipe")) {
            extractGTRecipeOutputs(recipe, outputStacks);
            return;
        }

        if (recipe instanceof Recipe<?> r) {
            extractVanillaRecipeOutputs(r, outputStacks);
            return;
        }

        extractCustomRecipeOutputs(recipe, outputStacks);
    }

    private static void extractGTRecipeOutputs(Object recipe, List<ItemStack> outputStacks) {
        try {
            Field outputsField = recipe.getClass().getField("outputs");
            outputsField.setAccessible(true);
            Object outs = outputsField.get(recipe);
            if (outs != null) {
                extractItemStacks(outs, outputStacks);
                return;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        try {
            Method outputsMethod = recipe.getClass().getMethod("outputs");
            outputsMethod.setAccessible(true);
            Object outs = outputsMethod.invoke(recipe);
            if (outs != null) {
                extractItemStacks(outs, outputStacks);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    private static void extractVanillaRecipeOutputs(Recipe<?> r, List<ItemStack> outputStacks) {
        ItemStack res = levelRecipeProvider.getRecipeResultItem(r);
        if (res != null && !res.isEmpty()) {
            outputStacks.add(res);
            return;
        }
        res = r.getResultItem(RegistryAccess.EMPTY);
        if (res != null && !res.isEmpty()) {
            outputStacks.add(res);
        }
    }

    private static void extractCustomRecipeOutputs(Object recipe, List<ItemStack> outputStacks) {
        try {
            Method getOutputsM = recipe.getClass().getMethod("getOutputs");
            getOutputsM.setAccessible(true);
            Object outs = getOutputsM.invoke(recipe);
            if (outs != null) {
                extractItemStacks(outs, outputStacks);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        try {
            Method getResultM = recipe.getClass().getMethod("getResultItem");
            getResultM.setAccessible(true);
            Object res = getResultM.invoke(recipe);
            if (res instanceof ItemStack is && !is.isEmpty()) {
                outputStacks.add(is);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    public static List<MachineAddon> crawlFastRegistries() {
        long startNanos = System.nanoTime();
        List<MachineAddon> rawList = new ArrayList<>();
        List<ItemStack> activeStacks = collectAllActiveItemStacks();

        for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
            adapter.discoverAddons(rawList, activeStacks);
        }

        List<MachineAddon> result = deduplicateAddons(rawList);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Crawler] Fast Track: Loaded {} registry addons (raw: {}, active stacks: {}) in {}ms.",
                result.size(), rawList.size(), activeStacks.size(), elapsedMs
        );
        return result;
    }

    private static volatile TagKey<Item>[] HIDDEN_TAGS = null;

    @SuppressWarnings("unchecked")
    private static TagKey<Item>[] getHiddenTags() {
        if (HIDDEN_TAGS == null) {
            synchronized (DynamicAddonCrawler.class) {
                if (HIDDEN_TAGS == null) {
                    var reg = Registries.ITEM;
                    HIDDEN_TAGS = new TagKey[]{
                            TagKey.create(reg, ResourceLocation.tryParse("c:hidden_from_recipe_viewers")),
                            TagKey.create(reg, ResourceLocation.tryParse("forge:hidden_from_recipe_viewers")),
                            TagKey.create(reg, ResourceLocation.tryParse("c:hidden_from_recipe_viewer")),
                            TagKey.create(reg, ResourceLocation.tryParse("forge:hidden_from_recipe_viewer")),
                            TagKey.create(reg, ResourceLocation.tryParse("emi:hidden_from_recipe_viewers")),
                            TagKey.create(reg, ResourceLocation.tryParse("emi:hidden_from_recipe_viewer")),
                            TagKey.create(reg, ResourceLocation.tryParse("jei:hidden_from_recipe_viewers")),
                            TagKey.create(reg, ResourceLocation.tryParse("c:disabled")),
                            TagKey.create(reg, ResourceLocation.tryParse("forge:disabled")),
                            TagKey.create(reg, ResourceLocation.tryParse("c:disabled_items")),
                            TagKey.create(reg, ResourceLocation.tryParse("forge:disabled_items"))
                    };
                }
            }
        }
        return HIDDEN_TAGS;
    }

    public static boolean isItemDisabledOrHidden(Item item, Set<Item> activeRecipeItems) {
        if (item == null || item == Items.AIR) return true;

        var holder = ForgeRegistries.ITEMS.getHolder(item);
        if (holder.isPresent()) {
            var h = holder.get();
            for (TagKey<Item> tag : getHiddenTags()) {
                if (h.containsTag(tag)) return true;
            }
        }

        return false;
    }

    public static List<MachineAddon> crawlExhaustiveRecipesParallel(DoubleConsumer progressCallback) {
        long startNanos = System.nanoTime();
        List<MachineAddon> extraAddons = Collections.synchronizedList(new ArrayList<>());
        try {
            if (progressCallback != null) progressCallback.accept(0.1);
            List<ItemStack> activeStacks = collectAllActiveItemStacks();
            if (progressCallback != null) progressCallback.accept(0.5);
            for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
                adapter.discoverAddons(extraAddons, activeStacks);
            }
        } finally {
            if (progressCallback != null) {
                progressCallback.accept(1.0);
            }
        }

        List<MachineAddon> result = deduplicateAddons(extraAddons);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Crawler] Exhaustive Track: Completed scan in {}ms, total {} addons (raw: {}).",
                elapsedMs, result.size(), extraAddons.size()
        );
        return result;
    }

    public static List<MachineAddon> deduplicateAddons(List<MachineAddon> sourceList) {
        if (sourceList == null || sourceList.isEmpty()) return new ArrayList<>();
        Map<String, MachineAddon> unique = new LinkedHashMap<>();
        for (MachineAddon a : sourceList) {
            if (a == null || a.getId() == null) continue;
            unique.putIfAbsent(a.getId(), a);
        }
        return new ArrayList<>(unique.values());
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
            if (!is.isEmpty()) outputStacks.add(is);
            return;
        }
        if (obj instanceof Ingredient ing) {
            for (ItemStack is : ing.getItems()) {
                if (is != null && !is.isEmpty()) outputStacks.add(is);
            }
            return;
        }
        if (ModCompatHelper.isEmiLoaded() && EmiExtractor.extractEmiStack(obj, outputStacks)) {
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object val : map.values()) extractItemStacks(val, outputStacks);
            return;
        }
        if (obj instanceof Iterable<?> iter) {
            for (Object item : iter) extractItemStacks(item, outputStacks);
            return;
        }
        if (obj instanceof Object[] arr) {
            for (Object item : arr) extractItemStacks(item, outputStacks);
            return;
        }

        inspectKnownWrapperObjects(obj, outputStacks);
    }

    private static void inspectKnownWrapperObjects(Object obj, List<ItemStack> outputStacks) {
        String clName = obj.getClass().getName();
        if (clName.contains("SizedIngredient")) {
            invokeMethodAndExtract(obj, "getInner", outputStacks);
            invokeMethodAndExtract(obj, "getItems", outputStacks);
            return;
        }
        if (clName.contains("Content")) {
            invokeMethodAndExtract(obj, "getContent", outputStacks);
            return;
        }

        scanFieldsAndMethodsForStacks(obj, outputStacks);
    }

    private static void invokeMethodAndExtract(Object obj, String methodName, List<ItemStack> outputStacks) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object res = m.invoke(obj);
            if (res != null && res != obj) extractItemStacks(res, outputStacks);
        } catch (ReflectiveOperationException ignored) {}
    }

    private static void scanFieldsAndMethodsForStacks(Object obj, List<ItemStack> outputStacks) {
        scanFieldsForStacks(obj, outputStacks);
        scanMethodsForStacks(obj, outputStacks);
    }

    private static void scanFieldsForStacks(Object obj, List<ItemStack> outputStacks) {
        Class<?> cl = obj.getClass();
        for (Field f : cl.getFields()) {
            if (!isTargetStackFieldName(f.getName())) continue;
            try {
                Object fVal = f.get(obj);
                if (fVal != null && fVal != obj) extractItemStacks(fVal, outputStacks);
            } catch (ReflectiveOperationException ignored) {}
        }
    }

    private static boolean isTargetStackFieldName(String fn) {
        return fn.equals("content") || fn.equals("stack") || fn.equals("itemStack") || fn.equals("ingredient") || fn.equals("inner") || fn.equals("outputs");
    }

    private static void scanMethodsForStacks(Object obj, List<ItemStack> outputStacks) {
        Class<?> cl = obj.getClass();
        for (Method m : cl.getMethods()) {
            if (m.getParameterCount() != 0 || !isTargetStackMethodName(m.getName())) continue;
            try {
                Object res = m.invoke(obj);
                if (res != null && res != obj) extractItemStacks(res, outputStacks);
            } catch (ReflectiveOperationException ignored) {}
        }
    }

    private static boolean isTargetStackMethodName(String mn) {
        return mn.equals("getContent") || mn.equals("getItems") || mn.equals("getMatchingStacks") || mn.equals("getInner") || mn.equals("getItemStack") || mn.equals("getOutputs") || mn.equals("getOutputsList") || mn.equals("getResults") || mn.equals("getResultItem") || mn.equals("item");
    }

    private static class EmiExtractor {
        private static void scanEmiRecipes(Consumer<ItemStack> collector) {
            var emiRecipeManager = dev.emi.emi.api.EmiApi.getRecipeManager();
            if (emiRecipeManager == null || emiRecipeManager.getRecipes() == null || emiRecipeManager.getRecipes().isEmpty()) {
                return;
            }
            for (dev.emi.emi.api.recipe.EmiRecipe emiRecipe : emiRecipeManager.getRecipes()) {
                if (emiRecipe == null || emiRecipe.getOutputs() == null) continue;
                collectEmiRecipeOutputs(emiRecipe.getOutputs(), collector);
            }
        }

        private static void collectEmiRecipeOutputs(List<dev.emi.emi.api.stack.EmiStack> outputs, Consumer<ItemStack> collector) {
            for (dev.emi.emi.api.stack.EmiStack es : outputs) {
                if (es == null || es.isEmpty()) continue;
                ItemStack is = es.getItemStack();
                if (is == null || is.isEmpty()) continue;
                if (!is.hasTag() && es.getNbt() != null) {
                    is = is.copy();
                    is.setTag(es.getNbt().copy());
                }
                collector.accept(is);
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



