package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BoardManager;
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

        double zoom = (Minecraft.getInstance().screen instanceof BoardScreen bs) ? bs.getZoom() : BoardScreen.lastZoom;
        if (zoom < 0.28) {
            renderLOD(widget, graphics, font, x, y, cardW, height, node);
            return;
        }

        // 1. Card Background & Golden/Metallic/Module Outlines
        int cardBg = node.isModule() ? 0xF01D172E : (node.isGenerator() ? 0xF0122218 : 0xF01E222B);
        graphics.fill(x, y, x + cardW, y + height, cardBg);

        int headerColor = node.isModule()
                ? (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF3D2A5E : 0xFF2A1C42)
                : (node.isGenerator()
                    ? (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF1E482E : 0xFF163824)
                    : (widget.isHeaderHovered(mouseX, mouseY) ? 0xFF353C4D : 0xFF2A2E39));
        graphics.fill(x, y, x + cardW, y + NodeWidget.HEADER_HEIGHT, headerColor);

        boolean isSelected = false;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof BoardScreen bs) {
            isSelected = bs.isNodeSelected(node.getId());
        }

        int outlineColor = isSelected ? 0xFF00FFFF : (node.isModule() ? 0xFF9955FF : (node.isBaseNode() ? 0xFFFFD700 : (node.isGenerator() ? 0xFF33AA66 : 0xFF3D4455)));
        graphics.renderOutline(x, y, cardW, height, outlineColor);
        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, cardW + 2, height + 2, 0x8800FFFF);
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x8800FFFF);
        } else if (node.isModule()) {
            graphics.renderOutline(x + 1, y + 1, cardW - 2, height - 2, 0x559955FF);
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
        if (nameEditor != null && nameEditor.isEditing()) {
            String editTxt = nameEditor.getDisplayText();
            int editW = Math.max(60, cardW - (titleX - x) - (node.isModule() ? 58 : 40));
            graphics.fill(titleX - 2, y + 2, titleX + editW, y + 18, 0xFF0D1B2A);
            graphics.renderOutline(titleX - 2, y + 2, editW, 16, 0xFF55FFFF);
            graphics.drawString(font, editTxt, titleX + 2, y + 6, 0xFF55FFFF, false);
        } else {
            String title = (node.isModule() ? "§d📦 " : (node.isBaseNode() ? "§6★ " : (node.isGenerator() ? "§a⚡ " : ""))) + node.getName();
            int maxTitleChars = Math.max(8, (cardW - (titleX - x) - (node.isModule() ? 58 : 40)) / 6);
            if (title.length() > maxTitleChars) title = title.substring(0, Math.max(2, maxTitleChars - 2)) + "...";
            graphics.drawString(font, title, titleX, y + 6, node.isModule() ? 0xFFFFB3FF : (node.isBaseNode() ? 0xFFFFE066 : (node.isGenerator() ? 0xFF77FFAA : 0xFFE0E0E0)), false);
        }

        // 3. Module Expand Button [⤢] (if module)
        if (node.isModule()) {
            int expandX = x + cardW - 54;
            int expandY = y + 2;
            boolean expandHover = widget.isExpandButtonHovered(mouseX, mouseY);
            graphics.fill(expandX, expandY, expandX + 16, expandY + 16, expandHover ? 0xFF5A3A8A : 0xFF352055);
            graphics.renderOutline(expandX, expandY, 16, 16, expandHover ? 0xFFCC88FF : 0xFF7744AA);
            graphics.drawCenteredString(font, "⤢", expandX + 8, expandY + 3, expandHover ? 0xFFFFFFFF : 0xFFDDAAFF);
        }

        // 4. Target Base Node Toggle Button [🎯]
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

        // 5. Close/Delete Button [X]
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
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.count"), x + 6, ctrlY + 3, 0xFFAAAAAA, false);

        int countMinusX = x + 36;
        drawBtn(graphics, font, "-", countMinusX, ctrlY, 14, 14, mouseX, mouseY);

        // Interactive Numeric Count Box
        NodeCountEditor countEditor = widget.getCountEditor();
        String countText = countEditor.getDisplayText();
        int countBoxW = Math.max(28, font.width(countText) + 6);
        int countBoxX = countMinusX + 16;
        boolean countHover = mouseX >= countBoxX && mouseX <= countBoxX + countBoxW && mouseY >= ctrlY && mouseY <= ctrlY + 14;
        boolean isEditing = countEditor.isEditing();
        int countBg = isEditing ? 0xFF0D1B2A : (countHover ? 0xFF252A36 : 0xFF14171E);
        int countBorder = isEditing ? 0xFF55FFFF : (countHover ? 0xFF5577AA : 0xFF3D4455);
        graphics.fill(countBoxX, ctrlY, countBoxX + countBoxW, ctrlY + 14, countBg);
        graphics.renderOutline(countBoxX, ctrlY, countBoxW, 14, countBorder);
        graphics.drawCenteredString(font, countText, countBoxX + countBoxW / 2, ctrlY + 3, isEditing ? 0xFF55FFFF : 0xFFFFFFAA);

        int afterCountX = countBoxX + countBoxW + 2;
        drawBtn(graphics, font, "+", afterCountX, ctrlY, 14, 14, mouseX, mouseY);
        drawBtn(graphics, font, "/2", afterCountX + 16, ctrlY, 16, 14, mouseX, mouseY);
        drawBtn(graphics, font, "x2", afterCountX + 34, ctrlY, 16, 14, mouseX, mouseY);

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
                if (!addon.getItemStackSample().isEmpty()) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(trayX - 1, ctrlY - 2, 0);
                    graphics.pose().scale(0.80f, 0.80f, 1.0f);
                    graphics.renderItem(addon.getItemStackSample(), 1, 1);
                    graphics.pose().popPose();
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
            // 6. Second Row Controls: [Tier] + [OC / Rotor] + [Parallel]
            GTVoltageTier tier = node.getTargetTier();
            drawBtn(graphics, font, tier.getName(), x + 6, row2Y, 32, 14, mouseX, mouseY, tier.getColor());

            int nextCtrlX = x + 42;

            if (node.isGenerator()) {
                // [GEN] / [발전기] badge
                String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
                int genW = Math.max(28, font.width(genBadge) + 4);
                drawBtn(graphics, font, genBadge, nextCtrlX, row2Y, genW, 14, mouseX, mouseY, 0xFF55FF88);
                nextCtrlX += genW + 3;

                // [⚙ 220%] rotor / config button
                int activeEff = node.getRotorEfficiency();
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() == MachineAddon.Category.ROTOR) {
                        activeEff = (int) Math.round(a.getDurationMultiplier() * 100.0);
                        break;
                    }
                }
                String rotorText = "⚙ " + activeEff + "%";
                long nonRotorAddons = node.getAddons().stream().filter(a -> a.getCategory() != MachineAddon.Category.ROTOR).count();
                if (nonRotorAddons > 0) {
                    rotorText += " (+" + nonRotorAddons + ")";
                }
                int rotorW = Math.max(46, (x + cardW - 6) - nextCtrlX);
                drawBtn(graphics, font, rotorText, nextCtrlX, row2Y, rotorW, 14, mouseX, mouseY, 0xFFFFAA00);
            } else {
                String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
                String ocText = Component.translatable(ocKey).getString();
                int ocColor = node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA;
                int ocW = Math.max(50, font.width(ocText) + 6);
                drawBtn(graphics, font, ocText, nextCtrlX, row2Y, ocW, 14, mouseX, mouseY, ocColor);
                nextCtrlX += ocW + 3;

                // Unified Machine Config & Parallel Button [⚙ 4x (+1)]
                String parLabel = "⚙ " + node.getTotalParallel() + "x";
                if (!node.getAddons().isEmpty()) {
                    parLabel += " (+" + node.getAddons().size() + ")";
                }
                int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
                int parX = nextCtrlX;
                drawBtn(graphics, font, parLabel, parX, row2Y, parW, 14, mouseX, mouseY, !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF);
            }
        }

        // 8. Recipe Energy & Duration Info
        int infoY = node.isModule() ? (ctrlY + 18) : (row2Y + 18);
        double durationSec = node.getEffectiveDurationSeconds();
        double effCps = node.getEffectiveCyclesPerSecond();

        String eutStr = BoardManager.getInstance().getPowerDisplayMode().formatNodePower(node);
        if (node.getEfficiency() < 0.999) {
            String effPercent = String.format("§e⚡%.0f%% ", node.getEfficiency() * 100.0);
            graphics.drawString(font, effPercent + eutStr, x + 6, infoY, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, eutStr, x + 6, infoY, 0xFFFFFFFF, false);
        }

        if (node.isModule()) {
            String modTag = "§d§l[📦 " + Component.translatable("gui.gtcalcboard.module").getString() + "]";
            int tagW = font.width(modTag);
            graphics.drawString(font, modTag, x + cardW - 6 - tagW, infoY, 0xFFFFFFFF, false);
        } else {
            String cycleStr = String.format("§b%.2fs §7(§f%.2f/s§7)", durationSec, effCps);
            int cycleW = font.width(cycleStr);
            graphics.drawString(font, cycleStr, x + cardW - 6 - cycleW, infoY, 0xFFFFFFFF, false);
        }

        // Separator Line
        int sepY = infoY + 14;
        graphics.fill(x + 4, sepY, x + cardW - 4, sepY + 1, 0xFF353C4D);
        graphics.fill(x + 4, sepY, x + cardW - 4, sepY + 1, 0xFF353C4D);

        // 9. Input & Output Ports Listing
        int contentY = sepY + 4;
        List<IngredientStack> inputs = node.getInputs();
        List<IngredientStack> outputs = node.getOutputs();
        int maxRows = Math.max(inputs.size(), outputs.size());

        FlowGraph graph = widget.getParent() != null ? widget.getParent().getGraph() : null;

        for (int i = 0; i < maxRows; i++) {
            int rowY = contentY + i * 18;

            // Render Input (Left side)
            if (i < inputs.size()) {
                IngredientStack in = inputs.get(i);
                double rate = widget.getInputRate(i);
                int inPortX = x + 4;
                int inPortY = rowY + 5;

                FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getInputPortStats(node, i) : null;
                boolean isConnected = stats != null && stats.isConnected();
                boolean isBalanced = stats != null && stats.isBalanced();
                boolean isDeficit = stats != null && stats.isInputDeficit();

                boolean isPortGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isPortGlowing(node.getId(), true, i);
                int portColor = isPortGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF5599FF) : (!isConnected ? 0xFF5599FF : (isBalanced ? 0xFF55FF88 : (isDeficit ? 0xFFFFAA33 : 0xFF55FFFF)));
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
                if (!isConnected) {
                    rateStr = formatRate(rate, in.isFluid());
                    textColor = 0xFFFFAAAA;
                } else if (isBalanced) {
                    rateStr = formatRate(rate, in.isFluid()) + " ✔";
                    textColor = 0xFF55FF88;
                } else if (isDeficit) {
                    rateStr = formatConnectedFraction(stats.connectedRate(), rate, in.isFluid(), "⚠");
                    textColor = 0xFFFFAA33;
                } else {
                    rateStr = formatConnectedFraction(stats.connectedRate(), rate, in.isFluid(), "+");
                    textColor = 0xFF66DDFF;
                }
                graphics.drawString(font, rateStr, x + 30, rowY + 4, textColor, false);
            }

            // Render Output (Right side)
            if (i < outputs.size()) {
                IngredientStack out = outputs.get(i);
                double rate = widget.getOutputRate(i);
                int outPortX = x + cardW - 10;
                int outPortY = rowY + 5;

                FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getOutputPortStats(node, i) : null;
                boolean isConnected = stats != null && stats.isConnected();
                boolean isBalanced = stats != null && stats.isBalanced();
                boolean isSurplus = stats != null && stats.isOutputSurplus(); // Produced > Demanded (Safe / Surplus)
                boolean isDeficit = stats != null && stats.isOutputDeficit(); // Produced < Demanded (Shortage / Warning)

                boolean isPortGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isPortGlowing(node.getId(), false, i);
                int portColor = isPortGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFF55FF88) : (!isConnected ? 0xFF55FF88 : (isBalanced ? 0xFF55FF88 : (isDeficit ? 0xFFFFAA33 : 0xFF55FFFF)));
                boolean portHover = mouseX >= x + cardW - 28 && mouseX <= x + cardW && mouseY >= rowY - 2 && mouseY <= rowY + 16;
                graphics.fill(outPortX, outPortY, outPortX + 6, outPortY + 6, portHover ? 0xFFFFFFFF : portColor);
                if (isPortGlowing) {
                    graphics.renderOutline(outPortX - 2, outPortY - 2, 10, 10, portColor);
                }

                String rateStr;
                int textColor;
                if (!isConnected) {
                    rateStr = formatRate(rate, out.isFluid());
                    textColor = 0xFFAAFFAA;
                } else if (isBalanced) {
                    rateStr = formatRate(rate, out.isFluid()) + " ✔";
                    textColor = 0xFF55FF88;
                } else if (isSurplus) {
                    rateStr = formatConnectedFraction(stats.connectedRate(), rate, out.isFluid(), "+");
                    textColor = 0xFF66DDFF;
                } else {
                    rateStr = formatConnectedFraction(stats.connectedRate(), rate, out.isFluid(), "⚠");
                    textColor = 0xFFFF6666;
                }
                int textW = font.width(rateStr);
                graphics.drawString(font, rateStr, x + cardW - 30 - textW, rowY + 4, textColor, false);

                IngredientRenderer.render(graphics, out, x + cardW - 28, rowY - 1);
            }
        }

        // 10. Corner Resize Handle (Bottom-Right ⤡)
        int handleX = x + cardW - 8;
        int handleY = y + height - 8;
        boolean handleHover = widget.isResizeHandleHovered(mouseX, mouseY);
        int handleColor = handleHover ? 0xFF55FFFF : 0x88657595;
        graphics.drawString(font, "⤡", handleX - 2, handleY - 3, handleColor, false);
    }

    private static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, 0xFFFFFFFF);
    }

    private static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? 0xFF3E475A : 0xFF282E3B);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF657595 : 0xFF3D4455);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textColor);
    }

    public static String formatRate(double rate, boolean isFluid) {
        if (isFluid) {
            if (rate >= 1000.0) {
                return String.format("%.2f B/s", rate / 1000.0).replaceAll("\\.?0+ B/s", " B/s");
            }
            return String.format("%.0f mB/s", rate);
        } else {
            return String.format("%.2f/s", rate).replaceAll("\\.?0+/s", "/s");
        }
    }

    public static String formatConnectedFraction(double connected, double required, boolean isFluid, String symbol) {
        String symStr = symbol.isEmpty() ? "" : " " + symbol;
        if (isFluid) {
            if (required >= 1000.0 || connected >= 1000.0) {
                double connB = connected / 1000.0;
                double reqB = required / 1000.0;
                String connStr = (connB == (long) connB) ? String.format("%d", (long) connB) : String.format("%.2f", connB).replaceAll("\\.?0+$", "");
                String reqStr = (reqB == (long) reqB) ? String.format("%d", (long) reqB) : String.format("%.2f", reqB).replaceAll("\\.?0+$", "");
                return connStr + "/" + reqStr + " B/s" + symStr;
            } else {
                return String.format("%.0f/%.0f mB/s%s", connected, required, symStr);
            }
        } else {
            String connStr = (connected == (long) connected) ? String.format("%d", (long) connected) : String.format("%.2f", connected).replaceAll("\\.?0+$", "");
            String reqStr = (required == (long) required) ? String.format("%d", (long) required) : String.format("%.2f", required).replaceAll("\\.?0+$", "");
            return connStr + "/" + reqStr + "/s" + symStr;
        }
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
}
