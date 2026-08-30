package com.gtceu.calcboard.client.gui.compat;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only Service Provider Interface (SPI) for GUI rendering, card controls,
 * and dialog interaction handling of machine nodes.
 */
@OnlyIn(Dist.CLIENT)
public interface IModGuiHandler {

    String getModId();

    /**
     * Renders row 2 machine controls (tiers, speeds, overclock modes, generator badges, parallel buttons).
     */
    void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing);

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

    /**
     * Checks if the secondary mode button (e.g. Overclock Mode STD/PERF) is hovered.
     */
    default boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    /**
     * Checks if the machine configuration / parallel button on row 2 is hovered.
     */
    default boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }
}
