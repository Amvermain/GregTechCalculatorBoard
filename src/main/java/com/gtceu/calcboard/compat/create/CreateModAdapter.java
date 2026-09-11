package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.model.RecipeDetails;
import com.gtceu.calcboard.api.util.RecipeConversionHelper;
import com.gtceu.calcboard.compat.create.extractor.CreateKineticRpmExtractor;
import com.gtceu.calcboard.api.spi.extension.ICapabilityMatrixProvider;
import com.gtceu.calcboard.api.spi.extension.ICompoundRecipeProvider;
import com.gtceu.calcboard.api.spi.extension.IEnergySimulationProvider;
import com.gtceu.calcboard.api.spi.extension.IHardwareAddonProvider;
import com.gtceu.calcboard.api.spi.extension.IModExtension;
import com.gtceu.calcboard.api.spi.extension.IMultiblockBOMProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Mod Adapter facade for Create kinetic generators and processing machinery.
 */
public class CreateModAdapter extends AbstractKineticModAdapter implements IHardwareAddonProvider, IMultiblockBOMProvider {

    private static final Set<Class<? extends IModExtension>> SUPPORTED_EXTENSIONS = Set.of(
            IEnergySimulationProvider.class,
            ICompoundRecipeProvider.class,
            ICapabilityMatrixProvider.class,
            IHardwareAddonProvider.class,
            IMultiblockBOMProvider.class
    );

    @Override
    public Set<Class<? extends IModExtension>> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    public static final String MOD_ID = "create";
    public static final String MOD_ID_ADDITION = "createaddition";

    static {
        CreateProperties.init();
        com.gtceu.calcboard.api.catalog.AddonFactoryRegistry.register(
                com.gtceu.calcboard.api.catalog.AddonCategory.HEATER,
                (id, name, desc, icon, tag) -> {
                    int heat = 1;
                    if (tag != null && tag.contains("heatLevel")) {
                        heat = tag.getInt("heatLevel");
                    } else if ("create:blaze_burner_superheated".equals(id)) {
                        heat = 2;
                    }
                    return new com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon(
                            id, name, desc, icon, heat
                    );
                }
        );
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID)
                        || ModList.get().isLoaded(MOD_ID_ADDITION)
                        || !FMLLoader.isProduction();
            }
        } catch (Throwable t) {
            return true; // Test environment fallback
        }
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace();
        if (ns.equals("create_new_age")) return false; // Dedicated CreateNewAgeModAdapter handles this
        if (ns.equals("greate")) return false; // Dedicated GreateModAdapter handles this
        if (ns.equals("createdieselgenerators")) return false; // Dedicated CreateDieselGeneratorsModAdapter handles this
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isCreateFamilyNamespace(ns)) return true;
        return "gtcalcboard".equals(ns) && "kinetic_generation".equals(categoryId.getPath());
    }

    @Override
    public void initialize() {
        com.gtceu.calcboard.api.property.RecipePropertyExtractorPipeline.register(new CreateKineticRpmExtractor());
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && "greate".equals(node.getMachineIcon().getNamespace())) return false;
        if (node.getRecipeCategoryId() != null && "greate".equals(node.getRecipeCategoryId().getNamespace())) return false;
        if (node.getMachineIcon() != null && "createdieselgenerators".equals(node.getMachineIcon().getNamespace())) return false;
        if (node.getRecipeCategoryId() != null && "createdieselgenerators".equals(node.getRecipeCategoryId().getNamespace())) return false;
        return com.gtceu.calcboard.api.util.ModCompatHelper.isCreateMachine(node);
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, RecipeDetails details) {
        return CreateRecipeHandler.adaptRecipeDetails(emiRecipe, backingRecipe, details);
    }

    @Override
    public com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster buildCompoundRecipe(
            Object recipeObj,
            Object backingRecipe,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        if (backingRecipe == null) return null;
        if (CreateSequencedRecipeExtractor.isSequencedRecipe(backingRecipe)) {
            String machineName = preferredWorkstation != null ? RecipeConversionHelper.formatName(preferredWorkstation.getPath()) : "Sequenced Assembly";
            ResourceLocation icon = preferredWorkstation != null ? preferredWorkstation : ResourceLocation.tryParse("create:sequenced_assembly");
            return CreateSequencedRecipeExtractor.buildCompoundCluster(
                    backingRecipe, machineName, icon, GTVoltageTier.ULV, startX, startY
            );
        }
        return null;
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return CreateProperties.isCreateBoiler(node);
    }

    @Override
    public List<com.gtceu.calcboard.api.catalog.AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        if (CreateProperties.isCreateBoiler(node)) {
            return List.of(com.gtceu.calcboard.api.catalog.AddonCategory.HEATER, com.gtceu.calcboard.api.catalog.AddonCategory.CUSTOM);
        }
        return List.of();
    }

    @Override
    public void discoverAddons(List<com.gtceu.calcboard.api.catalog.MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        collector.add(new com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon(
                "create:blaze_burner_heated",
                "gui.gtcalcboard.addon.create_heater",
                "gui.gtcalcboard.addon.create_heater_desc",
                ResourceLocation.tryParse("create:blaze_burner"),
                1
        ));
        collector.add(new com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon(
                "create:blaze_burner_superheated",
                "gui.gtcalcboard.addon.create_superheated_heater",
                "gui.gtcalcboard.addon.create_superheated_heater_desc",
                ResourceLocation.tryParse("create:blaze_burner"),
                2
        ));
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, com.gtceu.calcboard.api.catalog.MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(com.gtceu.calcboard.api.catalog.AddonCategory.CUSTOM)) return true;
        return CreateProperties.isCreateBoiler(node) && addon.getCategory().equals(com.gtceu.calcboard.api.catalog.AddonCategory.HEATER);
    }

    @Override
    public boolean canInstallAddon(RecipeNode node, com.gtceu.calcboard.api.catalog.MachineAddon addon) {
        if (!isAddonCompatible(node, addon)) return false;
        if (addon instanceof com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon heater) {
            long currentBurners = node.getAddons().stream().filter(a -> a instanceof com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon).count();
            if (currentBurners >= 9) return false;
            int currentHeat = CreateProperties.calculateTotalHeatFromAddons(node);
            return currentHeat + heater.getHeatLevel() <= 18;
        }
        return true;
    }

    @Override
    public void onAddonInstalled(RecipeNode node, com.gtceu.calcboard.api.catalog.MachineAddon addon) {
        if (node == null || addon == null) return;
        node.getAddons().add(addon);
        int totalHeat = CreateProperties.calculateTotalHeatFromAddons(node);
        CreateProperties.setBoilerHeat(node, totalHeat);
        node.markOverclockDirty();
    }

    @Override
    public void onAddonRemoved(RecipeNode node, com.gtceu.calcboard.api.catalog.MachineAddon addon) {
        if (node == null || addon == null) return;
        int totalHeat = CreateProperties.calculateTotalHeatFromAddons(node);
        CreateProperties.setBoilerHeat(node, totalHeat);
        node.markOverclockDirty();
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(stack);
        if (node != null) return node;
        RecipeNode cnaNode = com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(stack);
        if (cnaNode != null) return cnaNode;
        if (stack != null && !stack.isEmpty()) {
            ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            return com.gtceu.calcboard.compat.createdieselgenerators.CDGRecipeHandler.createKineticGeneratorNode(itemId, stack.getHoverName().getString());
        }
        return null;
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(itemId, displayName);
        if (node != null) return node;
        RecipeNode cnaNode = com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(itemId, displayName);
        if (cnaNode != null) return cnaNode;
        return com.gtceu.calcboard.compat.createdieselgenerators.CDGRecipeHandler.createKineticGeneratorNode(itemId, displayName);
    }

    @Override
    public void collectNativeCatalogRecipes(List<SearchableRecipe> collector) {
        CreateRecipeHandler.collectNativeCatalogRecipes(collector);
    }

    private static final ResourceLocation ITEM_FLUID_TANK = ResourceLocation.tryParse("create:fluid_tank");
    private static final ResourceLocation ITEM_STEAM_ENGINE = ResourceLocation.tryParse("create:steam_engine");

    @Override
    public void populateExtraBOMParts(RecipeNode node, List<com.gtceu.calcboard.api.bom.MultiblockStructurePart> parts) {
        if (node == null || parts == null) return;
        if (!CreateProperties.isCreateBoiler(node)) return;

        int sizeBlocks = node.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS);
        if (sizeBlocks > 0) {
            String tankName = com.gtceu.calcboard.api.bom.BOMDisplayNameResolver.resolve(ITEM_FLUID_TANK, "Fluid Tank");
            parts.add(new com.gtceu.calcboard.api.bom.MultiblockStructurePart(
                    ITEM_FLUID_TANK,
                    tankName,
                    sizeBlocks,
                    com.gtceu.calcboard.api.bom.PartCategory.CASING
            ));
        }

        int level = node.getProperties().get(CreateProperties.BOILER_LEVEL);
        int requiredEngines = Math.max(1, level);
        if (requiredEngines > 1) {
            updateEngineControllerPartAmount(node, parts, requiredEngines);
        }
    }

    private void updateEngineControllerPartAmount(RecipeNode node, List<com.gtceu.calcboard.api.bom.MultiblockStructurePart> parts, int requiredEngines) {
        ResourceLocation icon = node.getMachineIcon();
        for (int i = 0; i < parts.size(); i++) {
            com.gtceu.calcboard.api.bom.MultiblockStructurePart p = parts.get(i);
            if (p.category() == com.gtceu.calcboard.api.bom.PartCategory.CONTROLLER && Objects.equals(p.itemId(), icon)) {
                parts.set(i, new com.gtceu.calcboard.api.bom.MultiblockStructurePart(
                        p.itemId(),
                        p.displayName(),
                        requiredEngines,
                        p.category()
                ));
                return;
            }
        }
    }

    @Override
    public com.gtceu.calcboard.api.bom.PartCategory classifyBOMPart(ResourceLocation itemId) {
        if (itemId == null) return null;
        if (ITEM_FLUID_TANK.equals(itemId)) return com.gtceu.calcboard.api.bom.PartCategory.CASING;
        if (ITEM_STEAM_ENGINE.equals(itemId)) return com.gtceu.calcboard.api.bom.PartCategory.CONTROLLER;
        return null;
    }
}




