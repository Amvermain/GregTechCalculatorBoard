package com.gtceu.calcboard.compat.gtceu.model.mcf;

import com.google.gson.JsonObject;

/**
 * Individual slot descriptor for docked module multis inside Modular Combustion Frame.
 */
public class MCFModuleSlot {

    private final int slotIndex;
    private boolean enabled;
    private MCFModuleType moduleType;
    private MCFFuel fuel;
    private boolean oxidizerBoosted;

    public MCFModuleSlot(int slotIndex) {
        this(slotIndex, false, MCFModuleType.UCM, MCFFuel.CETANE_DIESEL, true);
    }

    public MCFModuleSlot(int slotIndex, boolean enabled, MCFModuleType moduleType, MCFFuel fuel, boolean oxidizerBoosted) {
        this.slotIndex = slotIndex;
        this.enabled = enabled;
        this.moduleType = moduleType != null ? moduleType : MCFModuleType.UCM;
        this.fuel = fuel != null ? fuel : MCFFuel.CETANE_DIESEL;
        this.oxidizerBoosted = oxidizerBoosted;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MCFModuleType getModuleType() {
        return moduleType;
    }

    public void setModuleType(MCFModuleType moduleType) {
        this.moduleType = moduleType != null ? moduleType : MCFModuleType.UCM;
    }

    public MCFFuel getFuel() {
        return fuel;
    }

    public void setFuel(MCFFuel fuel) {
        this.fuel = fuel != null ? fuel : MCFFuel.CETANE_DIESEL;
    }

    public boolean isOxidizerBoosted() {
        return oxidizerBoosted;
    }

    public void setOxidizerBoosted(boolean oxidizerBoosted) {
        this.oxidizerBoosted = oxidizerBoosted;
    }

    public double getFuelRecipeEUt() {
        return fuel != null ? fuel.getRecipeEUt() : 160.0;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("slot", slotIndex);
        json.addProperty("enabled", enabled);
        json.addProperty("type", moduleType.name());
        json.addProperty("fuel", fuel.name());
        json.addProperty("oxBoost", oxidizerBoosted);
        return json;
    }

    public static MCFModuleSlot fromJson(JsonObject json) {
        if (json == null) return new MCFModuleSlot(0);
        int slot = json.has("slot") ? json.get("slot").getAsInt() : 0;
        boolean enabled = json.has("enabled") && json.get("enabled").getAsBoolean();
        String typeName = json.has("type") ? json.get("type").getAsString() : "UCM";
        String fuelName = json.has("fuel") ? json.get("fuel").getAsString() : "CETANE_DIESEL";
        boolean oxBoost = !json.has("oxBoost") || json.get("oxBoost").getAsBoolean();

        MCFModuleType type = MCFModuleType.fromNameOrDefault(typeName);
        MCFFuel fuel = MCFFuel.fromIdOrDefault(fuelName);
        return new MCFModuleSlot(slot, enabled, type, fuel, oxBoost);
    }

    public MCFModuleSlot copy() {
        return new MCFModuleSlot(slotIndex, enabled, moduleType, fuel, oxidizerBoosted);
    }
}
