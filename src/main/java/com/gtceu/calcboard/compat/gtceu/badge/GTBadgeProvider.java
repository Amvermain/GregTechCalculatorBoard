package com.gtceu.calcboard.compat.gtceu.badge;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.NumberFormatUtil;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Registers GTCEu-specific node badges and tooltips (Fusion, Cleanroom, Reflector, Turbine Deficit).
 */
public final class GTBadgeProvider {

    private GTBadgeProvider() {}

    public static void registerAll() {
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            long startEU = store.get(NodeProperties.FUSION_START_EU);
            boolean isFusionCat = node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion_reactor");
            if (startEU <= 0 && !isFusionCat && !node.isFusion()) return List.of();

            GTVoltageTier ctrlTier = GTCEuModAdapter.extractVoltageTierFromIcon(node.getMachineIcon());
            GTVoltageTier tier = (ctrlTier != null) ? ctrlTier : node.getTargetTier();
            String mkLabel;
            if (tier == GTVoltageTier.ZPM) mkLabel = "Mk2";
            else if (tier == GTVoltageTier.UV) mkLabel = "Mk3";
            else if (tier == GTVoltageTier.UHV) mkLabel = "AUX I";
            else if (tier == GTVoltageTier.UEV) mkLabel = "Mk4";
            else if (tier == GTVoltageTier.UIV) mkLabel = "AUX II";
            else if (tier == GTVoltageTier.UXV) mkLabel = "Mk5";
            else if (tier == GTVoltageTier.OpV) mkLabel = "AUX III";
            else if (tier == GTVoltageTier.MAX) mkLabel = "Mk6";
            else if (tier == GTVoltageTier.LuV) mkLabel = "Mk1";
            else {
                int fTier = startEU <= 160_000_000L ? 1 : (startEU <= 320_000_000L ? 2 : 3);
                mkLabel = "Mk" + fTier;
            }

            String tierBadgeText = "⚛ " + mkLabel;
            List<Component> tierTooltip = List.of(
                    Component.literal(String.format(Locale.ROOT, "§d⚛ Fusion Reactor %s", mkLabel)),
                    Component.literal(String.format(Locale.ROOT, "§7Operating Voltage Tier: §f%s", node.getTargetTier().getName()))
            );
            NodeBadge tierBadge = new NodeBadge(tierBadgeText, 0xFFFFFFFF, 0xEE3D1B5E, 0xFFCC44FF, tierTooltip);

            if (startEU > 0) {
                String startText = "⚡ " + NumberFormatUtil.formatCompactNumber(startEU) + " EU";
                List<Component> startTooltip = List.of(
                        Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.fusion_start_buffer_title").getString()),
                        Component.literal(String.format(Locale.ROOT, "§7Required Ignition Energy: §e%,d EU", startEU)),
                        Component.literal(String.format(Locale.ROOT, "§7Formatted: §f%s EU", NumberFormatUtil.formatCompactNumber(startEU)))
                );
                NodeBadge startBadge = new NodeBadge(startText, 0xFFFFAA00, 0xEE3D2B1E, 0xFFFFAA00, startTooltip);
                return List.of(tierBadge, startBadge);
            }

            return List.of(tierBadge);
        });

        // 2. Cleanroom Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            String cleanroom = store.get(NodeProperties.CLEANROOM_TYPE);
            if (cleanroom == null || cleanroom.isEmpty()) return List.of();

            String label = cleanroom.toLowerCase(Locale.ROOT).contains("sterile") ? "☣ Sterile" : "🧹 Cleanroom";
            List<Component> tooltip = List.of(
                    Component.literal("§b🧹 Cleanroom Required"),
                    Component.literal("§7Type: §f" + cleanroom)
            );
            return List.of(new NodeBadge(label, 0xFF55FFFF, 0xEE1E2D3D, 0xFF55FFFF, tooltip));
        });

        // 3. Fusion Reflector Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            int reqTier = store.get(NodeProperties.REQUIRED_REFLECTOR_TIER);
            int instTier = node.getInstalledReflectorTier();
            if (reqTier <= 0 && instTier <= 0) return List.of();

            if (reqTier > 0) {
                if (instTier >= reqTier) {
                    String badgeText = "✦ T" + instTier;
                    List<Component> tooltip = List.of(
                            Component.literal("§b✦ " + Component.translatable("gui.gtcalcboard.reflector_valid_title").getString()),
                            Component.literal(String.format(Locale.ROOT, "§7Installed Reflector: §aTier %d", instTier)),
                            Component.literal(String.format(Locale.ROOT, "§7Required Reflector: §fTier %d", reqTier)),
                            Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.reflector_met").getString())
                    );
                    return List.of(new NodeBadge(badgeText, 0xFF55FFFF, 0xEE1E3D3D, 0xFF55FFFF, tooltip));
                } else {
                    String badgeText = Component.translatable("gui.gtcalcboard.node_badge.reflector_required", reqTier).getString();
                    List<Component> tooltip = List.of(
                            Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.reflector_missing_title").getString()),
                            Component.literal(String.format(Locale.ROOT, "§7Required Reflector: §cTier %d", reqTier)),
                            Component.literal(String.format(Locale.ROOT, "§7Installed Reflector: §e%s", instTier > 0 ? ("Tier " + instTier) : Component.translatable("gui.gtcalcboard.reflector.none").getString())),
                            Component.literal("§c❌ " + String.format(Locale.ROOT, Component.translatable("gui.gtcalcboard.reflector_missing_desc").getString(), reqTier))
                    );
                    return List.of(new NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
                }
            } else if (instTier > 0) {
                String badgeText = "✦ T" + instTier;
                List<Component> tooltip = List.of(
                        Component.literal("§b✦ " + Component.translatable("gui.gtcalcboard.reflector_installed_title").getString()),
                        Component.literal(String.format(Locale.ROOT, "§7Installed Reflector: §bTier %d", instTier))
                );
                return List.of(new NodeBadge(badgeText, 0xFF55FFFF, 0xEE1E3D3D, 0xFF55FFFF, tooltip));
            }
            return List.of();
        });

        // 4. Turbine Deficit Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || !GTTurbineHelper.isTurbine(node)) return List.of();
            FlowGraph graph = BoardManager.getInstance().getActiveGraph();
            if (graph != null && GTTurbineHelper.hasTurbineFlowDeficit(node, graph)) {
                String badgeText = Component.translatable("gui.gtcalcboard.node_badge.turbine_deficit").getString();
                List<Component> tooltip = List.of(
                        Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.turbine_deficit_title").getString()),
                        Component.literal("§7" + Component.translatable("gui.gtcalcboard.turbine_deficit_desc").getString()),
                        Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.node_warning.inactive").getString())
                );
                return List.of(new NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
            }
            return List.of();
        });
    }
}
