package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator;
import com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Locale;

public class MultiblockEnergyHatchSection implements IInspectorSection {

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
        if (node == null) return false;
        if (SteamModeSection.isSteamNode(node) || BoilerThrottleSection.isBoiler(node)) return false;
        return node.isMultiblock() && GTEnergyHatchCalculator.requiresEnergyHatch(node);
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 42 + SingleblockTierSection.getTierControlsHeight(node);
    }

    @Override
    public Component getPendingTooltip() {
        return pendingTooltip;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        this.pendingTooltip = null;
        if (node == null) return;

        String headerLabel = "⚡ " + Component.translatable("gui.gtcalcboard.inspector.energy_hatch").getString();
        graphics.drawString(font, headerLabel, x, y, 0xFF94A3B8, false);

        int bannerY = y + 12;
        GTEnergyHatchAddon hatch = getPrimaryEnergyHatch(node);
        renderHatchStatusBanner(graphics, font, x, bannerY, w, hatch);

        int gridY = bannerY + 24;
        renderHatchTierGrid(graphics, font, x, gridY, w, hatch, mouseX, mouseY);
    }

    private void renderHatchStatusBanner(GuiGraphics graphics, Font font, int x, int y, int w, GTEnergyHatchAddon hatch) {
        if (hatch != null) {
            GTVoltageTier tier = hatch.getTier();
            int amp = hatch.getAmperage();
            long capacity = GTEnergyHatchCalculator.getMaxEUtCapacity(node);
            int hatchCount = (int) node.getAddons().stream().filter(a -> a instanceof GTEnergyHatchAddon).count();
            String bannerText = hatchCount > 1
                    ? String.format(Locale.ROOT, "⚡ %dx %s %dA (%,d EU/t)", hatchCount, tier.getName(), amp, capacity)
                    : String.format(Locale.ROOT, "⚡ %s %dA (%,d EU/t)", tier.getName(), amp, capacity);

            graphics.fill(x, y, x + w, y + 20, 0xFF1E3A5F);
            graphics.renderOutline(x, y, w, 20, 0xFF2563EB);
            graphics.drawString(font, font.plainSubstrByWidth(bannerText, w - 8), x + 6, y + 6, 0xFF93C5FD, false);
        } else {
            String missingText = Component.translatable("gui.gtcalcboard.inspector.hatch_missing").getString();

            graphics.fill(x, y, x + w, y + 20, 0xFF3B1818);
            graphics.renderOutline(x, y, w, 20, 0xFFEF4444);
            graphics.drawString(font, font.plainSubstrByWidth(missingText, w - 8), x + 6, y + 6, 0xFFFCA5A5, false);
        }
    }

    private void renderHatchTierGrid(GuiGraphics graphics, Font font, int x, int y, int w,
                                     GTEnergyHatchAddon hatch, int mouseX, int mouseY) {
        List<GTVoltageTier> tiers = SingleblockTierSection.getInspectorTiers(node);
        GTVoltageTier currentTier = hatch != null ? hatch.getTier() : null;
        int cols = 4;
        int gap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;
        int chipH = 16;

        for (int i = 0; i < tiers.size(); i++) {
            GTVoltageTier t = tiers.get(i);
            int cx = x + (i % cols) * (chipW + gap);
            int cy = y + (i / cols) * (chipH + 4);
            boolean isCur = (currentTier != null && t == currentTier);
            boolean hov = mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH;

            int bg = isCur ? 0xFF0284C7 : (hov ? 0xFF334155 : 0xFF1E293B);
            int border = isCur ? 0xFF38BDF8 : (hov ? 0xFF64748B : 0xFF334155);
            int textColor = isCur ? 0xFFFFFFFF : 0xFF94A3B8;

            graphics.fill(cx, cy, cx + chipW, cy + chipH, bg);
            graphics.renderOutline(cx, cy, chipW, chipH, border);
            graphics.drawCenteredString(font, t.name(), cx + chipW / 2, cy + 4, textColor);

            if (hov) {
                long cap = (long) t.getVoltage() * 2L;
                this.pendingTooltip = Component.translatable("gui.gtcalcboard.inspector.hatch_install_tooltip", t.getName(), 2, cap);
            }
        }
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        List<GTVoltageTier> tiers = SingleblockTierSection.getInspectorTiers(node);
        int cols = 4;
        int gap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;
        int chipH = 16;
        int gridY = y + 12 + 24;
        GTEnergyHatchAddon currentHatch = getPrimaryEnergyHatch(node);

        for (int i = 0; i < tiers.size(); i++) {
            GTVoltageTier t = tiers.get(i);
            int cx = x + (i % cols) * (chipW + gap);
            int cy = gridY + (i / cols) * (chipH + 4);
            if (mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH) {
                if (currentHatch != null && currentHatch.getTier() == t) {
                    return true;
                }
                installEnergyHatch(t);
                return true;
            }
        }
        return false;
    }

    private void installEnergyHatch(GTVoltageTier tier) {
        boolean ok = EnergyHatchHelper.installDefaultEnergyHatch(node, tier);
        if (!ok) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        if (widget != null) widget.invalidateCache();
        if (screen != null) screen.markSummaryDirty();
        syncSharedFrame();
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

    public static GTEnergyHatchAddon getPrimaryEnergyHatch(RecipeNode node) {
        if (node == null) return null;
        GTEnergyHatchAddon primary = null;
        for (MachineAddon addon : node.getAddons()) {
            if (addon instanceof GTEnergyHatchAddon hatch) {
                if (primary == null || hatch.getTier().ordinal() > primary.getTier().ordinal()) {
                    primary = hatch;
                }
            }
        }
        return primary;
    }
}
