package com.gtceu.calcboard.compat.tfg;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.extension.IBoosterProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Implementation of {@link IBoosterProvider} for TFG Large Boilers.
 */
public class TFGBoilerBoosterProvider implements IBoosterProvider {

    private static final TFGBoilerBoosterProvider INSTANCE = new TFGBoilerBoosterProvider();

    private TFGBoilerBoosterProvider() {}

    public static TFGBoilerBoosterProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean supportsBoosterControl(RecipeNode node) {
        return TFGBoilerPhysics.isTFGLargeBoiler(node);
    }

    @Override
    public Component getBoosterDisplayComponent(RecipeNode node) {
        if (!supportsBoosterControl(node)) return null;
        TFGBoilerPhysics.BoosterFluid booster = TFGBoilerPhysics.getActiveBooster(node);
        if (booster.index() <= 0) {
            return Component.translatable("tfg.multiblock.large_boiler.booster_none");
        }
        return Component.translatable("tfg.multiblock.large_boiler.booster_active",
                Component.translatable(booster.translationKey()),
                "+" + booster.pressureBonus() + "PU");
    }

    @Override
    public void cycleBooster(RecipeNode node, int direction) {
        TFGBoilerPhysics.cycleBooster(node, direction);
    }

    @Override
    public void syncBoosterInputs(RecipeNode node) {
        TFGBoilerPhysics.syncDynamicPorts(node);
    }

    @Override
    public void buildBoosterTooltip(RecipeNode node, List<Component> tooltip) {
        TFGBoilerPhysics.buildBoosterTooltip(node, tooltip);
    }

    @Override
    public int getBoosterBackgroundColor(RecipeNode node, boolean isHovered) {
        if (TFGBoilerPhysics.getActiveBooster(node).index() > 0) {
            return isHovered ? 0xFF2A281E : 0xFF1F1D16;
        }
        return isHovered ? 0xFF2A303C : 0xFF1E222D;
    }

    @Override
    public int getBoosterBorderColor(RecipeNode node, boolean isHovered) {
        if (TFGBoilerPhysics.getActiveBooster(node).index() > 0) {
            return isHovered ? 0xFF58D3FF : 0xFF38BDF8;
        }
        return isHovered ? 0xFF6B7B96 : 0xFF353C4D;
    }

    @Override
    public int getBoosterTextColor(RecipeNode node, boolean isHovered) {
        if (TFGBoilerPhysics.getActiveBooster(node).index() > 0) {
            return 0xFFFFD700;
        }
        return 0xFFCCCCCC;
    }
}
