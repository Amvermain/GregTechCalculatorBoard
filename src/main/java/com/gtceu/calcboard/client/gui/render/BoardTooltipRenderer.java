package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.ProductionETACalculator;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.CanvasInteractionHandler;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated renderer for hover tooltips across the Calculator Board screen,
 * including input/output port flow diagnostics, wire hints, and quick-add markers.
 */
public final class BoardTooltipRenderer {

    public record VirtualTooltipPositioner(int screenWidth, int screenHeight) implements ClientTooltipPositioner {
        @Override
        public Vector2ic positionTooltip(int ignoredW, int ignoredH, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
            Vector2i pos = new Vector2i(mouseX + 12, mouseY - 12);
            if (pos.x + tooltipWidth > screenWidth) {
                pos.x = Math.max(pos.x - 24 - tooltipWidth, 4);
            }
            int totalH = tooltipHeight + 3;
            if (pos.y + totalH > screenHeight) {
                pos.y = screenHeight - totalH;
            }
            if (pos.y < 4) {
                pos.y = 4;
            }
            return pos;
        }
    }

    public static ClientTooltipPositioner createPositioner(int screenWidth, int screenHeight) {
        return new VirtualTooltipPositioner(screenWidth, screenHeight);
    }

    public static void renderComponentTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines == null || lines.isEmpty() || font == null || graphics == null) return;
        List<FormattedCharSequence> formatted = lines.stream().map(Component::getVisualOrderText).toList();

        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 1000.0f);
        try {
            graphics.renderTooltip(font, formatted, createPositioner(screenWidth, screenHeight), mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
            RenderSystem.enableDepthTest();
        }
    }

    public static void renderComponentTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY) {
        int sw = 800;
        int sh = 600;
        com.gtceu.calcboard.client.gui.util.BoardViewportTransform transform = BoardScreen.getCurrentTransform();
        if (transform != null && transform.isScaled()) {
            sw = transform.getVirtualWidth();
            sh = transform.getVirtualHeight();
        } else if (graphics != null) {
            sw = graphics.guiWidth();
            sh = graphics.guiHeight();
        }
        renderComponentTooltip(graphics, font, lines, mouseX, mouseY, sw, sh);
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, Component component, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (component == null) return;
        renderComponentTooltip(graphics, font, List.of(component), mouseX, mouseY, screenWidth, screenHeight);
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, Component component, int mouseX, int mouseY) {
        if (component == null) return;
        renderComponentTooltip(graphics, font, List.of(component), mouseX, mouseY);
    }

    private BoardTooltipRenderer() {}

    public static void renderTooltips(BoardScreen screen, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (screen == null) return;
        renderTooltipsInternal(screen, graphics, font, mouseX, mouseY);
    }

    private static void renderTooltipsInternal(BoardScreen screen, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);
        FlowGraph graph = screen.getGraph();
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();

        // 1. Port / Node Hover Tooltips
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                if (widget.isCloseButtonHovered(canvasMouseX, canvasMouseY)) {
                    renderTooltip(graphics, font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.tooltip.remove_node")), mouseX, mouseY, screen.width, screen.height);
                    return;
                }
                if (widget.isTargetButtonHovered(canvasMouseX, canvasMouseY)) {
                    renderTooltip(graphics, font, Component.literal("§6🎯 ").append(Component.translatable("gui.gtcalcboard.tooltip.target_base")), mouseX, mouseY, screen.width, screen.height);
                    return;
                }
                if (widget.isFlipButtonHovered(canvasMouseX, canvasMouseY)) {
                    String flipKey = widget.getNode().isFlipped() ? "gui.gtcalcboard.flip_direction.right_to_left" : "gui.gtcalcboard.flip_direction.left_to_right";
                    renderTooltip(graphics, font, Component.literal("§b⇄ ").append(Component.translatable(flipKey)), mouseX, mouseY, screen.width, screen.height);
                    return;
                }
                if (widget.isExpandButtonHovered(canvasMouseX, canvasMouseY)) {
                    renderTooltip(graphics, font, Component.literal("§d⤢ ").append(Component.translatable("gui.gtcalcboard.tooltip.expand_module")), mouseX, mouseY, screen.width, screen.height);
                    return;
                }
                if (widget.isSwitchButtonHovered(canvasMouseX, canvasMouseY)) {
                    renderTooltip(graphics, font, Component.literal("§e🔄 ").append(Component.translatable("gui.gtcalcboard.switch_recipe.button")), mouseX, mouseY, screen.width, screen.height);
                    return;
                }

                if (renderRerouteTooltip(graphics, font, screen, widget, graph, mouseX, mouseY)) {
                    return;
                }

                int inIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                if (renderInputPortTooltip(graphics, font, screen, widget, inIdx, mouseX, mouseY)) {
                    return;
                }

                int outIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
                if (renderOutputPortTooltip(graphics, font, screen, widget, outIdx, mouseX, mouseY)) {
                    return;
                }

                if (renderNodeInfoTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
                    return;
                }

                // Machine Icon Hover Tooltip
                if (widget.isMachineIconHovered(canvasMouseX, canvasMouseY)) {
                    RecipeNode n = widget.getNode();
                    List<Component> tooltipLines = new ArrayList<>();
                    String mName = n.getMachineDisplayName();
                    tooltipLines.add(Component.literal((n.isMultiblock() ? "§e🏛 " : "§b⚡ ") + mName));
                    tooltipLines.add(Component.literal("§7[Click]: §f" + Component.translatable("gui.gtcalcboard.tooltip.switch_machine_hint").getString()));
                    renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
                    return;
                }

                // Voltage Tier / Energy Hatch / Dynamo-Boiler Button Hover Tooltip
                if (widget.isTierButtonHovered(canvasMouseX, canvasMouseY)) {
                    RecipeNode n = widget.getNode();
                    List<Component> tooltipLines = new ArrayList<>();
                    if (com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(n)) {
                        boolean isGen = n.isGenerator() || n.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.ELECTRIC_FE;
                        if (isGen) {
                            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.dynamo_badge").getString()));
                            tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.tooltip.systeams_toggle_to_boiler").getString()));
                        } else {
                            tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.boiler_badge").getString()));
                            tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.tooltip.systeams_toggle_to_dynamo").getString()));
                        }
                    } else if (n.isMultiblock()) {
                        if (n.isTurbine()) {
                            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.config.voltage_tier").getString() + " §7(" + (n.getTargetTier() != null ? n.getTargetTier().getName() : "HV") + ")"));
                            tooltipLines.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
                        } else if (!n.isGenerator()) {
                            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.addon_cat.energy_hatch").getString() + " §7(" + (n.getTargetTier() != null ? n.getTargetTier().getName() : "LV") + ")"));
                            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.multiblock_energy_hatch_hint").getString()));
                        } else {
                            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.config.voltage_tier").getString() + " §7(" + (n.getTargetTier() != null ? n.getTargetTier().getName() : "LV") + ")"));
                            tooltipLines.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
                        }
                    } else if (!n.isMultiblock() && n.supportsSteamMode() && n.getSteamMode() != com.gtceu.calcboard.api.type.SteamMode.NONE) {
                        tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.config.steam_tier").getString() + " §7(" + n.getSteamMode().name() + ")"));
                        tooltipLines.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_steam_tier").getString()));
                    } else {
                        tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.config.voltage_tier").getString() + " §7(" + (n.getTargetTier() != null ? n.getTargetTier().getName() : "LV") + ")"));
                        tooltipLines.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
                    }
                    renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
                    return;
                }

                if (renderCountBoxTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
                    return;
                }

                if (renderMachineConfigTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
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
                    renderComponentTooltip(graphics, font, lines, mouseX, mouseY, screen.width, screen.height);
                } else {
                    renderTooltip(graphics, font, Component.translatable(hintKey), mouseX, mouseY, screen.width, screen.height);
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
                    renderTooltip(graphics, font, Component.literal("§a🔍 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_search")), mouseX, mouseY, screen.width, screen.height);
                    return;
                } else if (junctionHovered) {
                    renderTooltip(graphics, font, Component.literal("§b🔀 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_junction")), mouseX, mouseY, screen.width, screen.height);
                    return;
                } else if (frameHovered) {
                    renderTooltip(graphics, font, Component.literal("§d🖼 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_frame")), mouseX, mouseY, screen.width, screen.height);
                    return;
                } else if (noteHovered) {
                    renderTooltip(graphics, font, Component.literal("§e📝 ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_note")), mouseX, mouseY, screen.width, screen.height);
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

    private static boolean renderNodeInfoTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        int nodeX = (int) widget.getNode().getPosX();
        int nodeY = (int) widget.getNode().getPosY();
        int ctrlY = nodeY + NodeWidget.HEADER_HEIGHT + 6;
        int row2H = widget.getNode().isModule() ? 0 : 18;
        int infoY = ctrlY + row2H + 18;

        if (canvasMouseY < infoY - 2 || canvasMouseY > infoY + 14 || canvasMouseX < nodeX || canvasMouseX > nodeX + widget.getWidth()) {
            return false;
        }

        RecipeNode n = widget.getNode();
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(n);
        List<Component> tooltipLines = new ArrayList<>(adapter.buildEnergyTooltip(n));
        if (BoardManager.getInstance().isShowDebugInfo()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§7[Debug] §8Node ID: §7" + n.getId()));
            if (n.getRecipeCategoryId() != null) {
                tooltipLines.add(Component.literal("§7[Debug] §8Category: §e" + n.getRecipeCategoryId()));
            }
            tooltipLines.add(Component.literal("§7[Debug] §8Adapter: §a" + adapter.getClass().getSimpleName() + " (Priority " + adapter.getPriority() + ")"));
            if (n.getMachineIcon() != null) {
                tooltipLines.add(Component.literal("§7[Debug] §8Machine Icon: §6" + n.getMachineIcon()));
            }
        }
        renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static boolean renderCountBoxTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        int nodeX = (int) widget.getNode().getPosX();
        int nodeY = (int) widget.getNode().getPosY();
        int ctrlY = nodeY + NodeWidget.HEADER_HEIGHT + 6;
        int countMinusX = nodeX + 36;
        int countBoxX = countMinusX + 16;
        int countBoxW = Math.max(28, font.width(widget.getCountEditor().getDisplayText()) + 6);
        if (canvasMouseX < countBoxX || canvasMouseX > countBoxX + countBoxW || canvasMouseY < ctrlY || canvasMouseY > ctrlY + 14) {
            return false;
        }

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§6🏭 " + Component.translatable("gui.gtcalcboard.count").getString()));
        tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Exact Count: §f%,.4f", widget.getNode().getMachineCount())));
        renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static boolean renderMachineConfigTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (!widget.isMachineConfigButtonHovered(canvasMouseX, canvasMouseY) && !widget.isAddonTrayHovered(canvasMouseX, canvasMouseY)) {
            return false;
        }

        RecipeNode n = widget.getNode();
        FlowGraph graph = screen.getGraph();
        List<Component> tooltipLines = new ArrayList<>();
        if (!n.isOperational(graph)) {
            tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.node_warning.inactive").getString()));
            if (!n.hasValidReflector()) {
                int req = n.getRequiredReflectorTier();
                int inst = n.getInstalledReflectorTier();
                String instStr = inst > 0 ? ("Tier " + inst) : Component.translatable("gui.gtcalcboard.none_plain").getString();
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

        appendAddonQuickListDetails(tooltipLines, n);

        tooltipLines.add(Component.literal("§e[ " + Component.translatable("gui.gtcalcboard.config.install").getString() + " / " + Component.translatable("gui.gtcalcboard.config.remove").getString() + " ]"));
        renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static void appendAddonQuickListDetails(List<Component> tooltipLines, RecipeNode n) {
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
                int totalEff = com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getTotalTurbineEfficiency(n);
                effStr += " §a(+" + holderBonus + "% Holder -> " + totalEff + "%)";
            }
            tooltipLines.add(Component.literal(effStr));
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.rotor.power").getString() + ": §f" + pwr + "%"));
            if (com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isDynamoBottleneck(n)) {
                int pmax = com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator.getMaxParallelCapacity(n);
                int holderPmax = com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getRotorHolderMaxParallel(n);
                tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dyn_bottleneck_detail").getString(), pmax, holderPmax)));
            }
            if (n.getTotalParallel() > 1) {
                tooltipLines.add(Component.literal("§b⚙ " + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(n.getTotalParallel())).getString()));
            }
        } else if (com.gtceu.calcboard.api.util.ModCompatHelper.isCreateMachine(n)) {
            tooltipLines.add(Component.literal("§6⚙ " + n.getRpm() + " RPM"));
        } else {
            String parStr = "§b" + Component.translatable("gui.gtcalcboard.config.base_parallel", String.valueOf(n.getParallel())).getString()
                    + " §7(" + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(n.getTotalParallel())).getString() + "§7)";
            tooltipLines.add(Component.literal(parStr));
        }

        List<MachineAddon> addons = n.getAddons();
        if (!addons.isEmpty()) {
            tooltipLines.add(Component.literal("§6" + Component.translatable("gui.gtcalcboard.config.active_addons", String.valueOf(addons.size())).getString() + ":"));
            for (var a : addons) {
                String badge = MachineConfigDialog.formatAddonBadge(a, n);
                tooltipLines.add(Component.literal(" §7• §f" + a.getName() + (!badge.isEmpty() ? " " + badge : "")));
                if (BoardManager.getInstance().isShowDebugInfo() && a.getDiscoverySource() != null && !a.getDiscoverySource().isEmpty()) {
                    tooltipLines.add(Component.literal("   §8↳ §b" + a.getDiscoverySource()));
                }
            }
        } else {
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.config.no_addons_installed").getString()));
        }

        if (BoardManager.getInstance().isShowDebugInfo()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§7[Debug] §8Node ID: §7" + n.getId()));
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(n);
            tooltipLines.add(Component.literal("§7[Debug] §8Adapter: §a" + adapter.getClass().getSimpleName() + " (Priority " + adapter.getPriority() + ")"));
            if (n.getMachineIcon() != null) {
                tooltipLines.add(Component.literal("§7[Debug] §8Machine Icon: §6" + n.getMachineIcon()));
            }
        }
    }

    private static boolean renderInputPortTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, int inIdx, int mouseX, int mouseY) {
        if (inIdx < 0 || inIdx >= widget.getNode().getInputs().size()) {
            return false;
        }

        IngredientStack in = widget.getNode().getInputs().get(inIdx);
        FlowGraph graph = screen.getGraph();
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getInputPortStats(widget.getNode(), inIdx) : new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        boolean showExact = Screen.hasShiftDown();
        boolean[] hiddenRef = new boolean[]{false};

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§b[📥 " + Component.translatable("gui.gtcalcboard.input").getString() + "] §f" + in.getDisplayName()));

        String reqDisplay = formatPortRate(stats.requiredOrProducedRate(), in.isFluid(), showExact, hiddenRef);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.demand").getString() + ": §f" + reqDisplay));

        appendInputPortStats(tooltipLines, stats, in, showExact, hiddenRef);

        if (in.getChance() < 1.0) {
            tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format(java.util.Locale.ROOT, "%.1f", in.getChance() * 100.0))));
        }
        if (in.hasAlternatives()) {
            int curIdx = in.getAlternatives().indexOf(in.getId()) + 1;
            tooltipLines.add(Component.literal("§6[⟲ " + Component.translatable("gui.gtcalcboard.tooltip.scroll_cycle").getString() + "]: §e" + Component.translatable("gui.gtcalcboard.tooltip.tag_alts", String.valueOf(curIdx), String.valueOf(in.getAlternatives().size())).getString()));
        }

        if (BoardManager.getInstance().isShowDebugInfo()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§7[Debug] §8Port: §fInput #" + inIdx));
            if (in.getId() != null) {
                tooltipLines.add(Component.literal("§7[Debug] §8ID: §7" + in.getId()));
            }
        }

        if (hiddenRef[0]) {
            tooltipLines.add(Component.literal("§7[Shift]: §8" + Component.translatable("gui.gtcalcboard.tooltip.shift_exact").getString()));
        }
        tooltipLines.add(Component.literal("§7[Drag]: §f" + Component.translatable("gui.gtcalcboard.tooltip.drag_connect").getString()));
        tooltipLines.add(Component.literal("§e[Shift+Drag]: §a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.shift_auto_ratio").getString()));
        tooltipLines.add(Component.literal("§c[Right-Click]: §7" + Component.translatable("gui.gtcalcboard.tooltip.right_click_hide").getString()));
        tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
        renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static boolean renderOutputPortTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, int outIdx, int mouseX, int mouseY) {
        if (outIdx < 0 || outIdx >= widget.getNode().getOutputs().size()) {
            return false;
        }

        IngredientStack out = widget.getNode().getOutputs().get(outIdx);
        FlowGraph graph = screen.getGraph();
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getOutputPortStats(widget.getNode(), outIdx) : new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        boolean showExact = Screen.hasShiftDown();
        boolean[] hiddenRef = new boolean[]{false};

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§a[📤 " + Component.translatable("gui.gtcalcboard.output").getString() + "] §f" + out.getDisplayName()));
        if (widget.getNode().isOutputPortVoided(outIdx)) {
            tooltipLines.add(Component.literal("§d[∅] §d" + Component.translatable("gui.gtcalcboard.tooltip.voided_port").getString()));
        }

        String prodDisplay = formatPortRate(stats.requiredOrProducedRate(), out.isFluid(), showExact, hiddenRef);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.production").getString() + ": §f" + prodDisplay));

        appendOutputPortStats(tooltipLines, stats, out, showExact, hiddenRef);

        RecipeNode node = widget.getNode();
        appendOutputChanceInfo(tooltipLines, node, out, outIdx);

        if (BoardManager.getInstance().isShowDebugInfo()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§7[Debug] §8Port: §fOutput #" + outIdx));
            if (out.getId() != null) {
                tooltipLines.add(Component.literal("§7[Debug] §8ID: §7" + out.getId()));
            }
        }

        if (hiddenRef[0]) {
            tooltipLines.add(Component.literal("§7[Shift]: §8" + Component.translatable("gui.gtcalcboard.tooltip.shift_exact").getString()));
        }
        tooltipLines.add(Component.literal("§7[Drag]: §f" + Component.translatable("gui.gtcalcboard.tooltip.drag_connect").getString()));
        tooltipLines.add(Component.literal("§e[Shift+Drag]: §a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.shift_auto_ratio").getString()));
        tooltipLines.add(Component.literal("§c[Right-Click]: §7" + Component.translatable("gui.gtcalcboard.tooltip.right_click_hide").getString()));
        tooltipLines.add(Component.literal("§d[Ctrl/Alt+Right-Click]: §f" + Component.translatable("gui.gtcalcboard.tooltip.alt_right_click_void").getString()));
        tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
        renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static void appendOutputChanceInfo(List<Component> tooltipLines, RecipeNode node, IngredientStack out, int outIdx) {
        double effChance = node != null ? node.getEffectiveOutputChance(outIdx) : out.getChance();
        if (effChance >= 1.0 && out.getChance() >= 1.0) {
            return;
        }

        String chanceLabel = Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim();
        if (effChance <= 0.0) {
            if (node != null && node.getSteamMode().isSteam()) {
                tooltipLines.add(Component.literal("§c⚠ " + chanceLabel + ": 0% (Steam Mode: No Byproducts)"));
            } else if (node != null && ResourceLocation.tryParse("gtceu:macerator").equals(node.getRecipeCategoryId())) {
                com.gtceu.calcboard.api.type.GTVoltageTier reqTier = (node.getRecipeTier() != null && node.getRecipeTier().ordinal() > com.gtceu.calcboard.api.type.GTVoltageTier.HV.ordinal())
                        ? node.getRecipeTier()
                        : com.gtceu.calcboard.api.type.GTVoltageTier.HV;
                tooltipLines.add(Component.literal("§c⚠ " + chanceLabel + ": 0% (Requires " + reqTier.getName() + "+)"));
            } else {
                tooltipLines.add(Component.literal("§c⚠ " + chanceLabel + ": 0%"));
            }
            return;
        }

        if (out.getTierChanceBoost() > 0.0) {
            int tierDelta = node != null ? node.getTierDelta() : 0;
            if (tierDelta > 0 && effChance > out.getChance()) {
                tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§e%s: %.1f%% §7(+%.1f%%/Tier §a→ %.1f%%§7)",
                        chanceLabel,
                        out.getChance() * 100.0,
                        out.getTierChanceBoost() * 100.0,
                        effChance * 100.0)));
            } else {
                tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§e%s: %.1f%% §7(+%.1f%%/Tier)",
                        chanceLabel,
                        out.getChance() * 100.0,
                        out.getTierChanceBoost() * 100.0)));
            }
            return;
        }

        tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format(java.util.Locale.ROOT, "%.1f", effChance * 100.0))));
    }

    public static String formatPortRate(double rate, boolean isFluid, boolean showExact, boolean[] hiddenExactRef) {
        String compact = NodeCardRenderer.formatRate(rate, isFluid);
        String exact = FormatUtil.formatExactRate(rate, isFluid);
        if (!compact.equals(exact)) {
            if (hiddenExactRef != null && !showExact) {
                hiddenExactRef[0] = true;
            }
            if (showExact) {
                return compact + " §8(" + exact + ")";
            }
        }
        return compact;
    }

    private static void appendInputPortStats(List<Component> tooltipLines, FlowGraphSolver.PortFlowStats stats, IngredientStack in, boolean showExact, boolean[] hiddenRef) {
        if (!stats.isConnected()) {
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_raw").getString()));
            return;
        }

        String supDisplay = formatPortRate(stats.connectedRate(), in.isFluid(), showExact, hiddenRef);
        String percentCol = stats.isBalanced() ? "§a" : (stats.isInputDeficit() ? "§c" : "§b");

        if (stats.isBalanced()) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay + " §7(§a✔ 100%§7)"));
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_producers", String.valueOf(stats.connectionCount())).getString()));
            return;
        }

        if (stats.isInputDeficit()) {
            double deficit = stats.requiredOrProducedRate() - stats.connectedRate();
            double defPercent = (1.0 - stats.getRatio()) * 100.0;
            String defDisplay = formatPortRate(deficit, in.isFluid(), showExact, hiddenRef);

            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay
                    + String.format(java.util.Locale.ROOT, " §7(§c%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.fulfilled").getString())));
            tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.tooltip.deficit").getString() + ": §c-" + defDisplay
                    + String.format(java.util.Locale.ROOT, " §7(-%.1f%%)", defPercent)));
        } else {
            double surplus = stats.connectedRate() - stats.requiredOrProducedRate();
            double surPercent = (stats.getRatio() - 1.0) * 100.0;
            String surDisplay = formatPortRate(surplus, in.isFluid(), showExact, hiddenRef);

            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay
                    + String.format(java.util.Locale.ROOT, " §7(§b+%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.fulfilled").getString())));
            tooltipLines.add(Component.literal("§b+ " + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ": §b+" + surDisplay
                    + String.format(java.util.Locale.ROOT, " §7(+%.1f%%)", surPercent)));
        }

        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_producers", String.valueOf(stats.connectionCount())).getString()));
    }

    private static void appendOutputPortStats(List<Component> tooltipLines, FlowGraphSolver.PortFlowStats stats, IngredientStack out, boolean showExact, boolean[] hiddenRef) {
        if (!stats.isConnected()) {
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_final").getString()));
            return;
        }

        String demDisplay = formatPortRate(stats.connectedRate(), out.isFluid(), showExact, hiddenRef);
        String percentCol = stats.isBalanced() ? "§a" : (stats.isOutputSurplus() ? "§b" : "§c");

        if (stats.isBalanced()) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demDisplay + " §7(§a✔ 100%§7)"));
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_consumers", String.valueOf(stats.connectionCount())).getString()));
            return;
        }

        if (stats.isOutputSurplus()) {
            double surplus = stats.requiredOrProducedRate() - stats.connectedRate();
            double surPercent = (1.0 - stats.getRatio()) * 100.0;
            String surDisplay = formatPortRate(surplus, out.isFluid(), showExact, hiddenRef);

            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demDisplay
                    + String.format(java.util.Locale.ROOT, " §7(§b%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.consumed_stat").getString())));
            tooltipLines.add(Component.literal("§b+ " + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ": §b+" + surDisplay
                    + String.format(java.util.Locale.ROOT, " §7(+%.1f%%)", surPercent)));
        } else {
            double deficit = stats.connectedRate() - stats.requiredOrProducedRate();
            double defPercent = (stats.getRatio() - 1.0) * 100.0;
            String defDisplay = formatPortRate(deficit, out.isFluid(), showExact, hiddenRef);

            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demDisplay
                    + String.format(java.util.Locale.ROOT, " §7(§c%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.demanded_stat").getString())));
            tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.tooltip.deficit").getString() + ": §c-" + defDisplay
                    + String.format(java.util.Locale.ROOT, " §7(-%.1f%%)", defPercent)));
        }

        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_consumers", String.valueOf(stats.connectionCount())).getString()));
    }

    private static boolean renderRerouteTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, FlowGraph graph, int mouseX, int mouseY) {
        if (!widget.getNode().isReroute()) {
            return false;
        }

        List<Component> tooltipLines = new ArrayList<>();
        RecipeNode rNode = widget.getNode();
        IngredientStack rStack = !rNode.getInputs().isEmpty() ? rNode.getInputs().get(0) : null;

        if (rStack == null) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.reroute_junction").getString()));
            renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
            return true;
        }

        boolean hasIncoming = graph != null && graph.getConnections().stream().anyMatch(e -> e.toNodeId().equals(rNode.getId()) && e.inputIndex() == 0);
        double upstreamSupply = calculateRerouteSupply(rNode, graph, hasIncoming);
        double downstreamDemand = graph != null ? FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, rNode, 0, null) : 0.0;
        boolean hasSupply = hasIncoming || rNode.isExternalSupply() || rNode.isInfiniteSupply();
        boolean isInputSource = !hasSupply && !rNode.isVoidSink() && downstreamDemand > 0.0001;

        appendRerouteHeader(rNode, rStack, isInputSource, tooltipLines);
        appendRerouteFlowRates(rNode, rStack, graph, upstreamSupply, downstreamDemand, hasSupply, isInputSource, tooltipLines);
        appendRerouteTargetBatch(rNode, rStack, graph, upstreamSupply, downstreamDemand, isInputSource, tooltipLines);

        renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static double calculateRerouteSupply(RecipeNode rNode, FlowGraph graph, boolean hasIncoming) {
        if (rNode.isInfiniteSupply()) {
            return Double.POSITIVE_INFINITY;
        }
        if (rNode.isExternalSupply()) {
            return rNode.getExternalSupplyRate();
        }
        if (graph != null && hasIncoming) {
            return com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateNetInflowRate(graph, rNode, 0);
        }
        return 0.0;
    }

    private static void appendRerouteHeader(RecipeNode rNode, IngredientStack rStack, boolean isInputSource, List<Component> tooltipLines) {
        if (!rNode.hasTargetBatch()) {
            tooltipLines.add(Component.literal("§6🔀 §f" + rStack.getDisplayName() + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.reroute_junction").getString() + ")"));
            return;
        }
        String titleKey = isInputSource ? "gui.gtcalcboard.dt.tooltip.title" : "gui.gtcalcboard.eta.tooltip.title";
        String icon = isInputSource ? "§b📥" : "§6🎯";
        tooltipLines.add(Component.literal(icon + " §f" + Component.translatable(titleKey).getString() + " §7(" + rStack.getDisplayName() + "§7)"));
    }

    private static void appendRerouteFlowRates(RecipeNode rNode, IngredientStack rStack, FlowGraph graph, double upstreamSupply, double downstreamDemand, boolean hasSupply, boolean isInputSource, List<Component> tooltipLines) {
        if (hasSupply) {
            String supStr = Double.isInfinite(upstreamSupply) ? "∞" : ("+" + FormatUtil.formatRate(upstreamSupply, rStack));
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": §a" + supStr));
        }
        if (downstreamDemand > 0.0001) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.demand").getString() + ": §c-" + FormatUtil.formatRate(downstreamDemand, rStack)));
        }

        double netSurplus = upstreamSupply - downstreamDemand;
        if (hasSupply && downstreamDemand > 0.0001) {
            appendBalancedRateLine(upstreamSupply, netSurplus, rStack, tooltipLines);
            return;
        }
        if (isInputSource) {
            double actualDrainRate = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateNetOutflowRate(graph, rNode);
            double displayDrain = actualDrainRate > 0.0001 ? actualDrainRate : downstreamDemand;
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.dt.tooltip.rate").getString() + ": §e-" + FormatUtil.formatRate(displayDrain, rStack) + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_raw").getString() + ")"));
            return;
        }
        if (hasSupply) {
            String supStr = Double.isInfinite(upstreamSupply) ? "∞" : ("+" + FormatUtil.formatRate(upstreamSupply, rStack));
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §a" + supStr));
            return;
        }
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §70/s"));
    }

    private static void appendBalancedRateLine(double upstreamSupply, double netSurplus, IngredientStack rStack, List<Component> tooltipLines) {
        if (Double.isInfinite(upstreamSupply)) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §b+∞ §7(" + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ")"));
            return;
        }
        if (Math.abs(netSurplus) < 0.0001) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §a✔ " + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + " = " + Component.translatable("gui.gtcalcboard.tooltip.demand").getString()));
            return;
        }
        if (netSurplus > 0) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §b+" + FormatUtil.formatRate(netSurplus, rStack) + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ")"));
            return;
        }
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §c-" + FormatUtil.formatRate(-netSurplus, rStack) + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.deficit").getString() + ")"));
    }

    private static void appendRerouteTargetBatch(RecipeNode rNode, IngredientStack rStack, FlowGraph graph, double upstreamSupply, double downstreamDemand, boolean isInputSource, List<Component> tooltipLines) {
        if (!rNode.hasTargetBatch()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.eta.tooltip.click_set").getString()));
            return;
        }

        double targetAmount = rNode.getTargetBatchAmount();
        String goalStr = FormatUtil.formatBatchAmount(targetAmount, rStack.isFluid());
        String targetKey = isInputSource ? "gui.gtcalcboard.dt.tooltip.target" : "gui.gtcalcboard.eta.tooltip.target";
        tooltipLines.add(Component.literal("§7" + Component.translatable(targetKey).getString() + ": §e" + goalStr));

        if (isInputSource) {
            appendRerouteDepletionTime(graph, rNode, targetAmount, tooltipLines);
        } else {
            appendRerouteEstimatedTime(graph, rNode, targetAmount, upstreamSupply, downstreamDemand, tooltipLines);
        }

        tooltipLines.add(Component.literal("§8§m------------------------"));
        tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.eta.tooltip.click_edit").getString()));
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.shift_reset").getString()));
    }

    private static void appendRerouteDepletionTime(FlowGraph graph, RecipeNode rNode, double targetAmount, List<Component> tooltipLines) {
        double drainRate = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateNetOutflowRate(graph, rNode);
        double depletionSec = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateDepletionTime(graph, rNode, targetAmount, drainRate);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.dt.tooltip.duration").getString() + ": §b" + FormatUtil.formatETA(depletionSec)));
    }

    private static void appendRerouteEstimatedTime(FlowGraph graph, RecipeNode rNode, double targetAmount, double upstreamSupply, double downstreamDemand, List<Component> tooltipLines) {
        double netSurplus = upstreamSupply - downstreamDemand;
        double effectiveRate = upstreamSupply > 0.0001 ? (netSurplus > 0.0001 ? netSurplus : upstreamSupply) : 0.0;
        double etaSec = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateETA(graph, rNode, targetAmount, effectiveRate);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.duration").getString() + ": §b" + FormatUtil.formatETA(etaSec)));

        double totalEU = com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateTotalEnergyForBatch(graph, rNode, targetAmount);
        if (totalEU > 0) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.total_energy").getString() + ": §e" + FormatUtil.formatCompactNumber(totalEU) + " EU"));
        }
    }
}




