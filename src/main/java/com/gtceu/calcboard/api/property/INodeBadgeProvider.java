package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import java.util.List;

/**
 * Functional interface providing declarative {@link NodeBadge}s by inspecting a node's properties.
 */
@FunctionalInterface
public interface INodeBadgeProvider {

    /**
     * Inspects the node and its property store to produce badges.
     *
     * @param node The recipe node being rendered.
     * @param store The node's property store.
     * @return List of badges to display on the node card.
     */
    List<NodeBadge> createBadges(RecipeNode node, NodePropertyStore store);

    /**
     * Inspects the node, its property store, and the enclosing flow graph to produce badges.
     * Default implementation delegates to {@link #createBadges(RecipeNode, NodePropertyStore)}.
     *
     * @param node The recipe node being rendered.
     * @param store The node's property store.
     * @param graph The containing flow graph, or null if unattached.
     * @return List of badges to display on the node card.
     */
    default List<NodeBadge> createBadges(RecipeNode node, NodePropertyStore store, FlowGraph graph) {
        return createBadges(node, store);
    }
}

