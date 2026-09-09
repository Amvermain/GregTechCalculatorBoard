package com.gtceu.calcboard.client.gui.search;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
 * - Input prefix ('in:', 'input:', '>'): 'in:iron', 'input:steam', '>polyethylene'
 * - Output prefix ('out:', 'output:', '<', '^'): 'out:steel', 'output:oxygen', '<phenol'
 * - Quoted phrases ("..."): '"heavy fuel"', 'in:"heavy fuel"'
 */
public class RecipeSearchEngine {

    public record CategoryInfo(String displayName, int count) {}

    public record MatchedOutputResult(
            ResourceLocation id,
            String name,
            int index
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

    public enum Scope {
        ALL,
        INPUT,
        OUTPUT
    }

    public enum MatchType {
        GENERAL,
        MOD_ID,      // @mod
        TAG,         // #tag
        CATEGORY     // [category] or %category
    }

    public record QueryTerm(String text, MatchType type, Scope scope, boolean negated) {
        public QueryTerm(String text, MatchType type, boolean negated) {
            this(text, type, Scope.ALL, negated);
        }
    }

    public record AndGroup(List<QueryTerm> terms) {}

    public record ParsedQuery(List<AndGroup> orGroups, boolean isEmpty) {}

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(!?(?:in:|input:|out:|output:|>|<|\\^))?\"([^\"]*)\"|\\[([^\\]]*)\\]|(\\S+)");

    public static SearchableRecipe buildIndex(Object recipe) {
        if (recipe == null) return null;
        if (recipe instanceof com.gtceu.calcboard.integration.jei.JeiRecipeWrapper<?> jrw) {
            return com.gtceu.calcboard.integration.jei.JeiRecipeSearchIndexer.buildIndex(jrw, com.gtceu.calcboard.integration.jei.JeiRecipeViewerAdapter.getJeiRuntime());
        }
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded() || isEmiRecipeObject(recipe)) {
            return com.gtceu.calcboard.integration.emi.EmiRecipeSearchIndexer.buildIndex(recipe);
        }
        return null;
    }

    private static boolean isEmiRecipeObject(Object recipe) {
        if (recipe == null) return false;
        String name = recipe.getClass().getName();
        return name.startsWith("dev.emi.emi") || name.startsWith("com.gtceu.calcboard.integration.emi");
    }

    /**
     * Finds a matching recipe that consumes the given inputId within the specified machine category or workstations,
     * prioritizing recipes that share the same secondary inputs/fluids (e.g. Lubricant vs Water) as templateNode.
     */
    public static SearchableRecipe findRecipeForInput(
            RecipeNode templateNode,
            ResourceLocation inputId
    ) {
        if (templateNode == null || inputId == null) return null;
        List<SearchableRecipe> allRecipes = RecipeSearchCacheManager.getGlobalRecipes();
        if (allRecipes == null || allRecipes.isEmpty()) return null;

        ResourceLocation catId = templateNode.getRecipeCategoryId();
        ResourceLocation machineIcon = templateNode.getMachineIcon();
        List<ResourceLocation> workstations = templateNode.getAvailableWorkstations();

        Set<String> targetKeys = new HashSet<>();
        if (catId != null) {
            targetKeys.add(catId.toString().toLowerCase(Locale.ROOT));
            targetKeys.add(catId.getPath().toLowerCase(Locale.ROOT));
        }
        if (machineIcon != null) {
            targetKeys.add(machineIcon.toString().toLowerCase(Locale.ROOT));
            targetKeys.add(machineIcon.getPath().toLowerCase(Locale.ROOT));
        }
        if (workstations != null) {
            for (ResourceLocation ws : workstations) {
                if (ws != null) {
                    targetKeys.add(ws.toString().toLowerCase(Locale.ROOT));
                    targetKeys.add(ws.getPath().toLowerCase(Locale.ROOT));
                }
            }
        }

        Set<ResourceLocation> templateInputIds = new HashSet<>();
        for (IngredientStack in : templateNode.getInputs()) {
            if (in != null && in.getId() != null) {
                templateInputIds.add(in.getId());
            }
        }

        SearchableRecipe bestSr = null;
        int maxScore = -1;

        for (SearchableRecipe sr : allRecipes) {
            if (!sr.isSupported() || !sr.hasExactInput(inputId)) continue;
            if (!matchesCategoryOrWorkstation(sr.categoryId(), targetKeys)) continue;

            int score = calculateSecondaryMatchScore(sr, templateInputIds, inputId);
            if (score > maxScore) {
                maxScore = score;
                bestSr = sr;
            }
        }

        if (bestSr != null) {
            return bestSr;
        }

        String nodeName = templateNode.getName();
        String baseName = nodeName != null && nodeName.contains(" (") ? nodeName.substring(0, nodeName.indexOf(" (")).toLowerCase(Locale.ROOT).trim() : (nodeName != null ? nodeName.toLowerCase(Locale.ROOT).trim() : "");

        for (SearchableRecipe sr : allRecipes) {
            if (!sr.isSupported() || !sr.hasExactInput(inputId)) continue;
            String catName = sr.categoryName();
            if (catName == null || baseName.isEmpty() || !catName.toLowerCase(Locale.ROOT).contains(baseName)) continue;

            int score = calculateSecondaryMatchScore(sr, templateInputIds, inputId);
            if (score > maxScore) {
                maxScore = score;
                bestSr = sr;
            }
        }

        return bestSr;
    }

    private static boolean matchesCategoryOrWorkstation(String srCat, Set<String> targetKeys) {
        if (srCat == null || srCat.isEmpty()) return false;
        String lowerCat = srCat.toLowerCase(Locale.ROOT);
        if (targetKeys.contains(lowerCat)) return true;
        if (lowerCat.contains(":")) {
            String path = lowerCat.substring(lowerCat.indexOf(':') + 1);
            return targetKeys.contains(path);
        }
        return false;
    }

    private static int calculateSecondaryMatchScore(SearchableRecipe sr, Set<ResourceLocation> templateInputIds, ResourceLocation mainInputId) {
        if (sr.inputIds() == null || templateInputIds == null || templateInputIds.isEmpty()) return 0;
        int score = 0;
        for (ResourceLocation inId : sr.inputIds()) {
            if (inId == null || inId.equals(mainInputId)) continue;
            if (templateInputIds.contains(inId)) {
                score += 100;
            }
        }
        return score;
    }

    private record ParsedPrefix(Scope scope, boolean negated) {}

    private static ParsedPrefix parsePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ParsedPrefix(Scope.ALL, false);
        }
        String p = prefix.toLowerCase(Locale.ROOT);
        boolean neg = p.startsWith("!");
        if (neg) p = p.substring(1);

        if (p.equals("in:") || p.equals("input:") || p.equals(">")) {
            return new ParsedPrefix(Scope.INPUT, neg);
        }
        if (p.equals("out:") || p.equals("output:") || p.equals("<") || p.equals("^")) {
            return new ParsedPrefix(Scope.OUTPUT, neg);
        }
        return new ParsedPrefix(Scope.ALL, neg);
    }

    public record StackSearchData(String name, String searchText) {}
    private static final Map<ResourceLocation, StackSearchData> STACK_DATA_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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
                String prefix = matcher.group(1);
                String quoted = matcher.group(2);
                String bracketed = matcher.group(3);
                String word = matcher.group(4);

                String token;
                MatchType type = MatchType.GENERAL;
                Scope scope = Scope.ALL;
                boolean negated = false;

                if (quoted != null) {
                    token = quoted.trim();
                    type = MatchType.GENERAL;
                    ParsedPrefix pp = parsePrefix(prefix);
                    scope = pp.scope();
                    negated = pp.negated();
                } else if (bracketed != null) {
                    token = bracketed.trim();
                    type = MatchType.CATEGORY;
                    scope = Scope.ALL;
                } else {
                    token = word != null ? word.trim() : "";
                    if (token.isEmpty() || token.equals("&") || token.equals("|")
                            || token.equals("!") || token.equals("@") || token.equals("#") || token.equals("%")
                            || token.equals("[") || token.equals("]") || token.equalsIgnoreCase("in:")
                            || token.equalsIgnoreCase("input:") || token.equalsIgnoreCase("out:")
                            || token.equalsIgnoreCase("output:") || token.equals(">") || token.equals("<") || token.equals("^")) {
                        continue;
                    }

                    if (token.startsWith("!") && token.length() > 1) {
                        negated = true;
                        token = token.substring(1);
                    }

                    // Check Input / Output scope prefixes
                    String lower = token.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("in:")) {
                        scope = Scope.INPUT;
                        token = token.substring(3);
                    } else if (lower.startsWith("input:")) {
                        scope = Scope.INPUT;
                        token = token.substring(6);
                    } else if (token.startsWith(">") && token.length() > 1) {
                        scope = Scope.INPUT;
                        token = token.substring(1);
                    } else if (lower.startsWith("out:")) {
                        scope = Scope.OUTPUT;
                        token = token.substring(4);
                    } else if (lower.startsWith("output:")) {
                        scope = Scope.OUTPUT;
                        token = token.substring(7);
                    } else if ((token.startsWith("<") || token.startsWith("^")) && token.length() > 1) {
                        scope = Scope.OUTPUT;
                        token = token.substring(1);
                    }

                    // Check Type prefixes (@, #, [, %)
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
                    terms.add(new QueryTerm(token, type, scope, negated));
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
        String textSpaced = text.replace('_', ' ').trim();
        String textUnder = text.replace(' ', '_').trim();
        String textClean = text.replaceAll("_+$", "").trim();

        return switch (term.scope()) {
            case INPUT -> matchesInput(recipe, term.type(), text, textSpaced, textUnder, textClean);
            case OUTPUT -> matchesOutput(recipe, term.type(), text, textSpaced, textUnder, textClean);
            case ALL -> matchesAll(recipe, term.type(), text, textSpaced, textUnder, textClean);
        };
    }

    private static boolean matchesInput(SearchableRecipe recipe, MatchType type, String text, String textSpaced, String textUnder, String textClean) {
        return switch (type) {
            case MOD_ID -> recipe.inputSearchIndex().startsWith(text + ":") || recipe.inputSearchIndex().contains(" " + text + ":") || recipe.inputSearchIndex().contains(text);
            case TAG -> recipe.inputSearchIndex().contains(text) || recipe.inputSearchIndex().contains(textUnder);
            case CATEGORY -> false;
            case GENERAL -> recipe.inputSearchIndex().contains(text)
                    || (!textSpaced.isEmpty() && recipe.inputSearchIndex().contains(textSpaced))
                    || (!textUnder.isEmpty() && recipe.inputSearchIndex().contains(textUnder))
                    || (!textClean.isEmpty() && recipe.inputSearchIndex().contains(textClean));
        };
    }

    private static boolean matchesOutput(SearchableRecipe recipe, MatchType type, String text, String textSpaced, String textUnder, String textClean) {
        return switch (type) {
            case MOD_ID -> recipe.outputSearchIndex().startsWith(text + ":") || recipe.outputSearchIndex().contains(" " + text + ":") || recipe.outputSearchIndex().contains(text);
            case TAG -> recipe.outputSearchIndex().contains(text) || recipe.outputSearchIndex().contains(textUnder);
            case CATEGORY -> false;
            case GENERAL -> recipe.outputSearchIndex().contains(text)
                    || recipe.displayName().toLowerCase(Locale.ROOT).contains(text)
                    || (!textSpaced.isEmpty() && (recipe.outputSearchIndex().contains(textSpaced) || recipe.displayName().toLowerCase(Locale.ROOT).contains(textSpaced)))
                    || (!textUnder.isEmpty() && (recipe.outputSearchIndex().contains(textUnder) || recipe.displayName().toLowerCase(Locale.ROOT).contains(textUnder)))
                    || (!textClean.isEmpty() && (recipe.outputSearchIndex().contains(textClean) || recipe.displayName().toLowerCase(Locale.ROOT).contains(textClean)));
        };
    }

    private static boolean matchesAll(SearchableRecipe recipe, MatchType type, String text, String textSpaced, String textUnder, String textClean) {
        return switch (type) {
            case MOD_ID -> recipe.modId().contains(text) || recipe.inputSearchIndex().contains(text) || recipe.outputSearchIndex().contains(text);
            case TAG -> recipe.inputSearchIndex().contains(text) || recipe.inputSearchIndex().contains(textUnder) || recipe.outputSearchIndex().contains(text) || recipe.outputSearchIndex().contains(textUnder);
            case CATEGORY -> recipe.categoryId().contains(text)
                    || recipe.categoryId().contains(textUnder)
                    || recipe.categoryName().contains(text)
                    || recipe.categoryName().contains(textSpaced);
            case GENERAL -> {
                String dNameLower = recipe.displayName().toLowerCase(Locale.ROOT);
                yield dNameLower.contains(text)
                        || recipe.outputSearchIndex().contains(text)
                        || recipe.inputSearchIndex().contains(text)
                        || recipe.categoryName().contains(text)
                        || recipe.categoryId().contains(text)
                        || recipe.modId().contains(text)
                        || (!textSpaced.isEmpty() && (dNameLower.contains(textSpaced) || recipe.outputSearchIndex().contains(textSpaced) || recipe.inputSearchIndex().contains(textSpaced) || recipe.categoryName().contains(textSpaced)))
                        || (!textUnder.isEmpty() && (dNameLower.contains(textUnder) || recipe.outputSearchIndex().contains(textUnder) || recipe.inputSearchIndex().contains(textUnder) || recipe.categoryId().contains(textUnder)))
                        || (!textClean.isEmpty() && (dNameLower.contains(textClean) || recipe.outputSearchIndex().contains(textClean) || recipe.inputSearchIndex().contains(textClean) || recipe.categoryName().contains(textClean)));
            }
        };
    }

    /**
     * Finds the primary output matching the active search query or contextual target.
     * Used for promoting the matched secondary output to the front of row icons and title label.
     */
    public static MatchedOutputResult findMatchedOutput(SearchableRecipe recipe, ParsedQuery query, ResourceLocation contextId, String contextName) {
        if (recipe == null) return null;

        MatchedOutputResult byId = findMatchedOutputByContextId(recipe, contextId);
        if (byId != null) return byId;

        MatchedOutputResult byName = findMatchedOutputByContextName(recipe, contextName);
        if (byName != null) return byName;

        return findMatchedOutputByQuery(recipe, query);
    }

    private static MatchedOutputResult findMatchedOutputByContextId(SearchableRecipe recipe, ResourceLocation contextId) {
        if (contextId == null || recipe.outputIds() == null) return null;
        for (int i = 0; i < recipe.outputIds().length; i++) {
            ResourceLocation outId = recipe.outputIds()[i];
            if (outId != null && (outId.equals(contextId) || outId.getPath().equalsIgnoreCase(contextId.getPath()))) {
                String name = (recipe.outputNames() != null && i < recipe.outputNames().length) ? recipe.outputNames()[i] : null;
                return new MatchedOutputResult(outId, name, i);
            }
        }
        return null;
    }

    private static MatchedOutputResult findMatchedOutputByContextName(SearchableRecipe recipe, String contextName) {
        if (contextName == null || contextName.isEmpty() || recipe.outputNames() == null) return null;
        for (int i = 0; i < recipe.outputNames().length; i++) {
            String outName = recipe.outputNames()[i];
            if (outName != null && outName.equalsIgnoreCase(contextName)) {
                ResourceLocation id = (recipe.outputIds() != null && i < recipe.outputIds().length) ? recipe.outputIds()[i] : null;
                return new MatchedOutputResult(id, outName, i);
            }
        }
        return null;
    }

    private static MatchedOutputResult findMatchedOutputByQuery(SearchableRecipe recipe, ParsedQuery query) {
        if (query == null || query.isEmpty() || query.orGroups() == null || recipe.outputNames() == null) return null;
        for (AndGroup group : query.orGroups()) {
            MatchedOutputResult match = findMatchedOutputInGroup(recipe, group);
            if (match != null) return match;
        }
        return null;
    }

    private static MatchedOutputResult findMatchedOutputInGroup(SearchableRecipe recipe, AndGroup group) {
        for (QueryTerm term : group.terms()) {
            MatchedOutputResult match = findMatchedOutputForTerm(recipe, term);
            if (match != null) return match;
        }
        return null;
    }

    private static MatchedOutputResult findMatchedOutputForTerm(SearchableRecipe recipe, QueryTerm term) {
        if (term.negated()) return null;
        if (term.scope() == Scope.INPUT || term.type() == MatchType.CATEGORY) return null;

        String t = term.text().toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) return null;
        String tSpaced = t.replace('_', ' ').trim();
        String tUnder = t.replace(' ', '_').trim();

        MatchedOutputResult exactMatch = matchOutputTerm(recipe, t, tSpaced, tUnder, true);
        if (exactMatch != null) return exactMatch;

        return matchOutputTerm(recipe, t, tSpaced, tUnder, false);
    }

    private static MatchedOutputResult matchOutputTerm(SearchableRecipe recipe, String t, String tSpaced, String tUnder, boolean exactOnly) {
        if (recipe.outputNames() == null) return null;
        for (int i = 0; i < recipe.outputNames().length; i++) {
            String outName = recipe.outputNames()[i];
            ResourceLocation outId = (recipe.outputIds() != null && i < recipe.outputIds().length) ? recipe.outputIds()[i] : null;

            if (matchesOutputNameOrPath(outName, outId, t, tSpaced, tUnder, exactOnly)) {
                return new MatchedOutputResult(outId, outName, i);
            }
        }
        return null;
    }

    private static boolean matchesOutputNameOrPath(String outName, ResourceLocation outId, String t, String tSpaced, String tUnder, boolean exactOnly) {
        if (outName != null) {
            String lowerName = outName.toLowerCase(Locale.ROOT);
            if (exactOnly) {
                if (lowerName.equals(t) || lowerName.equals(tSpaced) || lowerName.startsWith(t) || lowerName.startsWith(tSpaced)) return true;
            } else {
                if (lowerName.contains(t) || (!tSpaced.isEmpty() && lowerName.contains(tSpaced))) return true;
            }
        }
        if (outId != null) {
            String lowerPath = outId.getPath().toLowerCase(Locale.ROOT);
            if (exactOnly) {
                return lowerPath.equals(t) || lowerPath.equals(tUnder) || lowerPath.startsWith(t) || lowerPath.startsWith(tUnder);
            } else {
                return lowerPath.contains(t) || (!tUnder.isEmpty() && lowerPath.contains(tUnder));
            }
        }
        return false;
    }

    /**
     * Finds alternative recipes suitable for switching an existing node,
     * ranked by category/machine match and shared output ingredients.
     */
    public static List<SearchableRecipe> findAlternativeRecipes(com.gtceu.calcboard.api.model.RecipeNode node, List<SearchableRecipe> allRecipes) {
        if (node == null || allRecipes == null || allRecipes.isEmpty()) return Collections.emptyList();

        ResourceLocation machineIcon = node.getMachineIcon();
        ResourceLocation catId = node.getRecipeCategoryId();
        String machinePath = (machineIcon != null) ? machineIcon.getPath().toLowerCase(Locale.ROOT) : "";
        String catPath = (catId != null) ? catId.getPath().toLowerCase(Locale.ROOT) : "";

        List<IngredientStack> currentOutputs = node.getOutputs();
        Set<ResourceLocation> outIds = new HashSet<>();
        Set<String> outNames = new HashSet<>();
        for (IngredientStack out : currentOutputs) {
            if (out != null) {
                if (out.getId() != null) outIds.add(out.getId());
                if (out.getDisplayName() != null) outNames.add(out.getDisplayName().toLowerCase(Locale.ROOT));
            }
        }

        record ScoredAlternative(SearchableRecipe recipe, int score) {}
        List<ScoredAlternative> list = new ArrayList<>();

        for (SearchableRecipe sr : allRecipes) {
            if (sr == null) continue;
            int score = 0;

            String srCat = sr.categoryId().toLowerCase(Locale.ROOT);
            boolean machineMatch = (!machinePath.isEmpty() && (srCat.equals(machinePath) || srCat.contains(machinePath)))
                    || (!catPath.isEmpty() && (srCat.equals(catPath) || srCat.contains(catPath)));

            if (machineMatch) {
                score += 10000;
            }

            score += calculateAlternativeSharedScore(sr, outIds, outNames);

            if (score > 0) {
                list.add(new ScoredAlternative(sr, score));
            }
        }

        list.sort((a, b) -> Integer.compare(b.score(), a.score()));

        return list.stream()
                .limit(80)
                .map(ScoredAlternative::recipe)
                .toList();
    }

    private static int calculateAlternativeSharedScore(SearchableRecipe sr, Set<ResourceLocation> outIds, Set<String> outNames) {
        int score = 0;
        if (sr.outputIds() != null) {
            for (ResourceLocation rid : sr.outputIds()) {
                if (rid != null && outIds.contains(rid)) {
                    score += 4000;
                }
            }
        }
        if (sr.outputNames() != null) {
            for (String oname : sr.outputNames()) {
                if (oname != null && outNames.contains(oname.toLowerCase(Locale.ROOT))) {
                    score += 2000;
                }
            }
        }
        return score;
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
                String tSpaced = t.replace('_', ' ').trim();
                String tUnder = t.replace(' ', '_').trim();
                String tClean = t.replaceAll("_+$", "").trim();

                if (allInOutputs && !checkTermMatch(recipe, term, t, tSpaced, tUnder, tClean, true)) {
                    allInOutputs = false;
                }
                if (allInInputsOrCat && !checkTermMatch(recipe, term, t, tSpaced, tUnder, tClean, false)) {
                    allInInputsOrCat = false;
                }
                if (!allInOutputs && !allInInputsOrCat) {
                    break;
                }
            }

            int groupScore = 1;
            if (allInOutputs) {
                groupScore = 3;
            } else if (allInInputsOrCat) {
                groupScore = 2;
            }
            if (groupScore > maxScore) {
                maxScore = groupScore;
            }
        }
        return maxScore;
    }

    private static boolean checkTermMatch(SearchableRecipe recipe, QueryTerm term, String t, String tSpaced, String tUnder, String tClean, boolean isOutputCheck) {
        if (term.scope() == Scope.OUTPUT) {
            return isOutputCheck && matchesOutput(recipe, term.type(), t, tSpaced, tUnder, tClean);
        }
        if (term.scope() == Scope.INPUT) {
            return !isOutputCheck && matchesInput(recipe, term.type(), t, tSpaced, tUnder, tClean);
        }
        if (isOutputCheck) {
            return recipe.outputSearchIndex().contains(t) || recipe.displayName().toLowerCase(Locale.ROOT).contains(t);
        }
        return recipe.inputSearchIndex().contains(t) || recipe.categoryName().contains(t) || recipe.categoryId().contains(t);
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

            String dn = recipe.displayName().toLowerCase(Locale.ROOT);
            String cat = recipe.categoryName().toLowerCase(Locale.ROOT);
            String catId = recipe.categoryId().toLowerCase(Locale.ROOT);

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
                score += calculateTermScore(term, recipe, cat, catId, dn);
            }
        }
        return score;
    }

    private static int calculateTermScore(QueryTerm term, SearchableRecipe recipe, String cat, String catId, String dn) {
        String t = term.text();
        String tUnder = t.replace(' ', '_');

        if (term.type() == MatchType.CATEGORY) {
            return scoreCategoryTerm(t, tUnder, cat, catId);
        }
        if (term.type() == MatchType.MOD_ID) {
            return scoreModIdTerm(term, recipe, t);
        }
        if (term.type() == MatchType.TAG) {
            return scoreTagTerm(recipe, t, tUnder);
        }
        return scoreGeneralTerm(term, recipe, t, tUnder, cat, catId, dn);
    }

    private static int scoreCategoryTerm(String t, String tUnder, String cat, String catId) {
        if (cat.equals(t) || catId.equals(t) || catId.equals(tUnder)) return 20000;
        if (cat.contains(t) || catId.contains(t) || catId.contains(tUnder)) return 10000;
        return 0;
    }

    private static int scoreModIdTerm(QueryTerm term, SearchableRecipe recipe, String t) {
        if (term.scope() == Scope.INPUT) {
            if (recipe.inputSearchIndex().startsWith(t + ":") || recipe.inputSearchIndex().contains(" " + t + ":")) return 10000;
            return 0;
        }
        if (term.scope() == Scope.OUTPUT) {
            if (recipe.outputSearchIndex().startsWith(t + ":") || recipe.outputSearchIndex().contains(" " + t + ":")) return 10000;
            return 0;
        }
        if (recipe.modId().equals(t)) return 10000;
        if (recipe.modId().contains(t)) return 5000;
        return 0;
    }

    private static int scoreTagTerm(SearchableRecipe recipe, String t, String tUnder) {
        if (recipe.inputSearchIndex().contains(t) || recipe.inputSearchIndex().contains(tUnder)
                || recipe.outputSearchIndex().contains(t) || recipe.outputSearchIndex().contains(tUnder)) {
            return 8000;
        }
        return 0;
    }

    private static int scoreGeneralTerm(QueryTerm term, SearchableRecipe recipe, String t, String tUnder, String cat, String catId, String dn) {
        int s = 0;
        if (term.scope() == Scope.OUTPUT) {
            if (dn.equals(t)) s += 12000;
            else if (dn.startsWith(t)) s += 6000;
            else if (dn.contains(t)) s += 3000;

            if (recipe.outputSearchIndex().contains(t)) s += 3000;
            return s;
        }
        if (term.scope() == Scope.INPUT) {
            if (recipe.inputSearchIndex().contains(t)) s += 4000;
            return s;
        }

        if (dn.equals(t)) s += 10000;
        else if (dn.startsWith(t)) s += 5000;
        else if (dn.contains(t)) s += 2500;

        if (cat.equals(t) || catId.equals(t) || catId.equals(tUnder)) s += 4000;
        else if (cat.contains(t) || catId.contains(t) || catId.contains(tUnder)) s += 2000;

        if (recipe.outputSearchIndex().contains(t)) s += 1500;
        else if (recipe.inputSearchIndex().contains(t)) s += 800;
        return s;
    }

    public static void clearCaches() {
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            com.gtceu.calcboard.integration.emi.EmiRecipeSearchIndexer.clearCaches();
        }
        STACK_DATA_CACHE.clear();
    }
}



