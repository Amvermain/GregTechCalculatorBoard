package com.gtceu.calcboard.api.model.role;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.NodePortOriginManager;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operational role representing a compound composite module encapsulating a dedicated subpage.
 * <p>
 * Aggregates internal machines, calculates composite power and duration, and projects boundary pin interfaces.
 */
public class SubPageModuleNodeRole implements INodeRole {

    private RecipeNode owner;
    private String subPageId = "";
    private FlowGraph subGraph = null;
    private final List<String> inputPinNodeIds = new ArrayList<>();
    private final List<String> outputPinNodeIds = new ArrayList<>();
    private int containedMachineCount = 0;
    private double scaleMultiplier = 1.0;
    private double efficiency = 1.0;
    private double baseEUt = 0.0;
    private double baseDurationTicks = 20.0;
    private com.gtceu.calcboard.api.type.GTVoltageTier targetTier = com.gtceu.calcboard.api.type.GTVoltageTier.LV;
    private boolean isGenerator = false;
    private EnergyType energyType = EnergyType.ELECTRIC_EU;
    private final NodePortOriginManager portOriginManager = new NodePortOriginManager();

    public SubPageModuleNodeRole() {}

    public SubPageModuleNodeRole(String subPageId) {
        this.subPageId = subPageId != null ? subPageId : "";
    }

    @Override
    public NodeRoleType getRoleType() {
        return NodeRoleType.MODULE;
    }

    @Override
    public void attach(RecipeNode owner) {
        this.owner = owner;
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
        return baseEUt;
    }

    @Override
    public double getTotalPower() {
        return baseEUt * scaleMultiplier;
    }

    @Override
    public double getEffectiveDurationSeconds() {
        return baseDurationTicks / 20.0;
    }

    @Override
    public double getCyclesPerSecond() {
        return baseDurationTicks > 0.0 ? (20.0 / baseDurationTicks) * scaleMultiplier : 1.0;
    }

    @Override
    public int getDefaultCardWidth() {
        return 245;
    }

    @Override
    public int getDefaultCardHeight() {
        return 0;
    }

    @Override
    public boolean isFixedSize() {
        return false;
    }

    public String getSubPageId() {
        return subPageId != null ? subPageId : "";
    }

    public void setSubPageId(String subPageId) {
        this.subPageId = subPageId != null ? subPageId : "";
    }

    public FlowGraph getSubGraph() {
        return subGraph;
    }

    public void setSubGraph(FlowGraph subGraph) {
        this.subGraph = subGraph;
    }

    public List<String> getInputPinNodeIds() {
        return inputPinNodeIds;
    }

    public List<String> getOutputPinNodeIds() {
        return outputPinNodeIds;
    }

    public int getContainedMachineCount() {
        return containedMachineCount;
    }

    public void setContainedMachineCount(int count) {
        this.containedMachineCount = Math.max(0, count);
    }

    public double getScaleMultiplier() {
        return scaleMultiplier;
    }

    public void setScaleMultiplier(double scale) {
        this.scaleMultiplier = Math.max(0.01, scale);
    }

    public NodePortOriginManager getPortOriginManager() {
        return portOriginManager;
    }

    public double getBaseEUt() {
        return baseEUt;
    }

    public void setBaseEUt(double baseEUt) {
        this.baseEUt = Math.max(0.0, baseEUt);
    }

    public double getBaseDurationTicks() {
        return baseDurationTicks;
    }

    public void setBaseDurationTicks(double baseDurationTicks) {
        this.baseDurationTicks = Math.max(1.0, baseDurationTicks);
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = Math.max(0.0, Math.min(1.0, efficiency));
    }

    public com.gtceu.calcboard.api.type.GTVoltageTier getTargetTier() {
        return targetTier != null ? targetTier : com.gtceu.calcboard.api.type.GTVoltageTier.LV;
    }

    public void setTargetTier(com.gtceu.calcboard.api.type.GTVoltageTier tier) {
        this.targetTier = tier != null ? tier : com.gtceu.calcboard.api.type.GTVoltageTier.LV;
    }

    public boolean isGenerator() {
        return isGenerator;
    }

    public void setGenerator(boolean generator) {
        this.isGenerator = generator;
    }

    public EnergyType getEnergyType() {
        return energyType != null ? energyType : EnergyType.ELECTRIC_EU;
    }

    public void setEnergyType(EnergyType energyType) {
        this.energyType = energyType != null ? energyType : EnergyType.ELECTRIC_EU;
    }

    @Override
    public void serializeRoleNBT(CompoundTag tag, Set<FlowGraph> visitedGraphs, int depth) {
        tag.putBoolean("isModule", true);
        if (subPageId != null && !subPageId.isEmpty()) {
            tag.putString("subPageId", subPageId);
        }
        if (containedMachineCount > 0) {
            tag.putInt("containedMachineCount", containedMachineCount);
        }
        if (Math.abs(scaleMultiplier - 1.0) > 0.001) {
            tag.putDouble("scaleMultiplier", scaleMultiplier);
            tag.putDouble("machineCount", scaleMultiplier);
        }
        if (baseEUt != 0.0) {
            tag.putDouble("baseEUt", baseEUt);
        }
        if (baseDurationTicks != 20.0) {
            tag.putDouble("baseDuration", baseDurationTicks);
        }
        if (isGenerator) {
            tag.putBoolean("isGenerator", true);
        }
        if (targetTier != null) {
            tag.putString("targetTier", targetTier.name());
        }
        if (energyType != null && energyType != EnergyType.ELECTRIC_EU) {
            tag.putString("energyType", energyType.name());
        }
        if (!inputPinNodeIds.isEmpty()) {
            ListTag inPins = new ListTag();
            for (String pid : inputPinNodeIds) {
                inPins.add(net.minecraft.nbt.StringTag.valueOf(pid));
            }
            tag.put("inputPinNodeIds", inPins);
        }
        if (!outputPinNodeIds.isEmpty()) {
            ListTag outPins = new ListTag();
            for (String pid : outputPinNodeIds) {
                outPins.add(net.minecraft.nbt.StringTag.valueOf(pid));
            }
            tag.put("outputPinNodeIds", outPins);
        }
        portOriginManager.serialize(tag);
        if (subGraph != null && depth < 16 && (visitedGraphs == null || visitedGraphs.add(subGraph))) {
            tag.put("subGraph", subGraph.serializeNBT(0, 0, 1.0, visitedGraphs, depth + 1));
        }
    }

    @Override
    public void deserializeRoleNBT(CompoundTag tag) {
        if (tag == null) return;
        if (tag.contains("subPageId")) {
            this.subPageId = tag.getString("subPageId");
        }
        if (tag.contains("containedMachineCount")) {
            this.containedMachineCount = tag.getInt("containedMachineCount");
        }
        if (tag.contains("scaleMultiplier")) {
            this.scaleMultiplier = tag.getDouble("scaleMultiplier");
        } else if (tag.contains("machineCount")) {
            this.scaleMultiplier = tag.getDouble("machineCount");
        }
        if (tag.contains("baseEUt")) {
            this.baseEUt = tag.getDouble("baseEUt");
        }
        if (tag.contains("baseDuration")) {
            this.baseDurationTicks = tag.getDouble("baseDuration");
        }
        if (tag.contains("isGenerator")) {
            this.isGenerator = tag.getBoolean("isGenerator");
        }
        if (tag.contains("targetTier")) {
            try {
                this.targetTier = com.gtceu.calcboard.api.type.GTVoltageTier.valueOf(tag.getString("targetTier"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("energyType")) {
            try {
                this.energyType = EnergyType.valueOf(tag.getString("energyType"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("inputPinNodeIds", Tag.TAG_LIST)) {
            inputPinNodeIds.clear();
            ListTag inPins = tag.getList("inputPinNodeIds", Tag.TAG_STRING);
            for (int i = 0; i < inPins.size(); i++) {
                inputPinNodeIds.add(inPins.getString(i));
            }
        }
        if (tag.contains("outputPinNodeIds", Tag.TAG_LIST)) {
            outputPinNodeIds.clear();
            ListTag outPins = tag.getList("outputPinNodeIds", Tag.TAG_STRING);
            for (int i = 0; i < outPins.size(); i++) {
                outputPinNodeIds.add(outPins.getString(i));
            }
        }
        portOriginManager.deserialize(tag);
        if (tag.contains("subGraph")) {
            this.subGraph = FlowGraph.deserializeNBT(tag.getCompound("subGraph"));
        }
    }

    @Override
    public NodeCalculationSnapshot captureSnapshot(FlowGraph graph) {
        if (owner == null) return NodeCalculationSnapshot.EMPTY;
        boolean op = isOperational(graph);
        double cps = getCyclesPerSecond();
        double effCps = op ? cps * owner.getEfficiency() : 0.0;
        double singlePower = getSingleMachinePower();
        double totPower = op ? getTotalPower() : 0.0;
        double effTotPower = totPower * owner.getEfficiency();
        double durSec = getEffectiveDurationSeconds();
        List<Component> warnings = owner.getOperationalWarnings(graph);

        Map<Integer, Double> inRates = new HashMap<>();
        Map<Integer, Double> effInChances = new HashMap<>();
        for (int i = 0; i < owner.getInputs().size(); i++) {
            inRates.put(i, owner.getInputSlotRate(i, true));
            effInChances.put(i, owner.getEffectiveInputChance(i));
        }
        Map<Integer, Double> outRates = new HashMap<>();
        Map<Integer, Double> effOutChances = new HashMap<>();
        for (int i = 0; i < owner.getOutputs().size(); i++) {
            outRates.put(i, owner.getOutputSlotRate(i, true));
            effOutChances.put(i, owner.getEffectiveOutputChance(i));
        }
        boolean isStarved = effCps < cps * 0.999 && op;
        return new NodeCalculationSnapshot(
            owner.getId(),
            NodeRoleType.MODULE,
            cps,
            effCps,
            singlePower,
            totPower,
            effTotPower,
            durSec,
            1,
            getEnergyType(),
            op,
            isStarved,
            warnings != null ? List.copyOf(warnings) : List.of(),
            inRates,
            outRates,
            effInChances,
            effOutChances
        );
    }

    @Override
    public SubPageModuleNodeRole copy(Set<FlowGraph> visitedGraphs, int depth) {
        SubPageModuleNodeRole cp = new SubPageModuleNodeRole(this.subPageId);
        cp.containedMachineCount = this.containedMachineCount;
        cp.scaleMultiplier = this.scaleMultiplier;
        cp.efficiency = this.efficiency;
        cp.baseEUt = this.baseEUt;
        cp.baseDurationTicks = this.baseDurationTicks;
        cp.targetTier = this.targetTier;
        cp.isGenerator = this.isGenerator;
        cp.energyType = this.energyType;
        cp.inputPinNodeIds.addAll(this.inputPinNodeIds);
        cp.outputPinNodeIds.addAll(this.outputPinNodeIds);
        cp.portOriginManager.copyFrom(this.portOriginManager);

        if (this.subGraph != null && depth < 10) {
            cp.subGraph = this.subGraph.copy(copyVisitedGraphs(visitedGraphs), depth + 1);
        }
        return cp;
    }

    private static Set<FlowGraph> copyVisitedGraphs(Set<FlowGraph> src) {
        Set<FlowGraph> copy = Collections.newSetFromMap(new IdentityHashMap<>());
        if (src != null) {
            copy.addAll(src);
        }
        return copy;
    }
}
