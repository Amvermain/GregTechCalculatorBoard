/*
 * Decompiled with CFR 0.152.
 */
package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTThreadingHelix;
import com.gtceu.calcboard.api.type.NodeThreadingConfig;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ThreadingHelixView {
    private final MachineConfigDialog dialog;
    private int selectedHelixTab = 0;

    public ThreadingHelixView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public int getSelectedHelixTab() {
        return this.selectedHelixTab;
    }

    public void setSelectedHelixTab(int selectedHelixTab) {
        this.selectedHelixTab = selectedHelixTab;
    }

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        if (node == null) {
            return;
        }
        NodeThreadingConfig cfg = node.getThreadingConfig();
        int maxHelix = MultiblockDetector.getMaxHelixCount(node);
        if (maxHelix > 0) {
            cfg.setMaxHelixCapacity(maxHelix);
        }
        int leftW = 184;
        graphics.fill(startX, startY, startX + leftW, startY + height, -15460834);
        graphics.renderOutline(startX, startY, leftW, height, -13814974);
        int tabW = leftW / 4;
        String[] tabShortNames = new String[]{"Sup", "Spd", "Par", "Thrd"};
        String[] tabIcons = new String[]{"\ud83d\udc8e", "\ud83d\udfe2", "\ud83d\udd34", "\ud83d\udd35"};
        for (int i = 0; i < 4; ++i) {
            boolean h;
            int tx = startX + i * tabW;
            boolean active = this.selectedHelixTab == i;
            boolean bl = h = mouseX >= tx && mouseX < tx + tabW && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(tx, startY, tx + tabW, startY + 14, active ? -14011318 : (h ? -14670282 : -15197146));
            if (active) {
                graphics.fill(tx, startY + 13, tx + tabW, startY + 14, -10972929);
            }
            graphics.drawCenteredString(font, tabIcons[i] + " " + tabShortNames[i], tx + tabW / 2, startY + 3, active ? -1 : -7829368);
        }
        GTThreadingHelix[] currentTiers = this.selectedHelixTab == 0 ? new GTThreadingHelix[]{GTThreadingHelix.UEV_SUPREME, GTThreadingHelix.UXV_SUPREME, GTThreadingHelix.MAX_SUPREME} : (this.selectedHelixTab == 1 ? new GTThreadingHelix[]{GTThreadingHelix.UHV_OVERDRIVE, GTThreadingHelix.UIV_OVERDRIVE, GTThreadingHelix.OPV_OVERDRIVE} : (this.selectedHelixTab == 2 ? new GTThreadingHelix[]{GTThreadingHelix.UHV_COPROCESSOR, GTThreadingHelix.UIV_COPROCESSOR, GTThreadingHelix.OPV_COPROCESSOR} : new GTThreadingHelix[]{GTThreadingHelix.UHV_WEAVING, GTThreadingHelix.UIV_WEAVING, GTThreadingHelix.OPV_WEAVING}));
        int totalInstalled = cfg.getTotalHelixCount();
        boolean atMax = maxHelix > 0 && totalInstalled >= maxHelix;
        int rowY = startY + 18;
        for (GTThreadingHelix helix : currentTiers) {
            int count = cfg.getHelixCount(helix);
            String hLabel = "\u00a7e" + helix.getTier().name() + " \u00a7f" + helix.getEnglishName();
            graphics.drawString(font, font.plainSubstrByWidth(hLabel, leftW - 75), startX + 4, rowY + 3, -1, false);
            StringBuilder sb = new StringBuilder("\u00a77");
            if (helix.getGeneral() > 0) {
                sb.append("+" + helix.getGeneral() + "Gen ");
            }
            if (helix.getSpeed() > 0) {
                sb.append("+" + helix.getSpeed() + "Spd ");
            }
            if (helix.getEfficiency() > 0) {
                sb.append("+" + helix.getEfficiency() + "Eff ");
            }
            if (helix.getParallels() > 0) {
                sb.append("+" + helix.getParallels() + "Par ");
            }
            if (helix.getThreading() > 0) {
                sb.append("+" + helix.getThreading() + "Thrd ");
            }
            graphics.drawString(font, sb.toString().trim(), startX + 4, rowY + 14, -5592406, false);
            int btnX = startX + leftW - 68;
            this.renderMiniBtn(graphics, font, "-", btnX, rowY + 5, 14, mouseX, mouseY);
            graphics.drawCenteredString(font, String.valueOf(count), btnX + 22, rowY + 8, count > 0 ? -11141291 : -7829368);
            this.renderMiniBtn(graphics, font, "+", btnX + 30, rowY + 5, 14, atMax ? -13421773 : mouseX, mouseY);
            this.renderMiniBtn(graphics, font, "+10", btnX + 46, rowY + 5, 20, atMax ? -13421773 : mouseX, mouseY);
            rowY += 26;
        }
        String helixCapStr = maxHelix > 0 ? String.format("\u00a7e\ud83c\udf00 Helixes: \u00a7a%d \u00a77/ \u00a7e%d", totalInstalled, maxHelix) : String.format("\u00a7e\ud83c\udf00 Helixes: \u00a7a%d", totalInstalled);
        graphics.drawString(font, helixCapStr, startX + 4, startY + height - 22, -1, false);
        String baseStatsStr = String.format("\u00a78Base: +%d Spd | +%d Eff | +%d Par | +%d Thrd", cfg.getBaseSpeed(), cfg.getBaseEfficiency(), cfg.getBaseParallels(), cfg.getBaseThreading());
        graphics.drawString(font, font.plainSubstrByWidth(baseStatsStr, leftW - 6), startX + 4, startY + height - 11, -7829368, false);
        int rightX = startX + 190;
        int rightW = width - 190;
        graphics.fill(rightX, startY, rightX + rightW, startY + height, -15460834);
        graphics.renderOutline(rightX, startY, rightW, height, -13814974);
        int remGen = cfg.getRemainingGeneral();
        int baseGen = cfg.getBaseGeneral();
        String genBadge = String.format("\u00a7b\ud83d\udc8e Generalis: \u00a7a%d \u00a77/ \u00a7e%d pt \u00a77(Avail/Total)", remGen, baseGen);
        graphics.drawString(font, genBadge, rightX + 4, startY + 4, -1, false);
        int statRowY = startY + 16;
        this.renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "\ud83d\udfe2 Velocitas", cfg.getAssignedSpeed(), cfg.getTotalSpeed(), String.format("\u00a7a\u23f1 %.2fx Dur (%.1fx Spd)", cfg.calculateDurationMultiplier(), 1.0 / Math.max(0.001, cfg.calculateDurationMultiplier())), mouseX, mouseY);
        this.renderStatAllocationRow(graphics, font, rightX, statRowY += 22, rightW, "\ud83d\udfe3 Efficienta", cfg.getAssignedEfficiency(), cfg.getTotalEfficiency(), String.format("\u00a7e\u26a1 %.2fx Power (%.0f%% Cost)", cfg.calculateEnergyMultiplier(), cfg.calculateEnergyMultiplier() * 100.0), mouseX, mouseY);
        int effPar = cfg.getEffectiveParallels();
        double parPen = Math.sqrt(effPar);
        this.renderStatAllocationRow(graphics, font, rightX, statRowY += 22, rightW, "\ud83d\udd34 Parallelismus", cfg.getAssignedParallels(), cfg.getTotalParallels(), String.format("\u00a7c\u26a1 %dx Par (\u23f1 +%.0f%% Time)", effPar, (parPen - 1.0) * 100.0), mouseX, mouseY);
        int effThrd = cfg.getEffectiveThreads();
        this.renderStatAllocationRow(graphics, font, rightX, statRowY += 22, rightW, "\ud83d\udd35 Filum", cfg.getAssignedThreading(), cfg.getTotalThreading(), String.format("\u00a79\ud83e\uddf5 %d Threads", effThrd), mouseX, mouseY);
        int actY = startY + height - 15;
        this.renderMiniBtn(graphics, font, "\u21ba Reset", rightX + 4, actY, 40, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "\u26a1 Max Spd", rightX + 48, actY, 46, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "\ud83d\udd0b Max Eff", rightX + 98, actY, 46, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "\u26a1 Max Par", rightX + 148, actY, 42, mouseX, mouseY);
    }

    private void renderStatAllocationRow(GuiGraphics graphics, Font font, int rx, int ry, int rw, String label, int assigned, int total, String statEffect, int mouseX, int mouseY) {
        graphics.drawString(font, label + " \u00a78(+" + assigned + ")", rx + 4, ry + 1, -1, false);
        graphics.drawString(font, statEffect, rx + 4, ry + 10, -5592406, false);
        int btnX = rx + rw - 72;
        this.renderMiniBtn(graphics, font, "-10", btnX, ry + 2, 16, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "-1", btnX + 18, ry + 2, 14, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "+1", btnX + 34, ry + 2, 14, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "+10", btnX + 50, ry + 2, 20, mouseX, mouseY);
    }

    private void renderMiniBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean h = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
        graphics.fill(bx, by, bx + bw, by + 14, h ? -12761511 : -14473421);
        graphics.renderOutline(bx, by, bw, 14, h ? -10955777 : -12761511);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + 3, h ? -10955777 : -5195576);
    }

    public boolean mouseClicked(int startX, int startY, int width, int height, double mouseX, double mouseY, RecipeNode node) {
        if (node == null) {
            return false;
        }
        NodeThreadingConfig cfg = node.getThreadingConfig();
        int leftW = 184;
        if (mouseX >= (double)startX && mouseX <= (double)(startX + leftW) && mouseY >= (double)startY && mouseY <= (double)(startY + height)) {
            int clickedTab;
            int tabW = leftW / 4;
            if (mouseY >= (double)startY && mouseY <= (double)(startY + 14) && (clickedTab = (int)((mouseX - (double)startX) / (double)tabW)) >= 0 && clickedTab < 4) {
                this.selectedHelixTab = clickedTab;
                return true;
            }
            GTThreadingHelix[] currentTiers = this.selectedHelixTab == 0 ? new GTThreadingHelix[]{GTThreadingHelix.UEV_SUPREME, GTThreadingHelix.UXV_SUPREME, GTThreadingHelix.MAX_SUPREME} : (this.selectedHelixTab == 1 ? new GTThreadingHelix[]{GTThreadingHelix.UHV_OVERDRIVE, GTThreadingHelix.UIV_OVERDRIVE, GTThreadingHelix.OPV_OVERDRIVE} : (this.selectedHelixTab == 2 ? new GTThreadingHelix[]{GTThreadingHelix.UHV_COPROCESSOR, GTThreadingHelix.UIV_COPROCESSOR, GTThreadingHelix.OPV_COPROCESSOR} : new GTThreadingHelix[]{GTThreadingHelix.UHV_WEAVING, GTThreadingHelix.UIV_WEAVING, GTThreadingHelix.OPV_WEAVING}));
            int rowY = startY + 18;
            for (GTThreadingHelix helix : currentTiers) {
                int btnX = startX + leftW - 68;
                if (mouseX >= (double)btnX && mouseX <= (double)(btnX + 14) && mouseY >= (double)(rowY + 5) && mouseY <= (double)(rowY + 19)) {
                    cfg.addHelixCount(helix, -1);
                    return true;
                }
                if (mouseX >= (double)(btnX + 30) && mouseX <= (double)(btnX + 44) && mouseY >= (double)(rowY + 5) && mouseY <= (double)(rowY + 19)) {
                    cfg.addHelixCount(helix, 1);
                    return true;
                }
                if (mouseX >= (double)(btnX + 46) && mouseX <= (double)(btnX + 66) && mouseY >= (double)(rowY + 5) && mouseY <= (double)(rowY + 19)) {
                    cfg.addHelixCount(helix, 10);
                    return true;
                }
                rowY += 26;
            }
        }
        int rightX = startX + 190;
        int rightW = width - 190;
        if (mouseX >= (double)rightX && mouseX <= (double)(rightX + rightW) && mouseY >= (double)startY && mouseY <= (double)(startY + height)) {
            int actY;
            int add;
            int btnX = rightX + rightW - 72;
            int statRowY = startY + 16;
            if (mouseY >= (double)(statRowY + 2) && mouseY <= (double)(statRowY + 16)) {
                if (mouseX >= (double)btnX && mouseX <= (double)(btnX + 16)) {
                    cfg.setAssignedSpeed(Math.max(0, cfg.getAssignedSpeed() - 10));
                    return true;
                }
                if (mouseX >= (double)(btnX + 18) && mouseX <= (double)(btnX + 32)) {
                    cfg.setAssignedSpeed(Math.max(0, cfg.getAssignedSpeed() - 1));
                    return true;
                }
                if (mouseX >= (double)(btnX + 34) && mouseX <= (double)(btnX + 48)) {
                    add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + add);
                    return true;
                }
                if (mouseX >= (double)(btnX + 50) && mouseX <= (double)(btnX + 70)) {
                    add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + add);
                    return true;
                }
            }
            if (mouseY >= (double)((statRowY += 22) + 2) && mouseY <= (double)(statRowY + 16)) {
                if (mouseX >= (double)btnX && mouseX <= (double)(btnX + 16)) {
                    cfg.setAssignedEfficiency(Math.max(0, cfg.getAssignedEfficiency() - 10));
                    return true;
                }
                if (mouseX >= (double)(btnX + 18) && mouseX <= (double)(btnX + 32)) {
                    cfg.setAssignedEfficiency(Math.max(0, cfg.getAssignedEfficiency() - 1));
                    return true;
                }
                if (mouseX >= (double)(btnX + 34) && mouseX <= (double)(btnX + 48)) {
                    add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + add);
                    return true;
                }
                if (mouseX >= (double)(btnX + 50) && mouseX <= (double)(btnX + 70)) {
                    add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + add);
                    return true;
                }
            }
            if (mouseY >= (double)((statRowY += 22) + 2) && mouseY <= (double)(statRowY + 16)) {
                if (mouseX >= (double)btnX && mouseX <= (double)(btnX + 16)) {
                    cfg.setAssignedParallels(Math.max(0, cfg.getAssignedParallels() - 10));
                    return true;
                }
                if (mouseX >= (double)(btnX + 18) && mouseX <= (double)(btnX + 32)) {
                    cfg.setAssignedParallels(Math.max(0, cfg.getAssignedParallels() - 1));
                    return true;
                }
                if (mouseX >= (double)(btnX + 34) && mouseX <= (double)(btnX + 48)) {
                    add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + add);
                    return true;
                }
                if (mouseX >= (double)(btnX + 50) && mouseX <= (double)(btnX + 70)) {
                    add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + add);
                    return true;
                }
            }
            if (mouseY >= (double)((statRowY += 22) + 2) && mouseY <= (double)(statRowY + 16)) {
                if (mouseX >= (double)btnX && mouseX <= (double)(btnX + 16)) {
                    cfg.setAssignedThreading(Math.max(0, cfg.getAssignedThreading() - 5));
                    return true;
                }
                if (mouseX >= (double)(btnX + 18) && mouseX <= (double)(btnX + 32)) {
                    cfg.setAssignedThreading(Math.max(0, cfg.getAssignedThreading() - 1));
                    return true;
                }
                if (mouseX >= (double)(btnX + 34) && mouseX <= (double)(btnX + 48)) {
                    add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedThreading(cfg.getAssignedThreading() + add);
                    return true;
                }
                if (mouseX >= (double)(btnX + 50) && mouseX <= (double)(btnX + 70)) {
                    add = Math.min(5, cfg.getRemainingGeneral());
                    cfg.setAssignedThreading(cfg.getAssignedThreading() + add);
                    return true;
                }
            }
            if (mouseY >= (double)(actY = startY + height - 15) && mouseY <= (double)(actY + 14)) {
                if (mouseX >= (double)(rightX + 4) && mouseX <= (double)(rightX + 44)) {
                    cfg.reset();
                    return true;
                }
                if (mouseX >= (double)(rightX + 48) && mouseX <= (double)(rightX + 94)) {
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + cfg.getRemainingGeneral());
                    return true;
                }
                if (mouseX >= (double)(rightX + 98) && mouseX <= (double)(rightX + 144)) {
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + cfg.getRemainingGeneral());
                    return true;
                }
                if (mouseX >= (double)(rightX + 148) && mouseX <= (double)(rightX + 190)) {
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + cfg.getRemainingGeneral());
                    return true;
                }
            }
        }
        return false;
    }
}
