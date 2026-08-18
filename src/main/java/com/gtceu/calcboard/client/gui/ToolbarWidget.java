package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.BlueprintCodec;
import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

public class ToolbarWidget {
    private final BoardScreen screen;
    private double scrollX = 0;
    private double maxScrollX = 0;
    private boolean isDraggingToolbar = false;
    private double lastMouseX = 0;

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

        // 0. Guide & Manual
        String guideTxt = "§e📖 " + Component.translatable("gui.gtcalcboard.guide_btn").getString();
        list.add(new ToolbarButtonDef("guide", guideTxt, 0xFFFFEE55, 0xFF352E1B, 0xFF5A4A28, 0xFF776433, font.width(guideTxt) + 12, btn -> {
            if (screen.getGuideDialog() != null) {
                screen.getGuideDialog().open();
            }
        }));

        // 0.05 Tutorial
        String tutTxt = "§a🎓 " + Component.translatable("gui.gtcalcboard.tutorial_btn").getString();
        list.add(new ToolbarButtonDef("tutorial", tutTxt, 0xFF55FF88, 0xFF1C3524, 0xFF2A5A38, 0xFF3B774E, font.width(tutTxt) + 12, btn -> {
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().startTutorial(screen);
        }));

        // 0.1 Undo
        boolean canUndo = BoardManager.getInstance().getActivePage() != null && BoardManager.getInstance().getActivePage().getHistoryManager().canUndo();
        String undoTxt = "↶ " + Component.translatable("gui.gtcalcboard.undo").getString();
        int undoCol = canUndo ? 0xFFEEEEEE : 0xFF777777;
        int undoBg = canUndo ? 0xFF282E3B : 0xFF1C2028;
        int undoHov = canUndo ? 0xFF3E475A : 0xFF1C2028;
        int undoBorder = canUndo ? 0xFF3D4455 : 0xFF282E3B;
        list.add(new ToolbarButtonDef("undo", undoTxt, undoCol, undoBg, undoHov, undoBorder, font.width(undoTxt) + 10, btn -> {
            if (canUndo) screen.undo();
        }));

        // 0.2 Redo
        boolean canRedo = BoardManager.getInstance().getActivePage() != null && BoardManager.getInstance().getActivePage().getHistoryManager().canRedo();
        String redoTxt = "↷ " + Component.translatable("gui.gtcalcboard.redo").getString();
        int redoCol = canRedo ? 0xFFEEEEEE : 0xFF777777;
        int redoBg = canRedo ? 0xFF282E3B : 0xFF1C2028;
        int redoHov = canRedo ? 0xFF3E475A : 0xFF1C2028;
        int redoBorder = canRedo ? 0xFF3D4455 : 0xFF282E3B;
        list.add(new ToolbarButtonDef("redo", redoTxt, redoCol, redoBg, redoHov, redoBorder, font.width(redoTxt) + 10, btn -> {
            if (canRedo) screen.redo();
        }));

        // 1. Add
        String addTxt = "§a➕ " + Component.translatable("gui.gtcalcboard.add_recipe").getString();
        list.add(new ToolbarButtonDef("add_recipe", addTxt, 0xFF55FF55, 0xFF2A623A, 0xFF3A824A, 0xFF44AA55, font.width(addTxt) + 12, btn -> {
            if (screen.getSearchDialog() != null) {
                screen.getSearchDialog().setVisible(true);
            }
        }));

        // 2. Connect
        String connTxt = "⚡ " + Component.translatable("gui.gtcalcboard.auto_connect").getString();
        list.add(new ToolbarButtonDef("auto_connect", connTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(connTxt) + 12, btn -> performAutoConnect()));

        // 3. Ratio
        String ratioTxt = "⚖ " + Component.translatable("gui.gtcalcboard.auto_ratio").getString();
        list.add(new ToolbarButtonDef("auto_ratio", ratioTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(ratioTxt) + 12, btn -> performAutoRatio()));

        // 4. Flow
        String flowTxt = "🚀 " + Component.translatable("gui.gtcalcboard.max_flow").getString();
        list.add(new ToolbarButtonDef("max_flow", flowTxt, 0xFFFFAA00, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(flowTxt) + 12, btn -> performMaxThroughputOptimization()));

        // 5. Group into Module
        String groupTxt = "📦 " + Component.translatable("gui.gtcalcboard.group_module").getString();
        list.add(new ToolbarButtonDef("group_module", groupTxt, 0xFFCC88FF, 0xFF352055, 0xFF5A3A8A, 0xFF7744AA, font.width(groupTxt) + 12, btn -> performGroupIntoModule()));

        // 6. Share
        String shareTxt = "📋 " + Component.translatable("gui.gtcalcboard.export").getString();
        list.add(new ToolbarButtonDef("export", shareTxt, 0xFF66DDFF, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(shareTxt) + 12, btn -> copyBlueprintToClipboard()));

        // 8. Import
        String importTxt = "📥 " + Component.translatable("gui.gtcalcboard.import").getString();
        list.add(new ToolbarButtonDef("import", importTxt, 0xFF66FF88, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(importTxt) + 12, btn -> importBlueprintFromClipboard()));

        // 9. Save
        String saveTxt = "💾 " + Component.translatable("gui.gtcalcboard.save").getString();
        list.add(new ToolbarButtonDef("save", saveTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(saveTxt) + 12, btn -> {
            boolean saved = BoardManager.getInstance().saveToFile(BoardManager.getInstance().getDefaultSaveFile(), screen.getPanX(), screen.getPanY(), screen.getZoom());
            Minecraft mc = Minecraft.getInstance();
            if (saved) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.save_success", String.valueOf(graph.getNodes().size()))), true);
                }
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            } else {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.save_fail")), true);
                }
            }
        }));

        // 10. Load
        String loadTxt = "📂 " + Component.translatable("gui.gtcalcboard.load").getString();
        list.add(new ToolbarButtonDef("load", loadTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(loadTxt) + 12, btn -> {
            boolean loaded = BoardManager.getInstance().loadFromFile(BoardManager.getInstance().getDefaultSaveFile());
            Minecraft mc = Minecraft.getInstance();
            if (loaded) {
                screen.setPanX(BoardScreen.lastPanX);
                screen.setPanY(BoardScreen.lastPanY);
                screen.setZoom(BoardScreen.lastZoom);
                screen.rebuildWidgets();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.load_success", String.valueOf(graph.getNodes().size()))), true);
                }
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
            } else {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.load_fail")), true);
                }
            }
        }));

        // 11. Clear
        String clearTxt = "🗑 " + Component.translatable("gui.gtcalcboard.clear").getString();
        list.add(new ToolbarButtonDef("clear", clearTxt, 0xFFFF6666, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(clearTxt) + 12, btn -> {
            if (graph.getNodes().isEmpty()) return;
            List<RecipeNode> removedNodes = new ArrayList<>(graph.getNodes());
            List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>(graph.getConnections());
            graph.clear();
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Clear board"));
            screen.rebuildWidgets();
            screen.markSummaryDirty();
        }));

        // 12. Center
        String centerTxt = "⊕ " + Component.translatable("gui.gtcalcboard.center").getString();
        list.add(new ToolbarButtonDef("center", centerTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(centerTxt) + 12, btn -> {
            screen.setPanX(screen.width / 2.0 - 100);
            screen.setPanY(screen.height / 2.0 - 50);
            screen.setZoom(1.0);
            BoardScreen.lastPanX = screen.getPanX();
            BoardScreen.lastPanY = screen.getPanY();
            BoardScreen.lastZoom = screen.getZoom();
        }));

        return list;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        Font font = Minecraft.getInstance().font;
        int width = screen.width;

        int tbX = 8;
        int tbY = 26;
        int tbH = 18;
        int tbW = width - 16;

        // Toolbar background
        graphics.fill(tbX, tbY, tbX + tbW, tbY + tbH, 0xEE1E222B);
        graphics.renderOutline(tbX, tbY, tbW, tbH, 0xFF3D4455);

        // Fixed title on the left
        String titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString();
        graphics.drawString(font, titleStr, 14, tbY + 5, 0xFFFFFFFF, false);

        int titleRight = 14 + font.width(titleStr) + 8;
        int scrollAreaX = titleRight;
        int scrollAreaW = (tbX + tbW) - scrollAreaX - 4;

        List<ToolbarButtonDef> buttons = buildButtons(font);
        int totalBtnW = 0;
        for (ToolbarButtonDef btn : buttons) {
            totalBtnW += btn.width + 3;
        }

        maxScrollX = Math.max(0, totalBtnW - scrollAreaW);
        scrollX = Math.max(0, Math.min(maxScrollX, scrollX));

        graphics.enableScissor(scrollAreaX, tbY + 1, tbX + tbW - 2, tbY + tbH - 1);

        int curX = scrollAreaX - (int) scrollX;
        for (ToolbarButtonDef btn : buttons) {
            boolean isGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isToolbarButtonGlowing(btn.id);
            drawBtn(graphics, font, btn.text, curX, tbY + 1, btn.width, 16, mouseX, mouseY, btn.color, btn.bg, btn.hoverBg, btn.border, isGlowing);
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
        if (mouseY < 26 || mouseY > 44) return false;

        Font font = Minecraft.getInstance().font;
        String titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString();
        int titleRight = 14 + font.width(titleStr) + 8;
        int curX = titleRight - (int) scrollX;

        List<ToolbarButtonDef> buttons = buildButtons(font);
        for (ToolbarButtonDef btn : buttons) {
            if (mouseX >= curX && mouseX <= curX + btn.width) {
                btn.onClick.accept(button);
                return true;
            }
            curX += btn.width + 3;
        }

        // Click on empty space inside toolbar starts horizontal drag
        if (mouseX >= titleRight && mouseX <= screen.width - 8) {
            isDraggingToolbar = true;
            lastMouseX = mouseX;
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingToolbar) {
            isDraggingToolbar = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingToolbar && button == 0) {
            double deltaX = mouseX - lastMouseX;
            lastMouseX = mouseX;
            if (maxScrollX > 0) {
                scrollX = Math.max(0, Math.min(maxScrollX, scrollX - deltaX));
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= 8 && mouseY <= 26) {
            if (maxScrollX > 0) {
                scrollX = Math.max(0, Math.min(maxScrollX, scrollX - (delta * 24.0)));
                return true;
            }
        }
        return false;
    }

    public void performAutoConnect() {
        FlowGraph graph = screen.getGraph();
        List<FlowGraph.ConnectionEdge> addedEdges = new ArrayList<>();
        for (RecipeNode from : graph.getNodes()) {
            for (int outIdx = 0; outIdx < from.getOutputs().size(); outIdx++) {
                var out = from.getOutputs().get(outIdx);
                for (RecipeNode to : graph.getNodes()) {
                    if (from == to) continue;
                    for (int inIdx = 0; inIdx < to.getInputs().size(); inIdx++) {
                        var in = to.getInputs().get(inIdx);
                        if (out.equals(in)) {
                            FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(from.getId(), outIdx, to.getId(), inIdx);
                            if (!graph.getConnections().contains(edge)) {
                                graph.addConnection(from.getId(), outIdx, to.getId(), inIdx);
                                addedEdges.add(edge);
                            }
                        }
                    }
                }
            }
        }
        if (!addedEdges.isEmpty()) {
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(Collections.emptyList(), addedEdges, "Auto Connect " + addedEdges.size() + " wires"));
        }
        screen.markSummaryDirty();
    }

    public void performAutoRatio() {
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
            graph.autoRatioFromAnchor(baseNode);
        }

        List<com.gtceu.calcboard.api.history.BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            double oldC = oldCounts.getOrDefault(n.getId(), 1.0);
            double newC = n.getMachineCount();
            if (Math.abs(oldC - newC) > 0.0001) {
                subCmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(
                    n.getId(),
                    com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.MACHINE_COUNT,
                    oldC,
                    newC
                ));
            }
        }
        if (!subCmds.isEmpty()) {
            String baseName = baseNode != null ? baseNode.getName() : "Graph";
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(subCmds, "Auto Ratio (" + baseName + ")"));
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
        }
        screen.markSummaryDirty();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onAutoRatioTriggered();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String baseName = baseNode != null ? baseNode.getName() : "Graph";
            mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.auto_ratio_matched", baseName)), true);
        }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
    }

    public void performMaxThroughputOptimization() {
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = graph.findBaseNode();
        if (baseNode == null && !graph.getNodes().isEmpty()) {
            baseNode = graph.getNodes().get(0);
        }

        Map<String, Object[]> oldProps = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            oldProps.put(n.getId(), new Object[]{n.getTargetTier(), n.getOverclockMode(), n.getParallel(), n.getMachineCount()});
        }

        graph.optimizeMaxThroughput(true, false);

        if (baseNode != null) {
            graph.autoRatioFromAnchor(baseNode);
        }

        List<com.gtceu.calcboard.api.history.BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            Object[] oldP = oldProps.get(n.getId());
            if (oldP != null) {
                if (oldP[0] != n.getTargetTier()) {
                    subCmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(n.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.TARGET_TIER, oldP[0], n.getTargetTier()));
                }
                if (oldP[1] != n.getOverclockMode()) {
                    subCmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(n.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.OVERCLOCK_MODE, oldP[1], n.getOverclockMode()));
                }
                if (!oldP[2].equals(n.getParallel())) {
                    subCmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(n.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.PARALLEL, oldP[2], n.getParallel()));
                }
                if (Math.abs(((Double) oldP[3]) - n.getMachineCount()) > 0.0001) {
                    subCmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(n.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.MACHINE_COUNT, oldP[3], n.getMachineCount()));
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

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6🚀 ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", "MAX")), true);
        }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
    }

    private void performGroupIntoModule() {
        FlowGraph graph = screen.getGraph();
        var selectedIds = screen.getSelectedNodeIds();
        boolean hasSpecificSelection = selectedIds != null && selectedIds.size() >= 2;

        if (!hasSpecificSelection && graph.getNodes().size() < 2) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.group_min_nodes")), true);
            }
            return;
        }

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        RecipeNode moduleNode = hasSpecificSelection 
            ? graph.groupIntoModule(selectedIds, "Compound Process")
            : graph.groupIntoModule("Compound Process");

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

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§d📦 ").append(Component.translatable("message.gtcalcboard.group_success", String.valueOf(moduleNode.getContainedMachineCount()))), true);
            }
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2F));
        }
    }

    public void copyBlueprintToClipboard() {
        FlowGraph graph = screen.getGraph();
        String code = BlueprintCodec.exportToString(graph, screen.getPanX(), screen.getPanY(), screen.getZoom());
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(code);
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copy_success")), true);
        }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F));
    }

    public void importBlueprintFromClipboard() {
        Minecraft mc = Minecraft.getInstance();
        String clip = mc.keyboardHandler.getClipboard();
        if (clip == null || clip.trim().isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.clipboard_empty")), true);
            }
            return;
        }

        double[] viewport = new double[3];
        FlowGraph imported = BlueprintCodec.importFromString(clip, viewport);
        if (imported != null) {
            screen.getGraph().copyFrom(imported);
            screen.setPanX(viewport[0]);
            screen.setPanY(viewport[1]);
            screen.setZoom(viewport[2]);
            BoardScreen.lastPanX = screen.getPanX();
            BoardScreen.lastPanY = screen.getPanY();
            BoardScreen.lastZoom = screen.getZoom();
            screen.rebuildWidgets();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.import_success", String.valueOf(screen.getGraph().getNodes().size()))), true);
            }
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.1F));
        } else {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.import_fail")), true);
            }
        }
    }
}
