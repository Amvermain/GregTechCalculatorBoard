package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.type.OverclockMode;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NodePropertyStoreTest {

    @Test
    @DisplayName("NodePropertyKey type safety and default values")
    void testPropertyKeys() {
        NodePropertyKey<Integer> intKey = NodePropertyKey.ofInt("test_int", 42);
        assertEquals("test_int", intKey.getId());
        assertEquals(Integer.class, intKey.getType());
        assertEquals(42, intKey.getDefaultValue());

        NodePropertyKey<Long> longKey = NodePropertyKey.ofLong("test_long", 1000L);
        assertEquals(1000L, longKey.getDefaultValue());

        NodePropertyKey<Boolean> boolKey = NodePropertyKey.ofBoolean("test_bool", true);
        assertTrue(boolKey.getDefaultValue());
    }

    @Test
    @DisplayName("NodePropertyStore CRUD operations and default fallback")
    void testPropertyStoreCRUD() {
        NodePropertyStore store = new NodePropertyStore();
        assertTrue(store.isEmpty());

        // Default fallback
        assertEquals(0, store.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE));
        assertEquals(32, store.get(com.gtceu.calcboard.compat.create.CreateProperties.KINETIC_RPM));
        assertEquals(0L, store.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.FUSION_START_EU));
        assertFalse(store.has(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE));

        // Set non-default
        store.set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE, 1800);
        assertTrue(store.has(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE));
        assertEquals(1800, store.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE));

        // Set back to default auto-removes
        store.set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE, 0);
        assertFalse(store.has(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE));
        assertEquals(0, store.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE));

        // Setting null removes
        store.set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.TURBINE_ROTOR_NAME, "Titanium (140%)");
        assertTrue(store.has(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.TURBINE_ROTOR_NAME));
        store.set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.TURBINE_ROTOR_NAME, null);
        assertFalse(store.has(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.TURBINE_ROTOR_NAME));
    }

    @Test
    @DisplayName("NodePropertyStore NBT serialization and deserialization")
    void testPropertyStoreNBT() {
        NodePropertyStore original = new NodePropertyStore();
        original.set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE, 2700);
        original.set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.FUSION_START_EU, 160_000_000L);
        original.set(com.gtceu.calcboard.compat.create.CreateProperties.KINETIC_RPM, 128);
        original.set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.CLEANROOM_TYPE, "STERILE_CLEANROOM");

        CompoundTag tag = original.serializeNBT();
        assertNotNull(tag);
        assertEquals(2700, tag.getInt("ebf_temperature"));
        assertEquals(160_000_000L, tag.getLong("fusion_start_eu"));
        assertEquals(128, tag.getInt("kinetic_rpm"));
        assertEquals("STERILE_CLEANROOM", tag.getString("cleanroom_type"));

        NodePropertyStore loaded = new NodePropertyStore();
        loaded.deserializeNBT(tag);

        assertEquals(2700, loaded.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE));
        assertEquals(160_000_000L, loaded.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.FUSION_START_EU));
        assertEquals(128, loaded.get(com.gtceu.calcboard.compat.create.CreateProperties.KINETIC_RPM));
        assertEquals("STERILE_CLEANROOM", loaded.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.CLEANROOM_TYPE));
        assertEquals(original, loaded);
    }

    @Test
    @DisplayName("RecipeNode integration, delegation, and backward compatibility migration")
    void testRecipeNodeIntegrationAndLegacyMigration() {
        RecipeNode node = RecipeNode.create("Electric Blast Furnace", 200, 120, GTVoltageTier.MV);
        node.setRecipeTemperature(1800);
        node.setRpm(64);
        node.setRotorEfficiency(150);
        node.setEuToStart(320_000_000L);

        assertEquals(1800, node.getRecipeTemperature());
        assertEquals(64, node.getRpm());
        assertEquals(150, node.getRotorEfficiency());
        assertEquals(320_000_000L, node.getEuToStart());
        assertTrue(node.isFusion());
        assertEquals(2, node.getFusionTier()); // Mk2
        assertEquals(GTVoltageTier.ZPM, node.getMinFusionVoltageTier());

        // Copy test
        RecipeNode copied = node.copy();
        assertEquals(1800, copied.getRecipeTemperature());
        assertEquals(64, copied.getRpm());
        assertEquals(150, copied.getRotorEfficiency());
        assertEquals(320_000_000L, copied.getEuToStart());

        // Legacy NBT migration test (old save without "properties" tag)
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putString("id", "legacy-node");
        legacyTag.putString("name", "Legacy EBF");
        legacyTag.putDouble("baseDuration", 100);
        legacyTag.putDouble("baseEUt", 120);
        legacyTag.putString("recipeTier", "MV");
        legacyTag.putString("targetTier", "MV");
        legacyTag.putDouble("machineCount", 1.0);
        legacyTag.putInt("parallel", 1);
        legacyTag.putString("overclockMode", "STANDARD");
        legacyTag.putInt("recipeTemperature", 3600);
        legacyTag.putInt("rpm", 128);
        legacyTag.putInt("rotorEfficiency", 175);
        legacyTag.putLong("euToStart", 640_000_000L);

        RecipeNode migratedNode = RecipeNode.deserializeNBT(legacyTag);
        assertEquals(3600, migratedNode.getRecipeTemperature());
        assertEquals(128, migratedNode.getRpm());
        assertEquals(175, migratedNode.getRotorEfficiency());
        assertEquals(640_000_000L, migratedNode.getEuToStart());
        assertEquals(3, migratedNode.getFusionTier()); // Mk3
        assertEquals(GTVoltageTier.UV, migratedNode.getMinFusionVoltageTier());
    }

    @Test
    @DisplayName("RecipePropertyExtractorPipeline extracts EBF, Fusion, Cleanroom, and Create kinetic properties")
    void testExtractorPipeline() {
        NodePropertyStore store = new NodePropertyStore();

        // 1. EBF extraction
        CompoundTag ebfTag = new CompoundTag();
        ebfTag.putInt("ebf_temp", 2500);
        RecipePropertyExtractorPipeline.extractAll(new Object() {
            public String getClass_Name() { return "GTRecipe"; }
        }, ebfTag, ResourceLocation.tryParse("gtceu:electric_blast_furnace"), store);

        // 2. Fusion extraction
        CompoundTag fusionTag = new CompoundTag();
        fusionTag.putLong("eu_to_start", 160_000_000L);
        RecipePropertyExtractorPipeline.extractAll(null, fusionTag, ResourceLocation.tryParse("gtceu:fusion_reactor"), store);

        // 3. Cleanroom extraction
        CompoundTag cleanTag = new CompoundTag();
        cleanTag.putString("cleanroom", "CLEANROOM");
        RecipePropertyExtractorPipeline.extractAll(null, cleanTag, ResourceLocation.tryParse("gtceu:circuit_assembler"), store);

        // 4. Create extraction
        RecipePropertyExtractorPipeline.extractAll(null, new CompoundTag(), ResourceLocation.tryParse("create:milling"), store);

        assertEquals(160_000_000L, store.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.FUSION_START_EU));
        assertEquals("CLEANROOM", store.get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.CLEANROOM_TYPE));
        assertEquals(32, store.get(com.gtceu.calcboard.compat.create.CreateProperties.KINETIC_RPM));
    }

    @Test
    @DisplayName("NodeBadgeRegistry generates declarative badges for Fusion and Cleanroom")
    void testNodeBadgeRegistry() {
        RecipeNode fusionNode = RecipeNode.create("Fusion Reactor Mk1", 40, 16384, GTVoltageTier.LuV);
        fusionNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        fusionNode.setEuToStart(160_000_000L);
        fusionNode.getProperties().set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.CLEANROOM_TYPE, "STERILE_CLEANROOM");

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(fusionNode);
        assertNotNull(badges);
        assertTrue(badges.size() >= 2);

        boolean foundTierBadge = false;
        boolean foundStartBadge = false;
        boolean foundCleanBadge = false;

        for (NodeBadge badge : badges) {
            if (badge.text().contains("Mk1")) foundTierBadge = true;
            if (badge.text().contains("160M")) foundStartBadge = true;
            if (badge.text().contains("Sterile")) foundCleanBadge = true;
        }

        assertTrue(foundTierBadge, "Fusion Mk1 tier badge must be present");
        assertTrue(foundStartBadge, "160M Start EU badge must be present");
        assertTrue(foundCleanBadge, "Sterile cleanroom badge must be present");
    }
}


