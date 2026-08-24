package com.gtceu.calcboard.api;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Core domain model representing a recipe node on the calculator canvas.
 * Encapsulates graph attributes, I/O ingredient streams, and delegates mod-specific
 * physics, overclocking, and hardware calculations to registered {@link IModAdapter} instances.
 */
public class RecipeNode {
    private String id;
    private String name;
    private ResourceLocation machineIcon;

    private double baseDurationTicks;
    private double baseEUt;
    private GTVoltageTier recipeTier;

    // User-adjustable parameters
    private GTVoltageTier targetTier;
    private double machineCount;
    private int parallel;
    private OverclockMode overclockMode;

    // Inputs and outputs
    private final List<IngredientStack> inputs = new ArrayList<>();
    private final List<IngredientStack> outputs = new ArrayList<>();

    // Master base node for Auto Ratio
    private boolean isBaseNode = false;
    private boolean isGenerator = false;
    private final NodePropertyStore properties = new NodePropertyStore();

    // Canvas position & Dimensions
    private double posX;
    private double posY;
    private int cardWidth = 245;
    private int cardHeight = 0;

    // Module / Subgraph Abstraction
    private boolean isModule = false;
    private FlowGraph subGraph = null;
    private int containedMachineCount = 0;
    private double efficiency = 1.0;
    private EnergyType energyType = EnergyType.ELECTRIC_EU;

    // Reroute / Junction Node Abstraction (RFC-001)
    private boolean isReroute = false;

    // Module N:N Port Mapping Origins (RFC-002)
    public record PortOrigin(String internalNodeId, int internalPortIndex) {
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("nodeId", internalNodeId);
            tag.putInt("portIdx", internalPortIndex);
            return tag;
        }

        public static PortOrigin deserializeNBT(CompoundTag tag) {
            return new PortOrigin(tag.getString("nodeId"), tag.getInt("portIdx"));
        }
    }

    private final List<List<PortOrigin>> moduleInputOrigins = new ArrayList<>();
    private final List<List<PortOrigin>> moduleOutputOrigins = new ArrayList<>();

    // Hardware Addons, Hatches, and Augments
    private final List<MachineAddon> addons = new ArrayList<>();
    private final List<ResourceLocation> availableWorkstations = new ArrayList<>();
    private ResourceLocation recipeCategoryId;
    private SteamMode steamMode = SteamMode.NONE;
    private boolean isMultiblock = false;
    private NodeThreadingConfig threadingConfig;

    public static final int[] STANDARD_RPMS = {4, 8, 16, 32, 64, 128, 256};

    public RecipeNode(String id, String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.baseDurationTicks = Math.max(1.0, baseDurationTicks);
        this.baseEUt = Math.max(1.0, baseEUt);
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.getTierForVoltage((long) baseEUt);
        this.targetTier = this.recipeTier;
        this.machineCount = 1.0;
        this.parallel = 1;
        this.overclockMode = OverclockMode.STANDARD;
        this.isGenerator = false;
        this.efficiency = 1.0;
        this.isMultiblock = false;
    }

    public static RecipeNode create(String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        RecipeNode node = new RecipeNode(UUID.randomUUID().toString(), name, baseDurationTicks, baseEUt, recipeTier);
        if (name != null && !name.isEmpty()) {
            String base = name.contains(" (") ? name.substring(0, name.indexOf(" (")) : name;
            String sanitized = base.toLowerCase(Locale.ROOT).trim().replace(" ", "_");
            ResourceLocation loc = ResourceLocation.tryParse("gtceu:" + sanitized);
            if (loc != null) {
                node.setMachineIcon(loc);
                node.setRecipeCategoryId(loc);
                node.getAvailableWorkstations().add(loc);
            }
        }
        return node;
    }

    public static RecipeNode createReroute(double posX, double posY) {
        RecipeNode node = new RecipeNode(UUID.randomUUID().toString(), "Reroute", 0.0, 0.0, GTVoltageTier.ULV);
        node.setReroute(true);
        node.setPos(posX, posY);
        node.setCardWidth(32);
        node.setCardHeight(32);
        return node;
    }

    public RecipeNode copy() {
        CompoundTag tag = this.serializeNBT();
        tag.putString("id", UUID.randomUUID().toString());
        return RecipeNode.deserializeNBT(tag);
    }

    // =========================================================================
    // Core Identity & Attributes
    // =========================================================================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        if (name != null && name.contains(" (") && name.endsWith(")")) {
            if (!inputs.isEmpty() && inputs.get(0) != null) {
                int openParen = name.indexOf(" (");
                String prefix = name.substring(0, openParen);
                String currentIngredientName = inputs.get(0).getDisplayName();
                if (currentIngredientName != null && !currentIngredientName.isEmpty()) {
                    return prefix + " (" + currentIngredientName + ")";
                }
            } else if (!outputs.isEmpty() && outputs.get(0) != null) {
                int openParen = name.indexOf(" (");
                String prefix = name.substring(0, openParen);
                String currentIngredientName = outputs.get(0).getDisplayName();
                if (currentIngredientName != null && !currentIngredientName.isEmpty()) {
                    return prefix + " (" + currentIngredientName + ")";
                }
            }
        }
        return name != null ? name : "";
    }

    public String getRawName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResourceLocation getMachineIcon() {
        return machineIcon;
    }

    public void setMachineIcon(ResourceLocation machineIcon) {
        this.machineIcon = machineIcon;
    }

    public double getBaseDurationTicks() {
        return isReroute ? 0.0 : baseDurationTicks;
    }

    public void setBaseDurationTicks(double baseDurationTicks) {
        this.baseDurationTicks = isReroute ? 0.0 : Math.max(1.0, baseDurationTicks);
    }

    public double getBaseEUt() {
        return isReroute ? 0.0 : baseEUt;
    }

    public void setBaseEUt(double baseEUt) {
        this.baseEUt = isReroute ? 0.0 : Math.max(0.0, baseEUt);
    }

    public GTVoltageTier getRecipeTier() {
        return recipeTier;
    }

    public void setRecipeTier(GTVoltageTier recipeTier) {
        this.recipeTier = recipeTier;
    }

    public GTVoltageTier getTargetTier() {
        return targetTier;
    }

    public void setTargetTier(GTVoltageTier targetTier) {
        GTVoltageTier minTier = recipeTier;
        if (isFusion()) {
            GTVoltageTier minFusionTier = getMinFusionVoltageTier();
            if (minFusionTier.ordinal() > minTier.ordinal()) {
                minTier = minFusionTier;
            }
        }
        this.targetTier = (targetTier.ordinal() >= minTier.ordinal()) ? targetTier : minTier;
    }

    public double getMachineCount() {
        return machineCount;
    }

    public void setMachineCount(double machineCount) {
        this.machineCount = Math.max(0.01, machineCount);
    }

    public int getParallel() {
        return parallel;
    }

    public void setParallel(int parallel) {
        this.parallel = Math.max(1, parallel);
    }

    public OverclockMode getOverclockMode() {
        return overclockMode;
    }

    public void setOverclockMode(OverclockMode overclockMode) {
        this.overclockMode = overclockMode != null ? overclockMode : OverclockMode.STANDARD;
    }

    public boolean isBaseNode() {
        return isBaseNode;
    }

    public void setBaseNode(boolean baseNode) {
        this.isBaseNode = baseNode;
    }

    public boolean isGenerator() {
        return isGenerator;
    }

    public void setGenerator(boolean generator) {
        this.isGenerator = generator;
    }

    public NodePropertyStore getProperties() {
        return properties;
    }

    public EnergyType getEnergyType() {
        if (energyType != null) {
            return energyType;
        }
        if (baseEUt <= 0.0 && !isGenerator) {
            return isLiquidBoilerRecipe() || (steamMode != null && steamMode.isSteam()) ? EnergyType.HEAT_OR_SELF : EnergyType.NONE;
        }
        return EnergyType.ELECTRIC_EU;
    }

    public void setEnergyType(EnergyType energyType) {
        this.energyType = energyType;
    }

    // =========================================================================
    // Canvas & UI Geometry
    // =========================================================================

    public double getPosX() {
        return posX;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public double getPosY() {
        return posY;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public void setPos(double posX, double posY) {
        this.posX = posX;
        this.posY = posY;
    }

    public int getCardWidth() {
        if (isReroute) return 32;
        return Math.max(245, Math.min(500, cardWidth));
    }

    public void setCardWidth(int cardWidth) {
        this.cardWidth = isReroute ? 32 : Math.max(245, Math.min(500, cardWidth));
    }

    public int getCardHeight() {
        if (isReroute) return 32;
        return Math.max(0, Math.min(600, cardHeight));
    }

    public void setCardHeight(int cardHeight) {
        this.cardHeight = isReroute ? 32 : Math.max(0, Math.min(600, cardHeight));
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = Math.max(0.0, Math.min(1.0, efficiency));
    }

    // =========================================================================
    // Reroute & Subgraph Abstractions
    // =========================================================================

    public boolean isReroute() {
        return isReroute;
    }

    public void setReroute(boolean reroute) {
        this.isReroute = reroute;
        if (reroute) {
            this.cardWidth = 32;
            this.cardHeight = 32;
        }
    }

    public void bindRerouteIngredient(IngredientStack stack) {
        if (isReroute && stack != null) {
            inputs.clear();
            outputs.clear();
            inputs.add(stack.copy());
            outputs.add(stack.copy());
        }
    }

    public void unbindRerouteIngredient() {
        if (isReroute) {
            inputs.clear();
            outputs.clear();
        }
    }

    public boolean isModule() {
        return isModule;
    }

    public void setModule(boolean module) {
        this.isModule = module;
    }

    public FlowGraph getSubGraph() {
        return subGraph;
    }

    public void setSubGraph(FlowGraph subGraph) {
        this.subGraph = subGraph;
    }

    public int getContainedMachineCount() {
        return containedMachineCount;
    }

    public void setContainedMachineCount(int count) {
        this.containedMachineCount = count;
    }

    public List<List<PortOrigin>> getModuleInputOrigins() {
        return moduleInputOrigins;
    }

    public List<List<PortOrigin>> getModuleOutputOrigins() {
        return moduleOutputOrigins;
    }

    // =========================================================================
    // Streams & Workstations
    // =========================================================================

    public List<IngredientStack> getInputs() {
        return inputs;
    }

    public List<IngredientStack> getOutputs() {
        return outputs;
    }

    public void addInput(IngredientStack stack) {
        inputs.add(stack);
    }

    public void addOutput(IngredientStack stack) {
        outputs.add(stack);
    }

    public List<ResourceLocation> getAvailableWorkstations() {
        return availableWorkstations;
    }

    public void setAvailableWorkstations(List<ResourceLocation> availableWorkstations) {
        this.availableWorkstations.clear();
        if (availableWorkstations != null) {
            this.availableWorkstations.addAll(availableWorkstations);
        }
    }

    public ResourceLocation getRecipeCategoryId() {
        return recipeCategoryId;
    }

    public void setRecipeCategoryId(ResourceLocation recipeCategoryId) {
        this.recipeCategoryId = recipeCategoryId;
    }

    // =========================================================================
    // Hardware Addons, Augments & Upgrades
    // =========================================================================

    public static boolean isThermalUpgradeKit(MachineAddon addon) {
        if (addon == null) return false;
        if (addon.getCategory() != MachineAddon.Category.THERMAL_AUGMENT) return false;
        return addon.getParallelMultiplier() > 1 || addon.getId().contains("upgrade_kit") || addon.getId().contains("tier_upgrade");
    }

    public static boolean isMultiblockWorkstation(ResourceLocation ws) {
        if (ws == null) return false;
        return MultiblockDetector.isMultiblock(ws);
    }

    public List<MachineAddon> getAddons() {
        return addons;
    }

    public void addAddon(MachineAddon addon) {
        if (addon == null) return;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        if (adapter != null) {
            if (!adapter.canInstallAddon(this, addon)) {
                return;
            }
            adapter.onAddonInstalled(this, addon);
        } else {
            addons.add(addon);
        }
    }

    public void removeSingleAddon(String addonId) {
        if (addonId == null) return;
        for (int i = 0; i < addons.size(); i++) {
            if (addons.get(i).getId().equals(addonId)) {
                MachineAddon removed = addons.remove(i);
                IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
                if (adapter != null) {
                    adapter.onAddonRemoved(this, removed);
                }
                break;
            }
        }
    }

    public void removeAddon(String addonId) {
        addons.removeIf(a -> a.getId().equals(addonId));
    }

    public void clearAddons() {
        addons.clear();
    }

    public double getCombinedDurationMultiplier() {
        double mult = 1.0;
        for (MachineAddon a : addons) {
            mult *= a.getDurationMultiplier();
        }
        return mult;
    }

    public double getCombinedEutMultiplier() {
        double mult = 1.0;
        for (MachineAddon a : addons) {
            mult *= a.getEutMultiplier();
        }
        return mult;
    }

    public int getCombinedParallelMultiplier() {
        int mult = 1;
        for (MachineAddon a : addons) {
            mult *= a.getParallelMultiplier();
        }
        return mult;
    }

    public boolean hasPowerConstantAddon() {
        for (MachineAddon a : addons) {
            if (a.isPowerConstant()) return true;
        }
        return false;
    }

    // =========================================================================
    // Operating Modes & Capabilities (Delegated to IModAdapter)
    // =========================================================================

    public SteamMode getSteamMode() {
        return steamMode != null ? steamMode : SteamMode.NONE;
    }

    public void setSteamMode(SteamMode steamMode) {
        SteamMode oldMode = this.steamMode;
        this.steamMode = steamMode != null ? steamMode : SteamMode.NONE;
        syncSteamInputSlot(oldMode, this.steamMode);
    }

    public boolean supportsSteamMode() {
        return ModAdapterRegistry.getAdapterForNode(this).supportsSteamMode(this);
    }

    public boolean isLiquidBoilerRecipe() {
        return ModAdapterRegistry.getAdapterForNode(this).isLiquidBoilerRecipe(this);
    }

    public void syncSteamInputSlot(SteamMode oldMode, SteamMode newMode) {
        ModAdapterRegistry.getAdapterForNode(this).onSteamModeChanged(this, oldMode, newMode);
    }

    public int getRecipeTemperature() {
        return properties.get(NodeProperties.EBF_TEMPERATURE);
    }

    public void setRecipeTemperature(int recipeTemperature) {
        properties.set(NodeProperties.EBF_TEMPERATURE, Math.max(0, recipeTemperature));
    }

    public long getEuToStart() {
        return properties.get(NodeProperties.FUSION_START_EU);
    }

    public void setEuToStart(long euToStart) {
        properties.set(NodeProperties.FUSION_START_EU, Math.max(0L, euToStart));
        if (isFusion()) {
            GTVoltageTier minFusionTier = getMinFusionVoltageTier();
            if (targetTier.ordinal() < minFusionTier.ordinal()) {
                targetTier = minFusionTier;
            }
        }
    }

    public boolean isFusion() {
        return getEuToStart() > 0 || getRequiredReflectorTier() > 0
                || (recipeCategoryId != null && recipeCategoryId.getPath().contains("fusion"))
                || (machineIcon != null && machineIcon.getPath().contains("fusion"))
                || (name != null && name.toLowerCase(Locale.ROOT).contains("fusion"));
    }

    public int getFusionTier() {
        long startEU = getEuToStart();
        if (startEU <= 160_000_000L) return 1;
        if (startEU <= 320_000_000L) return 2;
        return 3;
    }

    public GTVoltageTier getMinFusionVoltageTier() {
        int fTier = getFusionTier();
        return fTier == 1 ? GTVoltageTier.LuV : (fTier == 2 ? GTVoltageTier.ZPM : GTVoltageTier.UV);
    }

    public NodeThreadingConfig getThreadingConfig() {
        if (threadingConfig == null) {
            threadingConfig = new NodeThreadingConfig();
        }
        return threadingConfig;
    }

    public void setThreadingConfig(NodeThreadingConfig threadingConfig) {
        this.threadingConfig = threadingConfig;
    }

    public boolean hasThreading() {
        if (threadingConfig != null && threadingConfig.isActive()) return true;
        return MultiblockDetector.getMaxHelixCount(this) > 0;
    }

    public int getRequiredReflectorTier() {
        return properties.get(NodeProperties.REQUIRED_REFLECTOR_TIER);
    }

    public void setRequiredReflectorTier(int tier) {
        properties.set(NodeProperties.REQUIRED_REFLECTOR_TIER, Math.max(0, tier));
    }

    public int getInstalledReflectorTier() {
        for (MachineAddon a : addons) {
            if (a.getCategory() == MachineAddon.Category.REFLECTOR) {
                return a.getReflectorTier();
            }
        }
        return 0;
    }

    public boolean hasValidReflector() {
        int req = getRequiredReflectorTier();
        if (req <= 0) return true;
        return getInstalledReflectorTier() >= req;
    }

    public boolean isOperational() {
        return isOperational(null);
    }

    public boolean isOperational(FlowGraph graph) {
        if (isReroute) return true;
        if (!hasValidReflector()) return false;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        return adapter != null ? adapter.validateNode(this, graph, null) : true;
    }

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public void setMultiblock(boolean multiblock) {
        this.isMultiblock = multiblock;
    }

    public boolean hasMultiblockOption() {
        if (isModule()) return false;
        if (isMultiblock() || canUseCoils()) return true;
        if (recipeCategoryId != null && CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId).hasMultiblockOption()) {
            return true;
        }
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                return true;
            }
        }
        return false;
    }

    public ResourceLocation getMultiblockWorkstation() {
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                return ws;
            }
        }
        return null;
    }

    public ResourceLocation getSingleblockWorkstation() {
        for (ResourceLocation ws : availableWorkstations) {
            if (!MultiblockDetector.isMultiblock(ws)) {
                return ws;
            }
        }
        return machineIcon;
    }

    public boolean canUseCoils() {
        if (getRecipeTemperature() > 0) return true;
        if (recipeCategoryId != null && CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId).canUseCoils()) return true;
        if (machineIcon != null && MultiblockDetector.isCoilMultiblock(machineIcon)) return true;
        if (recipeCategoryId != null && MultiblockDetector.isCoilRecipeCategory(recipeCategoryId)) return true;
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isCoilMultiblock(ws)) return true;
        }
        return false;
    }

    public boolean canUseMultiblockTraits() {
        if (isMultiblock() || hasMultiblockOption() || canUseCoils()) return true;
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) return true;
        }
        return false;
    }

    // =========================================================================
    // Create Kinetic Machine Properties
    // =========================================================================

    public int getRpm() {
        int cur = properties.get(NodeProperties.KINETIC_RPM);
        return cur > 0 ? cur : 32;
    }

    public void setRpm(int rpm) {
        properties.set(NodeProperties.KINETIC_RPM, Math.max(1, Math.min(256, rpm)));
    }

    public void cycleRpm(int direction) {
        int cur = getRpm();
        int closestIdx = 3;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < STANDARD_RPMS.length; i++) {
            int diff = Math.abs(STANDARD_RPMS[i] - cur);
            if (diff < minDiff) {
                minDiff = diff;
                closestIdx = i;
            }
        }
        int nextIdx = closestIdx + direction;
        if (nextIdx < 0) nextIdx = STANDARD_RPMS.length - 1;
        else if (nextIdx >= STANDARD_RPMS.length) nextIdx = 0;
        setRpm(STANDARD_RPMS[nextIdx]);
    }

    public boolean isCreateMachine() {
        if (energyType == EnergyType.KINETIC_SU) return true;
        if (machineIcon != null && (machineIcon.getNamespace().equals("create") || machineIcon.getNamespace().equals("createaddition") || machineIcon.getNamespace().equals("create_new_age"))) return true;
        if (recipeCategoryId != null && (recipeCategoryId.getNamespace().equals("create") || recipeCategoryId.getNamespace().equals("createaddition") || recipeCategoryId.getNamespace().equals("create_new_age"))) return true;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null && (ws.getNamespace().equals("create") || ws.getNamespace().equals("createaddition") || ws.getNamespace().equals("create_new_age"))) return true;
        }
        return false;
    }

    // =========================================================================
    // GT Turbine Properties & Delegations
    // =========================================================================

    public int getRotorEfficiency() {
        return properties.get(NodeProperties.TURBINE_ROTOR_EFFICIENCY);
    }

    public void setRotorEfficiency(int rotorEfficiency) {
        properties.set(NodeProperties.TURBINE_ROTOR_EFFICIENCY, Math.max(1, Math.min(100000, rotorEfficiency)));
    }

    public int getRotorPower() {
        return properties.get(NodeProperties.TURBINE_ROTOR_POWER);
    }

    public void setRotorPower(int rotorPower) {
        properties.set(NodeProperties.TURBINE_ROTOR_POWER, Math.max(1, Math.min(1000000, rotorPower)));
    }

    public String getRotorName() {
        return properties.get(NodeProperties.TURBINE_ROTOR_NAME);
    }

    public void setRotorName(String rotorName) {
        properties.set(NodeProperties.TURBINE_ROTOR_NAME, rotorName != null ? rotorName : "Standard (100%)");
    }

    public GTVoltageTier getTurbineBaseTier() {
        return GTTurbineHelper.getTurbineBaseTier(this);
    }

    public int getTurbineHolderEfficiencyBonus() {
        return GTTurbineHelper.getTurbineHolderEfficiencyBonus(this);
    }

    public double getTurbineBaseProduction() {
        return GTTurbineHelper.getTurbineBaseProduction(this);
    }

    public boolean hasRotorAddon() {
        return GTTurbineHelper.hasRotorAddon(this);
    }

    public boolean isLargeTurbine() {
        return GTTurbineHelper.isLargeTurbine(this);
    }

    public boolean isTurbine() {
        return GTTurbineHelper.isTurbine(this);
    }

    public double getNodeRotorHolderBaseCapacity(GTVoltageTier tier) {
        return GTTurbineHelper.getNodeRotorHolderBaseCapacity(this, tier);
    }

    public double getNodeRotorHolderMaxEUt(GTVoltageTier tier, int rotorPower) {
        return GTTurbineHelper.getNodeRotorHolderMaxEUt(this, tier, rotorPower);
    }

    public double getGeneratorMaxEUt() {
        return GTTurbineHelper.getGeneratorMaxEUt(this);
    }

    public int getEffectiveTurbineParallel() {
        return GTTurbineHelper.getEffectiveTurbineParallel(this);
    }

    public void autoCalculateTurbineParallel() {
        ModAdapterRegistry.getAdapterForNode(this).autoTuneParallel(this);
    }

    // =========================================================================
    // Recipe Calculations & Flow Analysis (Delegated to IModAdapter)
    // =========================================================================

    public int getTierDelta() {
        GTVoltageTier base = recipeTier;
        if (isFusion()) {
            GTVoltageTier minFusionTier = getMinFusionVoltageTier();
            if (minFusionTier.ordinal() > base.ordinal()) {
                base = minFusionTier;
            }
        }
        return Math.max(0, targetTier.ordinal() - base.ordinal());
    }

    public OverclockMode.OverclockResult getOverclockResult() {
        return ModAdapterRegistry.getAdapterForNode(this).computeOverclock(this, targetTier, isGenerator);
    }

    public double getEffectiveDurationSeconds() {
        return getOverclockResult().durationTicks() / 20.0;
    }

    public int getTotalParallel() {
        return ModAdapterRegistry.getAdapterForNode(this).computeEffectiveParallel(this);
    }

    public double getSingleMachineEUt() {
        return ModAdapterRegistry.getAdapterForNode(this).computeSingleMachinePower(this);
    }

    public double getTotalEUt() {
        if (!isOperational()) return 0.0;
        if (steamMode != null && steamMode.isSteam()) return 0.0;
        return getSingleMachineEUt() * machineCount;
    }

    public double getEffectiveTotalEUt() {
        return getTotalEUt() * efficiency;
    }

    public double getNominalCyclesPerSecond() {
        return getOverclockResult().getCyclesPerSecond() * machineCount * getTotalParallel();
    }

    public double getCyclesPerSecond() {
        return isOperational() ? getNominalCyclesPerSecond() : 0.0;
    }

    public double getEffectiveCyclesPerSecond() {
        return getCyclesPerSecond() * efficiency;
    }

    // =========================================================================
    // I/O Slot Flow Rates
    // =========================================================================

    public Map<IngredientStack, Double> calculateInputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getCyclesPerSecond();
        double fuelEnergyMult = MachineAddon.isThermalMachine(this) ? Math.max(0.01, getCombinedDurationMultiplier()) : 1.0;
        for (IngredientStack in : inputs) {
            double r;
            if (in.isStressUnit()) {
                r = isGenerator() ? (in.getAmount() * cps) : getTotalEUt();
            } else {
                double amount = in.getAmount();
                if (in.isFluid() && MachineAddon.isThermalMachine(this) && in.getId() != null && in.getId().getPath().contains("water")) {
                    amount *= fuelEnergyMult;
                }
                r = amount * cps;
            }
            boolean merged = false;
            for (Map.Entry<IngredientStack, Double> entry : rates.entrySet()) {
                if (entry.getKey().equals(in)) {
                    entry.setValue(entry.getValue() + r);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                rates.put(in, r);
            }
        }
        return rates;
    }

    public double getInputSlotRate(int index, boolean effective) {
        if (index < 0 || index >= inputs.size()) return 0.0;
        IngredientStack in = inputs.get(index);
        if (in.isStressUnit()) {
            return isGenerator() ? (in.getAmount() * (effective ? getEffectiveCyclesPerSecond() : getNominalCyclesPerSecond())) : (getTotalEUt() * (effective ? getEfficiency() : 1.0));
        }
        double amount = in.getAmount();
        if (in.isFluid() && MachineAddon.isThermalMachine(this) && in.getId() != null && in.getId().getPath().contains("water")) {
            amount *= Math.max(0.01, getCombinedDurationMultiplier());
        }
        double cps = effective ? getEffectiveCyclesPerSecond() : getNominalCyclesPerSecond();
        return amount * cps;
    }

    public double getOutputSlotRate(int index, boolean effective) {
        if (index < 0 || index >= outputs.size()) return 0.0;
        IngredientStack out = outputs.get(index);
        double amount = out.getExpectedAmount(getTierDelta());
        if (out.isFluid() && MachineAddon.isThermalMachine(this) && out.getId() != null && out.getId().getPath().contains("steam")) {
            amount *= Math.max(0.01, getCombinedDurationMultiplier());
        }
        double cps = effective ? getEffectiveCyclesPerSecond() : getCyclesPerSecond();
        return amount * cps;
    }

    public double getSingleOutputExpectedAmount(int index) {
        if (index < 0 || index >= outputs.size()) return 0.0;
        return outputs.get(index).getExpectedAmount(getTierDelta());
    }

    public Map<IngredientStack, Double> calculateOutputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getCyclesPerSecond();
        int tierDelta = getTierDelta();
        double fuelEnergyMult = MachineAddon.isThermalMachine(this) ? Math.max(0.01, getCombinedDurationMultiplier()) : 1.0;
        for (IngredientStack out : outputs) {
            double amount = out.getExpectedAmount(tierDelta);
            if (out.isFluid() && MachineAddon.isThermalMachine(this) && out.getId() != null && out.getId().getPath().contains("steam")) {
                amount *= fuelEnergyMult;
            }
            double r = amount * cps;
            boolean merged = false;
            for (Map.Entry<IngredientStack, Double> entry : rates.entrySet()) {
                if (entry.getKey().equals(out)) {
                    entry.setValue(entry.getValue() + r);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                rates.put(out, r);
            }
        }
        return rates;
    }

    public Map<IngredientStack, Double> calculateEffectiveInputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getEffectiveCyclesPerSecond();
        double fuelEnergyMult = MachineAddon.isThermalMachine(this) ? Math.max(0.01, getCombinedDurationMultiplier()) : 1.0;
        for (IngredientStack in : inputs) {
            double amount = in.getAmount();
            if (in.isFluid() && MachineAddon.isThermalMachine(this) && in.getId() != null && in.getId().getPath().contains("water")) {
                amount *= fuelEnergyMult;
            }
            double r = amount * cps;
            boolean merged = false;
            for (Map.Entry<IngredientStack, Double> entry : rates.entrySet()) {
                if (entry.getKey().equals(in)) {
                    entry.setValue(entry.getValue() + r);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                rates.put(in, r);
            }
        }
        return rates;
    }

    public Map<IngredientStack, Double> calculateEffectiveOutputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getEffectiveCyclesPerSecond();
        int tierDelta = getTierDelta();
        double fuelEnergyMult = MachineAddon.isThermalMachine(this) ? Math.max(0.01, getCombinedDurationMultiplier()) : 1.0;
        for (IngredientStack out : outputs) {
            double amount = out.getExpectedAmount(tierDelta);
            if (out.isFluid() && MachineAddon.isThermalMachine(this) && out.getId() != null && out.getId().getPath().contains("steam")) {
                amount *= fuelEnergyMult;
            }
            double r = amount * cps;
            boolean merged = false;
            for (Map.Entry<IngredientStack, Double> entry : rates.entrySet()) {
                if (entry.getKey().equals(out)) {
                    entry.setValue(entry.getValue() + r);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                rates.put(out, r);
            }
        }
        return rates;
    }

    public double calculateSingleMachineOutputRate(IngredientStack out) {
        if (out == null || !isOperational()) return 0.0;
        double singleCps = getOverclockResult().getCyclesPerSecond() * getTotalParallel();
        return out.getExpectedAmount(getTierDelta()) * singleCps;
    }

    public double calculateSingleMachineInputRate(IngredientStack in) {
        if (in == null) return 0.0;
        if (in.isStressUnit()) {
            return isGenerator() ? in.getAmount() : getSingleMachineEUt();
        }
        double singleCps = getOverclockResult().getCyclesPerSecond() * getTotalParallel();
        return in.getAmount() * singleCps;
    }

    // =========================================================================
    // NBT Serialization (Delegated to RecipeNodeSerializer)
    // =========================================================================

    public CompoundTag serializeNBT() {
        return RecipeNodeSerializer.serialize(this);
    }

    public static RecipeNode deserializeNBT(CompoundTag tag) {
        return RecipeNodeSerializer.deserialize(tag);
    }
}
