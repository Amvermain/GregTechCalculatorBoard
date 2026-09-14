package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchChangeTierCommand implements BoardCommand {

    public record NodeTierSnapshot(
            String nodeId,
            GTVoltageTier targetTier,
            ResourceLocation machineIcon,
            List<MachineAddon> addons
    ) {
        public static NodeTierSnapshot of(RecipeNode node) {
            List<MachineAddon> addonCopies = new ArrayList<>();
            for (MachineAddon a : node.getAddons()) {
                addonCopies.add(a.copy());
            }
            return new NodeTierSnapshot(node.getId(), node.getTargetTier(), node.getMachineIcon(), addonCopies);
        }

        public void applyTo(RecipeNode node) {
            node.getAddons().clear();
            for (MachineAddon a : addons) {
                node.getAddons().add(a.copy());
            }
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null) {
                adapter.onAddonsUpdated(node);
            }
            node.setTargetTier(targetTier);
            if (machineIcon != null) {
                node.setMachineIcon(machineIcon);
            }
            node.markOverclockDirty();
        }
    }

    private final List<NodeTierSnapshot> previousSnapshots;
    private final List<NodeTierSnapshot> newSnapshots;
    private final GTVoltageTier targetTier;

    public BatchChangeTierCommand(
            List<NodeTierSnapshot> previousSnapshots,
            List<NodeTierSnapshot> newSnapshots,
            GTVoltageTier targetTier
    ) {
        this.previousSnapshots = Collections.unmodifiableList(new ArrayList<>(previousSnapshots));
        this.newSnapshots = Collections.unmodifiableList(new ArrayList<>(newSnapshots));
        this.targetTier = targetTier;
    }

    @Override
    public void undo(FlowGraph graph) {
        for (NodeTierSnapshot snap : previousSnapshots) {
            RecipeNode node = graph.findNodeById(snap.nodeId());
            if (node != null) {
                snap.applyTo(node);
            }
        }
        graph.invalidatePortStatsCache();
    }

    @Override
    public void redo(FlowGraph graph) {
        for (NodeTierSnapshot snap : newSnapshots) {
            RecipeNode node = graph.findNodeById(snap.nodeId());
            if (node != null) {
                snap.applyTo(node);
            }
        }
        graph.invalidatePortStatsCache();
    }

    @Override
    public String getDescription() {
        return "Batch apply " + (targetTier != null ? targetTier.getName() : "tier") + " to " + newSnapshots.size() + " nodes";
    }

    public List<NodeTierSnapshot> getPreviousSnapshots() {
        return previousSnapshots;
    }

    public List<NodeTierSnapshot> getNewSnapshots() {
        return newSnapshots;
    }

    public GTVoltageTier getTargetTier() {
        return targetTier;
    }
}
