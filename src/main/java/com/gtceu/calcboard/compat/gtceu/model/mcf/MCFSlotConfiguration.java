package com.gtceu.calcboard.compat.gtceu.model.mcf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages 1 to 8 docked module slot configurations for Modular Combustion Frame.
 */
public class MCFSlotConfiguration {

    public static final int MAX_SLOTS = 8;
    private final MCFModuleSlot[] slots = new MCFModuleSlot[MAX_SLOTS];

    public MCFSlotConfiguration() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i] = new MCFModuleSlot(i);
        }
    }

    public MCFModuleSlot getSlot(int index) {
        if (index < 0 || index >= MAX_SLOTS) {
            throw new IndexOutOfBoundsException("Slot index must be 0.." + (MAX_SLOTS - 1));
        }
        return slots[index];
    }

    public int getActiveSlotCount() {
        int count = 0;
        for (MCFModuleSlot slot : slots) {
            if (slot.isEnabled()) count++;
        }
        return count;
    }

    public List<MCFModuleSlot> getActiveSlots() {
        List<MCFModuleSlot> list = new ArrayList<>();
        for (MCFModuleSlot slot : slots) {
            if (slot.isEnabled()) {
                list.add(slot);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public void applyPreset8xUCM() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i].setEnabled(true);
            slots[i].setModuleType(MCFModuleType.UCM);
            slots[i].setFuel(MCFFuel.CETANE_DIESEL);
            slots[i].setOxidizerBoosted(true);
        }
    }

    public void applyPreset8xSCM() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i].setEnabled(true);
            slots[i].setModuleType(MCFModuleType.SCM);
            slots[i].setFuel(MCFFuel.CETANE_DIESEL);
            slots[i].setOxidizerBoosted(true);
        }
    }

    public void clearAll() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i].setEnabled(false);
        }
    }

    public JsonArray toJsonArray() {
        JsonArray array = new JsonArray();
        for (MCFModuleSlot slot : slots) {
            array.add(slot.toJson());
        }
        return array;
    }

    public static MCFSlotConfiguration fromJsonArray(JsonArray array) {
        MCFSlotConfiguration config = new MCFSlotConfiguration();
        if (array == null) return config;

        for (JsonElement elem : array) {
            if (elem != null && elem.isJsonObject()) {
                MCFModuleSlot slot = MCFModuleSlot.fromJson(elem.getAsJsonObject());
                int idx = slot.getSlotIndex();
                if (idx >= 0 && idx < MAX_SLOTS) {
                    config.slots[idx] = slot;
                }
            }
        }
        return config;
    }

    public String serialize() {
        return toJsonArray().toString();
    }

    public static MCFSlotConfiguration deserialize(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank() || "[]".equals(jsonStr.trim())) {
            return new MCFSlotConfiguration();
        }
        try {
            JsonElement elem = JsonParser.parseString(jsonStr);
            if (elem != null && elem.isJsonArray()) {
                return fromJsonArray(elem.getAsJsonArray());
            }
        } catch (Exception ignored) {}
        return new MCFSlotConfiguration();
    }

    public static MCFSlotConfiguration readFromNode(RecipeNode node) {
        if (node == null) return new MCFSlotConfiguration();
        String raw = node.getProperties().get(GTCEuProperties.MCF_SLOTS_DATA);
        return deserialize(raw);
    }

    public void saveToNode(RecipeNode node) {
        if (node == null) return;
        node.getProperties().set(GTCEuProperties.MCF_SLOTS_DATA, serialize());
    }

    public MCFSlotConfiguration copy() {
        MCFSlotConfiguration cp = new MCFSlotConfiguration();
        for (int i = 0; i < MAX_SLOTS; i++) {
            cp.slots[i] = this.slots[i].copy();
        }
        return cp;
    }
}
