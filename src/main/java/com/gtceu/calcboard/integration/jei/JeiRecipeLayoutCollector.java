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
import net.minecraft.world.item.Item;
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
            if (ingredient == null || ingredient.isEmpty()) return this;
            ItemStack[] items = ingredient.getItems();
            if (items == null) return this;
            for (ItemStack is : items) {
                addItemStackIfValid(is);
            }
            return this;
        }

        private void addItemStackIfValid(ItemStack is) {
            if (is != null && !is.isEmpty()) {
                this.itemStacks.add(is);
            }
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
            if (ingredients == null) return this;
            for (Object ing : ingredients) {
                addSingleIngredientUnsafe(ing);
            }
            return this;
        }

        private void addSingleIngredientUnsafe(Object ing) {
            if (ing instanceof ItemStack is) {
                addItemStack(is);
            } else if (ing instanceof FluidStack fs) {
                addFluidStackUnsafe(fs);
            } else if (ing instanceof Ingredient in) {
                addIngredients(in);
            } else if (ing instanceof ITypedIngredient<?> ti) {
                addTypedIngredient(ti);
            }
        }

        private void addFluidStackUnsafe(FluidStack fs) {
            if (!fs.isEmpty()) {
                this.fluidStacks.add(fs);
            }
        }

        private void addTypedIngredient(ITypedIngredient<?> ti) {
            Object obj = ti.getIngredient();
            if (obj instanceof ItemStack is && !is.isEmpty()) {
                this.itemStacks.add(is);
            } else if (obj instanceof FluidStack fs && !fs.isEmpty()) {
                this.fluidStacks.add(fs);
            }
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
        return slots;
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

    public static IRecipeLayoutBuilder createProxyBuilder(JeiRecipeLayoutCollector collector) {
        if (collector == null) return null;
        try {
            return (IRecipeLayoutBuilder) java.lang.reflect.Proxy.newProxyInstance(
                    IRecipeLayoutBuilder.class.getClassLoader(),
                    new Class<?>[]{IRecipeLayoutBuilder.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("addSlot".equals(name) || "addInputSlot".equals(name) || "addOutputSlot".equals(name) || "addSlotToWidget".equals(name)) {
                            SlotRoleAndCoordinates coords = parseSlotArgs(name, args);
                            CollectedSlot slot = new CollectedSlot(coords.role, coords.x, coords.y);
                            collector.slots.add(slot);
                            return createProxySlot(slot);
                        }
                        try {
                            var m = collector.getClass().getMethod(method.getName(), method.getParameterTypes());
                            Object res = m.invoke(collector, args);
                            if (res instanceof CollectedSlot slot) {
                                return createProxySlot(slot);
                            }
                            return res;
                        } catch (NoSuchMethodException e) {
                            if (IRecipeSlotBuilder.class.isAssignableFrom(method.getReturnType())) {
                                CollectedSlot slot = new CollectedSlot(RecipeIngredientRole.INPUT, 0, 0);
                                collector.slots.add(slot);
                                return createProxySlot(slot);
                            }
                            if (IIngredientAcceptor.class.isAssignableFrom(method.getReturnType())) {
                                CollectedSlot slot = new CollectedSlot(RecipeIngredientRole.INPUT, 0, 0);
                                collector.slots.add(slot);
                                return createProxySlot(slot);
                            }
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            return null;
                        }
                    }
            );
        } catch (Throwable t) {
            return collector;
        }
    }

    public static IRecipeSlotBuilder createProxySlot(CollectedSlot slot) {
        if (slot == null) return null;
        try {
            return (IRecipeSlotBuilder) java.lang.reflect.Proxy.newProxyInstance(
                    IRecipeSlotBuilder.class.getClassLoader(),
                    new Class<?>[]{IRecipeSlotBuilder.class},
                    (proxy, method, args) -> {
                        try {
                            var m = slot.getClass().getMethod(method.getName(), method.getParameterTypes());
                            Object res = m.invoke(slot, args);
                            if (res == slot) return proxy;
                            return res;
                        } catch (NoSuchMethodException e) {
                            if (IRecipeSlotBuilder.class.isAssignableFrom(method.getReturnType())
                                    || IIngredientAcceptor.class.isAssignableFrom(method.getReturnType())
                                    || method.getReturnType().getName().endsWith("IPlaceable")
                                    || method.getReturnType().isAssignableFrom(proxy.getClass())) {
                                return proxy;
                            }
                            if (method.getReturnType() == int.class) return 16;
                            if (method.getReturnType() == boolean.class) return false;
                            return null;
                        }
                    }
            );
        } catch (Throwable t) {
            return slot;
        }
    }

    private void appendSlotIngredients(CollectedSlot slot, List<IngredientStack> target) {
        if (!slot.getFluidStacks().isEmpty()) {
            addSlotFluidIngredient(slot, target);
        } else if (!slot.getItemStacks().isEmpty()) {
            addSlotItemIngredient(slot, target);
        }
    }

    private static void addSlotFluidIngredient(CollectedSlot slot, List<IngredientStack> target) {
        FluidStack fs = slot.getFluidStacks().get(0);
        if (fs == null || fs.isEmpty()) return;
        ResourceLocation fId = getFluidIdQuietly(fs.getFluid());
        if (fId == null) return;

        String name = resolveFluidDisplayName(fs, fId);
        IngredientStack is = IngredientStack.fluid(fId, name, fs.getAmount());
        for (int i = 1; i < slot.getFluidStacks().size(); i++) {
            addAlternativeFluid(is, slot.getFluidStacks().get(i));
        }
        target.add(is);
    }

    private static void addAlternativeFluid(IngredientStack is, FluidStack altFs) {
        if (altFs == null || altFs.isEmpty()) return;
        ResourceLocation altId = getFluidIdQuietly(altFs.getFluid());
        if (altId != null && !is.getAlternatives().contains(altId)) {
            is.getAlternatives().add(altId);
        }
    }

    private static ResourceLocation getFluidIdQuietly(Fluid fluid) {
        try {
            return ForgeRegistries.FLUIDS.getKey(fluid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveFluidDisplayName(FluidStack fs, ResourceLocation id) {
        try {
            String name = fs.getDisplayName().getString();
            if (!name.isEmpty()) return name;
        } catch (Throwable ignored) {}
        return JeiRecipeConverter.formatName(id.getPath());
    }

    private static void addSlotItemIngredient(CollectedSlot slot, List<IngredientStack> target) {
        ItemStack primary = slot.getItemStacks().get(0);
        if (primary == null || primary.isEmpty()) return;
        ResourceLocation iId = getItemIdQuietly(primary.getItem());
        if (iId == null) return;

        String name = resolveItemDisplayName(primary, iId);
        IngredientStack is = IngredientStack.item(iId, name, primary.getCount());
        for (int i = 1; i < slot.getItemStacks().size(); i++) {
            addAlternativeItem(is, slot.getItemStacks().get(i));
        }
        target.add(is);
    }

    private static void addAlternativeItem(IngredientStack is, ItemStack altIs) {
        if (altIs == null || altIs.isEmpty()) return;
        ResourceLocation altId = getItemIdQuietly(altIs.getItem());
        if (altId != null && !is.getAlternatives().contains(altId)) {
            is.getAlternatives().add(altId);
        }
    }

    private static ResourceLocation getItemIdQuietly(Item item) {
        try {
            return ForgeRegistries.ITEMS.getKey(item);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveItemDisplayName(ItemStack stack, ResourceLocation id) {
        try {
            String name = stack.getHoverName().getString();
            if (!name.isEmpty()) return name;
        } catch (Throwable ignored) {}
        return JeiRecipeConverter.formatName(id.getPath());
    }

    private record SlotRoleAndCoordinates(RecipeIngredientRole role, int x, int y) {}

    private static SlotRoleAndCoordinates parseSlotArgs(String methodName, Object[] args) {
        RecipeIngredientRole role = "addOutputSlot".equals(methodName) ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
        int x = 0, y = 0;
        if (args == null) return new SlotRoleAndCoordinates(role, x, y);
        for (Object arg : args) {
            if (arg instanceof RecipeIngredientRole r) {
                role = r;
            } else if (arg instanceof Integer i) {
                if (x == 0) x = i;
                else y = i;
            }
        }
        return new SlotRoleAndCoordinates(role, x, y);
    }
}
