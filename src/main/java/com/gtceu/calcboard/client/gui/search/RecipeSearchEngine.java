package com.gtceu.calcboard.client.gui.search;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced parametric and boolean recipe search engine supporting:
 * - Multi-token AND (spaces or '&'): 'pyrolyse oil'
 * - OR ('|'): 'oil | creosote'
 * - NOT ('!'): 'pyrolyse !charcoal'
 * - Mod ID prefix ('@'): '@gtceu', '@thermal'
 * - Tag prefix ('#'): '#logs', '#forge:dusts'
 * - Category / Machine prefix ('[' or '%'): '[pyrolyse_oven]'
 * - Quoted phrases ("..."): '"heavy fuel"'
 */
public class RecipeSearchEngine {

    public record CategoryInfo(String displayName, int count) {}

    public record SearchableRecipe(
            Object recipe,
            String displayName,
            String modId,
            String categoryId,
            String categoryName,
            List<String> outputNames,
            List<String> inputNames,
            List<String> outputIds,
            List<String> inputIds,
            List<String> outputFluidIds,
            List<String> inputFluidIds,
            List<String> tags,
            String outputSearchIndex,
            String inputSearchIndex,
            String fullSearchIndex
    ) {}

    public static Map<String, CategoryInfo> discoverCategories(List<SearchableRecipe> allRecipes) {
        Map<String, CategoryInfo> map = new LinkedHashMap<>();
        if (allRecipes == null) return map;

        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> names = new HashMap<>();

        for (SearchableRecipe sr : allRecipes) {
            String catId = sr.categoryId();
            if (catId == null || catId.isEmpty()) continue;

            counts.put(catId, counts.getOrDefault(catId, 0) + 1);
            if (!names.containsKey(catId) || names.get(catId).isEmpty()) {
                String dName = sr.categoryName();
                if (dName == null || dName.isEmpty()) {
                    dName = catId;
                }
                names.put(catId, dName);
            }
        }

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            map.put(e.getKey(), new CategoryInfo(names.getOrDefault(e.getKey(), e.getKey()), e.getValue()));
        }

        return map;
    }

    public enum MatchType {
        GENERAL,
        MOD_ID,      // @mod
        TAG,         // #tag
        CATEGORY     // [category] or %category
    }

    public record QueryTerm(String text, MatchType type, boolean negated) {}

    public record AndGroup(List<QueryTerm> terms) {}

    public record ParsedQuery(List<AndGroup> orGroups, boolean isEmpty) {}

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|\\[([^\\]]*)\\]|(\\S+)");

    public static SearchableRecipe buildIndex(EmiRecipe recipe) {
        if (recipe == null) return null;

        String displayName = extractDisplayName(recipe);
        String modId = "";
        String categoryId = "";
        String categoryName = "";

        if (recipe.getId() != null) {
            modId = recipe.getId().getNamespace().toLowerCase(Locale.ROOT);
        }

        EmiRecipeCategory cat = recipe.getCategory();
        if (cat != null) {
            if (cat.getId() != null) {
                categoryId = cat.getId().getPath().toLowerCase(Locale.ROOT);
                if (modId.isEmpty()) {
                    modId = cat.getId().getNamespace().toLowerCase(Locale.ROOT);
                }
            }
            try {
                if (cat.getName() != null) {
                    categoryName = cat.getName().getString().toLowerCase(Locale.ROOT);
                }
            } catch (Throwable ignored) {}
        }

        List<String> outputNames = new ArrayList<>();
        List<String> outputIds = new ArrayList<>();
        List<String> outputFluidIds = new ArrayList<>();
        StringBuilder outSb = new StringBuilder();

        if (recipe.getOutputs() != null) {
            for (EmiStack out : recipe.getOutputs()) {
                if (out != null) {
                    indexStack(out, outputNames, outputIds, outputFluidIds, outSb);
                }
            }
        }

        List<String> inputNames = new ArrayList<>();
        List<String> inputIds = new ArrayList<>();
        List<String> inputFluidIds = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        StringBuilder inSb = new StringBuilder();

        if (recipe.getInputs() != null) {
            for (EmiIngredient in : recipe.getInputs()) {
                if (in != null && in.getEmiStacks() != null) {
                    for (EmiStack stack : in.getEmiStacks()) {
                        if (stack != null) {
                            indexStack(stack, inputNames, inputIds, inputFluidIds, inSb);
                        }
                    }
                }
            }
        }

        // Index virtual kinetic input for Create machine recipes
        if (cat != null && cat.getId() != null && ("create".equals(cat.getId().getNamespace()) || "createaddition".equals(cat.getId().getNamespace()))) {
            inputIds.add("create:stress_units");
            inputIds.add("stress_units");
            inputNames.add("stress units");
            inSb.append("create:stress_units stress_units stress units ");
        }

        StringBuilder fullSb = new StringBuilder();
        fullSb.append(displayName.toLowerCase(Locale.ROOT)).append(" ");
        if (!modId.isEmpty()) fullSb.append(modId).append(" ");
        if (!categoryId.isEmpty()) fullSb.append(categoryId).append(" ");
        if (!categoryName.isEmpty()) fullSb.append(categoryName).append(" ");
        if (recipe.getId() != null) fullSb.append(recipe.getId().toString().toLowerCase(Locale.ROOT)).append(" ");
        fullSb.append(outSb).append(" ").append(inSb);

        // Index category workstations (e.g. Lapidary Boiler, Electric Blast Furnace, Large Chemical Reactor)
        if (cat != null) {
            try {
                var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                if (rm != null) {
                    var workstations = rm.getWorkstations(cat);
                    if (workstations != null) {
                        for (dev.emi.emi.api.stack.EmiIngredient wsIng : workstations) {
                            if (wsIng != null && wsIng.getEmiStacks() != null) {
                                for (EmiStack wsStack : wsIng.getEmiStacks()) {
                                    if (wsStack != null) {
                                        if (wsStack.getId() != null) {
                                            fullSb.append(" ").append(wsStack.getId().toString().toLowerCase(Locale.ROOT));
                                            fullSb.append(" ").append(wsStack.getId().getPath().toLowerCase(Locale.ROOT));
                                        }
                                        try {
                                            if (wsStack.getName() != null) {
                                                fullSb.append(" ").append(wsStack.getName().getString().toLowerCase(Locale.ROOT));
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return new SearchableRecipe(
                recipe,
                displayName,
                modId,
                categoryId,
                categoryName,
                outputNames,
                inputNames,
                outputIds,
                inputIds,
                outputFluidIds,
                inputFluidIds,
                tags,
                outSb.toString(),
                inSb.toString(),
                fullSb.toString()
        );
    }

    private static void indexStack(EmiStack stack, List<String> names, List<String> ids, List<String> fluidIds, StringBuilder sb) {
        try {
            if (stack.getName() != null) {
                String name = stack.getName().getString().toLowerCase(Locale.ROOT);
                names.add(name);
                sb.append(name).append(" ");
            }
        } catch (Throwable ignored) {}

        if (stack.getId() != null) {
            String fullId = stack.getId().toString().toLowerCase(Locale.ROOT);
            String path = stack.getId().getPath().toLowerCase(Locale.ROOT);
            ids.add(fullId);
            ids.add(path);
            sb.append(fullId).append(" ").append(path).append(" ");

            boolean isFluid = false;
            try {
                Object key = stack.getKey();
                if (key instanceof net.minecraft.world.level.material.Fluid) {
                    isFluid = true;
                } else if (stack instanceof dev.emi.emi.api.stack.FluidEmiStack) {
                    isFluid = true;
                }
            } catch (Throwable ignored) {}

            if (isFluid && fluidIds != null) {
                fluidIds.add(fullId);
                fluidIds.add(path);
            }
        }
    }

    private static String extractDisplayName(EmiRecipe recipe) {
        try {
            if (recipe.getOutputs() != null && !recipe.getOutputs().isEmpty()) {
                var firstOut = recipe.getOutputs().get(0);
                if (firstOut != null && firstOut.getName() != null) {
                    return firstOut.getName().getString();
                }
            }
        } catch (Throwable ignored) {}
        if (recipe.getId() != null) {
            String path = recipe.getId().getPath();
            if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
            return formatName(path);
        }
        return "Recipe";
    }

    private static String formatName(String path) {
        String[] parts = path.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * Parses a user search query into boolean AST (OR groups containing AND terms).
     */
    public static ParsedQuery parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ParsedQuery(Collections.emptyList(), true);
        }

        List<AndGroup> orGroups = new ArrayList<>();
        // Split by '|' for OR conditions
        String[] orParts = query.split("\\|");

        for (String part : orParts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) continue;

            if ((trimmedPart.chars().filter(ch -> ch == '\"').count() % 2) != 0) {
                trimmedPart = trimmedPart + "\"";
            }

            List<QueryTerm> terms = new ArrayList<>();
            Matcher matcher = TOKEN_PATTERN.matcher(trimmedPart);

            while (matcher.find()) {
                String quoted = matcher.group(1);
                String bracketed = matcher.group(2);
                String word = matcher.group(3);

                String token;
                MatchType type = MatchType.GENERAL;
                boolean negated = false;

                if (quoted != null) {
                    token = quoted.trim();
                    type = MatchType.GENERAL;
                } else if (bracketed != null) {
                    token = bracketed.trim();
                    type = MatchType.CATEGORY;
                } else {
                    token = word != null ? word.trim() : "";
                    if (token.isEmpty() || token.equals("&") || token.equals("|")
                            || token.equals("!") || token.equals("@") || token.equals("#") || token.equals("%")
                            || token.equals("[") || token.equals("]")) {
                        continue;
                    }

                    if (token.startsWith("!") && token.length() > 1) {
                        negated = true;
                        token = token.substring(1);
                    }

                    if (token.startsWith("@") && token.length() > 1) {
                        type = MatchType.MOD_ID;
                        token = token.substring(1);
                    } else if (token.startsWith("#") && token.length() > 1) {
                        type = MatchType.TAG;
                        token = token.substring(1);
                    } else if (token.startsWith("[") && token.length() > 1) {
                        type = MatchType.CATEGORY;
                        token = token.substring(1);
                    } else if (token.startsWith("%") && token.length() > 1) {
                        type = MatchType.CATEGORY;
                        token = token.substring(1);
                    }

                    if (token.endsWith("]")) {
                        token = token.substring(0, token.length() - 1);
                    }
                }

                token = token.toLowerCase(Locale.ROOT).trim();
                if (!token.isEmpty()) {
                    terms.add(new QueryTerm(token, type, negated));
                }
            }

            if (!terms.isEmpty()) {
                orGroups.add(new AndGroup(terms));
            }
        }

        return new ParsedQuery(orGroups, orGroups.isEmpty());
    }

    /**
     * Tests if a recipe matches a parsed query.
     */
    public static boolean matches(SearchableRecipe recipe, ParsedQuery query) {
        if (query.isEmpty()) return true;
        if (recipe == null) return false;

        // Any OR group must evaluate to TRUE
        for (AndGroup group : query.orGroups()) {
            if (matchesGroup(recipe, group)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGroup(SearchableRecipe recipe, AndGroup group) {
        // ALL terms in an AND group must evaluate to TRUE
        for (QueryTerm term : group.terms()) {
            boolean termMatch = matchesTerm(recipe, term);
            if (term.negated()) {
                if (termMatch) return false; // Negated term matched -> group fails
            } else {
                if (!termMatch) return false; // Required term did not match -> group fails
            }
        }
        return true;
    }

    public static final Pattern COLOR_PATTERN = Pattern.compile("§[0-9a-fk-or]");

    public static String stripFormatting(String str) {
        if (str == null || str.indexOf('§') == -1) return str;
        return COLOR_PATTERN.matcher(str).replaceAll("");
    }

    private static boolean matchesTerm(SearchableRecipe recipe, QueryTerm term) {
        String text = term.text();
        String textSpaced = text.replace('_', ' ');
        String textUnder = text.replace(' ', '_');

        return switch (term.type()) {
            case MOD_ID -> recipe.modId.contains(text);

            case TAG -> {
                for (String t : recipe.tags) {
                    if (t.contains(text) || t.contains(textUnder)) yield true;
                }
                for (String id : recipe.inputIds) {
                    if (id.contains(text) || id.contains(textUnder)) yield true;
                }
                for (String id : recipe.outputIds) {
                    if (id.contains(text) || id.contains(textUnder)) yield true;
                }
                yield false;
            }

            case CATEGORY -> recipe.categoryId.contains(text)
                    || recipe.categoryId.contains(textUnder)
                    || recipe.categoryName.contains(text)
                    || recipe.categoryName.contains(textSpaced);

            case GENERAL -> recipe.fullSearchIndex.contains(text)
                    || (!textSpaced.equals(text) && recipe.fullSearchIndex.contains(textSpaced))
                    || (!textUnder.equals(text) && recipe.fullSearchIndex.contains(textUnder));
        };
    }

    /**
     * Categorical ranking score:
     * 3: Query matches directly in outputs or display name
     * 2: Query matches in inputs or category
     * 1: General match in full index
     */
    public static int calculateRank(SearchableRecipe recipe, ParsedQuery query) {
        if (query.isEmpty()) return 1;

        int maxScore = 1;
        for (AndGroup group : query.orGroups()) {
            boolean allInOutputs = true;
            boolean allInInputsOrCat = true;

            for (QueryTerm term : group.terms()) {
                if (term.negated()) continue;
                String t = term.text();
                if (!recipe.outputSearchIndex.contains(t) && !recipe.displayName.toLowerCase(Locale.ROOT).contains(t)) {
                    allInOutputs = false;
                }
                if (!recipe.inputSearchIndex.contains(t) && !recipe.categoryName.contains(t) && !recipe.categoryId.contains(t)) {
                    allInInputsOrCat = false;
                }
                if (!allInOutputs && !allInInputsOrCat) {
                    break;
                }
            }

            if (allInOutputs) {
                return 3;
            } else if (allInInputsOrCat) {
                maxScore = Math.max(maxScore, 2);
            }
        }

        return maxScore;
    }

    /**
     * Highly responsive fine-grained relevance score for sorting search results.
     */
    public static int calculateRelevanceScore(SearchableRecipe recipe, ParsedQuery query) {
        if (query.isEmpty()) return 0;

        int score = 0;
        for (AndGroup group : query.orGroups()) {
            StringBuilder groupTextSb = new StringBuilder();
            for (QueryTerm term : group.terms()) {
                if (!term.negated()) {
                    if (groupTextSb.length() > 0) groupTextSb.append(" ");
                    groupTextSb.append(term.text());
                }
            }
            String fullGroupText = groupTextSb.toString().toLowerCase(Locale.ROOT);
            String fullGroupUnder = fullGroupText.replace(' ', '_');

            String dn = recipe.displayName.toLowerCase(Locale.ROOT);
            String cat = recipe.categoryName.toLowerCase(Locale.ROOT);
            String catId = recipe.categoryId.toLowerCase(Locale.ROOT);

            if (!fullGroupText.isEmpty()) {
                if (dn.equals(fullGroupText)) {
                    score += 20000;
                } else if (dn.startsWith(fullGroupText)) {
                    score += 10000;
                } else if (dn.contains(fullGroupText)) {
                    score += 6000;
                }

                if (cat.equals(fullGroupText) || catId.equals(fullGroupText) || catId.equals(fullGroupUnder)) {
                    score += 18000;
                } else if (cat.contains(fullGroupText) || catId.contains(fullGroupText) || catId.contains(fullGroupUnder)) {
                    score += 9000;
                }
            }

            for (QueryTerm term : group.terms()) {
                if (term.negated()) continue;
                String t = term.text();
                String tUnder = t.replace(' ', '_');

                if (term.type() == MatchType.CATEGORY) {
                    if (cat.equals(t) || catId.equals(t) || catId.equals(tUnder)) {
                        score += 20000;
                    } else if (cat.contains(t) || catId.contains(t) || catId.contains(tUnder)) {
                        score += 10000;
                    }
                } else if (term.type() == MatchType.MOD_ID) {
                    if (recipe.modId.equals(t)) {
                        score += 10000;
                    } else if (recipe.modId.contains(t)) {
                        score += 5000;
                    }
                } else if (term.type() == MatchType.TAG) {
                    for (String tag : recipe.tags) {
                        if (tag.equals(t) || tag.equals(tUnder)) {
                            score += 8000;
                            break;
                        } else if (tag.contains(t) || tag.contains(tUnder)) {
                            score += 4000;
                            break;
                        }
                    }
                } else {
                    if (dn.equals(t)) {
                        score += 10000;
                    } else if (dn.startsWith(t)) {
                        score += 5000;
                    } else if (dn.contains(t)) {
                        score += 2500;
                    }

                    if (cat.equals(t) || catId.equals(t) || catId.equals(tUnder)) {
                        score += 4000;
                    } else if (cat.contains(t) || catId.contains(t) || catId.contains(tUnder)) {
                        score += 2000;
                    }

                    if (recipe.outputSearchIndex.contains(t)) {
                        score += 1500;
                    } else if (recipe.inputSearchIndex.contains(t)) {
                        score += 800;
                    } else if (recipe.fullSearchIndex.contains(t)) {
                        score += 100;
                    }
                }
            }
        }

        return score;
    }
}
