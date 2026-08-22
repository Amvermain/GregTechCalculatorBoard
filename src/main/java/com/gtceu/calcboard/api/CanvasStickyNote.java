package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents an independent, standalone Sticky Note / Comment card on the canvas.
 */
public class CanvasStickyNote {

    public static final int COLOR_AMBER = 0xFFF59E0B;
    public static final int COLOR_EMERALD = 0xFF10B981;
    public static final int COLOR_CYAN = 0xFF06B6D4;
    public static final int COLOR_PURPLE = 0xFF8B5CF6;
    public static final int COLOR_ROSE = 0xFFF43F5E;
    public static final int COLOR_SLATE = 0xFF64748B;

    public static final int[] PALETTE = {
            COLOR_AMBER, COLOR_EMERALD, COLOR_CYAN, COLOR_PURPLE, COLOR_ROSE, COLOR_SLATE
    };

    public static final double MIN_WIDTH = 100.0;
    public static final double MIN_HEIGHT = 60.0;
    public static final double HEADER_HEIGHT = 18.0;

    private String id;
    private String title;
    private String content;
    private int color;
    private double posX;
    private double posY;
    private double width;
    private double height;

    public CanvasStickyNote(String id, String title, String content, int color, double posX, double posY, double width, double height) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title != null ? title : "Note";
        this.content = content != null ? content : "";
        this.color = color != 0 ? color : COLOR_AMBER;
        this.posX = posX;
        this.posY = posY;
        this.width = Math.max(MIN_WIDTH, width);
        this.height = Math.max(MIN_HEIGHT, height);
    }

    public static CanvasStickyNote create(String title, String content, int color, double posX, double posY) {
        return new CanvasStickyNote(UUID.randomUUID().toString(), title, content, color, posX, posY, 160, 100);
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
        double gripSize = 12.0;
        return canvasX >= posX + width - gripSize && canvasX <= posX + width
                && canvasY >= posY + height - gripSize && canvasY <= posY + height;
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
        tag.putString("content", content);
        tag.putInt("color", color);
        tag.putDouble("posX", posX);
        tag.putDouble("posY", posY);
        tag.putDouble("width", width);
        tag.putDouble("height", height);
        return tag;
    }

    public static CanvasStickyNote deserializeNBT(CompoundTag tag) {
        if (tag == null) return null;
        String id = tag.getString("id");
        String title = tag.getString("title");
        String content = tag.getString("content");
        int color = tag.getInt("color");
        double posX = tag.getDouble("posX");
        double posY = tag.getDouble("posY");
        double width = tag.getDouble("width");
        double height = tag.getDouble("height");

        return new CanvasStickyNote(id, title, content, color, posX, posY, width, height);
    }

    public CanvasStickyNote copy() {
        return deserializeNBT(serializeNBT());
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title != null ? title : "Note"; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content != null ? content : ""; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public double getPosX() { return posX; }
    public void setPosX(double posX) { this.posX = posX; }
    public double getPosY() { return posY; }
    public void setPosY(double posY) { this.posY = posY; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = Math.max(MIN_WIDTH, width); }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = Math.max(MIN_HEIGHT, height); }
}
