package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Validates GTCEu operational constraints (reflector tiers, turbine fluid deficits, hatch slot capacities, fusion energy tiers).
 */
public final class GTNodeValidator {

    private GTNodeValidator() {}

    public static boolean validateNode(RecipeNode node, FlowGraph graph, List<Component> warnings) {
        if (node == null) return true;
        boolean valid = true;


        // 1. Reflector requirement check (RFC-001 & RFC-002)
        int req = node.getProperties().get(GTCEuProperties.REQUIRED_REFLECTOR_TIER);
        if (req > 0) {
            int inst = node.getInstalledReflectorTier();
            if (inst < req) {
                if (warnings != null) {
                    warnings.add(Component.translatable("gui.gtcalcboard.reflector_required_warning", req, inst > 0 ? ("T" + inst) : Component.translatable("gui.gtcalcboard.none_plain")));
                }
                valid = false;
            }
        }

        // 2. Heating Coil Temperature Check
        int reqTemp = node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE);
        if (reqTemp <= 0) {
            reqTemp = node.getRecipeTemperature();
        }
        if (reqTemp > 0) {
            int instTemp = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getInstalledCoilTemperature(node);
            if (instTemp < reqTemp) {
                if (warnings != null) {
                    warnings.add(Component.translatable("gui.gtcalcboard.node_warning.coil_temp_deficit", instTemp, reqTemp));
                }
                valid = false;
            }
        }

        // 3. Turbine 100% Flow Fulfillment Check
        if (graph != null && GTTurbineHelper.isTurbine(node)) {
            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(node, inIdx);
                if (stats != null && stats.isConnected() && stats.isNominalDeficit()) {
                    if (warnings != null) {
                        IngredientStack inStack = node.getInputs().get(inIdx);
                        String matName = inStack != null ? inStack.getDisplayName() : "Fluid";
                        warnings.add(Component.translatable("gui.gtcalcboard.turbine_flow_deficit_warning", matName, (int) Math.round(stats.getPercent())));
                    }
                    valid = false;
                }
            }
        }

        // 3. Equipped Custom Hatch / Bus Capacity & Slot Validation
        int totalEquippedItemInSlots = 0;
        int totalEquippedItemOutSlots = 0;
        int totalEquippedFluidInSlots = 0;
        int totalEquippedFluidOutSlots = 0;
        long maxOutputTankCapacityMB = 0;
        boolean hasCustomItemIn = false;
        boolean hasCustomItemOut = false;
        boolean hasCustomFluidIn = false;
        boolean hasCustomFluidOut = false;

        for (MachineAddon addon : node.getAddons()) {
            if (addon instanceof GTHatchAddon h) {
                int cap = h.getSlotCapacity();
                long tankPerSlot = h.getTankCapacityMB() / Math.max(1, cap);
                switch (h.getHatchType()) {
                    case ITEM_INPUT -> {
                        hasCustomItemIn = true;
                        totalEquippedItemInSlots += cap;
                    }
                    case ITEM_OUTPUT -> {
                        hasCustomItemOut = true;
                        totalEquippedItemOutSlots += cap;
                    }
                    case FLUID_INPUT -> {
                        hasCustomFluidIn = true;
                        totalEquippedFluidInSlots += cap;
                    }
                    case FLUID_OUTPUT -> {
                        hasCustomFluidOut = true;
                        totalEquippedFluidOutSlots += cap;
                        maxOutputTankCapacityMB = Math.max(maxOutputTankCapacityMB, tankPerSlot);
                    }
                    case DUAL_INPUT -> {
                        hasCustomItemIn = true;
                        hasCustomFluidIn = true;
                        totalEquippedItemInSlots += cap;
                        totalEquippedFluidInSlots += cap;
                    }
                    case DUAL_OUTPUT -> {
                        hasCustomItemOut = true;
                        hasCustomFluidOut = true;
                        totalEquippedItemOutSlots += cap;
                        totalEquippedFluidOutSlots += cap;
                        maxOutputTankCapacityMB = Math.max(maxOutputTankCapacityMB, tankPerSlot);
                    }
                    case ME_PATTERN_PROVIDER -> {
                        hasCustomItemIn = true;
                        hasCustomFluidIn = true;
                        totalEquippedItemInSlots += 36;
                        totalEquippedFluidInSlots += 36;
                    }
                    default -> {}
                }
            }
        }

        int reqItemOut = 0;
        for (int i = 0; i < node.getOutputs().size(); i++) {
            IngredientStack out = node.getOutputs().get(i);
            if (out.isItem() && node.getEffectiveOutputChance(i) > 0.0) {
                reqItemOut++;
            }
        }
        if (hasCustomItemOut && reqItemOut > totalEquippedItemOutSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.item_slot_deficit", reqItemOut, totalEquippedItemOutSlots));
            }
            valid = false;
        }

        int reqItemIn = (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
        if (hasCustomItemIn && reqItemIn > totalEquippedItemInSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.item_input_slot_deficit", reqItemIn, totalEquippedItemInSlots));
            }
            valid = false;
        }

        int reqFluidOut = 0;
        for (int i = 0; i < node.getOutputs().size(); i++) {
            IngredientStack out = node.getOutputs().get(i);
            if (out.isFluid() && node.getEffectiveOutputChance(i) > 0.0) {
                reqFluidOut++;
            }
        }
        if (hasCustomFluidOut && reqFluidOut > totalEquippedFluidOutSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.fluid_slot_deficit", reqFluidOut, totalEquippedFluidOutSlots));
            }
            valid = false;
        }

        int reqFluidIn = (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
        if (hasCustomFluidIn && reqFluidIn > totalEquippedFluidInSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.fluid_input_slot_deficit", reqFluidIn, totalEquippedFluidInSlots));
            }
            valid = false;
        }

        if (hasCustomFluidOut && maxOutputTankCapacityMB > 0) {
            for (IngredientStack out : node.getOutputs()) {
                if (out != null && out.isFluid()) {
                    double batchMB = out.getAmount() * node.getTotalParallel();
                    if (batchMB > maxOutputTankCapacityMB) {
                        if (warnings != null) {
                            warnings.add(Component.translatable("gui.gtcalcboard.node_warning.tank_capacity_overflow",
                                    out.getDisplayName(), (int) Math.round(batchMB), maxOutputTankCapacityMB));
                        }
                        valid = false;
                    }
                }
            }
        }

        if (GTAddonCompatibilityHandler.isDistillationTower(node)) {
            int reqFluidOutDT = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            int installedFluidOutHatches = 0;
            for (MachineAddon addon : node.getAddons()) {
                if (addon instanceof GTHatchAddon h) {
                    if (h.getHatchType() == GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == GTHatchAddon.HatchType.DUAL_OUTPUT) {
                        if (h.getSlotCapacity() > 1) {
                            if (warnings != null) {
                                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.dt_multi_fluid_unsupported", h.getName()));
                            }
                            valid = false;
                        }
                        installedFluidOutHatches++;
                    }
                }
            }
            if (installedFluidOutHatches > reqFluidOutDT && reqFluidOutDT > 0) {
                if (warnings != null) {
                    warnings.add(Component.translatable("gui.gtcalcboard.node_warning.dt_excess_output_hatches", installedFluidOutHatches, reqFluidOutDT));
                }
                valid = false;
            }
        }

        if (node.isFusion()) {
            for (MachineAddon addon : node.getAddons()) {
                if (addon instanceof GTEnergyHatchAddon eh) {
                    if (eh.getTier() != node.getTargetTier()) {
                        if (warnings != null) {
                            warnings.add(Component.translatable("gui.gtcalcboard.node_warning.fusion_energy_hatch_tier_mismatch",
                                    node.getTargetTier().getName(), eh.getTier().getName()));
                        }
                        valid = false;
                    }
                }
            }
        }

        return valid;
    }
}
