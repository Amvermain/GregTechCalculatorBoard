package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.FlowGraphSolver;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RecipeSearchDialog {
    private final BoardScreen parent;
    private final EditBox searchBox;

    public static class ContextualWireTarget {
        public final RecipeNode sourceNode;
        public final int sourcePortIdx;
        public final boolean sourceIsInput;
        public final IngredientStack sourceStack;
        public final double canvasX;
        public final double canvasY;
        public final boolean shiftAutoRatio;

        public ContextualWireTarget(RecipeNode sourceNode, int sourcePortIdx, boolean sourceIsInput, IngredientStack sourceStack, double canvasX, double canvasY, boolean shiftAutoRatio) {
            this.sourceNode = sourceNode;
            this.sourcePortIdx = sourcePortIdx;
            this.sourceIsInput = sourceIsInput;
            this.sourceStack = sourceStack;
            this.canvasX = canvasX;
            this.canvasY = canvasY;
            this.shiftAutoRatio = shiftAutoRatio;
        }
    }

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
    private ContextualWireTarget contextualWireTarget = null;

    private static final int DIALOG_WIDTH = 360;
    private static final int DIALOG_HEIGHT = 280;
    private static final int ROW_HEIGHT = 32;

    public RecipeSearchDialog(BoardScreen parent) {
        this.parent = parent;
        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, 0, 0, DIALOG_WIDTH - 24, 16, Component.translatable("gui.gtcalcboard.search"));
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.searchBox.setHint(Component.translatable("gui.gtcalcboard.search.search_help"));

        // Trigger background pre-caching immediately
        ensureGlobalRecipesCachedAsync(null);
    }

    public void openForContextualWire(RecipeNode sourceNode, int sourcePortIdx, boolean sourceIsInput, IngredientStack sourceStack, double canvasX, double canvasY, boolean shiftAutoRatio) {
        this.contextualWireTarget = new ContextualWireTarget(sourceNode, sourcePortIdx, sourceIsInput, sourceStack, canvasX, canvasY, shiftAutoRatio);
        this.hasTargetSpawnPos = true;
        this.targetSpawnCanvasX = canvasX;
        this.targetSpawnCanvasY = canvasY;
        setVisible(true);
    }

    public void openAt(double canvasX, double canvasY) {
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = true;
        this.targetSpawnCanvasX = canvasX;
        this.targetSpawnCanvasY = canvasY;
        setVisible(true);
    }

    public void open() {
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = false;
        setVisible(true);
    }

    public static void cacheFromRecipes(Collection<EmiRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            List<SearchableRecipe> tempList = new ArrayList<>();
            for (EmiRecipe recipe : recipes) {
                SearchableRecipe sr = RecipeSearchEngine.buildIndex(recipe);
                if (sr != null) tempList.add(sr);
            }
            synchronized (GLOBAL_RECIPES) {
                GLOBAL_RECIPES.clear();
                GLOBAL_RECIPES.addAll(tempList);
                GLOBAL_CACHED = true;
            }
        });
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
                List<SearchableRecipe> tempList = new ArrayList<>();
                var recipeManager = EmiApi.getRecipeManager();
                if (recipeManager != null) {
                    List<EmiRecipe> recipes = recipeManager.getRecipes();
                    if (recipes != null && !recipes.isEmpty()) {
                        for (EmiRecipe recipe : recipes) {
                            SearchableRecipe sr = RecipeSearchEngine.buildIndex(recipe);
                            if (sr != null) tempList.add(sr);
                        }
                    } else {
                        for (EmiRecipeCategory cat : recipeManager.getCategories()) {
                            if (cat == null) continue;
                            List<EmiRecipe> catRecipes = recipeManager.getRecipes(cat);
                            if (catRecipes != null && !catRecipes.isEmpty()) {
                                for (EmiRecipe recipe : catRecipes) {
                                    SearchableRecipe sr = RecipeSearchEngine.buildIndex(recipe);
                                    if (sr != null) tempList.add(sr);
                                }
                            }
                        }
                    }
                }

                if (!tempList.isEmpty()) {
                    synchronized (GLOBAL_RECIPES) {
                        GLOBAL_RECIPES.clear();
                        GLOBAL_RECIPES.addAll(tempList);
                        GLOBAL_CACHED = true;
                    }
                } else {
                    GLOBAL_CACHED = false;
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
        } else {
            this.contextualWireTarget = null;
        }
    }

    private void onSearchQueryChanged(String query) {
        scrollOffset = 0;
        updateSearchResults(query);
    }

    private void updateSearchResults(String query) {
        filteredRecipes.clear();
        ParsedQuery parsedQuery = RecipeSearchEngine.parseQuery(query);

        synchronized (GLOBAL_RECIPES) {
            if (GLOBAL_RECIPES.isEmpty()) {
                ensureGlobalRecipesCachedAsync(() -> {
                    if (this.visible) {
                        updateSearchResults(searchBox.getValue());
                    }
                });
                return;
            }

            List<SearchableRecipe> rank3 = new ArrayList<>();
            List<SearchableRecipe> rank2 = new ArrayList<>();
            List<SearchableRecipe> rank1 = new ArrayList<>();

            String targetIdPath = (contextualWireTarget != null && contextualWireTarget.sourceStack != null && contextualWireTarget.sourceStack.getId() != null)
                    ? contextualWireTarget.sourceStack.getId().getPath().toLowerCase(Locale.ROOT) : null;
            String targetFullId = (contextualWireTarget != null && contextualWireTarget.sourceStack != null && contextualWireTarget.sourceStack.getId() != null)
                    ? contextualWireTarget.sourceStack.getId().toString().toLowerCase(Locale.ROOT) : null;
            String targetName = (contextualWireTarget != null && contextualWireTarget.sourceStack != null)
                    ? contextualWireTarget.sourceStack.getDisplayName().toLowerCase(Locale.ROOT) : null;

            for (SearchableRecipe sr : GLOBAL_RECIPES) {
                if (rank3.size() + rank2.size() + rank1.size() >= 150) break;

                // If contextual wire target is active, filter specifically for consumers / producers
                if (contextualWireTarget != null) {
                    boolean contextualMatch = false;
                    if (!contextualWireTarget.sourceIsInput) {
                        // Looking for CONSUMERS (recipes with matching input)
                        if (targetIdPath != null && sr.inputSearchIndex().contains(targetIdPath)) contextualMatch = true;
                        else if (targetFullId != null && sr.inputSearchIndex().contains(targetFullId)) contextualMatch = true;
                        else if (targetName != null && sr.inputSearchIndex().contains(targetName)) contextualMatch = true;
                    } else {
                        // Looking for PRODUCERS (recipes with matching output)
                        if (targetIdPath != null && sr.outputSearchIndex().contains(targetIdPath)) contextualMatch = true;
                        else if (targetFullId != null && sr.outputSearchIndex().contains(targetFullId)) contextualMatch = true;
                        else if (targetName != null && sr.outputSearchIndex().contains(targetName)) contextualMatch = true;
                    }

                    if (!contextualMatch) continue;
                }

                if (RecipeSearchEngine.matches(sr, parsedQuery)) {
                    int score = RecipeSearchEngine.calculateRank(sr, parsedQuery);
                    if (score == 3) rank3.add(sr);
                    else if (score == 2) rank2.add(sr);
                    else rank1.add(sr);
                }
            }

            filteredRecipes.addAll(rank3);
            filteredRecipes.addAll(rank2);
            filteredRecipes.addAll(rank1);
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
        String headerTitle;
        if (contextualWireTarget != null) {
            String stackName = contextualWireTarget.sourceStack != null ? contextualWireTarget.sourceStack.getDisplayName() : "Item";
            if (!contextualWireTarget.sourceIsInput) {
                headerTitle = "§6➔ " + Component.translatable("gui.gtcalcboard.search.consumers_for", stackName).getString();
            } else {
                headerTitle = "§a➔ " + Component.translatable("gui.gtcalcboard.search.producers_for", stackName).getString();
            }
        } else {
            headerTitle = "§6➕ " + Component.translatable("gui.gtcalcboard.add_recipe").getString();
        }
        graphics.drawString(font, font.plainSubstrByWidth(headerTitle, dialogW - 36), x + 10, y + 8, 0xFFFFFFFF, false);

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

        if (filteredRecipes.isEmpty()) {
            if (!GLOBAL_RECIPES.isEmpty()) {
                updateSearchResults(searchBox.getValue());
            } else if (!GLOBAL_CACHED) {
                ensureGlobalRecipesCachedAsync(() -> {
                    if (this.visible) {
                        updateSearchResults(searchBox.getValue());
                    }
                });
            }
        }

        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (filteredRecipes.isEmpty()) {
            String emptyMsg = (GLOBAL_RECIPES.isEmpty() || !GLOBAL_CACHED)
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
                EmiRecipe r = (EmiRecipe) sr.recipe();
                if (r != null && !r.getInputs().isEmpty() && !r.getInputs().get(0).getEmiStacks().isEmpty()) {
                    r.getInputs().get(0).getEmiStacks().get(0).render(graphics, listX + 6, rowY + 8, 0);
                }

                // Arrow
                graphics.drawString(font, "➔", listX + 28, rowY + 12, 0xFF657595, false);

                // Right of arrow: Display First Output Stack Icon
                if (!r.getOutputs().isEmpty()) {
                    r.getOutputs().get(0).render(graphics, listX + 42, rowY + 8, 0);
                }

                // Display Recipe Name / Machine Category
                String rName = sr.displayName();
                String catText = !sr.categoryName().isEmpty() ? "§7[" + sr.categoryName() + "§7] " : (!sr.categoryId().isEmpty() ? "§7[" + sr.categoryId() + "§7] " : "");
                String fullRowText = catText + "§f" + rName;
                int maxTextW = listW - 110;
                graphics.drawString(font, font.plainSubstrByWidth(fullRowText, maxTextW), listX + 64, rowY + 12, 0xFFFFFFFF, false);

                // Add button on the right
                int btnW = 44;
                int btnH = 18;
                int btnX = listX + listW - btnW - 6;
                int btnY = rowY + 7;
                boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0xFF2A6840 : 0xFF1E4D2F);
                graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF359050);
                graphics.drawCenteredString(font, "➕ " + Component.translatable("gui.gtcalcboard.add_btn").getString(), btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);
            }

            // Scrollbar indicator
            if (filteredRecipes.size() > visibleRows) {
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
                RecipeNode node = sr.recipe() instanceof EmiRecipe er ? EmiRecipeConverter.convert(er) : null;
                if (node != null) {
                    if (contextualWireTarget != null) {
                        // Place node near drop position
                        if (!contextualWireTarget.sourceIsInput) {
                            node.setPosX(contextualWireTarget.canvasX);
                            node.setPosY(contextualWireTarget.canvasY - 30);
                        } else {
                            node.setPosX(contextualWireTarget.canvasX - 245);
                            node.setPosY(contextualWireTarget.canvasY - 30);
                        }
                    } else if (hasTargetSpawnPos) {
                        node.setPosX(targetSpawnCanvasX - 80);
                        node.setPosY(targetSpawnCanvasY - 30);
                    } else {
                        double[] center = BoardScreen.getNextNodeCenterPosition(screenWidth, screenHeight);
                        node.setPosX(center[0]);
                        node.setPosY(center[1]);
                    }

                    parent.addNode(node);

                    // Perform auto-wire if contextual target is active!
                    if (contextualWireTarget != null) {
                        FlowGraph graph = parent.getGraph();
                        RecipeNode sourceNode = contextualWireTarget.sourceNode;
                        IngredientStack sourceStack = contextualWireTarget.sourceStack;

                        if (!contextualWireTarget.sourceIsInput) {
                            // Forward Wire: sourceNode (Output) -> node (Input)
                            int matchedInIdx = -1;
                            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                                IngredientStack in = node.getInputs().get(inIdx);
                                if (in.equals(sourceStack) || in.matchesOrAlternative(sourceStack)) {
                                    matchedInIdx = inIdx;
                                    if (!in.equals(sourceStack)) {
                                        in.selectAlternative(sourceStack.getId());
                                    }
                                    break;
                                }
                            }

                            if (matchedInIdx >= 0) {
                                FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(sourceNode.getId(), contextualWireTarget.sourcePortIdx, node.getId(), matchedInIdx);
                                graph.addConnection(sourceNode.getId(), contextualWireTarget.sourcePortIdx, node.getId(), matchedInIdx);

                                Double oldMachineCount = contextualWireTarget.shiftAutoRatio ? node.getMachineCount() : null;
                                Double newMachineCount = null;

                                if (contextualWireTarget.shiftAutoRatio) {
                                    double matched = FlowGraphSolver.calculateConsumerMatchCount(graph, sourceNode, contextualWireTarget.sourcePortIdx, node, matchedInIdx);
                                    newMachineCount = matched;
                                    node.setMachineCount(matched);
                                    BoardToast.show(Component.literal("§a⚡ ").append(
                                        Component.translatable("message.gtcalcboard.shift_connect_matched", node.getName(), String.format("%.0f", matched))
                                    ));
                                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
                                } else {
                                    BoardToast.show(Component.literal("§a✔ ").append(
                                        Component.translatable("gui.gtcalcboard.toast.drag_auto_connected", sourceNode.getName(), node.getName())
                                    ));
                                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
                                }
                                parent.recordCommand(new BoardCommand.ConnectWireCommand(newEdge, contextualWireTarget.shiftAutoRatio ? node.getId() : null, oldMachineCount, newMachineCount));
                            }
                        } else {
                            // Reverse Wire: node (Output) -> sourceNode (Input)
                            int matchedOutIdx = -1;
                            for (int outIdx = 0; outIdx < node.getOutputs().size(); outIdx++) {
                                IngredientStack out = node.getOutputs().get(outIdx);
                                if (out.equals(sourceStack) || sourceStack.matchesOrAlternative(out)) {
                                    matchedOutIdx = outIdx;
                                    if (!out.equals(sourceStack)) {
                                        sourceStack.selectAlternative(out.getId());
                                    }
                                    break;
                                }
                            }

                            if (matchedOutIdx >= 0) {
                                FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(node.getId(), matchedOutIdx, sourceNode.getId(), contextualWireTarget.sourcePortIdx);
                                graph.addConnection(node.getId(), matchedOutIdx, sourceNode.getId(), contextualWireTarget.sourcePortIdx);

                                Double oldMachineCount = contextualWireTarget.shiftAutoRatio ? node.getMachineCount() : null;
                                Double newMachineCount = null;

                                if (contextualWireTarget.shiftAutoRatio) {
                                    double matched = FlowGraphSolver.calculateProducerMatchCount(graph, node, matchedOutIdx, sourceNode, contextualWireTarget.sourcePortIdx);
                                    newMachineCount = matched;
                                    node.setMachineCount(matched);
                                    BoardToast.show(Component.literal("§a⚡ ").append(
                                        Component.translatable("message.gtcalcboard.shift_connect_matched", node.getName(), String.format("%.0f", matched))
                                    ));
                                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
                                } else {
                                    BoardToast.show(Component.literal("§a✔ ").append(
                                        Component.translatable("gui.gtcalcboard.toast.drag_auto_connected", node.getName(), sourceNode.getName())
                                    ));
                                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
                                }
                                parent.recordCommand(new BoardCommand.ConnectWireCommand(newEdge, contextualWireTarget.shiftAutoRatio ? node.getId() : null, oldMachineCount, newMachineCount));
                            }
                        }

                        contextualWireTarget = null;
                    }

                    parent.markSummaryDirty();
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
