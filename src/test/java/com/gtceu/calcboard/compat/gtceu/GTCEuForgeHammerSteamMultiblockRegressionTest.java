package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.compat.gtceu.GTCEuNodeCardGuiHandler;
import com.gtceu.calcboard.testutil.TestMultiblockFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GTCEuForgeHammerSteamMultiblockRegressionTest {

    @BeforeEach
    void setUp() {
        MultiblockDetector.reinitialize();
        TestMultiblockFixtures.initTestEnvironmentDefaults();
        CategoryCapabilityMatrix.getInstance();
    }

    @Test
    @DisplayName("Singleblock LV Forge Hammer must not be detected as steam multiblock")
    void testSingleblockElectricForgeHammerIsNotSteamMultiblock() {
        RecipeNode node = RecipeNode.create("Forge Hammer", 20.0, 16.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:forge_hammer"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_forge_hammer"));
        node.setMultiblock(false);
        node.setSteamMode(SteamMode.NONE);

        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:steam_hammer"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:lp_steam_forge_hammer"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:hp_steam_forge_hammer"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:lv_forge_hammer"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:mv_forge_hammer"));

        assertFalse(MultiblockDetector.isSteamMultiblock(node),
                "Singleblock LV Forge Hammer must NOT be identified as steam multiblock even if steam_hammer is in available workstations");

        node.setTargetTier(GTVoltageTier.MV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:mv_forge_hammer"));
        assertFalse(MultiblockDetector.isSteamMultiblock(node),
                "Singleblock MV Forge Hammer must NOT be identified as steam multiblock");
    }

    @Test
    @DisplayName("Multiblock Steam Hammer must be detected as steam multiblock")
    void testSteamHammerMultiblockIsSteamMultiblock() {
        RecipeNode node = RecipeNode.create("Forge Hammer", 20.0, 16.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:forge_hammer"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_hammer"));
        node.setMultiblock(true);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);

        assertTrue(MultiblockDetector.isSteamMultiblock(node),
                "Multiblock with steam_hammer icon must be identified as steam multiblock");
    }

    @Test
    @DisplayName("Electric multiblock Large Macerator must not be detected as steam multiblock")
    void testElectricMultiblockMaceratorIsNotSteamMultiblock() {
        RecipeNode node = RecipeNode.create("Macerator", 400.0, 2.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.setMultiblock(true);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_macerator"));
        node.setSteamMode(SteamMode.NONE);

        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:steam_grinder"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:large_macerator"));

        assertFalse(MultiblockDetector.isSteamMultiblock(node),
                "Electric multiblock large_macerator must NOT be identified as steam multiblock even if steam_grinder exists in category");
    }

    @Test
    @DisplayName("Singleblock node with steam_hammer icon must not be detected as steam multiblock or steam node")
    void testSingleblockWithSteamHammerIconIsNotSteamMultiblock() {
        RecipeNode node = RecipeNode.create("Forge Hammer", 20.0, 16.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:forge_hammer"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_hammer"));
        node.setMultiblock(false);
        node.setSteamMode(SteamMode.NONE);

        assertFalse(MultiblockDetector.isSteamMultiblock(node),
                "Singleblock node must NOT be detected as steam multiblock even if icon is steam_hammer");
        assertFalse(GTCEuNodeCardGuiHandler.isSteamNode(node),
                "Singleblock node with SteamMode.NONE must NOT be detected as steam node");
    }

    @Test
    @DisplayName("Switching machine icon to singleblock electric resets SteamMode to NONE")
    void testSwitchingToElectricSingleblockResetsSteamMode() {
        RecipeNode node = RecipeNode.create("Forge Hammer", 20.0, 16.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:forge_hammer"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_hammer"));
        assertTrue(node.isMultiblock(), "Setting steam_hammer icon should initialize as multiblock");
        assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode(), "Steam multiblock should default to HIGH_PRESSURE");

        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_forge_hammer"));
        assertFalse(node.isMultiblock(), "Setting lv_forge_hammer icon must set multiblock to false");
        assertEquals(SteamMode.NONE, node.getSteamMode(), "Switching to electric singleblock must reset steamMode to NONE");
        assertFalse(GTCEuNodeCardGuiHandler.isSteamNode(node), "Electric singleblock must not be a steam node");
    }
}
