package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.NodeWorkstationResolver;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCapabilityScanner;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuWorkstationResolver;
import com.gtceu.calcboard.testutil.TestMultiblockFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MultiblockExclusiveRecipeBugTest {

    private static final ResourceLocation LCR_CAT = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
    private static final ResourceLocation CR_CAT = ResourceLocation.tryParse("gtceu:chemical_reactor");
    private static final ResourceLocation LCR_ID = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
    private static final ResourceLocation HV_CR_ID = ResourceLocation.tryParse("gtceu:hv_chemical_reactor");

    @BeforeEach
    void setUp() {
        TestMultiblockFixtures.initTestEnvironmentDefaults();
    }

    @Test
    void testChemicalReactorMustNotMapToLargeChemicalReactorInReverse() {
        ResourceLocation related = GTCEuCapabilityScanner.getRelatedRecipeCategory(CR_CAT);
        Assertions.assertNull(related, "chemical_reactor should not map forward to large_chemical_reactor");
    }

    @Test
    void testLargeChemicalReactorCapabilityMustNotHaveSingleblockOption() {
        CategoryCapabilityMatrix matrix = CategoryCapabilityMatrix.getInstance();
        CategoryCapability cap = matrix.getCapability(LCR_CAT);

        Assertions.assertNotNull(cap);
        Assertions.assertTrue(cap.hasMultiblockOption());
        Assertions.assertFalse(cap.hasSingleblockOption(), "large_chemical_reactor category must not have singleblock option");

        for (ResourceLocation ws : cap.availableWorkstations()) {
            Assertions.assertTrue(MultiblockDetector.isMultiblock(ws),
                    "All workstations for large_chemical_reactor must be multiblocks, but found: " + ws);
        }
    }

    @Test
    void testGetWorkstationForTierMustReturnNullForMultiblockOnlyCategory() {
        RecipeNode lcrNode = RecipeNode.create(LCR_ID, "Large Chemical Reactor (Sulfuric Acid)", 320.0, 480.0, GTVoltageTier.HV);
        lcrNode.setRecipeCategoryId(LCR_CAT);
        lcrNode.setMultiblock(true);
        lcrNode.setAvailableWorkstations(List.of(LCR_ID));

        ResourceLocation ws = GTCEuWorkstationResolver.getWorkstationForTier(lcrNode, GTVoltageTier.HV);
        Assertions.assertNull(ws, "getWorkstationForTier must return null for multiblock-only nodes");
    }

    @Test
    void testNodeWorkstationResolverFiltersOutSingleblocksForMultiblockOnlyNode() {
        RecipeNode lcrNode = RecipeNode.create(LCR_ID, "Large Chemical Reactor (Sulfuric Acid)", 320.0, 480.0, GTVoltageTier.HV);
        lcrNode.setRecipeCategoryId(LCR_CAT);
        lcrNode.setMultiblock(true);
        lcrNode.setAvailableWorkstations(List.of(LCR_ID, HV_CR_ID));

        Assertions.assertFalse(NodeWorkstationResolver.hasSingleblockOption(lcrNode),
                "Multiblock-only node must not report having singleblock option");
        ResourceLocation ws = NodeWorkstationResolver.getWorkstationForTier(lcrNode, GTVoltageTier.HV);
        Assertions.assertNull(ws, "Multiblock-only node must not return singleblock workstation for tier");
    }

    @Test
    void testChemicalReactorStillSharesMultiblocks() {
        CategoryCapabilityMatrix matrix = CategoryCapabilityMatrix.getInstance();
        CategoryCapability crCap = matrix.getCapability(CR_CAT);

        Assertions.assertNotNull(crCap);
        Assertions.assertTrue(crCap.hasSingleblockOption(), "chemical_reactor must have singleblock option");
        Assertions.assertTrue(crCap.hasMultiblockOption(), "chemical_reactor must have multiblock option");
        Assertions.assertTrue(crCap.availableWorkstations().contains(LCR_ID),
                "chemical_reactor must still allow LCR multiblock");
    }

    @Test
    void testLargeChemicalReactorNodeWorkstationsNeverContainSingleblocks() {
        RecipeNode lcrNode = RecipeNode.create(LCR_ID, "Large Chemical Reactor (Sulfuric Acid)", 320.0, 480.0, GTVoltageTier.HV);
        lcrNode.setRecipeCategoryId(LCR_CAT);

        CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(LCR_CAT);
        if (cap != null && !cap.availableWorkstations().isEmpty()) {
            lcrNode.getAvailableWorkstations().addAll(cap.availableWorkstations());
            if (cap.hasMultiblockOption() && !cap.hasSingleblockOption()) {
                lcrNode.setMultiblock(true);
                lcrNode.getAvailableWorkstations().removeIf(ws -> !MultiblockDetector.isMultiblock(ws));
            }
        }

        Assertions.assertTrue(lcrNode.isMultiblock(), "Node must be set to multiblock");
        Assertions.assertFalse(lcrNode.getAvailableWorkstations().contains(HV_CR_ID),
                "Available workstations must not contain singleblock HV chemical reactor");
        for (ResourceLocation ws : lcrNode.getAvailableWorkstations()) {
            Assertions.assertTrue(MultiblockDetector.isMultiblock(ws),
                    "All available workstations must be multiblocks: " + ws);
        }
    }
}
