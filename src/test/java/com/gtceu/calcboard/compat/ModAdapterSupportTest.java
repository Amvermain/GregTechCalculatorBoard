package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModAdapterSupportTest {

    @BeforeEach
    public void setup() {
        ModAdapterRegistry.init();
    }

    @Test
    public void testSupportedCategories() {
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("gtceu:electric_blast_furnace")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("gtceu:centrifuge")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("gtceu:chemical_reactor")));

        // Create Categories
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("create:mixing")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("create:pressing")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("create:cutting")));

        // Thermal Categories
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("thermal:furnace")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("thermal:pulverizer")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("thermal:sawmill")));

        // Systeams Categories
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("systeams:steam_boiler")));

        // Vanilla Categories
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("minecraft:crafting")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("minecraft:smelting")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("minecraft:blasting")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("minecraft:smoking")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("minecraft:stonecutting")));
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("minecraft:smithing")));

        // Synthetic Categories
        assertTrue(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("gtcalcboard:kinetic_generation")));
        assertEquals("create", ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("gtcalcboard:kinetic_generation")).getModId());
    }

    @Test
    public void testUnsupportedCategories() {
        // Unsupported Third-party mod categories
        assertFalse(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("mekanism:crusher")));
        assertFalse(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("mekanism:enrichment_chamber")));
        assertFalse(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("botania:mana_pool")));
        assertFalse(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("bloodmagic:altar")));
        assertFalse(ModAdapterRegistry.isCategorySupported(ResourceLocation.tryParse("farmersdelight:cooking")));
        assertFalse(ModAdapterRegistry.isCategorySupported(null));
    }

    @Test
    public void testRecipeFilterConfigIncludeUnsupported() {
        RecipeFilterConfig config = RecipeFilterConfig.getInstance();
        config.resetDefaults();
        assertFalse(config.isIncludeUnsupported(), "Default should be false (unsupported recipes excluded)");

        config.setIncludeUnsupported(true);
        assertTrue(config.isIncludeUnsupported());

        config.setIncludeUnsupported(false);
        assertFalse(config.isIncludeUnsupported());
    }

    @Test
    public void testGenericUnsupportedNodeBadge() {
        RecipeNode supportedNode = RecipeNode.create("GT Centrifuge", 20.0, 30.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        List<NodeBadge> badgesSupported = NodeBadgeRegistry.getBadgesForNode(supportedNode);
        boolean hasGenericBadge = badgesSupported.stream().anyMatch(b -> b.text().contains("Generic") || b.text().contains("수동") || b.text().contains("node_badge.generic_unsupported"));
        assertFalse(hasGenericBadge, "Supported node must not have generic badge");

        RecipeNode unsupportedNode = RecipeNode.create("Mekanism Crusher", 20.0, 0.0, com.gtceu.calcboard.api.type.GTVoltageTier.ULV);
        unsupportedNode.getProperties().set(NodeProperties.IS_GENERIC_UNSUPPORTED, true);
        List<NodeBadge> badgesUnsupported = NodeBadgeRegistry.getBadgesForNode(unsupportedNode);
        boolean hasGenericBadge2 = badgesUnsupported.stream().anyMatch(b -> b.text().contains("Generic") || b.text().contains("수동") || b.text().contains("node_badge.generic_unsupported"));
        assertTrue(hasGenericBadge2, "Unsupported node must have generic badge");
    }
}
