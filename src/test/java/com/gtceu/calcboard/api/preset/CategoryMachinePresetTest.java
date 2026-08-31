package com.gtceu.calcboard.api.preset;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Category Machine Defaults (Presets), domain model capture,
 * safe tier elevation, manager persistence, and NBT serialization roundtrips.
 */
public class CategoryMachinePresetTest {

    @BeforeEach
    public void setup() {
        CategoryMachinePresetManager.getInstance().clearAll();
    }

    @Test
    public void testPresetCaptureFromNode() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "Macerator Recipe", 20.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_macerator"));
        node.setTargetTier(GTVoltageTier.EV);
        node.setParallel(16);
        node.setOverclockMode(OverclockMode.PERFECT);

        MachineAddon parHatch = new MachineAddon("gtceu:16x_parallel_hatch", "16x Parallel Hatch", MachineAddon.Category.PARALLEL, "16x", null);
        parHatch.setParallelMultiplier(16);
        node.addAddon(parHatch);

        CategoryMachinePreset preset = CategoryMachinePreset.fromNode(node);
        Assertions.assertNotNull(preset);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:macerator"), preset.getCategoryId());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:large_macerator"), preset.getMachineIcon());
        Assertions.assertTrue(preset.isMultiblock());
        Assertions.assertEquals(GTVoltageTier.EV, preset.getTargetTier());
        Assertions.assertEquals(16, preset.getParallel());
        Assertions.assertEquals(OverclockMode.PERFECT, preset.getOverclockMode());
        Assertions.assertEquals(1, preset.getAddons().size());
        Assertions.assertEquals("gtceu:16x_parallel_hatch", preset.getAddons().get(0).getId());
    }

    @Test
    public void testPresetApplyToNewNode() {
        CategoryMachinePreset preset = new CategoryMachinePreset(ResourceLocation.tryParse("gtceu:macerator"));
        preset.setMultiblock(true);
        preset.setMachineIcon(ResourceLocation.tryParse("gtceu:large_macerator"));
        preset.setTargetTier(GTVoltageTier.HV);
        preset.setParallel(8);

        MachineAddon parHatch = new MachineAddon("gtceu:8x_parallel_hatch", "8x Parallel Hatch", MachineAddon.Category.PARALLEL, "8x", null);
        parHatch.setParallelMultiplier(8);
        preset.getAddons().add(parHatch);

        // Create new recipe node at default LV
        RecipeNode freshNode = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "Iron Macerating", 20.0, 30.0, GTVoltageTier.LV);
        freshNode.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:large_macerator"));
        Assertions.assertFalse(freshNode.isMultiblock());
        Assertions.assertEquals(GTVoltageTier.LV, freshNode.getTargetTier());
        Assertions.assertEquals(1, freshNode.getParallel());

        // Apply preset
        preset.applyTo(freshNode);

        Assertions.assertTrue(freshNode.isMultiblock());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:large_macerator"), freshNode.getMachineIcon());
        Assertions.assertEquals(GTVoltageTier.HV, freshNode.getTargetTier());
        Assertions.assertEquals(8, freshNode.getParallel());
        Assertions.assertEquals(1, freshNode.getAddons().size());
        Assertions.assertEquals("gtceu:8x_parallel_hatch", freshNode.getAddons().get(0).getId());
    }

    @Test
    public void testMinimumTierElevationSafety() {
        // Preset is set to MV
        CategoryMachinePreset preset = new CategoryMachinePreset(ResourceLocation.tryParse("gtceu:chemical_reactor"));
        preset.setTargetTier(GTVoltageTier.MV);
        preset.setParallel(4);

        // Recipe specifically requires IV voltage (baseEUt = 8000)
        RecipeNode highTierRecipeNode = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Nanotube Synthesis", 100.0, 8000.0, GTVoltageTier.IV);
        Assertions.assertEquals(GTVoltageTier.IV, highTierRecipeNode.getRecipeTier());

        preset.applyTo(highTierRecipeNode);

        // Should maintain IV tier rather than illegally downgrading to MV
        Assertions.assertEquals(GTVoltageTier.IV, highTierRecipeNode.getTargetTier(), "Recipe required tier (IV) must be safely preserved over lower preset tier (MV)");
        Assertions.assertEquals(4, highTierRecipeNode.getParallel());
    }

    @Test
    public void testSerializationRoundTrip() {
        CategoryMachinePreset preset = new CategoryMachinePreset(ResourceLocation.tryParse("gtceu:assembler"));
        preset.setMultiblock(false);
        preset.setMachineIcon(ResourceLocation.tryParse("gtceu:ev_assembler"));
        preset.setTargetTier(GTVoltageTier.EV);
        preset.setParallel(2);
        preset.setOverclockMode(OverclockMode.PERFECT);
        preset.setSteamMode(SteamMode.NONE);

        MachineAddon addon = new MachineAddon("custom:boost_kit", "Custom Booster", MachineAddon.Category.CUSTOM, "Speed Boost", null);
        addon.setDurationMultiplier(0.5);
        preset.getAddons().add(addon);

        CompoundTag tag = preset.serializeNBT();
        CategoryMachinePreset deserialized = CategoryMachinePreset.deserializeNBT(tag);

        Assertions.assertNotNull(deserialized);
        Assertions.assertEquals(preset.getCategoryId(), deserialized.getCategoryId());
        Assertions.assertEquals(preset.getMachineIcon(), deserialized.getMachineIcon());
        Assertions.assertEquals(preset.isMultiblock(), deserialized.isMultiblock());
        Assertions.assertEquals(preset.getTargetTier(), deserialized.getTargetTier());
        Assertions.assertEquals(preset.getParallel(), deserialized.getParallel());
        Assertions.assertEquals(preset.getOverclockMode(), deserialized.getOverclockMode());
        Assertions.assertEquals(1, deserialized.getAddons().size());
        Assertions.assertEquals(0.5, deserialized.getAddons().get(0).getDurationMultiplier(), 0.001);
    }

    @Test
    public void testManagerOperations() {
        CategoryMachinePresetManager manager = CategoryMachinePresetManager.getInstance();
        Assertions.assertEquals(0, manager.getAllPresets().size());

        ResourceLocation cat1 = ResourceLocation.tryParse("gtceu:macerator");
        ResourceLocation cat2 = ResourceLocation.tryParse("gtceu:centrifuge");

        CategoryMachinePreset p1 = new CategoryMachinePreset(cat1);
        p1.setTargetTier(GTVoltageTier.EV);
        CategoryMachinePreset p2 = new CategoryMachinePreset(cat2);
        p2.setTargetTier(GTVoltageTier.IV);

        manager.setPreset(p1);
        manager.setPreset(p2);

        Assertions.assertEquals(2, manager.getAllPresets().size());
        Assertions.assertTrue(manager.hasPreset(cat1));
        Assertions.assertTrue(manager.hasPreset(cat2));

        CompoundTag nbt = manager.serializeNBT();

        manager.clearAll();
        Assertions.assertEquals(0, manager.getAllPresets().size());

        manager.deserializeNBT(nbt);
        Assertions.assertEquals(2, manager.getAllPresets().size());
        Assertions.assertEquals(GTVoltageTier.EV, manager.getPreset(cat1).getTargetTier());
        Assertions.assertEquals(GTVoltageTier.IV, manager.getPreset(cat2).getTargetTier());

        manager.removePreset(cat1);
        Assertions.assertFalse(manager.hasPreset(cat1));
        Assertions.assertTrue(manager.hasPreset(cat2));
    }
}
