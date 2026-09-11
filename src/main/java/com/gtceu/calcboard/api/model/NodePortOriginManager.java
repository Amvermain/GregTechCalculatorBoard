package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.model.RecipeNode.PortOrigin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages N:N port mapping origins for nested sub-page modules.
 */
public class NodePortOriginManager {

    private final List<List<PortOrigin>> inputOrigins = new ArrayList<>();
    private final List<List<PortOrigin>> outputOrigins = new ArrayList<>();

    public List<List<PortOrigin>> getInputOrigins() {
        return inputOrigins;
    }

    public List<List<PortOrigin>> getOutputOrigins() {
        return outputOrigins;
    }

    public void clear() {
        inputOrigins.clear();
        outputOrigins.clear();
    }

    public void copyFrom(NodePortOriginManager other) {
        clear();
        if (other == null) return;
        copyOriginList(other.inputOrigins, this.inputOrigins);
        copyOriginList(other.outputOrigins, this.outputOrigins);
    }

    private void copyOriginList(List<List<PortOrigin>> src, List<List<PortOrigin>> dest) {
        for (List<PortOrigin> group : src) {
            List<PortOrigin> copy = new ArrayList<>();
            for (PortOrigin origin : group) {
                if (origin != null) {
                    copy.add(new PortOrigin(origin.internalNodeId(), origin.internalPortIndex()));
                }
            }
            dest.add(copy);
        }
    }

    public void serialize(CompoundTag tag) {
        if (!inputOrigins.isEmpty()) {
            tag.put("moduleInputOrigins", serializeOriginList(inputOrigins));
        }
        if (!outputOrigins.isEmpty()) {
            tag.put("moduleOutputOrigins", serializeOriginList(outputOrigins));
        }
    }

    public void deserialize(CompoundTag tag) {
        clear();
        if (tag.contains("moduleInputOrigins", Tag.TAG_LIST)) {
            deserializeOriginList(tag.getList("moduleInputOrigins", Tag.TAG_LIST), inputOrigins);
        }
        if (tag.contains("moduleOutputOrigins", Tag.TAG_LIST)) {
            deserializeOriginList(tag.getList("moduleOutputOrigins", Tag.TAG_LIST), outputOrigins);
        }
    }

    private ListTag serializeOriginList(List<List<PortOrigin>> list) {
        ListTag rootList = new ListTag();
        for (List<PortOrigin> group : list) {
            ListTag groupList = new ListTag();
            for (PortOrigin origin : group) {
                groupList.add(origin.serializeNBT());
            }
            rootList.add(groupList);
        }
        return rootList;
    }

    private void deserializeOriginList(ListTag rootList, List<List<PortOrigin>> target) {
        for (int i = 0; i < rootList.size(); i++) {
            ListTag groupList = rootList.getList(i);
            List<PortOrigin> group = new ArrayList<>();
            for (int j = 0; j < groupList.size(); j++) {
                group.add(PortOrigin.deserializeNBT(groupList.getCompound(j)));
            }
            target.add(group);
        }
    }
}
