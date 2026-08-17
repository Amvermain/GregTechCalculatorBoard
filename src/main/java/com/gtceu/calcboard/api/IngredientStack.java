package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public class IngredientStack {
    public enum Type {
        ITEM,
        FLUID
    }

    private final Type type;
    private final ResourceLocation id;
    private final String displayName;
    private final double amount;
    private final double chance; // 0.0 ~ 1.0 (1.0 = 100%)

    public IngredientStack(Type type, ResourceLocation id, String displayName, double amount, double chance) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
        this.amount = amount;
        this.chance = Math.max(0.0, Math.min(1.0, chance));
    }

    public static IngredientStack item(ResourceLocation id, String displayName, double amount, double chance) {
        return new IngredientStack(Type.ITEM, id, displayName, amount, chance);
    }

    public static IngredientStack fluid(ResourceLocation id, String displayName, double amountMilliBuckets, double chance) {
        return new IngredientStack(Type.FLUID, id, displayName, amountMilliBuckets, chance);
    }

    public Type getType() {
        return type;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getDisplayName() {
        if (id != null) {
            try {
                if (type == Type.FLUID) {
                    var fluid = ForgeRegistries.FLUIDS.getValue(id);
                    if (fluid != null) {
                        return fluid.getFluidType().getDescription().getString();
                    }
                } else {
                    var item = ForgeRegistries.ITEMS.getValue(id);
                    if (item != null && item != Items.AIR) {
                        return item.getDescription().getString();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return displayName != null ? displayName : "";
    }

    public double getAmount() {
        return amount;
    }

    public double getChance() {
        return chance;
    }

    public double getExpectedAmount() {
        return amount * chance;
    }

    public boolean isFluid() {
        return type == Type.FLUID;
    }

    public dev.emi.emi.api.stack.EmiStack getEmiStack() {
        if (id == null) return dev.emi.emi.api.stack.EmiStack.EMPTY;
        if (type == Type.FLUID) {
            var fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid != null) {
                return dev.emi.emi.api.stack.EmiStack.of(fluid, Math.max(1, (long) amount));
            }
        } else {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) {
                return dev.emi.emi.api.stack.EmiStack.of(item, Math.max(1, (long) Math.round(amount)));
            }
        }
        return dev.emi.emi.api.stack.EmiStack.EMPTY;
    }

    public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y) {
        var emiStack = getEmiStack();
        if (!emiStack.isEmpty()) {
            emiStack.render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
        } else {
            graphics.fill(x, y, x + 16, y + 16, isFluid() ? 0xFF3366CC : 0xFF888888);
        }
    }

    public ItemStack createItemDisplayStack() {
        if (type == Type.ITEM && id != null) {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) {
                return new ItemStack(item, Math.max(1, (int) Math.round(amount)));
            }
        }
        return ItemStack.EMPTY;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putString("id", id != null ? id.toString() : "");
        tag.putString("name", displayName);
        tag.putDouble("amount", amount);
        tag.putDouble("chance", chance);
        return tag;
    }

    public static IngredientStack deserializeNBT(CompoundTag tag) {
        Type type = Type.valueOf(tag.getString("type"));
        ResourceLocation id = tag.contains("id") && !tag.getString("id").isEmpty() ? new ResourceLocation(tag.getString("id")) : null;
        String name = tag.getString("name");
        double amount = tag.getDouble("amount");
        double chance = tag.contains("chance") ? tag.getDouble("chance") : 1.0;
        return new IngredientStack(type, id, name, amount, chance);
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
