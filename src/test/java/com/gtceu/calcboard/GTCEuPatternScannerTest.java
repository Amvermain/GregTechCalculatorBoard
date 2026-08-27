package com.gtceu.calcboard;

import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuPatternScanner;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class GTCEuPatternScannerTest {

    @Test
    void testEmptyAndNullDef() {
        GTCEuPatternScanner.PatternScanResult res = GTCEuPatternScanner.scanPattern(null);
        Assertions.assertNotNull(res);
        Assertions.assertTrue(res.allowedAbilities().isEmpty());
        Assertions.assertTrue(res.candidateBlocks().isEmpty());
    }

    @Test
    void testMultiblockStructureDefAbilitySupport() {
        ResourceLocation ctrlId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation casingId = ResourceLocation.tryParse("gtceu:inert_machine_casing");

        MultiblockStructureDef def = new MultiblockStructureDef(
                ctrlId,
                "Large Chemical Reactor",
                List.of(),
                16,
                2,
                4,
                4,
                4,
                4,
                1,
                Set.of("INPUT_ENERGY", "IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS", "EXPORT_FLUIDS", "MAINTENANCE", "PARALLEL_HATCH"),
                Set.of(casingId)
        );

        Assertions.assertTrue(def.supportsAbility("INPUT_ENERGY"));
        Assertions.assertTrue(def.supportsAbility("input_energy"));
        Assertions.assertTrue(def.supportsAbility("PARALLEL_HATCH"));
        Assertions.assertTrue(def.supportsAbility("IMPORT_FLUIDS"));
        Assertions.assertFalse(def.supportsAbility("ROTOR_HOLDER"));

        Assertions.assertTrue(def.isCandidateBlock(casingId));
        Assertions.assertFalse(def.isCandidateBlock(ResourceLocation.tryParse("minecraft:stone")));
    }
}
