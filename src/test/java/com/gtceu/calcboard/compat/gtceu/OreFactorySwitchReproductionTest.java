package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeHardwareReconciler;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import com.gtceu.calcboard.testutil.TestFixtures;
import com.gtceu.calcboard.testutil.TestMultiblockFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class OreFactorySwitchReproductionTest {

    private final ResourceLocation eofId = ResourceLocation.tryParse("gtceu:electric_ore_factory");
    private final ResourceLocation opfId = ResourceLocation.tryParse("gtceu:super_electric_ore_factory");
    private final ResourceLocation catId = ResourceLocation.tryParse("gtceu:electric_ore_processing");

    @BeforeEach
    public void setup() {
        MultiblockDetector.reinitialize();
        TestMultiblockFixtures.initTestEnvironmentDefaults();

        // Register multiblocks
        MultiblockDetector.registerMultiblock(eofId);
        MultiblockDetector.registerMultiblock(opfId);
        MultiblockDetector.registerThroughputBoostingMultiblock(opfId);
    }

    @Test
    public void testSwitchBetweenEofAndOpf() {
        // Base recipe: 16s (320 ticks), MV (120 EU/t)
        RecipeNode node = RecipeNode.create(
                opfId,
                "Electric Ore Processing",
                320.0,
                120.0,
                GTVoltageTier.MV
        );
        node.setRecipeCategoryId(catId);
        node.setAvailableWorkstations(List.of(eofId, opfId));
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.MV);

        // Add inputs & energy
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_ore"), "Crushed Ore", 1.0, 1.0f));
        node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));


        // Add 1 primary output (100% chance, 1 count)
        IngredientStack outPrimary = IngredientStack.item(ResourceLocation.tryParse("gtceu:purified_ore"), "Purified Ore", 1.0, 1.0f);
        node.addOutput(outPrimary);

        // Initially equipped with Throughput Boosting (from saved config / preset)
        MachineAddon boost = MachineAddonCatalog.getInstance().getAddon("gtceu:throughput_boosting");
        Assertions.assertNotNull(boost, "Throughput boosting addon must exist");
        node.addAddon(boost);
        NodeHardwareReconciler.clampParallel(node);

        System.out.println("=== INITIAL OPF ===");
        printNodeState("Initial OPF", node);
        Assertions.assertEquals(1, node.getParallel(), "Initial OPF base parallel must be 1 (not polluted by addon multiplier)");
        Assertions.assertEquals(4, node.getTotalParallel(), "Initial OPF with Throughput Boosting must have 4 total parallel");
        Assertions.assertEquals(25.6, node.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(9.375, node.getNominalCyclesPerSecond() * 60.0, 0.01, "OPF production rate must be 9.375/min");

        // 1. Remove trait on OPF before any switch
        node.getAddons().removeIf(a -> a.getId().equals("gtceu:throughput_boosting"));
        NodeHardwareReconciler.clampParallel(node);
        node.markOverclockDirty();
        System.out.println("=== OPF WITHOUT TRAIT (BEFORE SWITCH) ===");
        printNodeState("OPF without trait (before switch)", node);
        Assertions.assertEquals(1, node.getParallel(), "OPF base parallel without trait must be 1");
        Assertions.assertEquals(1, node.getTotalParallel(), "OPF without trait must have 1 total parallel");
        Assertions.assertEquals(16.0, node.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(3.75, node.getNominalCyclesPerSecond() * 60.0, 0.01, "OPF without trait rate must be 3.75/min");

        // Re-add trait
        node.addAddon(boost);
        NodeHardwareReconciler.clampParallel(node);
        node.markOverclockDirty();
        Assertions.assertEquals(1, node.getParallel(), "OPF base parallel must stay 1 after re-adding trait");
        Assertions.assertEquals(4, node.getTotalParallel());

        // 2. Switch to EOF
        System.out.println("=== SWITCHING TO EOF ===");
        NodeHardwareReconciler.reconcileForMachine(node, eofId);
        printNodeState("After switch to EOF", node);
        Assertions.assertEquals(1, node.getParallel(), "EOF base parallel must be 1");
        Assertions.assertEquals(1, node.getTotalParallel(), "EOF must have 1 total parallel");
        Assertions.assertEquals(16.0, node.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(3.75, node.getNominalCyclesPerSecond() * 60.0, 0.01, "EOF output rate must be 3.75/min, not 15.0/min");

        // 3. Switch back to OPF
        System.out.println("=== SWITCHING BACK TO OPF ===");
        NodeHardwareReconciler.reconcileForMachine(node, opfId);
        node.addAddon(boost); // Saved config re-applies trait
        NodeHardwareReconciler.clampParallel(node);
        printNodeState("After switch back to OPF (with trait)", node);
        Assertions.assertEquals(1, node.getParallel(), "OPF base parallel after switch must be 1, not 4");
        Assertions.assertEquals(4, node.getTotalParallel(), "OPF back with Throughput Boosting must have 4 total parallel, not 16");
        Assertions.assertEquals(25.6, node.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(9.375, node.getNominalCyclesPerSecond() * 60.0, 0.01, "OPF output rate must be 9.375/min, not 37.5/min");

        // 4. Remove trait on OPF after switch
        node.getAddons().removeIf(a -> a.getId().equals("gtceu:throughput_boosting"));
        NodeHardwareReconciler.clampParallel(node);
        node.markOverclockDirty();
        System.out.println("=== OPF WITHOUT TRAIT (AFTER SWITCH) ===");
        printNodeState("OPF without trait (after switch)", node);
        Assertions.assertEquals(1, node.getParallel(), "OPF base parallel after removing trait must be 1, not 4");
        Assertions.assertEquals(1, node.getTotalParallel(), "OPF after removing trait must have 1 total parallel, not 4");
        Assertions.assertEquals(16.0, node.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(3.75, node.getNominalCyclesPerSecond() * 60.0, 0.01, "OPF without trait rate must be 3.75/min, not 15.0/min");
    }

    private void printNodeState(String phase, RecipeNode node) {
        double durSec = node.getEffectiveDurationSeconds();
        int basePar = node.getParallel();
        int customPar = node.getCustomParallel();
        int totPar = node.getTotalParallel();
        double outRatePerSec = node.getNominalCyclesPerSecond();
        double outRatePerMin = outRatePerSec * 60.0;
        GTVoltageTier targetTier = node.getTargetTier();
        GTVoltageTier recipeTier = node.getRecipeTier();

        System.out.printf("[%s]%n", phase);
        System.out.printf("  TargetTier: %s (RecipeTier: %s)%n", targetTier, recipeTier);
        System.out.printf("  BaseParallel: %d, CustomParallel: %d, TotalParallel: %d%n", basePar, customPar, totPar);
        System.out.printf("  Addons: %s%n", node.getAddons().stream().map(MachineAddon::getId).toList());
        System.out.printf("  EffectiveDuration: %.2fs (%.1f ticks)%n", durSec, durSec * 20.0);
        System.out.printf("  OutputRate: %.4f/s (%.2f/min)%n", outRatePerSec, outRatePerMin);
        System.out.println();
    }
}
