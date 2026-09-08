package com.gtceu.calcboard.client.storage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientPreferenceManagerTest {

    @BeforeEach
    public void setup() {
        ClientPreferenceManager.getInstance().resetForTesting();
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().resetToDefault();
        com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveTimeUnit(com.gtceu.calcboard.api.type.RateTimeUnit.PER_SECOND);
        com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveFluidUnitMode(com.gtceu.calcboard.api.type.FluidUnitMode.AUTO);
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        ClientPreferenceManager.getInstance().resetForTesting();
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().resetToDefault();
        com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveTimeUnit(com.gtceu.calcboard.api.type.RateTimeUnit.PER_SECOND);
        com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveFluidUnitMode(com.gtceu.calcboard.api.type.FluidUnitMode.AUTO);
    }

    @Test
    public void testDefaultWelcomeMessageState() {
        ClientPreferenceManager mgr = ClientPreferenceManager.getInstance();
        Assertions.assertFalse(mgr.isWelcomeMessageSeen());
    }

    @Test
    public void testMarkWelcomeMessageSeen() {
        ClientPreferenceManager mgr = ClientPreferenceManager.getInstance();
        Assertions.assertFalse(mgr.isWelcomeMessageSeen());

        mgr.markWelcomeMessageSeen();
        Assertions.assertTrue(mgr.isWelcomeMessageSeen());
    }

    @Test
    public void testSetWelcomeMessageSeenExplicit() {
        ClientPreferenceManager mgr = ClientPreferenceManager.getInstance();
        mgr.setWelcomeMessageSeen(true);
        Assertions.assertTrue(mgr.isWelcomeMessageSeen());

        mgr.setWelcomeMessageSeen(false);
        Assertions.assertFalse(mgr.isWelcomeMessageSeen());
    }

    @Test
    public void testDefaultUnitPreferences() {
        ClientPreferenceManager mgr = ClientPreferenceManager.getInstance();
        Assertions.assertTrue(mgr.isPreserveUnitPreferences());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.RateTimeUnit.PER_SECOND, mgr.getPreferredTimeUnit());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.FluidUnitMode.AUTO, mgr.getPreferredFluidUnitMode());
    }

    @Test
    public void testSetPreserveUnitPreferences() {
        ClientPreferenceManager mgr = ClientPreferenceManager.getInstance();
        mgr.setPreserveUnitPreferences(false);
        Assertions.assertFalse(mgr.isPreserveUnitPreferences());

        mgr.setPreserveUnitPreferences(true);
        Assertions.assertTrue(mgr.isPreserveUnitPreferences());
    }

    @Test
    public void testUnitChangeListeners() {
        ClientPreferenceManager mgr = ClientPreferenceManager.getInstance();
        mgr.setPreserveUnitPreferences(true);

        mgr.onTimeUnitChanged(com.gtceu.calcboard.api.type.RateTimeUnit.PER_MINUTE);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.RateTimeUnit.PER_MINUTE, mgr.getPreferredTimeUnit());

        mgr.onFluidUnitModeChanged(com.gtceu.calcboard.api.type.FluidUnitMode.ALWAYS_B);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.FluidUnitMode.ALWAYS_B, mgr.getPreferredFluidUnitMode());

        mgr.setPreserveUnitPreferences(false);
        mgr.onTimeUnitChanged(com.gtceu.calcboard.api.type.RateTimeUnit.PER_HOUR);
        // Preferred time unit should not change when preserve is disabled
        Assertions.assertEquals(com.gtceu.calcboard.api.type.RateTimeUnit.PER_MINUTE, mgr.getPreferredTimeUnit());
    }

    @Test
    public void testApplyPreferencesToBoardManager() {
        ClientPreferenceManager mgr = ClientPreferenceManager.getInstance();
        com.gtceu.calcboard.api.storage.BoardManager bm = com.gtceu.calcboard.api.storage.BoardManager.getInstance();
        bm.resetToDefault();

        mgr.setPreserveUnitPreferences(true);
        mgr.setPreferredTimeUnit(com.gtceu.calcboard.api.type.RateTimeUnit.PER_MINUTE);
        mgr.setPreferredFluidUnitMode(com.gtceu.calcboard.api.type.FluidUnitMode.ALWAYS_MB);

        mgr.applyPreferencesTo(bm);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.RateTimeUnit.PER_MINUTE, bm.getTimeUnit());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.FluidUnitMode.ALWAYS_MB, bm.getFluidUnitMode());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.RateTimeUnit.PER_MINUTE, com.gtceu.calcboard.client.gui.util.FormatUtil.getActiveTimeUnit());
        Assertions.assertEquals(com.gtceu.calcboard.api.type.FluidUnitMode.ALWAYS_MB, com.gtceu.calcboard.client.gui.util.FormatUtil.getActiveFluidUnitMode());
    }
}
