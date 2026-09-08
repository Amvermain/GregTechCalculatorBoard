package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.extension.IBoosterProvider;
import com.gtceu.calcboard.compat.extension.ICapabilityMatrixProvider;
import com.gtceu.calcboard.compat.extension.ICompoundRecipeProvider;
import com.gtceu.calcboard.compat.extension.IEnergySimulationProvider;
import com.gtceu.calcboard.compat.extension.IHardwareAddonProvider;
import com.gtceu.calcboard.compat.extension.IModExtension;
import com.gtceu.calcboard.compat.extension.IMultiblockBOMProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface (SPI) for external mod integration.
 * Employs the Extension Object Pattern to decouple domain capabilities (hardware addons,
 * multiblock BOMs, energy simulation, compound recipes, boosters, capability matrices)
 * while providing sub-interface composite inheritance for seamless backward compatibility.
 */
public interface IModAdapter extends
        IHardwareAddonProvider,
        IMultiblockBOMProvider,
        IEnergySimulationProvider,
        ICompoundRecipeProvider,
        IBoosterProvider,
        ICapabilityMatrixProvider {

    String getModId();

    default int getPriority() {
        return 100;
    }

    default boolean isGenericFallback() {
        return false;
    }

    boolean isLoaded();

    boolean handlesCategory(ResourceLocation categoryId);

    boolean handlesNode(RecipeNode node);

    /**
     * Set of extension interfaces explicitly supported by this adapter.
     * Full-stack adapters (like GTCEu) support all 6 extensions by default.
     */
    default Set<Class<? extends IModExtension>> getSupportedExtensions() {
        return Set.of(
                IHardwareAddonProvider.class,
                IMultiblockBOMProvider.class,
                IEnergySimulationProvider.class,
                ICompoundRecipeProvider.class,
                IBoosterProvider.class,
                ICapabilityMatrixProvider.class
        );
    }

    /**
     * Extension Object query method.
     * Returns the extension capability if this adapter explicitly supports it.
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<T> getExtension(Class<T> extensionClass) {
        if (extensionClass != null && extensionClass.isInstance(this)) {
            Set<Class<? extends IModExtension>> supported = getSupportedExtensions();
            if (supported != null && supported.contains(extensionClass)) {
                return Optional.of((T) this);
            }
        }
        return Optional.empty();
    }

    default <T> boolean hasExtension(Class<T> extensionClass) {
        return getExtension(extensionClass).isPresent();
    }

    default ResourceLocation getWorkstationForTier(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return null;
        return node.getWorkstationForTierFromList(tier);
    }

    default GTVoltageTier getMinimumWorkstationTier(RecipeNode node) {
        return null;
    }

    default void onMachineIconChanged(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
    }

    default boolean validateNode(RecipeNode node, List<Component> warnings) {
        return true;
    }

    default boolean validateNode(RecipeNode node, FlowGraph graph, List<Component> warnings) {
        return validateNode(node, warnings);
    }

    default void collectNativeCatalogRecipes(List<SearchableRecipe> collector) {
    }
}
