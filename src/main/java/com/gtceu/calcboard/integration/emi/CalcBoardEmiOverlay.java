package com.gtceu.calcboard.integration.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.List;

public class CalcBoardEmiOverlay {

    private static boolean isRecipeScreen(Screen screen) {
        return screen != null && screen.getClass().getName().contains("RecipeScreen");
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static record RecipeButtonTarget(EmiRecipe recipe, int x, int y, int width, int height) {}

    private static RecipeButtonTarget getTargetForGroup(Object group, int screenX, int screenY, int bgWidth) {
        try {
            Field rF = findField(group.getClass(), "recipe");
            Field grpYF = findField(group.getClass(), "y");
            if (rF == null || grpYF == null) return null;

            Object recipeObj = rF.get(group);
            if (!(recipeObj instanceof EmiRecipe recipe)) return null;

            int grpY = grpYF.getInt(group);

            // Align button exactly above the heart/tree button column
            // In EMI RecipeScreen, button column is located at screenX + bgWidth + 2
            int btnX = screenX + bgWidth + 2;
            int btnY = screenY + grpY - 2; // Right above the first button in the column

            return new RecipeButtonTarget(recipe, btnX, btnY, 12, 12);
        } catch (Throwable t) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (isRecipeScreen(screen)) {
            GuiGraphics graphics = event.getGuiGraphics();
            Font font = Minecraft.getInstance().font;
            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();

            try {
                Class<?> screenClass = screen.getClass();
                Field xF = findField(screenClass, "x");
                Field yF = findField(screenClass, "y");
                Field bgWF = findField(screenClass, "backgroundWidth");
                Field curPageF = findField(screenClass, "currentPage");

                if (xF == null || yF == null || bgWF == null || curPageF == null) return;

                int screenX = xF.getInt(screen);
                int screenY = yF.getInt(screen);
                int bgWidth = bgWF.getInt(screen);

                List<?> groups = (List<?>) curPageF.get(screen);
                if (groups == null || groups.isEmpty()) return;

                for (Object group : groups) {
                    RecipeButtonTarget target = getTargetForGroup(group, screenX, screenY, bgWidth);
                    if (target == null) continue;

                    int btnX = target.x;
                    int btnY = target.y;
                    int btnW = target.width;
                    int btnH = target.height;

                    boolean hover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                    // Draw button matched to EMI dark theme style
                    graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? 0xFF3E5A8A : 0xEE1E2430);
                    graphics.renderOutline(btnX, btnY, btnW, btnH, hover ? 0xFF55AAFF : 0xFF3D4B66);
                    graphics.drawCenteredString(font, "⚡", btnX + btnW / 2, btnY + 2, hover ? 0xFFFFFF55 : 0xFFAAAAAA);

                    if (hover) {
                        graphics.renderTooltip(font, List.of(
                            Component.literal("§6⚡ Add to Calculator Board"),
                            Component.literal("§7Click to add this recipe to flowsheet"),
                            Component.literal("§8Shortcut: 'A' key")
                        ), java.util.Optional.empty(), mouseX, mouseY);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (isRecipeScreen(screen) && event.getButton() == 0) {
            int mouseX = (int) event.getMouseX();
            int mouseY = (int) event.getMouseY();

            try {
                Class<?> screenClass = screen.getClass();
                Field xF = findField(screenClass, "x");
                Field yF = findField(screenClass, "y");
                Field bgWF = findField(screenClass, "backgroundWidth");
                Field curPageF = findField(screenClass, "currentPage");

                if (xF == null || yF == null || bgWF == null || curPageF == null) return;

                int screenX = xF.getInt(screen);
                int screenY = yF.getInt(screen);
                int bgWidth = bgWF.getInt(screen);

                List<?> groups = (List<?>) curPageF.get(screen);
                if (groups == null) return;

                for (Object group : groups) {
                    RecipeButtonTarget target = getTargetForGroup(group, screenX, screenY, bgWidth);
                    if (target == null) continue;

                    if (mouseX >= target.x && mouseX <= target.x + target.width && mouseY >= target.y && mouseY <= target.y + target.height) {
                        CalcBoardEmiPlugin.addRecipeToBoard(target.recipe, true);
                        event.setCanceled(true);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}
