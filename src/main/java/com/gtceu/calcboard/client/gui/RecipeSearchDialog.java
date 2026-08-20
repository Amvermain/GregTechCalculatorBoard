package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.FlowGraphSolver;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeFilterDialog;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
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
import net.minecraft.resources.ResourceLocation;
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
    private static final java.util.concurrent.ExecutorService SEARCH_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GTCalcBoard-SearchWorker");
        t.setDaemon(true);
        return t;
    });

    private final java.util.concurrent.atomic.AtomicInteger searchVersion = new java.util.concurrent.atomic.AtomicInteger(0);
    private final List<SearchableRecipe> filteredRecipes = new ArrayList<>();
    private final RecipeFilterDialog filterDialog = new RecipeFilterDialog();
    private int scrollOffset = 0;
    private boolean visible = false;
    private boolean hasTargetSpawnPos = false;
    private double targetSpawnCanvasX = 0;
    private double targetSpawnCanvasY = 0;
    private ContextualWireTarget contextualWireTarget = null;
    private SearchableRecipe stickyHoverRecipe = null;
    private int stickyHoverRowY = 0;
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    private static final int DIALOG_WIDTH = 360;
    private static final int DIALOG_HEIGHT = 280;
    private static final int ROW_HEIGHT = 32;

    public RecipeSearchDialog(BoardScreen parent) {
        this.parent = parent;
        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, 0, 0, DIALOG_WIDTH - 48, 16, Component.translatable("gui.gtcalcboard.search"));
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.searchBox.setHint(Component.translatable("gui.gtcalcboard.search.search_help"));

        this.filterDialog.setOnFilterChanged(() -> updateSearchResults(searchBox.getValue()));
    }

    public static void clearGlobalCache() {
        GLOBAL_RECIPES.clear();
        GLOBAL_CACHED = false;
        IS_CACHING = false;
    }

    public static void invalidateCache() {
        clearGlobalCache();
    }

    public static boolean isGlobalCached() {
        return GLOBAL_CACHED;
    }

    public static int getCachedRecipeCount() {
        return GLOBAL_RECIPES.size();
    }

    public static void ensureGlobalRecipesCachedAsync(Runnable onComplete) {
        if (GLOBAL_CACHED) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (IS_CACHING) return;
        IS_CACHING = true;

        CompletableFuture.runAsync(() -> {
            try {
                var emiManager = dev.emi.emi.api.EmiApi.getRecipeManager();
                List<EmiRecipe> recipes = emiManager != null ? emiManager.getRecipes() : null;

                // If EMI is still actively baking on worker thread, retry in background every 200ms (up to 15 times = 3s)
                int retries = 0;
                while ((recipes == null || recipes.isEmpty()) && retries < 15) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {}
                    emiManager = dev.emi.emi.api.EmiApi.getRecipeManager();
                    recipes = emiManager != null ? emiManager.getRecipes() : null;
                    retries++;
                }

                if (recipes == null || recipes.isEmpty()) {
                    IS_CACHING = false;
                    return;
                }

                long startNanos = System.nanoTime();
                // Fast parallel indexing across all CPU cores (reduces 120s+ to < 1s)
                List<SearchableRecipe> tempList = recipes.parallelStream()
                        .map(RecipeSearchEngine::buildIndex)
                        .filter(Objects::nonNull)
                        .toList();

                synchronized (GLOBAL_RECIPES) {
                    GLOBAL_RECIPES.clear();
                    GLOBAL_RECIPES.addAll(tempList);
                    GLOBAL_CACHED = true;
                }

                RecipeFilterDialog.updateDiscoveredCategories(RecipeSearchEngine.discoverCategories(tempList));
                com.gtceu.calcboard.api.CategoryCapabilityMatrix.getInstance().bake(recipes);

                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                        "[GTCalcBoard] [RecipeSearch] Indexed {} EMI recipes in {}ms across {} CPU cores.",
                        tempList.size(), elapsedMs, Runtime.getRuntime().availableProcessors()
                );

                if (onComplete != null) {
                    Minecraft.getInstance().execute(onComplete);
                }
            } catch (Throwable t) {
                IS_CACHING = false;
            } finally {
                IS_CACHING = false;
            }
        });
    }

    public void openForContextualWire(RecipeNode sourceNode, int sourcePortIdx, boolean sourceIsInput, IngredientStack sourceStack, double canvasX, double canvasY, boolean shiftAutoRatio) {
        this.contextualWireTarget = new ContextualWireTarget(sourceNode, sourcePortIdx, sourceIsInput, sourceStack, canvasX, canvasY, shiftAutoRatio);
        this.hasTargetSpawnPos = true;
        this.targetSpawnCanvasX = canvasX;
        this.targetSpawnCanvasY = canvasY;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [UI] RecipeSearchDialog opened (Contextual Drag & Search for stack: '{}', isInput: {}).",
                sourceStack != null ? sourceStack.getDisplayName() : "null", sourceIsInput
        );
        setVisible(true, true, canvasX, canvasY);
    }

    public void openAt(double canvasX, double canvasY) {
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = true;
        this.targetSpawnCanvasX = canvasX;
        this.targetSpawnCanvasY = canvasY;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] RecipeSearchDialog opened at canvas pos ({}, {}).", canvasX, canvasY);
        setVisible(true, true, canvasX, canvasY);
    }

    public void open() {
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = false;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] RecipeSearchDialog opened.");
        setVisible(true, false, 0, 0);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible, boolean isLeftClick, double canvasX, double canvasY) {
        this.visible = visible;
        if (visible) {
            this.scrollOffset = 0;
            this.hasTargetSpawnPos = isLeftClick;
            this.targetSpawnCanvasX = canvasX;
            this.targetSpawnCanvasY = canvasY;
            this.stickyHoverRecipe = null;
            searchBox.setValue("");
            searchBox.setFocused(true);
            ensureGlobalRecipesCachedAsync(() -> {
                if (this.visible) {
                    updateSearchResults(searchBox.getValue());
                }
            });
            updateSearchResultsSynchronously("");
        } else {
            this.contextualWireTarget = null;
            this.stickyHoverRecipe = null;
        }
    }

    public void setVisible(boolean visible) {
        setVisible(visible, false, 0, 0);
    }

    private void onSearchQueryChanged(String query) {
        scrollOffset = 0;
        final int currentVersion = searchVersion.incrementAndGet();
        if (query == null || query.trim().isEmpty()) {
            updateSearchResultsSynchronously("");
            return;
        }

        SEARCH_EXECUTOR.submit(() -> {
            try {
                Thread.sleep(50);
                if (currentVersion != searchVersion.get()) {
                    return;
                }

                List<SearchableRecipe> results = computeSearchResults(query);
                if (currentVersion == searchVersion.get()) {
                    Minecraft.getInstance().execute(() -> {
                        if (currentVersion == searchVersion.get()) {
                            this.filteredRecipes.clear();
                            this.filteredRecipes.addAll(results);
                        }
                    });
                }
            } catch (InterruptedException ignored) {}
        });
    }

    public static List<SearchableRecipe> getTutorialDummyRecipes() {
        List<SearchableRecipe> list = new ArrayList<>();

        // 1. Steam Turbine (Tutorial)
        RecipeNode turbine = RecipeNode.create("Steam Turbine (Tutorial)", 20.0, 64.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0, 1.0));
        list.add(createTutorialSearchableRecipe(turbine, "gtceu", "steam_turbine", "Steam Turbine",
                List.of(), List.of("Steam"), List.of(), List.of("gtceu:steam", "steam")));

        // 2. Boiler (Tutorial)
        RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 30.0, GTVoltageTier.LV);
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        list.add(createTutorialSearchableRecipe(boiler, "gtceu", "boiler", "Boiler",
                List.of("Steam"), List.of(), List.of("gtceu:steam", "steam"), List.of()));

        // 3. Steam Engine (Tutorial)
        RecipeNode engine = RecipeNode.create("Steam Engine (Tutorial)", 20.0, 32.0, GTVoltageTier.LV);
        engine.setGenerator(true);
        engine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 200.0, 1.0));
        list.add(createTutorialSearchableRecipe(engine, "gtceu", "steam_engine", "Steam Engine",
                List.of(), List.of("Steam"), List.of(), List.of("gtceu:steam", "steam")));

        return list;
    }

    private static SearchableRecipe createTutorialSearchableRecipe(
            RecipeNode template,
            String modId,
            String categoryId,
            String categoryName,
            List<String> outputNames,
            List<String> inputNames,
            List<String> outputIds,
            List<String> inputIds
    ) {
        StringBuilder outSb = new StringBuilder();
        outputNames.forEach(o -> outSb.append(o.toLowerCase(Locale.ROOT)).append(" "));
        StringBuilder inSb = new StringBuilder();
        inputNames.forEach(i -> inSb.append(i.toLowerCase(Locale.ROOT)).append(" "));

        StringBuilder fullSb = new StringBuilder();
        fullSb.append(template.getName().toLowerCase(Locale.ROOT)).append(" ");
        fullSb.append(modId.toLowerCase(Locale.ROOT)).append(" ");
        fullSb.append(categoryId.toLowerCase(Locale.ROOT)).append(" ");
        fullSb.append(categoryName.toLowerCase(Locale.ROOT)).append(" ");
        fullSb.append(outSb).append(" ").append(inSb);

        return new SearchableRecipe(
                template,
                template.getName(),
                modId,
                categoryId,
                categoryName,
                outputNames,
                inputNames,
                outputIds,
                inputIds,
                List.of(),
                List.of(),
                List.of(),
                outSb.toString(),
                inSb.toString(),
                fullSb.toString()
        );
    }

    private void updateSearchResults(String query) {
        onSearchQueryChanged(query);
    }

    private void updateSearchResultsSynchronously(String query) {
        searchVersion.incrementAndGet();
        List<SearchableRecipe> results = computeSearchResults(query);
        filteredRecipes.clear();
        filteredRecipes.addAll(results);
    }

    private List<SearchableRecipe> computeSearchResults(String query) {
        ParsedQuery parsedQuery = RecipeSearchEngine.parseQuery(query);

        boolean isTutorial = TutorialManager.getInstance().isActive();
        List<SearchableRecipe> sourceList;
        if (isTutorial) {
            sourceList = getTutorialDummyRecipes();
        } else {
            synchronized (GLOBAL_RECIPES) {
                if (GLOBAL_RECIPES.isEmpty()) {
                    ensureGlobalRecipesCachedAsync(() -> {
                        if (this.visible) {
                            updateSearchResults(searchBox.getValue());
                        }
                    });
                    return Collections.emptyList();
                }
                sourceList = new ArrayList<>(GLOBAL_RECIPES);
            }
        }

        record ScoredRecipe(SearchableRecipe recipe, int score) {}
        List<ScoredRecipe> scoredList = new ArrayList<>();

        boolean hasContext = (contextualWireTarget != null && contextualWireTarget.sourceStack != null);
        boolean targetIsFluid = hasContext && contextualWireTarget.sourceStack.isFluid();
        String targetIdPath = (hasContext && contextualWireTarget.sourceStack.getId() != null)
                ? contextualWireTarget.sourceStack.getId().getPath().toLowerCase(Locale.ROOT) : null;
        String targetFullId = (hasContext && contextualWireTarget.sourceStack.getId() != null)
                ? contextualWireTarget.sourceStack.getId().toString().toLowerCase(Locale.ROOT) : null;
        String targetName = (hasContext && contextualWireTarget.sourceStack.getDisplayName() != null)
                ? contextualWireTarget.sourceStack.getDisplayName().toLowerCase(Locale.ROOT) : null;

        RecipeFilterConfig filterConfig = RecipeFilterConfig.getInstance();

        for (SearchableRecipe sr : sourceList) {
            if (filterConfig.isCategoryExcluded(sr.categoryId())) {
                continue;
            }
            if (!RecipeSearchEngine.matches(sr, parsedQuery)) {
                continue;
            }

            int contextualScore = 0;
            if (hasContext) {
                if (!contextualWireTarget.sourceIsInput) {
                    // Looking for CONSUMERS (recipes with matching input)
                    if (targetIsFluid) {
                        if ((targetFullId != null && sr.inputFluidIds().contains(targetFullId))
                                || (targetIdPath != null && sr.inputFluidIds().contains(targetIdPath))) {
                            contextualScore = 1000;
                        } else if (targetName != null && sr.inputNames().contains(targetName)) {
                            contextualScore = 500;
                        }
                    } else {
                        if ((targetFullId != null && !sr.inputFluidIds().contains(targetFullId) && sr.inputIds().contains(targetFullId))
                                || (targetIdPath != null && !sr.inputFluidIds().contains(targetIdPath) && sr.inputIds().contains(targetIdPath))) {
                            contextualScore = 1000;
                        } else if (targetName != null && sr.inputNames().contains(targetName)) {
                            contextualScore = 500;
                        }
                    }

                    // If in contextual mode and query is empty, only show matching recipes.
                    // If user typed a search query, allow general search across all recipes.
                    if (contextualScore == 0 && (query == null || query.trim().isEmpty())) {
                        continue;
                    }
                } else {
                    // Looking for PRODUCERS (recipes with matching output)
                    if (targetIsFluid) {
                        if ((targetFullId != null && sr.outputFluidIds().contains(targetFullId))
                                || (targetIdPath != null && sr.outputFluidIds().contains(targetIdPath))) {
                            contextualScore = 1000;
                        } else if (targetName != null && sr.outputNames().contains(targetName)) {
                            contextualScore = 500;
                        }
                    } else {
                        if ((targetFullId != null && !sr.outputFluidIds().contains(targetFullId) && sr.outputIds().contains(targetFullId))
                                || (targetIdPath != null && !sr.outputFluidIds().contains(targetIdPath) && sr.outputIds().contains(targetIdPath))) {
                            contextualScore = 1000;
                        } else if (targetName != null && sr.outputNames().contains(targetName)) {
                            contextualScore = 500;
                        }
                    }

                    // If in contextual mode and query is empty, only show matching recipes.
                    if (contextualScore == 0 && (query == null || query.trim().isEmpty())) {
                        continue;
                    }
                }
            }

            int queryScore = RecipeSearchEngine.calculateRank(sr, parsedQuery);
            int totalScore = contextualScore + (queryScore * 10);

            // Priority bonus for machine processing recipes over generic crafting table
            String cat = sr.categoryId().toLowerCase(Locale.ROOT);
            if (!cat.equals("crafting") && !cat.equals("minecraft:crafting")) {
                totalScore += 100;
            }
            if (cat.contains("turbine") || cat.contains("generator") || cat.contains("boiler")) {
                totalScore += 50;
            }

            scoredList.add(new ScoredRecipe(sr, totalScore));
        }

        // Sort descending by score
        scoredList.sort((a, b) -> Integer.compare(b.score(), a.score()));

        int limit = Math.min(150, scoredList.size());
        List<SearchableRecipe> resultList = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            resultList.add(scoredList.get(i).recipe());
        }
        return resultList;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

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

        // Search Input Box & Filter Button
        int filterBtnW = 20;
        int filterBtnH = 16;
        int filterBtnX = x + dialogW - 12 - filterBtnW;
        int filterBtnY = y + 30;

        searchBox.setX(x + 12);
        searchBox.setY(y + 30);
        searchBox.setWidth(dialogW - 24 - filterBtnW - 4);
        searchBox.render(graphics, mouseX, mouseY, 0);

        boolean filterHover = mouseX >= filterBtnX && mouseX <= filterBtnX + filterBtnW && mouseY >= filterBtnY && mouseY <= filterBtnY + filterBtnH;
        boolean filterActive = !RecipeFilterConfig.getInstance().getExcludedCategories().isEmpty();
        graphics.fill(filterBtnX, filterBtnY, filterBtnX + filterBtnW, filterBtnY + filterBtnH, filterHover ? 0xFF334155 : (filterActive ? 0xFF2A3649 : 0xFF1E293B));
        graphics.renderOutline(filterBtnX, filterBtnY, filterBtnW, filterBtnH, filterActive ? 0xFF38BDF8 : 0xFF475569);
        graphics.drawCenteredString(font, "⚙", filterBtnX + filterBtnW / 2, filterBtnY + 4, filterActive ? 0xFF38BDF8 : 0xFF94A3B8);

        if (filterHover && !filterDialog.isVisible()) {
            String raw = Component.translatable("gui.gtcalcboard.filter.btn_tooltip").getString();
            if (raw.contains("\n")) {
                List<Component> lines = java.util.Arrays.stream(raw.split("\n"))
                        .<Component>map(Component::literal)
                        .toList();
                graphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, Component.translatable("gui.gtcalcboard.filter.btn_tooltip"), mouseX, mouseY);
            }
        }

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

        SearchableRecipe newlyHoveredRecipe = null;
        int newlyHoveredRowY = 0;

        if (filteredRecipes.isEmpty()) {
            boolean isLoading = GLOBAL_RECIPES.isEmpty() || !GLOBAL_CACHED;
            long animDots = (System.currentTimeMillis() / 400L) % 4;
            String dots = ".".repeat((int) animDots);
            String emptyMsg = isLoading
                ? "§e⏳ " + Component.translatable("gui.gtcalcboard.loading_recipes").getString() + dots
                : "§7" + Component.translatable("gui.gtcalcboard.no_matching_recipes").getString();
            graphics.drawCenteredString(font, emptyMsg, listX + listW / 2, listY + listH / 2 - 4, isLoading ? 0xFFE0C040 : 0xFF888888);
        } else {
            for (int i = 0; i < visibleRows; i++) {
                int index = scrollOffset + i;
                if (index >= filteredRecipes.size()) break;

                SearchableRecipe sr = filteredRecipes.get(index);
                int rowY = listY + i * ROW_HEIGHT;

                // Add button on the right
                int btnW = 44;
                int btnH = 18;
                int btnX = listX + listW - btnW - 6;
                int btnY = rowY + 7;
                boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                boolean rowHover = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
                if (rowHover) {
                    newlyHoveredRecipe = sr;
                    newlyHoveredRowY = rowY;
                }

                boolean isRowSelectedOrHovered = rowHover || (stickyHoverRecipe == sr);
                graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, isRowSelectedOrHovered ? 0xFF2A3649 : (i % 2 == 0 ? 0xFF1A1E26 : 0xFF161A21));

                // Left: Display First Input Stack Icon & Rate
                if (sr.recipe() instanceof EmiRecipe r) {
                    if (!r.getInputs().isEmpty() && !r.getInputs().get(0).getEmiStacks().isEmpty()) {
                        r.getInputs().get(0).getEmiStacks().get(0).render(graphics, listX + 6, rowY + 8, 0);
                    }

                    // Arrow
                    graphics.drawString(font, "➔", listX + 28, rowY + 12, 0xFF657595, false);

                    // Right of arrow: Display First Output Stack Icon
                    if (!r.getOutputs().isEmpty()) {
                        r.getOutputs().get(0).render(graphics, listX + 42, rowY + 8, 0);
                    }
                } else if (sr.recipe() instanceof RecipeNode rn) {
                    // Tutorial dummy node preview
                    if (!rn.getInputs().isEmpty()) {
                        graphics.drawString(font, "📥", listX + 6, rowY + 12, 0xFFFFFFFF, false);
                    }
                    graphics.drawString(font, "➔", listX + 28, rowY + 12, 0xFF657595, false);
                    if (rn.isGenerator() || !rn.getOutputs().isEmpty()) {
                        graphics.drawString(font, rn.isGenerator() ? "⚡" : "📦", listX + 42, rowY + 12, 0xFFFFFFFF, false);
                    }
                }

                // Display Recipe Name / Machine Category
                String rName = sr.displayName();
                String catText = !sr.categoryName().isEmpty() ? "§7[" + sr.categoryName() + "§7] " : (!sr.categoryId().isEmpty() ? "§7[" + sr.categoryId() + "§7] " : "");
                String fullRowText = catText + "§f" + rName;
                int maxTextW = listW - 110;
                graphics.drawString(font, font.plainSubstrByWidth(fullRowText, maxTextW), listX + 64, rowY + 12, 0xFFFFFFFF, false);

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

        if (newlyHoveredRecipe != null) {
            stickyHoverRecipe = newlyHoveredRecipe;
            stickyHoverRowY = newlyHoveredRowY;
        } else if (stickyHoverRecipe != null) {
            int[] bounds = RecipeHoverPreviewRenderer.calculatePreviewBounds(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, screenWidth, screenHeight);
            if (bounds != null) {
                int cardX = bounds[0];
                int cardY = bounds[1];
                int cardW = bounds[2];
                int cardH = bounds[3];

                int minX = Math.min(x, cardX) - 16;
                int maxX = Math.max(x + dialogW, cardX + cardW) + 16;
                int minY = Math.min(y, cardY) - 16;
                int maxY = Math.max(y + dialogH, cardY + cardH) + 16;

                boolean inBridgeZone = mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
                if (!inBridgeZone) {
                    stickyHoverRecipe = null;
                }
            } else {
                stickyHoverRecipe = null;
            }
        }

        // Render Floating Recipe Preview Card on Hover
        if (stickyHoverRecipe != null && !filterDialog.isVisible()) {
            RecipeHoverPreviewRenderer.renderPreview(graphics, stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, mouseX, mouseY, 0, screenWidth, screenHeight);
        }

        graphics.pose().popPose();

        // Render Filter Dialog if visible
        if (filterDialog.isVisible()) {
            filterDialog.render(graphics, mouseX, mouseY, screenWidth, screenHeight);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        if (filterDialog.isVisible()) {
            return filterDialog.mouseClicked(mouseX, mouseY, button, screenWidth, screenHeight);
        }

        int dialogW = Math.min(380, screenWidth - 24);
        int dialogH = Math.min(280, screenHeight - 24);
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // If mouse is inside the sticky preview card on the right
        if (stickyHoverRecipe != null) {
            int[] bounds = RecipeHoverPreviewRenderer.calculatePreviewBounds(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, screenWidth, screenHeight);
            if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                var hoveredIngredient = RecipeHoverPreviewRenderer.getHoveredIngredient(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, (int) mouseX, (int) mouseY, screenWidth, screenHeight);
                if (hoveredIngredient != null && !hoveredIngredient.isEmpty()) {
                    String name = hoveredIngredient.getEmiStacks().get(0).getName().getString();
                    searchBox.setValue(name);
                    searchBox.setFocused(true);
                    return true;
                }
                return true;
            }
        }

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

        // Filter button [⚙]
        int filterBtnW = 20;
        int filterBtnH = 16;
        int filterBtnX = x + dialogW - 12 - filterBtnW;
        int filterBtnY = y + 30;
        if (mouseX >= filterBtnX && mouseX <= filterBtnX + filterBtnW && mouseY >= filterBtnY && mouseY <= filterBtnY + filterBtnH) {
            synchronized (GLOBAL_RECIPES) {
                filterDialog.updateCategories(RecipeSearchEngine.discoverCategories(GLOBAL_RECIPES));
            }
            filterDialog.setVisible(true);
            return true;
        }

        searchBox.mouseClicked(mouseX, mouseY, button);
        searchBox.setFocused(true);

        // Click anywhere on a row in the list to select & add that recipe
        int listX = x + 12;
        int listY = y + 52;
        int listW = dialogW - 24;
        int listH = dialogH - 60;
        int visibleRows = Math.max(1, listH / ROW_HEIGHT);

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollOffset + i;
            if (index >= filteredRecipes.size()) break;

            int rowY = listY + i * ROW_HEIGHT;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                SearchableRecipe sr = filteredRecipes.get(index);
                addRecipeAt(sr, screenWidth, screenHeight);
                return true;
            }
        }

        return true;
    }

    public void addRecipeAt(SearchableRecipe sr, int screenWidth, int screenHeight) {
        if (sr == null) return;
        RecipeNode node = sr.recipe() instanceof EmiRecipe er ? EmiRecipeConverter.convert(er) : (sr.recipe() instanceof RecipeNode rn ? rn.copy() : null);
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
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                    "[GTCalcBoard] [UI] Added recipe node '{}' to board (Category: {}, Outputs: {}).",
                    node.getName(), node.getRecipeCategoryId(), node.getOutputs().size()
            );

            if (contextualWireTarget != null) {
                RecipeNode sourceNode = contextualWireTarget.sourceNode;
                IngredientStack sourceStack = contextualWireTarget.sourceStack;

                if (!contextualWireTarget.sourceIsInput) {
                    connectContextualForwardWire(node, sourceNode, sourceStack);
                } else {
                    connectContextualReverseWire(node, sourceNode, sourceStack);
                }

                contextualWireTarget = null;
                parent.rebuildWidgets();
            }

            parent.markSummaryDirty();
            setVisible(false);
        }
    }

    private void connectContextualForwardWire(RecipeNode node, RecipeNode sourceNode, IngredientStack sourceStack) {
        FlowGraph graph = parent.getGraph();
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
            TutorialManager.getInstance().onWireConnected(contextualWireTarget.shiftAutoRatio);
        }
    }

    private void connectContextualReverseWire(RecipeNode node, RecipeNode sourceNode, IngredientStack sourceStack) {
        FlowGraph graph = parent.getGraph();
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
            TutorialManager.getInstance().onWireConnected(contextualWireTarget.shiftAutoRatio);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        if (filterDialog.isVisible()) {
            return filterDialog.mouseScrolled(mouseX, mouseY, delta, parent.width, parent.height);
        }
        int dialogH = Math.min(280, parent.height - 24);
        int listH = dialogH - 60;
        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta) * 2));
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (filterDialog.isVisible()) {
            return filterDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) { // Escape closes search dialog
            setVisible(false);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter adds top recipe
            if (!filteredRecipes.isEmpty()) {
                addRecipeAt(filteredRecipes.get(0), parent.width, parent.height);
                return true;
            }
        }
        // R / U recipe lookup over preview card ingredient
        if ((keyCode == 82 || keyCode == 85) && stickyHoverRecipe != null) { // R or U
            int dialogW = Math.min(380, parent.width - 24);
            int dialogH = Math.min(280, parent.height - 24);
            int x = (parent.width - dialogW) / 2;
            int y = (parent.height - dialogH) / 2;

            var hoveredIngredient = RecipeHoverPreviewRenderer.getHoveredIngredient(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, lastMouseX, lastMouseY, parent.width, parent.height);
            if (hoveredIngredient != null && !hoveredIngredient.isEmpty()) {
                if (keyCode == 82) { // R
                    dev.emi.emi.api.EmiApi.displayRecipes(hoveredIngredient);
                } else { // U
                    dev.emi.emi.api.EmiApi.displayUses(hoveredIngredient);
                }
                return true;
            }
        }
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (filterDialog.isVisible()) {
            return filterDialog.charTyped(codePoint, modifiers);
        }
        return searchBox.charTyped(codePoint, modifiers);
    }
}
