package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Floating contextual action toolbar that appears above multi-selected nodes on the canvas.
 * Provides quick actions for framing, grouping into modules, auto ratio scaling, and copying.
 */
public class SelectionFloatingToolbarWidget {

    public static final int BAR_HEIGHT = 22;
    private static final int BTN_HEIGHT = 16;
    private static final int BTN_SPACING = 3;

    private final IBoardScreenContext screen;
    private final List<ToolbarAction> actions = new ArrayList<>();
    private final List<ButtonSlot> buttonSlots = new ArrayList<>();

    private int barX = 0;
    private int barY = 0;
    private int barWidth = 0;
    private int badgeWidth = 0;
    private boolean visible = false;

    public record ToolbarAction(String icon, Component label, Component tooltip, Runnable action, boolean isDanger) {}

    private static class ButtonSlot {
        final ToolbarAction action;
        int x;
        int y;
        int width;
        int height;

        ButtonSlot(ToolbarAction action) {
            this.action = action;
        }

        boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public SelectionFloatingToolbarWidget(IBoardScreenContext screen) {
        this.screen = screen;
        initActions();
    }

    private void initActions() {
        Runnable onFrame = screen != null ? screen::createFrameFromSelection : () -> {};
        Runnable onModule = screen != null ? screen::performGroupIntoModule : () -> {};
        Runnable onShared = screen != null ? screen::createSharedMachineFrameFromSelection : () -> {};
        Runnable onRatio = screen != null ? screen::performAutoRatio : () -> {};
        Runnable onCopy = screen != null ? screen::copySelection : () -> {};
        Runnable onDelete = screen != null ? screen::deleteSelection : () -> {};

        actions.add(new ToolbarAction("▤", Component.translatable("gui.gtcalcboard.floating_bar.frame"),
                Component.translatable("gui.gtcalcboard.floating_bar.tooltip.frame"), onFrame, false));
        actions.add(new ToolbarAction("📦", Component.translatable("gui.gtcalcboard.floating_bar.module"),
                Component.translatable("gui.gtcalcboard.floating_bar.tooltip.module"), onModule, false));
        actions.add(new ToolbarAction("⧉", Component.translatable("gui.gtcalcboard.floating_bar.shared_frame"),
                Component.translatable("gui.gtcalcboard.floating_bar.tooltip.shared_frame"), onShared, false));
        actions.add(new ToolbarAction("⚖", Component.translatable("gui.gtcalcboard.floating_bar.auto_ratio"),
                Component.translatable("gui.gtcalcboard.floating_bar.tooltip.auto_ratio"), onRatio, false));
        actions.add(new ToolbarAction("📋", Component.translatable("gui.gtcalcboard.floating_bar.copy"),
                Component.translatable("gui.gtcalcboard.floating_bar.tooltip.copy"), onCopy, false));
        actions.add(new ToolbarAction("✕", Component.translatable("gui.gtcalcboard.floating_bar.delete"),
                Component.translatable("gui.gtcalcboard.floating_bar.tooltip.delete"), onDelete, true));
    }

    public boolean isVisible() {
        return visible;
    }

    public int getBarX() {
        return barX;
    }

    public int getBarY() {
        return barY;
    }

    public int getBarWidth() {
        return barWidth;
    }

    public int getBarHeight() {
        return BAR_HEIGHT;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!updateStateAndLayout(font)) {
            this.visible = false;
            return;
        }
        this.visible = true;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 310.0f);

        renderBackground(graphics);
        renderBadge(graphics, font);
        renderButtons(graphics, font, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private boolean updateStateAndLayout(Font font) {
        if (isSuppressed()) return false;

        double[] bounds = computeSelectionCanvasBounds();
        if (bounds == null) return false;

        boolean compact = screen.getScreenWidth() < 540;
        calculateButtonPositions(font, compact);
        positionToolbar(bounds);
        return true;
    }

    private boolean isSuppressed() {
        if (screen == null || screen.isAnyModalOpen()) return true;
        if (screen.getSelectedNodeIds().size() < 2 && getSelectedEntityTotal() < 2) return true;
        return screen.isBoxSelecting();
    }

    private int getSelectedEntityTotal() {
        return screen.getSelectedNodeIds().size() + screen.getSelectedFrameIds().size() + screen.getSelectedNoteIds().size();
    }

    private double[] computeSelectionCanvasBounds() {
        Set<String> selNodes = screen.getSelectedNodeIds();
        Set<String> selFrames = screen.getSelectedFrameIds();
        Set<String> selNotes = screen.getSelectedNoteIds();

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean found = false;

        for (NodeWidget nw : screen.getNodeWidgets()) {
            RecipeNode n = nw.getNode();
            if (!selNodes.contains(n.getId())) continue;
            found = true;
            minX = Math.min(minX, n.getPosX());
            minY = Math.min(minY, n.getPosY());
            maxX = Math.max(maxX, n.getPosX() + nw.getWidth());
            maxY = Math.max(maxY, n.getPosY() + nw.getHeight());
        }

        if (screen.getGraph() != null) {
            for (CanvasGroupFrame f : screen.getGraph().getFrames()) {
                if (!selFrames.contains(f.getId())) continue;
                found = true;
                minX = Math.min(minX, f.getPosX());
                minY = Math.min(minY, f.getPosY());
                maxX = Math.max(maxX, f.getPosX() + f.getWidth());
                maxY = Math.max(maxY, f.getPosY() + f.getHeight());
            }
            for (CanvasStickyNote note : screen.getGraph().getStickyNotes()) {
                if (!selNotes.contains(note.getId())) continue;
                found = true;
                minX = Math.min(minX, note.getPosX());
                minY = Math.min(minY, note.getPosY());
                maxX = Math.max(maxX, note.getPosX() + note.getWidth());
                maxY = Math.max(maxY, note.getPosY() + note.getHeight());
            }
        }

        return found ? new double[]{minX, minY, maxX, maxY} : null;
    }

    private void calculateButtonPositions(Font font, boolean compact) {
        buttonSlots.clear();
        String badgeText = getBadgeText();
        this.badgeWidth = font.width(badgeText) + 12;

        int curRelX = badgeWidth + 7;
        for (ToolbarAction action : actions) {
            ButtonSlot slot = new ButtonSlot(action);
            String btnText = compact ? action.icon() : action.icon() + " " + action.label().getString();
            slot.width = font.width(btnText) + 10;
            slot.height = BTN_HEIGHT;
            slot.x = curRelX;
            slot.y = (BAR_HEIGHT - BTN_HEIGHT) / 2;
            buttonSlots.add(slot);
            curRelX += slot.width + BTN_SPACING;
        }
        this.barWidth = curRelX + 3;
    }

    private void positionToolbar(double[] canvasBounds) {
        double canvasCenterX = (canvasBounds[0] + canvasBounds[2]) / 2.0;
        double canvasTopY = canvasBounds[1];
        double canvasBottomY = canvasBounds[3];

        double screenCenterX = screen.toScreenX(canvasCenterX);
        double screenTopY = screen.toScreenY(canvasTopY);
        double screenBottomY = screen.toScreenY(canvasBottomY);

        int targetX = (int) Math.round(screenCenterX - barWidth / 2.0);
        int targetY = (int) Math.round(screenTopY - BAR_HEIGHT - 8);

        int minAllowedY = screen.getHeaderBottomY() + 6;
        int maxAllowedY = screen.getScreenHeight() - AdaptiveStatusBar.BAR_HEIGHT - BAR_HEIGHT - 6;

        if (targetY < minAllowedY) {
            targetY = (int) Math.round(screenBottomY + 8);
        }
        targetY = Math.max(minAllowedY, Math.min(maxAllowedY, targetY));

        int minAllowedX = LeftActivityBarWidget.BAR_WIDTH + 6;
        int maxAllowedX = screen.getScreenWidth() - barWidth - screen.getSummaryRightOffset() - 6;
        targetX = Math.max(minAllowedX, Math.min(maxAllowedX, targetX));

        this.barX = targetX;
        this.barY = targetY;

        for (ButtonSlot slot : buttonSlots) {
            slot.x += barX;
            slot.y += barY;
        }
    }

    private void renderBackground(GuiGraphics graphics) {
        graphics.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, 0xF20F172A);
        graphics.renderOutline(barX, barY, barWidth, BAR_HEIGHT, 0xFF38BDF8);
        graphics.renderOutline(barX + 1, barY + 1, barWidth - 2, BAR_HEIGHT - 2, 0x3338BDF8);
    }

    private void renderBadge(GuiGraphics graphics, Font font) {
        String badgeText = getBadgeText();
        int textY = barY + 7;
        graphics.drawString(font, badgeText, barX + 6, textY, 0xFF38BDF8, false);

        int dividerX = barX + badgeWidth + 2;
        graphics.fill(dividerX, barY + 4, dividerX + 1, barY + BAR_HEIGHT - 4, 0xFF334155);
    }

    private String getBadgeText() {
        int count = getSelectedEntityTotal();
        return Component.translatable("gui.gtcalcboard.floating_bar.count", count).getString();
    }

    private void renderButtons(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        boolean compact = screen.getScreenWidth() < 540;
        for (ButtonSlot slot : buttonSlots) {
            boolean hovered = slot.isHovered(mouseX, mouseY);
            renderSingleButton(graphics, font, slot, hovered, compact);
        }
    }

    private void renderSingleButton(GuiGraphics graphics, Font font, ButtonSlot slot, boolean hovered, boolean compact) {
        int bg = hovered ? (slot.action.isDanger() ? 0x66EF4444 : 0xFF334155) : 0x221E293B;
        int border = hovered ? (slot.action.isDanger() ? 0xFFEF4444 : 0xFF38BDF8) : 0xFF334155;
        int textCol = slot.action.isDanger() ? (hovered ? 0xFFFFAAAA : 0xFFF87171) : (hovered ? 0xFFFFFFFF : 0xFFE2E8F0);

        graphics.fill(slot.x, slot.y, slot.x + slot.width, slot.y + slot.height, bg);
        graphics.renderOutline(slot.x, slot.y, slot.width, slot.height, border);

        String text = compact ? slot.action.icon() : slot.action.icon() + " " + slot.action.label().getString();
        int tw = font.width(text);
        int tx = slot.x + (slot.width - tw) / 2;
        int ty = slot.y + 4;
        graphics.drawString(font, text, tx, ty, textCol, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (mouseX < barX || mouseX > barX + barWidth || mouseY < barY || mouseY > barY + BAR_HEIGHT) {
            return false;
        }

        if (button == 0) {
            for (ButtonSlot slot : buttonSlots) {
                if (slot.isHovered(mouseX, mouseY)) {
                    playClickSound();
                    slot.action.action().run();
                    return true;
                }
            }
        }
        return true;
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;
        for (ButtonSlot slot : buttonSlots) {
            if (slot.isHovered(mouseX, mouseY)) {
                BoardTooltipRenderer.renderTooltip(graphics, font, slot.action.tooltip(), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
                return;
            }
        }
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
    }
}
