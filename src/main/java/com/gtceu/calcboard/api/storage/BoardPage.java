package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.history.HistoryManager;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.IngredientStack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Represents a single page / preset tab on the Calculator Board.
 * Each page holds its own FlowGraph, unique name, folder path, representative icon,
 * pinned state, and independent canvas viewport coordinates.
 */
public class BoardPage {
    private final String id;
    private String name;
    private String folderPath = "";
    private ItemStack representativeIcon = ItemStack.EMPTY;
    private boolean isPinned = true;
    private boolean isFolderCollapsed = false;

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

    public BoardPage(String name) {
        this(UUID.randomUUID().toString(), name, new FlowGraph());
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

    public String getFolderPath() {
        return folderPath != null ? folderPath : "";
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath != null ? folderPath.trim() : "";
    }

    public ItemStack getRepresentativeIcon() {
        return representativeIcon != null ? representativeIcon : ItemStack.EMPTY;
    }

    public void setRepresentativeIcon(ItemStack representativeIcon) {
        this.representativeIcon = representativeIcon != null ? representativeIcon : ItemStack.EMPTY;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        this.isPinned = pinned;
    }

    public boolean isFolderCollapsed() {
        return isFolderCollapsed;
    }

    public void setFolderCollapsed(boolean folderCollapsed) {
        this.isFolderCollapsed = folderCollapsed;
    }

    public ItemStack getEffectiveRepresentativeIcon() {
        if (representativeIcon != null && !representativeIcon.isEmpty()) {
            return representativeIcon;
        }
        if (graph == null) {
            return ItemStack.EMPTY;
        }

        RecipeNode baseNode = graph.findBaseNode();
        ItemStack baseIcon = extractFirstOutputItem(baseNode);
        if (!baseIcon.isEmpty()) {
            return baseIcon;
        }

        if (!graph.getNodes().isEmpty()) {
            return extractFirstOutputItem(graph.getNodes().get(0));
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack extractFirstOutputItem(RecipeNode node) {
        if (node == null || node.getOutputs().isEmpty()) {
            return ItemStack.EMPTY;
        }
        IngredientStack firstOut = node.getOutputs().get(0);
        if (firstOut == null || !firstOut.isItem() || firstOut.getId() == null) {
            return ItemStack.EMPTY;
        }
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(firstOut.getId());
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
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
        tag.putString("folderPath", getFolderPath());
        tag.putBoolean("isPinned", isPinned);
        tag.putBoolean("isFolderCollapsed", isFolderCollapsed);
        if (representativeIcon != null && !representativeIcon.isEmpty()) {
            tag.put("icon", representativeIcon.save(new CompoundTag()));
        }
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
        if (tag.contains("folderPath")) page.folderPath = tag.getString("folderPath");
        if (tag.contains("isPinned")) page.isPinned = tag.getBoolean("isPinned");
        if (tag.contains("isFolderCollapsed")) page.isFolderCollapsed = tag.getBoolean("isFolderCollapsed");
        if (tag.contains("icon", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            page.representativeIcon = ItemStack.of(tag.getCompound("icon"));
        }
        if (tag.contains("panX")) page.panX = tag.getDouble("panX");
        if (tag.contains("panY")) page.panY = tag.getDouble("panY");
        if (tag.contains("zoom")) page.zoom = Math.max(0.2, Math.min(3.0, tag.getDouble("zoom")));
        return page;
    }
}


