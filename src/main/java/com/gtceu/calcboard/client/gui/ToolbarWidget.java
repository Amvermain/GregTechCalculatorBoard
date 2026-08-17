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

import java.util.ArrayList;
import java.util.List;

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
        final String text;
        final int color;
        final int bg;
        final int hoverBg;
        final int border;
        final int width;
        final java.util.function.Consumer<Integer> onClick;

        ToolbarButtonDef(String text, int color, int bg, int hoverBg, int border, int width, java.util.function.Consumer<Integer> onClick) {
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

        // 1. Add
        String addTxt = "§a➕ " + Component.translatable("gui.gtcalcboard.add_recipe").getString();
        list.add(new ToolbarButtonDef(addTxt, 0xFF55FF55, 0xFF2A623A, 0xFF3A824A, 0xFF44AA55, font.width(addTxt) + 12, btn -> {
            if (screen.getSearchDialog() != null) {
                screen.getSearchDialog().setVisible(true);
            }
        }));

        // 2. Connect
        String connTxt = "⚡ " + Component.translatable("gui.gtcalcboard.auto_connect").getString();
        list.add(new ToolbarButtonDef(connTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(connTxt) + 12, btn -> performAutoConnect()));

        // 3. Ratio
        String ratioTxt = "⚖ " + Component.translatable("gui.gtcalcboard.auto_ratio").getString();
        list.add(new ToolbarButtonDef(ratioTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(ratioTxt) + 12, btn -> performAutoRatio()));

        // 4. Flow
        String flowTxt = "🚀 " + Component.translatable("gui.gtcalcboard.max_flow").getString();
        list.add(new ToolbarButtonDef(flowTxt, 0xFFFFAA00, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(flowTxt) + 12, btn -> performMaxThroughputOptimization()));

        // 6. Tier Cap
        String capStr = graph.getMaxTierCap() != null ? graph.getMaxTierCap().getName() : "ALL";
        String capTxt = "⚡ " + Component.translatable("gui.gtcalcboard.tier_cap", capStr).getString();
        int capColor = graph.getMaxTierCap() != null ? graph.getMaxTierCap().getColor() : 0xFF55FFFF;
        list.add(new ToolbarButtonDef(capTxt, capColor, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(capTxt) + 12, this::cycleMaxTierCap));

        // 7. Share
        String shareTxt = "📋 " + Component.translatable("gui.gtcalcboard.export").getString();
        list.add(new ToolbarButtonDef(shareTxt, 0xFF66DDFF, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(shareTxt) + 12, btn -> copyBlueprintToClipboard()));

        // 8. Import
        String importTxt = "📥 " + Component.translatable("gui.gtcalcboard.import").getString();
        list.add(new ToolbarButtonDef(importTxt, 0xFF66FF88, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(importTxt) + 12, btn -> importBlueprintFromClipboard()));

        // 9. Save
        String saveTxt = "💾 " + Component.translatable("gui.gtcalcboard.save").getString();
        list.add(new ToolbarButtonDef(saveTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(saveTxt) + 12, btn -> {
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
        list.add(new ToolbarButtonDef(loadTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(loadTxt) + 12, btn -> {
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
        list.add(new ToolbarButtonDef(clearTxt, 0xFFFF6666, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(clearTxt) + 12, btn -> {
            graph.clear();
            screen.rebuildWidgets();
        }));

        // 12. Center
        String centerTxt = "⊕ " + Component.translatable("gui.gtcalcboard.center").getString();
        list.add(new ToolbarButtonDef(centerTxt, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455, font.width(centerTxt) + 12, btn -> {
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
        Font font = Minecraft.getInstance().font;
        int width = screen.width;

        int tbX = 8;
        int tbY = 8;
        int tbW = width - 16;
        int tbH = 18;

        // Toolbar background
        graphics.fill(tbX, tbY, tbX + tbW, tbY + tbH, 0xEE1E222B);
        graphics.renderOutline(tbX, tbY, tbW, tbH, 0xFF3D4455);

        // Fixed title on the left
        String titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString();
        graphics.drawString(font, titleStr, 14, 13, 0xFFFFFFFF, false);

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
            drawBtn(graphics, font, btn.text, curX, 9, btn.width, 16, mouseX, mouseY, btn.color, btn.bg, btn.hoverBg, btn.border);
            curX += btn.width + 3;
        }

        graphics.disableScissor();

        // Visual scroll hints
        if (maxScrollX > 0) {
            if (scrollX > 2) {
                graphics.drawString(font, "«", scrollAreaX - 6, 13, 0xFFFFAA00, false);
            }
            if (scrollX < maxScrollX - 2) {
                graphics.drawString(font, "»", tbX + tbW - 10, 13, 0xFFFFAA00, false);
            }
        }
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int defaultTextColor, int bg, int hoverBg, int border) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? hoverBg : bg);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF657595 : border);
        int strW = font.width(text);
        int tx = bx + (bw - strW) / 2;
        int ty = by + (bh - 8) / 2;
        graphics.drawString(font, text, tx, ty, hover ? 0xFFFFFFFF : defaultTextColor, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < 8 || mouseY > 26) return false;

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
        for (RecipeNode from : graph.getNodes()) {
            for (int outIdx = 0; outIdx < from.getOutputs().size(); outIdx++) {
                var out = from.getOutputs().get(outIdx);
                for (RecipeNode to : graph.getNodes()) {
                    if (from == to) continue;
                    for (int inIdx = 0; inIdx < to.getInputs().size(); inIdx++) {
                        var in = to.getInputs().get(inIdx);
                        if (out.equals(in)) {
                            graph.addConnection(from.getId(), outIdx, to.getId(), inIdx);
                        }
                    }
                }
            }
        }
        screen.markSummaryDirty();
    }

    public void performAutoRatio() {
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = graph.findBaseNode();
        if (baseNode == null && !graph.getNodes().isEmpty()) {
            baseNode = graph.getNodes().get(0);
        }

        if (baseNode != null) {
            graph.autoRatioFromAnchor(baseNode);
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
        }
        screen.markSummaryDirty();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String baseName = baseNode != null ? baseNode.getName() : "Graph";
            mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.auto_ratio_matched", baseName)), true);
        }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
    }

    public void cycleMaxTierCap(int direction) {
        FlowGraph graph = screen.getGraph();
        GTVoltageTier[] tiers = GTVoltageTier.values();
        GTVoltageTier current = graph.getMaxTierCap();

        int nextIdx;
        if (current == null) {
            nextIdx = direction > 0 ? 0 : tiers.length - 1;
        } else {
            int curIdx = current.ordinal();
            nextIdx = curIdx + direction;
            if (nextIdx >= tiers.length || nextIdx < 0) {
                graph.setMaxTierCap(null);
                notifyTierCapChanged(null);
                return;
            }
        }

        GTVoltageTier newCap = tiers[nextIdx];
        graph.setMaxTierCap(newCap);
        notifyTierCapChanged(newCap);
    }

    private void notifyTierCapChanged(GTVoltageTier newCap) {
        screen.markSummaryDirty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String capName = newCap != null ? newCap.getName() : "ALL (Unlimited)";
            mc.player.displayClientMessage(Component.literal("§6⚡ ").append(Component.translatable("message.gtcalcboard.tier_cap_set", capName)), true);
        }
    }

    public void performMaxThroughputOptimization() {
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = graph.findBaseNode();
        if (baseNode == null && !graph.getNodes().isEmpty()) {
            baseNode = graph.getNodes().get(0);
        }

        graph.optimizeMaxThroughput(true, false);

        if (baseNode != null) {
            graph.autoRatioFromAnchor(baseNode);
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
            w.invalidateCache();
        }
        screen.markSummaryDirty();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String capName = graph.getMaxTierCap() != null ? graph.getMaxTierCap().getName() : "MAX";
            mc.player.displayClientMessage(Component.literal("§6🚀 ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", capName)), true);
        }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
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
