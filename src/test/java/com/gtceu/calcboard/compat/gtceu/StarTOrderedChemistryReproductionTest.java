package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.NodeHardwareReconciler;
import com.gtceu.calcboard.api.model.NodeWorkstationResolver;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry;
import com.gtceu.calcboard.client.gui.compat.gtceu.GTCEuModGuiHandler;
import com.gtceu.calcboard.client.gui.render.NodeCardTextCache;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.gtceu.handler.GTWorkstationSelector;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import com.gtceu.calcboard.testutil.TestMultiblockFixtures;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class StarTOrderedChemistryReproductionTest {

    private final ResourceLocation aspId = ResourceLocation.tryParse("gtceu:advanced_synthesis_plant");
    private final ResourceLocation tsaId = ResourceLocation.tryParse("gtceu:true_synthesis_array");
    private final ResourceLocation catId = ResourceLocation.tryParse("gtceu:ordered_chemistry");

    public static class MockRecipeType {
        public final ResourceLocation registryName;

        public MockRecipeType(ResourceLocation registryName) {
            this.registryName = registryName;
        }

        public boolean isLayered() {
            return true;
        }
    }

    public static class MockLayeredGTRecipe {
        public final MockRecipeType recipeType;
        public final int duration;
        public final long eut;
        public final CompoundTag data;
        public final List<Object> layeredSteps = new ArrayList<>();

        public MockLayeredGTRecipe(ResourceLocation categoryId, int duration, long eut) {
            this.recipeType = new MockRecipeType(categoryId);
            this.duration = duration;
            this.eut = eut;
            this.data = new CompoundTag();
            ListTag stepsTag = new ListTag();
            stepsTag.add(new CompoundTag());
            stepsTag.add(new CompoundTag());
            this.data.put("layered_steps", stepsTag);
        }

        public List<Object> getLayeredSteps() {
            return layeredSteps;
        }

        public int getDuration() {
            return duration;
        }

        public long getInputEUt() {
            return eut;
        }
    }

    @BeforeEach
    public void setUp() {
        MultiblockDetector.reinitialize();
        TestMultiblockFixtures.initTestEnvironmentDefaults();
        MachineAddonCatalog.getInstance().ensureFastLoaded();

        MultiblockDetector.registerMultiblock(aspId);
        MultiblockDetector.registerParallelHatchController(aspId);

        MultiblockDetector.registerMultiblock(tsaId);
        MultiblockDetector.registerParallelHatchController(tsaId);
        MultiblockDetector.registerThroughputBoostingMultiblock(tsaId);

        CategoryCapabilityMatrix.getInstance().registerMockCategory(
                catId,
                List.of(tsaId, aspId),
                aspId,
                false, true, false, false, false, false, false, null, null, null, 0.0
        );
    }

    @Test
    public void testBug1_LayeredRecipePreservesCategoryAndBothWorkstationsAndSelectsBaseMachine() {
        MockLayeredGTRecipe parentRecipe = new MockLayeredGTRecipe(catId, 350, 122880L);
        MockLayeredGTRecipe step1 = new MockLayeredGTRecipe(catId, 200, 122880L);
        MockLayeredGTRecipe step2 = new MockLayeredGTRecipe(catId, 150, 122880L);
        parentRecipe.layeredSteps.add(step1);
        parentRecipe.layeredSteps.add(step2);

        RecipeNode probeNode = RecipeNode.create("Ordered Chemistry", 350.0, 122880.0, GTVoltageTier.UHV);
        probeNode.setRecipeCategoryId(catId);
        ResourceLocation preferredWs = GTWorkstationSelector.getPreferredMultiblockWorkstation(
                probeNode, List.of(tsaId, aspId)
        );
        Assertions.assertEquals(aspId, preferredWs,
                "GTWorkstationSelector must prefer base ASP over UIV throughput-boosted TSA");

        var adapter = ModAdapterRegistry.getAdapterForCategory(catId);
        CompoundRecipeBuilder.CompoundCluster cluster = adapter.buildCompoundRecipe(
                null,
                parentRecipe,
                null,
                0.0,
                0.0
        );

        Assertions.assertNotNull(cluster, "Layered cluster must be created");
        Assertions.assertEquals(2, cluster.nodes().size(), "Must create 2 layer nodes");

        for (RecipeNode layerNode : cluster.nodes()) {
            Assertions.assertEquals(catId, layerNode.getRecipeCategoryId(),
                    "Layer node recipeCategoryId must be gtceu:ordered_chemistry, not machineIcon");
            List<ResourceLocation> mbWorkstations = NodeWorkstationResolver.getMultiblockWorkstations(layerNode);
            Assertions.assertTrue(mbWorkstations.contains(aspId),
                    "Multiblock workstations must include base Advanced Synthesis Plant (ASP)");
            Assertions.assertTrue(mbWorkstations.contains(tsaId),
                    "Multiblock workstations must include True Synthesis Array (TSA)");
            Assertions.assertEquals(aspId, layerNode.getMachineIcon(),
                    "Default machine for Ordered Chemistry must be the base ASP, not UIV boosted TSA");
        }
    }

    @Test
    public void testBug2_TrueSynthesisArrayEquipsThroughputBoostingByDefault() {
        MockLayeredGTRecipe parentRecipe = new MockLayeredGTRecipe(catId, 350, 122880L);
        parentRecipe.layeredSteps.add(new MockLayeredGTRecipe(catId, 200, 122880L));
        parentRecipe.layeredSteps.add(new MockLayeredGTRecipe(catId, 150, 122880L));

        var adapter = ModAdapterRegistry.getAdapterForCategory(catId);
        CompoundRecipeBuilder.CompoundCluster tsaCluster = adapter.buildCompoundRecipe(
                null,
                parentRecipe,
                tsaId,
                0.0,
                0.0
        );
        Assertions.assertNotNull(tsaCluster);
        RecipeNode directTsaNode = tsaCluster.nodes().get(0);
        Assertions.assertTrue(
                directTsaNode.getAddons().stream().anyMatch(a -> "gtceu:throughput_boosting".equals(a.getId())),
                "Node created directly with True Synthesis Array must have Throughput Boosting equipped by default"
        );
        Assertions.assertEquals(4, directTsaNode.getTotalParallel(),
                "TSA with default Throughput Boosting must have 4x total parallel");

        RecipeNode switchedNode = RecipeNode.create(
                aspId,
                "Ordered Chemistry (Layer I)",
                200.0,
                122880.0,
                GTVoltageTier.UHV
        );
        Assertions.assertFalse(
                switchedNode.getAddons().stream().anyMatch(a -> "gtceu:throughput_boosting".equals(a.getId())),
                "Base ASP must not have Throughput Boosting"
        );

        NodeHardwareReconciler.reconcileForMachine(switchedNode, tsaId);
        Assertions.assertTrue(
                switchedNode.getAddons().stream().anyMatch(a -> "gtceu:throughput_boosting".equals(a.getId())),
                "Switching from ASP to TSA must auto-equip Throughput Boosting"
        );
    }

    @Test
    public void testBug3_LayeredCardRow2ButtonsDoNotShiftHitboxes() {
        RecipeNode layerNode = RecipeNode.create(
                aspId,
                "Ordered Chemistry (Layer I)",
                200.0,
                122880.0,
                GTVoltageTier.UHV
        );
        layerNode.setRecipeCategoryId(catId);
        layerNode.setMultiblock(true);
        layerNode.setTargetTier(GTVoltageTier.UHV);
        layerNode.setCompoundMetadata("group-1", 0, 2, layerNode.getId());
        layerNode.setPos(100, 100);

        NodeWidget widget = new NodeWidget(layerNode, null);
        Font mockFont = new Font(loc -> null, false) {
            @Override
            public int width(String text) {
                return text != null ? Math.min(36, text.length() * 5) : 0;
            }
        };

        GTCEuModGuiHandler guiHandler = (GTCEuModGuiHandler) ModGuiHandlerRegistry.getHandlerForNode(layerNode);
        List<NodeCardTextCache.Row2Button> buttons = widget.getTextCache().getRow2Buttons();
        buttons.clear();
        guiHandler.populateRow2Buttons(widget, mockFont, layerNode, layerNode.getCardWidth(), true, buttons);

        NodeCardTextCache.Row2Button badgeBtn = buttons.stream()
                .filter(b -> b.role() == NodeCardTextCache.Row2Button.ButtonRole.BADGE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Compound layer node must have a step BADGE button"));
        NodeCardTextCache.Row2Button ocBtn = buttons.stream()
                .filter(b -> b.role() == NodeCardTextCache.Row2Button.ButtonRole.OC)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Consumer node must have an OC button"));
        NodeCardTextCache.Row2Button configBtn = buttons.stream()
                .filter(b -> b.role() == NodeCardTextCache.Row2Button.ButtonRole.CONFIG)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Node must have a CONFIG button"));

        int cardX = (int) layerNode.getPosX();
        int row2CenterY = (int) layerNode.getPosY() + 20 + 6 + 18 + 7;

        double badgeCenterX = cardX + badgeBtn.relX() + badgeBtn.width() / 2.0;
        double ocCenterX = cardX + ocBtn.relX() + ocBtn.width() / 2.0;
        double configCenterX = cardX + configBtn.relX() + configBtn.width() / 2.0;

        Assertions.assertEquals(OverclockMode.STANDARD, layerNode.getOverclockMode());

        guiHandler.handleControlClick(widget, layerNode, badgeCenterX, row2CenterY, 0);
        Assertions.assertEquals(OverclockMode.STANDARD, layerNode.getOverclockMode(),
                "Clicking step badge [▦ I/II] must NOT change OverclockMode");

        Assertions.assertTrue(guiHandler.isSecondaryControlHovered(widget, layerNode, ocCenterX, row2CenterY),
                "OC button center must be detected as secondary control hovered");
        Assertions.assertFalse(guiHandler.isMachineConfigHovered(widget, layerNode, ocCenterX, row2CenterY),
                "OC button center must NOT be detected as machine config hovered");
        guiHandler.handleControlClick(widget, layerNode, ocCenterX, row2CenterY, 0);
        Assertions.assertEquals(OverclockMode.PERFECT, layerNode.getOverclockMode(),
                "Clicking [STD OC] button must toggle OverclockMode to PERFECT");

        Assertions.assertTrue(guiHandler.isMachineConfigHovered(widget, layerNode, configCenterX, row2CenterY),
                "Config button center must be detected as machine config hovered");
    }

    @Test
    public void testBug4_CompoundStepsSyncHardwareAndPreserveLayerBadgesAndSingleMachineBOM() {
        MockLayeredGTRecipe parentRecipe = new MockLayeredGTRecipe(catId, 350, 122880L);
        parentRecipe.layeredSteps.add(new MockLayeredGTRecipe(catId, 200, 122880L));
        parentRecipe.layeredSteps.add(new MockLayeredGTRecipe(catId, 150, 122880L));

        var adapter = ModAdapterRegistry.getAdapterForCategory(catId);
        CompoundRecipeBuilder.CompoundCluster cluster = adapter.buildCompoundRecipe(
                null, parentRecipe, aspId, 0.0, 0.0
        );
        Assertions.assertNotNull(cluster);
        RecipeNode layer1 = cluster.nodes().get(0);
        RecipeNode layer2 = cluster.nodes().get(1);

        com.gtceu.calcboard.api.model.FlowGraph graph = new com.gtceu.calcboard.api.model.FlowGraph();
        graph.addNode(layer1);
        graph.addNode(layer2);
        if (cluster.frame() != null) {
            graph.addFrame(cluster.frame());
        }

        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon uivHatch =
                new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                        "gtceu:uiv_energy_input_hatch_16a", "UIV 16A Energy Hatch", "", null, GTVoltageTier.UIV, 16
                );
        com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon parHatch =
                new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(
                        "gtceu:uhv_parallel_hatch", "UHV Parallel Hatch", "", null, 1024, false
                );
        layer2.addAddon(uivHatch);
        layer2.addAddon(parHatch);
        NodeHardwareReconciler.reconcileForMachine(layer2, tsaId);

        Assertions.assertEquals(tsaId, layer1.getMachineIcon(),
                "Switching machine on Layer II must sync machineIcon to Layer I");
        Assertions.assertEquals(layer2.getTargetTier(), layer1.getTargetTier(),
                "Installing energy hatch on Layer II must sync targetTier to Layer I");
        Assertions.assertEquals(layer2.getParallel(), layer1.getParallel(),
                "Installing parallel hatch on Layer II must sync parallel to Layer I");
        Assertions.assertEquals(layer2.getAddons().size(), layer1.getAddons().size(),
                "Addons (hatches and traits) must be synced across all compound steps");

        Assertions.assertEquals(0, layer1.getCompoundLayerIndex(),
                "Layer I must preserve compoundLayerIndex 0 (I/II) after syncing from Layer II");
        Assertions.assertEquals(1, layer2.getCompoundLayerIndex(),
                "Layer II must preserve compoundLayerIndex 1 (II/II)");
        Assertions.assertTrue(layer1.isCompoundMaster(), "Layer I must remain compound master");
        Assertions.assertFalse(layer2.isCompoundMaster(), "Layer II must remain compound slave");

        var summary = com.gtceu.calcboard.api.solver.FlowSummaryAggregator.computeSummary(graph, true);
        Assertions.assertEquals(1, summary.totalMachineCount(),
                "Compound steps must be counted as 1 machine in FlowSummaryAggregator, not 2");
    }

    @Test
    public void testBug5_AutoRatioSyncsAllCompoundStepsAndPropagatesAcrossLayers() {
        ResourceLocation itemA = ResourceLocation.tryParse("gtceu:succinic_acid_dust");
        ResourceLocation fluidB = ResourceLocation.tryParse("gtceu:sulfur_dichloride");
        ResourceLocation fluidOut = ResourceLocation.tryParse("gtceu:edot");

        CompoundRecipeBuilder.LayerSpec spec1 = new CompoundRecipeBuilder.LayerSpec(
                "Layer I", aspId, 200.0, 122880.0,
                List.of(com.gtceu.calcboard.api.model.IngredientStack.item(itemA, "Succinic Acid", 14.0)),
                List.of()
        );
        CompoundRecipeBuilder.LayerSpec spec2 = new CompoundRecipeBuilder.LayerSpec(
                "Layer II", aspId, 150.0, 122880.0,
                List.of(com.gtceu.calcboard.api.model.IngredientStack.fluid(fluidB, "Sulfur Dichloride", 1000.0)),
                List.of(com.gtceu.calcboard.api.model.IngredientStack.fluid(fluidOut, "EDOT", 1000.0))
        );
        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Ordered Chemistry", aspId, 350.0, 122880.0, GTVoltageTier.UHV,
                List.of(spec1, spec2), 0.0, 0.0
        );
        RecipeNode layer1 = cluster.nodes().get(0);
        RecipeNode layer2 = cluster.nodes().get(1);
        com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.installDefaultEnergyHatch(layer1, GTVoltageTier.UHV);
        com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.installDefaultEnergyHatch(layer2, GTVoltageTier.UHV);

        RecipeNode upstreamA = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:hv_chemical_reactor"),
                "Succinic Acid Producer", 100.0, 480.0, GTVoltageTier.HV
        );
        upstreamA.addOutput(com.gtceu.calcboard.api.model.IngredientStack.item(itemA, "Succinic Acid", 14.0));

        RecipeNode upstreamB = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:hv_chemical_reactor"),
                "Sulfur Dichloride Producer", 100.0, 480.0, GTVoltageTier.HV
        );
        upstreamB.addOutput(com.gtceu.calcboard.api.model.IngredientStack.fluid(fluidB, "Sulfur Dichloride", 1000.0));

        RecipeNode downstreamConsumer = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:uhv_chemical_reactor"),
                "PEDOT Consumer", 350.0, 122880.0, GTVoltageTier.UHV
        );
        downstreamConsumer.addInput(com.gtceu.calcboard.api.model.IngredientStack.fluid(fluidOut, "EDOT", 250.0));
        downstreamConsumer.setMachineCount(1.0);

        com.gtceu.calcboard.api.model.FlowGraph graph = new com.gtceu.calcboard.api.model.FlowGraph();
        graph.addNode(upstreamA);
        graph.addNode(upstreamB);
        graph.addNode(layer1);
        graph.addNode(layer2);
        graph.addNode(downstreamConsumer);
        if (cluster.frame() != null) {
            graph.addFrame(cluster.frame());
        }

        graph.addConnection(upstreamA.getId(), 0, layer1.getId(), 0);
        graph.addConnection(upstreamB.getId(), 0, layer2.getId(), 0);
        graph.addConnection(layer2.getId(), 0, downstreamConsumer.getId(), 0);

        com.gtceu.calcboard.api.solver.AutoRatioEngine.autoRatioFromAnchor(graph, downstreamConsumer, false);

        Assertions.assertEquals(0.25, layer2.getMachineCount(), 1e-3,
                "Layer II must scale to 0.25 to match downstream consumer over the 350-tick compound cycle");
        Assertions.assertEquals(layer2.getMachineCount(), layer1.getMachineCount(), 1e-4,
                "Layer I must sync to the exact same machine count as Layer II during Auto-Ratio");

        double expectedUpstreamA = layer1.getInputSlotRate(0, false) / upstreamA.calculateSingleMachineOutputRate(upstreamA.getOutputs().get(0));
        Assertions.assertEquals(expectedUpstreamA, upstreamA.getMachineCount(), 1e-3,
                "Upstream producer feeding Layer I must be scaled to match Layer I's scaled demand");
    }

    @Test
    public void testBug6_CompoundStepRatesUseTotalSequentialCycleDurationAcrossAllSteps() {
        ResourceLocation estaltdyne = ResourceLocation.tryParse("gtceu:estaltdyne_dust");
        ResourceLocation carbon = ResourceLocation.tryParse("gtceu:carbon_dust");
        ResourceLocation product = ResourceLocation.tryParse("gtceu:synthesized_product_dust");

        List<CompoundRecipeBuilder.LayerSpec> specs = new ArrayList<>();
        specs.add(new CompoundRecipeBuilder.LayerSpec(
                "Layer I", aspId, 160.0, 983040.0,
                List.of(
                        com.gtceu.calcboard.api.model.IngredientStack.item(estaltdyne, "Estaltdyne Dust", 1.0),
                        com.gtceu.calcboard.api.model.IngredientStack.item(carbon, "Carbon Dust", 6.0)
                ),
                List.of()
        ));
        for (int i = 2; i <= 4; i++) {
            specs.add(new CompoundRecipeBuilder.LayerSpec(
                    "Layer " + i, aspId, 160.0, 983040.0,
                    List.of(com.gtceu.calcboard.api.model.IngredientStack.item(carbon, "Carbon Dust", 1.0)),
                    List.of()
            ));
        }
        specs.add(new CompoundRecipeBuilder.LayerSpec(
                "Layer V", aspId, 160.0, 983040.0,
                List.of(com.gtceu.calcboard.api.model.IngredientStack.item(carbon, "Carbon Dust", 1.0)),
                List.of(com.gtceu.calcboard.api.model.IngredientStack.item(product, "Product", 1.0))
        ));

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Ordered Chemistry", aspId, 800.0, 983040.0, GTVoltageTier.UEV,
                specs, 0.0, 0.0
        );

        com.gtceu.calcboard.api.model.FlowGraph graph = new com.gtceu.calcboard.api.model.FlowGraph();
        for (RecipeNode step : cluster.nodes()) {
            step.setMultiblock(true);
            com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.installDefaultEnergyHatch(step, GTVoltageTier.UEV);
            step.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(
                    "gtceu:ev_substation_hatch", "8x Parallel Hatch", "", null, 8, false
            ));
            graph.addNode(step);
        }

        RecipeNode step1 = cluster.nodes().get(0);
        RecipeNode step5 = cluster.nodes().get(4);

        Assertions.assertEquals(8.0, step1.getEffectiveDurationSeconds(), 1e-4,
                "Individual step duration must still report 8.0s (160 ticks)");
        Assertions.assertEquals(0.2, step1.getCyclesPerSecond(), 1e-4,
                "5 steps of 8s each (40s total cycle) with 8 parallels must run at 8 / 40s = 0.2 crafts/s, not 1.0/s");
        Assertions.assertEquals(0.2, step1.getInputSlotRate(0, false), 1e-4,
                "1 Estaltdyne Dust in Step 1 over a 40s 5-step cycle with 8 parallels must consume 0.2/s, not 1.0/s");
        Assertions.assertEquals(1.2, step1.getInputSlotRate(1, false), 1e-4,
                "6 Carbon Dust in Step 1 over a 40s 5-step cycle with 8 parallels must consume 1.2/s, not 6.0/s");
        Assertions.assertEquals(0.2, step5.getOutputSlotRate(0, false), 1e-4,
                "1 Product in Step 5 over a 40s 5-step cycle with 8 parallels must produce 0.2/s, not 1.0/s");
    }

    @Test
    public void testBug7_InspectorEnergyHatchAndOverclockQuickConfigSyncsAllCompoundLayers() {
        List<CompoundRecipeBuilder.LayerSpec> specs = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            specs.add(new CompoundRecipeBuilder.LayerSpec(
                    "Ordered Chemistry (Layer " + i + ")", aspId, 380.0, 7864320.0,
                    List.of(), List.of()
            ));
        }

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Ordered Chemistry", aspId, 1520.0, 7864320.0, GTVoltageTier.UEV,
                specs, 0.0, 0.0
        );

        com.gtceu.calcboard.api.model.FlowGraph graph = new com.gtceu.calcboard.api.model.FlowGraph();
        if (cluster.frame() != null) {
            graph.addFrame(cluster.frame());
        }
        for (RecipeNode step : cluster.nodes()) {
            step.setRecipeCategoryId(catId);
            step.setMultiblock(true);
            com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.installDefaultEnergyHatch(step, GTVoltageTier.UEV);
            graph.addNode(step);
        }

        RecipeNode layer1 = cluster.nodes().get(0);
        for (RecipeNode step : cluster.nodes()) {
            Assertions.assertEquals(GTVoltageTier.UEV, step.getTargetTier());
            Assertions.assertEquals(19.0, step.getEffectiveDurationSeconds(), 1e-4);
            Assertions.assertEquals(1.0 / 76.0, step.getCyclesPerSecond(), 1e-5);
        }

        com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper.installDefaultEnergyHatch(layer1, GTVoltageTier.UIV);
        graph.syncCompoundParameters(layer1);

        for (int i = 0; i < cluster.nodes().size(); i++) {
            RecipeNode step = cluster.nodes().get(i);
            Assertions.assertEquals(GTVoltageTier.UIV, step.getTargetTier(),
                    "Layer " + (i + 1) + " must sync targetTier to UIV when Layer I energy hatch is changed to UIV");
            Assertions.assertEquals(9.5, step.getEffectiveDurationSeconds(), 1e-4,
                    "Layer " + (i + 1) + " must overclock to 9.50s at UIV");
            Assertions.assertEquals(1.0 / 38.0, step.getCyclesPerSecond(), 1e-5,
                    "Layer " + (i + 1) + " must share the 38.00s total 4-layer cycle (0.0263/s)");
        }
    }
}
