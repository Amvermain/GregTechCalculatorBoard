package com.gtceu.calcboard.client.web;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Background queue and processor that prewarms and caches item, fluid, and machine
 * icons into disk cache without stalling the main client render thread.
 */
public final class IconPrewarmer {

    public record PrewarmRequest(String type, String id, String extra, Integer tint) {
        public static PrewarmRequest item(String id, String nbt) {
            return new PrewarmRequest("item", id, nbt, null);
        }

        public static PrewarmRequest fluid(String id, Integer tint) {
            return new PrewarmRequest("fluid", id, null, tint);
        }

        public String deduplicationKey() {
            return type + ":" + (id != null ? id : "") + ":" + (extra != null ? extra : "") + ":" + (tint != null ? tint : "");
        }
    }

    private static final IconPrewarmer INSTANCE = new IconPrewarmer();
    private static final int MAX_PER_TICK = 2;

    private final Queue<PrewarmRequest> queue = new ConcurrentLinkedQueue<>();
    private final Set<String> queuedKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> failedKeys = ConcurrentHashMap.newKeySet();

    private IconPrewarmer() {}

    public static IconPrewarmer getInstance() {
        return INSTANCE;
    }

    public void enqueue(FlowGraph graph) {
        if (graph == null) return;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[IconPrewarmer] Enqueueing graph with {} nodes", graph.getNodes().size());
        for (RecipeNode node : graph.getNodes()) {
            enqueue(node);
        }
    }

    public void enqueue(RecipeNode node) {
        if (node == null) return;
        String machineId = resolveMachineId(node);
        if (!machineId.isEmpty()) {
            enqueueItem(machineId, null);
        }
        if (node.getMachineIcon() != null) {
            enqueueItem(node.getMachineIcon().toString(), null);
        }
        for (IngredientStack in : node.getInputs()) {
            enqueueIngredient(in);
        }
        for (IngredientStack out : node.getOutputs()) {
            enqueueIngredient(out);
        }
    }

    private static String resolveMachineId(RecipeNode node) {
        if (node.getMachineIcon() != null) return node.getMachineIcon().toString();
        if (node.getAvailableWorkstations() != null && !node.getAvailableWorkstations().isEmpty()) {
            return node.getAvailableWorkstations().get(0).toString();
        }
        if (node.getRecipeCategoryId() != null) return node.getRecipeCategoryId().toString();
        return "";
    }

    public void enqueueIngredient(IngredientStack stack) {
        if (stack == null || stack.getId() == null) return;
        String idStr = stack.getId().toString();
        if (stack.isFluid()) {
            enqueueFluid(idStr, null);
        } else {
            enqueueItem(idStr, null);
        }
    }

    public void enqueueItem(String itemId, String nbt) {
        if (itemId == null || itemId.isBlank()) return;
        PrewarmRequest req = PrewarmRequest.item(itemId, nbt);
        if (failedKeys.contains(req.deduplicationKey())) return;
        if (IconDiskCache.getInstance().isItemCached(itemId, nbt)) return;

        if (queuedKeys.add(req.deduplicationKey())) {
            queue.offer(req);
        }
    }

    public void enqueueFluid(String fluidId, Integer tint) {
        if (fluidId == null || fluidId.isBlank()) return;
        PrewarmRequest req = PrewarmRequest.fluid(fluidId, tint);
        if (failedKeys.contains(req.deduplicationKey())) return;
        if (IconDiskCache.getInstance().isFluidCached(fluidId, tint)) return;

        if (queuedKeys.add(req.deduplicationKey())) {
            queue.offer(req);
        }
    }

    public void tick() {
        if (queue.isEmpty() || !MicroIconRenderer.isClientRenderAvailable()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        int processed = 0;
        int checked = 0;
        while (processed < MAX_PER_TICK && !queue.isEmpty() && checked < 50) {
            PrewarmRequest req = queue.poll();
            if (req == null) break;
            queuedKeys.remove(req.deduplicationKey());
            checked++;

            if (isAlreadyCached(req)) continue;

            processed++;
            NativeImage image = renderRequest(mc, req);
            if (image != null) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[IconPrewarmer] Rendered {}, dispatching async compression", req.deduplicationKey());
                dispatchAsyncCompression(req, image);
            } else {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[IconPrewarmer] Render failed (null image) for {}", req.deduplicationKey());
                failedKeys.add(req.deduplicationKey());
            }
        }
    }

    private boolean isAlreadyCached(PrewarmRequest req) {
        if ("item".equals(req.type())) {
            return IconDiskCache.getInstance().isItemCached(req.id(), req.extra());
        }
        return IconDiskCache.getInstance().isFluidCached(req.id(), req.tint());
    }

    private NativeImage renderRequest(Minecraft mc, PrewarmRequest req) {
        if ("item".equals(req.type())) {
            return MicroIconRenderer.renderItemImageDirect(mc, req.id(), req.extra());
        } else if ("fluid".equals(req.type())) {
            return MicroIconRenderer.renderFluidImageDirect(mc, req.id(), req.tint());
        }
        return null;
    }

    private void dispatchAsyncCompression(PrewarmRequest req, NativeImage image) {
        try {
            Util.ioPool().execute(() -> {
                try (image) {
                    byte[] bytes = image.asByteArray();
                    if (bytes != null && bytes.length > 0) {
                        if ("item".equals(req.type())) {
                            IconDiskCache.getInstance().saveRenderedItemIcon(req.id(), req.extra(), bytes);
                        } else {
                            IconDiskCache.getInstance().saveRenderedFluidIcon(req.id(), req.tint(), bytes);
                        }
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            try {
                image.close();
            } catch (Throwable ignored) {}
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        queuedKeys.clear();
        failedKeys.clear();
    }
}
