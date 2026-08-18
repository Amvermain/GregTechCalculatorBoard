package com.gtceu.calcboard.client.gui.tutorial;

public enum TutorialStep {
    STEP_1_PAN_ZOOM(1, "gui.gtcalcboard.tutorial.step1_title", "gui.gtcalcboard.tutorial.step1_desc"),
    STEP_2_ADD_RECIPE(2, "gui.gtcalcboard.tutorial.step2_title", "gui.gtcalcboard.tutorial.step2_desc"),
    STEP_3_REMOVE_RECIPE(3, "gui.gtcalcboard.tutorial.step3_title", "gui.gtcalcboard.tutorial.step3_desc"),
    STEP_4_NORMAL_WIRING(4, "gui.gtcalcboard.tutorial.step4_title", "gui.gtcalcboard.tutorial.step4_desc"),
    STEP_5_SHIFT_WIRING(5, "gui.gtcalcboard.tutorial.step5_title", "gui.gtcalcboard.tutorial.step5_desc"),
    STEP_6_AUTO_RATIO(6, "gui.gtcalcboard.tutorial.step6_title", "gui.gtcalcboard.tutorial.step6_desc"),
    STEP_7_SUMMARY_MODULE(7, "gui.gtcalcboard.tutorial.step7_title", "gui.gtcalcboard.tutorial.step7_desc"),
    COMPLETED(8, "gui.gtcalcboard.tutorial.completed_title", "gui.gtcalcboard.tutorial.completed_desc");

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
