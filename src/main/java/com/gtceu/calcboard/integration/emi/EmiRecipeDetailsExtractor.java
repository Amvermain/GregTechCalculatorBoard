package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler;
import com.gtceu.calcboard.compat.systeams.SysteamsModAdapter;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class EmiRecipeDetailsExtractor {

    private EmiRecipeDetailsExtractor() {}

    public static EmiRecipeConverter.RecipeDetails extractRecipeDetails(EmiRecipe recipe, ResourceLocation preferredWorkstation) {
        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        try {
            Object backing = unwrapBackingRecipe(recipe);
            ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;

            if (preferredWorkstation != null && "gtceu".equals(preferredWorkstation.getNamespace())) {
                IModAdapter gtAdapter = ModAdapterRegistry.getAdapterForModId("gtceu");
                if (gtAdapter != null && gtAdapter.adaptRecipeDetails(recipe, backing, details)) {
                    return details;
                }
            } else if (preferredWorkstation != null && "systeams".equals(preferredWorkstation.getNamespace())) {
                if (SysteamsModAdapter.adaptBoilerRecipe(backing, details, catId)) {
                    return details;
                }
            }

            IModAdapter adapter = ModAdapterRegistry.getAdapterForCategory(catId);
            boolean handled = adapter.adaptRecipeDetails(recipe, backing, details);

            if (!handled && backing != null) {
                if (GTCEuRecipeHandler.isGTRecipe(backing)) {
                    GTCEuRecipeHandler.extractGTRecipeDetails(backing, details);
                } else {
                    handled = tryLoadedAdapters(recipe, backing, details, adapter);
                    if (!handled && backing instanceof AbstractCookingRecipe acr) {
                        details.durationTicks = acr.getCookingTime();
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!details.isGenerator && recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            details.isGenerator = isGeneratorCategory(recipe.getCategory().getId());
        }

        return details;
    }

    private static boolean tryLoadedAdapters(EmiRecipe recipe, Object backing, EmiRecipeConverter.RecipeDetails details, IModAdapter exclude) {
        for (IModAdapter a : ModAdapterRegistry.getAllLoadedAdapters()) {
            if (a != exclude && a.adaptRecipeDetails(recipe, backing, details)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGeneratorCategory(ResourceLocation catId) {
        String catPath = catId.getPath().toLowerCase();
        String catNs = catId.getNamespace().toLowerCase();
        return catPath.contains("dynamo") || catPath.contains("turbine")
                || "generator".equals(catPath) || catPath.endsWith("_generator")
                || "combustion_generator".equals(catPath) || "semi_fluid_generator".equals(catPath)
                || "gas_turbine".equals(catPath) || "steam_turbine".equals(catPath) || "plasma_generator".equals(catPath)
                || (("thermal".equals(catNs) || "thermal_expansion".equals(catNs) || "systeams".equals(catNs)) && catPath.contains("fuel"));
    }

    public static Object unwrapBackingRecipe(EmiRecipe recipe) {
        if (recipe == null) return null;
        Object backing = recipe.getBackingRecipe();
        if (backing != null) {
            return unwrapInnerRecipe(backing);
        }

        Object unwrapped = scanFieldsForRecipe(recipe);
        if (unwrapped != null) {
            return unwrapped;
        }

        return lookupRecipeFromRecipeManager(recipe);
    }

    private static Object scanFieldsForRecipe(EmiRecipe recipe) {
        Class<?> cur = recipe.getClass();
        while (cur != null && cur != Object.class) {
            Object res = scanFieldNames(recipe, cur);
            if (res != null) return res;
            res = scanMethodNames(recipe, cur);
            if (res != null) return res;
            res = scanDeclaredFields(recipe, cur);
            if (res != null) return res;
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Object scanFieldNames(EmiRecipe recipe, Class<?> cur) {
        String[] names = {"recipe", "gtRecipe", "backingRecipe", "originalRecipe", "target", "source", "value", "delegate"};
        for (String fName : names) {
            try {
                Field f = cur.getDeclaredField(fName);
                f.setAccessible(true);
                Object res = f.get(recipe);
                if (res != null && res != recipe) {
                    return unwrapInnerRecipe(res);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object scanMethodNames(EmiRecipe recipe, Class<?> cur) {
        String[] names = {"getRecipe", "recipe", "getGTRecipe", "gtRecipe", "getOriginalRecipe", "originalRecipe", "getValue", "value"};
        for (String mName : names) {
            try {
                Method m = cur.getDeclaredMethod(mName);
                m.setAccessible(true);
                Object res = m.invoke(recipe);
                if (res != null && res != recipe) {
                    return unwrapInnerRecipe(res);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object scanDeclaredFields(EmiRecipe recipe, Class<?> cur) {
        for (Field f : cur.getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(recipe);
                if (val != null && val != recipe && val instanceof Recipe<?>) {
                    return unwrapInnerRecipe(val);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object lookupRecipeFromRecipeManager(EmiRecipe recipe) {
        ResourceLocation id = recipe.getId();
        if (id == null) return null;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return null;
            RecipeManager rm = mc.level.getRecipeManager();
            if (rm == null) return null;

            var direct = rm.byKey(id);
            if (direct.isPresent()) {
                return unwrapInnerRecipe(direct.get());
            }

            String path = id.getPath();
            if (path.contains("automatic_packing/")) {
                String stripped = path.replace("automatic_packing/", "");
                ResourceLocation cleanId = ResourceLocation.tryParse(id.getNamespace() + ":" + stripped);
                if (cleanId != null) {
                    var cleanRecipe = rm.byKey(cleanId);
                    if (cleanRecipe.isPresent()) {
                        return unwrapInnerRecipe(cleanRecipe.get());
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object unwrapInnerRecipe(Object obj) {
        if (obj == null) return null;
        Object cur = obj;
        for (int i = 0; i < 3; i++) {
            boolean unwrapped = false;
            Class<?> cl = cur.getClass();
            for (String mName : new String[]{"getRecipe", "value", "recipe"}) {
                try {
                    Method m = cl.getMethod(mName);
                    Object next = m.invoke(cur);
                    if (next != null && next != cur) {
                        cur = next;
                        unwrapped = true;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (!unwrapped) break;
        }
        return cur;
    }
}
