package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod Adapter facade for GregTech CEu Modern (GTCEu).
 * Manages GTCEu hardware addons (coils, rotors, reflectors, parallel hatches), recipes, and dialog controls.
 */
public class GTCEuModAdapter implements IModAdapter {

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
            Class<?> mlClass = Class.forName("net.minecraftforge.fml.ModList");
            Object ml = mlClass.getMethod("get").invoke(null);
            if (ml != null) {
                return (boolean) mlClass.getMethod("isLoaded", String.class).invoke(ml, "gtceu");
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace().toLowerCase(Locale.ROOT);
        return ns.equals("gtceu");
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.isCreateMachine()) return false;
        if (com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.isThermalMachine(node)) return false;
        if (node.getRecipeCategoryId() != null && handlesCategory(node.getRecipeCategoryId())) {
            return true;
        }
        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("gtceu")) {
                return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                String ns = ws.getNamespace().toLowerCase(Locale.ROOT);
                if (ns.equals("gtceu")) {
                    return true;
                }
            }
        }
        return node.getEnergyType() == EnergyType.ELECTRIC_EU || node.getEnergyType() == EnergyType.HEAT_OR_SELF;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        GTCEuAddonCrawler.discoverAddons(collector, recipeOutputStacks);
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        // Enriched through GTCEu multiblock structures in CategoryCapabilityMatrix
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.NONE) return false;
        if (isBoilerRecipe(node)) {
            return node.isMultiblock();
        }
        if (node.isTurbine()) {
            return node.isMultiblock();
        }
        return node.isMultiblock() || node.hasMultiblockOption() || node.canUseCoils() || node.isFusion() || node.getRequiredReflectorTier() > 0;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        if (node == null) return List.of();

        if (isBoilerRecipe(node)) {
            if (node.isMultiblock()) {
                List<AddonCategory> cats = new ArrayList<>();
                cats.add(AddonCategory.MAINTENANCE);
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of();
        }

        boolean isTurbine = node.isTurbine();
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));
        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || isFusion;
        boolean isCoil = node.canUseCoils();

        if (isTurbine) {
            if (node.isMultiblock()) {
                List<AddonCategory> cats = new ArrayList<>();
                cats.add(AddonCategory.ROTOR);
                cats.add(AddonCategory.MAINTENANCE);
                if (com.gtceu.calcboard.api.GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                    cats.add(AddonCategory.MULTIBLOCK_TRAIT);
                }
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of();
        }

        if (isFusion) {
            List<AddonCategory> cats = new ArrayList<>();
            cats.add(AddonCategory.REFLECTOR);
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        if (node.getRecipeCategoryId() != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap != CategoryCapability.DEFAULT) {
                return cap.getActiveCategoriesForNode(node);
            }
        }

        if (isMb) {
            List<AddonCategory> cats = new ArrayList<>();
            if (node.canUseCoils()) {
                cats.add(AddonCategory.COIL);
            }
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            if (node.hasThreading()) {
                cats.add(AddonCategory.THREADING);
            }
            cats.add(AddonCategory.MULTIBLOCK_TRAIT);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        List<AddonCategory> cats = new ArrayList<>();
        if (node.hasThreading()) {
            cats.add(AddonCategory.THREADING);
        }
        cats.add(AddonCategory.CUSTOM);
        return cats;
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;

        if (isBoilerRecipe(node)) {
            if (!node.isMultiblock()) return false;
            return addon.getCategory() == AddonCategory.MAINTENANCE;
        }

        if (node.isTurbine()) {
            if (!node.isMultiblock()) return false;
            if (addon.getCategory() == AddonCategory.ROTOR || addon.getCategory() == AddonCategory.MAINTENANCE) {
                return true;
            }
            if (addon.getCategory() == AddonCategory.MULTIBLOCK_TRAIT && com.gtceu.calcboard.compat.start.StarTTurbineHelper.isStarTTrait(addon)) {
                return com.gtceu.calcboard.compat.start.StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
            }
            return false;
        }

        List<AddonCategory> applicable = getApplicableAddonCategories(node);
        if (!applicable.contains(addon.getCategory())) {
            return false;
        }

        boolean isGen = node.isGenerator();
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));

        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return node.isTurbine() && node.isMultiblock();
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            return isFusion;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            return !isGen && node.canUseCoils() && node.isMultiblock();
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL || addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            return node.isMultiblock();
        }
        if (addon.getCategory().equals(AddonCategory.THREADING)) {
            return node.hasThreading();
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            if (node.isTurbine()) {
                return com.gtceu.calcboard.compat.start.StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
            }
            return !isGen && node.isMultiblock();
        }

        return true;
    }

    @Override
    public boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        return true;
    }

    @Override
    public void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int power = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            node.setRotorEfficiency(eff);
            node.setRotorPower(power);
            node.setRotorName(addon.getName());
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
            MachineAddon tailored = addon.forMachine(node);
            node.getAddons().add(tailored);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.REFLECTOR);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.MAINTENANCE);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getId().equals("gtceu:spt_lubricant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && in.getId().getPath().contains("tungsten_disulfide"));
            node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:tungsten_disulfide"), "Tungsten Disulfide", 277.77777777777777, 1.0));
        } else if (addon.getId().equals("gtceu:spt_coolant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("helium_3") || in.getId().getPath().contains("superstate")));
            node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:superstate_helium_3"), "Superstate Helium 3", 694.4444444444445, 1.0));
        } else if (addon.getId().equals("gtceu:npt_lubricant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && in.getId().getPath().contains("tungsten_disulfide"));
            node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:tungsten_disulfide"), "Tungsten Disulfide", 694.4444444444445, 1.0));
        } else if (addon.getId().equals("gtceu:npt_coolant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("bec_og") || in.getId().getPath().contains("bose_einstein") || in.getId().getPath().contains("oganesson")));
            node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bec_og"), "Oganesson Stabilized BEC", 222.22222222222223, 1.0));
        }
        node.getAddons().add(addon);
    }

    @Override
    public void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            node.setRotorName("Standard (100%)");
        } else if (addon.getId().equals("gtceu:spt_lubricant_boosting") || addon.getId().equals("gtceu:npt_lubricant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && in.getId().getPath().contains("tungsten_disulfide"));
        } else if (addon.getId().equals("gtceu:spt_coolant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("helium_3") || in.getId().getPath().contains("superstate")));
        } else if (addon.getId().equals("gtceu:npt_coolant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("bec_og") || in.getId().getPath().contains("bose_einstein") || in.getId().getPath().contains("oganesson")));
        }
    }

    @Override
    public void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
        if (addon.getId().equals("gtceu:spt_lubricant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +25% EU/t §7(1,000 B/h Tungsten Disulfide)"));
            return;
        }
        if (addon.getId().equals("gtceu:spt_coolant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +75% EU/t §7(2,500 B/h Superstate Helium 3)"));
            return;
        }
        if (addon.getId().equals("gtceu:npt_lubricant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +50% EU/t §7(2,500 B/h Tungsten Disulfide)"));
            return;
        }
        if (addon.getId().equals("gtceu:npt_coolant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +150% EU/t §7(800 B/h Oganesson BEC)"));
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int pwr = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            tooltip.add(Component.literal("§a⚙ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.efficiency", eff + "%")));
            tooltip.add(Component.literal("§6⚡ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.power", pwr + "%")));
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            tooltip.add(Component.literal("§6♨ ").append(Component.translatable("gui.gtcalcboard.addon.coil.temp", addon.getCoilTemperature())));
            MachineAddon tailored = addon.forMachine(node);
            if (tailored.getParallelMultiplier() > 1) {
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§b⚡ " + Component.translatable("gui.gtcalcboard.addon.parallel", tailored.getParallelMultiplier()).getString())));
            }
            if (tailored.getDurationMultiplier() != 1.0) {
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§a⏳ " + Component.translatable("gui.gtcalcboard.addon.speed_boost", String.format(Locale.ROOT, "%.2fx", 1.0 / tailored.getDurationMultiplier())).getString())));
            }
            if (tailored.getEutMultiplier() != 1.0) {
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§e⚡ " + Component.translatable("gui.gtcalcboard.addon.eut_multiplier", String.format(Locale.ROOT, "%.2fx", tailored.getEutMultiplier())).getString())));
            }
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            tooltip.add(Component.literal("§b🪞 ").append(Component.translatable("gui.gtcalcboard.addon.reflector.tier", addon.getReflectorTier())));
            return;
        }
        IModAdapter.super.buildAddonTooltip(node, addon, isActiveAddon, tooltip);
    }

    @Override
    public List<MachineAddon> getResetAddonCards(RecipeNode node) {
        List<MachineAddon> list = new ArrayList<>();
        if (node == null) return list;

        if (node.isTurbine()) {
            GTRotorAddon stdRotor = new GTRotorAddon("gtceu:rotor_standard",
                    Component.translatable("gui.gtcalcboard.rotor.standard").getString(),
                    Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", "100").getString(),
                    null, 100, 100, 1600.0);
            stdRotor.setDiscoverySource("Standard Default Rotor");
            list.add(stdRotor);
        }

        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));

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
        if (node == null) return true;
        boolean valid = true;

        // 1. Reflector requirement check (RFC-001 & RFC-002)
        int req = node.getProperties().get(NodeProperties.REQUIRED_REFLECTOR_TIER);
        if (req > 0) {
            int inst = node.getInstalledReflectorTier();
            if (inst < req) {
                if (warnings != null) {
                    warnings.add(Component.translatable("gui.gtcalcboard.reflector_required_warning", req, inst > 0 ? ("T" + inst) : Component.translatable("gui.gtcalcboard.none")));
                }
                valid = false;
            }
        }

        // 2. Turbine 100% Flow Fulfillment Check
        if (graph != null && GTTurbineHelper.isTurbine(node)) {
            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(node, inIdx);
                if (stats != null && stats.isConnected() && stats.isInputDeficit()) {
                    if (warnings != null) {
                        IngredientStack inStack = node.getInputs().get(inIdx);
                        String matName = inStack != null ? inStack.getDisplayName() : "Fluid";
                        warnings.add(Component.translatable("gui.gtcalcboard.turbine_flow_deficit_warning", matName, (int) Math.round(stats.getPercent())));
                    }
                    valid = false;
                }
            }
        }

        return valid;
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
        if (node.getEnergyType() == EnergyType.NONE) {
            double durationTicks = Math.max(1.0, node.getBaseDurationTicks() * node.getCombinedDurationMultiplier());
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            double boilerSpeed = getBoilerSpeedMultiplier(node);
            double durationTicks = Math.max(1.0, node.getBaseDurationTicks() / boilerSpeed);
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            double durationTicks = Math.max(1.0, node.getBaseDurationTicks() * node.getSteamMode().getDurationMultiplier() * node.getCombinedDurationMultiplier());
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        OverclockMode.OverclockResult baseRes;
        if (isGenerator) {
            baseRes = new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        } else {
            baseRes = node.getOverclockMode().calculate(node.getBaseDurationTicks(), node.getBaseEUt(), node.getTierDelta());
        }

        double finalDuration;
        double finalEut;

        if (isGenerator) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
                double rMult = (node.getRotorEfficiency() > 0 ? node.getRotorEfficiency() : 100) / 100.0;
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() == MachineAddon.Category.ROTOR) {
                        rMult = a.getDurationMultiplier();
                        break;
                    }
                }
                double rotorEffMult = rMult + (holderBonus / 100.0);

                double otherMult = 1.0;
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() != MachineAddon.Category.ROTOR) {
                        otherMult *= a.getDurationMultiplier();
                    }
                }

                finalDuration = Math.max(1.0, baseRes.durationTicks() * rotorEffMult * otherMult);
                finalEut = baseRes.eut();
            } else {
                finalDuration = Math.max(1.0, baseRes.durationTicks() * node.getCombinedDurationMultiplier());
                finalEut = Math.max(1.0, baseRes.eut() * node.getCombinedEutMultiplier());
            }
        } else {
            finalDuration = Math.max(1.0, baseRes.durationTicks() * node.getCombinedDurationMultiplier());
            finalEut = Math.max(1.0, baseRes.eut() * node.getCombinedEutMultiplier());
        }

        if (node.hasThreading()) {
            finalDuration = Math.max(1.0, finalDuration * node.getThreadingConfig().getFinalDurationMultiplier());
            finalEut = Math.max(1.0, finalEut * node.getThreadingConfig().getFinalPowerMultiplier());
        }

        return new OverclockMode.OverclockResult(finalDuration, finalEut, baseRes.batchesPerTick(), baseRes.overclocks());
    }

    public static double getBoilerSpeedMultiplier(RecipeNode node) {
        if (node == null) return 1.0;
        GTBoilerTier bt = GTBoilerTier.getBoilerTier(node);
        boolean isLiquid = node.isLiquidBoilerRecipe();
        return bt.getSpeedMultiplier(isLiquid);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return GTCEuGuiHandler.buildEnergyTooltip(node);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        GTCEuGuiHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        return GTCEuRecipeHandler.adaptRecipeDetails(emiRecipeObj, backing, details);
    }

    @Override
    public double computeSingleMachinePower(RecipeNode node) {
        if (!node.isOperational()) return 0.0;
        if (node.isGenerator()) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                double boost = node.getCombinedEutMultiplier();
                if (node.getParallel() == 1 && GTTurbineHelper.hasRotorAddon(node)) {
                    double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                    if (cap < Double.MAX_VALUE) {
                        return cap * boost;
                    }
                }
                double rawGen = computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
                double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                return Math.min(rawGen, cap) * boost;
            } else if (isGTGenerator(node) && node.getParallel() == 1) {
                double recipeEUt = Math.abs(node.getBaseEUt());
                if (recipeEUt < node.getTargetTier().getVoltage()) {
                    double rawGen = computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
                    double cap = (double) node.getTargetTier().getVoltage();
                    return Math.min(rawGen, cap);
                }
            }
            return computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
        }
        if (node.hasPowerConstantAddon()) {
            return computeOverclock(node, node.getTargetTier(), false).eut() * node.getParallel();
        }
        return computeOverclock(node, node.getTargetTier(), false).eut() * computeEffectiveParallel(node);
    }

    @Override
    public int computeEffectiveParallel(RecipeNode node) {
        int par;
        if (node.isGenerator()) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                par = GTTurbineHelper.getEffectiveTurbineParallel(node) * node.getCombinedParallelMultiplier();
            } else if (isGTGenerator(node)) {
                par = getEffectiveSingleblockParallel(node) * node.getCombinedParallelMultiplier();
            } else {
                par = Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
            }
        } else {
            par = Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
        }
        if (node.hasThreading()) {
            par *= node.getThreadingConfig().getEffectiveParallels();
        }
        return par;
    }

    @Override
    public int getDefaultParallel(RecipeNode node) {
        if (node == null) return 1;
        return MultiblockDetector.getDefaultParallel(node);
    }

    @Override
    public void autoTuneParallel(RecipeNode node) {
        if (node.isGenerator() && GTTurbineHelper.isLargeTurbine(node)) {
            GTTurbineHelper.autoCalculateTurbineParallel(node);
        } else if (node.getParallel() <= 1) {
            int defPar = getDefaultParallel(node);
            if (defPar > 1) {
                node.setParallel(defPar);
            }
        }
    }

    private boolean isGTGenerator(RecipeNode node) {
        if (!node.isGenerator()) return false;
        if (node.getEnergyType() != EnergyType.ELECTRIC_EU) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getNamespace().equals("gtceu")) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && ws.getNamespace().equals("gtceu")) return true;
        }
        if (node.getMachineIcon() != null && node.getMachineIcon().getNamespace().equals("gtceu")) return true;
        if (node.getName() != null && (node.getName().contains("Turbine") || node.getName().contains("Generator"))) return true;
        return false;
    }

    private int getEffectiveSingleblockParallel(RecipeNode node) {
        if (!isGTGenerator(node) || GTTurbineHelper.isLargeTurbine(node)) return Math.max(1, node.getParallel());
        if (node.getParallel() > 1) return node.getParallel();
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt > 0 && node.getTargetTier() != null && recipeEUt < node.getTargetTier().getVoltage()) {
            return (int) Math.max(1, Math.floor((double) node.getTargetTier().getVoltage() / recipeEUt));
        }
        return Math.max(1, node.getParallel());
    }

    @Override
    public boolean supportsSteamMode(RecipeNode node) {
        if (node.getRecipeCategoryId() != null && CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId()).supportsSteamMode()) {
            return true;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && ws.getNamespace().equals("gtceu")) {
                try {
                    Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                    Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
                    java.lang.reflect.Method mGet = machinesRegistry.getClass().getMethod("get", ResourceLocation.class);
                    Object def = mGet.invoke(machinesRegistry, ws);
                    if (def != null) {
                        java.lang.reflect.Method mTier = def.getClass().getMethod("getTier");
                        Object tObj = mTier.invoke(def);
                        int tier = -1;
                        if (tObj instanceof Number num) tier = num.intValue();
                        else if (tObj instanceof Enum<?> e) tier = e.ordinal();
                        if (tier == 0 && !MultiblockDetector.isMultiblock(ws)) {
                            return true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    @Override
    public void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        if (newMode != null && newMode.isSteam()) {
            // In GTCEu, 1 EU = 2 mB Steam.
            double durSec = (node.getBaseDurationTicks() * newMode.getDurationMultiplier()) / 20.0;
            double steamAmountPerBatch = (node.getBaseEUt() * 2.0 * 20.0) * durSec;

            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            node.getInputs().add(IngredientStack.fluid(steamId, "Steam", steamAmountPerBatch));

            // Sync workstation icon from pre-baked capability matrix
            if (node.getRecipeCategoryId() != null) {
                CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
                if (newMode == SteamMode.LOW_PRESSURE && cap.lowPressureWorkstation() != null) {
                    node.setMachineIcon(cap.lowPressureWorkstation());
                } else if (newMode == SteamMode.HIGH_PRESSURE && cap.highPressureWorkstation() != null) {
                    node.setMachineIcon(cap.highPressureWorkstation());
                }
            }
        } else if (oldMode != null && oldMode.isSteam()) {
            // Remove injected steam stack
            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            // Restore default workstation icon
            if (node.getRecipeCategoryId() != null) {
                ResourceLocation defWs = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId()).defaultWorkstation();
                if (defWs != null) {
                    node.setMachineIcon(defWs);
                }
            }
        }
    }

    @Override
    public boolean isBoilerRecipe(RecipeNode node) {
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("boiler")) {
            return true;
        }
        if (node.getName() != null && node.getName().toLowerCase(java.util.Locale.ROOT).contains("boiler")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isLiquidBoilerRecipe(RecipeNode node) {
        for (IngredientStack in : node.getInputs()) {
            if (in != null && in.getId() != null) {
                if (in.isFluid() && !in.getId().getPath().contains("water")) {
                    return true;
                }
                String p = in.getId().getPath().toLowerCase(java.util.Locale.ROOT);
                if (p.contains("lava") || p.contains("creosote") || p.contains("diesel") || p.contains("ethanol") || p.contains("biomass")
                        || (p.contains("oil") && !p.contains("boiler")) || (p.contains("fuel") && !p.contains("solid"))) {
                    return true;
                }
            }
        }
        if (node.getName() != null) {
            String n = node.getName().toLowerCase(java.util.Locale.ROOT);
            if (n.contains("lava") || n.contains("liquid") || n.contains("creosote") || n.contains("diesel") || n.contains("ethanol") || n.contains("biomass")
                    || n.contains("oil boiler") || n.contains("(oil)") || n.contains("liquid fuel")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks, net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
        GTCEuGuiHandler.renderDialogHeader(graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    @Override
    public boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW, double mouseX, double mouseY, int button, net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
        return GTCEuGuiHandler.handleDialogHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent);
    }

    public static void syncTurbineMachineIcon(RecipeNode node) {
        if (node == null || !node.isTurbine()) return;
        if (node.isMultiblock()) {
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("steam_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_steam_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("gas_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_gas_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("plasma_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));
            }
        } else {
            GTVoltageTier tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            String prefix = tier.name().toLowerCase(java.util.Locale.ROOT);
            if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("steam_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:" + prefix + "_steam_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("gas_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:" + prefix + "_gas_turbine"));
            }
        }
    }
}
