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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

/**
 * Top horizontal toolbar widget managing quick access buttons, layout modes, tools, and scrollable action bars.
 */
public class ToolbarWidget {
    private static final int MINIMAP_PADDING = 16;
    private final BoardScreen screen;

    private boolean overflowMenuOpen = false;
    private final List<ToolbarButtonDef> visibleButtons = new ArrayList<>();
    private final List<ToolbarButtonDef> overflowButtons = new ArrayList<>();
    private int overflowBtnX = 0;
    private int overflowBtnY = 0;
    private int overflowBtnW = 20;
    private boolean isCompactMode = false;
    private int lastMenuX = 0;
    private int lastMenuY = 0;
    private int lastMenuW = 160;
    private int lastMenuH = 0;
    private ToolbarButtonDef hoveredBtn = null;
    private boolean isTitleHovered = false;
    private int cachedTbX = 0;
    private int cachedTbY = 0;
    private int cachedTitleRight = 0;
    private int cachedActualW = 0;

    public ToolbarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean isOverflowMenuOpen() {
        return overflowMenuOpen;
    }

    private static class ToolbarButtonDef {
        final String id;
        final String text;
        final String icon;
        final int color;
        final int bg;
        final int hoverBg;
        final int border;
        final int width;
        final int compactWidth;
        final java.util.function.Consumer<Integer> onClick;

        ToolbarButtonDef(String id, String text, String icon, int color, int bg, int hoverBg, int border, int width, int compactWidth, java.util.function.Consumer<Integer> onClick) {
            this.id = id;
            this.text = text;
            this.icon = icon;
            this.color = color;
            this.bg = bg;
            this.hoverBg = hoverBg;
            this.border = border;
            this.width = width;
            this.compactWidth = compactWidth;
            this.onClick = onClick;
        }
    }

    private List<ToolbarButtonDef> buildButtons(Font font) {
        List<ToolbarButtonDef> list = new ArrayList<>();
        boolean isShift = Screen.hasShiftDown();

        addGuideAndTutorialButtons(list, font, isShift);
        addOptimizationButtons(list, font, isShift);
        addUtilityAndToggleButtons(list, font);
        addIoButtons(list, font);
        addTeamCollaborationButtons(list, font);
        addSystemButtons(list, font);

        return list;
    }

    private void addGuideAndTutorialButtons(List<ToolbarButtonDef> list, Font font, boolean isShift) {
        BoardManager bm = BoardManager.getInstance();
        if (bm.isShowGuideButton()) {
            String guideTxt = "§e? " + Component.translatable("gui.gtcalcboard.guide_btn").getString();
            list.add(new ToolbarButtonDef("guide", guideTxt, "?", 0xFFFFEE55, 0xFF352E1B, 0xFF5A4A28, 0xFF776433, font.width(guideTxt) + 12, 22, btn -> {
                if (screen.getGuideDialog() != null) {
                    screen.getGuideDialog().open();
                }
            }));
        }

        if (bm.isShowTutorialButton()) {
            if (isShift) {
                String advTutTxt = "§b✦ " + Component.translatable("gui.gtcalcboard.advanced_tutorial_btn").getString();
                list.add(new ToolbarButtonDef("tutorial", advTutTxt, "✦", 0xFF38BDF8, 0xFF0C4A6E, 0xFF075985, 0xFF0284C7, font.width(advTutTxt) + 12, 22, btn -> {
                    com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().startAdvancedTutorial(screen);
                }));
            } else {
                String tutTxt = "§a▶ " + Component.translatable("gui.gtcalcboard.tutorial_btn").getString();
                list.add(new ToolbarButtonDef("tutorial", tutTxt, "▶", 0xFF55FF88, 0xFF1C3524, 0xFF2A5A38, 0xFF3B774E, font.width(tutTxt) + 12, 22, btn -> {
                    com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().startTutorial(screen);
                }));
            }
        }
    }

    private void addOptimizationButtons(List<ToolbarButtonDef> list, Font font, boolean isShift) {
        if (isShift) {
            String quickConnTxt = "§e⚡ " + Component.translatable("gui.gtcalcboard.quick_connect").getString();
            list.add(new ToolbarButtonDef("auto_connect", quickConnTxt, "⚡", 0xFFFFF176, 0xFF3A351C, 0xFF5C5228, 0xFF887733, font.width(quickConnTxt) + 12, 22, btn -> performAutoConnect()));
        } else {
            String connTxt = "↔ " + Component.translatable("gui.gtcalcboard.auto_connect").getString();
            list.add(new ToolbarButtonDef("auto_connect", connTxt, "↔", 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(connTxt) + 12, 22, btn -> performAutoConnect()));
        }

        if (isShift) {
            String harmonizeTxt = "§6✧ " + Component.translatable("gui.gtcalcboard.harmonize_ratio").getString();
            list.add(new ToolbarButtonDef("auto_ratio", harmonizeTxt, "✧", 0xFFFFD700, 0xFF3D2A1C, 0xFF634226, 0xFFA66D38, font.width(harmonizeTxt) + 12, 22, btn -> performAutoRatio(true)));
        } else {
            String ratioTxt = "⚖ " + Component.translatable("gui.gtcalcboard.auto_ratio").getString();
            list.add(new ToolbarButtonDef("auto_ratio", ratioTxt, "⚖", 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(ratioTxt) + 12, 22, btn -> performAutoRatio(false)));
        }

        String flowTxt = "▲ " + Component.translatable("gui.gtcalcboard.max_flow").getString();
        list.add(new ToolbarButtonDef("max_flow", flowTxt, "▲", 0xFFFFAA00, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(flowTxt) + 12, 22, btn -> performMaxThroughputOptimization()));

        String fitTxt = "⌖ " + Component.translatable("gui.gtcalcboard.fit_view").getString();
        list.add(new ToolbarButtonDef("fit_view", fitTxt, "⌖", 0xFF38BDF8, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(fitTxt) + 12, 22, btn -> screen.fitToView()));
    }

    private void addUtilityAndToggleButtons(List<ToolbarButtonDef> list, Font font) {
        BoardManager bm = BoardManager.getInstance();
        String balanceTxt = "§b§l∑ " + Component.translatable("gui.gtcalcboard.global_balance").getString();
        list.add(new ToolbarButtonDef("global_balance", balanceTxt, "∑", 0xFF66E5FF, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(balanceTxt) + 12, 22, btn -> {
            if (screen.getGlobalBalanceDialog() != null) {
                screen.getGlobalBalanceDialog().open();
            }
        }));

        if (com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported() && bm.isShowMultiblockBomButton()) {
            String bomTxt = "§6▦ " + Component.translatable("gui.gtcalcboard.bom").getString();
            list.add(new ToolbarButtonDef("multiblock_bom", bomTxt, "▦", 0xFFFFCC66, 0xFF352B1C, 0xFF4D3D28, 0xFF665035, font.width(bomTxt) + 12, 22, btn -> {
                if (screen.getMultiblockBOMDialog() != null) {
                    screen.getMultiblockBOMDialog().open();
                }
            }));
        }

        if (bm.isShowTimeUnitButton()) {
            com.gtceu.calcboard.api.type.RateTimeUnit curUnit = FormatUtil.getActiveTimeUnit();
            String unitTxt = "§e⏱ " + curUnit.getSuffix() + " ▼";
            String compactUnit = "⏱" + curUnit.getSuffix();
            int compactW = font.width(compactUnit) + 8;
            list.add(new ToolbarButtonDef("time_unit", unitTxt, compactUnit, 0xFFFFF176, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(unitTxt) + 12, compactW, btn -> {
                com.gtceu.calcboard.api.type.RateTimeUnit next = curUnit.next();
                FormatUtil.setActiveTimeUnit(next);
                BoardManager.getInstance().setTimeUnit(next);
                BoardManager.getInstance().saveForCurrentContext();
                BoardToast.show(Component.literal("§e⏱ ").append(
                    Component.translatable("gui.gtcalcboard.toast.time_unit_changed", next.getSuffix(), Component.translatable(next.getTranslationKey()).getString())
                ));
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                screen.markSummaryDirty();
            }));
        }

        if (bm.isShowFluidUnitButton()) {
            com.gtceu.calcboard.api.type.FluidUnitMode curFluidMode = FormatUtil.getActiveFluidUnitMode();
            String fluidTxt = "§b~ " + curFluidMode.getLabel() + " ▼";
            String compactFluid = "~" + curFluidMode.getLabel();
            int compactW = font.width(compactFluid) + 8;
            list.add(new ToolbarButtonDef("fluid_unit", fluidTxt, compactFluid, 0xFF66E5FF, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(fluidTxt) + 12, compactW, btn -> {
                com.gtceu.calcboard.api.type.FluidUnitMode next = curFluidMode.next();
                FormatUtil.setActiveFluidUnitMode(next);
                BoardManager.getInstance().setFluidUnitMode(next);
                BoardManager.getInstance().saveForCurrentContext();
                BoardToast.show(Component.literal("§b~ ").append(
                    Component.translatable("gui.gtcalcboard.toast.fluid_unit_changed", Component.translatable(next.getTranslationKey()).getString())
                ));
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                screen.markSummaryDirty();
            }));
        }

        if (com.gtceu.calcboard.api.util.ModCompatHelper.isAe2Loaded()) {
            BoardPage activePage = bm.getActivePage();
            boolean isBound = activePage != null && com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry.getInstance().isPageBound(activePage.getId());
            String ae2Txt = isBound ? "§b⚡ " + Component.translatable("gui.gtcalcboard.ae2.btn_bound").getString()
                                    : "§7⚡ " + Component.translatable("gui.gtcalcboard.ae2.btn_bind").getString();
            int ae2Bg = isBound ? 0xFF0C4A6E : 0xFF282E3B;
            int ae2Hover = isBound ? 0xFF075985 : 0xFF3E475A;
            int ae2Border = isBound ? 0xFF0284C7 : 0xFF3D4455;
            list.add(new ToolbarButtonDef("ae2_bind", ae2Txt, "⚡", isBound ? 0xFF38BDF8 : 0xFFCCCCCC, ae2Bg, ae2Hover, ae2Border, font.width(ae2Txt) + 12, 22, btn -> {
                if (screen.getPatternBindingDialog() != null) {
                    screen.getPatternBindingDialog().open(activePage);
                }
            }));
        }
    }

    private void addIoButtons(List<ToolbarButtonDef> list, Font font) {
        String shareTxt = "» " + Component.translatable("gui.gtcalcboard.export").getString();
        list.add(new ToolbarButtonDef("export", shareTxt, "»", 0xFF66DDFF, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(shareTxt) + 12, 22, btn -> copyBlueprintToClipboard()));

        String importTxt = "« " + Component.translatable("gui.gtcalcboard.import").getString();
        list.add(new ToolbarButtonDef("import", importTxt, "«", 0xFF66FF88, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(importTxt) + 12, 22, btn -> importBlueprintFromClipboard()));
    }

    private void addTeamCollaborationButtons(List<ToolbarButtonDef> list, Font font) {
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (state.isTeamMode()) {
            String activePageId = state.getActiveTeamPageId();
            boolean hasLock = state.doesHoldLock(activePageId);

            if (hasLock) {
                String cancelTxt = "§c✕ " + Component.translatable("gui.gtcalcboard.btn_cancel_edit").getString();
                list.add(new ToolbarButtonDef("cancel_edit", cancelTxt, "✕", 0xFFFF6B6B, 0xFF3D1C1C, 0xFF5A2A2A, 0xFF773B3B, font.width(cancelTxt) + 12, 22, btn -> {
                    state.autoCommitAndRelease(screen, activePageId);
                    screen.rebuildWidgets();
                    screen.markSummaryDirty();
                }));
            }

            String copyTxt = "§b» " + Component.translatable("gui.gtcalcboard.btn_copy_to_personal").getString();
            list.add(new ToolbarButtonDef("copy_to_personal", copyTxt, "»", 0xFF66DDFF, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(copyTxt) + 12, 22, btn -> {
                BoardManager bmInstance = BoardManager.getInstance();
                com.gtceu.calcboard.server.storage.TeamWorkspacePage remotePage = state.getRemotePage(activePageId);
                String pageTitle = (remotePage != null && remotePage.getTitle() != null && !remotePage.getTitle().trim().isEmpty())
                    ? remotePage.getTitle() : "Team Page";
                com.gtceu.calcboard.api.storage.BoardPage newPage = new com.gtceu.calcboard.api.storage.BoardPage(pageTitle);
                FlowGraph copiedGraph = screen.getGraph().copy();
                for (RecipeNode n : copiedGraph.getNodes()) {
                    newPage.getGraph().addNode(n);
                }
                for (FlowGraph.ConnectionEdge e : copiedGraph.getConnections()) {
                    newPage.getGraph().getConnections().add(e);
                }
                bmInstance.addPage(newPage);
                bmInstance.setActivePageIndex(bmInstance.getPages().size() - 1);
                state.setCurrentMode(com.gtceu.calcboard.client.team.ClientWorkspaceState.WorkspaceMode.LOCAL);
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                BoardToast.show("gui.gtcalcboard.toast.copied_to_personal", pageTitle);
            }));

            String historyTxt = "§f⟲ " + Component.translatable("gui.gtcalcboard.btn_recent_saves").getString();
            list.add(new ToolbarButtonDef("recent_saves", historyTxt, "⟲", 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(historyTxt) + 12, 22, btn -> {
                if (screen.getRecentSavesDialog() != null) {
                    screen.getRecentSavesDialog().open();
                }
            }));
        } else if (state.isCollaborationEnabled()) {
            String exportTeamTxt = "§a» " + Component.translatable("gui.gtcalcboard.btn_export_to_team").getString();
            list.add(new ToolbarButtonDef("export_to_team", exportTeamTxt, "»", 0xFF55FF88, 0xFF1C3D26, 0xFF2A5A38, 0xFF3B774E, font.width(exportTeamTxt) + 12, 22, btn -> {
                if (screen.getExportToTeamDialog() != null) {
                    screen.getExportToTeamDialog().open();
                }
            }));
        }
    }

    private void addSystemButtons(List<ToolbarButtonDef> list, Font font) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            boolean isPaused = BoardManager.getInstance().isPauseGameInSingleplayer();
            String pauseTxt = isPaused
                    ? "§b⏸ " + Component.translatable("gui.gtcalcboard.btn_pause_on").getString()
                    : "§7▶ " + Component.translatable("gui.gtcalcboard.btn_pause_off").getString();
            int pauseBg = isPaused ? 0xFF1C2C44 : 0xFF222630;
            int pauseBorder = isPaused ? 0xFF355580 : 0xFF3D4455;
            list.add(new ToolbarButtonDef("pause_toggle", pauseTxt, isPaused ? "⏸" : "▶", isPaused ? 0xFF66DDFF : 0xFFAAAAAA, pauseBg, pauseBg + 0x00151515, pauseBorder, font.width(pauseTxt) + 12, 22, btn -> {
                boolean nextVal = !BoardManager.getInstance().isPauseGameInSingleplayer();
                BoardManager.getInstance().setPauseGameInSingleplayer(nextVal);
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                String statusStr = nextVal ? "ON" : "OFF";
                BoardToast.show(Component.literal("§e⚙ ").append(Component.translatable("gui.gtcalcboard.toast.pause_toggle_hint", statusStr)));
            }));
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        Font font = Minecraft.getInstance().font;
        int width = screen.width;

        int tbX = screen.getDynamicLeftMargin();
        int tbY = screen.getToolbarY();
        int tbH = 18;
        int tbW = Math.max(120, width - tbX - 8 - MINIMAP_PADDING);

        // Adaptive Title Text
        String titleStr;
        if (width >= 560) {
            titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString() + " §7⚙";
        } else if (width >= 420) {
            titleStr = "§6" + Component.translatable("gui.gtcalcboard.title_short").getString() + " §7⚙";
        } else {
            titleStr = "§6⚙ §7⚙";
        }
        int titleRight = tbX + 6 + font.width(titleStr) + 8;

        this.cachedTbX = tbX;
        this.cachedTbY = tbY;
        this.cachedTitleRight = titleRight;

        // Layout mode determination
        ToolbarDisplayMode prefMode = BoardManager.getInstance().getToolbarDisplayMode();
        List<ToolbarButtonDef> allButtons = buildButtons(font);
        int totalFullW = 0;
        for (ToolbarButtonDef btn : allButtons) {
            totalFullW += btn.width + 3;
        }
        int availBtnAreaW = Math.max(0, (tbX + tbW) - titleRight - 4);

        if (prefMode == ToolbarDisplayMode.FULL) {
            this.isCompactMode = false;
        } else if (prefMode == ToolbarDisplayMode.COMPACT) {
            this.isCompactMode = true;
        } else {
            this.isCompactMode = (totalFullW > availBtnAreaW);
        }

        visibleButtons.clear();
        overflowButtons.clear();

        int totalRequiredW = 0;
        for (ToolbarButtonDef btn : allButtons) {
            totalRequiredW += (isCompactMode ? btn.compactWidth : btn.width) + 3;
        }

        if (totalRequiredW <= availBtnAreaW) {
            visibleButtons.addAll(allButtons);
        } else {
            int budget = availBtnAreaW - overflowBtnW - 4;
            int currentUsed = 0;
            for (ToolbarButtonDef btn : allButtons) {
                int w = (isCompactMode ? btn.compactWidth : btn.width) + 3;
                if (currentUsed + w <= budget) {
                    visibleButtons.add(btn);
                    currentUsed += w;
                } else {
                    overflowButtons.add(btn);
                }
            }
        }

        int visibleButtonsW = 0;
        for (ToolbarButtonDef btn : visibleButtons) {
            visibleButtonsW += (isCompactMode ? btn.compactWidth : btn.width) + 3;
        }
        if (!overflowButtons.isEmpty()) {
            visibleButtonsW += overflowBtnW + 3;
        }
        int actualToolbarW = Math.min(tbW, (titleRight - tbX) + visibleButtonsW + 4);
        this.cachedActualW = actualToolbarW;

        // Toolbar background
        graphics.fill(tbX, tbY, tbX + actualToolbarW, tbY + tbH, 0xEE1E222B);
        graphics.renderOutline(tbX, tbY, actualToolbarW, tbH, 0xFF3D4455);

        // Fixed clickable title & settings gear on the left
        isTitleHovered = mouseX >= tbX + 2 && mouseX <= titleRight - 2 && mouseY >= tbY && mouseY <= tbY + tbH;
        if (isTitleHovered) {
            graphics.fill(tbX + 2, tbY + 1, titleRight - 2, tbY + tbH - 1, 0xFF2A364D);
            graphics.renderOutline(tbX + 2, tbY + 1, (titleRight - 4) - tbX, tbH - 2, 0xFF5B9BD5);
            graphics.drawString(font, titleStr.replace("§7", "§b"), tbX + 6, tbY + 5, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, titleStr, tbX + 6, tbY + 5, 0xFFFFFFFF, false);
        }

        // Render visible buttons
        hoveredBtn = null;
        int curX = titleRight;
        for (ToolbarButtonDef btn : visibleButtons) {
            int bw = isCompactMode ? btn.compactWidth : btn.width;
            String label = isCompactMode ? btn.icon : btn.text;
            boolean isGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isToolbarButtonGlowing(btn.id);
            drawBtn(graphics, font, label, curX, tbY + 1, bw, 16, mouseX, mouseY, btn.color, btn.bg, btn.hoverBg, btn.border, isGlowing);
            if (mouseX >= curX && mouseX <= curX + bw && mouseY >= tbY + 1 && mouseY <= tbY + 17) {
                hoveredBtn = btn;
            }
            curX += bw + 3;
        }

        // Render overflow button [···] if some buttons didn't fit
        boolean overflowHover = false;
        if (!overflowButtons.isEmpty()) {
            overflowBtnX = curX;
            overflowBtnY = tbY + 1;
            overflowHover = mouseX >= overflowBtnX && mouseX <= overflowBtnX + overflowBtnW && mouseY >= overflowBtnY && mouseY <= overflowBtnY + 16;
            int ofBg = overflowMenuOpen ? 0xFF3D4A66 : (overflowHover ? 0xFF353D4E : 0xFF282E3B);
            int ofBorder = overflowMenuOpen ? 0xFF5B9BD5 : (overflowHover ? 0xFF4F5B73 : 0xFF3D4455);
            drawBtn(graphics, font, "···", overflowBtnX, overflowBtnY, overflowBtnW, 16, mouseX, mouseY, 0xFFE0E0E0, ofBg, ofBg, ofBorder, false);
        }

        // Render floating dropdown popup menu if open
        if (overflowMenuOpen && !overflowButtons.isEmpty()) {
            lastMenuW = 160;
            int rowH = 18;
            lastMenuH = overflowButtons.size() * rowH + 4;
            lastMenuX = Math.max(8, Math.min(overflowBtnX, width - lastMenuW - 8));
            lastMenuY = tbY + tbH + 2;

            graphics.fill(lastMenuX, lastMenuY, lastMenuX + lastMenuW, lastMenuY + lastMenuH, 0xF5141720);
            graphics.renderOutline(lastMenuX, lastMenuY, lastMenuW, lastMenuH, 0xFF4A556B);

            for (int i = 0; i < overflowButtons.size(); i++) {
                ToolbarButtonDef btn = overflowButtons.get(i);
                int rowY = lastMenuY + 2 + i * rowH;
                boolean rowHover = mouseX >= lastMenuX + 2 && mouseX <= lastMenuX + lastMenuW - 2 && mouseY >= rowY && mouseY <= rowY + rowH;
                if (rowHover) {
                    graphics.fill(lastMenuX + 2, rowY, lastMenuX + lastMenuW - 2, rowY + rowH, 0xFF2A364D);
                }
                graphics.drawString(font, btn.text, lastMenuX + 6, rowY + 5, rowHover ? 0xFFFFFFFF : btn.color, false);
            }
        }

        graphics.pose().popPose();

        // Tooltips
        if (hoveredBtn != null) {
            String tooltipKey = "gui.gtcalcboard.tooltip.btn_" + hoveredBtn.id;
            String raw = Component.translatable(tooltipKey).getString();
            if (raw.contains("\n")) {
                List<Component> lines = Arrays.stream(raw.split("\n"))
                        .<Component>map(Component::literal)
                        .toList();
                BoardTooltipRenderer.renderComponentTooltip(graphics, font, lines, mouseX, mouseY, screen.width, screen.height);
            } else {
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable(tooltipKey), mouseX, mouseY, screen.width, screen.height);
            }
        } else if (isTitleHovered) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.tooltip.open_settings"), mouseX, mouseY, screen.width, screen.height);
        } else if (overflowHover && !overflowMenuOpen) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.toolbar.more"), mouseX, mouseY, screen.width, screen.height);
        }
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int defaultTextColor, int bg, int hoverBg, int border, boolean isGlowing) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

        if (isGlowing) {
            float glowTime = (System.currentTimeMillis() % 1500) / 1500.0f;
            float glowAlpha = 0.5f + 0.5f * (float) Math.sin(glowTime * Math.PI * 2);
            int glowColor = (int) (glowAlpha * 255) << 24 | 0x00FF88;
            graphics.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, glowColor & 0x4400FF88);
            graphics.renderOutline(bx - 1, by - 1, bw + 2, bh + 2, glowColor);
        }

        graphics.fill(bx, by, bx + bw, by + bh, hover ? hoverBg : bg);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF5B9BD5 : border);

        int strW = font.width(text);
        int tx = bx + (bw - strW) / 2;
        int ty = by + (bh - 8) / 2;
        graphics.drawString(font, text, tx, ty, (hover || isGlowing) ? 0xFFFFFFFF : defaultTextColor, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. If overflow menu is open, handle clicks inside the popup
        if (overflowMenuOpen && !overflowButtons.isEmpty()) {
            if (mouseX >= lastMenuX && mouseX <= lastMenuX + lastMenuW && mouseY >= lastMenuY && mouseY <= lastMenuY + lastMenuH) {
                int idx = (int) ((mouseY - lastMenuY - 2) / 18);
                if (idx >= 0 && idx < overflowButtons.size()) {
                    ToolbarButtonDef clicked = overflowButtons.get(idx);
                    overflowMenuOpen = false;
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                    );
                    clicked.onClick.accept(button);
                    return true;
                }
            } else if (mouseX >= overflowBtnX && mouseX <= overflowBtnX + overflowBtnW && mouseY >= overflowBtnY && mouseY <= overflowBtnY + 16) {
                overflowMenuOpen = false;
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                return true;
            } else {
                overflowMenuOpen = false;
            }
        }

        // 2. Check overflow button click
        if (!overflowButtons.isEmpty() && mouseX >= overflowBtnX && mouseX <= overflowBtnX + overflowBtnW && mouseY >= overflowBtnY && mouseY <= overflowBtnY + 16) {
            overflowMenuOpen = !overflowMenuOpen;
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }

        // 3. Check title / settings click
        if (mouseX >= cachedTbX + 2 && mouseX <= cachedTitleRight - 2 && mouseY >= cachedTbY && mouseY <= cachedTbY + 18) {
            screen.openSettingsDialog();
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }

        // 4. Check visible buttons click
        int curX = cachedTitleRight;
        for (ToolbarButtonDef btn : visibleButtons) {
            int bw = isCompactMode ? btn.compactWidth : btn.width;
            if (mouseX >= curX && mouseX <= curX + bw && mouseY >= cachedTbY + 1 && mouseY <= cachedTbY + 17) {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                btn.onClick.accept(button);
                return true;
            }
            curX += bw + 3;
        }

        // 5. Absorb click within toolbar bounds
        if (mouseX >= cachedTbX && mouseX <= cachedTbX + cachedActualW && mouseY >= cachedTbY && mouseY <= cachedTbY + 18) {
            return true;
        }

        return false;
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
        performAutoRatio(Screen.hasShiftDown());
    }

    public void performAutoRatio(boolean harmonized) {
        if (!screen.ensureEditPermission()) return;
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = graph.findBaseNode();
        if (baseNode == null && !graph.getNodes().isEmpty()) {
            baseNode = graph.getNodes().get(0);
        }

        Map<String, Double> oldCounts = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            oldCounts.put(n.getId(), n.getMachineCount());
        }

        if (baseNode != null) {
            if (harmonized) {
                graph.autoRatioHarmonized(baseNode);
            } else {
                graph.autoRatioFromAnchor(baseNode);
            }
        }

        List<com.gtceu.calcboard.api.history.BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            double oldC = oldCounts.getOrDefault(n.getId(), 1.0);
            double newC = n.getMachineCount();
            if (Math.abs(oldC - newC) > 0.0001) {
                subCmds.add(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(
                    n.getId(),
                    oldC,
                    newC
                ));
            }
        }
        if (!subCmds.isEmpty()) {
            String baseName = baseNode != null ? baseNode.getName() : "Graph";
            String actionName = harmonized ? "Harmonized Auto Ratio (" + baseName + ")" : "Auto Ratio (" + baseName + ")";
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(subCmds, actionName));
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
            w.invalidateCache();
        }
        screen.markSummaryDirty();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onAutoRatioTriggered();

        String baseName = baseNode != null ? baseNode.getName() : "Graph";
        if (harmonized && baseNode != null) {
            BoardToast.show(Component.literal("§6✨ ").append(Component.translatable("message.gtcalcboard.auto_ratio_harmonized", baseName, (int) baseNode.getMachineCount())));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
        } else {
            BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.auto_ratio_matched", baseName)));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
        }
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




