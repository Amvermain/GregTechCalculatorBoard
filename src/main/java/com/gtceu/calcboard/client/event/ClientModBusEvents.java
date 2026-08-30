package com.gtceu.calcboard.client.event;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.client.key.KeyBindings;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModBusEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_BOARD);
        event.register(KeyBindings.ADD_RECIPE_TO_BOARD);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            MachineAddonCatalog.getInstance().markDirty();
            if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
                com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog.clearGlobalCache();
            }
        });
    }
}



