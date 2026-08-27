package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.FlowGraphSolver;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
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
                if (widget.isTargetButtonHovered(canvasMouseX, canvasMouseY)) {
                    graphics.renderTooltip(font, Component.literal("§6🎯 ").append(Component.translatable("gui.gtcalcboard.tooltip.target_base")), mouseX, mouseY);
                    return;
                }
                if (widget.isFlipButtonHovered(canvasMouseX, canvasMouseY)) {
                    String flipKey = widget.getNode().isFlipped() ? "gui.gtcalcboard.flip_direction.right_to_left" : "gui.gtcalcboard.flip_direction.left_to_right";
                    graphics.renderTooltip(font, Component.literal("§b⇄ ").append(Component.translatable(flipKey)), mouseX, mouseY);
                    return;
                }
                if (widget.isExpandButtonHovered(canvasMouseX, canvasMouseY)) {
                    graphics.renderTooltip(font, Component.literal("§d⤢ ").append(Component.translatable("gui.gtcalcboard.tooltip.expand_module")), mouseX, mouseY);
                    return;
                }

                if (widget.getNode().isReroute()) {
                    List<Component> tooltipLines = new ArrayList<>();
                    RecipeNode rNode = widget.getNode();
                    IngredientStack rStack = !rNode.getInputs().isEmpty() ? rNode.getInputs().get(0) : null;

                    if (rStack != null) {
                        tooltipLines.add(Component.literal("§6🎯 §f" + Component.translatable("gui.gtcalcboard.eta.tooltip.title").getString() + " §7(" + rStack.getDisplayName() + "§7)"));
                        double netRate = com.gtceu.calcboard.api.ProductionETACalculator.calculateNetInflowRate(graph, rNode, 0);
                        String rateStr = FormatUtil.formatRate(netRate, rStack);
                        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §a+" + rateStr));

                        if (rNode.hasTargetBatch()) {
                            double targetAmount = rNode.getTargetBatchAmount();
                            String goalStr = FormatUtil.formatBatchAmount(targetAmount, rStack.isFluid());
                            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.target").getString() + ": §e" + goalStr));

                            double etaSec = com.gtceu.calcboard.api.ProductionETACalculator.calculateETA(targetAmount, netRate);
                            String etaFormatted = FormatUtil.formatETA(etaSec);
                            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.duration").getString() + ": §b" + etaFormatted));

                            double totalEU = com.gtceu.calcboard.api.ProductionETACalculator.calculateTotalEnergyForBatch(graph, rNode, targetAmount);
                            if (totalEU > 0) {
                                tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.total_energy").getString() + ": §e" + FormatUtil.formatCompactNumber(totalEU) + " EU"));
                            }

                            tooltipLines.add(Component.literal("§8§m------------------------"));
                            tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.eta.tooltip.click_edit").getString()));
                            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.shift_reset").getString()));
                        } else {
                            tooltipLines.add(Component.literal("§8§m------------------------"));
                            tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.eta.tooltip.click_set").getString()));
                        }
                    } else {
                        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.reroute_junction").getString()));
                    }

                    graphics.renderComponentTooltip(font, tooltipLines, mouseX, mouseY);
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

                    if (Minecraft.getInstance().options.advancedItemTooltips) {
                        tooltipLines.add(Component.literal("§8§m------------------------"));
                        tooltipLines.add(Component.literal("§7[F3+H Debug] §8Port: §fInput #" + inIdx));
                        if (in.getId() != null) {
                            tooltipLines.add(Component.literal("§7[F3+H Debug] §8ID: §7" + in.getId()));
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
                        double effChance = node != null ? node.getEffectiveOutputChance(outIdx) : out.getChance();
                        if (effChance <= 0.0) {
                            if (node != null && node.getSteamMode().isSteam()) {
                                tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim() + ": 0% (Steam Mode: No Byproducts)"));
                            } else if (node != null && node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("macerator")) {
                                String reqTierName = outIdx == 1 ? "HV" : (outIdx == 2 ? "EV" : "IV");
                                tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim() + ": 0% (Requires " + reqTierName + "+)"));
                            } else {
                                tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim() + ": 0%"));
                            }
                        } else if (out.getTierChanceBoost() > 0.0) {
                            int tierDelta = node != null ? node.getTierDelta() : 0;
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
                            tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format(java.util.Locale.ROOT, "%.1f", effChance * 100.0))));
                        }
                    }

                    if (Minecraft.getInstance().options.advancedItemTooltips) {
                        tooltipLines.add(Component.literal("§8§m------------------------"));
                        tooltipLines.add(Component.literal("§7[F3+H Debug] §8Port: §fOutput #" + outIdx));
                        if (out.getId() != null) {
                            tooltipLines.add(Component.literal("§7[F3+H Debug] §8ID: §7" + out.getId()));
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
                    com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(n);
                    List<Component> tooltipLines = new ArrayList<>(adapter.buildEnergyTooltip(n));
                    if (Minecraft.getInstance().options.advancedItemTooltips) {
                        tooltipLines.add(Component.literal("§8§m------------------------"));
                        tooltipLines.add(Component.literal("§7[F3+H Debug] §8Node ID: §7" + n.getId()));
                        if (n.getRecipeCategoryId() != null) {
                            tooltipLines.add(Component.literal("§7[F3+H Debug] §8Category: §e" + n.getRecipeCategoryId()));
                        }
                        tooltipLines.add(Component.literal("§7[F3+H Debug] §8Adapter: §a" + adapter.getClass().getSimpleName() + " (Priority " + adapter.getPriority() + ")"));
                        if (n.getMachineIcon() != null) {
                            tooltipLines.add(Component.literal("§7[F3+H Debug] §8Machine Icon: §6" + n.getMachineIcon()));
                        }
                    }
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }

                // Machine Icon Hover Tooltip
                if (widget.isMachineIconHovered(canvasMouseX, canvasMouseY)) {
                    RecipeNode n = widget.getNode();
                    List<Component> tooltipLines = new ArrayList<>();
                    String mName = n.getMachineDisplayName();
                    tooltipLines.add(Component.literal((n.isMultiblock() ? "§e🏛 " : "§b⚡ ") + mName));
                    if (n.hasMultiblockOption()) {
                        tooltipLines.add(Component.literal("§7[Click]: §f" + Component.translatable(n.isMultiblock() ? "gui.gtcalcboard.tooltip.switch_to_singleblock" : "gui.gtcalcboard.tooltip.switch_to_multiblock").getString()));
                    }
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }

                // Voltage Tier / Energy Hatch Button Hover Tooltip
                if (widget.isTierButtonHovered(canvasMouseX, canvasMouseY)) {
                    RecipeNode n = widget.getNode();
                    List<Component> tooltipLines = new ArrayList<>();
                    if (n.isMultiblock() && !n.isGenerator() && !n.isTurbine()) {
                        tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.addon_cat.energy_hatch").getString() + " §7(" + n.getTargetTier().getName() + ")"));
                        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.multiblock_energy_hatch_hint").getString()));
                    } else if (n.supportsSteamMode() && n.getSteamMode() != com.gtceu.calcboard.api.SteamMode.NONE) {
                        tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.config.steam_tier").getString() + " §7(" + n.getSteamMode().name() + ")"));
                        tooltipLines.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_steam_tier").getString()));
                    } else {
                        tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.config.voltage_tier").getString() + " §7(" + n.getTargetTier().getName() + ")"));
                        tooltipLines.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
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
                    if (!n.isOperational(graph)) {
                        tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.node_warning.inactive").getString()));
                        if (!n.hasValidReflector()) {
                            int req = n.getRequiredReflectorTier();
                            int inst = n.getInstalledReflectorTier();
                            String instStr = inst > 0 ? ("Tier " + inst) : Component.translatable("gui.gtcalcboard.none").getString();
                            tooltipLines.add(Component.literal("§c❌ " + String.format(java.util.Locale.ROOT, Component.translatable("gui.gtcalcboard.node_warning.reflector_detail").getString(), String.valueOf(req), instStr)));
                        }
                        if (graph != null && com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.hasTurbineFlowDeficit(n, graph)) {
                            tooltipLines.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.turbine_deficit_desc").getString()));
                        }
                    }
                    tooltipLines.add(Component.literal("§b⚙ " + Component.translatable("gui.gtcalcboard.config_dialog_title", n.getName()).getString()));

                    String modeStr = n.isMultiblock() ? "§a" + Component.translatable("gui.gtcalcboard.config.multiblock_mode").getString()
                                                      : "§7" + Component.translatable("gui.gtcalcboard.config.singleblock_mode").getString();
                    tooltipLines.add(Component.literal(modeStr));

                    if (n.isTurbine()) {
                        String rName = n.getRotorName();
                        if (rName == null || rName.isEmpty() || rName.startsWith("Standard")) {
                            rName = Component.translatable("gui.gtcalcboard.rotor.standard").getString();
                        }
                        tooltipLines.add(Component.literal("§6🌀 " + Component.translatable("gui.gtcalcboard.addon_cat.rotor").getString() + ": §f" + rName));
                        int eff = n.getRotorEfficiency();
                        int pwr = n.getRotorPower();
                        int holderBonus = com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getTurbineHolderEfficiencyBonus(n);
                        String effStr = "§b⏱ " + Component.translatable("gui.gtcalcboard.rotor.eff").getString() + ": §f" + eff + "%";
                        if (holderBonus > 0) {
                            effStr += " §a(+" + holderBonus + "% Holder)";
                        }
                        tooltipLines.add(Component.literal(effStr));
                        tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.rotor.power").getString() + ": §f" + pwr + "%"));
                        if (n.getTotalParallel() > 1) {
                            tooltipLines.add(Component.literal("§b⚙ " + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(n.getTotalParallel())).getString()));
                        }
                    } else if (n.isCreateMachine()) {
                        tooltipLines.add(Component.literal("§6⚙ " + n.getRpm() + " RPM"));
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
                            if (Minecraft.getInstance().options.advancedItemTooltips && a.getDiscoverySource() != null && !a.getDiscoverySource().isEmpty()) {
                                tooltipLines.add(Component.literal("   §8↳ §b" + a.getDiscoverySource()));
                            }
                        }
                    } else {
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.config.no_addons_installed").getString()));
                    }

                    if (Minecraft.getInstance().options.advancedItemTooltips) {
                        tooltipLines.add(Component.literal("§8§m------------------------"));
                        tooltipLines.add(Component.literal("§7[F3+H Debug] §8Node ID: §7" + n.getId()));
                        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(n);
                        tooltipLines.add(Component.literal("§7[F3+H Debug] §8Adapter: §a" + adapter.getClass().getSimpleName() + " (Priority " + adapter.getPriority() + ")"));
                        if (n.getMachineIcon() != null) {
                            tooltipLines.add(Component.literal("§7[F3+H Debug] §8Machine Icon: §6" + n.getMachineIcon()));
                        }
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
                String raw = Component.translatable(hintKey).getString();
                if (raw.contains("\n")) {
                    List<Component> lines = java.util.Arrays.stream(raw.split("\n"))
                            .<Component>map(Component::literal)
                            .toList();
                    graphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
                } else {
                    graphics.renderTooltip(font, Component.translatable(hintKey), mouseX, mouseY);
                }
                return;
            }

            if (mouseY >= screen.getHeaderBottomY() && canvasHandler.hasQuickAddMarker()) {
                double qx = canvasHandler.getQuickAddMarkerCanvasX();
                double qy = canvasHandler.getQuickAddMarkerCanvasY();

                boolean searchHovered = canvasMouseX >= qx - 44 && canvasMouseX <= qx - 24 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
                boolean junctionHovered = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
                boolean frameHovered = canvasMouseX >= qx + 2 && canvasMouseX <= qx + 22 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
                boolean noteHovered = canvasMouseX >= qx + 25 && canvasMouseX <= qx + 45 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;

                if (searchHovered) {
                    graphics.renderTooltip(font, Component.literal("§a🔍 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_search")), mouseX, mouseY);
                    return;
                } else if (junctionHovered) {
                    graphics.renderTooltip(font, Component.literal("§b🔀 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_junction")), mouseX, mouseY);
                    return;
                } else if (frameHovered) {
                    graphics.renderTooltip(font, Component.literal("§d🖼 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_frame")), mouseX, mouseY);
                    return;
                } else if (noteHovered) {
                    graphics.renderTooltip(font, Component.literal("§e📝 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_note")), mouseX, mouseY);
                    return;
                }
            }

            if (mouseY >= screen.getHeaderBottomY()) {
                CanvasGroupFrameRenderer.renderFrameTooltips(graphics, font, screen.getGraph(), canvasMouseX, canvasMouseY, mouseX, mouseY);
                CanvasStickyNoteRenderer.renderNoteTooltips(graphics, font, screen.getGraph(), canvasMouseX, canvasMouseY, mouseX, mouseY);
            }
        }

        // 3. Summary Overlay tooltips
        if (screen.getSummaryOverlay() != null) {
            screen.getSummaryOverlay().renderTooltips(graphics, font, mouseX, mouseY);
        }
    }
}
