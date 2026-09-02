package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTThreadingHelix;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.SteamMode;

import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Service Provider Interface (SPI) for external mod integration.
 * Encapsulates recipe parsing, hardware addons, capability matrices, overclocking, and compound recipe handling.
 */
public interface IModAdapter {

    /**
     * Unique mod identifier (e.g. "gtceu", "thermal", "systeams", "mekanism", "minecraft").
     */
    String getModId();

    /**
     * Adapter priority (higher values evaluated first, default 100).
     */
    default int getPriority() {
        return 100;
    }

    default boolean isGenericFallback() {
        return false;
    }

    /**
     * Checks if the target mod is currently loaded and active in the runtime environment.
     */
    boolean isLoaded();

    /**
     * Checks if this adapter handles the specified recipe category.
     */
    boolean handlesCategory(ResourceLocation categoryId);

    /**
     * Checks if this adapter handles the specified recipe node.
     */
    boolean handlesNode(RecipeNode node);

    /**
     * Discovers and registers hardware addons (coils, rotors, reflectors, augments) provided by the mod.
     */
    void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks);

    /**
     * Injects mod-specific capability matrix definitions.
     */
    void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager);

    /**
     * Registers synthetic/virtual recipes into EMI (e.g. kinetic generators, passive power sources).
     */
    default void registerSyntheticEmiRecipes(Object emiRegistry, Object emiCategory, Set<net.minecraft.world.item.Item> activeRecipeItems) {
        // Default no-op
    }

    /**
     * Scans and registers mod-specific multiblock machines and their capabilities.
     */
    default void scanMultiblocks(Object emiRecipeManager) {
        // Default no-op
    }

    /**
     * Scans and registers mod-specific multiblock 3D BOM structures and component requirements.
     */
    default void scanMultiblockStructures() {
        // Default no-op
    }

    /**
     * Scans and returns a single multiblock 3D BOM structure on demand if not yet cached.
     */
    default com.gtceu.calcboard.api.bom.MultiblockStructureDef scanMultiblockStructure(ResourceLocation machineId) {
        return null;
    }

    /**
     * Classifies a multiblock structure part into a BOM category (Coil, Hatch/Bus, Casing, Controller, etc.).
     * Returns null if this adapter does not recognize the part.
     */
    default PartCategory classifyBOMPart(ResourceLocation itemId) {
        return null;
    }

    /**
     * Accumulates structure slot counts for a given item and part category into the slot counts collector.
     */
    default void accumulateStructureSlots(ResourceLocation itemId, PartCategory category, int amount, com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.StructureSlotCounts slots) {
    }

    /**
     * Adapts compound recipes (e.g. water+fuel -> steam) or mod-specific energy/duration rules.
     * Returns true if custom handling was applied.
     */
    default boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return false;
    }

    /**
     * Attempts to build a compound/layered/multi-step recipe cluster for this mod.
     * Returns a {@link com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster} if confirmed via official APIs/objects, or {@code null}.
     */
    default com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster buildCompoundRecipe(
            Object recipeObj,
            Object backingRecipe,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        return null;
    }

    /**
     * Checks if the specified machine node supports installing hardware addons.
     */
    default boolean supportsAddons(RecipeNode node) {
        return !getApplicableAddonCategories(node).isEmpty();
    }

    /**
     * Returns the list of applicable addon categories for the specified node.
     */
    default List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return List.of();
    }

    /**
     * Checks if a specific addon is compatible with the target machine node.
     */
    default boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;
        return getApplicableAddonCategories(node).contains(addon.getCategory());
    }

    /**
     * Checks if the addon can be installed considering slot limits and duplicate rules.
     */
    default boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        return isAddonCompatible(node, addon);
    }

    /**
     * Lifecycle hook when an addon is installed onto a node (e.g. replacing existing coil/rotor/reflector).
     */
    default void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.COIL ||
            addon.getCategory() == MachineAddon.Category.ROTOR ||
            addon.getCategory() == MachineAddon.Category.REFLECTOR ||
            addon.getCategory() == MachineAddon.Category.PARALLEL ||
            addon.getCategory() == MachineAddon.Category.MAINTENANCE ||
            addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT ||
            addon.isThermalUpgradeKit()) {
            node.getAddons().removeIf(a -> a.getCategory() == addon.getCategory() || (addon.isThermalUpgradeKit() && a.isThermalUpgradeKit()) || a.getId().equals(addon.getId()));
        } else if (addon.getCategory() != MachineAddon.Category.ENERGY_HATCH &&
                   addon.getCategory() != MachineAddon.Category.THERMAL_AUGMENT &&
                   !addon.getCategory().equals(AddonCategory.MAGNET)) {
            node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
        }
        node.getAddons().add(addon);
    }

    /**
     * Lifecycle hook when an addon is removed from a node.
     */
    default void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
    }

    /**
     * Returns special reset/default cards in the machine config dialog for the node (e.g. standard 100% rotor).
     */
    default List<MachineAddon> getResetAddonCards(RecipeNode node) {
        return List.of();
    }

    /**
     * Supplies extra structure parts or companion hardware required for this machine in the BOM
     * (e.g. Generator Coil for Create: New Age Carbon Brushes, or equipped machine addons).
     */
    default void populateExtraBOMParts(RecipeNode node, List<com.gtceu.calcboard.api.bom.MultiblockStructurePart> parts) {
    }

    /**
     * Resolves the list of structural parts (controllers, casings, coils, hatches, buses, companion blocks, addons)
     * required to construct 1 unit of the machine represented by this node.
     */
    default List<com.gtceu.calcboard.api.bom.MultiblockStructurePart> resolveStructureParts(RecipeNode node, boolean dualLowerTierEnergyHatches) {
        List<com.gtceu.calcboard.api.bom.MultiblockStructurePart> list = new ArrayList<>();
        if (node == null) return list;

        ResourceLocation machineId = node.getMachineIcon();
        if (machineId == null && !node.getAvailableWorkstations().isEmpty()) {
            machineId = node.getAvailableWorkstations().get(0);
        }

        GTVoltageTier targetTier = node.getTargetTier() != null ? node.getTargetTier() : node.getRecipeTier();
        if (targetTier != null) {
            ResourceLocation tieredWs = getWorkstationForTier(node, targetTier);
            if (tieredWs != null) {
                machineId = tieredWs;
            }
        }

        if (machineId != null && isLikelyMachineOrStructure(node, machineId)) {
            String displayName = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.formatMachineName(machineId.getPath());
            list.add(new com.gtceu.calcboard.api.bom.MultiblockStructurePart(
                    machineId,
                    displayName,
                    1,
                    com.gtceu.calcboard.api.bom.PartCategory.CONTROLLER
            ));
        }

        // Aggregate equipped addons
        Map<ResourceLocation, Integer> addonCounts = new LinkedHashMap<>();
        Map<ResourceLocation, String> addonNames = new LinkedHashMap<>();
        for (MachineAddon addon : node.getAddons()) {
            if (addon != null && addon.getItemIcon() != null) {
                ResourceLocation icon = addon.getItemIcon();
                addonCounts.merge(icon, 1, Integer::sum);
                addonNames.put(icon, addon.getName());
            }
        }
        for (var entry : addonCounts.entrySet()) {
            ResourceLocation icon = entry.getKey();
            int count = entry.getValue();
            String partName = addonNames.getOrDefault(icon, icon.getPath());
            com.gtceu.calcboard.api.bom.PartCategory pCat = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.classifyPart(icon);
            list.add(new com.gtceu.calcboard.api.bom.MultiblockStructurePart(icon, partName, count, pCat));
        }

        populateExtraBOMParts(node, list);
        return list;
    }

    default boolean isLikelyMachineOrStructure(RecipeNode node, ResourceLocation machineId) {
        if (machineId == null) return false;
        String path = machineId.getPath().toLowerCase(java.util.Locale.ROOT);
        String ns = machineId.getNamespace().toLowerCase(java.util.Locale.ROOT);
        if (path.equals("air") || path.equals("barrier") || path.equals("structure_void")) return false;

        if (node != null) {
            if (node.isMultiblock() || node.isGenerator()) return true;
            if (node.getEnergyType() != com.gtceu.calcboard.api.type.EnergyType.NONE) return true;
            if (!node.getAddons().isEmpty()) return true;
            if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return true;
        }

        if (com.gtceu.calcboard.api.catalog.MultiblockDetector.isMultiblock(machineId)) return true;
        if (com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(machineId) != null) return true;

        if (ns.equals("gtceu") || ns.contains("start")) {
            return path.contains("machine") || path.contains("generator") || path.contains("hatch")
                    || path.contains("bus") || path.contains("sieve") || path.contains("furnace")
                    || path.contains("assembler") || path.contains("reactor") || path.contains("boiler")
                    || path.contains("turbine") || path.contains("pump") || path.contains("miner")
                    || path.contains("smelter") || path.contains("press") || path.contains("crusher")
                    || path.contains("breaker") || path.contains("centrifuge") || path.contains("cutter")
                    || path.contains("drill") || path.contains("lathe") || path.contains("mixer")
                    || path.contains("electrolyzer") || path.contains("extractor") || path.contains("sifter")
                    || path.startsWith("lv_") || path.startsWith("mv_") || path.startsWith("hv_")
                    || path.startsWith("ev_") || path.startsWith("iv_") || path.startsWith("luv_")
                    || path.startsWith("zpm_") || path.startsWith("uv_") || path.startsWith("uhv_")
                    || path.startsWith("uev_") || path.startsWith("uiv_") || path.startsWith("uxv_")
                    || path.startsWith("opv_") || path.startsWith("max_");
        } else if (ns.equals("create") || ns.equals("create_new_age") || ns.equals("create_enchantment_industry")) {
            return path.contains("deployer") || path.contains("spout") || path.contains("press")
                    || path.contains("saw") || path.contains("drill") || path.contains("millstone")
                    || path.contains("crushing_wheel") || path.contains("fan") || path.contains("mixer")
                    || path.contains("crafter") || path.contains("generator") || path.contains("motor")
                    || path.contains("wheel") || path.contains("sequenced_assembly");
        } else if (ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("thermal_innovation") || ns.equals("systeams")) {
            return path.contains("dynamo") || path.contains("machine") || path.contains("cell")
                    || path.contains("boiler") || path.contains("furnace") || path.contains("centrifuge");
        }

        return false;
    }

    /**
     * Resolves the appropriate workstation ResourceLocation for a given target tier on this node.
     * Default implementation delegates to node.getWorkstationForTierFromList(tier).
     * Mod adapters (e.g. GTCEuModAdapter) can deductively derive tier-specific machine IDs
     * (e.g. gtceu:opv_macerator) via official registry or naming conventions even if the recipe viewer
     * didn't inject all tier variants into availableWorkstations.
     */
    default ResourceLocation getWorkstationForTier(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return null;
        return node.getWorkstationForTierFromList(tier);
    }

    /**
     * Resolves all valid multiblock controller workstations for this node.
     * Default implementation filters node.getAvailableWorkstations() for multiblocks.
     * Mod adapters (e.g. GTCEuModAdapter) can deductively discover all multiblock machines
     * that support this node's recipe category via official registries.
     */
    default List<ResourceLocation> getMultiblockWorkstations(RecipeNode node) {
        if (node == null) return java.util.Collections.emptyList();
        List<ResourceLocation> list = new ArrayList<>();
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && MultiblockDetector.isMultiblock(ws) && !list.contains(ws)) {
                list.add(ws);
            }
        }
        return list;
    }

    /**
     * Determines the preferred multiblock workstation for a node from the available list.
     */
    default ResourceLocation getPreferredMultiblockWorkstation(RecipeNode node, List<ResourceLocation> availableWorkstations) {
        if (node == null || availableWorkstations == null || availableWorkstations.isEmpty()) return null;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (MultiblockDetector.isMultiblock(ws) && ws.getPath().equalsIgnoreCase(catId.getPath())) {
                    return ws;
                }
            }
        }
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                return ws;
            }
        }
        return null;
    }

    /**
     * Installs an addon onto the machine node (handles slots, replacement, shift-click filling).
     */
    default void handleInstallAddon(RecipeNode node, MachineAddon addon, boolean shiftClick) {
        if (node == null || addon == null) return;
        if (!node.isMultiblock() && node.hasMultiblockOption() && addon.getCategory() != AddonCategory.CUSTOM && addon.getCategory() != AddonCategory.THERMAL_AUGMENT) {
            node.setMultiblock(true);
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            }
        }
        onAddonInstalled(node, addon.copy());
    }

    /**
     * Uninstalls an addon or removes a copy from the machine node.
     */
    default void handleUninstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory().equals(AddonCategory.THREADING)) {
            com.gtceu.calcboard.api.type.GTThreadingHelix helix = com.gtceu.calcboard.api.type.GTThreadingHelix.fromId(addon.getId());
            if (helix != null && node.getThreadingConfig() != null) {
                node.getThreadingConfig().setHelixCount(helix, 0);
            }
        }
        node.removeOneAddon(addon.getId());
        onAddonRemoved(node, addon);
    }

    /**
     * Checks if the addon is considered installed on the node.
     */
    default boolean isAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        return node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
    }

    /**
     * Gets the installed count of this addon on the node.
     */
    default int getAddonInstalledCount(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 0;
        return (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
    }

    /**
     * Formats the subtitle text for an addon in the catalog grid (e.g. "Magnetic Force: 24x", "Parallel: 4x").
     */
    default String formatAddonSubtitle(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory().equals(AddonCategory.MAGNET)) {
            return String.format("Magnetic Force: %dx", addon.getMagneticForce());
        }
        if (addon.getCategory().equals(AddonCategory.PARALLEL)) {
            return String.format("Parallel: %dx", addon.getParallelMultiplier());
        }
        if (addon.getCategory().equals(AddonCategory.ENERGY_HATCH)) {
            if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon eh) {
                return String.format("Tier: %s (%,dA)", eh.getTier().getName(), eh.getAmperage());
            }
            return "Energy Hatch";
        }
        if (addon.getCategory().equals(AddonCategory.HATCH_BUS)) {
            if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h) {
                if (h.isME()) return "§d📡 ME Automation";
                if (h.getHatchType().isFluid()) {
                    return String.format("§b%,d mB/slot", h.getTankCapacityMB() / Math.max(1, h.getSlotCapacity()));
                }
                return String.format("§7%d Item Slots", h.getSlotCapacity());
            }
            return "Hatch / Bus";
        }
        if (addon.getCategory().equals(AddonCategory.REFLECTOR)) {
            return String.format("Tier %d Reflector", addon.getReflectorTier());
        }
        if (addon.getCategory().equals(AddonCategory.MAINTENANCE)) {
            return "Maintenance Hatch";
        }
        if (addon.getCategory().equals(AddonCategory.COIL)) {
            return String.format("Coil: %d K", addon.getCoilTemperature());
        }
        if (addon.getCategory().equals(AddonCategory.ROTOR)) {
            return String.format("§e⚡ %d%%", addon.getRotorPower());
        }
        return addon.getName();
    }

    /**
     * Builds the hover tooltip lines for an addon in the MachineConfigDialog (both active and catalog views).
     */
    default void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<net.minecraft.network.chat.Component> tooltip) {
        if (addon == null || tooltip == null) return;
        if (addon.getCategory().equals(AddonCategory.ENERGY_HATCH) && addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon eh) {
            tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§e⚡ Max Voltage: %s (%,d EU/t, %,dA)", eh.getTier().getName(), eh.getTier().getVoltage(), eh.getAmperage())));
            if (eh.isLaser()) tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§d📡 High-density Laser Target Input (%,dA)", eh.getAmperage())));
            if (eh.isSubstation()) tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§b🏢 Substation High-amperage Input (%,dA)", eh.getAmperage())));
        }
        if (addon.getCategory().equals(AddonCategory.HATCH_BUS) && addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h) {
            if (h.isME()) {
                tooltip.add(net.minecraft.network.chat.Component.literal("§d📡 ME Network Automation Integration"));
                tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§7Provides %d integrated virtual recipe slots", h.getSlotCapacity())));
            } else if (h.getHatchType().isFluid()) {
                tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§b📦 Fluid Slots: %d", h.getSlotCapacity())));
                tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§7Tank Capacity: %,d mB (%,d mB / slot)", h.getTankCapacityMB(), h.getTankCapacityMB() / Math.max(1, h.getSlotCapacity()))));
                tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§e⚡ Voltage Tier: %s", h.getTier().getName())));
            } else {
                tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§7📦 Item Inventory Slots: %d", h.getSlotCapacity())));
                tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§e⚡ Voltage Tier: %s", h.getTier().getName())));
            }
        }
        if (addon.getParallelMultiplier() > 1) {
            tooltip.add(net.minecraft.network.chat.Component.literal("§b").append(net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.stat.parallel", addon.getParallelMultiplier())));
        }
        if (addon.getDurationMultiplier() != 1.0) {
            tooltip.add(net.minecraft.network.chat.Component.literal("§a").append(net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.stat.speed_mult", String.format(java.util.Locale.ROOT, "%.2fx", 1.0 / addon.getDurationMultiplier()))));
        }
        if (addon.getEutMultiplier() != 1.0 && !addon.getCategory().equals(AddonCategory.MAGNET)) {
            tooltip.add(net.minecraft.network.chat.Component.literal("§e").append(net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.stat.eut_mult", String.format(java.util.Locale.ROOT, "%.2fx", addon.getEutMultiplier()))));
        }
    }

    /**
     * Formats the badge text displayed on the top/bottom corner of an addon card in the catalog grid.
     */
    default String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            return String.format("§b✦ Tier %d", addon.getReflectorTier());
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH && addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon eh) {
            return eh.getAmperage() > 2
                    ? String.format("§e⚡ %s (%,dA)", eh.getTier().getName(), eh.getAmperage())
                    : String.format("§e⚡ %s", eh.getTier().getName());
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS && addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h) {
            if (h.isME()) {
                return "§d📡 ME";
            }
            if (h.getHatchType().isFluid()) {
                return h.getSlotCapacity() > 1
                        ? String.format("§b📦 %dx Fluid", h.getSlotCapacity())
                        : String.format("§b📦 %s Fluid", h.getTier().getName());
            }
            return String.format("§7📦 %s (%d)", h.getTier().getName(), h.getSlotCapacity());
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            return String.format("§6♨ %d K", addon.getCoilTemperature());
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return String.format("§a⚙ %d%%", addon.getRotorEfficiency());
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            return String.format("§b%dx Par", addon.getParallelMultiplier());
        }
        return "";
    }

    /**
     * Validates node operating conditions (reflector tier, coil temperature, etc.) and fills warning list.
     * Returns true if all prerequisites are met.
     */
    default boolean validateNode(RecipeNode node, List<net.minecraft.network.chat.Component> warnings) {
        return true;
    }

    /**
     * Validates node operating conditions including graph flow state.
     */
    default boolean validateNode(RecipeNode node, FlowGraph graph, List<net.minecraft.network.chat.Component> warnings) {
        return validateNode(node, warnings);
    }

    /**
     * Tailors an addon for a specific machine node (e.g. coil energy discounts, thermal augment scaling).
     */
    default MachineAddon tailorAddon(MachineAddon addon, RecipeNode targetNode) {
        return addon;
    }

    /**
     * Calculates overclock and effective duration/power for the given node.
     */
    default OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        if (isGenerator) {
            return new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        }
        return node.getOverclockMode().calculate(node.getBaseDurationTicks(), node.getBaseEUt(), node.getTierDelta());
    }

    /**
     * Computes the single machine operating power (EU/t, FE/t, SU) while running.
     */
    default double computeSingleMachinePower(RecipeNode node) {
        if (!node.isOperational()) return 0.0;
        if (node.isGenerator()) {
            return computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
        }
        return computeOverclock(node, node.getTargetTier(), false).eut() * node.getCombinedEutMultiplier();
    }

    /**
     * Computes total effective parallel execution multiplier for this machine node.
     */
    default int computeEffectiveParallel(RecipeNode node) {
        return Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
    }

    /**
     * Gets the innate or recommended default parallel execution count for this machine node.
     */
    default int getDefaultParallel(RecipeNode node) {
        return 1;
    }

    /**
     * Automatically calculates and tunes optimal parallel count (e.g. for turbines/generators).
     */
    default void autoTuneParallel(RecipeNode node) {
        // Default no-op
    }

    /**
     * Checks if this node supports steam operational modes (LP/HP steam).
     */
    default boolean supportsSteamMode(RecipeNode node) {
        return false;
    }

    /**
     * Handles steam mode lifecycle changes (slot injection/removal and workstation icon sync).
     */
    default void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        // Default no-op
    }

    /**
     * Computes the effective ingredient flow rate (units/sec) for the given node and stack.
     */
    default double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        return defaultRate;
    }

    /**
     * Computes the single machine ingredient flow rate (units/sec) for the given node and stack.
     */
    default double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        return defaultRate;
    }

    /**
     * Calculates the maximum viable parallel capacity for the node under current voltage, hatch, and hardware limits.
     */
    default int getMaxParallelCapacity(RecipeNode node) {
        return node != null ? Math.max(1, node.getParallel()) : 1;
    }

    /**
     * Computes the effective probability/chance (0.0 ~ 1.0) of producing an output byproduct slot.
     */
    default double computeEffectiveOutputChance(RecipeNode node, int outputIndex, double defaultChance) {
        return defaultChance;
    }

    /**
     * Checks if this recipe node represents a boiler recipe.
     */
    default boolean isBoilerRecipe(RecipeNode node) {
        return false;
    }

    /**
     * Checks if this recipe node represents a liquid-fueled boiler recipe.
     */
    default boolean isLiquidBoilerRecipe(RecipeNode node) {
        return false;
    }
    /**
     * Handles machine icon lifecycle changes (e.g. configuring multiblock state, steam mode, default parallels).
     */
    default void onMachineIconChanged(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        // Default no-op
    }

    /**
     * Resolves the energy type for this node (EU, FE, SU, Steam/Heat, Passive/None).
     */
    default EnergyType getEnergyType(RecipeNode node) {
        return EnergyType.ELECTRIC_EU;
    }

    /**
     * Checks if this recipe node represents a turbine.
     */
    default boolean isTurbine(RecipeNode node) {
        return false;
    }

    /**
     * Checks if this recipe node represents a large multiblock turbine.
     */
    default boolean isLargeTurbine(RecipeNode node) {
        return false;
    }

    /**
     * Checks if this recipe node represents an energy generator or dynamo.
     */
    default boolean isGenerator(RecipeNode node) {
        if (node == null) return false;
        return node.getEnergyType() == EnergyType.ELECTRIC_EU && node.getBaseEUt() < 0;
    }

    /**
     * Computes the maximum generator power output (EU/t or RF/t) for this node.
     */
    default double getGeneratorMaxPower(RecipeNode node) {
        if (node == null) return 0.0;
        return Math.abs(node.getBaseEUt());
    }

    /**
     * Formats the power / energy / stress summary string for the bottom stats line of a node card.
     */
    default String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        if (node.getEnergyType() == EnergyType.NONE) {
            return net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        }
        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            return (node.getEfficiency() < 0.999)
                    ? String.format(java.util.Locale.ROOT, "§e♨%.0f%%", node.getEfficiency() * 100.0)
                    : "§6♨";
        }
        String eutStr = displayMode.formatNodePower(node);
        return (node.getEfficiency() < 0.999)
                ? String.format("§e⚡%.0f%% %s", node.getEfficiency() * 100.0, eutStr)
                : eutStr;
    }

    /**
     * Builds the detailed hover tooltip for the bottom energy / stats line of a node card.
     */
    default List<net.minecraft.network.chat.Component> buildEnergyTooltip(RecipeNode node) {
        List<net.minecraft.network.chat.Component> tooltipLines = new java.util.ArrayList<>();
        if (node.isGenerator()) {
            tooltipLines.add(net.minecraft.network.chat.Component.literal("§a⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.total_gen").getString()));
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = com.gtceu.calcboard.api.type.GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Total Generation: §a+%,.2f EU/t", totEUt)));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Current: §a+%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            if (node.getEfficiency() < 0.999) {
                tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§e⚡ Rotor Efficiency: §f%.1f%%", node.getEfficiency() * 100.0)));
            }
        } else {
            tooltipLines.add(net.minecraft.network.chat.Component.literal("§e⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.total_power").getString()));
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = com.gtceu.calcboard.api.type.GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Total Consumption: §c%,.2f EU/t", totEUt)));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Current: §e%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        }
        return tooltipLines;
    }

    /**
     * Checks if this recipe node represents a fusion reactor.
     */
    default boolean isFusion(RecipeNode node) {
        return false;
    }

    /**
     * Gets the fusion reactor tier (1, 2, 3, etc.) for this node.
     */
    default int getFusionTier(RecipeNode node) {
        return 0;
    }

    /**
     * Gets the minimum voltage tier required for this fusion node.
     */
    default GTVoltageTier getMinFusionVoltageTier(RecipeNode node) {
        return GTVoltageTier.LuV;
    }

    /**
     * Sanitizes or constrains the requested target voltage tier according to machine physical rules (e.g. Fusion minimum LuV/ZPM/UV).
     */
    default GTVoltageTier sanitizeTargetTier(RecipeNode node, GTVoltageTier requestedTier) {
        return requestedTier != null ? requestedTier : (node != null ? node.getRecipeTier() : GTVoltageTier.ULV);
    }

    /**
     * Checks if this machine node supports interactive fluid booster / catalyst control in the GUI header.
     */
    default boolean supportsBoosterControl(RecipeNode node) {
        return false;
    }

    /**
     * Gets the formatted display text for the booster button in the GUI header.
     */
    default Component getBoosterDisplayComponent(RecipeNode node) {
        return null;
    }

    /**
     * Cycles the booster level or mode on the machine node (e.g. None -> Passive -> Active).
     */
    default void cycleBooster(RecipeNode node, int direction) {
        // Default no-op
    }

    /**
     * Synchronizes any required auxiliary/booster fluid ingredient inputs on the machine node.
     */
    default void syncBoosterInputs(RecipeNode node) {
        // Default no-op
    }

    /**
     * Gets the background color integer (0xAARRGGBB) for the booster button.
     */
    default int getBoosterBackgroundColor(RecipeNode node, boolean isHovered) {
        return isHovered ? 0xFF2A303C : 0xFF1E222D;
    }

    /**
     * Gets the outline border color integer (0xAARRGGBB) for the booster button.
     */
    default int getBoosterBorderColor(RecipeNode node, boolean isHovered) {
        return isHovered ? 0xFF6B7B96 : 0xFF353C4D;
    }

    /**
     * Gets the text color integer (0xAARRGGBB) for the booster button.
     */
    default int getBoosterTextColor(RecipeNode node, boolean isHovered) {
        return 0xFFFFFFFF;
    }

    /**
     * Builds tooltip lines for the booster button in the machine config dialog.
     */
    default void buildBoosterTooltip(RecipeNode node, List<Component> tooltip) {
        // Default no-op
    }
}



