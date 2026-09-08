package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import net.minecraft.resources.ResourceLocation;

public final class NodeTierChangeHandler {

    private NodeTierChangeHandler() {}

    public static boolean changeTier(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction) {
        if (parent != null && !parent.ensureEditPermission()) return false;

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null && adapter.isBoilerRecipe(node)) {
            return changeBoilerTier(widget, node, parent, direction);
        }

        if (GTCombustionHelper.isCombustionFamily(node)) {
            return changeCombustionTier(widget, node, parent, direction);
        }

        boolean isVanillaCooking = node.getRecipeCategoryId() != null && GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId());
        boolean isPassiveOrSteam = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || node.getEnergyType() == EnergyType.NONE;

        int minIdx = resolveMinTierIndex(node, adapter, isVanillaCooking, isPassiveOrSteam);
        int maxIdx = GTVoltageTier.values().length - 1;

        if (node.isTurbine()) {
            if (node.isMultiblock()) {
                GTVoltageTier baseTier = GTTurbineHelper.getTurbineBaseTier(node);
                if (baseTier != null) {
                    minIdx = Math.max(minIdx, baseTier.ordinal());
                }
            } else {
                maxIdx = GTVoltageTier.HV.ordinal();
            }
        }

        if (!node.isMultiblock() && node.supportsSteamMode()) {
            Boolean steamHandled = changeSteamModeTier(widget, node, parent, direction, minIdx, isVanillaCooking);
            if (steamHandled != null) {
                return steamHandled;
            }
        } else if (node.getEnergyType() == EnergyType.NONE) {
            return transitionNoneEnergyToElectric(widget, node, parent, direction, minIdx, isVanillaCooking);
        }

        return changeStandardElectricOrTurbineTier(widget, node, parent, direction, minIdx, maxIdx);
    }

    private static boolean changeBoilerTier(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction) {
        GTBoilerTier curTier = GTBoilerTier.getBoilerTier(node);
        GTBoilerTier[] vals = GTBoilerTier.values();
        int newIdx = (curTier.ordinal() + direction + vals.length) % vals.length;
        GTBoilerTier nextTier = vals[newIdx];
        node.setMachineIcon(nextTier.getDefaultIcon());
        node.setMultiblock(nextTier.isMultiblock());
        if (parent != null) parent.markSummaryDirty();
        widget.invalidateCache();
        return true;
    }

    private static boolean changeCombustionTier(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction) {
        GTVoltageTier curTier = node.getTargetTier();
        if (curTier == null) {
            curTier = GTCombustionHelper.getCombustionTierForMachine(node.getMachineIcon());
            if (curTier == null) curTier = GTVoltageTier.LV;
        }
        int curOrdinal = curTier.ordinal();
        int minOrdinal = GTCombustionHelper.getMinCombustionTier().ordinal();
        int maxOrdinal = GTCombustionHelper.getMaxCombustionTier().ordinal();
        int nextOrdinal = curOrdinal + direction;
        if (nextOrdinal < minOrdinal || nextOrdinal > maxOrdinal || nextOrdinal == curOrdinal) {
            return false;
        }
        GTVoltageTier nextTier = GTVoltageTier.getByIndex(nextOrdinal);
        GTVoltageTier oldTier = node.getTargetTier();
        boolean ok = GTCombustionHelper.syncCombustionMachine(node, nextTier);
        if (ok) {
            syncSharedFrameHardware(parent, node);
            if (parent != null) {
                parent.recordCommand(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, nextTier));
                parent.markSummaryDirty();
            }
            widget.invalidateCache();
            return true;
        }
        return false;
    }

    private static int resolveMinTierIndex(RecipeNode node, IModAdapter adapter, boolean isVanillaCooking, boolean isPassiveOrSteam) {
        int minIdx = node.getRecipeTier() != null ? node.getRecipeTier().ordinal() : GTVoltageTier.ULV.ordinal();
        if (adapter != null && !isVanillaCooking && !isPassiveOrSteam) {
            GTVoltageTier minWsTier = adapter.getMinimumWorkstationTier(node);
            if (minWsTier != null) {
                minIdx = Math.max(minIdx, minWsTier.ordinal());
            }
        }
        return minIdx;
    }

    private static Boolean changeSteamModeTier(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction, int minIdx, boolean isVanillaCooking) {
        SteamMode curSteam = node.getSteamMode();
        if (curSteam == SteamMode.LOW_PRESSURE) {
            return handleLowPressureSteam(widget, node, parent, direction, isVanillaCooking);
        }
        if (curSteam == SteamMode.HIGH_PRESSURE) {
            return handleHighPressureSteam(widget, node, parent, direction, minIdx, isVanillaCooking);
        }
        if (node.getEnergyType() == EnergyType.NONE) {
            return handleNoneEnergySteamTransition(widget, node, parent, direction, minIdx, isVanillaCooking);
        }
        return handleElectricToSteamDowngrade(widget, node, parent, direction, minIdx, isVanillaCooking);
    }

    private static boolean handleLowPressureSteam(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction, boolean isVanillaCooking) {
        if (direction > 0) {
            node.setSteamMode(SteamMode.HIGH_PRESSURE);
            if (parent != null) parent.markSummaryDirty();
            widget.invalidateCache();
            return true;
        }
        if (direction < 0 && isVanillaCooking) {
            node.setSteamMode(SteamMode.NONE);
            node.setMachineIcon(ResourceLocation.tryParse("minecraft:furnace"));
            syncSharedFrameHardware(parent, node);
            if (parent != null) parent.markSummaryDirty();
            widget.invalidateCache();
            return true;
        }
        return false;
    }

    private static boolean handleHighPressureSteam(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction, int minIdx, boolean isVanillaCooking) {
        if (direction > 0) {
            node.setSteamMode(SteamMode.NONE);
            GTVoltageTier lowestElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV : GTVoltageTier.LV;
            node.setTargetTier(lowestElectric);
            ResourceLocation sbWs = node.getWorkstationForTier(lowestElectric);
            if (sbWs != null) {
                node.setMachineIcon(sbWs);
            } else if (isVanillaCooking) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_electric_furnace"));
            }
            syncSharedFrameHardware(parent, node);
            if (parent != null) parent.markSummaryDirty();
            widget.invalidateCache();
            return true;
        }
        if (direction < 0) {
            node.setSteamMode(SteamMode.LOW_PRESSURE);
            syncSharedFrameHardware(parent, node);
            if (parent != null) parent.markSummaryDirty();
            widget.invalidateCache();
            return true;
        }
        return false;
    }

    private static Boolean handleNoneEnergySteamTransition(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction, int minIdx, boolean isVanillaCooking) {
        if (direction > 0) {
            if (node.supportsSteamMode()) {
                node.setSteamMode(SteamMode.LOW_PRESSURE);
            } else {
                GTVoltageTier lowestElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV : GTVoltageTier.LV;
                node.setTargetTier(lowestElectric);
                ResourceLocation sbWs = node.getWorkstationForTier(lowestElectric);
                if (sbWs != null) {
                    node.setMachineIcon(sbWs);
                } else if (isVanillaCooking) {
                    node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_electric_furnace"));
                }
            }
            syncSharedFrameHardware(parent, node);
            if (parent != null) parent.markSummaryDirty();
            widget.invalidateCache();
            return true;
        }
        return false;
    }

    private static Boolean handleElectricToSteamDowngrade(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction, int minIdx, boolean isVanillaCooking) {
        int curIdx = node.getTargetTier() != null ? node.getTargetTier().ordinal() : GTVoltageTier.LV.ordinal();
        int lowestAllowedElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV.ordinal() : GTVoltageTier.LV.ordinal();
        if (direction < 0 && curIdx <= lowestAllowedElectric) {
            if (node.supportsSteamMode()) {
                node.setSteamMode(SteamMode.HIGH_PRESSURE);
                syncSharedFrameHardware(parent, node);
                if (parent != null) parent.markSummaryDirty();
                widget.invalidateCache();
                return true;
            }
            if (isVanillaCooking) {
                node.setMachineIcon(ResourceLocation.tryParse("minecraft:furnace"));
                syncSharedFrameHardware(parent, node);
                if (parent != null) parent.markSummaryDirty();
                widget.invalidateCache();
                return true;
            }
            return false;
        }
        return null;
    }

    private static boolean transitionNoneEnergyToElectric(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction, int minIdx, boolean isVanillaCooking) {
        if (direction > 0) {
            GTVoltageTier lowestElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV : GTVoltageTier.LV;
            node.setTargetTier(lowestElectric);
            ResourceLocation sbWs = node.getWorkstationForTier(lowestElectric);
            if (sbWs != null) {
                node.setMachineIcon(sbWs);
            }
            syncSharedFrameHardware(parent, node);
            if (parent != null) parent.markSummaryDirty();
            widget.invalidateCache();
            return true;
        }
        return false;
    }

    private static boolean changeStandardElectricOrTurbineTier(NodeWidget widget, RecipeNode node, BoardScreen parent, int direction, int minIdx, int maxIdx) {
        int curIdx = node.isLargeTurbine()
                ? GTTurbineHelper.getRotorHolderTier(node).ordinal()
                : (node.getTargetTier() != null ? node.getTargetTier().ordinal() : GTVoltageTier.LV.ordinal());
        int newIdx = curIdx + direction;

        if (node.isTurbine() && !node.isMultiblock() && newIdx > GTVoltageTier.HV.ordinal()) {
            if (node.hasMultiblockOption()) {
                GTVoltageTier oldTier = node.getTargetTier();
                GTVoltageTier newTier = GTVoltageTier.getByIndex(newIdx);
                node.setMultiblock(true);
                node.setTargetTier(newTier);
                GTTurbineHelper.setRotorHolderTier(node, newTier);
                GTCEuModAdapter.syncTurbineMachineIcon(node);
                syncSharedFrameHardware(parent, node);
                if (parent != null) {
                    parent.recordCommand(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, newTier));
                    parent.markSummaryDirty();
                }
                widget.invalidateCache();
                return true;
            }
            return false;
        }

        if (newIdx < minIdx || newIdx > maxIdx || newIdx == curIdx) {
            return false;
        }

        GTVoltageTier oldTier = node.getTargetTier();
        GTVoltageTier newTier = GTVoltageTier.getByIndex(newIdx);

        node.setTargetTier(newTier);
        if (node.isLargeTurbine()) {
            GTTurbineHelper.setRotorHolderTier(node, newTier);
        }
        if (!node.isMultiblock()) {
            ResourceLocation sbWs = node.getWorkstationForTier(newTier);
            if (sbWs != null) {
                node.setMachineIcon(sbWs);
            }
        }
        GTCEuModAdapter.syncTurbineMachineIcon(node);
        syncSharedFrameHardware(parent, node);
        if (parent != null) {
            parent.recordCommand(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, newTier));
            parent.markSummaryDirty();
        }
        widget.invalidateCache();
        return true;
    }

    public static void syncSharedFrameHardware(BoardScreen parent, RecipeNode node) {
        if (parent != null && parent.getGraph() != null) {
            var frame = parent.getGraph().findFrameEnclosingNode(node);
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, parent.getGraph());
                parent.rebuildWidgets();
            }
        }
    }
}
