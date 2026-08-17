package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

@EmiEntrypoint
public class CalcBoardEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // EMI Integration initialized
    }

    public static void addRecipeToBoard(EmiRecipe recipe) {
        addRecipeToBoard(recipe, true);
    }

    public static void addRecipeToBoard(EmiRecipe recipe, boolean openBoard) {
        if (recipe == null) return;
        RecipeNode node = EmiRecipeConverter.convert(recipe);

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
        node.setPosX(pos[0]);
        node.setPosY(pos[1]);

        BoardManager.getInstance().getActiveGraph().addNode(node);

        if (mc.player != null) {
            String name = recipe.getId() != null ? recipe.getId().getPath() : "Recipe";
            if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
            mc.player.displayClientMessage(Component.literal("§a✔ Added [" + name + "] to Calculator Board!"), true);
        }

        mc.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
            )
        );

        if (openBoard) {
            mc.setScreen(new BoardScreen());
        } else if (mc.screen instanceof BoardScreen boardScreen) {
            boardScreen.rebuildWidgets();
        }
    }
}
