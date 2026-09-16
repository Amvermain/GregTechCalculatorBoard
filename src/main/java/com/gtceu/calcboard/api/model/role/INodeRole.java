package com.gtceu.calcboard.api.model.role;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Defines the specialized behavioral role composed into a {@link RecipeNode}.
 * <p>
 * Implemented by machine, composite subpage module, junction buffer, and boundary I/O pin roles.
 */
public interface INodeRole {

    NodeRoleType getRoleType();

    void attach(RecipeNode owner);

    void detach();

    RecipeNode getOwner();

    void markDirty();

    boolean isOperational(FlowGraph graph);

    double getSingleMachinePower();

    double getTotalPower();

    double getEffectiveDurationSeconds();

    double getCyclesPerSecond();

    int getDefaultCardWidth();

    int getDefaultCardHeight();

    boolean isFixedSize();

    void serializeRoleNBT(CompoundTag tag, Set<FlowGraph> visitedGraphs, int depth);

    default void serializeRoleNBT(CompoundTag tag) {
        serializeRoleNBT(tag, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    void deserializeRoleNBT(CompoundTag tag);

    NodeCalculationSnapshot captureSnapshot(FlowGraph graph);

    default INodeRole copy() {
        return copy(Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    INodeRole copy(Set<FlowGraph> visitedGraphs, int depth);
}
