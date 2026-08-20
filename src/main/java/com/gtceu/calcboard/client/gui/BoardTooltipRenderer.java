package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.FlowGraphSolver;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated renderer for hover tooltips across the Calculator Board screen,
 * including input/output port flow diagnostics, wire hints, and quick-add markers.
 */
public final class BoardTooltipRenderer {

    private BoardTooltipRenderer() {}

    public static void renderTooltips(BoardScreen screen, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (screen == null) return;

        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);
        FlowGraph graph = screen.getGraph();
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();

        // 1. Port / Node Hover Tooltips
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                if (widget.isCloseButtonHovered(canvasMouseX, canvasMouseY)) {
                    graphics.renderTooltip(font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.tooltip.remove_node")), mouseX, mouseY);
                    return;
                }

                int inIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                int outIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);

                if (inIdx >= 0 && inIdx < widget.getNode().getInputs().size()) {
                    IngredientStack in = widget.getNode().getInputs().get(inIdx);
                    FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getInputPortStats(widget.getNode(), inIdx) : new FlowGraphSolver.PortFlowStats(0, 0, 0, false);

                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§b[📥 " + Component.translatable("gui.gtcalcboard.input").getString() + "] §f" + in.getDisplayName()));

                    String reqStr = NodeCardRenderer.formatRate(stats.requiredOrProducedRate(), in.isFluid());
                    String exactReq = FormatUtil.formatExactRate(stats.requiredOrProducedRate(), in.isFluid());
                    String reqDisplay = reqStr.equals(exactReq) ? reqStr : reqStr + " §8(" + exactReq + ")";
                    tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.demand").getString() + ": §f" + reqDisplay));

                    if (stats.isConnected()) {
                        String supStr = NodeCardRenderer.formatRate(stats.connectedRate(), in.isFluid());
                        String exactSup = FormatUtil.formatExactRate(stats.connectedRate(), in.isFluid());
                        String supDisplay = supStr.equals(exactSup) ? supStr : supStr + " §8(" + exactSup + ")";
                        String percentCol = stats.isBalanced() ? "§a" : (stats.isInputDeficit() ? "§c" : "§b");
                        String statusStr = stats.isBalanced()
                            ? "§a✔ 100%"
                            : (stats.isInputDeficit()
                                ? String.format(java.util.Locale.ROOT, "§c⚠ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.deficit").getString())
                                : String.format(java.util.Locale.ROOT, "§b+ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.surplus").getString()));

                        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay + " §7(" + statusStr + "§7)"));
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_producers", String.valueOf(stats.connectionCount())).getString()));
                    } else {
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_raw").getString()));
                    }

                    if (in.getChance() < 1.0) {
                        tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format(java.util.Locale.ROOT, "%.1f", in.getChance() * 100.0))));
                    }
                    if (in.hasAlternatives()) {
                        int curIdx = in.getAlternatives().indexOf(in.getId()) + 1;
                        tooltipLines.add(Component.literal("§6[⟲ " + Component.translatable("gui.gtcalcboard.tooltip.scroll_cycle").getString() + "]: §e" + Component.translatable("gui.gtcalcboard.tooltip.tag_alts", String.valueOf(curIdx), String.valueOf(in.getAlternatives().size())).getString()));
                    }
                    tooltipLines.add(Component.literal("§7[Drag]: §f" + Component.translatable("gui.gtcalcboard.tooltip.drag_connect").getString()));
                    tooltipLines.add(Component.literal("§e[Shift+Drag]: §a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.shift_auto_ratio").getString()));
                    if (stats.isConnected()) {
                        tooltipLines.add(Component.literal("§c[Right-Click]: §7" + Component.translatable("gui.gtcalcboard.tooltip.right_click_sever").getString()));
                    }
                    tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }

                if (outIdx >= 0 && outIdx < widget.getNode().getOutputs().size()) {
                    IngredientStack out = widget.getNode().getOutputs().get(outIdx);
                    FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getOutputPortStats(widget.getNode(), outIdx) : new FlowGraphSolver.PortFlowStats(0, 0, 0, false);

                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§a[📤 " + Component.translatable("gui.gtcalcboard.output").getString() + "] §f" + out.getDisplayName()));

                    String prodStr = NodeCardRenderer.formatRate(stats.requiredOrProducedRate(), out.isFluid());
                    String exactProd = FormatUtil.formatExactRate(stats.requiredOrProducedRate(), out.isFluid());
                    String prodDisplay = prodStr.equals(exactProd) ? prodStr : prodStr + " §8(" + exactProd + ")";
                    tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.production").getString() + ": §f" + prodDisplay));

                    if (stats.isConnected()) {
                        String demStr = NodeCardRenderer.formatRate(stats.connectedRate(), out.isFluid());
                        String exactDem = FormatUtil.formatExactRate(stats.connectedRate(), out.isFluid());
                        String demDisplay = demStr.equals(exactDem) ? demStr : demStr + " §8(" + exactDem + ")";
                        String percentCol = stats.isBalanced() ? "§a" : (stats.isOutputSurplus() ? "§b" : "§c");
                        String statusStr = stats.isBalanced()
                            ? "§a✔ 100%"
                            : (stats.isOutputSurplus()
                                ? String.format(java.util.Locale.ROOT, "§b+ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.surplus").getString())
                                : String.format(java.util.Locale.ROOT, "§c⚠ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.deficit").getString()));

                        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demDisplay + " §7(" + statusStr + "§7)"));
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_consumers", String.valueOf(stats.connectionCount())).getString()));
                    } else {
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_final").getString()));
                    }

                    if (out.getChance() < 1.0) {
                        RecipeNode node = widget.getNode();
                        int tierDelta = node != null ? node.getTierDelta() : 0;
                        double effChance = out.getEffectiveChance(tierDelta);
                        if (out.getTierChanceBoost() > 0.0) {
                            if (tierDelta > 0 && effChance > out.getChance()) {
                                tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§e%s: %.1f%% §7(+%.1f%%/Tier §a→ %.1f%%§7)",
                                        Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim(),
                                        out.getChance() * 100.0,
                                        out.getTierChanceBoost() * 100.0,
                                        effChance * 100.0)));
                            } else {
                                tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§e%s: %.1f%% §7(+%.1f%%/Tier)",
                                        Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim(),
                                        out.getChance() * 100.0,
                                        out.getTierChanceBoost() * 100.0)));
                            }
                        } else {
                            tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format(java.util.Locale.ROOT, "%.1f", out.getChance() * 100.0))));
                        }
                    }
                    tooltipLines.add(Component.literal("§7[Drag]: §f" + Component.translatable("gui.gtcalcboard.tooltip.drag_connect").getString()));
                    tooltipLines.add(Component.literal("§e[Shift+Drag]: §a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.shift_auto_ratio").getString()));
                    if (stats.isConnected()) {
                        tooltipLines.add(Component.literal("§c[Right-Click]: §7" + Component.translatable("gui.gtcalcboard.tooltip.right_click_sever").getString()));
                    }
                    tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }

                // Power & Cycle Info Row Hover Tooltip
                int nodeX = (int) widget.getNode().getPosX();
                int nodeY = (int) widget.getNode().getPosY();
                int ctrlY = nodeY + NodeWidget.HEADER_HEIGHT + 6;
                int row2H = widget.getNode().isModule() ? 0 : 18;
                int infoY = ctrlY + row2H + 18;

                if (canvasMouseY >= infoY - 2 && canvasMouseY <= infoY + 14 && canvasMouseX >= nodeX && canvasMouseX <= nodeX + widget.getWidth()) {
                    RecipeNode n = widget.getNode();
                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
                    double totEUt = n.getEffectiveTotalEUt();
                    var tier = n.getTargetTier();
                    if (tier == null) tier = com.gtceu.calcboard.api.GTVoltageTier.LV;
                    double amps = totEUt / (double) tier.getVoltage();
                    tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Total EU/t: §f%,.2f EU/t", totEUt)));
                    tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Current: §f%,.4fA %s", amps, tier.getName())));
                    tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", n.getEffectiveDurationSeconds(), n.getEffectiveCyclesPerSecond())));
                    if (n.getEfficiency() < 0.999) {
                        tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§e⚡ Efficiency: §f%.1f%%", n.getEfficiency() * 100.0)));
                    }
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }

                // Machine Count Box Hover Tooltip
                int countMinusX = nodeX + 36;
                int countBoxX = countMinusX + 16;
                int countBoxW = Math.max(28, font.width(widget.getCountEditor().getDisplayText()) + 6);
                if (canvasMouseX >= countBoxX && canvasMouseX <= countBoxX + countBoxW && canvasMouseY >= ctrlY && canvasMouseY <= ctrlY + 14) {
                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§6🏭 " + Component.translatable("gui.gtcalcboard.count").getString()));
                    tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Exact Count: §f%,.4f", widget.getNode().getMachineCount())));
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }

                // Machine Hardware Configuration & Installed Addons Tooltip
                if (widget.isMachineConfigButtonHovered(canvasMouseX, canvasMouseY) || widget.isAddonTrayHovered(canvasMouseX, canvasMouseY)) {
                    RecipeNode n = widget.getNode();
                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§b⚙ " + Component.translatable("gui.gtcalcboard.config_dialog_title", n.getName()).getString()));

                    String modeStr = n.isMultiblock() ? "§a" + Component.translatable("gui.gtcalcboard.config.multiblock_mode").getString()
                                                      : "§7" + Component.translatable("gui.gtcalcboard.config.singleblock_mode").getString();
                    tooltipLines.add(Component.literal(modeStr));

                    if (n.isGenerator()) {
                        tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.rotor.tooltip_eff", String.valueOf(n.getRotorEfficiency())).getString()));
                    } else {
                        String parStr = "§b" + Component.translatable("gui.gtcalcboard.config.base_parallel", String.valueOf(n.getParallel())).getString()
                                + " §7(" + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(n.getTotalParallel())).getString() + "§7)";
                        tooltipLines.add(Component.literal(parStr));
                    }

                    List<com.gtceu.calcboard.api.MachineAddon> addons = n.getAddons();
                    if (!addons.isEmpty()) {
                        tooltipLines.add(Component.literal("§6" + Component.translatable("gui.gtcalcboard.config.active_addons", String.valueOf(addons.size())).getString() + ":"));
                        for (var a : addons) {
                            String badge = MachineConfigDialog.formatAddonBadge(a, n);
                            tooltipLines.add(Component.literal(" §7• §f" + a.getName() + (!badge.isEmpty() ? " " + badge : "")));
                        }
                    } else {
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.config.no_addons_installed").getString()));
                    }

                    tooltipLines.add(Component.literal("§e[ " + Component.translatable("gui.gtcalcboard.config.install").getString() + " / " + Component.translatable("gui.gtcalcboard.config.remove").getString() + " ]"));
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
        }

        // 2. Floating hint while dragging wire
        CanvasInteractionHandler canvasHandler = screen.getCanvasHandler();
        if (canvasHandler != null) {
            NodeWidget wireStart = canvasHandler.getWireStartNode();
            if (wireStart != null) {
                boolean shift = Screen.hasShiftDown();
                String hintKey = shift ? "gui.gtcalcboard.tooltip.wire_mode_shift" : "gui.gtcalcboard.tooltip.wire_mode_normal";
                graphics.renderTooltip(font, Component.translatable(hintKey), mouseX, mouseY);
                return;
            }

            if (mouseY >= 64 && canvasHandler.hasQuickAddMarker()) {
                double qx = canvasHandler.getQuickAddMarkerCanvasX();
                double qy = canvasHandler.getQuickAddMarkerCanvasY();
                if (Math.hypot(canvasMouseX - qx, canvasMouseY - qy) <= 14.0) {
                    graphics.renderTooltip(font, Component.literal("§a➕ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_add")), mouseX, mouseY);
                    return;
                }
            }
        }

        // 3. Summary Overlay tooltips
        if (screen.getSummaryOverlay() != null) {
            screen.getSummaryOverlay().renderTooltips(graphics, font, mouseX, mouseY);
        }
    }
}
