package com.gtceu.calcboard.client.gui.search;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog.ContextualWireTarget;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Dedicated query execution engine for recipe search, handling asynchronous
 * debounce scheduling, filter chip matching, contextual scoring, and default recipe resolution.
 */
public final class RecipeSearchQueryEngine {

    private static final ScheduledExecutorService SEARCH_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GTCalcBoard-SearchScheduler");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger searchVersion = new AtomicInteger(0);
    private ScheduledFuture<?> pendingSearchTask = null;
    private ResourceLocation currentContextualDefaultRecipeId = null;
    private ParsedQuery currentParsedQuery = null;

    public RecipeSearchQueryEngine() {}

    public int incrementVersion() {
        return searchVersion.incrementAndGet();
    }

    public int getCurrentVersion() {
        return searchVersion.get();
    }

    public ResourceLocation getCurrentContextualDefaultRecipeId() {
        return currentContextualDefaultRecipeId;
    }

    public ParsedQuery getCurrentParsedQuery() {
        return currentParsedQuery;
    }

    public void cancelPendingSearch() {
        if (pendingSearchTask != null) {
            pendingSearchTask.cancel(false);
            pendingSearchTask = null;
        }
    }

    public void scheduleSearch(String query, ContextualWireTarget contextualTarget, boolean showFavoritesOnly, Consumer<List<SearchableRecipe>> onResults) {
        final int currentVersion = searchVersion.incrementAndGet();
        cancelPendingSearch();

        if (query == null || query.trim().isEmpty()) {
            List<SearchableRecipe> syncResults = computeSearchResults("", contextualTarget, showFavoritesOnly);
            if (onResults != null) {
                onResults.accept(syncResults);
            }
            return;
        }

        pendingSearchTask = SEARCH_SCHEDULER.schedule(() -> {
            try {
                if (currentVersion != searchVersion.get()) {
                    return;
                }

                List<SearchableRecipe> results = computeSearchResults(query, contextualTarget, showFavoritesOnly);
                if (currentVersion == searchVersion.get()) {
                    Minecraft.getInstance().execute(() -> {
                        if (currentVersion == searchVersion.get() && onResults != null) {
                            onResults.accept(results);
                        }
                    });
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 50, TimeUnit.MILLISECONDS);
    }

    public List<SearchableRecipe> computeSearchResults(String query, ContextualWireTarget contextualWireTarget, boolean showFavoritesOnly) {
        ParsedQuery parsedQuery = RecipeSearchEngine.parseQuery(query);
        this.currentParsedQuery = parsedQuery;

        boolean isTutorial = TutorialManager.getInstance().isActive();
        List<SearchableRecipe> sourceList;
        if (isTutorial) {
            sourceList = getTutorialDummyRecipes();
        } else {
            List<SearchableRecipe> globalList = RecipeSearchCacheManager.getGlobalRecipes();
            if (globalList.isEmpty()) {
                RecipeSearchCacheManager.ensureGlobalRecipesCachedAsync(null);
                return Collections.emptyList();
            }
            sourceList = globalList;
        }

        record ScoredRecipe(SearchableRecipe recipe, int score, int contextualScore, boolean isDefault, boolean isFavorite) {}

        boolean hasContext = (contextualWireTarget != null && contextualWireTarget.sourceStack != null);
        boolean targetIsFluid = hasContext && contextualWireTarget.sourceStack.isFluid();
        String targetIdPath = (hasContext && contextualWireTarget.sourceStack.getId() != null)
                ? contextualWireTarget.sourceStack.getId().getPath().toLowerCase(Locale.ROOT) : null;
        String targetName = (hasContext && contextualWireTarget.sourceStack.getDisplayName() != null)
                ? contextualWireTarget.sourceStack.getDisplayName().toLowerCase(Locale.ROOT) : null;

        RecipeFilterConfig filterConfig = RecipeFilterConfig.getInstance();
        Set<ResourceLocation> allFavoriteIds = RecipeSearchCacheManager.getFavoriteRecipeIds();
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
}
