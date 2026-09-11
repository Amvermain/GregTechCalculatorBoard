package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalStack;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RecipeSearchDialogScrollTest {

    private static List<SearchableRecipe> createDummyRecipes(int count) {
        List<SearchableRecipe> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new SearchableRecipe(
                    "recipe" + i,
                    "Recipe " + i,
                    "gtceu",
                    "assembler",
                    "Assembler",
                    "",
                    "",
                    new ResourceLocation[0],
                    new ResourceLocation[0],
                    new String[0],
                    new String[0]
            ));
        }
        return list;
    }

    @Test
    @DisplayName("RecipeSearchDialog updates scrollOffset on scrollbar drag and resets on release")
    void testScrollbarDragAndRelease() {
        RecipeSearchDialog dialog = new RecipeSearchDialog(null);
        dialog.open();
        dialog.setFilteredRecipesForTesting(createDummyRecipes(50));

        int screenW = 800;
        int screenH = 600;
        int dialogW = RecipeSearchDialog.getDialogWidth(screenW);
        int dialogH = RecipeSearchDialog.getDialogHeight(screenH);
        int sideW = 104;
        int gap = 6;
        boolean hasSideSpace = screenW >= (dialogW + sideW + gap + 16);
        int totalW = hasSideSpace ? (dialogW + sideW + gap) : dialogW;
        int startX = (screenW - totalW) / 2;
        int x = hasSideSpace ? (startX + sideW + gap) : startX;
        int y = (screenH - dialogH) / 2;

        int listX = x + 12;
        int listY = y + 52;
        int listW = dialogW - 24;
        int scrollBarX = listX + listW - 4;
        int scrollBarY = listY + 2;

        Assertions.assertEquals(0, dialog.getScrollOffset());
        Assertions.assertFalse(dialog.isDraggingScrollBar());

        boolean clicked = dialog.mouseClicked(scrollBarX, scrollBarY, 0, screenW, screenH);
        Assertions.assertTrue(clicked);
        Assertions.assertTrue(dialog.isDraggingScrollBar());

        boolean dragged = dialog.mouseDragged(scrollBarX, scrollBarY + 80, 0, 0, 80, screenW, screenH);
        Assertions.assertTrue(dragged);
        Assertions.assertTrue(dialog.getScrollOffset() > 0);

        boolean released = dialog.mouseReleased(scrollBarX, scrollBarY + 80, 0);
        Assertions.assertTrue(released);
        Assertions.assertFalse(dialog.isDraggingScrollBar());
    }

    @Test
    @DisplayName("ModalStack correctly dispatches mouseDragged and mouseReleased to RecipeSearchDialog")
    void testModalStackDispatchToRecipeSearchDialog() {
        ModalStack stack = new ModalStack();
        RecipeSearchDialog dialog = new RecipeSearchDialog(null);
        dialog.open();
        dialog.setFilteredRecipesForTesting(createDummyRecipes(50));
        stack.push(dialog);

        int screenW = 800;
        int screenH = 600;
        int dialogW = RecipeSearchDialog.getDialogWidth(screenW);
        int dialogH = RecipeSearchDialog.getDialogHeight(screenH);
        int sideW = 104;
        int gap = 6;
        int totalW = dialogW + sideW + gap;
        int startX = (screenW - totalW) / 2;
        int x = startX + sideW + gap;
        int y = (screenH - dialogH) / 2;

        int scrollBarX = (x + 12) + (dialogW - 24) - 4;
        int scrollBarY = y + 54;

        boolean clicked = stack.dispatchMouseClicked(scrollBarX, scrollBarY, 0, screenW, screenH);
        Assertions.assertTrue(clicked);
        Assertions.assertTrue(dialog.isDraggingScrollBar());

        boolean dragged = stack.dispatchMouseDragged(scrollBarX, scrollBarY + 60, 0, 0, 60, screenW, screenH);
        Assertions.assertTrue(dragged);
        Assertions.assertTrue(dialog.getScrollOffset() > 0);

        boolean released = stack.dispatchMouseReleased(scrollBarX, scrollBarY + 60, 0);
        Assertions.assertTrue(released);
        Assertions.assertFalse(dialog.isDraggingScrollBar());
    }

    @Test
    @DisplayName("Closing RecipeSearchDialog resets scrollbar dragging state")
    void testCloseResetsScrollbarDragging() {
        RecipeSearchDialog dialog = new RecipeSearchDialog(null);
        dialog.open();
        dialog.setFilteredRecipesForTesting(createDummyRecipes(50));

        int screenW = 800;
        int screenH = 600;
        int dialogW = RecipeSearchDialog.getDialogWidth(screenW);
        int dialogH = RecipeSearchDialog.getDialogHeight(screenH);
        int sideW = 104;
        int gap = 6;
        int totalW = dialogW + sideW + gap;
        int startX = (screenW - totalW) / 2;
        int x = startX + sideW + gap;
        int y = (screenH - dialogH) / 2;

        int scrollBarX = (x + 12) + (dialogW - 24) - 4;
        int scrollBarY = y + 54;

        dialog.mouseClicked(scrollBarX, scrollBarY, 0, screenW, screenH);
        Assertions.assertTrue(dialog.isDraggingScrollBar());

        dialog.close();
        Assertions.assertFalse(dialog.isDraggingScrollBar());
    }
}
