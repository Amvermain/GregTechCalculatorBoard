package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.action.BoardActionHandler;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeInspectorPanel;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public class PageSettingsInspector implements INodeSubInspector {

    private final IBoardScreenContext screen;
    private final Runnable closeAction;
    private Component pendingTooltip;

    public PageSettingsInspector(IBoardScreenContext screen, Runnable closeAction) {
        this.screen = screen;
        this.closeAction = closeAction;
    }

    @Override
    public void bind(NodeWidget targetWidget) {
    }

    @Override
    public int getContentHeight() {
        return 210;
    }

    @Override
    public Component getPendingTooltip() {
        return pendingTooltip;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int px, int py, int ph, int mouseX, int mouseY) {
        this.pendingTooltip = null;
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page == null) return;

        String title = "📄 " + Component.translatable("gui.gtcalcboard.page_settings.title").getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, NodeInspectorPanel.PANEL_WIDTH - 48), px + 6, py + 7, 0xFFE2E8F0, false);

        int popoutX = px + NodeInspectorPanel.PANEL_WIDTH - 30;
        int closeX = px + NodeInspectorPanel.PANEL_WIDTH - 16;
        int closeY = py + 5;
        boolean popoutHov = mouseX >= popoutX && mouseX <= popoutX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "↗", popoutX + 1, closeY + 1, popoutHov ? 0xFF38BDF8 : 0xFF94A3B8, false);
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);

        int curY = py + 28;
        int x = px + 8;
        int contentW = NodeInspectorPanel.PANEL_WIDTH - 16;

        renderPageInfoSection(graphics, font, x, curY, contentW, page);
        curY += 34;

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.page_settings.target_voltage").getString(), x, curY, 0xFF94A3B8, false);
        curY += 12;

        renderVoltageTierGrid(graphics, font, x, curY, contentW, page, mouseX, mouseY);
        curY += 80;

        renderAutoHatchCheckbox(graphics, font, x, curY, contentW, page, mouseX, mouseY);
        curY += 22;

        renderBatchApplyButton(graphics, font, x, curY, contentW, page, mouseX, mouseY);
    }

    private void renderPageInfoSection(GuiGraphics graphics, Font font, int x, int y, int w, BoardPage page) {
        graphics.fill(x, y, x + w, y + 30, 0xFF0F172A);
        graphics.renderOutline(x, y, w, 30, 0xFF334155);
        graphics.drawString(font, font.plainSubstrByWidth(page.getName(), w - 12), x + 6, y + 4, 0xFFE2E8F0, false);
        String folder = page.getFolderPath().isEmpty() ? "/" : page.getFolderPath();
        graphics.drawString(font, "📁 " + font.plainSubstrByWidth(folder, w - 20), x + 6, y + 16, 0xFF94A3B8, false);
    }

    private void renderVoltageTierGrid(GuiGraphics graphics, Font font, int x, int y, int w, BoardPage page, int mouseX, int mouseY) {
        int cols = 4;
        int gap = 4;
        int rowGap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;
        int chipH = 16;
        GTVoltageTier currentTier = page.getDefaultVoltageTier();

        for (int i = 0; i < 16; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (chipW + gap);
            int cy = y + row * (chipH + rowGap);
            boolean isCur = (i == 0) ? (currentTier == null) : (currentTier == GTVoltageTier.getByIndex(i - 1));
            boolean hov = mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH;

            int bg = isCur ? 0xFF0284C7 : (hov ? 0xFF334155 : 0xFF1E293B);
            int border = isCur ? 0xFF38BDF8 : (hov ? 0xFF64748B : 0xFF334155);
            int textColor = isCur ? 0xFFFFFFFF : 0xFF94A3B8;

            graphics.fill(cx, cy, cx + chipW, cy + chipH, bg);
            graphics.renderOutline(cx, cy, chipW, chipH, border);

            String chipLabel = (i == 0) ? "Auto" : GTVoltageTier.getByIndex(i - 1).name();
            graphics.drawCenteredString(font, chipLabel, cx + chipW / 2, cy + 4, textColor);

            if (hov) {
                this.pendingTooltip = (i == 0)
                        ? Component.translatable("gui.gtcalcboard.page_settings.target_voltage_auto")
                        : Component.literal(GTVoltageTier.getByIndex(i - 1).getFormatCode() + GTVoltageTier.getByIndex(i - 1).getName() + " §7(" + String.format(Locale.ROOT, "%,d", GTVoltageTier.getByIndex(i - 1).getVoltage()) + " EU/t)");
            }
        }
    }

    private void renderAutoHatchCheckbox(GuiGraphics graphics, Font font, int x, int y, int w, BoardPage page, int mouseX, int mouseY) {
        boolean autoHatch = page.isAutoEquipEnergyHatches();
        boolean checkHov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y + 1, x + 14, y + 15, autoHatch ? 0xFF0284C7 : (checkHov ? 0xFF334155 : 0xFF1E293B));
        graphics.renderOutline(x, y + 1, 14, 14, autoHatch ? 0xFF38BDF8 : 0xFF475569);
        if (autoHatch) {
            graphics.drawString(font, "✔", x + 3, y + 4, 0xFFFFFFFF, false);
        }
        String toggleText = font.plainSubstrByWidth(Component.translatable("gui.gtcalcboard.page_settings.autohatch_toggle").getString(), w - 20);
        graphics.drawString(font, toggleText, x + 18, y + 4, checkHov ? 0xFFFFFFFF : 0xFFCBD5E1, false);
        if (checkHov) {
            this.pendingTooltip = Component.translatable("gui.gtcalcboard.page_settings.autohatch_tooltip");
        }
    }

    private void renderBatchApplyButton(GuiGraphics graphics, Font font, int x, int y, int w, BoardPage page, int mouseX, int mouseY) {
        var graph = screen != null ? screen.getGraph() : null;
        int applicableCount = BoardActionHandler.countBatchApplicableNodes(graph, page.getDefaultVoltageTier());
        boolean canApply = page.getDefaultVoltageTier() != null && applicableCount > 0;
        boolean btnHov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22;

        int btnBg = canApply ? (btnHov ? 0xFF0369A1 : 0xFF0C4A6E) : 0xFF1E293B;
        int btnBorder = canApply ? (btnHov ? 0xFF38BDF8 : 0xFF0284C7) : 0xFF334155;
        int btnTextCol = canApply ? 0xFFFFFFFF : 0xFF64748B;
        graphics.fill(x, y, x + w, y + 22, btnBg);
        graphics.renderOutline(x, y, w, 22, btnBorder);
        String btnLabel = "⚡ " + Component.translatable("gui.gtcalcboard.page_settings.apply_to_existing", applicableCount).getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(btnLabel, w - 8), x + w / 2, y + 7, btnTextCol);
        if (btnHov) {
            this.pendingTooltip = Component.translatable("gui.gtcalcboard.page_settings.apply_to_existing_tooltip");
        }
    }

    @Override
    public boolean mouseClicked(int px, int py, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page == null) return false;

        int popoutX = px + NodeInspectorPanel.PANEL_WIDTH - 30;
        int closeX = px + NodeInspectorPanel.PANEL_WIDTH - 16;
        int closeY = py + 5;
        if (mouseX >= popoutX && mouseX <= popoutX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            if (closeAction != null) closeAction.run();
            if (screen != null) screen.openPageSettingsDialog(page);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            if (closeAction != null) closeAction.run();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        int curY = py + 28 + 34 + 12;
        int x = px + 8;
        int contentW = NodeInspectorPanel.PANEL_WIDTH - 16;
        int cols = 4;
        int gap = 4;
        int rowGap = 4;
        int chipW = (contentW - gap * (cols - 1)) / cols;
        int chipH = 16;

        for (int i = 0; i < 16; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (chipW + gap);
            int cy = curY + row * (chipH + rowGap);
            if (mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH) {
                if (i == 0) {
                    page.setDefaultVoltageTier(null);
                } else {
                    page.setDefaultVoltageTier(GTVoltageTier.getByIndex(i - 1));
                }
                BoardManager.getInstance().saveForCurrentContext();
                if (screen != null) {
                    screen.rebuildBoardWidgets();
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        curY += 80;

        if (mouseX >= x && mouseX <= x + contentW && mouseY >= curY && mouseY <= curY + 16) {
            page.setAutoEquipEnergyHatches(!page.isAutoEquipEnergyHatches());
            BoardManager.getInstance().saveForCurrentContext();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        curY += 22;

        if (mouseX >= x && mouseX <= x + contentW && mouseY >= curY && mouseY <= curY + 22) {
            GTVoltageTier currentTier = page.getDefaultVoltageTier();
            var graph = screen != null ? screen.getGraph() : null;
            int applicableCount = BoardActionHandler.countBatchApplicableNodes(graph, currentTier);
            if (currentTier != null && applicableCount > 0 && screen != null) {
                screen.batchApplyPageTargetVoltage();
            }
            return true;
        }

        return true;
    }
}
