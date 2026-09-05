package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.ProductionETACalculator;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class NodeInspectorPanel {

    private final BoardScreen screen;
    private NodeWidget targetWidget = null;
    private boolean visible = false;
    private static final int PANEL_WIDTH = 195;

    public NodeInspectorPanel(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean isVisible() {
        return visible && targetWidget != null && screen.getGraph().findNodeById(targetWidget.getNode().getId()) != null;
    }

    public void setTargetWidget(NodeWidget widget) {
        this.targetWidget = widget;
        this.visible = (widget != null);
    }

    public void close() {
        this.visible = false;
        this.targetWidget = null;
    }

    public int getPanelWidth() {
        return isVisible() ? PANEL_WIDTH : 0;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        Font font = Minecraft.getInstance().font;
        int screenW = screen.width;
        int screenH = screen.height;

        int px = screenW - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;
        int ph = Math.max(160, screenH - py - 32);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350.0f);

        // Panel Background & Border
        graphics.fill(px, py, px + PANEL_WIDTH, py + ph, 0xF5101522);
        graphics.renderOutline(px, py, PANEL_WIDTH, ph, 0xFF334155);

        // Header
        graphics.fill(px, py, px + PANEL_WIDTH, py + 22, 0xFF1E293B);
        graphics.renderOutline(px, py, PANEL_WIDTH, 22, 0xFF475569);

        var node = targetWidget.getNode();
        if (node.isReroute()) {
            renderJunctionInspector(graphics, font, px, py, ph, node, mouseX, mouseY);
            graphics.pose().popPose();
            return;
        }

        int titleX = px + 6;

        if (node.getMachineIcon() != null) {
            var item = ForgeRegistries.ITEMS.getValue(node.getMachineIcon());
            if (item != null) {
                graphics.renderItem(new ItemStack(item), px + 4, py + 3);
                titleX = px + 24;
            }
        }

        String nodeName = font.plainSubstrByWidth(node.getName(), PANEL_WIDTH - 44);
        graphics.drawString(font, nodeName, titleX, py + 7, 0xFFE2E8F0, false);

        // Close Button [x]
        int closeX = px + PANEL_WIDTH - 16;
        int closeY = py + 5;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);

        int curY = py + 28;

        String countLabel = Component.translatable("gui.gtcalcboard.inspector.count").getString();
        graphics.drawString(font, countLabel, px + 8, curY, 0xFF94A3B8, false);
        curY += 12;

        renderCountControls(graphics, font, px + 8, curY, mouseX, mouseY);
        curY += 22;

        if (node.getTargetTier() != null) {
            String tierLabel = Component.translatable("gui.gtcalcboard.inspector.tier").getString();
            graphics.drawString(font, tierLabel, px + 8, curY, 0xFF94A3B8, false);
            curY += 12;
            renderTierControls(graphics, font, px + 8, curY, mouseX, mouseY);
            curY += 20;
        }

        if (node.getOverclockMode() != null) {
            String ocLabel = Component.translatable("gui.gtcalcboard.inspector.overclock").getString();
            graphics.drawString(font, ocLabel, px + 8, curY, 0xFF94A3B8, false);
            curY += 12;
            renderOverclockModeButton(graphics, font, px + 8, curY, mouseX, mouseY);
            curY += 20;
        }

        renderHardwareSection(graphics, font, px + 8, curY, mouseX, mouseY);
        curY += 44;

        renderStatsSummary(graphics, font, px + 8, curY);

        graphics.pose().popPose();
    }

    private void renderCountControls(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        var node = targetWidget.getNode();
        double count = node.getMachineCount();

        boolean minusHov = mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y, x + 16, y + 16, minusHov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(x, y, 16, 16, minusHov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "-", x + 8, y + 4, minusHov ? 0xFFFFFFFF : 0xFFCBD5E1);

        int boxW = 56;
        int boxX = x + 18;
        graphics.fill(boxX, y, boxX + boxW, y + 16, 0xFF0F172A);
        graphics.renderOutline(boxX, y, boxW, 16, 0xFF38BDF8);
        String countStr = String.format("%.2f", count);
        graphics.drawCenteredString(font, countStr, boxX + boxW / 2, y + 4, 0xFFFCD34D);

        int plusX = boxX + boxW + 2;
        boolean plusHov = mouseX >= plusX && mouseX <= plusX + 16 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(plusX, y, plusX + 16, y + 16, plusHov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(plusX, y, 16, 16, plusHov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "+", plusX + 8, y + 4, plusHov ? 0xFFFFFFFF : 0xFFCBD5E1);

        int halfX = plusX + 18;
        boolean halfHov = mouseX >= halfX && mouseX <= halfX + 22 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(halfX, y, halfX + 22, y + 16, halfHov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(halfX, y, 22, 16, halfHov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "/2", halfX + 11, y + 4, halfHov ? 0xFFFFFFFF : 0xFF94A3B8);

        int doubleX = halfX + 24;
        boolean doubleHov = mouseX >= doubleX && mouseX <= doubleX + 22 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(doubleX, y, doubleX + 22, y + 16, doubleHov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(doubleX, y, 22, 16, doubleHov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "x2", doubleX + 11, y + 4, doubleHov ? 0xFFFFFFFF : 0xFF94A3B8);

        int anchorX = doubleX + 24;
        boolean isBase = node.isBaseNode();
        boolean anchorHov = mouseX >= anchorX && mouseX <= anchorX + 18 && mouseY >= y && mouseY <= y + 16;
        int anchorBg = isBase ? 0xFF78350F : (anchorHov ? 0xFF334155 : 0xFF1E293B);
        int anchorBorder = isBase ? 0xFFF59E0B : (anchorHov ? 0xFF64748B : 0xFF334155);
        graphics.fill(anchorX, y, anchorX + 18, y + 16, anchorBg);
        graphics.renderOutline(anchorX, y, 18, 16, anchorBorder);
        graphics.drawCenteredString(font, "⌖", anchorX + 9, y + 4, isBase ? 0xFFFDE68A : 0xFF94A3B8);
    }

    private java.util.List<GTVoltageTier> getInspectorTiers(com.gtceu.calcboard.api.model.RecipeNode node) {
        if (com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isCombustionFamily(node)) {
            return com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.getAvailableCombustionTiers();
        }
        return java.util.List.of(GTVoltageTier.LV, GTVoltageTier.MV, GTVoltageTier.HV, GTVoltageTier.EV);
    }

    private void renderTierControls(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        var node = targetWidget.getNode();
        GTVoltageTier currentTier = node.getTargetTier();
        java.util.List<GTVoltageTier> tiers = getInspectorTiers(node);
        int totalW = PANEL_WIDTH - 16;
        int count = tiers.size();
        int gap = 4;
        int chipW = Math.max(26, (totalW - gap * (count - 1)) / count);

        for (int i = 0; i < count; i++) {
            GTVoltageTier t = tiers.get(i);
            int cx = x + i * (chipW + gap);
            boolean isCur = (t == currentTier);
            boolean hov = mouseX >= cx && mouseX <= cx + chipW && mouseY >= y && mouseY <= y + 16;

            int bg = isCur ? 0xFF0284C7 : (hov ? 0xFF334155 : 0xFF1E293B);
            int border = isCur ? 0xFF38BDF8 : (hov ? 0xFF64748B : 0xFF334155);
            graphics.fill(cx, y, cx + chipW, y + 16, bg);
            graphics.renderOutline(cx, y, chipW, 16, border);
            graphics.drawCenteredString(font, t.name(), cx + chipW / 2, y + 4, isCur ? 0xFFFFFFFF : 0xFF94A3B8);
        }
    }

    private boolean handleTierControlsClick(double mouseX, double mouseY, int x, int curY) {
        var node = targetWidget.getNode();
        java.util.List<GTVoltageTier> tiers = getInspectorTiers(node);
        int totalW = PANEL_WIDTH - 16;
        int count = tiers.size();
        int gap = 4;
        int chipW = Math.max(26, (totalW - gap * (count - 1)) / count);

        for (int i = 0; i < count; i++) {
            GTVoltageTier t = tiers.get(i);
            int cx = x + i * (chipW + gap);
            if (mouseX >= cx && mouseX <= cx + chipW && mouseY >= curY && mouseY <= curY + 16) {
                applyTierSelection(node, t);
                targetWidget.invalidateCache();
                screen.markSummaryDirty();
                return true;
            }
        }
        return false;
    }

    private void applyTierSelection(com.gtceu.calcboard.api.model.RecipeNode node, GTVoltageTier tier) {
        if (com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isCombustionFamily(node)) {
            com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.syncCombustionMachine(node, tier);
            return;
        }
        node.setTargetTier(tier);
        if (node.isMultiblock()) {
            return;
        }
        var ws = com.gtceu.calcboard.api.model.NodeWorkstationResolver.getWorkstationForTier(node, tier);
        if (ws != null) {
            node.setMachineIcon(ws);
        }
    }

    private void renderOverclockModeButton(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        var node = targetWidget.getNode();
        OverclockMode mode = node.getOverclockMode();
        int btnW = PANEL_WIDTH - 16;
        boolean hov = mouseX >= x && mouseX <= x + btnW && mouseY >= y && mouseY <= y + 16;

        graphics.fill(x, y, x + btnW, y + 16, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, btnW, 16, hov ? 0xFF38BDF8 : 0xFF334155);

        String modeName = mode.getDisplayName();
        graphics.drawString(font, modeName, x + 6, y + 4, 0xFFE2E8F0, false);
        graphics.drawString(font, "▼", x + btnW - 12, y + 4, 0xFF64748B, false);
    }

    private void renderHardwareSection(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int btnW = PANEL_WIDTH - 16;
        boolean hov = mouseX >= x && mouseX <= x + btnW && mouseY >= y && mouseY <= y + 36;

        graphics.fill(x, y, x + btnW, y + 36, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, btnW, 36, hov ? 0xFF38BDF8 : 0xFF334155);

        graphics.drawString(font, "⚙ " + Component.translatable("gui.gtcalcboard.inspector.machine_config").getString(), x + 6, y + 5, 0xFF38BDF8, false);

        var node = targetWidget.getNode();
        String subText = node.isMultiblock()
                ? Component.translatable("gui.gtcalcboard.inspector.multiblock").getString()
                : Component.translatable("gui.gtcalcboard.inspector.singleblock").getString();
        graphics.drawString(font, subText, x + 6, y + 20, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + btnW - 12, y + 12, 0xFF64748B, false);
    }

    private void renderStatsSummary(GuiGraphics graphics, Font font, int x, int y) {
        int boxW = PANEL_WIDTH - 16;
        graphics.fill(x, y, x + boxW, y + 48, 0xFF0B1120);
        graphics.renderOutline(x, y, boxW, 48, 0xFF1E293B);

        var node = targetWidget.getNode();
        double power = node.getSingleMachineEUt();
        String powerStr = String.format("%,.0f EU/t", power);
        int powerCol = power > 0 ? 0xFF10B981 : (power < 0 ? 0xFFF59E0B : 0xFF94A3B8);

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.single_power").getString(), x + 6, y + 6, 0xFF64748B, false);
        graphics.drawString(font, powerStr, x + boxW - font.width(powerStr) - 6, y + 6, powerCol, false);

        double totalPower = node.getTotalEUt();
        String totalStr = String.format("%,.0f EU/t", totalPower);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.total_power").getString(), x + 6, y + 20, 0xFF64748B, false);
        graphics.drawString(font, totalStr, x + boxW - font.width(totalStr) - 6, y + 20, powerCol, false);

        double duration = node.getEffectiveDurationSeconds();
        String durStr = String.format("%.2f s", duration);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.duration").getString(), x + 6, y + 34, 0xFF64748B, false);
        graphics.drawString(font, durStr, x + boxW - font.width(durStr) - 6, y + 34, 0xFFCBD5E1, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;

        int screenW = screen.width;
        int px = screenW - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;
        int ph = Math.max(160, screen.height - py - 32);

        if (mouseX < px || mouseX > px + PANEL_WIDTH || mouseY < py || mouseY > py + ph) {
            return false;
        }

        if (button == 0) {
            int closeX = px + PANEL_WIDTH - 16;
            int closeY = py + 5;
            if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
                close();
                return true;
            }

            var node = targetWidget.getNode();
            if (node.isReroute()) {
                return handleJunctionInspectorClick(px, py, mouseX, mouseY, node);
            }

            int curY = py + 40;
            int x = px + 8;

            if (mouseX >= x && mouseX <= x + 16 && mouseY >= curY && mouseY <= curY + 16) {
                targetWidget.mouseClicked(targetWidget.getNode().getPosX() + 38, targetWidget.getNode().getPosY() + 26, 0);
                return true;
            }

            int plusX = x + 18 + 56 + 2;
            if (mouseX >= plusX && mouseX <= plusX + 16 && mouseY >= curY && mouseY <= curY + 16) {
                targetWidget.mouseClicked(targetWidget.getNode().getPosX() + 38 + 56 + 18, targetWidget.getNode().getPosY() + 26, 0);
                return true;
            }

            int halfX = plusX + 18;
            if (mouseX >= halfX && mouseX <= halfX + 22 && mouseY >= curY && mouseY <= curY + 16) {
                targetWidget.mouseClicked(targetWidget.getNode().getPosX() + 38 + 56 + 36, targetWidget.getNode().getPosY() + 26, 0);
                return true;
            }

            int doubleX = halfX + 24;
            if (mouseX >= doubleX && mouseX <= doubleX + 22 && mouseY >= curY && mouseY <= curY + 16) {
                targetWidget.mouseClicked(targetWidget.getNode().getPosX() + 38 + 56 + 54, targetWidget.getNode().getPosY() + 26, 0);
                return true;
            }

            int anchorX = doubleX + 24;
            if (mouseX >= anchorX && mouseX <= anchorX + 18 && mouseY >= curY && mouseY <= curY + 16) {
                boolean nowBase = !targetWidget.getNode().isBaseNode();
                screen.getGraph().setBaseNode(nowBase ? targetWidget.getNode() : null);
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                return true;
            }

            curY += 34;
            if (handleTierControlsClick(mouseX, mouseY, x, curY)) {
                return true;
            }

            curY += 32;
            int btnW = PANEL_WIDTH - 16;
            if (mouseX >= x && mouseX <= x + btnW && mouseY >= curY && mouseY <= curY + 16) {
                var curMode = targetWidget.getNode().getOverclockMode();
                var vals = OverclockMode.values();
                var nextMode = vals[(curMode.ordinal() + 1) % vals.length];
                targetWidget.getNode().setOverclockMode(nextMode);
                targetWidget.invalidateCache();
                screen.markSummaryDirty();
                return true;
            }

            curY += 20;
            if (mouseX >= x && mouseX <= x + btnW && mouseY >= curY && mouseY <= curY + 36) {
                if (screen.getMachineConfigDialog() != null) {
                    screen.getMachineConfigDialog().open(targetWidget.getNode());
                    return true;
                }
            }
        }
        return true;
    }

    private void renderJunctionInspector(GuiGraphics graphics, Font font, int px, int py, int ph, RecipeNode node, int mouseX, int mouseY) {
        renderJunctionHeader(graphics, font, px, py, node, mouseX, mouseY);

        int curY = py + 28;
        int x = px + 8;
        int contentW = PANEL_WIDTH - 16;

        renderJunctionSupplySection(graphics, font, x, curY, contentW, node);
        curY += 36;

        renderJunctionBufferSection(graphics, font, x, curY, contentW, node);
        curY += 36;

        renderJunctionBatchSection(graphics, font, x, curY, contentW, node);
        curY += 40;

        renderJunctionConfigButton(graphics, font, x, curY, contentW, mouseX, mouseY);
        curY += 44;

        renderJunctionFlowStats(graphics, font, x, curY, contentW, node);
    }

    private void renderJunctionHeader(GuiGraphics graphics, Font font, int px, int py, RecipeNode node, int mouseX, int mouseY) {
        graphics.fill(px, py, px + PANEL_WIDTH, py + 22, 0xFF1E293B);
        graphics.renderOutline(px, py, PANEL_WIDTH, 22, 0xFF475569);

        int titleX = px + 6;
        var boundStack = node.getRerouteIngredient();
        if (boundStack != null) {
            IngredientRenderer.render(graphics, boundStack, px + 4, py + 3);
            titleX = px + 24;
        }
        String title = boundStack != null ? boundStack.getDisplayName() : Component.translatable("gui.gtcalcboard.inspector.junction_title").getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, PANEL_WIDTH - 44), titleX, py + 7, 0xFFE2E8F0, false);

        int closeX = px + PANEL_WIDTH - 16;
        int closeY = py + 5;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);
    }

    private void renderJunctionSupplySection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.supply_mode").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 20, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 20, 0xFF334155);

        var mode = node.getSupplyMode();
        String label = Component.translatable(mode.getTranslationKey()).getString();
        var boundStack = node.getRerouteIngredient();
        int col = switch (mode) {
            case INFINITE -> 0xFF38BDF8;
            case FIXED_RATE -> {
                label += " (+" + FormatUtil.formatRate(node.getExternalSupplyRate(), boundStack) + ")";
                yield 0xFFFBBF24;
            }
            case FIXED_DRAIN -> {
                label += " (-" + FormatUtil.formatRate(node.getExternalDrainRate(), boundStack) + ")";
                yield 0xFFF97316;
            }
            case VOID_SINK -> 0xFFF87171;
            default -> 0xFFCBD5E1;
        };
        graphics.drawString(font, font.plainSubstrByWidth(label, w - 8), x + 5, boxY + 6, col, false);
    }

    private void renderJunctionBufferSection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.buffer_mode").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 20, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 20, 0xFF334155);

        boolean isBuffer = node.isJunctionBuffer();
        var boundStack = node.getRerouteIngredient();
        String label = isBuffer
                ? Component.translatable("gui.gtcalcboard.junction.mode_buffer").getString() + " (" + FormatUtil.formatBatchAmount(node.getJunctionBufferSize(), boundStack != null && boundStack.isFluid()) + ")"
                : Component.translatable("gui.gtcalcboard.junction.mode_passthrough").getString();
        int col = isBuffer ? 0xFF38BDF8 : 0xFF94A3B8;
        graphics.drawString(font, font.plainSubstrByWidth(label, w - 8), x + 5, boxY + 6, col, false);
    }

    private void renderJunctionBatchSection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.target_batch").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 24, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 24, 0xFF334155);

        double targetAmount = node.getTargetBatchAmount();
        var boundStack = node.getRerouteIngredient();
        boolean isFluid = boundStack != null && boundStack.isFluid();

        if (targetAmount <= 0.0) {
            String noGoal = Component.translatable("gui.gtcalcboard.inspector.no_target_batch").getString();
            graphics.drawString(font, noGoal, x + 6, boxY + 8, 0xFF64748B, false);
            return;
        }

        String amountStr = FormatUtil.formatBatchAmount(targetAmount, isFluid);
        graphics.drawString(font, amountStr, x + 6, boxY + 8, 0xFFFCD34D, false);

        var graph = screen.getGraph();
        boolean isInput = NodeCardRenderer.isInputSourceJunction(graph, node);
        String timeBadge = isInput ? computeJunctionDepletionBadge(graph, node, targetAmount) : computeJunctionEtaBadge(graph, node, targetAmount);
        int timeCol = isInput ? 0xFF7DD3FC : 0xFF86EFAC;
        int badgeW = font.width(timeBadge);
        graphics.drawString(font, timeBadge, x + w - badgeW - 6, boxY + 8, timeCol, false);
    }

    private String computeJunctionDepletionBadge(com.gtceu.calcboard.api.model.FlowGraph graph, RecipeNode node, double targetAmount) {
        double drainRate = ProductionETACalculator.calculateNetOutflowRate(graph, node);
        double dtSec = ProductionETACalculator.calculateDepletionTime(graph, node, targetAmount, drainRate);
        return "DT: " + FormatUtil.formatETA(dtSec);
    }

    private String computeJunctionEtaBadge(com.gtceu.calcboard.api.model.FlowGraph graph, RecipeNode node, double targetAmount) {
        double netRate = ProductionETACalculator.calculateNetInflowRate(graph, node, 0);
        double etaSec = ProductionETACalculator.calculateETA(graph, node, targetAmount, netRate);
        return "ET: " + FormatUtil.formatETA(etaSec);
    }

    private void renderJunctionConfigButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 36;
        graphics.fill(x, y, x + w, y + 36, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 36, hov ? 0xFF38BDF8 : 0xFF334155);

        String btnTitle = "⚙ " + Component.translatable("gui.gtcalcboard.inspector.configure_junction_btn").getString();
        graphics.drawString(font, font.plainSubstrByWidth(btnTitle, w - 20), x + 6, y + 6, 0xFF38BDF8, false);

        String sub = Component.translatable("gui.gtcalcboard.inspector.configure_junction_sub").getString();
        graphics.drawString(font, font.plainSubstrByWidth(sub, w - 20), x + 6, y + 20, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + w - 12, y + 13, hov ? 0xFF38BDF8 : 0xFF64748B, false);
    }

    private void renderJunctionFlowStats(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.fill(x, y, x + w, y + 48, 0xFF0B1120);
        graphics.renderOutline(x, y, w, 48, 0xFF1E293B);

        var graph = screen.getGraph();
        var boundStack = node.getRerouteIngredient();
        double inRate = ProductionETACalculator.calculateNetInflowRate(graph, node, 0);
        double outRate = ProductionETACalculator.calculateNetOutflowRate(graph, node);
        double netRate = inRate - outRate;

        String inLabel = Component.translatable("gui.gtcalcboard.inspector.inflow_rate").getString();
        String inVal = "+" + FormatUtil.formatRate(inRate, boundStack);
        graphics.drawString(font, inLabel, x + 6, y + 6, 0xFF64748B, false);
        graphics.drawString(font, inVal, x + w - font.width(inVal) - 6, y + 6, 0xFF10B981, false);

        String outLabel = Component.translatable("gui.gtcalcboard.inspector.outflow_rate").getString();
        String outVal = "-" + FormatUtil.formatRate(outRate, boundStack);
        graphics.drawString(font, outLabel, x + 6, y + 20, 0xFF64748B, false);
        graphics.drawString(font, outVal, x + w - font.width(outVal) - 6, y + 20, 0xFFF59E0B, false);

        String netLabel = Component.translatable("gui.gtcalcboard.inspector.net_rate").getString();
        String netVal = (netRate >= 0 ? "+" : "") + FormatUtil.formatRate(netRate, boundStack);
        int netCol = Math.abs(netRate) < 1e-4 ? 0xFF94A3B8 : (netRate > 0 ? 0xFF38BDF8 : 0xFFEF4444);
        graphics.drawString(font, netLabel, x + 6, y + 34, 0xFF64748B, false);
        graphics.drawString(font, netVal, x + w - font.width(netVal) - 6, y + 34, netCol, false);
    }

    private boolean handleJunctionInspectorClick(int px, int py, double mouseX, double mouseY, RecipeNode node) {
        int contentW = PANEL_WIDTH - 16;
        int x = px + 8;

        int batchY = py + 28 + 36 + 36;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= batchY + 12 && mouseY <= batchY + 36) {
            targetWidget.getTargetBatchEditor().startEditing();
            return true;
        }

        int btnY = batchY + 40;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= btnY && mouseY <= btnY + 36) {
            screen.openJunctionSupplyDialog(node);
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }

        return true;
    }
}
