package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class NativeCatalogSearchHelper {

    private NativeCatalogSearchHelper() {}

    public static SearchableRecipe createRecipe(
            RecipeNode templateNode,
            ResourceLocation itemId,
            String categoryId,
            String categoryName,
            Supplier<RecipeNode> nodeSupplier
    ) {
        String displayName = templateNode.getName();
        String modId = itemId != null ? itemId.getNamespace() : "minecraft";

        List<String> inputNames = new ArrayList<>();
        List<ResourceLocation> inputIds = new ArrayList<>();
        populateStackMetadata(templateNode.getInputs(), inputNames, inputIds);

        List<String> outputNames = new ArrayList<>();
        List<ResourceLocation> outputIds = new ArrayList<>();
        populateStackMetadata(templateNode.getOutputs(), outputNames, outputIds);

        if (itemId != null) {
            outputIds.add(itemId);
            outputNames.add(displayName.toLowerCase(Locale.ROOT));
        }

        String inIndex = buildSearchIndex(inputNames, inputIds, displayName, templateNode);
        String outIndex = buildSearchIndex(outputNames, outputIds, displayName, templateNode);

        ResourceLocation recipeId = ResourceLocation.tryParse(
                "gtcalcboard:native/" + (itemId != null ? itemId.getNamespace() + "/" + itemId.getPath() : "node")
        );

        return new SearchableRecipe(
                nodeSupplier,
                recipeId,
                displayName,
                modId.intern(),
                categoryId != null ? categoryId.intern() : "",
                categoryName != null ? categoryName.intern() : "",
                inIndex,
                outIndex,
                inputIds.toArray(new ResourceLocation[0]),
                outputIds.toArray(new ResourceLocation[0]),
                inputNames.toArray(new String[0]),
                outputNames.toArray(new String[0]),
                true
        );
    }

    private static void populateStackMetadata(
            List<IngredientStack> stacks,
            List<String> names,
            List<ResourceLocation> ids
    ) {
        for (IngredientStack stack : stacks) {
            if (stack == null) continue;
            if (stack.getId() != null) ids.add(stack.getId());
            if (stack.getDisplayName() != null && !stack.getDisplayName().isEmpty()) {
                names.add(stack.getDisplayName().toLowerCase(Locale.ROOT));
            }
            if (stack.isStressUnit()) {
                names.add("su");
                names.add("stress");
                names.add("units");
                names.add("kinetic");
            }
        }
    }

    private static String buildSearchIndex(
            List<String> names,
            List<ResourceLocation> ids,
            String displayName,
            RecipeNode node
    ) {
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            sb.append(name).append(' ');
        }
        for (ResourceLocation id : ids) {
            sb.append(id.toString().toLowerCase(Locale.ROOT)).append(' ');
            sb.append(id.getPath().toLowerCase(Locale.ROOT)).append(' ');
        }
        sb.append(displayName.toLowerCase(Locale.ROOT)).append(' ');
        if (node.isGenerator()) {
            sb.append("generator generator_node ");
        }
        if (node.getEnergyType() != null) {
            sb.append(node.getEnergyType().name().toLowerCase(Locale.ROOT)).append(' ');
        }
        return sb.toString().trim();
    }
}
