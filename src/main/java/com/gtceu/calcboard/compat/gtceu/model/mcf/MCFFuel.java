package com.gtceu.calcboard.compat.gtceu.model.mcf;

import net.minecraft.resources.ResourceLocation;

/**
 * Standard fuels supported in GTCEu combustion engines and Star Technology modular complex.
 */
public enum MCFFuel {
    CETANE_DIESEL("Cetane Diesel", ResourceLocation.tryParse("gtceu:cetane_boosted_diesel"), 720.0),
    DIESEL("Diesel", ResourceLocation.tryParse("gtceu:diesel"), 480.0),
    HIGH_OCTANE_GASOLINE("High Octane Gas", ResourceLocation.tryParse("gtceu:high_octane_gasoline"), 3200.0),
    GASOLINE("Gasoline", ResourceLocation.tryParse("gtceu:gasoline"), 1600.0),
    BIO_DIESEL("Bio Diesel", ResourceLocation.tryParse("gtceu:bio_diesel"), 256.0),
    ROCKET_FUEL("Rocket Fuel", ResourceLocation.tryParse("gtceu:rocket_fuel"), 5120.0),
    RP_1("RP-1", ResourceLocation.tryParse("gtceu:rp_1"), 7680.0),
    MONOMETHYLHYDRAZINE("Monomethylhydrazine", ResourceLocation.tryParse("gtceu:monomethylhydrazine"), 12800.0),
    SORBITOL_HYPERGOLIC_FUEL("Sorbitol Hypergolic Fuel", ResourceLocation.tryParse("gtceu:sorbitol_hypergolic_fuel"), 20480.0);

    private final String displayName;
    private final ResourceLocation fluidId;
    private final double energyPerMb;

    MCFFuel(String displayName, ResourceLocation fluidId, double energyPerMb) {
        this.displayName = displayName;
        this.fluidId = fluidId;
        this.energyPerMb = energyPerMb;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ResourceLocation getFluidId() {
        return fluidId;
    }

    public double getEnergyPerMb() {
        return energyPerMb;
    }

    public double getRecipeEUt() {
        return energyPerMb;
    }

    public MCFFuel next() {
        MCFFuel[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }

    public static MCFFuel fromFluidId(ResourceLocation id) {
        if (id == null) return null;
        for (MCFFuel f : values()) {
            if (id.equals(f.fluidId)) {
                return f;
            }
        }
        return null;
    }

    public static MCFFuel fromIdOrDefault(String id) {
        return fromIdOrDefault(id, null);
    }

    public static MCFFuel fromIdOrDefault(String id, MCFModuleType moduleType) {
        MCFFuel defaultFuel = (moduleType != null) ? moduleType.getDefaultFuel() : CETANE_DIESEL;
        if (id == null || id.isBlank()) return defaultFuel;
        for (MCFFuel f : values()) {
            if (f.fluidId.toString().equalsIgnoreCase(id) || f.name().equalsIgnoreCase(id) || f.displayName.equalsIgnoreCase(id)) {
                return f;
            }
        }
        return defaultFuel;
    }
}
