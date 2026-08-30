package com.gtceu.calcboard.compat.createnewage;

import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.AddonFactoryRegistry;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;

import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.createnewage.addon.CreateMagnetAddon;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Mod Adapter for Create: New Age (create_new_age).
 * Encapsulates Electricity Generation, Motors, Energising processing, and Magnet addons.
 */
public class CreateNewAgeModAdapter implements IModAdapter {

    static {
        com.gtceu.calcboard.api.catalog.AddonFactoryRegistry.register(com.gtceu.calcboard.api.catalog.AddonCategory.MAGNET, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.createnewage.addon.CreateMagnetAddon(id, name, desc, icon, 0));
    }

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
        return com.gtceu.calcboard.api.util.ModCompatHelper.isModLoaded(MOD_ID);
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
        return node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getNamespace().equals(MOD_ID);
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        if (node == null) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().toString().contains("create_new_age:generator")) return true;
        if (node.getMachineIcon() != null) {
            String path = node.getMachineIcon().getPath();
            return path.contains("generator_coil") || path.contains("carbon_brushes");
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
        if (node == null) return "";
        if (node.getEnergyType() == EnergyType.ELECTRIC_FE) {
            double effectivePower = node.getSingleMachineEUt() * node.getEfficiency();
            String unit = "FE/t";
            if (node.isGenerator()) {
                return String.format(Locale.ROOT, "+%,.2f %s", effectivePower, unit);
            } else {
                return String.format(Locale.ROOT, "-%,.2f %s", effectivePower, unit);
            }
        } else {
            double basePower = node.getBaseEUt();
            double speedFactor = Math.max(0.01, node.getRpm() / 32.0);
            double effectiveSu = (node.isGenerator() ? basePower : (basePower * speedFactor)) * node.getEfficiency();
            if (node.isGenerator()) {
                return String.format(Locale.ROOT, "+%,.0f SU", effectiveSu);
            } else {
                return String.format(Locale.ROOT, "-%,.0f SU", effectiveSu);
            }
        }
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        if (node == null) return tooltipLines;
        if (node.getEnergyType() == EnergyType.ELECTRIC_FE) {
            double singlePower = node.getSingleMachineEUt();
            double totPower = node.getTotalEUt();
            if (node.isGenerator()) {
                tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.single_gen").getString()));
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Generation: §a+%,.2f FE/t §7(§a+%,.2f EU/t eq§7)", singlePower, singlePower / 4.0)));
            } else {
                tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.single_power").getString()));
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Consumption: §c%,.2f FE/t §7(§c%,.2f EU/t eq§7)", singlePower, singlePower / 4.0)));
            }
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        } else {
            double totSU = node.getEffectiveTotalEUt();
            if (node.isGenerator()) {
                tooltipLines.add(Component.literal("§6⚙ " + Component.translatable("gui.gtcalcboard.total_gen").getString()));
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Capacity: §6+%,.0f SU", totSU)));
            } else {
                tooltipLines.add(Component.literal("§e⚙ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Stress Impact: §e%,.0f SU", totSU)));
            }
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Rotation Speed: §6%d RPM", node.getRpm())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        }
        return tooltipLines;
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
                if (configValue instanceof net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> cv) {
                    Object val = cv.get();
                    if (val instanceof Number n) {
                        return n.doubleValue();
                    }
                } else if (configValue != null) {
                    try {
                        Object val = configValue.getClass().getMethod("get").invoke(configValue);
                        if (val instanceof Number n) {
                            return n.doubleValue();
                        }
                    } catch (Throwable ignored2) {}
                }
            }
        } catch (Throwable ignored) {}
        return 15.0 / 512.0;
    }

    public static List<RecipeSearchEngine.SearchableRecipe> getVirtualSearchRecipes() {
        return CreateNewAgeRecipeHandler.getVirtualSearchRecipes();
    }

    @Override
    public void registerSyntheticEmiRecipes(Object emiRegistry, Object emiCategory, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
        CreateNewAgeRecipeHandler.registerSyntheticEmiRecipes(emiRegistry, emiCategory, activeRecipeItems);
    }
}





