package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFFuel;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleSlot;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleType;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MCFConfigView {

    private final MachineConfigDialog dialog;
    private double scrollY = 0;

    public MCFConfigView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        int leftW = 168;
        renderLeftPanel(graphics, font, node, startX, startY, leftW, height, mouseX, mouseY);

        int rightX = startX + leftW + 4;
        int rightW = width - leftW - 4;
        renderRightPanel(graphics, font, node, rightX, startY, rightW, height, mouseX, mouseY);
    }

    private void renderLeftPanel(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int leftW, int height, int mouseX, int mouseY) {
        graphics.fill(startX, startY, startX + leftW, startY + height, 0xFF14161E);
        graphics.renderOutline(startX, startY, leftW, height, 0xFF2D3342);

        graphics.drawString(font, "§b✦ " + Component.translatable("gui.gtcalcboard.mcf.cooling_boost").getString(), startX + 4, startY + 4, 0xFFFFFFFF, false);

        String currentCoolant = GTCombustionHelper.getMCFCoolantType(node);
        renderCoolantOption(graphics, font, "none", "gui.gtcalcboard.mcf.coolant_none", currentCoolant, startX + 4, startY + 16, leftW - 8, 13, mouseX, mouseY);
        renderCoolantOption(graphics, font, "distilled_water", "gui.gtcalcboard.mcf.coolant_distilled", currentCoolant, startX + 4, startY + 31, leftW - 8, 13, mouseX, mouseY);
        renderCoolantOption(graphics, font, "deionized_water", "gui.gtcalcboard.mcf.coolant_deionized", currentCoolant, startX + 4, startY + 46, leftW - 8, 13, mouseX, mouseY);

        MCFSlotConfiguration cfg = GTCombustionHelper.getMCFConfiguration(node);
        int activeModules = cfg.getActiveSlotCount();
        double demandRate = GTCombustionHelper.getCentralCoolantDemandMbPerSec(node);
        long demandBPerHr = (long) Math.round(activeModules * 500);
        String demandText = "none".equalsIgnoreCase(currentCoolant)
                ? "0 B/hr"
                : String.format(Locale.ROOT, "%,d B/hr (%.1f mB/s)", demandBPerHr, demandRate);
        graphics.drawString(font, font.plainSubstrByWidth("§7" + Component.translatable("gui.gtcalcboard.mcf.coolant_demand", demandText).getString(), leftW - 8), startX + 4, startY + 62, 0xFFFFFFFF, false);

        graphics.fill(startX + 4, startY + 75, startX + leftW - 4, startY + 76, 0xFF2D3342);

        double totalPower = GTCombustionHelper.computeMCFTotalPower(node);
        GTCombustionHelper.LaserHatchRecommendation rec = GTCombustionHelper.getLaserHatchRecommendation(totalPower);
        graphics.drawString(font, "§e⚡ " + Component.translatable("gui.gtcalcboard.mcf.net_generation", String.format(Locale.ROOT, "%,d EU/t", (long) totalPower)).getString(), startX + 4, startY + 79, 0xFFFFFFFF, false);
        graphics.drawString(font, "§d↳ " + rec.label(), startX + 4, startY + 91, 0xFFB0C0D8, false);

        String bomText = String.valueOf(activeModules);
        graphics.drawString(font, font.plainSubstrByWidth("§b▦ " + Component.translatable("gui.gtcalcboard.mcf.bom_summary", bomText).getString(), leftW - 8), startX + 4, startY + 103, 0xFFA0C0B0, false);

        Map<MCFFuel, Double> fuelDemands = GTCombustionHelper.getMCFFuelDemandMbPerSec(node);
        int fy = startY + 115;
        for (Map.Entry<MCFFuel, Double> entry : fuelDemands.entrySet()) {
            if (fy + 10 > startY + height) break;
            double rateSec = entry.getValue();
            double rateTick = rateSec / 20.0;
            String fuelStr = String.format(Locale.ROOT, "§6⛽ %s: §e%.1f §7mB/t", entry.getKey().getDisplayName(), rateTick);
            graphics.drawString(font, font.plainSubstrByWidth(fuelStr, leftW - 8), startX + 4, fy, 0xFFFFFFFF, false);
            fy += 11;
        }
    }

    private void renderCoolantOption(GuiGraphics graphics, Font font, String typeId, String transKey, String currentCoolant, int ox, int oy, int ow, int oh, int mouseX, int mouseY) {
        boolean selected = typeId.equalsIgnoreCase(currentCoolant);
        boolean hover = mouseX >= ox && mouseX <= ox + ow && mouseY >= oy && mouseY <= oy + oh;
        graphics.fill(ox, oy, ox + ow, oy + oh, hover ? 0xFF252D3D : (selected ? 0xFF1C273A : 0xFF1B202B));
        graphics.renderOutline(ox, oy, ow, oh, selected ? 0xFF5890FF : (hover ? 0xFF3D4C63 : 0xFF2A3448));
        String radio = selected ? "§a● " : "§7○ ";
        String label = radio + Component.translatable(transKey).getString();
        graphics.drawString(font, font.plainSubstrByWidth(label, ow - 4), ox + 4, oy + 3, selected ? 0xFFFFFFFF : 0xFFAAAAAA, false);
    }

    private void renderRightPanel(GuiGraphics graphics, Font font, RecipeNode node, int rightX, int startY, int rightW, int height, int mouseX, int mouseY) {
        graphics.fill(rightX, startY, rightX + rightW, startY + height, 0xFF14161E);
        graphics.renderOutline(rightX, startY, rightW, height, 0xFF2D3342);

        renderPresetButtons(graphics, font, rightX, startY, rightW, mouseX, mouseY);
        graphics.fill(rightX + 4, startY + 18, rightX + rightW - 4, startY + 19, 0xFF2D3342);

        MCFSlotConfiguration cfg = GTCombustionHelper.getMCFConfiguration(node);
        renderSlotRows(graphics, font, cfg, rightX, startY, rightW, height, mouseX, mouseY);
    }

    private void renderPresetButtons(GuiGraphics graphics, Font font, int rightX, int startY, int rightW, int mouseX, int mouseY) {
        renderMiniBtn(graphics, font, Component.translatable("gui.gtcalcboard.mcf.preset_8x_ucm").getString(), rightX + 4, startY + 3, 86, mouseX, mouseY);
        renderMiniBtn(graphics, font, Component.translatable("gui.gtcalcboard.mcf.preset_8x_scm").getString(), rightX + 94, startY + 3, 86, mouseX, mouseY);
        renderMiniBtn(graphics, font, Component.translatable("gui.gtcalcboard.mcf.preset_clear").getString(), rightX + 184, startY + 3, 64, mouseX, mouseY);
    }

    private void renderSlotRows(GuiGraphics graphics, Font font, MCFSlotConfiguration cfg, int rightX, int startY, int rightW, int height, int mouseX, int mouseY) {
        int clipTop = startY + 20;
        int clipBottom = startY + height - 2;
        dialog.enableScaledScissor(graphics, rightX + 2, clipTop, rightX + rightW - 2, clipBottom);

        int typeBtnW = 68;
        int fuelBtnW = 94;
        int typeBtnX = rightX + 38;
        int fuelBtnX = typeBtnX + typeBtnW + 3;
        int oxBtnX = fuelBtnX + fuelBtnW + 3;
        int oxBtnW = rightW - (oxBtnX - rightX) - 6;

        for (int i = 0; i < MCFSlotConfiguration.MAX_SLOTS; i++) {
            MCFModuleSlot slot = cfg.getSlot(i);
            int rowY = startY + 20 + i * 14 - (int) scrollY;
            if (rowY + 13 < clipTop || rowY > clipBottom) continue;

            boolean enabled = slot.isEnabled();
            graphics.fill(rightX + 4, rowY, rightX + rightW - 4, rowY + 13, enabled ? 0xFF1C212E : 0xFF161922);

            int cbX = rightX + 6;
            graphics.fill(cbX, rowY + 1, cbX + 11, rowY + 12, enabled ? 0xFF285038 : 0xFF222632);
            graphics.renderOutline(cbX, rowY + 1, 11, 11, enabled ? 0xFF45A065 : 0xFF3D4759);
            graphics.drawCenteredString(font, enabled ? "✔" : "", cbX + 5, rowY + 2, 0xFF55FF55);

            graphics.drawString(font, "#" + (i + 1), rightX + 20, rowY + 3, enabled ? 0xFFFFFFFF : 0xFF777777, false);

            renderSlotBtn(graphics, font, slot.getModuleType().getDisplayName(), typeBtnX, rowY + 1, typeBtnW, 11, mouseX, mouseY, enabled, 0xFF58D3FF);
            renderSlotBtn(graphics, font, slot.getFuel().getDisplayName(), fuelBtnX, rowY + 1, fuelBtnW, 11, mouseX, mouseY, enabled, 0xFFFFB347);

            int amps = slot.isOxidizerBoosted() ? slot.getModuleType().getBoostAmps() : slot.getModuleType().getBaseAmps();
            String oxLabel = (slot.isOxidizerBoosted() ? "⚡ Ox " : "○ Ox ") + amps + "A";
            renderSlotBtn(graphics, font, oxLabel, oxBtnX, rowY + 1, oxBtnW, 11, mouseX, mouseY, enabled, slot.isOxidizerBoosted() ? 0xFF55FF55 : 0xFF888888);

            if (enabled && mouseX >= fuelBtnX && mouseX <= fuelBtnX + fuelBtnW && mouseY >= rowY + 1 && mouseY <= rowY + 12) {
                MCFModuleType type = slot.getModuleType();
                MCFFuel fuel = slot.getFuel();
                double demandPerTick = ((double) type.getTier().getVoltage() * (slot.isOxidizerBoosted() ? 2.0 : 1.0)) / fuel.getEnergyPerMb();
                double demandPerSec = demandPerTick * 20.0;
                dialog.setDeferredTooltip(List.of(
                        Component.literal("§6" + fuel.getDisplayName()),
                        Component.literal(String.format(Locale.ROOT, "§7Demand: §e%.2f §7mB/t (§e%.1f §7mB/s)", demandPerTick, demandPerSec))
                ));
            }
        }

        BoardScissorHelper.disableScissor(graphics);
    }

    private void renderSlotBtn(GuiGraphics graphics, Font font, String label, int bx, int by, int bw, int bh, int mouseX, int mouseY, boolean active, int textColor) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? 0xFF35445C : (active ? 0xFF232B3A : 0xFF1A1E29));
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF58D3FF : (active ? 0xFF3D4C63 : 0xFF2A303F));
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label, bw - 2), bx + bw / 2, by + 2, active ? textColor : 0xFF666666);
    }

    private void renderMiniBtn(GuiGraphics graphics, Font font, String label, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 13;
        graphics.fill(bx, by, bx + bw, by + 13, hover ? 0xFF35445C : 0xFF232B3A);
        graphics.renderOutline(bx, by, bw, 13, hover ? 0xFF58D3FF : 0xFF3D4C63);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 3, hover ? 0xFF58D3FF : 0xFFB0C0D8);
    }

    public boolean mouseClicked(int startX, int startY, int width, int height, double mX, double mY, int button, RecipeNode node, BoardScreen parent) {
        int leftW = 168;
        if (handleLeftPanelClick(startX, startY, leftW, mX, mY, node)) {
            return true;
        }

        int rightX = startX + leftW + 4;
        int rightW = width - leftW - 4;
        return handleRightPanelClick(rightX, startY, rightW, height, mX, mY, button, node);
    }

    private boolean handleLeftPanelClick(int startX, int startY, int leftW, double mX, double mY, RecipeNode node) {
        if (mX < startX + 4 || mX > startX + leftW - 4) return false;

        if (mY >= startY + 16 && mY <= startY + 29) {
            GTCombustionHelper.setMCFCoolantType(node, "none");
            onConfigChanged(node);
            return true;
        }
        if (mY >= startY + 31 && mY <= startY + 44) {
            GTCombustionHelper.setMCFCoolantType(node, "distilled_water");
            onConfigChanged(node);
            return true;
        }
        if (mY >= startY + 46 && mY <= startY + 59) {
            GTCombustionHelper.setMCFCoolantType(node, "deionized_water");
            onConfigChanged(node);
            return true;
        }
        return false;
    }

    private boolean handleRightPanelClick(int rightX, int startY, int rightW, int height, double mX, double mY, int button, RecipeNode node) {
        MCFSlotConfiguration cfg = GTCombustionHelper.getMCFConfiguration(node);
        if (handlePresetClick(rightX, startY, mX, mY, cfg, node)) {
            return true;
        }

        return handleSlotRowsClick(rightX, startY, rightW, height, mX, mY, button, cfg, node);
    }

    private boolean handlePresetClick(int rightX, int startY, double mX, double mY, MCFSlotConfiguration cfg, RecipeNode node) {
        if (mY < startY + 3 || mY > startY + 16) return false;

        if (mX >= rightX + 4 && mX <= rightX + 90) {
            cfg.applyPreset8xUCM();
            cfg.saveToNode(node);
            onConfigChanged(node);
            return true;
        }
        if (mX >= rightX + 94 && mX <= rightX + 180) {
            cfg.applyPreset8xSCM();
            cfg.saveToNode(node);
            onConfigChanged(node);
            return true;
        }
        if (mX >= rightX + 184 && mX <= rightX + 248) {
            cfg.clearAll();
            cfg.saveToNode(node);
            onConfigChanged(node);
            return true;
        }
        return false;
    }

    private boolean handleSlotRowsClick(int rightX, int startY, int rightW, int height, double mX, double mY, int button, MCFSlotConfiguration cfg, RecipeNode node) {
        int clipTop = startY + 20;
        int clipBottom = startY + height - 2;
        if (mY < clipTop || mY > clipBottom) return false;

        int typeBtnW = 68;
        int fuelBtnW = 94;
        int typeBtnX = rightX + 38;
        int fuelBtnX = typeBtnX + typeBtnW + 3;
        int oxBtnX = fuelBtnX + fuelBtnW + 3;
        int oxBtnW = rightW - (oxBtnX - rightX) - 6;

        for (int i = 0; i < MCFSlotConfiguration.MAX_SLOTS; i++) {
            int rowY = startY + 20 + i * 14 - (int) scrollY;
            if (mY < rowY || mY > rowY + 13) continue;

            MCFModuleSlot slot = cfg.getSlot(i);
            if (mX >= rightX + 6 && mX <= rightX + 17) {
                slot.setEnabled(!slot.isEnabled());
                saveAndNotify(cfg, node);
                return true;
            }
            if (mX >= typeBtnX && mX <= typeBtnX + typeBtnW) {
                MCFModuleType next = (button == 1) ? previousModuleType(slot.getModuleType()) : slot.getModuleType().next();
                slot.setModuleType(next);
                slot.setEnabled(true);
                saveAndNotify(cfg, node);
                return true;
            }
            if (mX >= fuelBtnX && mX <= fuelBtnX + fuelBtnW) {
                MCFFuel next = (button == 1) ? previousFuel(slot.getFuel()) : slot.getFuel().next();
                slot.setFuel(next);
                slot.setEnabled(true);
                saveAndNotify(cfg, node);
                return true;
            }
            if (mX >= oxBtnX && mX <= oxBtnX + oxBtnW) {
                slot.setOxidizerBoosted(!slot.isOxidizerBoosted());
                slot.setEnabled(true);
                saveAndNotify(cfg, node);
                return true;
            }
        }
        return false;
    }

    private void saveAndNotify(MCFSlotConfiguration cfg, RecipeNode node) {
        cfg.saveToNode(node);
        onConfigChanged(node);
    }

    private void onConfigChanged(RecipeNode node) {
        GTCombustionHelper.syncCombustionInputs(node);
        playClickSound();
    }

    private static MCFModuleType previousModuleType(MCFModuleType current) {
        MCFModuleType[] vals = MCFModuleType.values();
        int idx = current != null ? current.ordinal() : 0;
        return vals[(idx - 1 + vals.length) % vals.length];
    }

    private static MCFFuel previousFuel(MCFFuel current) {
        MCFFuel[] vals = MCFFuel.values();
        int idx = current != null ? current.ordinal() : 0;
        return vals[(idx - 1 + vals.length) % vals.length];
    }

    public boolean mouseScrolled(double mX, double mY, double delta, int startX, int startY, int width, int height) {
        int leftW = 168;
        int rightX = startX + leftW + 4;
        int rightW = width - leftW - 4;
        if (mX >= rightX && mX <= rightX + rightW && mY >= startY + 20 && mY <= startY + height) {
            double maxScroll = Math.max(0, MCFSlotConfiguration.MAX_SLOTS * 14 - (height - 22));
            scrollY = Math.max(0, Math.min(maxScroll, scrollY - delta * 14.0));
            return true;
        }
        return false;
    }

    private void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
