package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.RecipeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of declarative {@link INodeBadgeProvider}s for assembling node badges.
 */
public final class NodeBadgeRegistry {

    private static final List<INodeBadgeProvider> PROVIDERS = new ArrayList<>();

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
