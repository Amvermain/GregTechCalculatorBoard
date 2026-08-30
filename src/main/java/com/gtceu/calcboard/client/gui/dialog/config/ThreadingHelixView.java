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

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        NodeThreadingConfig cfg = node.getThreadingConfig();
        int maxHelix = MultiblockDetector.getMaxHelixCount(node);
        if (maxHelix > 0) {
            cfg.setMaxHelixCapacity(maxHelix);
        }

        int leftW = 184;
        graphics.fill(startX, startY, startX + leftW, startY + height, 0xFF14161E);
        graphics.renderOutline(startX, startY, leftW, height, 0xFF2D3342);

        int tabW = leftW / 4;
        String[] tabShortNames = {"Sup", "Spd", "Par", "Thrd"};
        String[] tabIcons = {"⚛", "⚡", "⚙", "🧵"};
        for (int i = 0; i < 4; i++) {
            int tx = startX + i * tabW;
            boolean active = selectedHelixTab == i;
            boolean h = mouseX >= tx && mouseX < tx + tabW && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(tx, startY, tx + tabW, startY + 14, active ? 0xFF2A344A : (h ? 0xFF202636 : 0xFF181C26));
            if (active) {
                graphics.fill(tx, startY + 13, tx + tabW, startY + 14, 0xFF5890FF);
            }
            graphics.drawCenteredString(font, tabIcons[i] + " " + tabShortNames[i], tx + tabW / 2, startY + 3, active ? 0xFFFFFFFF : 0xFF888888);
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
        for (GTThreadingHelix helix : currentTiers) {
            int count = cfg.getHelixCount(helix);
            String hLabel = "§e" + helix.getTier().name() + " §f" + helix.getEnglishName();
            graphics.drawString(font, font.plainSubstrByWidth(hLabel, leftW - 75), startX + 4, rowY + 3, 0xFFFFFFFF, false);

            StringBuilder sb = new StringBuilder("§7");
            if (helix.getGeneral() > 0) sb.append("+").append(helix.getGeneral()).append("Gen ");
            if (helix.getSpeed() > 0) sb.append("+").append(helix.getSpeed()).append("Spd ");
            if (helix.getEfficiency() > 0) sb.append("+").append(helix.getEfficiency()).append("Eff ");
            if (helix.getParallels() > 0) sb.append("+").append(helix.getParallels()).append("Par ");
            if (helix.getThreading() > 0) sb.append("+").append(helix.getThreading()).append("Thrd ");
            graphics.drawString(font, sb.toString().trim(), startX + 4, rowY + 14, 0xFFAAAAAA, false);

            int btnX = startX + leftW - 68;
            renderMiniBtn(graphics, font, "-", btnX, rowY + 5, 14, mouseX, mouseY);
            graphics.drawCenteredString(font, String.valueOf(count), btnX + 22, rowY + 8, count > 0 ? 0xFF55FF55 : 0xFF888888);
            renderMiniBtn(graphics, font, "+", btnX + 30, rowY + 5, 14, atMax ? 0xFF333333 : mouseX, mouseY);
            renderMiniBtn(graphics, font, "+10", btnX + 46, rowY + 5, 20, atMax ? 0xFF333333 : mouseX, mouseY);

            rowY += 26;
        }

        String helixCapStr = maxHelix > 0
                ? String.format("§e🧬 Helixes: §a%d §7/ §e%d", totalInstalled, maxHelix)
                : String.format("§e🧬 Helixes: §a%d", totalInstalled);
        graphics.drawString(font, helixCapStr, startX + 4, startY + height - 22, 0xFFFFFFFF, false);

        String baseStatsStr = String.format("§8Base: +%d Spd | +%d Eff | +%d Par | +%d Thrd",
                cfg.getBaseSpeed(), cfg.getBaseEfficiency(), cfg.getBaseParallels(), cfg.getBaseThreading());
        graphics.drawString(font, font.plainSubstrByWidth(baseStatsStr, leftW - 6), startX + 4, startY + height - 11, 0xFF888888, false);

        int rightX = startX + 190;
        int rightW = width - 190;
        graphics.fill(rightX, startY, rightX + rightW, startY + height, 0xFF14161E);
        graphics.renderOutline(rightX, startY, rightW, height, 0xFF2D3342);

        int remGen = cfg.getRemainingGeneral();
        int baseGen = cfg.getBaseGeneral();
        String genBadge = String.format("§b⚛ Generalis: §a%d §7/ §e%d pt §7(Avail/Total)", remGen, baseGen);
        graphics.drawString(font, genBadge, rightX + 4, startY + 4, 0xFFFFFFFF, false);

        int statRowY = startY + 16;
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "⚡ Velocitas", cfg.getAssignedSpeed(), cfg.getTotalSpeed(),
                String.format("§a⏱ %.2fx Dur (%.1fx Spd)", cfg.calculateDurationMultiplier(), 1.0 / Math.max(0.001, cfg.calculateDurationMultiplier())),
                mouseX, mouseY);

        statRowY += 22;
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "💡 Efficienta", cfg.getAssignedEfficiency(), cfg.getTotalEfficiency(),
                String.format("§e⚡ %.2fx Power (%.0f%% Cost)", cfg.calculateEnergyMultiplier(), cfg.calculateEnergyMultiplier() * 100.0),
                mouseX, mouseY);

        statRowY += 22;
        int effPar = cfg.getEffectiveParallels();
        double parPen = Math.sqrt(effPar);
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "⚙ Parallelismus", cfg.getAssignedParallels(), cfg.getTotalParallels(),
                String.format("§c⚡ %dx Par (⏱ +%.0f%% Time)", effPar, (parPen - 1.0) * 100.0),
                mouseX, mouseY);

        statRowY += 22;
        int effThrd = cfg.getEffectiveThreads();
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "🧵 Filum", cfg.getAssignedThreading(), cfg.getTotalThreading(),
                String.format("§9🧵 %d Threads", effThrd),
                mouseX, mouseY);

        int actY = startY + height - 15;
        renderMiniBtn(graphics, font, "↺ Reset", rightX + 4, actY, 40, mouseX, mouseY);
        renderMiniBtn(graphics, font, "⚡ Max Spd", rightX + 48, actY, 46, mouseX, mouseY);
        renderMiniBtn(graphics, font, "💡 Max Eff", rightX + 98, actY, 46, mouseX, mouseY);
        renderMiniBtn(graphics, font, "⚙ Max Par", rightX + 148, actY, 42, mouseX, mouseY);
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
        NodeThreadingConfig cfg = node.getThreadingConfig();

        int leftW = 184;
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
                if (mouseX >= btnX && mouseX <= btnX + 14 && mouseY >= rowY + 5 && mouseY <= rowY + 19) {
                    cfg.addHelixCount(helix, -1);
                    return true;
                }
                if (mouseX >= btnX + 30 && mouseX <= btnX + 44 && mouseY >= rowY + 5 && mouseY <= rowY + 19) {
                    cfg.addHelixCount(helix, 1);
                    return true;
                }
                if (mouseX >= btnX + 46 && mouseX <= btnX + 66 && mouseY >= rowY + 5 && mouseY <= rowY + 19) {
                    cfg.addHelixCount(helix, 10);
                    return true;
                }
                rowY += 26;
            }
        }

        int rightX = startX + 190;
        int rightW = width - 190;
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
                    cfg.setAssignedThreading(Math.max(0, cfg.getAssignedThreading() - 5));
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
                    int add = Math.min(5, cfg.getRemainingGeneral());
                    cfg.setAssignedThreading(cfg.getAssignedThreading() + add);
                    return true;
                }
            }

            int actY = startY + height - 15;
            if (mouseY >= actY && mouseY <= actY + 14) {
                if (mouseX >= rightX + 4 && mouseX <= rightX + 44) {
                    cfg.reset();
                    return true;
                }
                if (mouseX >= rightX + 48 && mouseX <= rightX + 94) {
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + cfg.getRemainingGeneral());
                    return true;
                }
                if (mouseX >= rightX + 98 && mouseX <= rightX + 144) {
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + cfg.getRemainingGeneral());
                    return true;
                }
                if (mouseX >= rightX + 148 && mouseX <= rightX + 190) {
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + cfg.getRemainingGeneral());
                    return true;
                }
            }
        }

        return false;
    }
}
