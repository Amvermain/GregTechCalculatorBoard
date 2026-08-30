package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.EnergyType;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * Manages the state machine and progression of the 12-step interactive onboarding tutorial.
 */
public class TutorialManager {
    private static final TutorialManager INSTANCE = new TutorialManager();

    private boolean active = false;
    private TutorialStep currentStep = TutorialStep.STEP_1_ADD_RECIPE;
    private BoardScreen currentScreen = null;
    private String tutorialPageId = null;

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

    public String getTutorialPageId() {
        return tutorialPageId;
    }

    public boolean isTutorialPage(String pageId) {
        return active && tutorialPageId != null && tutorialPageId.equals(pageId);
    }

    public com.gtceu.calcboard.api.storage.BoardPage getTutorialPage() {
        if (!active || tutorialPageId == null) return null;
        for (com.gtceu.calcboard.api.storage.BoardPage p : com.gtceu.calcboard.api.storage.BoardManager.getInstance().getPages()) {
            if (tutorialPageId.equals(p.getId())) {
                return p;
            }
        }
        // If tutorial canvas was deleted, safely abort tutorial
        stopTutorial();
        return null;
    }

    public void startTutorial(BoardScreen screen) {
        this.currentScreen = screen;
        this.active = true;
        this.currentStep = TutorialStep.STEP_1_ADD_RECIPE;
        this.pannedOrZoomed = false;

        // Always create a dedicated new page for tutorial to protect user's existing work 100%!
        String pageName = "Tutorial";
        try {
            pageName = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tutorial.page_name").getString();
        } catch (Throwable ignored) {}
        com.gtceu.calcboard.api.storage.BoardPage newPage = com.gtceu.calcboard.api.storage.BoardManager.getInstance().addPage(pageName);
        this.tutorialPageId = newPage.getId();

        if (screen != null) {
            screen.getSummaryOverlay().setCollapsed(true);
            screen.setPanX(screen.width / 2.0);
            screen.setPanY(screen.height / 2.0);
            screen.setZoom(1.0);
            screen.rebuildWidgets();
        }

        playUiSound(0, 1.0f);
    }

    public void stopTutorial() {
        this.active = false;
        this.currentStep = TutorialStep.STEP_1_ADD_RECIPE;
        this.practiceNodeId = null;
        this.boilerNodeId = null;
        this.turbineNodeId = null;
        this.tutorialPageId = null;
    }

    public void nextStep() {
        int nextOrdinal = currentStep.ordinal() + 1;
        if (nextOrdinal < TutorialStep.values().length) {
            currentStep = TutorialStep.values()[nextOrdinal];
            playUiSound(1, 1.2f);
            onStepEnter(currentStep);
        } else {
            completeTutorial();
        }
    }

    public void completeTutorial() {
        currentStep = TutorialStep.COMPLETED;
        playUiSound(2, 1.0f);
    }

    private void onStepEnter(TutorialStep step) {
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null) return;

        // Ensure active page is tutorial page
        com.gtceu.calcboard.api.storage.BoardManager bm = com.gtceu.calcboard.api.storage.BoardManager.getInstance();
        if (bm.getActivePage() != tutPage) {
            int idx = bm.getPages().indexOf(tutPage);
            if (idx >= 0) {
                bm.switchPage(idx);
            }
        }

        if (step == TutorialStep.STEP_2_DRAG_TO_SEARCH) {
            setupStep2Exercise(tutPage);
        } else if (step == TutorialStep.STEP_3_JUNCTION) {
            setupStep3Exercise(tutPage);
        } else if (step == TutorialStep.STEP_4_SHIFT_WIRING) {
            setupStep4Exercise(tutPage);
        } else if (step == TutorialStep.STEP_5_MACHINE_CONFIG) {
            setupStep5Exercise(tutPage);
        } else if (step == TutorialStep.STEP_6_GROUP_FRAME) {
            setupStep6Exercise(tutPage);
        } else if (step == TutorialStep.STEP_7_COMPOUND_MODULE) {
            setupStep7Exercise(tutPage);
        }
    }

    private void setupStep2Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        tutPage.getGraph().clear();

        // 1. Boiler: produces Steam 500 mB/s
        RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 0.0, GTVoltageTier.LV);
        boiler.setEnergyType(com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF);
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        boiler.setPosX(-180);
        boiler.setPosY(-50);
        boiler.setMachineCount(1.0);
        this.boilerNodeId = boiler.getId();
        tutPage.getGraph().addNode(boiler);

        if (currentScreen != null) currentScreen.rebuildWidgets();
    }

    private void setupStep3Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);

        // Ensure a direct connection exists between Boiler and Turbine for user to double-click
        RecipeNode boiler = tutPage.getGraph().findNodeById(boilerNodeId);
        RecipeNode turbine = tutPage.getGraph().findNodeById(turbineNodeId);
        if (boiler != null && turbine != null) {
            boolean hasConn = tutPage.getGraph().getConnections().stream()
                    .anyMatch(c -> c.fromNodeId().equals(boiler.getId()) && c.toNodeId().equals(turbine.getId()));
            if (!hasConn) {
                tutPage.getGraph().addConnection(boiler.getId(), 0, turbine.getId(), 0);
            }
        }
        if (currentScreen != null) currentScreen.rebuildWidgets();
    }

    private void setupStep4Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);

        // Reset turbine machine count to 1.0 so user can experience Shift auto-calculation to 5.0
        RecipeNode turbine = tutPage.getGraph().findNodeById(turbineNodeId);
        if (turbine != null) {
            turbine.setMachineCount(1.0);
        }
        if (currentScreen != null) currentScreen.rebuildWidgets();
    }

    private void setupStep5Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);
        RecipeNode turbine = tutPage.getGraph().findNodeById(turbineNodeId);
        if (turbine != null) {
            turbine.setMachineCount(5.0);
        }
        if (currentScreen != null) currentScreen.rebuildWidgets();
    }

    private void setupStep6Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);
        tutPage.getGraph().getFrames().clear();
        if (currentScreen != null) currentScreen.rebuildWidgets();
    }

    private void setupStep7Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);
        if (tutPage.getGraph().getFrames().isEmpty()) {
            // Auto-create a frame if user skipped step 6
            RecipeNode boiler = tutPage.getGraph().findNodeById(boilerNodeId);
            RecipeNode turbine = tutPage.getGraph().findNodeById(turbineNodeId);
            if (boiler != null && turbine != null) {
                String defaultTitle = "Steam Power Group";
                try {
                    defaultTitle = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.default_frame_name").getString();
                } catch (Throwable ignored) {}
                com.gtceu.calcboard.api.model.CanvasGroupFrame frame = com.gtceu.calcboard.api.model.CanvasGroupFrame.createFromNodes(
                        defaultTitle, java.util.List.of(boiler, turbine), com.gtceu.calcboard.api.model.CanvasGroupFrame.COLOR_BLUE);
                tutPage.getGraph().addFrame(frame);
            }
        }
        if (currentScreen != null) {
            currentScreen.getSummaryOverlay().setCollapsed(false);
            currentScreen.rebuildWidgets();
        }
    }

    private void ensureBoilerAndTurbineExist(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        if (boilerNodeId == null || tutPage.getGraph().findNodeById(boilerNodeId) == null) {
            RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 0.0, GTVoltageTier.LV);
            boiler.setEnergyType(com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF);
            boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
            boiler.setPosX(-180);
            boiler.setPosY(-50);
            boiler.setMachineCount(1.0);
            this.boilerNodeId = boiler.getId();
            tutPage.getGraph().addNode(boiler);
        }

        if (turbineNodeId == null || tutPage.getGraph().findNodeById(turbineNodeId) == null) {
            RecipeNode turbine = RecipeNode.create("Steam Turbine (Tutorial)", 20.0, 64.0, GTVoltageTier.LV);
            turbine.setGenerator(true);
            turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0, 1.0));
            turbine.setPosX(80);
            turbine.setPosY(-50);
            turbine.setMachineCount(1.0);
            this.turbineNodeId = turbine.getId();
            tutPage.getGraph().addNode(turbine);
        }
    }

    // --- Action Event Triggers ---

    public void onPanOrZoom() {
    }

    public void onNodeAdded(RecipeNode node) {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_1_ADD_RECIPE) {
            this.practiceNodeId = node != null ? node.getId() : null;
            nextStep();
        } else if (currentStep == TutorialStep.STEP_2_DRAG_TO_SEARCH) {
            if (node != null && !node.getId().equals(boilerNodeId)) {
                this.turbineNodeId = node.getId();
            }
        } else if (currentStep == TutorialStep.STEP_3_JUNCTION) {
            if (node != null && node.isReroute()) {
                nextStep();
            }
        }
    }

    public void onRecipeLookup() {
    }

    public void onNodeRenamed() {
    }

    public void onNodeRemoved(RecipeNode node) {
    }

    public void onWireConnected(boolean shiftDown) {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_2_DRAG_TO_SEARCH) {
            nextStep(); // Advance to Step 3 (Junction)
        } else if (currentStep == TutorialStep.STEP_4_SHIFT_WIRING) {
            nextStep(); // Advance to Step 5 (Machine Config)
        }
    }

    public void onWireDisconnected() {
    }

    public void onJunctionInserted() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_3_JUNCTION) {
            nextStep(); // Advance to Step 4 (Shift Wiring)
        }
    }

    public void onAutoRatioTriggered() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_4_SHIFT_WIRING) {
            nextStep(); // Advance to Step 5 (Machine Config)
        }
    }

    public void onMachineConfigOpened() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_5_MACHINE_CONFIG) {
            nextStep(); // Advance to Step 6 (Group Frame)
        }
    }

    public void onGroupFramed() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_6_GROUP_FRAME) {
            nextStep(); // Advance to Step 7 (Compound Module)
        }
    }

    public void onSelectAll() {
    }

    public void onPasted() {
    }

    public void onCut() {
    }

    public void onUndo() {
    }

    public void onRedo() {
    }

    public void onModuleGrouped() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_7_COMPOUND_MODULE) {
            completeTutorial();
        }
    }

    public void onModuleExpanded() {
        if (!active) return;
        if (currentStep == TutorialStep.STEP_7_COMPOUND_MODULE) {
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
        if (currentStep == TutorialStep.STEP_1_ADD_RECIPE && "add_recipe".equals(buttonKey)) {
            return true;
        }
        return false;
    }

    public boolean isNodeCloseButtonGlowing(String nodeId) {
        return false;
    }

    public boolean isNodeBaseTargetButtonGlowing(String nodeId) {
        return false;
    }

    public boolean isMachineConfigButtonGlowing(String nodeId) {
        if (!active) return false;
        if (currentStep == TutorialStep.STEP_5_MACHINE_CONFIG) {
            return turbineNodeId != null && turbineNodeId.equals(nodeId);
        }
        return false;
    }

    public boolean isFrameCollapseButtonGlowing(String frameId) {
        if (!active) return false;
        return currentStep == TutorialStep.STEP_7_COMPOUND_MODULE;
    }

    public boolean isWireGlowing(String fromNodeId, String toNodeId) {
        if (!active) return false;
        if (currentStep == TutorialStep.STEP_3_JUNCTION) {
            return (boilerNodeId != null && boilerNodeId.equals(fromNodeId))
                    || (turbineNodeId != null && turbineNodeId.equals(toNodeId));
        }
        return false;
    }

    public boolean isPortGlowing(String nodeId, boolean isInput, int portIdx) {
        if (!active) return false;
        if (currentStep == TutorialStep.STEP_2_DRAG_TO_SEARCH) {
            if (!isInput && portIdx == 0 && boilerNodeId != null && boilerNodeId.equals(nodeId)) {
                return true;
            }
        } else if (currentStep == TutorialStep.STEP_4_SHIFT_WIRING) {
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

    private void playUiSound(int soundType, float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                net.minecraft.sounds.SoundEvent sound = switch (soundType) {
                    case 1 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                    case 2 -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
                    default -> SoundEvents.UI_TOAST_IN;
                };
                mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(sound, pitch)
                );
            }
        } catch (Throwable ignored) {}
    }
}



