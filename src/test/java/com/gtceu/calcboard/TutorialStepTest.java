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
        java.nio.file.Path koPath = java.nio.file.Paths.get("src/main/resources/assets/gtcalcboard/lang/ko_kr.json");
        java.nio.file.Path enPath = java.nio.file.Paths.get("src/main/resources/assets/gtcalcboard/lang/en_us.json");

        Assertions.assertTrue(java.nio.file.Files.exists(koPath), "ko_kr.json must exist");
        Assertions.assertTrue(java.nio.file.Files.exists(enPath), "en_us.json must exist");

        com.google.gson.JsonObject koJson = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(koPath)).getAsJsonObject();
        com.google.gson.JsonObject enJson = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(enPath)).getAsJsonObject();

        for (TutorialStep step : TutorialStep.values()) {
            Assertions.assertTrue(koJson.has(step.getTitleKey()), "ko_kr missing: " + step.getTitleKey());
            Assertions.assertTrue(koJson.has(step.getDescKey()), "ko_kr missing: " + step.getDescKey());
            Assertions.assertFalse(koJson.get(step.getTitleKey()).getAsString().isEmpty());
            Assertions.assertFalse(koJson.get(step.getDescKey()).getAsString().isEmpty());

            Assertions.assertTrue(enJson.has(step.getTitleKey()), "en_us missing: " + step.getTitleKey());
            Assertions.assertTrue(enJson.has(step.getDescKey()), "en_us missing: " + step.getDescKey());
            Assertions.assertFalse(enJson.get(step.getTitleKey()).getAsString().isEmpty());
            Assertions.assertFalse(enJson.get(step.getDescKey()).getAsString().isEmpty());
        }
    }
}
