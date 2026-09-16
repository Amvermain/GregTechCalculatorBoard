package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphModuleHandler;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.PageType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.tutorial.model.ITutorialChapter;
import com.gtceu.calcboard.client.gui.tutorial.model.TutorialChapterStepDef;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Registry and coordinator for modular academy chapters and fast-track onboarding steps.
 */
public class TutorialTrackRegistry {
    private static final TutorialTrackRegistry INSTANCE = new TutorialTrackRegistry();

    public static TutorialTrackRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<String, ITutorialChapter> chapters = new LinkedHashMap<>();
    private final Map<String, List<TutorialChapterStepDef>> chapterStepDefs = new LinkedHashMap<>();
    private final List<TutorialChapterStepDef> fastTrackSteps = new ArrayList<>();

    private TutorialTrackRegistry() {
        initFastTrack();
        initChapter1();
        initChapter2();
        initChapter3();
        initChapter4();
    }

    public List<ITutorialChapter> getChapters() {
        return new ArrayList<>(chapters.values());
    }

    public ITutorialChapter getChapter(String chapterId) {
        return chapters.get(chapterId);
    }

    public List<TutorialChapterStepDef> getChapterSteps(String chapterId) {
        return chapterStepDefs.getOrDefault(chapterId, Collections.emptyList());
    }

    public List<TutorialChapterStepDef> getFastTrackSteps() {
        return Collections.unmodifiableList(fastTrackSteps);
    }

    private void initFastTrack() {
        fastTrackSteps.add(new TutorialChapterStepDef(1,
                "gui.gtcalcboard.tutorial.fast_track.step1_title",
                "gui.gtcalcboard.tutorial.fast_track.step1_desc",
                "gui.gtcalcboard.tutorial.fast_track.step1_result",
                tutPage -> tutPage.getGraph().clear()));

        fastTrackSteps.add(new TutorialChapterStepDef(2,
                "gui.gtcalcboard.tutorial.fast_track.step2_title",
                "gui.gtcalcboard.tutorial.fast_track.step2_desc",
                "gui.gtcalcboard.tutorial.fast_track.step2_result",
                this::setupFastTrackStep2));

        fastTrackSteps.add(new TutorialChapterStepDef(3,
                "gui.gtcalcboard.tutorial.fast_track.step3_title",
                "gui.gtcalcboard.tutorial.fast_track.step3_desc",
                "gui.gtcalcboard.tutorial.fast_track.step3_result",
                this::setupFastTrackStep3));

        fastTrackSteps.add(new TutorialChapterStepDef(4,
                "gui.gtcalcboard.tutorial.fast_track.step4_title",
                "gui.gtcalcboard.tutorial.fast_track.step4_desc",
                "gui.gtcalcboard.tutorial.fast_track.step4_result",
                this::setupFastTrackStep4));
    }

    private void setupFastTrackStep2(BoardPage page) {
        RecipeNode existingBoiler = page.getGraph().getNodes().stream()
                .filter(n -> !n.isReroute() && n.getOutputs().stream().anyMatch(o -> o.isFluid() && "Steam".equalsIgnoreCase(o.getDisplayName())))
                .findFirst()
                .orElse(null);
        if (existingBoiler != null) {
            TutorialManager.getInstance().setBoilerNodeId(existingBoiler.getId());
            return;
        }

        page.getGraph().clear();
        RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 0.0, GTVoltageTier.LV);
        boiler.setEnergyType(com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF);
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        boiler.setPosX(-160);
        boiler.setPosY(-40);
        boiler.setMachineCount(1.0);
        page.getGraph().addNode(boiler);
        TutorialManager.getInstance().setBoilerNodeId(boiler.getId());
    }

    private void setupFastTrackStep3(BoardPage page) {
        String bId = TutorialManager.getInstance().getBoilerNodeId();
        String tId = TutorialManager.getInstance().getTurbineNodeId();
        RecipeNode existingBoiler = (bId != null) ? page.getGraph().findNodeById(bId) : null;
        RecipeNode existingTurbine = (tId != null) ? page.getGraph().findNodeById(tId) : null;
        if (existingBoiler != null && existingTurbine != null) {
            final String bFinalId = existingBoiler.getId();
            final String tFinalId = existingTurbine.getId();
            boolean hasConn = page.getGraph().getConnections().stream()
                    .anyMatch(c -> c.fromNodeId().equals(bFinalId) && c.toNodeId().equals(tFinalId));
            if (hasConn) {
                return;
            }
        }

        page.getGraph().clear();
        RecipeNode boiler = RecipeNode.create("Boiler (Tutorial)", 20.0, 0.0, GTVoltageTier.LV);
        boiler.setEnergyType(com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF);
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        boiler.setPosX(-160);
        boiler.setPosY(-40);
        boiler.setMachineCount(1.0);

        RecipeNode turbine = RecipeNode.create("Steam Turbine (Tutorial)", 20.0, 64.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0, 1.0));
        turbine.setPosX(100);
        turbine.setPosY(-40);
        turbine.setMachineCount(1.0);

        page.getGraph().addNode(boiler);
        page.getGraph().addNode(turbine);
        page.getGraph().addConnection(boiler.getId(), 0, turbine.getId(), 0);
        TutorialManager.getInstance().setBoilerNodeId(boiler.getId());
        TutorialManager.getInstance().setTurbineNodeId(turbine.getId());
    }

    private void setupFastTrackStep4(BoardPage page) {
        String tId = TutorialManager.getInstance().getTurbineNodeId();
        RecipeNode turbine = (tId != null) ? page.getGraph().findNodeById(tId) : null;
        if (turbine != null && Math.abs(turbine.getMachineCount() - 5.0) < 0.001) {
            return;
        }

        setupFastTrackStep3(page);
        turbine = page.getGraph().getNodes().stream()
                .filter(n -> !n.isReroute() && n.isGenerator())
                .findFirst()
                .orElse(null);
        if (turbine != null) {
            turbine.setMachineCount(5.0);
            TutorialManager.getInstance().setTurbineNodeId(turbine.getId());
        }
    }

    private void initChapter1() {
        String id = "ch1_solver";
        List<TutorialChapterStepDef> steps = List.of(
                new TutorialChapterStepDef(1,
                        "gui.gtcalcboard.tutorial.chapter.ch1.step1_title",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step1_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step1_result",
                        this::setupCh1Step1),
                new TutorialChapterStepDef(2,
                        "gui.gtcalcboard.tutorial.chapter.ch1.step2_title",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step2_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step2_result",
                        this::setupCh1Step2),
                new TutorialChapterStepDef(3,
                        "gui.gtcalcboard.tutorial.chapter.ch1.step3_title",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step3_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step3_result",
                        this::setupCh1Step3),
                new TutorialChapterStepDef(4,
                        "gui.gtcalcboard.tutorial.chapter.ch1.step4_title",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step4_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch1.step4_result",
                        this::setupCh1Step4)
        );
        chapterStepDefs.put(id, steps);
        chapters.put(id, new SimpleChapter(id,
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch1.title"),
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch1.desc"),
                ResourceLocation.tryParse("gtceu:textures/gui/icon/calculator.png"),
                List.of(TutorialStep.STEP_4_SHIFT_WIRING, TutorialStep.STEP_5_JUNCTION_ETA, TutorialStep.STEP_7_MACHINE_CONFIG),
                steps.get(0).setupAction()));
    }

    private void setupCh1Step1(BoardPage page) {
        page.getGraph().clear();
        RecipeNode oreSmelter = RecipeNode.create("Iron Smelter", 20.0, 32.0, GTVoltageTier.LV);
        oreSmelter.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0));
        oreSmelter.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        oreSmelter.setMachineCount(1.0);
        oreSmelter.setPos(-160, -40);

        RecipeNode plateBender = RecipeNode.create("Plate Bender", 20.0, 32.0, GTVoltageTier.LV);
        plateBender.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        plateBender.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_plate"), "Iron Plate", 1.0));
        plateBender.setMachineCount(2.0);
        plateBender.setPos(120, -40);

        page.getGraph().addNode(oreSmelter);
        page.getGraph().addNode(plateBender);
        page.getGraph().addConnection(oreSmelter.getId(), 0, plateBender.getId(), 0);
    }

    private void setupCh1Step2(BoardPage page) {
        RecipeNode plateBender = page.getGraph().getNodes().stream()
                .filter(n -> !n.isReroute() && "Plate Bender".equals(n.getName()))
                .findFirst()
                .orElse(null);
        if (plateBender != null) {
            plateBender.setBaseNode(true);
            return;
        }

        setupCh1Step1(page);
        plateBender = page.getGraph().getNodes().stream()
                .filter(n -> !n.isReroute() && "Plate Bender".equals(n.getName()))
                .findFirst()
                .orElse(null);
        if (plateBender != null) {
            plateBender.setBaseNode(true);
        }
    }

    private void setupCh1Step3(BoardPage page) {
        page.getGraph().clear();
        RecipeNode wiremill = RecipeNode.create("Wire Mill", 40.0, 30.0, GTVoltageTier.LV);
        wiremill.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));
        wiremill.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_single_wire"), "Copper Wire", 2.0));
        wiremill.setMachineCount(2.0);
        wiremill.setPos(-160, -40);

        RecipeNode assembler = RecipeNode.create("Cable Assembler", 40.0, 30.0, GTVoltageTier.LV);
        assembler.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_single_wire"), "Copper Wire", 3.0));
        assembler.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_single_cable"), "Copper Cable", 1.0));
        assembler.setMachineCount(1.0);
        assembler.setBaseNode(true);
        assembler.setPos(120, -40);

        page.getGraph().addNode(wiremill);
        page.getGraph().addNode(assembler);
        page.getGraph().addConnection(wiremill.getId(), 0, assembler.getId(), 0);
    }

    private void setupCh1Step4(BoardPage page) {
        page.getGraph().clear();
        RecipeNode feed = RecipeNode.create("Acid Supply Tank", 20.0, 30.0, GTVoltageTier.LV);
        feed.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 10.0, 1.0));
        feed.setMachineCount(1.0);
        feed.setPos(-240, 40);

        RecipeNode bath = RecipeNode.create("Chemical Bath", 20.0, 30.0, GTVoltageTier.LV);
        bath.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 100.0, 1.0));
        bath.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:raw_iron"), "Raw Iron", 1.0));
        bath.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:iron_slurry"), "Iron Slurry", 100.0, 1.0));
        bath.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:purified_iron_ore"), "Purified Iron", 1.0));
        bath.setMachineCount(1.0);
        bath.setPos(-20, -70);

        RecipeNode centrifuge = RecipeNode.create("Acid Centrifuge", 20.0, 30.0, GTVoltageTier.LV);
        centrifuge.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:iron_slurry"), "Iron Slurry", 100.0, 1.0));
        centrifuge.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 80.0, 1.0));
        centrifuge.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_dust"), "Iron Dust", 1.0));
        centrifuge.setMachineCount(1.0);
        centrifuge.setPos(160, 40);

        page.getGraph().addNode(feed);
        page.getGraph().addNode(bath);
        page.getGraph().addNode(centrifuge);

        page.getGraph().addConnection(bath.getId(), 0, centrifuge.getId(), 0);
        page.getGraph().addConnection(centrifuge.getId(), 0, bath.getId(), 0);
    }

    private void initChapter2() {
        String id = "ch2_wiring";
        List<TutorialChapterStepDef> steps = List.of(
                new TutorialChapterStepDef(1,
                        "gui.gtcalcboard.tutorial.chapter.ch2.step1_title",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step1_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step1_result",
                        this::setupCh2Step1),
                new TutorialChapterStepDef(2,
                        "gui.gtcalcboard.tutorial.chapter.ch2.step2_title",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step2_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step2_result",
                        this::setupCh2Step2),
                new TutorialChapterStepDef(3,
                        "gui.gtcalcboard.tutorial.chapter.ch2.step3_title",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step3_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step3_result",
                        this::setupCh2Step3),
                new TutorialChapterStepDef(4,
                        "gui.gtcalcboard.tutorial.chapter.ch2.step4_title",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step4_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch2.step4_result",
                        this::setupCh2Step4)
        );
        chapterStepDefs.put(id, steps);
        chapters.put(id, new SimpleChapter(id,
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch2.title"),
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch2.desc"),
                ResourceLocation.tryParse("gtceu:textures/gui/icon/wiring.png"),
                List.of(TutorialStep.STEP_3_JUNCTION, TutorialStep.STEP_4_SHIFT_WIRING, TutorialStep.STEP_12_JUNCTION_SUPPLY),
                steps.get(0).setupAction()));
    }

    private void setupCh2Step1(BoardPage page) {
        page.getGraph().clear();
        RecipeNode pump = RecipeNode.create("Primary Pump", 20.0, 16.0, GTVoltageTier.LV);
        pump.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0, 1.0));
        pump.setPos(-240, -40);

        RecipeNode consumer = RecipeNode.create("Main Consumer", 20.0, 16.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0, 1.0));
        consumer.setPos(200, -40);

        page.getGraph().addNode(pump);
        page.getGraph().addNode(consumer);
        page.getGraph().addConnection(pump.getId(), 0, consumer.getId(), 0);
    }

    private void setupCh2Step2(BoardPage page) {
        page.getGraph().clear();
        RecipeNode pump = RecipeNode.create("Primary Pump", 20.0, 16.0, GTVoltageTier.LV);
        pump.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0, 1.0));
        pump.setPos(-200, -40);

        RecipeNode junc = RecipeNode.createReroute(0, -40);
        junc.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0, 1.0));

        RecipeNode lineA = RecipeNode.create("Line A (Priority)", 20.0, 16.0, GTVoltageTier.LV);
        lineA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 600.0, 1.0));
        lineA.setPos(200, -90);

        RecipeNode lineB = RecipeNode.create("Line B (Surplus)", 20.0, 16.0, GTVoltageTier.LV);
        lineB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 600.0, 1.0));
        lineB.setPos(200, 30);

        page.getGraph().addNode(pump);
        page.getGraph().addNode(junc);
        page.getGraph().addNode(lineA);
        page.getGraph().addNode(lineB);

        page.getGraph().addConnection(pump.getId(), 0, junc.getId(), 0);
        page.getGraph().addConnection(junc.getId(), 0, lineA.getId(), 0);
        page.getGraph().addConnection(junc.getId(), 0, lineB.getId(), 0);
    }

    private void setupCh2Step3(BoardPage page) {
        page.getGraph().clear();
        RecipeNode centrifuge = RecipeNode.create("Air Separator", 20.0, 30.0, GTVoltageTier.LV);
        centrifuge.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:air"), "Air", 200.0, 1.0));
        centrifuge.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrogen"), "Nitrogen", 150.0, 1.0));
        centrifuge.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 50.0, 1.0));
        centrifuge.setPos(-160, -40);

        RecipeNode consumer = RecipeNode.create("Nitrogen Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrogen"), "Nitrogen", 150.0, 1.0));
        consumer.setPos(140, -40);

        page.getGraph().addNode(centrifuge);
        page.getGraph().addNode(consumer);
        page.getGraph().addConnection(centrifuge.getId(), 0, consumer.getId(), 0);
    }

    private void setupCh2Step4(BoardPage page) {
        page.getGraph().clear();
        RecipeNode extruder = RecipeNode.create("Plate Extruder", 20.0, 30.0, GTVoltageTier.LV);
        IngredientStack ing = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0);
        ing.addAlternative(ResourceLocation.tryParse("gtceu:aluminium_ingot"));
        ing.addAlternative(ResourceLocation.tryParse("minecraft:copper_ingot"));
        extruder.addInput(ing);
        extruder.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_plate"), "Plate", 1.0));
        extruder.setPos(0, -40);

        page.getGraph().addNode(extruder);
    }

    private void initChapter3() {
        String id = "ch3_packaging";
        List<TutorialChapterStepDef> steps = List.of(
                new TutorialChapterStepDef(1,
                        "gui.gtcalcboard.tutorial.chapter.ch3.step1_title",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step1_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step1_result",
                        this::setupCh3Step1),
                new TutorialChapterStepDef(2,
                        "gui.gtcalcboard.tutorial.chapter.ch3.step2_title",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step2_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step2_result",
                        this::setupCh3Step2),
                new TutorialChapterStepDef(3,
                        "gui.gtcalcboard.tutorial.chapter.ch3.step3_title",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step3_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step3_result",
                        this::setupCh3Step3),
                new TutorialChapterStepDef(4,
                        "gui.gtcalcboard.tutorial.chapter.ch3.step4_title",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step4_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch3.step4_result",
                        this::setupCh3Step4)
        );
        chapterStepDefs.put(id, steps);
        chapters.put(id, new SimpleChapter(id,
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch3.title"),
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch3.desc"),
                ResourceLocation.tryParse("gtceu:textures/gui/icon/packaging.png"),
                List.of(TutorialStep.STEP_10_SHARED_MACHINE, TutorialStep.STEP_9_COMPOUND_MODULE, TutorialStep.STEP_11_BOM_INSPECTION),
                steps.get(0).setupAction()));
    }

    private void setupCh3Step1(BoardPage page) {
        page.getGraph().clear();
        ResourceLocation icon = ResourceLocation.tryParse("gtceu:lv_cutter");
        RecipeNode c1 = RecipeNode.create(icon, "Cutter 1", 20.0, 30.0, GTVoltageTier.LV);
        c1.setMachineCount(0.15);
        c1.setPos(-240, -40);

        RecipeNode c2 = RecipeNode.create(icon, "Cutter 2", 20.0, 30.0, GTVoltageTier.LV);
        c2.setMachineCount(0.20);
        c2.setPos(0, -40);

        RecipeNode c3 = RecipeNode.create(icon, "Cutter 3", 20.0, 30.0, GTVoltageTier.LV);
        c3.setMachineCount(0.10);
        c3.setPos(240, -40);

        page.getGraph().addNode(c1);
        page.getGraph().addNode(c2);
        page.getGraph().addNode(c3);
    }

    private void setupCh3Step2(BoardPage page) {
        if (!page.getGraph().getFrames().isEmpty()) {
            return;
        }

        page.getGraph().clear();
        RecipeNode n1 = RecipeNode.create("Module Step A", 20.0, 30.0, GTVoltageTier.LV);
        n1.setPos(-120, -40);
        RecipeNode n2 = RecipeNode.create("Module Step B", 20.0, 30.0, GTVoltageTier.LV);
        n2.setPos(120, -40);

        page.getGraph().addNode(n1);
        page.getGraph().addNode(n2);
        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Factory Block", List.of(n1, n2), CanvasGroupFrame.COLOR_BLUE);
        page.getGraph().addFrame(frame);
    }

    private void setupCh3Step3(BoardPage page) {
        page.getGraph().clear();
        RecipeNode subA = RecipeNode.create("Sub Inner A", 20.0, 30.0, GTVoltageTier.LV);
        subA.setPos(-100, -30);
        RecipeNode subB = RecipeNode.create("Sub Inner B", 20.0, 30.0, GTVoltageTier.LV);
        subB.setPos(100, -30);

        BoardPage subPage = BoardManager.getInstance().getPages().stream()
                .filter(p -> p.isModuleSubPage() && page.getId().equals(p.getParentPageId()))
                .findFirst()
                .orElse(null);
        if (subPage == null) {
            subPage = BoardManager.getInstance().addPage("Sub Module Page");
            subPage.setPageType(PageType.MODULE);
            subPage.setParentPageId(page.getId());
        } else {
            subPage.getGraph().clear();
        }
        subPage.getGraph().addNode(subA);
        subPage.getGraph().addNode(subB);

        RecipeNode moduleNode = RecipeNode.create("Compound Module Card", 20.0, 30.0, GTVoltageTier.LV);
        moduleNode.setModule(true);
        moduleNode.setSubPageId(subPage.getId());
        moduleNode.setPos(0, 0);
        moduleNode.setCardWidth(230);
        subPage.setParentModuleNodeId(moduleNode.getId());
        page.getGraph().addNode(moduleNode);
    }

    private void setupCh3Step4(BoardPage page) {
        page.getGraph().clear();
        RecipeNode furnace = RecipeNode.create(ResourceLocation.tryParse("gtceu:electric_blast_furnace"), "Electric Blast Furnace", 200.0, 120.0, GTVoltageTier.LV);
        furnace.setMultiblock(true);
        furnace.setMachineCount(1.0);
        furnace.setPos(0, -40);
        page.getGraph().addNode(furnace);
    }

    private void initChapter4() {
        String id = "ch4_workspace";
        List<TutorialChapterStepDef> steps = List.of(
                new TutorialChapterStepDef(1,
                        "gui.gtcalcboard.tutorial.chapter.ch4.step1_title",
                        "gui.gtcalcboard.tutorial.chapter.ch4.step1_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch4.step1_result",
                        this::setupCh4Step1),
                new TutorialChapterStepDef(2,
                        "gui.gtcalcboard.tutorial.chapter.ch4.step2_title",
                        "gui.gtcalcboard.tutorial.chapter.ch4.step2_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch4.step2_result",
                        this::setupCh4Step2),
                new TutorialChapterStepDef(3,
                        "gui.gtcalcboard.tutorial.chapter.ch4.step3_title",
                        "gui.gtcalcboard.tutorial.chapter.ch4.step3_desc",
                        "gui.gtcalcboard.tutorial.chapter.ch4.step3_result",
                        this::setupCh4Step3)
        );
        chapterStepDefs.put(id, steps);
        chapters.put(id, new SimpleChapter(id,
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch4.title"),
                Component.translatable("gui.gtcalcboard.tutorial.chapter.ch4.desc"),
                ResourceLocation.tryParse("gtceu:textures/gui/icon/workspace.png"),
                List.of(TutorialStep.STEP_13_FOLDER_BROWSER),
                steps.get(0).setupAction()));
    }

    private void setupCh4Step1(BoardPage page) {
        page.getGraph().clear();
        page.setDefaultVoltageTier(GTVoltageTier.LV);
        RecipeNode m = RecipeNode.create("LV Wiremill", 20.0, 30.0, GTVoltageTier.LV);
        m.setPos(0, -40);
        page.getGraph().addNode(m);
    }

    private void setupCh4Step2(BoardPage page) {
        page.setFolderPath("Factory/Refining");
    }

    private void setupCh4Step3(BoardPage page) {
        page.getGraph().clear();
        RecipeNode gen = RecipeNode.create("Page A Generator", 20.0, 100.0, GTVoltageTier.LV);
        gen.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        gen.setPos(0, -40);
        page.getGraph().addNode(gen);
    }

    private record SimpleChapter(
            String id,
            Component title,
            Component description,
            ResourceLocation icon,
            List<TutorialStep> steps,
            java.util.function.Consumer<BoardPage> enterAction
    ) implements ITutorialChapter {
        @Override
        public String getChapterId() {
            return id;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public Component getDescription() {
            return description;
        }

        @Override
        public ResourceLocation getIconTexture() {
            return icon;
        }

        @Override
        public List<TutorialStep> getSteps() {
            return steps;
        }

        @Override
        public void onChapterEnter(BoardPage tutorialPage) {
            if (enterAction != null && tutorialPage != null) {
                enterAction.accept(tutorialPage);
            }
        }
    }
}
