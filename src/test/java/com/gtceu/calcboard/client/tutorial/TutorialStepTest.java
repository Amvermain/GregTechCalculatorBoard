package com.gtceu.calcboard.client.tutorial;

import com.gtceu.calcboard.client.gui.tutorial.TutorialStep;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@ExtendWith(MinecraftBootstrapExtension.class)
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
        java.nio.file.Path langDir = java.nio.file.Paths.get("src/main/resources/assets/gtcalcboard/lang");
        Assertions.assertTrue(java.nio.file.Files.exists(langDir) && java.nio.file.Files.isDirectory(langDir), "Lang directory must exist!");

        java.util.Map<String, com.google.gson.JsonObject> langJsons = new java.util.TreeMap<>();
        try (var stream = java.nio.file.Files.list(langDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String fileName = p.getFileName().toString();
                try {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p)).getAsJsonObject();
                    langJsons.put(fileName, json);
                } catch (Exception e) {
                    Assertions.fail("Failed to parse JSON for language file: " + fileName, e);
                }
            });
        }

        Assertions.assertTrue(langJsons.containsKey("en_us.json"), "en_us.json must exist!");

        String[] exitKeys = {
            "gui.gtcalcboard.tutorial.exit_dialog.title",
            "gui.gtcalcboard.tutorial.exit_dialog.desc",
            "gui.gtcalcboard.tutorial.exit_dialog.confirm",
            "gui.gtcalcboard.tutorial.exit_dialog.cancel"
        };

        for (var entry : langJsons.entrySet()) {
            String langFileName = entry.getKey();
            com.google.gson.JsonObject json = entry.getValue();

            for (TutorialStep step : TutorialStep.values()) {
                Assertions.assertTrue(json.has(step.getTitleKey()), langFileName + " missing: " + step.getTitleKey());
                Assertions.assertTrue(json.has(step.getDescKey()), langFileName + " missing: " + step.getDescKey());
                Assertions.assertFalse(json.get(step.getTitleKey()).getAsString().isEmpty(), langFileName + " empty value for: " + step.getTitleKey());
                Assertions.assertFalse(json.get(step.getDescKey()).getAsString().isEmpty(), langFileName + " empty value for: " + step.getDescKey());
            }

            for (String k : exitKeys) {
                Assertions.assertTrue(json.has(k), langFileName + " missing exit key: " + k);
                Assertions.assertFalse(json.get(k).getAsString().isEmpty(), langFileName + " empty value for exit key: " + k);
            }
        }
    }

    @Test
    public void testTutorialManagerLifecycleAndPageIsolation() {
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager mgr = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance();
        com.gtceu.calcboard.api.storage.BoardManager bm = com.gtceu.calcboard.api.storage.BoardManager.getInstance();
        bm.resetToDefault();

        // Register removal listener as BoardScreen would
        bm.setPageRemovalListener(page -> {
            if (mgr.isTutorialPage(page.getId())) {
                mgr.stopTutorial();
            }
        });

        // Add a dummy user working page
        com.gtceu.calcboard.api.storage.BoardPage userPage = bm.getActivePage();
        userPage.setName("My Factory");
        userPage.getGraph().addNode(com.gtceu.calcboard.api.model.RecipeNode.createReroute(10.0, 10.0));
        Assertions.assertEquals(1, userPage.getGraph().getNodes().size());

        // Start tutorial
        mgr.startTutorial(null);
        Assertions.assertTrue(mgr.isActive());
        Assertions.assertNotNull(mgr.getTutorialPageId());
        Assertions.assertEquals(2, bm.getPages().size());

        com.gtceu.calcboard.api.storage.BoardPage tutPage = mgr.getTutorialPage();
        Assertions.assertNotNull(tutPage);
        Assertions.assertNotEquals(userPage.getId(), tutPage.getId());

        // Step transition should only modify tutorial page, not userPage
        mgr.nextStep(); // to STEP_2_DRAG_TO_SEARCH
        Assertions.assertEquals(1, userPage.getGraph().getNodes().size(), "User working page nodes must remain untouched!");
        Assertions.assertEquals(1, tutPage.getGraph().getNodes().size(), "Tutorial page should receive Boiler");

        // Remove tutorial page tab -> tutorial should automatically terminate!
        int tutIdx = bm.getPages().indexOf(tutPage);
        Assertions.assertTrue(tutIdx >= 0);
        bm.removePage(tutIdx);

        Assertions.assertFalse(mgr.isActive(), "Tutorial must be stopped when tutorial canvas is deleted");
        Assertions.assertNull(mgr.getTutorialPageId());
        Assertions.assertEquals(1, bm.getPages().size());
        Assertions.assertEquals("My Factory", bm.getActivePage().getName());
        Assertions.assertEquals(1, bm.getActivePage().getGraph().getNodes().size());
    }
}
