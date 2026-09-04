package com.gtceu.calcboard.client.gui.tutorial;

public enum TutorialStep {
    STEP_1_ADD_RECIPE(1, "gui.gtcalcboard.tutorial.step1_title", "gui.gtcalcboard.tutorial.step1_desc"),
    STEP_2_DRAG_TO_SEARCH(2, "gui.gtcalcboard.tutorial.step2_title", "gui.gtcalcboard.tutorial.step2_desc"),
    STEP_3_JUNCTION(3, "gui.gtcalcboard.tutorial.step3_title", "gui.gtcalcboard.tutorial.step3_desc"),
    STEP_4_SHIFT_WIRING(4, "gui.gtcalcboard.tutorial.step4_title", "gui.gtcalcboard.tutorial.step4_desc"),
    STEP_5_JUNCTION_ETA(5, "gui.gtcalcboard.tutorial.step5_junction_title", "gui.gtcalcboard.tutorial.step5_junction_desc"),
    STEP_6_MACHINE_SELECTOR(6, "gui.gtcalcboard.tutorial.step5_selector_title", "gui.gtcalcboard.tutorial.step5_selector_desc"),
    STEP_7_MACHINE_CONFIG(7, "gui.gtcalcboard.tutorial.step5_title", "gui.gtcalcboard.tutorial.step5_desc"),
    STEP_8_GROUP_FRAME(8, "gui.gtcalcboard.tutorial.step6_title", "gui.gtcalcboard.tutorial.step6_desc"),
    STEP_9_COMPOUND_MODULE(9, "gui.gtcalcboard.tutorial.step7_title", "gui.gtcalcboard.tutorial.step7_desc"),
    STEP_10_SHARED_MACHINE(10, "gui.gtcalcboard.tutorial.step8_title", "gui.gtcalcboard.tutorial.step8_desc"),
    STEP_11_BOM_INSPECTION(11, "gui.gtcalcboard.tutorial.step9_title", "gui.gtcalcboard.tutorial.step9_desc"),
    STEP_12_JUNCTION_SUPPLY(12, "gui.gtcalcboard.tutorial.step10_title", "gui.gtcalcboard.tutorial.step10_desc"),
    STEP_13_FOLDER_BROWSER(13, "gui.gtcalcboard.tutorial.step11_title", "gui.gtcalcboard.tutorial.step11_desc"),
    COMPLETED(14, "gui.gtcalcboard.tutorial.completed_title", "gui.gtcalcboard.tutorial.completed_desc");

    private final int stepNumber;
    private final String titleKey;
    private final String descKey;

    TutorialStep(int stepNumber, String titleKey, String descKey) {
        this.stepNumber = stepNumber;
        this.titleKey = titleKey;
        this.descKey = descKey;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getDescKey() {
        return descKey;
    }
}
