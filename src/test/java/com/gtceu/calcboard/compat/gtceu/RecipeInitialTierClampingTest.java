package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class RecipeInitialTierClampingTest {

    @BeforeEach
    public void setUp() {
        CategoryCapabilityMatrix.getInstance().reset();
    }

    @Test
    public void initialTargetTierCannotBeLowerThanRecipeTier() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "HV Macerating", 100.0, 480.0, GTVoltageTier.HV);
        node.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:lv_macerator"),
                ResourceLocation.tryParse("gtceu:mv_macerator"),
                ResourceLocation.tryParse("gtceu:hv_macerator")
        ));

        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        Assertions.assertNotNull(adapter);

        GTVoltageTier sanitized = adapter.sanitizeTargetTier(node, GTVoltageTier.LV);
        Assertions.assertEquals(GTVoltageTier.HV, sanitized);

        node.setTargetTier(GTVoltageTier.LV);
        Assertions.assertEquals(GTVoltageTier.HV, node.getTargetTier());
    }

    @Test
    public void initialTargetTierClampedToWorkstationMinimumTierWhenEUtIsLow() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:autoclave"), "Autoclave Crystallizing", 100.0, 24.0, GTVoltageTier.LV);
        node.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:mv_autoclave"),
                ResourceLocation.tryParse("gtceu:hv_autoclave"),
                ResourceLocation.tryParse("gtceu:ev_autoclave")
        ));

        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        Assertions.assertNotNull(adapter);

        GTVoltageTier minWsTier = adapter.getMinimumWorkstationTier(node);
        Assertions.assertEquals(GTVoltageTier.MV, minWsTier);

        GTVoltageTier sanitized = adapter.sanitizeTargetTier(node, GTVoltageTier.LV);
        Assertions.assertEquals(GTVoltageTier.MV, sanitized);

        node.setTargetTier(GTVoltageTier.LV);
        Assertions.assertEquals(GTVoltageTier.MV, node.getTargetTier());
    }

    @Test
    public void onMachineIconChangedDoesNotDowngradeTargetTierBelowRecipeTier() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "HV Macerating", 100.0, 480.0, GTVoltageTier.HV);
        node.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:lv_macerator"),
                ResourceLocation.tryParse("gtceu:mv_macerator"),
                ResourceLocation.tryParse("gtceu:hv_macerator")
        ));

        node.setTargetTier(GTVoltageTier.HV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_macerator"));

        Assertions.assertTrue(node.getTargetTier().ordinal() >= GTVoltageTier.HV.ordinal());
    }

    @Test
    public void ulvRecipeClampedToMinimumWorkstationTier() {
        RecipeNode rockBreaker = RecipeNode.create(ResourceLocation.tryParse("gtceu:rock_breaker"), "Rock Breaker", 16.0, 7.0, GTVoltageTier.ULV);
        rockBreaker.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:lp_steam_rock_breaker"),
                ResourceLocation.tryParse("gtceu:hp_steam_rock_breaker"),
                ResourceLocation.tryParse("gtceu:lv_rock_breaker")
        ));

        var adapter = ModAdapterRegistry.getAdapterForNode(rockBreaker);
        Assertions.assertNotNull(adapter);

        GTVoltageTier minWsTier = adapter.getMinimumWorkstationTier(rockBreaker);
        Assertions.assertEquals(GTVoltageTier.LV, minWsTier);

        GTVoltageTier sanitized = adapter.sanitizeTargetTier(rockBreaker, GTVoltageTier.ULV);
        Assertions.assertEquals(GTVoltageTier.LV, sanitized);

        rockBreaker.setTargetTier(GTVoltageTier.ULV);
        Assertions.assertEquals(GTVoltageTier.LV, rockBreaker.getTargetTier());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:lv_rock_breaker"), rockBreaker.getMachineIcon());
        Assertions.assertNull(adapter.getWorkstationForTier(rockBreaker, GTVoltageTier.ULV));
    }
}
