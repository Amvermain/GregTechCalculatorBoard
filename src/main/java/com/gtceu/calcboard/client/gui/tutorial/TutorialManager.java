package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.EnergyType;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.tutorial.model.ITutorialChapter;
import com.gtceu.calcboard.client.gui.tutorial.model.TutorialChapterStepDef;
import com.gtceu.calcboard.client.gui.tutorial.model.TutorialTrackType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * Manages the state machine and progression of the 14-step interactive onboarding tutorial.
 */
public class TutorialManager {
    private static final ResourceLocation ELECTRIC_BLAST_FURNACE_ID = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
    private static final TutorialManager INSTANCE = new TutorialManager();

    static {
        registerEventListeners();
    }

    public static void registerEventListeners() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.gtceu.calcboard.api.event.FlowGraphEvent.WireConnected event) -> INSTANCE.onWireConnectedEvent(event));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.gtceu.calcboard.api.event.FlowGraphEvent.WireDisconnected event) -> INSTANCE.onWireDisconnectedEvent(event));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionInserted event) -> INSTANCE.onJunctionInsertedEvent(event));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionConfigured event) -> INSTANCE.onJunctionConfiguredEvent(event));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.gtceu.calcboard.api.event.FlowGraphEvent.PostSolve event) -> INSTANCE.onPostSolveEvent(event));
    }

    private boolean active = false;
    private TutorialStep currentStep = TutorialStep.STEP_1_ADD_RECIPE;
    private IBoardScreenContext currentScreen = null;
    private String tutorialPageId = null;

    private TutorialTrackType trackType = null;
    private boolean fastTrackActive = false;
    private int fastTrackStepIndex = 0;
    private String activeChapterId = null;
    private int activeChapterStepIndex = 0;

    // Track user actions for step transitions
    private boolean pannedOrZoomed = false;
    private String practiceNodeId = null;
    private String boilerNodeId = null;
    private String turbineNodeId = null;
    private String selectorNodeId = null;

    private boolean stepActionCompleted = false;
    private String stepResultDescKey = null;

    public static TutorialManager getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isStepActionCompleted() {
        return stepActionCompleted;
    }

    public String getStepResultDescKey() {
        return stepResultDescKey;
    }

    public void markStepActionCompleted(String resultDescKey) {
        if (!active || stepActionCompleted) return;
        if (currentScreen == null) {
            nextStep();
            return;
        }
        this.stepActionCompleted = true;
        this.stepResultDescKey = resultDescKey;
        playUiSound(1, 1.2f);
        if (currentScreen != null) {
            currentScreen.rebuildBoardWidgets();
        }
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

    public enum TutorialMode {
        BASIC,
        ADVANCED
    }

    private TutorialMode mode = TutorialMode.BASIC;

    public TutorialMode getMode() {
        return mode;
    }

    public void startTutorial(IBoardScreenContext screen) {
        startTutorial(screen, TutorialMode.BASIC);
    }

    public void startAdvancedTutorial(IBoardScreenContext screen) {
        startTutorial(screen, TutorialMode.ADVANCED);
    }

    public void startTutorial(IBoardScreenContext screen, TutorialMode mode) {
        this.currentScreen = screen;
        this.active = true;
        this.trackType = null;
        this.fastTrackActive = false;
        this.fastTrackStepIndex = 0;
        this.activeChapterId = null;
        this.activeChapterStepIndex = 0;
        this.mode = mode;
        this.currentStep = (mode == TutorialMode.ADVANCED) ? TutorialStep.STEP_10_SHARED_MACHINE : TutorialStep.STEP_1_ADD_RECIPE;
        this.pannedOrZoomed = false;
        this.stepActionCompleted = false;
        this.stepResultDescKey = null;

        // Always create a dedicated new page for tutorial to protect user's existing work 100%!
        String pageName = (mode == TutorialMode.ADVANCED) ? "Advanced Tutorial" : "Tutorial";
        String langKey = (mode == TutorialMode.ADVANCED) ? "gui.gtcalcboard.tutorial.advanced_page_name" : "gui.gtcalcboard.tutorial.page_name";
        try {
            pageName = net.minecraft.network.chat.Component.translatable(langKey).getString();
        } catch (Throwable ignored) {}
        com.gtceu.calcboard.api.storage.BoardPage newPage = com.gtceu.calcboard.api.storage.BoardManager.getInstance().addPage(pageName);
        this.tutorialPageId = newPage.getId();

        if (screen != null) {
            screen.getSummaryOverlay().setCollapsed(true);
            screen.setPanX(screen.getScreenWidth() / 2.0);
            screen.setPanY(screen.getScreenHeight() / 2.0);
            screen.setZoom(1.0);
            screen.rebuildBoardWidgets();
        }

        playUiSound(0, 1.0f);
        onStepEnter(this.currentStep);
    }

    public void startFastTrack(IBoardScreenContext screen) {
        this.currentScreen = screen;
        this.active = true;
        this.trackType = TutorialTrackType.FAST_TRACK;
        this.fastTrackActive = true;
        this.fastTrackStepIndex = 0;
        this.activeChapterId = null;
        this.activeChapterStepIndex = 0;
        this.mode = TutorialMode.BASIC;
        this.currentStep = TutorialStep.STEP_1_ADD_RECIPE;
        this.pannedOrZoomed = false;
        this.stepActionCompleted = false;
        this.stepResultDescKey = null;

        String pageName = "Fast-Track Tutorial";
        try {
            pageName = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tutorial.fast_track.page_name").getString();
        } catch (Throwable ignored) {}
        com.gtceu.calcboard.api.storage.BoardPage newPage = com.gtceu.calcboard.api.storage.BoardManager.getInstance().addPage(pageName);
        this.tutorialPageId = newPage.getId();

        if (screen != null) {
            screen.getSummaryOverlay().setCollapsed(true);
            screen.setPanX(screen.getScreenWidth() / 2.0);
            screen.setPanY(screen.getScreenHeight() / 2.0);
            screen.setZoom(1.0);
            screen.rebuildBoardWidgets();
        }

        playUiSound(0, 1.0f);
        setupFastTrackStep(0);
    }

    public void startChapter(IBoardScreenContext screen, String chapterId) {
        this.currentScreen = screen;
        this.active = true;
        this.trackType = TutorialTrackType.ACADEMY_CHAPTER;
        this.fastTrackActive = false;
        this.fastTrackStepIndex = 0;
        this.activeChapterId = chapterId;
        this.activeChapterStepIndex = 0;
        this.mode = TutorialMode.BASIC;
        this.currentStep = TutorialStep.STEP_1_ADD_RECIPE;
        this.pannedOrZoomed = false;
        this.stepActionCompleted = false;
        this.stepResultDescKey = null;

        ITutorialChapter chapter = TutorialTrackRegistry.getInstance().getChapter(chapterId);
        String pageName = chapter != null ? chapter.getTitle().getString() : "Academy Chapter";
        com.gtceu.calcboard.api.storage.BoardPage newPage = com.gtceu.calcboard.api.storage.BoardManager.getInstance().addPage(pageName);
        this.tutorialPageId = newPage.getId();

        if (screen != null) {
            screen.getSummaryOverlay().setCollapsed(true);
            screen.setPanX(screen.getScreenWidth() / 2.0);
            screen.setPanY(screen.getScreenHeight() / 2.0);
            screen.setZoom(1.0);
            screen.rebuildBoardWidgets();
        }

        playUiSound(0, 1.0f);
        setupChapterStep(chapterId, 0);
    }

    public void stopTutorial() {
        this.active = false;
        this.trackType = null;
        this.fastTrackActive = false;
        this.fastTrackStepIndex = 0;
        this.activeChapterId = null;
        this.activeChapterStepIndex = 0;
        this.mode = TutorialMode.BASIC;
        this.currentStep = TutorialStep.STEP_1_ADD_RECIPE;
        this.practiceNodeId = null;
        this.boilerNodeId = null;
        this.turbineNodeId = null;
        this.selectorNodeId = null;
        this.tutorialPageId = null;
        this.stepActionCompleted = false;
        this.stepResultDescKey = null;
    }

    public void nextStep() {
        this.stepActionCompleted = false;
        this.stepResultDescKey = null;

        if (fastTrackActive) {
            fastTrackStepIndex++;
            if (fastTrackStepIndex >= TutorialTrackRegistry.getInstance().getFastTrackSteps().size()) {
                completeTutorial();
            } else {
                playUiSound(1, 1.2f);
                setupFastTrackStep(fastTrackStepIndex);
            }
            return;
        }

        if (activeChapterId != null) {
            activeChapterStepIndex++;
            var steps = TutorialTrackRegistry.getInstance().getChapterSteps(activeChapterId);
            if (activeChapterStepIndex >= steps.size()) {
                completeTutorial();
            } else {
                playUiSound(1, 1.2f);
                setupChapterStep(activeChapterId, activeChapterStepIndex);
            }
            return;
        }

        if (mode == TutorialMode.BASIC && currentStep == TutorialStep.STEP_9_COMPOUND_MODULE) {
            completeTutorial();
            return;
        }
        if (mode == TutorialMode.ADVANCED && currentStep == TutorialStep.STEP_13_FOLDER_BROWSER) {
            completeTutorial();
            return;
        }

        int nextOrdinal = currentStep.ordinal() + 1;
        if (nextOrdinal < TutorialStep.values().length) {
            currentStep = TutorialStep.values()[nextOrdinal];
            playUiSound(1, 1.2f);
            onStepEnter(currentStep);
        } else {
            completeTutorial();
        }
    }

    public boolean hasPreviousStep() {
        if (!active || currentStep == TutorialStep.COMPLETED) return false;
        if (fastTrackActive) {
            return fastTrackStepIndex > 0;
        }
        if (activeChapterId != null) {
            return activeChapterStepIndex > 0;
        }
        if (mode == TutorialMode.ADVANCED) {
            return currentStep.ordinal() > TutorialStep.STEP_10_SHARED_MACHINE.ordinal();
        }
        return currentStep.ordinal() > TutorialStep.STEP_1_ADD_RECIPE.ordinal();
    }

    public void previousStep() {
        if (!hasPreviousStep()) return;
        this.stepActionCompleted = false;
        this.stepResultDescKey = null;

        if (fastTrackActive) {
            fastTrackStepIndex--;
            playUiSound(1, 0.9f);
            setupFastTrackStep(fastTrackStepIndex);
            return;
        }
        if (activeChapterId != null) {
            activeChapterStepIndex--;
            playUiSound(1, 0.9f);
            setupChapterStep(activeChapterId, activeChapterStepIndex);
            return;
        }
        int prevOrdinal = currentStep.ordinal() - 1;
        if (prevOrdinal >= 0) {
            currentStep = TutorialStep.values()[prevOrdinal];
            playUiSound(1, 0.9f);
            onStepEnter(currentStep);
        }
    }

    public void completeTutorial() {
        this.stepActionCompleted = false;
        this.stepResultDescKey = null;
        currentStep = TutorialStep.COMPLETED;
        playUiSound(2, 1.0f);
    }

    private void setupFastTrackStep(int index) {
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null) return;
        var steps = TutorialTrackRegistry.getInstance().getFastTrackSteps();
        if (index >= 0 && index < steps.size()) {
            steps.get(index).setupAction().accept(tutPage);
            if (currentScreen != null) {
                currentScreen.rebuildBoardWidgets();
            }
        }
    }

    private void setupChapterStep(String chapterId, int index) {
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null) return;
        var steps = TutorialTrackRegistry.getInstance().getChapterSteps(chapterId);
        if (index >= 0 && index < steps.size()) {
            steps.get(index).setupAction().accept(tutPage);
            if (currentScreen != null) {
                currentScreen.rebuildBoardWidgets();
            }
        }
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
        } else if (step == TutorialStep.STEP_5_JUNCTION_ETA) {
            setupStep5JunctionExercise(tutPage);
        } else if (step == TutorialStep.STEP_6_MACHINE_SELECTOR) {
            setupStep6SelectorExercise(tutPage);
        } else if (step == TutorialStep.STEP_7_MACHINE_CONFIG) {
            setupStep7Exercise(tutPage);
        } else if (step == TutorialStep.STEP_8_GROUP_FRAME) {
            setupStep8Exercise(tutPage);
        } else if (step == TutorialStep.STEP_9_COMPOUND_MODULE) {
            setupStep9Exercise(tutPage);
        } else if (step == TutorialStep.STEP_10_SHARED_MACHINE) {
            setupStep10Exercise(tutPage);
        } else if (step == TutorialStep.STEP_11_BOM_INSPECTION) {
            setupStep11Exercise(tutPage);
        } else if (step == TutorialStep.STEP_12_JUNCTION_SUPPLY) {
            setupStep12SupplyExercise(tutPage);
        } else if (step == TutorialStep.STEP_13_FOLDER_BROWSER) {
            setupStep13Exercise(tutPage);
        }
    }

    private void setupStep2Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;
        RecipeNode existingBoiler = boilerNodeId != null ? tutPage.getGraph().findNodeById(boilerNodeId) : null;
        if (existingBoiler == null) {
            existingBoiler = tutPage.getGraph().getNodes().stream()
                    .filter(n -> !n.isReroute() && n.getOutputs().stream().anyMatch(out -> "Steam".equalsIgnoreCase(out.getDisplayName()) || (out.getId() != null && "steam".equals(out.getId().getPath()))))
                    .findFirst()
                    .orElse(null);
        }
        if (existingBoiler == null && !tutPage.getGraph().getNodes().isEmpty()) {
            existingBoiler = tutPage.getGraph().getNodes().get(0);
        }

        if (existingBoiler == null) {
            RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 0.0, GTVoltageTier.LV);
            boiler.setEnergyType(com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF);
            boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
            boiler.setPosX(-180);
            boiler.setPosY(-50);
            boiler.setMachineCount(1.0);
            this.boilerNodeId = boiler.getId();
            tutPage.getGraph().addNode(boiler);
        } else {
            this.boilerNodeId = existingBoiler.getId();
        }

        if (currentScreen != null) currentScreen.rebuildBoardWidgets();
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
        if (currentScreen != null) currentScreen.rebuildBoardWidgets();
    }

    private void setupStep4Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);

        RecipeNode boiler = tutPage.getGraph().findNodeById(boilerNodeId);
        RecipeNode turbine = tutPage.getGraph().findNodeById(turbineNodeId);
        if (turbine != null) {
            turbine.setMachineCount(1.0);
        }

        RecipeNode junction = tutPage.getGraph().getNodes().stream()
                .filter(RecipeNode::isReroute)
                .findFirst()
                .orElse(null);

        if (junction == null && boiler != null && turbine != null) {
            IngredientStack outStack = !boiler.getOutputs().isEmpty() ? boiler.getOutputs().get(0) : null;
            junction = RecipeNode.createReroute(boiler.getPosX() + 180, boiler.getPosY() + 20);
            if (outStack != null) {
                junction.bindRerouteIngredient(outStack.copy());
            }
            tutPage.getGraph().addNode(junction);
            tutPage.getGraph().addConnection(boiler.getId(), 0, junction.getId(), 0);
            tutPage.getGraph().addConnection(junction.getId(), 0, turbine.getId(), 0);
        }
        if (currentScreen != null) currentScreen.rebuildBoardWidgets();
    }

    private void setupStep5JunctionExercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;
        ensureBoilerAndTurbineExist(tutPage);

        RecipeNode junction = tutPage.getGraph().getNodes().stream()
                .filter(RecipeNode::isReroute)
                .findFirst()
                .orElse(null);

        if (junction == null) {
            RecipeNode boiler = tutPage.getGraph().findNodeById(boilerNodeId);
            if (boiler != null) {
                IngredientStack outStack = !boiler.getOutputs().isEmpty() ? boiler.getOutputs().get(0) : null;
                junction = RecipeNode.createReroute(boiler.getPosX() + 180, boiler.getPosY() + 20);
                if (outStack != null) {
                    junction.bindRerouteIngredient(outStack.copy());
                }
                tutPage.getGraph().addNode(junction);
                tutPage.getGraph().addConnection(new com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge(
                        boiler.getId(), 0, junction.getId(), 0
                ));
            }
        }

        if (junction != null) {
            junction.setTargetBatchAmount(1000.0);
        }

        RecipeNode turbine = tutPage.getGraph().findNodeById(turbineNodeId);
        if (turbine != null) {
            turbine.setMachineCount(5.0);
            if (junction != null) {
                final String juncId = junction.getId();
                final String turbId = turbine.getId();
                boolean hasConn = tutPage.getGraph().getConnections().stream()
                        .anyMatch(c -> c.fromNodeId().equals(juncId) && c.toNodeId().equals(turbId));
                if (!hasConn) {
                    tutPage.getGraph().addConnection(new com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge(
                            juncId, 0, turbId, 0
                    ));
                }
            }
        }
        if (currentScreen != null) currentScreen.rebuildBoardWidgets();
    }

    private void setupStep6SelectorExercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);

        if (selectorNodeId == null || tutPage.getGraph().findNodeById(selectorNodeId) == null) {
            ResourceLocation sbIcon = ResourceLocation.tryParse("gtceu:lv_electric_blast_furnace");
            RecipeNode furnaceNode = RecipeNode.create(sbIcon, "Iron Ingot to Steel", 200.0, 120.0, GTVoltageTier.LV);
            furnaceNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
            furnaceNode.setAvailableWorkstations(java.util.List.of(
                ResourceLocation.tryParse("gtceu:lv_electric_blast_furnace"),
                ResourceLocation.tryParse("gtceu:electric_blast_furnace")
            ));
            furnaceNode.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
            furnaceNode.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:steel_ingot"), "Steel Ingot", 1.0));
            furnaceNode.setPosX(340);
            furnaceNode.setPosY(-50);
            furnaceNode.setMachineCount(1.0);
            furnaceNode.setMultiblock(false);
            this.selectorNodeId = furnaceNode.getId();
            tutPage.getGraph().addNode(furnaceNode);
        }
        if (currentScreen != null) currentScreen.rebuildBoardWidgets();
    }

    private void setupStep7Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);
        RecipeNode turbine = tutPage.getGraph().findNodeById(turbineNodeId);
        if (turbine != null) {
            turbine.setMachineCount(5.0);
        }
        if (currentScreen != null) currentScreen.rebuildBoardWidgets();
    }

    private void setupStep8Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);
        tutPage.getGraph().clearFrames();
        if (currentScreen != null) currentScreen.rebuildBoardWidgets();
    }

    private void setupStep9Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;

        ensureBoilerAndTurbineExist(tutPage);
        if (tutPage.getGraph().getFrames().isEmpty()) {
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
            currentScreen.rebuildBoardWidgets();
        }
    }

    private void setupStep10Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;
        tutPage.getGraph().clear();

        ResourceLocation cutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");
        RecipeNode cutter1 = RecipeNode.create(cutterIcon, "Quartz Slicing (Tutorial)", 20.0, 30.0, GTVoltageTier.LV);
        cutter1.setMachineCount(0.15);
        cutter1.setPos(-280, -50);
        cutter1.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:quartz_block"), "Quartz Block", 1.0));
        cutter1.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:quartz"), "Quartz", 4.0));

        RecipeNode cutter2 = RecipeNode.create(cutterIcon, "Amethyst Slicing (Tutorial)", 20.0, 30.0, GTVoltageTier.LV);
        cutter2.setMachineCount(0.20);
        cutter2.setPos(0, -50);
        cutter2.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:amethyst_block"), "Amethyst Block", 1.0));
        cutter2.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:amethyst_shard"), "Amethyst Shard", 4.0));

        RecipeNode cutter3 = RecipeNode.create(cutterIcon, "Echo Slicing (Tutorial)", 20.0, 30.0, GTVoltageTier.LV);
        cutter3.setMachineCount(0.10);
        cutter3.setPos(280, -50);
        cutter3.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:echo_shard"), "Echo Shard", 1.0));
        cutter3.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:sculk"), "Sculk", 2.0));

        tutPage.getGraph().addNode(cutter1);
        tutPage.getGraph().addNode(cutter2);
        tutPage.getGraph().addNode(cutter3);

        if (currentScreen != null) {
            currentScreen.rebuildBoardWidgets();
        }
    }

    private void setupStep11Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;
        boolean hasSharedFrame = tutPage.getGraph().getFrames().stream().anyMatch(CanvasGroupFrame::isSharedMachineFrame);
        if (!hasSharedFrame && tutPage.getGraph().getNodes().size() >= 2) {
            CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared LV Cutter", tutPage.getGraph().getNodes(), CanvasGroupFrame.COLOR_EMERALD);
            frame.setSharedMachineFrame(true);
            tutPage.getGraph().addFrame(frame);
        }
        if (currentScreen != null) {
            currentScreen.rebuildBoardWidgets();
        }
    }

    private void setupStep12SupplyExercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;
        RecipeNode sourceNode = tutPage.getGraph().getNodes().stream()
                .filter(n -> !n.isReroute())
                .findFirst()
                .orElse(null);
        if (sourceNode == null) return;

        RecipeNode junction = tutPage.getGraph().getNodes().stream()
                .filter(RecipeNode::isReroute)
                .findFirst()
                .orElse(null);

        double targetX = sourceNode.getPosX() + 105;
        double targetY = sourceNode.getPosY() + 160;

        if (junction == null) {
            IngredientStack outStack = !sourceNode.getOutputs().isEmpty() ? sourceNode.getOutputs().get(0) : null;
            if (outStack != null) {
                junction = RecipeNode.createReroute(targetX, targetY);
                junction.bindRerouteIngredient(outStack.copy());
                junction.setTargetBatchAmount(100.0);
                tutPage.getGraph().addNode(junction);
                tutPage.getGraph().addConnection(new com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge(
                        sourceNode.getId(), 0, junction.getId(), 0
                ));
            }
        } else if (junction.getPosY() < sourceNode.getPosY() + 100) {
            junction.setPosX(targetX);
            junction.setPosY(targetY);
        }

        if (currentScreen != null) {
            currentScreen.rebuildBoardWidgets();
        }
    }

    private void setupStep13Exercise(com.gtceu.calcboard.api.storage.BoardPage tutPage) {
        if (tutPage == null) return;
        tutPage.setFolderPath("Factory/Refining");
        if (currentScreen != null) {
            currentScreen.rebuildBoardWidgets();
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
        if (!active || stepActionCompleted) return;
        if (fastTrackActive && fastTrackStepIndex == 0) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.fast_track.step1_result");
            return;
        }
        if (currentStep == TutorialStep.STEP_1_ADD_RECIPE) {
            this.practiceNodeId = node != null ? node.getId() : null;
            markStepActionCompleted("gui.gtcalcboard.tutorial.step1_result");
        } else if (currentStep == TutorialStep.STEP_2_DRAG_TO_SEARCH) {
            if (node != null && !node.getId().equals(boilerNodeId)) {
                this.turbineNodeId = node.getId();
            }
        } else if (currentStep == TutorialStep.STEP_3_JUNCTION) {
            if (node != null && node.isReroute()) {
                markStepActionCompleted("gui.gtcalcboard.tutorial.step3_result");
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
        if (!active || stepActionCompleted) return;
        if (fastTrackActive && fastTrackStepIndex == 1) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.fast_track.step2_result");
            return;
        }
        if (currentStep == TutorialStep.STEP_2_DRAG_TO_SEARCH) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step2_result");
        } else if (currentStep == TutorialStep.STEP_4_SHIFT_WIRING) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step4_result");
        }
    }

    public void onWireDisconnected() {
        if (!active) return;
        if (currentScreen != null) {
            currentScreen.rebuildBoardWidgets();
        }
    }

    public void onJunctionInserted() {
        if (!active || stepActionCompleted) return;
        if ("ch2_wiring".equals(activeChapterId) && activeChapterStepIndex == 0) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch2.step1_result");
            return;
        }
        if (currentStep == TutorialStep.STEP_3_JUNCTION) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step3_result");
        }
    }

    public void onWireConnectedEvent(com.gtceu.calcboard.api.event.FlowGraphEvent.WireConnected event) {
        if (!active || event.getGraph() == null) return;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null || tutPage.getGraph() != event.getGraph()) return;
        onWireConnected(event.isShiftDown());
    }

    public void onWireDisconnectedEvent(com.gtceu.calcboard.api.event.FlowGraphEvent.WireDisconnected event) {
        if (!active || event.getGraph() == null) return;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null || tutPage.getGraph() != event.getGraph()) return;
        onWireDisconnected();
    }

    public void onJunctionInsertedEvent(com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionInserted event) {
        if (!active || event.getGraph() == null) return;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null || tutPage.getGraph() != event.getGraph()) return;
        onJunctionInserted();
    }

    public void onJunctionConfiguredEvent(com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionConfigured event) {
        if (!active || event.getGraph() == null) return;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null || tutPage.getGraph() != event.getGraph()) return;
        if (currentStep == TutorialStep.STEP_12_JUNCTION_SUPPLY) {
            if (event.getMode() != null && event.getMode() != com.gtceu.calcboard.api.type.SupplyMode.NONE) {
                onJunctionSupplyConfigured();
            }
        }
    }

    public void onPostSolveEvent(com.gtceu.calcboard.api.event.FlowGraphEvent.PostSolve event) {
        if (!active || event.getGraph() == null) return;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null || tutPage.getGraph() != event.getGraph()) return;
        if (currentStep == TutorialStep.STEP_12_JUNCTION_SUPPLY) {
            boolean hasConfiguredSupply = tutPage.getGraph().getNodes().stream()
                    .anyMatch(n -> n.isReroute() && n.getSupplyMode() != com.gtceu.calcboard.api.type.SupplyMode.NONE);
            if (hasConfiguredSupply) {
                onJunctionSupplyConfigured();
            }
        }
    }

    public void onAutoRatioTriggered() {
        if (!active || stepActionCompleted) return;
        if (fastTrackActive && fastTrackStepIndex == 2) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.fast_track.step3_result");
            return;
        }
        if ("ch1_solver".equals(activeChapterId)) {
            if (activeChapterStepIndex == 1) {
                markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch1.step2_result");
                return;
            }
            if (activeChapterStepIndex == 3) {
                markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch1.step4_result");
                return;
            }
        }
        if (currentStep == TutorialStep.STEP_4_SHIFT_WIRING) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step4_result");
        }
    }

    public void onRateUnitToggled() {
        if (!active || stepActionCompleted) return;
        if (fastTrackActive && fastTrackStepIndex == 3) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.fast_track.step4_result");
        }
    }

    public void onIntegerRatioTriggered() {
        if (!active || stepActionCompleted) return;
        if ("ch1_solver".equals(activeChapterId) && activeChapterStepIndex == 2) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch1.step3_result");
        }
    }

    public void onFractionalRatioTriggered() {
        if (!active || stepActionCompleted) return;
        if ("ch1_solver".equals(activeChapterId)) {
            if (activeChapterStepIndex == 2) {
                markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch1.step3_result");
            } else if (activeChapterStepIndex == 3) {
                markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch1.step4_result");
            }
        }
    }

    public void onAnchorConfigured() {
        if (!active || stepActionCompleted) return;
        if ("ch1_solver".equals(activeChapterId) && activeChapterStepIndex == 0) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch1.step1_result");
        }
    }

    public void onLoopScaled() {
        if (!active || stepActionCompleted) return;
        if ("ch1_solver".equals(activeChapterId) && activeChapterStepIndex == 3) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch1.step4_result");
        }
    }

    public void onWirePriorityChanged(com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge edge, int priority) {
        if (!active || stepActionCompleted) return;
        if ("ch2_wiring".equals(activeChapterId) && activeChapterStepIndex == 1) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch2.step2_result");
        }
    }

    public void onPortVoidConfigured() {
        if (!active || stepActionCompleted) return;
        if ("ch2_wiring".equals(activeChapterId) && activeChapterStepIndex == 2) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch2.step3_result");
        }
    }

    public void onPortTagCycled() {
        if (!active || stepActionCompleted) return;
        if ("ch2_wiring".equals(activeChapterId) && activeChapterStepIndex == 3) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch2.step4_result");
        }
    }

    public void onSubpageEntered() {
    }

    public void onSubpageExited() {
        if (!active || stepActionCompleted) return;
        if ("ch3_packaging".equals(activeChapterId) && activeChapterStepIndex == 2) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch3.step3_result");
        }
    }

    public void onPageSettingsConfigured() {
        if (!active || stepActionCompleted) return;
        if ("ch4_workspace".equals(activeChapterId) && activeChapterStepIndex == 0) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch4.step1_result");
        }
    }

    public void onGlobalBalanceOpened() {
        if (!active || stepActionCompleted) return;
        if ("ch4_workspace".equals(activeChapterId) && activeChapterStepIndex == 2) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch4.step3_result");
        }
    }

    public void onTargetBatchConfigured(RecipeNode node, double amount) {
        if (!active || stepActionCompleted) return;
        if (currentStep == TutorialStep.STEP_5_JUNCTION_ETA) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step5_junction_result");
        }
    }

    public void onMachineSwitched(RecipeNode node, ResourceLocation newWs) {
        if (!active || stepActionCompleted) return;
        if (currentStep == TutorialStep.STEP_6_MACHINE_SELECTOR) {
            if (node != null && (node.isMultiblock() || ELECTRIC_BLAST_FURNACE_ID.equals(newWs))) {
                markStepActionCompleted("gui.gtcalcboard.tutorial.step5_selector_result");
            }
        }
    }

    public void onMachineConfigOpened() {
        if (!active || stepActionCompleted) return;
        if (currentStep == TutorialStep.STEP_7_MACHINE_CONFIG) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step5_result");
        }
    }

    public void onGroupFramed() {
        if (!active || stepActionCompleted) return;
        if (currentStep == TutorialStep.STEP_8_GROUP_FRAME) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step6_result");
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
        if (!active || stepActionCompleted) return;
        if ("ch3_packaging".equals(activeChapterId) && activeChapterStepIndex == 1) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch3.step2_result");
            return;
        }
        if (currentStep == TutorialStep.STEP_9_COMPOUND_MODULE) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step7_result");
        }
    }

    public void onModuleExpanded() {
        if (!active || stepActionCompleted) return;
        if (currentStep == TutorialStep.STEP_9_COMPOUND_MODULE) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step7_result");
        }
    }

    public void onSharedMachineFramed() {
        if (!active || stepActionCompleted) return;
        if ("ch3_packaging".equals(activeChapterId) && activeChapterStepIndex == 0) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch3.step1_result");
            return;
        }
        if (currentStep == TutorialStep.STEP_10_SHARED_MACHINE) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step8_result");
        }
    }

    public void onBOMOpened() {
        if (!active || stepActionCompleted) return;
        if ("ch3_packaging".equals(activeChapterId) && activeChapterStepIndex == 3) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch3.step4_result");
            return;
        }
        if (currentStep == TutorialStep.STEP_11_BOM_INSPECTION) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step9_result");
        }
    }

    public void onJunctionSupplyConfigured() {
        if (!active || stepActionCompleted) return;
        if (currentStep == TutorialStep.STEP_12_JUNCTION_SUPPLY) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step10_result");
        }
    }

    public void onFolderBrowserOpened() {
        if (!active || stepActionCompleted) return;
        if ("ch4_workspace".equals(activeChapterId) && activeChapterStepIndex == 1) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.chapter.ch4.step2_result");
            return;
        }
        if (currentStep == TutorialStep.STEP_13_FOLDER_BROWSER) {
            markStepActionCompleted("gui.gtcalcboard.tutorial.step11_result");
        }
    }

    public boolean isFastTrackActive() {
        return active && fastTrackActive;
    }

    public boolean isChapterActive() {
        return active && activeChapterId != null;
    }

    public int getFastTrackStepIndex() {
        return fastTrackStepIndex;
    }

    public int getActiveChapterStepIndex() {
        return activeChapterStepIndex;
    }

    public String getActiveChapterId() {
        return activeChapterId;
    }

    public ITutorialChapter getActiveChapter() {
        return activeChapterId != null ? TutorialTrackRegistry.getInstance().getChapter(activeChapterId) : null;
    }

    public TutorialTrackType getTrackType() {
        return trackType;
    }

    public String getBoilerNodeId() {
        return boilerNodeId;
    }

    public void setBoilerNodeId(String id) {
        this.boilerNodeId = id;
    }

    public String getTurbineNodeId() {
        return turbineNodeId;
    }

    public void setTurbineNodeId(String id) {
        this.turbineNodeId = id;
    }

    public String getPracticeNodeId() {
        return practiceNodeId;
    }

    public String getSelectorNodeId() {
        return selectorNodeId;
    }

    // --- Dynamic Widget Glowing Helpers ---

    public boolean isToolbarButtonGlowing(String buttonKey) {
        if (!active || stepActionCompleted) return false;
        if (currentStep == TutorialStep.STEP_1_ADD_RECIPE && "add_recipe".equals(buttonKey) && !fastTrackActive && activeChapterId == null) {
            return true;
        }
        if (fastTrackActive && fastTrackStepIndex == 0 && "add_recipe".equals(buttonKey)) {
            return true;
        }
        if (fastTrackActive && fastTrackStepIndex == 2 && "auto_ratio".equals(buttonKey)) {
            return true;
        }
        if ("ch1_solver".equals(activeChapterId) && (activeChapterStepIndex == 1 || activeChapterStepIndex == 2) && "auto_ratio".equals(buttonKey)) {
            return true;
        }
        if (currentStep == TutorialStep.STEP_13_FOLDER_BROWSER && "page_browser".equals(buttonKey)) {
            return true;
        }
        if ("ch4_workspace".equals(activeChapterId) && activeChapterStepIndex == 1 && "page_browser".equals(buttonKey)) {
            return true;
        }
        return false;
    }

    public boolean isNodeCloseButtonGlowing(String nodeId) {
        return false;
    }

    public boolean isNodeBaseTargetButtonGlowing(String nodeId) {
        if (!active || stepActionCompleted || nodeId == null) return false;
        if ("ch1_solver".equals(activeChapterId) && activeChapterStepIndex == 0) {
            com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
            if (tutPage != null) {
                RecipeNode target = tutPage.getGraph().getNodes().stream()
                        .filter(n -> !n.isReroute() && "Plate Bender".equals(n.getName()))
                        .findFirst()
                        .orElse(null);
                return target != null && target.getId().equals(nodeId);
            }
        }
        return false;
    }

    public boolean isMachineIconGlowing(String nodeId) {
        if (!active || stepActionCompleted) return false;
        if (currentStep == TutorialStep.STEP_6_MACHINE_SELECTOR) {
            return selectorNodeId != null && selectorNodeId.equals(nodeId);
        }
        return false;
    }

    public boolean isMachineSelectorRowGlowing(ResourceLocation machineId) {
        if (!active || stepActionCompleted || machineId == null) return false;
        if (currentStep == TutorialStep.STEP_6_MACHINE_SELECTOR) {
            return ELECTRIC_BLAST_FURNACE_ID.equals(machineId);
        }
        return false;
    }

    public boolean isNodeConfigButtonGlowing(String nodeId) {
        if (!active || stepActionCompleted) return false;
        if (currentStep == TutorialStep.STEP_7_MACHINE_CONFIG) {
            return turbineNodeId != null && turbineNodeId.equals(nodeId);
        }
        return false;
    }

    public boolean isMachineConfigButtonGlowing(String nodeId) {
        return isNodeConfigButtonGlowing(nodeId);
    }

    public boolean isJunctionGlowing(String nodeId) {
        if (!active || stepActionCompleted || nodeId == null) return false;
        if (currentStep == TutorialStep.STEP_12_JUNCTION_SUPPLY) {
            com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
            if (tutPage != null) {
                RecipeNode junc = tutPage.getGraph().getNodes().stream().filter(RecipeNode::isReroute).findFirst().orElse(null);
                return junc != null && junc.getId().equals(nodeId);
            }
        }
        return false;
    }

    public boolean isFrameCollapseButtonGlowing(String frameId) {
        if (!active || stepActionCompleted) return false;
        return currentStep == TutorialStep.STEP_9_COMPOUND_MODULE;
    }

    public boolean isWireGlowing(String fromNodeId, String toNodeId) {
        if (!active || stepActionCompleted) return false;
        if (fastTrackActive && (fastTrackStepIndex == 2 || fastTrackStepIndex == 3)) {
            return true;
        }
        if ("ch2_wiring".equals(activeChapterId) && (activeChapterStepIndex == 0 || activeChapterStepIndex == 1)) {
            return true;
        }
        if (currentStep == TutorialStep.STEP_3_JUNCTION) {
            return (boilerNodeId != null && boilerNodeId.equals(fromNodeId))
                    || (turbineNodeId != null && turbineNodeId.equals(toNodeId));
        }
        if (currentStep == TutorialStep.STEP_4_SHIFT_WIRING) {
            return turbineNodeId != null && turbineNodeId.equals(toNodeId);
        }
        return false;
    }

    public boolean isPortGlowing(String nodeId, boolean isInput, int portIdx) {
        if (!active || stepActionCompleted || nodeId == null) return false;
        if (isFastTrackPortGlowing(nodeId, isInput, portIdx)) return true;
        if (isChapter1PortGlowing(nodeId, isInput, portIdx)) return true;
        if (isChapter2PortGlowing(nodeId, isInput, portIdx)) return true;
        if (isLegacyStep2PortGlowing(nodeId, isInput, portIdx)) return true;
        return isLegacyStep4PortGlowing(nodeId, isInput, portIdx);
    }

    private boolean isChapter1PortGlowing(String nodeId, boolean isInput, int portIdx) {
        if (!"ch1_solver".equals(activeChapterId) || activeChapterStepIndex != 3) return false;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null) return false;

        RecipeNode bath = findNodeByName(tutPage, "Chemical Bath");
        RecipeNode feed = findNodeByName(tutPage, "Acid Supply Tank");
        if (bath == null || feed == null) return false;

        boolean feedConnected = tutPage.getGraph().getConnections().stream()
                .anyMatch(c -> c.fromNodeId().equals(feed.getId()) && c.toNodeId().equals(bath.getId()));

        if (!feedConnected) {
            if (!isInput && portIdx == 0 && feed.getId().equals(nodeId)) return true;
            return isInput && portIdx == 0 && bath.getId().equals(nodeId);
        }
        return isInput && portIdx == 0 && bath.getId().equals(nodeId);
    }

    private RecipeNode findNodeByName(com.gtceu.calcboard.api.storage.BoardPage tutPage, String name) {
        return tutPage.getGraph().getNodes().stream()
                .filter(n -> !n.isReroute() && name.equals(n.getName()))
                .findFirst().orElse(null);
    }

    private boolean isFastTrackPortGlowing(String nodeId, boolean isInput, int portIdx) {
        return fastTrackActive && fastTrackStepIndex == 1 && !isInput && portIdx == 0 && nodeId.equals(boilerNodeId);
    }

    private boolean isChapter2PortGlowing(String nodeId, boolean isInput, int portIdx) {
        if (!"ch2_wiring".equals(activeChapterId)) return false;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        if (tutPage == null) return false;

        if (activeChapterStepIndex == 2 && !isInput && portIdx == 1) {
            RecipeNode separator = tutPage.getGraph().getNodes().stream()
                    .filter(n -> "Air Separator".equals(n.getName()))
                    .findFirst().orElse(null);
            return separator != null && separator.getId().equals(nodeId);
        }
        if (activeChapterStepIndex == 3 && isInput && portIdx == 0) {
            RecipeNode extruder = tutPage.getGraph().getNodes().stream()
                    .filter(n -> "Plate Extruder".equals(n.getName()))
                    .findFirst().orElse(null);
            return extruder != null && extruder.getId().equals(nodeId);
        }
        return false;
    }

    private boolean isLegacyStep2PortGlowing(String nodeId, boolean isInput, int portIdx) {
        return currentStep == TutorialStep.STEP_2_DRAG_TO_SEARCH && !isInput && portIdx == 0 && nodeId.equals(boilerNodeId);
    }

    private boolean isLegacyStep4PortGlowing(String nodeId, boolean isInput, int portIdx) {
        if (currentStep != TutorialStep.STEP_4_SHIFT_WIRING) return false;
        com.gtceu.calcboard.api.storage.BoardPage tutPage = getTutorialPage();
        RecipeNode junction = tutPage != null ? tutPage.getGraph().getNodes().stream().filter(RecipeNode::isReroute).findFirst().orElse(null) : null;
        String sourceId = junction != null ? junction.getId() : boilerNodeId;
        if (!isInput && portIdx == 0 && nodeId.equals(sourceId)) return true;
        return isInput && portIdx == 0 && nodeId.equals(turbineNodeId);
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



