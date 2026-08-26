package com.gtceu.calcboard.compat.createnewage;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.NodeWidget;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.create.CreateGuiHandler;
import com.gtceu.calcboard.compat.createnewage.addon.CreateMagnetAddon;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.List;

/**
 * Dedicated Mod Adapter for Create: New Age (create_new_age).
 * Encapsulates Electricity Generation, Motors, Energising processing, and Magnet addons.
 */
public class CreateNewAgeModAdapter implements IModAdapter {

    public static final String MOD_ID = "create_new_age";

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public int getPriority() {
        return 105;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID) || !FMLLoader.isProduction();
            }
        } catch (Throwable t) {
            return true; // Test environment fallback
        }
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        return categoryId.getNamespace().equals(MOD_ID);
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && node.getMachineIcon().getNamespace().equals(MOD_ID)) return true;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getNamespace().equals(MOD_ID)) return true;
        if (node.getName() != null) {
            String nameLower = node.getName().toLowerCase();
            if (nameLower.contains("carbon brush") || nameLower.contains("generator coil")) return true;
        }
        return false;
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        if (node == null) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().toString().contains("create_new_age:generator")) return true;
        if (node.getMachineIcon() != null) {
            String path = node.getMachineIcon().getPath();
            if (path.contains("generator_coil") || path.contains("carbon_brushes")) return true;
        }
        if (node.getName() != null) {
            String nameLower = node.getName().toLowerCase();
            if (nameLower.contains("carbon brush") || nameLower.contains("generator coil")) return true;
        }
        return false;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        if (supportsAddons(node)) {
            return List.of(AddonCategory.MAGNET, AddonCategory.CUSTOM);
        }
        return List.of();
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;
        if (supportsAddons(node) && addon.getCategory().equals(AddonCategory.MAGNET)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (!isAddonCompatible(node, addon)) return false;
        if (addon.getCategory().equals(AddonCategory.MAGNET)) {
            long magnetCount = node.getAddons().stream().filter(a -> a.getCategory().equals(AddonCategory.MAGNET)).count();
            return magnetCount < 12;
        }
        return true;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        CreateNewAgeAddonCrawler.discoverMagnets(collector);
    }

    @Override
    public MachineAddon tailorAddon(MachineAddon addon, RecipeNode node) {
        if (addon instanceof CreateMagnetAddon magnet) {
            return magnet.forMachine(node);
        }
        return addon;
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return CreateNewAgeRecipeHandler.adaptRecipeDetails(emiRecipe, backingRecipe, details);
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
    }

    @Override
    public void handleInstallAddon(RecipeNode node, MachineAddon addon, boolean shiftClick) {
        if (node == null || addon == null) return;
        if (addon.getCategory().equals(AddonCategory.MAGNET)) {
            int targetCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
            long totalMagnets = node.getAddons().stream().filter(a -> a.getCategory().equals(AddonCategory.MAGNET)).count();

            if (shiftClick) {
                int toAdd = (int) (12 - totalMagnets);
                if (toAdd > 0) {
                    for (int k = 0; k < toAdd; k++) {
                        node.addAddon(addon.copy());
                    }
                } else if (targetCount < 12) {
                    node.getAddons().removeIf(a -> a.getCategory().equals(AddonCategory.MAGNET) || a.getMagneticForce() > 0);
                    for (int k = 0; k < 12; k++) {
                        node.addAddon(addon.copy());
                    }
                } else {
                    node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
                }
            } else {
                if (totalMagnets < 12) {
                    node.addAddon(addon.copy());
                } else if (targetCount < 12) {
                    for (MachineAddon existing : new java.util.ArrayList<>(node.getAddons())) {
                        if ((existing.getCategory().equals(AddonCategory.MAGNET) || existing.getMagneticForce() > 0) && !existing.getId().equals(addon.getId())) {
                            node.removeSingleAddon(existing.getId());
                            node.addAddon(addon.copy());
                            break;
                        }
                    }
                } else {
                    node.removeSingleAddon(addon.getId());
                }
            }
        } else {
            node.addAddon(addon.copy());
        }
    }

    @Override
    public void handleUninstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory().equals(AddonCategory.MAGNET)) {
            node.removeSingleAddon(addon.getId());
        } else {
            node.removeAddon(addon.getId());
        }
    }

    @Override
    public String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory().equals(AddonCategory.MAGNET) || addon.getMagneticForce() > 0) {
            return String.format("🧲 %dx", addon.getMagneticForce());
        }
        return "";
    }

    @Override
    public String formatAddonSubtitle(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory().equals(AddonCategory.MAGNET) || addon.getMagneticForce() > 0) {
            return String.format("Magnetic Force: %dx", addon.getMagneticForce());
        }
        return addon.getName();
    }

    @Override
    public void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
        int force = addon.getMagneticForce();
        tooltip.add(Component.literal("§6🧲 ").append(Component.translatable("gui.gtcalcboard.addon.magnetic_force", force)));

        long totalMagnets = node.getAddons().stream().filter(a -> a.getCategory().equals(AddonCategory.MAGNET)).count();
        int targetCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();

        if (isActiveAddon) {
            tooltip.add(Component.literal(String.format("§7Ring Slot: §e%d / 12", totalMagnets)));
            tooltip.add(Component.literal("§c[Right-Click] Remove 1 Magnet"));
        } else {
            tooltip.add(Component.literal(String.format("§7Ring Slots: §e%d / 12", totalMagnets)));
            if (targetCount > 0) {
                tooltip.add(Component.literal(String.format("§aInstalled: §e%d / 12 magnets", targetCount)));
            }
            if (totalMagnets < 12) {
                tooltip.add(Component.literal("§a[Left-Click] Add 1 Magnet (+1)"));
                tooltip.add(Component.literal("§d[Shift+Left-Click] Fill All 12 Slots"));
            } else {
                tooltip.add(Component.literal("§e(Magnet Ring Full: 12/12)"));
            }
            if (targetCount > 0) {
                tooltip.add(Component.literal("§c[Right-Click] Remove 1 Magnet (-1)"));
            }
        }
    }

    @Override
    public void populateExtraBOMParts(RecipeNode node, List<com.gtceu.calcboard.api.bom.MultiblockStructurePart> parts) {
        if (node == null || parts == null) return;
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null && icon.getPath().contains("carbon_brushes")) {
            ResourceLocation coilId = ResourceLocation.tryParse("create_new_age:generator_coil");
            if (coilId != null) {
                parts.add(new com.gtceu.calcboard.api.bom.MultiblockStructurePart(coilId, "Generator Coil", 1, com.gtceu.calcboard.api.bom.PartCategory.COIL));
            }
        }
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        int rpm = node.getRpm();
        double baseDuration = node.getBaseDurationTicks();
        double basePower = node.getBaseEUt();

        if (isGenerator && node.getEnergyType() == EnergyType.ELECTRIC_FE) {
            // Create: New Age Generator Coil formula:
            // Sum of all installed magnet strengths (up to 12 positions per coil ring).
            int totalStrength = 0;
            for (MachineAddon addon : node.getAddons()) {
                if (addon.getCategory().equals(AddonCategory.MAGNET) || addon.getMagneticForce() > 0) {
                    totalStrength += addon.getMagneticForce();
                }
            }

            // Deductively queries NewAgeConfig.getCommon().suToEnergy ratio (e.g. 0.05 in modpacks, 15/512 default)
            double suToEnergy = getSuToEnergyRatio();
            double generatedFePerTick = totalStrength * Math.abs(rpm) * suToEnergy;
            double requiredSuPerTick = (24.0 + totalStrength) * Math.abs(rpm);

            if (!node.getInputs().isEmpty() && node.getInputs().get(0).isStressUnit()) {
                node.getInputs().set(0, IngredientStack.stressUnit(requiredSuPerTick));
            }

            return new OverclockMode.OverclockResult(baseDuration, generatedFePerTick, 1.0, 0);
        }

        // Standard kinetic consumers / motors
        double speedFactor = Math.max(0.01, rpm / 32.0);
        double rawDuration = (baseDuration * node.getCombinedDurationMultiplier()) / speedFactor;
        double durationTicks = Math.max(1.0, rawDuration);
        double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;

        double effectivePower;
        if (isGenerator) {
            effectivePower = basePower * node.getCombinedEutMultiplier();
        } else {
            effectivePower = basePower * speedFactor * node.getCombinedEutMultiplier();
        }

        return new OverclockMode.OverclockResult(durationTicks, effectivePower, batchesPerTick, 0);
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        return CreateNewAgeGuiHandler.formatEnergyStats(node, displayMode);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return CreateNewAgeGuiHandler.buildEnergyTooltip(node);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        CreateNewAgeGuiHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return CreateNewAgeGuiHandler.isTierOrSpeedControlHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return CreateNewAgeGuiHandler.isMachineConfigHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        return CreateNewAgeGuiHandler.handleControlClick(widget, node, mouseX, mouseY, button);
    }

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        return CreateNewAgeGuiHandler.handleControlScroll(widget, node, mouseX, mouseY, delta);
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        return CreateNewAgeRecipeHandler.createKineticGeneratorNode(stack);
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        return CreateNewAgeRecipeHandler.createKineticGeneratorNode(itemId, displayName);
    }

    public static Double testSuToEnergyOverride = null;

    public static double getSuToEnergyRatio() {
        if (testSuToEnergyOverride != null) {
            return testSuToEnergyOverride;
        }
        try {
            Class<?> configClass = Class.forName("org.antarcticgardens.newage.config.NewAgeConfig");
            java.lang.reflect.Method getCommon = configClass.getMethod("getCommon");
            Object common = getCommon.invoke(null);
            if (common != null) {
                var field = common.getClass().getField("suToEnergy");
                Object configValue = field.get(common);
                if (configValue instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> cv) {
                    Object val = cv.get();
                    if (val instanceof Number n) {
                        return n.doubleValue();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 15.0 / 512.0;
    }

    public static List<RecipeSearchEngine.SearchableRecipe> getVirtualSearchRecipes() {
        return CreateNewAgeRecipeHandler.getVirtualSearchRecipes();
    }

    @Override
    public void renderDialogHeader(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, RecipeNode node, int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks, net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
        CreateNewAgeGuiHandler.renderDialogHeader(graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    @Override
    public boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW, double mouseX, double mouseY, int button, net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
        return CreateNewAgeGuiHandler.handleDialogHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent);
    }
}
