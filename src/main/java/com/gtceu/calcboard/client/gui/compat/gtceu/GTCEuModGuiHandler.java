package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.GenericModGuiHandler;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * GTCEu implementation of {@link com.gtceu.calcboard.client.gui.compat.IModGuiHandler}.
 * Acts as a slim orchestrating facade delegating card controls to {@link GTCEuNodeCardGuiHandler},
 * dialog header rendering to {@link GTCEuMachineDialogHeaderRenderer}, and dialog interactions
 * to {@link GTCEuMachineDialogHeaderHandler}.
 */
@OnlyIn(Dist.CLIENT)
public class GTCEuModGuiHandler extends GenericModGuiHandler {

    private final GTCEuMachineDialogState dialogState = new GTCEuMachineDialogState();
    private final GTCEuNodeCardGuiHandler cardHandler = new GTCEuNodeCardGuiHandler(new GenericModGuiHandler());
    private final GTCEuMachineDialogHeaderRenderer headerRenderer = new GTCEuMachineDialogHeaderRenderer(dialogState);
    private final GTCEuMachineDialogHeaderHandler headerHandler = new GTCEuMachineDialogHeaderHandler(dialogState);

    public static void resetControllerScroll() {
        GTCEuMachineDialogState.resetControllerScroll();
    }

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public void populateRow2Buttons(NodeWidget widget, Font font, RecipeNode node, int cardW, boolean isOperational, java.util.List<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> buttons) {
        cardHandler.populateRow2Buttons(widget, font, node, cardW, isOperational, buttons);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        cardHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public void renderCardControls(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        cardHandler.renderCardControls(widget, graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        return cardHandler.isTierOrSpeedControlHovered(widget, node, mouseX, mouseY);
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        return cardHandler.handleControlClick(widget, node, mouseX, mouseY, button);
    }

    @Override
    public boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return cardHandler.isSecondaryControlHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean isSecondaryControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        return cardHandler.isSecondaryControlHovered(widget, node, mouseX, mouseY);
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return cardHandler.isMachineConfigHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean isMachineConfigHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        return cardHandler.isMachineConfigHovered(widget, node, mouseX, mouseY);
    }

    @Override
    public void renderDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        headerRenderer.renderDialogHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    @Override
    public boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node,
                                           int x, int y, int dialogW, double mouseX, double mouseY, int button,
                                           EditBox parallelBox, BoardScreen parent) {
        return headerHandler.handleDialogHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent);
    }

    @Override
    public boolean handleDialogHeaderDrag(MachineConfigDialog dialog, RecipeNode node,
                                          int x, int y, int dialogW, double mouseX, double mouseY, int button,
                                          double dragX, double dragY) {
        return headerHandler.handleDialogHeaderDrag(dialog, node, x, y, dialogW, mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean handleDialogHeaderRelease(MachineConfigDialog dialog, RecipeNode node,
                                             int x, int y, int dialogW, double mouseX, double mouseY, int button,
                                             EditBox parallelBox, BoardScreen parent) {
        return headerHandler.handleDialogHeaderRelease(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent);
    }

    @Override
    public boolean handleDialogHeaderScroll(MachineConfigDialog dialog, RecipeNode node,
                                            int x, int y, int dialogW, double mouseX, double mouseY, double delta) {
        return headerHandler.handleDialogHeaderScroll(dialog, node, x, y, dialogW, mouseX, mouseY, delta);
    }
}
