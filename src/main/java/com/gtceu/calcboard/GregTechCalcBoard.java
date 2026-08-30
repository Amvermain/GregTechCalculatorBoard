package com.gtceu.calcboard;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(GregTechCalcBoard.MOD_ID)
public class GregTechCalcBoard {
    public static final String MOD_ID = "gtcalcboard";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public GregTechCalcBoard(IEventBus modBus, ModContainer container) {
        com.gtceu.calcboard.integration.emi.ModMenus.MENUS.register(modBus);
        modBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::init);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WorkspaceLockManager.getInstance().clearAll();
    }
}
