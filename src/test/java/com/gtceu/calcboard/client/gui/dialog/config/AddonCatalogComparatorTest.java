package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AddonCatalogComparatorTest {

    @Test
    public void testTimSortGeneralContractCompliance() {
        List<MachineAddon> sampleAddons = createComplexAddonPool();

        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            List<MachineAddon> list = new ArrayList<>(sampleAddons);
            Collections.shuffle(list, random);
            Assertions.assertDoesNotThrow(() -> list.sort(AddonCatalogComparator.INSTANCE));
        }
    }

    @Test
    public void testOrderingHierarchyAndResetCards() {
        MachineAddon resetRotor = new MachineAddon("gtceu:rotor_standard", "Standard Rotor (Reset)", MachineAddon.Category.ROTOR, "", null);
        MachineAddon resetReflector = new MachineAddon("gtceu:reflector_none", "No Reflector (Reset)", MachineAddon.Category.REFLECTOR, "", null);

        GTCoilAddon cupronickel = new GTCoilAddon("gtceu:coil_cupronickel", "Cupronickel Coil", "", null);
        cupronickel.setCoilTemperature(1800);

        GTCoilAddon kanthal = new GTCoilAddon("gtceu:coil_kanthal", "Kanthal Coil", "", null);
        kanthal.setCoilTemperature(2700);

        GTCoilAddon nichrome = new GTCoilAddon("gtceu:coil_nichrome", "Nichrome Coil", "", null);
        nichrome.setCoilTemperature(3600);

        GTHatchAddon lvHatch = new GTHatchAddon("gtceu:lv_input_bus", "LV Input Bus", "", null);
        lvHatch.setTier(GTVoltageTier.LV);

        GTHatchAddon mvHatch = new GTHatchAddon("gtceu:mv_input_bus", "MV Input Bus", "", null);
        mvHatch.setTier(GTVoltageTier.MV);

        List<MachineAddon> list = new ArrayList<>();
        list.add(kanthal);
        list.add(resetReflector);
        list.add(mvHatch);
        list.add(nichrome);
        list.add(resetRotor);
        list.add(lvHatch);
        list.add(cupronickel);

        list.sort(AddonCatalogComparator.INSTANCE);

        Assertions.assertTrue(list.get(0).getId().equals("gtceu:rotor_standard") || list.get(0).getId().equals("gtceu:reflector_none"));
        Assertions.assertTrue(list.get(1).getId().equals("gtceu:rotor_standard") || list.get(1).getId().equals("gtceu:reflector_none"));

        int idxCupro = list.indexOf(cupronickel);
        int idxKanthal = list.indexOf(kanthal);
        int idxNichrome = list.indexOf(nichrome);
        Assertions.assertTrue(idxCupro < idxKanthal);
        Assertions.assertTrue(idxKanthal < idxNichrome);

        int idxLv = list.indexOf(lvHatch);
        int idxMv = list.indexOf(mvHatch);
        Assertions.assertTrue(idxLv < idxMv);
    }

    private List<MachineAddon> createComplexAddonPool() {
        List<MachineAddon> list = new ArrayList<>();

        list.add(new MachineAddon("gtceu:rotor_standard", "Standard Rotor", MachineAddon.Category.ROTOR, "", null));
        list.add(new MachineAddon("gtceu:reflector_none", "None", MachineAddon.Category.REFLECTOR, "", null));

        int[] temps = {1800, 2700, 3600, 4500, 5400, 7200, 9000, 10800};
        String[] coilNames = {"Z-Cupronickel", "A-Kanthal", "M-Nichrome", "T-RTM Alloy", "B-HSS-G", "N-Naquadah", "C-Trinium", "O-Tritanium"};
        for (int i = 0; i < temps.length; i++) {
            GTCoilAddon coil = new GTCoilAddon("gtceu:coil_" + i, coilNames[i], "", null);
            coil.setCoilTemperature(temps[i]);
            list.add(coil);
        }

        for (int tier = 1; tier <= 5; tier++) {
            list.add(new GTReflectorAddon("gtceu:reflector_" + tier, "Reflector Tier " + tier, "", null, tier));
        }

        GTVoltageTier[] tiers = {GTVoltageTier.ULV, GTVoltageTier.LV, GTVoltageTier.MV, GTVoltageTier.HV, GTVoltageTier.EV, GTVoltageTier.IV, GTVoltageTier.LuV, GTVoltageTier.ZPM, GTVoltageTier.UV};
        for (GTVoltageTier tier : tiers) {
            GTHatchAddon hatch = new GTHatchAddon("gtceu:hatch_" + tier.name().toLowerCase(), "Hatch " + tier.name(), "", null);
            hatch.setTier(tier);
            list.add(hatch);

            list.add(new GTEnergyHatchAddon("gtceu:energy_" + tier.name().toLowerCase(), "Energy " + tier.name(), "", null, tier, 1));
        }

        for (int par = 1; par <= 64; par *= 2) {
            MachineAddon parAddon = new MachineAddon("gtceu:parallel_" + par, "Parallel " + par, MachineAddon.Category.PARALLEL, "", null);
            parAddon.setParallelMultiplier(par);
            list.add(parAddon);
        }

        for (int c = 0; c < 15; c++) {
            list.add(new MachineAddon("custom:mod_" + c, "Custom Mod " + (15 - c), AddonCategory.CUSTOM, "", null));
        }

        return list;
    }
}
