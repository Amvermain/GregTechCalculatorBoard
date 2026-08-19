package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Represents a hardware addon, trait, upgrade kit, hatch, or custom multiplier attached to a RecipeNode.
 */
public class MachineAddon {

    public enum Category {
        PARALLEL("Parallel Hatches", "gui.gtcalcboard.addon_cat.parallel"),
        MAINTENANCE("Maintenance & Hatches", "gui.gtcalcboard.addon_cat.maintenance"),
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

    private double durationMultiplier = 1.0;
    private double eutMultiplier = 1.0;
    private int parallelMultiplier = 1;
    private boolean powerConstant = false; // If true, parallel execution does not scale EU/t (e.g. Absolute Parallel Hatch)

    public MachineAddon(String id, String name, Category category, String description, ResourceLocation itemIcon) {
        this.id = id;
        this.name = name;
        this.category = category != null ? category : Category.CUSTOM;
        this.description = description != null ? description : "";
        this.itemIcon = itemIcon;
    }

    public static MachineAddon custom(String name, double durationMult, double eutMult, int parallelMult) {
        MachineAddon addon = new MachineAddon("custom:" + System.currentTimeMillis(), name, Category.CUSTOM, "User-defined custom modifier", null);
        addon.setDurationMultiplier(durationMult);
        addon.setEutMultiplier(eutMult);
        addon.setParallelMultiplier(parallelMult);
        return addon;
    }

    public String getId() {
        return id;
    }

    public String getName() {
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

    public MachineAddon copy() {
        MachineAddon cp = new MachineAddon(this.id, this.name, this.category, this.description, this.itemIcon);
        cp.setDurationMultiplier(this.durationMultiplier);
        cp.setEutMultiplier(this.eutMultiplier);
        cp.setParallelMultiplier(this.parallelMultiplier);
        cp.setPowerConstant(this.powerConstant);
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
