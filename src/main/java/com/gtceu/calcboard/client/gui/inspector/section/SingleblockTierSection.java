package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.NodeWorkstationResolver;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeInspectorPanel;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SingleblockTierSection implements IInspectorSection {

    private NodeWidget widget;
    private RecipeNode node;
    private IBoardScreenContext screen;
    private Component pendingTooltip;

    @Override
    public void bind(NodeWidget widget, RecipeNode node, IBoardScreenContext screen) {
        this.widget = widget;
        this.node = node;
        this.screen = screen;
    }

    @Override
    public boolean isApplicable(RecipeNode node) {
        if (!supportsVoltageTier(node)) return false;
        return !(node.isMultiblock() && GTEnergyHatchCalculator.requiresEnergyHatch(node));
    }

    public static boolean supportsVoltageTier(RecipeNode node) {
        if (node == null || node.getTargetTier() == null || node.isModule()) return false;
        if (SteamModeSection.isSteamNode(node) || BoilerThrottleSection.isBoiler(node)) return false;
        if (Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE))) return true;
        return node.getEnergyType() == EnergyType.ELECTRIC_EU;
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 12 + getTierControlsHeight(node) + 6;
    }

    public static int getTierControlsHeight(RecipeNode node) {
        if (node == null || node.getTargetTier() == null) return 0;
        List<GTVoltageTier> tiers = getInspectorTiers(node);
        if (tiers.isEmpty()) return 0;
        int cols = 4;
        int numRows = (tiers.size() + cols - 1) / cols;
        return numRows * 16 + (numRows - 1) * 4;
    }

    public static List<GTVoltageTier> getInspectorTiers(RecipeNode node) {
        if (node == null) return Collections.emptyList();
        if (GTCombustionHelper.isCombustionFamily(node)) {
            return GTCombustionHelper.getAvailableCombustionTiers();
        }
        if (node.isTurbine()) {
            return getTurbineInspectorTiers(node);
        }
        return getStandardInspectorTiers(node);
    }

    private static List<GTVoltageTier> getTurbineInspectorTiers(RecipeNode node) {
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

    private static List<GTVoltageTier> getStandardInspectorTiers(RecipeNode node) {
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
    public Component getPendingTooltip() {
        return pendingTooltip;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        this.pendingTooltip = null;
        if (node == null) return;

        String tierLabel = Component.translatable("gui.gtcalcboard.inspector.tier").getString();
        graphics.drawString(font, tierLabel, x, y, 0xFF94A3B8, false);

        int gridY = y + 12;
        renderTierChips(graphics, font, x, gridY, w, mouseX, mouseY);
    }

    private void renderTierChips(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        GTVoltageTier currentTier = node.getTargetTier();
        List<GTVoltageTier> tiers = getInspectorTiers(node);
        boolean isEnergyHatchLocked = GTAddonCompatibilityHandler.hasEnergyHatch(node);
        int cols = 4;
        int gap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;
        int chipH = 16;

        for (int i = 0; i < tiers.size(); i++) {
            GTVoltageTier t = tiers.get(i);
            int cx = x + (i % cols) * (chipW + gap);
            int cy = y + (i / cols) * (chipH + 4);
            boolean isCur = (t == currentTier);
            boolean hov = mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH;

            renderChip(graphics, font, cx, cy, chipW, chipH, t, isCur, hov, isEnergyHatchLocked);

            if (hov && isEnergyHatchLocked) {
                String hatchTierName = currentTier != null ? currentTier.getName() : "Unknown";
                this.pendingTooltip = Component.translatable("gui.gtcalcboard.inspector.tier_locked_by_energy_hatch", hatchTierName);
            }
        }
    }

    private void renderChip(GuiGraphics graphics, Font font, int cx, int cy, int chipW, int chipH,
                            GTVoltageTier tier, boolean isCur, boolean hov, boolean locked) {
        int bg = locked ? (isCur ? 0xFF1E3A5F : 0xFF0F172A) : (isCur ? 0xFF0284C7 : (hov ? 0xFF334155 : 0xFF1E293B));
        int border = locked ? (isCur ? 0xFF2563EB : 0xFF1E293B) : (isCur ? 0xFF38BDF8 : (hov ? 0xFF64748B : 0xFF334155));
        int textColor = locked ? (isCur ? 0xFF93C5FD : 0xFF475569) : (isCur ? 0xFFFFFFFF : 0xFF94A3B8);

        graphics.fill(cx, cy, cx + chipW, cy + chipH, bg);
        graphics.renderOutline(cx, cy, chipW, chipH, border);
        graphics.drawCenteredString(font, tier.name(), cx + chipW / 2, cy + 4, textColor);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;
        if (GTAddonCompatibilityHandler.hasEnergyHatch(node)) return false;

        List<GTVoltageTier> tiers = getInspectorTiers(node);
        int cols = 4;
        int gap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;
        int chipH = 16;
        int gridY = y + 12;

        for (int i = 0; i < tiers.size(); i++) {
            GTVoltageTier t = tiers.get(i);
            int cx = x + (i % cols) * (chipW + gap);
            int cy = gridY + (i / cols) * (chipH + 4);
            if (mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH) {
                applyTierSelection(t);
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getSoundManager() != null) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                if (widget != null) widget.invalidateCache();
                if (screen != null) screen.markSummaryDirty();
                return true;
            }
        }
        return false;
    }

    private void applyTierSelection(GTVoltageTier tier) {
        if (GTCombustionHelper.isCombustionFamily(node)) {
            GTVoltageTier oldTier = node.getTargetTier();
            boolean ok = GTCombustionHelper.syncCombustionMachine(node, tier);
            if (ok && screen != null) {
                screen.recordCommand(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, tier));
                syncSharedFrame();
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
            syncSharedFrame();
        }
    }

    private void syncSharedFrame() {
        if (screen != null && screen.getGraph() != null) {
            var frame = screen.getGraph().findFrameEnclosingNode(node);
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, screen.getGraph());
                screen.rebuildBoardWidgets();
            }
        }
    }
}
