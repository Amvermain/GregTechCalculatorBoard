package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.BoundaryPinNode;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.ProductionETACalculator;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NodeInspectorPanel {

    private final IBoardScreenContext screen;
    private NodeWidget targetWidget = null;
    private boolean visible = false;
    public static final int PANEL_WIDTH = 195;

    public NodeInspectorPanel(IBoardScreenContext screen) {
        this.screen = screen;
    }

    public boolean isVisible() {
        return visible && targetWidget != null && screen.getGraph().findNodeById(targetWidget.getNode().getId()) != null;
    }

    public void setTargetWidget(NodeWidget widget) {
        boolean wasVisible = this.visible;
        this.targetWidget = widget;
        this.visible = (widget != null);
        if (this.visible && !wasVisible) {
            screen.onNodeInspectorOpened();
        } else if (!this.visible && wasVisible) {
            screen.onNodeInspectorClosed();
        }
    }

    public void close() {
        if (this.visible) {
            this.visible = false;
            this.targetWidget = null;
            screen.onNodeInspectorClosed();
        }
    }

    public NodeWidget getTargetWidget() {
        return targetWidget;
    }

    public int getPanelWidth() {
        return isVisible() ? PANEL_WIDTH : 0;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!isVisible()) return false;
        int px = screen.getScreenWidth() - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;
        int ph = getPanelHeight();
        return mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= py && mouseY <= py + ph;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOver(mouseX, mouseY);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        Font font = Minecraft.getInstance().font;
        int screenW = screen.getScreenWidth();

        int px = screenW - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;
        int ph = getPanelHeight();

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
        if (node.isBoundaryPin() && node instanceof BoundaryPinNode pin) {
            renderBoundaryPinInspector(graphics, font, px, py, ph, pin, mouseX, mouseY);
            graphics.pose().popPose();
            return;
        }

        int titleX = px + 6;

        if (node.getMachineIcon() != null) {
            var item = ForgeRegistries.ITEMS.getValue(node.getMachineIcon());
            if ((item == null || item == Items.AIR) && ForgeRegistries.BLOCKS != null) {
                var block = ForgeRegistries.BLOCKS.getValue(node.getMachineIcon());
                if (block != null && block.asItem() != Items.AIR) {
                    item = block.asItem();
                }
            }
            if (item != null && item != Items.AIR) {
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

        if (supportsVoltageTier(node)) {
            String tierLabel = Component.translatable("gui.gtcalcboard.inspector.tier").getString();
            graphics.drawString(font, tierLabel, px + 8, curY, 0xFF94A3B8, false);
            curY += 12;
            renderTierControls(graphics, font, px + 8, curY, mouseX, mouseY);
            curY += getTierControlsHeight(node) + 6;
        }

        if (supportsOverclockMode(node)) {
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

    public int getPanelHeight() {
        int screenH = screen.getScreenHeight();
        int py = screen.getToolbarY() + 22;
        int minH = Math.max(160, screenH - py - 32);
        if (targetWidget == null || targetWidget.getNode() == null) {
            return minH;
        }
        RecipeNode node = targetWidget.getNode();
        if (node.isReroute() || node.isBoundaryPin()) {
            return minH;
        }
        int contentH = calculateContentHeight(node);
        return Math.max(minH, contentH);
    }

    private int calculateContentHeight(RecipeNode node) {
        int h = 28;
        h += 12 + 22;
        if (supportsVoltageTier(node)) {
            h += 12 + getTierControlsHeight(node) + 6;
        }
        if (supportsOverclockMode(node)) {
            h += 12 + 20;
        }
        h += 44;
        h += 48 + 8;
        return h;
    }

    public int getTierControlsHeight(RecipeNode node) {
        if (node == null || node.getTargetTier() == null) {
            return 0;
        }
        List<GTVoltageTier> tiers = getInspectorTiers(node);
        if (tiers.isEmpty()) {
            return 0;
        }
        int cols = 4;
        int numRows = (tiers.size() + cols - 1) / cols;
        int chipH = 16;
        int rowGap = 4;
        return numRows * chipH + (numRows - 1) * rowGap;
    }

    public List<GTVoltageTier> getInspectorTiers(RecipeNode node) {
        if (node == null) {
            return Collections.emptyList();
        }
        if (GTCombustionHelper.isCombustionFamily(node)) {
            return GTCombustionHelper.getAvailableCombustionTiers();
        }
        if (node.isTurbine()) {
            return getTurbineInspectorTiers(node);
        }
        return getStandardInspectorTiers(node);
    }

    private List<GTVoltageTier> getTurbineInspectorTiers(RecipeNode node) {
        if (!node.isMultiblock()) {
            return List.of(GTVoltageTier.LV, GTVoltageTier.MV, GTVoltageTier.HV);
        }
        GTVoltageTier baseTier = GTTurbineHelper.getTurbineBaseTier(node);
        int minIdx = baseTier != null ? baseTier.ordinal() : GTVoltageTier.EV.ordinal();
        int maxIdx = GTVoltageTier.MAX.ordinal();
        if (node.getTargetTier() != null) {
            minIdx = Math.min(minIdx, node.getTargetTier().ordinal());
            maxIdx = Math.max(maxIdx, node.getTargetTier().ordinal());
        }
        List<GTVoltageTier> list = new ArrayList<>();
        for (int i = minIdx; i <= maxIdx; i++) {
            list.add(GTVoltageTier.getByIndex(i));
        }
        return list;
    }

    private List<GTVoltageTier> getStandardInspectorTiers(RecipeNode node) {
        int minIdx = node.getRecipeTier() != null ? node.getRecipeTier().ordinal() : GTVoltageTier.LV.ordinal();
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (node.isMultiblock()) {
            minIdx = Math.max(minIdx, GTVoltageTier.LV.ordinal());
            if (adapter != null && adapter.isFusion(node)) {
                var minFusion = adapter.getMinFusionVoltageTier(node);
                if (minFusion != null) {
                    minIdx = Math.max(minIdx, minFusion.ordinal());
                }
            }
        }
        boolean isVanillaCooking = node.getRecipeCategoryId() != null && com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId());
        if (adapter != null && !isVanillaCooking) {
            GTVoltageTier minWsTier = adapter.getMinimumWorkstationTier(node);
            if (minWsTier != null) {
                minIdx = Math.max(minIdx, minWsTier.ordinal());
            }
        }
        int maxIdx = GTVoltageTier.MAX.ordinal();
        if (node.getTargetTier() != null) {
            minIdx = Math.min(minIdx, node.getTargetTier().ordinal());
            maxIdx = Math.max(maxIdx, node.getTargetTier().ordinal());
        }
        List<GTVoltageTier> list = new ArrayList<>();
        for (int i = minIdx; i <= maxIdx; i++) {
            list.add(GTVoltageTier.getByIndex(i));
        }
        return list;
    }

    private void renderTierControls(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        var node = targetWidget.getNode();
        GTVoltageTier currentTier = node.getTargetTier();
        List<GTVoltageTier> tiers = getInspectorTiers(node);
        boolean isEnergyHatchLocked = com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.hasEnergyHatch(node);
        int totalW = PANEL_WIDTH - 16;
        int cols = 4;
        int gap = 4;
        int rowGap = 4;
        int chipW = (totalW - gap * (cols - 1)) / cols;
        int chipH = 16;
        int count = tiers.size();

        for (int i = 0; i < count; i++) {
            GTVoltageTier t = tiers.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (chipW + gap);
            int cy = y + row * (chipH + rowGap);
            boolean isCur = (t == currentTier);
            boolean hov = mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH;

            int bg;
            int border;
            int textColor;
            if (isEnergyHatchLocked) {
                if (isCur) {
                    bg = 0xFF1E3A5F;
                    border = 0xFF2563EB;
                    textColor = 0xFF93C5FD;
                } else {
                    bg = 0xFF0F172A;
                    border = 0xFF1E293B;
                    textColor = 0xFF475569;
                }
            } else {
                bg = isCur ? 0xFF0284C7 : (hov ? 0xFF334155 : 0xFF1E293B);
                border = isCur ? 0xFF38BDF8 : (hov ? 0xFF64748B : 0xFF334155);
                textColor = isCur ? 0xFFFFFFFF : 0xFF94A3B8;
            }

            graphics.fill(cx, cy, cx + chipW, cy + chipH, bg);
            graphics.renderOutline(cx, cy, chipW, chipH, border);
            graphics.drawCenteredString(font, t.name(), cx + chipW / 2, cy + 4, textColor);

            if (hov && isEnergyHatchLocked) {
                String hatchTierName = currentTier != null ? currentTier.getName() : "Unknown";
                graphics.renderTooltip(font, Component.translatable("gui.gtcalcboard.inspector.tier_locked_by_energy_hatch", hatchTierName), mouseX, mouseY);
            }
        }
    }

    private boolean handleTierControlsClick(double mouseX, double mouseY, int x, int curY) {
        var node = targetWidget.getNode();
        if (com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.hasEnergyHatch(node)) {
            return false;
        }
        List<GTVoltageTier> tiers = getInspectorTiers(node);
        int totalW = PANEL_WIDTH - 16;
        int cols = 4;
        int gap = 4;
        int rowGap = 4;
        int chipW = (totalW - gap * (cols - 1)) / cols;
        int chipH = 16;
        int count = tiers.size();

        for (int i = 0; i < count; i++) {
            GTVoltageTier t = tiers.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (chipW + gap);
            int cy = curY + row * (chipH + rowGap);
            if (mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH) {
                applyTierSelection(node, t);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                targetWidget.invalidateCache();
                screen.markSummaryDirty();
                return true;
            }
        }
        return false;
    }

    private void applyTierSelection(RecipeNode node, GTVoltageTier tier) {
        if (com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.hasEnergyHatch(node)) {
            return;
        }
        if (GTCombustionHelper.isCombustionFamily(node)) {
            GTVoltageTier oldTier = node.getTargetTier();
            boolean ok = GTCombustionHelper.syncCombustionMachine(node, tier);
            if (ok && screen != null) {
                screen.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, tier));
                syncSharedFrame(node);
            }
            return;
        }
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            node.setSteamMode(SteamMode.NONE);
        }
        GTVoltageTier oldTier = node.getTargetTier();
        node.setTargetTier(tier);
        if (node.isLargeTurbine()) {
            GTTurbineHelper.setRotorHolderTier(node, tier);
        }
        if (!node.isMultiblock()) {
            var ws = com.gtceu.calcboard.api.model.NodeWorkstationResolver.getWorkstationForTier(node, tier);
            if (ws != null) {
                node.setMachineIcon(ws);
            }
        }
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(node);
        if (screen != null) {
            screen.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, tier));
            syncSharedFrame(node);
        }
    }

    private void syncSharedFrame(RecipeNode node) {
        if (screen != null && screen.getGraph() != null) {
            var frame = screen.getGraph().findFrameEnclosingNode(node);
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, screen.getGraph());
                screen.rebuildBoardWidgets();
            }
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
        String powerStr = formatPowerValue(node, power);
        int powerCol = power > 0 ? 0xFF10B981 : (power < 0 ? 0xFFF59E0B : 0xFF94A3B8);

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.single_power").getString(), x + 6, y + 6, 0xFF64748B, false);
        graphics.drawString(font, powerStr, x + boxW - font.width(powerStr) - 6, y + 6, powerCol, false);

        double totalPower = node.getTotalEUt();
        String totalStr = formatPowerValue(node, totalPower);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.total_power").getString(), x + 6, y + 20, 0xFF64748B, false);
        graphics.drawString(font, totalStr, x + boxW - font.width(totalStr) - 6, y + 20, powerCol, false);

        double duration = node.getEffectiveDurationSeconds();
        String durStr = String.format(Locale.ROOT, "%.2f s", duration);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.duration").getString(), x + 6, y + 34, 0xFF64748B, false);
        graphics.drawString(font, durStr, x + boxW - font.width(durStr) - 6, y + 34, 0xFFCBD5E1, false);
    }

    private String formatPowerValue(RecipeNode node, double power) {
        EnergyType type = node.getEnergyType();
        if (type == EnergyType.NONE) {
            return Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        }
        String unit = switch (type) {
            case KINETIC_SU -> "SU";
            case ELECTRIC_FE -> "FE/t";
            default -> "EU/t";
        };
        if (power > 0 || node.isGenerator()) {
            return String.format(Locale.ROOT, "+%,.0f %s", Math.abs(power), unit);
        }
        return String.format(Locale.ROOT, "%,.0f %s", power, unit);
    }

    private boolean supportsVoltageTier(RecipeNode node) {
        if (node == null || node.getTargetTier() == null) return false;
        if (Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE))) return true;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return false;
        return node.getEnergyType() == EnergyType.ELECTRIC_EU;
    }

    private boolean supportsOverclockMode(RecipeNode node) {
        if (node == null || node.getOverclockMode() == null) return false;
        if (node.isGenerator() || node.getEnergyType() == EnergyType.NONE) return false;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return false;
        return node.getEnergyType() == EnergyType.ELECTRIC_EU;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int screenW = screen.getScreenWidth();
        int px = screenW - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;

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
            if (node.isBoundaryPin() && node instanceof BoundaryPinNode pin) {
                return handleBoundaryPinInspectorClick(px, py, mouseX, mouseY, pin);
            }

            int curY = py + 28;
            curY += 12;
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
                screen.rebuildBoardWidgets();
                screen.markSummaryDirty();
                return true;
            }

            curY += 22;

            if (supportsVoltageTier(node)) {
                curY += 12;
                if (handleTierControlsClick(mouseX, mouseY, x, curY)) {
                    return true;
                }
                curY += getTierControlsHeight(node) + 6;
            }

            int btnW = PANEL_WIDTH - 16;
            if (supportsOverclockMode(node)) {
                curY += 12;
                if (mouseX >= x && mouseX <= x + btnW && mouseY >= curY && mouseY <= curY + 16) {
                    var curMode = targetWidget.getNode().getOverclockMode();
                    var vals = OverclockMode.values();
                    var nextMode = vals[(curMode.ordinal() + 1) % vals.length];
                    targetWidget.getNode().setOverclockMode(nextMode);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    targetWidget.invalidateCache();
                    syncSharedFrame(targetWidget.getNode());
                    screen.markSummaryDirty();
                    return true;
                }
                curY += 20;
            }

            if (mouseX >= x && mouseX <= x + btnW && mouseY >= curY && mouseY <= curY + 36) {
                targetWidget.commitCountEdit();
                screen.openMachineConfigDialog(targetWidget.getNode());
                return true;
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

    private void renderBoundaryPinInspector(GuiGraphics graphics, Font font, int px, int py, int ph, BoundaryPinNode pin, int mouseX, int mouseY) {
        renderBoundaryPinHeader(graphics, font, px, py, pin, mouseX, mouseY);

        int curY = py + 28;
        int x = px + 8;
        int contentW = PANEL_WIDTH - 16;

        renderBoundaryPinDirectionSection(graphics, font, x, curY, contentW, pin);
        curY += 36;

        renderBoundaryPinFlipButton(graphics, font, x, curY, contentW, mouseX, mouseY);
        curY += 40;

        renderBoundaryPinIngredientSection(graphics, font, x, curY, contentW, pin);
        curY += 40;

        renderBoundaryPinFlowSection(graphics, font, x, curY, contentW, pin);
        curY += 56;

        renderBoundaryPinRenameButton(graphics, font, x, curY, contentW, mouseX, mouseY);
    }

    private void renderBoundaryPinHeader(GuiGraphics graphics, Font font, int px, int py, BoundaryPinNode pin, int mouseX, int mouseY) {
        graphics.fill(px, py, px + PANEL_WIDTH, py + 22, 0xFF1E293B);
        graphics.renderOutline(px, py, PANEL_WIDTH, 22, 0xFF475569);

        int titleX = px + 6;
        IngredientStack bound = pin.getBoundIngredient();
        if (bound != null) {
            IngredientRenderer.render(graphics, bound, px + 4, py + 3);
            titleX = px + 24;
        }
        String title = pin.getPinLabel().isEmpty() ? pin.getName() : pin.getPinLabel();
        graphics.drawString(font, font.plainSubstrByWidth(title, PANEL_WIDTH - 44), titleX, py + 7, 0xFFE2E8F0, false);

        int closeX = px + PANEL_WIDTH - 16;
        int closeY = py + 5;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);
    }

    private void renderBoundaryPinDirectionSection(GuiGraphics graphics, Font font, int x, int y, int w, BoundaryPinNode pin) {
        boolean isInput = pin.getDirection() == BoundaryPinNode.PinDirection.INPUT;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.boundary_pin.label").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 20, isInput ? 0xFF042F2E : 0xFF331B05);
        graphics.renderOutline(x, boxY, w, 20, isInput ? 0xFF0D9488 : 0xFFD97706);

        String badge = isInput ? "» IN" : "« OUT";
        String dirText = Component.translatable(isInput ? "gui.gtcalcboard.boundary_pin.input" : "gui.gtcalcboard.boundary_pin.output").getString();
        graphics.drawString(font, badge + " - " + dirText, x + 6, boxY + 6, isInput ? 0xFF5EEAD4 : 0xFFFCD34D, false);
    }

    private void renderBoundaryPinFlipButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 32;
        graphics.fill(x, y, x + w, y + 32, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 32, hov ? 0xFF38BDF8 : 0xFF334155);

        String title = "⇄ " + Component.translatable("gui.gtcalcboard.menu.flip_node").getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, w - 20), x + 6, y + 6, 0xFF38BDF8, false);
        String sub = Component.translatable("gui.gtcalcboard.boundary_pin.toggle_side").getString();
        graphics.drawString(font, font.plainSubstrByWidth(sub, w - 20), x + 6, y + 18, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + w - 12, y + 11, hov ? 0xFF38BDF8 : 0xFF64748B, false);
    }

    private void renderBoundaryPinIngredientSection(GuiGraphics graphics, Font font, int x, int y, int w, BoundaryPinNode pin) {
        IngredientStack bound = pin.getBoundIngredient();
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.boundary_pin.bound_ingredient").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 24, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 24, 0xFF334155);

        if (bound != null) {
            IngredientRenderer.render(graphics, bound, x + 4, boxY + 4);
            graphics.drawString(font, font.plainSubstrByWidth(bound.getDisplayName(), w - 26), x + 24, boxY + 8, 0xFFE2E8F0, false);
        } else {
            String unbound = Component.translatable("gui.gtcalcboard.junction.no_bound_ingredient").getString();
            graphics.drawString(font, unbound, x + 6, boxY + 8, 0xFF64748B, false);
        }
    }

    private void renderBoundaryPinFlowSection(GuiGraphics graphics, Font font, int x, int y, int w, BoundaryPinNode pin) {
        graphics.fill(x, y, x + w, y + 48, 0xFF0B1120);
        graphics.renderOutline(x, y, w, 48, 0xFF1E293B);

        boolean isInput = pin.getDirection() == BoundaryPinNode.PinDirection.INPUT;
        IngredientStack bound = pin.getBoundIngredient();
        var graph = screen.getGraph();
        var stats = graph != null
                ? (isInput ? graph.getOutputPortStats(pin, 0) : graph.getInputPortStats(pin, 0))
                : null;

        double ratedAmount = bound != null ? bound.getAmount() : 0.0;
        double connectedAmount = (stats != null && stats.isConnected()) ? stats.connectedRate() : 0.0;

        String ratedLabel = isInput ? Component.translatable("gui.gtcalcboard.tooltip.supply").getString() : Component.translatable("gui.gtcalcboard.tooltip.demand").getString();
        String ratedVal = (isInput ? "+" : "-") + FormatUtil.formatRate(ratedAmount, bound);
        graphics.drawString(font, ratedLabel, x + 6, y + 6, 0xFF64748B, false);
        graphics.drawString(font, ratedVal, x + w - font.width(ratedVal) - 6, y + 6, isInput ? 0xFF10B981 : 0xFFF59E0B, false);

        String flowLabel = isInput ? Component.translatable("gui.gtcalcboard.inspector.outflow_rate").getString() : Component.translatable("gui.gtcalcboard.inspector.inflow_rate").getString();
        String flowVal = FormatUtil.formatRate(connectedAmount, bound);
        int flowCol = (stats != null && stats.isConnected() && stats.isBalanced()) ? 0xFF10B981 : 0xFF38BDF8;
        graphics.drawString(font, flowLabel, x + 6, y + 20, 0xFF64748B, false);
        graphics.drawString(font, flowVal, x + w - font.width(flowVal) - 6, y + 20, flowCol, false);

        String statLabel = Component.translatable("gui.gtcalcboard.boundary_pin.status").getString();
        String statVal = (stats != null && stats.isConnected())
                ? (stats.isBalanced() ? Component.translatable("gui.gtcalcboard.boundary_pin.status_balanced").getString() : String.format(Locale.ROOT, "Flow: %.1f%%", stats.getPercent()))
                : Component.translatable("gui.gtcalcboard.boundary_pin.status_unconnected").getString();
        int statCol = (stats != null && stats.isConnected()) ? (stats.isBalanced() ? 0xFF10B981 : 0xFFF59E0B) : 0xFF64748B;
        graphics.drawString(font, statLabel, x + 6, y + 34, 0xFF64748B, false);
        graphics.drawString(font, statVal, x + w - font.width(statVal) - 6, y + 34, statCol, false);
    }

    private void renderBoundaryPinRenameButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 32;
        graphics.fill(x, y, x + w, y + 32, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 32, hov ? 0xFF38BDF8 : 0xFF334155);

        String title = "✎ " + Component.translatable("gui.gtcalcboard.menu.rename_pin").getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, w - 20), x + 6, y + 6, 0xFF38BDF8, false);
        String sub = Component.translatable("gui.gtcalcboard.boundary_pin.rename_hint").getString();
        graphics.drawString(font, font.plainSubstrByWidth(sub, w - 20), x + 6, y + 18, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + w - 12, y + 11, hov ? 0xFF38BDF8 : 0xFF64748B, false);
    }

    private boolean handleBoundaryPinInspectorClick(int px, int py, double mouseX, double mouseY, BoundaryPinNode pin) {
        int contentW = PANEL_WIDTH - 16;
        int x = px + 8;

        int flipY = py + 28 + 36;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= flipY && mouseY <= flipY + 32) {
            boolean oldFlipped = pin.isFlipped();
            boolean newFlipped = !oldFlipped;
            pin.setFlipped(newFlipped);
            if (screen != null) {
                screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.FlipNodesCommand(pin, oldFlipped, newFlipped));
                if (screen.getGraph() != null) {
                    screen.getGraph().cleanupInvalidConnections();
                }
                screen.markSummaryDirty();
            }
            targetWidget.invalidateCache();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        int renameY = flipY + 40 + 40 + 56;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= renameY && mouseY <= renameY + 32) {
            targetWidget.getNameEditor().startEditing();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return true;
    }
}
