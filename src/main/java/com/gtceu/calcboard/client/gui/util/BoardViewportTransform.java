package com.gtceu.calcboard.client.gui.util;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.BoardGuiScale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

/**
 * Viewport transformation engine decoupling the Calculator Board coordinate space
 * from Minecraft's global GUI scale.
 */
public class BoardViewportTransform {

    private int windowPhysW;
    private int windowPhysH;
    private int gameGuiScale;
    private int effectiveBoardScale;
    private double scaleRatio = 1.0;
    private int virtualWidth;
    private int virtualHeight;

    public BoardViewportTransform() {}

    public BoardViewportTransform(int gameGuiScale, int effectiveBoardScale, int windowPhysW, int windowPhysH) {
        this.gameGuiScale = Math.max(1, gameGuiScale);
        this.effectiveBoardScale = Math.max(1, effectiveBoardScale);
        this.windowPhysW = windowPhysW;
        this.windowPhysH = windowPhysH;
        this.scaleRatio = (double) this.effectiveBoardScale / (double) this.gameGuiScale;
        this.virtualWidth = Math.max(320, (int) Math.floor((double) windowPhysW / this.effectiveBoardScale));
        this.virtualHeight = Math.max(240, (int) Math.floor((double) windowPhysH / this.effectiveBoardScale));
    }

    public void update(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return;
        this.windowPhysW = mc.getWindow().getWidth();
        this.windowPhysH = mc.getWindow().getHeight();
        this.gameGuiScale = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));

        BoardGuiScale pref = BoardManager.getInstance().getBoardGuiScale();
        this.effectiveBoardScale = pref.resolveEffectiveScale(gameGuiScale, windowPhysW, windowPhysH);
        this.scaleRatio = (double) this.effectiveBoardScale / (double) this.gameGuiScale;

        this.virtualWidth = Math.max(320, (int) Math.floor((double) windowPhysW / this.effectiveBoardScale));
        this.virtualHeight = Math.max(240, (int) Math.floor((double) windowPhysH / this.effectiveBoardScale));
    }

    public boolean isScaled() {
        return Math.abs(scaleRatio - 1.0) > 1e-4;
    }

    public double getScaleRatio() {
        return scaleRatio;
    }

    public int getVirtualWidth() {
        return virtualWidth;
    }

    public int getVirtualHeight() {
        return virtualHeight;
    }

    public int getEffectiveBoardScale() {
        return effectiveBoardScale;
    }

    public int getGameGuiScale() {
        return gameGuiScale;
    }

    public double toVirtualX(double rawX) {
        return rawX / scaleRatio;
    }

    public double toVirtualY(double rawY) {
        return rawY / scaleRatio;
    }

    public float getRenderScale() {
        return (float) scaleRatio;
    }

    public double toGameX(double virtualX) {
        return toRawX(virtualX);
    }

    public double toGameY(double virtualY) {
        return toRawY(virtualY);
    }

    public double toRawX(double virtualX) {
        return virtualX * scaleRatio;
    }

    public double toRawY(double virtualY) {
        return virtualY * scaleRatio;
    }

    public void applyPose(PoseStack pose) {
        if (isScaled()) {
            pose.scale((float) scaleRatio, (float) scaleRatio, 1.0f);
        }
    }
}
