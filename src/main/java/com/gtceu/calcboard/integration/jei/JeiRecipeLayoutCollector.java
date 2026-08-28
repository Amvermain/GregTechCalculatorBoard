package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.api.model.IngredientStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Capture builder for JEI recipes implementing {@link IRecipeLayoutBuilder}.
 * Collects all inputs, outputs, catalysts, item stacks, fluid stacks, and ingredients.
 */
public class JeiRecipeLayoutCollector implements IRecipeLayoutBuilder {

    public static class EmptyFocusGroup implements IFocusGroup {
        public static final EmptyFocusGroup INSTANCE = new EmptyFocusGroup();

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public List<IFocus<?>> getAllFocuses() {
            return Collections.emptyList();
        }

        @Override
        public Stream<IFocus<?>> getFocuses(RecipeIngredientRole role) {
            return Stream.empty();
        }

        @Override
        public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType) {
            return Stream.empty();
        }

        @Override
        public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType, RecipeIngredientRole role) {
            return Stream.empty();
        }
    }

    public static class CollectedSlot implements IRecipeSlotBuilder {
        private final RecipeIngredientRole role;
        private final int x;
        private final int y;
        private final List<ItemStack> itemStacks = new ArrayList<>();
        private final List<FluidStack> fluidStacks = new ArrayList<>();
        private String slotName = "";

        public CollectedSlot(RecipeIngredientRole role, int x, int y) {
            this.role = role != null ? role : RecipeIngredientRole.INPUT;
            this.x = x;
            this.y = y;
        }

        public RecipeIngredientRole getRole() {
            return role;
        }

        public List<ItemStack> getItemStacks() {
            return Collections.unmodifiableList(itemStacks);
        }

        public List<FluidStack> getFluidStacks() {
            return Collections.unmodifiableList(fluidStacks);
        }

        public String getSlotName() {
            return slotName;
        }

        @Override
        public IRecipeSlotBuilder addItemStack(ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                this.itemStacks.add(stack);
            }
            return this;
        }

        @Override
        public IRecipeSlotBuilder addItemStacks(List<ItemStack> itemStacks) {
            if (itemStacks != null) {
                for (ItemStack is : itemStacks) {
                    if (is != null && !is.isEmpty()) {
                        this.itemStacks.add(is);
                    }
                }
            }
            return this;
        }

        @Override
        public IRecipeSlotBuilder addFluidStack(Fluid fluid, long amount) {
            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                this.fluidStacks.add(new FluidStack(fluid, (int) Math.max(1, amount)));
            }
            return this;
        }

        @Override
        public IRecipeSlotBuilder addFluidStack(Fluid fluid, long amount, @Nullable CompoundTag tag) {
            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                FluidStack fs = new FluidStack(fluid, (int) Math.max(1, amount));
                if (tag != null) {
                    fs.setTag(tag);
                }
                this.fluidStacks.add(fs);
            }
            return this;
        }

        @Override
        public IRecipeSlotBuilder addIngredients(Ingredient ingredient) {
            if (ingredient != null && !ingredient.isEmpty()) {
                ItemStack[] items = ingredient.getItems();
                if (items != null) {
                    for (ItemStack is : items) {
                        if (is != null && !is.isEmpty()) {
                            this.itemStacks.add(is);
                        }
                    }
                }
            }
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I> IRecipeSlotBuilder addIngredients(IIngredientType<I> ingredientType, List<@Nullable I> ingredients) {
            if (ingredients == null) return this;
            if (ingredientType == VanillaTypes.ITEM_STACK) {
                for (Object ing : ingredients) {
                    if (ing instanceof ItemStack is && !is.isEmpty()) {
                        this.itemStacks.add(is);
                    }
                }
            } else if (ingredientType == ForgeTypes.FLUID_STACK) {
                for (Object ing : ingredients) {
                    if (ing instanceof FluidStack fs && !fs.isEmpty()) {
                        this.fluidStacks.add(fs);
                    }
                }
            } else {
                for (I ing : ingredients) {
                    if (ing != null) {
                        addIngredient(ingredientType, ing);
                    }
                }
            }
            return this;
        }

        @Override
        public <I> IRecipeSlotBuilder addIngredient(IIngredientType<I> ingredientType, I ingredient) {
            if (ingredient == null) return this;
            if (ingredient instanceof ItemStack is && !is.isEmpty()) {
                this.itemStacks.add(is);
            } else if (ingredient instanceof FluidStack fs && !fs.isEmpty()) {
                this.fluidStacks.add(fs);
            } else if (ingredient instanceof ITypedIngredient<?> typed) {
                Object obj = typed.getIngredient();
                if (obj instanceof ItemStack is && !is.isEmpty()) {
                    this.itemStacks.add(is);
                } else if (obj instanceof FluidStack fs && !fs.isEmpty()) {
                    this.fluidStacks.add(fs);
                }
            }
            return this;
        }

        @Override
        public IRecipeSlotBuilder addIngredientsUnsafe(List<?> ingredients) {
            if (ingredients != null) {
                for (Object ing : ingredients) {
                    if (ing instanceof ItemStack is) {
                        addItemStack(is);
                    } else if (ing instanceof FluidStack fs) {
                        if (!fs.isEmpty()) this.fluidStacks.add(fs);
                    } else if (ing instanceof Ingredient in) {
                        addIngredients(in);
                    } else if (ing instanceof ITypedIngredient<?> ti) {
                        Object obj = ti.getIngredient();
                        if (obj instanceof ItemStack is && !is.isEmpty()) {
                            this.itemStacks.add(is);
                        } else if (obj instanceof FluidStack fs && !fs.isEmpty()) {
                            this.fluidStacks.add(fs);
                        }
                    }
                }
            }
            return this;
        }

        @Override
        public IRecipeSlotBuilder setBackground(IDrawable background, int xOffset, int yOffset) {
            return this;
        }

        @Override
        public IRecipeSlotBuilder setOverlay(IDrawable overlay, int xOffset, int yOffset) {
            return this;
        }

        @Override
        public IRecipeSlotBuilder setFluidRenderer(long capacity, boolean showCapacity, int width, int height) {
            return this;
        }

        @Override
        public <I> IRecipeSlotBuilder setCustomRenderer(IIngredientType<I> ingredientType, IIngredientRenderer<I> ingredientRenderer) {
            return this;
        }

        @Override
        public IRecipeSlotBuilder addTooltipCallback(IRecipeSlotTooltipCallback tooltipCallback) {
            return this;
        }

        @Override
        public IRecipeSlotBuilder setSlotName(String slotName) {
            this.slotName = slotName != null ? slotName : "";
            return this;
        }
    }

    private final List<CollectedSlot> slots = new ArrayList<>();
    private boolean shapeless = false;

    public List<CollectedSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    public boolean isShapeless() {
        return shapeless;
    }

    @Override
    public IRecipeSlotBuilder addSlot(RecipeIngredientRole role, int x, int y) {
        CollectedSlot slot = new CollectedSlot(role, x, y);
        this.slots.add(slot);
        return slot;
    }

    @Override
    public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole role) {
        return addSlot(role, 0, 0);
    }

    @Override
    public void moveRecipeTransferButton(int x, int y) {}

    @Override
    public void setShapeless() {
        this.shapeless = true;
    }

    @Override
    public void setShapeless(int x, int y) {
        this.shapeless = true;
    }

    @Override
    public void createFocusLink(IIngredientAcceptor<?>... acceptors) {}

    /**
     * Converts collected slots into resolved IngredientStack inputs.
     */
    public List<IngredientStack> extractInputs() {
        List<IngredientStack> list = new ArrayList<>();
        for (CollectedSlot slot : slots) {
            if (slot.getRole() == RecipeIngredientRole.INPUT || slot.getRole() == RecipeIngredientRole.CATALYST) {
                appendSlotIngredients(slot, list);
            }
        }
        return list;
    }

    /**
     * Converts collected slots into resolved IngredientStack outputs.
     */
    public List<IngredientStack> extractOutputs() {
        List<IngredientStack> list = new ArrayList<>();
        for (CollectedSlot slot : slots) {
            if (slot.getRole() == RecipeIngredientRole.OUTPUT) {
                appendSlotIngredients(slot, list);
            }
        }
        return list;
    }

    private void appendSlotIngredients(CollectedSlot slot, List<IngredientStack> target) {
        if (!slot.getFluidStacks().isEmpty()) {
            FluidStack fs = slot.getFluidStacks().get(0);
            if (fs != null && !fs.isEmpty()) {
                ResourceLocation fId = null;
                try {
                    fId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fs.getFluid());
                } catch (Throwable ignored) {}
                if (fId == null) {
                    try {
                        fId = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                    } catch (Throwable ignored) {}
                }
                if (fId != null) {
                    String name = "";
                    try {
                        name = fs.getDisplayName().getString();
                    } catch (Throwable ignored) {}
                    if (name.isEmpty()) {
                        name = JeiRecipeConverter.formatName(fId.getPath());
                    }
                    IngredientStack is = IngredientStack.fluid(fId, name, fs.getAmount());
                    for (int i = 1; i < slot.getFluidStacks().size(); i++) {
                        FluidStack altFs = slot.getFluidStacks().get(i);
                        if (altFs != null && !altFs.isEmpty()) {
                            ResourceLocation altId = null;
                            try {
                                altId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(altFs.getFluid());
                            } catch (Throwable ignored) {}
                            if (altId == null) {
                                try {
                                    altId = ForgeRegistries.FLUIDS.getKey(altFs.getFluid());
                                } catch (Throwable ignored) {}
                            }
                            if (altId != null && !is.getAlternatives().contains(altId)) {
                                is.getAlternatives().add(altId);
                            }
                        }
                    }
                    target.add(is);
                }
            }
        } else if (!slot.getItemStacks().isEmpty()) {
            ItemStack primary = slot.getItemStacks().get(0);
            if (primary != null && !primary.isEmpty()) {
                ResourceLocation iId = null;
                try {
                    iId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(primary.getItem());
                } catch (Throwable ignored) {}
                if (iId == null) {
                    try {
                        iId = ForgeRegistries.ITEMS.getKey(primary.getItem());
                    } catch (Throwable ignored) {}
                }
                if (iId != null) {
                    String name = "";
                    try {
                        name = primary.getHoverName().getString();
                    } catch (Throwable ignored) {}
                    if (name.isEmpty()) {
                        name = JeiRecipeConverter.formatName(iId.getPath());
                    }
                    IngredientStack is = IngredientStack.item(iId, name, primary.getCount());
                    for (int i = 1; i < slot.getItemStacks().size(); i++) {
                        ItemStack altIs = slot.getItemStacks().get(i);
                        if (altIs != null && !altIs.isEmpty()) {
                            ResourceLocation altId = null;
                            try {
                                altId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(altIs.getItem());
                            } catch (Throwable ignored) {}
                            if (altId == null) {
                                try {
                                    altId = ForgeRegistries.ITEMS.getKey(altIs.getItem());
                                } catch (Throwable ignored) {}
                            }
                            if (altId != null && !is.getAlternatives().contains(altId)) {
                                is.getAlternatives().add(altId);
                            }
                        }
                    }
                    target.add(is);
                }
            }
        }
    }
}
