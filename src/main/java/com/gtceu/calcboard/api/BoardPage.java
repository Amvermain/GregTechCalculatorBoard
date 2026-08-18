package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents a single page / preset tab on the Calculator Board.
 * Each page holds its own FlowGraph, unique name, and independent canvas viewport coordinates.
 */
public class BoardPage {
    private final String id;
    private String name;
    private final FlowGraph graph;
    private double panX = 40.0;
    private double panY = 40.0;
    private double zoom = 1.0;
    private final com.gtceu.calcboard.api.history.HistoryManager historyManager = new com.gtceu.calcboard.api.history.HistoryManager();

    public BoardPage(String id, String name, FlowGraph graph) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name != null && !name.isEmpty() ? name : "Page";
        this.graph = graph != null ? graph : new FlowGraph();
    }

    public com.gtceu.calcboard.api.history.HistoryManager getHistoryManager() {
        return historyManager;
    }

    public static BoardPage createDefault(String name) {
        return new BoardPage(UUID.randomUUID().toString(), name, new FlowGraph());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isEmpty() ? name : "Page";
    }

    public FlowGraph getGraph() {
        return graph;
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
        this.zoom = Math.max(0.2, Math.min(3.0, zoom));
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putDouble("panX", panX);
        tag.putDouble("panY", panY);
        tag.putDouble("zoom", zoom);
        tag.put("graph", graph.serializeNBT(panX, panY, zoom));
        return tag;
    }

    public static BoardPage deserializeNBT(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        FlowGraph graph = tag.contains("graph") ? FlowGraph.deserializeNBT(tag.getCompound("graph")) : new FlowGraph();

        BoardPage page = new BoardPage(id, name, graph);
        if (tag.contains("panX")) page.panX = tag.getDouble("panX");
        if (tag.contains("panY")) page.panY = tag.getDouble("panY");
        if (tag.contains("zoom")) page.zoom = Math.max(0.2, Math.min(3.0, tag.getDouble("zoom")));
        return page;
    }
}
