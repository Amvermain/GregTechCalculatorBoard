package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.model.ProjectedPort;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.start.StarTBacterialProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Locale;

/**
 * Configuration view for editing Bacterial Hydrocarbon Harvester production and metabolism tiers in MachineConfigDialog.
 */
public class BacteriaStatsConfigView {

    private final MachineConfigDialog dialog;

    public BacteriaStatsConfigView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        int leftW = 210;
        renderLeftPanel(graphics, font, node, startX, startY, leftW, height, mouseX, mouseY);

        int rightX = startX + leftW + 4;
        int rightW = width - leftW - 4;
        renderRightPanel(graphics, font, node, rightX, startY, rightW, height, mouseX, mouseY);
    }

    private void renderLeftPanel(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int leftW, int height, int mouseX, int mouseY) {
        graphics.fill(startX, startY, startX + leftW, startY + height, 0xFF14161E);
        graphics.renderOutline(startX, startY, leftW, height, 0xFF2D3342);

        graphics.drawString(font, "§d✦ " + Component.translatable("gui.gtcalcboard.bacteria_stats.title").getString(), startX + 6, startY + 6, 0xFFFFFFFF, false);

        int prod = StarTBacterialProperties.getProduction(node);
        int meta = StarTBacterialProperties.getMetabolism(node);

        renderProductionControl(graphics, font, prod, startX + 6, startY + 22, leftW - 12, mouseX, mouseY);
        renderMetabolismControl(graphics, font, meta, startX + 6, startY + 64, leftW - 12, mouseX, mouseY);

        graphics.fill(startX + 6, startY + 112, startX + leftW - 6, startY + 113, 0xFF2D3342);

        renderPresetSection(graphics, font, startX + 6, startY + 118, leftW - 12, mouseX, mouseY);
    }

    private void renderProductionControl(GuiGraphics graphics, Font font, int prod, int x, int y, int w, int mouseX, int mouseY) {
        String prodTitle = Component.translatable("gui.gtcalcboard.bacteria_stats.production").getString();
        graphics.drawString(font, "§f" + prodTitle + ": " + formatStatBar(prod, true), x, y, 0xFFFFFFFF, false);

        int btnY = y + 12;
        renderStepperButton(graphics, font, "-", x, btnY, 20, 14, mouseX, mouseY);
        graphics.drawString(font, "§e" + prod + " / 5", x + 30, btnY + 3, 0xFFFFFFFF, false);
        renderStepperButton(graphics, font, "+", x + 68, btnY, 20, 14, mouseX, mouseY);

        String multText = String.format(Locale.ROOT, "%,dx", prod);
        String desc = Component.translatable("gui.gtcalcboard.bacteria_stats.production_desc", multText).getString();
        graphics.drawString(font, "§7" + desc, x, y + 28, 0xFFAAAAAA, false);
    }

    private void renderMetabolismControl(GuiGraphics graphics, Font font, int meta, int x, int y, int w, int mouseX, int mouseY) {
        String metaTitle = Component.translatable("gui.gtcalcboard.bacteria_stats.metabolism").getString();
        graphics.drawString(font, "§f" + metaTitle + ": " + formatStatBar(meta, false), x, y, 0xFFFFFFFF, false);

        int btnY = y + 12;
        renderStepperButton(graphics, font, "-", x, btnY, 20, 14, mouseX, mouseY);
        graphics.drawString(font, "§b" + meta + " / 5", x + 30, btnY + 3, 0xFFFFFFFF, false);
        renderStepperButton(graphics, font, "+", x + 68, btnY, 20, 14, mouseX, mouseY);

        int sugar = 1 << meta;
        long biomass = 100L << meta;
        String saving = meta == 1 ? "§a(-93.8%)" : (meta == 5 ? "§c(100%)" : "§e(2^" + meta + ")");
        String summary = String.format(Locale.ROOT, "§7%s: §e%d §7| §7%s: §a%,d mB %s",
                Component.translatable("gui.gtcalcboard.bacteria_stats.sugar").getString(), sugar,
                Component.translatable("gui.gtcalcboard.bacteria_stats.biomass").getString(), biomass, saving);
        graphics.drawString(font, font.plainSubstrByWidth(summary, w), x, y + 28, 0xFF888888, false);
    }

    private void renderPresetSection(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        graphics.drawString(font, "§6★ " + Component.translatable("gui.gtcalcboard.bacteria_stats.presets").getString(), x, y, 0xFFFFFFFF, false);

        renderButton(graphics, font, "gui.gtcalcboard.bacteria_stats.preset_perfect", x, y + 12, w, 14, 0xFF2A422D, 0xFF3E6643, mouseX, mouseY);
        renderButton(graphics, font, "gui.gtcalcboard.bacteria_stats.preset_default", x, y + 29, w, 14, 0xFF252D3D, 0xFF35445E, mouseX, mouseY);
        renderButton(graphics, font, "gui.gtcalcboard.bacteria_stats.preset_initial", x, y + 46, w, 14, 0xFF3D2525, 0xFF5E3535, mouseX, mouseY);
    }

    private void renderRightPanel(GuiGraphics graphics, Font font, RecipeNode node, int rightX, int startY, int rightW, int height, int mouseX, int mouseY) {
        graphics.fill(rightX, startY, rightX + rightW, startY + height, 0xFF14161E);
        graphics.renderOutline(rightX, startY, rightW, height, 0xFF2D3342);

        graphics.drawString(font, "§b▦ " + Component.translatable("gui.gtcalcboard.bacteria_stats.preview_title").getString(), rightX + 6, startY + 6, 0xFFFFFFFF, false);
        graphics.drawString(font, "§78.0s (160t) | ZPM 65.5k EU/t", rightX + 6, startY + 18, 0xFF888888, false);

        graphics.fill(rightX + 6, startY + 29, rightX + rightW - 6, startY + 30, 0xFF2D3342);

        int cy = startY + 34;
        graphics.drawString(font, "§6↳ " + Component.translatable("gui.gtcalcboard.bacteria_stats.inputs_header").getString() + ":", rightX + 6, cy, 0xFFDDDDDD, false);
        cy += 12;

        List<ProjectedPort> inputs = node.getProjectedInputs();
        for (ProjectedPort p : inputs) {
            String line = String.format(Locale.ROOT, "  §7- §f%s: §e%,.0f%s",
                    p.stack().getDisplayName(),
                    p.stack().getAmount(),
                    p.stack().isFluid() ? " mB" : "x");
            graphics.drawString(font, font.plainSubstrByWidth(line, rightW - 12), rightX + 6, cy, 0xFFFFFFFF, false);
            cy += 11;
        }

        graphics.fill(rightX + 6, cy + 2, rightX + rightW - 6, cy + 3, 0xFF2D3342);
        cy += 7;

        graphics.drawString(font, "§a↳ " + Component.translatable("gui.gtcalcboard.bacteria_stats.outputs_header").getString() + ":", rightX + 6, cy, 0xFFDDDDDD, false);
        cy += 12;

        List<ProjectedPort> outputs = node.getProjectedOutputs();
        for (ProjectedPort p : outputs) {
            String line = String.format(Locale.ROOT, "  §7- §f%s: §a%,.0f mB",
                    p.stack().getDisplayName(),
                    p.stack().getAmount());
            graphics.drawString(font, font.plainSubstrByWidth(line, rightW - 12), rightX + 6, cy, 0xFFFFFFFF, false);
            cy += 11;
        }
    }

    public boolean mouseClicked(int startX, int startY, int width, int height, double mouseX, double mouseY, int button, RecipeNode node, BoardScreen parent) {
        if (button != 0 || node == null) return false;

        int leftW = 210;
        int x = startX + 6;

        int prodY = startY + 34;
        if (isMouseOver(mouseX, mouseY, x, prodY, 20, 14)) {
            changeStat(node, parent, -1, 0);
            return true;
        }
        if (isMouseOver(mouseX, mouseY, x + 68, prodY, 20, 14)) {
            changeStat(node, parent, 1, 0);
            return true;
        }

        int metaY = startY + 76;
        if (isMouseOver(mouseX, mouseY, x, metaY, 20, 14)) {
            changeStat(node, parent, 0, -1);
            return true;
        }
        if (isMouseOver(mouseX, mouseY, x + 68, metaY, 20, 14)) {
            changeStat(node, parent, 0, 1);
            return true;
        }

        int presetY = startY + 130;
        int btnW = leftW - 12;
        if (isMouseOver(mouseX, mouseY, x, presetY, btnW, 14)) {
            applyPreset(node, parent, 5, 1);
            return true;
        }
        if (isMouseOver(mouseX, mouseY, x, presetY + 17, btnW, 14)) {
            applyPreset(node, parent, 5, 5);
            return true;
        }
        if (isMouseOver(mouseX, mouseY, x, presetY + 34, btnW, 14)) {
            applyPreset(node, parent, 1, 5);
            return true;
        }

        return false;
    }

    private void changeStat(RecipeNode node, BoardScreen parent, int prodDelta, int metaDelta) {
        playClickSound();
        int p = StarTBacterialProperties.getProduction(node) + prodDelta;
        int m = StarTBacterialProperties.getMetabolism(node) + metaDelta;
        StarTBacterialProperties.setProduction(node, p);
        StarTBacterialProperties.setMetabolism(node, m);
        node.syncProjectedPorts();
        if (parent != null) {
            parent.markSummaryDirty();
        }
    }

    private void applyPreset(RecipeNode node, BoardScreen parent, int prod, int meta) {
        playClickSound();
        StarTBacterialProperties.setProduction(node, prod);
        StarTBacterialProperties.setMetabolism(node, meta);
        node.syncProjectedPorts();
        if (parent != null) {
            parent.markSummaryDirty();
        }
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void renderStepperButton(GuiGraphics graphics, Font font, String text, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hov = isMouseOver(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, hov ? 0xFF35445E : 0xFF1C273A);
        graphics.renderOutline(x, y, w, h, hov ? 0xFF5890FF : 0xFF2A3448);
        graphics.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private static void renderButton(GuiGraphics graphics, Font font, String transKey, int x, int y, int w, int h, int bg, int hoverBg, int mouseX, int mouseY) {
        boolean hov = isMouseOver(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, hov ? hoverBg : bg);
        graphics.renderOutline(x, y, w, h, hov ? 0xFF88BBFF : 0xFF3D4C63);
        String label = Component.translatable(transKey).getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    public static String formatStatBar(int stat, boolean highIsGood) {
        String color = switch (stat) {
            case 1 -> highIsGood ? "§4" : "§a";
            case 2 -> highIsGood ? "§c" : "§2";
            case 3 -> "§e";
            case 4 -> highIsGood ? "§2" : "§c";
            case 5 -> highIsGood ? "§a" : "§4";
            default -> "§f";
        };
        StringBuilder sb = new StringBuilder(color);
        for (int i = 0; i < stat; i++) sb.append('■');
        for (int i = stat; i < 5; i++) sb.append('□');
        return sb.append("§r").toString();
    }
}
