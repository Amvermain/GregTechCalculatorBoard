package com.gtceu.calcboard.client.update;

import com.gtceu.calcboard.client.storage.ClientPreferenceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientUpdateNotifierTest {

    @BeforeEach
    public void setUp() {
        ClientPreferenceManager.getInstance().resetForTesting();
        ClientUpdateNotifier.getInstance().resetForTesting();
    }

    @AfterEach
    public void tearDown() {
        ClientPreferenceManager.getInstance().resetForTesting();
        ClientUpdateNotifier.getInstance().resetForTesting();
    }

    @Test
    public void testInitialState() {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        Assertions.assertEquals(ClientUpdateNotifier.UpdateStatus.UNKNOWN, notifier.getStatus());
        Assertions.assertFalse(notifier.isUpdateAvailable());
        Assertions.assertFalse(notifier.isBadgeVisible());
    }

    @Test
    public void testUpdateAvailableAndBadgeVisibility() {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        notifier.setUpdateInfoForTesting(
                ClientUpdateNotifier.UpdateStatus.OUTDATED,
                "2.3.0",
                "https://example.com/download",
                "New feature"
        );

        Assertions.assertTrue(notifier.isUpdateAvailable());
        Assertions.assertTrue(notifier.isBadgeVisible());
        Assertions.assertEquals("2.3.0", notifier.getLatestVersion());
        Assertions.assertEquals("https://example.com/download", notifier.getUpdateUrl());
        Assertions.assertEquals("New feature", notifier.getChangelog());
    }

    @Test
    public void testDismissAndUndismissUpdate() {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        notifier.setUpdateInfoForTesting(
                ClientUpdateNotifier.UpdateStatus.OUTDATED,
                "2.3.0",
                "https://example.com/download",
                ""
        );

        Assertions.assertTrue(notifier.isBadgeVisible());

        notifier.dismissCurrentUpdate();
        Assertions.assertFalse(notifier.isBadgeVisible());
        Assertions.assertEquals("2.3.0", ClientPreferenceManager.getInstance().getDismissedUpdateVersion());

        notifier.undismissUpdate();
        Assertions.assertTrue(notifier.isBadgeVisible());
        Assertions.assertEquals("", ClientPreferenceManager.getInstance().getDismissedUpdateVersion());
    }

    @Test
    public void testUpToDateStatus() {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        notifier.setUpdateInfoForTesting(
                ClientUpdateNotifier.UpdateStatus.UP_TO_DATE,
                "2.2.0-alpha.4",
                "",
                ""
        );

        Assertions.assertFalse(notifier.isUpdateAvailable());
        Assertions.assertFalse(notifier.isBadgeVisible());
    }

    @Test
    public void testParseUpdateJsonPromos() {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        String json = """
                {
                  "homepage": "https://example.com/mod",
                  "promos": {
                    "1.20.1-latest": "2.9.0"
                  },
                  "1.20.1": {
                    "2.9.0": "Great update!"
                  }
                }
                """;
        notifier.parseUpdateJson(json);
        Assertions.assertEquals(ClientUpdateNotifier.UpdateStatus.OUTDATED, notifier.getStatus());
        Assertions.assertTrue(notifier.isUpdateAvailable());
        Assertions.assertTrue(notifier.isBadgeVisible());
        Assertions.assertEquals("2.9.0", notifier.getLatestVersion());
        Assertions.assertEquals("https://example.com/mod", notifier.getUpdateUrl());
        Assertions.assertEquals("Great update!", notifier.getChangelog());
    }

    @Test
    public void testParseUpdateJsonUpToDate() {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        String json = """
                {
                  "homepage": "https://example.com/mod",
                  "promos": {
                    "1.20.1-latest": "1.0.0"
                  }
                }
                """;
        notifier.parseUpdateJson(json);
        Assertions.assertEquals(ClientUpdateNotifier.UpdateStatus.UP_TO_DATE, notifier.getStatus());
        Assertions.assertFalse(notifier.isUpdateAvailable());
        Assertions.assertFalse(notifier.isBadgeVisible());
    }
}
