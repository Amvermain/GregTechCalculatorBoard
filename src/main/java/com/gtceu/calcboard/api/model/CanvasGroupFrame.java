package com.gtceu.calcboard.api.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Visual canvas group frame that encloses multiple machine/reroute nodes.
 * Supports theme colors, title headers, sticky notes, group movement, and module roundtrip conversion.
 */
public class CanvasGroupFrame {

    // Preset theme color palette (ARGB)
    public static final int COLOR_BLUE = 0xFF3B82F6;
    public static final int COLOR_EMERALD = 0xFF10B981;
    public static final int COLOR_PURPLE = 0xFF8B5CF6;
    public static final int COLOR_AMBER = 0xFFF59E0B;
    public static final int COLOR_ROSE = 0xFFF43F5E;
    public static final int COLOR_SLATE = 0xFF64748B;
    public static final int COLOR_CYAN = 0xFF06B6D4;
    public static final int COLOR_LIME = 0xFF84CC16;

    public static final int[] PALETTE = new int[]{
            COLOR_BLUE, COLOR_EMERALD, COLOR_PURPLE, COLOR_AMBER, COLOR_ROSE, COLOR_SLATE, COLOR_CYAN, COLOR_LIME
    };

    public static final double HEADER_HEIGHT = 24.0;
    public static final double MIN_WIDTH = 120.0;
    public static final double MIN_HEIGHT = 80.0;
    public static final double DEFAULT_PADDING = 24.0;

    private String id;
    private String title;
    private int color;
    private double posX;
    private double posY;
    private double width;
    private double height;
    private String note;
    private final Set<String> containedNodeIds = new LinkedHashSet<>();
    private boolean isCompoundFrame = false;
    private String compoundGroupId = "";

    public CanvasGroupFrame(String id, String title, int color, double posX, double posY, double width, double height) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title != null ? title : "Group Frame";
        this.color = color != 0 ? color : COLOR_BLUE;
        this.posX = posX;
        this.posY = posY;
        this.width = Math.max(MIN_WIDTH, width);
        this.height = Math.max(MIN_HEIGHT, height);
        this.note = "";
    }

    public static CanvasGroupFrame createFromNodes(String title, Collection<RecipeNode> nodes, int color) {
        return createFromElements(title, nodes, Collections.emptyList(), color);
    }

    public static CanvasGroupFrame createFromElements(String title, Collection<RecipeNode> nodes, Collection<CanvasStickyNote> notes, int color) {
        String frameId = UUID.randomUUID().toString();
        CanvasGroupFrame frame = new CanvasGroupFrame(frameId, title, color, 0, 0, MIN_WIDTH, MIN_HEIGHT);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        int count = 0;

        if (nodes != null) {
            for (RecipeNode n : nodes) {
                frame.addNode(n.getId());
                minX = Math.min(minX, n.getPosX());
                minY = Math.min(minY, n.getPosY());
                maxX = Math.max(maxX, n.getPosX() + n.getCardWidth());
                int h = n.getCardHeight() > 0 ? n.getCardHeight() : (n.isReroute() ? 32 : 160);
                maxY = Math.max(maxY, n.getPosY() + h);
                count++;
            }
        }
        if (notes != null) {
            for (CanvasStickyNote note : notes) {
                minX = Math.min(minX, note.getPosX());
                minY = Math.min(minY, note.getPosY());
                maxX = Math.max(maxX, note.getPosX() + note.getWidth());
                maxY = Math.max(maxY, note.getPosY() + note.getHeight());
                count++;
            }
        }

        if (count > 0) {
            double padding = DEFAULT_PADDING;
            frame.posX = minX - padding;
            frame.posY = minY - padding - HEADER_HEIGHT;
            frame.width = Math.max(MIN_WIDTH, (maxX - minX) + padding * 2);
            frame.height = Math.max(MIN_HEIGHT, (maxY - minY) + padding * 2 + HEADER_HEIGHT);
        }
        return frame;
    }

    public List<RecipeNode> getEnclosedNodes(FlowGraph graph) {
        List<RecipeNode> result = new ArrayList<>();
        if (graph == null) return result;
        for (RecipeNode n : graph.getNodes()) {
            double cx = n.getPosX() + n.getCardWidth() / 2.0;
            double cy = n.getPosY() + (n.getCardHeight() > 0 ? n.getCardHeight() / 2.0 : 40.0);
            if (isPointInside(cx, cy)) {
                result.add(n);
            }
        }
        return result;
    }

    public List<CanvasStickyNote> getEnclosedNotes(FlowGraph graph) {
        List<CanvasStickyNote> result = new ArrayList<>();
        if (graph == null) return result;
        for (CanvasStickyNote note : graph.getStickyNotes()) {
            double cx = note.getPosX() + note.getWidth() / 2.0;
            double cy = note.getPosY() + note.getHeight() / 2.0;
            if (isPointInside(cx, cy)) {
                result.add(note);
            }
        }
        return result;
    }

    public void recomputeBounds(Collection<RecipeNode> nodes, double padding) {
        if (nodes == null || nodes.isEmpty()) return;

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        int count = 0;
        for (RecipeNode n : nodes) {
            if (containedNodeIds.contains(n.getId())) {
                minX = Math.min(minX, n.getPosX());
                minY = Math.min(minY, n.getPosY());
                maxX = Math.max(maxX, n.getPosX() + n.getCardWidth());
                int h = n.getCardHeight() > 0 ? n.getCardHeight() : (n.isReroute() ? 32 : 160);
                maxY = Math.max(maxY, n.getPosY() + h);
                count++;
            }
        }

        if (count > 0) {
            this.posX = minX - padding;
            this.posY = minY - padding - HEADER_HEIGHT;
            this.width = Math.max(MIN_WIDTH, (maxX - minX) + padding * 2);
            this.height = Math.max(MIN_HEIGHT, (maxY - minY) + padding * 2 + HEADER_HEIGHT);
        }
    }

    public void moveBy(double dx, double dy) {
        this.posX += dx;
        this.posY += dy;
    }

    public boolean isPointInside(double canvasX, double canvasY) {
        return canvasX >= posX && canvasX <= posX + width && canvasY >= posY && canvasY <= posY + height;
    }

    public boolean isPointInHeader(double canvasX, double canvasY) {
        return canvasX >= posX && canvasX <= posX + width && canvasY >= posY && canvasY <= posY + HEADER_HEIGHT;
    }

    public boolean isPointInResizeGrip(double canvasX, double canvasY) {
        double gripSize = 14.0;
        return canvasX >= posX + width - gripSize && canvasX <= posX + width
                && canvasY >= posY + height - gripSize && canvasY <= posY + height;
    }

    public boolean containsNode(String nodeId) {
        return containedNodeIds.contains(nodeId);
    }

    public void addNode(String nodeId) {
        if (nodeId != null) {
            containedNodeIds.add(nodeId);
        }
    }

    public void removeNode(String nodeId) {
        containedNodeIds.remove(nodeId);
    }

    public void cycleColor() {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == this.color) {
                this.color = PALETTE[(i + 1) % PALETTE.length];
                return;
            }
        }
        this.color = PALETTE[0];
    }

    // =========================================================================
    // NBT Serialization
    // =========================================================================

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("title", title);
        tag.putInt("color", color);
        tag.putDouble("posX", posX);
        tag.putDouble("posY", posY);
        tag.putDouble("width", width);
        tag.putDouble("height", height);
        tag.putString("note", note != null ? note : "");
        if (isCompoundFrame) {
            tag.putBoolean("isCompoundFrame", true);
            if (compoundGroupId != null && !compoundGroupId.isEmpty()) {
                tag.putString("compoundGroupId", compoundGroupId);
            }
        }

        ListTag nodesTag = new ListTag();
        for (String nid : containedNodeIds) {
            nodesTag.add(StringTag.valueOf(nid));
        }
        tag.put("nodes", nodesTag);
        return tag;
    }

    public static CanvasGroupFrame deserializeNBT(CompoundTag tag) {
        String id = tag.getString("id");
        String title = tag.getString("title");
        int color = tag.getInt("color");
        double posX = tag.getDouble("posX");
        double posY = tag.getDouble("posY");
        double width = tag.getDouble("width");
        double height = tag.getDouble("height");
        String note = tag.getString("note");

        CanvasGroupFrame frame = new CanvasGroupFrame(id, title, color, posX, posY, width, height);
        frame.setNote(note);
        if (tag.getBoolean("isCompoundFrame")) {
            frame.setCompoundFrame(true);
            frame.setCompoundGroupId(tag.getString("compoundGroupId"));
        }

        if (tag.contains("nodes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("nodes", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                frame.addNode(list.getString(i));
            }
        }
        return frame;
    }

    public CanvasGroupFrame copy() {
        CompoundTag tag = serializeNBT();
        return deserializeNBT(tag);
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "Group Frame";
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public double getPosX() {
        return posX;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public double getPosY() {
        return posY;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.max(MIN_WIDTH, width);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(MIN_HEIGHT, height);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note != null ? note : "";
    }

    public Set<String> getContainedNodeIds() {
        return containedNodeIds;
    }

    public boolean isCompoundFrame() {
        return isCompoundFrame;
    }

    public void setCompoundFrame(boolean compoundFrame) {
        isCompoundFrame = compoundFrame;
    }

    public String getCompoundGroupId() {
        return compoundGroupId;
    }

    public void setCompoundGroupId(String compoundGroupId) {
        this.compoundGroupId = compoundGroupId != null ? compoundGroupId : "";
    }
}

