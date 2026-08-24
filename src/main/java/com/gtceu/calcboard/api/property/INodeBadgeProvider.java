package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.RecipeNode;

import java.util.List;

/**
 * Functional interface providing declarative {@link NodeBadge}s by inspecting a node's properties.
 */
public interface INodeBadgeProvider {

    /**
     * Inspects the node and its property store to produce badges.
     *
     * @param node The recipe node being rendered.
     * @param store The node's property store.
     * @return List of badges to display on the node card.
     */
    List<NodeBadge> createBadges(RecipeNode node, NodePropertyStore store);
}
