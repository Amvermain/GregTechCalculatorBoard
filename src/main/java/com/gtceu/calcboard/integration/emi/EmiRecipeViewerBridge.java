package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.spi.viewer.IRecipeViewerBridge;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.util.RecipeConversionHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Concrete EMI recipe viewer bridge providing headless-safe access to EMI recipe models.
 */
public final class EmiRecipeViewerBridge implements IRecipeViewerBridge {

    private static final EmiRecipeViewerBridge INSTANCE = new EmiRecipeViewerBridge();
    private static final Map<net.minecraft.world.item.Item, String> ITEM_NAME_CACHE = new ConcurrentHashMap<>();

    private EmiRecipeViewerBridge() {}

    public static EmiRecipeViewerBridge getInstance() {
        return INSTANCE;
    }

    @Override
    public String getViewerId() {
        return "emi";
    }

    @Override
    public boolean isAvailable() {
        return ModCompatHelper.isEmiLoaded();
    }

    @Override
    public boolean isRecipeBakingComplete() {
        return EmiLifecycleHook.isEmiRecipeBakingComplete();
    }

    @Override
    public void discoverMultiblockStructures(Consumer<MultiblockStructureDef> consumer) {
        if (!isAvailable() || !isRecipeBakingComplete() || consumer == null) return;
        try {
            EmiRecipeManager recipeManager = EmiApi.getRecipeManager();
            if (recipeManager == null || recipeManager.getCategories() == null) return;

            for (EmiRecipeCategory cat : recipeManager.getCategories()) {
                scanCategoryMultiblockStructures(cat, recipeManager, consumer);
            }
        } catch (Throwable ignored) {}
    }

    private void scanCategoryMultiblockStructures(EmiRecipeCategory cat, EmiRecipeManager recipeManager, Consumer<MultiblockStructureDef> consumer) {
        if (cat == null || cat.getId() == null) return;
        String path = cat.getId().getPath().toLowerCase(Locale.ROOT);
        if (!path.contains("multiblock_info") && !path.contains("multiblock")) return;

        List<EmiRecipe> recipes = recipeManager.getRecipes(cat);
        if (recipes == null) return;
        for (EmiRecipe recipe : recipes) {
            if (recipe == null) continue;
            MultiblockStructureDef def = parseEmiMultiblockRecipe(recipe);
            if (def != null) {
                consumer.accept(def);
            }
        }
    }

    private MultiblockStructureDef parseEmiMultiblockRecipe(EmiRecipe recipe) {
        Set<ResourceLocation> aliasIds = new HashSet<>();
        ControllerInfo controllerInfo = resolveControllerInfo(recipe, aliasIds);
        if (controllerInfo == null || controllerInfo.id == null) return null;

        MultiblockStructureCatalog.StructureSlotCounts slotCounts = new MultiblockStructureCatalog.StructureSlotCounts();
        List<MultiblockStructurePart> parts = extractStructureParts(recipe, controllerInfo, slotCounts);

        Set<ResourceLocation> candidateBlocks = new HashSet<>();
        boolean isSteam = isSteamController(controllerInfo.id);

        for (MultiblockStructurePart p : parts) {
            if (p == null || p.itemId() == null) continue;
            candidateBlocks.add(p.itemId());
            if (isSteamStructurePart(p.itemId())) {
                isSteam = true;
            }
        }

        boolean isCoilCapable = com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilMultiblock(controllerInfo.id);
        if (!isCoilCapable) {
            slotCounts.coilSlots = 0;
        }

        List<MultiblockStructurePart> resolvedParts = sanitizeEmiParts(parts, isCoilCapable);
        Set<String> allowedAbilities = determineAllowedAbilities(slotCounts, isSteam, isCoilCapable);

        String name = (controllerInfo.name != null && !controllerInfo.name.isBlank())
                ? controllerInfo.name
                : RecipeConversionHelper.formatName(controllerInfo.id.getPath());

        return new MultiblockStructureDef(
                controllerInfo.id,
                name,
                resolvedParts,
                slotCounts.coilSlots,
                slotCounts.energyHatchSlots,
                slotCounts.inputBusSlots,
                slotCounts.outputBusSlots,
                slotCounts.inputHatchSlots,
                slotCounts.outputHatchSlots,
                slotCounts.maintenanceSlots,
                java.util.Collections.unmodifiableSet(allowedAbilities),
                java.util.Collections.unmodifiableSet(candidateBlocks)
        );
    }

    private static boolean isSteamController(ResourceLocation controllerId) {
        if (controllerId == null) return false;
        return com.gtceu.calcboard.api.catalog.MultiblockDetector.isSteamMultiblock(controllerId)
                || controllerId.getPath().startsWith("steam_");
    }

    private static boolean isSteamStructurePart(ResourceLocation itemId) {
        if (itemId == null) return false;
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        return path.startsWith("steam_import_")
                || path.startsWith("steam_export_")
                || path.startsWith("steam_input_")
                || path.startsWith("steam_output_")
                || path.equals("steam_bus")
                || path.equals("steam_hatch");
    }

    private static List<MultiblockStructurePart> sanitizeEmiParts(List<MultiblockStructurePart> parts, boolean isCoilCapable) {
        if (isCoilCapable || parts == null) return parts;
        List<MultiblockStructurePart> sanitized = new ArrayList<>(parts.size());
        for (MultiblockStructurePart part : parts) {
            if (part != null && part.category() == PartCategory.COIL) {
                sanitized.add(new MultiblockStructurePart(part.itemId(), part.displayName(), part.amount(), PartCategory.CASING));
            } else {
                sanitized.add(part);
            }
        }
        return sanitized;
    }

    private static Set<String> determineAllowedAbilities(MultiblockStructureCatalog.StructureSlotCounts slots, boolean isSteam, boolean isCoilCapable) {
        Set<String> abilities = new HashSet<>();
        if (slots.inputBusSlots > 0) abilities.add(isSteam ? "STEAM_IMPORT_ITEMS" : "IMPORT_ITEMS");
        if (slots.outputBusSlots > 0) abilities.add(isSteam ? "STEAM_EXPORT_ITEMS" : "EXPORT_ITEMS");
        if (slots.inputHatchSlots > 0) abilities.add(isSteam ? "STEAM_IMPORT_FLUIDS" : "IMPORT_FLUIDS");
        if (slots.outputHatchSlots > 0) abilities.add(isSteam ? "STEAM_EXPORT_FLUIDS" : "EXPORT_FLUIDS");
        if (slots.energyHatchSlots > 0 && !isSteam) abilities.add("INPUT_ENERGY");
        if (slots.maintenanceSlots > 0 && !isSteam) abilities.add("MAINTENANCE");
        if (slots.coilSlots > 0 && isCoilCapable) abilities.add("HEATING_COILS");
        return abilities;
    }

    private record ControllerInfo(ResourceLocation id, String name) {}

    private ControllerInfo resolveControllerInfo(EmiRecipe recipe, Set<ResourceLocation> aliasIds) {
        ResourceLocation controllerId = extractControllerFromOutput(recipe, aliasIds);
        String controllerName = extractControllerNameFromOutput(recipe);

        if (recipe.getId() == null) {
            return controllerId != null ? new ControllerInfo(controllerId, controllerName) : null;
        }

        aliasIds.add(recipe.getId());
        String rPath = recipe.getId().getPath();
        if (rPath.contains("/")) {
            String machineName = rPath.substring(rPath.lastIndexOf('/') + 1);
            ResourceLocation strippedId = ResourceLocation.tryParse(recipe.getId().getNamespace() + ":" + machineName);
            if (strippedId != null) {
                aliasIds.add(strippedId);
            }
            if (controllerId == null && strippedId != null) {
                controllerId = strippedId;
                controllerName = RecipeConversionHelper.formatName(machineName);
            }
        } else if (controllerId == null) {
            controllerId = recipe.getId();
            controllerName = RecipeConversionHelper.formatName(rPath);
        }

        if (controllerId == null) return null;
        return new ControllerInfo(controllerId, controllerName);
    }

    private ResourceLocation extractControllerFromOutput(EmiRecipe recipe, Set<ResourceLocation> aliasIds) {
        if (recipe.getOutputs().isEmpty()) return null;
        EmiStack out = recipe.getOutputs().get(0);
        if (out == null || out.getItemStack() == null) return null;
        ItemStack stack = out.getItemStack();
        if (stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) aliasIds.add(id);
        return id;
    }

    private String extractControllerNameFromOutput(EmiRecipe recipe) {
        if (recipe.getOutputs().isEmpty()) return "";
        EmiStack out = recipe.getOutputs().get(0);
        if (out == null || out.getItemStack() == null) return "";
        ItemStack stack = out.getItemStack();
        if (stack.isEmpty()) return "";
        return ITEM_NAME_CACHE.computeIfAbsent(stack.getItem(), itm -> itm.getDescription().getString());
    }

    private List<MultiblockStructurePart> extractStructureParts(
            EmiRecipe recipe,
            ControllerInfo controllerInfo,
            MultiblockStructureCatalog.StructureSlotCounts slotCounts
    ) {
        List<MultiblockStructurePart> parts = new ArrayList<>();
        for (EmiIngredient ing : recipe.getInputs()) {
            if (ing == null || ing.getEmiStacks().isEmpty()) continue;
            processEmiIngredientPart(ing, controllerInfo.id, slotCounts, parts);
        }
        String ctrlDisplayName = (controllerInfo.name != null && !controllerInfo.name.isBlank())
                ? controllerInfo.name
                : controllerInfo.id.getPath();
        parts.add(0, new MultiblockStructurePart(controllerInfo.id, ctrlDisplayName, 1, PartCategory.CONTROLLER));
        return parts;
    }

    private void processEmiIngredientPart(
            EmiIngredient ing,
            ResourceLocation controllerId,
            MultiblockStructureCatalog.StructureSlotCounts slotCounts,
            List<MultiblockStructurePart> parts
    ) {
        for (EmiStack stack : ing.getEmiStacks()) {
            if (stack == null || stack.getItemStack() == null) continue;
            ItemStack is = stack.getItemStack();
            if (is.isEmpty()) continue;
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(is.getItem());
            if (itemId == null) continue;

            int amount = Math.max(1, (int) stack.getAmount());
            String name = ITEM_NAME_CACHE.computeIfAbsent(is.getItem(), itm -> is.getHoverName().getString());
            PartCategory category = MultiblockStructureCatalog.classifyPart(itemId);
            ModAdapterRegistry.accumulateStructureSlots(itemId, category, amount, slotCounts);

            if (!itemId.equals(controllerId)) {
                parts.add(new MultiblockStructurePart(itemId, name, amount, category));
            }
            break;
        }
    }

    @Override
    public void discoverCategoryWorkstations(BiConsumer<ResourceLocation, ResourceLocation> workstationConsumer) {
        if (!isAvailable() || !isRecipeBakingComplete() || workstationConsumer == null) return;
        try {
            EmiRecipeManager recipeManager = EmiApi.getRecipeManager();
            if (recipeManager == null || recipeManager.getCategories() == null) return;

            for (EmiRecipeCategory category : recipeManager.getCategories()) {
                scanCategoryWorkstations(category, recipeManager, workstationConsumer);
            }
        } catch (Throwable ignored) {}
    }

    private void scanCategoryWorkstations(EmiRecipeCategory category, EmiRecipeManager recipeManager, BiConsumer<ResourceLocation, ResourceLocation> workstationConsumer) {
        if (category == null || category.getId() == null) return;
        List<EmiIngredient> workstations = recipeManager.getWorkstations(category);
        if (workstations == null || workstations.isEmpty()) return;

        for (EmiIngredient ingredient : workstations) {
            collectIngredientWorkstations(category.getId(), ingredient, workstationConsumer);
        }
    }

    private void collectIngredientWorkstations(ResourceLocation categoryId, EmiIngredient ingredient, BiConsumer<ResourceLocation, ResourceLocation> consumer) {
        if (ingredient == null || ingredient.getEmiStacks() == null) return;
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (stack != null && !stack.isEmpty() && stack.getId() != null) {
                consumer.accept(categoryId, stack.getId());
            }
        }
    }

    @Override
    public void discoverRecipeOutputs(Consumer<ItemStack> outputConsumer) {
        if (!isAvailable() || !isRecipeBakingComplete() || outputConsumer == null) return;
        try {
            EmiRecipeManager recipeManager = EmiApi.getRecipeManager();
            if (recipeManager == null || recipeManager.getRecipes() == null) return;

            for (EmiRecipe emiRecipe : recipeManager.getRecipes()) {
                scanEmiRecipeOutputs(emiRecipe, outputConsumer);
            }
        } catch (Throwable ignored) {}
    }

    private void scanEmiRecipeOutputs(EmiRecipe emiRecipe, Consumer<ItemStack> outputConsumer) {
        if (emiRecipe == null || emiRecipe.getOutputs() == null) return;
        for (EmiStack es : emiRecipe.getOutputs()) {
            ItemStack is = toItemStackWithNbt(es);
            if (is != null) {
                outputConsumer.accept(is);
            }
        }
    }

    private ItemStack toItemStackWithNbt(EmiStack es) {
        if (es == null || es.isEmpty()) return null;
        ItemStack is = es.getItemStack();
        if (is == null || is.isEmpty()) return null;
        if (!is.hasTag() && es.getNbt() != null) {
            is = is.copy();
            is.setTag(es.getNbt().copy());
        }
        return is;
    }

    @Override
    public boolean extractRecipeOutputs(Object recipe, List<ItemStack> outputs) {
        if (recipe instanceof EmiRecipe emiRecipe) {
            if (emiRecipe.getOutputs() == null) return true;
            for (EmiStack es : emiRecipe.getOutputs()) {
                ItemStack is = toItemStackWithNbt(es);
                if (is != null) outputs.add(is);
            }
            return true;
        }
        if (recipe instanceof EmiStack emiStack) {
            ItemStack is = toItemStackWithNbt(emiStack);
            if (is != null) outputs.add(is);
            return true;
        }
        return false;
    }

    @Override
    public void discoverMultiblockRecipes(BiConsumer<ResourceLocation, Object> multiblockRecipeConsumer) {
        if (!isAvailable() || !isRecipeBakingComplete() || multiblockRecipeConsumer == null) return;
        try {
            EmiRecipeManager emiManager = EmiApi.getRecipeManager();
            if (emiManager == null || emiManager.getCategories() == null) return;

            for (EmiRecipeCategory cat : emiManager.getCategories()) {
                scanCategoryMultiblockRecipes(cat, emiManager, multiblockRecipeConsumer);
            }
        } catch (Throwable ignored) {}
    }

    private void scanCategoryMultiblockRecipes(EmiRecipeCategory cat, EmiRecipeManager emiManager, BiConsumer<ResourceLocation, Object> consumer) {
        if (cat == null || cat.getId() == null) return;
        String path = cat.getId().getPath().toLowerCase(Locale.ROOT);
        if (!path.contains("multiblock_info") && !path.contains("multiblock")) return;

        List<EmiRecipe> mbRecipes = emiManager.getRecipes(cat);
        if (mbRecipes == null) return;
        for (EmiRecipe r : mbRecipes) {
            if (r != null) consumer.accept(cat.getId(), r);
        }
    }

    @Override
    public void discoverMultiblockControllers(Consumer<ResourceLocation> controllerConsumer) {
        if (!isAvailable() || !isRecipeBakingComplete() || controllerConsumer == null) return;
        try {
            EmiRecipeManager emiManager = EmiApi.getRecipeManager();
            if (emiManager == null || emiManager.getCategories() == null) return;

            for (EmiRecipeCategory cat : emiManager.getCategories()) {
                scanCategoryMultiblockControllers(cat, emiManager, controllerConsumer);
            }
        } catch (Throwable ignored) {}
    }

    private void scanCategoryMultiblockControllers(EmiRecipeCategory cat, EmiRecipeManager emiManager, Consumer<ResourceLocation> consumer) {
        if (cat == null || cat.getId() == null) return;
        String path = cat.getId().getPath().toLowerCase(Locale.ROOT);
        if (!path.contains("multiblock_info") && !path.contains("multiblock")) return;

        List<EmiRecipe> mbRecipes = emiManager.getRecipes(cat);
        if (mbRecipes == null) return;
        for (EmiRecipe recipe : mbRecipes) {
            ResourceLocation controllerId = extractControllerId(recipe);
            if (controllerId != null) {
                consumer.accept(controllerId);
            }
        }
    }

    private ResourceLocation extractControllerId(EmiRecipe recipe) {
        if (recipe == null) return null;
        ResourceLocation controllerId = null;
        if (recipe.getId() != null) {
            String rPath = recipe.getId().getPath();
            if (rPath.contains("/")) {
                String machineName = rPath.substring(rPath.lastIndexOf('/') + 1);
                controllerId = ResourceLocation.tryParse(recipe.getId().getNamespace() + ":" + machineName);
            } else {
                controllerId = recipe.getId();
            }
        }
        if (controllerId == null && recipe.getOutputs() != null) {
            for (var es : recipe.getOutputs()) {
                if (es != null && es.getId() != null) {
                    controllerId = es.getId();
                    break;
                }
            }
        }
        return controllerId;
    }

    @Override
    public ResourceLocation findMachineIcon(Object recipe) {
        if (recipe instanceof dev.emi.emi.api.recipe.EmiRecipe emi) {
            return EmiRecipeConverter.findMachineIcon(emi);
        }
        return null;
    }
}
