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
        Assertions.assertEquals(14, steps.length);

        Assertions.assertEquals(TutorialStep.STEP_1_ADD_RECIPE, steps[0]);
        Assertions.assertEquals(TutorialStep.STEP_2_DRAG_TO_SEARCH, steps[1]);
        Assertions.assertEquals(TutorialStep.STEP_3_JUNCTION, steps[2]);
        Assertions.assertEquals(TutorialStep.STEP_4_SHIFT_WIRING, steps[3]);
        Assertions.assertEquals(TutorialStep.STEP_5_JUNCTION_ETA, steps[4]);
        Assertions.assertEquals(TutorialStep.STEP_6_MACHINE_SELECTOR, steps[5]);
        Assertions.assertEquals(TutorialStep.STEP_7_MACHINE_CONFIG, steps[6]);
        Assertions.assertEquals(TutorialStep.STEP_8_GROUP_FRAME, steps[7]);
        Assertions.assertEquals(TutorialStep.STEP_9_COMPOUND_MODULE, steps[8]);
        Assertions.assertEquals(TutorialStep.STEP_10_SHARED_MACHINE, steps[9]);
        Assertions.assertEquals(TutorialStep.STEP_11_BOM_INSPECTION, steps[10]);
        Assertions.assertEquals(TutorialStep.STEP_12_JUNCTION_SUPPLY, steps[11]);
        Assertions.assertEquals(TutorialStep.STEP_13_FOLDER_BROWSER, steps[12]);
        Assertions.assertEquals(TutorialStep.COMPLETED, steps[13]);

        for (int i = 0; i < 13; i++) {
            Assertions.assertEquals(i + 1, steps[i].getStepNumber());
        }
        Assertions.assertEquals(14, steps[13].getStepNumber());
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

    @Test
    public void testAdvancedTutorialLifecycle() {
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager mgr = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance();
        com.gtceu.calcboard.api.storage.BoardManager bm = com.gtceu.calcboard.api.storage.BoardManager.getInstance();
        bm.resetToDefault();

        // 1. Test Basic Tutorial ends at Step 9
        mgr.startTutorial(null);
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.tutorial.TutorialManager.TutorialMode.BASIC, mgr.getMode());
        Assertions.assertEquals(TutorialStep.STEP_1_ADD_RECIPE, mgr.getCurrentStep());

        // Fast-forward to step 9
        for (int i = 0; i < 8; i++) {
            mgr.nextStep();
        }
        Assertions.assertEquals(TutorialStep.STEP_9_COMPOUND_MODULE, mgr.getCurrentStep());
        mgr.nextStep(); // Should complete, not go to Step 10!
        Assertions.assertEquals(TutorialStep.COMPLETED, mgr.getCurrentStep());
        mgr.stopTutorial();

        // 2. Test Advanced Tutorial starts at Step 10 with 3 cutter nodes
        mgr.startAdvancedTutorial(null);
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.tutorial.TutorialManager.TutorialMode.ADVANCED, mgr.getMode());
        Assertions.assertEquals(TutorialStep.STEP_10_SHARED_MACHINE, mgr.getCurrentStep());

        com.gtceu.calcboard.api.storage.BoardPage advPage = mgr.getTutorialPage();
        Assertions.assertNotNull(advPage);
        Assertions.assertEquals(3, advPage.getGraph().getNodes().size(), "Advanced tutorial should initialize 3 cutter nodes!");

        // Step 10 -> Step 11 (BOM Inspection)
        mgr.nextStep();
        Assertions.assertEquals(TutorialStep.STEP_11_BOM_INSPECTION, mgr.getCurrentStep());

        // Step 11 -> Step 12 (Junction Supply)
        mgr.nextStep();
        Assertions.assertEquals(TutorialStep.STEP_12_JUNCTION_SUPPLY, mgr.getCurrentStep());
        Assertions.assertTrue(advPage.getGraph().getNodes().stream().anyMatch(com.gtceu.calcboard.api.model.RecipeNode::isReroute), "Step 12 should introduce a reroute junction node");

        // Step 12 -> Step 13 (Folder Browser)
        mgr.nextStep();
        Assertions.assertEquals(TutorialStep.STEP_13_FOLDER_BROWSER, mgr.getCurrentStep());

        // Step 13 -> Completed (via nextStep)
        mgr.nextStep();
        Assertions.assertEquals(TutorialStep.COMPLETED, mgr.getCurrentStep());
        mgr.stopTutorial();

        // 3. Test onFolderBrowserOpened trigger
        mgr.startAdvancedTutorial(null);
        mgr.nextStep(); // to Step 11
        mgr.nextStep(); // to Step 12
        mgr.nextStep(); // to Step 13
        Assertions.assertEquals(TutorialStep.STEP_13_FOLDER_BROWSER, mgr.getCurrentStep());
        mgr.onFolderBrowserOpened();
        Assertions.assertEquals(TutorialStep.COMPLETED, mgr.getCurrentStep());
        mgr.stopTutorial();
    }

    @Test
    public void testMachineSelectorStepLifecycle() {
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager mgr = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance();
        com.gtceu.calcboard.api.storage.BoardManager bm = com.gtceu.calcboard.api.storage.BoardManager.getInstance();
        bm.resetToDefault();

        mgr.startTutorial(null);
        // Advance to Step 6 (Machine Selector)
        for (int i = 0; i < 5; i++) {
            mgr.nextStep();
        }
        Assertions.assertEquals(TutorialStep.STEP_6_MACHINE_SELECTOR, mgr.getCurrentStep());

        com.gtceu.calcboard.api.storage.BoardPage tutPage = mgr.getTutorialPage();
        Assertions.assertNotNull(tutPage);
        String selectorId = mgr.getSelectorNodeId();
        Assertions.assertNotNull(selectorId);

        com.gtceu.calcboard.api.model.RecipeNode furnace = tutPage.getGraph().findNodeById(selectorId);
        Assertions.assertNotNull(furnace);
        Assertions.assertFalse(furnace.isMultiblock());
        Assertions.assertTrue(mgr.isMachineIconGlowing(selectorId));
        Assertions.assertFalse(mgr.isMachineIconGlowing("dummy_node_id"));

        net.minecraft.resources.ResourceLocation ebfId = net.minecraft.resources.ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        Assertions.assertTrue(mgr.isMachineSelectorRowGlowing(ebfId));
        Assertions.assertFalse(mgr.isMachineSelectorRowGlowing(net.minecraft.resources.ResourceLocation.tryParse("gtceu:lv_cutter")));

        // Simulate machine switch to EBF
        mgr.onMachineSwitched(furnace, ebfId);
        Assertions.assertEquals(TutorialStep.STEP_7_MACHINE_CONFIG, mgr.getCurrentStep());

        mgr.stopTutorial();
    }
}
