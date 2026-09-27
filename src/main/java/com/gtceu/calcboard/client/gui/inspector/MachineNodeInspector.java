package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.inspector.section.OverclockModeSection;
import com.gtceu.calcboard.client.gui.inspector.section.SingleblockTierSection;

import java.util.List;

public class MachineNodeInspector extends CompositeNodeInspector {

    public MachineNodeInspector(IBoardScreenContext screen) {
        super(screen);
    }

    public boolean supportsVoltageTier(RecipeNode node) {
        return SingleblockTierSection.supportsVoltageTier(node);
    }

    public boolean supportsOverclockMode(RecipeNode node) {
        return OverclockModeSection.supportsOverclockMode(node);
    }

    public int getTierControlsHeight(RecipeNode node) {
        return SingleblockTierSection.getTierControlsHeight(node);
    }

    public List<GTVoltageTier> getInspectorTiers(RecipeNode node) {
        return SingleblockTierSection.getInspectorTiers(node);
    }
}
