package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.PortRole;
import com.gtceu.calcboard.api.model.ProjectedPort;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import com.gtceu.calcboard.testutil.SimulatedGTEnvironment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class RecipeSpecPortProjectionTest {

    private static final ResourceLocation ROCKET_FUEL_ID = ResourceLocation.tryParse("gtceu:rocket_fuel");
    private static final ResourceLocation COMBUSTION_CAT = ResourceLocation.tryParse("gtceu:combustion_generator");
    private static final ResourceLocation MACERATOR_ID = ResourceLocation.tryParse("gtceu:macerator");
    private static final ResourceLocation COPPER_ORE = ResourceLocation.tryParse("minecraft:copper_ore");
    private static final ResourceLocation COPPER_DUST = ResourceLocation.tryParse("gtceu:copper_dust");

    @BeforeEach
    public void setUp() {
        SimulatedGTEnvironment.setupFullEnvironment();
        GTCombustionHelper.setForceStarTForTesting(true);
    }

    @AfterEach
    public void tearDown() {
        GTCombustionHelper.setForceStarTForTesting(false);
        SimulatedGTEnvironment.tearDownEnvironment();
    }

    @Test
    public void testRecipeSpecImmutability() {
        List<IngredientStack> inputs = new ArrayList<>();
        inputs.add(IngredientStack.fluid(ROCKET_FUEL_ID, "Rocket Fuel", 1000));
        List<IngredientStack> outputs = new ArrayList<>();

        RecipeSpec spec = new RecipeSpec(
                "recipe_rocket_fuel",
                COMBUSTION_CAT,
                72.0,
                160.0,
                inputs,
                outputs
        );

        inputs.add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:lubricant"), "Lubricant", 500));
        Assertions.assertEquals(1, spec.baseInputs().size());

        Assertions.assertThrows(UnsupportedOperationException.class, () ->
                spec.baseInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 200))
        );
        Assertions.assertThrows(UnsupportedOperationException.class, () ->
                spec.baseOutputs().add(IngredientStack.item(COPPER_DUST, "Copper Dust", 1))
        );
    }

    @Test
    public void testUS01AndUS02_MCFTransitionPreservesAndRestoresBaseRecipe() {
        RecipeNode node = new RecipeNode("combustion_node", "Combustion Generator", 72.0, 160.0, GTVoltageTier.HV);
        node.setMachineIcon(GTCombustionHelper.HV_COMBUSTION);
        node.setRecipeCategoryId(COMBUSTION_CAT);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setGenerator(true);

        IngredientStack rocketFuel = IngredientStack.fluid(ROCKET_FUEL_ID, "Rocket Fuel", 1000);
        RecipeSpec originalSpec = new RecipeSpec(
                "rocket_fuel_recipe",
                COMBUSTION_CAT,
                72.0,
                160.0,
                List.of(rocketFuel),
                List.of()
        );
        node.setBaseSpec(originalSpec);
        node.syncProjectedPorts();

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(ROCKET_FUEL_ID, node.getInputs().get(0).getId());

        node.setMachineIcon(GTCombustionHelper.START_MCF);
        node.setMultiblock(true);
        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(node);
        GTCombustionHelper.setMCFCoolantType(node, "deionized_water");

        node.syncProjectedPorts();

        boolean hasRocketFuelInInputs = node.getInputs().stream()
                .anyMatch(in -> ROCKET_FUEL_ID.equals(in.getId()));
        Assertions.assertFalse(hasRocketFuelInInputs, "MCF frame projected inputs should not contain base rocket fuel");

        RecipeSpec preservedSpec = node.getBaseSpec();
        Assertions.assertNotNull(preservedSpec);
        Assertions.assertEquals(1, preservedSpec.baseInputs().size());
        Assertions.assertEquals(ROCKET_FUEL_ID, preservedSpec.baseInputs().get(0).getId());

        node.setMachineIcon(GTCombustionHelper.HV_COMBUSTION);
        node.setMultiblock(false);
        node.restoreBaseRecipe();
        node.syncProjectedPorts();

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(ROCKET_FUEL_ID, node.getInputs().get(0).getId());
        Assertions.assertEquals(PortRole.CORE_RECIPE, node.getProjectedInput(0).role());
    }

    @Test
    public void testUS03AndUS04_LCEOxygenBoostAuxiliaryPortNonShifting() {
        RecipeNode node = new RecipeNode("lce_node", "Large Combustion Engine", 72.0, 160.0, GTVoltageTier.EV);
        node.setMachineIcon(GTCombustionHelper.LARGE_COMBUSTION_ENGINE);
        node.setRecipeCategoryId(COMBUSTION_CAT);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setGenerator(true);
        node.setMultiblock(true);

        IngredientStack rocketFuel = IngredientStack.fluid(ROCKET_FUEL_ID, "Rocket Fuel", 1000);
        RecipeSpec spec = new RecipeSpec(
                "lce_fuel_recipe",
                COMBUSTION_CAT,
                72.0,
                160.0,
                List.of(rocketFuel),
                List.of()
        );
        node.setBaseSpec(spec);
        node.syncProjectedPorts();

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertFalse(node.isAuxiliaryInputPort(0));

        node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, true);
        node.syncProjectedPorts();

        Assertions.assertEquals(2, node.getInputs().size());

        ProjectedPort corePort = node.getProjectedInput(0);
        Assertions.assertNotNull(corePort);
        Assertions.assertEquals(PortRole.CORE_RECIPE, corePort.role());
        Assertions.assertEquals(0, corePort.coreIndex());
        Assertions.assertEquals(ROCKET_FUEL_ID, corePort.stack().getId());
        Assertions.assertFalse(node.isAuxiliaryInputPort(0));

        ProjectedPort auxPort = node.getProjectedInput(1);
        Assertions.assertNotNull(auxPort);
        Assertions.assertEquals(PortRole.AUXILIARY_INPUT, auxPort.role());
        Assertions.assertEquals(-1, auxPort.coreIndex());
        Assertions.assertTrue(node.isAuxiliaryInputPort(1));
        Assertions.assertEquals("gtceu:oxygen_boost", auxPort.sourceAddonId());

        node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, false);
        node.syncProjectedPorts();

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(ROCKET_FUEL_ID, node.getInputs().get(0).getId());
        Assertions.assertFalse(node.isAuxiliaryInputPort(0));
    }

    @Test
    public void testUS05_SwitchRecipeCommandUndoRedoFidelity() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create(MACERATOR_ID, "Copper Ore Crushing", 200.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(MACERATOR_ID);
        IngredientStack inCopper = IngredientStack.item(COPPER_ORE, "Copper Ore", 1);
        IngredientStack outCopper = IngredientStack.item(COPPER_DUST, "Copper Dust", 1);
        RecipeSpec spec1 = new RecipeSpec("copper_recipe", MACERATOR_ID, 200.0, 30.0, List.of(inCopper), List.of(outCopper));
        node.setBaseSpec(spec1);
        node.syncProjectedPorts();
        graph.addNode(node);

        RecipeNode template2 = RecipeNode.create(MACERATOR_ID, "Iron Ore Crushing", 300.0, 30.0, GTVoltageTier.LV);
        template2.setRecipeCategoryId(MACERATOR_ID);
        IngredientStack inIron = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1);
        IngredientStack outIron = IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_dust"), "Iron Dust", 1);
        RecipeSpec spec2 = new RecipeSpec("iron_recipe", MACERATOR_ID, 300.0, 30.0, List.of(inIron), List.of(outIron));
        template2.setBaseSpec(spec2);
        template2.syncProjectedPorts();

        BoardCommand.SwitchRecipeCommand cmd = graph.switchNodeRecipe(node, template2);
        Assertions.assertNotNull(cmd);

        Assertions.assertEquals("iron_recipe", node.getBaseSpec().recipeId());
        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:iron_ore"), node.getInputs().get(0).getId());

        cmd.undo(graph);

        Assertions.assertEquals("copper_recipe", node.getBaseSpec().recipeId());
        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(COPPER_ORE, node.getInputs().get(0).getId());

        cmd.redo(graph);

        Assertions.assertEquals("iron_recipe", node.getBaseSpec().recipeId());
        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:iron_ore"), node.getInputs().get(0).getId());
    }

    @Test
    public void testSteamModeDynamicProjection() {
        RecipeNode node = RecipeNode.create(MACERATOR_ID, "Crushing", 100.0, 16.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(MACERATOR_ID);
        IngredientStack itemIn = IngredientStack.item(COPPER_ORE, "Copper Ore", 1);
        IngredientStack itemOut = IngredientStack.item(COPPER_DUST, "Copper Dust", 1);
        RecipeSpec spec = new RecipeSpec("macerator_copper", MACERATOR_ID, 100.0, 16.0, List.of(itemIn), List.of(itemOut));
        node.setBaseSpec(spec);
        node.syncProjectedPorts();

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertFalse(node.isAuxiliaryInputPort(0));

        node.setSteamMode(SteamMode.LOW_PRESSURE);
        node.syncProjectedPorts();

        Assertions.assertEquals(2, node.getInputs().size());
        Assertions.assertEquals(COPPER_ORE, node.getInputs().get(0).getId());
        Assertions.assertEquals(PortRole.CORE_RECIPE, node.getProjectedInput(0).role());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:steam"), node.getInputs().get(1).getId());
        Assertions.assertEquals(PortRole.AUXILIARY_INPUT, node.getProjectedInput(1).role());
        Assertions.assertTrue(node.isAuxiliaryInputPort(1));

        node.setSteamMode(SteamMode.NONE);
        node.syncProjectedPorts();

        Assertions.assertEquals(1, node.getInputs().size());
        Assertions.assertEquals(COPPER_ORE, node.getInputs().get(0).getId());
        Assertions.assertFalse(node.isAuxiliaryInputPort(0));
    }

    @Test
    public void testNbtSerializationDualWriteAndMigration() {
        RecipeNode node = new RecipeNode("test_nbt_node", "Smelting Node", 120.0, 32.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(MACERATOR_ID);
        IngredientStack inStack = IngredientStack.item(COPPER_ORE, "Copper Ore", 2);
        IngredientStack outStack = IngredientStack.item(COPPER_DUST, "Copper Dust", 2);
        RecipeSpec spec = new RecipeSpec("nbt_recipe_id", MACERATOR_ID, 120.0, 32.0, List.of(inStack), List.of(outStack));
        node.setBaseSpec(spec);
        node.syncProjectedPorts();

        CompoundTag tag = RecipeNodeSerializer.serialize(node);

        Assertions.assertTrue(tag.contains("baseSpec"));
        Assertions.assertTrue(tag.contains("inputs"));
        Assertions.assertTrue(tag.contains("outputs"));

        CompoundTag baseSpecTag = tag.getCompound("baseSpec");
        Assertions.assertEquals("nbt_recipe_id", baseSpecTag.getString("recipeId"));

        RecipeNode loadedNode = RecipeNodeSerializer.deserialize(tag);
        Assertions.assertNotNull(loadedNode);
        Assertions.assertNotNull(loadedNode.getBaseSpec());
        Assertions.assertEquals("nbt_recipe_id", loadedNode.getBaseSpec().recipeId());
        Assertions.assertEquals(1, loadedNode.getInputs().size());
        Assertions.assertEquals(COPPER_ORE, loadedNode.getInputs().get(0).getId());

        CompoundTag legacyTag = tag.copy();
        legacyTag.remove("baseSpec");

        RecipeNode legacyNode = RecipeNodeSerializer.deserialize(legacyTag);
        Assertions.assertNotNull(legacyNode);
        Assertions.assertNotNull(legacyNode.getBaseSpec());
        Assertions.assertEquals(1, legacyNode.getBaseSpec().baseInputs().size());
        Assertions.assertEquals(COPPER_ORE, legacyNode.getBaseSpec().baseInputs().get(0).getId());
    }

    @Test
    public void testLegacyMigrationWithSteamMode() {
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putString("id", "legacy_steam_node");
        legacyTag.putString("name", "Macerator");
        legacyTag.putDouble("baseDurationTicks", 100.0);
        legacyTag.putDouble("baseEUt", 16.0);
        legacyTag.putString("recipeTier", "LV");
        legacyTag.putString("roleType", "MACHINE");

        CompoundTag roleData = new CompoundTag();
        roleData.putString("recipeCategoryId", "gtceu:macerator");
        roleData.putString("machineIcon", "gtceu:lp_steam_macerator");
        legacyTag.put("roleData", roleData);

        net.minecraft.nbt.ListTag inputs = new net.minecraft.nbt.ListTag();
        inputs.add(IngredientStack.item(COPPER_ORE, "Copper Ore", 1).serializeNBT());
        inputs.add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 3200).serializeNBT());
        legacyTag.put("inputs", inputs);

        legacyTag.putString("steamMode", "LOW_PRESSURE");

        RecipeNode migratedNode = RecipeNodeSerializer.deserialize(legacyTag);
        Assertions.assertNotNull(migratedNode);
        Assertions.assertNotNull(migratedNode.getBaseSpec());

        // Core baseSpec MUST NOT have the steam auxiliary input!
        Assertions.assertEquals(1, migratedNode.getBaseSpec().baseInputs().size(), "Legacy migration should clean auxiliary steam from baseSpec");
        Assertions.assertEquals(COPPER_ORE, migratedNode.getBaseSpec().baseInputs().get(0).getId());

        // Runtime projected inputs should have both Copper Ore and projected Steam
        Assertions.assertEquals(2, migratedNode.getInputs().size());
        Assertions.assertEquals(COPPER_ORE, migratedNode.getInputs().get(0).getId());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:steam"), migratedNode.getInputs().get(1).getId());
        Assertions.assertTrue(migratedNode.isAuxiliaryInputPort(1));

        // When switching steam mode off, steam should disappear cleanly
        migratedNode.setSteamMode(SteamMode.NONE);
        Assertions.assertEquals(1, migratedNode.getInputs().size());
        Assertions.assertEquals(COPPER_ORE, migratedNode.getInputs().get(0).getId());
    }

    @Test
    public void testAddInputPreservesRecipeIdAndCategoryId() {
        RecipeNode node = RecipeNode.create(MACERATOR_ID, "Crushing", 100.0, 16.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(MACERATOR_ID);
        IngredientStack itemIn = IngredientStack.item(COPPER_ORE, "Copper Ore", 1);
        IngredientStack itemOut = IngredientStack.item(COPPER_DUST, "Copper Dust", 1);
        RecipeSpec spec = new RecipeSpec("my_custom_recipe", MACERATOR_ID, 100.0, 16.0, List.of(itemIn), List.of(itemOut));
        node.setBaseSpec(spec);

        node.setSteamMode(SteamMode.LOW_PRESSURE);
        node.syncProjectedPorts();

        IngredientStack extraInput = IngredientStack.item(ResourceLocation.tryParse("minecraft:sand"), "Sand", 1);
        node.addInput(extraInput);

        Assertions.assertEquals("my_custom_recipe", node.getBaseSpec().recipeId(), "addInput must not overwrite recipeId with node UUID");
        Assertions.assertEquals(MACERATOR_ID, node.getBaseSpec().categoryId());
    }

    @Test
    public void testSetIdPreservesRecipeId() {
        RecipeNode node = RecipeNode.create(MACERATOR_ID, "Crushing", 100.0, 16.0, GTVoltageTier.LV);
        IngredientStack itemIn = IngredientStack.item(COPPER_ORE, "Copper Ore", 1);
        IngredientStack itemOut = IngredientStack.item(COPPER_DUST, "Copper Dust", 1);
        RecipeSpec spec = new RecipeSpec("my_recipe_123", MACERATOR_ID, 100.0, 16.0, List.of(itemIn), List.of(itemOut));
        node.setBaseSpec(spec);

        node.setId("new-uuid-999");
        Assertions.assertEquals("my_recipe_123", node.getBaseSpec().recipeId(), "setId must not overwrite recipeId with node UUID");
    }

    @Test
    public void testReactiveSyncWithoutManualSyncProjectedPorts() {
        RecipeNode node = new RecipeNode("lce_test", "Large Combustion Engine", 72.0, 160.0, GTVoltageTier.EV);
        node.setMachineIcon(GTCombustionHelper.LARGE_COMBUSTION_ENGINE);
        node.setRecipeCategoryId(COMBUSTION_CAT);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setGenerator(true);
        node.setMultiblock(true);

        IngredientStack rocketFuel = IngredientStack.fluid(ROCKET_FUEL_ID, "Rocket Fuel", 1000);
        RecipeSpec spec = new RecipeSpec("lce_rf", COMBUSTION_CAT, 72.0, 160.0, List.of(rocketFuel), List.of());
        node.setBaseSpec(spec);

        Assertions.assertEquals(1, node.getProjectedInputs().size());

        // Modifying property directly without calling syncProjectedPorts()
        node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, true);

        // Reactive: node.getProjectedInputs() should automatically reflect the projected auxiliary port!
        Assertions.assertEquals(2, node.getProjectedInputs().size(), "node.getProjectedInputs() should lazily project ports when dirty without manual syncProjectedPorts()");
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:oxygen"), node.getProjectedInputs().get(1).stack().getId());

        node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, false);
        Assertions.assertEquals(1, node.getProjectedInputs().size(), "node.getProjectedInputs() should lazily project removal when dirty");
    }

    @Test
    public void testSwitchRecipeCommandClearsBaseSpecWhenNullInSnapshot() {
        RecipeNode node = RecipeNode.create(MACERATOR_ID, "Crushing", 100.0, 16.0, GTVoltageTier.LV);
        IngredientStack itemIn = IngredientStack.item(COPPER_ORE, "Copper Ore", 1);
        IngredientStack itemOut = IngredientStack.item(COPPER_DUST, "Copper Dust", 1);
        RecipeSpec spec = new RecipeSpec("initial_recipe", MACERATOR_ID, 100.0, 16.0, List.of(itemIn), List.of(itemOut));
        node.setBaseSpec(spec);

        // Snapshot of a node with no baseSpec (e.g. legacy or dynamically constructed node)
        BoardCommand.SwitchRecipeCommand.RecipeSnapshot snapshotWithoutSpec = new BoardCommand.SwitchRecipeCommand.RecipeSnapshot(
                "Simple Machine",
                50.0,
                8.0,
                GTVoltageTier.LV,
                null,
                List.of(itemIn),
                List.of(itemOut),
                ResourceLocation.tryParse("gtceu:basic_machine"),
                false,
                GTVoltageTier.LV,
                List.of(),
                List.of(),
                1,
                0,
                SteamMode.NONE,
                com.gtceu.calcboard.api.type.OverclockMode.STANDARD,
                false,
                new com.gtceu.calcboard.api.property.NodePropertyStore(),
                null // baseSpec is null
        );

        snapshotWithoutSpec.applyTo(node);

        // Node must not retain the previous "initial_recipe" baseSpec!
        Assertions.assertNotEquals("initial_recipe", node.getBaseSpec().recipeId(),
                "applyTo with null baseSpec should not retain stale previous recipeId");
    }
}
