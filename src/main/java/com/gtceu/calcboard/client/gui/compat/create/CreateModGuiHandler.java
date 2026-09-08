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
            if (com.gtceu.calcboard.compat.create.CreateProperties.isCreateBoiler(node)) {
                renderBoilerCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY);
            } else {
                String genBadge = "⚡ " + Component.translatable("gui.gtcalcboard.kinetic_generator").getString();
                int badgeW = cardW - 12;
                NodeCardRenderer.drawBtn(graphics, font, genBadge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, 0xFF55FF88);
            }
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

    private void renderBoilerCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY) {
        boolean waterMode = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_WATER_MODE);

        String levelText = getBoilerLevelText(node);
        int levelW = Math.max(48, font.width(levelText) + 8);
        NodeCardRenderer.drawBtn(graphics, font, levelText, x + 6, row2Y, levelW, 14, mouseX, mouseY, 0xFFFF8822);

        int modeX = x + 6 + levelW + 3;
        int modeW = (x + cardW - 6) - modeX;
        String modeText = waterMode
                ? "💧 " + Component.translatable("gui.gtcalcboard.create.boiler_water").getString()
                : "♨ " + Component.translatable("gui.gtcalcboard.create.boiler_steam").getString();
        NodeCardRenderer.drawBtn(graphics, font, modeText, modeX, row2Y, modeW, 14, mouseX, mouseY, waterMode ? 0xFF55AAFF : 0xFFFFAA33);
    }

    private static String getBoilerLevelText(RecipeNode node) {
        int level = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_LEVEL);
        int size = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_SIZE_BLOCKS);
        int heat = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_HEAT_LEVEL);
        int water = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_WATER_MB_TICK);
        var bn = com.gtceu.calcboard.compat.create.CreateProperties.getBottleneck(size, heat, water);

        String base = level == 0
                ? "♨ " + Component.translatable("gui.gtcalcboard.create.boiler_passive").getString()
                : "♨ " + Component.translatable("gui.gtcalcboard.create.boiler_level", level).getString();
        return (bn != com.gtceu.calcboard.compat.create.CreateProperties.BoilerBottleneck.NONE && bn != com.gtceu.calcboard.compat.create.CreateProperties.BoilerBottleneck.INACTIVE)
                ? base + " ⚠"
                : base;
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.isGenerator()) {
            if (!com.gtceu.calcboard.compat.create.CreateProperties.isCreateBoiler(node)) return false;
            int x = (int) node.getPosX();
            int y = (int) node.getPosY();
            int row2Y = y + 20 + 6 + 18;
            int levelW = Math.max(48, Minecraft.getInstance().font.width(getBoilerLevelText(node)) + 8);
            return mouseX >= x + 6 && mouseX <= x + 6 + levelW && mouseY >= row2Y && mouseY <= row2Y + 14;
        }
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int row2Y = y + 20 + 6 + 18;
        int rpmW = Math.max(50, Minecraft.getInstance().font.width(node.getRpm() + " RPM") + 10);
        return mouseX >= x + 6 && mouseX <= x + 6 + rpmW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        if (!com.gtceu.calcboard.compat.create.CreateProperties.isCreateBoiler(node)) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int row2Y = y + 20 + 6 + 18;
        int cardW = node.getCardWidth();
        int levelW = Math.max(48, Minecraft.getInstance().font.width(getBoilerLevelText(node)) + 8);
        int modeX = x + 6 + levelW + 3;
        return mouseX >= modeX && mouseX <= x + cardW - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (com.gtceu.calcboard.compat.create.CreateProperties.isCreateBoiler(node)) {
                com.gtceu.calcboard.compat.create.CreateProperties.cycleBoilerLevel(node, button == 1 ? -1 : 1);
            } else {
                com.gtceu.calcboard.compat.create.CreateProperties.cycleRpm(node, button == 1 ? -1 : 1);
            }
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound();
            return true;
        }
        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            com.gtceu.calcboard.compat.create.CreateProperties.toggleBoilerFluidMode(node);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (com.gtceu.calcboard.compat.create.CreateProperties.isCreateBoiler(node)) {
                com.gtceu.calcboard.compat.create.CreateProperties.cycleBoilerLevel(node, delta > 0 ? 1 : -1);
            } else {
                com.gtceu.calcboard.compat.create.CreateProperties.cycleRpm(node, delta > 0 ? 1 : -1);
            }
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound();
            return true;
        }
        return false;
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
        );
    }

    private static final int[] MACHINE_RPM_PRESETS = {16, 32, 64, 128, 256};
    private static final int[] GENERATOR_RPM_PRESETS = {4, 8, 16, 32, 64};
    private static final int[] BOILER_LEVEL_PRESETS = {0, 1, 4, 9, 18};
    private static final int[] WINDMILL_SAIL_PRESETS = {8, 16, 32, 64, 128};

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW,
                                   int mouseX, int mouseY, float partialTicks, EditBox parallelBox, BoardScreen parent) {
        if (CreateModAdapter.isFanProcessingRecipe(node)) {
            graphics.drawString(font, "§6⚙ " + Component.translatable("gui.gtcalcboard.encased_fan").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.tooltip.fan_fixed_duration_hint").getString(), x + 10, y + 48, 0xFF888888, false);
            return;
        }
        if (com.gtceu.calcboard.compat.create.CreateProperties.isCreateBoiler(node)) {
            renderBoilerDialogHeader(graphics, font, node, x, y, mouseX, mouseY);
            return;
        }
        if (com.gtceu.calcboard.compat.create.CreateProperties.isWindmill(node)) {
            renderWindmillDialogHeader(graphics, font, node, x, y, mouseX, mouseY);
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

    private void renderWindmillDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int mouseX, int mouseY) {
        int sails = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.WINDMILL_SAILS);
        if (sails <= 0) sails = 8;
        int rpm = node.getRpm();
        String title = String.format(Locale.ROOT, "§a🌀 %s: §e+%,.0f SU §7(%d Sails, %d RPM)",
                Component.translatable("gui.gtcalcboard.create.windmill_title").getString(),
                node.getBaseEUt(),
                sails,
                rpm
        );
        graphics.drawString(font, title, x + 10, y + 30, 0xFFFFFFFF, false);
        renderWindmillPresetButtons(graphics, font, node, x + 10, y + 44, mouseX, mouseY);
    }

    private void renderWindmillPresetButtons(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int mouseX, int mouseY) {
        int curSails = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.WINDMILL_SAILS);
        if (curSails <= 0) curSails = 8;
        int btnX = startX;

        for (int s : WINDMILL_SAIL_PRESETS) {
            String label = s + " Sails";
            int w = Math.max(38, font.width(label) + 8);
            boolean active = curSails == s;
            drawPresetButton(graphics, font, label, btnX, startY, w, active, mouseX, mouseY);
            btnX += w + 4;
        }

        drawPresetButton(graphics, font, "-8", btnX, startY, 24, false, mouseX, mouseY);
        btnX += 28;
        drawPresetButton(graphics, font, "+8", btnX, startY, 24, false, mouseX, mouseY);
    }

    private void renderBoilerDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int mouseX, int mouseY) {
        int level = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_LEVEL);
        int size = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_SIZE_BLOCKS);
        int heat = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_HEAT_LEVEL);
        int water = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_WATER_MB_TICK);
        boolean waterMode = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_WATER_MODE);
        var bn = com.gtceu.calcboard.compat.create.CreateProperties.getBottleneck(size, heat, water);

        String levelStr = level == 0
                ? Component.translatable("gui.gtcalcboard.create.boiler_passive").getString()
                : Component.translatable("gui.gtcalcboard.create.boiler_level", level).getString();
        String modeStr = waterMode
                ? Component.translatable("gui.gtcalcboard.create.boiler_water").getString()
                : Component.translatable("gui.gtcalcboard.create.boiler_steam").getString();
        String modeFormatted = Component.translatable("gui.gtcalcboard.create.boiler_mode_desc", modeStr).getString();
        String bnStr = switch (bn) {
            case SIZE -> " §e(⚠ " + Component.translatable("gui.gtcalcboard.create.boiler_bn_size").getString() + ")";
            case WATER -> " §9(⚠ " + Component.translatable("gui.gtcalcboard.create.boiler_bn_water").getString() + ")";
            case HEAT -> " §6(⚠ " + Component.translatable("gui.gtcalcboard.create.boiler_bn_heat").getString() + ")";
            case NONE, INACTIVE -> "";
        };

        String title = String.format(Locale.ROOT, "§6♨ %s: §e%s §7(+%,.0f SU, %s)%s",
                Component.translatable("gui.gtcalcboard.create.boiler_title").getString(),
                levelStr, node.getBaseEUt(), modeFormatted, bnStr);
        graphics.drawString(font, title, x + 10, y + 30, 0xFFFFFFFF, false);
        renderBoilerPresetButtons(graphics, font, node, x + 10, y + 44, mouseX, mouseY);
    }

    private void renderBoilerPresetButtons(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int mouseX, int mouseY) {
        int curLevel = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_LEVEL);
        boolean waterMode = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.BOILER_WATER_MODE);
        int btnX = startX;

        for (int lvl : BOILER_LEVEL_PRESETS) {
            String label = lvl == 0
                    ? Component.translatable("gui.gtcalcboard.create.boiler_passive").getString()
                    : ("Lv." + lvl);
            int w = lvl == 0 ? Math.max(44, font.width(label) + 8) : 32;
            boolean active = curLevel == lvl;
            drawPresetButton(graphics, font, label, btnX, startY, w, active, mouseX, mouseY);
            btnX += w + 4;
        }

        drawPresetButton(graphics, font, "-", btnX, startY, 20, false, mouseX, mouseY);
        btnX += 24;
        drawPresetButton(graphics, font, "+", btnX, startY, 20, false, mouseX, mouseY);
        btnX += 24;

        String fluidLabel = waterMode
                ? "💧 " + Component.translatable("gui.gtcalcboard.create.boiler_water").getString()
                : "♨ " + Component.translatable("gui.gtcalcboard.create.boiler_steam").getString();
        int fluidW = Math.max(50, font.width(fluidLabel) + 8);
        drawPresetButton(graphics, font, fluidLabel, btnX, startY, fluidW, false, mouseX, mouseY);
    }

    private void drawPresetButton(GuiGraphics graphics, Font font, String label, int btnX, int startY, int w, boolean active, int mouseX, int mouseY) {
        boolean hov = mouseX >= btnX && mouseX <= btnX + w && mouseY >= startY && mouseY <= startY + 16;
        graphics.fill(btnX, startY, btnX + w, startY + 16, active ? 0xFF5D3E1A : (hov ? 0xFF3D4558 : 0xFF282D3B));
        graphics.renderOutline(btnX, startY, w, 16, active ? 0xFFFFAA00 : 0xFF3F4658);
        graphics.drawCenteredString(font, label, btnX + w / 2, startY + 4, active ? 0xFFFFD28C : 0xFFB0B8C8);
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
        if (com.gtceu.calcboard.compat.create.CreateProperties.isCreateBoiler(node)) {
            return handleBoilerHeaderClick(node, x + 10, y + 44, mouseX, mouseY, parent);
        }
        if (com.gtceu.calcboard.compat.create.CreateProperties.isWindmill(node)) {
            return handleWindmillHeaderClick(node, x + 10, y + 44, mouseX, mouseY, parent);
        }
        int[] presets = node.isGenerator() ? GENERATOR_RPM_PRESETS : MACHINE_RPM_PRESETS;
        return handleRpmButtonClick(node, x + 10, y + 44, mouseX, mouseY, presets, parent);
    }

    private boolean handleWindmillHeaderClick(RecipeNode node, int startX, int startY, double mouseX, double mouseY, BoardScreen parent) {
        int curSails = node.getProperties().get(com.gtceu.calcboard.compat.create.CreateProperties.WINDMILL_SAILS);
        if (curSails <= 0) curSails = 8;
        int btnX = startX;

        for (int s : WINDMILL_SAIL_PRESETS) {
            int w = 48;
            if (mouseX >= btnX && mouseX <= btnX + w && mouseY >= startY && mouseY <= startY + 16) {
                com.gtceu.calcboard.compat.create.CreateProperties.applyWindmillSails(node, s);
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
            btnX += w + 4;
        }

        if (mouseX >= btnX && mouseX <= btnX + 24 && mouseY >= startY && mouseY <= startY + 16) {
            com.gtceu.calcboard.compat.create.CreateProperties.applyWindmillSails(node, curSails - 8);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        btnX += 28;

        if (mouseX >= btnX && mouseX <= btnX + 24 && mouseY >= startY && mouseY <= startY + 16) {
            com.gtceu.calcboard.compat.create.CreateProperties.applyWindmillSails(node, curSails + 8);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handleBoilerHeaderClick(RecipeNode node, int startX, int startY, double mouseX, double mouseY, BoardScreen parent) {
        int btnX = startX;
        for (int lvl : BOILER_LEVEL_PRESETS) {
            int w = lvl == 0 ? 44 : 32;
            if (mouseX >= btnX && mouseX <= btnX + w && mouseY >= startY && mouseY <= startY + 16) {
                com.gtceu.calcboard.compat.create.CreateProperties.applyBoilerLevel(node, lvl);
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
            btnX += w + 4;
        }
        if (mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= startY && mouseY <= startY + 16) {
            com.gtceu.calcboard.compat.create.CreateProperties.cycleBoilerLevel(node, -1);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        btnX += 24;
        if (mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= startY && mouseY <= startY + 16) {
            com.gtceu.calcboard.compat.create.CreateProperties.cycleBoilerLevel(node, 1);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        btnX += 24;
        if (mouseX >= btnX && mouseX <= btnX + 50 && mouseY >= startY && mouseY <= startY + 16) {
            com.gtceu.calcboard.compat.create.CreateProperties.toggleBoilerFluidMode(node);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        return false;
    }

    private static boolean handleRpmButtonClick(RecipeNode node, int startX, int startY, double mouseX, double mouseY, int[] presets, BoardScreen parent) {
        int btnX = startX;
        for (int r : presets) {
            if (mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= startY && mouseY <= startY + 16) {
                node.setRpm(r);
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
            btnX += 48;
        }
        return false;
    }
}

