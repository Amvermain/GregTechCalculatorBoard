package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.type.GTThreadingHelix;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class GTCEuMultiblockScanner {

    public static void scan(Object emiRecipeManager) {
        if (!ModCompatHelper.isGTLoaded()) {
            return;
        }

        scanGTCEuRegistries();
        scanGTCEuEmiRecipes(emiRecipeManager);
    }

    private static void scanGTCEuRegistries() {
        try {
            Iterable<?> iterable = GTCEuReflectionBridge.getMachinesRegistryIterable();
            if (iterable == null) return;

            for (Object def : iterable) {
                if (def == null) continue;
                processScannedMachineDefinition(def);
            }
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] [GTCEuMultiblockScanner] GTCEu Registry scan failed: {}", t.getMessage());
        }
    }

    private static void processScannedMachineDefinition(Object def) {
        ResourceLocation id = GTCEuReflectionBridge.getMachineId(def);
        if (id == null) return;

        GTCEuMachineAnalyzer.MachineCapabilities caps = GTCEuMachineAnalyzer.analyze(id, def);
        MultiblockDetector.registerMachineCapabilities(id, caps);

        registerMachineRecipeCategories(id, def, caps);
    }

    private static void registerMachineRecipeCategories(ResourceLocation id, Object def, GTCEuMachineAnalyzer.MachineCapabilities caps) {
        if (caps.isTurbine()) {
            MultiblockDetector.registerTurbine(id, null, caps.turbineTier(), caps.turbineBaseEnergy());
        }

        List<Object> recipeTypesList = GTCEuReflectionBridge.getRecipeTypes(def);
        for (Object rt : recipeTypesList) {
            ResourceLocation rl = MultiblockDetector.extractRecipeTypeId(rt);
            if (rl == null) continue;

            MultiblockDetector.inspectAndRegisterMachine(id, def, rl);
            if (caps.isCoilWorkable()) {
                MultiblockDetector.registerCoilMultiblock(id, rl);
            }
            if (caps.isTurbine()) {
                MultiblockDetector.registerTurbine(id, rl, caps.turbineTier(), caps.turbineBaseEnergy());
            }

            ResourceLocation relRl = GTCEuCapabilityScanner.getRelatedRecipeCategory(rl);
            if (relRl != null && !relRl.equals(rl)) {
                MultiblockDetector.inspectAndRegisterMachine(id, def, relRl);
                if (caps.isCoilWorkable()) {
                    MultiblockDetector.registerCoilMultiblock(id, relRl);
                }
                if (caps.isTurbine()) {
                    MultiblockDetector.registerTurbine(id, relRl, caps.turbineTier(), caps.turbineBaseEnergy());
                }
            }
        }
    }

    private static void scanGTCEuEmiRecipes(Object rmObj) {
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) return;
        EmiGTCEuScanner.scan(rmObj);
    }

    private static class EmiGTCEuScanner {
        private static void scan(Object rmObj) {
            try {
                if (rmObj == null && com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) {
                    try {
                        rmObj = dev.emi.emi.api.EmiApi.getRecipeManager();
                    } catch (Throwable ignored) {}
                }
                if (rmObj == null) return;

                if (rmObj instanceof dev.emi.emi.api.recipe.EmiRecipeManager emiManager) {
                    if (emiManager.getCategories() != null) {
                        for (dev.emi.emi.api.recipe.EmiRecipeCategory cat : emiManager.getCategories()) {
                            if (cat == null || cat.getId() == null) continue;
                            String catPath = cat.getId().getPath();
                            if (catPath.equals("multiblock_info") || catPath.contains("multiblock")) {
                                List<dev.emi.emi.api.recipe.EmiRecipe> mbRecipes = emiManager.getRecipes(cat);
                                if (mbRecipes != null) {
                                    for (dev.emi.emi.api.recipe.EmiRecipe recipe : mbRecipes) {
                                        processGTCEuEmiMultiblockRecipe(recipe);
                                    }
                                }
                            }
                        }
                    }
                    return;
                }

                Iterable<?> recipes = null;
                if (rmObj instanceof Iterable<?> it) {
                    recipes = it;
                } else {
                    try {
                        Method mGetRecipes = rmObj.getClass().getMethod("getRecipes");
                        Object res = mGetRecipes.invoke(rmObj);
                        if (res instanceof Iterable<?> it) recipes = it;
                    } catch (Throwable ignored) {}
                }

                if (recipes != null) {
                    for (Object recipeObj : recipes) {
                        if (recipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe) {
                            if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
                                String catPath = recipe.getCategory().getId().getPath();
                                if (catPath.equals("multiblock_info") || catPath.contains("multiblock")) {
                                    processGTCEuEmiMultiblockRecipe(recipe);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        private static void processGTCEuEmiMultiblockRecipe(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            if (recipe == null) return;
            ResourceLocation controllerId = null;

            if (recipe.getId() != null) {
                String rPath = recipe.getId().getPath();
                if (rPath.contains("/")) {
                    String machineName = rPath.substring(rPath.lastIndexOf('/') + 1);
                    controllerId = ResourceLocation.tryParse(recipe.getId().getNamespace() + ":" + machineName);
                } else {
                    controllerId = recipe.getId();
                }
            }

            if (controllerId == null && recipe.getOutputs() != null) {
                for (var es : recipe.getOutputs()) {
                    if (es != null && es.getId() != null) {
                        controllerId = es.getId();
                        break;
                    }
                }
            }

            if (controllerId == null) return;

            int helixCount = 0;
            if (recipe.getInputs() != null) {
                for (var ei : recipe.getInputs()) {
                    if (ei == null || ei.getEmiStacks() == null) continue;
                    for (var es : ei.getEmiStacks()) {
                        if (es != null) {
                            ItemStack stack = es.getItemStack();
                            if (stack != null && !stack.isEmpty()) {
                                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                                if (itemId != null) {
                                    if (GTThreadingHelix.fromId(itemId) != null || GTThreadingHelix.fromId(itemId.toString()) != null) {
                                        helixCount = Math.max(helixCount, (int) es.getAmount());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (helixCount > 0) {
                MultiblockDetector.registerThreadingMultiblock(controllerId, helixCount);
            }
        }
    }

    private static ResourceLocation extractRecipeTypeId(Object rt) {
        if (rt == null) return null;
        if (rt instanceof ResourceLocation rl) return rl;
        try {
            if (rt instanceof RecipeType<?> rType) {
                ResourceLocation loc = ForgeRegistries.RECIPE_TYPES.getKey(rType);
                if (loc != null && !loc.getPath().equals("air")) return loc;
            }
        } catch (Throwable ignored) {}
        try {
            Method mGetId = rt.getClass().getMethod("getId");
            Object idVal = mGetId.invoke(rt);
            if (idVal instanceof ResourceLocation rl) return rl;
        } catch (Throwable ignored) {}
        try {
            Method mRegistryName = rt.getClass().getMethod("getRegistryName");
            Object idVal = mRegistryName.invoke(rt);
            if (idVal instanceof ResourceLocation rl) return rl;
        } catch (Throwable ignored) {}
        return null;
    }

    private static Iterable<?> getRegistryIterable(Object registry) {
        if (registry == null) return null;
        if (registry instanceof Iterable<?> iterable) {
            return iterable;
        }
        try {
            Method valuesMethod = registry.getClass().getMethod("values");
            Object result = valuesMethod.invoke(registry);
            if (result instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static GTVoltageTier extractTurbineBaseTier(ResourceLocation id, Object def) {
        GTVoltageTier registered = MultiblockDetector.getTurbineBaseTier(id);
        if (registered != null && registered != GTVoltageTier.ULV) {
            return registered;
        }
        GTVoltageTier fromDef = GTCEuReflectionBridge.getMachineTier(def);
        if (fromDef != null) {
            return fromDef;
        }
        return GTVoltageTier.HV;
    }

    private static double extractBaseEnergyFromDefinition(ResourceLocation id, Object def, GTVoltageTier tier) {
        Double registered = MultiblockDetector.getTurbineBaseProduction(id);
        if (registered != null && registered > 0) {
            return registered;
        }
        return GTCEuReflectionBridge.getTurbineBaseEnergy(def, tier);
    }
}



