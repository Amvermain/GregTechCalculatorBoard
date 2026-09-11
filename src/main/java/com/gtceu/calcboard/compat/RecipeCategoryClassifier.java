package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.compat.gtceu.physics.GTBoilerPhysics;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class RecipeCategoryClassifier {

    private RecipeCategoryClassifier() {}

    private static final Set<ResourceLocation> DEDICATED_GENERATOR_CATEGORIES = Set.of(
            ResourceLocation.tryParse("gtceu:steam_turbine"),
            ResourceLocation.tryParse("gtceu:gas_turbine"),
            ResourceLocation.tryParse("gtceu:plasma_generator"),
            ResourceLocation.tryParse("gtceu:combustion_generator"),
            ResourceLocation.tryParse("gtceu:semi_fluid_generator"),
            ResourceLocation.tryParse("systeams:steam_dynamo"),
            ResourceLocation.tryParse("thermal:stirling_fuel"),
            ResourceLocation.tryParse("thermal:compression_fuel"),
            ResourceLocation.tryParse("thermal:magmatic_fuel"),
            ResourceLocation.tryParse("thermal:numismatic_fuel"),
            ResourceLocation.tryParse("thermal:lapidary_fuel"),
            ResourceLocation.tryParse("thermal:disenchantment_fuel"),
            ResourceLocation.tryParse("thermal:gourmand_fuel"),
            ResourceLocation.tryParse("createdieselgenerators:diesel_engine"),
            ResourceLocation.tryParse("createdieselgenerators:large_diesel_engine"),
            ResourceLocation.tryParse("create_new_age:generator_coil"),
            ResourceLocation.tryParse("create_new_age:motor"),
            ResourceLocation.tryParse("gtcalcboard:kinetic_generation")
    );

    private static final Set<ResourceLocation> KINETIC_SEARCH_IDS = Set.of(
            ResourceLocation.tryParse("create:stress_units"),
            ResourceLocation.tryParse("create:cogwheel"),
            ResourceLocation.tryParse("create:large_cogwheel"),
            ResourceLocation.tryParse("create:shaft")
    );

    public static boolean isBoilerCategory(ResourceLocation categoryId) {
        return GTBoilerPhysics.isBoilerCategory(categoryId);
    }

    public static boolean isGeneratorCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        if (DEDICATED_GENERATOR_CATEGORIES.contains(categoryId)) return true;
        String path = categoryId.getPath();
        return path.endsWith("_generator") || path.equals("generator")
                || path.endsWith("_turbine") || path.equals("turbine")
                || path.endsWith("_dynamo") || path.equals("dynamo");
    }

    public static boolean isGeneratorOrBoilerCategory(ResourceLocation categoryId) {
        return isBoilerCategory(categoryId) || isGeneratorCategory(categoryId);
    }

    public static boolean isKineticUnitOrGear(ResourceLocation id) {
        if (id == null) return false;
        if (KINETIC_SEARCH_IDS.contains(id)) return true;
        if (!"create".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "stress_units".equals(path) || "cogwheel".equals(path) || "large_cogwheel".equals(path);
    }
}
