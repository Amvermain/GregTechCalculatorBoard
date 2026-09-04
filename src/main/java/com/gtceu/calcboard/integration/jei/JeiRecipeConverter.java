package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.create.CreateRecipeHandler;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler;
import com.gtceu.calcboard.compat.gtceu.GTCEuLayeredRecipeExtractor;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler;
import com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler;
import com.gtceu.calcboard.compat.thermal.ThermalRecipeHandler;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts JEI recipes and categories into standard RecipeNode domain models.
 */
public class JeiRecipeConverter {

    private static final Map<ResourceLocation, ResourceLocation> VANILLA_CATEGORY_ICON_FALLBACKS = Map.of(
            ResourceLocation.tryParse("minecraft:smelting"), ResourceLocation.tryParse("minecraft:furnace"),
            ResourceLocation.tryParse("minecraft:blasting"), ResourceLocation.tryParse("minecraft:blast_furnace"),
            ResourceLocation.tryParse("minecraft:smoking"), ResourceLocation.tryParse("minecraft:smoker"),
            ResourceLocation.tryParse("minecraft:crafting"), ResourceLocation.tryParse("minecraft:crafting_table"),
            ResourceLocation.tryParse("minecraft:stonecutting"), ResourceLocation.tryParse("minecraft:stonecutter"),
            ResourceLocation.tryParse("minecraft:smithing"), ResourceLocation.tryParse("minecraft:smithing_table"),
            ResourceLocation.tryParse("minecraft:brewing"), ResourceLocation.tryParse("minecraft:brewing_stand"),
            ResourceLocation.tryParse("minecraft:campfire_cooking"), ResourceLocation.tryParse("minecraft:campfire"),
            ResourceLocation.tryParse("minecraft:anvil"), ResourceLocation.tryParse("minecraft:anvil")
    );

    public static <T> RecipeNode convert(JeiRecipeWrapper<T> wrapper) {
        if (wrapper == null) return null;
        return convert(wrapper.category(), wrapper.recipe(), null);
    }

    public static <T> RecipeNode convert(IRecipeCategory<T> category, T recipe) {
        return convert(category, recipe, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> RecipeNode convert(IRecipeCategory<T> category, T recipe, ResourceLocation preferredWorkstation) {
        if (recipe == null) return null;
        if (recipe instanceof RecipeNode rn) {
            return rn.copy();
        }

        ResourceLocation catId = (category != null && category.getRecipeType() != null)
                ? category.getRecipeType().getUid()
                : null;

        String catName = "";
        if (category != null) {
            try {
                Component comp = category.getTitle();
                if (comp != null) catName = comp.getString();
            } catch (Throwable ignored) {}
        }
        if (catName.isEmpty() && catId != null) {
            catName = formatName(catId.getPath());
        }

        // Extract recipe inputs & outputs via Layout Collector
        JeiRecipeLayoutCollector collector = new JeiRecipeLayoutCollector();
        if (category != null) {
            try {
                mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder = JeiRecipeLayoutCollector.createProxyBuilder(collector);
                category.setRecipe(builder, recipe, JeiRecipeLayoutCollector.EmptyFocusGroup.INSTANCE);
            } catch (Throwable ignored) {}
        }

        List<IngredientStack> inputs = new ArrayList<>();
        List<IngredientStack> outputs = new ArrayList<>();

        boolean isGT = GTCEuRecipeHandler.isGTRecipe(recipe) || (catId != null && GTCEuRecipeHandler.isGTCategoryNamespace(catId.getNamespace()));
        if (isGT) {
            List<IngredientStack> gtIns = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "inputs");
            List<IngredientStack> gtOuts = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "outputs");
            if (gtIns != null && !gtIns.isEmpty()) inputs.addAll(gtIns);
            if (gtOuts != null && !gtOuts.isEmpty()) outputs.addAll(gtOuts);
        }

        if (inputs.isEmpty()) {
            inputs.addAll(collector.extractInputs());
        }
        if (outputs.isEmpty()) {
            outputs.addAll(collector.extractOutputs());
        }

        // Synchronize chance and tierChanceBoost for outputs if extracted via layout collector
        if (isGT && !outputs.isEmpty()) {
            List<IngredientStack> gtOuts = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "outputs");
            if (gtOuts != null && !gtOuts.isEmpty()) {
                boolean[] used = new boolean[gtOuts.size()];
                for (int i = 0; i < outputs.size(); i++) {
                    IngredientStack out = outputs.get(i);
                    if (out == null || out.getId() == null) continue;
                    if (i < gtOuts.size() && !used[i] && gtOuts.get(i) != null && out.getId().equals(gtOuts.get(i).getId())) {
                        out.setChance(gtOuts.get(i).getChance());
                        out.setTierChanceBoost(gtOuts.get(i).getTierChanceBoost());
                        used[i] = true;
                    } else {
                        for (int j = 0; j < gtOuts.size(); j++) {
                            if (!used[j] && gtOuts.get(j) != null && out.getId().equals(gtOuts.get(j).getId())) {
                                out.setChance(gtOuts.get(j).getChance());
                                out.setTierChanceBoost(gtOuts.get(j).getTierChanceBoost());
                                used[j] = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Vanilla Recipe Fallback extraction (Smelting, Crafting, Blasting, Stonecutting, etc.)
        if (inputs.isEmpty() || outputs.isEmpty()) {
            extractVanillaRecipeContents(recipe, inputs, outputs);
        }

        // Details extraction
        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        if (isGT) {
            GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);
            GTCEuRecipeHandler.adaptRecipeDetails(null, recipe, details);
        } else if (ModCompatHelper.isThermalLoaded() && catId != null && "thermal".equals(catId.getNamespace())) {
            ThermalRecipeHandler.adaptRecipeDetails(null, recipe, details);
        } else if (ModCompatHelper.isCreateLoaded() && catId != null && !"create_new_age".equals(catId.getNamespace()) && ModCompatHelper.isCreateFamilyNamespace(catId.getNamespace())) {
            CreateRecipeHandler.adaptRecipeDetails(null, recipe, details);
        } else if (ModCompatHelper.isCreateNewAgeLoaded() && catId != null && CreateNewAgeRecipeHandler.MOD_ID.equals(catId.getNamespace())) {
            CreateNewAgeRecipeHandler.adaptRecipeDetails(null, recipe, details);
        }

        if (catId != null && "systeams".equals(catId.getNamespace())) {
            var adapter = ModAdapterRegistry.getAdapterForModId("systeams");
            if (adapter != null) {
                adapter.adaptRecipeDetails(null, recipe, details);
            }
        }

        if (recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cr) {
            details.durationTicks = cr.getCookingTime();
            details.eut = 0.0;
            details.energyType = EnergyType.NONE;
        } else if (catId != null && GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(catId)) {
            details.durationTicks = 200.0;
            details.eut = 0.0;
            details.energyType = EnergyType.NONE;
        } else if (catId != null && (catId.equals(ResourceLocation.tryParse("minecraft:crafting")) || catId.equals(ResourceLocation.tryParse("minecraft:stonecutting")))) {
            details.durationTicks = 0.0;
            details.eut = 0.0;
            details.energyType = EnergyType.NONE;
        }

        if (details.overrideOutputs && !details.customOutputs.isEmpty()) {
            outputs.clear();
            outputs.addAll(details.customOutputs);
        }
        if (!details.extraInputs.isEmpty()) {
            inputs.addAll(details.extraInputs);
        }

        String inputItemName = !inputs.isEmpty() ? inputs.get(0).getDisplayName() : (!outputs.isEmpty() ? outputs.get(0).getDisplayName() : null);
        String name;
        if (preferredWorkstation != null) {
            String wsName = formatName(preferredWorkstation.getPath());
            name = (inputItemName != null) ? wsName + " (" + inputItemName + ")" : wsName;
        } else if (!catName.isEmpty()) {
            name = (inputItemName != null) ? catName + " (" + inputItemName + ")" : catName;
        } else if (inputItemName != null) {
            name = inputItemName + " Recipe";
        } else {
            name = "Recipe";
        }

        RecipeNode node = RecipeNode.create(preferredWorkstation != null ? preferredWorkstation : catId, name, details.durationTicks, details.eut, details.tier);
        node.setGenerator(details.isGenerator);
        if (details.energyType != null && details.energyType != EnergyType.ELECTRIC_EU) {
            node.setEnergyType(details.energyType);
        }
        node.getAvailableWorkstations().clear();

        if (catId != null) {
            node.setRecipeCategoryId(catId);
            com.gtceu.calcboard.api.catalog.CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(catId);
            if (cap != null && !cap.availableWorkstations().isEmpty()) {
                node.getAvailableWorkstations().addAll(cap.availableWorkstations());
                if (cap.hasMultiblockOption() && !cap.hasSingleblockOption()) {
                    node.setMultiblock(true);
                }
            }
        }

        // Workstations from JEI catalysts
        IJeiRuntime runtime = JeiRecipeViewerAdapter.getJeiRuntime();
        if (runtime != null && category != null && category.getRecipeType() != null) {
            try {
                var catalystLookup = runtime.getRecipeManager().createRecipeCatalystLookup(category.getRecipeType());
                if (catalystLookup != null) {
                    var catalysts = catalystLookup.get().toList();
                    for (var cat : catalysts) {
                        if (cat != null) {
                            ItemStack is = cat.getItemStack().orElse(ItemStack.EMPTY);
                            if (is.isEmpty() && cat.getIngredient() instanceof ItemStack s) {
                                is = s;
                            }
                            if (!is.isEmpty()) {
                                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(is.getItem());
                                if (itemId != null && !node.getAvailableWorkstations().contains(itemId)) {
                                    node.getAvailableWorkstations().add(itemId);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Fallback workstation from vanilla Recipe.getToastSymbol()
        if (recipe instanceof Recipe<?> vanillaRecipe) {
            try {
                ItemStack toast = vanillaRecipe.getToastSymbol();
                if (toast != null && !toast.isEmpty()) {
                    ResourceLocation tId = ForgeRegistries.ITEMS.getKey(toast.getItem());
                    if (tId != null && !node.getAvailableWorkstations().contains(tId)) {
                        node.getAvailableWorkstations().add(tId);
                    }
                }
            } catch (Throwable ignored) {}
        }

        ResourceLocation icon = preferredWorkstation;
        if (icon == null && !node.getAvailableWorkstations().isEmpty()) {
            icon = node.getAvailableWorkstations().get(0);
        }
        if (icon == null && catId != null) {
            if (ForgeRegistries.ITEMS.containsKey(catId)) {
                icon = catId;
            } else {
                icon = VANILLA_CATEGORY_ICON_FALLBACKS.getOrDefault(catId, catId);
            }
        }
        if (icon != null) {
            node.setMachineIcon(icon);
            if (!node.getAvailableWorkstations().contains(icon)) {
                node.getAvailableWorkstations().add(0, icon);
            }
        }

        // Extensible property pipeline
        CompoundTag recipeDataTag = GTCEuRecipeHandler.extractRecipeDataTag(recipe);
        com.gtceu.calcboard.api.property.RecipePropertyExtractorPipeline.extractAll(recipe, recipeDataTag, catId, node.getProperties());

        String modId = catId != null ? catId.getNamespace().toLowerCase(Locale.ROOT) : "";
        boolean isSupported = ModAdapterRegistry.isCategorySupported(catId);
        if (!isSupported && !modId.isEmpty()) {
            isSupported = ModAdapterRegistry.isRecipeSupported(modId, catId);
        }
        if (!isSupported) {
            node.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.IS_GENERIC_UNSUPPORTED, true);
        }

        if (details.backingRecipeTemp > 0) {
            node.setRecipeTemperature(details.backingRecipeTemp);
        }

        if (node.isFusion()) {
            node.setMultiblock(true);
            GTVoltageTier minTier = node.getMinFusionVoltageTier();
            node.setTargetTier(minTier);
            var adapter = ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null) {
                var preferredWs = adapter.getPreferredMultiblockWorkstation(node, node.getAvailableWorkstations());
                if (preferredWs != null) {
                    node.setMachineIcon(preferredWs);
                }
            }
        }

        for (IngredientStack in : inputs) {
            if (in != null && in.getId() != null && !EmiRecipeConverter.isIgnoredInput(in.getId(), in.getChance())) {
                node.addInput(in);
            }
        }
        for (IngredientStack out : outputs) {
            if (out != null && out.getId() != null && !EmiRecipeConverter.isDummyConditionMarker(out.getId())) {
                node.addOutput(out);
            }
        }

        // Auto-provision Heating Coil if temperature is required
        int reqTemp = node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE);
        if (reqTemp <= 0) reqTemp = node.getRecipeTemperature();
        if (reqTemp > 0) {
            var coil = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilForTemperature(reqTemp);
            if (coil != null) {
                com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.installCoil(node, coil);
            }
        }

        // Auto-provision Fusion Reflector if required
        int reqReflector = node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.REQUIRED_REFLECTOR_TIER);
        if (reqReflector > 0) {
            com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper.installReflector(node, reqReflector);
        }

        if (preferredWorkstation == null) {
            com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().applyPresetIfPresent(node);
        }

        return node;
    }

    public static void extractVanillaRecipeContents(Object recipe, List<IngredientStack> inputs, List<IngredientStack> outputs) {
        if (!(recipe instanceof Recipe<?> vanillaRecipe)) return;
        try {
            if (inputs != null && inputs.isEmpty()) {
                for (net.minecraft.world.item.crafting.Ingredient ing : vanillaRecipe.getIngredients()) {
                    if (ing != null && !ing.isEmpty()) {
                        ItemStack[] items = ing.getItems();
                        if (items != null && items.length > 0) {
                            ItemStack primary = items[0];
                            ResourceLocation iId = ForgeRegistries.ITEMS.getKey(primary.getItem());
                            if (iId != null) {
                                String name = primary.getHoverName().getString();
                                if (name.isEmpty()) name = formatName(iId.getPath());
                                IngredientStack stack = IngredientStack.item(iId, name, Math.max(1, primary.getCount()));
                                for (int i = 1; i < items.length; i++) {
                                    ResourceLocation altId = ForgeRegistries.ITEMS.getKey(items[i].getItem());
                                    if (altId != null && !stack.getAlternatives().contains(altId)) {
                                        stack.getAlternatives().add(altId);
                                    }
                                }
                                inputs.add(stack);
                            }
                        }
                    }
                }
            }
            if (outputs != null && outputs.isEmpty()) {
                ItemStack result = ItemStack.EMPTY;
                try {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null && mc.level != null) {
                        result = vanillaRecipe.getResultItem(mc.level.registryAccess());
                    }
                } catch (Throwable ignored) {}
                if (result.isEmpty()) {
                    try {
                        result = vanillaRecipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
                    } catch (Throwable ignored) {}
                }
                if (result.isEmpty()) {
                    try {
                        var m = vanillaRecipe.getClass().getMethod("getResultItem");
                        result = (ItemStack) m.invoke(vanillaRecipe);
                    } catch (Throwable ignored) {}
                }
                if (!result.isEmpty()) {
                    ResourceLocation oId = ForgeRegistries.ITEMS.getKey(result.getItem());
                    if (oId != null) {
                        String name = result.getHoverName().getString();
                        if (name.isEmpty()) name = formatName(oId.getPath());
                        outputs.add(IngredientStack.item(oId, name, Math.max(1, result.getCount())));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String formatName(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] parts = path.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }
}
