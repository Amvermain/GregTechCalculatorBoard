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
import com.gtceu.calcboard.compat.IModAdapter;
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
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCapabilityScanner;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockScanner;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockStructureScanner;
import com.gtceu.calcboard.compat.gtceu.physics.GTBoilerPhysics;
import com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTMultiblockBOMResolver;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.compat.gtceu.physics.GTTurbinePhysics;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
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

    private static final Map<ResourceLocation, Map<GTVoltageTier, ResourceLocation>> DEDUCTED_TIER_WORKSTATIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<ResourceLocation>> DEDUCTED_MULTIBLOCK_WORKSTATIONS = new ConcurrentHashMap<>();

    public static final Set<ResourceLocation> VANILLA_COOKING_RECIPE_TYPES = Set.of(
            ResourceLocation.tryParse("minecraft:smelting"),
            ResourceLocation.tryParse("minecraft:blasting"),
            ResourceLocation.tryParse("minecraft:smoking"),
            ResourceLocation.tryParse("minecraft:campfire_cooking"),
            ResourceLocation.tryParse("minecraft:furnace")
    );

    private static final Map<String, GTVoltageTier> FUSION_TIER_TOKEN_MAP = Map.ofEntries(
            Map.entry("mk1", GTVoltageTier.LuV),
            Map.entry("mk_1", GTVoltageTier.LuV),
            Map.entry("mk_i", GTVoltageTier.LuV),
            Map.entry("mki", GTVoltageTier.LuV),
            Map.entry("mk2", GTVoltageTier.ZPM),
            Map.entry("mk_2", GTVoltageTier.ZPM),
            Map.entry("mk_ii", GTVoltageTier.ZPM),
            Map.entry("mkii", GTVoltageTier.ZPM),
            Map.entry("mk3", GTVoltageTier.UV),
            Map.entry("mk_3", GTVoltageTier.UV),
            Map.entry("mk_iii", GTVoltageTier.UV),
            Map.entry("mkiii", GTVoltageTier.UV),
            Map.entry("aux1", GTVoltageTier.UHV),
            Map.entry("aux_1", GTVoltageTier.UHV),
            Map.entry("aux_i", GTVoltageTier.UHV),
            Map.entry("auxi", GTVoltageTier.UHV),
            Map.entry("mk4", GTVoltageTier.UEV),
            Map.entry("mk_4", GTVoltageTier.UEV),
            Map.entry("mk_iv", GTVoltageTier.UEV),
            Map.entry("mkiv", GTVoltageTier.UEV),
            Map.entry("aux2", GTVoltageTier.UIV),
            Map.entry("aux_2", GTVoltageTier.UIV),
            Map.entry("aux_ii", GTVoltageTier.UIV),
            Map.entry("auxii", GTVoltageTier.UIV),
            Map.entry("mk5", GTVoltageTier.UXV),
            Map.entry("mk_5", GTVoltageTier.UXV),
            Map.entry("aux3", GTVoltageTier.OpV),
            Map.entry("aux_3", GTVoltageTier.OpV),
            Map.entry("mk6", GTVoltageTier.MAX),
            Map.entry("mk_6", GTVoltageTier.MAX)
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
    public boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
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
        if (backingRecipe == null) return null;

        String machineName = preferredWorkstation != null ? EmiRecipeConverter.formatName(preferredWorkstation.getPath()) : "Machine";
        ResourceLocation icon = preferredWorkstation;

        if (GTCEuLayeredRecipeExtractor.isLayeredRecipe(backingRecipe)) {
            EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
            GTCEuRecipeHandler.extractGTRecipeDetails(backingRecipe, details);
            return GTCEuLayeredRecipeExtractor.buildCompoundCluster(
                    backingRecipe, machineName, icon, details.tier, startX, startY
            );
        }

        return null;
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
        if (icon == null) return null;

        Object def = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineDefinition(icon);
        if (def != null) {
            GTVoltageTier tier = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineTier(def);
            if (tier != null) return tier;
        }

        String path = icon.getPath().toLowerCase(Locale.ROOT);

        if (path.contains("auxiliary") || path.contains("aux_booster") || path.contains("aux_fusion")) {
            if (path.contains("mk2") || path.contains("mk_2") || path.contains("ii") || path.contains("aux2") || path.contains("aux_2") || path.contains("uiv")) return GTVoltageTier.UIV;
            if (path.contains("mk3") || path.contains("mk_3") || path.contains("iii") || path.contains("aux3") || path.contains("aux_3") || path.contains("opv")) return GTVoltageTier.OpV;
            return GTVoltageTier.UHV;
        }

        GTVoltageTier[] tiers = GTVoltageTier.values().clone();
        java.util.Arrays.sort(tiers, (a, b) -> Integer.compare(b.name().length(), a.name().length()));
        for (GTVoltageTier tier : tiers) {
            String nameLower = tier.name().toLowerCase(Locale.ROOT);
            if (path.startsWith(nameLower + "_") || path.contains("_" + nameLower + "_") || path.endsWith("_" + nameLower)) {
                return tier;
            }
        }

        for (Map.Entry<String, GTVoltageTier> entry : FUSION_TIER_TOKEN_MAP.entrySet()) {
            String token = entry.getKey();
            if (path.startsWith(token + "_") || path.contains("_" + token + "_") || path.endsWith("_" + token) || path.contains(token)) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public List<ResourceLocation> getMultiblockWorkstations(RecipeNode node) {
        if (node == null) return Collections.emptyList();
        List<ResourceLocation> result = new ArrayList<>();

        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && MultiblockDetector.isMultiblock(ws) && !result.contains(ws)) {
                result.add(ws);
            }
        }

        ResourceLocation catId = node.getRecipeCategoryId();

        if (catId != null) {
            List<ResourceLocation> cached = DEDUCTED_MULTIBLOCK_WORKSTATIONS.get(catId);
            if (cached == null) {
                cached = deductMultiblocksFromGTRegistries(catId);
                DEDUCTED_MULTIBLOCK_WORKSTATIONS.put(catId, cached);
            }
            for (ResourceLocation mb : cached) {
                if (mb != null && !result.contains(mb)) {
                    result.add(mb);
                }
            }
        }

        if (result.isEmpty() && catId != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(catId);
            if (cap != null && cap.availableWorkstations() != null) {
                for (ResourceLocation ws : cap.availableWorkstations()) {
                    if (ws != null && MultiblockDetector.isMultiblock(ws) && !result.contains(ws)) {
                        result.add(ws);
                    }
                }
            }
        }

        if (result.isEmpty() && node.getMachineIcon() != null && MultiblockDetector.isMultiblock(node.getMachineIcon())) {
            result.add(node.getMachineIcon());
        }

        if (result.size() > 1) {
            result.sort((a, b) -> {
                // 1. Direct category match priority
                if (catId != null) {
                    String catPath = catId.getPath().toLowerCase(Locale.ROOT);
                    boolean aMatch = a.getPath().toLowerCase(Locale.ROOT).contains(catPath);
                    boolean bMatch = b.getPath().toLowerCase(Locale.ROOT).contains(catPath);
                    if (aMatch && !bMatch) return -1;
                    if (!aMatch && bMatch) return 1;
                }

                // 2. Power / Voltage tier progression order (Ascending)
                GTVoltageTier tierA = extractVoltageTierFromIcon(a);
                GTVoltageTier tierB = extractVoltageTierFromIcon(b);
                if (tierA != null && tierB != null) {
                    int tierCmp = Integer.compare(tierA.ordinal(), tierB.ordinal());
                    if (tierCmp != 0) return tierCmp;
                }

                return 0;
            });
        }

        return result;
    }

    private static List<ResourceLocation> deductMultiblocksFromGTRegistries(ResourceLocation catId) {
        List<ResourceLocation> list = new ArrayList<>();
        if (catId == null) return list;
        Iterable<?> iterable = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachinesRegistryIterable();
        if (iterable != null) {
            for (Object machineDef : iterable) {
                if (machineDef == null) continue;
                ResourceLocation id = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineId(machineDef);
                if (id == null || !MultiblockDetector.isMultiblock(id)) continue;

                if (matchesRecipeType(machineDef, catId)) {
                    if (!list.contains(id)) {
                        list.add(id);
                    }
                }
            }
        }
        return list;
    }

    @Override
    public ResourceLocation getWorkstationForTier(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return null;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return null;
        }
        if (isBoilerRecipe(node) || isLiquidBoilerRecipe(node)) {
            return null;
        }

        ResourceLocation fromList = node.getWorkstationForTierFromList(tier);
        if (fromList != null) {
            return fromList;
        }

        ResourceLocation catId = node.getRecipeCategoryId();

        if (catId != null) {
            Map<GTVoltageTier, ResourceLocation> tierMap = DEDUCTED_TIER_WORKSTATIONS.get(catId);
            if (tierMap != null) {
                ResourceLocation cached = tierMap.get(tier);
                if (cached != null) return cached;
            }
        }

        ResourceLocation resolved = deductWorkstationFromGTRegistries(catId, tier);
        if (resolved != null) {
            if (catId != null) {
                DEDUCTED_TIER_WORKSTATIONS.computeIfAbsent(catId, k -> new ConcurrentHashMap<>()).put(tier, resolved);
            }
            return resolved;
        }

        if (catId != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(catId);
            if (cap != null && cap.availableWorkstations() != null) {
                String tierName = tier.name().toLowerCase(Locale.ROOT);
                for (ResourceLocation ws : cap.availableWorkstations()) {
                    if (ws != null && !MultiblockDetector.isMultiblock(ws)) {
                        String path = ws.getPath().toLowerCase(Locale.ROOT);
                        if (path.startsWith(tierName + "_") || path.contains("_" + tierName + "_")) {
                            return ws;
                        }
                    }
                }
            }
        }

        if (catId != null && "gtceu".equals(catId.getNamespace())) {
            String cPath = catId.getPath();
            for (GTVoltageTier t : GTVoltageTier.values()) {
                String prefix = t.name().toLowerCase(Locale.ROOT) + "_";
                if (cPath.startsWith(prefix)) {
                    cPath = cPath.substring(prefix.length());
                    break;
                }
            }
            return ResourceLocation.tryParse("gtceu:" + tier.name().toLowerCase(Locale.ROOT) + "_" + cPath);
        }

        return null;
    }

    private static ResourceLocation deductWorkstationFromGTRegistries(ResourceLocation catId, GTVoltageTier targetTier) {
        if (catId == null || targetTier == null) return null;
        Iterable<?> iterable = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachinesRegistryIterable();
        if (iterable != null) {
            for (Object machineDef : iterable) {
                if (machineDef == null) continue;
                ResourceLocation id = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineId(machineDef);
                if (id == null || MultiblockDetector.isMultiblock(id)) continue;

                GTVoltageTier tier = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getMachineTier(machineDef);
                if (tier == targetTier && matchesRecipeType(machineDef, catId)) {
                    return id;
                }
            }
        }
        return null;
    }

    private static boolean matchesRecipeType(Object machineDef, ResourceLocation catId) {
        if (machineDef == null || catId == null) return false;
        List<Object> recipeTypes = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getRecipeTypes(machineDef);
        for (Object rt : recipeTypes) {
            ResourceLocation rtId = MultiblockDetector.extractRecipeTypeId(rt);
            if (catId.equals(rtId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onMachineIconChanged(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        if (node == null || newIcon == null) return;

        if (MultiblockDetector.isSteamMultiblock(newIcon)) {
            node.setMultiblock(true);
            int defPar = MultiblockDetector.getDefaultParallel(newIcon);
            node.setParallel(Math.max(1, defPar));
            node.setSteamMode(SteamMode.HIGH_PRESSURE);
        } else if (MultiblockDetector.isMultiblock(newIcon) || node.isFusion()) {
            node.setMultiblock(true);
            if (node.getSteamMode().isSteam()) {
                node.setSteamMode(SteamMode.NONE);
            }
            int defPar = MultiblockDetector.getDefaultParallel(newIcon);
            if (defPar > 1 && node.getParallel() <= 1) {
                node.setParallel(defPar);
            }
        } else {
            node.setMultiblock(false);
            if (node.getParallel() > 1 && oldIcon != null && MultiblockDetector.isMultiblock(oldIcon)) {
                node.setParallel(1);
            }
        }

        // Addon Compatibility Invalidation & Purge Pipeline (only when switching from an existing machine)
        if (oldIcon != null && !oldIcon.equals(newIcon)) {
            purgeIncompatibleAddons(node, oldIcon, newIcon);
        }

        // Machine Preset Setup
        applyMachinePresets(node, oldIcon, newIcon);
    }

    private void purgeIncompatibleAddons(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        // (A) Coil Purge
        if (!MultiblockDetector.isCoilMultiblock(newIcon) && !node.canUseCoils()) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
        }

        // (B) Parallel Hatch Purge
        if (!MultiblockDetector.supportsParallelHatch(newIcon, null, null)) {
            boolean hadParAddon = node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
            if (hadParAddon) {
                int defPar = MultiblockDetector.getDefaultParallel(newIcon);
                node.setParallel(Math.max(1, defPar));
            }
        }

        // (C) Rotor Purge
        if (!MultiblockDetector.supportsTurbineRotor(newIcon, null) && !MultiblockDetector.isTurbineMachine(newIcon)) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            node.setRotorName(null);
        }

        // (D) Laser Hatch Purge
        if (!MultiblockDetector.supportsLaserHatch(newIcon, null)) {
            node.getAddons().removeIf(a -> a instanceof GTEnergyHatchAddon eh && eh.isLaser());
        }

        // (E) Threading Helix Purge
        if (MultiblockDetector.getMaxHelixCount(newIcon) == 0) {
            node.setThreadingConfig(null);
        }

        // (F) Reflector Purge
        if (!node.isFusion() && node.getRequiredReflectorTier() <= 0) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.REFLECTOR);
        }

        // (G) Innate Multiblock Trait Purge
        if (!MultiblockDetector.supportsThroughputBoosting(newIcon)) {
            node.getAddons().removeIf(a -> a.getId() != null && a.getId().equals("gtceu:throughput_boosting"));
        }
        if (!MultiblockDetector.supportsBulkProcessing(newIcon)) {
            node.getAddons().removeIf(a -> a.getId() != null && a.getId().equals("gtceu:bulk_processing"));
        }
        if (!MultiblockDetector.supportsOverpressure(newIcon)) {
            node.getAddons().removeIf(a -> a.getId() != null && a.getId().equals("gtceu:overpressure_autoclave"));
        }
    }

    private void applyMachinePresets(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        if (oldIcon != null && !oldIcon.equals(newIcon)) {
            if (MultiblockDetector.isTurbineMachine(newIcon)) {
                node.setGenerator(true);
                int defPar = MultiblockDetector.getDefaultParallel(newIcon);
                if (defPar > 1) {
                    node.setParallel(defPar);
                }
                GTVoltageTier baseTier = MultiblockDetector.getTurbineBaseTier(newIcon);
                if (baseTier != null && (node.getTargetTier() == null || (MultiblockDetector.requiresMinimumBaseTier(newIcon) && node.getTargetTier().ordinal() < baseTier.ordinal()))) {
                    node.setTargetTier(baseTier);
                }
            }

            if (MultiblockDetector.supportsThroughputBoosting(newIcon)) {
                boolean hasBoost = node.getAddons().stream().anyMatch(a -> a.getId() != null && a.getId().equals("gtceu:throughput_boosting"));
                if (!hasBoost) {
                    MachineAddon boost = MachineAddonCatalog.getInstance().getAddon("gtceu:throughput_boosting");
                    if (boost != null) {
                        node.addAddon(boost);
                    }
                }
            }
            if (MultiblockDetector.supportsOverpressure(newIcon)) {
                boolean hasOver = node.getAddons().stream().anyMatch(a -> a.getId() != null && a.getId().equals("gtceu:overpressure_autoclave"));
                if (!hasOver) {
                    MachineAddon overpressure = MachineAddonCatalog.getInstance().getAddon("gtceu:overpressure_autoclave");
                    if (overpressure != null) {
                        node.addAddon(overpressure);
                    }
                }
            }
        }

        GTVoltageTier iconTier = extractVoltageTierFromIcon(newIcon);
        if (iconTier != null && !node.isTurbine()) {
            node.setTargetTier(iconTier);
            if (node.isFusion()) {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH
                        && a instanceof GTEnergyHatchAddon eh
                        && eh.getTier() != iconTier);
            }
        }

        if (node.getRecipeCategoryId() != null && VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId())) {
            String newNs = newIcon.getNamespace().toLowerCase(Locale.ROOT);
            if (newNs.equals("gtceu") || newNs.contains("start")) {
                node.setBaseEUt(4.0);
                node.setRecipeTier(GTVoltageTier.LV);
                if (node.getTargetTier() == null || node.getTargetTier() == GTVoltageTier.ULV) {
                    node.setTargetTier(GTVoltageTier.LV);
                }
                node.setBaseDurationTicks(128.0);
                node.setEnergyType(EnergyType.ELECTRIC_EU);
            } else if (newNs.equals("minecraft")) {
                node.setBaseEUt(0.0);
                node.setRecipeTier(GTVoltageTier.ULV);
                node.setTargetTier(GTVoltageTier.ULV);
                node.setBaseDurationTicks(200.0);
                node.setEnergyType(EnergyType.NONE);
            }
        }
    }

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        return GTPowerCalculator.getEnergyType(node);
    }

    @Override
    public void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        if (newMode != null && newMode.isSteam()) {
            double durTicks = node.getBaseDurationTicks() * newMode.getDurationMultiplier();
            double baseEu = (node.getBaseEUt() > 0) ? node.getBaseEUt() : 4.0;
            double steamAmountPerBatch;
            if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), newMode);
                int parallel = Math.max(1, node.getParallel());
                steamAmountPerBatch = (steamRatePerTick * durTicks) / parallel;
            } else {
                steamAmountPerBatch = (baseEu * 2.0) * durTicks;
            }

            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            node.getInputs().add(IngredientStack.fluid(steamId, "Steam", steamAmountPerBatch));

            if (!node.isMultiblock() && !MultiblockDetector.isSteamMultiblock(node.getMachineIcon()) && node.getRecipeCategoryId() != null) {
                CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
                if (newMode == SteamMode.LOW_PRESSURE) {
                    if (cap != null && cap.lowPressureWorkstation() != null) {
                        node.setMachineIcon(cap.lowPressureWorkstation());
                    } else if (VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId())) {
                        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lp_steam_furnace"));
                    }
                } else if (newMode == SteamMode.HIGH_PRESSURE) {
                    if (cap != null && cap.highPressureWorkstation() != null) {
                        node.setMachineIcon(cap.highPressureWorkstation());
                    } else if (VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId())) {
                        node.setMachineIcon(ResourceLocation.tryParse("gtceu:hp_steam_furnace"));
                    }
                }
            }
        } else if (oldMode != null && oldMode.isSteam()) {
            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            if (!node.isMultiblock() && !MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                ResourceLocation sbWs = node.getWorkstationForTier(node.getTargetTier());
                if (sbWs == null) {
                    sbWs = node.getSingleblockWorkstation();
                }
                if (sbWs != null) {
                    node.setMachineIcon(sbWs);
                }
            }
        }
    }

    @Override
    public double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (isInput && stack != null && stack.isFluid() && stack.getId() != null && "gtceu:steam".equals(stack.getId().toString())) {
            if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
                if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                    double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), node.getSteamMode());
                    return steamRatePerTick * 20.0 * node.getMachineCount();
                } else {
                    return (node.getBaseEUt() * 2.0 * 20.0) * node.getMachineCount();
                }
            }
        }
        return defaultRate;
    }

    @Override
    public double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (isInput && stack != null && stack.isFluid() && stack.getId() != null && "gtceu:steam".equals(stack.getId().toString())) {
            if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
                if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                    double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), node.getSteamMode());
                    return steamRatePerTick * 20.0;
                } else {
                    return node.getBaseEUt() * 2.0 * 20.0;
                }
            }
        }
        return defaultRate;
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
    public GTVoltageTier sanitizeTargetTier(RecipeNode node, GTVoltageTier requestedTier) {
        GTVoltageTier tier = requestedTier != null ? requestedTier : (node != null ? node.getRecipeTier() : GTVoltageTier.ULV);
        if (node != null && isFusion(node)) {
            GTVoltageTier minTier = getMinFusionVoltageTier(node);
            if (tier.ordinal() < minTier.ordinal()) {
                tier = minTier;
            }
        }
        return tier;
    }
}
