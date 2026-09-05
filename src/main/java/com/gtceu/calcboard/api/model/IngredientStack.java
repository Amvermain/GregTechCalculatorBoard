package com.gtceu.calcboard.api.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public class IngredientStack {
    public enum Type {
        ITEM,
        FLUID
    }

    private final Type type;
    private ResourceLocation id;
    private final String displayName;
    private final double amount;
    private double chance; // 0.0 ~ 1.0 (1.0 = 100%)
    private final java.util.List<ResourceLocation> alternatives = new java.util.ArrayList<>();
    private int selectedAltIndex = 0;

    public IngredientStack(Type type, ResourceLocation id, String displayName, double amount, double chance) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
        this.amount = amount;
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        if (id != null) {
            this.alternatives.add(id);
        }
    }

    public static IngredientStack item(ResourceLocation id, String displayName, double amount, double chance) {
        return new IngredientStack(Type.ITEM, id, displayName, amount, chance);
    }

    public static IngredientStack item(ResourceLocation id, String displayName, double amount) {
        return new IngredientStack(Type.ITEM, id, displayName, amount, 1.0);
    }

    public static IngredientStack fluid(ResourceLocation id, String displayName, double amountMilliBuckets, double chance) {
        return new IngredientStack(Type.FLUID, id, displayName, amountMilliBuckets, chance);
    }

    public static IngredientStack fluid(ResourceLocation id, String displayName, double amountMilliBuckets) {
        return new IngredientStack(Type.FLUID, id, displayName, amountMilliBuckets, 1.0);
    }

    public static IngredientStack stressUnit(double amount) {
        return new IngredientStack(Type.ITEM, ResourceLocation.tryParse("create:stress_units"), "Stress Units", amount, 1.0);
    }

    public boolean isStressUnit() {
        return this.id != null && (this.id.toString().equals("create:stress_units") || this.id.getPath().equals("stress_units"));
    }

    public Type getType() {
        return type;
    }

    public ResourceLocation getId() {
        return id;
    }

    public java.util.List<ResourceLocation> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(java.util.List<ResourceLocation> alts) {
        this.alternatives.clear();
        if (alts != null) {
            for (ResourceLocation r : alts) {
                if (r != null && !this.alternatives.contains(r)) {
                    this.alternatives.add(r);
                }
            }
        }
        if (this.id != null && !this.alternatives.contains(this.id)) {
            this.alternatives.add(0, this.id);
        }
    }

    public IngredientStack copy() {
        IngredientStack c = new IngredientStack(this.type, this.id, this.displayName, this.amount, this.chance);
        c.setAlternatives(new java.util.ArrayList<>(this.alternatives));
        c.selectedAltIndex = this.selectedAltIndex;
        return c;
    }

    public void addAlternative(ResourceLocation alt) {
        if (alt != null && !this.alternatives.contains(alt)) {
            this.alternatives.add(alt);
        }
        if (this.id != null && !this.alternatives.contains(this.id)) {
            this.alternatives.add(0, this.id);
        }
    }

    public boolean hasAlternatives() {
        return alternatives.size() > 1;
    }

    public boolean selectAlternative(ResourceLocation targetId) {
        if (targetId == null) return false;
        int idx = alternatives.indexOf(targetId);
        if (idx >= 0) {
            this.id = targetId;
            this.selectedAltIndex = idx;
            return true;
        }
        return false;
    }

    public void cycleAlternative(int delta) {
        if (alternatives.isEmpty()) return;
        selectedAltIndex = (selectedAltIndex + delta) % alternatives.size();
        if (selectedAltIndex < 0) selectedAltIndex += alternatives.size();
        this.id = alternatives.get(selectedAltIndex);
    }

    public boolean matchesOrAlternative(IngredientStack other) {
        if (other == null || other.type != this.type) return false;
        if (Objects.equals(this.id, other.id)) return true;
        return other.id != null && this.alternatives.contains(other.id);
    }

    public String getDisplayName() {
        if (id != null) {
            try {
                if (type == Type.FLUID) {
                    var fluid = ForgeRegistries.FLUIDS.getValue(id);
                    if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                        String name = fluid.getFluidType().getDescription().getString();
                        if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("air") && !name.equalsIgnoreCase("empty")) {
                            return name;
                        }
                    }
                } else {
                    var item = ForgeRegistries.ITEMS.getValue(id);
                    if (item != null && item != Items.AIR) {
                        String name = item.getDescription().getString();
                        if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("air")) {
                            return name;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return displayName != null ? displayName : "";
    }

    private double tierChanceBoost = 0.0; // Additional chance per voltage tier above base recipe tier (e.g. 0.05 = +5% per tier)

    public double getAmount() {
        return amount;
    }

    public double getChance() {
        return chance;
    }

    public double getTierChanceBoost() {
        return tierChanceBoost;
    }

    public void setTierChanceBoost(double tierChanceBoost) {
        this.tierChanceBoost = tierChanceBoost;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0, Math.min(1.0, chance));
    }

    public double getEffectiveChance(int tierDelta) {
        if (chance >= 1.0 && tierChanceBoost >= 0.0) return 1.0;
        if (tierChanceBoost == 0.0 || tierDelta <= 0) return chance;
        return Math.min(1.0, Math.max(0.0, chance + tierDelta * tierChanceBoost));
    }

    public double getExpectedAmount() {
        return amount * chance;
    }

    public double getExpectedAmount(int tierDelta) {
        return amount * getEffectiveChance(tierDelta);
    }

    public boolean isFluid() {
        return type == Type.FLUID;
    }

    public boolean isItem() {
        return type == Type.ITEM;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        if (id != null) {
            tag.putString("id", id.toString());
        }
        if (displayName != null && !displayName.isEmpty()) {
            tag.putString("name", displayName);
        }
        tag.putDouble("amount", amount);
        if (Math.abs(chance - 1.0) > 0.0001) {
            tag.putDouble("chance", chance);
        }
        if (Math.abs(tierChanceBoost) > 0.00001) {
            tag.putDouble("tierChanceBoost", tierChanceBoost);
        }
        if (!alternatives.isEmpty()) {
            net.minecraft.nbt.ListTag altList = new net.minecraft.nbt.ListTag();
            for (ResourceLocation alt : alternatives) {
                altList.add(net.minecraft.nbt.StringTag.valueOf(alt.toString()));
            }
            tag.put("alternatives", altList);
        }
        return tag;
    }

    public static IngredientStack deserializeNBT(CompoundTag tag) {
        Type type = Type.valueOf(tag.getString("type"));
        ResourceLocation id = tag.contains("id") && !tag.getString("id").isEmpty() ? ResourceLocation.tryParse(tag.getString("id")) : null;
        String name = tag.contains("name") ? tag.getString("name") : (id != null ? id.getPath() : "");
        double amount = tag.getDouble("amount");
        double chance = tag.contains("chance") ? tag.getDouble("chance") : 1.0;
        IngredientStack stack = new IngredientStack(type, id, name, amount, chance);
        if (tag.contains("tierChanceBoost")) {
            stack.setTierChanceBoost(tag.getDouble("tierChanceBoost"));
        }
        if (tag.contains("alternatives", 9)) { // 9 = TAG_List
            net.minecraft.nbt.ListTag altList = tag.getList("alternatives", 8); // 8 = TAG_String
            java.util.List<ResourceLocation> alts = new java.util.ArrayList<>();
            for (int i = 0; i < altList.size(); i++) {
                ResourceLocation altId = ResourceLocation.tryParse(altList.getString(i));
                if (altId != null) alts.add(altId);
            }
            stack.setAlternatives(alts);
        }
        return stack;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IngredientStack that)) return false;
        return type == that.type && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}

