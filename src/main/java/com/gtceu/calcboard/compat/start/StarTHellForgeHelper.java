package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helper for Star Technology Hellforge and Fornax's Infernal Rotary Engine crucible heat and infernal parallel mechanics.
 */
public final class StarTHellForgeHelper {

    public enum HeatingFluid {
        FLAMEWAKE_SOLVENT(
                900,
                ResourceLocation.tryParse("start_core:flamewake_solvent"),
                ResourceLocation.tryParse("gtceu:flamewake_solvent"),
                "Flamewake Solvent",
                "🔥",
                50,
                0xFFFF9933
        ),
        CINDERBREW_SOLVENT(
                1350,
                ResourceLocation.tryParse("start_core:cinderbrew_solvent"),
                ResourceLocation.tryParse("gtceu:cinderbrew_solvent"),
                "Cinderbrew Solvent",
                "🔥+",
                10,
                0xFFDD7208
        ),
        EMBERHEART_NECTAR(
                1800,
                ResourceLocation.tryParse("start_core:emberheart_nectar"),
                ResourceLocation.tryParse("gtceu:emberheart_nectar"),
                "Emberheart Nectar",
                "🔥🔥",
                75,
                0xFFFF3C28
        ),
        COREFIRE_NECTAR(
                2250,
                ResourceLocation.tryParse("start_core:corefire_nectar"),
                ResourceLocation.tryParse("gtceu:corefire_nectar"),
                "Corefire Nectar",
                "🔥🔥+",
                15,
                0xFFBA2312
        ),
        IGNIFEROUS_ELIXIR(
                2700,
                ResourceLocation.tryParse("start_core:igniferous_elixir"),
                ResourceLocation.tryParse("gtceu:igniferous_elixir"),
                "Igniferous Elixir",
                "🔥🔥🔥",
                100,
                0xFFFF6E40
        ),
        INFERNUM_ELIXIR(
                3150,
                ResourceLocation.tryParse("start_core:infernum_elixir"),
                ResourceLocation.tryParse("gtceu:infernum_elixir"),
                "Infernum Elixir",
                "🔥🔥🔥+",
                20,
                0xFFBE502F
        ),
        BLAZING_PHLOGISTON(
                3600,
                ResourceLocation.tryParse("start_core:blazing_phlogiston"),
                ResourceLocation.tryParse("gtceu:blazing_phlogiston"),
                "Blazing Phlogiston",
                "🔥🔥🔥🔥",
                125,
                0xFFFF4500
        ),
        HELLFIRE_ESSENCE(
                4050,
                ResourceLocation.tryParse("start_core:hellfire_essence"),
                ResourceLocation.tryParse("gtceu:hellfire_essence"),
                "Hellfire Essence",
                "🔥🔥🔥🔥+",
                25,
                0xFFCE3700
        );

        private final int maxTempMK;
        private final ResourceLocation primaryFluidId;
        private final ResourceLocation fallbackFluidId;
        private final String englishName;
        private final String formulaSymbol;
        private final int heatPerBucketMK;
        private final int color;

        HeatingFluid(int maxTempMK, ResourceLocation primaryFluidId, ResourceLocation fallbackFluidId,
                     String englishName, String formulaSymbol, int heatPerBucketMK, int color) {
            this.maxTempMK = maxTempMK;
            this.primaryFluidId = primaryFluidId;
            this.fallbackFluidId = fallbackFluidId;
            this.englishName = englishName;
            this.formulaSymbol = formulaSymbol;
            this.heatPerBucketMK = heatPerBucketMK;
            this.color = color;
        }

        public int maxTempMK() {
            return maxTempMK;
        }

        public ResourceLocation primaryFluidId() {
            return primaryFluidId;
        }

        public ResourceLocation fallbackFluidId() {
            return fallbackFluidId;
        }

        public String englishName() {
            return englishName;
        }

        public String formulaSymbol() {
            return formulaSymbol;
        }

        public int heatPerBucketMK() {
            return heatPerBucketMK;
        }

        public int color() {
            return color;
        }

        public String getDisplayName() {
            String localized = resolveFluidLocalizedName(primaryFluidId);
            if (localized != null) return localized;
            localized = resolveFluidLocalizedName(fallbackFluidId);
            if (localized != null) return localized;
            return englishName;
        }

        public String getShortChipLabel() {
            return String.format(Locale.ROOT, "%s %,dMK", formulaSymbol, maxTempMK);
        }
    }

    private static final List<HeatingFluid> ALL_FLUIDS = List.of(HeatingFluid.values());

    private static final Set<ResourceLocation> HELLFORGE_CATEGORIES = Set.of(
            StarTReflectionBridge.HELLFORGE_RECIPE_ID,
            StarTReflectionBridge.HELLFORGE_HEATING_RECIPE_ID,
            StarTReflectionBridge.HELLFORGE_START_RECIPE_ID,
            StarTReflectionBridge.HELLFORGE_START_HEATING_RECIPE_ID
    );

    private static final Set<ResourceLocation> HELLFORGE_MACHINES = Set.of(
            StarTReflectionBridge.HELLFORGE_START_MACHINE_ID,
            StarTReflectionBridge.HELLFORGE_GT_MACHINE_ID,
            StarTReflectionBridge.BOOSTED_HELLFORGE_START_MACHINE_ID,
            StarTReflectionBridge.BOOSTED_HELLFORGE_GT_MACHINE_ID
    );

    private static final Set<ResourceLocation> BOOSTED_HELLFORGE_MACHINES = Set.of(
            StarTReflectionBridge.BOOSTED_HELLFORGE_START_MACHINE_ID,
            StarTReflectionBridge.BOOSTED_HELLFORGE_GT_MACHINE_ID
    );

    static {
        registerBaselineCapabilities();
    }

    private StarTHellForgeHelper() {}

    public static void registerBaselineCapabilities() {
        for (ResourceLocation id : HELLFORGE_MACHINES) {
            MultiblockDetector.registerMultiblock(id);
            MultiblockDetector.registerBatchModeMultiblock(id);
        }
        for (ResourceLocation id : BOOSTED_HELLFORGE_MACHINES) {
            MultiblockDetector.registerThroughputBoostingMultiblock(id);
        }
    }

    public static List<HeatingFluid> getAllHeatingFluids() {
        return ALL_FLUIDS;
    }

    public static boolean isHellForgeCategory(ResourceLocation categoryId) {
        return categoryId != null && HELLFORGE_CATEGORIES.contains(categoryId);
    }

    public static boolean isHellForgeMachine(ResourceLocation machineId) {
        return machineId != null && HELLFORGE_MACHINES.contains(machineId);
    }

    public static boolean isBoostedHellForgeMachine(ResourceLocation machineId) {
        return machineId != null && BOOSTED_HELLFORGE_MACHINES.contains(machineId);
    }

    public static boolean isHellForge(RecipeNode node) {
        if (node == null) return false;
        if (isHellForgeCategory(node.getRecipeCategoryId())) return true;
        if (isHellForgeMachine(node.getMachineIcon())) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (isHellForgeMachine(ws)) return true;
        }
        return false;
    }

    public static int getRequiredTemperature(RecipeNode node) {
        if (node == null) return 0;
        int propTemp = node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE);
        if (propTemp > 0) return propTemp;
        return Math.max(0, node.getRecipeTemperature());
    }

    public static HeatingFluid getFluidForTemperature(int requiredTempMK) {
        for (HeatingFluid fluid : ALL_FLUIDS) {
            if (fluid.maxTempMK() >= requiredTempMK) {
                return fluid;
            }
        }
        return HeatingFluid.HELLFIRE_ESSENCE;
    }

    public static HeatingFluid getSelectedFluid(RecipeNode node) {
        int temp = getOperatingTemperature(node);
        return getFluidForTemperature(temp);
    }

    public static int normalizeTemperature(int tempMK) {
        if (tempMK <= 0) return HeatingFluid.FLAMEWAKE_SOLVENT.maxTempMK();
        return getFluidForTemperature(tempMK).maxTempMK();
    }

    public static int getOperatingTemperature(RecipeNode node) {
        if (node == null) return HeatingFluid.FLAMEWAKE_SOLVENT.maxTempMK();
        int stored = node.getProperties().get(StarTProperties.HELLFORGE_TEMP);
        if (stored > 0) {
            return normalizeTemperature(stored);
        }
        int req = getRequiredTemperature(node);
        return getFluidForTemperature(req).maxTempMK();
    }

    public static void setOperatingTemperature(RecipeNode node, int tempMK) {
        if (node == null) return;
        int normalized = normalizeTemperature(tempMK);
        node.getProperties().set(StarTProperties.HELLFORGE_TEMP, normalized);
        purgeLegacyCoilAddons(node);
        node.markOverclockDirty();
        node.markOperationalDirty();
    }

    public static void ensureDefaultHeat(RecipeNode node) {
        if (!isHellForge(node)) return;
        purgeLegacyCoilAddons(node);
        int stored = node.getProperties().get(StarTProperties.HELLFORGE_TEMP);
        int req = getRequiredTemperature(node);
        if (stored <= 0 && req > 0) {
            setOperatingTemperature(node, getFluidForTemperature(req).maxTempMK());
        }
    }

    public static void purgeLegacyCoilAddons(RecipeNode node) {
        if (node == null || node.getAddons().isEmpty()) return;
        boolean removed = node.getAddons().removeIf(a -> a != null && a.getCategory() == MachineAddon.Category.COIL);
        if (removed) {
            node.markOverclockDirty();
            node.markOperationalDirty();
        }
    }

    public static void cycleTemperature(RecipeNode node, int direction) {
        if (!isHellForge(node)) return;
        HeatingFluid current = getSelectedFluid(node);
        int idx = ALL_FLUIDS.indexOf(current);
        if (idx < 0) idx = 0;
        int nextIdx = (idx + direction) % ALL_FLUIDS.size();
        if (nextIdx < 0) nextIdx += ALL_FLUIDS.size();
        setOperatingTemperature(node, ALL_FLUIDS.get(nextIdx).maxTempMK());
    }

    public static int computeInfernalParallel(RecipeNode node) {
        if (!isHellForge(node)) return 1;
        int reqTemp = getRequiredTemperature(node);
        if (reqTemp <= 0) return 1;
        int curTemp = getOperatingTemperature(node);
        return computeInfernalParallelForTemp(reqTemp, curTemp);
    }

    public static int computeInfernalParallelForTemp(int requiredTempMK, int operatingTempMK) {
        if (requiredTempMK <= 0) return 1;
        if (operatingTempMK < requiredTempMK) return 1;
        int tiersAbove = (operatingTempMK - requiredTempMK) / 450;
        int clampedTiers = Math.min(20, Math.max(0, tiersAbove));
        return 1 << clampedTiers;
    }

    public static int computeExcessTiers(int requiredTempMK, int operatingTempMK) {
        if (requiredTempMK <= 0 || operatingTempMK < requiredTempMK) return 0;
        return Math.max(0, (operatingTempMK - requiredTempMK) / 450);
    }

    private static String resolveFluidLocalizedName(ResourceLocation fluidId) {
        if (fluidId == null) return null;
        try {
            var reg = ForgeRegistries.FLUIDS;
            if (reg == null || reg.isEmpty()) return null;
            Fluid fluid = reg.getValue(fluidId);
            if (fluid == null) return null;
            FluidStack stack = new FluidStack(fluid, 1000);
            if (stack.isEmpty()) return null;
            String name = stack.getDisplayName().getString();
            if (name == null || name.isBlank() || name.startsWith("fluid.") || name.startsWith("block.")) {
                return null;
            }
            return name.replaceAll("§[0-9a-fk-or]", "").trim();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
