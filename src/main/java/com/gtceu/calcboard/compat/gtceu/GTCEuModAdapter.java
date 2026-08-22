package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility adapter facade for GregTech CEu Modern (GTCEu).
 * Delegates to GTCEuAddonCrawler, GTCEuGuiHandler, and GTCEuRecipeHandler.
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
            if (ModList.get() != null) {
                return ModList.get().isLoaded("gtceu");
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
        if (node.getEnergyType() == EnergyType.KINETIC_SU || node.getEnergyType() == EnergyType.HEAT_OR_SELF) return false;
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
        return false;
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
        return node.isTurbine() || node.isMultiblock() || node.hasMultiblockOption() || node.canUseCoils();
    }

    @Override
    public List<MachineAddon.Category> getApplicableAddonCategories(RecipeNode node) {
        if (node == null) return List.of();

        if (node.getRecipeCategoryId() != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap != CategoryCapability.DEFAULT) {
                return cap.getActiveCategoriesForNode(node);
            }
        }

        List<MachineAddon.Category> cats = new ArrayList<>();
        boolean isTurbine = node.isTurbine();
        boolean isMb = node.isMultiblock() || node.hasMultiblockOption();
        boolean isCoil = node.canUseCoils();

        if (isTurbine) {
            cats.add(MachineAddon.Category.ROTOR);
            if (isMb) {
                cats.add(MachineAddon.Category.MAINTENANCE);
            }
            cats.add(MachineAddon.Category.CUSTOM);
            return cats;
        }

        if (isMb) {
            if (isCoil) {
                cats.add(MachineAddon.Category.COIL);
            }
            cats.add(MachineAddon.Category.PARALLEL);
            cats.add(MachineAddon.Category.MAINTENANCE);
            cats.add(MachineAddon.Category.MULTIBLOCK_TRAIT);
            cats.add(MachineAddon.Category.CUSTOM);
            return cats;
        }

        return List.of();
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory() == MachineAddon.Category.CUSTOM) return true;

        List<MachineAddon.Category> applicable = getApplicableAddonCategories(node);
        if (!applicable.contains(addon.getCategory())) {
            return false;
        }

        boolean isGen = node.isGenerator();
        boolean isMb = node.isMultiblock() || node.hasMultiblockOption();
        boolean isTurbine = node.isTurbine();

        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return isTurbine;
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
            if (node.isLargeTurbine()) {
                int holderBonus = node.getTurbineHolderEfficiencyBonus();
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
