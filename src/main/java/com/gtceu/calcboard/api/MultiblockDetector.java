package com.gtceu.calcboard.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Dynamically detects whether a machine block/workstation is a valid Multiblock
 * by checking if it has a registered "multiblock_info" structure recipe in EMI or is a GT Multiblock Controller.
 */
public class MultiblockDetector {

    private static final Set<ResourceLocation> MULTIBLOCK_RECIPE_CONTROLLERS = new HashSet<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized && !MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) return;

        // 1. Scan EMI's "multiblock_info" recipe category directly
        try {
            var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
            if (rm != null) {
                for (var recipe : rm.getRecipes()) {
                    if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
                        String catPath = recipe.getCategory().getId().getPath();
                        if (catPath.equals("multiblock_info") || catPath.contains("multiblock")) {
                            // Extract from recipe outputs
                            for (var es : recipe.getOutputs()) {
                                if (es != null && es.getId() != null) {
                                    MULTIBLOCK_RECIPE_CONTROLLERS.add(es.getId());
                                }
                            }
                            // Extract from recipe inputs
                            for (var ei : recipe.getInputs()) {
                                for (var es : ei.getEmiStacks()) {
                                    if (es != null && es.getId() != null) {
                                        MULTIBLOCK_RECIPE_CONTROLLERS.add(es.getId());
                                    }
                                }
                            }
                            // Extract from recipe ID path (e.g. gtceu:multiblock_info/large_mixer -> gtceu:large_mixer)
                            if (recipe.getId() != null) {
                                String rPath = recipe.getId().getPath();
                                if (rPath.contains("/")) {
                                    String machineName = rPath.substring(rPath.lastIndexOf('/') + 1);
                                    ResourceLocation mLoc = ResourceLocation.tryParse(recipe.getId().getNamespace() + ":" + machineName);
                                    if (mLoc != null && ForgeRegistries.ITEMS.containsKey(mLoc)) {
                                        MULTIBLOCK_RECIPE_CONTROLLERS.add(mLoc);
                                    }
                                }
                            }
                        }
                    }
                }
                if (!MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
                    initialized = true;
                }
            }
        } catch (Throwable ignored) {}

        // 2. GTCEu MetaMachine Multiblock Definition inspection via Reflection
        try {
            if (ModCompatHelper.isGTLoaded() && ForgeRegistries.ITEMS != null) {
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id != null && (id.getNamespace().equals("gtceu") || id.getNamespace().equals("kubejs") || id.getNamespace().equals("start_core"))) {
                        if (item instanceof BlockItem bi) {
                            Block b = bi.getBlock();
                            try {
                                Method mGetDef = b.getClass().getMethod("getDefinition");
                                Object def = mGetDef.invoke(b);
                                if (def != null) {
                                    String defName = def.getClass().getName();
                                    if (defName.contains("Multiblock") || defName.contains("Multi")) {
                                        MULTIBLOCK_RECIPE_CONTROLLERS.add(id);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Checks whether the given workstation item/block has a registered Multiblock Info structure recipe or controller definition.
     */
    public static boolean isMultiblock(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return MULTIBLOCK_RECIPE_CONTROLLERS.contains(workstationId);
    }
}
