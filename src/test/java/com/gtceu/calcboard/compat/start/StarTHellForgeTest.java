package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuMachineLifecycleHandler;
import com.gtceu.calcboard.compat.start.StarTHellForgeHelper.HeatingFluid;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StarTHellForgeTest {

    private static final ResourceLocation HELLFORGE_RECIPE_TYPE = ResourceLocation.tryParse("gtceu:hellforge");
    private static final ResourceLocation HELLFORGE_MACHINE_ID = ResourceLocation.tryParse("start_core:hellforge");
    private static final ResourceLocation BOOSTED_HELLFORGE_MACHINE_ID = ResourceLocation.tryParse("start_core:fornaxs_infernal_rotary_engine");

    @BeforeEach
    public void setup() {
        ModAdapterRegistry.init();
        com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
        com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().ensureFastLoaded();
    }

    private RecipeNode createHellForgeNode(ResourceLocation machineId, int requiredTempMK) {
        RecipeNode node = RecipeNode.create("hellforge_gravi_star", 60.0, 30720.0, GTVoltageTier.LuV);
        node.setMultiblock(true);
        node.setMachineIcon(machineId);
        node.setRecipeCategoryId(HELLFORGE_RECIPE_TYPE);
        node.setAvailableWorkstations(List.of(HELLFORGE_MACHINE_ID, BOOSTED_HELLFORGE_MACHINE_ID));
        node.setRecipeTemperature(requiredTempMK);
        node.getProperties().set(GTCEuProperties.EBF_TEMPERATURE, requiredTempMK);
        node.setTargetTier(GTVoltageTier.UHV);
        node.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:uhv_energy_input_hatch",
                "UHV Energy Hatch",
                "UHV 2A",
                ResourceLocation.tryParse("gtceu:uhv_energy_input_hatch"),
                GTVoltageTier.UHV,
                2,
                false,
                false,
                false
        ));
        GTCEuMachineLifecycleHandler.ensureDefaultMachineTraits(node, machineId);
        StarTHellForgeHelper.ensureDefaultHeat(node);
        return node;
    }

    @Test
    public void testHellForgeExcludesCoilsCompletely() {
        RecipeNode node = createHellForgeNode(HELLFORGE_MACHINE_ID, 1800);

        assertTrue(StarTHellForgeHelper.isHellForge(node));
        assertFalse(node.canUseCoils());
        assertEquals(0, CoilHelper.getInstalledCoilTemperature(node));

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        assertNotNull(adapter);
        assertEquals("start_core", adapter.getModId());
        assertFalse(adapter.canUseCoils(node));
        assertFalse(adapter.getApplicableAddonCategories(node).contains(AddonCategory.COIL));
        assertFalse(CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId()).getActiveCategoriesForNode(node).contains(AddonCategory.COIL));

        var coil = CoilHelper.getCoilForTemperature(1800);
        CoilHelper.installCoil(node, coil);
        assertNull(CoilHelper.getInstalledCoil(node));
        assertEquals(1800, StarTHellForgeHelper.getOperatingTemperature(node));
    }

    @Test
    public void testInfernalParallelScalingBy450MKIncrements() {
        RecipeNode node = createHellForgeNode(HELLFORGE_MACHINE_ID, 1800);

        assertEquals(1800, StarTHellForgeHelper.getOperatingTemperature(node));
        assertEquals(HeatingFluid.EMBERHEART_NECTAR, StarTHellForgeHelper.getSelectedFluid(node));
        assertEquals(1, StarTHellForgeHelper.computeInfernalParallel(node));
        assertEquals(1, node.getTotalParallel());
        assertTrue(node.isOperational());

        double baseTotalEUtAt1800 = node.getEffectiveTotalEUt();
        double baseDurationAt1800 = node.getEffectiveDurationSeconds();

        StarTHellForgeHelper.setOperatingTemperature(node, 2250);
        assertEquals(2, StarTHellForgeHelper.computeInfernalParallel(node));
        assertEquals(2, node.getTotalParallel());
        assertEquals(baseTotalEUtAt1800, node.getEffectiveTotalEUt(), 0.01);
        assertEquals(baseDurationAt1800, node.getEffectiveDurationSeconds(), 0.001);

        StarTHellForgeHelper.setOperatingTemperature(node, 2700);
        assertEquals(4, StarTHellForgeHelper.computeInfernalParallel(node));
        assertEquals(4, node.getTotalParallel());

        StarTHellForgeHelper.setOperatingTemperature(node, 3150);
        assertEquals(8, StarTHellForgeHelper.computeInfernalParallel(node));
        assertEquals(8, node.getTotalParallel());

        StarTHellForgeHelper.setOperatingTemperature(node, 3600);
        assertEquals(16, StarTHellForgeHelper.computeInfernalParallel(node));
        assertEquals(16, node.getTotalParallel());

        StarTHellForgeHelper.setOperatingTemperature(node, 4050);
        assertEquals(32, StarTHellForgeHelper.computeInfernalParallel(node));
        assertEquals(32, node.getTotalParallel());
    }

    @Test
    public void testInsufficientCrucibleHeatValidationAndBadge() {
        RecipeNode node = createHellForgeNode(HELLFORGE_MACHINE_ID, 1800);

        StarTHellForgeHelper.setOperatingTemperature(node, 900);
        assertEquals(900, StarTHellForgeHelper.getOperatingTemperature(node));
        assertFalse(node.isOperational());

        List<NodeBadge> deficitBadges = NodeBadgeRegistry.getBadgesForNode(node, null);
        assertTrue(deficitBadges.stream().noneMatch(b -> b.text().startsWith("♨")));
        NodeBadge heatBadge = deficitBadges.stream()
                .filter(b -> b.text().startsWith("🔥"))
                .findFirst()
                .orElseThrow();
        assertTrue(heatBadge.isWarning());
        assertEquals("🔥 ⚠ 900MK", heatBadge.text());

        heatBadge.onClick().run();
        assertEquals(1350, StarTHellForgeHelper.getOperatingTemperature(node));
        assertFalse(node.isOperational());

        StarTHellForgeHelper.cycleTemperature(node, 1);
        assertEquals(1800, StarTHellForgeHelper.getOperatingTemperature(node));
        assertTrue(node.isOperational());

        List<NodeBadge> validBadges = NodeBadgeRegistry.getBadgesForNode(node, null);
        NodeBadge validHeatBadge = validBadges.stream()
                .filter(b -> b.text().startsWith("🔥"))
                .findFirst()
                .orElseThrow();
        assertFalse(validHeatBadge.isWarning());
        assertEquals("🔥 1,800MK", validHeatBadge.text());
    }

    @Test
    public void testFornaxInfernalRotaryEngineCombinesThroughputBoostingAndInfernalParallel() {
        RecipeNode node = createHellForgeNode(BOOSTED_HELLFORGE_MACHINE_ID, 1800);

        assertTrue(StarTHellForgeHelper.isHellForge(node));
        assertFalse(node.canUseCoils());
        assertEquals(4, node.getTotalParallel());

        StarTHellForgeHelper.setOperatingTemperature(node, 2700);
        assertEquals(4, StarTHellForgeHelper.computeInfernalParallel(node));
        assertEquals(16, node.getTotalParallel());
    }
}
