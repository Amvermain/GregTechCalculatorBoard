package com.gtceu.calcboard;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.integration.jei.JeiRecipeConverter;
import com.gtceu.calcboard.integration.jei.JeiRecipeLayoutCollector;
import com.gtceu.calcboard.integration.jei.JeiRecipeSearchIndexer;
import com.gtceu.calcboard.integration.jei.JeiRecipeViewerAdapter;
import com.gtceu.calcboard.integration.jei.JeiRecipeWrapper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class JeiRecipeIntegrationTest {

    static {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            java.lang.reflect.Field field = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            if (!field.getBoolean(null)) {
                net.minecraft.server.Bootstrap.bootStrap();
            }
        } catch (Throwable ignored) {}
    }

    private static class MockRecipeCategory<T> implements IRecipeCategory<T> {
        private final RecipeType<T> recipeType;
        private final Component title;

        public MockRecipeCategory(RecipeType<T> recipeType, String title) {
            this.recipeType = recipeType;
            this.title = Component.literal(title);
        }

        @Override
        public RecipeType<T> getRecipeType() {
            return recipeType;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public int getWidth() {
            return 176;
        }

        @Override
        public int getHeight() {
            return 166;
        }

        @Override
        public IDrawable getBackground() {
            return null;
        }

        @Override
        public IDrawable getIcon() {
            return null;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
            if (recipe instanceof MockCraftingRecipe cr) {
                for (ItemStack in : cr.inputs) {
                    builder.addSlot(RecipeIngredientRole.INPUT, 0, 0).addItemStack(in);
                }
                for (ItemStack out : cr.outputs) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, 0, 0).addItemStack(out);
                }
            } else if (recipe instanceof net.minecraft.world.item.crafting.SmeltingRecipe sr) {
                for (ItemStack in : sr.getIngredients().get(0).getItems()) {
                    builder.addSlot(RecipeIngredientRole.INPUT, 0, 0).addItemStack(in);
                }
                builder.addSlot(RecipeIngredientRole.OUTPUT, 0, 0).addItemStack(sr.getResultItem(null));
            }
        }

        @Nullable
        @Override
        public ResourceLocation getRegistryName(T recipe) {
            if (recipe instanceof MockCraftingRecipe mcr) {
                return mcr.id;
            }
            return null;
        }
    }

    public static class MockCraftingRecipe {
        public final ResourceLocation id;
        public final List<ItemStack> inputs;
        public final List<ItemStack> outputs;

        public MockCraftingRecipe(ResourceLocation id, List<ItemStack> inputs, List<ItemStack> outputs) {
            this.id = id;
            this.inputs = inputs;
            this.outputs = outputs;
        }
    }

    private static ItemStack createItem(String modId, String path, int count) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(modId + ":" + path));
        if (item != null) {
            return new ItemStack(item, count);
        }
        return ItemStack.EMPTY;
    }

    private static FluidStack createFluid(String modId, String path, int amount) {
        var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(modId + ":" + path));
        if (fluid != null) {
            return new FluidStack(fluid, amount);
        }
        return FluidStack.EMPTY;
    }

    @Test
    public void testJeiRecipeLayoutCollector() {
        JeiRecipeLayoutCollector collector = new JeiRecipeLayoutCollector();

        collector.addSlot(RecipeIngredientRole.INPUT, 10, 20)
                .addItemStack(new ItemStack(Items.IRON_INGOT, 3));
        collector.addSlot(RecipeIngredientRole.INPUT, 30, 20)
                .addFluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        collector.addSlot(RecipeIngredientRole.OUTPUT, 50, 20)
                .addItemStack(new ItemStack(Items.IRON_BLOCK, 1));

        Assertions.assertEquals(3, collector.getSlots().size());
        Assertions.assertFalse(collector.isShapeless());

        collector.setShapeless();
        Assertions.assertTrue(collector.isShapeless());

        List<IngredientStack> inputs = collector.extractInputs();
        List<IngredientStack> outputs = collector.extractOutputs();

        Assertions.assertEquals(2, inputs.size());
        Assertions.assertEquals(1, outputs.size());

        IngredientStack ironIn = inputs.get(0);
        Assertions.assertEquals("minecraft:iron_ingot", ironIn.getId().toString());
        Assertions.assertEquals(3.0, ironIn.getAmount(), 0.001);

        IngredientStack waterIn = inputs.get(1);
        Assertions.assertEquals("minecraft:water", waterIn.getId().toString());
        Assertions.assertEquals(1000.0, waterIn.getAmount(), 0.001);

        IngredientStack ironBlockOut = outputs.get(0);
        Assertions.assertEquals("minecraft:iron_block", ironBlockOut.getId().toString());
        Assertions.assertEquals(1.0, ironBlockOut.getAmount(), 0.001);
    }

    @Test
    public void testJeiRecipeWrapperAndConversion() {
        RecipeType<MockCraftingRecipe> recipeType = RecipeType.create("gtceu", "assembler", MockCraftingRecipe.class);
        MockRecipeCategory<MockCraftingRecipe> category = new MockRecipeCategory<>(recipeType, "Assembler");

        MockCraftingRecipe recipe = new MockCraftingRecipe(
                ResourceLocation.tryParse("gtceu:assembler_iron_block"),
                List.of(new ItemStack(Items.IRON_INGOT, 9)),
                List.of(new ItemStack(Items.IRON_BLOCK, 1))
        );

        JeiRecipeWrapper<MockCraftingRecipe> wrapper = new JeiRecipeWrapper<>(category, recipe);
        Assertions.assertEquals("gtceu:assembler", wrapper.getCategoryId().toString());
        Assertions.assertEquals("gtceu:assembler_iron_block", wrapper.getRecipeId().toString());
        Assertions.assertEquals("gtceu", wrapper.getModId());
        Assertions.assertEquals("Assembler", wrapper.getCategoryTitle().getString());

        RecipeNode node = JeiRecipeConverter.convert(wrapper);
        Assertions.assertNotNull(node);
        Assertions.assertEquals("gtceu:assembler", node.getRecipeCategoryId().toString());
        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(1, node.getOutputs().size());
        Assertions.assertEquals("minecraft:iron_ingot", node.getInputs().get(0).getId().toString());
        Assertions.assertEquals(9.0, node.getInputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals("minecraft:iron_block", node.getOutputs().get(0).getId().toString());
        Assertions.assertEquals(1.0, node.getOutputs().get(0).getAmount(), 0.001);
    }

    @Test
    public void testJeiRecipeSearchIndexing() {
        RecipeType<MockCraftingRecipe> recipeType = RecipeType.create("minecraft", "crafting", MockCraftingRecipe.class);
        MockRecipeCategory<MockCraftingRecipe> category = new MockRecipeCategory<>(recipeType, "Crafting");

        MockCraftingRecipe recipe = new MockCraftingRecipe(
                ResourceLocation.tryParse("minecraft:crafting_iron_block"),
                List.of(new ItemStack(Items.IRON_INGOT, 9)),
                List.of(new ItemStack(Items.IRON_BLOCK, 1))
        );

        JeiRecipeWrapper<MockCraftingRecipe> wrapper = new JeiRecipeWrapper<>(category, recipe);
        RecipeSearchEngine.SearchableRecipe sr = JeiRecipeSearchIndexer.buildIndex(wrapper, null);

        Assertions.assertNotNull(sr);
        Assertions.assertEquals("minecraft:crafting_iron_block", sr.recipeId().toString());
        Assertions.assertEquals("minecraft", sr.modId());
        Assertions.assertEquals("crafting", sr.categoryId());
        Assertions.assertTrue(sr.inputSearchIndex().contains("iron_ingot"));
        Assertions.assertTrue(sr.outputSearchIndex().contains("iron_block"));
    }

    @Test
    public void testJeiRecipeViewerAdapterMethods() {
        JeiRecipeViewerAdapter adapter = new JeiRecipeViewerAdapter();
        Assertions.assertEquals("jei", adapter.getViewerId());
        Assertions.assertEquals(50, adapter.getPriority());

        RecipeType<MockCraftingRecipe> recipeType = RecipeType.create("minecraft", "crafting", MockCraftingRecipe.class);
        MockCraftingRecipe recipe = new MockCraftingRecipe(
                ResourceLocation.tryParse("minecraft:crafting_iron_block"),
                List.of(new ItemStack(Items.IRON_INGOT, 9)),
                List.of(new ItemStack(Items.IRON_BLOCK, 1))
        );
        MockRecipeCategory<MockCraftingRecipe> category = new MockRecipeCategory<>(recipeType, "Crafting");
        JeiRecipeWrapper<MockCraftingRecipe> wrapper = new JeiRecipeWrapper<>(category, recipe);
        RecipeNode node = adapter.convertToNode(wrapper);
        Assertions.assertNotNull(node);
    }

    @Test
    public void testVanillaSmeltingRecipeExtraction() {
        net.minecraft.world.item.crafting.SmeltingRecipe smeltingRecipe = new net.minecraft.world.item.crafting.SmeltingRecipe(
                ResourceLocation.tryParse("minecraft:iron_ingot_from_smelting_raw_iron"),
                "",
                net.minecraft.world.item.crafting.CookingBookCategory.MISC,
                net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.RAW_IRON),
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
                0.7f,
                200
        );

        RecipeType<net.minecraft.world.item.crafting.SmeltingRecipe> recipeType = RecipeType.create("minecraft", "smelting", net.minecraft.world.item.crafting.SmeltingRecipe.class);
        MockRecipeCategory<net.minecraft.world.item.crafting.SmeltingRecipe> category = new MockRecipeCategory<>(recipeType, "Smelting");
        JeiRecipeWrapper<net.minecraft.world.item.crafting.SmeltingRecipe> wrapper = new JeiRecipeWrapper<>(category, smeltingRecipe);

        RecipeNode node = JeiRecipeConverter.convert(wrapper);
        Assertions.assertNotNull(node);
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:lp_steam_furnace"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:hp_steam_furnace"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:lv_electric_furnace"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:mv_electric_furnace"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:multi_smelter"));

        Assertions.assertEquals("Smelting (Raw Iron)", node.getName());
        Assertions.assertEquals(200.0, node.getBaseDurationTicks());
        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:raw_iron"), node.getInputs().get(0).getId());
        Assertions.assertEquals(1, node.getOutputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:iron_ingot"), node.getOutputs().get(0).getId());
        Assertions.assertNotNull(node.getMachineIcon());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:furnace"), node.getMachineIcon());
        Assertions.assertFalse(node.getAvailableWorkstations().isEmpty());

        var indexed = JeiRecipeSearchIndexer.buildIndex(wrapper, null);
        Assertions.assertNotNull(indexed);
        Assertions.assertEquals("Iron Ingot", indexed.displayName());
        Assertions.assertTrue(indexed.inputIndex().contains("raw_iron"));
        Assertions.assertTrue(indexed.outputIndex().contains("iron_ingot"));

        // Switch to GTCEu Multi Smelter
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:multi_smelter"));
        Assertions.assertEquals(4.0, node.getBaseEUt());
        Assertions.assertEquals(128.0, node.getBaseDurationTicks());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.EnergyType.ELECTRIC_EU, node.getEnergyType());
        Assertions.assertTrue(node.isMultiblock());

        // Switch back to Vanilla Furnace
        node.setMachineIcon(ResourceLocation.tryParse("minecraft:furnace"));
        Assertions.assertEquals(0.0, node.getBaseEUt());
        Assertions.assertEquals(200.0, node.getBaseDurationTicks());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.EnergyType.NONE, node.getEnergyType());
        Assertions.assertFalse(node.isMultiblock());

        // Test NodeWidget changeTier progression from Passive -> LP Steam -> HP Steam -> LV -> MV
        com.gtceu.calcboard.client.gui.widget.NodeWidget widget = new com.gtceu.calcboard.client.gui.widget.NodeWidget(node, null);
        
        // 1. Step up: Passive -> LP Steam
        boolean stepped1 = widget.changeTier(1);
        Assertions.assertTrue(stepped1);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE, node.getSteamMode());

        // 2. Step up: LP Steam -> HP Steam
        boolean stepped2 = widget.changeTier(1);
        Assertions.assertTrue(stepped2);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.SteamMode.HIGH_PRESSURE, node.getSteamMode());

        // 3. Step up: HP Steam -> LV Electric
        boolean stepped3 = widget.changeTier(1);
        Assertions.assertTrue(stepped3);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.SteamMode.NONE, node.getSteamMode());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.GTVoltageTier.LV, node.getTargetTier());
        Assertions.assertEquals(4.0, node.getBaseEUt());

        // 4. Step up: LV -> MV
        boolean stepped4 = widget.changeTier(1);
        Assertions.assertTrue(stepped4);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.GTVoltageTier.MV, node.getTargetTier());

        // 5. Step down: MV -> LV
        boolean stepDown1 = widget.changeTier(-1);
        Assertions.assertTrue(stepDown1);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.GTVoltageTier.LV, node.getTargetTier());

        // 6. Step down: LV -> HP Steam
        boolean stepDown2 = widget.changeTier(-1);
        Assertions.assertTrue(stepDown2);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.SteamMode.HIGH_PRESSURE, node.getSteamMode());

        // 7. Step down: HP Steam -> LP Steam
        boolean stepDown3 = widget.changeTier(-1);
        Assertions.assertTrue(stepDown3);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE, node.getSteamMode());

        // 8. Step down: LP Steam -> Passive
        boolean stepDown4 = widget.changeTier(-1);
        Assertions.assertTrue(stepDown4);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.SteamMode.NONE, node.getSteamMode());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.EnergyType.NONE, node.getEnergyType());
        Assertions.assertEquals(0.0, node.getBaseEUt());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:furnace"), node.getMachineIcon());

        // 9. Test Mouse Click on Passive Banner (row 2 at y + 44)
        node.setPosX(100);
        node.setPosY(100);
        int bannerCenterX = 100 + 50;
        int bannerCenterY = 100 + 20 + 6 + 18 + 7; // y + HEADER_HEIGHT + 6 + 18 + 7 = 151
        boolean clicked = widget.mouseClicked(bannerCenterX, bannerCenterY, 0); // Left Click
        Assertions.assertTrue(clicked, "Click on Passive banner should trigger tier step up to LP Steam");
        Assertions.assertEquals(com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE, node.getSteamMode());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:lp_steam_furnace"), node.getMachineIcon());
    }

    @Test
    public void testJeiSparseWorkstationTierDeduction() {
        // Simulate a node created from JEI where only a single catalyst workstation (e.g. lv_macerator) was injected
        RecipeNode node = RecipeNode.create("Macerator (Naquadah Ingot)", 400.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_macerator"));
        node.getAvailableWorkstations().clear();
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:lv_macerator"));

        // 1. Resolve higher tiers (MV, HV, EV, ..., OpV, MAX) - must deductively derive gtceu:<tier>_macerator
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:mv_macerator"), node.getWorkstationForTier(GTVoltageTier.MV));
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:hv_macerator"), node.getWorkstationForTier(GTVoltageTier.HV));
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:ev_macerator"), node.getWorkstationForTier(GTVoltageTier.EV));
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:opv_macerator"), node.getWorkstationForTier(GTVoltageTier.OpV));
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:max_macerator"), node.getWorkstationForTier(GTVoltageTier.MAX));

        // 2. NodeWidget step-up all the way to MAX tier and verify machineIcon changes at each step
        com.gtceu.calcboard.client.gui.widget.NodeWidget widget = new com.gtceu.calcboard.client.gui.widget.NodeWidget(node, null);
        for (int i = GTVoltageTier.LV.ordinal() + 1; i <= GTVoltageTier.MAX.ordinal(); i++) {
            boolean stepped = widget.changeTier(1);
            Assertions.assertTrue(stepped, "Stepping up to tier ordinal " + i + " must succeed");
            GTVoltageTier expectedTier = GTVoltageTier.getByIndex(i);
            Assertions.assertEquals(expectedTier, node.getTargetTier());
            Assertions.assertEquals(
                    ResourceLocation.tryParse("gtceu:" + expectedTier.name().toLowerCase(java.util.Locale.ROOT) + "_macerator"),
                    node.getMachineIcon(),
                    "Machine icon must update to " + expectedTier.name() + " macerator"
            );
        }
    }

    @Test
    public void testJeiSparseMultiblockWorkstationDeduction() {
        RecipeNode node = RecipeNode.create("Macerator (Naquadah Ingot)", 400.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_macerator"));
        node.getAvailableWorkstations().clear();
        // JEI catalyst only returned miners or sparse list
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:lv_macerator"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:advanced_large_miner_iii"));

        // Multiblock workstations must deductively discover and prioritize large_macerator
        List<ResourceLocation> mbList = node.getMultiblockWorkstations();
        Assertions.assertFalse(mbList.isEmpty());
        Assertions.assertTrue(mbList.contains(ResourceLocation.tryParse("gtceu:large_macerator")));
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:large_macerator"), node.getMultiblockWorkstation(), "Primary multiblock must be large_macerator");
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:large_macerator"), mbList.get(0));
    }

    @Test
    public void testJeiMaceratorByproductTierGating() {
        RecipeNode node = RecipeNode.create("Macerator (Raw Chicken)", 400.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:chicken"), "Raw Chicken", 1, 1.0f));
        // Byproduct (slot 1) with 50% base chance and 5% tier boost
        IngredientStack byproduct = IngredientStack.item(ResourceLocation.tryParse("minecraft:bone_meal"), "Bone Meal", 1, 0.5f);
        byproduct.setTierChanceBoost(0.05);
        node.getOutputs().add(byproduct);

        // 1. At LV / MV: Macerator ore byproduct tier gating suppresses slot 1 (requires HV+) -> chance must be 0.0!
        node.setTargetTier(GTVoltageTier.LV);
        Assertions.assertEquals(0.0, node.getEffectiveOutputChance(1), 0.001, "At LV, 1st byproduct must be 0%");
        Assertions.assertEquals(0.0, node.getOutputSlotRate(1, false), 0.001, "At LV, byproduct output rate must be 0");

        node.setTargetTier(GTVoltageTier.MV);
        Assertions.assertEquals(0.0, node.getEffectiveOutputChance(1), 0.001, "At MV, 1st byproduct must still be 0%");
        Assertions.assertEquals(0.0, node.getOutputSlotRate(1, false), 0.001, "At MV, byproduct output rate must be 0");

        // 2. At HV: 1st byproduct unlocks at base 50%
        node.setTargetTier(GTVoltageTier.HV);
        Assertions.assertEquals(0.5, node.getEffectiveOutputChance(1), 0.001, "At HV, 1st byproduct unlocks at base chance");
        Assertions.assertTrue(node.getOutputSlotRate(1, false) > 0.0, "At HV, byproduct rate must be active");

        // 3. At EV (HV + 1): 1st byproduct boosted by +5% = 55%
        node.setTargetTier(GTVoltageTier.EV);
        Assertions.assertEquals(0.55, node.getEffectiveOutputChance(1), 0.001, "At EV, 1st byproduct receives tier boost");
    }

    @Test
    public void testJeiMultipleOutputsSameItemDifferentChances() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:autoclave"), "Autoclave", 20, 30, GTVoltageTier.HV);
        ResourceLocation diamondId = ResourceLocation.tryParse("minecraft:diamond");
        node.getOutputs().add(IngredientStack.item(diamondId, "Diamond", 1.0, 0.50));
        node.getOutputs().add(IngredientStack.item(diamondId, "Diamond", 1.0, 0.45));

        Assertions.assertEquals(2, node.getOutputs().size());
        Assertions.assertEquals(0.50, node.getOutputs().get(0).getChance(), 1e-4, "Slot 0 chance must be 50%");
        Assertions.assertEquals(0.45, node.getOutputs().get(1).getChance(), 1e-4, "Slot 1 chance must be 45%");

        Assertions.assertEquals(0.50, node.getEffectiveOutputChance(0), 1e-4);
        Assertions.assertEquals(0.45, node.getEffectiveOutputChance(1), 1e-4);
    }
}
