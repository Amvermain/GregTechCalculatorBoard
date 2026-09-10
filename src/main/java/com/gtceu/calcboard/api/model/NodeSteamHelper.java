package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.SteamMode;

/**
 * Encapsulates steam mode transitions, adapter notification, and steam capabilities.
 */
public final class NodeSteamHelper {

    private NodeSteamHelper() {}

    public static void setSteamMode(RecipeNode node, SteamMode steamMode) {
        SteamMode oldMode = node.getSteamMode();
        SteamMode newMode = steamMode != null ? steamMode : SteamMode.NONE;
        node.getProperties().set(NodeProperties.STEAM_MODE, newMode);
        syncSteamInputSlot(node, oldMode, newMode);
        node.markOverclockDirty();
    }

    public static boolean supportsSteamMode(RecipeNode node) {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null && !adapter.isGenericFallback()) {
            return adapter.supportsSteamMode(node);
        }
        for (IModAdapter a : ModAdapterRegistry.getAllLoadedAdapters()) {
            if (!a.isGenericFallback() && a.supportsSteamMode(node)) {
                return true;
            }
        }
        return false;
    }

    public static void syncSteamInputSlot(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null && !adapter.isGenericFallback()) {
            adapter.onSteamModeChanged(node, oldMode, newMode);
        } else {
            for (IModAdapter a : ModAdapterRegistry.getAllLoadedAdapters()) {
                if (!a.isGenericFallback() && a.supportsSteamMode(node)) {
                    a.onSteamModeChanged(node, oldMode, newMode);
                    break;
                }
            }
        }
    }
}
