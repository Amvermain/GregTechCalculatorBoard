package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.GregTechCalcBoard;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Event handler that seamlessly integrates the EMI RecipeScreen [+] (fill button)
 * directly with GregTech Calculator Board without requiring BoardScreen to be an AbstractContainerScreen.
 * This completely avoids unwanted mod sidebar menus (FTB Teams, JourneyMap, Curios, etc.) while allowing [+] click to add recipes to board.
 */
@Mod.EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CalcBoardEmiOverlayHandler {

    private static Field currentPageField = null;
    private static Field canFillField = null;
    private static Field tooltipField = null;

    static {
        try {
            currentPageField = RecipeScreen.class.getDeclaredField("currentPage");
            currentPageField.setAccessible(true);
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.error("[GTCalcBoard] Failed to reflect RecipeScreen.currentPage", t);
        }

        try {
            canFillField = RecipeFillButtonWidget.class.getDeclaredField("canFill");
            canFillField.setAccessible(true);
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.error("[GTCalcBoard] Failed to reflect RecipeFillButtonWidget.canFill", t);
        }

        try {
            tooltipField = RecipeFillButtonWidget.class.getDeclaredField("tooltip");
            tooltipField.setAccessible(true);
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.error("[GTCalcBoard] Failed to reflect RecipeFillButtonWidget.tooltip", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<WidgetGroup> getCurrentPage(RecipeScreen screen) {
        if (currentPageField == null) return List.of();
        try {
            return (List<WidgetGroup>) currentPageField.get(screen);
        } catch (Throwable t) {
            return List.of();
        }
    }

    /**
     * Every frame before rendering, dynamically enable and customize EMI's RecipeFillButtonWidget ([+])
     * so that it renders as an active, enabled button with a clean "Add to Calculator Board" tooltip.
     */
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof RecipeScreen recipeScreen) {
            List<WidgetGroup> groups = getCurrentPage(recipeScreen);
            for (WidgetGroup group : groups) {
                for (Widget w : group.widgets) {
                    if (w instanceof RecipeFillButtonWidget fillBtn) {
                        try {
                            if (canFillField != null) {
                                canFillField.setBoolean(fillBtn, true);
                            }
                            if (tooltipField != null) {
                                String label = Component.translatable("gui.gtcalcboard.add_to_board").getString();
                                tooltipField.set(fillBtn, List.of(
                                    ClientTooltipComponent.create(Component.literal("§a➕ " + label).getVisualOrderText())
                                ));
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    /**
     * Intercept mouse clicks on EMI RecipeScreen [+] buttons.
     */
    @SubscribeEvent
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return; // Left click only
        if (event.getScreen() instanceof RecipeScreen recipeScreen) {
            List<WidgetGroup> groups = getCurrentPage(recipeScreen);
            double mx = event.getMouseX();
            double my = event.getMouseY();

            for (WidgetGroup group : groups) {
                int relX = (int) (mx - group.x);
                int relY = (int) (my - group.y);

                for (Widget w : group.widgets) {
                    if (w instanceof RecipeFillButtonWidget) {
                        if (w.getBounds().contains(relX, relY)) {
                            // Intercept [+] fill button click and route directly to Calculator Board!
                            EmiRecipe recipe = group.recipe;
                            if (recipe != null) {
                                CalcBoardEmiPlugin.addRecipeToBoard(recipe, true);
                                Minecraft.getInstance().getSoundManager().play(
                                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                                );
                                event.setCanceled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Support custom keybinding on EMI RecipeScreen to quickly add the hovered recipe to Calculator Board.
     */
    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!com.gtceu.calcboard.client.key.KeyBindings.ADD_RECIPE.isUnbound()
                && com.gtceu.calcboard.client.key.KeyBindings.ADD_RECIPE.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            if (event.getScreen() instanceof RecipeScreen recipeScreen) {
                if (recipeScreen.getFocused() != null && recipeScreen.getFocused().isFocused()) return;

                List<WidgetGroup> groups = getCurrentPage(recipeScreen);
                Minecraft mc = Minecraft.getInstance();
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

                for (WidgetGroup group : groups) {
                    if (mx >= group.x && mx <= group.x + group.width && my >= group.y && my <= group.y + group.height) {
                        if (group.recipe != null) {
                            CalcBoardEmiPlugin.addRecipeToBoard(group.recipe, true);
                            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }
        }
    }
}

