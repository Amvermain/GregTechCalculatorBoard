package com.gtceu.calcboard.integration.ae2.registry;

import appeng.api.crafting.IPatternDetails;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.IPageLifecycleListener;
import com.gtceu.calcboard.integration.ae2.model.PatternBindingEntry;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry managing deterministic 1:1 bindings between AE2 autocrafting patterns and Calculator Board pages.
 */
public final class PatternGraphRegistry {
    private static final PatternGraphRegistry INSTANCE = new PatternGraphRegistry();

    private final Map<String, PatternBindingEntry> bindingsByPatternKey = new ConcurrentHashMap<>();
    private final Map<String, String> pageIdToPatternKey = new ConcurrentHashMap<>();

    private PatternGraphRegistry() {
        BoardManager.getInstance().addPageLifecycleListener(new IPageLifecycleListener() {
            @Override
            public void onPageRemoved(BoardPage page, int index) {
                if (page != null) {
                    unbindPage(page.getId());
                }
            }
        });
    }

    public static PatternGraphRegistry getInstance() {
        return INSTANCE;
    }

    public void bind(PatternId patternId, String pageId, String pageName) {
        if (patternId == null || pageId == null || pageId.isEmpty()) return;

        PatternBindingEntry existingForPattern = bindingsByPatternKey.get(patternId.getKey());
        if (existingForPattern != null) {
            pageIdToPatternKey.remove(existingForPattern.pageId());
        }

        String existingPatternKey = pageIdToPatternKey.get(pageId);
        if (existingPatternKey != null) {
            bindingsByPatternKey.remove(existingPatternKey);
        }

        PatternBindingEntry entry = PatternBindingEntry.of(patternId, pageId, pageName);
        bindingsByPatternKey.put(patternId.getKey(), entry);
        pageIdToPatternKey.put(pageId, patternId.getKey());
    }

    public void bind(PatternId patternId, BoardPage page) {
        if (patternId == null || page == null) return;
        bind(patternId, page.getId(), page.getName());
    }

    public void unbind(PatternId patternId) {
        if (patternId == null) return;
        PatternBindingEntry entry = bindingsByPatternKey.remove(patternId.getKey());
        if (entry != null) {
            pageIdToPatternKey.remove(entry.pageId());
        }
    }

    public void unbindPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return;
        String patternKey = pageIdToPatternKey.remove(pageId);
        if (patternKey != null) {
            bindingsByPatternKey.remove(patternKey);
        }
    }

    public Optional<BoardPage> getDirectBoundPage(PatternId patternId) {
        if (patternId == null) return Optional.empty();
        PatternBindingEntry entry = bindingsByPatternKey.get(patternId.getKey());
        if (entry != null) {
            return findPageById(entry.pageId());
        }

        if (patternId.getPrimaryOutputId() != null) {
            for (PatternBindingEntry candidate : bindingsByPatternKey.values()) {
                if (candidate != null && candidate.patternId() != null
                        && Objects.equals(candidate.patternId().getPrimaryOutputId(), patternId.getPrimaryOutputId())) {
                    return findPageById(candidate.pageId());
                }
            }
        }
        return Optional.empty();
    }

    public Optional<BoardPage> getBoundPage(PatternId patternId) {
        if (patternId == null) return Optional.empty();
        Optional<BoardPage> directOpt = getDirectBoundPage(patternId);
        if (directOpt.isPresent()) {
            return directOpt;
        }

        if (patternId.getPrimaryOutputId() != null) {
            for (PatternBindingEntry candidate : bindingsByPatternKey.values()) {
                if (candidate == null) continue;
                Optional<BoardPage> pageOpt = findPageById(candidate.pageId());
                if (pageOpt.isPresent() && pageProducesOutput(pageOpt.get(), patternId.getPrimaryOutputId())) {
                    return pageOpt;
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isOutputJunction(FlowGraph graph, RecipeNode node) {
        if (graph == null || node == null || !node.isReroute()) return false;
        boolean hasIncoming = false;
        boolean hasOutgoing = false;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(node.getId())) hasIncoming = true;
            if (edge.fromNodeId().equals(node.getId())) hasOutgoing = true;
        }
        return hasIncoming && !hasOutgoing;
    }

    public static boolean pageProducesOutput(BoardPage page, ResourceLocation outputId) {
        if (page == null || page.getGraph() == null || outputId == null) return false;
        FlowGraph graph = page.getGraph();

        for (RecipeNode node : graph.getNodes()) {
            if (node == null) continue;
            if (node.isReroute() && node.getRerouteIngredient() != null && outputId.equals(node.getRerouteIngredient().getId())) {
                if (isOutputJunction(graph, node)) {
                    return true;
                }
            }
            if (!node.isReroute()) {
                for (IngredientStack out : node.getOutputs()) {
                    if (out != null && outputId.equals(out.getId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static double findPageBatchOutputAmount(BoardPage page, ResourceLocation outputId) {
        if (page == null || page.getGraph() == null || outputId == null) return 1.0;
        FlowGraph graph = page.getGraph();

        for (RecipeNode node : graph.getNodes()) {
            if (node == null) continue;
            if (node.isReroute() && node.getRerouteIngredient() != null && outputId.equals(node.getRerouteIngredient().getId())) {
                if (isOutputJunction(graph, node)) {
                    if (node.getTargetBatchAmount() > 0) {
                        return node.getTargetBatchAmount();
                    }
                    if (node.getRerouteIngredient().getAmount() > 0) {
                        return node.getRerouteIngredient().getAmount();
                    }
                }
            }
        }
        for (RecipeNode node : graph.getNodes()) {
            if (node == null || node.isReroute()) continue;
            for (IngredientStack out : node.getOutputs()) {
                if (out != null && outputId.equals(out.getId()) && out.getAmount() > 0) {
                    return out.getAmount();
                }
            }
        }
        return 1.0;
    }

    public static Map<ResourceLocation, Double> extractAllPageOutputs(BoardPage page) {
        Map<ResourceLocation, Double> outputJunctionOutputs = new HashMap<>();
        Map<ResourceLocation, Double> machineOutputs = new HashMap<>();
        if (page == null || page.getGraph() == null) return outputJunctionOutputs;

        FlowGraph graph = page.getGraph();

        for (RecipeNode node : graph.getNodes()) {
            if (node == null) continue;
            if (node.isReroute() && node.getRerouteIngredient() != null) {
                if (isOutputJunction(graph, node)) {
                    ResourceLocation id = node.getRerouteIngredient().getId();
                    if (id != null) {
                        double amount = node.getTargetBatchAmount() > 0 ? node.getTargetBatchAmount() : Math.max(1.0, node.getRerouteIngredient().getAmount());
                        outputJunctionOutputs.put(id, outputJunctionOutputs.getOrDefault(id, 0.0) + amount);
                    }
                }
            } else if (!node.isReroute()) {
                for (IngredientStack out : node.getOutputs()) {
                    if (out != null && out.getId() != null) {
                        machineOutputs.put(out.getId(), machineOutputs.getOrDefault(out.getId(), 0.0) + Math.max(1.0, out.getAmount()));
                    }
                }
            }
        }

        return !outputJunctionOutputs.isEmpty() ? outputJunctionOutputs : machineOutputs;
    }

    public static Map<ResourceLocation, Double> extractPageOutputsForPrimary(BoardPage page, ResourceLocation primaryId) {
        Map<ResourceLocation, Double> outputs = new HashMap<>();
        if (page == null || page.getGraph() == null) return outputs;

        FlowGraph graph = page.getGraph();
        RecipeNode targetNode = null;

        for (RecipeNode node : graph.getNodes()) {
            if (node == null || node.isReroute()) continue;
            for (IngredientStack out : node.getOutputs()) {
                if (out != null && Objects.equals(out.getId(), primaryId)) {
                    targetNode = node;
                    break;
                }
            }
            if (targetNode != null) break;
        }

        if (targetNode != null) {
            for (IngredientStack out : targetNode.getOutputs()) {
                if (out != null && out.getId() != null) {
                    outputs.put(out.getId(), Math.max(1.0, out.getAmount()));
                }
            }
            return outputs;
        }
        return extractAllPageOutputs(page);
    }

    public static Set<ResourceLocation> extractAllPageInputs(BoardPage page) {
        Set<ResourceLocation> inputs = new HashSet<>();
        if (page == null || page.getGraph() == null) return inputs;

        FlowGraph graph = page.getGraph();
        for (RecipeNode node : graph.getNodes()) {
            if (node == null) continue;
            if (node.isReroute() && node.getRerouteIngredient() != null) {
                if (!isOutputJunction(graph, node)) {
                    ResourceLocation id = node.getRerouteIngredient().getId();
                    if (id != null) {
                        inputs.add(id);
                    }
                }
            } else if (!node.isReroute()) {
                for (IngredientStack in : node.getInputs()) {
                    if (in != null && in.getId() != null) {
                        inputs.add(in.getId());
                    }
                }
            }
        }
        return inputs;
    }

    public Optional<BoardPage> getBoundPage(IPatternDetails pattern) {
        if (pattern == null) return Optional.empty();
        return getBoundPage(PatternId.of(pattern));
    }

    public Optional<BoardPage> getBoundPage(ItemStack patternStack) {
        if (patternStack == null || patternStack.isEmpty()) return Optional.empty();
        return getBoundPage(PatternId.of(patternStack));
    }

    public Optional<PatternBindingEntry> getBindingForPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return Optional.empty();
        String patternKey = pageIdToPatternKey.get(pageId);
        if (patternKey == null) return Optional.empty();
        return Optional.ofNullable(bindingsByPatternKey.get(patternKey));
    }

    public boolean isPageBound(String pageId) {
        if (pageId == null || pageId.isEmpty()) return false;
        return pageIdToPatternKey.containsKey(pageId);
    }

    public boolean isPatternBound(PatternId patternId) {
        if (patternId == null) return false;
        return bindingsByPatternKey.containsKey(patternId.getKey());
    }

    public Map<String, PatternBindingEntry> getAllBindings() {
        return Collections.unmodifiableMap(bindingsByPatternKey);
    }

    private Optional<BoardPage> findPageById(String pageId) {
        for (BoardPage page : BoardManager.getInstance().getPages()) {
            if (page.getId().equals(pageId)) {
                return Optional.of(page);
            }
        }
        return Optional.empty();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (PatternBindingEntry entry : bindingsByPatternKey.values()) {
            list.add(entry.serializeNBT());
        }
        tag.put("bindings", list);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        clear();
        if (tag == null || !tag.contains("bindings", Tag.TAG_LIST)) return;
        ListTag list = tag.getList("bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PatternBindingEntry entry = PatternBindingEntry.deserializeNBT(list.getCompound(i));
            if (entry != null) {
                bindingsByPatternKey.put(entry.patternId().getKey(), entry);
                pageIdToPatternKey.put(entry.pageId(), entry.patternId().getKey());
            }
        }
    }

    public void clear() {
        bindingsByPatternKey.clear();
        pageIdToPatternKey.clear();
    }
}
