package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.widget.NodeTierChangeHandler;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTNodeValidator;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class MultiblockEnergyHatchLockTest {

    @Test
    @DisplayName("Electric multiblock without energy hatch must fail validation and report missing warning")
    void testMissingEnergyHatchOnElectricMultiblockFailsValidation() {
        RecipeNode lbv = RecipeNode.create("Large Brewing Vat", 300.0, 240.0, GTVoltageTier.HV);
        lbv.setMultiblock(true);
        lbv.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));

        Assertions.assertTrue(GTAddonCompatibilityHandler.requiresEnergyHatch(lbv));
        Assertions.assertFalse(GTAddonCompatibilityHandler.hasEnergyHatch(lbv));

        List<Component> warnings = new ArrayList<>();
        boolean valid = GTNodeValidator.validateNode(lbv, null, warnings);

        Assertions.assertFalse(valid);
        Assertions.assertEquals(1, warnings.size());
        Component warning = warnings.get(0);
        Assertions.assertTrue(warning.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && "gui.gtcalcboard.node_warning.energy_hatch_missing".equals(tc.getKey()));

        List<Component> nodeWarnings = lbv.getOperationalWarnings(null);
        Assertions.assertEquals(1, nodeWarnings.size());
        Assertions.assertTrue(nodeWarnings.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc2
                && "gui.gtcalcboard.node_warning.energy_hatch_missing".equals(tc2.getKey()));
    }

    @Test
    @DisplayName("Equipping energy hatch on electric multiblock must pass validation and synchronize target tier")
    void testEquippedEnergyHatchPassesValidation() {
        RecipeNode lbv = RecipeNode.create("Large Brewing Vat", 300.0, 240.0, GTVoltageTier.HV);
        lbv.setMultiblock(true);
        lbv.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));

        GTEnergyHatchAddon luvHatch = new GTEnergyHatchAddon(
                "gtceu:luv_energy_hatch", "LuV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:luv_energy_hatch"), GTVoltageTier.LuV, 1, false, false, false);
        var adapter = ModAdapterRegistry.getAdapterForNode(lbv);
        adapter.onAddonInstalled(lbv, luvHatch);

        Assertions.assertTrue(GTAddonCompatibilityHandler.hasEnergyHatch(lbv));
        Assertions.assertEquals(GTVoltageTier.LuV, lbv.getTargetTier());

        List<Component> warnings = new ArrayList<>();
        boolean valid = GTNodeValidator.validateNode(lbv, null, warnings);

        Assertions.assertTrue(valid);
        Assertions.assertTrue(warnings.isEmpty());
        Assertions.assertTrue(lbv.getOperationalWarnings(null).isEmpty());
    }

    @Test
    @DisplayName("Target tier must be strictly locked to installed energy hatch and ignore scroll or setTargetTier")
    void testVoltageTierLockedWhenEnergyHatchEquipped() {
        RecipeNode lbv = RecipeNode.create("Large Brewing Vat", 300.0, 240.0, GTVoltageTier.HV);
        lbv.setMultiblock(true);
        lbv.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));

        GTEnergyHatchAddon luvHatch = new GTEnergyHatchAddon(
                "gtceu:luv_energy_hatch", "LuV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:luv_energy_hatch"), GTVoltageTier.LuV, 1, false, false, false);
        var adapter = ModAdapterRegistry.getAdapterForNode(lbv);
        adapter.onAddonInstalled(lbv, luvHatch);

        Assertions.assertEquals(GTVoltageTier.LuV, lbv.getTargetTier());

        // Attempting to set target tier to UV directly
        lbv.setTargetTier(GTVoltageTier.UV);
        Assertions.assertEquals(GTVoltageTier.LuV, lbv.getTargetTier());

        // Attempting to set target tier to MV directly
        lbv.setTargetTier(GTVoltageTier.MV);
        Assertions.assertEquals(GTVoltageTier.LuV, lbv.getTargetTier());

        // Scroll tier change must be rejected
        boolean scrollResult = NodeTierChangeHandler.changeTier(null, lbv, null, 1);
        Assertions.assertFalse(scrollResult);
        Assertions.assertEquals(GTVoltageTier.LuV, lbv.getTargetTier());
    }

    @Test
    @DisplayName("Non-electric multiblock machines must not require energy hatches")
    void testNonElectricMultiblockDoesNotRequireEnergyHatch() {
        RecipeNode boiler = RecipeNode.create("Large Bronze Boiler", 20.0, 0.0, GTVoltageTier.LV);
        boiler.setMultiblock(true);
        boiler.setMachineIcon(ResourceLocation.tryParse("gtceu:bronze_large_boiler"));
        boiler.setEnergyType(EnergyType.HEAT_OR_SELF);

        Assertions.assertFalse(GTAddonCompatibilityHandler.requiresEnergyHatch(boiler));

        List<Component> warnings = new ArrayList<>();
        boolean valid = GTNodeValidator.validateNode(boiler, null, warnings);

        Assertions.assertTrue(valid);
        Assertions.assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("Custom electric multiblock with omitted INPUT_ENERGY ability must still allow standard energy hatches")
    void testCustomKubeJsMultiblockEnergyHatchCompatibility() {
        ResourceLocation mdId = ResourceLocation.tryParse("gtceu:molten_destabiliser");
        com.gtceu.calcboard.api.bom.MultiblockStructureDef customDef = new com.gtceu.calcboard.api.bom.MultiblockStructureDef(
                mdId,
                "Molten Destabiliser",
                java.util.Collections.emptyList(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                java.util.Set.of("PARALLEL_HATCH", "BATCH_MODE", "BLOCKS"),
                java.util.Collections.emptySet()
        );
        com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.registerManualStructure(customDef);

        RecipeNode mdNode = RecipeNode.create("Molten Destabiliser", 100.0, 512.0, GTVoltageTier.EV);
        mdNode.setMultiblock(true);
        mdNode.setMachineIcon(mdId);
        mdNode.setEnergyType(EnergyType.ELECTRIC_EU);

        GTEnergyHatchAddon standardHatch = new GTEnergyHatchAddon(
                "gtceu:ev_energy_hatch", "EV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:ev_energy_hatch"), GTVoltageTier.EV, 1, false, false, false);
        var adapter = ModAdapterRegistry.getAdapterForNode(mdNode);

        Assertions.assertTrue(adapter.isAddonCompatible(mdNode, standardHatch));

        GTEnergyHatchAddon dreamLinkHatch = new GTEnergyHatchAddon(
                "start_core:uev_16a_dream_link_energy_hatch", "UEV 16A Dream-Link Energy Hatch", "",
                ResourceLocation.tryParse("start_core:uev_16a_dream_link_energy_hatch"), GTVoltageTier.UEV, 16, false, false, false);
        Assertions.assertTrue(adapter.isAddonCompatible(mdNode, dreamLinkHatch));
    }
}
