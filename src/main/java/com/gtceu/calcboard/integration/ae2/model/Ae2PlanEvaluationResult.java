package com.gtceu.calcboard.integration.ae2.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of the precision ETA and bottleneck evaluation for an AE2 autocrafting plan.
 */
public record Ae2PlanEvaluationResult(
        long totalDurationTicks,
        double totalDurationSeconds,
        String formattedEta,
        String bottleneckName,
        String bottleneckPageId,
        List<Ae2PlanStep> steps,
        int coProcessors
) {
    public static final Ae2PlanEvaluationResult EMPTY = new Ae2PlanEvaluationResult(
            0L,
            0.0,
            "0s",
            "",
            "",
            Collections.emptyList(),
            0
    );

    public Ae2PlanEvaluationResult {
        formattedEta = formattedEta != null ? formattedEta : "0s";
        bottleneckName = bottleneckName != null ? bottleneckName : "";
        bottleneckPageId = bottleneckPageId != null ? bottleneckPageId : "";
        steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
    }

    public boolean hasBottleneckPage() {
        return bottleneckPageId != null && !bottleneckPageId.isEmpty();
    }

    public void writeBuffer(FriendlyByteBuf buf) {
        buf.writeVarLong(totalDurationTicks);
        buf.writeDouble(totalDurationSeconds);
        buf.writeUtf(formattedEta);
        buf.writeUtf(bottleneckName);
        buf.writeUtf(bottleneckPageId);
        buf.writeVarInt(coProcessors);
        buf.writeVarInt(steps.size());
        for (Ae2PlanStep step : steps) {
            step.writeBuffer(buf);
        }
    }

    public static Ae2PlanEvaluationResult readBuffer(FriendlyByteBuf buf) {
        long totalTicks = buf.readVarLong();
        double totalSeconds = buf.readDouble();
        String formattedEta = buf.readUtf();
        String bottleneckName = buf.readUtf();
        String bottleneckPageId = buf.readUtf();
        int coProcessors = buf.readVarInt();
        int stepCount = buf.readVarInt();
        List<Ae2PlanStep> steps = new ArrayList<>(stepCount);
        for (int i = 0; i < stepCount; i++) {
            steps.add(Ae2PlanStep.readBuffer(buf));
        }
        return new Ae2PlanEvaluationResult(totalTicks, totalSeconds, formattedEta, bottleneckName, bottleneckPageId, steps, coProcessors);
    }
}
