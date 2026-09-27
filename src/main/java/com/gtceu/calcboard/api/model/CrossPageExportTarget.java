package com.gtceu.calcboard.api.model;

/**
 * Immutable record specifying a cross-page flow export target for a junction node.
 * Defines target page identifier, allocation priority, and optional flow cap.
 */
public record CrossPageExportTarget(
    String targetPageId,
    int priority,
    double fixedLimit
) {
    public CrossPageExportTarget {
        if (targetPageId == null || targetPageId.isBlank()) {
            throw new IllegalArgumentException("targetPageId cannot be blank");
        }
        priority = Math.max(0, Math.min(99, priority));
        fixedLimit = Double.isFinite(fixedLimit) ? Math.max(0.0, fixedLimit) : 0.0;
    }

    public CrossPageExportTarget(String targetPageId) {
        this(targetPageId, 0, 0.0);
    }

    public CrossPageExportTarget(String targetPageId, int priority) {
        this(targetPageId, priority, 0.0);
    }

    public boolean hasFixedLimit() {
        return fixedLimit > 0.0001;
    }

    public boolean hasLimit() {
        return hasFixedLimit();
    }

    public CrossPageExportTarget withPriority(int newPriority) {
        return new CrossPageExportTarget(targetPageId, newPriority, fixedLimit);
    }

    public CrossPageExportTarget withFixedLimit(double newLimit) {
        return new CrossPageExportTarget(targetPageId, priority, newLimit);
    }
}
