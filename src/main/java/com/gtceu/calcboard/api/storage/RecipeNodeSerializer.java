package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.BoundaryPinNode;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.ModuleInputPin;
import com.gtceu.calcboard.api.model.ModuleOutputPin;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.model.role.INodeRole;
import com.gtceu.calcboard.api.model.role.JunctionNodeRole;
import com.gtceu.calcboard.api.model.role.MachineNodeRole;
import com.gtceu.calcboard.api.model.role.NodeRoleType;
import com.gtceu.calcboard.api.model.role.SubPageModuleNodeRole;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.spi.extension.IPortProjectionProvider;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class RecipeNodeSerializer {

    private RecipeNodeSerializer() {}

    public static CompoundTag serialize(RecipeNode node) {
        return serialize(node, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    public static CompoundTag serialize(RecipeNode node, Set<FlowGraph> visitedGraphs, int depth) {
        if (node == null) return new CompoundTag();

        CompoundTag tag = new CompoundTag();
        tag.putString("id", node.getId());
        if (node.getName() != null && !node.getName().isEmpty()) {
            tag.putString("name", node.getName());
        }
        if (node.hasCustomName()) {
            tag.putBoolean("hasCustomName", true);
        }
        tag.putDouble("posX", node.getPosX());
        tag.putDouble("posY", node.getPosY());
        if (node.getCardWidth() != 245) {
            tag.putInt("cardWidth", node.getCardWidth());
        }
        if (node.getCardHeight() > 0) {
            tag.putInt("cardHeight", node.getCardHeight());
        }
        if (node.isFlipped()) {
            tag.putBoolean("isFlipped", true);
        }
        if (node.isBaseNode()) {
            tag.putBoolean("isBaseNode", true);
        }

        CompoundTag propTag = node.getProperties().serializeNBT();
        if (!propTag.isEmpty()) {
            tag.put("properties", propTag);
        }

        if (node.getBaseSpec() != null) {
            tag.put("baseSpec", node.getBaseSpec().serializeNBT());
        }

        serializePorts(node, tag);

        INodeRole role = node.getRole();
        if (role != null) {
            tag.putString("roleType", role.getRoleType().name());
            CompoundTag roleData = new CompoundTag();
            role.serializeRoleNBT(roleData, copyVisitedGraphs(visitedGraphs), depth);
            if (!roleData.isEmpty()) {
                tag.put("roleData", roleData);
            }
            role.serializeRoleNBT(tag, visitedGraphs, depth);
        }

        if (node.getSteamMode() != null && node.getSteamMode() != SteamMode.NONE) {
            tag.putString("steamMode", node.getSteamMode().name());
        }

        return tag;
    }

    private static void serializePorts(RecipeNode node, CompoundTag tag) {
        if (!node.getInputs().isEmpty()) {
            ListTag inList = new ListTag();
            for (IngredientStack in : node.getInputs()) {
                inList.add(in.serializeNBT());
            }
            tag.put("inputs", inList);
        }

        if (!node.getOutputs().isEmpty()) {
            ListTag outList = new ListTag();
            for (IngredientStack out : node.getOutputs()) {
                outList.add(out.serializeNBT());
            }
            tag.put("outputs", outList);
        }

        if (!node.getHiddenInputIndices().isEmpty()) {
            tag.putIntArray("hiddenInputs", node.getHiddenInputIndices().stream().mapToInt(Integer::intValue).toArray());
        }
        if (!node.getHiddenOutputIndices().isEmpty()) {
            tag.putIntArray("hiddenOutputs", node.getHiddenOutputIndices().stream().mapToInt(Integer::intValue).toArray());
        }
        if (!node.getVoidedOutputIndices().isEmpty()) {
            tag.putIntArray("voidedOutputs", node.getVoidedOutputIndices().stream().mapToInt(Integer::intValue).toArray());
        }
    }

    public static RecipeNode deserialize(CompoundTag tag) {
        if (tag == null) return null;

        String id = tag.getString("id");
        String name = tag.getString("name");
        double baseDuration = tag.getDouble("baseDuration");
        double baseEUt = tag.getDouble("baseEUt");

        GTVoltageTier recipeTier = resolveRecipeTier(tag, baseEUt);
        NodeRoleType detectedRoleType = resolveRoleType(tag);

        RecipeNode node = createNodeInstance(id, name, baseDuration, baseEUt, recipeTier, detectedRoleType, tag);

        if (tag.contains("hasCustomName")) {
            node.setHasCustomName(tag.getBoolean("hasCustomName"));
        }
        if (tag.contains("properties", Tag.TAG_COMPOUND)) {
            node.getProperties().deserializeNBT(tag.getCompound("properties"));
        } else if (tag.contains("threadingJson")) {
            CompoundTag legacyProps = new CompoundTag();
            legacyProps.putString("threadingJson", tag.getString("threadingJson"));
            node.getProperties().deserializeNBT(legacyProps);
        }

        restoreLegacyHardwareProperties(node, tag);

        if (tag.contains("isBaseNode")) {
            node.setBaseNode(tag.getBoolean("isBaseNode"));
        }
        if (tag.contains("cardWidth")) {
            node.setCardWidth(tag.getInt("cardWidth"));
        }
        if (tag.contains("cardHeight")) {
            node.setCardHeight(tag.getInt("cardHeight"));
        }
        if (tag.contains("isFlipped")) {
            node.setFlipped(tag.getBoolean("isFlipped"));
        }
        node.setPosX(tag.getDouble("posX"));
        node.setPosY(tag.getDouble("posY"));

        restorePortLists(node, tag);
        if (tag.contains("steamMode")) {
            try {
                node.setSteamMode(SteamMode.valueOf(tag.getString("steamMode")));
            } catch (Throwable ignored) {}
        }

        if (tag.contains("baseSpec", Tag.TAG_COMPOUND)) {
            RecipeSpec spec = RecipeSpec.deserializeNBT(tag.getCompound("baseSpec"));
            node.setBaseSpecOnly(spec);
        } else {
            reconstructLegacyBaseSpec(node, tag);
        }
        node.syncProjectedPorts();
        restorePortVisibility(node, tag);

        return node;
    }

    private static GTVoltageTier resolveRecipeTier(CompoundTag tag, double baseEUt) {
        GTVoltageTier recipeTier = null;
        if (tag.contains("recipeTier")) {
            try {
                recipeTier = GTVoltageTier.valueOf(tag.getString("recipeTier"));
            } catch (Throwable ignored) {}
        }
        return recipeTier != null ? recipeTier : GTVoltageTier.getTierForVoltage((long) baseEUt);
    }

    private static NodeRoleType resolveRoleType(CompoundTag tag) {
        if (!tag.contains("roleType")) return null;
        try {
            return NodeRoleType.valueOf(tag.getString("roleType"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static RecipeNode createNodeInstance(String id, String name, double baseDuration, double baseEUt, GTVoltageTier recipeTier, NodeRoleType roleType, CompoundTag tag) {
        if (roleType != null) {
            return createNodeFromRoleType(id, name, baseDuration, baseEUt, recipeTier, roleType, tag);
        }
        return createNodeFromLegacyTags(id, name, baseDuration, baseEUt, recipeTier, tag);
    }

    private static RecipeNode createNodeFromRoleType(String id, String name, double baseDuration, double baseEUt, GTVoltageTier recipeTier, NodeRoleType roleType, CompoundTag tag) {
        CompoundTag roleData = tag.contains("roleData", Tag.TAG_COMPOUND) ? tag.getCompound("roleData") : null;
        switch (roleType) {
            case BOUNDARY_PIN -> {
                CompoundTag pinTag = roleData != null && roleData.contains("pinType") ? roleData : tag;
                return createLegacyBoundaryPin(id, name, pinTag);
            }
            case JUNCTION -> {
                RecipeNode node = new RecipeNode(id, name != null ? name : "Reroute", 0.0, 0.0, GTVoltageTier.ULV);
                JunctionNodeRole junctionRole = new JunctionNodeRole();
                junctionRole.deserializeRoleNBT(tag);
                if (roleData != null) junctionRole.deserializeRoleNBT(roleData);
                node.setRole(junctionRole);
                node.setCardWidth(32);
                node.setCardHeight(32);
                return node;
            }
            case MODULE -> {
                RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
                SubPageModuleNodeRole moduleRole = new SubPageModuleNodeRole();
                moduleRole.deserializeRoleNBT(tag);
                if (roleData != null) moduleRole.deserializeRoleNBT(roleData);
                node.setRole(moduleRole);
                return node;
            }
            case MACHINE -> {
                RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
                MachineNodeRole machineRole = new MachineNodeRole(baseDuration, baseEUt, recipeTier);
                machineRole.deserializeRoleNBT(tag);
                if (roleData != null) machineRole.deserializeRoleNBT(roleData);
                node.setRole(machineRole);
                return node;
            }
        }
        return new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
    }

    private static RecipeNode createNodeFromLegacyTags(String id, String name, double baseDuration, double baseEUt, GTVoltageTier recipeTier, CompoundTag tag) {
        if (tag.contains("pinType")) {
            return createLegacyBoundaryPin(id, name, tag);
        }
        if (tag.getBoolean("isReroute")) {
            RecipeNode node = new RecipeNode(id, name != null ? name : "Reroute", 0.0, 0.0, GTVoltageTier.ULV);
            JunctionNodeRole junctionRole = new JunctionNodeRole();
            junctionRole.deserializeRoleNBT(tag);
            node.setRole(junctionRole);
            node.setCardWidth(32);
            node.setCardHeight(32);
            return node;
        }
        if (tag.getBoolean("isModule")) {
            RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
            SubPageModuleNodeRole moduleRole = new SubPageModuleNodeRole();
            moduleRole.deserializeRoleNBT(tag);
            node.setRole(moduleRole);
            return node;
        }
        RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
        if (node.isMachine()) {
            node.asMachine().deserializeRoleNBT(tag);
        }
        return node;
    }

    private static RecipeNode createLegacyBoundaryPin(String id, String name, CompoundTag tag) {
        String pType = tag.getString("pinType");
        String pLabel = tag.contains("pinLabel") && !tag.getString("pinLabel").isEmpty()
                ? tag.getString("pinLabel")
                : (name != null && !name.isEmpty() ? name : ("OUTPUT".equalsIgnoreCase(pType) ? "Output Pin" : "Input Pin"));
        IngredientStack bound = tag.contains("boundIngredient")
                ? IngredientStack.deserializeNBT(tag.getCompound("boundIngredient"))
                : null;
        BoundaryPinNode pinNode;
        if ("OUTPUT".equalsIgnoreCase(pType)) {
            pinNode = new ModuleOutputPin(id, pLabel, bound);
        } else {
            pinNode = new ModuleInputPin(id, pLabel, bound);
        }
        pinNode.setTargetPortIndex(tag.getInt("targetPortIndex"));
        return pinNode;
    }

    private static void restorePortLists(RecipeNode node, CompoundTag tag) {
        if (tag.contains("inputs", Tag.TAG_LIST)) {
            node.getInputs().clear();
            ListTag inList = tag.getList("inputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < inList.size(); i++) {
                node.getInputs().add(IngredientStack.deserializeNBT(inList.getCompound(i)));
            }
        }
        if (tag.contains("outputs", Tag.TAG_LIST)) {
            node.getOutputs().clear();
            ListTag outList = tag.getList("outputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < outList.size(); i++) {
                node.getOutputs().add(IngredientStack.deserializeNBT(outList.getCompound(i)));
            }
        }
    }

    private static void restorePortVisibility(RecipeNode node, CompoundTag tag) {
        if (tag.contains("hiddenInputs")) {
            for (int idx : tag.getIntArray("hiddenInputs")) {
                node.hideInputPort(idx);
            }
        }
        if (tag.contains("hiddenOutputs")) {
            for (int idx : tag.getIntArray("hiddenOutputs")) {
                node.hideOutputPort(idx);
            }
        }
        if (tag.contains("voidedOutputs")) {
            for (int idx : tag.getIntArray("voidedOutputs")) {
                node.setOutputPortVoided(idx, true);
            }
        }
    }

    private static void restoreLegacyHardwareProperties(RecipeNode node, CompoundTag tag) {
        if (tag.contains("recipeTemperature") && !node.getProperties().hasById("ebf_temperature")) {
            node.setRecipeTemperature(tag.getInt("recipeTemperature"));
        }
        if (tag.contains("rpm") && !node.getProperties().hasById("kinetic_rpm")) {
            node.setRpm(tag.getInt("rpm"));
        }
        if (tag.contains("rotorEfficiency") && !node.getProperties().hasById("rotor_efficiency")) {
            node.setRotorEfficiency(tag.getInt("rotorEfficiency"));
        }
        if (tag.contains("rotorPower") && !node.getProperties().hasById("rotor_power")) {
            node.setRotorPower(tag.getInt("rotorPower"));
        }
        if (tag.contains("rotorName") && !node.getProperties().hasById("rotor_name")) {
            node.setRotorName(tag.getString("rotorName"));
        }
        if (tag.contains("fusionStartEU") && !node.getProperties().hasById("fusion_start_eu")) {
            node.setEuToStart(tag.getLong("fusionStartEU"));
        } else if (tag.contains("euToStart") && !node.getProperties().hasById("fusion_start_eu")) {
            node.setEuToStart(tag.getLong("euToStart"));
        }
    }

    private static void reconstructLegacyBaseSpec(RecipeNode node, CompoundTag tag) {
        IPortProjectionProvider provider = ModAdapterRegistry.findExtension(node, IPortProjectionProvider.class).orElse(null);
        if (provider == null) {
            provider = ModAdapterRegistry.getAdapterForNode(node);
        }
        List<IngredientStack> coreInputs = provider != null
                ? provider.sanitizeLegacyCoreInputs(node, node.getInputs())
                : new ArrayList<>(node.getInputs());

        List<IngredientStack> coreOutputs = new ArrayList<>();
        for (IngredientStack out : node.getOutputs()) {
            coreOutputs.add(out.copy());
        }

        RecipeSpec legacySpec = new RecipeSpec(
                node.getId(),
                node.getRecipeCategoryId(),
                node.getBaseDurationTicks(),
                node.getBaseEUt(),
                coreInputs,
                coreOutputs
        );
        node.setBaseSpecOnly(legacySpec);
    }

    private static Set<FlowGraph> copyVisitedGraphs(Set<FlowGraph> visitedGraphs) {
        if (visitedGraphs == null) return null;
        Set<FlowGraph> copy = Collections.newSetFromMap(new IdentityHashMap<>());
        copy.addAll(visitedGraphs);
        return copy;
    }
}
