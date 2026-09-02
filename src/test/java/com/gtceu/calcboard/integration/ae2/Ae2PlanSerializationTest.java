package com.gtceu.calcboard.integration.ae2;

import com.gtceu.calcboard.integration.ae2.model.Ae2PlanEvaluationResult;
import com.gtceu.calcboard.integration.ae2.model.Ae2PlanStep;
import com.gtceu.calcboard.integration.ae2.model.PatternBindingEntry;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class Ae2PlanSerializationTest {

    @Test
    public void testPatternIdNbtSerialization() {
        PatternId original = PatternId.ofKey("test:processor", "Logic Processor", ItemStack.EMPTY);
        CompoundTag tag = original.serializeNBT();

        PatternId restored = PatternId.deserializeNBT(tag);
        Assertions.assertEquals(original.getKey(), restored.getKey());
        Assertions.assertEquals(original.getDisplayName(), restored.getDisplayName());
    }

    @Test
    public void testPatternBindingEntryNbtSerialization() {
        PatternId patternId = PatternId.ofKey("test:cell", "16k ME Storage Cell", ItemStack.EMPTY);
        PatternBindingEntry original = PatternBindingEntry.of(patternId, "page_123", "Storage Page");

        CompoundTag tag = original.serializeNBT();
        PatternBindingEntry restored = PatternBindingEntry.deserializeNBT(tag);

        Assertions.assertNotNull(restored);
        Assertions.assertEquals(original.pageId(), restored.pageId());
        Assertions.assertEquals(original.pageName(), restored.pageName());
        Assertions.assertEquals(original.patternId().getKey(), restored.patternId().getKey());
    }

    @Test
    public void testPlanEvaluationResultBufferSerialization() {
        PatternId p1 = PatternId.ofKey("gtceu:motor", "LV Motor", ItemStack.EMPTY);
        Ae2PlanStep step = new Ae2PlanStep(p1, "LV Motor", 50, 50, 20.0, 1, 1000.0, "page_motor", "Motor Page");
        Ae2PlanEvaluationResult original = new Ae2PlanEvaluationResult(
                1000L,
                50.0,
                "~50.0s",
                "LV Motor",
                "page_motor",
                List.of(step),
                2
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.writeBuffer(buf);

        Ae2PlanEvaluationResult restored = Ae2PlanEvaluationResult.readBuffer(buf);

        Assertions.assertEquals(original.totalDurationTicks(), restored.totalDurationTicks());
        Assertions.assertEquals(original.totalDurationSeconds(), restored.totalDurationSeconds(), 0.001);
        Assertions.assertEquals(original.formattedEta(), restored.formattedEta());
        Assertions.assertEquals(original.bottleneckName(), restored.bottleneckName());
        Assertions.assertEquals(original.bottleneckPageId(), restored.bottleneckPageId());
        Assertions.assertEquals(original.coProcessors(), restored.coProcessors());
        Assertions.assertEquals(1, restored.steps().size());

        Ae2PlanStep restoredStep = restored.steps().get(0);
        Assertions.assertEquals(step.stepName(), restoredStep.stepName());
        Assertions.assertEquals(step.totalCount(), restoredStep.totalCount());
        Assertions.assertEquals(step.batches(), restoredStep.batches());
        Assertions.assertEquals(step.unitDurationTicks(), restoredStep.unitDurationTicks(), 0.001);
        Assertions.assertEquals(step.effectiveParallel(), restoredStep.effectiveParallel());
        Assertions.assertEquals(step.totalDurationTicks(), restoredStep.totalDurationTicks(), 0.001);
    }
}
