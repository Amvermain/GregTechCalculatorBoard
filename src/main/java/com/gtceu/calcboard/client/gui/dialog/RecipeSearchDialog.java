package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.event.CatalogLifecycleEvent;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.FavoritesDockWidget;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeFilterDialog;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;

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
    private static final List<Runnable> ON_COMPLETE_CALLBACKS = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean GLOBAL_CACHED = false;
    private static volatile boolean IS_CACHING = false;
    private static volatile long GLOBAL_VERSION = 0;
    private static final java.util.concurrent.ScheduledExecutorService SEARCH_SCHEDULER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GTCalcBoard-SearchScheduler");
        t.setDaemon(true);
        return t;
    });
    private java.util.concurrent.ScheduledFuture<?> pendingSearchTask = null;

    private final java.util.concurrent.atomic.AtomicInteger searchVersion = new java.util.concurrent.atomic.AtomicInteger(0);
    private boolean showFavoritesOnly = false;
    private final List<SearchableRecipe> filteredRecipes = new ArrayList<>();
    private final RecipeFilterDialog filterDialog = new RecipeFilterDialog();
    private int scrollOffset = 0;
    private boolean visible = false;
    private boolean hasTargetSpawnPos = false;
    private double targetSpawnCanvasX = 0;
    private double targetSpawnCanvasY = 0;
    private ContextualWireTarget contextualWireTarget = null;
    private RecipeNode switchTargetNode = null;
    private SearchableRecipe stickyHoverRecipe = null;
    private int stickyHoverRowY = 0;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private ResourceLocation currentContextualDefaultRecipeId = null;
    private ParsedQuery currentParsedQuery = null;
    private long lastObservedGlobalVersion = -1;

    private static final List<Runnable> FAVORITES_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void registerFavoritesListener(Runnable listener) {
        if (listener != null) {
            FAVORITES_LISTENERS.add(listener);
        }
    }

    public static void unregisterFavoritesListener(Runnable listener) {
        FAVORITES_LISTENERS.remove(listener);
    }

    public static void notifyFavoritesChanged() {
        FavoritesDockWidget.clearCache();
        for (Runnable r : FAVORITES_LISTENERS) {
            try {
                r.run();
            } catch (Throwable ignored) {}
        }
    }

    private static final int DIALOG_WIDTH = 360;
    private static final int DIALOG_HEIGHT = 280;
    private static final int ROW_HEIGHT = 32;

    public record PrefixGuideItem(
            String prefix,
            String labelKey,
            String descKey,
            int color,
            int hoverBg
    ) {}

    public static final List<PrefixGuideItem> PREFIX_ITEMS = List.of(
            new PrefixGuideItem("@", "gui.gtcalcboard.search.prefix.mod", "gui.gtcalcboard.search.prefix.mod.desc", 0xFF38BDF8, 0xFF1C2C44),
            new PrefixGuideItem("#", "gui.gtcalcboard.search.prefix.tag", "gui.gtcalcboard.search.prefix.tag.desc", 0xFFFBBF24, 0xFF3D351C),
            new PrefixGuideItem("[", "gui.gtcalcboard.search.prefix.category", "gui.gtcalcboard.search.prefix.category.desc", 0xFF4ADE80, 0xFF1B3D26),
            new PrefixGuideItem(">", "gui.gtcalcboard.search.prefix.input", "gui.gtcalcboard.search.prefix.input.desc", 0xFFFB923C, 0xFF3D2C1C),
            new PrefixGuideItem("<", "gui.gtcalcboard.search.prefix.output", "gui.gtcalcboard.search.prefix.output.desc", 0xFFF472B6, 0xFF3D1C34),
            new PrefixGuideItem("!", "gui.gtcalcboard.search.prefix.exclude", "gui.gtcalcboard.search.prefix.exclude.desc", 0xFFF87171, 0xFF3D1C1C),
            new PrefixGuideItem("|", "gui.gtcalcboard.search.prefix.or", "gui.gtcalcboard.search.prefix.or.desc", 0xFFA78BFA, 0xFF2A1C44),
            new PrefixGuideItem("\"", "gui.gtcalcboard.search.prefix.exact", "gui.gtcalcboard.search.prefix.exact.desc", 0xFFE2E8F0, 0xFF282E3B)
    );

    public RecipeSearchDialog(BoardScreen parent) {
        this.parent = parent;
        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, 0, 0, DIALOG_WIDTH - 48, 16, Component.translatable("gui.gtcalcboard.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.searchBox.setHint(Component.translatable("gui.gtcalcboard.search.search_help"));

        this.filterDialog.setOnFilterChanged(() -> updateSearchResults(searchBox.getValue()));
        registerFavoritesListener(() -> {
            if (this.visible) {
                updateSearchResults(searchBox.getValue());
            }
        });
    }

    public static Set<ResourceLocation> getFavoriteRecipeIds() {
        try {
            return com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().getFavoriteRecipeIds();
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    public static boolean isRecipeFavorite(Object recipe) {
        if (recipe == null) return false;
        try {
            return com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().isFavorite(recipe);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void toggleFavoriteRecipe(Object recipe) {
        if (recipe == null) return;
        try {
            com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().toggleFavorite(recipe);
        } catch (Throwable ignored) {}
    }

    public static void clearGlobalCache() {
        GLOBAL_RECIPES.clear();
        ON_COMPLETE_CALLBACKS.clear();
        GLOBAL_CACHED = false;
        IS_CACHING = false;
        GLOBAL_VERSION++;
    }

    public static void invalidateCache() {
        clearGlobalCache();
    }

    public static boolean isGlobalCached() {
        return GLOBAL_CACHED;
    }

    public static long getGlobalVersion() {
        return GLOBAL_VERSION;
    }

    public static int getCachedRecipeCount() {
        return GLOBAL_RECIPES.size();
    }

    public static List<SearchableRecipe> getGlobalRecipes() {
        synchronized (GLOBAL_RECIPES) {
            return new ArrayList<>(GLOBAL_RECIPES);
        }
    }

    public record RecipeLoadingProgress(int currentPhase, int totalPhases, String phaseKey, String detail) {}
    private static volatile RecipeLoadingProgress CACHING_PROGRESS = new RecipeLoadingProgress(1, 4, "gui.gtcalcboard.loading_recipe_phase.1", "");

    public static RecipeLoadingProgress getCachingProgress() {
        return CACHING_PROGRESS;
    }

    public static boolean isCaching() {
        return IS_CACHING;
    }

    private static final java.util.concurrent.ForkJoinPool BACKGROUND_INDEX_POOL = new java.util.concurrent.ForkJoinPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)),
            pool -> {
                var worker = java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                worker.setName("GTCalcBoard-RecipeIndexer-" + worker.getPoolIndex());
                worker.setDaemon(true);
                worker.setPriority(Thread.MIN_PRIORITY);
                return worker;
            },
            null,
            false
    );

    public static void ensureGlobalRecipesCachedAsync(Runnable onComplete) {
        if (GLOBAL_CACHED) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (onComplete != null) {
            ON_COMPLETE_CALLBACKS.add(onComplete);
        }
        if (IS_CACHING) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level == null) {
            return;
        }

        var adapter = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter();
        if (!adapter.isRecipeBakingComplete()) {
            adapter.runWhenReady(() -> ensureGlobalRecipesCachedAsync(null));
            return;
        }

        IS_CACHING = true;
        CACHING_PROGRESS = new RecipeLoadingProgress(1, 4, "gui.gtcalcboard.loading_recipe_phase.1", "Connecting to " + adapter.getViewerId().toUpperCase(Locale.ROOT) + " Recipe Manager");

        CompletableFuture.runAsync(() -> {
            try {
                long startNanos = System.nanoTime();
                List<SearchableRecipe> rawList = adapter.collectSearchableRecipes();

                CACHING_PROGRESS = new RecipeLoadingProgress(2, 4, "gui.gtcalcboard.loading_recipe_phase.2", rawList.size() + " Recipes");
                List<SearchableRecipe> tempList = new ArrayList<>(rawList);
                if (ModCompatHelper.isCreateLoaded() || ModCompatHelper.isCreateNewAgeLoaded() || ModCompatHelper.isCreateAdditionsLoaded()) {
                    tempList.addAll(com.gtceu.calcboard.compat.create.CreateModAdapter.getVirtualKineticSearchRecipes());
                }

                synchronized (GLOBAL_RECIPES) {
                    GLOBAL_RECIPES.clear();
                    GLOBAL_RECIPES.addAll(tempList);
                    GLOBAL_CACHED = true;
                    GLOBAL_VERSION++;
                }

                CACHING_PROGRESS = new RecipeLoadingProgress(3, 4, "gui.gtcalcboard.loading_recipe_phase.3", "Baking Machine Capabilities Matrix");
                RecipeFilterDialog.updateDiscoveredCategories(RecipeSearchEngine.discoverCategories(tempList));
                try {
                    com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().bake(null);
                } catch (Throwable ignored) {}
                CACHING_PROGRESS = new RecipeLoadingProgress(4, 4, "gui.gtcalcboard.loading_recipe_phase.3", "Completed");

                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                        "[GTCalcBoard] [RecipeSearch] Indexed {} {} recipes in {}ms in background.",
                        tempList.size(), adapter.getViewerId().toUpperCase(Locale.ROOT), elapsedMs
                );

                try {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new com.gtceu.calcboard.api.event.CatalogLifecycleEvent.RecipesReady(tempList.size(), elapsedMs)
                    );
                } catch (Throwable ignored) {}

                List<Runnable> callbacks;
                synchronized (ON_COMPLETE_CALLBACKS) {
                    callbacks = new ArrayList<>(ON_COMPLETE_CALLBACKS);
                    ON_COMPLETE_CALLBACKS.clear();
                }
                for (Runnable cb : callbacks) {
                    Minecraft.getInstance().execute(cb);
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
        this.switchTargetNode = null;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] RecipeSearchDialog opened.");
        setVisible(true, false, 0, 0);
    }

    public void openForSwitch(RecipeNode targetNode) {
        if (targetNode == null) return;
        this.switchTargetNode = targetNode;
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = false;
        this.visible = true;
        this.scrollOffset = 0;
        this.stickyHoverRecipe = null;
        this.lastObservedGlobalVersion = GLOBAL_VERSION;

        String prefill = "";
        if (targetNode.getRecipeCategoryId() != null) {
            prefill = "[" + targetNode.getRecipeCategoryId().getPath() + "] ";
        } else if (targetNode.getMachineIcon() != null) {
            prefill = "[" + targetNode.getMachineIcon().getPath() + "] ";
        }
        searchBox.setValue(prefill);
        searchBox.setFocused(true);
        ensureGlobalRecipesCachedAsync(() -> {
            if (this.visible) {
                updateSearchResults(searchBox.getValue());
            }
        });
        updateSearchResultsSynchronously(prefill);
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
            this.lastObservedGlobalVersion = GLOBAL_VERSION;
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
            this.switchTargetNode = null;
            this.currentContextualDefaultRecipeId = null;
            this.stickyHoverRecipe = null;
        }
    }

    public void setVisible(boolean visible) {
        setVisible(visible, false, 0, 0);
    }

    private void onSearchQueryChanged(String query) {
        scrollOffset = 0;
        final int currentVersion = searchVersion.incrementAndGet();

        if (pendingSearchTask != null) {
            pendingSearchTask.cancel(false);
            pendingSearchTask = null;
        }

        if (query == null || query.trim().isEmpty()) {
            updateSearchResultsSynchronously("");
            return;
        }

        pendingSearchTask = SEARCH_SCHEDULER.schedule(() -> {
            try {
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
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static List<SearchableRecipe> getTutorialDummyRecipes() {
        List<SearchableRecipe> list = new ArrayList<>();

        // 1. Steam Turbine (Tutorial)
        RecipeNode turbine = RecipeNode.create("Steam Turbine (Tutorial)", 20.0, 64.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0, 1.0));
        list.add(createTutorialSearchableRecipe(turbine, "gtceu", "steam_turbine", "Steam Turbine"));

        // 2. Boiler (Tutorial)
        RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 0.0, GTVoltageTier.LV);
        boiler.setEnergyType(com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF);
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        list.add(createTutorialSearchableRecipe(boiler, "gtceu", "boiler", "Boiler"));

        // 3. Steam Engine (Tutorial)
        RecipeNode engine = RecipeNode.create("Steam Engine (Tutorial)", 20.0, 32.0, GTVoltageTier.LV);
        engine.setGenerator(true);
        engine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 200.0, 1.0));
        list.add(createTutorialSearchableRecipe(engine, "gtceu", "steam_engine", "Steam Engine"));

        return list;
    }

    private static SearchableRecipe createTutorialSearchableRecipe(
            RecipeNode template,
            String modId,
            String categoryId,
            String categoryName
    ) {
        List<ResourceLocation> outputIds = new ArrayList<>();
        List<String> outputNames = new ArrayList<>();
        List<ResourceLocation> inputIds = new ArrayList<>();
        List<String> inputNames = new ArrayList<>();
        StringBuilder outSb = new StringBuilder();
        StringBuilder inSb = new StringBuilder();

        for (IngredientStack in : template.getInputs()) {
            if (in.getDisplayName() != null) {
                inputNames.add(in.getDisplayName());
                inSb.append(in.getDisplayName().toLowerCase(Locale.ROOT)).append(" ");
            }
            if (in.getId() != null) {
                inputIds.add(in.getId());
                inSb.append(in.getId().toString().toLowerCase(Locale.ROOT)).append(" ")
                    .append(in.getId().getPath().toLowerCase(Locale.ROOT)).append(" ");
            }
        }

        for (IngredientStack out : template.getOutputs()) {
            if (out.getDisplayName() != null) {
                outputNames.add(out.getDisplayName());
                outSb.append(out.getDisplayName().toLowerCase(Locale.ROOT)).append(" ");
            }
            if (out.getId() != null) {
                outputIds.add(out.getId());
                outSb.append(out.getId().toString().toLowerCase(Locale.ROOT)).append(" ")
                    .append(out.getId().getPath().toLowerCase(Locale.ROOT)).append(" ");
            }
        }

        ResourceLocation[] inArr = inputIds.isEmpty() ? null : inputIds.toArray(new ResourceLocation[0]);
        ResourceLocation[] outArr = outputIds.isEmpty() ? null : outputIds.toArray(new ResourceLocation[0]);
        String[] inNamesArr = inputNames.isEmpty() ? null : inputNames.toArray(new String[0]);
        String[] outNamesArr = outputNames.isEmpty() ? null : outputNames.toArray(new String[0]);
        ResourceLocation recipeId = template.getRecipeCategoryId() != null ? template.getRecipeCategoryId() : template.getMachineIcon();

        return new SearchableRecipe(
                template,
                recipeId,
                template.getName(),
                modId.intern(),
                categoryId.intern(),
                categoryName.intern(),
                inSb.toString().trim(),
                outSb.toString().trim(),
                inArr,
                outArr,
                inNamesArr,
                outNamesArr,
                true
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
        this.currentParsedQuery = parsedQuery;

        boolean isTutorial = TutorialManager.getInstance().isActive();
        List<SearchableRecipe> sourceList;
        if (isTutorial) {
            sourceList = getTutorialDummyRecipes();
        } else {
            if (GLOBAL_RECIPES.isEmpty()) {
                ensureGlobalRecipesCachedAsync(() -> {
                    if (this.visible) {
                        updateSearchResults(searchBox.getValue());
                    }
                });
                return Collections.emptyList();
            }
            sourceList = GLOBAL_RECIPES;
        }

        record ScoredRecipe(SearchableRecipe recipe, int score, int contextualScore, boolean isDefault, boolean isFavorite) {}

        boolean hasContext = (contextualWireTarget != null && contextualWireTarget.sourceStack != null);
        boolean targetIsFluid = hasContext && contextualWireTarget.sourceStack.isFluid();
        String targetIdPath = (hasContext && contextualWireTarget.sourceStack.getId() != null)
                ? contextualWireTarget.sourceStack.getId().getPath().toLowerCase(Locale.ROOT) : null;
        String targetFullId = (hasContext && contextualWireTarget.sourceStack.getId() != null)
                ? contextualWireTarget.sourceStack.getId().toString().toLowerCase(Locale.ROOT) : null;
        String targetName = (hasContext && contextualWireTarget.sourceStack.getDisplayName() != null)
                ? contextualWireTarget.sourceStack.getDisplayName().toLowerCase(Locale.ROOT) : null;

        RecipeFilterConfig filterConfig = RecipeFilterConfig.getInstance();
        Set<ResourceLocation> allFavoriteIds = getFavoriteRecipeIds();
        Set<ResourceLocation> favoriteIds = showFavoritesOnly ? allFavoriteIds : null;
        boolean hasQuery = (query != null && !query.trim().isEmpty());

        ResourceLocation contextualDefaultRecipeId = null;
        if (hasContext && contextualWireTarget.sourceStack != null && ModCompatHelper.isEmiLoaded()) {
            contextualDefaultRecipeId = com.gtceu.calcboard.integration.emi.EmiSearchHelper.resolveContextualDefaultRecipeId(contextualWireTarget.sourceStack, targetIsFluid);
        }
        this.currentContextualDefaultRecipeId = contextualDefaultRecipeId;
        final ResourceLocation finalContextualDefaultId = contextualDefaultRecipeId;

        List<ScoredRecipe> candidateList = sourceList.parallelStream()
                .filter(sr -> {
                    if (showFavoritesOnly) {
                        ResourceLocation rId = sr.recipeId();
                        if (rId == null || favoriteIds == null || !favoriteIds.contains(rId)) {
                            return false;
                        }
                    }
                    if (filterConfig.isCategoryExcluded(sr.categoryId())) {
                        return false;
                    }
                    if (!filterConfig.isIncludeUnsupported() && !sr.isSupported()) {
                        return false;
                    }
                    return RecipeSearchEngine.matches(sr, parsedQuery);
                })
                .map(sr -> {
                    int contextualScore = 0;
                    if (hasContext) {
                        ResourceLocation targetId = contextualWireTarget.sourceStack.getId();
                        boolean isStress = contextualWireTarget.sourceStack.isStressUnit();
                        if (!contextualWireTarget.sourceIsInput) {
                            // Looking for CONSUMERS (recipes with matching input)
                            if (targetId != null && sr.hasExactInput(targetId)) {
                                contextualScore = 100000;
                            } else if (targetIdPath != null && sr.hasInputPath(targetIdPath)) {
                                contextualScore = 80000;
                            } else if (targetName != null && sr.hasExactInputName(targetName)) {
                                contextualScore = 50000;
                            } else if (isStress && (sr.inputIndex().contains("stress_units") || sr.inputIndex().contains("create:stress_units") || (ModCompatHelper.isEmiLoaded() && !com.gtceu.calcboard.integration.emi.EmiSearchHelper.isKineticGenerator(sr.recipe())))) {
                                contextualScore = 90000;
                            }
                        } else {
                            // Looking for PRODUCERS (recipes with matching output)
                            if (targetId != null && sr.hasExactOutput(targetId)) {
                                contextualScore = 100000;
                            } else if (targetIdPath != null && sr.hasOutputPath(targetIdPath)) {
                                contextualScore = 80000;
                            } else if (targetName != null && sr.hasExactOutputName(targetName)) {
                                contextualScore = 50000;
                            } else if (isStress && (sr.outputIndex().contains("stress_units") || sr.outputIndex().contains("create:stress_units") || (ModCompatHelper.isEmiLoaded() && com.gtceu.calcboard.integration.emi.EmiSearchHelper.isKineticGenerator(sr.recipe())))) {
                                contextualScore = 90000;
                            }
                        }
                    }

                    int totalScore = contextualScore;
                    if (hasQuery) {
                        totalScore += RecipeSearchEngine.calculateRelevanceScore(sr, parsedQuery);
                    } else {
                        // Default recommendations when search query is empty
                        String cat = sr.categoryId().toLowerCase(Locale.ROOT);
                        if (!cat.equals("crafting") && !cat.equals("minecraft:crafting")) {
                            totalScore += 100;
                        }
                        if (cat.contains("turbine") || cat.contains("generator") || cat.contains("boiler")) {
                            totalScore += 50;
                        }
                    }

                    boolean isFav = false;
                    ResourceLocation rId = sr.recipeId();
                    if (rId != null && allFavoriteIds != null && allFavoriteIds.contains(rId)) {
                        isFav = true;
                    }
                    return new ScoredRecipe(sr, totalScore, contextualScore, false, isFav);
                })
                .filter(sr -> !hasContext || sr.contextualScore() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(200)
                .toList();

        // 2. Sequential thread-safe resolution of Default Recipe on the top 200 candidates only
        List<ScoredRecipe> resolvedMatches = new ArrayList<>(candidateList.size());
        for (ScoredRecipe sr : candidateList) {
            boolean isDefault = false;
            if (ModCompatHelper.isEmiLoaded()) {
                isDefault = com.gtceu.calcboard.integration.emi.EmiSearchHelper.isDefaultRecipe(sr.recipe().recipe(), hasContext, finalContextualDefaultId, hasQuery, parsedQuery);
            }
            resolvedMatches.add(new ScoredRecipe(sr.recipe(), sr.score(), sr.contextualScore(), isDefault, sr.isFavorite()));
        }

        resolvedMatches.sort((a, b) -> {
            int cmp = Integer.compare(b.score(), a.score());
            if (cmp != 0) return cmp;
            if (a.isDefault() != b.isDefault()) {
                return a.isDefault() ? -1 : 1;
            }
            if (a.isFavorite() != b.isFavorite()) {
                return a.isFavorite() ? -1 : 1;
            }
            return a.recipe().displayName().compareTo(b.recipe().displayName());
        });

        return resolvedMatches.stream()
                .limit(150)
                .map(ScoredRecipe::recipe)
                .toList();
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        // Auto-retry trigger if EMI was not yet ready when opened
        if (!GLOBAL_CACHED && !IS_CACHING) {
            ensureGlobalRecipesCachedAsync(() -> {
                if (this.visible) {
                    updateSearchResults(searchBox.getValue());
                }
            });
        }

        // Auto-refresh when background indexing completes or global recipe cache is updated
        long currentGlobalVer = GLOBAL_VERSION;
        if (currentGlobalVer != lastObservedGlobalVersion) {
            lastObservedGlobalVersion = currentGlobalVer;
            updateSearchResults(searchBox.getValue());
        }

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Font font = Minecraft.getInstance().font;
        int dialogW = Math.min(380, screenWidth - 24);
        int dialogH = Math.min(280, screenHeight - 24);
        int sideW = 104;
        int gap = 6;
        boolean hasSideSpace = screenWidth >= (dialogW + sideW + gap + 16);
        int totalW = hasSideSpace ? (dialogW + sideW + gap) : dialogW;
        int startX = (screenWidth - totalW) / 2;
        int sideX = hasSideSpace ? startX : -1000;
        int x = hasSideSpace ? (startX + sideW + gap) : startX;
        int y = (screenHeight - dialogH) / 2;

        // Solid Dark Backdrop
        graphics.fill(0, 0, screenWidth, screenHeight, 0xCC000000);

        // Render Left Prefix Guide Side Panel
        if (hasSideSpace) {
            renderPrefixSidePanel(graphics, font, sideX, y, sideW, dialogH, mouseX, mouseY);
        }

        graphics.fill(x, y, x + dialogW, y + dialogH, 0xFF1E222B);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4A90E2);

        // Header
        graphics.fill(x, y, x + dialogW, y + 24, 0xFF282E3B);
        String headerTitle;
        if (switchTargetNode != null) {
            headerTitle = "§e🔄 " + Component.translatable("gui.gtcalcboard.switch_recipe.title", switchTargetNode.getName()).getString();
        } else if (contextualWireTarget != null) {
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

        // Search Input Box, Favorites Toggle, Help & Filter Buttons
        int topBtnW = 20;
        int topBtnH = 16;
        int filterBtnX = x + dialogW - 12 - topBtnW;
        int favBtnX = filterBtnX - topBtnW - 3;
        int helpBtnX = favBtnX - topBtnW - 3;
        int searchBoxW = dialogW - 24 - (topBtnW * 3) - 9;

        searchBox.setX(x + 12);
        searchBox.setY(y + 30);
        searchBox.setWidth(searchBoxW);
        searchBox.render(graphics, mouseX, mouseY, 0);

        // Help / Search Syntax Guide Button [?]
        boolean helpHover = mouseX >= helpBtnX && mouseX <= helpBtnX + topBtnW && mouseY >= y + 30 && mouseY <= y + 30 + topBtnH;
        graphics.fill(helpBtnX, y + 30, helpBtnX + topBtnW, y + 30 + topBtnH, helpHover ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(helpBtnX, y + 30, topBtnW, topBtnH, helpHover ? 0xFFF59E0B : 0xFF475569);
        graphics.drawCenteredString(font, "?", helpBtnX + topBtnW / 2, y + 34, helpHover ? 0xFFFBBF24 : 0xFF94A3B8);

        if (helpHover && !filterDialog.isVisible()) {
            String raw = Component.translatable("gui.gtcalcboard.search.help_tooltip").getString();
            if (raw.contains("\n")) {
                List<Component> lines = java.util.Arrays.stream(raw.split("\n"))
                        .<Component>map(Component::literal)
                        .toList();
                graphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, Component.translatable("gui.gtcalcboard.search.help_tooltip"), mouseX, mouseY);
            }
        }

        // Favorites Button [⭐]
        boolean favHover = mouseX >= favBtnX && mouseX <= favBtnX + topBtnW && mouseY >= y + 30 && mouseY <= y + 30 + topBtnH;
        graphics.fill(favBtnX, y + 30, favBtnX + topBtnW, y + 30 + topBtnH, favHover ? 0xFF3D3A20 : (showFavoritesOnly ? 0xFF353018 : 0xFF1E293B));
        graphics.renderOutline(favBtnX, y + 30, topBtnW, topBtnH, showFavoritesOnly ? 0xFFFFD700 : (favHover ? 0xFF94A3B8 : 0xFF475569));
        graphics.drawCenteredString(font, showFavoritesOnly ? "⭐" : "☆", favBtnX + topBtnW / 2, y + 34, showFavoritesOnly ? 0xFFFFD700 : 0xFF94A3B8);

        if (favHover && !filterDialog.isVisible()) {
            graphics.renderTooltip(font, Component.translatable("gui.gtcalcboard.filter.favorites_tooltip"), mouseX, mouseY);
        }

        // Category Filter Button [⚙]
        boolean filterHover = mouseX >= filterBtnX && mouseX <= filterBtnX + topBtnW && mouseY >= y + 30 && mouseY <= y + 30 + topBtnH;
        boolean filterActive = !RecipeFilterConfig.getInstance().getExcludedCategories().isEmpty();
        graphics.fill(filterBtnX, y + 30, filterBtnX + topBtnW, y + 30 + topBtnH, filterHover ? 0xFF334155 : (filterActive ? 0xFF2A3649 : 0xFF1E293B));
        graphics.renderOutline(filterBtnX, y + 30, topBtnW, topBtnH, filterActive ? 0xFF38BDF8 : 0xFF475569);
        graphics.drawCenteredString(font, "⚙", filterBtnX + topBtnW / 2, y + 34, filterActive ? 0xFF38BDF8 : 0xFF94A3B8);

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


        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        SearchableRecipe newlyHoveredRecipe = null;
        int newlyHoveredRowY = 0;

        if (filteredRecipes.isEmpty()) {
            boolean isLoading = GLOBAL_RECIPES.isEmpty() || !GLOBAL_CACHED;
            long animDots = (System.currentTimeMillis() / 400L) % 4;
            String dots = ".".repeat((int) animDots);

            if (isLoading) {
                var progress = CACHING_PROGRESS;
                String phaseText = Component.translatable(progress.phaseKey()).getString();
                String phaseTitle = "§e⏳ " + Component.translatable("gui.gtcalcboard.loading_recipes_phase",
                        progress.currentPhase(), progress.totalPhases(), phaseText).getString() + dots;

                int centerY = listY + (listH / 2);
                graphics.drawCenteredString(font, phaseTitle, listX + listW / 2, centerY - 20, 0xFFE0C040);

                // Progress Bar
                int barW = Math.min(220, listW - 40);
                int barH = 6;
                int barX = (listX + listW / 2) - (barW / 2);
                int barY = centerY - 4;

                graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222733);
                graphics.renderOutline(barX, barY, barW, barH, 0xFF3D4659);

                float fillRatio = Math.max(0.15f, (float) progress.currentPhase() / (float) progress.totalPhases());
                int fillW = (int) (barW * fillRatio);
                graphics.fill(barX + 1, barY + 1, barX + fillW - 1, barY + barH - 1, 0xFF4A90E2);

                if (progress.detail() != null && !progress.detail().isEmpty()) {
                    graphics.drawCenteredString(font, "§7" + progress.detail(), listX + listW / 2, centerY + 8, 0xFFAAAAAA);
                }
                String hint = "§8" + Component.translatable("gui.gtcalcboard.loading_recipe_phase_hint").getString();
                graphics.drawCenteredString(font, hint, listX + listW / 2, centerY + 20, 0xFF666666);
            } else {
                String emptyMsg = "§7" + Component.translatable("gui.gtcalcboard.no_matching_recipes").getString();
                graphics.drawCenteredString(font, emptyMsg, listX + listW / 2, listY + listH / 2 - 4, 0xFF888888);
            }
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

                boolean isDefault = false;
                if (ModCompatHelper.isEmiLoaded()) {
                    isDefault = com.gtceu.calcboard.integration.emi.EmiSearchHelper.isDefaultRecipe(sr.recipe(), this.contextualWireTarget != null, currentContextualDefaultRecipeId, searchBox != null && !searchBox.getValue().trim().isEmpty(), searchBox != null ? searchBox.getValue().trim() : "");
                }

                boolean isRowSelectedOrHovered = rowHover || (stickyHoverRecipe == sr);
                if (isRowSelectedOrHovered) {
                    graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, 0xFF2A3649);
                    graphics.renderOutline(listX + 1, rowY + 1, listW - 2, ROW_HEIGHT - 2, 0xFFFFD700);
                } else if (isDefault) {
                    graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, 0xFF1B2436);
                    graphics.renderOutline(listX + 1, rowY + 1, listW - 2, ROW_HEIGHT - 2, 0xFF38BDF8);
                } else {
                    graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, (i % 2 == 0 ? 0xFF1A1E26 : 0xFF161A21));
                }

                // Identify matched output for promotion
                RecipeSearchEngine.MatchedOutputResult matched = RecipeSearchEngine.findMatchedOutput(
                        sr,
                        currentParsedQuery,
                        (contextualWireTarget != null && contextualWireTarget.sourceStack != null) ? contextualWireTarget.sourceStack.getId() : null,
                        (contextualWireTarget != null && contextualWireTarget.sourceStack != null) ? contextualWireTarget.sourceStack.getDisplayName() : null
                );
                ResourceLocation matchedId = (matched != null) ? matched.id() : null;
                String matchedName = (matched != null) ? matched.name() : null;

                // Left: Display First Input Stack Icon ➔ Output Stack Icons (with matched output promoted)
                int renderedIconWidth = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter()
                        .renderRowIcon(graphics, font, sr.recipe(), listX, rowY, matchedId, matchedName);

                String rName = sr.displayName();
                if (matchedName != null && !matchedName.isEmpty() && !matchedName.equalsIgnoreCase(rName)) {
                    rName = matchedName + " §7(" + rName + ")";
                }

                String catText = !sr.categoryName().isEmpty() ? "§7[" + sr.categoryName() + "§7] " : (!sr.categoryId().isEmpty() ? "§7[" + sr.categoryId() + "§7] " : "");
                String star = isDefault ? "§6★ " : "";
                String genericBadge = !sr.isSupported() ? "§6[" + Component.translatable("gui.gtcalcboard.search.badge.unsupported").getString() + "] " : "";
                String fullRowText = star + genericBadge + catText + (isDefault ? "§b" : "§f") + rName;

                int textStartX = listX + Math.max(58, renderedIconWidth + 4);
                int maxTextW = (btnX - 24) - textStartX;
                if (maxTextW > 20) {
                    graphics.drawString(font, font.plainSubstrByWidth(fullRowText, maxTextW), textStartX, rowY + 12, isDefault ? 0xFF38BDF8 : 0xFFFFFFFF, false);
                }

                // Star favorite indicator / toggle next to Add button
                boolean isFav = isRecipeFavorite(sr.recipe());
                int favStarX = btnX - 20;
                int favStarY = rowY + 7;
                int favStarW = 16;
                int favStarH = 18;
                boolean favStarHover = mouseX >= favStarX && mouseX <= favStarX + favStarW && mouseY >= favStarY && mouseY <= favStarY + favStarH;

                if (isFav || favStarHover || isRowSelectedOrHovered) {
                    graphics.drawString(font, isFav ? "⭐" : "☆", favStarX + 4, favStarY + 5, isFav ? 0xFFFFD700 : (favStarHover ? 0xFFFDE047 : 0xFF64748B), false);
                }

                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0xFF2A6840 : 0xFF1E4D2F);
                graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF359050);
                String btnText = (switchTargetNode != null)
                        ? ("🔄 " + Component.translatable("gui.gtcalcboard.switch_recipe.apply").getString())
                        : ("➕ " + Component.translatable("gui.gtcalcboard.add_btn").getString());
                graphics.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);
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

    private void renderPrefixSidePanel(GuiGraphics graphics, Font font, int sideX, int y, int sideW, int dialogH, int mouseX, int mouseY) {
        graphics.fill(sideX, y, sideX + sideW, y + dialogH, 0xFF1E222B);
        graphics.renderOutline(sideX, y, sideW, dialogH, 0xFF4A90E2);

        // Header
        graphics.fill(sideX, y, sideX + sideW, y + 24, 0xFF282E3B);
        String headerTitle = "§e⌨ " + Component.translatable("gui.gtcalcboard.search.prefix_guide.title").getString();
        graphics.drawString(font, font.plainSubstrByWidth(headerTitle, sideW - 10), sideX + 6, y + 8, 0xFFFFFFFF, false);

        int itemW = sideW - 12;
        int itemH = 24;
        int itemSpacing = 28;
        PrefixGuideItem hoveredItem = null;

        for (int i = 0; i < PREFIX_ITEMS.size(); i++) {
            PrefixGuideItem item = PREFIX_ITEMS.get(i);
            int itemX = sideX + 6;
            int itemY = y + 28 + i * itemSpacing;

            boolean hover = mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= itemY && mouseY <= itemY + itemH;
            if (hover) {
                hoveredItem = item;
            }

            int bg = hover ? item.hoverBg() : 0xFF151922;
            int border = hover ? item.color() : 0xFF334155;

            graphics.fill(itemX, itemY, itemX + itemW, itemY + itemH, bg);
            graphics.renderOutline(itemX, itemY, itemW, itemH, border);

            // Colored Prefix Badge
            graphics.fill(itemX + 2, itemY + 2, itemX + 18, itemY + itemH - 2, 0xFF0F172A);
            graphics.drawCenteredString(font, item.prefix(), itemX + 10, itemY + 8, item.color());

            // Label text
            String label = Component.translatable(item.labelKey()).getString();
            if (label.startsWith(item.prefix())) {
                label = label.substring(item.prefix().length()).trim();
            }
            graphics.drawString(font, font.plainSubstrByWidth(label, itemW - 22), itemX + 22, itemY + 8, hover ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }

        // Render Tooltip for hovered item
        if (hoveredItem != null && !filterDialog.isVisible()) {
            List<Component> tooltipLines = new ArrayList<>();
            tooltipLines.add(Component.literal("§6§l" + Component.translatable(hoveredItem.labelKey()).getString()));
            tooltipLines.add(Component.literal("§7" + Component.translatable(hoveredItem.descKey()).getString()));
            tooltipLines.add(Component.literal("§8----------------------"));
            tooltipLines.add(Component.literal("§e💡 " + Component.translatable("gui.gtcalcboard.search.prefix.click_hint").getString()));
            graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        if (filterDialog.isVisible()) {
            return filterDialog.mouseClicked(mouseX, mouseY, button, screenWidth, screenHeight);
        }

        int dialogW = Math.min(380, screenWidth - 24);
        int dialogH = Math.min(280, screenHeight - 24);
        int sideW = 104;
        int gap = 6;
        boolean hasSideSpace = screenWidth >= (dialogW + sideW + gap + 16);
        int totalW = hasSideSpace ? (dialogW + sideW + gap) : dialogW;
        int startX = (screenWidth - totalW) / 2;
        int sideX = hasSideSpace ? startX : -1000;
        int x = hasSideSpace ? (startX + sideW + gap) : startX;
        int y = (screenHeight - dialogH) / 2;

        // Side panel click handling
        if (hasSideSpace && mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= y && mouseY <= y + dialogH) {
            int itemW = sideW - 12;
            int itemH = 24;
            int itemSpacing = 28;
            for (int i = 0; i < PREFIX_ITEMS.size(); i++) {
                int itemY = y + 28 + i * itemSpacing;
                if (mouseX >= sideX + 6 && mouseX <= sideX + 6 + itemW && mouseY >= itemY && mouseY <= itemY + itemH) {
                    PrefixGuideItem item = PREFIX_ITEMS.get(i);
                    String current = searchBox.getValue();
                    if (!current.isEmpty() && !current.endsWith(" ")) {
                        current += " ";
                    }
                    searchBox.setValue(current + item.prefix());
                    searchBox.setFocused(true);
                    searchBox.setCursorPosition(searchBox.getValue().length());
                    Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
                    );
                    return true;
                }
            }
            return true;
        }

        // If mouse is inside the sticky preview card on the right
        if (stickyHoverRecipe != null) {
            int[] bounds = RecipeHoverPreviewRenderer.calculatePreviewBounds(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, screenWidth, screenHeight);
            if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                var hoveredIngredient = RecipeHoverPreviewRenderer.getHoveredIngredient(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, (int) mouseX, (int) mouseY, screenWidth, screenHeight);
                if (hoveredIngredient != null) {
                    if (com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().handleHoveredIngredientClick(hoveredIngredient, searchBox)) {
                        return true;
                    }
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

        // Top action buttons
        int btnW = 20;
        int btnH = 16;
        int filterBtnX = x + dialogW - 12 - btnW;
        int favBtnX = filterBtnX - btnW - 3;
        int helpBtnX = favBtnX - btnW - 3;
        int btnY = y + 30;

        // Help button [?] -> Open Chapter 2 of Guidebook
        if (mouseX >= helpBtnX && mouseX <= helpBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
            );
            setVisible(false);
            if (parent != null && parent.getGuideDialog() != null) {
                parent.getGuideDialog().openCategory(GuideDialog.GuideCategory.SEARCH);
            }
            return true;
        }

        // Favorites toggle button [⭐]
        if (mouseX >= favBtnX && mouseX <= favBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            showFavoritesOnly = !showFavoritesOnly;
            updateSearchResultsSynchronously(searchBox.getValue());
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), showFavoritesOnly ? 1.4F : 1.0F)
            );
            return true;
        }

        // Filter button [⚙]
        if (mouseX >= filterBtnX && mouseX <= filterBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
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
                int rowBtnW = 44;
                int btnX = listX + listW - rowBtnW - 6;
                int favStarX = btnX - 20;
                int favStarW = 16;
                int favStarY = rowY + 7;
                int favStarH = 18;

                // 1. Star button click or Right Click -> Toggle Favorite
                boolean isStarClicked = (button == 0 && mouseX >= favStarX && mouseX <= favStarX + favStarW && mouseY >= favStarY && mouseY <= favStarY + favStarH);
                if (isStarClicked || button == 1) {
                    if (sr.recipe() != null) {
                        toggleFavoriteRecipe(sr.recipe());
                        updateSearchResultsSynchronously(searchBox.getValue());
                        Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
                        );
                        return true;
                    }
                }

                // 2. Left click -> Switch recipe OR Add recipe to board
                if (button == 0) {
                    if (switchTargetNode != null) {
                        RecipeNode template = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().convertToNode(sr.recipe());
                        if (template != null && parent != null) {
                            parent.switchNodeRecipe(switchTargetNode, template);
                            Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F)
                            );
                        }
                        setVisible(false);
                        return true;
                    }
                    addRecipeAt(sr, screenWidth, screenHeight);
                    return true;
                }
            }
        }

        return true;
    }

    public void addRecipeAt(SearchableRecipe sr, int screenWidth, int screenHeight) {
        if (sr == null) return;
        double spawnX, spawnY;
        if (contextualWireTarget != null) {
            if (!contextualWireTarget.sourceIsInput) {
                spawnX = contextualWireTarget.canvasX;
                spawnY = contextualWireTarget.canvasY - 30;
            } else {
                spawnX = contextualWireTarget.canvasX - 245;
                spawnY = contextualWireTarget.canvasY - 30;
            }
        } else if (hasTargetSpawnPos) {
            spawnX = targetSpawnCanvasX - 80;
            spawnY = targetSpawnCanvasY - 30;
        } else {
            double[] center = BoardScreen.getNextNodeCenterPosition(screenWidth, screenHeight);
            spawnX = center[0];
            spawnY = center[1];
        }

        com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster cluster = null;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            cluster = com.gtceu.calcboard.integration.emi.EmiStepRecipeDetector.tryDetectAndBuild(sr.recipe(), null, spawnX, spawnY);
        }

        if (cluster != null && !cluster.nodes().isEmpty()) {
            for (RecipeNode n : cluster.nodes()) {
                parent.addNode(n);
            }
            if (cluster.frame() != null) {
                parent.getGraph().addFrame(cluster.frame());
            }
            for (FlowGraph.ConnectionEdge edge : cluster.internalEdges()) {
                parent.getGraph().addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
            }
            if (contextualWireTarget != null) {
                RecipeNode targetConnectNode = !contextualWireTarget.sourceIsInput ? cluster.nodes().get(0) : cluster.nodes().get(cluster.nodes().size() - 1);
                if (!contextualWireTarget.sourceIsInput) {
                    connectContextualForwardWire(targetConnectNode, contextualWireTarget.sourceNode, contextualWireTarget.sourceStack);
                } else {
                    connectContextualReverseWire(targetConnectNode, contextualWireTarget.sourceNode, contextualWireTarget.sourceStack);
                }
                contextualWireTarget = null;
                parent.rebuildWidgets();
            }
            parent.markSummaryDirty();
            setVisible(false);
            return;
        }

        RecipeNode node = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().convertToNode(sr.recipe());
        if (node != null) {
            node.setPosX(spawnX);
            node.setPosY(spawnY);

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
            if (in.equals(sourceStack) || in.matchesOrAlternative(sourceStack) || (in.isStressUnit() && sourceStack.isStressUnit())) {
                matchedInIdx = inIdx;
                if (!in.equals(sourceStack) && !in.isStressUnit()) {
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
            if (out.equals(sourceStack) || sourceStack.matchesOrAlternative(out) || (out.isStressUnit() && sourceStack.isStressUnit())) {
                matchedOutIdx = outIdx;
                if (!out.equals(sourceStack) && !out.isStressUnit()) {
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
            if (hoveredIngredient != null) {
                if (com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().handleHoveredIngredientLookup(hoveredIngredient, keyCode == 82)) {
                    return true;
                }
            }
        }
        if (searchBox != null) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (filterDialog.isVisible()) {
            return filterDialog.charTyped(codePoint, modifiers);
        }
        return searchBox.charTyped(codePoint, modifiers);
    }
}




