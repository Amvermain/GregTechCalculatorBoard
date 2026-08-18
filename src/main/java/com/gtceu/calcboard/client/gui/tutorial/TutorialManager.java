package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * Manages the state machine and progression of the 7-step interactive onboarding tutorial.
 */
public class TutorialManager {
    private static final TutorialManager INSTANCE = new TutorialManager();

    private boolean active = false;
    private TutorialStep currentStep = TutorialStep.STEP_1_PAN_ZOOM;
    private BoardScreen currentScreen = null;

    // Track user actions for step transitions
    private boolean pannedOrZoomed = false;
    private String practiceNodeId = null;
    private String boilerNodeId = null;
    private String turbineNodeId = null;

    public static TutorialManager getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public TutorialStep getCurrentStep() {
        return currentStep;
    }

    public void startTutorial(BoardScreen screen) {
        this.currentScreen = screen;
        this.active = true;
        this.currentStep = TutorialStep.STEP_1_PAN_ZOOM;
        this.pannedOrZoomed = false;

        // Reset canvas to a clean slate
        if (screen != null) {
            screen.getSummaryOverlay().setCollapsed(true);
            screen.getGraph().getNodes().clear();
            screen.getGraph().getConnections().clear();
            screen.setPanX(screen.width / 2.0);
            screen.setPanY(screen.height / 2.0);
            screen.setZoom(1.0);
            screen.rebuildWidgets();
        }

        playSound(SoundEvents.UI_TOAST_IN, 1.0f);
    }

    public void stopTutorial() {
        this.active = false;
        this.currentStep = TutorialStep.STEP_1_PAN_ZOOM;
        this.practiceNodeId = null;
        this.boilerNodeId = null;
        this.turbineNodeId = null;
    }

    public void nextStep() {
        int nextOrdinal = currentStep.ordinal() + 1;
        if (nextOrdinal < TutorialStep.values().length) {
            currentStep = TutorialStep.values()[nextOrdinal];
            playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f);
            onStepEnter(currentStep);
        } else {
            completeTutorial();
        }
    }

    public void completeTutorial() {
        currentStep = TutorialStep.COMPLETED;
        playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
    }

    private void onStepEnter(TutorialStep step) {
        if (currentScreen == null) return;

        if (step == TutorialStep.STEP_4_NORMAL_WIRING) {
            setupWiringExercise();
        } else if (step == TutorialStep.STEP_6_SHIFT_WIRING) {
            // Reset turbine count to 1.0 so user can experience Shift auto-calculation to 5.0
            RecipeNode turbine = currentScreen.getGraph().findNodeById(turbineNodeId);
            if (turbine != null) {
                turbine.setMachineCount(1.0);
                currentScreen.rebuildWidgets();
            }
        }
    }

    private void setupWiringExercise() {
        if (currentScreen == null) return;

        currentScreen.getGraph().getNodes().clear();
        currentScreen.getGraph().getConnections().clear();

        // 1. Boiler: produces Steam 500 mB/s
        RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 30.0, GTVoltageTier.LV);
        boiler.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "steam"), "Steam", 500.0, 1.0));
        boiler.setPosX(-220);
        boiler.setPosY(-50);
        boiler.setMachineCount(1.0);
        this.boilerNodeId = boiler.getId();
        currentScreen.getGraph().addNode(boiler);

        // 2. Steam Turbine: consumes Steam 100 mB/s per machine, produces EU
        RecipeNode turbine = RecipeNode.create("Steam Turbine (Tutorial)", 20.0, 64.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "steam"), "Steam", 100.0, 1.0));
        turbine.setPosX(60);
        turbine.setPosY(-50);
        turbine.setMachineCount(1.0);
        this.turbineNodeId = turbine.getId();
        currentScreen.getGraph().addNode(turbine);

        currentScreen.rebuildWidgets();
    }

    // --- Action Event Triggers ---

    public void onPanOrZoom() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_1_PAN_ZOOM && !pannedOrZoomed) {
            pannedOrZoomed = true;
            nextStep();
        }
    }

    public void onNodeAdded(RecipeNode node) {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_2_ADD_RECIPE) {
            this.practiceNodeId = node != null ? node.getId() : null;
            nextStep();
        }
    }

    public void onNodeRemoved(RecipeNode node) {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_3_REMOVE_RECIPE) {
            nextStep();
        }
    }

    public void onWireConnected(boolean shiftDown) {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_4_NORMAL_WIRING) {
            // Advance to Step 5 (Delete Wiring)
            nextStep();
        } else if (currentStep == TutorialStep.STEP_6_SHIFT_WIRING) {
            // Advance to Step 7 (Auto Ratio)
            nextStep();
        }
    }

    public void onWireDisconnected() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_5_DELETE_WIRING) {
            nextStep(); // Advance to Step 6 (Shift Wiring)
        }
    }

    public void onAutoRatioTriggered() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_7_AUTO_RATIO) {
            nextStep();
        }
    }

    public void onModuleGrouped() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_8_SUMMARY_MODULE) {
            completeTutorial();
        }
    }

    public String getBoilerNodeId() {
        return boilerNodeId;
    }

    public String getTurbineNodeId() {
        return turbineNodeId;
    }

    public String getPracticeNodeId() {
        return practiceNodeId;
    }

    // --- Dynamic Widget Glowing Helpers ---

    public boolean isToolbarButtonGlowing(String buttonKey) {
        if (!active) return false;
        return switch (currentStep) {
            case STEP_2_ADD_RECIPE -> "add_recipe".equals(buttonKey);
            case STEP_7_AUTO_RATIO -> "auto_ratio".equals(buttonKey);
            case STEP_8_SUMMARY_MODULE -> "group_module".equals(buttonKey);
            default -> false;
        };
    }

    public boolean isNodeCloseButtonGlowing(String nodeId) {
        if (!active) return false;
        return currentStep == TutorialStep.STEP_3_REMOVE_RECIPE;
    }

    public boolean isNodeBaseTargetButtonGlowing(String nodeId) {
        if (!active) return false;
        if (currentStep == TutorialStep.STEP_7_AUTO_RATIO) {
            return turbineNodeId != null && turbineNodeId.equals(nodeId);
        }
        return false;
    }

    public boolean isPortGlowing(String nodeId, boolean isInput, int portIdx) {
        if (!active) return false;
        if (currentStep == TutorialStep.STEP_4_NORMAL_WIRING || currentStep == TutorialStep.STEP_6_SHIFT_WIRING) {
            if (!isInput && portIdx == 0 && boilerNodeId != null && boilerNodeId.equals(nodeId)) {
                return true;
            }
            if (isInput && portIdx == 0 && turbineNodeId != null && turbineNodeId.equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    public static int getGlowBorderColor(int defaultBorder) {
        float time = (System.currentTimeMillis() % 1200) / 600.0f;
        float factor = (float) (0.5 + 0.5 * Math.sin(time * Math.PI));
        int r = (int) (0x00 * (1 - factor) + 0x00 * factor);
        int g = (int) (0xEE * (1 - factor) + 0xFF * factor);
        int b = (int) (0x76 * (1 - factor) + 0xFF * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int getGlowBgColor(int defaultBg) {
        float time = (System.currentTimeMillis() % 1200) / 600.0f;
        float factor = (float) (0.5 + 0.5 * Math.sin(time * Math.PI));
        int r = (int) (0x1C * (1 - factor) + 0x3A * factor);
        int g = (int) (0x35 * (1 - factor) + 0x6A * factor);
        int b = (int) (0x24 * (1 - factor) + 0x4E * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(sound, pitch)
        );
    }
}
