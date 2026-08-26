package com.gtceu.calcboard;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GregTechCalcBoard.MOD_ID)
public class GregTechCalcBoard {
    public static final String MOD_ID = "gtcalcboard";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public GregTechCalcBoard(FMLJavaModLoadingContext context) {
        // Register DisplayTest to make the mod completely optional on both client and server sides
        context.registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                        (remoteVersion, isServer) -> true
                )
        );

        var modBus = context.getModEventBus();
        com.gtceu.calcboard.integration.emi.ModMenus.MENUS.register(modBus);
        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::init);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WorkspaceLockManager.getInstance().clearAll();
    }
}
