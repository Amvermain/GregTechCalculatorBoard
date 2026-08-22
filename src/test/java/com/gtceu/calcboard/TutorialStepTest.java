package com.gtceu.calcboard;

import com.gtceu.calcboard.client.gui.tutorial.TutorialStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class TutorialStepTest {

    @Test
    public void testTutorialStepSequence() {
        TutorialStep[] steps = TutorialStep.values();
        Assertions.assertEquals(8, steps.length);

        Assertions.assertEquals(TutorialStep.STEP_1_ADD_RECIPE, steps[0]);
        Assertions.assertEquals(TutorialStep.STEP_2_DRAG_TO_SEARCH, steps[1]);
        Assertions.assertEquals(TutorialStep.STEP_3_JUNCTION, steps[2]);
        Assertions.assertEquals(TutorialStep.STEP_4_SHIFT_WIRING, steps[3]);
        Assertions.assertEquals(TutorialStep.STEP_5_MACHINE_CONFIG, steps[4]);
        Assertions.assertEquals(TutorialStep.STEP_6_GROUP_FRAME, steps[5]);
        Assertions.assertEquals(TutorialStep.STEP_7_COMPOUND_MODULE, steps[6]);
        Assertions.assertEquals(TutorialStep.COMPLETED, steps[7]);

        for (int i = 0; i < 7; i++) {
            Assertions.assertEquals(i + 1, steps[i].getStepNumber());
        }
    }

    @Test
    public void testTutorialI18nKeysExist() throws Exception {
        Gson gson = new Gson();
        java.lang.reflect.Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        // Check Korean
        try (InputStream is = getClass().getResourceAsStream("/assets/gtcalcboard/lang/ko_kr.json")) {
            Assertions.assertNotNull(is, "ko_kr.json must exist");
            Map<String, String> koMap = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), mapType);
            for (TutorialStep step : TutorialStep.values()) {
                Assertions.assertTrue(koMap.containsKey(step.getTitleKey()), "ko_kr missing: " + step.getTitleKey());
                Assertions.assertTrue(koMap.containsKey(step.getDescKey()), "ko_kr missing: " + step.getDescKey());
                Assertions.assertFalse(koMap.get(step.getTitleKey()).isEmpty());
                Assertions.assertFalse(koMap.get(step.getDescKey()).isEmpty());
            }
        }

        // Check English
        try (InputStream is = getClass().getResourceAsStream("/assets/gtcalcboard/lang/en_us.json")) {
            Assertions.assertNotNull(is, "en_us.json must exist");
            Map<String, String> enMap = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), mapType);
            for (TutorialStep step : TutorialStep.values()) {
                Assertions.assertTrue(enMap.containsKey(step.getTitleKey()), "en_us missing: " + step.getTitleKey());
                Assertions.assertTrue(enMap.containsKey(step.getDescKey()), "en_us missing: " + step.getDescKey());
                Assertions.assertFalse(enMap.get(step.getTitleKey()).isEmpty());
                Assertions.assertFalse(enMap.get(step.getDescKey()).isEmpty());
            }
        }
    }
}
