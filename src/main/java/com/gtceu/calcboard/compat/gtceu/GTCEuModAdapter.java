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
        return categoryId.getNamespace().equals("gtceu");
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getEnergyType() != EnergyType.ELECTRIC_EU) return false;
        if (node.isCreateMachine()) return false;
        if (com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.isThermalMachine(node)) return false;
        if (node.getRecipeCategoryId() != null && handlesCategory(node.getRecipeCategoryId())) {
            return true;
        }
        if (node.getMachineIcon() != null && node.getMachineIcon().getNamespace().equals("gtceu")) {
            return true;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && ws.getNamespace().equals("gtceu")) {
                return true;
            }
        }
        return true;
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
        if (node == null) return false;
        return node.isTurbine() || node.isMultiblock() || node.hasMultiblockOption() || node.canUseCoils() || node.isFusion() || node.getRequiredReflectorTier() > 0;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        if (node == null) return List.of();

        boolean isTurbine = node.isTurbine();
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));
        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || isFusion;
        boolean isCoil = node.canUseCoils();

        if (isTurbine) {
            List<AddonCategory> cats = new ArrayList<>();
            cats.add(AddonCategory.ROTOR);
            if (isMb) {
                cats.add(AddonCategory.MAINTENANCE);
            }
            cats.add(AddonCategory.CUSTOM);
            return cats;
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
            if (isCoil) {
                cats.add(AddonCategory.COIL);
            }
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            cats.add(AddonCategory.MULTIBLOCK_TRAIT);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        return List.of();
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;

        List<AddonCategory> applicable = getApplicableAddonCategories(node);
        if (!applicable.contains(addon.getCategory())) {
            return false;
        }

        boolean isGen = node.isGenerator();
        boolean isTurbine = node.isTurbine();
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));
        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || isFusion;

        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return isTurbine;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            return isFusion;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            return !isGen && node.canUseCoils();
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT || addon.getCategory() == MachineAddon.Category.PARALLEL) {
            return !isGen && isMb;
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            return isMb;
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
        }
    }

    @Override
    public void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int pwr = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            tooltip.add(Component.literal("§a⚙ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.efficiency", eff)));
            tooltip.add(Component.literal("§6⚡ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.power", pwr)));
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

        return new OverclockMode.OverclockResult(finalDuration, finalEut, baseRes.batchesPerTick(), baseRes.overclocks());
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
}
