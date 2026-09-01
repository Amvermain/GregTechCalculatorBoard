package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GTTurbineDetectionTest {

    @BeforeAll
    public static void setup() {
        ModAdapterRegistry.init();
        com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().reset();
        MultiblockDetector.reinitialize();
    }

    @Test
    @DisplayName("Verify that EBF node with plasma recipe name is not classified as turbine")
    void testEbfNodeWithPlasmaInNameIsNotTurbine() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        RecipeNode node = RecipeNode.create(ebfId, "Electric Blast Furnace (Abyssal Plasma Ingot)", 100.0, 1920.0, GTVoltageTier.EV);
        node.setRecipeCategoryId(ebfId);
        node.setMultiblock(true);

        assertFalse(GTTurbineHelper.isTurbine(node), "EBF node with plasma in name must not be classified as a turbine");
        assertFalse(GTTurbineHelper.isLargeTurbine(node), "EBF node must not be classified as a large turbine");
        assertFalse(node.isTurbine(), "RecipeNode.isTurbine() must return false for EBF");
        assertFalse(node.isLargeTurbine(), "RecipeNode.isLargeTurbine() must return false for EBF");

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertFalse(cats.contains(AddonCategory.ROTOR), "EBF must not have ROTOR addon category");
        assertTrue(cats.contains(AddonCategory.COIL), "EBF must have COIL addon category");
        assertTrue(cats.contains(AddonCategory.ENERGY_HATCH), "EBF must have ENERGY_HATCH addon category");
        assertTrue(cats.contains(AddonCategory.MAINTENANCE), "EBF must have MAINTENANCE addon category");
        assertTrue(cats.contains(AddonCategory.HATCH_BUS), "EBF must have HATCH_BUS addon category");
        assertFalse(cats.contains(AddonCategory.PARALLEL), "Standard EBF must not have PARALLEL addon category");
    }

    @Test
    @DisplayName("Verify that EBF node with turbine recipe name is not classified as turbine")
    void testEbfNodeWithTurbineInNameIsNotTurbine() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        RecipeNode node = RecipeNode.create(ebfId, "Electric Blast Furnace (Turbine Housing)", 100.0, 1920.0, GTVoltageTier.EV);
        node.setRecipeCategoryId(ebfId);
        node.setMultiblock(true);

        assertFalse(GTTurbineHelper.isTurbine(node));
        assertFalse(GTTurbineHelper.isLargeTurbine(node));
        assertFalse(node.isTurbine());
        assertFalse(node.isLargeTurbine());

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertFalse(cats.contains(AddonCategory.ROTOR));
        assertTrue(cats.contains(AddonCategory.COIL));
    }

    @Test
    @DisplayName("Verify that actual Large Steam Turbine is correctly detected and has rotor category")
    void testActualLargeSteamTurbineIsDetected() {
        ResourceLocation turbineId = ResourceLocation.tryParse("gtceu:large_steam_turbine");
        ResourceLocation categoryId = ResourceLocation.tryParse("gtceu:steam_turbine");
        RecipeNode node = RecipeNode.create(turbineId, "Large Steam Turbine", 20.0, 1024.0, GTVoltageTier.HV);
        node.setRecipeCategoryId(categoryId);
        node.setMultiblock(true);
        node.setGenerator(true);

        assertTrue(GTTurbineHelper.isTurbine(node), "Large Steam Turbine must be detected as turbine");
        assertTrue(GTTurbineHelper.isLargeTurbine(node), "Large Steam Turbine must be detected as large turbine");
        assertTrue(node.isTurbine());
        assertTrue(node.isLargeTurbine());

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertTrue(cats.contains(AddonCategory.ROTOR), "Turbine must have ROTOR category");
        assertTrue(cats.contains(AddonCategory.MAINTENANCE), "Turbine must have MAINTENANCE category");
        assertFalse(cats.contains(AddonCategory.COIL), "Turbine must not have COIL category");
        assertFalse(cats.contains(AddonCategory.ENERGY_HATCH), "Turbine must not have regular consumer ENERGY_HATCH category in applicable list");
    }

    @Test
    @DisplayName("Verify that actual Large Plasma Turbine is correctly detected")
    void testActualLargePlasmaTurbineIsDetected() {
        ResourceLocation turbineId = ResourceLocation.tryParse("gtceu:large_plasma_turbine");
        ResourceLocation categoryId = ResourceLocation.tryParse("gtceu:plasma_turbine");
        RecipeNode node = RecipeNode.create(turbineId, "Large Plasma Turbine", 20.0, 16384.0, GTVoltageTier.IV);
        node.setRecipeCategoryId(categoryId);
        node.setMultiblock(true);
        node.setGenerator(true);

        assertTrue(GTTurbineHelper.isTurbine(node));
        assertTrue(GTTurbineHelper.isLargeTurbine(node));
        assertTrue(node.isTurbine());
        assertTrue(node.isLargeTurbine());

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertTrue(cats.contains(AddonCategory.ROTOR));
        assertTrue(cats.contains(AddonCategory.MULTIBLOCK_TRAIT));
    }

    @Test
    @DisplayName("Verify that EBF node with Beryllium and mixed workstations is not classified as turbine")
    void testEbfBerylliumWithTurbineInWorkstationsIsNotTurbine() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation turbineWs = ResourceLocation.tryParse("gtceu:large_gas_turbine");
        RecipeNode node = RecipeNode.create(ebfId, "Electric Blast Furnace (Beryllium Ingot)", 120.0, 1920.0, GTVoltageTier.EV);
        node.setRecipeCategoryId(ebfId);
        node.setMultiblock(true);
        // Even if candidate workstations somehow contain a turbine
        node.getAvailableWorkstations().add(turbineWs);

        assertFalse(GTTurbineHelper.isTurbine(node), "EBF with turbine in candidate workstations must still not be classified as a turbine");
        assertFalse(GTTurbineHelper.isLargeTurbine(node));
        assertFalse(node.isTurbine());
        assertFalse(node.isLargeTurbine());

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertFalse(cats.contains(AddonCategory.ROTOR));
        assertTrue(cats.contains(AddonCategory.COIL));
        assertTrue(cats.contains(AddonCategory.ENERGY_HATCH));
    }

    @Test
    @DisplayName("Verify that Catalytic Hellfire Energized Furnace (CHEF / ultimate_ebf) with rotor holder is not classified as turbine")
    void testChefIsNotTurbine() {
        ResourceLocation chefId = ResourceLocation.tryParse("gtceu:ultimate_ebf");
        ResourceLocation ebfCatId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        MultiblockDetector.registerCoilMultiblock(chefId, ebfCatId);

        RecipeNode node = RecipeNode.create(chefId, "Catalytic Hellfire Energized Furnace [CHEF]", 120.0, 1920.0, GTVoltageTier.UIV);
        node.setRecipeCategoryId(ebfCatId);
        node.setMultiblock(true);

        assertFalse(GTTurbineHelper.isTurbine(node), "CHEF must not be classified as a turbine");
        assertFalse(GTTurbineHelper.isLargeTurbine(node));
        assertFalse(node.isTurbine());
        assertFalse(node.isLargeTurbine());

        List<AddonCategory> cats = MachineAddon.getRelevantCategories(node);
        assertFalse(cats.contains(AddonCategory.ROTOR), "CHEF must not have ROTOR category");
        assertTrue(cats.contains(AddonCategory.COIL), "CHEF must have COIL category");
        assertTrue(cats.contains(AddonCategory.ENERGY_HATCH), "CHEF must have ENERGY_HATCH category");
    }
}
