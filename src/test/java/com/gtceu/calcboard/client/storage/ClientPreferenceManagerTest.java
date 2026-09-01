package com.gtceu.calcboard.client.storage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientPreferenceManagerTest {

    @BeforeEach
    public void setup() {
        ClientPreferenceManager.getInstance().resetForTesting();
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
}
