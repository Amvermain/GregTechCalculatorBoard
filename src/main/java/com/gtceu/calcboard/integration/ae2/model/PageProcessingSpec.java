package com.gtceu.calcboard.integration.ae2.model;

/**
 * Encapsulates the evaluated processing specification of a calculator board page.
 */
public record PageProcessingSpec(
        double unitDurationTicks,
        int effectiveParallel,
        String pageId,
        String pageName,
        String primaryOutputName
) {
    public static final PageProcessingSpec DEFAULT_FALLBACK = new PageProcessingSpec(
            20.0,
            1,
            "",
            "Standard Crafting",
            "Item"
    );

    public PageProcessingSpec {
        unitDurationTicks = Math.max(1.0, unitDurationTicks);
        effectiveParallel = Math.max(1, effectiveParallel);
        pageId = pageId != null ? pageId : "";
        pageName = pageName != null ? pageName : "Page";
        primaryOutputName = primaryOutputName != null ? primaryOutputName : "Output";
    }
}
