package com.gtceu.calcboard.integration.ae2;

import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.client.gui.compat.ae2.Ae2PatternTransferHelper;
import com.gtceu.calcboard.client.gui.compat.ae2.ClientAe2PatternTermHook;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Ae2PatternTransferTest {

    @Test
    public void testIsPatternEncodingScreenValidation() {
        Assertions.assertFalse(ClientAe2PatternTermHook.isPatternEncodingScreen(null));

        Screen dummyScreen = new Screen(Component.literal("Dummy")) {};
        Assertions.assertFalse(ClientAe2PatternTermHook.isPatternEncodingScreen(dummyScreen));
    }

    @Test
    public void testTransferBoMRejectsInvalidInputsSafely() {
        Assertions.assertFalse(Ae2PatternTransferHelper.transferBoMToPatternTerminal(null, null, Collections.emptySet()));

        Screen dummyScreen = new Screen(Component.literal("Dummy")) {};
        MultiblockBOMSummary emptySummary = new MultiblockBOMSummary(List.of(), List.of(), 0, 0);
        Assertions.assertFalse(Ae2PatternTransferHelper.transferBoMToPatternTerminal(dummyScreen, emptySummary, Collections.emptySet()));
    }

    @Test
    public void testBoMPreparedFilteringBehavior() {
        ResourceLocation casingId = ResourceLocation.tryParse("gtceu:solid_machine_casing");
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");

        MultiblockBOMSummary.BOMItemEntry casingEntry = new MultiblockBOMSummary.BOMItemEntry(
                casingId, "Solid Machine Casing", 10, 0, 10, PartCategory.CASING, List.of("Electric Blast Furnace")
        );
        MultiblockBOMSummary.BOMItemEntry controllerEntry = new MultiblockBOMSummary.BOMItemEntry(
                controllerId, "Electric Blast Furnace", 1, 0, 1, PartCategory.CONTROLLER, List.of("Electric Blast Furnace")
        );

        MultiblockBOMSummary summary = new MultiblockBOMSummary(
                List.of(casingEntry, controllerEntry),
                List.of(),
                1,
                2
        );

        Set<ResourceLocation> preparedAll = Set.of(casingId, controllerId);
        MultiblockBOMSummary filtered = summary.filterPrepared(preparedAll);
        Assertions.assertTrue(filtered.aggregatedItems().isEmpty());

        Set<ResourceLocation> preparedCasing = Set.of(casingId);
        MultiblockBOMSummary filteredRemaining = summary.filterPrepared(preparedCasing);
        Assertions.assertEquals(1, filteredRemaining.aggregatedItems().size());
        Assertions.assertEquals(controllerId, filteredRemaining.aggregatedItems().get(0).itemId());
    }

    @Test
    public void testAllBoMItemsIncludedInPatternInputs() {
        Assertions.assertTrue(Ae2PatternTransferHelper.buildPatternIngredients(null).isEmpty());

        ResourceLocation casingId = ResourceLocation.tryParse("minecraft:iron_block");
        ResourceLocation controllerId = ResourceLocation.tryParse("minecraft:furnace");

        MultiblockBOMSummary.BOMItemEntry casingEntry = new MultiblockBOMSummary.BOMItemEntry(
                casingId, "Iron Block", 10, 0, 10, PartCategory.CASING, List.of("Blast Furnace")
        );
        MultiblockBOMSummary.BOMItemEntry controllerEntry = new MultiblockBOMSummary.BOMItemEntry(
                controllerId, "Furnace", 1, 0, 1, PartCategory.CONTROLLER, List.of("Blast Furnace")
        );

        MultiblockBOMSummary summary = new MultiblockBOMSummary(
                List.of(casingEntry, controllerEntry),
                List.of(),
                1,
                2
        );

        var ingredients = Ae2PatternTransferHelper.buildPatternIngredients(summary);
        Assertions.assertEquals(2, ingredients.size());
        Assertions.assertEquals(10, ingredients.get(0).get(0).amount());
        Assertions.assertEquals(1, ingredients.get(1).get(0).amount());
    }
}
