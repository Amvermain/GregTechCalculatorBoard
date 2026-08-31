package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.PartCategory;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides reflection-based integration with JEI Unofficial (by StardustMINUS) Bookmark Group and Recipe Tree systems.
 */
public class JeiUnofficialHelper {

    public static boolean isJeiUnofficialLoaded(IJeiRuntime jeiRuntime) {
        if (jeiRuntime == null) return false;
        try {
            IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
            if (bookmarkOverlay == null) return false;

            Object bookmarkList = resolveBookmarkList(bookmarkOverlay);
            if (bookmarkList == null) return false;

            Method createGroupMethod = bookmarkList.getClass().getMethod("createGroupForBookmarks", String.class, List.class);
            return createGroupMethod != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerBoMGroup(IJeiRuntime jeiRuntime, MultiblockBOMSummary summary) {
        if (jeiRuntime == null || summary == null) return false;

        try {
            IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
            if (bookmarkOverlay == null) return false;

            Object bookmarkList = resolveBookmarkList(bookmarkOverlay);
            if (bookmarkList == null) return false;

            IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
            if (ingredientManager == null) return false;

            Class<?> ingredientBookmarkCls = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark");
            Method createPreservingAmountMethod = ingredientBookmarkCls.getMethod(
                "createPreservingAmount",
                ITypedIngredient.class,
                IIngredientManager.class
            );

            Method addBookmarkMethod = bookmarkList.getClass().getMethod("add", Class.forName("mezz.jei.gui.bookmarks.IBookmark"));
            Method createGroupForBookmarksMethod = bookmarkList.getClass().getMethod("createGroupForBookmarks", String.class, List.class);

            List<Object> createdBookmarks = new ArrayList<>();
            ItemStack primaryControllerStack = ItemStack.EMPTY;

            for (MultiblockBOMSummary.BOMItemEntry item : summary.aggregatedItems()) {
                ItemStack stack = item.resolveItemStack();
                if (stack.isEmpty()) continue;

                if (primaryControllerStack.isEmpty() && item.category() == PartCategory.CONTROLLER) {
                    primaryControllerStack = stack.copy();
                }

                ItemStack countStack = stack.copy();
                countStack.setCount(item.totalAmount());

                Optional<ITypedIngredient<ItemStack>> typedOpt = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, countStack);
                if (typedOpt.isEmpty()) continue;

                Object bookmarkObj = createPreservingAmountMethod.invoke(null, typedOpt.get(), ingredientManager);
                if (bookmarkObj != null) {
                    addBookmarkMethod.invoke(bookmarkList, bookmarkObj);
                    createdBookmarks.add(bookmarkObj);
                }
            }

            if (createdBookmarks.isEmpty()) return false;

            String groupTitle;
            if (!primaryControllerStack.isEmpty()) {
                groupTitle = "GTCalc: " + primaryControllerStack.getHoverName().getString();
            } else {
                groupTitle = "GTCalc: Multiblock BOM (" + summary.totalUniqueItemTypes() + ")";
            }

            Object groupId = createGroupForBookmarksMethod.invoke(bookmarkList, groupTitle, createdBookmarks);

            // Activate Crafting Mode for the newly created group if supported
            try {
                Method setCraftingModeMethod = bookmarkList.getClass().getMethod("setGroupCraftingMode", String.class, boolean.class);
                setCraftingModeMethod.invoke(bookmarkList, groupId, true);
            } catch (Throwable ignored) {}

            // Open the bookmark panel if supported
            try {
                Method showBookmarkPanelMethod = bookmarkOverlay.getClass().getMethod("showBookmarkPanel");
                showBookmarkPanelMethod.invoke(bookmarkOverlay);
            } catch (Throwable ignored) {}

            GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Successfully registered {} Multiblock BOM items to JEI Unofficial group '{}'!",
                createdBookmarks.size(), groupTitle);
            return true;
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to register Multiblock BOM into JEI Unofficial: {}", t.getMessage(), t);
            return false;
        }
    }

    private static Object resolveBookmarkList(IBookmarkOverlay bookmarkOverlay) {
        try {
            Method getBookmarkListMethod = bookmarkOverlay.getClass().getMethod("getBookmarkList");
            getBookmarkListMethod.setAccessible(true);
            return getBookmarkListMethod.invoke(bookmarkOverlay);
        } catch (Throwable t1) {
            try {
                Method getBookmarkListMethod = bookmarkOverlay.getClass().getDeclaredMethod("getBookmarkList");
                getBookmarkListMethod.setAccessible(true);
                return getBookmarkListMethod.invoke(bookmarkOverlay);
            } catch (Throwable t2) {
                try {
                    Field bookmarkListField = bookmarkOverlay.getClass().getDeclaredField("bookmarkList");
                    bookmarkListField.setAccessible(true);
                    return bookmarkListField.get(bookmarkOverlay);
                } catch (Throwable t3) {
                    return null;
                }
            }
        }
    }
}
