package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.testutil.SimulatedGTEnvironment;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GTCEuModpackSimulationTest {

    @BeforeEach
    void setUp() {
        SimulatedGTEnvironment.setupFullEnvironment();
    }

    @Test
    @DisplayName("Distillation Tower must never be detected as a turbine under full simulated modpack environment")
    void testDistillationTowerSimulation() {
        RecipeNode node = SimulatedGTEnvironment.createDistillationTowerNode();

        assertFalse(MultiblockDetector.isTurbine(SimulatedGTEnvironment.DISTILLATION_TOWER_ID),
                "Distillation Tower ID must not be classified as a turbine controller");
        assertFalse(MultiblockDetector.isTurbineMachine(SimulatedGTEnvironment.DISTILLATION_TOWER_ID),
                "Distillation Tower machine ID must not be classified as a turbine machine");
        assertFalse(MultiblockDetector.isTurbineRecipeCategory(SimulatedGTEnvironment.DISTILLATION_TOWER_ID),
                "Distillation Tower category ID must not be classified as a turbine recipe category");

        assertFalse(GTTurbineHelper.isTurbine(node),
                "GTTurbineHelper.isTurbine must return false for Distillation Tower");
        assertFalse(GTTurbineHelper.isLargeTurbine(node),
                "GTTurbineHelper.isLargeTurbine must return false for Distillation Tower");
        assertFalse(node.isTurbine(),
                "RecipeNode.isTurbine must return false for Distillation Tower");
        assertFalse(node.isLargeTurbine(),
                "RecipeNode.isLargeTurbine must return false for Distillation Tower");

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertFalse(cats.contains(AddonCategory.ROTOR),
                "Distillation Tower must NOT have ROTOR addon category");
        assertFalse(cats.contains(AddonCategory.COIL),
                "Distillation Tower must NOT have COIL addon category");
        assertTrue(cats.contains(AddonCategory.MAINTENANCE),
                "Distillation Tower must have MAINTENANCE addon category");
        assertTrue(cats.contains(AddonCategory.HATCH_BUS),
                "Distillation Tower must have HATCH_BUS addon category");
        assertTrue(cats.contains(AddonCategory.ENERGY_HATCH),
                "Distillation Tower must have ENERGY_HATCH addon category");
    }

    @Test
    @DisplayName("Large Steam Turbine must be properly detected with rotor category in simulated environment")
    void testLargeSteamTurbineSimulation() {
        RecipeNode node = SimulatedGTEnvironment.createLargeSteamTurbineNode();

        assertTrue(MultiblockDetector.isTurbine(SimulatedGTEnvironment.STEAM_TURBINE_ID),
                "Large Steam Turbine ID must be detected as a turbine controller");
        assertTrue(MultiblockDetector.isTurbineRecipeCategory(SimulatedGTEnvironment.STEAM_TURBINE_CAT),
                "Steam Turbine category ID must be detected as a turbine category");

        assertTrue(GTTurbineHelper.isTurbine(node),
                "GTTurbineHelper.isTurbine must return true for Large Steam Turbine");
        assertTrue(GTTurbineHelper.isLargeTurbine(node),
                "GTTurbineHelper.isLargeTurbine must return true for Large Steam Turbine");
        assertTrue(node.isTurbine());
        assertTrue(node.isLargeTurbine());

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertTrue(cats.contains(AddonCategory.ROTOR),
                "Large Steam Turbine must have ROTOR addon category");
        assertTrue(cats.contains(AddonCategory.MAINTENANCE),
                "Large Steam Turbine must have MAINTENANCE addon category");
        assertFalse(cats.contains(AddonCategory.COIL),
                "Large Steam Turbine must NOT have COIL addon category");
    }

    @Test
    @DisplayName("Multiblock with rotor casing part but non-generator must not be classified as turbine")
    void testRotorCasingMachineIsNotTurbine() {
        RecipeNode node = SimulatedGTEnvironment.createRotorCasingMachineNode();

        assertFalse(MultiblockDetector.isTurbine(SimulatedGTEnvironment.ROTOR_CASING_MACHINE_ID));
        assertFalse(GTTurbineHelper.isTurbine(node));
        assertFalse(node.isTurbine());

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertFalse(cats.contains(AddonCategory.ROTOR),
                "Non-generator machine with rotor casing must not have ROTOR category");
    }

    @Test
    @DisplayName("Category capability matrix must not pollute turbine flags for distillation tower")
    void testCapabilityMatrixPurity() {
        CategoryCapabilityMatrix matrix = CategoryCapabilityMatrix.getInstance();
        CategoryCapability dtCap = matrix.getCapability(SimulatedGTEnvironment.DISTILLATION_TOWER_ID);

        assertNotNull(dtCap);
        assertFalse(dtCap.isTurbine(), "Distillation tower capability must not have isTurbine=true");
        assertFalse(dtCap.supportedAddonCategories().contains(AddonCategory.ROTOR),
                "Distillation tower capability must not contain ROTOR addon");

        CategoryCapability stCap = matrix.getCapability(SimulatedGTEnvironment.STEAM_TURBINE_CAT);
        assertNotNull(stCap);
        assertTrue(stCap.isTurbine(), "Steam turbine capability must have isTurbine=true");
        assertTrue(stCap.supportedAddonCategories().contains(AddonCategory.ROTOR),
                "Steam turbine capability must contain ROTOR addon");
    }

    @Test
    @DisplayName("Verify that deductArchetype deterministically maps GTCEu machines to positive archetypes without cross-talk")
    void testMachineArchetypeClassification() {
        ResourceLocation dtId = SimulatedGTEnvironment.DISTILLATION_TOWER_ID;
        Set<String> dtAbilities = Set.of("IMPORT_FLUIDS", "EXPORT_FLUIDS", "EXPORT_FLUIDS_1X", "EXPORT_ITEMS", "INPUT_ENERGY", "MAINTENANCE", "BATCH_MODE");
        com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype dtArchetype =
                com.gtceu.calcboard.compat.gtceu.helper.GTCEuMachineAnalyzer.deductArchetype(dtId, null, null, dtAbilities, true);
        assertEquals(com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype.STANDARD_PROCESSING, dtArchetype,
                "Distillation Tower must be classified as STANDARD_PROCESSING");

        ResourceLocation ebfId = SimulatedGTEnvironment.EBF_ID;
        Set<String> ebfAbilities = Set.of("HEATING_COILS", "IMPORT_ITEMS", "EXPORT_ITEMS", "INPUT_ENERGY", "MAINTENANCE");
        com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype ebfArchetype =
                com.gtceu.calcboard.compat.gtceu.helper.GTCEuMachineAnalyzer.deductArchetype(ebfId, null, null, ebfAbilities, true);
        assertEquals(com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype.COIL_HEATED, ebfArchetype,
                "EBF must be classified as COIL_HEATED");

        ResourceLocation threadId = ResourceLocation.tryParse("gtceu:multithreaded_component_synthesis_forge");
        Set<String> threadAbilities = Set.of("THREADING", "INPUT_ENERGY", "MAINTENANCE");
        com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype threadArchetype =
                com.gtceu.calcboard.compat.gtceu.helper.GTCEuMachineAnalyzer.deductArchetype(threadId, null, null, threadAbilities, true);
        assertEquals(com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype.THREADED_SYNTHESIS, threadArchetype,
                "Threading machine must be classified as THREADED_SYNTHESIS");

        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam_grinder");
        com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype steamArchetype =
                com.gtceu.calcboard.compat.gtceu.helper.GTCEuMachineAnalyzer.deductArchetype(steamId, null, null, Set.of(), true);
        assertEquals(com.gtceu.calcboard.compat.gtceu.model.GTMachineArchetype.STEAM_MACHINE, steamArchetype,
                "Steam grinder must be classified as STEAM_MACHINE");
    }

    @Test
    @DisplayName("Distillation Tower must strictly disallow parallel hatch and exclude PARALLEL category even when LFD is in availableWorkstations")
    void testDistillationTowerParallelHatchExclusion() {
        RecipeNode dtNode = SimulatedGTEnvironment.createDistillationTowerNode();
        dtNode.setMachineIcon(SimulatedGTEnvironment.DISTILLATION_TOWER_ID);

        assertFalse(MultiblockDetector.supportsParallelHatch(dtNode.getMachineIcon()),
                "Distillation Tower must not support parallel hatch");

        CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(dtNode.getRecipeCategoryId());
        assertNotNull(cap);
        List<AddonCategory> activeCats = cap.getActiveCategoriesForNode(dtNode);
        assertFalse(activeCats.contains(AddonCategory.PARALLEL),
                "Active categories for Distillation Tower must NOT contain PARALLEL category");

        List<AddonCategory> applicableCats = GTAddonCompatibilityHandler.getApplicableAddonCategories(dtNode);
        assertFalse(applicableCats.contains(AddonCategory.PARALLEL),
                "Applicable categories for Distillation Tower must NOT contain PARALLEL category");

        MachineAddon mockParallelAddon = new GTParallelHatchAddon(
                "gtceu:parallel_hatch_test",
                "Test Parallel Hatch",
                "Description",
                null,
                4,
                false
        );
        assertFalse(GTAddonCompatibilityHandler.isAddonCompatible(dtNode, mockParallelAddon),
                "Distillation Tower must reject parallel hatch addon installation");
    }

    @Test
    @DisplayName("Switching between Distillation Tower and LFD correctly toggles parallel hatch capabilities and purges installed hatch on downgrade")
    void testDistillationTowerAndLfdSwitchingLifecycle() {
        RecipeNode node = SimulatedGTEnvironment.createDistillationTowerNode();
        node.setMachineIcon(SimulatedGTEnvironment.DISTILLATION_TOWER_ID);

        CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
        assertNotNull(cap);

        MachineAddon mockParallelAddon = new GTParallelHatchAddon(
                "gtceu:parallel_hatch_test",
                "Test Parallel Hatch",
                "Description",
                null,
                4,
                false
        );

        // 1. Initially DT: no parallel hatch
        assertFalse(MultiblockDetector.supportsParallelHatch(node.getMachineIcon()));
        assertFalse(cap.getActiveCategoriesForNode(node).contains(AddonCategory.PARALLEL));
        assertFalse(GTAddonCompatibilityHandler.isAddonCompatible(node, mockParallelAddon));

        // 2. Switch to LFD: parallel hatch enabled
        node.setMachineIcon(SimulatedGTEnvironment.LFD_ID);
        assertTrue(MultiblockDetector.supportsParallelHatch(node.getMachineIcon()),
                "LFD must support parallel hatch");
        assertTrue(cap.getActiveCategoriesForNode(node).contains(AddonCategory.PARALLEL),
                "LFD must expose PARALLEL category");
        assertTrue(GTAddonCompatibilityHandler.isAddonCompatible(node, mockParallelAddon),
                "LFD must accept parallel hatch addon");

        // 3. Install parallel hatch on LFD
        node.addAddon(mockParallelAddon);
        node.setParallel(4);
        assertTrue(node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.PARALLEL));
        assertEquals(4, node.getParallel());

        // 4. Switch back to DT: installed parallel hatch must be automatically purged and reset
        node.setMachineIcon(SimulatedGTEnvironment.DISTILLATION_TOWER_ID);
        assertFalse(MultiblockDetector.supportsParallelHatch(node.getMachineIcon()));
        assertFalse(cap.getActiveCategoriesForNode(node).contains(AddonCategory.PARALLEL));
        assertFalse(node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.PARALLEL),
                "Switching back to DT must purge parallel hatch addon");
        assertEquals(1, node.getParallel(),
                "Switching back to DT must reset parallel multiplier to 1");
    }
}
