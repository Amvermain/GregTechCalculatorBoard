package com.gtceu.calcboard;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.key.KeyBindings;
import com.gtceu.calcboard.integration.emi.CalcBoardEmiPlugin;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GregTechCalcBoard.MOD_ID)
public class GregTechCalcBoard {
    public static final String MOD_ID = "gtcalcboard";

    public GregTechCalcBoard(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        modBus.addListener(this::clientSetup);
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerReloadListeners);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(com.gtceu.calcboard.integration.emi.CalcBoardEmiOverlay.class);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // Client initialization
    }

    private void registerReloadListeners(final net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resourceManager -> {
            com.gtceu.calcboard.api.MachineAddonCatalog.getInstance().markDirty();
        });
    }

    private void registerKeys(final RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_BOARD);
        event.register(KeyBindings.ADD_RECIPE);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        // Preload addon catalog & recipe search index in background immediately upon logging in!
        com.gtceu.calcboard.api.MachineAddonCatalog.getInstance().preloadAsync();
        com.gtceu.calcboard.client.gui.RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
    }

    @SubscribeEvent
    public void onRecipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent event) {
        com.gtceu.calcboard.api.MachineAddonCatalog.getInstance().markDirty();
        com.gtceu.calcboard.api.MachineAddonCatalog.getInstance().preloadAsync();
        com.gtceu.calcboard.client.gui.RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (KeyBindings.OPEN_BOARD.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    mc.setScreen(new BoardScreen());
                }
            }
        }
    }

    @SubscribeEvent
    public void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        net.minecraft.client.gui.screens.Screen screen = event.getScreen();
        if (screen == null) return;

        // 1. Never capture keys in system, options, keybinds, controls, pause, or chat screens
        if (screen instanceof net.minecraft.client.gui.screens.OptionsScreen
                || screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen
                || screen instanceof net.minecraft.client.gui.screens.controls.ControlsScreen
                || screen instanceof net.minecraft.client.gui.screens.ChatScreen
                || screen instanceof net.minecraft.client.gui.screens.PauseScreen
                || screen instanceof net.minecraft.client.gui.screens.TitleScreen
                || screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
            return;
        }

        String screenClass = screen.getClass().getName();
        if (screenClass.contains("KeyBinds") || screenClass.contains("Controls") || screenClass.contains("options")) {
            return;
        }

        // 2. Never capture keys if any EditBox or text input is currently focused
        var focused = screen.getFocused();
        if (focused instanceof net.minecraft.client.gui.components.EditBox) {
            return;
        }
        if (focused != null) {
            String focusedClass = focused.getClass().getName();
            if (focusedClass.contains("EditBox") || focusedClass.contains("TextField") || focusedClass.contains("Search")) {
                return;
            }
        }

        // 3. Process ADD_RECIPE only when bound and hovering over a recipe in recipe/inventory screens
        if (!KeyBindings.ADD_RECIPE.isUnbound() && KeyBindings.ADD_RECIPE.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            try {
                // If cursor is hovering over an EMI stack / recipe context
                var interaction = EmiApi.getHoveredStack(true);
                EmiRecipe targetRecipe = null;

                if (interaction != null && !interaction.isEmpty()) {
                    targetRecipe = interaction.getRecipeContext();
                    if (targetRecipe == null && interaction.getStack() != null) {
                        targetRecipe = EmiApi.getRecipeContext(interaction.getStack());
                        if (targetRecipe == null) {
                            var recipes = EmiApi.getRecipeManager().getRecipesByOutput(interaction.getStack().getEmiStacks().get(0));
                            if (recipes != null && !recipes.isEmpty()) {
                                targetRecipe = recipes.get(0);
                            }
                        }
                    }
                }

                if (targetRecipe != null) {
                    CalcBoardEmiPlugin.addRecipeToBoard(targetRecipe, true);
                    event.setCanceled(true);
                }
            } catch (Throwable ignored) {}
        }
    }
}

