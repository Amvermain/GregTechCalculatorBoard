package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.integration.emi.EmiStepRecipeDetector;
import com.gtceu.calcboard.integration.jei.JeiRecipeConverter;
import com.gtceu.calcboard.integration.jei.JeiRecipeWrapper;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

public final class RecipeSearchNodeSpawner {

    private RecipeSearchNodeSpawner() {}

    public static void spawnRecipeAt(
            RecipeSearchDialog dialog,
            SearchableRecipe sr,
            int screenWidth,
            int screenHeight,
            BoardScreen parent,
            RecipeSearchDialog.ContextualWireTarget contextualWireTarget,
            boolean hasTargetSpawnPos,
            double targetSpawnCanvasX,
            double targetSpawnCanvasY
    ) {
        if (sr == null) return;
        double[] pos = calculateSpawnPosition(contextualWireTarget, hasTargetSpawnPos, targetSpawnCanvasX, targetSpawnCanvasY, screenWidth, screenHeight);
        double spawnX = pos[0];
        double spawnY = pos[1];

        CompoundRecipeBuilder.CompoundCluster cluster = null;
        if (ModCompatHelper.isEmiLoaded()) {
            cluster = EmiStepRecipeDetector.tryDetectAndBuild(sr.recipe(), null, spawnX, spawnY);
        }

        if (cluster != null && !cluster.nodes().isEmpty()) {
            spawnCompoundCluster(dialog, parent, contextualWireTarget, cluster);
            return;
        }

        RecipeNode node = convertRecipeToNode(sr.recipe());
        if (node != null) {
            node.setPosX(spawnX);
            node.setPosY(spawnY);

            parent.addNode(node);
            GregTechCalcBoard.LOGGER.info(
                    "[GTCalcBoard] [UI] Added recipe node '{}' to board (Category: {}, Outputs: {}).",
                    node.getName(), node.getRecipeCategoryId(), node.getOutputs().size()
            );

            if (contextualWireTarget != null) {
                linkContextualWire(parent, contextualWireTarget, node);
                dialog.clearContextualWireTarget();
                parent.rebuildWidgets();
            }

            parent.markSummaryDirty();
            dialog.setVisible(false);
        } else {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] [UI] Failed to convert recipe to RecipeNode: {}", sr.displayName());
            dialog.setVisible(false);
        }
    }

    private static double[] calculateSpawnPosition(
            RecipeSearchDialog.ContextualWireTarget target,
            boolean hasTargetSpawnPos,
            double targetSpawnX,
            double targetSpawnY,
            int screenWidth,
            int screenHeight
    ) {
        if (target != null) {
            double sx = !target.sourceIsInput ? target.canvasX : target.canvasX - 245;
            return new double[]{sx, target.canvasY - 30};
        }
        if (hasTargetSpawnPos) {
            return new double[]{targetSpawnX - 80, targetSpawnY - 30};
        }
        double[] center = BoardScreen.getNextNodeCenterPosition(screenWidth, screenHeight);
        return new double[]{center[0], center[1]};
    }

    private static void spawnCompoundCluster(RecipeSearchDialog dialog, BoardScreen parent, RecipeSearchDialog.ContextualWireTarget target, CompoundRecipeBuilder.CompoundCluster cluster) {
        for (RecipeNode n : cluster.nodes()) {
            parent.addNode(n);
        }
        if (cluster.frame() != null) {
            parent.getGraph().addFrame(cluster.frame());
        }
        for (FlowGraph.ConnectionEdge edge : cluster.internalEdges()) {
            parent.getGraph().addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
        }
        if (target != null) {
            RecipeNode targetConnectNode = !target.sourceIsInput ? cluster.nodes().get(0) : cluster.nodes().get(cluster.nodes().size() - 1);
            linkContextualWire(parent, target, targetConnectNode);
            dialog.clearContextualWireTarget();
            parent.rebuildWidgets();
        }
        parent.markSummaryDirty();
        dialog.setVisible(false);
    }

    private static void linkContextualWire(BoardScreen parent, RecipeSearchDialog.ContextualWireTarget target, RecipeNode node) {
        if (!target.sourceIsInput) {
            connectContextualForwardWire(parent, target, node, target.sourceNode, target.sourceStack);
        } else {
            connectContextualReverseWire(parent, target, node, target.sourceNode, target.sourceStack);
        }
    }

    public static RecipeNode convertRecipeToNode(Object recipe) {
        if (recipe instanceof Supplier<?> supp) {
            Object obj = supp.get();
            if (obj instanceof RecipeNode rn) return rn;
        } else if (recipe instanceof RecipeNode rn) {
            return rn.copy();
        } else {
            RecipeNode node = RecipeViewerRegistry.getActiveAdapter().convertToNode(recipe);
            if (node == null && recipe instanceof JeiRecipeWrapper<?> jrw) {
                return JeiRecipeConverter.convert(jrw);
            }
            return node;
        }
        return null;
    }

    private static void connectContextualForwardWire(BoardScreen parent, RecipeSearchDialog.ContextualWireTarget target, RecipeNode node, RecipeNode sourceNode, IngredientStack sourceStack) {
        FlowGraph graph = parent.getGraph();
        int matchedInIdx = findMatchedInputIndex(node, sourceStack);

        if (matchedInIdx >= 0) {
            FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(sourceNode.getId(), target.sourcePortIdx, node.getId(), matchedInIdx);
            graph.addConnection(sourceNode.getId(), target.sourcePortIdx, node.getId(), matchedInIdx);

            Double oldMachineCount = target.shiftAutoRatio ? node.getMachineCount() : null;
            Double newMachineCount = null;

            if (target.shiftAutoRatio) {
                double matched = FlowGraphSolver.calculateConsumerMatchCount(graph, sourceNode, target.sourcePortIdx, node, matchedInIdx);
                newMachineCount = matched;
                node.setMachineCount(matched);
                BoardToast.show(Component.literal("§a⚡ ").append(
                        Component.translatable("message.gtcalcboard.shift_connect_matched", node.getName(), String.format(java.util.Locale.ROOT, "%.0f", matched))
                ));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
            } else {
                BoardToast.show(Component.literal("§a✔ ").append(
                        Component.translatable("gui.gtcalcboard.toast.drag_auto_connected", sourceNode.getName(), node.getName())
                ));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            }
            parent.recordCommand(new BoardCommand.ConnectWireCommand(newEdge, target.shiftAutoRatio ? node.getId() : null, oldMachineCount, newMachineCount));
            TutorialManager.getInstance().onWireConnected(target.shiftAutoRatio);
        }
    }

    private static int findMatchedInputIndex(RecipeNode node, IngredientStack sourceStack) {
        for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
            IngredientStack in = node.getInputs().get(inIdx);
            if (in.equals(sourceStack) || in.matchesOrAlternative(sourceStack) || (in.isStressUnit() && sourceStack.isStressUnit())) {
                if (!in.equals(sourceStack) && !in.isStressUnit()) {
                    in.selectAlternative(sourceStack.getId());
                }
                return inIdx;
            }
        }
        return -1;
    }

    private static void connectContextualReverseWire(BoardScreen parent, RecipeSearchDialog.ContextualWireTarget target, RecipeNode node, RecipeNode sourceNode, IngredientStack sourceStack) {
        FlowGraph graph = parent.getGraph();
        int matchedOutIdx = findMatchedOutputIndex(node, sourceStack);

        if (matchedOutIdx >= 0) {
            FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(node.getId(), matchedOutIdx, sourceNode.getId(), target.sourcePortIdx);
            graph.addConnection(node.getId(), matchedOutIdx, sourceNode.getId(), target.sourcePortIdx);

            Double oldMachineCount = target.shiftAutoRatio ? node.getMachineCount() : null;
            Double newMachineCount = null;

            if (target.shiftAutoRatio) {
                double matched = FlowGraphSolver.calculateProducerMatchCount(graph, node, matchedOutIdx, sourceNode, target.sourcePortIdx);
                newMachineCount = matched;
                node.setMachineCount(matched);
                BoardToast.show(Component.literal("§a⚡ ").append(
                        Component.translatable("message.gtcalcboard.shift_connect_matched", node.getName(), String.format(java.util.Locale.ROOT, "%.0f", matched))
                ));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
            } else {
                BoardToast.show(Component.literal("§a✔ ").append(
                        Component.translatable("gui.gtcalcboard.toast.drag_auto_connected", node.getName(), sourceNode.getName())
                ));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            }
            parent.recordCommand(new BoardCommand.ConnectWireCommand(newEdge, target.shiftAutoRatio ? node.getId() : null, oldMachineCount, newMachineCount));
            TutorialManager.getInstance().onWireConnected(target.shiftAutoRatio);
        }
    }

    private static int findMatchedOutputIndex(RecipeNode node, IngredientStack sourceStack) {
        for (int outIdx = 0; outIdx < node.getOutputs().size(); outIdx++) {
            IngredientStack out = node.getOutputs().get(outIdx);
            if (out.equals(sourceStack) || sourceStack.matchesOrAlternative(out) || (out.isStressUnit() && sourceStack.isStressUnit())) {
                if (!out.equals(sourceStack) && !out.isStressUnit()) {
                    sourceStack.selectAlternative(out.getId());
                }
                return outIdx;
            }
        }
        return -1;
    }
}
