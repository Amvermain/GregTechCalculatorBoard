package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.util.FormatUtil;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.ToolbarDisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

/**
 * Top horizontal toolbar widget managing quick access buttons, layout modes, tools, and dropdown menus.
 */
public class ToolbarWidget {
    private final BoardScreen screen;

    public enum DropdownMenu {
        NONE, OPTIMIZE, VIEW, IO, HELP
    }

    public record DropdownItem(String label, String shortcut, Runnable action, boolean keepOpen) {
        public DropdownItem(String label, String shortcut, Runnable action) {
            this(label, shortcut, action, false);
        }
    }

    private DropdownMenu activeDropdown = DropdownMenu.NONE;
    private int dropdownX = 0;
    private int dropdownY = 0;
    private int dropdownW = 160;
    private final List<DropdownItem> currentDropdownItems = new ArrayList<>();

    private int settingsBtnX, settingsBtnW = 18;
    private int pageBtnX, pageBtnW;
    private int searchBtnX, searchBtnW;
    private int optimizeBtnX, optimizeBtnW;
    private int viewBtnX, viewBtnW;
    private int ioBtnX, ioBtnW;
    private int guideBtnX, guideBtnW;

    private int undoBtnX, undoBtnW = 18;
    private int redoBtnX, redoBtnW = 18;
    private int closeBtnX, closeBtnW = 18;
    private int tbX, tbY, tbW, tbH = 18;

    public ToolbarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean isOverflowMenuOpen() {
        return activeDropdown != DropdownMenu.NONE;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        Font font = Minecraft.getInstance().font;
        int width = screen.width;

        this.tbX = screen.getDynamicLeftMargin();
        this.tbY = screen.getToolbarY();
        this.tbW = Math.max(200, width - tbX - 8);

        renderBarBackground(graphics, tbX, tbY, tbW, tbH);
        renderLeftHeaderGroup(graphics, font, mouseX, mouseY);
        renderRightHeaderGroup(graphics, font, mouseX, mouseY);
        updateHoverDropdown(mouseX, mouseY);
        renderActiveDropdownPopup(graphics, font, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderBarBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xF50F172A);
        graphics.renderOutline(x, y, w, h, 0xFF1E293B);
    }

    private void renderLeftHeaderGroup(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int curX = tbX + 2;

        curX = renderSettingsButton(graphics, font, curX, mouseX, mouseY);
        curX = renderPageTitleButton(graphics, font, curX, mouseX, mouseY);
        curX = renderSeparator(graphics, curX);
        curX = renderSearchButton(graphics, font, curX, mouseX, mouseY);
        curX = renderOptimizeButton(graphics, font, curX, mouseX, mouseY);
        curX = renderViewButton(graphics, font, curX, mouseX, mouseY);
        curX = renderIoButton(graphics, font, curX, mouseX, mouseY);
        renderGuideButton(graphics, font, curX, mouseX, mouseY);
    }

    private int renderSettingsButton(GuiGraphics graphics, Font font, int x, int mouseX, int mouseY) {
        this.settingsBtnX = x;
        this.settingsBtnW = 18;
        boolean hovered = isHovered(mouseX, mouseY, settingsBtnX, settingsBtnW);

        graphics.fill(settingsBtnX, tbY + 1, settingsBtnX + settingsBtnW, tbY + tbH - 1, hovered ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(settingsBtnX, tbY + 1, settingsBtnW, tbH - 2, hovered ? 0xFFF59E0B : 0xFF334155);
        graphics.drawCenteredString(font, "⚙", settingsBtnX + settingsBtnW / 2, tbY + 5, 0xFFF59E0B);

        return settingsBtnX + settingsBtnW + 4;
    }

    private int renderPageTitleButton(GuiGraphics graphics, Font font, int x, int mouseX, int mouseY) {
        BoardPage activePage = BoardManager.getInstance().getActivePage();
        String title = (activePage != null && activePage.getName() != null && !activePage.getName().isEmpty())
                ? activePage.getName() : "Untitled Page";
        String pageTxt = font.plainSubstrByWidth(title, 120) + " ▼";

        this.pageBtnX = x;
        this.pageBtnW = font.width(pageTxt) + 10;
        boolean hovered = isHovered(mouseX, mouseY, pageBtnX, pageBtnW);

        if (hovered) {
            graphics.fill(pageBtnX, tbY + 1, pageBtnX + pageBtnW, tbY + tbH - 1, 0xFF1E293B);
            graphics.renderOutline(pageBtnX, tbY + 1, pageBtnW, tbH - 2, 0xFF38BDF8);
        }
        graphics.drawString(font, pageTxt, pageBtnX + 5, tbY + 5, hovered ? 0xFFFFFFFF : 0xFFE2E8F0, false);

        return pageBtnX + pageBtnW + 4;
    }

    private int renderSeparator(GuiGraphics graphics, int x) {
        graphics.fill(x, tbY + 3, x + 1, tbY + tbH - 3, 0xFF334155);
        return x + 5;
    }

    private int renderSearchButton(GuiGraphics graphics, Font font, int x, int mouseX, int mouseY) {
        String label = "🔍 " + Component.translatable("gui.gtcalcboard.search").getString();
        this.searchBtnX = x;
        this.searchBtnW = font.width(label) + 10;
        boolean hovered = isHovered(mouseX, mouseY, searchBtnX, searchBtnW);

        graphics.fill(searchBtnX, tbY + 1, searchBtnX + searchBtnW, tbY + tbH - 1, hovered ? 0xFF0C4A6E : 0xFF0F172A);
        graphics.renderOutline(searchBtnX, tbY + 1, searchBtnW, tbH - 2, hovered ? 0xFF38BDF8 : 0xFF1E293B);
        graphics.drawString(font, label, searchBtnX + 5, tbY + 5, hovered ? 0xFFFFFFFF : 0xFF38BDF8, false);

        return searchBtnX + searchBtnW + 3;
    }

    private int renderOptimizeButton(GuiGraphics graphics, Font font, int x, int mouseX, int mouseY) {
        String label = Component.translatable("gui.gtcalcboard.toolbar.group_optimize").getString() + " ▼";
        this.optimizeBtnX = x;
        this.optimizeBtnW = font.width(label) + 10;
        boolean hovered = isHovered(mouseX, mouseY, optimizeBtnX, optimizeBtnW) || activeDropdown == DropdownMenu.OPTIMIZE;

        graphics.fill(optimizeBtnX, tbY + 1, optimizeBtnX + optimizeBtnW, tbY + tbH - 1, hovered ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(optimizeBtnX, tbY + 1, optimizeBtnW, tbH - 2, hovered ? 0xFF38BDF8 : 0xFF1E293B);
        graphics.drawString(font, label, optimizeBtnX + 5, tbY + 5, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);

        return optimizeBtnX + optimizeBtnW + 3;
    }

    private int renderViewButton(GuiGraphics graphics, Font font, int x, int mouseX, int mouseY) {
        String label = Component.translatable("gui.gtcalcboard.toolbar.group_view").getString() + " ▼";
        this.viewBtnX = x;
        this.viewBtnW = font.width(label) + 10;
        boolean hovered = isHovered(mouseX, mouseY, viewBtnX, viewBtnW) || activeDropdown == DropdownMenu.VIEW;

        graphics.fill(viewBtnX, tbY + 1, viewBtnX + viewBtnW, tbY + tbH - 1, hovered ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(viewBtnX, tbY + 1, viewBtnW, tbH - 2, hovered ? 0xFF38BDF8 : 0xFF1E293B);
        graphics.drawString(font, label, viewBtnX + 5, tbY + 5, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);

        return viewBtnX + viewBtnW + 3;
    }

    private int renderIoButton(GuiGraphics graphics, Font font, int x, int mouseX, int mouseY) {
        String label = Component.translatable("gui.gtcalcboard.toolbar.group_share").getString() + " ▼";
        this.ioBtnX = x;
        this.ioBtnW = font.width(label) + 10;
        boolean hovered = isHovered(mouseX, mouseY, ioBtnX, ioBtnW) || activeDropdown == DropdownMenu.IO;

        graphics.fill(ioBtnX, tbY + 1, ioBtnX + ioBtnW, tbY + tbH - 1, hovered ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(ioBtnX, tbY + 1, ioBtnW, tbH - 2, hovered ? 0xFF38BDF8 : 0xFF1E293B);
        graphics.drawString(font, label, ioBtnX + 5, tbY + 5, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);

        return ioBtnX + ioBtnW + 3;
    }

    private void renderGuideButton(GuiGraphics graphics, Font font, int x, int mouseX, int mouseY) {
        String label = "? " + Component.translatable("gui.gtcalcboard.guide_btn").getString() + " ▼";
        this.guideBtnX = x;
        this.guideBtnW = font.width(label) + 10;
        boolean hovered = isHovered(mouseX, mouseY, guideBtnX, guideBtnW) || activeDropdown == DropdownMenu.HELP;

        graphics.fill(guideBtnX, tbY + 1, guideBtnX + guideBtnW, tbY + tbH - 1, hovered ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(guideBtnX, tbY + 1, guideBtnW, tbH - 2, hovered ? 0xFF38BDF8 : 0xFF1E293B);
        graphics.drawString(font, label, guideBtnX + 5, tbY + 5, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);
    }

    private void renderRightHeaderGroup(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int rightEdge = tbX + tbW - 2;

        this.closeBtnX = rightEdge - 18;
        this.closeBtnW = 18;
        boolean closeHover = isHovered(mouseX, mouseY, closeBtnX, closeBtnW);
        graphics.fill(closeBtnX, tbY + 1, closeBtnX + closeBtnW, tbY + tbH - 1, closeHover ? 0xFF7F1D1D : 0xFF1E293B);
        graphics.renderOutline(closeBtnX, tbY + 1, closeBtnW, tbH - 2, closeHover ? 0xFFEF4444 : 0xFF334155);
        graphics.drawCenteredString(font, "✕", closeBtnX + closeBtnW / 2, tbY + 5, closeHover ? 0xFFFFFFFF : 0xFF94A3B8);

        int sepX = closeBtnX - 5;
        graphics.fill(sepX, tbY + 3, sepX + 1, tbY + tbH - 3, 0xFF334155);

        this.redoBtnX = sepX - 20;
        this.redoBtnW = 18;
        boolean redoHover = isHovered(mouseX, mouseY, redoBtnX, redoBtnW);
        graphics.fill(redoBtnX, tbY + 1, redoBtnX + redoBtnW, tbY + tbH - 1, redoHover ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(redoBtnX, tbY + 1, redoBtnW, tbH - 2, redoHover ? 0xFF38BDF8 : 0xFF334155);
        graphics.drawCenteredString(font, "↷", redoBtnX + redoBtnW / 2, tbY + 5, redoHover ? 0xFFFFFFFF : 0xFF94A3B8);

        this.undoBtnX = redoBtnX - 20;
        this.undoBtnW = 18;
        boolean undoHover = isHovered(mouseX, mouseY, undoBtnX, undoBtnW);
        graphics.fill(undoBtnX, tbY + 1, undoBtnX + undoBtnW, tbY + tbH - 1, undoHover ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(undoBtnX, tbY + 1, undoBtnW, tbH - 2, undoHover ? 0xFF38BDF8 : 0xFF334155);
        graphics.drawCenteredString(font, "↶", undoBtnX + undoBtnW / 2, tbY + 5, undoHover ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    private void renderActiveDropdownPopup(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (activeDropdown == DropdownMenu.NONE || currentDropdownItems.isEmpty()) return;

        buildCurrentDropdownItems();
        int popupH = currentDropdownItems.size() * 18 + 4;
        int px = Math.min(dropdownX, screen.width - dropdownW - 8);
        int py = tbY + tbH + 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 420.0f);

        graphics.fill(px, py, px + dropdownW, py + popupH, 0xF80F172A);
        graphics.renderOutline(px, py, dropdownW, popupH, 0xFF334155);

        for (int i = 0; i < currentDropdownItems.size(); i++) {
            DropdownItem it = currentDropdownItems.get(i);
            int rowY = py + 2 + i * 18;
            boolean hovered = mouseX >= px + 2 && mouseX <= px + dropdownW - 2 && mouseY >= rowY && mouseY <= rowY + 18;

            if (hovered) {
                graphics.fill(px + 2, rowY, px + dropdownW - 2, rowY + 18, 0xFF1E293B);
            }
            graphics.drawString(font, it.label(), px + 6, rowY + 5, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);

            if (it.shortcut() != null) {
                int scW = font.width(it.shortcut());
                graphics.drawString(font, it.shortcut(), px + dropdownW - scW - 6, rowY + 5, 0xFF64748B, false);
            }
        }

        graphics.pose().popPose();
    }

    private void buildCurrentDropdownItems() {
        currentDropdownItems.clear();
        if (activeDropdown == DropdownMenu.OPTIMIZE) {
            populateOptimizeDropdown();
        } else if (activeDropdown == DropdownMenu.VIEW) {
            populateViewDropdown();
        } else if (activeDropdown == DropdownMenu.IO) {
            populateIoDropdown();
        } else if (activeDropdown == DropdownMenu.HELP) {
            populateHelpDropdown();
        }
        adjustDropdownWidth();
    }

    private void adjustDropdownWidth() {
        Font font = Minecraft.getInstance().font;
        int maxW = 160;
        for (DropdownItem it : currentDropdownItems) {
            int labelW = font.width(it.label());
            int scW = (it.shortcut() != null) ? font.width(it.shortcut()) + 16 : 0;
            maxW = Math.max(maxW, labelW + scW + 20);
        }
        this.dropdownW = maxW;
    }

    private void populateOptimizeDropdown() {
        currentDropdownItems.add(new DropdownItem("↔ " + Component.translatable("gui.gtcalcboard.auto_connect").getString(), "Shift+C", this::performAutoConnect));
        currentDropdownItems.add(new DropdownItem("⚖ " + Component.translatable("gui.gtcalcboard.auto_ratio").getString(), "Alt+R", () -> performAutoRatio(false, false)));
        currentDropdownItems.add(new DropdownItem("⚡ " + Component.translatable("gui.gtcalcboard.auto_ratio_fractional").getString(), "Shift+Alt+R", () -> performAutoRatio(false, true)));
        currentDropdownItems.add(new DropdownItem("▲ " + Component.translatable("gui.gtcalcboard.max_flow").getString(), null, this::performMaxThroughputOptimization));
    }

    private void populateViewDropdown() {
        currentDropdownItems.add(new DropdownItem("⌖ " + Component.translatable("gui.gtcalcboard.fit_view").getString(), "Home", screen::fitToView));

        var timeUnit = FormatUtil.getActiveTimeUnit();
        String timeLabel = "⏱ " + Component.translatable("gui.gtcalcboard.toolbar.time_unit").getString() + ": " + timeUnit.getSuffix();
        currentDropdownItems.add(new DropdownItem(timeLabel, null, () -> {
            var next = timeUnit.next();
            FormatUtil.setActiveTimeUnit(next);
            BoardManager.getInstance().setTimeUnit(next);
            BoardManager.getInstance().saveForCurrentContext();
            screen.markSummaryDirty();
        }, true));

        var fluidMode = FormatUtil.getActiveFluidUnitMode();
        String fluidLabel = "~ " + Component.translatable("gui.gtcalcboard.toolbar.fluid_unit").getString() + ": " + fluidMode.getLabel();
        currentDropdownItems.add(new DropdownItem(fluidLabel, null, () -> {
            var next = fluidMode.next();
            FormatUtil.setActiveFluidUnitMode(next);
            BoardManager.getInstance().setFluidUnitMode(next);
            BoardManager.getInstance().saveForCurrentContext();
            screen.markSummaryDirty();
        }, true));

        String snapStatus = BoardManager.getInstance().isGridSnapEnabled() ? "ON" : "OFF";
        String snapLabel = "▦ " + Component.translatable("gui.gtcalcboard.toolbar.grid_snap").getString() + ": " + snapStatus;
        currentDropdownItems.add(new DropdownItem(snapLabel, null, () -> {
            BoardManager.getInstance().setGridSnapEnabled(!BoardManager.getInstance().isGridSnapEnabled());
            BoardManager.getInstance().saveForCurrentContext();
        }, true));

        String slimStatus = BoardManager.getInstance().isSlimCardMode() ? "ON" : "OFF";
        String slimLabel = "□ " + Component.translatable("gui.gtcalcboard.toolbar.slim_card_mode").getString() + ": " + slimStatus;
        currentDropdownItems.add(new DropdownItem(slimLabel, null, () -> {
            BoardManager.getInstance().setSlimCardMode(!BoardManager.getInstance().isSlimCardMode());
            BoardManager.getInstance().saveForCurrentContext();
            screen.rebuildWidgets();
            screen.markSummaryDirty();
        }, true));
    }

    private void populateIoDropdown() {
        currentDropdownItems.add(new DropdownItem("» " + Component.translatable("gui.gtcalcboard.export").getString(), "Ctrl+C", this::copyBlueprintToClipboard));
        currentDropdownItems.add(new DropdownItem("« " + Component.translatable("gui.gtcalcboard.import").getString(), "Ctrl+V", this::importBlueprintFromClipboard));

        if (ModCompatHelper.isAe2Loaded()) {
            BoardPage activePage = BoardManager.getInstance().getActivePage();
            String ae2Label = "⚡ " + Component.translatable("gui.gtcalcboard.ae2.btn_bind").getString();
            currentDropdownItems.add(new DropdownItem(ae2Label, null, () -> {
                if (screen.getPatternBindingDialog() != null) {
                    screen.getPatternBindingDialog().open(activePage);
                }
            }));
        }

        var state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (state.isCollaborationEnabled()) {
            String teamLabel = "👥 " + Component.translatable("gui.gtcalcboard.btn_export_to_team").getString();
            currentDropdownItems.add(new DropdownItem(teamLabel, null, () -> {
                if (screen.getExportToTeamDialog() != null) {
                    screen.getExportToTeamDialog().open();
                }
            }));
        }
    }

    private void populateHelpDropdown() {
        currentDropdownItems.add(new DropdownItem("📖 " + Component.translatable("gui.gtcalcboard.guide.modal_title").getString(), null, () -> {
            if (screen.getGuideDialog() != null) {
                screen.getGuideDialog().open();
            }
        }));
        currentDropdownItems.add(new DropdownItem("▶ " + Component.translatable("gui.gtcalcboard.tutorial_btn").getString(), null, () -> {
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().startTutorial(screen);
        }));
        currentDropdownItems.add(new DropdownItem("✦ " + Component.translatable("gui.gtcalcboard.advanced_tutorial_btn").getString(), null, () -> {
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().startAdvancedTutorial(screen);
        }));
        currentDropdownItems.add(new DropdownItem("⌨ " + Component.translatable("gui.gtcalcboard.activity_bar.help").getString(), "H", () -> {
            if (screen.getHotkeyHudWidget() != null) {
                screen.getHotkeyHudWidget().toggle();
            }
        }));
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int width) {
        return mouseX >= x && mouseX <= x + width && mouseY >= tbY + 1 && mouseY <= tbY + tbH - 1;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (handleDropdownPopupClick(mouseX, mouseY)) {
            return true;
        }

        if (handleLeftGroupClick(mouseX, mouseY)) {
            return true;
        }

        if (handleRightGroupClick(mouseX, mouseY)) {
            return true;
        }

        return isClickInsideBar(mouseX, mouseY);
    }

    private boolean handleDropdownPopupClick(double mouseX, double mouseY) {
        if (activeDropdown == DropdownMenu.NONE || currentDropdownItems.isEmpty()) {
            return false;
        }

        int popupH = currentDropdownItems.size() * 18 + 4;
        int px = Math.min(dropdownX, screen.width - dropdownW - 8);
        int py = tbY + tbH + 2;

        if (mouseX < px || mouseX > px + dropdownW || mouseY < py || mouseY > py + popupH) {
            activeDropdown = DropdownMenu.NONE;
            return false;
        }

        int idx = (int) ((mouseY - py - 2) / 18);
        if (idx < 0 || idx >= currentDropdownItems.size()) {
            return true;
        }

        DropdownItem it = currentDropdownItems.get(idx);
        if (!it.keepOpen()) {
            activeDropdown = DropdownMenu.NONE;
        }
        playClickSound();
        if (it.action() != null) {
            it.action().run();
        }
        if (it.keepOpen()) {
            buildCurrentDropdownItems();
        }
        return true;
    }

    private boolean handleLeftGroupClick(double mouseX, double mouseY) {
        if (isHovered(mouseX, mouseY, settingsBtnX, settingsBtnW)) {
            playClickSound();
            screen.openSettingsDialog();
            return true;
        }
        if (isHovered(mouseX, mouseY, pageBtnX, pageBtnW)) {
            playClickSound();
            screen.openQuickPageSwitcher();
            return true;
        }
        if (isHovered(mouseX, mouseY, searchBtnX, searchBtnW)) {
            playClickSound();
            if (screen.getSearchDialog() != null) {
                double[] center = screen.getScreenCenterCanvasPosition();
                screen.getSearchDialog().openAt(center[0], center[1]);
            }
            return true;
        }
        if (isHovered(mouseX, mouseY, optimizeBtnX, optimizeBtnW)) {
            openDropdown(DropdownMenu.OPTIMIZE, optimizeBtnX, 170);
            return true;
        }
        if (isHovered(mouseX, mouseY, viewBtnX, viewBtnW)) {
            openDropdown(DropdownMenu.VIEW, viewBtnX, 185);
            return true;
        }
        if (isHovered(mouseX, mouseY, ioBtnX, ioBtnW)) {
            openDropdown(DropdownMenu.IO, ioBtnX, 175);
            return true;
        }
        if (isHovered(mouseX, mouseY, guideBtnX, guideBtnW)) {
            openDropdown(DropdownMenu.HELP, guideBtnX, 195);
            return true;
        }
        return false;
    }

    private boolean handleRightGroupClick(double mouseX, double mouseY) {
        if (isHovered(mouseX, mouseY, closeBtnX, closeBtnW)) {
            playClickSound();
            screen.onClose();
            return true;
        }
        if (isHovered(mouseX, mouseY, redoBtnX, redoBtnW)) {
            playClickSound();
            screen.redo();
            return true;
        }
        if (isHovered(mouseX, mouseY, undoBtnX, undoBtnW)) {
            playClickSound();
            screen.undo();
            return true;
        }
        return false;
    }

    private boolean isClickInsideBar(double mouseX, double mouseY) {
        return mouseX >= tbX && mouseX <= tbX + tbW && mouseY >= tbY && mouseY <= tbY + tbH;
    }

    private void updateHoverDropdown(int mouseX, int mouseY) {
        if (isHovered(mouseX, mouseY, optimizeBtnX, optimizeBtnW)) {
            openDropdown(DropdownMenu.OPTIMIZE, optimizeBtnX, 170);
            return;
        }
        if (isHovered(mouseX, mouseY, viewBtnX, viewBtnW)) {
            openDropdown(DropdownMenu.VIEW, viewBtnX, 185);
            return;
        }
        if (isHovered(mouseX, mouseY, ioBtnX, ioBtnW)) {
            openDropdown(DropdownMenu.IO, ioBtnX, 175);
            return;
        }
        if (isHovered(mouseX, mouseY, guideBtnX, guideBtnW)) {
            openDropdown(DropdownMenu.HELP, guideBtnX, 195);
            return;
        }

        if (activeDropdown != DropdownMenu.NONE) {
            int curBtnX = getActiveDropdownButtonX();
            int curBtnW = getActiveDropdownButtonW();
            boolean overBtn = mouseX >= curBtnX - 2 && mouseX <= curBtnX + curBtnW + 2 && mouseY >= tbY - 2 && mouseY <= tbY + tbH + 3;

            int px = Math.min(dropdownX, screen.width - dropdownW - 8);
            int py = tbY + tbH + 2;
            int popupH = currentDropdownItems.size() * 18 + 4;
            boolean overPopup = mouseX >= px - 4 && mouseX <= px + dropdownW + 4 && mouseY >= py - 4 && mouseY <= py + popupH + 4;

            if (!overBtn && !overPopup) {
                activeDropdown = DropdownMenu.NONE;
            }
        }
    }

    private void openDropdown(DropdownMenu menu, int btnX, int popupW) {
        if (activeDropdown != menu) {
            activeDropdown = menu;
            dropdownX = btnX;
            dropdownW = popupW;
            buildCurrentDropdownItems();
        }
    }

    private int getActiveDropdownButtonX() {
        return switch (activeDropdown) {
            case OPTIMIZE -> optimizeBtnX;
            case VIEW -> viewBtnX;
            case IO -> ioBtnX;
            case HELP -> guideBtnX;
            default -> 0;
        };
    }

    private int getActiveDropdownButtonW() {
        return switch (activeDropdown) {
            case OPTIMIZE -> optimizeBtnW;
            case VIEW -> viewBtnW;
            case IO -> ioBtnW;
            case HELP -> guideBtnW;
            default -> 0;
        };
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    public void performAutoConnect() {
        if (!screen.ensureEditPermission()) return;
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            performAutoConnectWithFilter(screen, null);
        } else {
            screen.openAutoConnectDialog();
        }
    }

    public static void performAutoConnectWithFilter(BoardScreen screen, Set<ResourceLocation> allowedItemIds) {
        if (screen == null || !screen.ensureEditPermission()) return;
        FlowGraph graph = screen.getGraph();
        List<com.gtceu.calcboard.api.history.BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> addedEdges = autoConnect(graph, subCommands, allowedItemIds);

        if (!addedEdges.isEmpty()) {
            subCommands.add(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(
                Collections.emptyList(), addedEdges, "Auto Connect " + addedEdges.size() + " wires"
            ));
        }
        if (!subCommands.isEmpty()) {
            if (subCommands.size() == 1) {
                screen.recordCommand(subCommands.get(0));
            } else {
                screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(
                    subCommands, "Auto Connect " + addedEdges.size() + " wires"
                ));
            }
            BoardToast.show(Component.literal("§a⚡ ").append(Component.translatable("gui.gtcalcboard.toast.auto_connected", String.valueOf(addedEdges.size()))));
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
        } else {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("gui.gtcalcboard.dialog.auto_connect.no_connections")));
        }
        screen.markSummaryDirty();
    }

    public static List<FlowGraph.ConnectionEdge> autoConnect(FlowGraph graph, List<com.gtceu.calcboard.api.history.BoardCommand> subCommands) {
        return autoConnect(graph, subCommands, null);
    }

    public static List<FlowGraph.ConnectionEdge> autoConnect(FlowGraph graph, List<com.gtceu.calcboard.api.history.BoardCommand> subCommands, Set<ResourceLocation> allowedItemIds) {
        List<FlowGraph.ConnectionEdge> addedEdges = new ArrayList<>();
        if (graph == null) return addedEdges;

        for (RecipeNode from : graph.getNodes()) {
            for (int outIdx = 0; outIdx < from.getOutputs().size(); outIdx++) {
                var out = from.getOutputs().get(outIdx);
                boolean fromFeedsReroute = !from.isReroute() && isOutputFeedingReroute(graph, from.getId(), outIdx);

                for (RecipeNode to : graph.getNodes()) {
                    if (from == to) continue;
                    for (int inIdx = 0; inIdx < to.getInputs().size(); inIdx++) {
                        var in = to.getInputs().get(inIdx);

                        boolean inputAlreadyFed = isInputPortFed(graph, to.getId(), inIdx);
                        if (inputAlreadyFed) {
                            if (!out.equals(in) && !Objects.equals(out.getId(), in.getId())) {
                                continue;
                            }
                        } else {
                            if (!out.equals(in) && !in.matchesOrAlternative(out)) {
                                continue;
                            }
                        }

                        ResourceLocation itemKey = out.getId() != null ? out.getId() : in.getId();
                        if (allowedItemIds != null && (itemKey == null || !allowedItemIds.contains(itemKey))) {
                            continue;
                        }

                        // Check if a path already exists (directly or via intermediate reroute junctions)
                        if (isPortConnected(graph, from.getId(), outIdx, to.getId(), inIdx)) {
                            continue;
                        }

                        // If this output is already routed to a junction hub, do not create direct bypass wires to normal nodes
                        if (fromFeedsReroute && !to.isReroute()) {
                            continue;
                        }

                        // Prevent creating cyclic dependencies
                        if (isReachable(graph, to.getId(), from.getId())) {
                            continue;
                        }

                        // If target input is already fed by a reroute junction, route into that junction instead of bypassing it
                        RecipeNode targetNode = to;
                        int targetInIdx = inIdx;
                        if (!from.isReroute() && !to.isReroute()) {
                            RecipeNode feedingReroute = findFeedingRerouteNode(graph, to.getId(), inIdx);
                            if (feedingReroute != null) {
                                targetNode = feedingReroute;
                                targetInIdx = 0;
                                if (isPortConnected(graph, from.getId(), outIdx, targetNode.getId(), targetInIdx)) {
                                    continue;
                                }
                            }
                        }

                        if (!inputAlreadyFed && !out.equals(in) && in.hasAlternatives() && subCommands != null) {
                            ResourceLocation oldAlt = in.getId();
                            in.selectAlternative(out.getId());
                            ResourceLocation newAlt = in.getId();
                            if (!Objects.equals(oldAlt, newAlt)) {
                                subCommands.add(new com.gtceu.calcboard.api.history.BoardCommand.SelectAlternativeCommand(
                                    to.getId(), inIdx, true, oldAlt, newAlt
                                ));
                            }
                        }

                        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(from.getId(), outIdx, targetNode.getId(), targetInIdx);
                        if (!graph.getConnections().contains(edge)) {
                            graph.addConnection(from.getId(), outIdx, targetNode.getId(), targetInIdx);
                            addedEdges.add(edge);
                        }
                    }
                }
            }
        }
        return addedEdges;
    }

    private static boolean isPortConnected(FlowGraph graph, String fromNodeId, int outIdx, String toNodeId, int inIdx) {
        if (fromNodeId.equals(toNodeId)) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(fromNodeId) && edge.outputIndex() == outIdx) {
                if (edge.toNodeId().equals(toNodeId) && edge.inputIndex() == inIdx) {
                    return true;
                }
                RecipeNode target = graph.getNode(edge.toNodeId());
                if (target != null && target.isReroute()) {
                    if (visited.add(target.getId())) {
                        queue.add(target.getId());
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            String currRerouteId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(currRerouteId)) {
                    if (edge.toNodeId().equals(toNodeId) && edge.inputIndex() == inIdx) {
                        return true;
                    }
                    RecipeNode target = graph.getNode(edge.toNodeId());
                    if (target != null && target.isReroute()) {
                        if (visited.add(target.getId())) {
                            queue.add(target.getId());
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isOutputFeedingReroute(FlowGraph graph, String fromNodeId, int outIdx) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(fromNodeId) && edge.outputIndex() == outIdx) {
                RecipeNode target = graph.getNode(edge.toNodeId());
                if (target != null && target.isReroute()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RecipeNode findFeedingRerouteNode(FlowGraph graph, String toNodeId, int inIdx) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(toNodeId) && edge.inputIndex() == inIdx) {
                RecipeNode src = graph.getNode(edge.fromNodeId());
                if (src != null && src.isReroute()) {
                    return src;
                }
            }
        }
        return null;
    }

    private static boolean isInputPortFed(FlowGraph graph, String toNodeId, int inIdx) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(toNodeId) && edge.inputIndex() == inIdx) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReachable(FlowGraph graph, String startNodeId, String targetNodeId) {
        if (startNodeId.equals(targetNodeId)) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        visited.add(startNodeId);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(curr)) {
                    if (edge.toNodeId().equals(targetNodeId)) {
                        return true;
                    }
                    if (visited.add(edge.toNodeId())) {
                        queue.add(edge.toNodeId());
                    }
                }
            }
        }
        return false;
    }

    public void performAutoRatio() {
        boolean isShift = Screen.hasShiftDown();
        boolean isAlt = Screen.hasAltDown();
        boolean isFractionalDefault = BoardManager.getInstance().isAutoRatioFractionalDefault();
        if (isAlt) {
            performAutoRatio(false, true);
        } else if (isShift) {
            performAutoRatio(true, false);
        } else {
            performAutoRatio(false, isFractionalDefault);
        }
    }

    public void performAutoRatio(boolean harmonized) {
        performAutoRatio(harmonized, false);
    }

    public void performAutoRatio(boolean harmonized, boolean fractional) {
        if (!screen.ensureEditPermission()) return;
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = findAnchorNode(graph);

        Map<String, Double> oldCounts = captureMachineCounts(graph);
        executeAutoRatioAlgorithm(graph, baseNode, harmonized, fractional);
        recordAutoRatioHistory(graph, baseNode, oldCounts, harmonized, fractional);

        refreshWidgetsAfterAutoRatio();
        notifyAutoRatioResult(baseNode, harmonized, fractional);
    }

    private RecipeNode findAnchorNode(FlowGraph graph) {
        if (graph == null) return null;
        RecipeNode base = graph.findBaseNode();
        if (base == null && !graph.getNodes().isEmpty()) {
            return graph.getNodes().get(0);
        }
        return base;
    }

    private Map<String, Double> captureMachineCounts(FlowGraph graph) {
        Map<String, Double> counts = new HashMap<>();
        if (graph == null) return counts;
        for (RecipeNode n : graph.getNodes()) {
            counts.put(n.getId(), n.getMachineCount());
        }
        return counts;
    }

    private void executeAutoRatioAlgorithm(FlowGraph graph, RecipeNode baseNode, boolean harmonized, boolean fractional) {
        if (graph == null || baseNode == null) return;
        if (harmonized) {
            graph.autoRatioHarmonized(baseNode);
        } else if (fractional) {
            graph.autoRatioFractional(baseNode);
        } else {
            graph.autoRatioFromAnchor(baseNode);
        }
    }

    private void recordAutoRatioHistory(FlowGraph graph, RecipeNode baseNode, Map<String, Double> oldCounts, boolean harmonized, boolean fractional) {
        if (graph == null) return;
        List<com.gtceu.calcboard.api.history.BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            double oldC = oldCounts.getOrDefault(n.getId(), 1.0);
            double newC = n.getMachineCount();
            if (Math.abs(oldC - newC) > 0.0001) {
                subCmds.add(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(n.getId(), oldC, newC));
            }
        }
        if (subCmds.isEmpty()) return;

        String baseName = baseNode != null ? baseNode.getName() : "Graph";
        String actionName = harmonized ? "Harmonized Auto Ratio (" + baseName + ")"
                : (fractional ? "Fractional Auto Ratio (" + baseName + ")" : "Auto Ratio (" + baseName + ")");
        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(subCmds, actionName));
    }

    private void refreshWidgetsAfterAutoRatio() {
        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
            w.invalidateCache();
        }
        screen.markSummaryDirty();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onAutoRatioTriggered();
    }

    private void notifyAutoRatioResult(RecipeNode baseNode, boolean harmonized, boolean fractional) {
        String baseName = baseNode != null ? baseNode.getName() : "Graph";
        if (harmonized && baseNode != null) {
            BoardToast.show(Component.literal("§6✨ ").append(Component.translatable("message.gtcalcboard.auto_ratio_harmonized", baseName, (int) baseNode.getMachineCount())));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
            return;
        }
        if (fractional && baseNode != null) {
            BoardToast.show(Component.literal("§b⚡ ").append(Component.translatable("message.gtcalcboard.auto_ratio_fractional", baseName)));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            return;
        }
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.auto_ratio_matched", baseName)));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
    }

    public void performMaxThroughputOptimization() {
        if (!screen.ensureEditPermission()) return;
        runMaxFlow();
        BoardToast.show(Component.literal("§6▲ ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", "MAX")));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
    }

    private void runMaxFlow() {
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = graph.findBaseNode();
        if (baseNode == null && !graph.getNodes().isEmpty()) {
            baseNode = graph.getNodes().get(0);
        }

        // Snapshot existing settings
        Map<String, Object[]> oldProps = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            oldProps.put(n.getId(), new Object[]{n.getTargetTier(), n.getOverclockMode(), n.getParallel(), n.getMachineCount()});
        }

        // 1. Maximize Overclock (up to MAX)
        for (RecipeNode n : graph.getNodes()) {
            com.gtceu.calcboard.api.type.GTVoltageTier baseTier = n.getRecipeTier();
            com.gtceu.calcboard.api.type.GTVoltageTier targetTier = com.gtceu.calcboard.api.type.GTVoltageTier.MAX;
            if (targetTier.ordinal() < baseTier.ordinal()) {
                targetTier = baseTier;
            }
            n.setTargetTier(targetTier);
        }

        // 2. Propagate auto ratio from anchor
        if (baseNode != null) {
            graph.autoRatioFromAnchor(baseNode);
        }

        List<com.gtceu.calcboard.api.history.BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            Object[] oldP = oldProps.get(n.getId());
            if (oldP != null) {
                if (oldP[0] != n.getTargetTier()) {
                    subCmds.add(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(n.getId(), (com.gtceu.calcboard.api.type.GTVoltageTier) oldP[0], n.getTargetTier()));
                }
                if (oldP[1] != n.getOverclockMode()) {
                    subCmds.add(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.overclockMode(n.getId(), (com.gtceu.calcboard.api.type.OverclockMode) oldP[1], n.getOverclockMode()));
                }
                if (!oldP[2].equals(n.getParallel())) {
                    subCmds.add(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.parallel(n.getId(), (Integer) oldP[2], n.getParallel()));
                }
                if (Math.abs(((Double) oldP[3]) - n.getMachineCount()) > 0.0001) {
                    subCmds.add(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(n.getId(), (Double) oldP[3], n.getMachineCount()));
                }
            }
        }
        if (!subCmds.isEmpty()) {
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(subCmds, "Max Flow Optimization"));
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
            w.invalidateCache();
        }
        screen.markSummaryDirty();

        BoardToast.show(Component.literal("§6▲ ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", "MAX")));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
    }

    public void performGroupIntoModule() {
        FlowGraph graph = screen.getGraph();
        var selectedIds = screen.getSelectedNodeIds();
        boolean hasSpecificSelection = selectedIds != null && selectedIds.size() >= 2;

        if (!hasSpecificSelection && graph.getNodes().size() < 2) {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.group_min_nodes")));
            return;
        }

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        String defaultModuleName = Component.translatable("gui.gtcalcboard.default_compound_name").getString();
        RecipeNode moduleNode = hasSpecificSelection 
            ? graph.groupIntoModule(selectedIds, defaultModuleName)
            : graph.groupIntoModule(defaultModuleName);

        if (moduleNode != null) {
            List<RecipeNode> groupedNodes = new ArrayList<>();
            for (RecipeNode n : origNodes) {
                if (!graph.getNodes().contains(n)) {
                    groupedNodes.add(n);
                }
            }
            List<FlowGraph.ConnectionEdge> rewires = new ArrayList<>(graph.getConnections());
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.GroupModuleCommand(groupedNodes, moduleNode, origEdges, rewires));

            screen.clearSelection();
            screen.rebuildWidgets();
            screen.markSummaryDirty();
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onModuleGrouped();

            BoardToast.show(Component.literal("§d▦ ").append(Component.translatable("message.gtcalcboard.group_success", String.valueOf(moduleNode.getContainedMachineCount()))));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2F));
        }
    }

    public void copyBlueprintToClipboard() {
        if (screen.getExportBlueprintDialog() != null) {
            screen.getExportBlueprintDialog().open();
        }
    }

    public void importBlueprintFromClipboard() {
        Minecraft mc = Minecraft.getInstance();
        String clip = mc.keyboardHandler.getClipboard();
        if (clip != null && !clip.trim().isEmpty()) {
            com.gtceu.calcboard.api.storage.BlueprintPackage pkg = BlueprintCodec.importPackageFromString(clip);
            if (pkg != null && screen.getImportBlueprintDialog() != null) {
                screen.getImportBlueprintDialog().open(pkg);
                return;
            }
        }

        if (screen.getDiskBlueprintsDialog() != null) {
            screen.getDiskBlueprintsDialog().open();
        } else {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.clipboard_empty")));
        }
    }
}




