package com.gtceu.calcboard.template;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.template.MachineHardwareTemplate;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MachineHardwareTemplateTest {

    @Test
    public void testTemplateExtractionAndApplication() {
        ResourceLocation ebfIcon = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        RecipeNode sourceNode = RecipeNode.create(ebfIcon, "Titanium Blast Recipe", 200, 480, GTVoltageTier.HV);
        sourceNode.setTargetTier(GTVoltageTier.EV);
        sourceNode.setParallel(4);
        sourceNode.setOverclockMode(OverclockMode.PERFECT);
        sourceNode.setMultiblock(true);
        sourceNode.getProperties().set(NodeProperties.TARGET_BATCH_AMOUNT, 64.0);

        MachineAddon coilAddon = new MachineAddon("gtceu:kanthal_coil", "Kanthal Coil", AddonCategory.COIL, "", ResourceLocation.tryParse("gtceu:kanthal_coil"));
        sourceNode.getAddons().add(coilAddon);

        // Extract Template
        MachineHardwareTemplate template = MachineHardwareTemplate.fromNode(sourceNode);
        assertEquals(GTVoltageTier.EV, template.getVoltageTier());
        assertEquals(4, template.getParallelLimit());
        assertEquals(OverclockMode.PERFECT, template.getOverclockMode());
        assertTrue(template.isMultiblock());
        assertEquals(64.0, template.getProperties().get(NodeProperties.TARGET_BATCH_AMOUNT));
        assertEquals(1, template.getAddons().size());

        // Create a new recipe template node (e.g. Tungsten Ingot)
        ResourceLocation tungstenIcon = ResourceLocation.tryParse("gtceu:tungsten_ingot");
        RecipeNode tungstenRecipe = RecipeNode.create(null, "Tungsten Ingot", 500, 1920, GTVoltageTier.EV);

        // Apply template to new recipe
        RecipeNode appliedNode = template.applyToRecipe(tungstenRecipe, 100.0, 150.0);
        assertNotNull(appliedNode);
        assertNotEquals(tungstenRecipe.getId(), appliedNode.getId());
        assertEquals(100.0, appliedNode.getPosX());
        assertEquals(150.0, appliedNode.getPosY());
        assertEquals(ebfIcon, appliedNode.getMachineIcon());
        assertEquals(GTVoltageTier.EV, appliedNode.getTargetTier());
        assertEquals(4, appliedNode.getParallel());
        assertEquals(OverclockMode.PERFECT, appliedNode.getOverclockMode());
        assertTrue(appliedNode.isMultiblock());
        assertEquals(64.0, appliedNode.getProperties().get(NodeProperties.TARGET_BATCH_AMOUNT));
        assertEquals("Tungsten Ingot", appliedNode.getName());
        assertEquals(1, appliedNode.getAddons().size());
        assertEquals("Kanthal Coil", appliedNode.getAddons().get(0).getName());
    }

    @Test
    public void testTemplateNBTSerialization() {
        ResourceLocation machineIcon = ResourceLocation.tryParse("gtceu:chemical_reactor");
        MachineHardwareTemplate template = new MachineHardwareTemplate(
                "temp-1",
                "Chemical Reactor (HV 8x)",
                machineIcon,
                GTVoltageTier.HV,
                OverclockMode.STANDARD,
                8,
                SteamMode.NONE,
                false
        );
        template.getProperties().set(NodeProperties.TARGET_BATCH_AMOUNT, 32.0);

        CompoundTag tag = template.serializeNBT();
        MachineHardwareTemplate loaded = MachineHardwareTemplate.deserializeNBT(tag);

        assertEquals("temp-1", loaded.getTemplateId());
        assertEquals("Chemical Reactor (HV 8x)", loaded.getDisplayName());
        assertEquals(machineIcon, loaded.getMachineId());
        assertEquals(GTVoltageTier.HV, loaded.getVoltageTier());
        assertEquals(OverclockMode.STANDARD, loaded.getOverclockMode());
        assertEquals(8, loaded.getParallelLimit());
        assertFalse(loaded.isMultiblock());
        assertEquals(32.0, loaded.getProperties().get(NodeProperties.TARGET_BATCH_AMOUNT));
    }
}
