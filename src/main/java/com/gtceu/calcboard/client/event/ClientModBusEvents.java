package com.gtceu.calcboard.client.event;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.MachineAddonCatalog;
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
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_BOARD);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            MachineAddonCatalog.getInstance().markDirty();
        });
    }
}
