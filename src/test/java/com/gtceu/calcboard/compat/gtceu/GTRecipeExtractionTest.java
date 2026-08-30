package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GTRecipeExtractionTest {

    public static class StructuralGTRecipeFixture {
        public final Object recipeType = new Object();
        public final Map<Object, List<Object>> inputs = new HashMap<>();
        public final Map<Object, List<Object>> outputs = new HashMap<>();
        public final Map<Object, List<Object>> tickInputs = new HashMap<>();
        public final Map<Object, List<Object>> tickOutputs = new HashMap<>();
        public int duration;
    }

    public static class MockGTRecipe extends StructuralGTRecipeFixture {
        public int duration = 160;
        public CompoundTag data = new CompoundTag();

        public long getInputEUt() {
            return 65536L;
        }

        public long getOutputEUt() {
            return 0L;
        }
    }

    public static class MockGTGeneratorRecipe extends StructuralGTRecipeFixture {
        public long duration = 40L;

        public long getInputEUt() {
            return 0L;
        }

        public long getOutputEUt() {
            return 2048L;
        }
    }

    public static class BacterialHarvestingCustomRecipe {
        private final int dur = 160;

        public int duration() {
            return dur;
        }

        public long getInputEUt() {
            return 65536L;
        }
    }

    public static class MockMapEnergyRecipe extends StructuralGTRecipeFixture {

        public MockMapEnergyRecipe() {
            duration = 100;
            tickInputs.put("gtceu:eur_capability", List.of(65536L));
        }
    }

    public static class MockEnergyContent {
        private final Long content;

        public MockEnergyContent(long content) {
            this.content = content;
        }

        public Object getContent() {
            return content;
        }
    }

    public static class GTRecipeWrapperFixture {
        public final Object recipe;

        public GTRecipeWrapperFixture(Object recipe) {
            this.recipe = recipe;
        }
    }

    public static class PrivateBackingRecipeWrapperFixture {
        private final Object backing;

        public PrivateBackingRecipeWrapperFixture(Object backing) {
            this.backing = backing;
        }
    }

    public static class UnrelatedGTRecipeWrapperFixture {
        public final Object recipe = new Object();

        public long getInputEUt() {
            throw new NoClassDefFoundError("optional/dependency/ShouldNotBeLoaded");
        }
    }

    public static class ShadowedBackingBaseFixture {
        public final Object recipe;

        public ShadowedBackingBaseFixture(Object recipe) {
            this.recipe = recipe;
        }
    }

    public static class ShadowedBackingSubFixture extends ShadowedBackingBaseFixture {
        public Object recipe = null;

        public ShadowedBackingSubFixture(Object recipe) {
            super(recipe);
        }
    }

    public static class MockVoltageAmperageObject {
        public long voltage() {
            return 8192L;
        }

        public long amperage() {
            return 2L;
        }
    }

    public static class MockObjectEnergyRecipe {
        public int duration = 80;

        public Object getInputEUt() {
            return new MockVoltageAmperageObject();
        }
    }

    @Test
    public void testExtractGTRecipeWithLongInputEUt() {
        MockGTRecipe recipe = new MockGTRecipe();
        recipe.data.putInt("ebf_temp", 1800);

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(160.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(65536.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.ZPM, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertFalse(details.isGenerator);
        Assertions.assertEquals(1800, details.backingRecipeTemp);
    }

    @Test
    public void testExtractGTRecipeGeneratorWithOutputEUt() {
        MockGTGeneratorRecipe recipe = new MockGTGeneratorRecipe();

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(40.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(2048.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.EV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertTrue(details.isGenerator);
    }

    @Test
    public void testExtractGTRecipeWithCustomSubclass() {
        BacterialHarvestingCustomRecipe recipe = new BacterialHarvestingCustomRecipe();

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(160.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(65536.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.ZPM, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertFalse(details.isGenerator);
    }

    @Test
    public void testExtractEnergyFromMapInspection() {
        MockMapEnergyRecipe recipe = new MockMapEnergyRecipe();

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(100.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(65536.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.ZPM, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
    }

    @Test
    public void testUnwrapsJeiWrapperBeforeExtractingInputDetails() {
        StructuralGTRecipeFixture recipe = new StructuralGTRecipeFixture();
        recipe.duration = 120;
        recipe.tickInputs.put("gtceu:eu_recipe_capability", List.of(new MockEnergyContent(8192L)));
        GTRecipeWrapperFixture wrapper = new GTRecipeWrapperFixture(recipe);

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(wrapper));
        Assertions.assertSame(recipe, GTCEuRecipeHandler.unwrapRecipe(wrapper));
        Assertions.assertNotSame(wrapper, GTCEuRecipeHandler.unwrapRecipe(wrapper), "JEI wrapper must not be treated as the recipe body");

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(wrapper, details);

        Assertions.assertEquals(120.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(8192.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.IV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertFalse(details.isGenerator);
    }

    @Test
    public void testUnwrapsPrivateBackingFieldAndExtractsOutputDetails() {
        StructuralGTRecipeFixture recipe = new StructuralGTRecipeFixture();
        recipe.duration = 40;
        recipe.tickOutputs.put("gtceu:eu_recipe_capability", List.of(new MockEnergyContent(2048L)));
        PrivateBackingRecipeWrapperFixture wrapper = new PrivateBackingRecipeWrapperFixture(recipe);

        Assertions.assertSame(recipe, GTCEuRecipeHandler.unwrapRecipe(wrapper));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(wrapper, details);

        Assertions.assertEquals(40.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(2048.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.EV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertTrue(details.isGenerator);
    }

    @Test
    public void testUnrelatedRecipeIsNotAcceptedByNameOrEnergyMethod() {
        UnrelatedGTRecipeWrapperFixture unrelated = new UnrelatedGTRecipeWrapperFixture();

        Assertions.assertFalse(GTCEuRecipeHandler.isGTRecipe(unrelated));
        Assertions.assertSame(unrelated, GTCEuRecipeHandler.unwrapRecipe(unrelated));
    }

    @Test
    public void testUnwrapsShadowedBackingFieldFromSuperclass() {
        StructuralGTRecipeFixture recipe = new StructuralGTRecipeFixture();
        recipe.duration = 90;
        recipe.tickInputs.put("gtceu:eu_recipe_capability", List.of(new MockEnergyContent(128L)));
        ShadowedBackingSubFixture wrapper = new ShadowedBackingSubFixture(recipe);

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(wrapper));
        Assertions.assertSame(recipe, GTCEuRecipeHandler.unwrapRecipe(wrapper));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(wrapper, details);

        Assertions.assertEquals(90.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(128.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.MV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
    }

    @Test
    public void testGTCategoryNamespaceDetection() {
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("gtceu"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("start_core"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("gtceu_start"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("start"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("star_technology"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("GTCEU"));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace("minecraft"));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace("create"));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace(""));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace(null));
    }

    @Test
    public void testExtractEnergyFromObjectWithVoltageAndAmperage() {
        MockObjectEnergyRecipe recipe = new MockObjectEnergyRecipe();

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(80.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(16384.0, details.eut, 1e-6); // 8192 * 2
        Assertions.assertEquals(GTVoltageTier.LuV, details.tier); // 16384 <= 32768 (LuV)
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
    }

    @Test
    public void testExtractDurationFromTagAndMethods() {
        MockGTRecipe recipe = new MockGTRecipe();
        recipe.duration = 0;
        recipe.data.putDouble("duration", 240.0);

        double dur = GTCEuRecipeHandler.extractDuration(recipe);
        Assertions.assertEquals(240.0, dur, 1e-6);
    }

    public static class MockMultipleOutputGTRecipe {
        public int duration = 100;
        public Map<String, List<Object>> outputs = new HashMap<>();

        public static class MockContent {
            public Object content;
            public int chance;
            public int tierChanceBoost;

            public MockContent(Object content, int chance, int tierChanceBoost) {
                this.content = content;
                this.chance = chance;
                this.tierChanceBoost = tierChanceBoost;
            }
        }

        public MockMultipleOutputGTRecipe() {
            outputs.put("gtceu:item", List.of(
                    new MockContent(IngredientStack.item(net.minecraft.resources.ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0), 5000, 0),
                    new MockContent(IngredientStack.item(net.minecraft.resources.ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0), 4500, 0)
            ));
        }
    }

    public static class MockTagInputGTRecipe {
        public int duration = 100;
        public Map<String, List<Object>> inputs = new HashMap<>();

        public static class SizedTagIngredient extends net.minecraft.world.item.crafting.Ingredient {
            private final net.minecraft.world.item.ItemStack[] stacks;
            private final long amount;

            public SizedTagIngredient(long amount, net.minecraft.world.item.ItemStack... stacks) {
                super(java.util.stream.Stream.empty());
                this.stacks = stacks;
                this.amount = amount;
            }

            @Override
            public net.minecraft.world.item.ItemStack[] getItems() {
                return stacks;
            }

            public long getAmount() {
                return amount;
            }
        }

        public static class SizedTagContent {
            public Object content;

            public SizedTagContent(Object content) {
                this.content = content;
            }
        }

        public MockTagInputGTRecipe() {
            inputs.put("gtceu:item", List.of(new SizedTagContent(new SizedTagIngredient(4,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT)))));
        }
    }

    public static class MockFluidTagInputGTRecipe {
        public int duration = 100;
        public Map<String, List<Object>> inputs = new HashMap<>();

        public static class MockFluidIngredient {
            private final net.minecraftforge.fluids.FluidStack[] stacks;

            public MockFluidIngredient(net.minecraftforge.fluids.FluidStack... stacks) {
                this.stacks = stacks;
            }

            public net.minecraftforge.fluids.FluidStack[] getStacks() {
                return stacks;
            }
        }

        public static class FluidContent {
            public Object content;

            public FluidContent(Object content) {
                this.content = content;
            }
        }

        public MockFluidTagInputGTRecipe() {
            inputs.put("gtceu:fluid", List.of(new FluidContent(new MockFluidIngredient(
                    new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                    new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000)))));
        }
    }

    @Test
    public void testTagItemInputPreservesAllAlternatives() {
        MockTagInputGTRecipe recipe = new MockTagInputGTRecipe();
        List<IngredientStack> extracted = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "inputs");

        Assertions.assertEquals(1, extracted.size());
        IngredientStack in = extracted.get(0);
        Assertions.assertEquals(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_ingot"), in.getId());
        Assertions.assertEquals(4.0, in.getAmount(), 1e-6);
        Assertions.assertTrue(in.hasAlternatives());
        Assertions.assertEquals(3, in.getAlternatives().size());
        Assertions.assertTrue(in.getAlternatives().contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:copper_ingot")));
        Assertions.assertTrue(in.getAlternatives().contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:gold_ingot")));

        in.cycleAlternative(1);
        Assertions.assertEquals(net.minecraft.resources.ResourceLocation.tryParse("minecraft:copper_ingot"), in.getId());
        Assertions.assertTrue(in.selectAlternative(net.minecraft.resources.ResourceLocation.tryParse("minecraft:gold_ingot")));
        Assertions.assertEquals(net.minecraft.resources.ResourceLocation.tryParse("minecraft:gold_ingot"), in.getId());

        IngredientStack copied = in.copy();
        Assertions.assertEquals(3, copied.getAlternatives().size());
        Assertions.assertEquals(in.getId(), copied.getId());
    }

    @Test
    public void testFluidTagInputPreservesAllAlternatives() {
        MockFluidTagInputGTRecipe recipe = new MockFluidTagInputGTRecipe();
        List<IngredientStack> extracted = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "inputs");

        Assertions.assertEquals(1, extracted.size());
        IngredientStack in = extracted.get(0);
        Assertions.assertEquals(net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), in.getId());
        Assertions.assertTrue(in.hasAlternatives());
        Assertions.assertEquals(2, in.getAlternatives().size());
        Assertions.assertTrue(in.getAlternatives().contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:lava")));
        Assertions.assertEquals(1000.0, in.getAmount(), 1e-6);
    }

    public static class MockLdlibFluidStack {
        private final net.minecraft.world.level.material.Fluid fluid;
        private final long amount;

        public MockLdlibFluidStack(net.minecraft.world.level.material.Fluid fluid, long amount) {
            this.fluid = fluid;
            this.amount = amount;
        }

        public net.minecraft.world.level.material.Fluid getFluid() {
            return fluid;
        }

        public long getAmount() {
            return amount;
        }

        public Object getDisplayName() {
            throw new IllegalStateException("headless");
        }
    }

    public static class MockLdlibFluidIngredient {
        public Object[] getStacks() {
            return new Object[]{
                    new MockLdlibFluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                    new MockLdlibFluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000)
            };
        }
    }

    public static class MockGT14MixedOutputRecipe {
        public int duration = 720;
        public Map<String, List<Object>> inputs = new HashMap<>();
        public Map<String, List<Object>> outputs = new HashMap<>();

        public static class ContentHolder {
            public Object content;
            public int chance = 10000;
            public int maxChance = 10000;
            public int tierChanceBoost = 0;

            public ContentHolder(Object content) {
                this.content = content;
            }
        }

        public MockGT14MixedOutputRecipe() {
            inputs.put("gtceu:fluid", List.of(new ContentHolder(new MockLdlibFluidIngredient())));
            outputs.put("gtceu:item", List.of(new ContentHolder(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 3))));
            outputs.put("gtceu:fluid", List.of(new ContentHolder(new MockLdlibFluidIngredient())));
        }
    }

    @Test
    public void testLDLibFluidIngredientOutputsAreExtracted() {
        MockGT14MixedOutputRecipe recipe = new MockGT14MixedOutputRecipe();

        List<IngredientStack> outs = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "outputs");
        Assertions.assertEquals(2, outs.size(), "item and ldlib fluid outputs must both be extracted");
        Assertions.assertEquals(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_nugget"), outs.get(0).getId());
        Assertions.assertEquals(net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), outs.get(1).getId());
        Assertions.assertTrue(outs.get(1).isFluid());
        Assertions.assertEquals(1000.0, outs.get(1).getAmount(), 1e-6);
        Assertions.assertTrue(outs.get(1).hasAlternatives());
        Assertions.assertTrue(outs.get(1).getAlternatives().contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:lava")));

        List<IngredientStack> ins = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "inputs");
        Assertions.assertEquals(1, ins.size());
        Assertions.assertEquals(net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), ins.get(0).getId());
    }

    @Test
    public void testExtractMultipleOutputsSameItemDifferentChances() {
        MockMultipleOutputGTRecipe recipe = new MockMultipleOutputGTRecipe();
        List<com.gtceu.calcboard.api.model.IngredientStack> extracted = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "outputs");

        Assertions.assertEquals(2, extracted.size());
        Assertions.assertEquals(0.50, extracted.get(0).getChance(), 1e-4, "First slot must be 50%");
        Assertions.assertEquals(0.45, extracted.get(1).getChance(), 1e-4, "Second slot must be 45%");
    }
}
