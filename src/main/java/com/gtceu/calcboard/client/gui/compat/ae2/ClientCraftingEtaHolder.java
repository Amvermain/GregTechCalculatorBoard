package com.gtceu.calcboard.client.gui.compat.ae2;

import com.gtceu.calcboard.integration.ae2.model.Ae2PlanEvaluationResult;

/**
 * Client-side holder managing the most recently calculated AE2 autocrafting plan ETA and bottleneck details.
 */
public final class ClientCraftingEtaHolder {
    private static final ClientCraftingEtaHolder INSTANCE = new ClientCraftingEtaHolder();

    private volatile Ae2PlanEvaluationResult currentResult = Ae2PlanEvaluationResult.EMPTY;

    private ClientCraftingEtaHolder() {}

    public static ClientCraftingEtaHolder getInstance() {
        return INSTANCE;
    }

    public Ae2PlanEvaluationResult getCurrentResult() {
        return currentResult != null ? currentResult : Ae2PlanEvaluationResult.EMPTY;
    }

    public void setCurrentResult(Ae2PlanEvaluationResult result) {
        this.currentResult = result != null ? result : Ae2PlanEvaluationResult.EMPTY;
    }

    public void clear() {
        this.currentResult = Ae2PlanEvaluationResult.EMPTY;
    }
}
