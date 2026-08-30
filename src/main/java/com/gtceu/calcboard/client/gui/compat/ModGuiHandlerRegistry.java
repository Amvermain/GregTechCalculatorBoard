package com.gtceu.calcboard.client.gui.compat;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.compat.create.CreateModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.createnewage.CreateNewAgeModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.gtceu.GTCEuModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.systeams.SysteamsModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.thermal.ThermalModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.vanilla.VanillaModGuiHandler;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for client-side {@link IModGuiHandler} instances.
 */
@OnlyIn(Dist.CLIENT)
public final class ModGuiHandlerRegistry {

    private static final Map<String, IModGuiHandler> HANDLERS = new HashMap<>();
    private static final IModGuiHandler FALLBACK_HANDLER = new GenericModGuiHandler();

    static {
        register(new GTCEuModGuiHandler());
        register(new CreateModGuiHandler());
        register(new CreateNewAgeModGuiHandler());
        register(new SysteamsModGuiHandler());
        register(new ThermalModGuiHandler());
        register(new VanillaModGuiHandler());
    }

    private ModGuiHandlerRegistry() {}

    public static void register(IModGuiHandler handler) {
        if (handler != null && handler.getModId() != null) {
            HANDLERS.put(handler.getModId(), handler);
        }
    }

    public static IModGuiHandler getHandler(String modId) {
        if (modId == null) return FALLBACK_HANDLER;
        return HANDLERS.getOrDefault(modId, FALLBACK_HANDLER);
    }

    public static IModGuiHandler getHandlerForNode(RecipeNode node) {
        if (node == null) return FALLBACK_HANDLER;

        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            IModGuiHandler handler = HANDLERS.get(adapter.getModId());
            if (handler != null) return handler;
        }

        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace();
            IModGuiHandler handler = HANDLERS.get(ns);
            if (handler != null) return handler;
        }

        return FALLBACK_HANDLER;
    }
}
