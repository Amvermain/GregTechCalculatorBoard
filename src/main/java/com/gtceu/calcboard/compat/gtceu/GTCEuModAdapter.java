package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.AddonFactoryRegistry;
import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.extension.IBoosterProvider;
import com.gtceu.calcboard.api.spi.extension.ICapabilityMatrixProvider;
import com.gtceu.calcboard.api.spi.extension.ICompoundRecipeProvider;
import com.gtceu.calcboard.api.spi.extension.IEnergySimulationProvider;
import com.gtceu.calcboard.api.spi.extension.IHardwareAddonProvider;
import com.gtceu.calcboard.api.spi.extension.IMultiblockBOMProvider;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import com.gtceu.calcboard.compat.gtceu.badge.GTBadgeProvider;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTNodeValidator;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCapabilityScanner;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuMachineLifecycleHandler;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockScanner;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockStructureScanner;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuWorkstationResolver;
import com.gtceu.calcboard.compat.gtceu.physics.GTBoilerPhysics;
import com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTMultiblockBOMResolver;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.compat.gtceu.physics.GTTurbinePhysics;
import com.gtceu.calcboard.api.model.RecipeDetails;
import com.gtceu.calcboard.api.property.RecipePropertyExtractorPipeline;
import com.gtceu.calcboard.api.util.RecipeConversionHelper;
import com.gtceu.calcboard.api.spi.viewer.RecipeViewerBridgeRegistry;
import com.gtceu.calcboard.api.catalog.TurbineCatalog;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge;
import com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel;
import com.gtceu.calcboard.compat.gtceu.extractor.GTCEuCleanroomExtractor;
import com.gtceu.calcboard.compat.gtceu.extractor.GTCEuEbfTemperatureExtractor;
import com.gtceu.calcboard.compat.gtceu.extractor.GTCEuFusionStartEnergyExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mod Adapter facade for GregTech CEu Modern (GTCEu).
 * Manages GTCEu hardware addons, overclock physics, multiblock BOMs, and recipe conversion.
 */
public class GTCEuModAdapter implements IModAdapter {

    static {
        GTCEuProperties.init();

        AddonFactoryRegistry.register(AddonCategory.COIL, (id, name, desc, icon, tag) -> new GTCoilAddon(id, name, desc, icon));
        AddonFactoryRegistry.register(AddonCategory.ROTOR, (id, name, desc, icon, tag) -> new GTRotorAddon(id, name, desc, icon));
        AddonFactoryRegistry.register(AddonCategory.REFLECTOR, (id, name, desc, icon, tag) -> new GTReflectorAddon(id, name, desc, icon));
        AddonFactoryRegistry.register(AddonCategory.PARALLEL, (id, name, desc, icon, tag) -> new GTParallelHatchAddon(id, name, desc, icon));
        AddonFactoryRegistry.register(AddonCategory.ENERGY_HATCH, (id, name, desc, icon, tag) -> new GTEnergyHatchAddon(id, name, desc, icon));
        AddonFactoryRegistry.register(AddonCategory.HATCH_BUS, (id, name, desc, icon, tag) -> new GTHatchAddon(id, name, desc, icon));

        GTBadgeProvider.registerAll();
    }

    public static final Set<ResourceLocation> VANILLA_COOKING_RECIPE_TYPES = Set.of(
            ResourceLocation.tryParse("minecraft:smelting"),
            ResourceLocation.tryParse("minecraft:blasting"),
            ResourceLocation.tryParse("minecraft:smoking"),
            ResourceLocation.tryParse("minecraft:campfire_cooking"),
            ResourceLocation.tryParse("minecraft:furnace")
    );

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public void invalidateTextCaches() {
        com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockStructureScanner.invalidateTextCaches();
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded("gtceu");
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        return GTCEuRecipeHandler.isGTCategoryNamespace(categoryId.getNamespace());
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isCreateMachine(node)) return false;
        if (com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.isThermalMachine(node)) return false;
        if (node.getEnergyTypeOverride() == EnergyType.KINETIC_SU) return false;
        if (GTFusionHelper.isFusion(node)) return true;
        if (node.getRecipeCategoryId() != null && VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId())) {
            return true;
        }

        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("minecraft") || ns.equals("emi")) {
                return false;
            }
            if (GTCEuRecipeHandler.isGTCategoryNamespace(ns)) {
                return true;
            }
        }
        if (node.getRecipeCategoryId() != null) {
            String ns = node.getRecipeCategoryId().getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("minecraft") || ns.equals("emi")) {
                return false;
            }
            if (GTCEuRecipeHandler.isGTCategoryNamespace(ns)) {
                return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && GTCEuRecipeHandler.isGTCategoryNamespace(ws.getNamespace())) {
                return true;
            }
        }
        return node.getEnergyTypeOverride() == EnergyType.ELECTRIC_EU || (node.getEnergyTypeOverride() == null && node.getBaseEUt() > 0 && (node.getMachineIcon() == null || !node.getMachineIcon().getNamespace().equals("minecraft")));
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        GTCEuAddonCrawler.discoverAddons(collector, recipeOutputStacks);
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        GTCEuCapabilityScanner.enrichCapabilities(matrix, emiRecipeManager);
    }

    @Override
    public void scanMultiblocks(Object emiRecipeManager) {
        GTCEuMultiblockScanner.scan(emiRecipeManager);
    }

    @Override
    public void scanMultiblockStructures() {
        GTCEuMultiblockStructureScanner.scan();
    }

    @Override
    public MultiblockStructureDef scanMultiblockStructure(ResourceLocation machineId) {
        return GTMultiblockBOMResolver.scanMultiblockStructure(machineId);
    }

    @Override
    public PartCategory classifyBOMPart(ResourceLocation itemId) {
        return GTMultiblockBOMResolver.classifyBOMPart(itemId);
    }

    @Override
    public void accumulateStructureSlots(
            ResourceLocation itemId,
            PartCategory category,
            int amount,
            com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.StructureSlotCounts slots
    ) {
        GTMultiblockBOMResolver.accumulateStructureSlots(itemId, category, amount, slots);
    }

    @Override
    public boolean isTurbine(RecipeNode node) {
        return GTTurbinePhysics.isTurbine(node);
    }

    @Override
    public boolean isLargeTurbine(RecipeNode node) {
        return GTTurbinePhysics.isLargeTurbine(node);
    }

    public static void syncTurbineMachineIcon(RecipeNode node) {
        GTTurbinePhysics.syncTurbineMachineIcon(node);
    }

    @Override
    public boolean isGenerator(RecipeNode node) {
        if (node == null) return false;
        if (MultiblockDetector.isCoilMultiblock(node.getMachineIcon()) || MultiblockDetector.isCoilRecipeCategory(node.getRecipeCategoryId())) {
            return false;
        }
        return node.isGenerator() || GTTurbinePhysics.isTurbine(node) || node.getBaseEUt() < 0;
    }

    @Override
    public double getGeneratorMaxPower(RecipeNode node) {
        return GTTurbinePhysics.getGeneratorMaxEUt(node);
    }

    @Override
    public double computeEffectiveOutputChance(RecipeNode node, int outputIndex, double defaultChance) {
        return GTPowerCalculator.computeEffectiveOutputChance(node, outputIndex, defaultChance);
    }

    @Override
    public int getMaxParallelCapacity(RecipeNode node) {
        return GTPowerCalculator.getMaxParallelCapacity(node);
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return GTAddonCompatibilityHandler.supportsAddons(node);
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return GTAddonCompatibilityHandler.getApplicableAddonCategories(node);
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        return GTAddonCompatibilityHandler.isAddonCompatible(node, addon);
    }

    public static boolean isDistillationTower(RecipeNode node) {
        return GTAddonCompatibilityHandler.isDistillationTower(node);
    }

    @Override
    public boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        return GTAddonCompatibilityHandler.canInstallAddon(node, addon);
    }

    public static GTHatchAddon.HatchType resolveHatchType(MachineAddon addon) {
        return GTAddonCompatibilityHandler.resolveHatchType(addon);
    }

    @Override
    public ResourceLocation getPreferredMultiblockWorkstation(RecipeNode node, List<ResourceLocation> availableWorkstations) {
        return GTAddonCompatibilityHandler.getPreferredMultiblockWorkstation(node, availableWorkstations);
    }

    @Override
    public void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        GTAddonCompatibilityHandler.onAddonInstalled(node, addon);
    }

    @Override
    public void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        GTAddonCompatibilityHandler.onAddonRemoved(node, addon);
    }

    public static void updateNodeTierFromEnergyHatches(RecipeNode node) {
        GTAddonCompatibilityHandler.updateNodeTierFromEnergyHatches(node);
    }

    public static long getMaxEUtCapacity(RecipeNode node) {
        return GTAddonCompatibilityHandler.getMaxEUtCapacity(node);
    }

    public static long getOverclockVoltage(RecipeNode node) {
        return GTAddonCompatibilityHandler.getOverclockVoltage(node);
    }

    @Override
    public void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
        GTAddonCompatibilityHandler.buildAddonTooltip(node, addon, isActiveAddon, tooltip);
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int pwr = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            tooltip.add(Component.literal("§a⚙ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.efficiency", eff + "%")));
            tooltip.add(Component.literal("§6⚡ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.power", pwr + "%")));
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            tooltip.add(Component.literal("§6♨ ").append(Component.translatable("gui.gtcalcboard.addon.stat.coil_temp", addon.getCoilTemperature())));
            MachineAddon tailored = addon.forMachine(node);
            if (tailored.getParallelMultiplier() > 1) {
                tooltip.add(Component.literal("§b⚡ ").append(Component.translatable("gui.gtcalcboard.addon.stat.parallel", tailored.getParallelMultiplier())));
            }
            if (tailored.getDurationMultiplier() != 1.0) {
                tooltip.add(Component.literal("§a⏳ ").append(Component.translatable("gui.gtcalcboard.addon.stat.speed_mult", String.format(Locale.ROOT, "%.2fx", 1.0 / tailored.getDurationMultiplier()))));
            }
            if (tailored.getEutMultiplier() != 1.0) {
                tooltip.add(Component.literal("§e⚡ ").append(Component.translatable("gui.gtcalcboard.addon.stat.eut_mult", String.format(Locale.ROOT, "%.2fx", tailored.getEutMultiplier()))));
            }
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            tooltip.add(Component.literal("§b✦ ").append(Component.translatable("gui.gtcalcboard.addon.reflector.tier", addon.getReflectorTier())));
            return;
        }
        IModAdapter.super.buildAddonTooltip(node, addon, isActiveAddon, tooltip);
    }

    @Override
    public List<MachineAddon> getResetAddonCards(RecipeNode node) {
        List<MachineAddon> list = new ArrayList<>();
        if (node == null) return list;

        if (node.isTurbine() && node.isMultiblock()) {
            GTRotorAddon stdRotor = new GTRotorAddon("gtceu:rotor_standard",
                    Component.translatable("gui.gtcalcboard.rotor.standard").getString(),
                    Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", "100").getString(),
                    ResourceLocation.tryParse("gtceu:turbine_rotor"), 100, 100, 1600.0);
            stdRotor.setDiscoverySource("Standard Default Rotor");
            list.add(stdRotor);
        }

        boolean isFusion = GTFusionHelper.isFusion(node);

        if (isFusion) {
            GTReflectorAddon noRefl = new GTReflectorAddon("gtceu:reflector_none",
                    Component.translatable("gui.gtcalcboard.reflector.none").getString(),
                    Component.translatable("gui.gtcalcboard.reflector.none_desc").getString(),
                    null, 0);
            noRefl.setDiscoverySource("No Reflector Default");
            list.add(noRefl);
        }

        return list;
    }

    @Override
    public boolean validateNode(RecipeNode node, List<Component> warnings) {
        return validateNode(node, (FlowGraph) null, warnings);
    }

    @Override
    public boolean validateNode(RecipeNode node, FlowGraph graph, List<Component> warnings) {
        return GTNodeValidator.validateNode(node, graph, warnings);
    }

    @Override
    public MachineAddon tailorAddon(MachineAddon addon, RecipeNode targetNode) {
        if (addon == null || targetNode == null) return addon;
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            return CoilHelper.tailorCoilAddon(addon, targetNode);
        }
        return addon;
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        return GTPowerCalculator.computeOverclock(node, targetTier, isGenerator);
    }

    public static double getBoilerSpeedMultiplier(RecipeNode node) {
        return GTPowerCalculator.getBoilerSpeedMultiplier(node);
    }

    public static boolean isLargeBoilerRecipe(RecipeNode node) {
        return GTPowerCalculator.isLargeBoilerRecipe(node);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return GTPowerCalculator.buildEnergyTooltip(node);
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, RecipeDetails details) {
        return GTCEuRecipeHandler.adaptRecipeDetails(emiRecipeObj, backing, details);
    }

    @Override
    public com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster buildCompoundRecipe(
            Object recipeObj,
            Object backingRecipe,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        if (backingRecipe == null && recipeObj == null) return null;

        String machineName = preferredWorkstation != null ? RecipeConversionHelper.formatName(preferredWorkstation.getPath()) : resolveMachineName(recipeObj);
        ResourceLocation icon = preferredWorkstation != null ? preferredWorkstation : resolveMachineIcon(recipeObj);

        if (GTCEuLayeredRecipeExtractor.isLayeredRecipe(backingRecipe, recipeObj)) {
            RecipeDetails details = new RecipeDetails();
            Object detailSource = backingRecipe != null ? backingRecipe : recipeObj;
            GTCEuRecipeHandler.extractGTRecipeDetails(detailSource, details);
            return GTCEuLayeredRecipeExtractor.buildCompoundCluster(
                    backingRecipe, recipeObj, machineName, icon, details.tier, startX, startY
            );
        }

        return null;
    }

    private static String resolveMachineName(Object recipeObj) {
        if (recipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emi && emi.getCategory() != null && emi.getCategory().getId() != null) {
            return RecipeConversionHelper.formatName(emi.getCategory().getId().getPath());
        }
        return "Machine";
    }

    private static ResourceLocation resolveMachineIcon(Object recipeObj) {
        var bridge = RecipeViewerBridgeRegistry.getActiveBridge();
        return bridge != null ? bridge.findMachineIcon(recipeObj) : null;
    }

    @Override
    public double computeSingleMachinePower(RecipeNode node) {
        return GTPowerCalculator.computeSingleMachinePower(node);
    }

    @Override
    public int computeEffectiveParallel(RecipeNode node) {
        return GTPowerCalculator.computeEffectiveParallel(node);
    }

    @Override
    public int getDefaultParallel(RecipeNode node) {
        return GTPowerCalculator.getDefaultParallel(node);
    }

    @Override
    public void autoTuneParallel(RecipeNode node) {
        GTPowerCalculator.autoTuneParallel(node);
    }

    @Override
    public boolean supportsSteamMode(RecipeNode node) {
        if (node == null) return false;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return true;
        }
        if (MultiblockDetector.isSteamMultiblock(node.getMachineIcon()) || MultiblockDetector.isSteamMultiblock(node.getMultiblockWorkstation())) {
            return true;
        }
        if (node.getRecipeCategoryId() != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap.supportsSteamMode()) {
                return true;
            }
            if (VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId())) {
                return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && (MultiblockDetector.isSteamMultiblock(ws) || ws.getPath().startsWith("steam_") || ws.getPath().startsWith("lp_steam_") || ws.getPath().startsWith("hp_steam_"))) {
                return true;
            }
            if (ws != null && ws.getNamespace().equals("gtceu")) {
                Object def = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineDefinition(ws);
                if (def != null && GTCEuCapabilityScanner.isSteamDefinition(def, ws)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static GTVoltageTier extractVoltageTierFromIcon(ResourceLocation icon) {
        return GTCEuWorkstationResolver.extractVoltageTierFromIcon(icon);
    }

    @Override
    public List<ResourceLocation> getMultiblockWorkstations(RecipeNode node) {
        return GTCEuWorkstationResolver.getMultiblockWorkstations(node);
    }

    @Override
    public ResourceLocation getWorkstationForTier(RecipeNode node, GTVoltageTier tier) {
        return GTCEuWorkstationResolver.getWorkstationForTier(node, tier);
    }

    @Override
    public void onMachineIconChanged(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        GTCEuMachineLifecycleHandler.onMachineIconChanged(node, oldIcon, newIcon);
    }

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        return GTPowerCalculator.getEnergyType(node);
    }

    @Override
    public void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        GTCEuMachineLifecycleHandler.onSteamModeChanged(node, oldMode, newMode);
    }

    @Override
    public double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        return GTCEuMachineLifecycleHandler.computeEffectiveIngredientRate(node, stack, isInput, defaultRate);
    }

    @Override
    public double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        return GTCEuMachineLifecycleHandler.computeSingleMachineIngredientRate(node, stack, isInput, defaultRate);
    }

    @Override
    public boolean isBoilerRecipe(RecipeNode node) {
        return GTPowerCalculator.isBoilerRecipe(node);
    }

    @Override
    public boolean isLiquidBoilerRecipe(RecipeNode node) {
        return GTPowerCalculator.isLiquidBoilerRecipe(node);
    }

    public static boolean isMufflerAddon(MachineAddon addon) {
        return GTAddonCompatibilityHandler.isMufflerAddon(addon);
    }

    @Override
    public List<MultiblockStructurePart> resolveStructureParts(RecipeNode node, boolean dualLowerTierEnergyHatches) {
        if (node == null) return List.of();
        ResourceLocation machineId = node.getMachineIcon();
        if (node.isMultiblock() || (machineId != null && MultiblockDetector.isMultiblock(machineId))) {
            return GTMultiblockBOMResolver.resolveStructureParts(node, dualLowerTierEnergyHatches);
        }
        return IModAdapter.super.resolveStructureParts(node, dualLowerTierEnergyHatches);
    }

    @Override
    public boolean isFusion(RecipeNode node) {
        return GTFusionHelper.isFusion(node);
    }

    @Override
    public int getFusionTier(RecipeNode node) {
        return GTFusionHelper.getFusionTier(node);
    }

    @Override
    public GTVoltageTier getMinFusionVoltageTier(RecipeNode node) {
        return GTFusionHelper.getMinFusionVoltageTier(node);
    }

    @Override
    public GTVoltageTier getMinimumWorkstationTier(RecipeNode node) {
        return GTCEuWorkstationResolver.getMinimumWorkstationTier(node);
    }

    @Override
    public GTVoltageTier sanitizeTargetTier(RecipeNode node, GTVoltageTier requestedTier) {
        return GTCEuWorkstationResolver.sanitizeTargetTier(node, requestedTier);
    }

    @Override
    public String formatAddonSubtitle(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory().equals(AddonCategory.ENERGY_HATCH) && addon instanceof GTEnergyHatchAddon eh) {
            return String.format("Tier: %s (%,dA)", eh.getTier().getName(), eh.getAmperage());
        }
        if (addon.getCategory().equals(AddonCategory.HATCH_BUS) && addon instanceof GTHatchAddon h) {
            return String.format("Tier: %s", h.getTier().getName());
        }
        return IModAdapter.super.formatAddonSubtitle(node, addon);
    }

    @Override
    public String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH && addon instanceof GTEnergyHatchAddon eh) {
            return eh.getAmperage() > 2
                    ? String.format("§e⚡%s (%,dA)", eh.getTier().getName(), eh.getAmperage())
                    : String.format("§e⚡%s", eh.getTier().getName());
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS && addon instanceof GTHatchAddon h) {
            return String.format("§d⚡%s", h.getTier().getName());
        }
        return IModAdapter.super.formatAddonBadge(node, addon);
    }

    @Override
    public void initialize() {
        RecipePropertyExtractorPipeline.register(new GTCEuEbfTemperatureExtractor());
        RecipePropertyExtractorPipeline.register(new GTCEuFusionStartEnergyExtractor());
        RecipePropertyExtractorPipeline.register(new GTCEuCleanroomExtractor());
    }

    @Override
    public int calculateTierDelta(RecipeNode node, GTVoltageTier targetTier, GTVoltageTier recipeTier) {
        if (targetTier == null || recipeTier == null) return 0;
        int delta = targetTier.ordinal() - recipeTier.ordinal();
        if (recipeTier == GTVoltageTier.ULV) {
            delta--;
        }
        return Math.max(0, delta);
    }

    @Override
    public boolean isMultiblock(ResourceLocation machineId) {
        if (machineId == null) return false;
        if (isCombustionEngine(machineId)) return true;
        Object def = GTCEuReflectionBridge.getMachineDefinition(machineId);
        return def != null && GTCEuReflectionBridge.isMultiblockDefinition(def);
    }

    @Override
    public boolean isCoilMultiblock(ResourceLocation machineId) {
        if (machineId == null) return false;
        Object def = GTCEuReflectionBridge.getMachineDefinition(machineId);
        if (def != null) {
            Class<?> mCls = GTCEuReflectionBridge.getMachineClass(def);
            if (mCls != null && GTCEuReflectionBridge.isCoilWorkableClass(mCls)) {
                return true;
            }
        }
        return GTCEuCoilModifierHelper.getCoilMachineSpec(machineId).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC;
    }

    @Override
    public boolean isCombustionEngine(ResourceLocation machineId) {
        if (machineId == null) return false;
        return GTCombustionHelper.isCombustionEngine(machineId);
    }

    @Override
    public boolean isTurbine(ResourceLocation machineId) {
        if (machineId == null) return false;
        if (TurbineCatalog.classifyTurbineId(machineId) != null || TurbineCatalog.getTurbineBaseTier(machineId) != null) {
            return true;
        }
        return hasTurbineSignature(machineId, TurbineCatalog.getTurbineAlias(machineId));
    }

    @Override
    public boolean isPlasmaTurbine(RecipeNode node) {
        return com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(node);
    }

    @Override
    public boolean isCombustionMachine(RecipeNode node) {
        return GTCombustionHelper.isCombustionEngine(node);
    }

    @Override
    public boolean hasTurbineSignature(ResourceLocation machineId, ResourceLocation alias) {
        Object gtDef = GTCEuReflectionBridge.getMachineDefinition(machineId);
        if (gtDef == null && alias != null) {
            gtDef = GTCEuReflectionBridge.getMachineDefinition(alias);
        }
        return gtDef != null && GTCEuReflectionBridge.hasTurbineSignature(gtDef);
    }

    @Override
    public boolean isThreadingAvailable(RecipeNode node) {
        return com.gtceu.calcboard.compat.start.helper.RecipeNodeThreadingHelper.isThreadingAvailable(node);
    }

    @Override
    public boolean hasThreading(RecipeNode node) {
        return com.gtceu.calcboard.compat.start.helper.RecipeNodeThreadingHelper.hasThreading(node);
    }

    @Override
    public void setThreadingActive(RecipeNode node, boolean active) {
        com.gtceu.calcboard.compat.start.helper.RecipeNodeThreadingHelper.setThreadingActive(node, active);
    }
}
