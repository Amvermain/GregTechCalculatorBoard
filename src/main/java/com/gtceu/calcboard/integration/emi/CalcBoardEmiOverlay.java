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
            Field grpXF = findField(group.getClass(), "x");
            Field grpYF = findField(group.getClass(), "y");
            Field grpWF = findField(group.getClass(), "width");
            Field grpHF = findField(group.getClass(), "height");
            Field widgetsF = findField(group.getClass(), "widgets");
            if (rF == null || grpYF == null) return null;

            Object recipeObj = rF.get(group);
            if (!(recipeObj instanceof EmiRecipe recipe)) return null;

            int grpX = grpXF != null ? grpXF.getInt(group) : screenX;
            int grpY = grpYF.getInt(group);
            int grpW = grpWF != null ? grpWF.getInt(group) : bgWidth;
            int grpH = grpHF != null ? grpHF.getInt(group) : 32;

            Minecraft mc = Minecraft.getInstance();
            int screenGuiW = mc.getWindow().getGuiScaledWidth();

            int baseX = grpW + 5;
            int baseY = 0;

            // Track occupied grid cells (col, row) in EMI's side button area
            boolean[][] occupied = new boolean[10][10];

            if (widgetsF != null) {
                Object widgetsObj = widgetsF.get(group);
                if (widgetsObj instanceof List<?> widgetsList) {
                    // First pass: find base X and base Y of EMI side buttons
                    Integer minX = null;
                    Integer minY = null;
                    for (Object w : widgetsList) {
                        if (w instanceof dev.emi.emi.api.widget.Widget widget) {
                            dev.emi.emi.api.widget.Bounds b = widget.getBounds();
                            if (b != null && b.x() >= grpW - 4 && b.width() <= 16 && b.height() <= 16) {
                                if (minX == null || b.x() < minX) minX = b.x();
                                if (minY == null || b.y() < minY) minY = b.y();
                            }
                        }
                    }

                    if (minX != null) baseX = minX;
                    if (minY != null) baseY = minY;

                    // Second pass: mark occupied grid cells
                    for (Object w : widgetsList) {
                        if (w instanceof dev.emi.emi.api.widget.Widget widget) {
                            dev.emi.emi.api.widget.Bounds b = widget.getBounds();
                            if (b != null && b.x() >= grpW - 4 && b.width() <= 16 && b.height() <= 16) {
                                int col = Math.max(0, (int) Math.round((b.x() - baseX) / 14.0));
                                int row = Math.max(0, (int) Math.round((b.y() - baseY) / 14.0));
                                if (col < 10 && row < 10) {
                                    occupied[col][row] = true;
                                }
                            }
                        }
                    }
                }
            }

            int maxRows = Math.max(1, (grpH - baseY + 2) / 14);

            int chosenCol = 0;
            int chosenRow = 0;
            boolean found = false;

            // Search for first free slot in column-major order (fill col 0 first, then col 1, etc.)
            for (int c = 0; c < 10 && !found; c++) {
                for (int r = 0; r < maxRows; r++) {
                    if (!occupied[c][r]) {
                        chosenCol = c;
                        chosenRow = r;
                        found = true;
                        break;
                    }
                }
            }

            int btnRelX = baseX + chosenCol * 14;
            int btnRelY = baseY + chosenRow * 14;

            int btnX = grpX + btnRelX;
            if (btnX + 13 > screenGuiW) {
                btnX = grpX + grpW - 13 - chosenCol * 14;
            }

            int btnY = grpY + btnRelY;

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

                    // Draw button matched to EMI dark theme style & 3D bevel
                    graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? 0xFF3D4654 : 0xFF2A2E38);

                    // Top & Left highlight (EMI style 3D border)
                    graphics.fill(btnX, btnY, btnX + btnW, btnY + 1, hover ? 0xFF657595 : 0xFF424B5D);
                    graphics.fill(btnX, btnY, btnX + 1, btnY + btnH, hover ? 0xFF657595 : 0xFF424B5D);

                    // Bottom & Right shadow
                    graphics.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, hover ? 0xFF1E232B : 0xFF14171E);
                    graphics.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, hover ? 0xFF1E232B : 0xFF14171E);

                    // Centered Lightning Icon
                    int textW = font.width("⚡");
                    int tx = btnX + (btnW - textW) / 2;
                    int ty = btnY + (btnH - 8) / 2;
                    graphics.drawString(font, "⚡", tx, ty, hover ? 0xFFFFFF55 : 0xFFB0B8C8, false);

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
