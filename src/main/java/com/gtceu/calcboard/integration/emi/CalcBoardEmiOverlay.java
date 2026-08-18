package com.gtceu.calcboard.integration.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
            if (rF == null) return null;

            Object recipeObj = rF.get(group);
            if (!(recipeObj instanceof EmiRecipe recipe)) return null;

            Minecraft mc = Minecraft.getInstance();
            int screenGuiW = mc.getWindow().getGuiScaledWidth();

            int btnX = screenX + bgWidth + 1;
            if (btnX + 13 > screenGuiW) {
                // If hugging screen edge in low resolution, dock inside the right border
                btnX = screenX + bgWidth - 13;
            }

            Integer lastButtonBottom = null;

            // Search for EMI's existing side buttons (Favorite, Tree, Fill '+') to dock seamlessly below them
            for (Field f : group.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(group);
                if (val != null) {
                    Field wYF = findField(val.getClass(), "y");
                    Field wHF = findField(val.getClass(), "height");
                    Field wXF = findField(val.getClass(), "x");
                    if (wYF != null && wHF != null) {
                        try {
                            int wy = wYF.getInt(val);
                            int wh = wHF.getInt(val);
                            int wx = wXF != null ? wXF.getInt(val) : 0;

                            // Check if this widget is located in the right-side button column
                            if (wXF == null || Math.abs(wx - (screenX + bgWidth)) <= 12 || Math.abs(wx - bgWidth) <= 12 || wx == 0) {
                                int actualY = wy >= screenY ? wy : (screenY + wy);
                                if (lastButtonBottom == null || actualY + wh > lastButtonBottom) {
                                    lastButtonBottom = actualY + wh;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            int btnY;
            if (lastButtonBottom != null && lastButtonBottom > screenY) {
                // Dock directly below the last EMI button ('+' button) with 1px gap
                btnY = lastButtonBottom + 1;
            } else if (grpYF != null) {
                int grpY = grpYF.getInt(group);
                btnY = (grpY >= screenY ? grpY : screenY + grpY) + 39;
            } else {
                btnY = screenY + 41;
            }

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
                            Component.literal("§6⚡ ").append(Component.translatable("gui.gtcalcboard.emi.btn_title")),
                            Component.literal("§7").append(Component.translatable("gui.gtcalcboard.emi.btn_desc")),
                            Component.literal("§8").append(Component.translatable("gui.gtcalcboard.emi.btn_shortcut"))
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
