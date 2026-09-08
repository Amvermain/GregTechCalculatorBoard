package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.greate.GreateProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NodeControlsTooltipRenderer {

    private NodeControlsTooltipRenderer() {}

    public static boolean renderTierButtonTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (!widget.isTierButtonHovered(canvasMouseX, canvasMouseY)) {
            return false;
        }

        RecipeNode n = widget.getNode();
        List<Component> tooltipLines;
        if (n.getProperties().get(GreateProperties.IS_GREATE)) {
            tooltipLines = buildGreateTierTooltipLines(n);
        } else if (SysteamsRecipeHandler.isDynamoToBoilerConvertible(n)) {
            tooltipLines = buildSysteamsTierTooltipLines(n);
        } else if (n.isMultiblock()) {
            tooltipLines = buildMultiblockTierTooltipLines(n);
        } else if (n.supportsSteamMode() && n.getSteamMode() != SteamMode.NONE) {
            tooltipLines = buildSteamTierTooltipLines(n);
        } else {
            tooltipLines = buildStandardTierTooltipLines(n);
        }

        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    public static boolean renderNodeBadgeTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        RecipeNode node = widget.getNode();
        if (node == null || node.isReroute()) return false;
        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(node);
        if (badges.isEmpty()) return false;

        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int row2Y = y + 20 + 6 + 18;
        if (canvasMouseY < row2Y || canvasMouseY > row2Y + 14) {
            return false;
        }

        int nextCtrlX = computeRow2ControlsStartX(node, font);
        int cardW = node.getCardWidth();

        for (NodeBadge badge : badges) {
            int badgeW = font.width(badge.text()) + 8;
            if (nextCtrlX + badgeW > x + cardW - 46) break;
            if (canvasMouseX >= nextCtrlX && canvasMouseX <= nextCtrlX + badgeW) {
                if (badge.tooltipLines() != null && !badge.tooltipLines().isEmpty()) {
                    BoardTooltipRenderer.renderComponentTooltip(graphics, font, badge.tooltipLines(), mouseX, mouseY, screen.width, screen.height);
                    return true;
                }
            }
            nextCtrlX += badgeW + 3;
        }
        return false;
    }

    public static boolean renderNodeInfoTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
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
        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    public static boolean renderCountBoxTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
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
        tooltipLines.add(Component.literal("§6▦ " + Component.translatable("gui.gtcalcboard.count").getString()));
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Exact Count: §f%,.4f", widget.getNode().getMachineCount())));
        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    public static boolean renderMachineConfigTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
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
                tooltipLines.add(Component.literal("§c❌ " + String.format(Locale.ROOT, Component.translatable("gui.gtcalcboard.node_warning.reflector_detail").getString(), String.valueOf(req), instStr)));
            }
            if (graph != null && GTTurbineHelper.hasTurbineFlowDeficit(n, graph)) {
                tooltipLines.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.turbine_deficit_desc").getString()));
            }
        }
        tooltipLines.add(Component.literal("§b⚙ " + Component.translatable("gui.gtcalcboard.config_dialog_title", n.getName()).getString()));

        String modeStr = n.isMultiblock() ? "§a" + Component.translatable("gui.gtcalcboard.config.multiblock_mode").getString()
                : "§7" + Component.translatable("gui.gtcalcboard.config.singleblock_mode").getString();
        tooltipLines.add(Component.literal(modeStr));

        appendAddonQuickListDetails(tooltipLines, n);

        tooltipLines.add(Component.literal("§e[ " + Component.translatable("gui.gtcalcboard.config.install").getString() + " / " + Component.translatable("gui.gtcalcboard.config.remove").getString() + " ]"));
        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static int computeRow2ControlsStartX(RecipeNode node, Font font) {
        int x = (int) node.getPosX();
        if (node.getEnergyType() == EnergyType.NONE) {
            return x + 42;
        }
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (node.isLiquidBoilerRecipe() || (adapter != null && adapter.isBoilerRecipe(node))) {
            var bTier = GTBoilerTier.getBoilerTier(node);
            String boilerText = bTier.getDisplayName();
            if (bTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            int tierBtnW = Math.max(54, font.width(boilerText) + 8);
            return x + 6 + tierBtnW + 4;
        }
        if (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
            int tierBtnW = Math.max(48, font.width(steamText) + 8);
            return x + 6 + tierBtnW + 4;
        }
        GTVoltageTier tier = node.getTargetTier();
        String tierName = (tier != null ? tier.getName() : "LV");
        if (node.isMultiblock()) tierName = "▦ " + tierName;
        int tierBtnW = Math.max(32, font.width(tierName) + 8);
        return x + 6 + tierBtnW + 4;
    }

    private static List<Component> buildGreateTierTooltipLines(RecipeNode n) {
        List<Component> list = new ArrayList<>();
        int mTier = Math.max(0, n.getProperties().get(GreateProperties.MACHINE_TIER));
        int rTier = Math.max(0, n.getProperties().get(GreateProperties.REQUIRED_RECIPE_TIER));
        String tName = GreateProperties.getTierName(mTier);
        double cap = GreateProperties.getShaftCapacityForTier(mTier);

        list.add(Component.literal("§6⚙ " + Component.translatable("gui.gtcalcboard.greate.stress_tier").getString() + " §7(" + tName + ")"));
        list.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.greate.shaft_capacity", String.format(Locale.ROOT, "%,.0f", cap)).getString()));

        if (rTier >= 0) {
            String reqName = GreateProperties.getTierName(rTier);
            list.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.greate.tier_deficit_desc", tName, reqName).getString()));
        }

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(n);
        double stress = adapter.computeOverclock(n, n.getTargetTier(), false).eut() * n.getMachineCount() * n.getCombinedEutMultiplier();
        if (stress > cap) {
            list.add(Component.literal(String.format(Locale.ROOT, "§c⚠ " + Component.translatable("gui.gtcalcboard.greate.stress_deficit", String.format(Locale.ROOT, "%,.0f", stress - cap)).getString())));
        } else {
            list.add(Component.literal(String.format(Locale.ROOT, "§a✔ " + Component.translatable("gui.gtcalcboard.greate.stress_headroom", String.format(Locale.ROOT, "%,.0f", cap - stress)).getString())));
        }

        list.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
        return list;
    }

    private static List<Component> buildSysteamsTierTooltipLines(RecipeNode n) {
        List<Component> list = new ArrayList<>();
        boolean isGen = n.isGenerator() || n.getEnergyType() == EnergyType.ELECTRIC_FE;
        if (isGen) {
            list.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.dynamo_badge").getString()));
            list.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.tooltip.systeams_toggle_to_boiler").getString()));
        } else {
            list.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.boiler_badge").getString()));
            list.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.tooltip.systeams_toggle_to_dynamo").getString()));
        }
        return list;
    }

    private static List<Component> buildMultiblockTierTooltipLines(RecipeNode n) {
        List<Component> list = new ArrayList<>();
        String tierName = n.getTargetTier() != null ? n.getTargetTier().getName() : "LV";
        if (n.isTurbine()) {
            list.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.config.voltage_tier").getString() + " §7(" + tierName + ")"));
            list.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
        } else if (!n.isGenerator()) {
            list.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.addon_cat.energy_hatch").getString() + " §7(" + tierName + ")"));
            list.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.multiblock_energy_hatch_hint").getString()));
        } else {
            list.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.config.voltage_tier").getString() + " §7(" + tierName + ")"));
            list.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
        }
        return list;
    }

    private static List<Component> buildSteamTierTooltipLines(RecipeNode n) {
        List<Component> list = new ArrayList<>();
        list.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.config.steam_tier").getString() + " §7(" + n.getSteamMode().name() + ")"));
        list.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_steam_tier").getString()));
        return list;
    }

    private static List<Component> buildStandardTierTooltipLines(RecipeNode n) {
        List<Component> list = new ArrayList<>();
        String tierName = n.getTargetTier() != null ? n.getTargetTier().getName() : "LV";
        list.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.config.voltage_tier").getString() + " §7(" + tierName + ")"));
        list.add(Component.literal("§7[Click / Scroll]: §f" + Component.translatable("gui.gtcalcboard.tooltip.cycle_tier").getString()));
        return list;
    }

    private static void appendAddonQuickListDetails(List<Component> tooltipLines, RecipeNode n) {
        if (n.isTurbine()) {
            appendTurbineAddonDetails(tooltipLines, n);
        } else if (ModCompatHelper.isCreateMachine(n)) {
            tooltipLines.add(Component.literal("§6⚙ " + n.getRpm() + " RPM"));
        } else {
            String parStr = "§b" + Component.translatable("gui.gtcalcboard.config.base_parallel", String.valueOf(n.getParallel())).getString()
                    + " §7(" + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(n.getTotalParallel())).getString() + "§7)";
            tooltipLines.add(Component.literal(parStr));
        }

        appendInstalledAddonList(tooltipLines, n);
        appendDebugInfo(tooltipLines, n);
    }

    private static void appendTurbineAddonDetails(List<Component> tooltipLines, RecipeNode n) {
        String rName = n.getRotorName();
        if (rName == null || rName.isEmpty() || rName.startsWith("Standard")) {
            rName = Component.translatable("gui.gtcalcboard.rotor.standard").getString();
        }
        tooltipLines.add(Component.literal("§6~ " + Component.translatable("gui.gtcalcboard.addon_cat.rotor").getString() + ": §f" + rName));
        int eff = n.getRotorEfficiency();
        int pwr = n.getRotorPower();
        int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(n);
        String effStr = "§b⏱ " + Component.translatable("gui.gtcalcboard.rotor.eff").getString() + ": §f" + eff + "%";
        if (holderBonus > 0) {
            int totalEff = GTTurbineHelper.getTotalTurbineEfficiency(n);
            effStr += " §a(+" + holderBonus + "% Holder -> " + totalEff + "%)";
        }
        tooltipLines.add(Component.literal(effStr));
        tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.rotor.power").getString() + ": §f" + pwr + "%"));
        if (GTTurbineHelper.isDynamoBottleneck(n)) {
            int pmax = GTPowerCalculator.getMaxParallelCapacity(n);
            int holderPmax = GTTurbineHelper.getRotorHolderMaxParallel(n);
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dyn_bottleneck_detail").getString(), pmax, holderPmax)));
        }
        if (n.getTotalParallel() > 1) {
            tooltipLines.add(Component.literal("§b⚙ " + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(n.getTotalParallel())).getString()));
        }
    }

    private static void appendInstalledAddonList(List<Component> tooltipLines, RecipeNode n) {
        List<MachineAddon> addons = n.getAddons();
        if (addons.isEmpty()) {
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.config.no_addons_installed").getString()));
            return;
        }

        tooltipLines.add(Component.literal("§6" + Component.translatable("gui.gtcalcboard.config.active_addons", String.valueOf(addons.size())).getString() + ":"));
        for (var a : addons) {
            String badge = MachineConfigDialog.formatAddonBadge(a, n);
            tooltipLines.add(Component.literal(" §7• §f" + a.getName() + (!badge.isEmpty() ? " " + badge : "")));
            if (BoardManager.getInstance().isShowDebugInfo() && a.getDiscoverySource() != null && !a.getDiscoverySource().isEmpty()) {
                tooltipLines.add(Component.literal("   §8↳ §b" + a.getDiscoverySource()));
            }
        }
    }

    private static void appendDebugInfo(List<Component> tooltipLines, RecipeNode n) {
        if (!BoardManager.getInstance().isShowDebugInfo()) return;
        tooltipLines.add(Component.literal("§8§m------------------------"));
        tooltipLines.add(Component.literal("§7[Debug] §8Node ID: §7" + n.getId()));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(n);
        tooltipLines.add(Component.literal("§7[Debug] §8Adapter: §a" + adapter.getClass().getSimpleName() + " (Priority " + adapter.getPriority() + ")"));
        if (n.getMachineIcon() != null) {
            tooltipLines.add(Component.literal("§7[Debug] §8Machine Icon: §6" + n.getMachineIcon()));
        }
    }
}
