package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Dedicated renderer for GTCEu machine configuration dialog headers.
 * Delegated to GTCEuTurbineHeaderRenderer and GTCEuHardwareStatusRenderer.
 */
@OnlyIn(Dist.CLIENT)
public class GTCEuMachineDialogHeaderRenderer {

    private final GTCEuMachineDialogState state;

    public GTCEuMachineDialogHeaderRenderer(GTCEuMachineDialogState state) {
        this.state = state;
    }

    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        renderDialogHeader(null, graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    public void renderDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            GTCEuTurbineHeaderRenderer.renderTurbineDialogHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY);
        } else if (GTCombustionHelper.isCombustionEngine(node)) {
            GTCEuHardwareStatusRenderer.renderCombustionDialogHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
        } else if (node.isLiquidBoilerRecipe() || (ModAdapterRegistry.getAdapterForNode(node) != null && ModAdapterRegistry.getAdapterForNode(node).isBoilerRecipe(node))) {
            GTCEuHardwareStatusRenderer.renderBoilerDialogHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY);
        } else if (!node.isMultiblock()) {
            GTCEuHardwareStatusRenderer.renderSteamModeDialogHeader(dialog, graphics, font, node, x, y, mouseX, mouseY);
        } else if (GTCEuNodeCardGuiHandler.isFusionMachine(node)) {
            GTCEuCoilFusionHeaderRenderer.renderFusionReflectorHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY, state);
        } else if (GTCEuNodeCardGuiHandler.isCoilMultiblock(node)) {
            GTCEuCoilFusionHeaderRenderer.renderCoilDialogHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY, state);
        } else {
            GTCEuHardwareStatusRenderer.renderGenericMultiblockControllerHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY);
        }
    }

    public static String getMultiblockShortLabel(ResourceLocation id) {
        return GTCEuHardwareStatusRenderer.getMultiblockShortLabel(id);
    }
}
