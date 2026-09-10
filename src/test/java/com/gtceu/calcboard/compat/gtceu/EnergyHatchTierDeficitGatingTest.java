package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
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
public class EnergyHatchTierDeficitGatingTest {

    @Test
    @DisplayName("Electric multiblock with energy hatch tier lower than recipe tier must fail validation and stop power")
    void testMultiblockWithLowerTierHatchFailsValidationAndStopsPower() {
        RecipeNode ecr = RecipeNode.create("Extreme Chemical Reactor", 540.0, 7680.0, GTVoltageTier.IV);
        ecr.setMultiblock(true);
        ecr.setMachineIcon(ResourceLocation.tryParse("gtceu:extreme_chemical_reactor"));

        GTEnergyHatchAddon ulvHatch = new GTEnergyHatchAddon(
                "gtceu:ulv_energy_hatch", "ULV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:ulv_energy_hatch"), GTVoltageTier.ULV, 1, false, false, false);
        var adapter = ModAdapterRegistry.getAdapterForNode(ecr);
        adapter.onAddonInstalled(ecr, ulvHatch);

        Assertions.assertTrue(GTAddonCompatibilityHandler.hasEnergyHatch(ecr));
        Assertions.assertEquals(GTVoltageTier.ULV, ecr.getTargetTier());
        Assertions.assertEquals(GTVoltageTier.IV, ecr.getRecipeTier());

        List<Component> warnings = new ArrayList<>();
        boolean valid = GTNodeValidator.validateNode(ecr, null, warnings);

        Assertions.assertFalse(valid, "Multiblock with ULV hatch running IV recipe must fail validation");
        Assertions.assertFalse(warnings.isEmpty(), "Must generate warning message");
        boolean hasTierDeficitWarning = warnings.stream().anyMatch(w ->
                w.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                        && "gui.gtcalcboard.node_warning.energy_hatch_tier_deficit".equals(tc.getKey()));
        Assertions.assertTrue(hasTierDeficitWarning, "Warning must be energy_hatch_tier_deficit");

        Assertions.assertFalse(ecr.isOperational());
        Assertions.assertEquals(0.0, ecr.getSingleMachineEUt(), 0.001, "EU/t must be 0 when hatch tier is too low");
    }

    @Test
    @DisplayName("Dual identical hatches provide +1 tier skip to satisfy recipe requirement")
    void testDualHatchTierSkipAllowsHigherTierRecipe() {
        RecipeNode ecr = RecipeNode.create("Extreme Chemical Reactor", 540.0, 7680.0, GTVoltageTier.IV);
        ecr.setMultiblock(true);
        ecr.setMachineIcon(ResourceLocation.tryParse("gtceu:extreme_chemical_reactor"));

        var adapter = ModAdapterRegistry.getAdapterForNode(ecr);

        // 1x EV hatch installed: EV < IV -> Deficit
        GTEnergyHatchAddon evHatch1 = new GTEnergyHatchAddon(
                "gtceu:ev_energy_hatch_1", "EV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:ev_energy_hatch"), GTVoltageTier.EV, 2, false, false, false);
        adapter.onAddonInstalled(ecr, evHatch1);

        Assertions.assertEquals(GTVoltageTier.EV, ecr.getTargetTier());
        List<Component> warnings1 = new ArrayList<>();
        boolean valid1 = GTNodeValidator.validateNode(ecr, null, warnings1);
        Assertions.assertFalse(valid1, "1x EV hatch cannot satisfy IV recipe");

        // 2x EV hatch installed: EV + EV = IV (Tier skip) -> Satisfied!
        GTEnergyHatchAddon evHatch2 = new GTEnergyHatchAddon(
                "gtceu:ev_energy_hatch_2", "EV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:ev_energy_hatch"), GTVoltageTier.EV, 2, false, false, false);
        adapter.onAddonInstalled(ecr, evHatch2);

        Assertions.assertEquals(GTVoltageTier.IV, ecr.getTargetTier());
        List<Component> warnings2 = new ArrayList<>();
        boolean valid2 = GTNodeValidator.validateNode(ecr, null, warnings2);
        Assertions.assertTrue(valid2, "2x EV hatches provide IV tier and must pass validation");
        Assertions.assertTrue(warnings2.isEmpty());
        Assertions.assertTrue(ecr.isOperational());
        Assertions.assertTrue(ecr.getSingleMachineEUt() > 0.0);
    }
}
