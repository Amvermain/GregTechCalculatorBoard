package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Synthetic EMI recipe representing Kinetic Generation (Create / Create New Age water wheels, windmills, engines, motors).
 */
public class KineticGenerationEmiRecipe implements EmiRecipe {

    private final ResourceLocation id;
    private final EmiRecipeCategory category;
    private final ResourceLocation machineIconId;
    private final String displayName;
    private final double durationTicks;
    private final double eut;
    private final GTVoltageTier tier;
    private final EnergyType energyType;
    private final boolean isGenerator;
    private final List<IngredientStack> inputStacks;
    private final List<IngredientStack> outputStacks;
    private final List<EmiIngredient> emiInputs;
    private final List<EmiStack> emiOutputs;
    private final List<EmiIngredient> workstations;

    public KineticGenerationEmiRecipe(
            ResourceLocation id,
            EmiRecipeCategory category,
            ResourceLocation machineIconId,
            String displayName,
            double durationTicks,
            double eut,
            GTVoltageTier tier,
            EnergyType energyType,
            boolean isGenerator,
            List<IngredientStack> inputStacks,
            List<IngredientStack> outputStacks,
            ItemStack machineItem
    ) {
        this.id = id;
        this.category = category;
        this.machineIconId = machineIconId;
        this.displayName = displayName;
        this.durationTicks = durationTicks;
        this.eut = eut;
        this.tier = tier;
        this.energyType = energyType;
        this.isGenerator = isGenerator;
        this.inputStacks = inputStacks != null ? inputStacks : List.of();
        this.outputStacks = outputStacks != null ? outputStacks : List.of();

        List<EmiIngredient> inList = new ArrayList<>();
        if (inputStacks != null) {
            for (IngredientStack in : inputStacks) {
                var emi = EmiStackHelper.toEmiStack(in);
                if (!emi.isEmpty()) inList.add(emi);
            }
        }
        this.emiInputs = Collections.unmodifiableList(inList);

        List<EmiStack> outList = new ArrayList<>();
        if (machineItem != null && !machineItem.isEmpty()) {
            outList.add(EmiStack.of(machineItem));
        }
        if (outputStacks != null) {
            for (IngredientStack out : outputStacks) {
                var emi = EmiStackHelper.toEmiStack(out);
                if (!emi.isEmpty()) outList.add(emi);
            }
        }
        this.emiOutputs = Collections.unmodifiableList(outList);

        if (machineItem != null && !machineItem.isEmpty()) {
            this.workstations = List.of(EmiStack.of(machineItem));
        } else {
            this.workstations = List.of();
        }
    }

    public RecipeNode toRecipeNode() {
        RecipeNode node = RecipeNode.create(displayName, durationTicks, eut, tier);
        node.setGenerator(isGenerator);
        node.setEnergyType(energyType);
        node.setMachineIcon(machineIconId);
        node.setRecipeCategoryId(category.getId());
        node.getAvailableWorkstations().clear();
        if (machineIconId != null) {
            node.getAvailableWorkstations().add(machineIconId);
        }
        for (IngredientStack in : inputStacks) {
            node.addInput(in.copy());
        }
        for (IngredientStack out : outputStacks) {
            node.addOutput(out.copy());
        }
        return node;
    }

    public ResourceLocation getMachineIconId() {
        return machineIconId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<IngredientStack> getInputStacks() {
        return inputStacks;
    }

    public List<IngredientStack> getOutputStacks() {
        return outputStacks;
    }

    public boolean isGenerator() {
        return isGenerator;
    }

    public EnergyType getEnergyType() {
        return energyType;
    }

    public double getEut() {
        return eut;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return emiInputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return emiOutputs;
    }

    public List<EmiIngredient> getWorkstations() {
        return workstations;
    }

    @Override
    public int getDisplayWidth() {
        return 130;
    }

    @Override
    public int getDisplayHeight() {
        return 50;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        if (!emiInputs.isEmpty()) {
            widgets.addSlot(emiInputs.get(0), 10, 8);
        } else if (!workstations.isEmpty()) {
            widgets.addSlot(workstations.get(0), 10, 8);
        }

        widgets.addFillingArrow(45, 8, 2000);

        if (!emiOutputs.isEmpty()) {
            widgets.addSlot(emiOutputs.get(0), 85, 8).recipeContext(this);
        }

        String statText;
        if (energyType == EnergyType.KINETIC_SU) {
            statText = (isGenerator ? "§a+" : "§c-") + (int) eut + " SU (" + String.format(java.util.Locale.ROOT, "%.1f", eut / (durationTicks / 20.0)) + " SU/s)";
        } else {
            statText = (isGenerator ? "§e+" : "§c-") + (int) eut + " FE/t";
        }
        widgets.addText(Component.literal(statText), 10, 32, 0xFFFFFF, true);
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

