package com.gtceu.calcboard.integration.ae2.model;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Detailed metrics for an individual pattern execution step within an AE2 crafting plan.
 */
public record Ae2PlanStep(
        PatternId patternId,
        String stepName,
        long totalCount,
        int batches,
        double unitDurationTicks,
        int effectiveParallel,
        double totalDurationTicks,
        String boundPageId,
        String boundPageName
) {
    public void writeBuffer(FriendlyByteBuf buf) {
        buf.writeUtf(patternId.getKey());
        buf.writeUtf(stepName);
        buf.writeVarLong(totalCount);
        buf.writeVarInt(batches);
        buf.writeDouble(unitDurationTicks);
        buf.writeVarInt(effectiveParallel);
        buf.writeDouble(totalDurationTicks);
        buf.writeUtf(boundPageId != null ? boundPageId : "");
        buf.writeUtf(boundPageName != null ? boundPageName : "");
    }

    public static Ae2PlanStep readBuffer(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        String stepName = buf.readUtf();
        long totalCount = buf.readVarLong();
        int batches = buf.readVarInt();
        double unitDuration = buf.readDouble();
        int parallel = buf.readVarInt();
        double totalDuration = buf.readDouble();
        String pageId = buf.readUtf();
        String pageName = buf.readUtf();
        PatternId patternId = PatternId.ofKey(key, stepName, null);
        return new Ae2PlanStep(patternId, stepName, totalCount, batches, unitDuration, parallel, totalDuration, pageId, pageName);
    }
}
