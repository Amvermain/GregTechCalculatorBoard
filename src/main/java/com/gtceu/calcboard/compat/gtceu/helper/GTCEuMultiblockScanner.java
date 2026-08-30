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
            Class<?> multiblockDefCls = null;
            try {
                multiblockDefCls = Class.forName("com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition");
            } catch (Throwable ignored) {}

            Class<?> coilWorkableCls = null;
            try {
                coilWorkableCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.electric.CoilWorkableElectricMultiblockMachine");
            } catch (Throwable ignored) {}

            Class<?> largeTurbineCls = null;
            try {
                largeTurbineCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.generator.LargeTurbineMachine");
            } catch (Throwable ignored) {}

            Class<?> iTurbineCls = null;
            try {
                iTurbineCls = Class.forName("com.gregtechceu.gtceu.api.machine.feature.multiblock.ITurbineMachine");
            } catch (Throwable ignored) {}

            Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
            Iterable<?> iterable = getRegistryIterable(machinesRegistry);

            if (iterable == null) return;

            for (Object def : iterable) {
                if (def == null) continue;
                try {
                    Method mGetId = def.getClass().getMethod("getId");
                    ResourceLocation id = (ResourceLocation) mGetId.invoke(def);
                    if (id == null) continue;

                    boolean isMb = MultiblockDetector.inspectAndRegisterMachine(id, def, null);

                    Class<?> mCls = null;
                    try {
                        Method mGetMachineClass = def.getClass().getMethod("getMachineClass");
                        mCls = (Class<?>) mGetMachineClass.invoke(def);
                    } catch (Throwable ignored) {}

                    var structDef = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructureCached(id);

                    boolean isCoilMb = isMb && ((coilWorkableCls != null && mCls != null && coilWorkableCls.isAssignableFrom(mCls))
                            || (structDef != null && (structDef.coilSlotCount() > 0 || structDef.supportsAbility("HEATING_COILS"))));

                    boolean isTurbineMb = isMb && ((largeTurbineCls != null && mCls != null && largeTurbineCls.isAssignableFrom(mCls))
                            || (iTurbineCls != null && mCls != null && iTurbineCls.isAssignableFrom(mCls))
                            || (structDef != null && (structDef.supportsAbility("ROTOR_HOLDER") || structDef.supportsAbility("TURBINE_ROTOR"))));

                    if (isCoilMb) {
                        MultiblockDetector.registerCoilMultiblock(id, null);
                    }

                    if (isTurbineMb) {
                        GTVoltageTier turbineTier = null;
                        for (String mName : new String[]{"getTier", "tier", "getBaseTier"}) {
                            try {
                                Method m = def.getClass().getMethod(mName);
                                Object tVal = m.invoke(def);
                                if (tVal instanceof Number num) {
                                    int tIdx = num.intValue();
                                    if (tIdx >= 0 && tIdx < GTVoltageTier.values().length) {
                                        turbineTier = GTVoltageTier.values()[tIdx];
                                        break;
                                    }
                                } else if (tVal instanceof Enum<?> en) {
                                    try {
                                        turbineTier = GTVoltageTier.valueOf(en.name());
                                        break;
                                    } catch (Throwable ignored) {}
                                }
                            } catch (Throwable ignored) {}
                        }

                        double baseEnergy = turbineTier != null ? (double) (turbineTier.getVoltage() * 2L) : 4096.0;
                        for (String mName : new String[]{"getBaseEnergyPerTick", "getBaseEnergy", "getBaseEUt", "getEnergyCapacity"}) {
                            try {
                                Method m = def.getClass().getMethod(mName);
                                Object eVal = m.invoke(def);
                                if (eVal instanceof Number num && num.doubleValue() > 0) {
                                    baseEnergy = num.doubleValue();
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }

                        MultiblockDetector.registerTurbine(id, null, turbineTier, baseEnergy);
                    }

                    boolean isSteamMb = def.getClass().getName().toLowerCase(Locale.ROOT).contains("steam")
                            || (id.getPath().startsWith("steam_") && isMb)
                            || def.getClass().getSimpleName().contains("SteamParallel");

                    if (isSteamMb) {
                        double steamDrainRate = 64.0;
                        boolean foundCustomRate = false;
                        for (String mName : new String[]{"getSteamDrainRate", "getSteamDrain", "getSteamConsumption", "getSteamPerTick", "getSteamRate", "getBaseSteamRate", "getConversionRate"}) {
                            try {
                                Method m = def.getClass().getMethod(mName);
                                Object sVal = m.invoke(def);
                                if (sVal instanceof Number num && num.doubleValue() > 0) {
                                    steamDrainRate = num.doubleValue();
                                    foundCustomRate = true;
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }

                        if (!foundCustomRate) {
                            for (String fName : new String[]{"STEAM_DRAIN_RATE", "STEAM_PER_TICK", "STEAM_CONSUMPTION", "DEFAULT_STEAM_DRAIN_RATE", "STEAM_DRAIN"}) {
                                try {
                                    Field f = def.getClass().getField(fName);
                                    Object sVal = f.get(null);
                                    if (sVal instanceof Number num && num.doubleValue() > 0) {
                                        steamDrainRate = num.doubleValue();
                                        foundCustomRate = true;
                                        break;
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }

                        int innatePar = 8;
                        for (String mName : new String[]{"getParallelAmount", "getSteamParallel", "getBaseParallel", "getParallels", "getParallelCount", "getDefaultParallel"}) {
                            try {
                                Method m = def.getClass().getMethod(mName);
                                Object pVal = m.invoke(def);
                                if (pVal instanceof Number num && num.intValue() > 1) {
                                    innatePar = num.intValue();
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }

                        MultiblockDetector.registerSteamMultiblock(id, innatePar, steamDrainRate);
                    }

                    if (!isSteamMb) {
                        int innatePar = 1;
                        for (String mName : new String[]{"getParallelAmount", "getSteamParallel", "getBaseParallel", "getParallels", "getParallelCount", "getDefaultParallel"}) {
                            try {
                                Method m = def.getClass().getMethod(mName);
                                Object pVal = m.invoke(def);
                                if (pVal instanceof Number num && num.intValue() > 1) {
                                    innatePar = num.intValue();
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (innatePar > 1) {
                            MultiblockDetector.registerDefaultParallel(id, innatePar);
                        }
                    }

                    List<Object> recipeTypesList = new java.util.ArrayList<>();
                    try {
                        Method mGetRecipeTypes = def.getClass().getMethod("getRecipeTypes");
                        Object rTypes = mGetRecipeTypes.invoke(def);
                        if (rTypes instanceof Object[] arr) {
                            for (Object rt : arr) {
                                if (rt != null) recipeTypesList.add(rt);
                            }
                        } else if (rTypes instanceof Iterable<?> it) {
                            for (Object rt : it) {
                                if (rt != null) recipeTypesList.add(rt);
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (recipeTypesList.isEmpty()) {
                        try {
                            Method mGetRecipeType = def.getClass().getMethod("getRecipeType");
                            Object rt = mGetRecipeType.invoke(def);
                            if (rt != null) recipeTypesList.add(rt);
                        } catch (Throwable ignored) {}
                    }

                    for (Object rt : recipeTypesList) {
                        ResourceLocation rl = MultiblockDetector.extractRecipeTypeId(rt);
                        if (rl != null) {
                            MultiblockDetector.inspectAndRegisterMachine(id, def, rl);
                            if (isCoilMb) {
                                MultiblockDetector.registerCoilMultiblock(id, rl);
                            }
                            if (isTurbineMb) {
                                MultiblockDetector.registerTurbine(id, rl, null, 0.0);
                            }

                            ResourceLocation relRl = GTCEuCapabilityScanner.getRelatedRecipeCategory(rl);
                            if (relRl != null && !relRl.equals(rl)) {
                                MultiblockDetector.inspectAndRegisterMachine(id, def, relRl);
                                if (isCoilMb) {
                                    MultiblockDetector.registerCoilMultiblock(id, relRl);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] [GTCEuMultiblockScanner] GTCEu Registry scan failed: {}", t.getMessage());
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

            boolean usesCoilBlock = false;
            if (recipe.getInputs() != null) {
                for (var ei : recipe.getInputs()) {
                    if (ei == null || ei.getEmiStacks() == null) continue;
                    for (var es : ei.getEmiStacks()) {
                        if (es != null) {
                            ItemStack stack = es.getItemStack();
                            if (stack != null && !stack.isEmpty()) {
                                CoilHelper.CoilStats stats = CoilHelper.getCoilStats(stack);
                                if (stats != null && stats.temperature() > 0) {
                                    usesCoilBlock = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (usesCoilBlock) break;
                }
            }

            if (usesCoilBlock) {
                if (!controllerId.equals(ResourceLocation.tryParse("gtceu:rock_filtrator")) && !controllerId.equals(ResourceLocation.tryParse("gtceu:geode_filter"))) {
                    MultiblockDetector.registerCoilMultiblock(controllerId, null);
                }
            }

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
                                    String p = itemId.getPath();
                                    if (GTThreadingHelix.fromId(itemId.toString()) != null || p.contains("thread_helix") || p.contains("threading_helix")
                                            || p.contains("threading_controller") || p.contains("supreme_helix") || p.contains("overdrive_helix")
                                            || p.contains("coprocessor_helix") || p.contains("weaver_helix")) {
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
}



