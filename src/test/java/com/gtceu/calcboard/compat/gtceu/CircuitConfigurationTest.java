package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.RecipeDetails;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.greate.GreateProperties;
import com.gtceu.calcboard.compat.gtceu.extractor.GTCEuCircuitNumberExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class CircuitConfigurationTest {

    @Test
    @DisplayName("Verify RecipeNode circuit number getter, setter, and default state")
    public void testRecipeNodeCircuitNumber() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Chemical Reactor", 20.0, 30.0, GTVoltageTier.LV);
        Assertions.assertEquals(-1, node.getCircuitNumber());

        node.setCircuitNumber(24);
        Assertions.assertEquals(24, node.getCircuitNumber());
        Assertions.assertEquals(24, node.getProperties().get(NodeProperties.CIRCUIT_NUMBER));

        // Test standard circuit number on greate-scoped node
        RecipeNode greateNode = RecipeNode.create(ResourceLocation.tryParse("greate:mechanical_press"), "Greate Press", 20.0, 30.0, GTVoltageTier.LV);
        greateNode.setCircuitNumber(5);
        Assertions.assertEquals(5, greateNode.getCircuitNumber());
    }

    @Test
    @DisplayName("Verify RecipeNode property serialization persists circuit number")
    public void testCircuitNumberSerialization() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:distillation_tower"), "Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        node.setCircuitNumber(12);

        CompoundTag serialized = node.getProperties().serializeNBT();
        Assertions.assertNotNull(serialized);

        NodePropertyStore restored = new NodePropertyStore();
        restored.deserializeNBT(serialized);
        Assertions.assertEquals(12, restored.get(NodeProperties.CIRCUIT_NUMBER));
    }

    @Test
    @DisplayName("Verify GTCEuRecipeDetailExtractor extracts circuit number from recipe data tag")
    public void testExtractCircuitNumberFromDataTag() {
        CompoundTag tag1 = new CompoundTag();
        tag1.putInt("Configuration", 18);
        Object recipe1 = new Object() {
            public CompoundTag data() { return tag1; }
        };
        Assertions.assertEquals(18, GTCEuRecipeDetailExtractor.extractCircuitNumber(recipe1));

        CompoundTag tag2 = new CompoundTag();
        tag2.putInt("circuit_config", 7);
        Object recipe2 = new Object() {
            public CompoundTag getData() { return tag2; }
        };
        Assertions.assertEquals(7, GTCEuRecipeDetailExtractor.extractCircuitNumber(recipe2));
    }

    @Test
    @DisplayName("Verify GTCEuRecipeDetailExtractor extracts circuit number from simulated inputs map")
    public void testExtractCircuitNumberFromInputsMap() {
        ItemStack circuitStack = new ItemStack(Items.REPEATER);
        circuitStack.getOrCreateTag().putInt("Configuration", 24);

        Object content = new Object() {
            public Object content() { return circuitStack; }
        };

        Map<String, List<Object>> inputsMap = Map.of("item", List.of(content));
        Object recipe = new Object() {
            public Map<String, List<Object>> inputs() { return inputsMap; }
        };

        Assertions.assertEquals(24, GTCEuRecipeDetailExtractor.extractCircuitNumber(recipe));

        RecipeDetails details = new RecipeDetails();
        GTCEuRecipeDetailExtractor.extractGTRecipeDetails(recipe, details);
        Assertions.assertEquals(24, details.circuitNumber);
    }

    @Test
    @DisplayName("Verify GTCEuCircuitNumberExtractor works in RecipePropertyExtractorPipeline")
    public void testCircuitNumberExtractorPipeline() {
        GTCEuCircuitNumberExtractor extractor = new GTCEuCircuitNumberExtractor();
        Assertions.assertEquals("gtceu", extractor.getModId());
        Assertions.assertTrue(extractor.matches(null, ResourceLocation.tryParse("gtceu:chemical_reactor")));

        CompoundTag tag = new CompoundTag();
        tag.putInt("Configuration", 3);
        Object recipe = new Object() {
            public CompoundTag data() { return tag; }
        };

        NodePropertyStore store = new NodePropertyStore();
        extractor.extract(recipe, tag, ResourceLocation.tryParse("gtceu:chemical_reactor"), store);
        Assertions.assertEquals(3, store.get(NodeProperties.CIRCUIT_NUMBER));
    }
}
