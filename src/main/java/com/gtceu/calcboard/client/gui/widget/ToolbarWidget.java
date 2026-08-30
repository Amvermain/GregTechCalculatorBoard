package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.util.FormatUtil;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

public class ToolbarWidget {
    private final BoardScreen screen;
    private double scrollX = 0;
    private double maxScrollX = 0;
    private boolean isDraggingToolbar = false;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private double initialScrollX = 0;
    private boolean hasDragged = false;
    private int pressedButton = -1;

    public ToolbarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    private static class ToolbarButtonDef {
        final String id;
        final String text;
        final int color;
        final int bg;
        final int hoverBg;
        final int border;
        final int width;
        final java.util.function.Consumer<Integer> onClick;

        ToolbarButtonDef(String id, String text, int color, int bg, int hoverBg, int border, int width, java.util.function.Consumer<Integer> onClick) {
            this.id = id;
            this.text = text;
            this.color = color;
            this.bg = bg;
            this.hoverBg = hoverBg;
            this.border = border;
            this.width = width;
            this.onClick = onClick;
        }
    }

    private List<ToolbarButtonDef> buildButtons(Font font) {
        List<ToolbarButtonDef> list = new ArrayList<>();
        FlowGraph graph = screen.getGraph();

        BoardManager bm = BoardManager.getInstance();

        // 0. Guide & Manual
        if (bm.isShowGuideButton()) {
            String guideTxt = "§e📖 " + Component.translatable("gui.gtcalcboard.guide_btn").getString();
            list.add(new ToolbarButtonDef("guide", guideTxt, 0xFFFFEE55, 0xFF352E1B, 0xFF5A4A28, 0xFF776433, font.width(guideTxt) + 12, btn -> {
                if (screen.getGuideDialog() != null) {
                    screen.getGuideDialog().open();
                }
            }));
        }

        // 0.05 Tutorial
        if (bm.isShowTutorialButton()) {
            String tutTxt = "§a🎓 " + Component.translatable("gui.gtcalcboard.tutorial_btn").getString();
            list.add(new ToolbarButtonDef("tutorial", tutTxt, 0xFF55FF88, 0xFF1C3524, 0xFF2A5A38, 0xFF3B774E, font.width(tutTxt) + 12, btn -> {
                com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().startTutorial(screen);
            }));
        }

        boolean isShift = Screen.hasShiftDown();

        // 1. Connect (Shift -> Quick Connect)
        if (isShift) {
            String quickConnTxt = "§e⚡ " + Component.translatable("gui.gtcalcboard.quick_connect").getString();
            list.add(new ToolbarButtonDef("auto_connect", quickConnTxt, 0xFFFFF176, 0xFF3A351C, 0xFF5C5228, 0xFF887733, font.width(quickConnTxt) + 12, btn -> performAutoConnect()));
        } else {
            String connTxt = "🔗 " + Component.translatable("gui.gtcalcboard.auto_connect").getString();
            list.add(new ToolbarButtonDef("auto_connect", connTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(connTxt) + 12, btn -> performAutoConnect()));
        }

        // 2. Ratio (Shift -> Harmonize)
        if (isShift) {
            String harmonizeTxt = "§6✨ " + Component.translatable("gui.gtcalcboard.harmonize_ratio").getString();
            list.add(new ToolbarButtonDef("auto_ratio", harmonizeTxt, 0xFFFFD700, 0xFF3D2A1C, 0xFF634226, 0xFFA66D38, font.width(harmonizeTxt) + 12, btn -> performAutoRatio(true)));
        } else {
            String ratioTxt = "⚖ " + Component.translatable("gui.gtcalcboard.auto_ratio").getString();
            list.add(new ToolbarButtonDef("auto_ratio", ratioTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(ratioTxt) + 12, btn -> performAutoRatio(false)));
        }

        // 3. Flow
        String flowTxt = "🚀 " + Component.translatable("gui.gtcalcboard.max_flow").getString();
        list.add(new ToolbarButtonDef("max_flow", flowTxt, 0xFFFFAA00, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(flowTxt) + 12, btn -> performMaxThroughputOptimization()));

        // 3.1 Fit View / Center Content
        String fitTxt = "🎯 " + Component.translatable("gui.gtcalcboard.fit_view").getString();
        list.add(new ToolbarButtonDef("fit_view", fitTxt, 0xFF38BDF8, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(fitTxt) + 12, btn -> screen.fitToView()));

        // 3.5 Global Balance Dashboard
        String balanceTxt = "§b📊 " + Component.translatable("gui.gtcalcboard.global_balance").getString();
        list.add(new ToolbarButtonDef("global_balance", balanceTxt, 0xFF66E5FF, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(balanceTxt) + 12, btn -> {
            if (screen.getGlobalBalanceDialog() != null) {
                screen.getGlobalBalanceDialog().open();
            }
        }));

        // 3.55 Multiblock Construction BOM (Only enabled when EMI or JEI is loaded)
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported() && bm.isShowMultiblockBomButton()) {
            String bomTxt = "§6📋 " + Component.translatable("gui.gtcalcboard.bom").getString();
            list.add(new ToolbarButtonDef("multiblock_bom", bomTxt, 0xFFFFCC66, 0xFF352B1C, 0xFF4D3D28, 0xFF665035, font.width(bomTxt) + 12, btn -> {
                if (screen.getMultiblockBOMDialog() != null) {
                    screen.getMultiblockBOMDialog().open();
                }
            }));
        }

        // 3.6 Time Unit Toggle (/t, /s, /min, /h, /d)
        if (bm.isShowTimeUnitButton()) {
            com.gtceu.calcboard.api.type.RateTimeUnit curUnit = FormatUtil.getActiveTimeUnit();
            String unitTxt = "§e⏱ " + curUnit.getSuffix() + " ▼";
            list.add(new ToolbarButtonDef("time_unit", unitTxt, 0xFFFFF176, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(unitTxt) + 12, btn -> {
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

        // 3.7 Fluid Unit Toggle (Auto, Always mB, Always B)
        if (bm.isShowFluidUnitButton()) {
            com.gtceu.calcboard.api.type.FluidUnitMode curFluidMode = FormatUtil.getActiveFluidUnitMode();
            String fluidTxt = "§b💧 " + curFluidMode.getLabel() + " ▼";
            list.add(new ToolbarButtonDef("fluid_unit", fluidTxt, 0xFF66E5FF, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(fluidTxt) + 12, btn -> {
                com.gtceu.calcboard.api.type.FluidUnitMode next = curFluidMode.next();
                FormatUtil.setActiveFluidUnitMode(next);
                BoardManager.getInstance().setFluidUnitMode(next);
                BoardManager.getInstance().saveForCurrentContext();
                BoardToast.show(Component.literal("§b💧 ").append(
                    Component.translatable("gui.gtcalcboard.toast.fluid_unit_changed", Component.translatable(next.getTranslationKey()).getString())
                ));
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                screen.markSummaryDirty();
            }));
        }

        // 4. Share
        String shareTxt = "📋 " + Component.translatable("gui.gtcalcboard.export").getString();
        list.add(new ToolbarButtonDef("export", shareTxt, 0xFF66DDFF, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(shareTxt) + 12, btn -> copyBlueprintToClipboard()));

        // 5. Import
        String importTxt = "📥 " + Component.translatable("gui.gtcalcboard.import").getString();
        list.add(new ToolbarButtonDef("import", importTxt, 0xFF66FF88, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(importTxt) + 12, btn -> importBlueprintFromClipboard()));

        // 6. Team Collaboration Actions
        // 6. Team Collaboration Actions
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (state.isTeamMode()) {
            String activePageId = state.getActiveTeamPageId();
            boolean hasLock = state.doesHoldLock(activePageId);

            if (hasLock) {
                String cancelTxt = "§c✕ " + Component.translatable("gui.gtcalcboard.btn_cancel_edit").getString();
                list.add(new ToolbarButtonDef("cancel_edit", cancelTxt, 0xFFFF6B6B, 0xFF3D1C1C, 0xFF5A2A2A, 0xFF773B3B, font.width(cancelTxt) + 12, btn -> {
                    state.autoCommitAndRelease(screen, activePageId);
                    screen.rebuildWidgets();
                    screen.markSummaryDirty();
                }));
            }

            String copyTxt = "§b📋 " + Component.translatable("gui.gtcalcboard.btn_copy_to_personal").getString();
            list.add(new ToolbarButtonDef("copy_to_personal", copyTxt, 0xFF66DDFF, 0xFF1C2C44, 0xFF2B4466, 0xFF355580, font.width(copyTxt) + 12, btn -> {
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

            String historyTxt = "§f📜 " + Component.translatable("gui.gtcalcboard.btn_recent_saves").getString();
            list.add(new ToolbarButtonDef("recent_saves", historyTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(historyTxt) + 12, btn -> {
                if (screen.getRecentSavesDialog() != null) {
                    screen.getRecentSavesDialog().open();
                }
            }));
        } else if (state.isCollaborationEnabled()) {
            String exportTeamTxt = "§a📤 " + Component.translatable("gui.gtcalcboard.btn_export_to_team").getString();
            list.add(new ToolbarButtonDef("export_to_team", exportTeamTxt, 0xFF55FF88, 0xFF1C3D26, 0xFF2A5A38, 0xFF3B774E, font.width(exportTeamTxt) + 12, btn -> {
                if (screen.getExportToTeamDialog() != null) {
                    screen.getExportToTeamDialog().open();
                }
            }));
        }

        // 7. Singleplayer Pause Toggle
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            boolean isPaused = BoardManager.getInstance().isPauseGameInSingleplayer();
            String pauseTxt = isPaused
                    ? "§b⏸ " + Component.translatable("gui.gtcalcboard.btn_pause_on").getString()
                    : "§7▶ " + Component.translatable("gui.gtcalcboard.btn_pause_off").getString();
            int pauseBg = isPaused ? 0xFF1C2C44 : 0xFF222630;
            int pauseBorder = isPaused ? 0xFF355580 : 0xFF3D4455;
            list.add(new ToolbarButtonDef("pause_toggle", pauseTxt, isPaused ? 0xFF66DDFF : 0xFFAAAAAA, pauseBg, pauseBg + 0x00151515, pauseBorder, font.width(pauseTxt) + 12, btn -> {
                boolean nextVal = !BoardManager.getInstance().isPauseGameInSingleplayer();
                BoardManager.getInstance().setPauseGameInSingleplayer(nextVal);
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                String statusStr = nextVal ? "ON" : "OFF";
                BoardToast.show(Component.literal("§e⚙ ").append(Component.translatable("gui.gtcalcboard.toast.pause_toggle_hint", statusStr)));
            }));
        }

        return list;
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
        int tbW = width - tbX - 8;

        String titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString() + " §7⚙";
        int titleRight = tbX + 6 + font.width(titleStr) + 8;

        List<ToolbarButtonDef> buttons = buildButtons(font);
        int totalBtnW = 0;
        for (ToolbarButtonDef btn : buttons) {
            totalBtnW += btn.width + 3;
        }

        int scrollAreaX = titleRight;
        int scrollAreaW = (tbX + tbW) - scrollAreaX - 4;
        maxScrollX = Math.max(0, totalBtnW - scrollAreaW);
        scrollX = Math.max(0, Math.min(maxScrollX, scrollX));

        int actualToolbarW = (maxScrollX > 0) ? tbW : Math.min(tbW, (titleRight + totalBtnW) - tbX + 4);

        // Toolbar background
        graphics.fill(tbX, tbY, tbX + actualToolbarW, tbY + tbH, 0xEE1E222B);
        graphics.renderOutline(tbX, tbY, actualToolbarW, tbH, 0xFF3D4455);

        // Fixed clickable title & settings gear on the left
        boolean isTitleHovered = mouseX >= tbX + 2 && mouseX <= titleRight - 2 && mouseY >= tbY && mouseY <= tbY + tbH;
        if (isTitleHovered) {
            graphics.fill(tbX + 2, tbY + 1, titleRight - 2, tbY + tbH - 1, 0xFF2A364D);
            graphics.renderOutline(tbX + 2, tbY + 1, (titleRight - 4) - tbX, tbH - 2, 0xFF5B9BD5);
            graphics.drawString(font, "§e" + Component.translatable("gui.gtcalcboard.title").getString() + " §b⚙", tbX + 6, tbY + 5, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, titleStr, tbX + 6, tbY + 5, 0xFFFFFFFF, false);
        }

        graphics.enableScissor(scrollAreaX, tbY + 1, tbX + tbW - 2, tbY + tbH - 1);

        ToolbarButtonDef hoveredBtn = null;
        int curX = scrollAreaX - (int) scrollX;
        for (ToolbarButtonDef btn : buttons) {
            boolean isGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isToolbarButtonGlowing(btn.id);
            drawBtn(graphics, font, btn.text, curX, tbY + 1, btn.width, 16, mouseX, mouseY, btn.color, btn.bg, btn.hoverBg, btn.border, isGlowing);
            if (mouseX >= curX && mouseX <= curX + btn.width && mouseY >= tbY + 1 && mouseY <= tbY + 17) {
                if (mouseX >= scrollAreaX && mouseX <= tbX + tbW - 2) {
                    hoveredBtn = btn;
                }
            }
            curX += btn.width + 3;
        }

        graphics.disableScissor();

        // Visual scroll hints
        if (maxScrollX > 0) {
            if (scrollX > 2) {
                graphics.drawString(font, "«", scrollAreaX - 6, tbY + 5, 0xFFFFAA00, false);
            }
            if (scrollX < maxScrollX - 2) {
                graphics.drawString(font, "»", tbX + tbW - 10, tbY + 5, 0xFFFFAA00, false);
            }
        }

        graphics.pose().popPose();

        if (hoveredBtn != null) {
            String tooltipKey = "gui.gtcalcboard.tooltip.btn_" + hoveredBtn.id;
            String raw = Component.translatable(tooltipKey).getString();
            if (raw.contains("\n")) {
                List<Component> lines = Arrays.stream(raw.split("\n"))
                        .<Component>map(Component::literal)
                        .toList();
                graphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, Component.translatable(tooltipKey), mouseX, mouseY);
            }
        } else if (isTitleHovered) {
            graphics.renderTooltip(font, Component.translatable("gui.gtcalcboard.tooltip.open_settings"), mouseX, mouseY);
        }
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int defaultTextColor, int bg, int hoverBg, int border, boolean isGlowing) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int actualBg = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBgColor(bg) : (hover ? hoverBg : bg);
        int actualBorder = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(border) : (hover ? 0xFF657595 : border);

        graphics.fill(bx, by, bx + bw, by + bh, actualBg);
        graphics.renderOutline(bx, by, bw, bh, actualBorder);
        if (isGlowing) {
            graphics.renderOutline(bx - 1, by - 1, bw + 2, bh + 2, actualBorder & 0x77FFFFFF);
        }
        int strW = font.width(text);
        int tx = bx + (bw - strW) / 2;
        int ty = by + (bh - 8) / 2;
        graphics.drawString(font, text, tx, ty, (hover || isGlowing) ? 0xFFFFFFFF : defaultTextColor, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tbY = screen.getToolbarY();
        if (mouseY < tbY - 2 || mouseY > tbY + 20 || mouseX < 8 || mouseX > screen.width - 8) {
            return false;
        }

        Font font = Minecraft.getInstance().font;
        String titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString() + " §7⚙";
        int titleRight = screen.getDynamicLeftMargin() + 6 + font.width(titleStr) + 8;
        List<ToolbarButtonDef> buttons = buildButtons(font);
        int totalBtnW = 0;
        for (ToolbarButtonDef btn : buttons) {
            totalBtnW += btn.width + 3;
        }

        int contentRight = titleRight + totalBtnW - (int) scrollX;
        // If mouse is beyond all buttons and toolbar doesn't overflow, let click pass through to canvas
        if (maxScrollX <= 0 && mouseX > contentRight + 4) {
            return false;
        }

        isDraggingToolbar = true;
        dragStartX = mouseX;
        dragStartY = mouseY;
        initialScrollX = scrollX;
        hasDragged = false;
        pressedButton = button;
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isDraggingToolbar) {
            return false;
        }

        isDraggingToolbar = false;

        // If not dragged (simple click), trigger button click at dragStartX / dragStartY
        if (!hasDragged && button == pressedButton) {
            Font font = Minecraft.getInstance().font;
            String titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString() + " §7⚙";
            int tbX = screen.getDynamicLeftMargin();
            int titleRight = tbX + 6 + font.width(titleStr) + 8;
            int tbY = screen.getToolbarY();

            // Check if title / settings was clicked
            if (dragStartX >= tbX + 2 && dragStartX <= titleRight - 2 && dragStartY >= tbY && dragStartY <= tbY + 18) {
                screen.openSettingsDialog();
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                return true;
            }

            int curX = titleRight - (int) scrollX;
            List<ToolbarButtonDef> buttons = buildButtons(font);
            for (ToolbarButtonDef btn : buttons) {
                if (dragStartX >= curX && dragStartX <= curX + btn.width && dragStartY >= tbY && dragStartY <= tbY + 18) {
                    btn.onClick.accept(button);
                    return true;
                }
                curX += btn.width + 3;
            }
        }

        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingToolbar && button == 0) {
            if (Math.abs(mouseX - dragStartX) > 2.0) {
                hasDragged = true;
            }
            if (maxScrollX > 0) {
                scrollX = Math.max(0, Math.min(maxScrollX, initialScrollX - (mouseX - dragStartX)));
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int tbY = screen.getToolbarY();
        if (mouseY >= tbY - 2 && mouseY <= tbY + 20 && mouseX >= 8 && mouseX <= screen.width - 8) {
            if (maxScrollX > 0) {
                scrollX = Math.max(0, Math.min(maxScrollX, scrollX - (delta * 24.0)));
                return true;
            }
        }
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
                        if (out.equals(in) || in.matchesOrAlternative(out)) {
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

                            if (!out.equals(in) && in.hasAlternatives() && subCommands != null) {
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
        BoardToast.show(Component.literal("§6🚀 ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", "MAX")));
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

        BoardToast.show(Component.literal("§6🚀 ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", "MAX")));
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

            BoardToast.show(Component.literal("§d📦 ").append(Component.translatable("message.gtcalcboard.group_success", String.valueOf(moduleNode.getContainedMachineCount()))));
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




