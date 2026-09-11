package com.gtceu.calcboard.client.gui.compat;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardTextCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Client-only Service Provider Interface (SPI) for GUI rendering, card controls,
 * and dialog interaction handling of machine nodes.
 */
@OnlyIn(Dist.CLIENT)
public interface IModGuiHandler {

    String getModId();

    /**
     * Precomputes Row 2 control buttons for caching in NodeCardTextCache.
     */
    default void populateRow2Buttons(NodeWidget widget, Font font, RecipeNode node, int cardW, boolean isOperational, List<NodeCardTextCache.Row2Button> buttons) {}

    /**
     * Renders row 2 machine controls (tiers, speeds, overclock modes, generator badges, parallel buttons).
     */
    void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing);

    default void renderCardControls(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        List<NodeCardTextCache.Row2Button> buttons = (widget != null && widget.getTextCache() != null)
                ? widget.getTextCache().getRow2Buttons()
                : null;
        if (buttons != null && !buttons.isEmpty()) {
            for (NodeCardTextCache.Row2Button btn : buttons) {
                int btnX = x + btn.relX();
                boolean glow = btn.isGlowing() || (isGlowing && btn.role() == NodeCardTextCache.Row2Button.ButtonRole.CONFIG);
                NodeCardRenderer.drawBtn(graphics, font, btn.text(), btn.textWidth(), btnX, row2Y, btn.width(), 14, mouseX, mouseY, btn.color(), btn.isAlert(), glow);
            }
            return;
        }
        renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    /**
     * Handles clicking on row 2 controls for this node.
     */
    boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button);

    /**
     * Handles mouse wheel scrolling on row 2 controls for this node.
     */
    default boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        return false;
    }

    /**
     * Renders Section 1 (Top Header / Machine Base Settings) inside MachineConfigDialog.
     */
    default void renderDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW,
                                    int mouseX, int mouseY, float partialTicks,
                                    EditBox parallelBox, BoardScreen parent) {
        renderDialogHeader(graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    /**
     * Renders Section 1 (Top Header / Machine Base Settings) inside MachineConfigDialog (legacy fallback).
     */
    default void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW,
                                    int mouseX, int mouseY, float partialTicks,
                                    EditBox parallelBox, BoardScreen parent) {}

    /**
     * Handles clicks on Section 1 controls inside MachineConfigDialog.
     */
    default boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                            double mouseX, double mouseY, int button,
                                            EditBox parallelBox, BoardScreen parent) {
        return false;
    }

    /**
     * Handles mouse drag on Section 1 header inside MachineConfigDialog.
     */
    default boolean handleDialogHeaderDrag(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /**
     * Handles mouse release on Section 1 header inside MachineConfigDialog.
     */
    default boolean handleDialogHeaderRelease(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                              double mouseX, double mouseY, int button,
                                              EditBox parallelBox, BoardScreen parent) {
        return false;
    }

    /**
     * Handles mouse wheel scroll on Section 1 header inside MachineConfigDialog.
     */
    default boolean handleDialogHeaderScroll(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                             double mouseX, double mouseY, double delta) {
        return false;
    }

    /**
     * Checks if the primary tier / speed selector button on row 2 is hovered.
     */
    default boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    default boolean isTierOrSpeedControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        return isTierOrSpeedControlHovered(node, mouseX, mouseY);
    }

    /**
     * Checks if the secondary mode button (e.g. Overclock Mode STD/PERF) is hovered.
     */
    default boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    default boolean isSecondaryControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        return isSecondaryControlHovered(node, mouseX, mouseY);
    }

    /**
     * Checks if the machine configuration / parallel button on row 2 is hovered.
     */
    default boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    default boolean isMachineConfigHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        return isMachineConfigHovered(node, mouseX, mouseY);
    }
}
