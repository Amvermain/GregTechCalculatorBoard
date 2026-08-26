package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Synthetic EMI recipe representing the full Multiblock Construction BOM.
 * Directly contains all unique items and blocks from the BOM table with exact quantities.
 */
public class MultiblockBOMEmiRecipe implements EmiRecipe {

    private final ResourceLocation id;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;
    private final String customName;

    public MultiblockBOMEmiRecipe(ResourceLocation id, String customName, List<EmiIngredient> inputs, List<EmiStack> outputs) {
        this.id = id;
        this.customName = customName;
        this.inputs = inputs != null ? inputs : List.of();
        this.outputs = outputs != null ? outputs : List.of();
    }

    /**
     * Creates the top-level Project BOM Root recipe containing all items and blocks from the BOM table.
     */
    public static MultiblockBOMEmiRecipe createProjectRootRecipe(MultiblockBOMSummary summary) {
        List<EmiIngredient> inputs = new ArrayList<>();
        List<EmiStack> projectOutputs = new ArrayList<>();

        if (summary != null) {
            for (MultiblockBOMSummary.BOMItemEntry item : summary.aggregatedItems()) {
                ItemStack is = item.resolveItemStack();
                if (!is.isEmpty()) {
                    inputs.add(EmiStack.of(is, item.totalAmount()));
                }
            }

            ItemStack projectStack = new ItemStack(Items.COMPASS);
            projectStack.setHoverName(Component.literal("§6Factory Multiblock BOM (" + summary.totalMultiblockCount() + " Multiblocks, " + summary.totalUniqueItemTypes() + " Items)"));
            projectOutputs.add(EmiStack.of(projectStack, 1));
        }

        return new MultiblockBOMEmiRecipe(
            ResourceLocation.tryParse("gtcalcboard:factory_bom_project"),
            "Factory Multiblock BOM Project",
            inputs,
            projectOutputs
        );
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return VanillaEmiRecipeCategories.CRAFTING;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    @Override
    public int getDisplayWidth() {
        return 160;
    }

    @Override
    public int getDisplayHeight() {
        return 120;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int x = 0;
        int y = 0;
        for (EmiIngredient input : inputs) {
            widgets.addSlot(input, x, y);
            x += 18;
            if (x > 140) {
                x = 0;
                y += 18;
            }
        }
    }

    @Override
    public boolean supportsRecipeTree() {
        return true;
    }

    @Override
    public boolean hideCraftable() {
        return false;
    }

    @Override
    public @Nullable Recipe<?> getBackingRecipe() {
        return null;
    }
}
