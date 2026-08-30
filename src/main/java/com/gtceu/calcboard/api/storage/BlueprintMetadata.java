package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Metadata container for blueprint packages and shareable codes.
 */
public class BlueprintMetadata {

    private String title;
    private String description;
    private String author;
    private long createdAt;
    private int nodeCount;
    private int machineCount;
    private int connectionCount;
    private double netEuPerTick;
    private double netFePerTick;
    private double netSuPerTick;
    private final List<IngredientStack> primaryInputs = new ArrayList<>();
    private final List<IngredientStack> primaryOutputs = new ArrayList<>();

    public BlueprintMetadata() {
        this.title = "Factory Blueprint";
        this.description = "";
        this.author = "";
        this.createdAt = System.currentTimeMillis();
    }

    public static BlueprintMetadata fromGraph(FlowGraph graph, String title, String description, String author) {
        BlueprintMetadata meta = new BlueprintMetadata();
        meta.setTitle(title != null && !title.trim().isEmpty() ? title.trim() : "Factory Blueprint");
        meta.setDescription(description != null ? description.trim() : "");
        meta.setAuthor(author != null ? author.trim() : "");
        meta.setCreatedAt(System.currentTimeMillis());

        if (graph != null) {
            meta.setNodeCount(graph.getNodes().size());
            meta.setConnectionCount(graph.getConnections().size());

            BalanceSummary summary = FlowGraphSolver.computeSummary(graph);
            meta.setMachineCount(summary.totalMachineCount());
            meta.setNetEuPerTick(summary.totalEUt());
            meta.setNetFePerTick(summary.totalFE());
            meta.setNetSuPerTick(summary.totalSU());

            List<Map.Entry<IngredientStack, Double>> sortedInputs = new ArrayList<>(summary.rawInputs().entrySet());
            sortedInputs.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(6, sortedInputs.size()); i++) {
                IngredientStack base = sortedInputs.get(i).getKey();
                double amt = sortedInputs.get(i).getValue();
                IngredientStack stack = base.isFluid()
                        ? IngredientStack.fluid(base.getId(), base.getDisplayName(), amt)
                        : IngredientStack.item(base.getId(), base.getDisplayName(), amt);
                meta.primaryInputs.add(stack);
            }

            List<Map.Entry<IngredientStack, Double>> sortedOutputs = new ArrayList<>(summary.netOutputs().entrySet());
            sortedOutputs.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(6, sortedOutputs.size()); i++) {
                IngredientStack base = sortedOutputs.get(i).getKey();
                double amt = sortedOutputs.get(i).getValue();
                IngredientStack stack = base.isFluid()
                        ? IngredientStack.fluid(base.getId(), base.getDisplayName(), amt)
                        : IngredientStack.item(base.getId(), base.getDisplayName(), amt);
                meta.primaryOutputs.add(stack);
            }
        }

        return meta;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public int getMachineCount() {
        return machineCount > 0 ? machineCount : nodeCount;
    }

    public void setMachineCount(int machineCount) {
        this.machineCount = machineCount;
    }

    public int getConnectionCount() {
        return connectionCount;
    }

    public void setConnectionCount(int connectionCount) {
        this.connectionCount = connectionCount;
    }

    public double getNetEuPerTick() {
        return netEuPerTick;
    }

    public void setNetEuPerTick(double netEuPerTick) {
        this.netEuPerTick = netEuPerTick;
    }

    public double getNetFePerTick() {
        return netFePerTick;
    }

    public void setNetFePerTick(double netFePerTick) {
        this.netFePerTick = netFePerTick;
    }

    public double getNetSuPerTick() {
        return netSuPerTick;
    }

    public void setNetSuPerTick(double netSuPerTick) {
        this.netSuPerTick = netSuPerTick;
    }

    public List<IngredientStack> getPrimaryInputs() {
        return Collections.unmodifiableList(primaryInputs);
    }

    public List<IngredientStack> getPrimaryOutputs() {
        return Collections.unmodifiableList(primaryOutputs);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", title != null ? title : "");
        tag.putString("description", description != null ? description : "");
        tag.putString("author", author != null ? author : "");
        tag.putLong("createdAt", createdAt);
        tag.putInt("nodeCount", nodeCount);
        tag.putInt("machineCount", machineCount);
        tag.putInt("connectionCount", connectionCount);
        tag.putDouble("netEuPerTick", netEuPerTick);
        tag.putDouble("netFePerTick", netFePerTick);
        tag.putDouble("netSuPerTick", netSuPerTick);

        if (!primaryInputs.isEmpty()) {
            ListTag inList = new ListTag();
            for (IngredientStack in : primaryInputs) {
                inList.add(in.serializeNBT());
            }
            tag.put("primaryInputs", inList);
        }

        if (!primaryOutputs.isEmpty()) {
            ListTag outList = new ListTag();
            for (IngredientStack out : primaryOutputs) {
                outList.add(out.serializeNBT());
            }
            tag.put("primaryOutputs", outList);
        }

        return tag;
    }

    public static BlueprintMetadata deserializeNBT(CompoundTag tag) {
        BlueprintMetadata meta = new BlueprintMetadata();
        if (tag == null) return meta;

        if (tag.contains("title")) meta.setTitle(tag.getString("title"));
        if (tag.contains("description")) meta.setDescription(tag.getString("description"));
        if (tag.contains("author")) meta.setAuthor(tag.getString("author"));
        if (tag.contains("createdAt")) meta.setCreatedAt(tag.getLong("createdAt"));
        if (tag.contains("nodeCount")) meta.setNodeCount(tag.getInt("nodeCount"));
        if (tag.contains("machineCount")) meta.setMachineCount(tag.getInt("machineCount"));
        else if (tag.contains("nodeCount")) meta.setMachineCount(tag.getInt("nodeCount"));
        if (tag.contains("connectionCount")) meta.setConnectionCount(tag.getInt("connectionCount"));
        if (tag.contains("netEuPerTick")) meta.setNetEuPerTick(tag.getDouble("netEuPerTick"));
        if (tag.contains("netFePerTick")) meta.setNetFePerTick(tag.getDouble("netFePerTick"));
        if (tag.contains("netSuPerTick")) meta.setNetSuPerTick(tag.getDouble("netSuPerTick"));

        if (tag.contains("primaryInputs", Tag.TAG_LIST)) {
            ListTag inList = tag.getList("primaryInputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < inList.size(); i++) {
                meta.primaryInputs.add(IngredientStack.deserializeNBT(inList.getCompound(i)));
            }
        }

        if (tag.contains("primaryOutputs", Tag.TAG_LIST)) {
            ListTag outList = tag.getList("primaryOutputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < outList.size(); i++) {
                meta.primaryOutputs.add(IngredientStack.deserializeNBT(outList.getCompound(i)));
            }
        }

        return meta;
    }
}
