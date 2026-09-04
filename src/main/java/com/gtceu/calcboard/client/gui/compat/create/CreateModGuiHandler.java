package com.gtceu.calcboard.client.gui.compat.create;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Create implementation of {@link IModGuiHandler}.
 */
@OnlyIn(Dist.CLIENT)
public class CreateModGuiHandler implements IModGuiHandler {

    @Override
    public String getModId() {
        return "create";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        if (node.isGenerator()) {
            String genBadge = "⚡ " + Component.translatable("gui.gtcalcboard.kinetic_generator").getString();
            int badgeW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, genBadge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, 0xFF55FF88);
        } else {
            int rpm = node.getRpm();
            String rpmText = rpm + " RPM";
            int rpmW = Math.max(50, font.width(rpmText) + 10);
            NodeCardRenderer.drawBtn(graphics, font, rpmText, x + 6, row2Y, rpmW, 14, mouseX, mouseY, 0xFFFFAA00);

            int rscX = x + 6 + rpmW + 3;
            int rscW = (x + cardW - 6) - rscX;
            String rscText = "⚙ " + Component.translatable("gui.gtcalcboard.rotation_speed_controller").getString();
            if (font.width(rscText) > rscW - 4) {
                rscText = "⚙ RSC";
            }
            NodeCardRenderer.drawBtn(graphics, font, rscText, rscX, row2Y, rscW, 14, mouseX, mouseY, 0xFFE07A28);
        }
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.isGenerator()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int rpmW = Math.max(50, Minecraft.getInstance().font.width(node.getRpm() + " RPM") + 10);
        return mouseX >= x + 6 && mouseX <= x + 6 + rpmW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            com.gtceu.calcboard.compat.create.CreateProperties.cycleRpm(node, button == 1 ? -1 : 1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
            return true;
        }
        return false;
    }

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            com.gtceu.calcboard.compat.create.CreateProperties.cycleRpm(node, delta > 0 ? 1 : -1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
            return true;
        }
        return false;
    }

    private static final int[] MACHINE_RPM_PRESETS = {16, 32, 64, 128, 256};
    private static final int[] GENERATOR_RPM_PRESETS = {4, 8, 16, 32, 64};

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW,
                                   int mouseX, int mouseY, float partialTicks, EditBox parallelBox, BoardScreen parent) {
        if (CreateModAdapter.isFanProcessingRecipe(node)) {
            graphics.drawString(font, "§6⚙ " + Component.translatable("gui.gtcalcboard.encased_fan").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.tooltip.fan_fixed_duration_hint").getString(), x + 10, y + 48, 0xFF888888, false);
            return;
        }
        if (!node.isGenerator()) {
            graphics.drawString(font, "§6⚙ " + Component.translatable("gui.gtcalcboard.rotation_speed_controller").getString() + ": §e" + node.getRpm() + " RPM", x + 10, y + 30, 0xFFFFFFFF, false);
            renderRpmPresetButtons(graphics, font, node, x + 10, y + 44, mouseX, mouseY, MACHINE_RPM_PRESETS);
            return;
        }
        String genTitle = String.format(Locale.ROOT, "§a⚡ %s: §e+%,.0f SU §7(%d RPM)",
                Component.translatable("gui.gtcalcboard.kinetic_generator").getString(),
                node.getEffectiveTotalEUt(),
                node.getRpm()
        );
        graphics.drawString(font, genTitle, x + 10, y + 30, 0xFFFFFFFF, false);
        renderRpmPresetButtons(graphics, font, node, x + 10, y + 44, mouseX, mouseY, GENERATOR_RPM_PRESETS);
    }

    private static void renderRpmPresetButtons(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int mouseX, int mouseY, int[] presets) {
        int btnX = startX;
        for (int r : presets) {
            boolean active = node.getRpm() == r;
            boolean hov = mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= startY && mouseY <= startY + 16;
            graphics.fill(btnX, startY, btnX + 44, startY + 16, active ? 0xFF5D3E1A : (hov ? 0xFF3D4558 : 0xFF282D3B));
            graphics.renderOutline(btnX, startY, 44, 16, active ? 0xFFFFAA00 : 0xFF3F4658);
            graphics.drawCenteredString(font, r + " RPM", btnX + 22, startY + 4, active ? 0xFFFFD28C : 0xFFB0B8C8);
            btnX += 48;
        }
    }

    @Override
    public boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, EditBox parallelBox, BoardScreen parent) {
        if (CreateModAdapter.isFanProcessingRecipe(node)) return false;
        int[] presets = node.isGenerator() ? GENERATOR_RPM_PRESETS : MACHINE_RPM_PRESETS;
        return handleRpmButtonClick(node, x + 10, y + 44, mouseX, mouseY, presets, parent);
    }

    private static boolean handleRpmButtonClick(RecipeNode node, int startX, int startY, double mouseX, double mouseY, int[] presets, BoardScreen parent) {
        int btnX = startX;
        for (int r : presets) {
            if (mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= startY && mouseY <= startY + 16) {
                node.setRpm(r);
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            btnX += 48;
        }
        return false;
    }
}

