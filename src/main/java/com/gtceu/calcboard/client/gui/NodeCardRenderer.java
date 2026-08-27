package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.FlowGraphSolver;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.OverclockMode;
import com.gtceu.calcboard.api.RecipeNode;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
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

        double zoom = (Minecraft.getInstance().screen instanceof BoardScreen bs) ? bs.getZoom() : BoardScreen.lastZoom;
        if (zoom < 0.28) {
            renderLOD(widget, graphics, font, x, y, cardW, height, node);
            return;
        }

        FlowGraph graph = widget.getParent() != null ? widget.getParent().getGraph() : (Minecraft.getInstance().screen instanceof BoardScreen bs ? bs.getGraph() : null);
        boolean isOperational = node.isOperational(graph);

        // 1. Card Background & Golden/Metallic/Module Outlines
        int cardBg = !isOperational ? 0xF0251417 : (node.isModule() ? 0xF01D172E : (node.isFusion() ? 0xF022132D : (node.isGenerator() ? 0xF0122218 : 0xF01E222B)));
        graphics.fill(x, y, x + cardW, y + height, cardBg);

        int headerColor = !isOperational
                ? (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF521C22 : 0xFF3D1419)
                : (node.isModule()
                    ? (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF3D2A5E : 0xFF2A1C42)
                    : (node.isFusion()
                        ? (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF4A1E6D : 0xFF35154E)
                        : (node.isGenerator()
                            ? (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF1E482E : 0xFF163824)
                            : (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF353C4D : 0xFF2A2E39))));
        graphics.fill(x, y, x + cardW, y + NodeWidget.HEADER_HEIGHT, headerColor);

        boolean isSelected = false;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof BoardScreen bs) {
            isSelected = bs.isNodeSelected(node.getId());
        }

        int outlineColor;
        if (isSelected) {
            outlineColor = 0xFF00FFFF;
        } else if (!isOperational) {
            float pulse = (float) (0.60 + 0.40 * Math.sin(System.currentTimeMillis() / 200.0));
            int red = (int) (170 + 85 * pulse);
            outlineColor = 0xFF000000 | (red << 16) | (0x33 << 8) | 0x33;
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
        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, cardW + 2, height + 2, 0x8800FFFF);
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x8800FFFF);
        } else if (!isOperational) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, (outlineColor & 0x00FFFFFF) | 0x88000000);
        } else if (node.isModule()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x559955FF);
        } else if (node.isFusion()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x66CC44FF);
        } else if (node.isBaseNode()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x88FFD700);
        } else if (node.isGenerator()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x4433AA66);
        }

        // 2. Machine Icon & Header Title
        int titleX = x + 6;
        ResourceLocation iconId = node.getMachineIcon();
        if (iconId != null) {
            var item = ForgeRegistries.ITEMS.getValue(iconId);
            if (item != null && item != Items.AIR) {
                EmiStack emiStack = EmiStack.of(item);
                emiStack.render(graphics, x + 3, y + 2, 0, EmiIngredient.RENDER_ICON);
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                titleX = x + 22;
            }
        }

        NodeNameEditor nameEditor = widget.getNameEditor();
        int headerBtnMargin = node.isModule() ? 76 : 58;
        if (nameEditor != null && nameEditor.isEditing()) {
            String editTxt = nameEditor.getDisplayText();
            int editW = Math.max(60, cardW - (titleX - x) - headerBtnMargin);
            graphics.fill(titleX - 2, y + 2, titleX + editW, y + 18, 0xFF0D1B2A);
            graphics.renderOutline(titleX - 2, y + 2, editW, 16, 0xFF55FFFF);
            graphics.drawString(font, editTxt, titleX + 2, y + 6, 0xFF55FFFF, false);
        } else {
            String title = (!isOperational ? "§c⚠ " : "") + (node.isModule() ? "§d📦 " : (node.isFusion() ? "§d⚛ " : (node.isBaseNode() ? "§6★ " : (node.isGenerator() ? "§a⚡ " : "")))) + node.getName();
            int maxTitleChars = Math.max(8, (cardW - (titleX - x) - headerBtnMargin) / 6);
            if (title.length() > maxTitleChars) title = title.substring(0, Math.max(2, maxTitleChars - 2)) + "...";
            graphics.drawString(font, title, titleX, y + 6, !isOperational ? 0xFFFF7777 : (node.isModule() ? 0xFFFFB3FF : (node.isFusion() ? 0xFFFFB3FF : (node.isBaseNode() ? 0xFFFFE066 : (node.isGenerator() ? 0xFF77FFAA : 0xFFE0E0E0)))), false);
        }

        // 3. Module Expand Button [⤢] (if module)
        if (node.isModule()) {
            int expandX = x + cardW - 72;
            int expandY = y + 2;
            boolean expandHover = widget.isExpandButtonHovered(mouseX, mouseY);
            graphics.fill(expandX, expandY, expandX + 16, expandY + 16, expandHover ? 0xFF5A3A8A : 0xFF352055);
            graphics.renderOutline(expandX, expandY, 16, 16, expandHover ? 0xFFCC88FF : 0xFF7744AA);
            graphics.drawCenteredString(font, "⤢", expandX + 8, expandY + 3, expandHover ? 0xFFFFFFFF : 0xFFDDAAFF);
        }

        // 4. Direction / Flip Button [➔] or [⬅]
        int flipX = x + cardW - 54;
        int flipY = y + 2;
        boolean flipHover = widget.isFlipButtonHovered(mouseX, mouseY);
        int flipBg = flipHover ? 0xFF3A4456 : 0xFF222834;
        int flipBorder = flipHover ? 0xFF88AAFF : 0xFF4A5568;
        graphics.fill(flipX, flipY, flipX + 16, flipY + 16, flipBg);
        graphics.renderOutline(flipX, flipY, 16, 16, flipBorder);
        graphics.drawCenteredString(font, node.isFlipped() ? "⬅" : "➔", flipX + 8, flipY + 4, flipHover ? 0xFFFFFFFF : 0xFFAAAAAA);

        // 5. Target Base Node Toggle Button [🎯]
        int targetX = x + cardW - 36;
        int targetY = y + 2;
        boolean isTargetGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isNodeBaseTargetButtonGlowing(node.getId());
        boolean targetHover = widget.isTargetButtonHovered(mouseX, mouseY);
        int targetBg = node.isBaseNode() ? 0xFF886600 : (isTargetGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBgColor(0xFF222834) : (targetHover ? 0xFF3A4456 : 0xFF222834));
        int targetBorder = node.isBaseNode() ? 0xFFFFD700 : (isTargetGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF4A5568) : (targetHover ? 0xFF88AAFF : 0xFF4A5568));
        graphics.fill(targetX, targetY, targetX + 16, targetY + 16, targetBg);
        graphics.renderOutline(targetX, targetY, 16, 16, targetBorder);
        if (isTargetGlowing) {
            graphics.renderOutline(targetX - 1, targetY - 1, 18, 18, targetBorder & 0x77FFFFFF);
        }
        graphics.drawCenteredString(font, "🎯", targetX + 8, targetY + 4, (node.isBaseNode() || isTargetGlowing) ? 0xFFFFEE55 : 0xFFAAAAAA);

        // 6. Close/Delete Button [X]
        int closeX = x + cardW - 18;
        int closeY = y + 2;
        boolean isCloseGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isNodeCloseButtonGlowing(node.getId());
        boolean closeHover = widget.isCloseButtonHovered(mouseX, mouseY);
        int closeBg = (closeHover || isCloseGlowing) ? 0xFFFF4444 : 0x44FF4444;
        graphics.fill(closeX, closeY, closeX + 16, closeY + 16, closeBg);
        if (isCloseGlowing) {
            graphics.renderOutline(closeX - 1, closeY - 1, 18, 18, 0xFFFF5555);
        }
        graphics.drawString(font, "x", closeX + 5, closeY + 3, 0xFFFFFFFF, false);

        // 5. Machine Count Controls: Count: [-] [Input Box] [+] [/2] [x2]
        int ctrlY = y + NodeWidget.HEADER_HEIGHT + 6;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.count"), x + 6, ctrlY + 3, !isOperational ? 0xFFFF8888 : 0xFFAAAAAA, false);

        int countBtnCol = !isOperational ? 0xFFFF8888 : 0xFFFFFFFF;
        int countMinusX = x + 36;
        drawBtn(graphics, font, "-", countMinusX, ctrlY, 14, 14, mouseX, mouseY, countBtnCol, !isOperational, false);

        // Interactive Numeric Count Box
        NodeCountEditor countEditor = widget.getCountEditor();
        String countText = countEditor.getDisplayText();
        int countBoxW = Math.max(28, font.width(countText) + 6);
        int countBoxX = countMinusX + 16;
        boolean countHover = mouseX >= countBoxX && mouseX <= countBoxX + countBoxW && mouseY >= ctrlY && mouseY <= ctrlY + 14;
        boolean isEditing = countEditor.isEditing();
        int countBg = isEditing ? 0xFF0D1B2A : (!isOperational ? (countHover ? 0xFF36161A : 0xFF220E12) : (countHover ? 0xFF252A36 : 0xFF14171E));
        int countBorder = isEditing ? 0xFF55FFFF : (!isOperational ? (countHover ? 0xFFFF6666 : 0xFF993333) : (countHover ? 0xFF5577AA : 0xFF3D4455));
        graphics.fill(countBoxX, ctrlY, countBoxX + countBoxW, ctrlY + 14, countBg);
        graphics.renderOutline(countBoxX, ctrlY, countBoxW, 14, countBorder);
        graphics.drawCenteredString(font, countText, countBoxX + countBoxW / 2, ctrlY + 3, isEditing ? 0xFF55FFFF : (!isOperational ? 0xFFFFB3B3 : 0xFFFFFFAA));

        int afterCountX = countBoxX + countBoxW + 2;
        drawBtn(graphics, font, "+", afterCountX, ctrlY, 14, 14, mouseX, mouseY, countBtnCol, !isOperational, false);
        drawBtn(graphics, font, "/2", afterCountX + 16, ctrlY, 16, 14, mouseX, mouseY, countBtnCol, !isOperational, false);
        drawBtn(graphics, font, "x2", afterCountX + 34, ctrlY, 16, 14, mouseX, mouseY, countBtnCol, !isOperational, false);

        int row2Y = ctrlY + 18;

        if (node.isModule()) {
            // Module Contained Machines Badge on the right (compact)
            String machinesBadge = String.format("§d📦 %d%s", node.getContainedMachineCount(), Component.translatable("gui.gtcalcboard.machine_unit").getString());
            int badgeW = font.width(machinesBadge);
            graphics.drawString(font, machinesBadge, x + cardW - 6 - badgeW, ctrlY + 3, 0xFFFFFFFF, false);
        } else if (!node.getAddons().isEmpty()) {
            // Mini Installed Addons Tray on the right of Count row
            List<com.gtceu.calcboard.api.MachineAddon> addons = node.getAddons();
            int maxIcons = Math.min(addons.size(), 3);
            int trayX = x + cardW - 6;
            for (int a = maxIcons - 1; a >= 0; a--) {
                var addon = addons.get(a);
                trayX -= 15;
                boolean iconHover = mouseX >= trayX && mouseX <= trayX + 14 && mouseY >= ctrlY - 1 && mouseY <= ctrlY + 13;
                graphics.fill(trayX, ctrlY - 1, trayX + 14, ctrlY + 13, iconHover ? 0xFF2F3B4D : 0xFF181D26);
                graphics.renderOutline(trayX, ctrlY - 1, 14, 14, iconHover ? 0xFF58D3FF : 0xFF354054);
                ItemStack sample = addon.getRenderItemStack();
                if (sample != null && !sample.isEmpty()) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(trayX + 2.0, ctrlY + 1.0, 0.0);
                    graphics.pose().scale(0.625f, 0.625f, 1.0f);
                    graphics.renderItem(sample, 0, 0);
                    if (sample.getCount() > 1) {
                        graphics.renderItemDecorations(font, sample, 0, 0);
                    }
                    graphics.pose().popPose();
                }
                if (addon.getCategory() == com.gtceu.calcboard.api.MachineAddon.Category.REFLECTOR && !node.hasValidReflector()) {
                    graphics.fill(trayX + 7, ctrlY - 2, trayX + 15, ctrlY + 6, 0xFFFF2222);
                    graphics.drawString(font, "!", trayX + 9, ctrlY - 3, 0xFFFFFFFF, false);
                }
            }
            if (addons.size() > 3) {
                String extraStr = "+" + (addons.size() - 3);
                int extraW = font.width(extraStr);
                trayX -= (extraW + 3);
                graphics.drawString(font, "§b" + extraStr, trayX, ctrlY + 2, 0xFFFFFFFF, false);
            }
        }

        if (!node.isModule()) {
            com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            boolean isGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isMachineConfigButtonGlowing(node.getId());
            adapter.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
        }

        // 8. Recipe Energy & Duration Info
        int infoY = node.isModule() ? (ctrlY + 18) : (row2Y + 18);
        double durationSec = node.getEffectiveDurationSeconds();
        double effCps = node.getEffectiveCyclesPerSecond();

        String rightStr;
        if (!isOperational) {
            rightStr = String.format(java.util.Locale.ROOT, "§c%.2fs §7(§c0/s§7)", durationSec);
        } else if (node.isModule()) {
            rightStr = "§d§l[📦 " + Component.translatable("gui.gtcalcboard.module").getString() + "]";
        } else {
            rightStr = String.format(java.util.Locale.ROOT, "§b%.2fs §7(§f%s/s§7)", durationSec, formatCompactNumber(effCps));
        }
        int rightW = font.width(rightStr);
        graphics.drawString(font, rightStr, x + cardW - 6 - rightW, infoY, 0xFFFFFFFF, false);

        int maxPowerW = Math.max(20, (cardW - 12) - rightW - 4);
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        String powerStr;
        if (node.getEnergyType() == EnergyType.NONE) {
            powerStr = Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        } else if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            powerStr = "§6♨";
        } else if (!isOperational) {
            GTVoltageTier tier = node.getTargetTier();
            String tierName = tier != null ? tier.getName() : "LV";
            powerStr = "§c0.0 EU/t §7(0A " + tierName + ")";
        } else {
            powerStr = adapter.formatEnergyStats(node, BoardManager.getInstance().getPowerDisplayMode());
        }
        String fittedPowerStr = font.plainSubstrByWidth(powerStr, maxPowerW);
        graphics.drawString(font, fittedPowerStr, x + 6, infoY, 0xFFFFFFFF, false);

        // Separator Line
        int sepY = infoY + 14;
        graphics.fill(x + 4, sepY, x + cardW - 4, sepY + 1, !isOperational ? 0xFF5A2228 : 0xFF353C4D);

        // 9. Input & Output Ports Listing
        int contentY = sepY + 4;
        List<IngredientStack> inputs = node.getInputs();
        List<IngredientStack> outputs = node.getOutputs();
        int maxRows = Math.max(inputs.size(), outputs.size());
        boolean isFlipped = node.isFlipped();

        for (int i = 0; i < maxRows; i++) {
            int rowY = contentY + i * 18;
            boolean hasInput = (i < inputs.size());
            boolean hasOutput = (i < outputs.size());

            // 1. Render Left Slot: Input (if !isFlipped) or Output (if isFlipped)
            if (!isFlipped && hasInput) {
                IngredientStack in = inputs.get(i);
                double rate = widget.getInputRate(i);
                int inPortX = x + 4;
                int inPortY = rowY + 5;

                FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getInputPortStats(node, i) : null;
                boolean isConnected = stats != null && stats.isConnected();
                boolean isBalanced = stats != null && stats.isBalanced();
                boolean isDeficit = stats != null && stats.isInputDeficit();

                boolean isPortGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isPortGlowing(node.getId(), true, i);
                int portColor;
                if (!isOperational) {
                    portColor = 0xFF77333B;
                } else if (isPortGlowing) {
                    portColor = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF5599FF);
                } else if (!isConnected) {
                    portColor = 0xFF5599FF;
                } else if (isBalanced) {
                    portColor = 0xFF55FF88;
                } else if (isDeficit) {
                    portColor = 0xFFFFAA33;
                } else {
                    portColor = 0xFF55FFFF;
                }
                boolean portHover = mouseX >= x && mouseX <= x + 28 && mouseY >= rowY - 2 && mouseY <= rowY + 16;
                graphics.fill(inPortX, inPortY, inPortX + 6, inPortY + 6, portHover ? 0xFFFFFFFF : portColor);
                if (isPortGlowing) {
                    graphics.renderOutline(inPortX - 2, inPortY - 2, 10, 10, portColor);
                }

                IngredientRenderer.render(graphics, in, x + 12, rowY - 1);
                if (in.hasAlternatives()) {
                    graphics.drawString(font, "§e⟲", x + 21, rowY + 6, 0xFFFFFFFF, true);
                }

                String rateStr;
                int textColor;
                if (!isOperational) {
                    rateStr = "§c-" + formatRate(0.0, in);
                    textColor = 0xFFFF7777;
                } else if (!isConnected) {
                    rateStr = "§7-" + formatRate(rate, in);
                    textColor = 0xFFFFAAAA;
                } else if (isBalanced) {
                    rateStr = "§a" + formatRate(rate, in) + " §2✔";
                    textColor = 0xFFFFFFFF;
                } else if (isDeficit) {
                    rateStr = FormatUtil.formatConnectedInput(stats.connectedRate(), rate, in, true);
                    textColor = 0xFFFFFFFF;
                } else {
                    rateStr = FormatUtil.formatConnectedInput(stats.connectedRate(), rate, in, false);
                    textColor = 0xFFFFFFFF;
                }

                int maxInTextW = hasOutput ? Math.max(20, (cardW / 2) - 34) : (cardW - 36);
                int inTextW = font.width(rateStr);
                if (inTextW > maxInTextW) {
                    rateStr = font.plainSubstrByWidth(rateStr, maxInTextW);
                }
                graphics.drawString(font, rateStr, x + 30, rowY + 4, textColor, false);
            } else if (isFlipped && hasOutput) {
                IngredientStack out = outputs.get(i);
                double rate = widget.getOutputRate(i);
                int outPortX = x + 4;
                int outPortY = rowY + 5;

                FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getOutputPortStats(node, i) : null;
                boolean isConnected = stats != null && stats.isConnected();
                boolean isBalanced = stats != null && stats.isBalanced();
                boolean isSurplus = stats != null && stats.isOutputSurplus();
                boolean isDeficit = stats != null && stats.isOutputDeficit();

                boolean isPortGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isPortGlowing(node.getId(), false, i);
                boolean isInactive = rate <= 0.00001;
                int portColor;
                if (!isOperational) {
                    portColor = 0xFF77333B;
                } else if (isPortGlowing) {
                    portColor = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF55FF88);
                } else if (!isConnected) {
                    portColor = isInactive ? 0xFF444B5A : 0xFF55FF88;
                } else if (isBalanced) {
                    portColor = 0xFF55FF88;
                } else if (isDeficit) {
                    portColor = 0xFFFFAA33;
                } else {
                    portColor = 0xFF55FFFF;
                }
                boolean portHover = mouseX >= x && mouseX <= x + 28 && mouseY >= rowY - 2 && mouseY <= rowY + 16;
                graphics.fill(outPortX, outPortY, outPortX + 6, outPortY + 6, portHover ? 0xFFFFFFFF : portColor);
                if (isPortGlowing) {
                    graphics.renderOutline(outPortX - 2, outPortY - 2, 10, 10, portColor);
                }

                IngredientRenderer.render(graphics, out, x + 12, rowY - 1);

                String rateStr;
                int textColor;
                if (!isOperational) {
                    rateStr = "§c" + formatRate(0.0, out) + " §4⏸";
                    textColor = 0xFFFF7777;
                } else if (!isConnected) {
                    if (isInactive) {
                        rateStr = "§8+0/s §7(0%)";
                        textColor = 0xFF778092;
                    } else {
                        rateStr = "§a+" + formatRate(rate, out);
                        textColor = 0xFFAAFFAA;
                    }
                } else if (isBalanced) {
                    rateStr = "§a" + formatRate(rate, out) + " §2✔";
                    textColor = 0xFFFFFFFF;
                } else if (isSurplus) {
                    rateStr = FormatUtil.formatConnectedOutput(rate, stats.connectedRate(), out, false);
                    textColor = 0xFFFFFFFF;
                } else {
                    rateStr = FormatUtil.formatConnectedOutput(rate, stats.connectedRate(), out, true);
                    textColor = 0xFFFFFFFF;
                }

                int maxOutTextW = hasInput ? Math.max(20, (cardW / 2) - 34) : (cardW - 36);
                int textW = font.width(rateStr);
                if (textW > maxOutTextW) {
                    rateStr = font.plainSubstrByWidth(rateStr, maxOutTextW);
                }
                graphics.drawString(font, rateStr, x + 30, rowY + 4, textColor, false);
            }

            // 2. Render Right Slot: Output (if !isFlipped) or Input (if isFlipped)
            if (!isFlipped && hasOutput) {
                IngredientStack out = outputs.get(i);
                double rate = widget.getOutputRate(i);
                int outPortX = x + cardW - 10;
                int outPortY = rowY + 5;

                FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getOutputPortStats(node, i) : null;
                boolean isConnected = stats != null && stats.isConnected();
                boolean isBalanced = stats != null && stats.isBalanced();
                boolean isSurplus = stats != null && stats.isOutputSurplus();
                boolean isDeficit = stats != null && stats.isOutputDeficit();

                boolean isPortGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isPortGlowing(node.getId(), false, i);
                boolean isInactive = rate <= 0.00001;
                int portColor;
                if (!isOperational) {
                    portColor = 0xFF77333B;
                } else if (isPortGlowing) {
                    portColor = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF55FF88);
                } else if (!isConnected) {
                    portColor = isInactive ? 0xFF444B5A : 0xFF55FF88;
                } else if (isBalanced) {
                    portColor = 0xFF55FF88;
                } else if (isDeficit) {
                    portColor = 0xFFFFAA33;
                } else {
                    portColor = 0xFF55FFFF;
                }
                boolean portHover = mouseX >= x + cardW - 28 && mouseX <= x + cardW && mouseY >= rowY - 2 && mouseY <= rowY + 16;
                graphics.fill(outPortX, outPortY, outPortX + 6, outPortY + 6, portHover ? 0xFFFFFFFF : portColor);
                if (isPortGlowing) {
                    graphics.renderOutline(outPortX - 2, outPortY - 2, 10, 10, portColor);
                }

                String rateStr;
                int textColor;
                if (!isOperational) {
                    rateStr = "§c" + formatRate(0.0, out) + " §4⏸";
                    textColor = 0xFFFF7777;
                } else if (!isConnected) {
                    if (isInactive) {
                        rateStr = "§8+0/s §7(0%)";
                        textColor = 0xFF778092;
                    } else {
                        rateStr = "§a+" + formatRate(rate, out);
                        textColor = 0xFFAAFFAA;
                    }
                } else if (isBalanced) {
                    rateStr = "§a" + formatRate(rate, out) + " §2✔";
                    textColor = 0xFFFFFFFF;
                } else if (isSurplus) {
                    rateStr = FormatUtil.formatConnectedOutput(rate, stats.connectedRate(), out, false);
                    textColor = 0xFFFFFFFF;
                } else {
                    rateStr = FormatUtil.formatConnectedOutput(rate, stats.connectedRate(), out, true);
                    textColor = 0xFFFFFFFF;
                }

                int maxOutTextW = hasInput ? Math.max(20, (cardW / 2) - 34) : (cardW - 36);
                int textW = font.width(rateStr);
                if (textW > maxOutTextW) {
                    rateStr = font.plainSubstrByWidth(rateStr, maxOutTextW);
                    textW = font.width(rateStr);
                }
                graphics.drawString(font, rateStr, x + cardW - 30 - textW, rowY + 4, textColor, false);

                IngredientRenderer.render(graphics, out, x + cardW - 28, rowY - 1);
            } else if (isFlipped && hasInput) {
                IngredientStack in = inputs.get(i);
                double rate = widget.getInputRate(i);
                int inPortX = x + cardW - 10;
                int inPortY = rowY + 5;

                FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getInputPortStats(node, i) : null;
                boolean isConnected = stats != null && stats.isConnected();
                boolean isBalanced = stats != null && stats.isBalanced();
                boolean isDeficit = stats != null && stats.isInputDeficit();

                boolean isPortGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isPortGlowing(node.getId(), true, i);
                int portColor;
                if (!isOperational) {
                    portColor = 0xFF77333B;
                } else if (isPortGlowing) {
                    portColor = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF5599FF);
                } else if (!isConnected) {
                    portColor = 0xFF5599FF;
                } else if (isBalanced) {
                    portColor = 0xFF55FF88;
                } else if (isDeficit) {
                    portColor = 0xFFFFAA33;
                } else {
                    portColor = 0xFF55FFFF;
                }
                boolean portHover = mouseX >= x + cardW - 28 && mouseX <= x + cardW && mouseY >= rowY - 2 && mouseY <= rowY + 16;
                graphics.fill(inPortX, inPortY, inPortX + 6, inPortY + 6, portHover ? 0xFFFFFFFF : portColor);
                if (isPortGlowing) {
                    graphics.renderOutline(inPortX - 2, inPortY - 2, 10, 10, portColor);
                }

                String rateStr;
                int textColor;
                if (!isOperational) {
                    rateStr = "§c-" + formatRate(0.0, in);
                    textColor = 0xFFFF7777;
                } else if (!isConnected) {
                    rateStr = "§7-" + formatRate(rate, in);
                    textColor = 0xFFFFAAAA;
                } else if (isBalanced) {
                    rateStr = "§a" + formatRate(rate, in) + " §2✔";
                    textColor = 0xFFFFFFFF;
                } else if (isDeficit) {
                    rateStr = FormatUtil.formatConnectedInput(stats.connectedRate(), rate, in, true);
                    textColor = 0xFFFFFFFF;
                } else {
                    rateStr = FormatUtil.formatConnectedInput(stats.connectedRate(), rate, in, false);
                    textColor = 0xFFFFFFFF;
                }

                int maxInTextW = hasOutput ? Math.max(20, (cardW / 2) - 34) : (cardW - 36);
                int inTextW = font.width(rateStr);
                if (inTextW > maxInTextW) {
                    rateStr = font.plainSubstrByWidth(rateStr, maxInTextW);
                    inTextW = font.width(rateStr);
                }
                graphics.drawString(font, rateStr, x + cardW - 30 - inTextW, rowY + 4, textColor, false);

                IngredientRenderer.render(graphics, in, x + cardW - 28, rowY - 1);
                if (in.hasAlternatives()) {
                    graphics.drawString(font, "§e⟲", x + cardW - 19, rowY + 6, 0xFFFFFFFF, true);
                }
            }
        }

        // 10. Corner Resize Handle (Bottom-Right ⤡)
        int handleX = x + cardW - 8;
        int handleY = y + height - 8;
        boolean handleHover = widget.isResizeHandleHovered(mouseX, mouseY);
        int handleColor = handleHover ? 0xFF55FFFF : 0x88657595;
        graphics.drawString(font, "⤡", handleX - 2, handleY - 3, handleColor, false);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, 0xFFFFFFFF, false, false);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, textColor, false, false);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor, boolean isGlowing) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, textColor, false, isGlowing);
    }

    public static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor, boolean isAlert, boolean isGlowing) {
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
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, finalTextColor);
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
        String title = (node.isModule() ? "📦 " : (node.isBaseNode() ? "★ " : (node.isGenerator() ? "⚡ " : ""))) + node.getName();
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
            graphics.fill(px - 3, py - 3, px + 3, py + 3, 0xFF55FF88);
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

        int border = isSelected ? 0xFF00FFFF : (isHovered ? 0xFF94A3B8 : 0xFF64748B);
        graphics.renderOutline(x + 2, y + 2, 28, 28, border);
        if (isSelected) {
            graphics.renderOutline(x + 1, y + 1, 30, 30, 0x8800FFFF);
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

        // 6. Estimated Time (ET) Badge below the node
        if (node.hasTargetBatch() || batchEditor.isEditing()) {
            FlowGraph graph = widget.getParent() != null ? widget.getParent().getGraph() : (Minecraft.getInstance().screen instanceof BoardScreen bs ? bs.getGraph() : null);
            double netRate = com.gtceu.calcboard.api.ProductionETACalculator.calculateNetInflowRate(graph, node, 0);
            double targetAmount = node.getTargetBatchAmount();
            double etaSec = com.gtceu.calcboard.api.ProductionETACalculator.calculateETA(targetAmount, netRate);
            String etaStr = "ET: " + FormatUtil.formatETA(etaSec);

            int etaW = font.width(etaStr);
            int badgeW = Math.max(32, etaW + 6);
            int badgeX = x + 16 - badgeW / 2;
            int badgeY = y + 33;

            int badgeBg = 0xEE0B132B;
            int badgeBorder = Double.isInfinite(etaSec) ? 0xFF7F1D1D : (netRate > 0 ? 0xFF15803D : 0xFF475569);
            int etaTextColor = Double.isInfinite(etaSec) ? 0xFFFCA5A5 : (netRate > 0 ? 0xFF86EFAC : 0xFFCBD5E1);

            graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, badgeBg);
            graphics.renderOutline(badgeX, badgeY, badgeW, 11, badgeBorder);
            graphics.drawString(font, etaStr, badgeX + (badgeW - etaW) / 2, badgeY + 2, etaTextColor, false);
        }
    }
}
