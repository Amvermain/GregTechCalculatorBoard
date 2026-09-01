package com.gtceu.calcboard.client.event;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.client.key.KeyBindings;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModBusEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            com.gtceu.calcboard.api.storage.BoardManager.setCustomSaveDirectoryProvider(com.gtceu.calcboard.client.ClientSaveHelper::getClientSaveFile);
            com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.setLevelRecipeProvider(com.gtceu.calcboard.client.ClientLevelHelper.INSTANCE);
        });
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



