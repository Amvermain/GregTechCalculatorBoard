package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog managing baseline and custom GT voltage tiers and baseline productions for large turbines.
 */
public final class TurbineCatalog {

    private static final Map<ResourceLocation, GTVoltageTier> TURBINE_BASE_TIERS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Double> TURBINE_BASE_PRODUCTIONS = new ConcurrentHashMap<>();

    private static final java.util.Set<ResourceLocation> GAS_TURBINE_IDS = java.util.Set.of(
            ResourceLocation.tryParse("gtceu:large_gas_turbine"),
            ResourceLocation.tryParse("gtceu:gas_large_turbine"),
            ResourceLocation.tryParse("gtceu:gas_turbine"),
            ResourceLocation.tryParse("gtceu:gas_turbine_fuels")
    );
    private static final java.util.Set<ResourceLocation> PLASMA_TURBINE_IDS = java.util.Set.of(
            ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
            ResourceLocation.tryParse("gtceu:plasma_large_turbine"),
            ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"),
            ResourceLocation.tryParse("start_core:supreme_plasma_turbine"),
            ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"),
            ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine"),
            ResourceLocation.tryParse("gtceu:plasma_turbine"),
            ResourceLocation.tryParse("gtceu:plasma_generator"),
            ResourceLocation.tryParse("gtceu:plasma_generator_fuels")
    );
    private static final java.util.Set<ResourceLocation> STEAM_TURBINE_IDS = java.util.Set.of(
            ResourceLocation.tryParse("gtceu:large_steam_turbine"),
            ResourceLocation.tryParse("gtceu:steam_large_turbine"),
            ResourceLocation.tryParse("gtceu:steam_turbine"),
            ResourceLocation.tryParse("gtceu:steam_turbine_fuels"),
            ResourceLocation.tryParse("gtceu:steam_turbine_superheated")
    );

    public static GTVoltageTier classifyTurbineId(ResourceLocation id) {
        if (id == null) return null;
        if (GAS_TURBINE_IDS.contains(id)) return GTVoltageTier.EV;
        if (PLASMA_TURBINE_IDS.contains(id)) return GTVoltageTier.IV;
        if (STEAM_TURBINE_IDS.contains(id)) return GTVoltageTier.HV;
        return null;
    }

    private TurbineCatalog() {}

    public static void registerTurbineTierAndProduction(ResourceLocation id, GTVoltageTier baseTier, double baseProduction) {
        if (id == null) return;
        if (baseTier != null) TURBINE_BASE_TIERS.put(id, baseTier);
        if (baseProduction > 0) TURBINE_BASE_PRODUCTIONS.put(id, baseProduction);
    }

    public static void clear() {
        TURBINE_BASE_TIERS.clear();
        TURBINE_BASE_PRODUCTIONS.clear();
    }

    public static void registerBaselineTurbines() {
        ResourceLocation lst1 = ResourceLocation.tryParse("gtceu:large_steam_turbine");
        ResourceLocation lst2 = ResourceLocation.tryParse("gtceu:steam_large_turbine");
        ResourceLocation lgt1 = ResourceLocation.tryParse("gtceu:large_gas_turbine");
        ResourceLocation lgt2 = ResourceLocation.tryParse("gtceu:gas_large_turbine");
        ResourceLocation lpt1 = ResourceLocation.tryParse("gtceu:large_plasma_turbine");
        ResourceLocation lpt2 = ResourceLocation.tryParse("gtceu:plasma_large_turbine");
        ResourceLocation spt = ResourceLocation.tryParse("gtceu:supreme_plasma_turbine");
        ResourceLocation sptStart = ResourceLocation.tryParse("start_core:supreme_plasma_turbine");
        ResourceLocation npt = ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine");
        ResourceLocation nptStart = ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine");

        ResourceLocation st = ResourceLocation.tryParse("gtceu:steam_turbine");
        ResourceLocation stFuels = ResourceLocation.tryParse("gtceu:steam_turbine_fuels");
        ResourceLocation stSuper = ResourceLocation.tryParse("gtceu:steam_turbine_superheated");
        ResourceLocation gt = ResourceLocation.tryParse("gtceu:gas_turbine");
        ResourceLocation gtFuels = ResourceLocation.tryParse("gtceu:gas_turbine_fuels");
        ResourceLocation pt = ResourceLocation.tryParse("gtceu:plasma_turbine");
        ResourceLocation plasmaGen = ResourceLocation.tryParse("gtceu:plasma_generator");
        ResourceLocation plasmaGenFuels = ResourceLocation.tryParse("gtceu:plasma_generator_fuels");

        MultiblockDetector.registerTurbine(lst1, st, GTVoltageTier.HV, 1024.0);
        MultiblockDetector.registerTurbine(lst2, stFuels, GTVoltageTier.HV, 1024.0);
        MultiblockDetector.registerTurbine(null, stSuper, GTVoltageTier.HV, 1024.0);

        MultiblockDetector.registerTurbine(lgt1, gt, GTVoltageTier.EV, 4096.0);
        MultiblockDetector.registerTurbine(lgt2, gtFuels, GTVoltageTier.EV, 4096.0);

        MultiblockDetector.registerTurbine(lpt1, pt, GTVoltageTier.IV, 16384.0);
        MultiblockDetector.registerTurbine(lpt2, plasmaGen, GTVoltageTier.IV, 16384.0);
        MultiblockDetector.registerTurbine(null, plasmaGenFuels, GTVoltageTier.IV, 16384.0);

        MultiblockDetector.registerTurbine(spt, null, GTVoltageTier.IV, 98304.0);
        MultiblockDetector.registerTurbine(sptStart, null, GTVoltageTier.IV, 98304.0);
        MultiblockDetector.registerTurbine(npt, null, GTVoltageTier.IV, 196608.0);
        MultiblockDetector.registerTurbine(nptStart, null, GTVoltageTier.IV, 196608.0);
    }

    public static ResourceLocation getTurbineAlias(ResourceLocation id) {
        if (id == null) return null;
        String path = id.getPath();
        if (path.startsWith("large_") && path.endsWith("_turbine")) {
            String middle = path.substring("large_".length(), path.length() - "_turbine".length());
            return ResourceLocation.tryParse(id.getNamespace() + ":" + middle + "_large_turbine");
        } else if (path.endsWith("_large_turbine")) {
            String prefix = path.substring(0, path.length() - "_large_turbine".length());
            return ResourceLocation.tryParse(id.getNamespace() + ":large_" + prefix + "_turbine");
        }
        return null;
    }

    public static GTVoltageTier getTurbineBaseTier(ResourceLocation id) {
        if (id == null) return null;
        GTVoltageTier tier = TURBINE_BASE_TIERS.get(id);
        if (tier == null) {
            ResourceLocation alias = getTurbineAlias(id);
            if (alias != null) tier = TURBINE_BASE_TIERS.get(alias);
        }
        if (tier == null) {
            tier = classifyTurbineId(id);
        }
        return tier;
    }

    public static Double getTurbineBaseProduction(ResourceLocation id) {
        if (id == null) return null;
        Double prod = TURBINE_BASE_PRODUCTIONS.get(id);
        if (prod == null) {
            ResourceLocation alias = getTurbineAlias(id);
            if (alias != null) prod = TURBINE_BASE_PRODUCTIONS.get(alias);
        }
        if (prod == null) {
            GTVoltageTier tier = getTurbineBaseTier(id);
            if (tier != null) {
                prod = (double) (tier.getVoltage() * 2L);
            }
        }
        return prod;
    }

    public static GTVoltageTier getTurbineBaseTier(RecipeNode node) {
        if (node == null) return GTVoltageTier.HV;
        if (node.getMachineIcon() != null) {
            GTVoltageTier t = getTurbineBaseTier(node.getMachineIcon());
            if (t != null) return t;
        }
        if (node.getRecipeCategoryId() != null) {
            GTVoltageTier t = getTurbineBaseTier(node.getRecipeCategoryId());
            if (t != null) return t;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                GTVoltageTier t = getTurbineBaseTier(ws);
                if (t != null) return t;
            }
        }
        ResourceLocation cat = node.getRecipeCategoryId();
        if (cat != null) {
            GTVoltageTier t = classifyTurbineId(cat);
            if (t != null) return t;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            GTVoltageTier t = classifyTurbineId(icon);
            if (t != null) return t;
        }
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return GTVoltageTier.HV;
        return GTVoltageTier.HV;
    }

    public static double getTurbineBaseProduction(RecipeNode node) {
        if (node == null) return 1024.0;
        if (node.getMachineIcon() != null) {
            Double prod = getTurbineBaseProduction(node.getMachineIcon());
            if (prod != null && prod > 0) return prod;
        }
        if (node.getRecipeCategoryId() != null) {
            Double prod = getTurbineBaseProduction(node.getRecipeCategoryId());
            if (prod != null && prod > 0) return prod;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                Double prod = getTurbineBaseProduction(ws);
                if (prod != null && prod > 0) return prod;
            }
        }
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        return baseTier != null ? (double) (baseTier.getVoltage() * 2L) : 1024.0;
    }

    public static boolean requiresMinimumBaseTier(ResourceLocation turbineId) {
        if (turbineId == null) return false;
        GTVoltageTier baseTier = getTurbineBaseTier(turbineId);
        return baseTier != null && baseTier.ordinal() >= GTVoltageTier.IV.ordinal();
    }
}
