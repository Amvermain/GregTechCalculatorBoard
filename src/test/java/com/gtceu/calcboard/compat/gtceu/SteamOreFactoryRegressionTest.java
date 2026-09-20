package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.helper.GTHatchHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SteamOreFactoryRegressionTest {

    private static final GTCEuModAdapter ADAPTER = new GTCEuModAdapter();
    private static final ResourceLocation STEAM_ORE_FACTORY = ResourceLocation.tryParse("gtceu:steam_ore_factory");
    private static final ResourceLocation BRONZE_STEAM_ORE_FACTORY = ResourceLocation.tryParse("gtceu:bronze_steam_ore_factory");
    private static final ResourceLocation STEEL_STEAM_ORE_FACTORY = ResourceLocation.tryParse("gtceu:steel_steam_ore_factory");

    @Test
    @DisplayName("Steam Ore Factory variants are correctly recognized as steam multiblocks with parallel 6")
    public void testSteamOreFactoryRecognition() {
        assertTrue(MultiblockDetector.isSteamMultiblock(STEAM_ORE_FACTORY));
        assertTrue(MultiblockDetector.isSteamMultiblock(BRONZE_STEAM_ORE_FACTORY));
        assertTrue(MultiblockDetector.isSteamMultiblock(STEEL_STEAM_ORE_FACTORY));

        assertEquals(6, MultiblockDetector.getDefaultParallel(STEAM_ORE_FACTORY));
        assertEquals(6, MultiblockDetector.getDefaultParallel(BRONZE_STEAM_ORE_FACTORY));
        assertEquals(6, MultiblockDetector.getDefaultParallel(STEEL_STEAM_ORE_FACTORY));
    }

    @Test
    @DisplayName("Steam Ore Factory retains steam mode and requires no energy hatch when multiblock is toggled")
    public void testSteamOreFactoryModeAndEnergyHatch() {
        RecipeNode node = RecipeNode.create("Steam Ore Processing", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineIcon(STEAM_ORE_FACTORY);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setMultiblock(true);

        assertTrue(MultiblockDetector.isSteamMultiblock(node));
        assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode());
        assertEquals(6, node.getParallel());
        assertFalse(GTAddonCompatibilityHandler.requiresEnergyHatch(node));
    }

    @Test
    @DisplayName("Steam Ore Factory isolates steam hatches and rejects electrical addons")
    public void testSteamOreFactoryAddonIsolation() {
        RecipeNode node = RecipeNode.create("Steam Ore Processing", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineIcon(STEAM_ORE_FACTORY);
        node.setMultiblock(true);

        List<AddonCategory> categories = ADAPTER.getApplicableAddonCategories(node);
        assertTrue(categories.contains(AddonCategory.HATCH_BUS));
        assertTrue(categories.contains(AddonCategory.CUSTOM));
        assertFalse(categories.contains(AddonCategory.ENERGY_HATCH));
        assertFalse(categories.contains(AddonCategory.PARALLEL));
        assertFalse(categories.contains(AddonCategory.MAINTENANCE));
        assertFalse(categories.contains(AddonCategory.COIL));

        List<MachineAddon> allHatches = new ArrayList<>();
        GTHatchHelper.discoverGTCEuHatches(allHatches);

        GTHatchAddon steamInHatch = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("steam_input_hatch") || h.getId().contains("steam_hatch"))
                .findFirst().orElseThrow();
        GTHatchAddon steamInBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().contains("steam_input_bus"))
                .findFirst().orElseThrow();
        GTHatchAddon lvInBus = (GTHatchAddon) allHatches.stream()
                .filter(h -> h.getId().equals("gtceu:lv_input_bus"))
                .findFirst().orElseThrow();

        GTEnergyHatchAddon lvEnergyHatch = new GTEnergyHatchAddon("gtceu:lv_energy_input_hatch", "LV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), GTVoltageTier.LV, 2, false, false, false);
        MachineAddon maintHatch = new MachineAddon("gtceu:maintenance_hatch", "Maintenance Hatch", AddonCategory.MAINTENANCE, "",
                ResourceLocation.tryParse("gtceu:maintenance_hatch"));
        GTParallelHatchAddon parallelHatch = new GTParallelHatchAddon("gtceu:parallel_hatch", "Parallel Hatch", "",
                ResourceLocation.tryParse("gtceu:parallel_hatch"), 4, false);

        assertTrue(ADAPTER.isAddonCompatible(node, steamInHatch));
        assertTrue(ADAPTER.isAddonCompatible(node, steamInBus));
        assertFalse(ADAPTER.isAddonCompatible(node, lvInBus));
        assertFalse(ADAPTER.isAddonCompatible(node, lvEnergyHatch));
        assertFalse(ADAPTER.isAddonCompatible(node, maintHatch));
        assertFalse(ADAPTER.isAddonCompatible(node, parallelHatch));
    }
}
