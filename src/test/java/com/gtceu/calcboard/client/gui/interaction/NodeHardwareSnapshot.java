package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot capturing a node's hardware attributes, immutable recipe specification, and operational metrics.
 */
public record NodeHardwareSnapshot(
        String nodeId,
        ResourceLocation machineIcon,
        GTVoltageTier targetTier,
        GTVoltageTier recipeTier,
        int parallel,
        int customParallel,
        boolean isMultiblock,
        List<MachineAddon> addons,
        double baseDurationTicks,
        double baseEUt,
        double singleMachinePower,
        double totalPower,
        double effectiveDurationSeconds,
        com.gtceu.calcboard.api.type.OverclockMode overclockMode,
        RecipeSpec recipeSpec,
        List<Double> inputAmounts,
        List<Double> outputAmounts,
        NodePropertyStore properties
) {
    public static NodeHardwareSnapshot capture(RecipeNode node) {
        Objects.requireNonNull(node, "node cannot be null");
        List<MachineAddon> addonsCopy = new ArrayList<>(node.getAddons().size());
        for (MachineAddon addon : node.getAddons()) {
            addonsCopy.add(addon.copy());
        }

        List<Double> inputs = extractAmounts(node.getInputs());
        List<Double> outputs = extractAmounts(node.getOutputs());
        NodePropertyStore propertiesCopy = new NodePropertyStore(node.getProperties());

        return new NodeHardwareSnapshot(
                node.getId(),
                node.getMachineIcon(),
                node.getTargetTier(),
                node.getRecipeTier(),
                node.getParallel(),
                node.getCustomParallel(),
                node.isMultiblock(),
                addonsCopy,
                node.getBaseDurationTicks(),
                node.getBaseEUt(),
                node.getSingleMachineEUt(),
                node.getTotalEUt(),
                node.getEffectiveDurationSeconds(),
                node.getOverclockMode(),
                node.getBaseSpec(),
                inputs,
                outputs,
                propertiesCopy
        );
    }

    private static List<Double> extractAmounts(List<IngredientStack> stacks) {
        List<Double> amounts = new ArrayList<>(stacks.size());
        for (IngredientStack stack : stacks) {
            amounts.add(stack != null ? stack.getAmount() : 0.0);
        }
        return amounts;
    }

    public void restoreTo(RecipeNode node) {
        Objects.requireNonNull(node, "node cannot be null");
        node.setMultiblock(isMultiblock);
        node.setTargetTier(targetTier);
        if (recipeTier != null) {
            node.setRecipeTier(recipeTier);
        }
        node.setMachineIcon(machineIcon);
        node.setCustomParallel(customParallel);
        node.setParallel(parallel);

        node.getAddons().clear();
        for (MachineAddon addon : addons) {
            node.getAddons().add(addon.copy());
        }

        node.setBaseDurationTicks(baseDurationTicks);
        node.setBaseEUt(baseEUt);
        if (overclockMode != null) {
            node.setOverclockMode(overclockMode);
        }
        if (recipeSpec != null) {
            node.setBaseSpec(recipeSpec);
        }
        if (properties != null) {
            node.getProperties().copyFrom(properties);
        }
        node.markOperationalDirty();
        node.markOverclockDirty();
    }
}
