package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
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

public class SteamKilnRegressionTest {

    private static final GTCEuModAdapter ADAPTER = new GTCEuModAdapter();
    private static final ResourceLocation STEAM_KILN_ID = ResourceLocation.tryParse("gtceu:steam_kiln");

    @Test
    @DisplayName("Steam Kiln is correctly recognized as steam multiblock with fixed parallel")
    public void testSteamKilnRecognition() {
        assertTrue(MultiblockDetector.isSteamMultiblock(STEAM_KILN_ID));
        assertEquals(8, MultiblockDetector.getDefaultParallel(STEAM_KILN_ID));
    }

    @Test
    @DisplayName("Steam Kiln requires no energy hatch and permits only steam hatches")
    public void testSteamKilnAddonIsolation() {
        RecipeNode node = RecipeNode.create("Brick Smelting", 100.0, 10.0, GTVoltageTier.ULV);
        node.setMultiblock(true);
        node.setMachineIcon(STEAM_KILN_ID);
        node.setEnergyType(EnergyType.ELECTRIC_EU);

        assertTrue(MultiblockDetector.isSteamMultiblock(node));
        assertFalse(GTAddonCompatibilityHandler.requiresEnergyHatch(node));

        List<AddonCategory> categories = ADAPTER.getApplicableAddonCategories(node);
        assertTrue(categories.contains(AddonCategory.HATCH_BUS));
        assertTrue(categories.contains(AddonCategory.CUSTOM));
        assertFalse(categories.contains(AddonCategory.ENERGY_HATCH));
        assertFalse(categories.contains(AddonCategory.PARALLEL));
        assertFalse(categories.contains(AddonCategory.MAINTENANCE));
        assertFalse(categories.contains(AddonCategory.COIL));
        assertFalse(categories.contains(AddonCategory.THREADING));

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
