package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RecipeSearchDialog {
    private final BoardScreen parent;
    private final EditBox searchBox;

    public record SearchableRecipe(EmiRecipe recipe, String displayName, String outputSearchIndex, String inputSearchIndex, String fullSearchIndex) {}

    // Static global cache shared across the entire game session (loaded only once in background)
    private static final List<SearchableRecipe> GLOBAL_RECIPES = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean GLOBAL_CACHED = false;
    private static volatile boolean IS_CACHING = false;

    private final List<SearchableRecipe> filteredRecipes = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean visible = false;
    private boolean hasTargetSpawnPos = false;
    private double targetSpawnCanvasX = 0;
    private double targetSpawnCanvasY = 0;

    private static final int DIALOG_WIDTH = 360;
    private static final int DIALOG_HEIGHT = 280;
    private static final int ROW_HEIGHT = 32;

    public RecipeSearchDialog(BoardScreen parent) {
        this.parent = parent;
        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, 0, 0, DIALOG_WIDTH - 24, 16, Component.translatable("gui.gtcalcboard.search"));
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.searchBox.setHint(Component.translatable("gui.gtcalcboard.search"));

        // Trigger background pre-caching immediately
        ensureGlobalRecipesCachedAsync(null);
    }

    public void openAt(double canvasX, double canvasY) {
        this.hasTargetSpawnPos = true;
        this.targetSpawnCanvasX = canvasX;
        this.targetSpawnCanvasY = canvasY;
        setVisible(true);
    }

    public void open() {
        this.hasTargetSpawnPos = false;
        setVisible(true);
    }

    public static void cacheFromRecipes(Collection<EmiRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            List<SearchableRecipe> tempList = new ArrayList<>();
            populateList(recipes, tempList);
            synchronized (GLOBAL_RECIPES) {
                GLOBAL_RECIPES.clear();
                GLOBAL_RECIPES.addAll(tempList);
                GLOBAL_CACHED = true;
            }
        });
    }

    private static void populateList(Collection<EmiRecipe> recipes, List<SearchableRecipe> target) {
        for (EmiRecipe recipe : recipes) {
            if (recipe == null) continue;
            String displayName = extractDisplayName(recipe);
            StringBuilder outSb = new StringBuilder();
            StringBuilder inSb = new StringBuilder();
            StringBuilder fullSb = new StringBuilder();

            fullSb.append(displayName.toLowerCase()).append(" ");
            if (recipe.getId() != null) {
                fullSb.append(recipe.getId().toString().toLowerCase()).append(" ");
            }
            if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
                fullSb.append(recipe.getCategory().getId().toString().toLowerCase()).append(" ");
            }

            if (recipe.getOutputs() != null) {
                for (var out : recipe.getOutputs()) {
                    if (out != null && out.getEmiStacks() != null) {
                        for (var stack : out.getEmiStacks()) {
                            if (stack != null && stack.getName() != null) {
                                String name = stack.getName().getString().toLowerCase();
                                outSb.append(name).append(" ");
                                fullSb.append(name).append(" ");
                                if (stack.getId() != null) {
                                    outSb.append(stack.getId().getPath().toLowerCase()).append(" ");
                                    fullSb.append(stack.getId().getPath().toLowerCase()).append(" ");
                                }
                            }
                        }
                    }
                }
            }

            if (recipe.getInputs() != null) {
                for (var in : recipe.getInputs()) {
                    if (in != null && in.getEmiStacks() != null) {
                        for (var stack : in.getEmiStacks()) {
                            if (stack != null && stack.getName() != null) {
                                String name = stack.getName().getString().toLowerCase();
                                inSb.append(name).append(" ");
                                fullSb.append(name).append(" ");
                                if (stack.getId() != null) {
                                    inSb.append(stack.getId().getPath().toLowerCase()).append(" ");
                                    fullSb.append(stack.getId().getPath().toLowerCase()).append(" ");
                                }
                            }
                        }
                    }
                }
            }

            target.add(new SearchableRecipe(recipe, displayName, outSb.toString(), inSb.toString(), fullSb.toString()));
        }
    }

    public static void ensureGlobalRecipesCachedAsync(Runnable onComplete) {
        if (GLOBAL_CACHED && !GLOBAL_RECIPES.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (IS_CACHING) return;
        IS_CACHING = true;

        CompletableFuture.runAsync(() -> {
            try {
                var recipeManager = EmiApi.getRecipeManager();
                if (recipeManager != null) {
                    List<SearchableRecipe> tempList = new ArrayList<>();
                    List<EmiRecipe> recipes = recipeManager.getRecipes();
                    if (recipes != null && !recipes.isEmpty()) {
                        populateList(recipes, tempList);
                    } else {
                        for (EmiRecipeCategory cat : recipeManager.getCategories()) {
                            if (cat == null) continue;
                            List<EmiRecipe> catRecipes = recipeManager.getRecipes(cat);
                            if (catRecipes != null) {
                                populateList(catRecipes, tempList);
                            }
                        }
                    }

                    synchronized (GLOBAL_RECIPES) {
                        GLOBAL_RECIPES.clear();
                        GLOBAL_RECIPES.addAll(tempList);
                        GLOBAL_CACHED = true;
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                IS_CACHING = false;
                if (onComplete != null) {
                    Minecraft.getInstance().execute(onComplete);
                }
            }
        });
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            ensureGlobalRecipesCachedAsync(() -> {
                if (this.visible) {
                    updateSearchResults(searchBox.getValue());
                }
            });
            updateSearchResults("");
        }
    }

    private static String extractDisplayName(EmiRecipe recipe) {
        try {
            if (recipe.getOutputs() != null && !recipe.getOutputs().isEmpty()) {
                var firstOut = recipe.getOutputs().get(0);
                if (firstOut != null && firstOut.getEmiStacks() != null && !firstOut.getEmiStacks().isEmpty()) {
                    var st = firstOut.getEmiStacks().get(0);
                    if (st != null && st.getName() != null) {
                        return st.getName().getString();
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (recipe.getId() != null) {
            String path = recipe.getId().getPath();
            if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
            return EmiRecipeConverter.formatName(path);
        }
        return Component.translatable("gui.gtcalcboard.default_recipe_name").getString();
    }

    private void onSearchQueryChanged(String query) {
        scrollOffset = 0;
        updateSearchResults(query);
    }

    private void updateSearchResults(String query) {
        filteredRecipes.clear();
        String lower = query.toLowerCase().trim();

        synchronized (GLOBAL_RECIPES) {
            if (GLOBAL_RECIPES.isEmpty()) {
                try {
                    var rm = EmiApi.getRecipeManager();
                    if (rm != null) {
                        List<EmiRecipe> all = rm.getRecipes();
                        if (all != null && !all.isEmpty()) {
                            populateList(all, GLOBAL_RECIPES);
                            GLOBAL_CACHED = true;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (GLOBAL_RECIPES.isEmpty()) return;

            if (lower.isEmpty()) {
                int limit = Math.min(100, GLOBAL_RECIPES.size());
                for (int i = 0; i < limit; i++) {
                    filteredRecipes.add(GLOBAL_RECIPES.get(i));
                }
                return;
            }

            // Two-tier ranking:
            // 1. Output producers (recipes producing the item) -> Highest Priority
            // 2. Input consumers (recipes requiring the item) -> Secondary Priority
            List<SearchableRecipe> outputMatches = new ArrayList<>();
            List<SearchableRecipe> otherMatches = new ArrayList<>();

            for (SearchableRecipe sr : GLOBAL_RECIPES) {
                if (outputMatches.size() + otherMatches.size() >= 120) break;

                if (sr.outputSearchIndex.contains(lower) || sr.displayName.toLowerCase().contains(lower)) {
                    outputMatches.add(sr);
                } else if (sr.fullSearchIndex.contains(lower)) {
                    otherMatches.add(sr);
                }
            }

            filteredRecipes.addAll(outputMatches);
            filteredRecipes.addAll(otherMatches);
        }
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Font font = Minecraft.getInstance().font;
        int dialogW = Math.min(380, screenWidth - 24);
        int dialogH = Math.min(280, screenHeight - 24);
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Solid Dark Backdrop
        graphics.fill(0, 0, screenWidth, screenHeight, 0xCC000000);
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xFF1E222B);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4A90E2);

        // Header
        graphics.fill(x, y, x + dialogW, y + 24, 0xFF282E3B);
        graphics.drawString(font, "§6➕ " + Component.translatable("gui.gtcalcboard.add_recipe").getString(), x + 10, y + 8, 0xFFFFFFFF, false);

        // Close [X]
        int closeX = x + dialogW - 18;
        int closeY = y + 6;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX, closeY, closeHover ? 0xFFFF5555 : 0xFFAAAAAA, false);

        // Search Input Box
        searchBox.setX(x + 12);
        searchBox.setY(y + 30);
        searchBox.setWidth(dialogW - 24);
        searchBox.render(graphics, mouseX, mouseY, 0);

        // Recipe Results List Area
        int listX = x + 12;
        int listY = y + 52;
        int listW = dialogW - 24;
        int listH = dialogH - 60;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF14171E);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF3D4455);

        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (filteredRecipes.isEmpty()) {
            String emptyMsg = !GLOBAL_CACHED 
                ? "§e" + Component.translatable("gui.gtcalcboard.loading_recipes").getString() 
                : "§7" + Component.translatable("gui.gtcalcboard.no_matching_recipes").getString();
            graphics.drawCenteredString(font, emptyMsg, listX + listW / 2, listY + listH / 2 - 4, 0xFF888888);
        } else {
            for (int i = 0; i < visibleRows; i++) {
                int index = scrollOffset + i;
                if (index >= filteredRecipes.size()) break;

                SearchableRecipe sr = filteredRecipes.get(index);
                int rowY = listY + i * ROW_HEIGHT;

                boolean rowHover = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
                graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, rowHover ? 0xFF2A3649 : (i % 2 == 0 ? 0xFF1A1E26 : 0xFF161A21));

                // Left: Display First Input Stack Icon & Rate
                EmiRecipe r = sr.recipe;
                if (!r.getInputs().isEmpty() && !r.getInputs().get(0).getEmiStacks().isEmpty()) {
                    r.getInputs().get(0).getEmiStacks().get(0).render(graphics, listX + 6, rowY + 8, 0);
                }

                // Arrow
                graphics.drawString(font, "➔", listX + 28, rowY + 12, 0xFF657595, false);

                // Right: Output Stack Icon
                if (!r.getOutputs().isEmpty() && !r.getOutputs().get(0).getEmiStacks().isEmpty()) {
                    r.getOutputs().get(0).getEmiStacks().get(0).render(graphics, listX + 42, rowY + 8, 0);
                }

                // Recipe Name and Details
                String title = font.plainSubstrByWidth(sr.displayName, listW - 130);
                graphics.drawString(font, "§f" + title, listX + 66, rowY + 6, 0xFFFFFFFF, false);

                // Machine / Category / Tier info
                String catName = r.getCategory() != null ? r.getCategory().getId().getPath() : "recipe";
                graphics.drawString(font, "§8[" + catName + "] §7" + String.format("%.1fs", (double) r.getInputs().size()), listX + 66, rowY + 18, 0xFFAAAAAA, false);

                // Add button [➕]
                int btnW = 44;
                int btnH = 18;
                int btnX = listX + listW - btnW - 6;
                int btnY = rowY + 7;
                boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0xFF3A824A : 0xFF245030);
                graphics.renderOutline(btnX, btnY, btnW, btnH, btnHover ? 0xFF55FF88 : 0xFF357045);
                graphics.drawCenteredString(font, "§a+ " + Component.translatable("gui.gtcalcboard.add_btn").getString(), btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);
            }

            // Scrollbar indicator
            if (maxScroll > 0) {
                int scrollTrackH = listH - 4;
                int barH = Math.max(16, (int) ((double) visibleRows / filteredRecipes.size() * scrollTrackH));
                int barY = listY + 2 + (int) ((double) scrollOffset / maxScroll * (scrollTrackH - barH));
                int barX = listX + listW - 4;
                graphics.fill(barX, barY, barX + 3, barY + barH, 0xFF657595);
            }
        }

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int dialogW = Math.min(380, screenWidth - 24);
        int dialogH = Math.min(280, screenHeight - 24);
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Click outside closes dialog
        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            setVisible(false);
            return true;
        }

        // Close [X]
        int closeX = x + dialogW - 18;
        int closeY = y + 6;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            setVisible(false);
            return true;
        }

        searchBox.mouseClicked(mouseX, mouseY, button);

        // Click on Add buttons in list
        int listX = x + 12;
        int listY = y + 52;
        int listW = dialogW - 24;
        int listH = dialogH - 60;
        int visibleRows = Math.max(1, listH / ROW_HEIGHT);

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollOffset + i;
            if (index >= filteredRecipes.size()) break;

            int rowY = listY + i * ROW_HEIGHT;
            int btnW = 44;
            int btnH = 18;
            int btnX = listX + listW - btnW - 6;
            int btnY = rowY + 7;

            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                SearchableRecipe sr = filteredRecipes.get(index);
                RecipeNode node = EmiRecipeConverter.convert(sr.recipe);
                if (node != null) {
                    if (hasTargetSpawnPos) {
                        node.setPosX(targetSpawnCanvasX - 80);
                        node.setPosY(targetSpawnCanvasY - 30);
                    } else {
                        double[] center = BoardScreen.getNextNodeCenterPosition(screenWidth, screenHeight);
                        node.setPosX(center[0]);
                        node.setPosY(center[1]);
                    }
                    parent.addNode(node);
                    setVisible(false);
                }
                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        int dialogH = Math.min(280, parent.height - 24);
        int listH = dialogH - 60;
        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta) * 2));
            return true;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        return searchBox.charTyped(codePoint, modifiers);
    }
}
