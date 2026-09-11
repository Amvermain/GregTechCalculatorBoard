package com.gtceu.calcboard.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CalcBoardClientConfigTest {

    @Test
    public void testConfigSpecInitialization() {
        Assertions.assertNotNull(CalcBoardClientConfig.SPEC);
        Assertions.assertNotNull(CalcBoardClientConfig.SHOW_WELCOME_CHAT_MESSAGE);
        Assertions.assertTrue(CalcBoardClientConfig.SHOW_WELCOME_CHAT_MESSAGE.getDefault());

        Assertions.assertNotNull(CalcBoardClientConfig.CHECK_FOR_UPDATES);
        Assertions.assertTrue(CalcBoardClientConfig.CHECK_FOR_UPDATES.getDefault());

        Assertions.assertNotNull(CalcBoardClientConfig.SHOW_UPDATE_BADGE);
        Assertions.assertTrue(CalcBoardClientConfig.SHOW_UPDATE_BADGE.getDefault());

        Assertions.assertNotNull(CalcBoardClientConfig.NOTIFY_UPDATE_IN_CHAT);
        Assertions.assertFalse(CalcBoardClientConfig.NOTIFY_UPDATE_IN_CHAT.getDefault());
    }
}
