package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;

import net.minecraft.nbt.CompoundTag;

/**
 * Encapsulates a complete blueprint package containing metadata, graph topology, and viewport settings.
 */
public class BlueprintPackage {

    public static final int CURRENT_VERSION = 2;

    private BlueprintMetadata metadata;
    private FlowGraph graph;
    private double panX = 40.0;
    private double panY = 40.0;
    private double zoom = 1.0;

    public BlueprintPackage(BlueprintMetadata metadata, FlowGraph graph, double panX, double panY, double zoom) {
        this.metadata = metadata != null ? metadata : new BlueprintMetadata();
        this.graph = graph != null ? graph : new FlowGraph();
        this.panX = panX;
        this.panY = panY;
        this.zoom = zoom;
    }

    public BlueprintMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(BlueprintMetadata metadata) {
        this.metadata = metadata != null ? metadata : new BlueprintMetadata();
    }

    public FlowGraph getGraph() {
        return graph;
    }

    public void setGraph(FlowGraph graph) {
        this.graph = graph != null ? graph : new FlowGraph();
    }

    public double getPanX() {
        return panX;
    }

    public void setPanX(double panX) {
        this.panX = panX;
    }

    public double getPanY() {
        return panY;
    }

    public void setPanY(double panY) {
        this.panY = panY;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        root.putInt("version", CURRENT_VERSION);
        root.put("meta", metadata.serializeNBT());
        root.putDouble("panX", panX);
        root.putDouble("panY", panY);
        root.putDouble("zoom", zoom);
        root.put("graph", graph.serializeNBT(panX, panY, zoom));
        return root;
    }

    public static BlueprintPackage deserializeNBT(CompoundTag root) {
        if (root == null) return new BlueprintPackage(new BlueprintMetadata(), new FlowGraph(), 40.0, 40.0, 1.0);

        if (root.contains("version") && root.contains("graph")) {
            BlueprintMetadata meta = root.contains("meta")
                    ? BlueprintMetadata.deserializeNBT(root.getCompound("meta"))
                    : new BlueprintMetadata();
            FlowGraph graph = FlowGraph.deserializeNBT(root.getCompound("graph"));
            double panX = root.contains("panX") ? root.getDouble("panX") : 40.0;
            double panY = root.contains("panY") ? root.getDouble("panY") : 40.0;
            double zoom = root.contains("zoom") ? root.getDouble("zoom") : 1.0;
            return new BlueprintPackage(meta, graph, panX, panY, zoom);
        }

        // Legacy V1 format fallback (root tag is directly FlowGraph serialized data)
        FlowGraph graph = FlowGraph.deserializeNBT(root);
        double panX = root.contains("panX") ? root.getDouble("panX") : 40.0;
        double panY = root.contains("panY") ? root.getDouble("panY") : 40.0;
        double zoom = root.contains("zoom") ? root.getDouble("zoom") : 1.0;
        BlueprintMetadata meta = BlueprintMetadata.fromGraph(graph, "Imported Factory", "", "");
        return new BlueprintPackage(meta, graph, panX, panY, zoom);
    }
}
