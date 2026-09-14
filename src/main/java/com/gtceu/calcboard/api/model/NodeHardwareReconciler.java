package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reconciles hardware addons, voltage tiers, and parallel capacities during machine or recipe transitions.
 * <p>
 * Ensures idempotent transitions, notifies mod adapters of lifecycle changes, and clamps specifications safely.
 */
public final class NodeHardwareReconciler {

    private NodeHardwareReconciler() {}

    public static void reconcileForRecipe(RecipeNode node, RecipeNode template) {
        if (node == null || template == null) return;

        ResourceLocation oldIcon = node.getMachineIcon();
        IModAdapter oldAdapter = ModAdapterRegistry.getAdapterForNode(node);

        boolean compatible = isWorkstationCompatible(node, template);
        if (!compatible) {
            node.setMachineIcon(template.getMachineIcon());
            node.setMultiblock(template.isMultiblock());
        }

        updateWorkstations(node, template);
        clampVoltageTier(node);
        triggerAdapterTransition(node, oldAdapter, oldIcon);
        purgeIncompatibleAddons(node);
        clampParallel(node);

        node.markOverclockDirty();
        node.markOperationalDirty();
    }

    public static void reconcileForMachine(RecipeNode node, ResourceLocation newWs) {
        if (node == null || newWs == null) return;

        ResourceLocation oldIcon = node.getMachineIcon();
        IModAdapter oldAdapter = ModAdapterRegistry.getAdapterForNode(node);

        boolean nextMb = NodeWorkstationResolver.isMultiblockWorkstation(newWs);
        node.setMachineIcon(newWs);
        node.setMultiblock(nextMb);

        triggerAdapterTransition(node, oldAdapter, oldIcon);
        purgeIncompatibleAddons(node);
        clampParallel(node);

        node.markOverclockDirty();
        node.markOperationalDirty();
    }

    public static boolean isWorkstationCompatible(RecipeNode node, RecipeNode template) {
        ResourceLocation current = node.getMachineIcon();
        if (current == null) return false;

        List<ResourceLocation> templateWs = template.getAvailableWorkstations();
        if (templateWs != null && templateWs.contains(current)) {
            return true;
        }
        return Objects.equals(current, template.getMachineIcon());
    }

    private static void updateWorkstations(RecipeNode node, RecipeNode template) {
        List<ResourceLocation> newWs = new ArrayList<>(template.getAvailableWorkstations());
        if (newWs.isEmpty() && template.getMachineIcon() != null) {
            newWs.add(template.getMachineIcon());
        }
        if (node.getMachineIcon() != null && !newWs.contains(node.getMachineIcon())) {
            newWs.add(node.getMachineIcon());
        }
        node.setAvailableWorkstations(newWs);
    }

    private static void clampVoltageTier(RecipeNode node) {
        GTVoltageTier recipeTier = node.getRecipeTier();
        if (recipeTier == null) return;

        GTVoltageTier targetTier = node.getTargetTier();
        if (targetTier == null || targetTier.ordinal() < recipeTier.ordinal()) {
            node.setTargetTier(recipeTier);
        }

        if (!node.isMultiblock()) {
            ResourceLocation tierWs = NodeWorkstationResolver.getWorkstationForTier(node, node.getTargetTier());
            if (tierWs != null) {
                node.setMachineIcon(tierWs);
            }
        }
    }

    private static void triggerAdapterTransition(RecipeNode node, IModAdapter oldAdapter, ResourceLocation oldIcon) {
        IModAdapter newAdapter = ModAdapterRegistry.getAdapterForNode(node);
        if (oldAdapter != null && oldAdapter != newAdapter) {
            oldAdapter.onDetach(node);
            if (newAdapter != null) {
                newAdapter.onAttach(node);
            }
        }
        if (newAdapter != null) {
            newAdapter.onMachineIconChanged(node, oldIcon, node.getMachineIcon());
        }
    }

    public static void purgeIncompatibleAddons(RecipeNode node) {
        if (node == null || node.getAddons().isEmpty()) return;

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        List<MachineAddon> currentAddons = new ArrayList<>(node.getAddons());

        for (MachineAddon addon : currentAddons) {
            if (isAddonCompatible(node, addon, adapter)) continue;
            node.getAddons().remove(addon);
            if (adapter != null) {
                adapter.onAddonRemoved(node, addon);
            }
            cleanupAddonState(node, addon);
        }
    }

    private static boolean isAddonCompatible(RecipeNode node, MachineAddon addon, IModAdapter adapter) {
        if (addon == null) return false;
        if (addon.getCategory() == AddonCategory.CUSTOM) return true;

        if (addon.getCategory() == AddonCategory.COIL && (!node.isMultiblock() || !isCoilAddonValid(node))) {
            return false;
        }

        if (adapter != null) {
            List<AddonCategory> applicable = adapter.getApplicableAddonCategories(node);
            if (!applicable.contains(addon.getCategory())) {
                return false;
            }
            return adapter.canInstallAddon(node, addon);
        }
        return true;
    }

    private static boolean isCoilAddonValid(RecipeNode node) {
        if (!node.canUseCoils()) return false;
        ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        if (mbId == null) return true;
        var def = MultiblockStructureCatalog.getStructure(mbId);
        if (def != null && def.coilSlotCount() == 0 && !MultiblockDetector.isCoilMultiblock(mbId)) {
            return false;
        }
        return true;
    }

    private static void cleanupAddonState(RecipeNode node, MachineAddon addon) {
        if (addon.getCategory() == AddonCategory.ROTOR) {
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            node.setRotorName(null);
        }
    }

    public static void clampParallel(RecipeNode node) {
        if (node == null) return;

        if (!node.isMultiblock()) {
            node.setParallel(1);
            node.setCustomParallel(0);
            return;
        }

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null && adapter.isCombustionMachine(node)) {
            node.setParallel(1);
            node.setCustomParallel(0);
            return;
        }

        if (node.isTurbine()) {
            node.autoCalculateTurbineParallel();
            return;
        }

        int parallelFromAddons = NodeAddonHelper.getCombinedParallelMultiplier(node.getAddons());
        if (node.getCustomParallel() > 0) {
            node.setParallel(Math.max(1, node.getCustomParallel()));
        } else if (parallelFromAddons > 1) {
            node.setParallel(parallelFromAddons);
        } else if (node.getParallel() < 1) {
            node.setParallel(1);
        }
    }
}
