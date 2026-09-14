package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuOverclockHelper;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import com.gtceu.calcboard.testutil.TestMultiblockFixtures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class GTCEuPerfectOverclockTest {

    private static final ResourceLocation LCR_ID = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
    private static final ResourceLocation EBF_ID = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
    private static final ResourceLocation CUSTOM_POC_ID = ResourceLocation.tryParse("gtceu:custom_perfect_machine");

    private GTCEuModAdapter adapter;

    @BeforeEach
    public void setup() {
        ModCompatHelper.setTestOverride("start_core", false);
        ModCompatHelper.setTestOverride("start", false);
        GTCEuCoilModifierHelper.clearCache();
        MultiblockDetector.reinitialize();
        TestMultiblockFixtures.initTestEnvironmentDefaults();
        adapter = new GTCEuModAdapter();
    }

    @AfterEach
    public void tearDown() {
        ModCompatHelper.clearTestOverrides();
        GTCEuCoilModifierHelper.clearCache();
        MultiblockDetector.reinitialize();
    }

    @Test
    @DisplayName("Modifier names are deterministically classified as POC or non-POC")
    public void testModifierNameMatching() {
        Assertions.assertTrue(GTCEuOverclockHelper.isPerfectOverclockModifierName("OC_PERFECT"));
        Assertions.assertTrue(GTCEuOverclockHelper.isPerfectOverclockModifierName("OC_PERFECT_SUBTICK"));
        Assertions.assertTrue(GTCEuOverclockHelper.isPerfectOverclockModifierName("oc_perfect_subtick"));
        Assertions.assertTrue(GTCEuOverclockHelper.isPerfectOverclockModifierName("PERFECT_OC"));
        Assertions.assertTrue(GTCEuOverclockHelper.isPerfectOverclockModifierName("PERFECT_OVERCLOCK"));

        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName("NON_PERFECT_OVERCLOCK_SUBTICK"));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName("NONPERFECT_OVERCLOCK"));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName("NOT_PERFECT_OVERCLOCK"));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName("IMPERFECT_OVERCLOCK"));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName("ebfOverclock"));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName("EBF_PERFECT_OC"));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName("STANDARD_OVERCLOCK"));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName(""));
        Assertions.assertFalse(GTCEuOverclockHelper.isPerfectOverclockModifierName(null));
    }

    @Test
    @DisplayName("LCR is recognized as perfect overclock in baseline GTCEu")
    public void testBaselineLcrIsPerfectOverclockMachine() {
        Assertions.assertTrue(MultiblockDetector.isPerfectOverclockMachine(LCR_ID));
        Assertions.assertTrue(adapter.hasNativePerfectOverclock(LCR_ID));
        Assertions.assertFalse(MultiblockDetector.isPerfectOverclockMachine(EBF_ID));
        Assertions.assertFalse(adapter.hasNativePerfectOverclock(EBF_ID));
    }

    @Test
    @DisplayName("In Star Technology, LCR is a coil reactor and NOT a POC machine")
    public void testStarTLcrIsNotPerfectOverclockMachine() {
        try {
            ModCompatHelper.setTestOverride("start_core", true);
            GTCEuCoilModifierHelper.clearCache();
            MultiblockDetector.reinitialize();
            TestMultiblockFixtures.initTestEnvironmentDefaults();

            Assertions.assertFalse(MultiblockDetector.isPerfectOverclockMachine(LCR_ID));
            Assertions.assertFalse(adapter.hasNativePerfectOverclock(LCR_ID));

            RecipeNode lcrNode = RecipeNode.create(LCR_ID, "LCR Synthesis", 100.0, 30.0, GTVoltageTier.LV);
            Assertions.assertEquals(OverclockMode.STANDARD, lcrNode.getOverclockMode());
        } finally {
            ModCompatHelper.setTestOverride("start_core", false);
            GTCEuCoilModifierHelper.clearCache();
            MultiblockDetector.reinitialize();
            TestMultiblockFixtures.initTestEnvironmentDefaults();
        }
    }

    @Test
    @DisplayName("Custom machine registered via scanner or catalog is recognized as POC")
    public void testCustomMachineRegistration() {
        Assertions.assertFalse(MultiblockDetector.isPerfectOverclockMachine(CUSTOM_POC_ID));

        MultiblockDetector.registerPerfectOverclockMachine(CUSTOM_POC_ID);
        Assertions.assertTrue(MultiblockDetector.isPerfectOverclockMachine(CUSTOM_POC_ID));

        MultiblockDetector.unregisterPerfectOverclockMachine(CUSTOM_POC_ID);
        Assertions.assertFalse(MultiblockDetector.isPerfectOverclockMachine(CUSTOM_POC_ID));
    }

    @Test
    @DisplayName("Creating node with POC machine defaults to OverclockMode.PERFECT")
    public void testNodeCreationSetsDefaultPerfectOverclock() {
        RecipeNode lcrNode = RecipeNode.create(LCR_ID, "LCR Synthesis", 100.0, 30.0, GTVoltageTier.LV);
        Assertions.assertEquals(OverclockMode.PERFECT, lcrNode.getOverclockMode());

        RecipeNode ebfNode = RecipeNode.create(EBF_ID, "EBF Smelting", 100.0, 30.0, GTVoltageTier.LV);
        Assertions.assertEquals(OverclockMode.STANDARD, ebfNode.getOverclockMode());
    }

    @Test
    @DisplayName("Icon transition from non-POC to POC sets PERFECT, and POC to non-POC reverts to STANDARD")
    public void testIconTransitionUpdatesOverclockMode() {
        RecipeNode node = RecipeNode.create(EBF_ID, "Processing Node", 100.0, 30.0, GTVoltageTier.LV);
        Assertions.assertEquals(OverclockMode.STANDARD, node.getOverclockMode());

        node.setMachineIcon(LCR_ID);
        Assertions.assertEquals(OverclockMode.PERFECT, node.getOverclockMode());

        node.setMachineIcon(EBF_ID);
        Assertions.assertEquals(OverclockMode.STANDARD, node.getOverclockMode());
    }

    @Test
    @DisplayName("Icon transition from POC to another POC preserves overclock mode")
    public void testPocToPocPreservesOverclockMode() {
        MultiblockDetector.registerPerfectOverclockMachine(CUSTOM_POC_ID);

        RecipeNode node = RecipeNode.create(LCR_ID, "LCR Synthesis", 100.0, 30.0, GTVoltageTier.LV);
        Assertions.assertEquals(OverclockMode.PERFECT, node.getOverclockMode());

        node.setMachineIcon(CUSTOM_POC_ID);
        Assertions.assertEquals(OverclockMode.PERFECT, node.getOverclockMode());
    }

    @Test
    @DisplayName("Non-standard non-perfect mode is preserved when switching away from POC")
    public void testCustomOverclockPreservedOnNonPocTransition() {
        RecipeNode node = RecipeNode.create(LCR_ID, "LCR Synthesis", 100.0, 30.0, GTVoltageTier.LV);
        node.setOverclockMode(OverclockMode.LOSSLESS);

        node.setMachineIcon(EBF_ID);
        Assertions.assertEquals(OverclockMode.LOSSLESS, node.getOverclockMode());
    }

    @Test
    @DisplayName("Manual override to STANDARD on POC machine is preserved across NBT roundtrip")
    public void testNbtRoundtripPreservesExplicitStandardOverride() {
        RecipeNode node = RecipeNode.create(LCR_ID, "LCR Node", 100.0, 30.0, GTVoltageTier.LV);
        Assertions.assertEquals(OverclockMode.PERFECT, node.getOverclockMode());

        node.setOverclockMode(OverclockMode.STANDARD);
        Assertions.assertEquals(OverclockMode.STANDARD, node.getOverclockMode());

        CompoundTag tag = RecipeNodeSerializer.serialize(node);
        Assertions.assertTrue(tag.contains("overclockMode"));
        Assertions.assertEquals("STANDARD", tag.getString("overclockMode"));

        RecipeNode loaded = RecipeNodeSerializer.deserialize(tag);
        Assertions.assertEquals(OverclockMode.STANDARD, loaded.getOverclockMode());
    }

    @Test
    @DisplayName("Legacy save without overclockMode tag defaults to PERFECT for POC machine")
    public void testLegacySaveWithoutTagDefaultsToPerfect() {
        RecipeNode node = RecipeNode.create(LCR_ID, "LCR Node", 100.0, 30.0, GTVoltageTier.LV);
        CompoundTag tag = RecipeNodeSerializer.serialize(node);
        tag.remove("overclockMode");

        RecipeNode loaded = RecipeNodeSerializer.deserialize(tag);
        Assertions.assertEquals(OverclockMode.PERFECT, loaded.getOverclockMode());
    }

    @Test
    @DisplayName("Corrupted or invalid overclockMode in NBT does not crash deserializer")
    public void testCorruptedOverclockModeTagDoesNotThrow() {
        RecipeNode node = RecipeNode.create(EBF_ID, "EBF Node", 100.0, 30.0, GTVoltageTier.LV);
        CompoundTag tag = RecipeNodeSerializer.serialize(node);
        tag.putString("overclockMode", "NON_EXISTENT_MODE_123");

        RecipeNode loaded = Assertions.assertDoesNotThrow(() -> RecipeNodeSerializer.deserialize(tag));
        Assertions.assertNotNull(loaded);
        Assertions.assertEquals(OverclockMode.STANDARD, loaded.getOverclockMode());
    }

    @Test
    @DisplayName("Registering a POC machine does not automatically mark it as a multiblock")
    public void testSingleblockPocMachineIsNotForcedToMultiblock() {
        ResourceLocation singleblockPoc = ResourceLocation.tryParse("gtceu:singleblock_poc_machine");
        MultiblockDetector.registerPerfectOverclockMachine(singleblockPoc);

        Assertions.assertTrue(MultiblockDetector.isPerfectOverclockMachine(singleblockPoc));
        Assertions.assertFalse(MultiblockDetector.isMultiblock(singleblockPoc));

        MultiblockDetector.unregisterPerfectOverclockMachine(singleblockPoc);
    }
}
