package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public class BoardEmiDragDropHandler implements EmiDragDropHandler<BoardScreen> {

    @Override
    public boolean dropStack(BoardScreen screen, EmiIngredient ingredient, int x, int y) {
        if (ingredient == null || ingredient.getEmiStacks().isEmpty() || screen == null) return false;
        EmiStack emiStack = ingredient.getEmiStacks().get(0);
        ItemStack itemStack = emiStack.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) return false;

        double canvasX = screen.toCanvasX(x);
        double canvasY = screen.toCanvasY(y);

        RecipeNode node = CreateModAdapter.createKineticGeneratorNode(itemStack);
        if (node != null) {
            node.setPosX(canvasX);
            node.setPosY(canvasY);
            BoardManager.getInstance().getActiveGraph().addNode(node);
            screen.rebuildWidgets();
            screen.markSummaryDirty();

            BoardToast.show(Component.literal("§6⚙ ").append(Component.translatable("message.gtcalcboard.recipe_added", node.getName())));
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
                    )
            );
            return true;
        }

        return false;
    }
}

