package com.gtceu.calcboard.api.type;

/**
 * Defines the flow splitting behavior for outgoing lines on a Junction/Reroute node.
 */
public enum FlowSplitMode {
    PROPORTIONAL("gui.gtcalcboard.junction.split_mode.proportional"),
    EQUAL("gui.gtcalcboard.junction.split_mode.equal"),
    WEIGHTED("gui.gtcalcboard.junction.split_mode.weighted");

    private final String translationKey;

    FlowSplitMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
