package com.gtceu.calcboard.client.event;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardHotkeyHandler;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.event.CatalogLifecycleEvent;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog;
import com.gtceu.calcboard.client.key.KeyBindings;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket;
import net.minecraft.client.Minecraft;
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
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] Client logged in to world/server. Reloading board data and preloading catalogs...");
        // Load personal boards scoped to this world / server
        BoardManager.getInstance().reloadForCurrentContext();

        // Fast load registry-backed addons immediately (<5ms, zero memory overhead)
        MachineAddonCatalog.getInstance().ensureFastLoaded();

        // Eagerly pre-index multiblock capabilities and 3D structures in the background so board opens instantly
        com.gtceu.calcboard.api.catalog.MultiblockDetector.initializeAsync();
        MultiblockStructureCatalog.initializeAsync();

        // Eagerly pre-index recipes as soon as recipe viewer is ready
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            com.gtceu.calcboard.integration.emi.EmiLifecycleHook.runWhenEmiReady(() -> {
                RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
            });
        } else {
            RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof BoardScreen) {
            mc.setScreen(null);
        }
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] Client logged out. Saving local context and resetting caches.");
        try { BoardManager.getInstance().saveForCurrentContext(); } catch (Throwable t) { GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Error saving board on logout: {}", t.getMessage()); }
        try { BoardManager.getInstance().resetToDefault(); } catch (Throwable ignored) {}
        try { ClientWorkspaceState.getInstance().clear(); } catch (Throwable ignored) {}
        try { MachineAddonCatalog.getInstance().reset(); } catch (Throwable ignored) {}
        try { RecipeSearchDialog.clearGlobalCache(); } catch (Throwable ignored) {}
        try { CategoryCapabilityMatrix.getInstance().reset(); } catch (Throwable ignored) {}
        try { MultiblockStructureCatalog.clear(); } catch (Throwable ignored) {}
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            try { com.gtceu.calcboard.integration.emi.EmiLifecycleHook.reset(); } catch (Throwable ignored) {}
        }
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] RecipesUpdatedEvent received. Refreshing pre-indexes in background...");
        try { MachineAddonCatalog.getInstance().markDirty(); } catch (Throwable ignored) {}
        try { RecipeSearchDialog.clearGlobalCache(); } catch (Throwable ignored) {}
        try { MultiblockStructureCatalog.clear(); } catch (Throwable ignored) {}
        com.gtceu.calcboard.api.catalog.MultiblockDetector.initializeAsync();
        MultiblockStructureCatalog.initializeAsync();
        MachineAddonCatalog.getInstance().ensureFastLoadedAsync();
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            com.gtceu.calcboard.integration.emi.EmiLifecycleHook.runWhenEmiReady(() -> {
                RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
            });
        } else {
            RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
        }
    }

    @SubscribeEvent
    public static void onRecipesReady(CatalogLifecycleEvent.RecipesReady event) {
        GregTechCalcBoard.LOGGER.info(
            "[GTCalcBoard] [Lifecycle] RecipesReady event received ({} recipes in {}ms). Triggering multiblock & addon deep scan...",
            event.getRecipeCount(), event.getElapsedMs()
        );
        com.gtceu.calcboard.api.catalog.MultiblockDetector.initializeAsync();
        MultiblockStructureCatalog.initializeAsync();
        MachineAddonCatalog.getInstance().ensureFastLoadedAsync();
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof BoardScreen) {
            RecipeSearchDialog.notifyFavoritesChanged();
            RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null || mc.player == null || mc.level == null) return;

        net.minecraft.client.gui.screens.Screen screen = event.getScreen();
        // Do not intercept if in pause, options, controls/keybinds, death, or configuration screens
        if (screen instanceof net.minecraft.client.gui.screens.PauseScreen
                || screen instanceof net.minecraft.client.gui.screens.OptionsScreen
                || screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen
                || screen instanceof net.minecraft.client.gui.screens.DeathScreen
                || screen.getClass().getName().toLowerCase().contains("config")) {
            return;
        }

        // Do not intercept if player is actively typing inside a text box or external recipe viewer search bar
        if (isScreenTypingActive(screen) || com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.isAnySearchFocused()) {
            return;
        }

        // If player is already on BoardScreen, let BoardScreen / BoardHotkeyHandler handle keys
        if (mc.screen instanceof BoardScreen) {
            return;
        }

        // Allow adding hovered recipe directly from external recipe viewers (EMI, JEI) via hotkey (Shift + A)
        boolean matchesAddRecipe = KeyBindings.ADD_RECIPE_TO_BOARD.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyCode(), event.getScanCode()))
                || (net.minecraft.client.gui.screens.Screen.hasShiftDown() && event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_A);
        if (matchesAddRecipe) {
            double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) Math.max(1, mc.getWindow().getScreenWidth());
            double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) Math.max(1, mc.getWindow().getScreenHeight());
            if (com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.tryAddHoveredRecipeToBoard(screen, mouseX, mouseY)) {
                event.setCanceled(true);
                return;
            }
        }

        // Allow opening Calculator Board directly from inventory, chests, crafting tables, etc.
        if (KeyBindings.OPEN_BOARD.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            mc.setScreen(new BoardScreen());
            event.setCanceled(true);
        }
    }

    private static boolean isScreenTypingActive(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null) return false;
        if (isWidgetFocusedAndTyping(screen.getFocused())) {
            return true;
        }
        for (var child : screen.children()) {
            if (isWidgetFocusedAndTyping(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWidgetFocusedAndTyping(net.minecraft.client.gui.components.events.GuiEventListener listener) {
        if (listener == null) return false;
        if (listener instanceof net.minecraft.client.gui.components.EditBox editBox) {
            return editBox.canConsumeInput();
        }
        if (listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
            if (isWidgetFocusedAndTyping(container.getFocused())) {
                return true;
            }
            for (var nestedChild : container.children()) {
                if (nestedChild != container && nestedChild.isFocused() && isWidgetFocusedAndTyping(nestedChild)) {
                    return true;
                }
            }
        }
        String className = listener.getClass().getName().toLowerCase();
        return (className.contains("editbox") || className.contains("textfield") || className.contains("search") || className.contains("filter"))
                && listener.isFocused();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) {
                while (KeyBindings.OPEN_BOARD.consumeClick()) {
                    // Drain clicks while outside world
                }
                return;
            }
            while (KeyBindings.OPEN_BOARD.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new BoardScreen());
                }
            }
        }
    }
}



