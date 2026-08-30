package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.start.StarTModAdapter;
import com.gtceu.calcboard.compat.systeams.SysteamsModAdapter;
import com.gtceu.calcboard.compat.thermal.ThermalModAdapter;
import com.gtceu.calcboard.compat.vanilla.VanillaModAdapter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for RFC-004: ModAdapterRegistry and IModAdapter SPI routing.
 */
public class ModAdapterRegistryTest {

    @BeforeEach
    public void setup() {
        com.gtceu.calcboard.api.util.ModCompatHelper.clearTestOverrides();
        ModAdapterRegistry.reset();
        ModAdapterRegistry.init();
    }

    @Test
    public void testRegistryInitializationAndPriority() {
        List<IModAdapter> adapters = ModAdapterRegistry.getAllLoadedAdapters();
        Assertions.assertFalse(adapters.isEmpty());

        // Verify priority sorting: Systeams (110) > Thermal (100) / GTCEu (100) > Vanilla (0)
        IModAdapter first = adapters.get(0);
        Assertions.assertEquals("systeams", first.getModId());
        Assertions.assertEquals(110, first.getPriority());

        IModAdapter fallback = ModAdapterRegistry.getFallbackAdapter();
        Assertions.assertNotNull(fallback);
        Assertions.assertEquals("minecraft", fallback.getModId());
    }

    @Test
    public void testCategoryRouting() {
        // 1. GTCEu category routing
        IModAdapter gtAdapter = ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        Assertions.assertInstanceOf(GTCEuModAdapter.class, gtAdapter);

        // 2. Thermal category routing
        IModAdapter thAdapter = ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        Assertions.assertInstanceOf(ThermalModAdapter.class, thAdapter);

        // 3. Systeams category routing
        IModAdapter sysAdapter = ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("systeams:boiling"));
        Assertions.assertInstanceOf(SysteamsModAdapter.class, sysAdapter);

        // 4. Vanilla / unknown category routing
        IModAdapter vanAdapter = ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("minecraft:smelting"));
        Assertions.assertInstanceOf(VanillaModAdapter.class, vanAdapter);
    }

    @Test
    public void testNodeRouting() {
        // 1. GTCEu Node
        RecipeNode gtNode = RecipeNode.create("EBF", 20.0, 120.0, GTVoltageTier.MV);
        gtNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        IModAdapter gtAdapter = ModAdapterRegistry.getAdapterForNode(gtNode);
        Assertions.assertInstanceOf(GTCEuModAdapter.class, gtAdapter);
        Assertions.assertEquals("gtceu", gtAdapter.getModId());
        Assertions.assertFalse(gtAdapter instanceof StarTModAdapter);

        // 2. Thermal Dynamo Node
        RecipeNode dynNode = RecipeNode.create("Lapidary Dynamo", 1500.0, 50.0, GTVoltageTier.LV);
        dynNode.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        dynNode.setMachineIcon(ResourceLocation.tryParse("thermal:dynamo_lapidary"));
        dynNode.setGenerator(true);
        IModAdapter thAdapter = ModAdapterRegistry.getAdapterForNode(dynNode);
        Assertions.assertInstanceOf(ThermalModAdapter.class, thAdapter);

        // 3. Systeams Boiler Node
        RecipeNode boilerNode = RecipeNode.create("Lapidary Boiler", 1500.0, 0.0, GTVoltageTier.LV);
        boilerNode.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        boilerNode.setMachineIcon(ResourceLocation.tryParse("systeams:lapidary_boiler"));
        IModAdapter sysAdapter = ModAdapterRegistry.getAdapterForNode(boilerNode);
        Assertions.assertInstanceOf(SysteamsModAdapter.class, sysAdapter);

        // 4. Star Technology Gravitational Compression Chamber (GCC) - routes to StarTModAdapter
        RecipeNode gccNode = RecipeNode.create("Gravitational Compression Chamber [GCC]", 200.0, 1920.0, GTVoltageTier.EV);
        gccNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:compressor"));
        gccNode.setMachineIcon(ResourceLocation.tryParse("start_core:gravitational_compression_chamber"));
        IModAdapter gccAdapter = ModAdapterRegistry.getAdapterForNode(gccNode);
        Assertions.assertInstanceOf(StarTModAdapter.class, gccAdapter);
        Assertions.assertEquals("start_core", gccAdapter.getModId());
        Assertions.assertFalse(com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.isThermalMachine(gccNode));

        // 5. Plasma Turbines (LPT, SPT, NPT)
        RecipeNode nptNode = RecipeNode.create("Nyinsane Plasma Turbine", 10.0, 196608.0, GTVoltageTier.MAX);
        nptNode.setGenerator(true);
        nptNode.setMultiblock(true);
        nptNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_turbine"));
        nptNode.setMachineIcon(ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"));

        IModAdapter nptAdapter = ModAdapterRegistry.getAdapterForNode(nptNode);
        Assertions.assertInstanceOf(StarTModAdapter.class, nptAdapter);
        Assertions.assertTrue(nptAdapter.getApplicableAddonCategories(nptNode).contains(com.gtceu.calcboard.api.catalog.AddonCategory.MULTIBLOCK_TRAIT));

        com.gtceu.calcboard.api.catalog.MachineAddon nptCoolant = new com.gtceu.calcboard.api.catalog.MachineAddon(
                "gtceu:npt_coolant_boosting", "NPT Coolant", com.gtceu.calcboard.api.catalog.AddonCategory.MULTIBLOCK_TRAIT, "+150% EU/t", null
        );
        com.gtceu.calcboard.api.catalog.MachineAddon sptCoolant = new com.gtceu.calcboard.api.catalog.MachineAddon(
                "gtceu:spt_coolant_boosting", "SPT Coolant", com.gtceu.calcboard.api.catalog.AddonCategory.MULTIBLOCK_TRAIT, "+75% EU/t", null
        );

        // NPT node must accept NPT coolant, reject SPT coolant
        Assertions.assertTrue(nptAdapter.isAddonCompatible(nptNode, nptCoolant));
        Assertions.assertFalse(nptAdapter.isAddonCompatible(nptNode, sptCoolant));
    }

    @Test
    public void testThermalCentrifugeAndCraftingNodeDisambiguation() {
        // 1. GT Thermal Centrifuge should route to GTCEuModAdapter and have ELECTRIC_EU energy type
        RecipeNode tcNode = RecipeNode.create("Thermal Centrifuge (Crushed Copper Ore)", 20.0, 30.0, GTVoltageTier.LV);
        tcNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:thermal_centrifuge"));
        tcNode.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_thermal_centrifuge"));

        IModAdapter tcAdapter = ModAdapterRegistry.getAdapterForNode(tcNode);
        Assertions.assertInstanceOf(GTCEuModAdapter.class, tcAdapter);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, tcNode.getEnergyType());
        Assertions.assertFalse(com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.isThermalMachine(tcNode));

        // 2. Minecraft Vanilla Crafting Table recipe with thermal secondary workstations should route to VanillaModAdapter and have NONE energy type
        RecipeNode craftNode = RecipeNode.create("Crafting (Small Pile of Blaze Powder)", 20.0, 0.0, GTVoltageTier.ULV);
        craftNode.setRecipeCategoryId(ResourceLocation.tryParse("minecraft:crafting"));
        craftNode.setMachineIcon(ResourceLocation.tryParse("minecraft:crafting_table"));
        craftNode.getAvailableWorkstations().add(ResourceLocation.tryParse("minecraft:crafting_table"));
        craftNode.getAvailableWorkstations().add(ResourceLocation.tryParse("thermal:tinker_bench"));

        IModAdapter craftAdapter = ModAdapterRegistry.getAdapterForNode(craftNode);
        Assertions.assertInstanceOf(com.gtceu.calcboard.compat.vanilla.VanillaModAdapter.class, craftAdapter);
        Assertions.assertEquals(EnergyType.NONE, craftNode.getEnergyType());
        Assertions.assertFalse(com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.isThermalMachine(craftNode));
    }
}



