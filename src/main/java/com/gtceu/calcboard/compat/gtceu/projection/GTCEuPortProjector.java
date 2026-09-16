package com.gtceu.calcboard.compat.gtceu.projection;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.PortRole;
import com.gtceu.calcboard.api.model.ProjectedPort;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.spi.extension.IPortProjectionProvider;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFFuel;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleSlot;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleType;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic port projection provider for GregTech machines, boilers, turbines, and modular combustion frames.
 * <p>
 * Projects auxiliary fluid ports (steam, oxygen boost, coolant, lubricant) while preserving core recipe inputs.
 */
public final class GTCEuPortProjector implements IPortProjectionProvider {

    private static final GTCEuPortProjector INSTANCE = new GTCEuPortProjector();
    private static final ResourceLocation STEAM_ID = ResourceLocation.tryParse("gtceu:steam");

    private GTCEuPortProjector() {}

    public static GTCEuPortProjector getInstance() {
        return INSTANCE;
    }

    @Override
    public List<ProjectedPort> projectInputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (node == null) return Collections.emptyList();
        if (com.gtceu.calcboard.compat.tfg.TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return com.gtceu.calcboard.compat.tfg.TFGBoilerPortProjectionProvider.getInstance().projectInputPorts(node, baseSpec);
        }
        if (GTCombustionHelper.isModularCombustionFrame(node)) {
            return projectMCFInputs(node);
        }

        List<ProjectedPort> ports = new ArrayList<>();
        appendCoreInputs(baseSpec, ports);
        appendSteamInput(node, ports);
        appendCombustionInputs(node, ports);
        return Collections.unmodifiableList(ports);
    }

    @Override
    public List<ProjectedPort> projectOutputPorts(RecipeNode node, RecipeSpec baseSpec) {
        if (node == null) return Collections.emptyList();
        if (com.gtceu.calcboard.compat.tfg.TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return com.gtceu.calcboard.compat.tfg.TFGBoilerPortProjectionProvider.getInstance().projectOutputPorts(node, baseSpec);
        }
        if (baseSpec == null || baseSpec.baseOutputs() == null) {
            return Collections.emptyList();
        }
        List<ProjectedPort> ports = new ArrayList<>(baseSpec.baseOutputs().size());
        for (int i = 0; i < baseSpec.baseOutputs().size(); i++) {
            ports.add(ProjectedPort.ofCore(baseSpec.baseOutputs().get(i), i));
        }
        return Collections.unmodifiableList(ports);
    }

    private static void appendCoreInputs(RecipeSpec baseSpec, List<ProjectedPort> ports) {
        if (baseSpec == null || baseSpec.baseInputs() == null) return;
        for (int i = 0; i < baseSpec.baseInputs().size(); i++) {
            ports.add(ProjectedPort.ofCore(baseSpec.baseInputs().get(i), i));
        }
    }

    private static void appendSteamInput(RecipeNode node, List<ProjectedPort> ports) {
        SteamMode steamMode = node.getSteamMode();
        if (steamMode == null || !steamMode.isSteam() || STEAM_ID == null) return;

        double durTicks = node.getBaseDurationTicks() * steamMode.getDurationMultiplier();
        double baseEu = node.getBaseEUt() > 0 ? node.getBaseEUt() : 4.0;
        double batchAmount = computeSteamBatchAmount(node, steamMode, durTicks, baseEu);
        ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(STEAM_ID, "Steam", batchAmount), "gtceu:steam_mode"));
    }

    private static double computeSteamBatchAmount(RecipeNode node, SteamMode steamMode, double durTicks, double baseEu) {
        if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
            double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), steamMode);
            int parallel = Math.max(1, node.getParallel());
            return (steamRatePerTick * durTicks) / parallel;
        }
        return (baseEu * 2.0) * durTicks;
    }

    private static void appendCombustionInputs(RecipeNode node, List<ProjectedPort> ports) {
        if (!GTCombustionHelper.isCombustionEngine(node)) return;

        double durSec = Math.max(0.05, node.getBaseDurationTicks() / 20.0);
        int parallel = Math.max(1, GTPowerCalculator.computeEffectiveParallel(node));

        if (GTCombustionHelper.isLargeCombustionEngine(node)) {
            appendLCEInputs(node, ports, durSec, parallel);
        } else if (GTCombustionHelper.isExtremeCombustionEngine(node)) {
            appendECEInputs(node, ports, durSec, parallel);
        } else if (GTCombustionHelper.isStarTCombustionModule(node) || GTCombustionHelper.isStarTRocketModule(node)) {
            appendStarTModuleInputs(node, ports, durSec, parallel);
        }
    }

    private static void appendLCEInputs(RecipeNode node, List<ProjectedPort> ports, double durSec, int parallel) {
        if (!GTCombustionHelper.isOxygenBoosted(node)) return;
        double batchAmount = (20.0 * durSec) / parallel;
        ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(GTCombustionHelper.OXYGEN, "Oxygen", batchAmount), "gtceu:oxygen_boost"));
    }

    private static void appendECEInputs(RecipeNode node, List<ProjectedPort> ports, double durSec, int parallel) {
        if (!GTCombustionHelper.isLiquidOxygenBoosted(node)) return;
        double batchAmount = (80.0 * durSec) / parallel;
        ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(GTCombustionHelper.LIQUID_OXYGEN, "Liquid Oxygen", batchAmount), "gtceu:liquid_oxygen_boost"));
    }

    private static void appendStarTModuleInputs(RecipeNode node, List<ProjectedPort> ports, double durSec, int parallel) {
        ResourceLocation icon = node.getMachineIcon();
        if (GTCombustionHelper.START_T1_COMBUSTION.equals(icon)) {
            projectStarTTier(ports, GTCombustionHelper.LUBRICANT, "Lubricant", 100.0, GTCombustionHelper.WHITE_FUMING_NITRIC_ACID, "White Fuming Nitric Acid", 324.0, node, durSec, parallel, "start_core:t1_oxidizer_boost");
        } else if (GTCombustionHelper.START_T2_COMBUSTION.equals(icon)) {
            projectStarTTier(ports, GTCombustionHelper.LUBRICANT, "Lubricant", 200.0, GTCombustionHelper.RED_FUMING_NITRIC_ACID, "Red Fuming Nitric Acid", 432.0, node, durSec, parallel, "start_core:t2_oxidizer_boost");
        } else if (GTCombustionHelper.START_T3_ROCKET.equals(icon)) {
            projectStarTTier(ports, GTCombustionHelper.TUNGSTEN_DISULFIDE, "Tungsten Disulfide", 200.0, GTCombustionHelper.DIOXYGEN_DIFLUORIDE, "Dioxygen Difluoride", 756.0, node, durSec, parallel, "start_core:t3_oxidizer_boost");
        } else if (GTCombustionHelper.START_T4_ROCKET.equals(icon)) {
            projectStarTTier(ports, GTCombustionHelper.TUNGSTEN_DISULFIDE, "Tungsten Disulfide", 400.0, GTCombustionHelper.FERROCENIUM_SUPEROXIDE, "Ferrocenium Superoxide", 864.0, node, durSec, parallel, "start_core:t4_oxidizer_boost");
        }
    }

    private static void projectStarTTier(List<ProjectedPort> ports, ResourceLocation lubeId, String lubeName, double lubeMb, ResourceLocation oxId, String oxName, double oxMb, RecipeNode node, double durSec, int parallel, String boostAddonId) {
        double lubeBatch = ((lubeMb / 3.6) * durSec) / parallel;
        ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(lubeId, lubeName, lubeBatch), "start_core:lubricant"));
        if (GTCombustionHelper.isOxidizerBoosted(node)) {
            double oxBatch = ((oxMb / 3.6) * durSec) / parallel;
            ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(oxId, oxName, oxBatch), boostAddonId));
        }
    }

    private static List<ProjectedPort> projectMCFInputs(RecipeNode node) {
        double durSec = Math.max(0.05, node.getBaseDurationTicks() / 20.0);
        int parallel = Math.max(1, GTPowerCalculator.computeEffectiveParallel(node));
        List<ProjectedPort> ports = new ArrayList<>();

        int coreIdx = 0;
        for (Map.Entry<MCFFuel, Double> entry : GTCombustionHelper.getMCFFuelDemandMbPerSec(node).entrySet()) {
            MCFFuel fuel = entry.getKey();
            double batch = (entry.getValue() * durSec) / parallel;
            ports.add(new ProjectedPort(IngredientStack.fluid(fuel.getFluidId(), fuel.getDisplayName(), batch), PortRole.CORE_RECIPE, coreIdx++, null));
        }

        appendMCFCoolant(node, ports, durSec, parallel);
        appendMCFAuxFluids(node, ports, durSec, parallel);
        return Collections.unmodifiableList(ports);
    }

    private static void appendMCFCoolant(RecipeNode node, List<ProjectedPort> ports, double durSec, int parallel) {
        double rate = GTCombustionHelper.getCentralCoolantDemandMbPerSec(node);
        if (rate <= 0.0) return;
        double batch = (rate * durSec) / parallel;
        String coolant = GTCombustionHelper.getMCFCoolantType(node);
        if ("distilled_water".equalsIgnoreCase(coolant)) {
            ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(GTCombustionHelper.DISTILLED_WATER, "Distilled Water", batch), "start_core:distilled_water_coolant"));
        } else if ("deionized_water".equalsIgnoreCase(coolant)) {
            ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(GTCombustionHelper.DEIONIZED_WATER, "Deionized Water", batch), "start_core:deionized_water_coolant"));
        }
    }

    private static void appendMCFAuxFluids(RecipeNode node, List<ProjectedPort> ports, double durSec, int parallel) {
        MCFSlotConfiguration config = GTCombustionHelper.getMCFConfiguration(node);
        Map<ResourceLocation, Double> auxRates = new LinkedHashMap<>();
        for (MCFModuleSlot slot : config.getActiveSlots()) {
            MCFModuleType type = slot.getModuleType();
            if (type == null) continue;
            auxRates.merge(type.getLubricantFluid(), type.getLubricantMbPerPeriod() / 3.6, Double::sum);
            if (slot.isOxidizerBoosted()) {
                auxRates.merge(type.getOxidizerFluid(), type.getOxidizerMbPerPeriod() / 3.6, Double::sum);
            }
        }
        for (Map.Entry<ResourceLocation, Double> entry : auxRates.entrySet()) {
            double batch = (entry.getValue() * durSec) / parallel;
            ports.add(ProjectedPort.ofAuxInput(IngredientStack.fluid(entry.getKey(), resolveFluidDisplayName(entry.getKey()), batch), "start_core:mcf_auxiliary"));
        }
    }

    private static String resolveFluidDisplayName(ResourceLocation id) {
        if (id == null) return "Fluid";
        MCFFuel fuel = MCFFuel.fromFluidId(id);
        if (fuel != null) return fuel.getDisplayName();
        if (GTCombustionHelper.LUBRICANT.equals(id)) return "Lubricant";
        if (GTCombustionHelper.TUNGSTEN_DISULFIDE.equals(id)) return "Tungsten Disulfide";
        if (GTCombustionHelper.WHITE_FUMING_NITRIC_ACID.equals(id)) return "White Fuming Nitric Acid";
        if (GTCombustionHelper.RED_FUMING_NITRIC_ACID.equals(id)) return "Red Fuming Nitric Acid";
        if (GTCombustionHelper.DIOXYGEN_DIFLUORIDE.equals(id)) return "Dioxygen Difluoride";
        if (GTCombustionHelper.FERROCENIUM_SUPEROXIDE.equals(id)) return "Ferrocenium Superoxide";
        if (GTCombustionHelper.DISTILLED_WATER.equals(id)) return "Distilled Water";
        if (GTCombustionHelper.DEIONIZED_WATER.equals(id)) return "Deionized Water";
        return id.getPath();
    }

    @Override
    public List<IngredientStack> sanitizeLegacyCoreInputs(RecipeNode node, List<IngredientStack> savedInputs) {
        if (savedInputs == null) return Collections.emptyList();
        if (com.gtceu.calcboard.compat.tfg.TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return com.gtceu.calcboard.compat.tfg.TFGBoilerPortProjectionProvider.getInstance().sanitizeLegacyCoreInputs(node, savedInputs);
        }
        List<IngredientStack> result = new ArrayList<>();
        boolean isSteam = node != null && node.getSteamMode() != null && node.getSteamMode().isSteam();

        for (IngredientStack in : savedInputs) {
            if (isSteam && in.isFluid() && STEAM_ID != null && STEAM_ID.equals(in.getId())) {
                continue;
            }
            if (node != null && GTCombustionHelper.isModularCombustionFrame(node) && in.isFluid()) {
                ResourceLocation id = in.getId();
                if (GTCombustionHelper.DISTILLED_WATER.equals(id)
                        || GTCombustionHelper.DEIONIZED_WATER.equals(id)
                        || GTCombustionHelper.LUBRICANT.equals(id)
                        || GTCombustionHelper.TUNGSTEN_DISULFIDE.equals(id)) {
                    continue;
                }
            }
            if (node != null && GTCombustionHelper.isCombustionEngine(node) && in.isFluid()) {
                ResourceLocation id = in.getId();
                if (ResourceLocation.tryParse("gtceu:oxygen").equals(id)
                        || ResourceLocation.tryParse("gtceu:liquid_oxygen").equals(id)
                        || GTCombustionHelper.LUBRICANT.equals(id)
                        || GTCombustionHelper.TUNGSTEN_DISULFIDE.equals(id)
                        || GTCombustionHelper.RED_FUMING_NITRIC_ACID.equals(id)
                        || GTCombustionHelper.DIOXYGEN_DIFLUORIDE.equals(id)
                        || GTCombustionHelper.FERROCENIUM_SUPEROXIDE.equals(id)) {
                    continue;
                }
            }
            result.add(in.copy());
        }
        return result;
    }
}
