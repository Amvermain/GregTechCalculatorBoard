package com.gtceu.calcboard.client.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ClientSafetyHelperTest {

    @Test
    @DisplayName("RFC-058: ClientSafetyHelper methods safely execute in headless environment without NPE")
    public void testHeadlessSafety() {
        Assertions.assertDoesNotThrow(() -> {
            boolean shift = ClientSafetyHelper.isShiftDown();
            Assertions.assertFalse(shift);

            boolean ctrl = ClientSafetyHelper.isControlDown();
            Assertions.assertFalse(ctrl);

            long window = ClientSafetyHelper.getWindowHandleSafely();
            Assertions.assertEquals(0L, window);

            ClientSafetyHelper.playSoundSafely(null);
        });
    }
}
