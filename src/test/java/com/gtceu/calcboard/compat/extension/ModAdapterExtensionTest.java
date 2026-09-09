package com.gtceu.calcboard.compat.extension;

import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.spi.extension.IBoosterProvider;
import com.gtceu.calcboard.api.spi.extension.ICapabilityMatrixProvider;
import com.gtceu.calcboard.api.spi.extension.ICompoundRecipeProvider;
import com.gtceu.calcboard.api.spi.extension.IEnergySimulationProvider;
import com.gtceu.calcboard.api.spi.extension.IHardwareAddonProvider;
import com.gtceu.calcboard.api.spi.extension.IMultiblockBOMProvider;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.start.StarTModAdapter;
import com.gtceu.calcboard.compat.vanilla.VanillaModAdapter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModAdapterExtensionTest {

    @Test
    void testVanillaAdapterExtensions() {
        VanillaModAdapter adapter = new VanillaModAdapter();

        assertTrue(adapter.hasExtension(IEnergySimulationProvider.class));
        assertFalse(adapter.hasExtension(IHardwareAddonProvider.class));
        assertFalse(adapter.hasExtension(IMultiblockBOMProvider.class));
        assertFalse(adapter.hasExtension(ICompoundRecipeProvider.class));
        assertFalse(adapter.hasExtension(IBoosterProvider.class));
        assertFalse(adapter.hasExtension(ICapabilityMatrixProvider.class));

        Optional<IEnergySimulationProvider> energyExt = adapter.getExtension(IEnergySimulationProvider.class);
        assertTrue(energyExt.isPresent());
        assertNotNull(energyExt.get());

        // Verify default backward compatibility bridge
        assertFalse(adapter.supportsAddons(null));
        assertTrue(adapter.getApplicableAddonCategories(null).isEmpty());
    }

    @Test
    void testCreateModAdapterExtensions() {
        CreateModAdapter adapter = new CreateModAdapter();

        assertTrue(adapter.hasExtension(IEnergySimulationProvider.class));
        assertTrue(adapter.hasExtension(ICompoundRecipeProvider.class));
        assertTrue(adapter.hasExtension(ICapabilityMatrixProvider.class));
        assertTrue(adapter.hasExtension(IHardwareAddonProvider.class));
        assertTrue(adapter.hasExtension(IMultiblockBOMProvider.class));
        assertFalse(adapter.hasExtension(IBoosterProvider.class));
    }

    @Test
    void testGTCEuModAdapterExtensions() {
        GTCEuModAdapter adapter = new GTCEuModAdapter();

        assertTrue(adapter.hasExtension(IHardwareAddonProvider.class));
        assertTrue(adapter.hasExtension(IMultiblockBOMProvider.class));
        assertTrue(adapter.hasExtension(IEnergySimulationProvider.class));
        assertTrue(adapter.hasExtension(ICompoundRecipeProvider.class));
        assertTrue(adapter.hasExtension(IBoosterProvider.class));
        assertTrue(adapter.hasExtension(ICapabilityMatrixProvider.class));
    }

    @Test
    void testStarTModAdapterExtensions() {
        StarTModAdapter adapter = new StarTModAdapter();

        assertTrue(adapter.hasExtension(IHardwareAddonProvider.class));
        assertTrue(adapter.hasExtension(IMultiblockBOMProvider.class));
        assertTrue(adapter.hasExtension(IEnergySimulationProvider.class));
        assertTrue(adapter.hasExtension(ICompoundRecipeProvider.class));
        assertTrue(adapter.hasExtension(ICapabilityMatrixProvider.class));
        assertFalse(adapter.hasExtension(IBoosterProvider.class)); // StarT explicitly does not implement booster controls
    }

    @Test
    void testModAdapterRegistryExtensions() {
        Optional<IEnergySimulationProvider> vanillaEnergy = ModAdapterRegistry.getExtensionForMod("minecraft", IEnergySimulationProvider.class);
        assertTrue(vanillaEnergy.isPresent());

        Optional<IHardwareAddonProvider> vanillaAddon = ModAdapterRegistry.getExtensionForMod("minecraft", IHardwareAddonProvider.class);
        assertFalse(vanillaAddon.isPresent());

        Optional<IHardwareAddonProvider> gtceuAddon = ModAdapterRegistry.getExtensionForMod("gtceu", IHardwareAddonProvider.class);
        assertTrue(gtceuAddon.isPresent());

        Optional<ICompoundRecipeProvider> createRecipe = ModAdapterRegistry.getExtensionForMod("create", ICompoundRecipeProvider.class);
        assertTrue(createRecipe.isPresent());
    }
}
