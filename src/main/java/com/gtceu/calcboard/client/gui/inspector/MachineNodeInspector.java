package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.NodeWorkstationResolver;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeInspectorPanel;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
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

public class MachineNodeInspector implements INodeSubInspector {

    private final IBoardScreenContext screen;
    private NodeWidget targetWidget;
    private Component pendingTooltip;

    public MachineNodeInspector(IBoardScreenContext screen) {
        this.screen = screen;
    }

    @Override
    public void bind(NodeWidget targetWidget) {
        this.targetWidget = targetWidget;
    }

    @Override
    public int getContentHeight() {
        if (targetWidget == null || targetWidget.getNode() == null) {
            return 0;
        }
        return calculateContentHeight(targetWidget.getNode());
    }

    @Override
    public Component getPendingTooltip() {
        return pendingTooltip;
    }

    public int calculateContentHeight(RecipeNode node) {
        if (node == null) return 0;
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

    public boolean supportsVoltageTier(RecipeNode node) {
        if (node == null || node.getTargetTier() == null) return false;
        if (Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE))) return true;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return false;
        return node.getEnergyType() == EnergyType.ELECTRIC_EU;
    }

    public boolean supportsOverclockMode(RecipeNode node) {
        if (node == null || node.getOverclockMode() == null) return false;
        if (node.isGenerator() || node.getEnergyType() == EnergyType.NONE) return false;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return false;
        return node.getEnergyType() == EnergyType.ELECTRIC_EU;
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
        boolean isVanillaCooking = node.getRecipeCategoryId() != null && GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId());
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

    @Override
    public void render(GuiGraphics graphics, Font font, int px, int py, int ph, int mouseX, int mouseY) {
        this.pendingTooltip = null;
        if (targetWidget == null || targetWidget.getNode() == null) {
            return;
        }
        RecipeNode node = targetWidget.getNode();
        renderHeader(graphics, font, px, py, node, mouseX, mouseY);

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
    }

    private void renderHeader(GuiGraphics graphics, Font font, int px, int py, RecipeNode node, int mouseX, int mouseY) {
        graphics.fill(px, py, px + NodeInspectorPanel.PANEL_WIDTH, py + 22, 0xFF1E293B);
        graphics.renderOutline(px, py, NodeInspectorPanel.PANEL_WIDTH, 22, 0xFF475569);

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

        String nodeName = font.plainSubstrByWidth(node.getName(), NodeInspectorPanel.PANEL_WIDTH - 44);
        graphics.drawString(font, nodeName, titleX, py + 7, 0xFFE2E8F0, false);

        int closeX = px + NodeInspectorPanel.PANEL_WIDTH - 16;
        int closeY = py + 5;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);
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

    private void renderTierControls(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        var node = targetWidget.getNode();
        GTVoltageTier currentTier = node.getTargetTier();
        List<GTVoltageTier> tiers = getInspectorTiers(node);
        boolean isEnergyHatchLocked = GTAddonCompatibilityHandler.hasEnergyHatch(node);
        int totalW = NodeInspectorPanel.PANEL_WIDTH - 16;
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
                this.pendingTooltip = Component.translatable("gui.gtcalcboard.inspector.tier_locked_by_energy_hatch", hatchTierName);
            }
        }
    }

    private void renderOverclockModeButton(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        var node = targetWidget.getNode();
        OverclockMode mode = node.getOverclockMode();
        int btnW = NodeInspectorPanel.PANEL_WIDTH - 16;
        boolean hov = mouseX >= x && mouseX <= x + btnW && mouseY >= y && mouseY <= y + 16;

        graphics.fill(x, y, x + btnW, y + 16, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, btnW, 16, hov ? 0xFF38BDF8 : 0xFF334155);

        String modeName = mode.getDisplayName();
        graphics.drawString(font, modeName, x + 6, y + 4, 0xFFE2E8F0, false);
        graphics.drawString(font, "▼", x + btnW - 12, y + 4, 0xFF64748B, false);
    }

    private void renderHardwareSection(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int btnW = NodeInspectorPanel.PANEL_WIDTH - 16;
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
        int boxW = NodeInspectorPanel.PANEL_WIDTH - 16;
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

    @Override
    public boolean mouseClicked(int px, int py, double mouseX, double mouseY, int button) {
        if (button != 0 || targetWidget == null || targetWidget.getNode() == null) {
            return false;
        }
        RecipeNode node = targetWidget.getNode();
        int curY = py + 28 + 12;
        int x = px + 8;

        if (handleCountControlsClick(mouseX, mouseY, x, curY)) {
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

        int btnW = NodeInspectorPanel.PANEL_WIDTH - 16;
        if (supportsOverclockMode(node)) {
            curY += 12;
            if (handleOverclockModeClick(mouseX, mouseY, x, curY, btnW)) {
                return true;
            }
            curY += 20;
        }

        handleHardwareSectionClick(mouseX, mouseY, x, curY, btnW);
        return true;
    }

    public NodeWidget getTargetWidget() {
        return targetWidget;
    }

    private boolean handleCountControlsClick(double mouseX, double mouseY, int x, int curY) {
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
            if (screen != null && screen.getGraph() != null) {
                screen.getGraph().setBaseNode(nowBase ? targetWidget.getNode() : null);
                screen.rebuildBoardWidgets();
                screen.markSummaryDirty();
            }
            return true;
        }
        return false;
    }

    private boolean handleTierControlsClick(double mouseX, double mouseY, int x, int curY) {
        var node = targetWidget.getNode();
        if (GTAddonCompatibilityHandler.hasEnergyHatch(node)) {
            return false;
        }
        List<GTVoltageTier> tiers = getInspectorTiers(node);
        int totalW = NodeInspectorPanel.PANEL_WIDTH - 16;
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
                if (screen != null) {
                    screen.markSummaryDirty();
                }
                return true;
            }
        }
        return false;
    }

    private void applyTierSelection(RecipeNode node, GTVoltageTier tier) {
        if (GTAddonCompatibilityHandler.hasEnergyHatch(node)) {
            return;
        }
        if (GTCombustionHelper.isCombustionFamily(node)) {
            GTVoltageTier oldTier = node.getTargetTier();
            boolean ok = GTCombustionHelper.syncCombustionMachine(node, tier);
            if (ok && screen != null) {
                screen.recordCommand(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, tier));
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
            var ws = NodeWorkstationResolver.getWorkstationForTier(node, tier);
            if (ws != null) {
                node.setMachineIcon(ws);
            }
        }
        GTCEuModAdapter.syncTurbineMachineIcon(node);
        if (screen != null) {
            screen.recordCommand(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, tier));
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

    private boolean handleOverclockModeClick(double mouseX, double mouseY, int x, int curY, int btnW) {
        if (mouseX >= x && mouseX <= x + btnW && mouseY >= curY && mouseY <= curY + 16) {
            var curMode = targetWidget.getNode().getOverclockMode();
            var vals = OverclockMode.values();
            var nextMode = vals[(curMode.ordinal() + 1) % vals.length];
            targetWidget.getNode().setOverclockMode(nextMode);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            targetWidget.invalidateCache();
            syncSharedFrame(targetWidget.getNode());
            if (screen != null) {
                screen.markSummaryDirty();
            }
            return true;
        }
        return false;
    }

    private boolean handleHardwareSectionClick(double mouseX, double mouseY, int x, int curY, int btnW) {
        if (mouseX >= x && mouseX <= x + btnW && mouseY >= curY && mouseY <= curY + 36) {
            targetWidget.commitCountEdit();
            if (screen != null) {
                screen.openMachineConfigDialog(targetWidget.getNode());
            }
            return true;
        }
        return false;
    }
}
