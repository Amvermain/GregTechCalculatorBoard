package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTThreadingHelix;
import com.gtceu.calcboard.compat.start.helper.RecipeNodeThreadingHelper;
import com.gtceu.calcboard.compat.start.model.NodeThreadingConfig;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ThreadingHelixView {

    private static final int LEFT_PANEL_WIDTH = 212;
    private static final int PANEL_GAP = 6;
    private static final int ROW_HEIGHT = 28;

    private static final String[] TAB_LABEL_KEYS = {
            "gui.gtcalcboard.threading.tab.supreme",
            "gui.gtcalcboard.threading.tab.overdrive",
            "gui.gtcalcboard.threading.tab.coprocessor",
            "gui.gtcalcboard.threading.tab.weaving"
    };
    private static final String[] TAB_TOOLTIP_KEYS = {
            "gui.gtcalcboard.threading.cat.supreme",
            "gui.gtcalcboard.threading.cat.overdrive",
            "gui.gtcalcboard.threading.cat.coprocessor",
            "gui.gtcalcboard.threading.cat.weaving"
    };

    private final MachineConfigDialog dialog;
    private int selectedHelixTab = 0;

    public ThreadingHelixView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        NodeThreadingConfig cfg = RecipeNodeThreadingHelper.getThreadingConfig(node);
        int maxHelix = MultiblockDetector.getMaxHelixCount(node);
        if (maxHelix > 0) {
            cfg.setMaxHelixCapacity(maxHelix);
        }

        int leftW = LEFT_PANEL_WIDTH;
        graphics.fill(startX, startY, startX + leftW, startY + height, 0xFF14161E);
        graphics.renderOutline(startX, startY, leftW, height, 0xFF2D3342);

        int tabW = leftW / 4;
        String[] tabIcons = {"⚛", "⚡", "⚙", "~"};
        for (int i = 0; i < 4; i++) {
            int tx = startX + i * tabW;
            int currentTabW = (i == 3) ? (leftW - tx + startX) : tabW;
            boolean active = selectedHelixTab == i;
            boolean h = mouseX >= tx && mouseX < tx + currentTabW && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(tx, startY, tx + currentTabW, startY + 14, active ? 0xFF2A344A : (h ? 0xFF202636 : 0xFF181C26));
            if (active) {
                graphics.fill(tx, startY + 13, tx + currentTabW, startY + 14, 0xFF5890FF);
            }
            String tabLabel = tabIcons[i] + " " + Component.translatable(TAB_LABEL_KEYS[i]).getString();
            graphics.drawCenteredString(font, tabLabel, tx + currentTabW / 2, startY + 3, active ? 0xFFFFFFFF : 0xFF888888);

            if (h && dialog != null) {
                dialog.setDeferredTooltip(List.of(Component.translatable(TAB_TOOLTIP_KEYS[i])));
            }
        }

        GTThreadingHelix[] currentTiers;
        if (selectedHelixTab == 0) {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UEV_SUPREME, GTThreadingHelix.UXV_SUPREME, GTThreadingHelix.MAX_SUPREME};
        } else if (selectedHelixTab == 1) {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_OVERDRIVE, GTThreadingHelix.UIV_OVERDRIVE, GTThreadingHelix.OPV_OVERDRIVE};
        } else if (selectedHelixTab == 2) {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_COPROCESSOR, GTThreadingHelix.UIV_COPROCESSOR, GTThreadingHelix.OPV_COPROCESSOR};
        } else {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_WEAVING, GTThreadingHelix.UIV_WEAVING, GTThreadingHelix.OPV_WEAVING};
        }

        int totalInstalled = cfg.getTotalHelixCount();
        boolean atMax = maxHelix > 0 && totalInstalled >= maxHelix;

        int rowY = startY + 18;
        int btnAreaWidth = 68;
        int maxTextW = leftW - btnAreaWidth - 8;

        for (GTThreadingHelix helix : currentTiers) {
            int count = cfg.getHelixCount(helix);
            String hLabel = helix.getTier().getFormatCode() + helix.getDisplayName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(hLabel, maxTextW), startX + 4, rowY + 3, 0xFFFFFFFF, false);

            StringBuilder sb = new StringBuilder();
            if (helix.getGeneral() > 0) sb.append("§b⚛+").append(helix.getGeneral()).append(" ");
            if (helix.getSpeed() > 0) sb.append("§a⚡+").append(helix.getSpeed()).append(" ");
            if (helix.getEfficiency() > 0) sb.append("§e★+").append(helix.getEfficiency()).append(" ");
            if (helix.getParallels() > 0) sb.append("§c⚙+").append(helix.getParallels()).append(" ");
            if (helix.getThreading() > 0) sb.append("§9~+").append(helix.getThreading()).append(" ");
            String statStr = sb.toString().trim();
            graphics.drawString(font, font.plainSubstrByWidth(statStr, maxTextW), startX + 4, rowY + 15, 0xFFAAAAAA, false);

            int btnX = startX + leftW - btnAreaWidth;
            int btnY = rowY + 6;
            renderMiniBtn(graphics, font, "-", btnX, btnY, 14, mouseX, mouseY);
            graphics.drawCenteredString(font, String.valueOf(count), btnX + 22, btnY + 3, count > 0 ? 0xFF55FF55 : 0xFF888888);
            renderMiniBtn(graphics, font, "+", btnX + 30, btnY, 14, atMax ? 0xFF333333 : mouseX, mouseY);
            renderMiniBtn(graphics, font, "+10", btnX + 46, btnY, 20, atMax ? 0xFF333333 : mouseX, mouseY);

            boolean rowHover = mouseX >= startX && mouseX < btnX && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rowHover && dialog != null) {
                List<Component> tt = new ArrayList<>();
                tt.add(helix.getDisplayName().copy().withStyle(net.minecraft.ChatFormatting.GOLD));
                if (helix.getGeneral() > 0) {
                    tt.add(Component.literal("§b⚛ ").append(Component.translatable("gui.gtcalcboard.threading.stat.gen")).append(": +" + helix.getGeneral()));
                }
                if (helix.getSpeed() > 0) {
                    tt.add(Component.literal("§a⚡ ").append(Component.translatable("gui.gtcalcboard.threading.stat.spd")).append(": +" + helix.getSpeed()));
                }
                if (helix.getEfficiency() > 0) {
                    tt.add(Component.literal("§e★ ").append(Component.translatable("gui.gtcalcboard.threading.stat.eff")).append(": +" + helix.getEfficiency()));
                }
                if (helix.getParallels() > 0) {
                    tt.add(Component.literal("§c⚙ ").append(Component.translatable("gui.gtcalcboard.threading.stat.par")).append(": +" + helix.getParallels()));
                }
                if (helix.getThreading() > 0) {
                    tt.add(Component.literal("§9~ ").append(Component.translatable("gui.gtcalcboard.threading.stat.thrd")).append(": +" + helix.getThreading()));
                }
                dialog.setDeferredTooltip(tt);
            }

            rowY += ROW_HEIGHT;
        }

        String helixCapStr = maxHelix > 0
                ? Component.translatable("gui.gtcalcboard.threading.helixes_cap", totalInstalled, maxHelix).getString()
                : Component.translatable("gui.gtcalcboard.threading.helixes_count", totalInstalled).getString();
        graphics.drawString(font, font.plainSubstrByWidth(helixCapStr, leftW - 8), startX + 4, startY + height - 22, 0xFFFFFFFF, false);

        String baseStatsStr = Component.translatable("gui.gtcalcboard.threading.base_stats",
                cfg.getBaseSpeed(), cfg.getBaseEfficiency(), cfg.getBaseParallels(), cfg.getBaseThreading()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(baseStatsStr, leftW - 8), startX + 4, startY + height - 11, 0xFF888888, false);

        int rightX = startX + leftW + PANEL_GAP;
        int rightW = width - leftW - PANEL_GAP;
        graphics.fill(rightX, startY, rightX + rightW, startY + height, 0xFF14161E);
        graphics.renderOutline(rightX, startY, rightW, height, 0xFF2D3342);

        int remGen = cfg.getRemainingGeneral();
        int baseGen = cfg.getBaseGeneral();
        String genBadge = Component.translatable("gui.gtcalcboard.threading.stat.generalis", remGen, baseGen).getString();
        graphics.drawString(font, font.plainSubstrByWidth(genBadge, rightW - 8), rightX + 4, startY + 4, 0xFFFFFFFF, false);

        int statRowY = startY + 16;
        String vLabel = Component.translatable("gui.gtcalcboard.threading.stat.velocitas").getString();
        String vEffect = Component.translatable("gui.gtcalcboard.threading.effect.speed",
                cfg.calculateDurationMultiplier(), 1.0 / Math.max(0.001, cfg.calculateDurationMultiplier())).getString();
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, vLabel, cfg.getAssignedSpeed(), cfg.getTotalSpeed(),
                vEffect, mouseX, mouseY);

        statRowY += 22;
        String eLabel = Component.translatable("gui.gtcalcboard.threading.stat.efficienta").getString();
        String eEffect = Component.translatable("gui.gtcalcboard.threading.effect.efficiency",
                cfg.calculateEnergyMultiplier(), cfg.calculateEnergyMultiplier() * 100.0).getString();
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, eLabel, cfg.getAssignedEfficiency(), cfg.getTotalEfficiency(),
                eEffect, mouseX, mouseY);

        statRowY += 22;
        int effPar = cfg.getEffectiveParallels();
        double parPen = Math.sqrt(effPar);
        String pLabel = Component.translatable("gui.gtcalcboard.threading.stat.parallelismus").getString();
        String pEffect = Component.translatable("gui.gtcalcboard.threading.effect.parallels",
                effPar, (parPen - 1.0) * 100.0).getString();
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, pLabel, cfg.getAssignedParallels(), cfg.getTotalParallels(),
                pEffect, mouseX, mouseY);

        statRowY += 22;
        int effThrd = cfg.getEffectiveThreads();
        String fLabel = Component.translatable("gui.gtcalcboard.threading.stat.filum").getString();
        String fEffect = Component.translatable("gui.gtcalcboard.threading.effect.threads", effThrd).getString();
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, fLabel, cfg.getAssignedThreading(), cfg.getTotalThreading(),
                fEffect, mouseX, mouseY);

        int actY = startY + height - 15;
        String rText = Component.translatable("gui.gtcalcboard.threading.btn.reset").getString();
        String sText = Component.translatable("gui.gtcalcboard.threading.btn.max_spd").getString();
        String eText = Component.translatable("gui.gtcalcboard.threading.btn.max_eff").getString();
        String pText = Component.translatable("gui.gtcalcboard.threading.btn.max_par").getString();
        int btnW1 = font.width(rText) + 8;
        int btnW2 = font.width(sText) + 8;
        int btnW3 = font.width(eText) + 8;
        int btnW4 = font.width(pText) + 8;
        int curBtnX = rightX + 4;
        renderMiniBtn(graphics, font, rText, curBtnX, actY, btnW1, mouseX, mouseY);
        curBtnX += btnW1 + 4;
        renderMiniBtn(graphics, font, sText, curBtnX, actY, btnW2, mouseX, mouseY);
        curBtnX += btnW2 + 4;
        renderMiniBtn(graphics, font, eText, curBtnX, actY, btnW3, mouseX, mouseY);
        curBtnX += btnW3 + 4;
        renderMiniBtn(graphics, font, pText, curBtnX, actY, btnW4, mouseX, mouseY);
    }

    private void renderStatAllocationRow(GuiGraphics graphics, Font font, int rx, int ry, int rw, String label, int assigned, int total, String statEffect, int mouseX, int mouseY) {
        graphics.drawString(font, label + " §8(+" + assigned + ")", rx + 4, ry + 1, 0xFFFFFFFF, false);
        graphics.drawString(font, statEffect, rx + 4, ry + 10, 0xFFAAAAAA, false);

        int btnX = rx + rw - 72;
        renderMiniBtn(graphics, font, "-10", btnX, ry + 2, 16, mouseX, mouseY);
        renderMiniBtn(graphics, font, "-1", btnX + 18, ry + 2, 14, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+1", btnX + 34, ry + 2, 14, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+10", btnX + 50, ry + 2, 20, mouseX, mouseY);
    }

    private void renderMiniBtn(GuiGraphics graphics, Font font, String label, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean h = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
        graphics.fill(bx, by, bx + bw, by + 14, h ? 0xFF3F4658 : 0xFF2B313E);
        graphics.renderOutline(bx, by, bw, 14, 0xFF454E62);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 3, 0xFFE0E6F0);
    }

    public boolean mouseClicked(int startX, int startY, int width, int height, double mouseX, double mouseY, RecipeNode node) {
        NodeThreadingConfig cfg = RecipeNodeThreadingHelper.getThreadingConfig(node);

        int leftW = LEFT_PANEL_WIDTH;
        if (mouseX >= startX && mouseX <= startX + leftW && mouseY >= startY && mouseY <= startY + height) {
            int tabW = leftW / 4;
            if (mouseY >= startY && mouseY <= startY + 14) {
                int clickedTab = (int) ((mouseX - startX) / tabW);
                if (clickedTab >= 0 && clickedTab < 4) {
                    selectedHelixTab = clickedTab;
                    return true;
                }
            }

            GTThreadingHelix[] currentTiers;
            if (selectedHelixTab == 0) {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UEV_SUPREME, GTThreadingHelix.UXV_SUPREME, GTThreadingHelix.MAX_SUPREME};
            } else if (selectedHelixTab == 1) {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_OVERDRIVE, GTThreadingHelix.UIV_OVERDRIVE, GTThreadingHelix.OPV_OVERDRIVE};
            } else if (selectedHelixTab == 2) {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_COPROCESSOR, GTThreadingHelix.UIV_COPROCESSOR, GTThreadingHelix.OPV_COPROCESSOR};
            } else {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_WEAVING, GTThreadingHelix.UIV_WEAVING, GTThreadingHelix.OPV_WEAVING};
            }

            int rowY = startY + 18;
            for (GTThreadingHelix helix : currentTiers) {
                int btnX = startX + leftW - 68;
                int btnY = rowY + 6;
                if (mouseX >= btnX && mouseX <= btnX + 14 && mouseY >= btnY && mouseY <= btnY + 14) {
                    cfg.addHelixCount(helix, -1);
                    return true;
                }
                if (mouseX >= btnX + 30 && mouseX <= btnX + 44 && mouseY >= btnY && mouseY <= btnY + 14) {
                    cfg.addHelixCount(helix, 1);
                    return true;
                }
                if (mouseX >= btnX + 46 && mouseX <= btnX + 66 && mouseY >= btnY && mouseY <= btnY + 14) {
                    cfg.addHelixCount(helix, 10);
                    return true;
                }
                rowY += ROW_HEIGHT;
            }
        }

        int rightX = startX + leftW + PANEL_GAP;
        int rightW = width - leftW - PANEL_GAP;
        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= startY && mouseY <= startY + height) {
            int btnX = rightX + rightW - 72;

            int statRowY = startY + 16;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedSpeed(Math.max(0, cfg.getAssignedSpeed() - 10));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedSpeed(Math.max(0, cfg.getAssignedSpeed() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + add);
                    return true;
                }
            }

            statRowY += 22;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedEfficiency(Math.max(0, cfg.getAssignedEfficiency() - 10));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedEfficiency(Math.max(0, cfg.getAssignedEfficiency() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + add);
                    return true;
                }
            }

            statRowY += 22;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedParallels(Math.max(0, cfg.getAssignedParallels() - 10));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedParallels(Math.max(0, cfg.getAssignedParallels() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + add);
                    return true;
                }
            }

            statRowY += 22;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedThreading(Math.max(0, cfg.getAssignedThreading() - 10));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedThreading(Math.max(0, cfg.getAssignedThreading() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedThreading(cfg.getAssignedThreading() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedThreading(cfg.getAssignedThreading() + add);
                    return true;
                }
            }

            int actY = startY + height - 15;
            if (mouseY >= actY && mouseY <= actY + 14) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                Font font = mc != null ? mc.font : null;
                String rText = Component.translatable("gui.gtcalcboard.threading.btn.reset").getString();
                String sText = Component.translatable("gui.gtcalcboard.threading.btn.max_spd").getString();
                String eText = Component.translatable("gui.gtcalcboard.threading.btn.max_eff").getString();
                String pText = Component.translatable("gui.gtcalcboard.threading.btn.max_par").getString();
                int btnW1 = (font != null ? font.width(rText) : 32) + 8;
                int btnW2 = (font != null ? font.width(sText) : 38) + 8;
                int btnW3 = (font != null ? font.width(eText) : 38) + 8;
                int btnW4 = (font != null ? font.width(pText) : 34) + 8;

                int curBtnX = rightX + 4;
                if (mouseX >= curBtnX && mouseX <= curBtnX + btnW1) {
                    cfg.reset();
                    return true;
                }
                curBtnX += btnW1 + 4;
                if (mouseX >= curBtnX && mouseX <= curBtnX + btnW2) {
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + cfg.getRemainingGeneral());
                    return true;
                }
                curBtnX += btnW2 + 4;
                if (mouseX >= curBtnX && mouseX <= curBtnX + btnW3) {
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + cfg.getRemainingGeneral());
                    return true;
                }
                curBtnX += btnW3 + 4;
                if (mouseX >= curBtnX && mouseX <= curBtnX + btnW4) {
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + cfg.getRemainingGeneral());
                    return true;
                }
            }
        }

        return false;
    }
}
