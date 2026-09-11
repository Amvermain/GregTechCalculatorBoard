package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public final class EmiFavoritesNodeSpawner {

    private EmiFavoritesNodeSpawner() {}

    public static void spawnFavoriteNode(IBoardScreenContext screen, EmiFavorite fav, double canvasX, double canvasY) {
        RecipeNode node = resolveRecipeNode(fav);
        if (node == null) return;

        node.setPosX(canvasX);
        node.setPosY(canvasY);
        screen.addNode(node);
        screen.recordCommand(new BoardCommand.AddNodesCommand(node, "Add from Favorites Dock"));

        String name = extractFavoriteName(fav);
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
        );

        screen.rebuildBoardWidgets();
        screen.markSummaryDirty();
    }

    public static void spawnRecipeNode(IBoardScreenContext screen, EmiRecipe recipe, double canvasX, double canvasY) {
        CompoundRecipeBuilder.CompoundCluster cluster =
                EmiStepRecipeDetector.tryDetectAndBuild(recipe, null, canvasX, canvasY);
        if (cluster != null && !cluster.nodes().isEmpty()) {
            for (RecipeNode n : cluster.nodes()) {
                screen.addNode(n);
            }
            if (cluster.frame() != null) {
                screen.getGraph().addFrame(cluster.frame());
            }
            for (FlowGraph.ConnectionEdge edge : cluster.internalEdges()) {
                screen.getGraph().addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
            }
            String name = extractRecipeDisplayName(recipe);
            BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
            screen.rebuildBoardWidgets();
            screen.markSummaryDirty();
            return;
        }

        RecipeNode node = EmiRecipeConverter.convert(recipe);
        if (node == null) return;

        node.setPosX(canvasX);
        node.setPosY(canvasY);
        screen.addNode(node);
        screen.recordCommand(new BoardCommand.AddNodesCommand(node, "Add from Favorites Flyout"));

        String name = extractRecipeDisplayName(recipe);
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
        );

        screen.rebuildBoardWidgets();
        screen.markSummaryDirty();
    }

    public static RecipeNode resolveRecipeNode(EmiFavorite fav) {
        if (fav.getRecipe() != null) {
            return EmiRecipeConverter.convert(fav.getRecipe());
        }

        RecipeNode fromStacks = resolveRecipeNodeFromStacks(fav);
        if (fromStacks != null) {
            return fromStacks;
        }

        return createFallbackNode(fav);
    }

    private static RecipeNode resolveRecipeNodeFromStacks(EmiFavorite fav) {
        if (fav.getEmiStacks().isEmpty()) return null;
        var rm = EmiApi.getRecipeManager();
        if (rm == null) return null;

        for (var stack : fav.getEmiStacks()) {
            RecipeNode node = resolveNodeFromSingleStack(rm, stack);
            if (node != null) return node;
        }
        return null;
    }

    private static RecipeNode resolveNodeFromSingleStack(EmiRecipeManager rm, EmiStack stack) {
        try {
            EmiRecipe def = BoM.getRecipe(stack);
            if (def != null) {
                RecipeNode node = EmiRecipeConverter.convert(def);
                if (node != null) return node;
            }
        } catch (Throwable ignored) {}

        var outRecipes = rm.getRecipesByOutput(stack);
        if (outRecipes == null || outRecipes.isEmpty()) return null;
        return EmiRecipeConverter.convert(outRecipes.get(0));
    }

    private static RecipeNode createFallbackNode(EmiFavorite fav) {
        String name = extractFavoriteName(fav);
        RecipeNode fallback = new RecipeNode(UUID.randomUUID().toString(), name, 20.0, 100.0, GTVoltageTier.ULV);
        if (fav.getEmiStacks().isEmpty()) return fallback;

        var stack = fav.getEmiStacks().get(0);
        if (stack.getItemStack() != null && !stack.getItemStack().isEmpty()) {
            var item = stack.getItemStack().getItem();
            var id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null) {
                fallback.getOutputs().add(IngredientStack.item(id, name, 1.0));
            }
        }
        return fallback;
    }

    public static String extractFavoriteName(EmiFavorite fav) {
        if (fav.getRecipe() != null) {
            return extractRecipeDisplayName(fav.getRecipe());
        }
        if (!fav.getEmiStacks().isEmpty()) {
            return fav.getEmiStacks().get(0).getName().getString();
        }
        return "Favorite";
    }

    public static String extractRecipeDisplayName(EmiRecipe recipe) {
        if (!recipe.getOutputs().isEmpty()) {
            var stacks = recipe.getOutputs().get(0).getEmiStacks();
            if (!stacks.isEmpty()) return stacks.get(0).getName().getString();
        }
        if (recipe.getId() != null) {
            String path = recipe.getId().getPath();
            if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
            return path;
        }
        return "Recipe";
    }
}
