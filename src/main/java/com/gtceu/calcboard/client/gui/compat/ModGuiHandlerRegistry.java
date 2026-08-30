package com.gtceu.calcboard.client.gui.compat;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.compat.create.CreateModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.createnewage.CreateNewAgeModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.gtceu.GTCEuModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.systeams.SysteamsModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.thermal.ThermalModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.vanilla.VanillaModGuiHandler;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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

        var gtHandler = HANDLERS.get("gtceu");
        if (gtHandler != null) {
            HANDLERS.put("start_core", gtHandler);
            HANDLERS.put("gtceu_start", gtHandler);
            HANDLERS.put("start", gtHandler);
            HANDLERS.put("star_technology", gtHandler);
        }
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
            if (adapter instanceof com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter) {
                return HANDLERS.getOrDefault("gtceu", FALLBACK_HANDLER);
            }
        }

        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace();
            IModGuiHandler handler = HANDLERS.get(ns);
            if (handler != null) return handler;
            if (ns.equals("start_core") || ns.equals("gtceu_start") || ns.equals("start") || ns.equals("star_technology")) {
                return HANDLERS.getOrDefault("gtceu", FALLBACK_HANDLER);
            }
        }

        return FALLBACK_HANDLER;
    }
}
