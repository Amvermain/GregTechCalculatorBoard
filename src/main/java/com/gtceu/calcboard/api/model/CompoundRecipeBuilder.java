package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Dedicated builder for constructing bonded compound node clusters representing
 * multi-layered / progressive assembly recipes.
 */
public final class CompoundRecipeBuilder {

    public record LayerSpec(
            String layerName,
            ResourceLocation machineIcon,
            double durationTicks,
            double eut,
            List<IngredientStack> inputs,
            List<IngredientStack> outputs
    ) {
        public LayerSpec(String layerName, double durationTicks, double eut, List<IngredientStack> inputs, List<IngredientStack> outputs) {
            this(layerName, null, durationTicks, eut, inputs, outputs);
        }

        public LayerSpec(String layerName, List<IngredientStack> inputs, List<IngredientStack> outputs) {
            this(layerName, null, 0.0, 0.0, inputs, outputs);
        }
    }

    public record CompoundCluster(
            List<RecipeNode> nodes,
            CanvasGroupFrame frame,
            List<FlowGraph.ConnectionEdge> internalEdges
    ) {}

    private CompoundRecipeBuilder() {}

    public static CompoundCluster build(
            String baseMachineName,
            ResourceLocation machineIcon,
            double baseDurationTicks,
            double baseEUt,
            GTVoltageTier recipeTier,
            List<LayerSpec> layers,
            double startX,
            double startY
    ) {
        if (layers == null || layers.isEmpty()) {
            return new CompoundCluster(Collections.emptyList(), null, Collections.emptyList());
        }

        String groupId = UUID.randomUUID().toString();
        int totalLayers = layers.size();
        List<RecipeNode> createdNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> edges = new ArrayList<>();

        double spacingX = 285.0; // 245 cardWidth + 40 gap
        String masterId = null;

        for (int i = 0; i < totalLayers; i++) {
            LayerSpec spec = layers.get(i);
            String roman = formatRoman(i + 1);
            String nodeName = baseMachineName + " (Layer " + roman + ")";

            double nodeDuration = spec.durationTicks() > 0.0 ? spec.durationTicks() : baseDurationTicks;
            double nodeEUt = spec.eut() > 0.0 ? spec.eut() : baseEUt;

            ResourceLocation nodeIcon = spec.machineIcon() != null ? spec.machineIcon() : machineIcon;
            RecipeNode node = RecipeNode.create(nodeIcon, nodeName, nodeDuration, nodeEUt, recipeTier);
            if (i == 0) {
                masterId = node.getId();
            }
            node.setCompoundMetadata(groupId, i, totalLayers, masterId != null ? masterId : node.getId());
            node.setPos(startX + (i * spacingX), startY);

            if (spec.inputs != null) {
                for (IngredientStack in : spec.inputs) {
                    node.addInput(in.copy());
                }
            }
            if (spec.outputs != null) {
                for (IngredientStack out : spec.outputs) {
                    node.addOutput(out.copy());
                }
            }

            createdNodes.add(node);
        }

        String frameTitle = "🧩 " + baseMachineName + " (" + totalLayers + "-Layer)";
        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes(frameTitle, createdNodes, CanvasGroupFrame.COLOR_PURPLE);
        frame.setCompoundFrame(true);
        frame.setCompoundGroupId(groupId);

        return new CompoundCluster(createdNodes, frame, edges);
    }

    public static String formatRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            case 11 -> "XI";
            case 12 -> "XII";
            case 13 -> "XIII";
            case 14 -> "XIV";
            case 15 -> "XV";
            case 16 -> "XVI";
            default -> String.valueOf(num);
        };
    }
}
