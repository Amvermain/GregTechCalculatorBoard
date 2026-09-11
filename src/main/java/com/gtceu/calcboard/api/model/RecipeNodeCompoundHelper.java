package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;

/**
 * Helper for managing compound (multi-layered) recipe metadata on RecipeNode properties.
 */
public final class RecipeNodeCompoundHelper {

    private RecipeNodeCompoundHelper() {}

    public static boolean isCompoundNode(NodePropertyStore properties) {
        return properties.has(NodeProperties.COMPOUND_GROUP_ID)
                && !properties.get(NodeProperties.COMPOUND_GROUP_ID).isEmpty();
    }

    public static boolean isCompoundMaster(NodePropertyStore properties) {
        return isCompoundNode(properties) && getCompoundLayerIndex(properties) == 0;
    }

    public static String getCompoundGroupId(NodePropertyStore properties) {
        return properties.get(NodeProperties.COMPOUND_GROUP_ID);
    }

    public static int getCompoundLayerIndex(NodePropertyStore properties) {
        return properties.get(NodeProperties.COMPOUND_LAYER_INDEX);
    }

    public static int getCompoundTotalLayers(NodePropertyStore properties) {
        return properties.get(NodeProperties.COMPOUND_TOTAL_LAYERS);
    }

    public static String getCompoundMasterNodeId(NodePropertyStore properties) {
        return properties.get(NodeProperties.COMPOUND_MASTER_NODE_ID);
    }

    public static void setCompoundMetadata(NodePropertyStore properties, String groupId, int layerIndex, int totalLayers, String masterId) {
        if (groupId == null || groupId.isEmpty()) {
            properties.remove(NodeProperties.COMPOUND_GROUP_ID);
            properties.remove(NodeProperties.COMPOUND_LAYER_INDEX);
            properties.remove(NodeProperties.COMPOUND_TOTAL_LAYERS);
            properties.remove(NodeProperties.COMPOUND_MASTER_NODE_ID);
        } else {
            properties.set(NodeProperties.COMPOUND_GROUP_ID, groupId);
            properties.set(NodeProperties.COMPOUND_LAYER_INDEX, Math.max(0, layerIndex));
            properties.set(NodeProperties.COMPOUND_TOTAL_LAYERS, Math.max(1, totalLayers));
            properties.set(NodeProperties.COMPOUND_MASTER_NODE_ID, masterId != null ? masterId : "");
        }
    }
}
