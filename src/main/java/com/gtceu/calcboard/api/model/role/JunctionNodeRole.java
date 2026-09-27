package com.gtceu.calcboard.api.model.role;

import com.gtceu.calcboard.api.model.CrossPageExportTarget;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeJunctionHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.FlowSplitMode;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operational role representing a flow distribution, buffering, or void junction node.
 * <p>
 * Manages external supply/drain rates, flow split modes (proportional, equal, weighted), and void sink mechanics.
 */
public class JunctionNodeRole implements INodeRole {

    private RecipeNode owner;
    private SupplyMode supplyMode = SupplyMode.NONE;
    private double externalSupplyRate = 0.0;
    private double externalDrainRate = 0.0;
    private boolean isBuffer = false;
    private double bufferSize = 0.0;
    private FlowSplitMode splitMode = FlowSplitMode.PROPORTIONAL;
    private IngredientStack boundIngredient = null;

    private final List<CrossPageExportTarget> exportTargets = new ArrayList<>();
    private String linkedSourcePageId = "";
    private String linkedSourceNodeId = "";
    private double allocatedInputRate = 0.0;
    private double allocatedExportRate = 0.0;

    public JunctionNodeRole() {}

    public JunctionNodeRole(SupplyMode supplyMode, double externalSupplyRate, double externalDrainRate) {
        this.supplyMode = supplyMode != null ? supplyMode : SupplyMode.NONE;
        this.externalSupplyRate = Math.max(0.0, externalSupplyRate);
        this.externalDrainRate = Math.max(0.0, externalDrainRate);
    }

    @Override
    public NodeRoleType getRoleType() {
        return NodeRoleType.JUNCTION;
    }

    @Override
    public void attach(RecipeNode owner) {
        this.owner = owner;
        if (owner != null) {
            if (this.isBuffer) {
                owner.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_IS_BUFFER, this.isBuffer);
                owner.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_BUFFER_SIZE, this.bufferSize);
            }
            if (this.splitMode != null && this.splitMode != FlowSplitMode.PROPORTIONAL) {
                owner.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_SPLIT_MODE, this.splitMode);
            }
            if (boundIngredient != null) {
                NodeJunctionHelper.bindRerouteIngredient(owner, boundIngredient);
            } else if (owner.getRerouteIngredient() != null) {
                this.boundIngredient = owner.getRerouteIngredient().copy();
            }
        }
    }

    @Override
    public void detach() {
        this.owner = null;
    }

    @Override
    public RecipeNode getOwner() {
        return owner;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isOperational(FlowGraph graph) {
        return true;
    }

    @Override
    public double getSingleMachinePower() {
        return 0.0;
    }

    @Override
    public double getTotalPower() {
        return 0.0;
    }

    @Override
    public double getEffectiveDurationSeconds() {
        return 1.0;
    }

    @Override
    public double getCyclesPerSecond() {
        return 1.0;
    }

    @Override
    public int getDefaultCardWidth() {
        return 32;
    }

    @Override
    public int getDefaultCardHeight() {
        return 32;
    }

    @Override
    public boolean isFixedSize() {
        return true;
    }

    public SupplyMode getSupplyMode() {
        return supplyMode != null ? supplyMode : SupplyMode.NONE;
    }

    public void setSupplyMode(SupplyMode supplyMode) {
        this.supplyMode = supplyMode != null ? supplyMode : SupplyMode.NONE;
        if (this.supplyMode != SupplyMode.LINKED_JUNCTION) {
            this.allocatedInputRate = 0.0;
        }
    }

    public boolean isExternalSupply() {
        return getSupplyMode().isExternal();
    }

    public boolean isInfiniteSupply() {
        return getSupplyMode() == SupplyMode.INFINITE;
    }

    public boolean isVoidSink() {
        return getSupplyMode() == SupplyMode.VOID_SINK;
    }

    public boolean isFixedDrain() {
        return getSupplyMode() == SupplyMode.FIXED_DRAIN;
    }

    public boolean isLinkedJunction() {
        return getSupplyMode() == SupplyMode.LINKED_JUNCTION;
    }

    public double getExternalSupplyRate() {
        if (supplyMode == SupplyMode.LINKED_JUNCTION) {
            return allocatedInputRate;
        }
        return externalSupplyRate;
    }

    public void setExternalSupplyRate(double rate) {
        this.externalSupplyRate = Math.max(0.0, rate);
    }

    public double getAllocatedInputRate() {
        return allocatedInputRate;
    }

    public void setAllocatedInputRate(double rate) {
        this.allocatedInputRate = Math.max(0.0, rate);
    }

    public double getAllocatedExportRate() {
        return allocatedExportRate;
    }

    public void setAllocatedExportRate(double rate) {
        this.allocatedExportRate = Math.max(0.0, rate);
    }

    public String getLinkedSourcePageId() {
        return linkedSourcePageId != null ? linkedSourcePageId : "";
    }

    public void setLinkedSourcePageId(String pageId) {
        this.linkedSourcePageId = pageId != null ? pageId.trim() : "";
    }

    public String getLinkedSourceNodeId() {
        return linkedSourceNodeId != null ? linkedSourceNodeId : "";
    }

    public void setLinkedSourceNodeId(String nodeId) {
        this.linkedSourceNodeId = nodeId != null ? nodeId.trim() : "";
    }

    public void setLinkedSource(String pageId, String nodeId) {
        setLinkedSourcePageId(pageId);
        setLinkedSourceNodeId(nodeId);
    }

    public List<CrossPageExportTarget> getExportTargets() {
        return Collections.unmodifiableList(exportTargets);
    }

    public void setExportTargets(List<CrossPageExportTarget> targets) {
        this.exportTargets.clear();
        if (targets != null) {
            for (CrossPageExportTarget t : targets) {
                addExportTarget(t);
            }
        }
    }

    public void addExportTarget(CrossPageExportTarget target) {
        if (target == null) return;
        this.exportTargets.removeIf(t -> t.targetPageId().equals(target.targetPageId()));
        this.exportTargets.add(target);
    }

    public void removeExportTarget(String targetPageId) {
        if (targetPageId == null || targetPageId.isBlank()) return;
        this.exportTargets.removeIf(t -> t.targetPageId().equals(targetPageId));
    }

    public double getExternalDrainRate() {
        return externalDrainRate;
    }

    public void setExternalDrainRate(double rate) {
        this.externalDrainRate = Math.max(0.0, rate);
    }

    public boolean isBuffer() {
        if (owner != null) {
            return owner.getProperties().get(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_IS_BUFFER);
        }
        return isBuffer;
    }

    public void setBuffer(boolean buffer) {
        this.isBuffer = buffer;
        if (owner != null) {
            owner.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_IS_BUFFER, buffer);
        }
    }

    public double getBufferSize() {
        if (owner != null) {
            return owner.getProperties().get(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_BUFFER_SIZE);
        }
        return bufferSize;
    }

    public void setBufferSize(double size) {
        this.bufferSize = Math.max(0.0, size);
        if (owner != null) {
            owner.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_BUFFER_SIZE, this.bufferSize);
        }
    }

    public FlowSplitMode getSplitMode() {
        if (owner != null) {
            return owner.getProperties().get(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_SPLIT_MODE);
        }
        return splitMode != null ? splitMode : FlowSplitMode.PROPORTIONAL;
    }

    public void setSplitMode(FlowSplitMode splitMode) {
        this.splitMode = splitMode != null ? splitMode : FlowSplitMode.PROPORTIONAL;
        if (owner != null) {
            owner.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.JUNCTION_SPLIT_MODE, this.splitMode);
        }
    }

    public IngredientStack getBoundIngredient() {
        return boundIngredient;
    }

    public void setBoundIngredient(IngredientStack boundIngredient) {
        this.boundIngredient = boundIngredient != null ? boundIngredient.copy() : null;
    }

    public void bindIngredient(IngredientStack stack) {
        this.boundIngredient = stack != null ? stack.copy() : null;
        if (owner != null) {
            NodeJunctionHelper.bindRerouteIngredient(owner, stack);
        }
    }

    public void unbindIngredient() {
        this.boundIngredient = null;
        if (owner != null) {
            NodeJunctionHelper.unbindRerouteIngredient(owner);
        }
    }

    public double getChargeDuration(FlowGraph graph) {
        if (owner == null) return 0.0;
        return NodeJunctionHelper.getJunctionChargeDuration(owner, graph);
    }

    @Override
    public void serializeRoleNBT(CompoundTag tag, Set<FlowGraph> visitedGraphs, int depth) {
        tag.putBoolean("isReroute", true);
        if (supplyMode != SupplyMode.NONE) {
            tag.putString("supplyMode", supplyMode.name());
        }
        if (externalSupplyRate > 0.0) {
            tag.putDouble("externalSupplyRate", externalSupplyRate);
        }
        if (externalDrainRate > 0.0) {
            tag.putDouble("externalDrainRate", externalDrainRate);
        }
        if (isBuffer) {
            tag.putBoolean("isBuffer", true);
        }
        if (bufferSize > 0.0) {
            tag.putDouble("bufferSize", bufferSize);
        }
        if (splitMode != FlowSplitMode.PROPORTIONAL) {
            tag.putString("splitMode", splitMode.name());
        }
        if (boundIngredient != null) {
            tag.put("boundIngredient", boundIngredient.serializeNBT());
        }
        if (linkedSourcePageId != null && !linkedSourcePageId.isBlank()) {
            tag.putString("linkedSourcePage", linkedSourcePageId);
        }
        if (linkedSourceNodeId != null && !linkedSourceNodeId.isBlank()) {
            tag.putString("linkedSourceNode", linkedSourceNodeId);
        }
        if (!exportTargets.isEmpty()) {
            ListTag list = new ListTag();
            for (CrossPageExportTarget target : exportTargets) {
                CompoundTag targetTag = new CompoundTag();
                targetTag.putString("pageId", target.targetPageId());
                targetTag.putInt("priority", target.priority());
                targetTag.putDouble("limit", target.fixedLimit());
                list.add(targetTag);
            }
            tag.put("exportTargets", list);
        }
    }

    @Override
    public void deserializeRoleNBT(CompoundTag tag) {
        if (tag == null) return;
        if (tag.contains("supplyMode")) {
            try {
                this.supplyMode = SupplyMode.valueOf(tag.getString("supplyMode"));
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("externalSupplyRate")) {
            this.externalSupplyRate = tag.getDouble("externalSupplyRate");
        }
        if (tag.contains("externalDrainRate")) {
            this.externalDrainRate = tag.getDouble("externalDrainRate");
        }
        if (tag.contains("isBuffer")) {
            this.isBuffer = tag.getBoolean("isBuffer");
        }
        if (tag.contains("bufferSize")) {
            this.bufferSize = tag.getDouble("bufferSize");
        }
        if (tag.contains("splitMode")) {
            try {
                this.splitMode = FlowSplitMode.valueOf(tag.getString("splitMode"));
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("boundIngredient")) {
            this.boundIngredient = IngredientStack.deserializeNBT(tag.getCompound("boundIngredient"));
        }
        if (tag.contains("linkedSourcePage")) {
            this.linkedSourcePageId = tag.getString("linkedSourcePage");
        }
        if (tag.contains("linkedSourceNode")) {
            this.linkedSourceNodeId = tag.getString("linkedSourceNode");
        }
        if (tag.contains("exportTargets", Tag.TAG_LIST)) {
            this.exportTargets.clear();
            ListTag list = tag.getList("exportTargets", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag targetTag = list.getCompound(i);
                String pageId = targetTag.getString("pageId");
                int pri = targetTag.getInt("priority");
                double limit = targetTag.getDouble("limit");
                if (!pageId.isBlank()) {
                    this.exportTargets.add(new CrossPageExportTarget(pageId, pri, limit));
                }
            }
        }
    }

    @Override
    public NodeCalculationSnapshot captureSnapshot(FlowGraph graph) {
        if (owner == null) return NodeCalculationSnapshot.EMPTY;
        Map<Integer, Double> inRates = new HashMap<>();
        for (int i = 0; i < owner.getInputs().size(); i++) {
            inRates.put(i, owner.getInputSlotRate(i, true));
        }
        Map<Integer, Double> outRates = new HashMap<>();
        for (int i = 0; i < owner.getOutputs().size(); i++) {
            outRates.put(i, owner.getOutputSlotRate(i, true));
        }
        return new NodeCalculationSnapshot(
            owner.getId(),
            NodeRoleType.JUNCTION,
            1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1,
            EnergyType.NONE, true, false, List.of(),
            inRates, outRates, Map.of(), Map.of()
        );
    }

    @Override
    public JunctionNodeRole copy(Set<FlowGraph> visitedGraphs, int depth) {
        JunctionNodeRole cp = new JunctionNodeRole(this.getSupplyMode(), this.externalSupplyRate, this.getExternalDrainRate());
        cp.isBuffer = this.isBuffer();
        cp.bufferSize = this.getBufferSize();
        cp.splitMode = this.getSplitMode();
        cp.boundIngredient = this.getBoundIngredient() != null ? this.getBoundIngredient().copy() : null;
        cp.linkedSourcePageId = this.linkedSourcePageId;
        cp.linkedSourceNodeId = this.linkedSourceNodeId;
        cp.allocatedInputRate = this.allocatedInputRate;
        cp.allocatedExportRate = this.allocatedExportRate;
        for (CrossPageExportTarget t : this.exportTargets) {
            cp.exportTargets.add(new CrossPageExportTarget(t.targetPageId(), t.priority(), t.fixedLimit()));
        }
        return cp;
    }
}
