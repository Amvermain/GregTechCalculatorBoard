package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;

import com.gtceu.calcboard.api.model.RecipeNode.PortOrigin;
import com.gtceu.calcboard.api.property.NodeProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated serialization helper for RecipeNode NBT storage and blueprint exports.
 */
public final class RecipeNodeSerializer {

    private RecipeNodeSerializer() {}

    public static CompoundTag serialize(RecipeNode node) {
        if (node == null) return new CompoundTag();

        CompoundTag tag = new CompoundTag();
        tag.putString("id", node.getId());
        if (node.getName() != null && !node.getName().isEmpty()) {
            tag.putString("name", node.getName());
        }
        if (node.getMachineIcon() != null) {
            tag.putString("icon", node.getMachineIcon().toString());
        }
        if (node.getBaseDurationTicks() != 0.0) {
            tag.putDouble("baseDuration", node.getBaseDurationTicks());
        }
        if (node.getBaseEUt() != 0.0) {
            tag.putDouble("baseEUt", node.getBaseEUt());
        }
        if (node.getRecipeTier() != null) {
            tag.putString("recipeTier", node.getRecipeTier().name());
            if (node.getTargetTier() != null && node.getTargetTier() != node.getRecipeTier()) {
                tag.putString("targetTier", node.getTargetTier().name());
            }
        }
        if (Math.abs(node.getMachineCount() - 1.0) > 0.0001) {
            tag.putDouble("machineCount", node.getMachineCount());
        }
        if (node.getParallel() > 1) {
            tag.putInt("parallel", node.getParallel());
        }
        if (node.getOverclockMode() != OverclockMode.STANDARD) {
            tag.putString("overclockMode", node.getOverclockMode().name());
        }
        if (node.isBaseNode()) {
            tag.putBoolean("isBaseNode", true);
        }
        if (node.isGenerator()) {
            tag.putBoolean("isGenerator", true);
        }
        CompoundTag propTag = node.getProperties().serializeNBT();
        if (!propTag.isEmpty()) {
            tag.put("properties", propTag);
        }
        tag.putDouble("posX", node.getPosX());
        tag.putDouble("posY", node.getPosY());
        if (node.getCardWidth() != 245) {
            tag.putInt("cardWidth", node.getCardWidth());
        }
        if (node.getCardHeight() > 0) {
            tag.putInt("cardHeight", node.getCardHeight());
        }
        if (node.isModule()) {
            tag.putBoolean("isModule", true);
            if (node.getContainedMachineCount() > 0) {
                tag.putInt("containedMachineCount", node.getContainedMachineCount());
            }
            if (node.getSubGraph() != null) {
                tag.put("subGraph", node.getSubGraph().serializeNBT(0, 0, 1.0));
            }
        }

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

        if (!node.getAddons().isEmpty()) {
            ListTag addonList = new ListTag();
            for (MachineAddon a : node.getAddons()) {
                addonList.add(a.serializeNBT());
            }
            tag.put("addons", addonList);
        }

        if (node.getRecipeCategoryId() != null) {
            tag.putString("recipeCategoryId", node.getRecipeCategoryId().toString());
        }

        if (!node.getAvailableWorkstations().isEmpty()) {
            CategoryCapability cap = node.getRecipeCategoryId() != null 
                    ? CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId()) 
                    : null;
            List<ResourceLocation> capWs = (cap != null && cap.availableWorkstations() != null) ? cap.availableWorkstations() : List.of();
            if (!node.getAvailableWorkstations().equals(capWs)) {
                ListTag wsList = new ListTag();
                for (ResourceLocation ws : node.getAvailableWorkstations()) {
                    wsList.add(net.minecraft.nbt.StringTag.valueOf(ws.toString()));
                }
                tag.put("workstations", wsList);
            }
        }

        if (node.getEnergyType() != null) {
            tag.putString("energyType", node.getEnergyType().name());
        }

        if (node.getSteamMode() != null && node.getSteamMode() != SteamMode.NONE) {
            tag.putString("steamMode", node.getSteamMode().name());
        }

        if (node.isMultiblock()) {
            tag.putBoolean("isMultiblock", true);
        }
        if (node.isReroute()) {
            tag.putBoolean("isReroute", true);
        }
        if (node.isFlipped()) {
            tag.putBoolean("isFlipped", true);
        }

        if (!node.getModuleInputOrigins().isEmpty()) {
            ListTag inOriginsTag = new ListTag();
            for (List<PortOrigin> origins : node.getModuleInputOrigins()) {
                ListTag slotList = new ListTag();
                for (PortOrigin o : origins) {
                    slotList.add(o.serializeNBT());
                }
                inOriginsTag.add(slotList);
            }
            tag.put("moduleInputOrigins", inOriginsTag);
        }

        if (!node.getModuleOutputOrigins().isEmpty()) {
            ListTag outOriginsTag = new ListTag();
            for (List<PortOrigin> origins : node.getModuleOutputOrigins()) {
                ListTag slotList = new ListTag();
                for (PortOrigin o : origins) {
                    slotList.add(o.serializeNBT());
                }
                outOriginsTag.add(slotList);
            }
            tag.put("moduleOutputOrigins", outOriginsTag);
        }

        if (node.hasThreading()) {
            tag.putString("threadingJson", node.getThreadingConfig().toJson().toString());
        }

        return tag;
    }

    public static RecipeNode deserialize(CompoundTag tag) {
        if (tag == null) return null;

        String id = tag.getString("id");
        String name = tag.getString("name");
        double baseDuration = tag.getDouble("baseDuration");
        double baseEUt = tag.getDouble("baseEUt");
        GTVoltageTier recipeTier = GTVoltageTier.valueOf(tag.getString("recipeTier"));

        RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
        if (tag.contains("properties", Tag.TAG_COMPOUND)) {
            node.getProperties().deserializeNBT(tag.getCompound("properties"));
        }

        // Backward compatibility for legacy saves and blueprints
        if (tag.contains("recipeTemperature") && !node.getProperties().has(NodeProperties.EBF_TEMPERATURE)) {
            node.setRecipeTemperature(tag.getInt("recipeTemperature"));
        }
        if (tag.contains("rpm") && !node.getProperties().has(NodeProperties.KINETIC_RPM)) {
            node.setRpm(tag.getInt("rpm"));
        }
        if (tag.contains("rotorEfficiency") && !node.getProperties().has(NodeProperties.TURBINE_ROTOR_EFFICIENCY)) {
            node.setRotorEfficiency(tag.getInt("rotorEfficiency"));
        }
        if (tag.contains("rotorPower") && !node.getProperties().has(NodeProperties.TURBINE_ROTOR_POWER)) {
            node.setRotorPower(tag.getInt("rotorPower"));
        }
        if (tag.contains("rotorName") && !node.getProperties().has(NodeProperties.TURBINE_ROTOR_NAME)) {
            node.setRotorName(tag.getString("rotorName"));
        }
        if (tag.contains("fusionStartEU") && !node.getProperties().has(NodeProperties.FUSION_START_EU)) {
            node.setEuToStart(tag.getLong("fusionStartEU"));
        } else if (tag.contains("euToStart") && !node.getProperties().has(NodeProperties.FUSION_START_EU)) {
            node.setEuToStart(tag.getLong("euToStart"));
        }

        if (tag.contains("recipeCategoryId")) {
            node.setRecipeCategoryId(ResourceLocation.tryParse(tag.getString("recipeCategoryId")));
        }
        if (tag.contains("workstations")) {
            ListTag wsList = tag.getList("workstations", 8);
            List<ResourceLocation> wsColl = new ArrayList<>();
            for (int i = 0; i < wsList.size(); i++) {
                ResourceLocation ws = ResourceLocation.tryParse(wsList.getString(i));
                if (ws != null) wsColl.add(ws);
            }
            node.setAvailableWorkstations(wsColl);
        } else if (node.getRecipeCategoryId() != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap.availableWorkstations() != null && !cap.availableWorkstations().isEmpty()) {
                node.setAvailableWorkstations(cap.availableWorkstations());
            }
        }
        if (tag.contains("icon")) {
            node.setMachineIcon(ResourceLocation.tryParse(tag.getString("icon")));
        }
        if (tag.contains("isMultiblock")) {
            node.setMultiblock(tag.getBoolean("isMultiblock"));
        }
        if (tag.contains("targetTier")) {
            node.setTargetTier(GTVoltageTier.valueOf(tag.getString("targetTier")));
        }
        if (tag.contains("machineCount")) {
            node.setMachineCount(tag.getDouble("machineCount"));
        }
        if (tag.contains("parallel")) {
            node.setParallel(tag.getInt("parallel"));
        }
        if (tag.contains("addons")) {
            ListTag addonList = tag.getList("addons", 10);
            for (int i = 0; i < addonList.size(); i++) {
                MachineAddon a = MachineAddon.deserializeNBT(addonList.getCompound(i));
                if (a != null) {
                    node.addAddon(a);
                }
            }
        }
        if (tag.contains("overclockMode")) {
            node.setOverclockMode(OverclockMode.valueOf(tag.getString("overclockMode")));
        }
        if (tag.contains("isBaseNode")) {
            node.setBaseNode(tag.getBoolean("isBaseNode"));
        }
        if (tag.contains("isGenerator")) {
            node.setGenerator(tag.getBoolean("isGenerator"));
        }
        if (tag.contains("cardWidth")) {
            node.setCardWidth(tag.getInt("cardWidth"));
        }
        if (tag.contains("cardHeight")) {
            node.setCardHeight(tag.getInt("cardHeight"));
        }
        if (tag.contains("isModule")) {
            node.setModule(tag.getBoolean("isModule"));
        }
        if (tag.contains("containedMachineCount")) {
            node.setContainedMachineCount(tag.getInt("containedMachineCount"));
        }
        node.getInputs().clear();
        if (tag.contains("inputs", Tag.TAG_LIST)) {
            ListTag inList = tag.getList("inputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < inList.size(); i++) {
                node.getInputs().add(IngredientStack.deserializeNBT(inList.getCompound(i)));
            }
        }
        node.getOutputs().clear();
        if (tag.contains("outputs", Tag.TAG_LIST)) {
            ListTag outList = tag.getList("outputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < outList.size(); i++) {
                node.getOutputs().add(IngredientStack.deserializeNBT(outList.getCompound(i)));
            }
        }

        if (tag.contains("energyType")) {
            try {
                node.setEnergyType(EnergyType.valueOf(tag.getString("energyType")));
            } catch (Throwable ignored) {}
        }
        if (tag.contains("steamMode")) {
            try {
                node.setSteamMode(SteamMode.valueOf(tag.getString("steamMode")));
            } catch (Throwable ignored) {}
        }
        if (tag.contains("subGraph")) {
            node.setSubGraph(FlowGraph.deserializeNBT(tag.getCompound("subGraph")));
        }
        node.setPosX(tag.getDouble("posX"));
        node.setPosY(tag.getDouble("posY"));

        if (tag.contains("isReroute")) {
            node.setReroute(tag.getBoolean("isReroute"));
        }
        if (tag.contains("isFlipped")) {
            node.setFlipped(tag.getBoolean("isFlipped"));
        }
        if (tag.contains("moduleInputOrigins", Tag.TAG_LIST)) {
            ListTag inOriginsTag = tag.getList("moduleInputOrigins", Tag.TAG_LIST);
            for (int i = 0; i < inOriginsTag.size(); i++) {
                ListTag slotList = inOriginsTag.getList(i);
                List<PortOrigin> origins = new ArrayList<>();
                for (int j = 0; j < slotList.size(); j++) {
                    origins.add(PortOrigin.deserializeNBT(slotList.getCompound(j)));
                }
                node.getModuleInputOrigins().add(origins);
            }
        }
        if (tag.contains("moduleOutputOrigins", Tag.TAG_LIST)) {
            ListTag outOriginsTag = tag.getList("moduleOutputOrigins", Tag.TAG_LIST);
            for (int i = 0; i < outOriginsTag.size(); i++) {
                ListTag slotList = outOriginsTag.getList(i);
                List<PortOrigin> origins = new ArrayList<>();
                for (int j = 0; j < slotList.size(); j++) {
                    origins.add(PortOrigin.deserializeNBT(slotList.getCompound(j)));
                }
                node.getModuleOutputOrigins().add(origins);
            }
        }

        if (tag.contains("threadingJson")) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(tag.getString("threadingJson")).getAsJsonObject();
                node.getThreadingConfig().fromJson(json);
            } catch (Throwable ignored) {}
        }

        return node;
    }
}



