package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.history.command.BatchChangeTierCommand;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.action.BoardActionHandler;
import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Dedicated modal dialog for configuring page-level target voltage tier,
 * multiblock energy hatch auto-provisioning, and batch tier application.
 */
public class PageSettingsDialog implements IBoardModal {

    private final BoardScreen parent;
    private BoardPage targetPage;
    private boolean visible = false;

    private static final int DIALOG_WIDTH = 320;
    private static final int DIALOG_HEIGHT = 230;

    private Component pendingTooltip = null;

    public PageSettingsDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(BoardPage page) {
        this.targetPage = (page != null) ? page : BoardManager.getInstance().getActivePage();
        this.visible = true;
        this.pendingTooltip = null;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void close() {
        this.visible = false;
        this.pendingTooltip = null;
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        if (!visible || targetPage == null) return;

        GuiGraphics graphics = context.graphics();
        Font font = Minecraft.getInstance().font;
        int screenWidth = context.screenWidth();
        int screenHeight = context.screenHeight();
        int mouseX = context.mouseX();
        int mouseY = context.mouseY();

        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        renderBackground(graphics, font, x, y, mouseX, mouseY);
        renderPageInfo(graphics, font, x + 16, y + 28, DIALOG_WIDTH - 32);
        renderVoltageGrid(graphics, font, x + 16, y + 68, DIALOG_WIDTH - 32, mouseX, mouseY);
        renderAutoHatchToggle(graphics, font, x + 16, y + 158, DIALOG_WIDTH - 32, mouseX, mouseY);
        renderBatchApplyButton(graphics, font, x + 16, y + 188, DIALOG_WIDTH - 32, mouseX, mouseY);

        if (pendingTooltip != null) {
            BoardTooltipRenderer.renderTooltip(graphics, font, pendingTooltip, mouseX, mouseY, screenWidth, screenHeight);
            pendingTooltip = null;
        }
    }

    private void renderBackground(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xF5101522);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF334155);

        graphics.fill(x, y, x + DIALOG_WIDTH, y + 22, 0xFF1E293B);
        graphics.renderOutline(x, y, DIALOG_WIDTH, 22, 0xFF475569);

        String title = "📄 " + Component.translatable("gui.gtcalcboard.page_settings.title").getString();
        graphics.drawString(font, title, x + 8, y + 7, 0xFFE2E8F0, false);

        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 6;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX, closeY, closeHover ? 0xFFEF4444 : 0xFF94A3B8, false);
    }

    private void renderPageInfo(GuiGraphics graphics, Font font, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 26, 0xFF182234);
        graphics.renderOutline(x, y, w, 26, 0xFF334155);

        graphics.drawString(font, font.plainSubstrByWidth(targetPage.getName(), w - 12), x + 6, y + 4, 0xFFE2E8F0, false);
        String folder = targetPage.getFolderPath().isEmpty() ? "/" : targetPage.getFolderPath();
        graphics.drawString(font, "📁 " + font.plainSubstrByWidth(folder, w - 20), x + 6, y + 15, 0xFF94A3B8, false);
    }

    private void renderVoltageGrid(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        String label = Component.translatable("gui.gtcalcboard.page_settings.target_voltage").getString();
        graphics.drawString(font, label, x, y - 11, 0xFF94A3B8, false);

        int cols = 4;
        int gap = 6;
        int rowGap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;
        int chipH = 16;
        GTVoltageTier currentTier = targetPage.getDefaultVoltageTier();

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

    private void renderAutoHatchToggle(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean autoHatch = targetPage.isAutoEquipEnergyHatches();
        boolean checkHov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 16;

        graphics.fill(x, y + 1, x + 14, y + 15, autoHatch ? 0xFF0284C7 : (checkHov ? 0xFF334155 : 0xFF1E293B));
        graphics.renderOutline(x, y + 1, 14, 14, autoHatch ? 0xFF38BDF8 : 0xFF475569);
        if (autoHatch) {
            graphics.drawString(font, "✔", x + 3, y + 4, 0xFFFFFFFF, false);
        }

        String toggleText = Component.translatable("gui.gtcalcboard.page_settings.autohatch_toggle").getString();
        graphics.drawString(font, toggleText, x + 18, y + 4, checkHov ? 0xFFFFFFFF : 0xFFCBD5E1, false);

        if (checkHov) {
            this.pendingTooltip = Component.translatable("gui.gtcalcboard.page_settings.autohatch_tooltip");
        }
    }

    private void renderBatchApplyButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        int applicableCount = BoardActionHandler.countBatchApplicableNodes(parent.getGraph(), targetPage.getDefaultVoltageTier());
        boolean canApply = targetPage.getDefaultVoltageTier() != null && applicableCount > 0;
        boolean btnHov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22;

        int btnBg = canApply ? (btnHov ? 0xFF0369A1 : 0xFF0C4A6E) : 0xFF1E293B;
        int btnBorder = canApply ? (btnHov ? 0xFF38BDF8 : 0xFF0284C7) : 0xFF334155;
        int btnTextCol = canApply ? 0xFFFFFFFF : 0xFF64748B;

        graphics.fill(x, y, x + w, y + 22, btnBg);
        graphics.renderOutline(x, y, w, 22, btnBorder);

        String btnLabel = "⚡ " + Component.translatable("gui.gtcalcboard.page_settings.apply_to_existing", applicableCount).getString();
        graphics.drawCenteredString(font, btnLabel, x + w / 2, y + 7, btnTextCol);

        if (btnHov) {
            this.pendingTooltip = Component.translatable("gui.gtcalcboard.page_settings.apply_to_existing_tooltip");
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || targetPage == null) return false;

        int screenWidth = parent.width;
        int screenHeight = parent.height;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        if (mouseX < x || mouseX > x + DIALOG_WIDTH || mouseY < y || mouseY > y + DIALOG_HEIGHT) {
            close();
            return true;
        }

        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 6;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            close();
            playClickSound();
            return true;
        }

        if (handleTierGridClick(x + 16, y + 68, DIALOG_WIDTH - 32, mouseX, mouseY)) {
            return true;
        }

        if (handleAutoHatchClick(x + 16, y + 158, DIALOG_WIDTH - 32, mouseX, mouseY)) {
            return true;
        }

        return handleBatchApplyClick(x + 16, y + 188, DIALOG_WIDTH - 32, mouseX, mouseY);
    }

    private boolean handleTierGridClick(int x, int y, int w, double mouseX, double mouseY) {
        int cols = 4;
        int gap = 6;
        int rowGap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;
        int chipH = 16;

        for (int i = 0; i < 16; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (chipW + gap);
            int cy = y + row * (chipH + rowGap);
            if (mouseX >= cx && mouseX <= cx + chipW && mouseY >= cy && mouseY <= cy + chipH) {
                if (i == 0) {
                    targetPage.setDefaultVoltageTier(null);
                } else {
                    targetPage.setDefaultVoltageTier(GTVoltageTier.getByIndex(i - 1));
                }
                BoardManager.getInstance().saveForCurrentContext();
                parent.rebuildBoardWidgets();
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private boolean handleAutoHatchClick(int x, int y, int w, double mouseX, double mouseY) {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 16) {
            targetPage.setAutoEquipEnergyHatches(!targetPage.isAutoEquipEnergyHatches());
            BoardManager.getInstance().saveForCurrentContext();
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handleBatchApplyClick(int x, int y, int w, double mouseX, double mouseY) {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22) {
            GTVoltageTier currentTier = targetPage.getDefaultVoltageTier();
            int applicableCount = BoardActionHandler.countBatchApplicableNodes(parent.getGraph(), currentTier);
            if (currentTier != null && applicableCount > 0) {
                parent.batchApplyPageTargetVoltage();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
