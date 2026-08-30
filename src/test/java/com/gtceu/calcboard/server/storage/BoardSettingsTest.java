package com.gtceu.calcboard.server.storage;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.type.WireColorPreset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class BoardSettingsTest {

    private BoardManager manager;

    @BeforeEach
    public void setUp() {
        manager = BoardManager.getInstance();
        manager.resetToDefault();
    }

    @Test
    public void testDefaultSettings() {
        Assertions.assertTrue(manager.isShowGuideButton());
        Assertions.assertTrue(manager.isShowTutorialButton());
        Assertions.assertTrue(manager.isShowTimeUnitButton());
        Assertions.assertTrue(manager.isShowFluidUnitButton());
        Assertions.assertTrue(manager.isShowMultiblockBomButton());
        Assertions.assertTrue(manager.isShowHotkeyHud());
        Assertions.assertTrue(manager.isShowWirePulseAnimation());
        Assertions.assertEquals(WireColorPreset.CYAN, manager.getWireColorPreset());
        Assertions.assertEquals(WireColorPreset.GREEN, manager.getMatchedWireColorPreset());
        Assertions.assertEquals(0xFF00E5FF, manager.getWireColor());
        Assertions.assertEquals(0xFF00E676, manager.getMatchedWireColor());
    }

    @Test
    public void testSettingsMutationAndSerialization() throws IOException {
        File tempFile = File.createTempFile("calcboard_settings_test", ".nbt");
        tempFile.deleteOnExit();

        manager.setShowGuideButton(false);
        manager.setShowTutorialButton(false);
        manager.setShowTimeUnitButton(false);
        manager.setShowFluidUnitButton(false);
        manager.setShowMultiblockBomButton(false);
        manager.setShowHotkeyHud(false);
        manager.setShowWirePulseAnimation(false);
        manager.setWireColorPreset(WireColorPreset.GOLD);
        manager.setMatchedWireColorPreset(WireColorPreset.VIOLET);
        manager.setTimeUnit(RateTimeUnit.PER_HOUR);
        manager.setFluidUnitMode(FluidUnitMode.ALWAYS_B);
        manager.setPowerDisplayMode(PowerDisplayMode.AMPS);
        manager.setPauseGameInSingleplayer(true);
        manager.setMaxHarmonizeScale(32);
        manager.setHarmonizeSurplusTolerance(0.05);

        boolean saved = manager.saveToFile(tempFile);
        Assertions.assertTrue(saved);

        // Reset to default then load
        manager.resetToDefault();
        Assertions.assertTrue(manager.isShowGuideButton());
        Assertions.assertEquals(WireColorPreset.CYAN, manager.getWireColorPreset());
        Assertions.assertEquals(16, manager.getMaxHarmonizeScale());
        Assertions.assertEquals(0.02, manager.getHarmonizeSurplusTolerance(), 1e-4);

        boolean loaded = manager.loadFromFile(tempFile);
        Assertions.assertTrue(loaded);

        Assertions.assertFalse(manager.isShowGuideButton());
        Assertions.assertFalse(manager.isShowTutorialButton());
        Assertions.assertFalse(manager.isShowTimeUnitButton());
        Assertions.assertFalse(manager.isShowFluidUnitButton());
        Assertions.assertFalse(manager.isShowMultiblockBomButton());
        Assertions.assertFalse(manager.isShowHotkeyHud());
        Assertions.assertFalse(manager.isShowWirePulseAnimation());
        Assertions.assertEquals(WireColorPreset.GOLD, manager.getWireColorPreset());
        Assertions.assertEquals(WireColorPreset.VIOLET, manager.getMatchedWireColorPreset());
        Assertions.assertEquals(0xFFFFD700, manager.getWireColor());
        Assertions.assertEquals(0xFFB388FF, manager.getMatchedWireColor());
        Assertions.assertEquals(RateTimeUnit.PER_HOUR, manager.getTimeUnit());
        Assertions.assertEquals(FluidUnitMode.ALWAYS_B, manager.getFluidUnitMode());
        Assertions.assertEquals(PowerDisplayMode.AMPS, manager.getPowerDisplayMode());
        Assertions.assertTrue(manager.isPauseGameInSingleplayer());
        Assertions.assertEquals(32, manager.getMaxHarmonizeScale());
        Assertions.assertEquals(0.05, manager.getHarmonizeSurplusTolerance(), 1e-4);
    }
}
