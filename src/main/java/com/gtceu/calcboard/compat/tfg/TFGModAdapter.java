package com.gtceu.calcboard.compat.tfg;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.extension.IBoosterProvider;
import com.gtceu.calcboard.api.spi.extension.IPortProjectionProvider;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Mod compatibility adapter for TerraFirmaGreg (TFG).
 * Extends GTCEuModAdapter with support for TFG Large Boilers, dynamic booster fluids,
 * and Super Boiler dual fuel modes.
 */
public class TFGModAdapter extends GTCEuModAdapter {

    static {
        TFGBoilerProperties.init();
    }

    @Override
    public String getModId() {
        return "tfg";
    }

    @Override
    public int getPriority() {
        return 105;
    }

    @Override
    public boolean isLoaded() {
        return ModCompatHelper.isTFGLoaded();
    }

    private static final java.util.Set<String> KINETIC_CATEGORY_PATHS = java.util.Set.of(
            "compacting", "pressing", "cutting", "milling", "crushing",
            "mixing", "sawing", "sandpaper_polishing", "deploying",
            "sequenced_assembly", "spout_filling", "draining",
            "haunting", "item_application"
    );

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        if (TFGBoilerPhysics.SUPER_BOILER_RECIPE.equals(categoryId)) return true;
        if (KINETIC_CATEGORY_PATHS.contains(categoryId.getPath().toLowerCase(Locale.ROOT))
                || ModCompatHelper.isCreateFamilyNamespace(categoryId.getNamespace())) {
            return false;
        }
        return "tfg".equalsIgnoreCase(categoryId.getNamespace());
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) return true;
        if (ModCompatHelper.isCreateMachine(node)
                || node.getEnergyTypeOverride() == com.gtceu.calcboard.api.type.EnergyType.KINETIC_SU
                || Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE))) {
            return false;
        }
        if (node.getMachineIcon() != null) {
            String iconNs = node.getMachineIcon().getNamespace().toLowerCase(Locale.ROOT);
            if (ModCompatHelper.isCreateFamilyNamespace(iconNs)) {
                return false;
            }
            if (iconNs.equals("tfg")) {
                return true;
            }
        }
        return node.getRecipeCategoryId() != null && handlesCategory(node.getRecipeCategoryId());
    }

    @Override
    public boolean isBoilerRecipe(RecipeNode node) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) return true;
        return super.isBoilerRecipe(node);
    }

    @Override
    public boolean supportsBoosterControl(RecipeNode node) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) return true;
        return super.supportsBoosterControl(node);
    }

    @Override
    public Component getBoosterDisplayComponent(RecipeNode node) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return TFGBoilerBoosterProvider.getInstance().getBoosterDisplayComponent(node);
        }
        return super.getBoosterDisplayComponent(node);
    }

    @Override
    public void cycleBooster(RecipeNode node, int direction) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            TFGBoilerBoosterProvider.getInstance().cycleBooster(node, direction);
            return;
        }
        super.cycleBooster(node, direction);
    }

    @Override
    public void syncBoosterInputs(RecipeNode node) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            TFGBoilerBoosterProvider.getInstance().syncBoosterInputs(node);
            return;
        }
        super.syncBoosterInputs(node);
    }

    @Override
    public void buildBoosterTooltip(RecipeNode node, List<Component> tooltip) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            TFGBoilerBoosterProvider.getInstance().buildBoosterTooltip(node, tooltip);
            return;
        }
        super.buildBoosterTooltip(node, tooltip);
    }

    @Override
    public <T> Optional<T> getExtension(Class<T> extensionClass) {
        if (IBoosterProvider.class.equals(extensionClass)) {
            return Optional.of(extensionClass.cast(TFGBoilerBoosterProvider.getInstance()));
        }
        if (IPortProjectionProvider.class.equals(extensionClass)) {
            return Optional.of(extensionClass.cast(TFGBoilerPortProjectionProvider.getInstance()));
        }
        return super.getExtension(extensionClass);
    }

    @Override
    public double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return TFGBoilerPhysics.computeEffectiveIngredientRate(node, stack, isInput, defaultRate);
        }
        return super.computeEffectiveIngredientRate(node, stack, isInput, defaultRate);
    }

    @Override
    public double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (TFGBoilerPhysics.isTFGLargeBoiler(node)) {
            return TFGBoilerPhysics.computeSingleMachineIngredientRate(node, stack, isInput, defaultRate);
        }
        return super.computeSingleMachineIngredientRate(node, stack, isInput, defaultRate);
    }
}
