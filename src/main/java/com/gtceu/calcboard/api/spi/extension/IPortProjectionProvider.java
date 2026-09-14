package com.gtceu.calcboard.api.spi.extension;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.PortRole;
import com.gtceu.calcboard.api.model.ProjectedPort;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider interface for dynamic hardware port projections.
 * Synthesizes effective runtime ports from immutable base recipe specifications
 * and current node hardware configuration.
 */
public interface IPortProjectionProvider extends IModExtension {

    default List<ProjectedPort> projectInputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (baseSpec == null || baseSpec.baseInputs() == null) {
            return Collections.emptyList();
        }
        List<ProjectedPort> list = new ArrayList<>(baseSpec.baseInputs().size());
        for (int i = 0; i < baseSpec.baseInputs().size(); i++) {
            list.add(ProjectedPort.ofCore(baseSpec.baseInputs().get(i), i));
        }
        return Collections.unmodifiableList(list);
    }

    default List<ProjectedPort> projectOutputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (baseSpec == null || baseSpec.baseOutputs() == null) {
            return Collections.emptyList();
        }
        List<ProjectedPort> list = new ArrayList<>(baseSpec.baseOutputs().size());
        for (int i = 0; i < baseSpec.baseOutputs().size(); i++) {
            list.add(ProjectedPort.ofCore(baseSpec.baseOutputs().get(i), i));
        }
        return Collections.unmodifiableList(list);
    }

    default List<IngredientStack> sanitizeLegacyCoreInputs(RecipeNode node, List<IngredientStack> savedInputs) {
        if (savedInputs == null) return Collections.emptyList();
        List<IngredientStack> copy = new ArrayList<>(savedInputs.size());
        for (IngredientStack stack : savedInputs) {
            copy.add(stack.copy());
        }
        return copy;
    }
}
