package com.gtceu.calcboard.api;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a hardware addon, trait, upgrade kit, hatch, or custom multiplier attached to a RecipeNode.
 */
public class MachineAddon {

    public enum Category {
        PARALLEL("Parallel Hatches", "gui.gtcalcboard.addon_cat.parallel"),
        MAINTENANCE("Maintenance & Hatches", "gui.gtcalcboard.addon_cat.maintenance"),
        COIL("Heating Coils", "gui.gtcalcboard.addon_cat.coil"),
        ROTOR("Turbine Rotors", "gui.gtcalcboard.addon_cat.rotor"),
        MULTIBLOCK_TRAIT("Multiblock Traits", "gui.gtcalcboard.addon_cat.trait"),
        THERMAL_AUGMENT("Thermal Augments & Kits", "gui.gtcalcboard.addon_cat.thermal"),
        CUSTOM("Custom Modifiers", "gui.gtcalcboard.addon_cat.custom");

        private final String englishName;
        private final String translatableKey;

        Category(String englishName, String translatableKey) {
            this.englishName = englishName;
            this.translatableKey = translatableKey;
        }

        public String getEnglishName() {
            return englishName;
        }

        public String getTranslatableKey() {
            return translatableKey;
        }
    }

    private final String id;
    private String name;
    private Category category;
    private String description;
    private ResourceLocation itemIcon;
    private ItemStack itemStackSample;

    private double durationMultiplier = 1.0;
    private double eutMultiplier = 1.0;
    private int parallelMultiplier = 1;
    private boolean powerConstant = false; // If true, parallel execution does not scale EU/t (e.g. Absolute Parallel Hatch)
    private String discoverySource; // Debug provenance metadata (e.g. Recipe Output NBT, Registry, Behavior reflection)

    // Machine-specific coil bonus metrics
    private int coilTemperature = 1800;
    private int pyrolyseSpeedPercent = 100;
    private int crackingEnergyPercent = 100;
    private int chemicalSpeedPercent = 100;
    private int chemicalEnergyPercent = 100;
    private int smelterParallel = 1;

    // Turbine rotor specific capacity
    private double rotorMaxEUt = 0.0;

    public MachineAddon(String id, String name, Category category, String description, ResourceLocation itemIcon) {
        this.id = id;
        this.name = name;
        this.category = category != null ? category : Category.CUSTOM;
        this.description = description != null ? description : "";
        this.itemIcon = itemIcon;
        this.itemStackSample = null;
    }

    public static MachineAddon custom(String name, double durationMult, double eutMult, int parallelMult) {
        MachineAddon addon = new MachineAddon("custom:" + System.currentTimeMillis(), name, Category.CUSTOM, "gui.gtcalcboard.addon.custom_modifier_desc", null);
        addon.setDurationMultiplier(durationMult);
        addon.setEutMultiplier(eutMult);
        addon.setParallelMultiplier(parallelMult);
        return addon;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        if (itemStackSample != null && !itemStackSample.isEmpty()) {
            try {
                String stackName = itemStackSample.getHoverName().getString();
                if (stackName != null && !stackName.isEmpty() && !stackName.contains("%s")) {
                    return stackName;
                }
            } catch (Throwable ignored) {}
        }
        if (name != null && !name.isEmpty() && !name.contains("%s")) {
            if (name.startsWith("gui.gtcalcboard.") || name.contains(".")) {
                try {
                    String trans = Component.translatable(name).getString();
                    if (!trans.contains("%s")) return trans;
                } catch (Throwable ignored) {}
            }
            return name;
        }
        if (itemIcon != null) {
            try {
                var item = ForgeRegistries.ITEMS.getValue(itemIcon);
                if (item != null && item != Items.AIR) {
                    String itemDesc = item.getDescription().getString();
                    if (!itemDesc.contains("%s")) {
                        return itemDesc;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return name != null ? name : id;
    }

    public String getRawName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getDescription() {
        if (description != null && (description.startsWith("gui.gtcalcboard.") || description.contains("."))) {
            try {
                return Component.translatable(description).getString();
            } catch (Throwable ignored) {}
        }
        if (itemIcon != null) {
            try {
                var item = ForgeRegistries.ITEMS.getValue(itemIcon);
                if (item != null && item != Items.AIR) {
                    ItemStack stack = new ItemStack(item);
                    var player = Minecraft.getInstance().player;
                    var lines = stack.getTooltipLines(player, TooltipFlag.Default.NORMAL);
                    if (lines != null && lines.size() > 1) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < lines.size(); i++) {
                            String t = lines.get(i).getString().trim();
                            if (!t.isEmpty()) {
                                if (sb.length() > 0) sb.append(" | ");
                                sb.append(t);
                            }
                        }
                        if (sb.length() > 0) return sb.toString();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return description != null ? description : "";
    }

    public String getRawDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ResourceLocation getItemIcon() {
        return itemIcon;
    }

    public void setItemIcon(ResourceLocation itemIcon) {
        this.itemIcon = itemIcon;
    }

    public ItemStack getItemStackSample() {
        if (itemStackSample != null && !itemStackSample.isEmpty()) {
            return itemStackSample;
        }
        if (itemIcon != null) {
            try {
                Item item = ForgeRegistries.ITEMS.getValue(itemIcon);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            } catch (Throwable ignored) {}
        }
        return ItemStack.EMPTY;
    }

    public void setItemStackSample(ItemStack itemStackSample) {
        this.itemStackSample = itemStackSample;
    }

    public double getDurationMultiplier() {
        return durationMultiplier;
    }

    public void setDurationMultiplier(double durationMultiplier) {
        this.durationMultiplier = Math.max(0.01, durationMultiplier);
    }

    public double getEutMultiplier() {
        return eutMultiplier;
    }

    public void setEutMultiplier(double eutMultiplier) {
        this.eutMultiplier = Math.max(0.0, eutMultiplier);
    }

    public int getParallelMultiplier() {
        return parallelMultiplier;
    }

    public void setParallelMultiplier(int parallelMultiplier) {
        this.parallelMultiplier = Math.max(1, parallelMultiplier);
    }

    public boolean isPowerConstant() {
        return powerConstant;
    }

    public void setPowerConstant(boolean powerConstant) {
        this.powerConstant = powerConstant;
    }

    public int getCoilTemperature() {
        return coilTemperature;
    }

    public void setCoilTemperature(int coilTemperature) {
        this.coilTemperature = coilTemperature;
    }

    public int getPyrolyseSpeedPercent() {
        return pyrolyseSpeedPercent;
    }

    public void setPyrolyseSpeedPercent(int pyrolyseSpeedPercent) {
        this.pyrolyseSpeedPercent = pyrolyseSpeedPercent;
    }

    public int getCrackingEnergyPercent() {
        return crackingEnergyPercent;
    }

    public void setCrackingEnergyPercent(int crackingEnergyPercent) {
        this.crackingEnergyPercent = crackingEnergyPercent;
    }

    public int getChemicalSpeedPercent() {
        return chemicalSpeedPercent;
    }

    public void setChemicalSpeedPercent(int chemicalSpeedPercent) {
        this.chemicalSpeedPercent = chemicalSpeedPercent;
    }

    public int getChemicalEnergyPercent() {
        return chemicalEnergyPercent;
    }

    public void setChemicalEnergyPercent(int chemicalEnergyPercent) {
        this.chemicalEnergyPercent = chemicalEnergyPercent;
    }

    public int getSmelterParallel() {
        return smelterParallel;
    }

    public void setSmelterParallel(int smelterParallel) {
        this.smelterParallel = smelterParallel;
    }

    private int rotorPower = 100;

    public double getRotorMaxEUt() {
        return rotorMaxEUt;
    }

    public void setRotorMaxEUt(double rotorMaxEUt) {
        this.rotorMaxEUt = Math.max(0.0, rotorMaxEUt);
    }

    public int getRotorPower() {
        return rotorPower;
    }

    public void setRotorPower(int rotorPower) {
        this.rotorPower = Math.max(10, Math.min(5000, rotorPower));
    }

    /**
     * Tailors coil bonus multipliers specifically for the target machine.
     * Pyrolyse: Speed % -> Duration (100 / Speed)
     * Cracking: Energy % -> EU/t (Energy / 100)
     * Chemical Reactor / LCR: Speed % -> Duration (100 / Speed), Energy % -> EU/t (Energy / 100)
     * Multi Smelter: Max Parallel -> Parallel
     * EBF: 5% EU discount per 900K excess temperature above the recipe's required temperature
     */
    public MachineAddon forMachine(RecipeNode node) {
        if (node == null) return this;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.tailorAddon(this, node);
    }

    public MachineAddon forMachine(String machineName) {
        return forMachine(RecipeNode.create(machineName, 20.0, 32.0, GTVoltageTier.LV));
    }

    /**
     * Checks if this addon is compatible with the given RecipeNode's machine type.
     */
    public boolean isCompatibleWith(RecipeNode node) {
        if (node == null) return true;
        if (category == Category.CUSTOM) return true;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.isAddonCompatible(node, this);
    }

    public static boolean isThermalMachine(RecipeNode node) {
        return ThermalAugmentHelper.isThermalMachine(node);
    }

    public static boolean isTurbineMachine(RecipeNode node) {
        if (node == null) return false;
        return node.isTurbine();
    }

    /**
     * Returns the list of applicable addon categories for the given RecipeNode machine.
     */
    public static List<Category> getRelevantCategories(RecipeNode node) {
        if (node == null) {
            return List.of(Category.values());
        }
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.getApplicableAddonCategories(node);
    }

    public String getDiscoverySource() {
        return discoverySource;
    }

    public void setDiscoverySource(String discoverySource) {
        this.discoverySource = discoverySource;
    }

    public MachineAddon copy() {
        MachineAddon cp = new MachineAddon(this.id, this.name, this.category, this.description, this.itemIcon);
        cp.setItemStackSample(this.itemStackSample != null ? this.itemStackSample.copy() : null);
        cp.setDurationMultiplier(this.durationMultiplier);
        cp.setEutMultiplier(this.eutMultiplier);
        cp.setParallelMultiplier(this.parallelMultiplier);
        cp.setPowerConstant(this.powerConstant);
        cp.setCoilTemperature(this.coilTemperature);
        cp.setPyrolyseSpeedPercent(this.pyrolyseSpeedPercent);
        cp.setCrackingEnergyPercent(this.crackingEnergyPercent);
        cp.setChemicalSpeedPercent(this.chemicalSpeedPercent);
        cp.setChemicalEnergyPercent(this.chemicalEnergyPercent);
        cp.setSmelterParallel(this.smelterParallel);
        cp.setRotorMaxEUt(this.rotorMaxEUt);
        cp.setRotorPower(this.rotorPower);
        cp.setDiscoverySource(this.discoverySource);
        return cp;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name != null ? name : "");
        tag.putString("category", category.name());
        tag.putString("description", description != null ? description : "");
        if (itemIcon != null) {
            tag.putString("icon", itemIcon.toString());
        }
        tag.putDouble("durationMultiplier", durationMultiplier);
        tag.putDouble("eutMultiplier", eutMultiplier);
        tag.putInt("parallelMultiplier", parallelMultiplier);
        tag.putBoolean("powerConstant", powerConstant);
        tag.putInt("coilTemp", coilTemperature);
        tag.putInt("pyroSpeed", pyrolyseSpeedPercent);
        tag.putInt("crackEnergy", crackingEnergyPercent);
        tag.putInt("chemSpeed", chemicalSpeedPercent);
        tag.putInt("chemEnergy", chemicalEnergyPercent);
        tag.putInt("smelterPar", smelterParallel);
        tag.putDouble("rotorMaxEUt", rotorMaxEUt);
        tag.putInt("rotorPower", rotorPower);
        if (discoverySource != null) {
            tag.putString("discoverySource", discoverySource);
        }
        return tag;
    }

    public static MachineAddon deserializeNBT(CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return null;
        String id = tag.getString("id");
        String name = tag.getString("name");
        Category cat = Category.CUSTOM;
        if (tag.contains("category")) {
            try {
                cat = Category.valueOf(tag.getString("category"));
            } catch (Exception ignored) {}
        }
        String desc = tag.getString("description");
        ResourceLocation icon = tag.contains("icon") ? ResourceLocation.tryParse(tag.getString("icon")) : null;

        MachineAddon addon = new MachineAddon(id, name, cat, desc, icon);
        if (tag.contains("durationMultiplier")) addon.setDurationMultiplier(tag.getDouble("durationMultiplier"));
        if (tag.contains("eutMultiplier")) addon.setEutMultiplier(tag.getDouble("eutMultiplier"));
        if (tag.contains("parallelMultiplier")) addon.setParallelMultiplier(tag.getInt("parallelMultiplier"));
        if (tag.contains("powerConstant")) addon.setPowerConstant(tag.getBoolean("powerConstant"));
        if (tag.contains("coilTemp")) addon.setCoilTemperature(tag.getInt("coilTemp"));
        if (tag.contains("pyroSpeed")) addon.setPyrolyseSpeedPercent(tag.getInt("pyroSpeed"));
        if (tag.contains("crackEnergy")) addon.setCrackingEnergyPercent(tag.getInt("crackEnergy"));
        if (tag.contains("chemSpeed")) addon.setChemicalSpeedPercent(tag.getInt("chemSpeed"));
        if (tag.contains("chemEnergy")) addon.setChemicalEnergyPercent(tag.getInt("chemEnergy"));
        if (tag.contains("smelterPar")) addon.setSmelterParallel(tag.getInt("smelterPar"));
        if (tag.contains("rotorMaxEUt")) addon.setRotorMaxEUt(tag.getDouble("rotorMaxEUt"));
        if (tag.contains("rotorPower")) addon.setRotorPower(tag.getInt("rotorPower"));
        if (tag.contains("discoverySource")) addon.setDiscoverySource(tag.getString("discoverySource"));

        return addon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MachineAddon that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
