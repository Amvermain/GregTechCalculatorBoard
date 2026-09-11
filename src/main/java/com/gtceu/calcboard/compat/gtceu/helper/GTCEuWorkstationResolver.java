package com.gtceu.calcboard.compat.gtceu.helper;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.api.util.RecipeConversionHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GTCEuWorkstationResolver {

    private GTCEuWorkstationResolver() {}

    private static final Map<ResourceLocation, Map<GTVoltageTier, ResourceLocation>> DEDUCTED_TIER_WORKSTATIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<ResourceLocation>> DEDUCTED_MULTIBLOCK_WORKSTATIONS = new ConcurrentHashMap<>();

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

    public static GTVoltageTier extractVoltageTierFromIcon(ResourceLocation icon) {
        if (icon == null) return null;

        Object def = GTCEuReflectionBridge.getMachineDefinition(icon);
        if (def != null) {
            GTVoltageTier tier = GTCEuReflectionBridge.getMachineTier(def);
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

    public static List<ResourceLocation> getMultiblockWorkstations(RecipeNode node) {
        if (node == null) return Collections.emptyList();
        List<ResourceLocation> result = new ArrayList<>();

        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && MultiblockDetector.isMultiblock(ws) && !result.contains(ws)) {
                result.add(ws);
            }
        }

        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            List<ResourceLocation> cached = DEDUCTED_MULTIBLOCK_WORKSTATIONS.computeIfAbsent(catId, GTCEuWorkstationResolver::deductMultiblocksFromGTRegistries);
            for (ResourceLocation mb : cached) {
                if (mb != null && !result.contains(mb)) {
                    result.add(mb);
                }
            }
        }

        appendCapabilityMultiblocks(catId, result);

        if (result.isEmpty() && node.getMachineIcon() != null && MultiblockDetector.isMultiblock(node.getMachineIcon())) {
            result.add(node.getMachineIcon());
        }

        if (result.size() > 1) {
            result.sort((a, b) -> {
                if (catId != null) {
                    String catPath = catId.getPath().toLowerCase(Locale.ROOT);
                    boolean aMatch = a.equals(catId) || a.getPath().endsWith("_" + catPath);
                    boolean bMatch = b.equals(catId) || b.getPath().endsWith("_" + catPath);
                    if (aMatch != bMatch) return aMatch ? -1 : 1;
                }

                GTVoltageTier tierA = extractVoltageTierFromIcon(a);
                GTVoltageTier tierB = extractVoltageTierFromIcon(b);
                int tA = tierA != null ? tierA.ordinal() : -1;
                int tB = tierB != null ? tierB.ordinal() : -1;
                if (tA != tB) {
                    return Integer.compare(tA, tB);
                }

                return a.toString().compareTo(b.toString());
            });
        }

        return result;
    }

    private static void appendCapabilityMultiblocks(ResourceLocation catId, List<ResourceLocation> result) {
        if (!result.isEmpty() || catId == null) return;
        CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(catId);
        if (cap == null || cap.availableWorkstations() == null) return;
        for (ResourceLocation ws : cap.availableWorkstations()) {
            if (ws != null && MultiblockDetector.isMultiblock(ws) && !result.contains(ws)) {
                result.add(ws);
            }
        }
    }

    public static List<ResourceLocation> deductMultiblocksFromGTRegistries(ResourceLocation catId) {
        List<ResourceLocation> list = new ArrayList<>();
        if (catId == null) return list;
        Iterable<?> iterable = GTCEuReflectionBridge.getMachinesRegistryIterable();
        if (iterable == null) return list;
        for (Object machineDef : iterable) {
            ResourceLocation id = extractMatchingMultiblockId(machineDef, catId);
            if (id != null && !list.contains(id)) {
                list.add(id);
            }
        }
        return list;
    }

    private static ResourceLocation extractMatchingMultiblockId(Object machineDef, ResourceLocation catId) {
        if (machineDef == null) return null;
        ResourceLocation id = GTCEuReflectionBridge.getMachineId(machineDef);
        if (id == null || !MultiblockDetector.isMultiblock(id)) return null;
        return matchesRecipeType(machineDef, catId) ? id : null;
    }

    public static ResourceLocation getWorkstationForTier(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return null;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return null;
        }
        if (GTPowerCalculator.isBoilerRecipe(node) || GTPowerCalculator.isLiquidBoilerRecipe(node)) {
            return null;
        }

        if (GTCombustionHelper.isCombustionFamily(node)) {
            ResourceLocation combustionWs = GTCombustionHelper.getCombustionMachineForTier(tier);
            if (combustionWs != null) {
                return combustionWs;
            }
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

        ResourceLocation wsFromCap = findWorkstationInCapability(catId, tier);
        if (wsFromCap != null) {
            return wsFromCap;
        }

        if (catId != null && "gtceu".equals(catId.getNamespace())) {
            if (tier == GTVoltageTier.ULV) {
                return null;
            }
            String cPath = catId.getPath();
            for (GTVoltageTier t : GTVoltageTier.values()) {
                String prefix = t.name().toLowerCase(Locale.ROOT) + "_";
                if (cPath.startsWith(prefix)) {
                    cPath = cPath.substring(prefix.length());
                    break;
                }
            }
            ResourceLocation cand = ResourceLocation.tryParse("gtceu:" + tier.name().toLowerCase(Locale.ROOT) + "_" + cPath);
            var itemReg = net.minecraftforge.registries.ForgeRegistries.ITEMS;
            boolean hasGtItems = itemReg != null && itemReg.containsKey(ResourceLocation.tryParse("gtceu:lv_macerator"));
            if (cand != null && (itemReg == null || itemReg.isEmpty() || !hasGtItems || itemReg.containsKey(cand))) {
                return cand;
            }
            return null;
        }

        return null;
    }

    private static ResourceLocation findWorkstationInCapability(ResourceLocation catId, GTVoltageTier tier) {
        if (catId == null) return null;
        CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(catId);
        if (cap == null || cap.availableWorkstations() == null) return null;
        String tierName = tier.name().toLowerCase(Locale.ROOT);
        for (ResourceLocation ws : cap.availableWorkstations()) {
            if (ws == null || MultiblockDetector.isMultiblock(ws)) continue;
            String path = ws.getPath().toLowerCase(Locale.ROOT);
            if (path.startsWith(tierName + "_") || path.contains("_" + tierName + "_")) {
                return ws;
            }
        }
        return null;
    }

    public static ResourceLocation deductWorkstationFromGTRegistries(ResourceLocation catId, GTVoltageTier targetTier) {
        if (catId == null || targetTier == null) return null;
        Iterable<?> iterable = GTCEuReflectionBridge.getMachinesRegistryIterable();
        if (iterable != null) {
            for (Object machineDef : iterable) {
                if (machineDef == null) continue;
                ResourceLocation id = GTCEuReflectionBridge.getMachineId(machineDef);
                if (id == null || MultiblockDetector.isMultiblock(id)) continue;

                GTVoltageTier tier = GTCEuReflectionBridge.getMachineTier(machineDef);
                if (tier == targetTier && matchesRecipeType(machineDef, catId)) {
                    return id;
                }
            }
        }
        return null;
    }

    public static boolean matchesRecipeType(Object machineDef, ResourceLocation catId) {
        if (machineDef == null || catId == null) return false;
        List<Object> recipeTypes = GTCEuReflectionBridge.getRecipeTypes(machineDef);
        for (Object rt : recipeTypes) {
            ResourceLocation rtId = MultiblockDetector.extractRecipeTypeId(rt);
            if (catId.equals(rtId)) {
                return true;
            }
        }
        return false;
    }

    public static GTVoltageTier getMinimumWorkstationTier(RecipeNode node) {
        if (node == null) return null;
        List<ResourceLocation> workstations = node.getAvailableWorkstations();
        if (workstations == null || workstations.isEmpty()) {
            ResourceLocation catId = node.getRecipeCategoryId();
            if (catId != null) {
                CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(catId);
                if (cap != null && cap.availableWorkstations() != null) {
                    workstations = cap.availableWorkstations();
                }
            }
        }
        if (workstations == null || workstations.isEmpty()) {
            ResourceLocation catId = node.getRecipeCategoryId();
            if (catId != null && "gtceu".equals(catId.getNamespace())) {
                return GTVoltageTier.LV;
            }
            return null;
        }

        GTVoltageTier minTier = null;
        for (ResourceLocation ws : workstations) {
            if (ws == null || RecipeConversionHelper.isDummyConditionMarker(ws) || RecipeConversionHelper.isIgnoredWorkstation(ws)) {
                continue;
            }
            GTVoltageTier tier = extractVoltageTierFromIcon(ws);
            if (tier != null && (minTier == null || tier.ordinal() < minTier.ordinal())) {
                minTier = tier;
            }
        }
        if (minTier == null) {
            ResourceLocation catId = node.getRecipeCategoryId();
            if (catId != null && "gtceu".equals(catId.getNamespace())) {
                return GTVoltageTier.LV;
            }
        }
        return minTier;
    }

    public static GTVoltageTier sanitizeTargetTier(RecipeNode node, GTVoltageTier requestedTier) {
        if (node != null && com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.hasEnergyHatch(node)) {
            GTVoltageTier hatchTier = com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.getPrimaryEnergyHatchTier(node);
            if (hatchTier != null) {
                return hatchTier;
            }
        }

        GTVoltageTier tier = requestedTier != null ? requestedTier : (node != null ? node.getRecipeTier() : GTVoltageTier.ULV);
        if (node == null) return tier;

        tier = clampToRecipeMinimumTier(node, tier);
        tier = clampToFusionMinimumTier(node, tier);
        tier = clampToTurbineMinimumTier(node, tier);
        tier = clampToWorkstationMinimumTier(node, tier);
        return tier;
    }

    private static GTVoltageTier clampToRecipeMinimumTier(RecipeNode node, GTVoltageTier tier) {
        if (node.isTurbine()) {
            return tier;
        }
        boolean isVanillaCooking = node.getRecipeCategoryId() != null && GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId());
        boolean isPassiveOrSteam = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || node.getEnergyType() == EnergyType.NONE;
        if (!isVanillaCooking && !isPassiveOrSteam && node.getRecipeTier() != null) {
            if (tier.ordinal() < node.getRecipeTier().ordinal()) {
                return node.getRecipeTier();
            }
        }
        return tier;
    }

    private static GTVoltageTier clampToFusionMinimumTier(RecipeNode node, GTVoltageTier tier) {
        if (GTFusionHelper.isFusion(node)) {
            GTVoltageTier minTier = GTFusionHelper.getMinFusionVoltageTier(node);
            if (minTier != null && tier.ordinal() < minTier.ordinal()) {
                return minTier;
            }
        }
        return tier;
    }

    private static GTVoltageTier clampToTurbineMinimumTier(RecipeNode node, GTVoltageTier tier) {
        if (node.isTurbine() && node.isMultiblock()) {
            GTVoltageTier baseTier = GTTurbineHelper.getTurbineBaseTier(node);
            if (baseTier != null && tier.ordinal() < baseTier.ordinal()) {
                return baseTier;
            }
        }
        return tier;
    }

    private static GTVoltageTier clampToWorkstationMinimumTier(RecipeNode node, GTVoltageTier tier) {
        boolean isVanillaCooking = node.getRecipeCategoryId() != null && GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId());
        boolean isPassiveOrSteam = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || node.getEnergyType() == EnergyType.NONE;
        if (isVanillaCooking || isPassiveOrSteam) {
            return tier;
        }
        GTVoltageTier minWsTier = getMinimumWorkstationTier(node);
        if (minWsTier != null && tier.ordinal() < minWsTier.ordinal()) {
            return minWsTier;
        }
        return tier;
    }
}
