package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.ToolbarWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Interactive modal dialog that previews and filters auto-connect candidates by resource type (item/fluid).
 * Allows users to selectively exclude universal materials (e.g. water, oxygen, steam) before wiring.
 */
public class AutoConnectFilterDialog {
    private final BoardScreen parent;
    private boolean visible = false;
    private final List<ResourceEntry> entries = new ArrayList<>();
    private double scrollY = 0;
    private double maxScrollY = 0;

    public static class ResourceEntry {
        private final IngredientStack stack;
        private final int potentialWireCount;
        private boolean selected;

        public ResourceEntry(IngredientStack stack, int potentialWireCount, boolean selected) {
            this.stack = stack;
            this.potentialWireCount = potentialWireCount;
            this.selected = selected;
        }

        public IngredientStack getStack() {
            return stack;
        }

        public int getPotentialWireCount() {
            return potentialWireCount;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public void toggleSelected() {
            this.selected = !this.selected;
        }
    }

    public AutoConnectFilterDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open() {
        FlowGraph graph = parent.getGraph();
        if (graph == null || graph.getNodes().isEmpty()) {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("gui.gtcalcboard.dialog.auto_connect.no_connections")));
            return;
        }

        List<ResourceEntry> candidates = scanCandidates(graph);
        if (candidates.isEmpty()) {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("gui.gtcalcboard.dialog.auto_connect.no_connections")));
            return;
        }

        this.entries.clear();
        this.entries.addAll(candidates);
        this.scrollY = 0;
        this.visible = true;
    }

    public void close() {
        this.visible = false;
        this.entries.clear();
    }

    public boolean isVisible() {
        return visible;
    }

    public static List<ResourceEntry> scanCandidates(FlowGraph graph) {
        List<ResourceEntry> list = new ArrayList<>();
        if (graph == null) return list;

        Map<ResourceLocation, IngredientStack> sampleStacks = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> wireCounts = new LinkedHashMap<>();

        for (RecipeNode from : graph.getNodes()) {
            for (int outIdx = 0; outIdx < from.getOutputs().size(); outIdx++) {
                IngredientStack out = from.getOutputs().get(outIdx);
                boolean fromFeedsReroute = !from.isReroute() && isOutputFeedingReroute(graph, from.getId(), outIdx);

                for (RecipeNode to : graph.getNodes()) {
                    if (from == to) continue;
                    for (int inIdx = 0; inIdx < to.getInputs().size(); inIdx++) {
                        IngredientStack in = to.getInputs().get(inIdx);
                        if (out.equals(in) || in.matchesOrAlternative(out)) {
                            if (isPortConnected(graph, from.getId(), outIdx, to.getId(), inIdx)) {
                                continue;
                            }
                            if (fromFeedsReroute && !to.isReroute()) {
                                continue;
                            }

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

                            FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(from.getId(), outIdx, targetNode.getId(), targetInIdx);
                            if (!graph.getConnections().contains(edge)) {
                                ResourceLocation key = out.getId() != null ? out.getId() : in.getId();
                                if (key != null) {
                                    sampleStacks.putIfAbsent(key, out.copy());
                                    wireCounts.put(key, wireCounts.getOrDefault(key, 0) + 1);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<ResourceLocation, IngredientStack> entry : sampleStacks.entrySet()) {
            ResourceLocation key = entry.getKey();
            int count = wireCounts.getOrDefault(key, 0);
            if (count > 0) {
                list.add(new ResourceEntry(entry.getValue(), count, true));
            }
        }

        // Sort by wire count descending, then by name
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getPotentialWireCount(), a.getPotentialWireCount());
            if (cmp != 0) return cmp;
            return a.getStack().getDisplayName().compareToIgnoreCase(b.getStack().getDisplayName());
        });

        return list;
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

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible) return;

        Font font = Minecraft.getInstance().font;

        int dialogW = Math.min(360, screenW - 24);
        int dialogH = Math.min(280, screenH - 32);
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 700.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        // 1. Semi-transparent backdrop
        graphics.fill(0, 0, screenW, screenH, 0x88000000);

        // 2. Main Box Background & Border
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xF5161C26);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFF3D4B66);

        // 3. Header Bar
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 24, 0xFF1C2433);
        graphics.drawString(font, "§e🔗 " + Component.translatable("gui.gtcalcboard.dialog.auto_connect.title").getString(), dialogX + 10, dialogY + 8, 0xFFFFFFFF, false);

        // Close Button [✕]
        int closeX = dialogX + dialogW - 20;
        int closeY = dialogY + 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16;
        graphics.fill(closeX, closeY, closeX + 16, closeY + 16, closeHover ? 0xFF992222 : 0xFF2A3345);
        graphics.renderOutline(closeX, closeY, 16, 16, closeHover ? 0xFFFF4444 : 0xFF4A5A78);
        graphics.drawCenteredString(font, "✕", closeX + 8, closeY + 4, closeHover ? 0xFFFFFFFF : 0xFFAAAAAA);

        // 4. Subtitle & Quick Selection Bar
        int subY = dialogY + 28;
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.dialog.auto_connect.desc").getString(), dialogX + 10, subY + 2, 0xFFAAAAAA, false);

        // Select All / Deselect All Buttons
        int selAllW = 74;
        int deselAllW = 82;
        int btnH = 14;
        int deselAllX = dialogX + dialogW - deselAllW - 10;
        int selAllX = deselAllX - selAllW - 4;

        drawSmallBtn(graphics, font, Component.translatable("gui.gtcalcboard.dialog.auto_connect.select_all").getString(), selAllX, subY, selAllW, btnH, mouseX, mouseY, 0xFF55FF88, 0xFF1C3524, 0xFF2A5A38);
        drawSmallBtn(graphics, font, Component.translatable("gui.gtcalcboard.dialog.auto_connect.deselect_all").getString(), deselAllX, subY, deselAllW, btnH, mouseX, mouseY, 0xFFFF7777, 0xFF3D1C1C, 0xFF5A2A2A);

        // 5. Scrollable Candidate Item List
        int listX = dialogX + 8;
        int listY = subY + 18;
        int listW = dialogW - 16;
        int listH = dialogH - 80;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xAA0D121B);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF283344);

        int rowH = 22;
        int totalContentH = entries.size() * rowH;
        maxScrollY = Math.max(0, totalContentH - listH);
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY));

        graphics.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

        int curY = listY + 2 - (int) scrollY;
        for (int i = 0; i < entries.size(); i++) {
            ResourceEntry entry = entries.get(i);
            boolean rowHover = mouseX >= listX + 2 && mouseX <= listX + listW - 8 && mouseY >= curY && mouseY <= curY + rowH - 2 && mouseY >= listY && mouseY <= listY + listH;

            int rowBg = rowHover ? 0xFF243042 : (i % 2 == 0 ? 0x66161F2E : 0x33161F2E);
            graphics.fill(listX + 2, curY, listX + listW - 8, curY + rowH - 2, rowBg);
            if (rowHover) {
                graphics.renderOutline(listX + 2, curY, listW - 10, rowH - 2, 0xFF4A6080);
            }

            // Checkbox
            int chkX = listX + 6;
            int chkY = curY + 3;
            graphics.fill(chkX, chkY, chkX + 12, chkY + 12, entry.isSelected() ? 0xFF1E4D2B : 0xFF222833);
            graphics.renderOutline(chkX, chkY, 12, 12, entry.isSelected() ? 0xFF55FF88 : 0xFF556070);
            if (entry.isSelected()) {
                graphics.drawCenteredString(font, "✔", chkX + 6, chkY + 2, 0xFF55FF88);
            }

            // Item Icon
            int iconX = chkX + 16;
            int iconY = curY + 1;
            IngredientRenderer.render(graphics, entry.getStack(), iconX, iconY);

            // Item Display Name
            String name = entry.getStack().getDisplayName();
            int maxNameW = listW - 100;
            if (font.width(name) > maxNameW) {
                name = font.plainSubstrByWidth(name, maxNameW - 8) + "...";
            }
            int nameColor = entry.isSelected() ? 0xFFFFFFFF : 0xFF888888;
            graphics.drawString(font, name, iconX + 20, curY + 5, nameColor, false);

            // Wire count badge
            String wireTxt = Component.translatable("gui.gtcalcboard.dialog.auto_connect.wire_count", String.valueOf(entry.getPotentialWireCount())).getString();
            int badgeW = font.width(wireTxt) + 6;
            int badgeX = listX + listW - 12 - badgeW;
            int badgeY = curY + 3;
            graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 12, entry.isSelected() ? 0xFF2B4466 : 0xFF1C222E);
            graphics.renderOutline(badgeX, badgeY, badgeW, 12, entry.isSelected() ? 0xFF55FFFF : 0xFF445060);
            graphics.drawString(font, wireTxt, badgeX + 3, badgeY + 2, entry.isSelected() ? 0xFF88EEFF : 0xFF777777, false);

            curY += rowH;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScrollY > 0) {
            int sbX = listX + listW - 5;
            graphics.fill(sbX, listY + 1, sbX + 3, listY + listH - 1, 0x55000000);
            int thumbH = Math.max(16, (int) ((double) listH / totalContentH * listH));
            int thumbY = listY + (int) ((scrollY / maxScrollY) * (listH - thumbH));
            graphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }

        // 6. Bottom Action Buttons
        int selectedWireCount = getSelectedWireCount();
        int bottomBtnH = 20;
        int bottomBtnY = dialogY + dialogH - bottomBtnH - 8;

        int cancelBtnW = 80;
        int cancelBtnX = dialogX + 10;
        drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.dialog.auto_connect.cancel").getString(), cancelBtnX, bottomBtnY, cancelBtnW, bottomBtnH, mouseX, mouseY, 0xFFFFFFFF, 0xFF282E3B, 0xFF3D4455);

        int connectBtnW = dialogW - cancelBtnW - 26;
        int connectBtnX = cancelBtnX + cancelBtnW + 6;
        String connectTxt = "⚡ " + Component.translatable("gui.gtcalcboard.dialog.auto_connect.connect_count", String.valueOf(selectedWireCount)).getString();
        boolean hasSelected = selectedWireCount > 0;
        int connectBg = hasSelected ? 0xFF1C3D26 : 0xFF22262E;
        int connectHoverBg = hasSelected ? 0xFF2A5A38 : 0xFF22262E;
        int connectBorder = hasSelected ? 0xFF3B774E : 0xFF3D4455;
        int connectTextCol = hasSelected ? 0xFF55FF88 : 0xFF777777;
        drawBtn(graphics, font, connectTxt, connectBtnX, bottomBtnY, connectBtnW, bottomBtnH, mouseX, mouseY, connectTextCol, connectBg, connectHoverBg, connectBorder);

        graphics.pose().popPose();
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textCol, int bg, int border) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, textCol, bg, bg + 0x00151515, border);
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textCol, int bg, int hoverBg, int border) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int activeBg = hover ? hoverBg : bg;
        int activeBorder = hover ? 0xFFFFFFFF : border;
        graphics.fill(bx, by, bx + bw, by + bh, activeBg);
        graphics.renderOutline(bx, by, bw, bh, activeBorder);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, hover ? 0xFFFFFFFF : textCol);
    }

    private void drawSmallBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textCol, int bg, int border) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int activeBg = hover ? (bg + 0x00151515) : bg;
        int activeBorder = hover ? 0xFFFFFFFF : border;
        graphics.fill(bx, by, bx + bw, by + bh, activeBg);
        graphics.renderOutline(bx, by, bw, bh, activeBorder);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 7) / 2, hover ? 0xFFFFFFFF : textCol);
    }

    public int getSelectedWireCount() {
        int count = 0;
        for (ResourceEntry e : entries) {
            if (e.isSelected()) {
                count += e.getPotentialWireCount();
            }
        }
        return count;
    }

    public Set<ResourceLocation> getSelectedResourceIds() {
        Set<ResourceLocation> set = new HashSet<>();
        for (ResourceEntry e : entries) {
            if (e.isSelected() && e.getStack().getId() != null) {
                set.add(e.getStack().getId());
            }
        }
        return set;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;

        int screenW = parent.width;
        int screenH = parent.height;
        int dialogW = Math.min(360, screenW - 24);
        int dialogH = Math.min(280, screenH - 32);
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;

        // Close [✕] Button
        int closeX = dialogX + dialogW - 20;
        int closeY = dialogY + 4;
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16) {
            close();
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }

        // Select All / Deselect All
        int subY = dialogY + 28;
        int selAllW = 74;
        int deselAllW = 82;
        int btnH = 14;
        int deselAllX = dialogX + dialogW - deselAllW - 10;
        int selAllX = deselAllX - selAllW - 4;

        if (mouseX >= selAllX && mouseX <= selAllX + selAllW && mouseY >= subY && mouseY <= subY + btnH) {
            for (ResourceEntry e : entries) e.setSelected(true);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        if (mouseX >= deselAllX && mouseX <= deselAllX + deselAllW && mouseY >= subY && mouseY <= subY + btnH) {
            for (ResourceEntry e : entries) e.setSelected(false);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Candidate List Items Click
        int listX = dialogX + 8;
        int listY = subY + 18;
        int listW = dialogW - 16;
        int listH = dialogH - 80;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int rowH = 22;
            int clickedIdx = (int) ((mouseY - listY + scrollY) / rowH);
            if (clickedIdx >= 0 && clickedIdx < entries.size()) {
                entries.get(clickedIdx).toggleSelected();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F));
                return true;
            }
        }

        // Bottom Action Buttons
        int bottomBtnH = 20;
        int bottomBtnY = dialogY + dialogH - bottomBtnH - 8;
        int cancelBtnW = 80;
        int cancelBtnX = dialogX + 10;
        int connectBtnW = dialogW - cancelBtnW - 26;
        int connectBtnX = cancelBtnX + cancelBtnW + 6;

        if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW && mouseY >= bottomBtnY && mouseY <= bottomBtnY + bottomBtnH) {
            close();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (mouseX >= connectBtnX && mouseX <= connectBtnX + connectBtnW && mouseY >= bottomBtnY && mouseY <= bottomBtnY + bottomBtnH) {
            if (getSelectedWireCount() > 0) {
                executeConnect();
            }
            return true;
        }

        // Absorb clicks inside dialog
        if (mouseX >= dialogX && mouseX <= dialogX + dialogW && mouseY >= dialogY && mouseY <= dialogY + dialogH) {
            return true;
        }

        // Click outside -> close
        close();
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        int screenW = parent.width;
        int screenH = parent.height;
        int dialogW = Math.min(360, screenW - 24);
        int dialogH = Math.min(280, screenH - 32);
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;

        if (mouseX >= dialogX && mouseX <= dialogX + dialogW && mouseY >= dialogY && mouseY <= dialogY + dialogH) {
            if (maxScrollY > 0) {
                scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (delta * 22.0)));
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (getSelectedWireCount() > 0) {
                executeConnect();
            }
            return true;
        }
        return true;
    }

    private void executeConnect() {
        Set<ResourceLocation> allowedIds = getSelectedResourceIds();
        close();
        ToolbarWidget.performAutoConnectWithFilter(parent, allowedIds);
    }
}
