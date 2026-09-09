package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.solver.ProductionETACalculator;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.editor.NodeCountEditor;
import com.gtceu.calcboard.client.gui.editor.NodeNameEditor;
import com.gtceu.calcboard.client.gui.editor.NodeTargetBatchEditor;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.WireAnimationMode;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * Dedicated visual renderer for Recipe Node cards in the Calculator Board.
 */
public class NodeCardRenderer {
    private static final Component COUNT_LABEL = Component.translatable("gui.gtcalcboard.count");
    private static final Map<ResourceLocation, ItemStack> MACHINE_ICON_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static ItemStack getOrCreateMachineIcon(ResourceLocation iconId) {
        return MACHINE_ICON_CACHE.computeIfAbsent(iconId, id -> {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if ((item == null || item == Items.AIR) && ForgeRegistries.BLOCKS != null) {
                var block = ForgeRegistries.BLOCKS.getValue(id);
                if (block != null && block.asItem() != Items.AIR) {
                    item = block.asItem();
                }
            }
            if (item != null && item != Items.AIR) {
                return item.getDefaultInstance();
            }
            return ItemStack.EMPTY;
        });
    }
    public static void render(NodeWidget widget, GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        RecipeNode node = widget.getNode();
        Font font = Minecraft.getInstance().font;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int cardW = widget.getWidth();
        int height = widget.getHeight();

        if (node.isReroute()) {
            renderRerouteNode(widget, graphics, font, x, y, mouseX, mouseY);
            return;
        }

        boolean isModalOpen = (Minecraft.getInstance().screen instanceof BoardScreen bs) && bs.isAnyModalOpen();
        double zoom = (Minecraft.getInstance().screen instanceof BoardScreen bs) ? bs.getZoom() : BoardScreen.lastZoom;
        if (isModalOpen || zoom < 0.28) {
            renderLOD(widget, graphics, font, x, y, cardW, height, node);
            return;
        }

        FlowGraph graph = widget.getParent() != null ? widget.getParent().getGraph() : (Minecraft.getInstance().screen instanceof BoardScreen bs ? bs.getGraph() : null);
        boolean isOperational = node.isOperational(graph);

        boolean isCardHovered = mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + height;
        int activeMouseX = isCardHovered ? mouseX : -9999;
        int activeMouseY = isCardHovered ? mouseY : -9999;

        int titleX = (node.getMachineIcon() != null) ? (x + 22) : (x + 6);
        int headerBtnMargin = node.isModule() ? 76 : 58;
        NodeCardTextCache textCache = widget.getTextCache();
        textCache.update(widget, font, graph, node, cardW, titleX, x, headerBtnMargin);

        renderCardBackground(widget, graphics, node, x, y, cardW, height, isOperational, activeMouseX, activeMouseY);
        renderCardOutline(graphics, node, x, y, cardW, height, isOperational, textCache);

        renderMachineIcon(graphics, node, x, y);
        renderCardTitle(widget, graphics, font, x, y, cardW, titleX, headerBtnMargin, textCache);
        renderHeaderButtons(widget, graphics, font, node, x, y, cardW, activeMouseX, activeMouseY);

        int ctrlY = y + NodeWidget.HEADER_HEIGHT + 6;
        renderCountControls(widget, graphics, font, x, ctrlY, isCardHovered, mouseX, mouseY, activeMouseX, activeMouseY, isOperational);

        if (node.isModule()) {
            renderModuleBadge(graphics, font, node, x, cardW, ctrlY);
        } else if (!node.getAddons().isEmpty()) {
            renderAddonTray(graphics, font, node, x, cardW, ctrlY, isCardHovered, mouseX, mouseY);
        }

        int row2Y = ctrlY + 18;
        renderMiddleControlsAndInfo(widget, graphics, font, node, x, row2Y, cardW, ctrlY, activeMouseX, activeMouseY, textCache);

        var bounds = widget.getLayoutBounds();
        int sepY = bounds.getSeparatorY();
        graphics.fill(x + 4, sepY, x + cardW - 4, sepY + 1, !isOperational ? 0xFF5A2228 : 0xFF353C4D);

        renderPortRows(widget, graphics, font, node, x, cardW, bounds.getContentStartY(), isCardHovered, mouseX, mouseY, textCache);
        renderHiddenPorts(widget, graphics, font, node, x, y, cardW, height, isCardHovered, mouseX, mouseY);
        renderResizeHandle(widget, graphics, font, x, y, cardW, height, isCardHovered, mouseX, mouseY);
    }

    private static void renderCardBackground(NodeWidget widget, GuiGraphics graphics, RecipeNode node, int x, int y, int cardW, int height, boolean isOperational, int activeMouseX, int activeMouseY) {
        int cardBg = !isOperational ? 0xFF251417 : (node.isModule() ? 0xFF1D172E : (node.isFusion() ? 0xFF22132D : (node.isGenerator() ? 0xFF122218 : 0xFF1E222B)));
        graphics.fill(x, y, x + cardW, y + height, cardBg);

        int headerColor = !isOperational
                ? (widget.isHeaderHovered(activeMouseX, activeMouseY) ? 0xFF521C22 : 0xFF3D1419)
                : (node.isModule()
                    ? (widget.isHeaderHovered(activeMouseX, activeMouseY) ? 0xFF3D2A5E : 0xFF2A1C42)
                    : (node.isFusion()
                        ? (widget.isHeaderHovered(activeMouseX, activeMouseY) ? 0xFF4A1E6D : 0xFF35154E)
                        : (node.isGenerator()
                            ? (widget.isHeaderHovered(activeMouseX, activeMouseY) ? 0xFF1E482E : 0xFF163824)
                            : (widget.isHeaderHovered(activeMouseX, activeMouseY) ? 0xFF353C4D : 0xFF2A2E39))));
        graphics.fill(x, y, x + cardW, y + NodeWidget.HEADER_HEIGHT, headerColor);
    }

    private static void renderCardOutline(GuiGraphics graphics, RecipeNode node, int x, int y, int cardW, int height, boolean isOperational, NodeCardTextCache textCache) {
        boolean isSelected = false;
        if (Minecraft.getInstance().screen instanceof BoardScreen bs) {
            isSelected = bs.isNodeSelected(node.getId());
        }

        boolean isStarved = false;
        int outlineColor;
        if (isSelected) {
            outlineColor = 0xFF00FFFF;
        } else if (!isOperational) {
            float pulse = (float) (0.60 + 0.40 * Math.sin(System.currentTimeMillis() / 200.0));
            int red = (int) (170 + 85 * pulse);
            outlineColor = 0xFF000000 | (red << 16) | (0x33 << 8) | 0x33;
        } else if (BoardManager.getInstance().getWireAnimationMode() == WireAnimationMode.RATE_MODULATED && (isStarved = textCache.isStarved())) {
            float pulse = (float) (0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 240.0));
            int red = (int) (245 * pulse);
            int green = (int) (158 * pulse);
            outlineColor = 0xFF000000 | (red << 16) | (green << 8) | 0x0B;
        } else if (node.isModule()) {
            outlineColor = 0xFF9955FF;
        } else if (node.isFusion()) {
            outlineColor = 0xFFCC44FF;
        } else if (node.isBaseNode()) {
            outlineColor = 0xFFFFD700;
        } else if (node.isGenerator()) {
            outlineColor = 0xFF33AA66;
        } else {
            outlineColor = 0xFF3D4455;
        }
        graphics.renderOutline(x, y, cardW, height, outlineColor);
        renderCardOutlineLayers(graphics, node, x, y, cardW, height, isOperational, isSelected, isStarved, outlineColor);
    }

    private static void renderCardOutlineLayers(GuiGraphics graphics, RecipeNode node, int x, int y, int cardW, int height, boolean isOperational, boolean isSelected, boolean isStarved, int outlineColor) {
        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, cardW + 2, height + 2, 0x8800FFFF);
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x8800FFFF);
        } else if (!isOperational) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, (outlineColor & 0x00FFFFFF) | 0x88000000);
        } else if (isStarved) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x66F59E0B);
        } else if (node.isModule()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x559955FF);
        } else if (node.isFusion()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x66CC44FF);
        } else if (node.isBaseNode()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x88FFD700);
        } else if (node.isGenerator()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x4433AA66);
        }
    }

    private static void renderMachineIcon(GuiGraphics graphics, RecipeNode node, int x, int y) {
        ResourceLocation iconId = node.getMachineIcon();
        if (iconId == null) return;
        ItemStack iconStack = getOrCreateMachineIcon(iconId);
        if (iconStack.isEmpty()) return;

        if (TutorialManager.getInstance().isMachineIconGlowing(node.getId())) {
            int glowBorder = TutorialManager.getGlowBorderColor(0xFFFFD700);
            graphics.fill(x + 2, y + 1, x + 20, y + 19, 0x4400E676);
            graphics.renderOutline(x + 2, y + 1, 18, 18, glowBorder);
            graphics.renderOutline(x + 1, y, 20, 20, glowBorder & 0x77FFFFFF);
        }
        IngredientRenderer.renderItemStack(graphics, iconStack, x + 3, y + 2);
    }

    private static void renderCardTitle(NodeWidget widget, GuiGraphics graphics, Font font, int x, int y, int cardW, int titleX, int headerBtnMargin, NodeCardTextCache textCache) {
        NodeNameEditor nameEditor = widget.getNameEditor();
        if (nameEditor != null && nameEditor.isEditing()) {
            int editW = Math.max(60, cardW - (titleX - x) - headerBtnMargin);
            graphics.fill(titleX - 2, y + 2, titleX + editW, y + 18, 0xFF0D1B2A);
            graphics.renderOutline(titleX - 2, y + 2, editW, 16, 0xFF55FFFF);

            var editor = nameEditor.getEditor();
            if (editor != null && editor.hasSelection()) {
                String fullTxt = editor.getText();
                int selStart = editor.getSelectionStart();
                int selEnd = editor.getSelectionEnd();
                int beforeW = font.width(fullTxt.substring(0, selStart));
                int selW = font.width(fullTxt.substring(selStart, selEnd));
                graphics.fill(titleX + 2 + beforeW, y + 5, titleX + 2 + beforeW + selW, y + 15, 0xFF0055AA);
                graphics.drawString(font, fullTxt, titleX + 2, y + 6, 0xFFFFFFFF, false);
            } else {
                String editTxt = nameEditor.getDisplayText();
                graphics.drawString(font, editTxt, titleX + 2, y + 6, 0xFF55FFFF, false);
            }
        } else {
            graphics.drawString(font, textCache.getTitle(), titleX, y + 6, textCache.getTitleColor(), false);
        }
    }

    private static void renderHeaderButtons(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int cardW, int activeMouseX, int activeMouseY) {
        renderExpandOrSwitchButton(widget, graphics, font, node, x, y, cardW, activeMouseX, activeMouseY);
        renderFlipButton(widget, graphics, font, node, x, y, cardW, activeMouseX, activeMouseY);
        renderTargetButton(widget, graphics, font, node, x, y, cardW, activeMouseX, activeMouseY);
        renderCloseButton(widget, graphics, font, node, x, y, cardW, activeMouseX, activeMouseY);
    }

    private static void renderExpandOrSwitchButton(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int cardW, int activeMouseX, int activeMouseY) {
        if (node.isModule()) {
            int expandX = x + cardW - 72;
            int expandY = y + 2;
            boolean expandHover = widget.isExpandButtonHovered(activeMouseX, activeMouseY);
            graphics.fill(expandX, expandY, expandX + 16, expandY + 16, expandHover ? 0xFF5A3A8A : 0xFF352055);
            graphics.renderOutline(expandX, expandY, 16, 16, expandHover ? 0xFFCC88FF : 0xFF7744AA);
            graphics.drawString(font, "⤢", expandX + 4, expandY + 3, expandHover ? 0xFFFFFFFF : 0xFFDDAAFF, false);
        } else if (!node.isReroute()) {
            int switchX = x + cardW - 72;
            int switchY = y + 2;
            boolean switchHover = widget.isSwitchButtonHovered(activeMouseX, activeMouseY);
            int switchBg = switchHover ? 0xFF2A4866 : 0xFF1D2F44;
            int switchBorder = switchHover ? 0xFF5B9BD5 : 0xFF35587A;
            graphics.fill(switchX, switchY, switchX + 16, switchY + 16, switchBg);
            graphics.renderOutline(switchX, switchY, 16, 16, switchBorder);
            graphics.drawString(font, "⟲", switchX + 4, switchY + 4, switchHover ? 0xFFFFFFFF : 0xFF88CCFF, false);
        }
    }

    private static void renderFlipButton(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int cardW, int activeMouseX, int activeMouseY) {
        int flipX = x + cardW - 54;
        int flipY = y + 2;
        boolean flipHover = widget.isFlipButtonHovered(activeMouseX, activeMouseY);
        int flipBg = flipHover ? 0xFF3A4456 : 0xFF222834;
        int flipBorder = flipHover ? 0xFF88AAFF : 0xFF4A5568;
        graphics.fill(flipX, flipY, flipX + 16, flipY + 16, flipBg);
        graphics.renderOutline(flipX, flipY, 16, 16, flipBorder);
        graphics.drawString(font, node.isFlipped() ? "⬅" : "➔", flipX + 5, flipY + 4, flipHover ? 0xFFFFFFFF : 0xFFAAAAAA, false);
    }

    private static void renderTargetButton(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int cardW, int activeMouseX, int activeMouseY) {
        int targetX = x + cardW - 36;
        int targetY = y + 2;
        boolean isTargetGlowing = TutorialManager.getInstance().isNodeBaseTargetButtonGlowing(node.getId());
        boolean targetHover = widget.isTargetButtonHovered(activeMouseX, activeMouseY);
        int targetBg = node.isBaseNode() ? 0xFF886600 : (isTargetGlowing ? TutorialManager.getGlowBgColor(0xFF222834) : (targetHover ? 0xFF3A4456 : 0xFF222834));
        int targetBorder = node.isBaseNode() ? 0xFFFFD700 : (isTargetGlowing ? TutorialManager.getGlowBorderColor(0xFF4A5568) : (targetHover ? 0xFF88AAFF : 0xFF4A5568));
        graphics.fill(targetX, targetY, targetX + 16, targetY + 16, targetBg);
        graphics.renderOutline(targetX, targetY, 16, 16, targetBorder);
        if (isTargetGlowing) {
            graphics.renderOutline(targetX - 1, targetY - 1, 18, 18, targetBorder & 0x77FFFFFF);
        }
        graphics.drawString(font, "⌖", targetX + 5, targetY + 4, (node.isBaseNode() || isTargetGlowing) ? 0xFFFFEE55 : 0xFFAAAAAA, false);
    }

    private static void renderCloseButton(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int cardW, int activeMouseX, int activeMouseY) {
        int closeX = x + cardW - 18;
        int closeY = y + 2;
        boolean isCloseGlowing = TutorialManager.getInstance().isNodeCloseButtonGlowing(node.getId());
        boolean closeHover = widget.isCloseButtonHovered(activeMouseX, activeMouseY);
        int closeBg = (closeHover || isCloseGlowing) ? 0xFFFF4444 : 0x44FF4444;
        graphics.fill(closeX, closeY, closeX + 16, closeY + 16, closeBg);
        if (isCloseGlowing) {
            graphics.renderOutline(closeX - 1, closeY - 1, 18, 18, 0xFFFF5555);
        }
        graphics.drawString(font, "x", closeX + 5, closeY + 3, 0xFFFFFFFF, false);
    }

    private static void renderCountControls(NodeWidget widget, GuiGraphics graphics, Font font, int x, int ctrlY, boolean isCardHovered, int mouseX, int mouseY, int activeMouseX, int activeMouseY, boolean isOperational) {
        NodeCountEditor countEditor = widget.getCountEditor();
        String countText = countEditor.getDisplayText();

        graphics.drawString(font, COUNT_LABEL, x + 6, ctrlY + 3, !isOperational ? 0xFFFF8888 : 0xFFAAAAAA, false);

        int countBtnCol = !isOperational ? 0xFFFF8888 : 0xFFFFFFFF;
        int countMinusX = x + 36;
        drawBtn(graphics, font, "-", 5, countMinusX, ctrlY, 14, 14, activeMouseX, activeMouseY, countBtnCol, !isOperational, false);

        int countTextW = font.width(countText);
        int countBoxW = Math.max(28, countTextW + 6);
        int countBoxX = countMinusX + 16;
        renderCountBox(graphics, font, countEditor, countText, countBoxX, ctrlY, countBoxW, countTextW, isCardHovered, mouseX, mouseY, isOperational);

        int afterCountX = countBoxX + countBoxW + 2;
        drawBtn(graphics, font, "+", 7, afterCountX, ctrlY, 14, 14, activeMouseX, activeMouseY, countBtnCol, !isOperational, false);
        drawBtn(graphics, font, "/2", 10, afterCountX + 16, ctrlY, 16, 14, activeMouseX, activeMouseY, countBtnCol, !isOperational, false);
        drawBtn(graphics, font, "x2", 11, afterCountX + 34, ctrlY, 16, 14, activeMouseX, activeMouseY, countBtnCol, !isOperational, false);
    }

    private static void renderCountBox(GuiGraphics graphics, Font font, NodeCountEditor countEditor, String countText, int countBoxX, int ctrlY, int countBoxW, int countTextW, boolean isCardHovered, int mouseX, int mouseY, boolean isOperational) {
        boolean countHover = isCardHovered && mouseX >= countBoxX && mouseX <= countBoxX + countBoxW && mouseY >= ctrlY && mouseY <= ctrlY + 14;
        boolean isEditing = countEditor.isEditing();
        int countBg = isEditing ? 0xFF0D1B2A : (!isOperational ? (countHover ? 0xFF36161A : 0xFF220E12) : (countHover ? 0xFF252A36 : 0xFF14171E));
        int countBorder = isEditing ? 0xFF55FFFF : (!isOperational ? (countHover ? 0xFFFF6666 : 0xFF993333) : (countHover ? 0xFF5577AA : 0xFF3D4455));
        graphics.fill(countBoxX, ctrlY, countBoxX + countBoxW, ctrlY + 14, countBg);
        graphics.renderOutline(countBoxX, ctrlY, countBoxW, 14, countBorder);

        if (isEditing && countEditor.getEditor() != null && countEditor.getEditor().hasSelection()) {
            var editor = countEditor.getEditor();
            String fullTxt = editor.getText();
            int selStart = editor.getSelectionStart();
            int selEnd = editor.getSelectionEnd();
            int fullW = font.width(fullTxt);
            int startX = countBoxX + (countBoxW - fullW) / 2;
            int beforeW = font.width(fullTxt.substring(0, selStart));
            int selW = font.width(fullTxt.substring(selStart, selEnd));
            graphics.fill(startX + beforeW, ctrlY + 2, startX + beforeW + selW, ctrlY + 12, 0xFF0055AA);
            graphics.drawString(font, fullTxt, startX, ctrlY + 3, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, countText, countBoxX + (countBoxW - countTextW) / 2, ctrlY + 3, isEditing ? 0xFF55FFFF : (!isOperational ? 0xFFFFB3B3 : 0xFFFFFFAA), false);
        }
    }

    private static void renderModuleBadge(GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int ctrlY) {
        String machinesBadge = String.format("§d▦ %d%s", node.getContainedMachineCount(), Component.translatable("gui.gtcalcboard.machine_unit").getString());
        int badgeW = font.width(machinesBadge);
        graphics.drawString(font, machinesBadge, x + cardW - 6 - badgeW, ctrlY + 3, 0xFFFFFFFF, false);
    }

    private static void renderAddonTray(GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int ctrlY, boolean isCardHovered, int mouseX, int mouseY) {
        List<MachineAddon> addons = node.getAddons();
        int maxIcons = Math.min(addons.size(), 3);
        int trayX = x + cardW - 6;
        for (int a = maxIcons - 1; a >= 0; a--) {
            var addon = addons.get(a);
            trayX -= 15;
            renderAddonSlot(graphics, font, node, addon, trayX, ctrlY, isCardHovered, mouseX, mouseY);
        }
        if (addons.size() > 3) {
            String extraStr = "+" + (addons.size() - 3);
            int extraW = font.width(extraStr);
            trayX -= (extraW + 3);
            graphics.drawString(font, "§b" + extraStr, trayX, ctrlY + 2, 0xFFFFFFFF, false);
        }
    }

    private static void renderAddonSlot(GuiGraphics graphics, Font font, RecipeNode node, MachineAddon addon, int trayX, int ctrlY, boolean isCardHovered, int mouseX, int mouseY) {
        boolean iconHover = isCardHovered && mouseX >= trayX && mouseX <= trayX + 14 && mouseY >= ctrlY - 1 && mouseY <= ctrlY + 13;
        graphics.fill(trayX, ctrlY - 1, trayX + 14, ctrlY + 13, iconHover ? 0xFF2F3B4D : 0xFF181D26);
        graphics.renderOutline(trayX, ctrlY - 1, 14, 14, iconHover ? 0xFF58D3FF : 0xFF354054);
        renderAddonItem(graphics, font, addon, trayX, ctrlY);
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR && !node.hasValidReflector()) {
            graphics.fill(trayX + 7, ctrlY - 2, trayX + 15, ctrlY + 6, 0xFFFF2222);
            graphics.drawString(font, "!", trayX + 9, ctrlY - 3, 0xFFFFFFFF, false);
        }
    }

    private static void renderAddonItem(GuiGraphics graphics, Font font, MachineAddon addon, int trayX, int ctrlY) {
        ItemStack sample = addon.getRenderItemStack();
        if (sample == null || sample.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(trayX + 2.0, ctrlY + 1.0, 0.0);
        graphics.pose().scale(0.625f, 0.625f, 1.0f);
        IngredientRenderer.renderItemStack(graphics, sample, 0, 0);
        if (sample.getCount() > 1) {
            graphics.renderItemDecorations(font, sample, 0, 0);
        }
        graphics.pose().popPose();
    }

    private static void renderMiddleControlsAndInfo(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int ctrlY, int activeMouseX, int activeMouseY, NodeCardTextCache textCache) {
        if (BoardManager.getInstance().isSlimCardMode()) return;

        if (!node.isModule()) {
            var guiHandler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
            boolean isGlowing = TutorialManager.getInstance().isMachineConfigButtonGlowing(node.getId());
            guiHandler.renderCardControls(widget, graphics, font, node, x, row2Y, cardW, activeMouseX, activeMouseY, isGlowing);
        }

        int infoY = node.isModule() ? (ctrlY + 18) : (row2Y + 18);
        graphics.drawString(font, textCache.getRightInfoStr(), x + cardW - 6 - textCache.getRightInfoW(), infoY, 0xFFFFFFFF, false);
        graphics.drawString(font, textCache.getFittedPowerStr(), x + 6, infoY, 0xFFFFFFFF, false);
    }

    private static void renderPortRows(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int contentY, boolean isCardHovered, int mouseX, int mouseY, NodeCardTextCache textCache) {
        List<IngredientStack> inputs = node.getInputs();
        List<IngredientStack> outputs = node.getOutputs();
        List<Integer> visInputs = node.getVisibleInputIndices();
        List<Integer> visOutputs = node.getVisibleOutputIndices();
        int maxRows = Math.max(visInputs.size(), visOutputs.size());
        boolean isFlipped = node.isFlipped();

        for (int r = 0; r < maxRows; r++) {
            int rowY = contentY + r * 18;
            renderSinglePortRow(widget, graphics, font, node, x, cardW, rowY, r, isFlipped, inputs, outputs, visInputs, visOutputs, isCardHovered, mouseX, mouseY, textCache);
        }
    }

    private static void renderSinglePortRow(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int rowY, int r, boolean isFlipped, List<IngredientStack> inputs, List<IngredientStack> outputs, List<Integer> visInputs, List<Integer> visOutputs, boolean isCardHovered, int mouseX, int mouseY, NodeCardTextCache textCache) {
        boolean hasInput = (r < visInputs.size());
        boolean hasOutput = (r < visOutputs.size());
        int inOrigIdx = hasInput ? visInputs.get(r) : -1;
        int outOrigIdx = hasOutput ? visOutputs.get(r) : -1;

        if (!isFlipped && hasInput) {
            renderLeftInputSlot(widget, graphics, font, node, x, cardW, rowY, r, inOrigIdx, inputs, outputs, isCardHovered, mouseX, mouseY, textCache);
        } else if (isFlipped && hasOutput) {
            renderLeftOutputSlot(widget, graphics, font, node, x, cardW, rowY, r, outOrigIdx, inputs, outputs, isCardHovered, mouseX, mouseY, textCache);
        }

        if (!isFlipped && hasOutput) {
            renderRightOutputSlot(widget, graphics, font, node, x, cardW, rowY, r, outOrigIdx, inputs, outputs, isCardHovered, mouseX, mouseY, textCache);
        } else if (isFlipped && hasInput) {
            renderRightInputSlot(widget, graphics, font, node, x, cardW, rowY, r, inOrigIdx, inputs, outputs, isCardHovered, mouseX, mouseY, textCache);
        }
    }

    private static void renderLeftInputSlot(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int rowY, int r, int inOrigIdx, List<IngredientStack> inputs, List<IngredientStack> outputs, boolean isCardHovered, int mouseX, int mouseY, NodeCardTextCache textCache) {
        IngredientStack in = inputs.get(inOrigIdx);
        int inPortX = x + 4;
        int inPortY = rowY + 5;
        NodeCardTextCache.PortText left = textCache.getLeftPortTexts().get(r);

        boolean isPortGlowing = TutorialManager.getInstance().isPortGlowing(node.getId(), true, inOrigIdx);
        boolean isPortSelected = (Minecraft.getInstance().screen instanceof BoardScreen bs && bs.isPortSelected(node.getId(), true, inOrigIdx));
        if (isPortSelected) {
            boolean hasBoth = !inputs.isEmpty() && !outputs.isEmpty();
            int slotW = hasBoth ? ((cardW / 2) - 4) : (cardW - 4);
            graphics.fill(x + 2, rowY - 2, x + 2 + slotW, rowY + 16, 0x4438BDF8);
        }
        int portColor = isPortSelected ? 0xFF38BDF8 : (isPortGlowing ? TutorialManager.getGlowBorderColor(0xFF5599FF) : (left != null ? left.portColor() : 0xFF5599FF));
        var inPort = widget.getLayoutBounds().findPort(true, inOrigIdx);
        boolean portHover = isCardHovered && inPort != null && inPort.hitBox().contains(mouseX, mouseY);
        graphics.fill(inPortX, inPortY, inPortX + 6, inPortY + 6, portHover ? 0xFFFFFFFF : portColor);
        if (isPortGlowing || isPortSelected) {
            graphics.renderOutline(inPortX - 2, inPortY - 2, 10, 10, isPortSelected ? 0xFF38BDF8 : portColor);
        }

        renderIngredient(graphics, in, x + 12, rowY - 1);
        if (in.hasAlternatives()) {
            renderAlternativeBadge(graphics, font, x + 12, rowY - 1);
        }

        if (left != null) {
            graphics.drawString(font, left.text(), x + 30, rowY + 4, left.textColor(), false);
        }
    }

    private static void renderLeftOutputSlot(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int rowY, int r, int outOrigIdx, List<IngredientStack> inputs, List<IngredientStack> outputs, boolean isCardHovered, int mouseX, int mouseY, NodeCardTextCache textCache) {
        IngredientStack out = outputs.get(outOrigIdx);
        int outPortX = x + 4;
        int outPortY = rowY + 5;
        NodeCardTextCache.PortText left = textCache.getLeftPortTexts().get(r);

        boolean isPortGlowing = TutorialManager.getInstance().isPortGlowing(node.getId(), false, outOrigIdx);
        boolean isPortSelected = (Minecraft.getInstance().screen instanceof BoardScreen bs && bs.isPortSelected(node.getId(), false, outOrigIdx));
        if (isPortSelected) {
            boolean hasBoth = !inputs.isEmpty() && !outputs.isEmpty();
            int slotW = hasBoth ? ((cardW / 2) - 4) : (cardW - 4);
            graphics.fill(x + 2, rowY - 2, x + 2 + slotW, rowY + 16, 0x4438BDF8);
        }
        boolean isVoided = node.isOutputPortVoided(outOrigIdx);
        int portColor = isPortSelected ? 0xFF38BDF8 : (isVoided ? 0xFFA855F7 : (isPortGlowing ? TutorialManager.getGlowBorderColor(0xFF55FF88) : (left != null ? left.portColor() : 0xFF55FF88)));
        var outPort = widget.getLayoutBounds().findPort(false, outOrigIdx);
        boolean portHover = isCardHovered && outPort != null && outPort.hitBox().contains(mouseX, mouseY);
        graphics.fill(outPortX, outPortY, outPortX + 6, outPortY + 6, portHover ? 0xFFFFFFFF : portColor);
        if (isPortGlowing || isPortSelected) {
            graphics.renderOutline(outPortX - 2, outPortY - 2, 10, 10, isPortSelected ? 0xFF38BDF8 : portColor);
        } else if (isVoided) {
            graphics.renderOutline(outPortX - 1, outPortY - 1, 8, 8, 0xFFA855F7);
        }

        renderIngredient(graphics, out, x + 12, rowY - 1);
        if (left != null) {
            int txtColor = isVoided ? 0xFFC084FC : left.textColor();
            graphics.drawString(font, left.text(), x + 30, rowY + 4, txtColor, false);
        }
    }

    private static void renderRightOutputSlot(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int rowY, int r, int outOrigIdx, List<IngredientStack> inputs, List<IngredientStack> outputs, boolean isCardHovered, int mouseX, int mouseY, NodeCardTextCache textCache) {
        IngredientStack out = outputs.get(outOrigIdx);
        int outPortX = x + cardW - 10;
        int outPortY = rowY + 5;
        NodeCardTextCache.PortText right = textCache.getRightPortTexts().get(r);

        boolean isPortGlowing = TutorialManager.getInstance().isPortGlowing(node.getId(), false, outOrigIdx);
        boolean isPortSelected = (Minecraft.getInstance().screen instanceof BoardScreen bs && bs.isPortSelected(node.getId(), false, outOrigIdx));
        if (isPortSelected) {
            boolean hasBoth = !inputs.isEmpty() && !outputs.isEmpty();
            int slotW = hasBoth ? ((cardW / 2) - 4) : (cardW - 4);
            int startSlotX = hasBoth ? (x + (cardW / 2) + 2) : (x + 2);
            graphics.fill(startSlotX, rowY - 2, startSlotX + slotW, rowY + 16, 0x4438BDF8);
        }
        boolean isVoided = node.isOutputPortVoided(outOrigIdx);
        int portColor = isPortSelected ? 0xFF38BDF8 : (isVoided ? 0xFFA855F7 : (isPortGlowing ? TutorialManager.getGlowBorderColor(0xFF55FF88) : (right != null ? right.portColor() : 0xFF55FF88)));
        var outPort = widget.getLayoutBounds().findPort(false, outOrigIdx);
        boolean portHover = isCardHovered && outPort != null && outPort.hitBox().contains(mouseX, mouseY);
        graphics.fill(outPortX, outPortY, outPortX + 6, outPortY + 6, portHover ? 0xFFFFFFFF : portColor);
        if (isPortGlowing || isPortSelected) {
            graphics.renderOutline(outPortX - 2, outPortY - 2, 10, 10, isPortSelected ? 0xFF38BDF8 : portColor);
        } else if (isVoided) {
            graphics.renderOutline(outPortX - 1, outPortY - 1, 8, 8, 0xFFA855F7);
        }

        if (right != null) {
            int txtColor = isVoided ? 0xFFC084FC : right.textColor();
            graphics.drawString(font, right.text(), x + cardW - 30 - right.width(), rowY + 4, txtColor, false);
        }
        renderIngredient(graphics, out, x + cardW - 28, rowY - 1);
    }

    private static void renderRightInputSlot(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int cardW, int rowY, int r, int inOrigIdx, List<IngredientStack> inputs, List<IngredientStack> outputs, boolean isCardHovered, int mouseX, int mouseY, NodeCardTextCache textCache) {
        IngredientStack in = inputs.get(inOrigIdx);
        int inPortX = x + cardW - 10;
        int inPortY = rowY + 5;
        NodeCardTextCache.PortText right = textCache.getRightPortTexts().get(r);

        boolean isPortGlowing = TutorialManager.getInstance().isPortGlowing(node.getId(), true, inOrigIdx);
        boolean isPortSelected = (Minecraft.getInstance().screen instanceof BoardScreen bs && bs.isPortSelected(node.getId(), true, inOrigIdx));
        if (isPortSelected) {
            boolean hasBoth = !inputs.isEmpty() && !outputs.isEmpty();
            int slotW = hasBoth ? ((cardW / 2) - 4) : (cardW - 4);
            int startSlotX = hasBoth ? (x + (cardW / 2) + 2) : (x + 2);
            graphics.fill(startSlotX, rowY - 2, startSlotX + slotW, rowY + 16, 0x4438BDF8);
        }
        int portColor = isPortSelected ? 0xFF38BDF8 : (isPortGlowing ? TutorialManager.getGlowBorderColor(0xFF5599FF) : (right != null ? right.portColor() : 0xFF5599FF));
        var inPort = widget.getLayoutBounds().findPort(true, inOrigIdx);
        boolean portHover = isCardHovered && inPort != null && inPort.hitBox().contains(mouseX, mouseY);
        graphics.fill(inPortX, inPortY, inPortX + 6, inPortY + 6, portHover ? 0xFFFFFFFF : portColor);
        if (isPortGlowing || isPortSelected) {
            graphics.renderOutline(inPortX - 2, inPortY - 2, 10, 10, isPortSelected ? 0xFF38BDF8 : portColor);
        }

        if (right != null) {
            graphics.drawString(font, right.text(), x + cardW - 30 - right.width(), rowY + 4, right.textColor(), false);
        }
        renderIngredient(graphics, in, x + cardW - 28, rowY - 1);
        if (in.hasAlternatives()) {
            renderAlternativeBadge(graphics, font, x + cardW - 28, rowY - 1);
        }
    }

    private static void renderHiddenPorts(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int cardW, int height, boolean isCardHovered, int mouseX, int mouseY) {
        int totalHidden = node.getTotalHiddenCount();
        if (totalHidden > 0) {
            renderHiddenPortsBadge(widget, graphics, font, node, x, y, cardW, height, totalHidden, isCardHovered, mouseX, mouseY);
        }
        widget.getHiddenPortsPopup().render(graphics, mouseX, mouseY);
    }

    private static void renderHiddenPortsBadge(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int cardW, int height, int totalHidden, boolean isCardHovered, int mouseX, int mouseY) {
        int hiddenIn = node.getHiddenInputCount();
        int hiddenOut = node.getHiddenOutputCount();
        String hiddenText;
        if (hiddenIn > 0 && hiddenOut == 0) {
            hiddenText = hiddenIn == 1
                    ? Component.translatable("gui.gtcalcboard.hidden_port_single_input", 1).getString()
                    : Component.translatable("gui.gtcalcboard.hidden_port_multi_inputs", hiddenIn).getString();
        } else if (hiddenOut > 0 && hiddenIn == 0) {
            hiddenText = hiddenOut == 1
                    ? Component.translatable("gui.gtcalcboard.hidden_port_single_output", 1).getString()
                    : Component.translatable("gui.gtcalcboard.hidden_port_multi_outputs", hiddenOut).getString();
        } else {
            hiddenText = Component.translatable("gui.gtcalcboard.hidden_port_mixed", totalHidden).getString();
        }

        int textW = font.width(hiddenText);
        int badgeX = x + cardW - textW - 14;
        int badgeY = y + height - 13;
        boolean badgeHover = isCardHovered && widget.isHiddenPortsBadgeHovered(mouseX, mouseY);
        int textColor = badgeHover ? 0xFFFFFFFF : 0xFFB0B0C0;

        if (badgeHover) {
            graphics.fill(badgeX - 3, badgeY - 2, badgeX + textW + 3, badgeY + 10, 0x443388FF);
            graphics.renderOutline(badgeX - 3, badgeY - 2, textW + 6, 12, 0xFF55AAFF);
        }

        graphics.drawString(font, hiddenText, badgeX, badgeY, textColor, false);
    }

    private static void renderResizeHandle(NodeWidget widget, GuiGraphics graphics, Font font, int x, int y, int cardW, int height, boolean isCardHovered, int mouseX, int mouseY) {
        int handleX = x + cardW - 8;
        int handleY = y + height - 8;
        boolean handleHover = isCardHovered && widget.isResizeHandleHovered(mouseX, mouseY);
        int handleColor = handleHover ? 0xFF55FFFF : 0x88657595;
        graphics.drawString(font, "⤡", handleX - 2, handleY - 3, handleColor, false);
    }

    public static void renderAlternativeBadge(GuiGraphics graphics, Font font, int itemX, int itemY) {
        int bx = itemX + 8;
        int by = itemY + 8;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(bx, by, bx + 9, by + 9, 0xF00D1626);
        graphics.renderOutline(bx, by, 9, 9, 0xFFFFD700);
        graphics.drawString(font, "§e⟲", bx + 2, by, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my) {
        drawBtn(graphics, font, text, -1, bx, by, bw, bh, mx, my, 0xFFFFFFFF, false, false);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor) {
        drawBtn(graphics, font, text, -1, bx, by, bw, bh, mx, my, textColor, false, false);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor, boolean isGlowing) {
        drawBtn(graphics, font, text, -1, bx, by, bw, bh, mx, my, textColor, false, isGlowing);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor, boolean isAlert, boolean isGlowing) {
        drawBtn(graphics, font, text, -1, bx, by, bw, bh, mx, my, textColor, isAlert, isGlowing);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int textW, int bx, int by, int bw, int bh, int mx, int my, int textColor, boolean isAlert, boolean isGlowing) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int defaultBg = isAlert ? 0xFF351818 : 0xFF282E3B;
        int hoverBg = isAlert ? 0xFF4D2222 : 0xFF3E475A;
        int defaultBorder = isAlert ? 0xFFFF4444 : 0xFF3D4455;
        int hoverBorder = isAlert ? 0xFFFF7777 : 0xFF657595;
        int finalTextColor = isAlert ? 0xFFFF8888 : textColor;

        int bg = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBgColor(defaultBg) : (hover ? hoverBg : defaultBg);
        int border = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(defaultBorder) : (hover ? hoverBorder : defaultBorder);
        graphics.fill(bx, by, bx + bw, by + bh, bg);
        graphics.renderOutline(bx, by, bw, bh, border);
        int actualW = textW >= 0 ? textW : font.width(text);
        graphics.drawString(font, text, bx + (bw - actualW) / 2, by + (bh - 8) / 2, finalTextColor, false);
    }

    public static String formatCompactNumber(double val) {
        return FormatUtil.formatCompactNumber(val);
    }

    public static String formatRate(double rate, IngredientStack stack) {
        return FormatUtil.formatRate(rate, stack);
    }

    public static String formatRate(double rate, boolean isFluid) {
        return FormatUtil.formatRate(rate, isFluid);
    }

    public static String formatConnectedFraction(double connected, double required, boolean isFluid, String symbol) {
        return FormatUtil.formatConnectedFraction(connected, required, isFluid, symbol);
    }

    private static void renderLOD(NodeWidget widget, GuiGraphics graphics, Font font, int x, int y, int cardW, int height, RecipeNode node) {
        int cardBg = node.isModule() ? 0xF01D172E : (node.isGenerator() ? 0xF0122218 : 0xF01E222B);
        graphics.fill(x, y, x + cardW, y + height, cardBg);

        int headerColor = node.isModule() ? 0xFF3D2A5E : (node.isGenerator() ? 0xFF1E482E : 0xFF353C4D);
        graphics.fill(x, y, x + cardW, y + NodeWidget.HEADER_HEIGHT, headerColor);

        int outlineColor = node.isModule() ? 0xFF9955FF : (node.isBaseNode() ? 0xFFFFD700 : (node.isGenerator() ? 0xFF33AA66 : 0xFF3D4455));
        graphics.renderOutline(x, y, cardW, height, outlineColor);

        // Header Title (compact)
        String title = (node.isModule() ? "▦ " : (node.isBaseNode() ? "★ " : (node.isGenerator() ? "⚡ " : ""))) + node.getName();
        int maxTitleChars = Math.max(6, (cardW - 12) / 6);
        if (title.length() > maxTitleChars) title = title.substring(0, Math.max(2, maxTitleChars - 2)) + "...";
        graphics.drawString(font, title, x + 6, y + 6, node.isModule() ? 0xFFFFB3FF : (node.isBaseNode() ? 0xFFFFE066 : (node.isGenerator() ? 0xFF77FFAA : 0xFFE0E0E0)), false);

        // Count Text
        String countStr = String.format("Count: %.2f", node.getMachineCount());
        graphics.drawString(font, countStr, x + 6, y + NodeWidget.HEADER_HEIGHT + 6, 0xFF55FFFF, false);

        // Input & Output Port Color Dots
        for (int i = 0; i < node.getInputs().size(); i++) {
            int px = Math.round(widget.getInputPortX(i));
            int py = Math.round(widget.getInputPortY(i));
            graphics.fill(px - 3, py - 3, px + 3, py + 3, 0xFF5599FF);
        }
        for (int i = 0; i < node.getOutputs().size(); i++) {
            int px = Math.round(widget.getOutputPortX(i));
            int py = Math.round(widget.getOutputPortY(i));
            int dotColor = node.isOutputPortVoided(i) ? 0xFFA855F7 : 0xFF55FF88;
            graphics.fill(px - 3, py - 3, px + 3, py + 3, dotColor);
        }
    }

    private static void renderRerouteNode(NodeWidget widget, GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        RecipeNode node = widget.getNode();
        boolean isSelected = false;
        if (Minecraft.getInstance().screen instanceof BoardScreen bs) {
            isSelected = bs.isNodeSelected(node.getId());
        }
        boolean isHovered = widget.isPointInside(mouseX, mouseY);
        boolean isFlipped = node.isFlipped();

        // 1. Background Capsule (32x32)
        int bg = isHovered ? 0xF0334155 : 0xF01E293B;
        graphics.fill(x + 2, y + 2, x + 30, y + 30, bg);

        int border;
        boolean isGlow = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isJunctionGlowing(node.getId());
        if (isGlow) {
            border = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF00FF88);
        } else if (isSelected) {
            border = 0xFF00FFFF;
        } else if (node.isBaseNode()) {
            border = 0xFFFFD700;
        } else if (node.isVoidSink()) {
            border = 0xFFA855F7;
        } else if (node.isFixedDrain()) {
            border = 0xFFF97316;
        } else if (node.isInfiniteSupply()) {
            border = 0xFF38BDF8;
        } else if (node.isExternalSupply()) {
            border = 0xFF34D399;
        } else {
            border = isHovered ? 0xFF94A3B8 : 0xFF64748B;
        }
        graphics.renderOutline(x + 2, y + 2, 28, 28, border);
        if (isGlow) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, border);
        } else if (isSelected) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, 0x8800FFFF);
        } else if (node.isBaseNode()) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, 0x88FFD700);
        } else if (node.isVoidSink()) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, 0x44A855F7);
        } else if (node.isFixedDrain()) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, 0x44F97316);
        } else if (node.isInfiniteSupply()) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, 0x4438BDF8);
        } else if (node.isExternalSupply()) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, 0x4434D399);
        }

        // External Supply Badge (Top: ∞, VOID, -rate, or +rate)
        if (node.isInfiniteSupply()) {
            graphics.fill(x + 19, y + 1, x + 31, y + 10, 0xEE0B132B);
            graphics.renderOutline(x + 19, y + 1, 12, 9, 0xFF38BDF8);
            graphics.drawString(font, "∞", x + 22, y + 1, 0xFF38BDF8, false);
        } else if (node.isVoidSink()) {
            graphics.fill(x + 13, y + 1, x + 31, y + 10, 0xEE1E1035);
            graphics.renderOutline(x + 13, y + 1, 18, 9, 0xFFA855F7);
            graphics.drawString(font, "VOID", x + 15, y + 1, 0xFFA855F7, false);
        } else if (node.isFixedDrain()) {
            IngredientStack rStack = !node.getInputs().isEmpty() ? node.getInputs().get(0) : null;
            boolean isFluid = rStack != null && rStack.isFluid();
            String rateStr = "-" + com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(node.getExternalDrainRate(), isFluid);
            int rw = font.width(rateStr);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.pose().scale(0.7f, 0.7f, 1.0f);
            int rx = (int) ((x + 16) / 0.7f - rw / 2);
            int ry = (int) ((y + 1) / 0.7f);
            graphics.drawString(font, rateStr, rx, ry, 0xFFF87171, true);
            graphics.pose().popPose();
        } else if (node.isExternalSupply()) {
            IngredientStack rStack = !node.getInputs().isEmpty() ? node.getInputs().get(0) : null;
            boolean isFluid = rStack != null && rStack.isFluid();
            String rateStr = "+" + com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(node.getExternalSupplyRate(), isFluid);
            int rw = font.width(rateStr);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.pose().scale(0.7f, 0.7f, 1.0f);
            int rx = (int) ((x + 16) / 0.7f - rw / 2);
            int ry = (int) ((y + 1) / 0.7f);
            graphics.drawString(font, rateStr, rx, ry, 0xFF34D399, true);
            graphics.pose().popPose();
        }

        if (node.isBaseNode()) {
            graphics.fill(x + 1, y + 21, x + 11, y + 31, 0xEE2A2005);
            graphics.renderOutline(x + 1, y + 21, 10, 10, 0xFFFFD700);
            graphics.drawString(font, "⌖", x + 3, y + 22, 0xFFFFEE55, false);
        }

        // 2. Input Port Dot (Left: x, y + 14..18 if !isFlipped, Right: x + 28..32 if isFlipped)
        boolean inHover = widget.getHoveredInputPortIndex(mouseX, mouseY) >= 0;
        int inDotColor = inHover ? 0xFF00FFFF : 0xFF38BDF8;
        int inDotX = isFlipped ? (x + 28) : x;
        graphics.fill(inDotX, y + 14, inDotX + 4, y + 18, inDotColor);
        graphics.renderOutline(inDotX - 1, y + 13, 6, 6, inHover ? 0xFFFFFFFF : 0x88000000);

        // 3. Output Port Dot (Right: x + 28..32 if !isFlipped, Left: x, y + 14..18 if isFlipped)
        boolean outHover = widget.getHoveredOutputPortIndex(mouseX, mouseY) >= 0;
        int outDotColor = outHover ? 0xFF00FFFF : 0xFF38BDF8;
        int outDotX = isFlipped ? x : (x + 28);
        graphics.fill(outDotX, y + 14, outDotX + 4, y + 18, outDotColor);
        graphics.renderOutline(outDotX - 1, y + 13, 6, 6, outHover ? 0xFFFFFFFF : 0x88000000);

        // 4. Center Icon: Ingredient Icon or Direction Arrow (➔ / ⬅)
        IngredientStack stack = !node.getInputs().isEmpty() ? node.getInputs().get(0) : null;
        if (stack != null) {
            IngredientRenderer.render(graphics, stack, x + 8, y + 8);
        } else {
            graphics.drawString(font, isFlipped ? "⬅" : "➔", x + 12, y + 12, 0xFF94A3B8, false);
        }

        // 5. Target Batch Amount (Inline Editor or Display Text)
        NodeTargetBatchEditor batchEditor = widget.getTargetBatchEditor();
        if (batchEditor.isEditing()) {
            String editStr = batchEditor.getDisplayText();
            int textW = font.width(editStr);
            int boxW = Math.max(28, textW + 6);
            int boxX = x + 16 - boxW / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.fill(boxX, y + 19, boxX + boxW, y + 29, 0xF00F172A);
            graphics.renderOutline(boxX, y + 19, boxW, 10, 0xFF38BDF8);
            graphics.drawString(font, editStr, boxX + (boxW - textW) / 2, y + 20, 0xFFFFFFFF, false);
            graphics.pose().popPose();
        } else if (node.hasTargetBatch()) {
            boolean isFluid = stack != null && stack.isFluid();
            String amountStr = FormatUtil.formatBatchAmount(node.getTargetBatchAmount(), isFluid);
            int textW = font.width(amountStr);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            if (textW > 26) {
                graphics.pose().scale(0.8f, 0.8f, 1.0f);
                int scaledX = (int) ((x + 16) / 0.8f - textW / 2);
                int scaledY = (int) ((y + 21) / 0.8f);
                graphics.drawString(font, amountStr, scaledX, scaledY, 0xFFFFEE55, true);
            } else {
                graphics.drawString(font, amountStr, x + 16 - textW / 2, y + 21, 0xFFFFEE55, true);
            }
            graphics.pose().popPose();
        }

        if (node.hasTargetBatch() || batchEditor.isEditing()) {
            FlowGraph graph = widget.getParent() != null ? widget.getParent().getGraph() : (Minecraft.getInstance().screen instanceof BoardScreen bs ? bs.getGraph() : null);
            renderJunctionTimeBadge(graphics, font, graph, node, x, y);
        }
    }

    private static void renderJunctionTimeBadge(GuiGraphics graphics, Font font, FlowGraph graph, RecipeNode node, int x, int y) {
        boolean isInputSource = isInputSourceJunction(graph, node);
        double targetAmount = node.getTargetBatchAmount();

        String badgeStr;
        int badgeBorder;
        int textColor;

        if (isInputSource) {
            double drainRate = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateNetOutflowRate(graph, node);
            double depletionSec = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateDepletionTime(graph, node, targetAmount, drainRate);
            badgeStr = "DT: " + FormatUtil.formatETA(depletionSec);
            badgeBorder = Double.isInfinite(depletionSec) ? 0xFF475569 : 0xFF0284C7;
            textColor = Double.isInfinite(depletionSec) ? 0xFF94A3B8 : 0xFF7DD3FC;
        } else {
            double netRate = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateNetInflowRate(graph, node, 0);
            double etaSec = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateETA(graph, node, targetAmount, netRate);
            badgeStr = "ET: " + FormatUtil.formatETA(etaSec);
            badgeBorder = Double.isInfinite(etaSec) ? 0xFF7F1D1D : (netRate > 0 ? 0xFF15803D : 0xFF475569);
            textColor = Double.isInfinite(etaSec) ? 0xFFFCA5A5 : (netRate > 0 ? 0xFF86EFAC : 0xFFCBD5E1);
        }

        int textW = font.width(badgeStr);
        int badgeW = Math.max(32, textW + 6);
        int badgeX = x + 16 - badgeW / 2;
        int badgeY = y + 33;
        int badgeBg = 0xEE0B132B;

        graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, badgeBg);
        graphics.renderOutline(badgeX, badgeY, badgeW, 11, badgeBorder);
        graphics.drawString(font, badgeStr, badgeX + (badgeW - textW) / 2, badgeY + 2, textColor, false);
    }

    public static boolean isInputSourceJunction(FlowGraph graph, RecipeNode node) {
        if (graph == null || node == null || !node.isReroute()) return false;
        if (node.isExternalSupply() || node.isInfiniteSupply() || node.isVoidSink() || node.isFixedDrain()) return false;
        boolean hasIncoming = false;
        boolean hasOutgoing = false;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(node.getId())) {
                hasIncoming = true;
            }
            if (edge.fromNodeId().equals(node.getId())) {
                hasOutgoing = true;
            }
        }
        return !hasIncoming && hasOutgoing;
    }

    private static void renderIngredient(GuiGraphics graphics, IngredientStack stack, int x, int y) {
        IngredientRenderer.render(graphics, stack, x, y);
    }

}




