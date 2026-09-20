package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.util.RecipeConversionHelper;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.helper.ParallelHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class StarTParallelAndPortPreservationTest {

    @BeforeEach
    public void setUp() {
        ModCompatHelper.clearTestOverrides();
    }

    @AfterEach
    public void tearDown() {
        ModCompatHelper.clearTestOverrides();
    }

    @Test
    @DisplayName("Unpowered multiblock with 8x parallel hatch should not be capped by arbitrary default LV target tier")
    public void testUnpoweredMultiblockPreservesParallelHatchMultiplier() {
        ResourceLocation lmtId = ResourceLocation.tryParse("gtceu:large_maceration_tower");
        MultiblockDetector.registerMultiblock(lmtId);
        MultiblockDetector.registerParallelHatchController(lmtId);

        RecipeNode node = RecipeNode.create(lmtId, "Aluminium Ingot", 100.0, 8.0, GTVoltageTier.ULV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.LV);

        ResourceLocation ivParHatchId = ResourceLocation.tryParse("gtceu:iv_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                ivParHatchId.toString(), "Elite Parallel Control Hatch", "8x Parallel", ivParHatchId, 8, false
        );
        node.getAddons().add(parHatch);
        node.markOverclockDirty();

        int effectivePar = GTPowerCalculator.computeEffectiveParallel(node);
        Assertions.assertEquals(8, effectivePar);

        ResourceLocation mvHatchId = ResourceLocation.tryParse("gtceu:mv_energy_input_hatch_1a");
        GTEnergyHatchAddon mvHatch = new GTEnergyHatchAddon(
                mvHatchId.toString(), "1A MV Energy Hatch", "Energy Hatch", mvHatchId,
                GTVoltageTier.MV, 1, false, false, false
        );
        node.getAddons().add(mvHatch);
        node.markOverclockDirty();

        Assertions.assertEquals(8, GTPowerCalculator.computeEffectiveParallel(node));
    }

    @Test
    @DisplayName("Setting baseSpec to null should not wipe out existing inputs and outputs")
    public void testSetBaseSpecNullDoesNotClearExistingPorts() {
        RecipeNode node = RecipeNode.create("Test Machine", 100.0, 30.0, GTVoltageTier.LV);
        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");
        ResourceLocation dustId = ResourceLocation.tryParse("gtceu:iron_dust");

        node.addInput(IngredientStack.item(ironId, "Iron Ingot", 1));
        node.addOutput(IngredientStack.item(dustId, "Iron Dust", 1));

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(1, node.getOutputs().size());

        node.setBaseSpec(null);

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(1, node.getOutputs().size());
    }

    @Test
    @DisplayName("FlowGraph.switchNodeRecipe preserves ports when template has no explicit baseSpec")
    public void testRecipeSwitchPreservesPortsWhenTemplateLacksBaseSpec() {
        FlowGraph graph = new FlowGraph();
        RecipeNode target = RecipeNode.create("Old Recipe", 100.0, 30.0, GTVoltageTier.LV);
        target.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1));
        target.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_dust"), "Copper Dust", 1));
        graph.addNode(target);

        RecipeNode template = RecipeNode.create("New Recipe", 80.0, 8.0, GTVoltageTier.ULV);
        template.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:aluminium_ingot"), "Aluminium Ingot", 1));
        template.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:aluminium_dust"), "Aluminium Dust", 1));

        graph.switchNodeRecipe(target, template);

        Assertions.assertEquals(1, target.getInputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:aluminium_ingot"), target.getInputs().get(0).getId());
        Assertions.assertEquals(1, target.getOutputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:aluminium_dust"), target.getOutputs().get(0).getId());
    }

    @Test
    @DisplayName("Default parallel hatch registration yields 8x for IV hatch when Star Technology is loaded")
    public void testStarTIVParallelHatchDefaultEight() {
        ModCompatHelper.setTestOverride("start_core", true);
        List<MachineAddon> addons = new ArrayList<>();
        ParallelHelper.registerDefaultParallelHatches(addons);

        MachineAddon ivAddon = addons.stream()
                .filter(a -> a.getId().contains("iv_parallel_hatch"))
                .findFirst()
                .orElse(null);

        Assertions.assertNotNull(ivAddon);
        Assertions.assertEquals(8, ivAddon.getParallelMultiplier());
    }

    @Test
    @DisplayName("Recipe input with zero chance should not be ignored so macerator inputs remain intact")
    public void testZeroChanceInputIsNotDiscarded() {
        ResourceLocation aluminiumIngot = ResourceLocation.tryParse("gtceu:aluminium_ingot");
        Assertions.assertFalse(RecipeConversionHelper.isIgnoredInput(aluminiumIngot, 0.0));
        Assertions.assertFalse(EmiRecipeConverter.isIgnoredInput(aluminiumIngot, 0.0));

        ResourceLocation circuit = ResourceLocation.tryParse("gtceu:programmed_circuit");
        Assertions.assertTrue(RecipeConversionHelper.isIgnoredInput(circuit, 1.0));
        Assertions.assertTrue(EmiRecipeConverter.isIgnoredInput(circuit, 0.0));

        ResourceLocation marker = ResourceLocation.tryParse("start_core:planet_marker");
        Assertions.assertTrue(RecipeConversionHelper.isIgnoredInput(marker, 1.0));
        Assertions.assertTrue(EmiRecipeConverter.isIgnoredInput(marker, 0.0));
    }
}
