package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GTCEuLayeredRecipeExtractorTest {

    public static class MockStepContent {
        private final Object content;
        public float chance = 1.0f;

        public MockStepContent(Object content) {
            this.content = content;
        }

        public Object getContent() {
            return content;
        }
    }

    public static class MockLayeredGTRecipe {
        public final Map<Object, List<Object>> inputs = new HashMap<>();
        public final Map<Object, List<Object>> outputs = new HashMap<>();
        public final Map<Object, List<Object>> tickInputs = new HashMap<>();
        public final Map<Object, List<Object>> tickOutputs = new HashMap<>();
        public int duration = 3200;
        public CompoundTag data = new CompoundTag();

        public long getInputEUt() {
            return 245760L;
        }

        public long getOutputEUt() {
            return 0L;
        }
    }

    @Test
    public void testIsLayeredRecipeDeduction() {
        MockLayeredGTRecipe recipeWithSteps = new MockLayeredGTRecipe();
        recipeWithSteps.data.put("layered_steps", new CompoundTag());
        Assertions.assertTrue(GTCEuLayeredRecipeExtractor.isLayeredRecipe(recipeWithSteps));

        MockLayeredGTRecipe recipeWithInfo = new MockLayeredGTRecipe();
        recipeWithInfo.data.put("layered_info", new CompoundTag());
        Assertions.assertTrue(GTCEuLayeredRecipeExtractor.isLayeredRecipe(recipeWithInfo));

        MockLayeredGTRecipe recipeWithIsLayer = new MockLayeredGTRecipe();
        recipeWithIsLayer.data.putBoolean("is_layer", true);
        Assertions.assertTrue(GTCEuLayeredRecipeExtractor.isLayeredRecipe(recipeWithIsLayer));

        MockLayeredGTRecipe plainRecipe = new MockLayeredGTRecipe();
        Assertions.assertFalse(GTCEuLayeredRecipeExtractor.isLayeredRecipe(plainRecipe));
        Assertions.assertFalse(GTCEuLayeredRecipeExtractor.isLayeredRecipe(null));
    }

    @Test
    public void testBuildCompoundClusterFromLayers() {
        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec(
                "Layer I",
                800.0,
                245760.0,
                List.of(
                        IngredientStack.item(ResourceLocation.tryParse("kubejs:csg_stargate_rod_base"), "Stargate Rod Base", 1.0),
                        IngredientStack.item(ResourceLocation.tryParse("gtceu:quantum_star"), "Quantum Star", 2.0)
                ),
                List.of()
        );

        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec(
                "Layer II",
                800.0,
                245760.0,
                List.of(
                        IngredientStack.item(ResourceLocation.tryParse("gtceu:naquadah_alloy_foil_ream"), "Naquadah Foil Ream", 1.0),
                        IngredientStack.fluid(ResourceLocation.tryParse("gtceu:lubricant"), "Lubricant", 25000.0)
                ),
                List.of()
        );

        CompoundRecipeBuilder.LayerSpec layer3 = new CompoundRecipeBuilder.LayerSpec(
                "Layer III",
                800.0,
                245760.0,
                List.of(
                        IngredientStack.item(ResourceLocation.tryParse("gtceu:netherite_foil_ream"), "Netherite Foil Ream", 1.0),
                        IngredientStack.fluid(ResourceLocation.tryParse("gtceu:lubricant"), "Lubricant", 25000.0)
                ),
                List.of()
        );

        CompoundRecipeBuilder.LayerSpec layer4 = new CompoundRecipeBuilder.LayerSpec(
                "Layer IV",
                800.0,
                245760.0,
                List.of(
                        IngredientStack.item(ResourceLocation.tryParse("gtceu:naquadah_alloy_foil_ream"), "Naquadah Foil Ream", 1.0),
                        IngredientStack.fluid(ResourceLocation.tryParse("gtceu:lubricant"), "Lubricant", 25000.0)
                ),
                List.of(
                        IngredientStack.item(ResourceLocation.tryParse("kubejs:raw_stargate_rod"), "Raw Stargate Rod", 1.0)
                )
        );

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                3200.0,
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2, layer3, layer4),
                100,
                100
        );

        Assertions.assertNotNull(cluster);
        Assertions.assertEquals(4, cluster.nodes().size());
        Assertions.assertNotNull(cluster.frame());
        Assertions.assertTrue(cluster.frame().isCompoundFrame());

        RecipeNode master = cluster.nodes().get(0);
        Assertions.assertTrue(master.isCompoundNode());
        Assertions.assertTrue(master.isCompoundMaster());
        Assertions.assertEquals(0, master.getCompoundLayerIndex());
        Assertions.assertEquals(4, master.getCompoundTotalLayers());

        for (int i = 1; i < 4; i++) {
            RecipeNode slave = cluster.nodes().get(i);
            Assertions.assertTrue(slave.isCompoundNode());
            Assertions.assertFalse(slave.isCompoundMaster());
            Assertions.assertEquals(i, slave.getCompoundLayerIndex());
            Assertions.assertEquals(4, slave.getCompoundTotalLayers());
            Assertions.assertEquals(master.getCompoundGroupId(), slave.getCompoundGroupId());
        }

        Assertions.assertNotNull(cluster.internalEdges());
    }
}
