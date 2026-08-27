package com.gtceu.calcboard;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.DynamicAddonCrawler;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.event.FlowGraphEvent;
import com.gtceu.calcboard.api.event.MachineAddonRegisterEvent;
import com.gtceu.calcboard.api.event.RecipeNodeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LifecycleEventBusTest {

    @BeforeEach
    public void setUp() {
        MinecraftForge.EVENT_BUS.start();
        cleanupListeners();
    }

    @AfterEach
    public void tearDown() {
        cleanupListeners();
    }

    private void cleanupListeners() {
        RecipeNodeEvent.clearListeners();
        FlowGraphEvent.clearListeners();
        MachineAddonRegisterEvent.clearListeners();
    }

    @Test
    public void testRecipeNodeCreatedEvent() {
        AtomicInteger createdCount = new AtomicInteger(0);
        List<RecipeNode> createdNodes = new ArrayList<>();

        MinecraftForge.EVENT_BUS.addListener((RecipeNodeEvent.Created event) -> {
            createdCount.incrementAndGet();
            createdNodes.add(event.getNode());
        });

        RecipeNode node = RecipeNode.create("Electric Blast Furnace", 200.0, 128.0, GTVoltageTier.MV);

        Assertions.assertEquals(1, createdCount.get());
        Assertions.assertFalse(createdNodes.isEmpty());
        Assertions.assertEquals(node, createdNodes.get(0));
    }

    @Test
    public void testRecipeNodePreAndPostCalculationEvents() {
        AtomicBoolean preFired = new AtomicBoolean(false);
        AtomicBoolean postFired = new AtomicBoolean(false);

        MinecraftForge.EVENT_BUS.addListener((RecipeNodeEvent.PreCalculation event) -> {
            preFired.set(true);
        });

        MinecraftForge.EVENT_BUS.addListener((RecipeNodeEvent.PostCalculation event) -> {
            postFired.set(true);
            for (Map.Entry<IngredientStack, Double> entry : event.getOutputRates().entrySet()) {
                entry.setValue(entry.getValue() * 2.0);
            }
        });

        RecipeNode node = RecipeNode.create("Macerator", 100.0, 32.0, GTVoltageTier.LV);
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));

        node.getOverclockResult();
        Assertions.assertTrue(preFired.get(), "PreCalculation event should fire on getOverclockResult");

        Map<IngredientStack, Double> outputRates = node.calculateOutputRates();
        Assertions.assertTrue(postFired.get(), "PostCalculation event should fire on calculateOutputRates");

        IngredientStack outStack = node.getOutputs().get(0);
        double standardNominalCps = node.getCyclesPerSecond();
        Assertions.assertEquals(standardNominalCps * 2.0, outputRates.get(outStack), 0.001);
    }

    @Test
    public void testFlowGraphSolverLifecycleEvents() {
        AtomicInteger preSolveCount = new AtomicInteger(0);
        AtomicInteger postSolveCount = new AtomicInteger(0);

        MinecraftForge.EVENT_BUS.addListener((FlowGraphEvent.PreSolve event) -> {
            preSolveCount.incrementAndGet();
        });

        MinecraftForge.EVENT_BUS.addListener((FlowGraphEvent.PostSolve event) -> {
            postSolveCount.incrementAndGet();
        });

        FlowGraph graph = new FlowGraph();
        RecipeNode producer = RecipeNode.create("Producer", 100.0, 32.0, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        RecipeNode consumer = RecipeNode.create("Consumer", 100.0, 32.0, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));

        graph.addNode(producer);
        graph.addNode(consumer);
        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        FlowGraphSolver.autoRatioFromAnchor(graph, consumer, false);

        Assertions.assertEquals(1, preSolveCount.get(), "PreSolve event must fire once");
        Assertions.assertEquals(1, postSolveCount.get(), "PostSolve event must fire once");
    }

    @Test
    public void testMachineAddonRegisterEventInjection() {
        MachineAddon customAugment = new MachineAddon(
            "custom:star_booster",
            "Star Booster [8x Par, 2x Speed]",
            AddonCategory.CUSTOM,
            "Custom Star Technology Overclock Augment",
            ResourceLocation.tryParse("minecraft:nether_star")
        );
        customAugment.setParallelMultiplier(8);
        customAugment.setDurationMultiplier(0.5);

        MinecraftForge.EVENT_BUS.addListener((MachineAddonRegisterEvent event) -> {
            event.register(customAugment);
        });

        List<MachineAddon> allTraits = DynamicAddonCrawler.getBuiltinTraits();

        boolean containsCustom = allTraits.stream().anyMatch(a -> "custom:star_booster".equals(a.getId()));
        Assertions.assertTrue(containsCustom, "Custom addon injected via MachineAddonRegisterEvent must be in getBuiltinTraits()");
    }
}

