package com.gtceu.calcboard.client.gui.dialog.modal;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Immutable rendering context passed to modal dialogs.
 */
public record ModalRenderContext(
        GuiGraphics graphics,
        int screenWidth,
        int screenHeight,
        int mouseX,
        int mouseY,
        float partialTicks
) {}
