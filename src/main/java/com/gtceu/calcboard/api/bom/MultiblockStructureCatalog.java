package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.api.ModCompatHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
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
                // 1. Delegate to loaded IModAdapters to scan mod-specific 3D multiblock structures immediately
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

                // 2. Complementary scan of EMI multiblock_info recipes (only once EMI is fully ready)
                if (com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) {
                    scanEmiMultiblockRecipes();
                } else {
                    com.gtceu.calcboard.integration.emi.EmiLifecycleHook.runWhenEmiReady(MultiblockStructureCatalog::scanEmiMultiblockRecipes);
                }
            } catch (Throwable t) {
                // Headless test or pre-init environment
            } finally {
                initialized = true;
                initializing = false;
                try {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new com.gtceu.calcboard.api.event.CatalogLifecycleEvent.MultiblocksReady(STRUCTURES.size())
                    );
                } catch (Throwable ignored) {}
            }
        }
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

    public static MultiblockStructureDef getStructure(ResourceLocation id) {
        if (!initialized && !initializing) {
            initializeAsync();
        }
        if (id == null) return null;
        MultiblockStructureDef def = STRUCTURES.get(id);
        if (def != null) return def;

        // Try stripped path
        String path = id.getPath();
        if (path.contains("/")) {
            String machineName = path.substring(path.lastIndexOf('/') + 1);
            ResourceLocation stripped = ResourceLocation.tryParse(id.getNamespace() + ":" + machineName);
            if (stripped != null) {
                MultiblockStructureDef sDef = STRUCTURES.get(stripped);
                if (sDef != null) return sDef;
            }
        }

        // On-demand lazy scan from loaded IModAdapters
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
        try {
            var recipeManager = EmiApi.getRecipeManager();
            if (recipeManager != null && recipeManager.getCategories() != null) {
                for (EmiRecipeCategory cat : recipeManager.getCategories()) {
                    if (cat == null || cat.getId() == null) continue;
                    ResourceLocation catId = cat.getId();
                    if (catId.getPath().contains("multiblock_info") || catId.getPath().contains("multiblock")) {
                        List<EmiRecipe> recipes = recipeManager.getRecipes(cat);
                        if (recipes != null) {
                            for (EmiRecipe recipe : recipes) {
                                if (recipe != null) {
                                    parseEmiMultiblockRecipe(recipe);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static final Map<net.minecraft.world.item.Item, String> ITEM_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static void parseEmiMultiblockRecipe(EmiRecipe recipe) {
        ResourceLocation controllerId = null;
        String controllerName = "";
        Set<ResourceLocation> aliasIds = new HashSet<>();

        if (!recipe.getOutputs().isEmpty()) {
            EmiStack out = recipe.getOutputs().get(0);
            if (out != null && out.getItemStack() != null) {
                ItemStack stack = out.getItemStack();
                if (!stack.isEmpty()) {
                    controllerId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    controllerName = ITEM_NAME_CACHE.computeIfAbsent(stack.getItem(), itm -> itm.getDescription().getString());
                    if (controllerId != null) aliasIds.add(controllerId);
                }
            }
        }

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

        if (controllerId == null) {
            return;
        }

        MultiblockStructureDef existing = STRUCTURES.get(controllerId);
        int existingBlockCount = 0;
        if (existing != null) {
            for (MultiblockStructurePart p : existing.parts()) {
                if (p != null) existingBlockCount += p.amount();
            }
        }

        List<MultiblockStructurePart> parts = new ArrayList<>();
        int coilSlots = 0;
        int energyHatchSlots = 0;
        int inputBusSlots = 0;
        int outputBusSlots = 0;
        int inputHatchSlots = 0;
        int outputHatchSlots = 0;
        int maintenanceSlots = 0;

        for (EmiIngredient ing : recipe.getInputs()) {
            if (ing == null || ing.getEmiStacks().isEmpty()) continue;
            for (EmiStack stack : ing.getEmiStacks()) {
                if (stack == null || stack.getItemStack() == null) continue;
                ItemStack is = stack.getItemStack();
                if (!is.isEmpty()) {
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(is.getItem());
                    if (itemId != null) {
                        int amount = (int) stack.getAmount();
                        if (amount <= 0) amount = 1;
                        String name = ITEM_NAME_CACHE.computeIfAbsent(is.getItem(), itm -> is.getHoverName().getString());
                        PartCategory category = classifyPart(itemId);

                        String path = itemId.getPath().toLowerCase(Locale.ROOT);
                        if (category == PartCategory.COIL) {
                            coilSlots = Math.max(coilSlots, amount);
                        } else if ((path.contains("energy") && path.contains("hatch")) || (path.contains("power") && path.contains("hatch")) || path.contains("laser_target") || path.contains("laser_source")) {
                            energyHatchSlots = Math.max(energyHatchSlots, amount);
                        } else if (path.contains("input_bus") || path.contains("import_bus")) {
                            inputBusSlots = Math.max(inputBusSlots, amount);
                        } else if (path.contains("output_bus") || path.contains("export_bus")) {
                            outputBusSlots = Math.max(outputBusSlots, amount);
                        } else if (path.contains("input_hatch") || path.contains("fluid_import")) {
                            inputHatchSlots = Math.max(inputHatchSlots, amount);
                        } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
                            outputHatchSlots = Math.max(outputHatchSlots, amount);
                        } else if (path.contains("maintenance")) {
                            maintenanceSlots = Math.max(maintenanceSlots, amount);
                        }

                        if (!itemId.equals(controllerId)) {
                            parts.add(new MultiblockStructurePart(itemId, name, amount, category));
                        }
                        break;
                    }
                }
            }
        }

        parts.add(0, new MultiblockStructurePart(
            controllerId,
            controllerName != null && !controllerName.isBlank() ? controllerName : controllerId.getPath(),
            1,
            PartCategory.CONTROLLER
        ));

        int newBlockCount = 0;
        for (MultiblockStructurePart p : parts) {
            if (p != null) newBlockCount += p.amount();
        }

        if (existing != null && existingBlockCount >= newBlockCount) {
            return;
        }

        MultiblockStructureDef def = new MultiblockStructureDef(
            controllerId,
            controllerName,
            parts,
            coilSlots,
            energyHatchSlots,
            inputBusSlots,
            outputBusSlots,
            inputHatchSlots,
            outputHatchSlots,
            maintenanceSlots
        );

        for (ResourceLocation id : aliasIds) {
            STRUCTURES.put(id, def);
        }
    }

    public static PartCategory classifyPart(ResourceLocation itemId) {
        if (itemId == null) return PartCategory.OTHER;
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("coil") || path.contains("magnet")) {
            return PartCategory.COIL;
        } else if (path.contains("hatch") || path.contains("bus") || path.contains("target") || path.contains("source") || path.contains("import") || path.contains("export") || path.contains("augment") || path.contains("upgrade")) {
            return PartCategory.HATCH_BUS;
        } else if (path.contains("casing") || path.contains("pipe") || path.contains("glass") || path.contains("wall") || path.contains("grate") || path.contains("frame")) {
            return PartCategory.CASING;
        }
        return PartCategory.OTHER;
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
