package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.FormatUtil;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Registry of declarative {@link INodeBadgeProvider}s for assembling node badges.
 */
public final class NodeBadgeRegistry {

    private static final List<INodeBadgeProvider> PROVIDERS = new ArrayList<>();

    static {
        // 1. Fusion Reactor Badges Provider (RFC-001 & RFC-002)
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            long startEU = store.get(NodeProperties.FUSION_START_EU);
            boolean isFusionCat = node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion_reactor");
            if (startEU <= 0 && !isFusionCat) return List.of();

            int fTier = startEU <= 160_000_000L ? 1 : (startEU <= 320_000_000L ? 2 : 3);
            String tierBadgeText = "⚛ Mk" + fTier;
            List<Component> tierTooltip = List.of(
                    Component.literal(String.format(Locale.ROOT, "§d⚛ Fusion Reactor Mk%d", fTier)),
                    Component.literal(String.format(Locale.ROOT, "§7Min Voltage Tier: §f%s", fTier == 1 ? "LuV" : (fTier == 2 ? "ZPM" : "UV")))
            );
            NodeBadge tierBadge = new NodeBadge(tierBadgeText, 0xFFFFFFFF, 0xEE3D1B5E, 0xFFCC44FF, tierTooltip);

            if (startEU > 0) {
                String startText = "⚡ " + FormatUtil.formatCompactNumber(startEU) + " EU";
                List<Component> startTooltip = List.of(
                        Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.fusion_start_buffer_title").getString()),
                        Component.literal(String.format(Locale.ROOT, "§7Required Ignition Energy: §e%,d EU", startEU)),
                        Component.literal(String.format(Locale.ROOT, "§7Formatted: §f%s EU", FormatUtil.formatCompactNumber(startEU)))
                );
                NodeBadge startBadge = new NodeBadge(startText, 0xFFFFAA00, 0xEE3D2B1E, 0xFFFFAA00, startTooltip);
                return List.of(tierBadge, startBadge);
            }

            return List.of(tierBadge);
        });

        // 2. Cleanroom Badge Provider
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            String cleanroom = store.get(NodeProperties.CLEANROOM_TYPE);
            if (cleanroom == null || cleanroom.isEmpty()) return List.of();

            String label = cleanroom.toLowerCase().contains("sterile") ? "☣ Sterile" : "🧹 Cleanroom";
            List<Component> tooltip = List.of(
                    Component.literal("§b🧹 Cleanroom Required"),
                    Component.literal("§7Type: §f" + cleanroom)
            );
            return List.of(new NodeBadge(label, 0xFF55FFFF, 0xEE1E2D3D, 0xFF55FFFF, tooltip));
        });

        // 3. Fusion Reflector Badge Provider
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            int reqTier = store.get(NodeProperties.REQUIRED_REFLECTOR_TIER);
            int instTier = node.getInstalledReflectorTier();
            if (reqTier <= 0 && instTier <= 0) return List.of();

            if (reqTier > 0) {
                if (instTier >= reqTier) {
                    String badgeText = "🪞 T" + instTier;
                    List<Component> tooltip = List.of(
                            Component.literal("§b🪞 " + Component.translatable("gui.gtcalcboard.reflector_valid_title").getString()),
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
                            Component.literal(String.format(Locale.ROOT, "§7Installed Reflector: §e%s", instTier > 0 ? ("Tier " + instTier) : Component.translatable("gui.gtcalcboard.none").getString())),
                            Component.literal("§c❌ " + String.format(Locale.ROOT, Component.translatable("gui.gtcalcboard.reflector_missing_desc").getString(), reqTier))
                    );
                    return List.of(new NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
                }
            } else if (instTier > 0) {
                String badgeText = "🪞 T" + instTier;
                List<Component> tooltip = List.of(
                        Component.literal("§b🪞 " + Component.translatable("gui.gtcalcboard.reflector_installed_title").getString()),
                        Component.literal(String.format(Locale.ROOT, "§7Installed Reflector: §bTier %d", instTier))
                );
                return List.of(new NodeBadge(badgeText, 0xFF55FFFF, 0xEE1E3D3D, 0xFF55FFFF, tooltip));
            }
            return List.of();
        });

        // 4. Turbine Deficit Badge Provider
        register((node, store) -> {
            if (node == null || !com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isTurbine(node)) return List.of();
            FlowGraph graph = null;
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.gtceu.calcboard.client.gui.BoardScreen bs) {
                graph = bs.getGraph();
            }
            if (graph != null && com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.hasTurbineFlowDeficit(node, graph)) {
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

    private NodeBadgeRegistry() {}

    public static synchronized void register(INodeBadgeProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
        }
    }

    public static List<INodeBadgeProvider> getProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    /**
     * Gathers all badges provided for the specified node, sorting critical warning badges first.
     */
    public static List<NodeBadge> getBadgesForNode(RecipeNode node) {
        if (node == null) return List.of();
        NodePropertyStore store = node.getProperties();
        List<NodeBadge> list = new ArrayList<>();
        for (INodeBadgeProvider provider : PROVIDERS) {
            try {
                List<NodeBadge> badges = provider.createBadges(node, store);
                if (badges != null && !badges.isEmpty()) {
                    list.addAll(badges);
                }
            } catch (Throwable ignored) {}
        }
        return list;
    }
}
