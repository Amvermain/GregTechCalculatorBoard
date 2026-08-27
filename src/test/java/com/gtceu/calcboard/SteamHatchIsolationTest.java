package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.helper.GTHatchHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SteamHatchIsolationTest {

    private static final GTCEuModAdapter ADAPTER = new GTCEuModAdapter();

    @BeforeAll
    public static void setUp() {
        // Register Steam Grinder definition with strict PartAbilities and candidateBlocks
        ResourceLocation steamGrinderId = ResourceLocation.tryParse("gtceu:steam_grinder");
        List<MultiblockStructurePart> steamParts = List.of(
                new MultiblockStructurePart(steamGrinderId, "Steam Grinder", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:treated_wood_machine_casing"), "Treated Wood Casing", 20, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lp_steam_input_bus"), "LP Steam Input Bus", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lp_steam_output_bus"), "LP Steam Output Bus", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lp_steam_input_hatch"), "LP Steam Input Hatch", 1, PartCategory.HATCH_BUS)
        );

        MultiblockStructureDef steamGrinderDef = new MultiblockStructureDef(
                steamGrinderId,
                "Steam Grinder",
                steamParts,
                0, // coil
                0, // energy
                1, // in bus
                1, // out bus
                1, // in hatch
                0, // out hatch
                0, // maint
                Set.of("STEAM_IMPORT_ITEMS", "STEAM_EXPORT_ITEMS", "STEAM_IMPORT_FLUIDS"),
                Set.of(
                        ResourceLocation.tryParse("gtceu:treated_wood_machine_casing"),
                        ResourceLocation.tryParse("gtceu:lp_steam_input_bus"),
                        ResourceLocation.tryParse("gtceu:hp_steam_input_bus"),
                        ResourceLocation.tryParse("gtceu:lp_steam_output_bus"),
                        ResourceLocation.tryParse("gtceu:hp_steam_output_bus"),
                        ResourceLocation.tryParse("gtceu:lp_steam_input_hatch"),
                        ResourceLocation.tryParse("gtceu:hp_steam_input_hatch")
                )
        );
        MultiblockStructureCatalog.registerManualStructure(steamGrinderDef);

        // Register Large Chemical Reactor definition (electric multiblock)
        ResourceLocation lcrId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        List<MultiblockStructurePart> lcrParts = List.of(
                new MultiblockStructurePart(lcrId, "Large Chemical Reactor", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:inert_machine_casing"), "Inert Casing", 20, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_input_bus"), "LV Input Bus", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_output_bus"), "LV Output Bus", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS)
        );

        MultiblockStructureDef lcrDef = new MultiblockStructureDef(
                lcrId,
                "Large Chemical Reactor",
                lcrParts,
                1, // coil
                1, // energy
                1, // in bus
                1, // out bus
                1, // in hatch
                1, // out hatch
                1, // maint
                Set.of("IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS", "EXPORT_FLUIDS", "INPUT_ENERGY", "MAINTENANCE", "PARALLEL_HATCH"),
                Set.of(
                        ResourceLocation.tryParse("gtceu:inert_machine_casing"),
                        ResourceLocation.tryParse("gtceu:lv_input_bus"),
                        ResourceLocation.tryParse("gtceu:lv_output_bus"),
                        ResourceLocation.tryParse("gtceu:lv_input_hatch"),
                        ResourceLocation.tryParse("gtceu:lv_output_hatch"),
                        ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"),
                        ResourceLocation.tryParse("gtceu:maintenance_hatch")
                )
        );
        MultiblockStructureCatalog.registerManualStructure(lcrDef);
    }

    @Test
    @DisplayName("Steam Grinder strictly accepts only steam buses/hatches and rejects electric hatches")
    public void testSteamGrinderHatchIsolation() {
        RecipeNode steamNode = RecipeNode.create("Steam Grinder Recipe", 100.0, 10.0, GTVoltageTier.ULV);
        steamNode.setMultiblock(true);
        steamNode.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_grinder"));
        steamNode.setEnergyType(EnergyType.ELECTRIC_EU);

        // Discovery collector
        List<com.gtceu.calcboard.api.MachineAddon> allHatches = new ArrayList<>();
        GTHatchHelper.discoverGTCEuHatches(allHatches);

        GTHatchAddon steamInBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("steam_input_bus"))
                .findFirst().orElseThrow();
        GTHatchAddon steamOutBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("steam_output_bus"))
                .findFirst().orElseThrow();
        GTHatchAddon steamInHatch = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("steam_input_hatch") || h.getId().contains("steam_hatch"))
                .findFirst().orElseThrow();

        GTHatchAddon lvInBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().equals("gtceu:lv_input_bus"))
                .findFirst().orElseThrow();
        GTHatchAddon lvOutBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().equals("gtceu:lv_output_bus"))
                .findFirst().orElseThrow();
        GTHatchAddon meDualHatch = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("me_dual"))
                .findFirst().orElseThrow();

        GTEnergyHatchAddon lvEnergyHatch = new GTEnergyHatchAddon("gtceu:lv_energy_input_hatch", "LV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), GTVoltageTier.LV, 2, false, false, false);
        MachineAddon maintHatch = new MachineAddon("gtceu:maintenance_hatch", "Maintenance Hatch", AddonCategory.MAINTENANCE, "",
                ResourceLocation.tryParse("gtceu:maintenance_hatch"));
        GTParallelHatchAddon parallelHatch = new GTParallelHatchAddon("gtceu:parallel_hatch", "Parallel Hatch", "",
                ResourceLocation.tryParse("gtceu:parallel_hatch"), 4, false);

        // 1. Steam hatches MUST be compatible
        assertTrue(ADAPTER.isAddonCompatible(steamNode, steamInBus), "Steam In Bus should be compatible with Steam Grinder");
        assertTrue(ADAPTER.isAddonCompatible(steamNode, steamOutBus), "Steam Out Bus should be compatible with Steam Grinder");
        assertTrue(ADAPTER.isAddonCompatible(steamNode, steamInHatch), "Steam In Hatch should be compatible with Steam Grinder");

        // 2. Electric hatches MUST be strictly rejected
        assertFalse(ADAPTER.isAddonCompatible(steamNode, lvInBus), "Electric LV In Bus must be rejected by Steam Grinder");
        assertFalse(ADAPTER.isAddonCompatible(steamNode, lvOutBus), "Electric LV Out Bus must be rejected by Steam Grinder");
        assertFalse(ADAPTER.isAddonCompatible(steamNode, meDualHatch), "ME Dual Hatch must be rejected by Steam Grinder");
        assertFalse(ADAPTER.isAddonCompatible(steamNode, lvEnergyHatch), "Energy Hatch must be rejected by Steam Grinder");
        assertFalse(ADAPTER.isAddonCompatible(steamNode, maintHatch), "Maintenance Hatch must be rejected by Steam Grinder");
        assertFalse(ADAPTER.isAddonCompatible(steamNode, parallelHatch), "Parallel Hatch must be rejected by Steam Grinder");

        // 3. Category capability deduction check
        List<AddonCategory> applicableCategories = ADAPTER.getApplicableAddonCategories(steamNode);
        assertTrue(applicableCategories.contains(AddonCategory.HATCH_BUS), "Steam Grinder should have HATCH_BUS category");
        assertFalse(applicableCategories.contains(AddonCategory.ENERGY_HATCH), "Steam Grinder should NOT have ENERGY_HATCH category");
        assertFalse(applicableCategories.contains(AddonCategory.PARALLEL), "Steam Grinder should NOT have PARALLEL category");
        assertFalse(applicableCategories.contains(AddonCategory.MAINTENANCE), "Steam Grinder should NOT have MAINTENANCE category");
    }

    @Test
    @DisplayName("Electric Large Chemical Reactor accepts electric hatches and rejects steam hatches")
    public void testElectricMultiblockHatchIsolation() {
        RecipeNode lcrNode = RecipeNode.create("LCR Recipe", 100.0, 30.0, GTVoltageTier.LV);
        lcrNode.setMultiblock(true);
        lcrNode.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        lcrNode.setEnergyType(EnergyType.ELECTRIC_EU);

        List<com.gtceu.calcboard.api.MachineAddon> allHatches = new ArrayList<>();
        GTHatchHelper.discoverGTCEuHatches(allHatches);

        GTHatchAddon steamInBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("steam_input_bus"))
                .findFirst().orElseThrow();
        GTHatchAddon lvInBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().equals("gtceu:lv_input_bus"))
                .findFirst().orElseThrow();
        GTHatchAddon meDualHatch = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("me_dual"))
                .findFirst().orElseThrow();

        // 1. Electric hatches compatible
        assertTrue(ADAPTER.isAddonCompatible(lcrNode, lvInBus), "LV In Bus should be compatible with LCR");
        assertTrue(ADAPTER.isAddonCompatible(lcrNode, meDualHatch), "ME Dual Hatch should be compatible with LCR");

        // 2. Steam hatches rejected
        assertFalse(ADAPTER.isAddonCompatible(lcrNode, steamInBus), "Steam In Bus must be rejected by LCR");

        // 3. Category checks
        List<AddonCategory> applicableCategories = ADAPTER.getApplicableAddonCategories(lcrNode);
        assertTrue(applicableCategories.contains(AddonCategory.HATCH_BUS));
        assertTrue(applicableCategories.contains(AddonCategory.ENERGY_HATCH));
        assertTrue(applicableCategories.contains(AddonCategory.PARALLEL));
        assertTrue(applicableCategories.contains(AddonCategory.MAINTENANCE));
    }
}
