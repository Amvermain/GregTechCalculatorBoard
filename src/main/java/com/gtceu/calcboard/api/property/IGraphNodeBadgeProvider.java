package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import java.util.List;

/**
 * Extension of {@link INodeBadgeProvider} that receives the enclosing {@link FlowGraph}
 * to dynamically evaluate flow topology and port states.
 */
@FunctionalInterface
public interface IGraphNodeBadgeProvider extends INodeBadgeProvider {

    @Override
    default List<NodeBadge> createBadges(RecipeNode node, NodePropertyStore store) {
        return createBadges(node, store, node != null ? node.getParentGraph() : null);
    }

    @Override
    List<NodeBadge> createBadges(RecipeNode node, NodePropertyStore store, FlowGraph graph);
}
