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
        // Preload addon catalog & recipe search index in background immediately upon logging in!
        MachineAddonCatalog.getInstance().preloadAsync();
        RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWorkspaceState.getInstance().clear();
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        MachineAddonCatalog.getInstance().markDirty();
        MachineAddonCatalog.getInstance().preloadAsync();
        RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
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
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        // 1. Never capture keys in system, options, keybinds, controls, pause, or chat screens
        if (screen instanceof OptionsScreen
                || screen instanceof KeyBindsScreen
                || screen instanceof ControlsScreen
                || screen instanceof ChatScreen
                || screen instanceof PauseScreen
                || screen instanceof TitleScreen
                || screen instanceof DeathScreen) {
            return;
        }

        String screenClass = screen.getClass().getName();
        if (screenClass.contains("KeyBinds") || screenClass.contains("Controls") || screenClass.contains("options")) {
            return;
        }

        // 2. Never capture keys if any EditBox or text input is currently focused
        var focused = screen.getFocused();
        if (focused instanceof EditBox) {
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
