package com.gtceu.calcboard.integration.ae2.model;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import com.gtceu.calcboard.api.model.IngredientStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic immutable identifier and representation for AE2 crafting and processing patterns.
 */
public final class PatternId {
    private final String key;
    private final String displayName;
    private final ItemStack outputIcon;
    private final ResourceLocation primaryOutputId;

    public PatternId(String key, String displayName, ItemStack outputIcon, ResourceLocation primaryOutputId) {
        this.key = Objects.requireNonNull(key, "Pattern key cannot be null");
        this.displayName = displayName != null ? displayName : "AE2 Pattern";
        this.outputIcon = outputIcon != null ? outputIcon.copy() : ItemStack.EMPTY;
        this.primaryOutputId = primaryOutputId;
    }

    public static PatternId ofKey(String key, String displayName, ItemStack outputIcon) {
        ResourceLocation outputId = null;
        if (outputIcon != null && !outputIcon.isEmpty()) {
            outputId = ForgeRegistries.ITEMS.getKey(outputIcon.getItem());
        }
        if (outputId == null && key != null && !key.isEmpty()) {
            outputId = ResourceLocation.tryParse(key);
        }
        return new PatternId(key, displayName, outputIcon, outputId);
    }

    public static PatternId of(IPatternDetails pattern) {
        if (pattern == null) {
            return ofKey("empty", "Empty Pattern", ItemStack.EMPTY);
        }

        GenericStack primaryOut = pattern.getPrimaryOutput();
        ItemStack icon = ItemStack.EMPTY;
        String name = "AE2 Pattern";
        ResourceLocation outputId = null;

        if (primaryOut != null) {
            var itemKey = primaryOut.what();
            if (itemKey instanceof appeng.api.stacks.AEItemKey ak) {
                icon = ak.toStack((int) Math.max(1, primaryOut.amount()));
                name = icon.getHoverName().getString();
                outputId = ForgeRegistries.ITEMS.getKey(icon.getItem());
            } else if (itemKey instanceof appeng.api.stacks.AEFluidKey fk) {
                name = fk.getDisplayName().getString();
                outputId = ForgeRegistries.FLUIDS.getKey(fk.getFluid());
            }
        }

        String patternKey = computePatternSignature(pattern);
        return new PatternId(patternKey, name, icon, outputId);
    }

    public static PatternId of(ItemStack patternStack) {
        return of(patternStack, resolveClientLevel());
    }

    public static PatternId of(ItemStack patternStack, Level level) {
        if (patternStack == null || patternStack.isEmpty()) {
            return ofKey("empty", "Empty Pattern", ItemStack.EMPTY);
        }

        Level resolvedLevel = level != null ? level : resolveClientLevel();
        var decoded = PatternDetailsHelper.decodePattern(patternStack, resolvedLevel);
        if (decoded != null) {
            return of(decoded);
        }

        CompoundTag tag = patternStack.getTag();
        String key = tag != null ? "tag:" + tag.toString().hashCode() : "item:" + ForgeRegistries.ITEMS.getKey(patternStack.getItem());
        return new PatternId(key, patternStack.getHoverName().getString(), patternStack, ForgeRegistries.ITEMS.getKey(patternStack.getItem()));
    }

    public static PatternId ofIngredients(List<IngredientStack> inputs, List<IngredientStack> outputs, String displayName, ItemStack representative) {
        String signature = computeIngredientsSignature(inputs, outputs);
        ResourceLocation primaryOutputId = null;
        if (outputs != null && !outputs.isEmpty() && outputs.get(0) != null) {
            primaryOutputId = outputs.get(0).getId();
        }
        return new PatternId(signature, displayName, representative, primaryOutputId);
    }

    public static String computePatternSignature(IPatternDetails pattern) {
        if (pattern == null) return "empty";
        StringBuilder sb = new StringBuilder();

        var outputs = pattern.getOutputs();
        if (outputs != null) {
            for (GenericStack out : outputs) {
                if (out != null && out.what() != null) {
                    sb.append("out:").append(out.what().getId()).append("x").append(out.amount()).append(";");
                }
            }
        }

        var inputs = pattern.getInputs();
        if (inputs != null) {
            for (var in : inputs) {
                if (in == null) continue;
                var possibles = in.getPossibleInputs();
                if (possibles != null && possibles.length > 0 && possibles[0] != null && possibles[0].what() != null) {
                    sb.append("in:").append(possibles[0].what().getId()).append("x").append(possibles[0].amount()).append(";");
                }
            }
        }

        if (sb.length() == 0) {
            var def = pattern.getDefinition();
            return def != null ? def.toString() : "pattern:" + pattern.hashCode();
        }

        return sb.toString();
    }

    public static String computeIngredientsSignature(List<IngredientStack> inputs, List<IngredientStack> outputs) {
        StringBuilder sb = new StringBuilder();
        if (outputs != null) {
            for (IngredientStack out : outputs) {
                if (out != null && out.getId() != null) {
                    sb.append("out:").append(out.getId()).append("x").append((long) out.getAmount()).append(";");
                }
            }
        }
        if (inputs != null) {
            for (IngredientStack in : inputs) {
                if (in != null && in.getId() != null) {
                    sb.append("in:").append(in.getId()).append("x").append((long) in.getAmount()).append(";");
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "empty";
    }

    private static Level resolveClientLevel() {
        try {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                return net.minecraft.client.Minecraft.getInstance().level;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemStack getOutputIcon() {
        return outputIcon.copy();
    }

    public ResourceLocation getPrimaryOutputId() {
        return primaryOutputId;
    }

    public boolean matches(IPatternDetails other) {
        if (other == null) return false;
        return Objects.equals(this.key, of(other).getKey());
    }

    public boolean matches(ItemStack otherStack) {
        if (otherStack == null || otherStack.isEmpty()) return false;
        return Objects.equals(this.key, of(otherStack).getKey());
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putString("displayName", displayName);
        if (!outputIcon.isEmpty()) {
            tag.put("icon", outputIcon.save(new CompoundTag()));
        }
        if (primaryOutputId != null) {
            tag.putString("outputId", primaryOutputId.toString());
        }
        return tag;
    }

    public static PatternId deserializeNBT(CompoundTag tag) {
        if (tag == null) return ofKey("empty", "Empty Pattern", ItemStack.EMPTY);
        String key = tag.getString("key");
        String name = tag.getString("displayName");
        ItemStack icon = tag.contains("icon") ? ItemStack.of(tag.getCompound("icon")) : ItemStack.EMPTY;
        ResourceLocation outputId = tag.contains("outputId") ? ResourceLocation.tryParse(tag.getString("outputId")) : null;
        return new PatternId(key, name, icon, outputId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PatternId other)) return false;
        return Objects.equals(key, other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "PatternId[" + key + " (" + displayName + ")]";
    }
}
