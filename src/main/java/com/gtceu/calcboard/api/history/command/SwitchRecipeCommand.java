package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * In-place recipe switching for an existing node with smart wire preservation.
 */
public class SwitchRecipeCommand implements BoardCommand {
    public record RecipeSnapshot(
            String name,
            double baseDurationTicks,
            double baseEUt,
            com.gtceu.calcboard.api.type.GTVoltageTier recipeTier,
            ResourceLocation recipeCategoryId,
            List<IngredientStack> inputs,
            List<IngredientStack> outputs,
            ResourceLocation machineIcon,
            boolean isMultiblock,
            com.gtceu.calcboard.api.type.GTVoltageTier targetTier,
            List<com.gtceu.calcboard.api.catalog.MachineAddon> addons,
            List<ResourceLocation> availableWorkstations,
            int parallel,
            int customParallel,
            SteamMode steamMode,
            OverclockMode overclockMode,
            boolean isGenerator,
            com.gtceu.calcboard.api.property.NodePropertyStore properties,
            com.gtceu.calcboard.api.model.RecipeSpec baseSpec
    ) {
        public RecipeSnapshot(
                String name,
                double baseDurationTicks,
                double baseEUt,
                com.gtceu.calcboard.api.type.GTVoltageTier recipeTier,
                ResourceLocation recipeCategoryId,
                List<IngredientStack> inputs,
                List<IngredientStack> outputs,
                ResourceLocation machineIcon,
                boolean isMultiblock,
                com.gtceu.calcboard.api.type.GTVoltageTier targetTier,
                List<com.gtceu.calcboard.api.catalog.MachineAddon> addons,
                List<ResourceLocation> availableWorkstations,
                int parallel,
                int customParallel,
                SteamMode steamMode,
                OverclockMode overclockMode,
                boolean isGenerator,
                com.gtceu.calcboard.api.property.NodePropertyStore properties
        ) {
            this(name, baseDurationTicks, baseEUt, recipeTier, recipeCategoryId, inputs, outputs,
                    machineIcon, isMultiblock, targetTier, addons, availableWorkstations,
                    parallel, customParallel, steamMode, overclockMode, isGenerator, properties, null);
        }

        public RecipeSnapshot(
                String name,
                double baseDurationTicks,
                double baseEUt,
                com.gtceu.calcboard.api.type.GTVoltageTier recipeTier,
                ResourceLocation recipeCategoryId,
                List<IngredientStack> inputs,
                List<IngredientStack> outputs
        ) {
            this(name, baseDurationTicks, baseEUt, recipeTier, recipeCategoryId, inputs, outputs,
                    null, false, recipeTier, Collections.emptyList(), Collections.emptyList(),
                    1, 0, SteamMode.NONE, OverclockMode.STANDARD, false, new com.gtceu.calcboard.api.property.NodePropertyStore(), null);
        }

        public static RecipeSnapshot of(RecipeNode node) {
            List<IngredientStack> inList = new ArrayList<>();
            for (IngredientStack in : node.getInputs()) {
                inList.add(in.copy());
            }
            List<IngredientStack> outList = new ArrayList<>();
            for (IngredientStack out : node.getOutputs()) {
                outList.add(out.copy());
            }
            List<com.gtceu.calcboard.api.catalog.MachineAddon> addonList = new ArrayList<>();
            for (com.gtceu.calcboard.api.catalog.MachineAddon a : node.getAddons()) {
                addonList.add(a.copy());
            }
            List<ResourceLocation> wsList = new ArrayList<>(node.getAvailableWorkstations());
            com.gtceu.calcboard.api.property.NodePropertyStore propsCopy = node.getProperties().copy();

            return new RecipeSnapshot(
                    node.getRawName(),
                    node.getBaseDurationTicks(),
                    node.getBaseEUt(),
                    node.getRecipeTier(),
                    node.getRecipeCategoryId(),
                    inList,
                    outList,
                    node.getMachineIcon(),
                    node.isMultiblock(),
                    node.getTargetTier(),
                    addonList,
                    wsList,
                    node.getParallel(),
                    node.getCustomParallel(),
                    node.getSteamMode(),
                    node.getOverclockMode(),
                    node.isGenerator(),
                    propsCopy,
                    node.getBaseSpec()
            );
        }

        public void applyTo(RecipeNode node) {
            IModAdapter oldAdapter = ModAdapterRegistry.getAdapterForNode(node);

            if (baseSpec != null) {
                node.setBaseSpec(baseSpec);
            } else {
                node.setBaseSpecOnly(null);
                node.getInputs().clear();
                for (IngredientStack in : inputs) {
                    node.getInputs().add(in.copy());
                }
                node.getOutputs().clear();
                for (IngredientStack out : outputs) {
                    node.getOutputs().add(out.copy());
                }
                node.markPortsDirty();
            }

            node.setName(name);
            node.setBaseDurationTicks(baseDurationTicks);
            node.setBaseEUt(baseEUt);
            node.setRecipeTier(recipeTier);
            node.setRecipeCategoryId(recipeCategoryId);

            node.setMachineIcon(machineIcon);
            node.setMultiblock(isMultiblock);
            node.setTargetTier(targetTier);
            node.setParallel(parallel);
            node.setCustomParallel(customParallel);
            node.setSteamMode(steamMode);
            node.setOverclockMode(overclockMode);
            node.setGenerator(isGenerator);

            if (availableWorkstations != null) {
                node.getAvailableWorkstations().clear();
                node.getAvailableWorkstations().addAll(availableWorkstations);
            }

            node.getAddons().clear();
            if (addons != null) {
                for (com.gtceu.calcboard.api.catalog.MachineAddon a : addons) {
                    node.getAddons().add(a.copy());
                }
            }

            if (properties != null) {
                node.getProperties().copyFrom(properties);
            }

            IModAdapter newAdapter = ModAdapterRegistry.getAdapterForNode(node);
            if (oldAdapter != null && oldAdapter != newAdapter) {
                oldAdapter.onDetach(node);
                if (newAdapter != null) {
                    newAdapter.onAttach(node);
                }
            }

            if (baseSpec != null) {
                node.syncProjectedPorts();
            } else {
                node.markPortsDirty();
            }
            node.markOverclockDirty();
            node.markOperationalDirty();
        }
    }

    private final String nodeId;
    private final RecipeSnapshot oldRecipe;
    private final RecipeSnapshot newRecipe;
    private final List<FlowGraph.ConnectionEdge> oldEdges;
    private final List<FlowGraph.ConnectionEdge> newEdges;

    public SwitchRecipeCommand(
            String nodeId,
            RecipeSnapshot oldRecipe,
            RecipeSnapshot newRecipe,
            List<FlowGraph.ConnectionEdge> oldEdges,
            List<FlowGraph.ConnectionEdge> newEdges
    ) {
        this.nodeId = nodeId;
        this.oldRecipe = oldRecipe;
        this.newRecipe = newRecipe;
        this.oldEdges = new ArrayList<>(oldEdges);
        this.newEdges = new ArrayList<>(newEdges);
    }

    @Override
    public void undo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            oldRecipe.applyTo(node);
        }
        graph.removeConnectionIf(e -> e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId));
        for (FlowGraph.ConnectionEdge e : oldEdges) {
            graph.addConnection(e);
        }
        graph.invalidatePortStatsCache();
    }

    @Override
    public void redo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            newRecipe.applyTo(node);
        }
        graph.removeConnectionIf(e -> e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId));
        for (FlowGraph.ConnectionEdge e : newEdges) {
            graph.addConnection(e);
        }
        graph.invalidatePortStatsCache();
    }

    @Override
    public String getDescription() {
        return "Switch recipe for " + (newRecipe != null ? newRecipe.name() : "node");
    }
}
