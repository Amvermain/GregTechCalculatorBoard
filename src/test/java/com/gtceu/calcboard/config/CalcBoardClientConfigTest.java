package com.gtceu.calcboard.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CalcBoardClientConfigTest {

    @Test
    public void testConfigSpecInitialization() {
        Assertions.assertNotNull(CalcBoardClientConfig.SPEC);
        Assertions.assertNotNull(CalcBoardClientConfig.SHOW_WELCOME_CHAT_MESSAGE);
        Assertions.assertTrue(CalcBoardClientConfig.SHOW_WELCOME_CHAT_MESSAGE.getDefault());
    }
}
