package com.gtceu.calcboard.client.event;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.MachineAddonCatalog;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.RecipeSearchDialog;
import com.gtceu.calcboard.client.key.KeyBindings;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.integration.emi.CalcBoardEmiPlugin;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] Client logged in to world/server. Reloading board data and preloading addon catalog...");
        // Load personal boards scoped to this world / server
        com.gtceu.calcboard.api.BoardManager.getInstance().reloadForCurrentContext();

        // Preload addon catalog & recipe search index in background immediately upon logging in!
        MachineAddonCatalog.getInstance().preloadAsync();
        RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);

        // Preload team workspace in background
        com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket(null, "page_main"));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] Client logged out. Saving local context and resetting caches.");
        com.gtceu.calcboard.api.BoardManager.getInstance().saveForCurrentContext();
        com.gtceu.calcboard.api.BoardManager.getInstance().resetToDefault();
        ClientWorkspaceState.getInstance().clear();
        MachineAddonCatalog.getInstance().reset();
        RecipeSearchDialog.clearGlobalCache();
        com.gtceu.calcboard.api.CategoryCapabilityMatrix.getInstance().reset();
        System.gc();
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] RecipesUpdatedEvent received. Refreshing addon catalog and recipe search index...");
        MachineAddonCatalog.getInstance().markDirty();
        RecipeSearchDialog.clearGlobalCache();
        MachineAddonCatalog.getInstance().preloadAsync();
        RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof BoardScreen) {
            RecipeSearchDialog.notifyFavoritesChanged();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Background pre-cache trigger once EMI and registries are ready
            if (!RecipeSearchDialog.isGlobalCached() && !RecipeSearchDialog.isCaching()) {
                RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
            }

            while (KeyBindings.OPEN_BOARD.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    mc.setScreen(new BoardScreen());
                }
            }
        }
    }
}
