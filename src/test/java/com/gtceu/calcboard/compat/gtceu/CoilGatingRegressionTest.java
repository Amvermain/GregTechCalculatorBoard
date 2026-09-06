package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.badge.GTBadgeProvider;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CoilGatingRegressionTest {

    @BeforeAll
    public static void setup() {
        GTBadgeProvider.registerAll();
    }

    @Test
    @DisplayName("Non-coil machine in a coil-capable category must not display coil badge on card or return coil temperature")
    public void testNonCoilMachineInCoilCategoryDoesNotExposeCoilBadgeOnCard() {
        ResourceLocation categoryId = ResourceLocation.tryParse("gtceu:void_drilling");
        ResourceLocation coilMachineId = ResourceLocation.tryParse("gtceu:void_excavator");
        ResourceLocation nonCoilMachineId = ResourceLocation.tryParse("gtceu:void_extractor");

        MultiblockDetector.registerCoilMultiblock(coilMachineId, categoryId);
        MultiblockDetector.registerMultiblock(nonCoilMachineId);

        Assertions.assertTrue(MultiblockDetector.isCoilRecipeCategory(categoryId));
        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(coilMachineId));
        Assertions.assertFalse(MultiblockDetector.isCoilMultiblock(nonCoilMachineId));

        RecipeNode extractorNode = new RecipeNode("test_extractor", "Void Extractor", 100, 100, GTVoltageTier.LV);
        extractorNode.setMultiblock(true);
        extractorNode.setRecipeCategoryId(categoryId);
        extractorNode.setMachineIcon(nonCoilMachineId);
        extractorNode.setRecipeTemperature(0);

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(extractorNode);
        boolean hasCoilBadge = badges.stream().anyMatch(b -> b.text().startsWith("♨"));
        Assertions.assertFalse(hasCoilBadge, "Card must not display ♨ coil badge when the current machine icon does not support coils");

        int installedTemp = CoilHelper.getInstalledCoilTemperature(extractorNode);
        Assertions.assertEquals(0, installedTemp, "Non-coil machine must return 0 installed coil temperature");

        List<AddonCategory> categories = CategoryCapability.DEFAULT.getActiveCategoriesForNode(extractorNode);
        Assertions.assertFalse(categories.contains(AddonCategory.COIL), "Parts dialog must not contain COIL tab for non-coil machine");
    }

    @Test
    @DisplayName("Coil-capable machine in a coil category correctly exposes coil badge on card and in dialog")
    public void testCoilMachineInCoilCategoryCorrectlyExposesCoilBadgeOnCard() {
        ResourceLocation categoryId = ResourceLocation.tryParse("gtceu:void_drilling_coil");
        ResourceLocation coilMachineId = ResourceLocation.tryParse("gtceu:void_excavator_real");

        MultiblockDetector.registerCoilMultiblock(coilMachineId, categoryId);

        RecipeNode excavatorNode = new RecipeNode("test_excavator", "Void Excavator", 100, 100, GTVoltageTier.EV);
        excavatorNode.setMultiblock(true);
        excavatorNode.setRecipeCategoryId(categoryId);
        excavatorNode.setMachineIcon(coilMachineId);
        excavatorNode.setRecipeTemperature(0);

        int installedTemp = CoilHelper.getInstalledCoilTemperature(excavatorNode);
        Assertions.assertTrue(installedTemp > 0, "Coil machine must have positive default coil temperature");

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(excavatorNode);
        boolean hasCoilBadge = badges.stream().anyMatch(b -> b.text().startsWith("♨"));
        Assertions.assertTrue(hasCoilBadge, "Card must display ♨ coil badge when machine supports coils");
    }

    @Test
    @DisplayName("Fixed structural coil casing machine must not be registered as coil multiblock")
    public void testFixedStructuralCoilMachineGating() {
        ResourceLocation fixedMachineId = ResourceLocation.tryParse("start:heat_chamber");
        MultiblockStructureDef def = new MultiblockStructureDef(
                fixedMachineId,
                "Heat Chamber",
                List.of(
                        new MultiblockStructurePart(fixedMachineId, "Heat Chamber", 1, PartCategory.CONTROLLER),
                        new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil Block", 16, PartCategory.CASING)
                ),
                0, 1, 1, 1, 0, 0, 1
        );
        MultiblockStructureCatalog.registerManualStructure(def);

        Assertions.assertFalse(MultiblockDetector.isCoilMultiblock(fixedMachineId));

        RecipeNode node = new RecipeNode("test_heat", "Heat Chamber", 100, 100, GTVoltageTier.ZPM);
        node.setMultiblock(true);
        node.setMachineIcon(fixedMachineId);

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(node);
        boolean hasCoilBadge = badges.stream().anyMatch(b -> b.text().startsWith("♨"));
        Assertions.assertFalse(hasCoilBadge);
        Assertions.assertEquals(0, CoilHelper.getInstalledCoilTemperature(node));
    }
}
