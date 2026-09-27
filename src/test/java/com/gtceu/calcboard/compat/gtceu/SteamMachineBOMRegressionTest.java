package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.testutil.TestMultiblockFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class SteamMachineBOMRegressionTest {

    private static final ResourceLocation LV_ENERGY_HATCH = ResourceLocation.tryParse("gtceu:lv_energy_input_hatch");
    private static final ResourceLocation STEAM_INPUT_HATCH = ResourceLocation.tryParse("gtceu:steam_input_hatch");

    @BeforeEach
    public void setUp() {
        MultiblockDetector.reinitialize();
        TestMultiblockFixtures.initTestEnvironmentDefaults();
    }

    @Test
    @DisplayName("Steam Kiln must require Steam Input Hatch and never require LV Energy Hatch in BOM")
    public void testSteamKilnBOMRequiresSteamHatchNotEnergyHatch() {
        RecipeNode node = RecipeNode.create("Steam Kiln (철 주괴)", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_kiln"));
        node.setMultiblock(true);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        boolean hasEnergyHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(LV_ENERGY_HATCH));
        Assertions.assertFalse(hasEnergyHatch, "Steam Kiln must not require LV Energy Hatch in BOM!");

        boolean hasSteamHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(STEAM_INPUT_HATCH));
        Assertions.assertTrue(hasSteamHatch, "Steam Kiln must require Steam Input Hatch in BOM!");
    }

    @Test
    @DisplayName("Steam Ore Processing must require Steam Input Hatch and never require LV Energy Hatch in BOM")
    public void testSteamOreProcessingBOMRequiresSteamHatchNotEnergyHatch() {
        RecipeNode node = RecipeNode.create("Steam Ore Processing (분쇄된 자철석 광석)", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_ore_factory"));
        node.setMultiblock(true);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        boolean hasEnergyHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(LV_ENERGY_HATCH));
        Assertions.assertFalse(hasEnergyHatch, "Steam Ore Processing must not require LV Energy Hatch in BOM!");

        boolean hasSteamHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(STEAM_INPUT_HATCH));
        Assertions.assertTrue(hasSteamHatch, "Steam Ore Processing must require Steam Input Hatch in BOM!");
    }

    @Test
    @DisplayName("Custom Steam multiblock (e.g. Greenhouse/Sieve) without catalog structure must require Steam Input Hatch")
    public void testCustomSteamMultiblockFallbackRequiresSteamHatch() {
        RecipeNode node = RecipeNode.create("온실 (물)", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:greenhouse"));
        node.setMultiblock(true);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        boolean hasEnergyHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(LV_ENERGY_HATCH));
        Assertions.assertFalse(hasEnergyHatch, "Custom Steam multiblock must not require LV Energy Hatch in BOM!");

        boolean hasSteamHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(STEAM_INPUT_HATCH));
        Assertions.assertTrue(hasSteamHatch, "Custom Steam multiblock must require Steam Input Hatch in BOM!");
    }

    @Test
    @DisplayName("Dual-energy multiblock running in steam mode replaces energy hatch with Steam Input Hatch")
    public void testDualEnergyMultiblockInSteamModeReplacesEnergyHatchWithSteamHatch() {
        ResourceLocation multiId = ResourceLocation.tryParse("gtceu:dual_power_sieve");
        List<MultiblockStructurePart> parts = List.of(
                new MultiblockStructurePart(multiId, "Dual Power Sieve", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:bronze_brick_casing"), "Bronze Brick Casing", 14, PartCategory.CASING),
                new MultiblockStructurePart(LV_ENERGY_HATCH, "LV Energy Input Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(
                multiId, "Dual Power Sieve", parts, 0, 1, 0, 0, 0, 0, 0
        ));

        RecipeNode node = RecipeNode.create("Mechanical Sieve (자갈)", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineIcon(multiId);
        node.setMultiblock(true);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        boolean hasEnergyHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(LV_ENERGY_HATCH));
        Assertions.assertFalse(hasEnergyHatch, "Dual Power Sieve running in Steam mode must not require LV Energy Hatch!");

        boolean hasSteamHatch = summary.aggregatedItems().stream()
                .anyMatch(e -> e.itemId().equals(STEAM_INPUT_HATCH));
        Assertions.assertTrue(hasSteamHatch, "Dual Power Sieve running in Steam mode must require Steam Input Hatch!");
    }
}
