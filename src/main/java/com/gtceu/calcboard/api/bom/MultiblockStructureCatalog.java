package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.api.event.CatalogLifecycleEvent;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog indexer for GTCEu Multiblock Structure blueprints.
 * Deductively extracts 3D shape blocks from GTCEu's MultiblockMachineDefinition registry
 * and falls back to EMI multiblock recipes where appropriate.
 */
public class MultiblockStructureCatalog {

    private static final Map<ResourceLocation, MultiblockStructureDef> STRUCTURES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<MultiblockStructureDef>> STRUCTURE_VARIANTS = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static volatile boolean initializing = false;

    public static void clear() {
        STRUCTURES.clear();
        STRUCTURE_VARIANTS.clear();
        initialized = false;
        initializing = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void initialize() {
        if (initialized) return;
        synchronized (MultiblockStructureCatalog.class) {
            if (initialized || initializing) return;
            initializing = true;
            try {
                scanAdapterMultiblockStructures();
                scanEmiMultiblockInfo();
            } catch (Throwable ignored) {
            } finally {
                initialized = true;
                initializing = false;
                postMultiblocksReadyEvent();
            }
        }
    }

    private static void scanAdapterMultiblockStructures() {
        for (com.gtceu.calcboard.compat.IModAdapter adapter : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.scanMultiblockStructures();
            } catch (Throwable t) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn(
                        "[GTCalcBoard] [MultiblockStructureCatalog] Adapter '{}' scanMultiblockStructures failed: {}",
                        adapter.getModId(), t.getMessage()
                );
            }
        }
    }

    private static void scanEmiMultiblockInfo() {
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) return;
        if (com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) {
            scanEmiMultiblockRecipes();
        } else {
            com.gtceu.calcboard.integration.emi.EmiLifecycleHook.runWhenEmiReady(MultiblockStructureCatalog::scanEmiMultiblockRecipes);
        }
    }

    private static void postMultiblocksReadyEvent() {
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.gtceu.calcboard.api.event.CatalogLifecycleEvent.MultiblocksReady(STRUCTURES.size())
            );
        } catch (Throwable ignored) {}
    }

    public static java.util.concurrent.CompletableFuture<Void> initializeAsync() {
        if (initialized || initializing) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return java.util.concurrent.CompletableFuture.runAsync(MultiblockStructureCatalog::initialize, net.minecraft.Util.backgroundExecutor());
    }

    public static void registerManualStructure(MultiblockStructureDef def) {
        if (def != null && def.controllerId() != null) {
            STRUCTURES.put(def.controllerId(), def);
            STRUCTURE_VARIANTS.computeIfAbsent(def.controllerId(), k -> new ArrayList<>()).add(def);
        }
    }

    public static void registerStructure(MultiblockStructureDef canonicalDef, List<MultiblockStructureDef> variants) {
        if (canonicalDef == null || canonicalDef.controllerId() == null) return;
        ResourceLocation controllerId = canonicalDef.controllerId();
        STRUCTURES.put(controllerId, canonicalDef);
        if (variants != null && !variants.isEmpty()) {
            STRUCTURE_VARIANTS.put(controllerId, variants);
        }
        ResourceLocation infoAlias = ResourceLocation.tryParse(controllerId.getNamespace() + ":multiblock_info/" + controllerId.getPath());
        if (infoAlias != null) {
            STRUCTURES.put(infoAlias, canonicalDef);
            if (variants != null && !variants.isEmpty()) {
                STRUCTURE_VARIANTS.put(infoAlias, variants);
            }
        }
    }

    public static MultiblockStructureDef getStructureCached(ResourceLocation id) {
        if (id == null) return null;
        MultiblockStructureDef def = STRUCTURES.get(id);
        if (def != null) return def;

        String path = id.getPath();
        if (path.contains("/")) {
            String machineName = path.substring(path.lastIndexOf('/') + 1);
            ResourceLocation stripped = ResourceLocation.tryParse(id.getNamespace() + ":" + machineName);
            if (stripped != null) {
                return STRUCTURES.get(stripped);
            }
        }
        return null;
    }

    public static MultiblockStructureDef getStructure(ResourceLocation id) {
        if (!initialized && !initializing) {
            initializeAsync();
        }
        if (id == null) return null;
        MultiblockStructureDef def = getStructureCached(id);
        if (def != null) return def;

        return scanStructureFromAdapters(id);
    }

    private static MultiblockStructureDef scanStructureFromAdapters(ResourceLocation id) {
        for (com.gtceu.calcboard.compat.IModAdapter adapter : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                MultiblockStructureDef scanned = adapter.scanMultiblockStructure(id);
                if (scanned != null) return scanned;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Finds the best-fitting multiblock shape variant that satisfies the required port counts.
     */
    public static MultiblockStructureDef getMatchingStructure(ResourceLocation id, int requiredFluidOut, int requiredItemOut, int requiredFluidIn, int requiredItemIn) {
        if (!initialized && !initializing) {
            initializeAsync();
        }
        if (id == null) return null;
        List<MultiblockStructureDef> variants = STRUCTURE_VARIANTS.get(id);
        if (variants == null || variants.isEmpty()) {
            String path = id.getPath();
            if (path.contains("/")) {
                String machineName = path.substring(path.lastIndexOf('/') + 1);
                ResourceLocation stripped = ResourceLocation.tryParse(id.getNamespace() + ":" + machineName);
                if (stripped != null) {
                    variants = STRUCTURE_VARIANTS.get(stripped);
                }
            }
        }

        if (variants == null || variants.isEmpty()) {
            return getStructure(id);
        }

        MultiblockStructureDef bestFit = null;
        for (MultiblockStructureDef def : variants) {
            if (def.outputHatchSlotCount() >= requiredFluidOut
                    && def.outputBusSlotCount() >= requiredItemOut
                    && def.inputHatchSlotCount() >= requiredFluidIn
                    && def.inputBusSlotCount() >= requiredItemIn) {
                bestFit = def;
                break;
            }
        }

        if (bestFit == null) {
            bestFit = variants.get(variants.size() - 1);
        }

        return bestFit;
    }

    private static void scanEmiMultiblockRecipes() {
        if (!ModCompatHelper.isEmiLoaded()) return;
        try {
            EmiStructureScanner.scan();
        } catch (Throwable ignored) {}
    }

    private static final Map<net.minecraft.world.item.Item, String> ITEM_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static class EmiStructureScanner {
        private static void scan() {
            var recipeManager = dev.emi.emi.api.EmiApi.getRecipeManager();
            if (recipeManager == null || recipeManager.getCategories() == null) return;
            scanMultiblockCategories(recipeManager);
        }

        private static void scanMultiblockCategories(dev.emi.emi.api.recipe.EmiRecipeManager recipeManager) {
            for (dev.emi.emi.api.recipe.EmiRecipeCategory cat : recipeManager.getCategories()) {
                if (cat == null || cat.getId() == null) continue;
                if (isMultiblockCategory(cat.getId())) {
                    processCategoryRecipes(recipeManager, cat);
                }
            }
        }

        private static boolean isMultiblockCategory(ResourceLocation catId) {
            String path = catId.getPath();
            return path.contains("multiblock_info") || path.contains("multiblock");
        }

        private static void processCategoryRecipes(
                dev.emi.emi.api.recipe.EmiRecipeManager recipeManager,
                dev.emi.emi.api.recipe.EmiRecipeCategory cat
        ) {
            List<dev.emi.emi.api.recipe.EmiRecipe> recipes = recipeManager.getRecipes(cat);
            if (recipes == null) return;
            for (dev.emi.emi.api.recipe.EmiRecipe recipe : recipes) {
                if (recipe != null) {
                    parseEmiMultiblockRecipe(recipe);
                }
            }
        }

        private static void parseEmiMultiblockRecipe(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            Set<ResourceLocation> aliasIds = new HashSet<>();
            ControllerInfo controllerInfo = resolveControllerInfo(recipe, aliasIds);
            if (controllerInfo == null || controllerInfo.id == null) return;

            MultiblockStructureDef existing = STRUCTURES.get(controllerInfo.id);
            int existingBlockCount = calculateStructureBlockCount(existing);

            StructureSlotCounts slotCounts = new StructureSlotCounts();
            List<MultiblockStructurePart> parts = extractStructureParts(recipe, controllerInfo, slotCounts);
            int newBlockCount = calculatePartsBlockCount(parts);

            if (existing != null && existingBlockCount >= newBlockCount) return;

            MultiblockStructureDef def = createMultiblockDef(controllerInfo, parts, slotCounts);
            for (ResourceLocation id : aliasIds) {
                STRUCTURES.put(id, def);
            }
        }

        private record ControllerInfo(ResourceLocation id, String name) {}

        private static class StructureSlotCounts {
            int coilSlots = 0;
            int energyHatchSlots = 0;
            int inputBusSlots = 0;
            int outputBusSlots = 0;
            int inputHatchSlots = 0;
            int outputHatchSlots = 0;
            int maintenanceSlots = 0;
        }

        private static ControllerInfo resolveControllerInfo(dev.emi.emi.api.recipe.EmiRecipe recipe, Set<ResourceLocation> aliasIds) {
            ResourceLocation controllerId = extractControllerFromOutput(recipe, aliasIds);
            String controllerName = extractControllerNameFromOutput(recipe);

            if (recipe.getId() != null) {
                aliasIds.add(recipe.getId());
                String rPath = recipe.getId().getPath();
                if (rPath.contains("/")) {
                    String machineName = rPath.substring(rPath.lastIndexOf('/') + 1);
                    ResourceLocation strippedId = ResourceLocation.tryParse(recipe.getId().getNamespace() + ":" + machineName);
                    if (strippedId != null) {
                        aliasIds.add(strippedId);
                        if (controllerId == null) {
                            controllerId = strippedId;
                            controllerName = formatMachineName(machineName);
                        }
                    }
                } else if (controllerId == null) {
                    controllerId = recipe.getId();
                    controllerName = formatMachineName(rPath);
                }
            }
            if (controllerId == null) return null;
            return new ControllerInfo(controllerId, controllerName);
        }

        private static ResourceLocation extractControllerFromOutput(dev.emi.emi.api.recipe.EmiRecipe recipe, Set<ResourceLocation> aliasIds) {
            if (recipe.getOutputs().isEmpty()) return null;
            dev.emi.emi.api.stack.EmiStack out = recipe.getOutputs().get(0);
            if (out == null || out.getItemStack() == null) return null;
            ItemStack stack = out.getItemStack();
            if (stack.isEmpty()) return null;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) aliasIds.add(id);
            return id;
        }

        private static String extractControllerNameFromOutput(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            if (recipe.getOutputs().isEmpty()) return "";
            dev.emi.emi.api.stack.EmiStack out = recipe.getOutputs().get(0);
            if (out == null || out.getItemStack() == null) return "";
            ItemStack stack = out.getItemStack();
            if (stack.isEmpty()) return "";
            return ITEM_NAME_CACHE.computeIfAbsent(stack.getItem(), itm -> itm.getDescription().getString());
        }

        private static int calculateStructureBlockCount(MultiblockStructureDef def) {
            if (def == null) return 0;
            int count = 0;
            for (MultiblockStructurePart p : def.parts()) {
                if (p != null) count += p.amount();
            }
            return count;
        }

        private static int calculatePartsBlockCount(List<MultiblockStructurePart> parts) {
            int count = 0;
            for (MultiblockStructurePart p : parts) {
                if (p != null) count += p.amount();
            }
            return count;
        }

        private static List<MultiblockStructurePart> extractStructureParts(
                dev.emi.emi.api.recipe.EmiRecipe recipe,
                ControllerInfo controllerInfo,
                StructureSlotCounts slotCounts
        ) {
            List<MultiblockStructurePart> parts = new ArrayList<>();
            for (dev.emi.emi.api.stack.EmiIngredient ing : recipe.getInputs()) {
                if (ing == null || ing.getEmiStacks().isEmpty()) continue;
                processEmiIngredientPart(ing, controllerInfo.id, slotCounts, parts);
            }
            String ctrlDisplayName = (controllerInfo.name != null && !controllerInfo.name.isBlank())
                    ? controllerInfo.name
                    : controllerInfo.id.getPath();
            parts.add(0, new MultiblockStructurePart(controllerInfo.id, ctrlDisplayName, 1, PartCategory.CONTROLLER));
            return parts;
        }

        private static void processEmiIngredientPart(
                dev.emi.emi.api.stack.EmiIngredient ing,
                ResourceLocation controllerId,
                StructureSlotCounts slotCounts,
                List<MultiblockStructurePart> parts
        ) {
            for (dev.emi.emi.api.stack.EmiStack stack : ing.getEmiStacks()) {
                if (stack == null || stack.getItemStack() == null) continue;
                ItemStack is = stack.getItemStack();
                if (is.isEmpty()) continue;
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(is.getItem());
                if (itemId == null) continue;

                int amount = Math.max(1, (int) stack.getAmount());
                String name = ITEM_NAME_CACHE.computeIfAbsent(is.getItem(), itm -> is.getHoverName().getString());
                PartCategory category = classifyPart(itemId);
                accumulateSlotCounts(itemId.getPath().toLowerCase(Locale.ROOT), category, amount, slotCounts);

                if (!itemId.equals(controllerId)) {
                    parts.add(new MultiblockStructurePart(itemId, name, amount, category));
                }
                break;
            }
        }

        private static void accumulateSlotCounts(String path, PartCategory category, int amount, StructureSlotCounts slots) {
            if (category == PartCategory.COIL) {
                slots.coilSlots = Math.max(slots.coilSlots, amount);
            } else if ((path.contains("energy") && path.contains("hatch")) || (path.contains("power") && path.contains("hatch")) || path.contains("laser_target") || path.contains("laser_source")) {
                slots.energyHatchSlots = Math.max(slots.energyHatchSlots, amount);
            } else if (path.contains("input_bus") || path.contains("import_bus")) {
                slots.inputBusSlots = Math.max(slots.inputBusSlots, amount);
            } else if (path.contains("output_bus") || path.contains("export_bus")) {
                slots.outputBusSlots = Math.max(slots.outputBusSlots, amount);
            } else if (path.contains("input_hatch") || path.contains("fluid_import")) {
                slots.inputHatchSlots = Math.max(slots.inputHatchSlots, amount);
            } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
                slots.outputHatchSlots = Math.max(slots.outputHatchSlots, amount);
            } else if (path.contains("maintenance")) {
                slots.maintenanceSlots = Math.max(slots.maintenanceSlots, amount);
            }
        }

        private static MultiblockStructureDef createMultiblockDef(
                ControllerInfo controllerInfo,
                List<MultiblockStructurePart> parts,
                StructureSlotCounts slots
        ) {
            Set<ResourceLocation> candidateBlocks = new HashSet<>();
            boolean isSteam = controllerInfo.id.getPath().startsWith("steam_") || controllerInfo.id.getPath().contains("_steam_");

            for (MultiblockStructurePart p : parts) {
                if (p == null || p.itemId() == null) continue;
                candidateBlocks.add(p.itemId());
                if (p.itemId().getPath().toLowerCase(Locale.ROOT).contains("steam")) {
                    isSteam = true;
                }
            }

            Set<String> allowedAbilities = determineAllowedAbilities(slots, isSteam);
            return new MultiblockStructureDef(
                    controllerInfo.id,
                    controllerInfo.name,
                    parts,
                    slots.coilSlots,
                    slots.energyHatchSlots,
                    slots.inputBusSlots,
                    slots.outputBusSlots,
                    slots.inputHatchSlots,
                    slots.outputHatchSlots,
                    slots.maintenanceSlots,
                    Collections.unmodifiableSet(allowedAbilities),
                    Collections.unmodifiableSet(candidateBlocks)
            );
        }

        private static Set<String> determineAllowedAbilities(StructureSlotCounts slots, boolean isSteam) {
            Set<String> abilities = new HashSet<>();
            if (slots.inputBusSlots > 0) abilities.add(isSteam ? "STEAM_IMPORT_ITEMS" : "IMPORT_ITEMS");
            if (slots.outputBusSlots > 0) abilities.add(isSteam ? "STEAM_EXPORT_ITEMS" : "EXPORT_ITEMS");
            if (slots.inputHatchSlots > 0) abilities.add(isSteam ? "STEAM_IMPORT_FLUIDS" : "IMPORT_FLUIDS");
            if (slots.outputHatchSlots > 0) abilities.add(isSteam ? "STEAM_EXPORT_FLUIDS" : "EXPORT_FLUIDS");
            if (slots.energyHatchSlots > 0 && !isSteam) abilities.add("INPUT_ENERGY");
            if (slots.maintenanceSlots > 0 && !isSteam) abilities.add("MAINTENANCE");
            return abilities;
        }
    }

    public static PartCategory classifyPart(ResourceLocation itemId) {
        if (itemId == null) return PartCategory.OTHER;
        return ModAdapterRegistry.classifyBOMPart(itemId);
    }

    public static String formatMachineName(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static Map<ResourceLocation, MultiblockStructureDef> getAllStructures() {
        if (!initialized || STRUCTURES.isEmpty()) {
            initialize();
        }
        return Collections.unmodifiableMap(STRUCTURES);
    }
}



