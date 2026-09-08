package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

@EmiEntrypoint
public class CalcBoardEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(BoardScreen.class, new BoardEmiDragDropHandler());
        if (ModMenus.BOARD_MENU.isPresent()) {
            try {
                registry.addRecipeHandler(ModMenus.BOARD_MENU.get(), new BoardEmiRecipeHandler());
            } catch (Throwable ignored) {}
        }
        com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().markDirty();
        com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog.invalidateCache();
    }

    public static void addRecipeToBoard(EmiRecipe recipe) {
        addRecipeToBoard(recipe, true);
    }

    public static void addRecipeToBoard(EmiRecipe recipe, boolean openBoard) {
        if (recipe == null) return;

        Minecraft mc = Minecraft.getInstance();
        double[] pos = BoardScreen.getNextNodeCenterPosition();

        com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster cluster =
                EmiStepRecipeDetector.tryDetectAndBuild(recipe, null, pos[0], pos[1]);

        if (cluster != null && !cluster.nodes().isEmpty()) {
            for (RecipeNode n : cluster.nodes()) {
                BoardManager.getInstance().getActiveGraph().addNode(n);
            }
            if (cluster.frame() != null) {
                BoardManager.getInstance().getActiveGraph().addFrame(cluster.frame());
            }
            for (com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge edge : cluster.internalEdges()) {
                BoardManager.getInstance().getActiveGraph().addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
            }
        } else {
            RecipeNode node = EmiRecipeConverter.convert(recipe);
            node.setPosX(pos[0]);
            node.setPosY(pos[1]);
            BoardManager.getInstance().getActiveGraph().addNode(node);
        }

        String name = recipe.getId() != null ? recipe.getId().getPath() : "Recipe";
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));

        mc.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
            )
        );

        if (mc.screen instanceof BoardScreen boardScreen) {
            boardScreen.rebuildWidgets();
            boardScreen.markSummaryDirty();
        } else if (openBoard) {
            mc.setScreen(new BoardScreen());
        }
    }
}



