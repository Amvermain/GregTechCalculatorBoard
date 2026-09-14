package com.gtceu.calcboard.api.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable recipe specification definition.
 * Holds the source-of-truth recipe inputs, outputs, duration, and EU/t.
 * Never mutated in-place by hardware addons, overclocking, or dynamic port projections.
 */
public record RecipeSpec(
        String recipeId,
        ResourceLocation categoryId,
        double baseDurationTicks,
        double baseEUt,
        List<IngredientStack> baseInputs,
        List<IngredientStack> baseOutputs
) {
    public RecipeSpec {
        baseInputs = baseInputs != null ? List.copyOf(copyStacks(baseInputs)) : List.of();
        baseOutputs = baseOutputs != null ? List.copyOf(copyStacks(baseOutputs)) : List.of();
    }

    private static List<IngredientStack> copyStacks(List<IngredientStack> stacks) {
        List<IngredientStack> copy = new ArrayList<>(stacks.size());
        for (IngredientStack s : stacks) {
            if (s != null) {
                copy.add(s.copy());
            }
        }
        return copy;
    }

    public static RecipeSpec of(String recipeId, ResourceLocation categoryId, double durationTicks, double eut, List<IngredientStack> inputs, List<IngredientStack> outputs) {
        return new RecipeSpec(recipeId, categoryId, durationTicks, eut, inputs, outputs);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (recipeId != null && !recipeId.isEmpty()) {
            tag.putString("recipeId", recipeId);
        }
        if (categoryId != null) {
            tag.putString("categoryId", categoryId.toString());
        }
        tag.putDouble("duration", baseDurationTicks);
        tag.putDouble("eut", baseEUt);

        ListTag inList = new ListTag();
        for (IngredientStack in : baseInputs) {
            inList.add(in.serializeNBT());
        }
        tag.put("baseInputs", inList);

        ListTag outList = new ListTag();
        for (IngredientStack out : baseOutputs) {
            outList.add(out.serializeNBT());
        }
        tag.put("baseOutputs", outList);

        return tag;
    }

    public static RecipeSpec deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        String rId = tag.contains("recipeId") ? tag.getString("recipeId") : null;
        ResourceLocation catId = tag.contains("categoryId") ? ResourceLocation.tryParse(tag.getString("categoryId")) : null;
        double dur = tag.getDouble("duration");
        double eut = tag.getDouble("eut");

        List<IngredientStack> ins = new ArrayList<>();
        if (tag.contains("baseInputs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("baseInputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ins.add(IngredientStack.deserializeNBT(list.getCompound(i)));
            }
        }

        List<IngredientStack> outs = new ArrayList<>();
        if (tag.contains("baseOutputs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("baseOutputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                outs.add(IngredientStack.deserializeNBT(list.getCompound(i)));
            }
        }

        return new RecipeSpec(rId, catId, dur, eut, ins, outs);
    }
}
