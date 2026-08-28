package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.model.RecipeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of declarative {@link INodeBadgeProvider}s for assembling node badges.
 */
public final class NodeBadgeRegistry {

    private static final List<INodeBadgeProvider> PROVIDERS = new ArrayList<>();

    static {
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            if (Boolean.TRUE.equals(store.get(NodeProperties.IS_GENERIC_UNSUPPORTED))) {
                String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported").getString();
                List<net.minecraft.network.chat.Component> tooltip = List.of(
                        net.minecraft.network.chat.Component.literal("§6🛠 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported_title").getString()),
                        net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported_desc").getString()),
                        net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported_hint").getString())
                );
                return List.of(new NodeBadge(badgeText, 0xFFFB923C, 0xEE3D2C1C, 0xFFFB923C, tooltip));
            }
            return List.of();
        });

        // Compound / Layered Recipe Badge Provider
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            if (node.isCompoundNode()) {
                int layerIdx = node.getCompoundLayerIndex();
                int totalLayers = node.getCompoundTotalLayers();
                String roman = com.gtceu.calcboard.api.model.CompoundRecipeBuilder.formatRoman(layerIdx + 1);
                String badgeText = "🧩 " + roman + "/" + com.gtceu.calcboard.api.model.CompoundRecipeBuilder.formatRoman(totalLayers);
                List<net.minecraft.network.chat.Component> tooltip = List.of(
                        net.minecraft.network.chat.Component.literal("§d🧩 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.compound_layer_title", roman, totalLayers).getString()),
                        net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.compound_layer_desc").getString()),
                        net.minecraft.network.chat.Component.literal("§b🔗 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.compound_layer_hint").getString())
                );
                return List.of(new NodeBadge(badgeText, 0xFFE879F9, 0xEE3B1D45, 0xFFC084FC, tooltip));
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

