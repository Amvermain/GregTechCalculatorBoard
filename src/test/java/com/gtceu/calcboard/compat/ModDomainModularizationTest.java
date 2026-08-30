package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ParallelHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import com.gtceu.calcboard.compat.gtceu.helper.TurbineRotorHelper;
import com.gtceu.calcboard.compat.thermal.ThermalModAdapter;
import com.gtceu.calcboard.compat.thermal.addon.ThermalAugmentAddon;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates domain separation, polymorphic addon hierarchy, and Reflector validation system.
 */
public class ModDomainModularizationTest {

    @BeforeEach
    void setup() {
        ModAdapterRegistry.init();
    }

    @Test
    @DisplayName("Verify Polymorphic Addon Creation and NBT Round-trip Serialization")
    void testPolymorphicAddonsNBT() {
        // 1. GT Coil Addon
        GTCoilAddon coil = new GTCoilAddon("gtceu:cupronickel_coil", "Cupronickel Coil", "1800K Coil", null, new CoilHelper.CoilStats(1800, 100, 100, 100, 100, 16));
        CompoundTag coilTag = coil.serializeNBT();
        MachineAddon deserializedCoil = MachineAddon.deserializeNBT(coilTag);
        assertInstanceOf(GTCoilAddon.class, deserializedCoil);
        assertEquals(1800, deserializedCoil.getCoilTemperature());
        assertEquals(16, deserializedCoil.getSmelterParallel());

        // 2. GT Rotor Addon
        GTRotorAddon rotor = new GTRotorAddon("gtceu:rotor_iridium", "Iridium Turbine Rotor", "160% Eff", null, 160, 140, 50000.0);
        CompoundTag rotorTag = rotor.serializeNBT();
        MachineAddon deserializedRotor = MachineAddon.deserializeNBT(rotorTag);
        assertInstanceOf(GTRotorAddon.class, deserializedRotor);
        assertEquals(160, deserializedRotor.getRotorEfficiency());
        assertEquals(140, deserializedRotor.getRotorPower());
        assertEquals(50000.0, deserializedRotor.getRotorMaxEUt());

        // 3. GT Reflector Addon
        GTReflectorAddon refl = new GTReflectorAddon("gtceu:reflector_tier_2", "Tier 2 Fusion Reflector", "Requires T2+", null, 2);
        CompoundTag reflTag = refl.serializeNBT();
        MachineAddon deserializedRefl = MachineAddon.deserializeNBT(reflTag);
        assertInstanceOf(GTReflectorAddon.class, deserializedRefl);
        assertEquals(2, deserializedRefl.getReflectorTier());

        // 4. GT Parallel Hatch Addon
        GTParallelHatchAddon par = new GTParallelHatchAddon("gtceu:parallel_hatch_iv", "IV Parallel Hatch", "4x Par", null, 4, false);
        CompoundTag parTag = par.serializeNBT();
        MachineAddon deserializedPar = MachineAddon.deserializeNBT(parTag);
        assertInstanceOf(GTParallelHatchAddon.class, deserializedPar);
        assertEquals(4, deserializedPar.getParallelMultiplier());
        assertFalse(((GTParallelHatchAddon) deserializedPar).isAbsolute());

        // 5. Thermal Augment Addon
        ThermalAugmentAddon aug = new ThermalAugmentAddon("thermal:machine_speed", "Speed Augment", "Speed +50%", null, 1, 0.67, 1.5, false);
        CompoundTag augTag = aug.serializeNBT();
        MachineAddon deserializedAug = MachineAddon.deserializeNBT(augTag);
        assertInstanceOf(ThermalAugmentAddon.class, deserializedAug);
        assertEquals(1.5, deserializedAug.getEutMultiplier());
        assertEquals(0.67, deserializedAug.getDurationMultiplier(), 0.01);
    }

    @Test
    @DisplayName("Verify Fusion Reflector Requirement and Node Validation Logic")
    void testReflectorValidation() {
        RecipeNode node = RecipeNode.create("Fusion Mk2 D-T", 20.0, -4096.0, GTVoltageTier.ZPM);
        node.setEuToStart(180_000_000L);
        node.setRequiredReflectorTier(2); // Requires Tier 2 Reflector

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        assertInstanceOf(GTCEuModAdapter.class, adapter);

        // 1. Initial State: No reflector installed -> Invalid
        assertEquals(0, node.getInstalledReflectorTier());
        assertFalse(node.hasValidReflector());

        List<Component> warnings = new ArrayList<>();
        boolean valid = adapter.validateNode(node, warnings);
        assertFalse(valid);
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.get(0).getString().contains("reflector_required_warning") || warnings.get(0).getString().contains("2"));

        // 2. Install T1 Reflector -> Still Invalid
        GTReflectorAddon t1Refl = new GTReflectorAddon("gtceu:reflector_tier_1", "T1 Reflector", "", null, 1);
        node.addAddon(t1Refl);
        assertEquals(1, node.getInstalledReflectorTier());
        assertFalse(node.hasValidReflector());

        // 3. Install T2 Reflector -> Valid! (Replaces T1)
        GTReflectorAddon t2Refl = new GTReflectorAddon("gtceu:reflector_tier_2", "T2 Reflector", "", null, 2);
        node.addAddon(t2Refl);
        assertEquals(2, node.getInstalledReflectorTier());
        assertEquals(1, node.getAddons().size()); // Single slot replaced
        assertTrue(node.hasValidReflector());

        warnings.clear();
        assertTrue(adapter.validateNode(node, warnings));
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("Verify Reflector Badge Presentation in NodeBadgeRegistry")
    void testReflectorBadges() {
        RecipeNode node = RecipeNode.create("Fusion Mk2 D-T", 20.0, -4096.0, GTVoltageTier.ZPM);
        node.setEuToStart(180_000_000L);
        node.setRequiredReflectorTier(2);

        // 1. Missing Reflector Badge: [⚠ T2+ Reflector Req]
        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(node);
        boolean hasWarningBadge = badges.stream().anyMatch(b -> b.isWarning() && (b.text().contains("2") || b.text().contains("reflector_required")));
        assertTrue(hasWarningBadge);

        // 2. Satisfied Reflector Badge: [🪞 T2]
        node.addAddon(new GTReflectorAddon("gtceu:reflector_tier_2", "T2 Reflector", "", null, 2));
        List<NodeBadge> satisfiedBadges = NodeBadgeRegistry.getBadgesForNode(node);
        boolean hasSatisfiedBadge = satisfiedBadges.stream().anyMatch(b -> b.text().contains("🪞 T2"));
        assertTrue(hasSatisfiedBadge);
    }

    @Test
    @DisplayName("Verify Thermal Adapter 3-Slot Augment Capacity and Kit Replacement")
    void testThermalAdapterSlotRules() {
        RecipeNode node = RecipeNode.create("Thermal Pulverizer", 100.0, -20.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("thermal:machine_pulverizer"));

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        assertInstanceOf(ThermalModAdapter.class, adapter);

        ThermalAugmentAddon aug1 = new ThermalAugmentAddon("thermal:aug1", "Aug 1", "", null, 1, 0.9, 1.1, false);
        ThermalAugmentAddon aug2 = new ThermalAugmentAddon("thermal:aug2", "Aug 2", "", null, 1, 0.9, 1.1, false);
        ThermalAugmentAddon aug3 = new ThermalAugmentAddon("thermal:aug3", "Aug 3", "", null, 1, 0.9, 1.1, false);
        ThermalAugmentAddon aug4 = new ThermalAugmentAddon("thermal:aug4", "Aug 4", "", null, 1, 0.9, 1.1, false);

        assertTrue(adapter.canInstallAddon(node, aug1));
        node.addAddon(aug1);
        assertTrue(adapter.canInstallAddon(node, aug2));
        node.addAddon(aug2);
        assertTrue(adapter.canInstallAddon(node, aug3));
        node.addAddon(aug3);

        // 4th regular augment cannot be installed (3 slot limit)
        assertFalse(adapter.canInstallAddon(node, aug4));

        // Upgrade Kit can always be installed and replaces previous kit
        ThermalAugmentAddon kit1 = new ThermalAugmentAddon("thermal:upgrade_kit_1", "Hardened Kit", "", null, 2, 1.0, 1.0, true);
        ThermalAugmentAddon kit2 = new ThermalAugmentAddon("thermal:upgrade_kit_2", "Reinforced Kit", "", null, 3, 1.0, 1.0, true);

        assertTrue(adapter.canInstallAddon(node, kit1));
        node.addAddon(kit1);
        assertEquals(4, node.getAddons().size()); // 3 regular + 1 kit

        assertTrue(adapter.canInstallAddon(node, kit2));
        node.addAddon(kit2);
        assertEquals(4, node.getAddons().size()); // kit1 replaced by kit2
        assertEquals(3, node.getCombinedParallelMultiplier());
    }
}

