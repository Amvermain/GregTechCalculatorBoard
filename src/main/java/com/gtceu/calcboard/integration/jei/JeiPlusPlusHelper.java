package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.api.bom.PartCategory;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides reflection-based integration with JEI++ (Just Enough Items Plus Plus) Recipe Tree system.
 */
public class JeiPlusPlusHelper {

    private static final String JEI_PLUS_PLUS_MOD_ID = "jei_plus_plus";

    public static boolean isJeiPlusPlusLoaded() {
        return ModList.get().isLoaded(JEI_PLUS_PLUS_MOD_ID);
    }

    public static boolean registerBoMGoal(IJeiRuntime jeiRuntime, MultiblockBOMSummary summary) {
        if (!isJeiPlusPlusLoaded() || summary == null || jeiRuntime == null) return false;

        try {
            Class<?> recipeInputCls = Class.forName("com.lingmu0.JeiPlusPlusMod.client.RecipeTreeData$RecipeInput");
            Class<?> recipeRefCls = Class.forName("com.lingmu0.JeiPlusPlusMod.client.RecipeTreeData$RecipeRef");
            Class<?> snapshotCls = Class.forName("com.lingmu0.JeiPlusPlusMod.client.RecipeTreeData$RecipeSnapshot");
            Class<?> treeCls = Class.forName("com.lingmu0.JeiPlusPlusMod.client.RecipeTreeData$Tree");
            Class<?> sessionCls = Class.forName("com.lingmu0.JeiPlusPlusMod.client.RecipeTreeSession");

            // 1. Build inputs list for RecipeSnapshot
            Constructor<?> inputCtor = recipeInputCls.getConstructor(List.class, List.class);
            List<Object> inputList = new ArrayList<>();
            List<ItemStack> outputList = new ArrayList<>();

            int slotIdx = 0;
            for (MultiblockBOMSummary.BOMItemEntry item : summary.aggregatedItems()) {
                ItemStack stack = item.resolveItemStack();
                if (stack.isEmpty()) continue;

                // Create ItemStack with required amount (or max stack count if large)
                ItemStack inputStack = stack.copy();
                inputStack.setCount(Math.min(item.totalAmount(), 64));

                Object inputObj = inputCtor.newInstance(List.of(inputStack), List.of(slotIdx++));
                inputList.add(inputObj);
            }

            if (inputList.isEmpty()) return false;

            // Pick primary controller/block as root output
            ItemStack rootOutput = ItemStack.EMPTY;
            for (MultiblockBOMSummary.BOMItemEntry item : summary.aggregatedItems()) {
                if (item.category() == com.gtceu.calcboard.api.bom.PartCategory.CONTROLLER) {
                    rootOutput = item.resolveItemStack().copy();
                    break;
                }
            }
            if (rootOutput.isEmpty()) {
                rootOutput = summary.aggregatedItems().get(0).resolveItemStack().copy();
            }
            rootOutput.setCount(1);
            outputList.add(rootOutput);

            // 2. Resolve a valid IRecipeCategory so RecipeTreeScreen.drawNode won't NPE on category().getIcon()
            IRecipeCategory<?> primaryCategory = null;
            try {
                var rm = jeiRuntime.getRecipeManager();
                if (rm != null) {
                    primaryCategory = rm.createRecipeCategoryLookup().get().findFirst().orElse(null);
                }
            } catch (Throwable ignored) {}

            Constructor<?> refCtor = recipeRefCls.getConstructor(
                IRecipeCategory.class,
                Object.class,
                String.class,
                String.class
            );
            Object refObj = refCtor.newInstance(
                primaryCategory,
                "gtcalcboard:multiblock_bom",
                "gtcalcboard:multiblock_bom",
                "gtcalcboard:multiblock_bom"
            );

            // 3. Build RecipeSnapshot
            Constructor<?> snapshotCtor = snapshotCls.getConstructor(
                recipeRefCls,
                List.class,
                int.class,
                List.class
            );
            Object snapshotObj = snapshotCtor.newInstance(refObj, inputList, inputList.size(), outputList);

            // 4. Build Tree
            Constructor<?> treeCtor = treeCls.getDeclaredConstructor(snapshotCls, ItemStack.class);
            treeCtor.setAccessible(true);
            Object treeObj = treeCtor.newInstance(snapshotObj, rootOutput);

            // Set Tree.setCraftingMode(true) and rebuild
            Method setCraftingModeMethod = treeCls.getMethod("setCraftingMode", boolean.class);
            setCraftingModeMethod.invoke(treeObj, true);

            Method rebuildMethod = treeCls.getMethod("rebuild");
            rebuildMethod.invoke(treeObj);

            // 5. Set into RecipeTreeSession
            Field treeField = sessionCls.getDeclaredField("tree");
            treeField.setAccessible(true);
            treeField.set(null, treeObj);

            Field craftingTreeField = sessionCls.getDeclaredField("craftingTree");
            craftingTreeField.setAccessible(true);
            craftingTreeField.set(null, treeObj);

            Method sessionSetCraftingMode = sessionCls.getMethod("setCraftingMode", boolean.class);
            sessionSetCraftingMode.invoke(null, true);

            // 6. Refresh RecipeTreeFavorites
            try {
                Class<?> favCls = Class.forName("com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites");
                Method refreshMethod = favCls.getMethod("refreshNow");
                refreshMethod.invoke(null);
            } catch (Throwable ignored) {}

            GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Successfully registered {} Multiblock BOM items to JEI++ Recipe Tree!", inputList.size());
            return true;
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to register Multiblock BOM into JEI++ Recipe Tree: {}", t.getMessage(), t);
            return false;
        }
    }
}

