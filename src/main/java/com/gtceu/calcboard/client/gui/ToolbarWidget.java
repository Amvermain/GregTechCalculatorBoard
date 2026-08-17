package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BlueprintCodec;
import com.gtceu.calcboard.api.BoardManager;
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

/**
 * Top navigation and action toolbar widget with horizontal scrolling and dragging.
 */
public class ToolbarWidget {
    private final BoardScreen screen;

    // Horizontal Scrolling & Dragging
    private double scrollX = 0;
    private double maxScrollX = 0;
    private boolean isDragging = false;
    private double dragStartX = 0;
    private double startScrollX = 0;

    public ToolbarWidget(BoardScreen screen) {
        this.screen = screen;
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

        // Calculate total content width for buttons
        int totalBtnW = 49 + 59 + 51 + 49 + 51 + 51 + 53 + 47 + 47 + 47 + 48; // 552px
        maxScrollX = Math.max(0, totalBtnW - scrollAreaW);
        scrollX = Math.max(0, Math.min(maxScrollX, scrollX));

        // Enable scissor clipping for horizontal scrolling
        graphics.enableScissor(scrollAreaX, tbY + 1, tbX + tbW - 2, tbY + tbH - 1);

        int curX = scrollAreaX - (int) scrollX;
        FlowGraph graph = screen.getGraph();

        // 1. ➕ Add
        drawBtn(graphics, font, "§a➕ Add", curX, 9, 46, 16, mouseX, mouseY, 0xFF55FF55, 0xFF2A623A, 0xFF3A824A, 0xFF44AA55);
        curX += 49;

        // 2. ⚡ Connect
        drawBtn(graphics, font, "⚡ Connect", curX, 9, 56, 16, mouseX, mouseY);
        curX += 59;

        // 3. ⚖ Ratio
        drawBtn(graphics, font, "⚖ Ratio", curX, 9, 48, 16, mouseX, mouseY);
        curX += 51;

        // 4. 🚀 Flow
        drawBtn(graphics, font, "🚀 Flow", curX, 9, 46, 16, mouseX, mouseY, 0xFFFFAA00);
        curX += 49;

        // 5. ⚡ Cap
        String capStr = graph.getMaxTierCap() != null ? graph.getMaxTierCap().getName() : "ALL";
        String capText = "⚡ " + capStr;
        int capColor = graph.getMaxTierCap() != null ? graph.getMaxTierCap().getColor() : 0xFF55FFFF;
        drawBtn(graphics, font, capText, curX, 9, 48, 16, mouseX, mouseY, capColor);
        curX += 51;

        // 6. 📋 Share
        drawBtn(graphics, font, "📋 Share", curX, 9, 48, 16, mouseX, mouseY, 0xFF66DDFF);
        curX += 51;

        // 7. 📥 Import
        drawBtn(graphics, font, "📥 Import", curX, 9, 50, 16, mouseX, mouseY, 0xFF66FF88);
        curX += 53;

        // 8. 💾 Save
        drawBtn(graphics, font, "💾 Save", curX, 9, 44, 16, mouseX, mouseY);
        curX += 47;

        // 9. 📂 Load
        drawBtn(graphics, font, "📂 Load", curX, 9, 44, 16, mouseX, mouseY);
        curX += 47;

        // 10. 🗑 Clear
        drawBtn(graphics, font, "🗑 Clear", curX, 9, 44, 16, mouseX, mouseY, 0xFFFF6666);
        curX += 47;

        // 11. ⊕ Center
        drawBtn(graphics, font, "⊕ Center", curX, 9, 48, 16, mouseX, mouseY);

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

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, 0xFFCCCCCC, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455);
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int defaultTextColor) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, defaultTextColor, 0xFF282E3B, 0xFF3E475A, 0xFF3D4455);
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int defaultTextColor, int bg, int hoverBg, int border) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? hoverBg : bg);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF657595 : border);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + 4, hover ? 0xFFFFFFFF : defaultTextColor);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < 8 || mouseY > 26) {
            return false;
        }

        Font font = Minecraft.getInstance().font;
        String titleStr = "§6" + Component.translatable("gui.gtcalcboard.title").getString();
        int titleRight = 14 + font.width(titleStr) + 8;
        int curX = titleRight - (int) scrollX;
        FlowGraph graph = screen.getGraph();

        // 1. ➕ Add
        if (mouseX >= curX && mouseX <= curX + 46) {
            if (screen.getSearchDialog() != null) {
                screen.getSearchDialog().setVisible(true);
            }
            return true;
        }
        curX += 49;

        // 2. ⚡ Connect
        if (mouseX >= curX && mouseX <= curX + 56) {
            performAutoConnect();
            return true;
        }
        curX += 59;

        // 3. ⚖ Ratio
        if (mouseX >= curX && mouseX <= curX + 48) {
            performAutoRatio();
            return true;
        }
        curX += 51;

        // 4. 🚀 Flow
        if (mouseX >= curX && mouseX <= curX + 46) {
            performMaxThroughputOptimization();
            return true;
        }
        curX += 49;

        // 5. ⚡ Cap
        if (mouseX >= curX && mouseX <= curX + 48) {
            cycleMaxTierCap(button == 0 ? 1 : -1);
            return true;
        }
        curX += 51;

        // 6. 📋 Share
        if (mouseX >= curX && mouseX <= curX + 48) {
            copyBlueprintToClipboard();
            return true;
        }
        curX += 51;

        // 7. 📥 Import
        if (mouseX >= curX && mouseX <= curX + 50) {
            importBlueprintFromClipboard();
            return true;
        }
        curX += 53;

        // 8. 💾 Save
        if (mouseX >= curX && mouseX <= curX + 44) {
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
            return true;
        }
        curX += 47;

        // 9. 📂 Load
        if (mouseX >= curX && mouseX <= curX + 44) {
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
            return true;
        }
        curX += 47;

        // 10. 🗑 Clear
        if (mouseX >= curX && mouseX <= curX + 44) {
            graph.clear();
            screen.rebuildWidgets();
            return true;
        }
        curX += 47;

        // 11. ⊕ Center
        if (mouseX >= curX && mouseX <= curX + 48) {
            screen.setPanX(screen.width / 2.0 - 100);
            screen.setPanY(screen.height / 2.0 - 50);
            screen.setZoom(1.0);
            BoardScreen.lastPanX = screen.getPanX();
            BoardScreen.lastPanY = screen.getPanY();
            BoardScreen.lastZoom = screen.getZoom();
            return true;
        }

        // Start horizontal drag on toolbar background
        if (button == 0) {
            isDragging = true;
            dragStartX = mouseX;
            startScrollX = scrollX;
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= 8 && mouseY <= 26) {
            scrollX = Math.max(0, Math.min(maxScrollX, scrollX - delta * 25));
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging && button == 0) {
            double dx = mouseX - dragStartX;
            scrollX = Math.max(0, Math.min(maxScrollX, startScrollX - dx));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging && button == 0) {
            isDragging = false;
            return true;
        }
        return false;
    }

    public void performAutoConnect() {
        FlowGraph graph = screen.getGraph();
        graph.getConnections().clear();
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
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public void performMaxThroughputOptimization() {
        FlowGraph graph = screen.getGraph();
        graph.optimizeMaxThroughput(true, false);
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
        if (!code.isEmpty()) {
            mc.keyboardHandler.setClipboard(code);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copy_success")), true);
            }
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
        }
    }

    public void importBlueprintFromClipboard() {
        Minecraft mc = Minecraft.getInstance();
        String clipboard = mc.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.trim().isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.clipboard_empty")), true);
            }
            return;
        }

        double[] viewport = new double[3];
        FlowGraph loaded = BlueprintCodec.importFromString(clipboard, viewport);
        FlowGraph graph = screen.getGraph();
        if (loaded != null && !loaded.getNodes().isEmpty()) {
            graph.copyFrom(loaded);
            if (viewport[2] > 0.1) {
                screen.setPanX(viewport[0]);
                screen.setPanY(viewport[1]);
                screen.setZoom(viewport[2]);
                BoardScreen.lastPanX = screen.getPanX();
                BoardScreen.lastPanY = screen.getPanY();
                BoardScreen.lastZoom = screen.getZoom();
            }
            screen.rebuildWidgets();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.import_success", String.valueOf(graph.getNodes().size()))), true);
            }
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
        } else {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.import_fail")), true);
            }
        }
    }
}
