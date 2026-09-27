package com.gtceu.calcboard.compat.systeams;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class SysteamsBoilerAlternativeScrollRegressionTest {

    @BeforeAll
    public static void setup() {
        ModAdapterRegistry.init();
    }

    @Test
    @DisplayName("Reproduction test: Boiler alternative fluid scroll should not duplicate outputs on board reopen")
    public void testBoilerFluidScrollOutputDuplicationOnReopen() {
        RecipeNode dynamo = RecipeNode.create("Lapidary Dynamo", 1500.0, 50.0, GTVoltageTier.LV);
        dynamo.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        dynamo.setMachineIcon(ResourceLocation.tryParse("thermal:dynamo_lapidary"));
        IngredientStack diamondIn = IngredientStack.item(ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0);
        diamondIn.setAlternatives(List.of(
                ResourceLocation.tryParse("minecraft:diamond"),
                ResourceLocation.tryParse("minecraft:emerald")
        ));
        dynamo.addInput(diamondIn);
        dynamo.setBaseSpec(RecipeSpec.of(
                dynamo.getId(),
                dynamo.getRecipeCategoryId(),
                dynamo.getBaseDurationTicks(),
                dynamo.getBaseEUt(),
                dynamo.getInputs(),
                dynamo.getOutputs()
        ));

        Assertions.assertTrue(SysteamsRecipeHandler.isDynamoToBoilerConvertible(dynamo));
        SysteamsRecipeHandler.toggleDynamoBoilerMode(dynamo);
        Assertions.assertEquals(1, dynamo.getOutputs().size(), "Immediately after switching to boiler, outputs must be 1");

        SysteamsRecipeHandler.updateBoilerFluidRecipe(dynamo, ResourceLocation.tryParse("gtceu:steam"));
        SysteamsRecipeHandler.updateBoilerFluidRecipe(dynamo, ResourceLocation.tryParse("systeams:steamier"));
        SysteamsRecipeHandler.updateBoilerFluidRecipe(dynamo, ResourceLocation.tryParse("systeams:steamiest"));
        SysteamsRecipeHandler.updateBoilerFluidRecipe(dynamo, ResourceLocation.tryParse("systeams:steamiester"));

        CompoundTag serialized = RecipeNodeSerializer.serialize(dynamo);
        RecipeNode restored = RecipeNodeSerializer.deserialize(serialized);

        Assertions.assertNotNull(restored);
        Assertions.assertEquals(1, restored.getOutputs().size(),
                "Restored boiler node must have exactly 1 output! Found: " + restored.getOutputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("systeams:steamiestest"), restored.getOutputs().get(0).getId());
        Assertions.assertEquals(2, restored.getInputs().size(),
                "Restored boiler node must have exactly 2 inputs (fuel + boiling fluid)");
    }
}
