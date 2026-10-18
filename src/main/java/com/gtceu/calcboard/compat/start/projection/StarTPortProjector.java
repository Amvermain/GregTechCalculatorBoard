package com.gtceu.calcboard.compat.start.projection;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.ProjectedPort;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.spi.extension.IPortProjectionProvider;
import com.gtceu.calcboard.compat.gtceu.projection.GTCEuPortProjector;
import com.gtceu.calcboard.compat.start.StarTBacterialProperties;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dynamic port projection provider for Star Technology machines, scaling Bacterial Hydrocarbon Harvester
 * input and output ports according to colony production and metabolism stats.
 */
public final class StarTPortProjector implements IPortProjectionProvider {

    private static final StarTPortProjector INSTANCE = new StarTPortProjector();

    private static final ResourceLocation SUGAR_ID = ResourceLocation.tryParse("minecraft:sugar");
    private static final ResourceLocation BIOMASS_ID = ResourceLocation.tryParse("gtceu:biomass");

    private StarTPortProjector() {}

    public static StarTPortProjector getInstance() {
        return INSTANCE;
    }

    public static boolean isBacterialHarvester(RecipeNode node) {
        return com.gtceu.calcboard.compat.start.StarTReflectionBridge.isBacterialHarvester(node);
    }

    @Override
    public List<ProjectedPort> projectInputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (!isBacterialHarvester(node)) {
            return GTCEuPortProjector.getInstance().projectInputPorts(node, baseSpec);
        }

        List<IngredientStack> source = resolveBaseInputs(node, baseSpec);
        if (source.isEmpty()) return Collections.emptyList();

        int metabolism = StarTBacterialProperties.getMetabolism(node);
        List<ProjectedPort> projected = new ArrayList<>(source.size());

        for (int i = 0; i < source.size(); i++) {
            IngredientStack original = source.get(i);
            IngredientStack scaled = scaleHarvesterInput(original, metabolism);
            projected.add(ProjectedPort.ofCore(scaled, i));
        }

        return Collections.unmodifiableList(projected);
    }

    @Override
    public List<ProjectedPort> projectOutputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (!isBacterialHarvester(node)) {
            return GTCEuPortProjector.getInstance().projectOutputPorts(node, baseSpec);
        }

        List<IngredientStack> source = resolveBaseOutputs(node, baseSpec);
        if (source.isEmpty()) return Collections.emptyList();

        int production = StarTBacterialProperties.getProduction(node);
        List<ProjectedPort> projected = new ArrayList<>(source.size());

        for (int i = 0; i < source.size(); i++) {
            IngredientStack original = source.get(i);
            IngredientStack scaled = scaleHarvesterOutput(original, production);
            projected.add(ProjectedPort.ofCore(scaled, i));
        }

        return Collections.unmodifiableList(projected);
    }

    private static List<IngredientStack> resolveBaseInputs(RecipeNode node, RecipeSpec baseSpec) {
        if (baseSpec != null && baseSpec.baseInputs() != null && !baseSpec.baseInputs().isEmpty()) {
            return baseSpec.baseInputs();
        }
        return node != null ? node.getInputs() : Collections.emptyList();
    }

    private static List<IngredientStack> resolveBaseOutputs(RecipeNode node, RecipeSpec baseSpec) {
        if (baseSpec != null && baseSpec.baseOutputs() != null && !baseSpec.baseOutputs().isEmpty()) {
            return baseSpec.baseOutputs();
        }
        return node != null ? node.getOutputs() : Collections.emptyList();
    }

    private static IngredientStack scaleHarvesterInput(IngredientStack original, int metabolism) {
        if (original == null) return null;

        if (isSugar(original)) {
            return original.withAmount(1 << metabolism);
        }
        if (isBiomass(original)) {
            return original.withAmount(100 << metabolism);
        }
        return original.copy();
    }

    private static IngredientStack scaleHarvesterOutput(IngredientStack original, int production) {
        if (original == null) return null;
        double baseUnit = original.getAmount() / 5.0;
        return original.withAmount(baseUnit * production);
    }

    private static boolean isSugar(IngredientStack stack) {
        if (stack == null || stack.getType() != IngredientStack.Type.ITEM) return false;
        ResourceLocation id = stack.getId();
        return id != null && id.equals(SUGAR_ID);
    }

    private static boolean isBiomass(IngredientStack stack) {
        if (stack == null || stack.getType() != IngredientStack.Type.FLUID) return false;
        ResourceLocation id = stack.getId();
        return id != null && id.equals(BIOMASS_ID);
    }
}
