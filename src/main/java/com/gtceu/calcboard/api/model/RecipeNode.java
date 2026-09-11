package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Pure domain model representing a recipe node on the calculator canvas.
 * Encapsulates graph attributes, I/O ingredient streams, and delegates mod-specific
 * physics, rate integration, and hardware resolutions to registered SPI adapters and calculators.
 */
public class RecipeNode {
    private String id;
    private String name;
    private ResourceLocation machineIcon;
    private boolean hasCustomName = false;

    private double baseDurationTicks;
    private double baseEUt;
    private GTVoltageTier recipeTier;

    // User-adjustable parameters
    private GTVoltageTier targetTier;
    private double machineCount;
    private int parallel;
    private OverclockMode overclockMode;
    private int customParallel = 0;

    // Inputs and outputs
    private final List<IngredientStack> inputs = new ArrayList<>();
    private final List<IngredientStack> outputs = new ArrayList<>();
    private final NodePortVisibility portVisibility = new NodePortVisibility();

    // Master base node for Auto Ratio
    private boolean isBaseNode = false;
    private boolean isGenerator = false;
    private final NodePropertyStore properties = new NodePropertyStore();
    private transient OverclockMode.OverclockResult cachedOverclockResult = null;
    private transient boolean overclockDirty = true;
    private transient IModAdapter cachedModAdapter = null;
    private transient int cachedTotalParallel = -1;
    private transient double cachedNominalCps = -1.0;
    private transient double cachedSingleMachinePower = -1.0;
    private transient Boolean cachedOperational = null;
    private transient FlowGraph cachedOperationalGraph = null;
    private transient FlowGraph parentGraph = null;

    // Canvas position & Dimensions
    private double posX;
    private double posY;
    private int cardWidth = 245;
    private int cardHeight = 0;

    // Module / Subgraph Abstraction
    private boolean isModule = false;
    private FlowGraph subGraph = null;
    private String subPageId = "";
    private final List<String> inputPinNodeIds = new ArrayList<>();
    private final List<String> outputPinNodeIds = new ArrayList<>();
    private int containedMachineCount = 0;
    private double efficiency = 1.0;
    private EnergyType energyType = null;

    // Reroute / Junction Node Abstraction (RFC-001, RFC-012)
    private boolean isReroute = false;
    private SupplyMode supplyMode = SupplyMode.NONE;
    private double externalSupplyRate = 0.0;
    private double externalDrainRate = 0.0;

    // Horizontal Flip / Directionality (Left-to-Right vs Right-to-Left)
    private boolean isFlipped = false;

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

    private final NodePortOriginManager portOriginManager = new NodePortOriginManager();

    // Hardware Addons, Hatches, and Augments
    private final List<MachineAddon> addons = new ArrayList<>();
    private final List<ResourceLocation> availableWorkstations = new ArrayList<>();
    private ResourceLocation recipeCategoryId;
    private boolean isMultiblock = false;

    public RecipeNode(String id, String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.baseDurationTicks = Math.max(0.0, baseDurationTicks);
        this.baseEUt = Math.abs(baseEUt);
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.getTierForVoltage((long) baseEUt);
        this.targetTier = this.recipeTier;
        this.machineCount = 1.0;
        this.parallel = 1;
        this.overclockMode = OverclockMode.STANDARD;
        this.isGenerator = false;
        this.efficiency = 1.0;
        this.isMultiblock = false;
        this.properties.setChangeListener(this::markOverclockDirty);
    }

    public static RecipeNode create(ResourceLocation machineId, String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        RecipeNode node = new RecipeNode(UUID.randomUUID().toString(), name, baseDurationTicks, baseEUt, recipeTier);
        if (machineId != null) {
            node.setMachineIcon(machineId);
            node.setRecipeCategoryId(machineId);
            node.getAvailableWorkstations().add(machineId);
        }
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.RecipeNodeEvent.Created(node));
        } catch (Throwable ignored) {}
        return node;
    }

    public static RecipeNode create(String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        ResourceLocation loc = NodeWorkstationResolver.resolveLocationFromName(name);
        return create(loc, name, baseDurationTicks, baseEUt, recipeTier);
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
        return deserializeNBT(serializeNBT());
    }

    public boolean isFlipped() { return isFlipped; }
    public void setFlipped(boolean flipped) { this.isFlipped = flipped; }
    public void toggleFlipped() { this.isFlipped = !this.isFlipped; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRawName() { return name != null ? name : ""; }
    public boolean hasCustomName() { return hasCustomName; }
    public void setHasCustomName(boolean hasCustomName) { this.hasCustomName = hasCustomName; }

    public String getMachineDisplayName() {
        if (machineIcon != null) {
            return NodeWorkstationResolver.getWorkstationDisplayName(machineIcon);
        }
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return "Unknown Machine";
    }

    public ResourceLocation getMachineIcon() { return machineIcon; }

    public void setMachineIcon(ResourceLocation machineIcon) {
        if (Objects.equals(this.machineIcon, machineIcon)) return;
        invalidateModAdapterCache();
        ResourceLocation oldIcon = this.machineIcon;
        this.machineIcon = machineIcon;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        if (adapter != null) {
            adapter.onMachineIconChanged(this, oldIcon, machineIcon);
        }
        markOverclockDirty();
    }

    public double getBaseDurationTicks() { return baseDurationTicks; }
    public void setBaseDurationTicks(double baseDurationTicks) {
        this.baseDurationTicks = Math.max(0.0, baseDurationTicks);
        markOverclockDirty();
    }

    public double getBaseEUt() { return baseEUt; }
    public void setBaseEUt(double baseEUt) {
        this.baseEUt = Math.abs(baseEUt);
        markOverclockDirty();
    }

    public GTVoltageTier getRecipeTier() { return recipeTier; }
    public void setRecipeTier(GTVoltageTier recipeTier) {
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.ULV;
        markOverclockDirty();
    }

    public GTVoltageTier getTargetTier() { return targetTier; }

    public void setTargetTier(GTVoltageTier targetTier) {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        this.targetTier = adapter.sanitizeTargetTier(this, targetTier);
        if (!isMultiblock && !isLargeTurbine() && (getSteamMode() == null || !getSteamMode().isSteam()) && (machineIcon == null || !MultiblockDetector.isMultiblock(machineIcon))) {
            ResourceLocation ws = getWorkstationForTier(this.targetTier);
            if (ws != null) {
                setMachineIcon(ws);
            }
        }
        if (isTurbine()) {
            autoCalculateTurbineParallel();
        }
        markOverclockDirty();
    }

    public ResourceLocation getWorkstationForTier(GTVoltageTier tier) {
        return NodeWorkstationResolver.getWorkstationForTier(this, tier);
    }

    public ResourceLocation getWorkstationForTierFromList(GTVoltageTier tier) {
        return NodeWorkstationResolver.getWorkstationForTierFromList(this, tier);
    }

    public double getMachineCount() {
        return machineCount;
    }

    public void setMachineCount(double machineCount) {
        this.machineCount = Math.max(0.01, machineCount);
        markOverclockDirty();
    }

    public int getParallel() { return parallel; }
    public void setParallel(int parallel) {
        this.parallel = Math.max(1, parallel);
        markOverclockDirty();
    }

    public OverclockMode getOverclockMode() { return overclockMode; }
    public void setOverclockMode(OverclockMode overclockMode) {
        this.overclockMode = overclockMode != null ? overclockMode : OverclockMode.STANDARD;
        markOverclockDirty();
    }

    public boolean isBaseNode() { return isBaseNode; }
    public void setBaseNode(boolean baseNode) { this.isBaseNode = baseNode; }

    public boolean isGenerator() {
        if (MultiblockDetector.isCoilMultiblock(machineIcon)
                || MultiblockDetector.isCoilRecipeCategory(recipeCategoryId)) {
            return false;
        }
        return isGenerator;
    }

    public void setGenerator(boolean generator) {
        this.isGenerator = generator;
        markOverclockDirty();
    }

    public NodePropertyStore getProperties() { return properties; }

    public EnergyType getEnergyType() {
        if (energyType != null) return energyType;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        return adapter != null ? adapter.getEnergyType(this) : EnergyType.ELECTRIC_EU;
    }

    public EnergyType getEnergyTypeOverride() { return energyType; }
    public void setEnergyType(EnergyType energyType) {
        this.energyType = energyType;
        markOverclockDirty();
    }

    public double getPosX() { return posX; }
    public void setPosX(double posX) { this.posX = posX; }
    public double getPosY() { return posY; }
    public void setPosY(double posY) { this.posY = posY; }
    public void setPos(double posX, double posY) { this.posX = posX; this.posY = posY; }

    public int getCardWidth() { return (isReroute || isBoundaryPin()) ? 32 : Math.max(245, Math.min(500, cardWidth)); }
    public void setCardWidth(int cardWidth) { this.cardWidth = (isReroute || isBoundaryPin()) ? 32 : Math.max(245, Math.min(500, cardWidth)); }
    public int getCardHeight() { return (isReroute || isBoundaryPin()) ? 32 : Math.max(0, Math.min(600, cardHeight)); }
    public void setCardHeight(int cardHeight) { this.cardHeight = (isReroute || isBoundaryPin()) ? 32 : Math.max(0, Math.min(600, cardHeight)); }

    public double getEfficiency() { return efficiency; }
    public void setEfficiency(double efficiency) { this.efficiency = Math.max(0.0, Math.min(1.0, efficiency)); }

    public boolean isReroute() { return isReroute; }
    public void setReroute(boolean reroute) { this.isReroute = reroute; }

    public SupplyMode getSupplyMode() { return supplyMode != null ? supplyMode : SupplyMode.NONE; }
    public void setSupplyMode(SupplyMode supplyMode) { this.supplyMode = supplyMode != null ? supplyMode : SupplyMode.NONE; }

    public boolean isExternalSupply() { return isReroute && getSupplyMode().isExternal(); }
    public boolean isInfiniteSupply() { return isReroute && getSupplyMode() == SupplyMode.INFINITE; }
    public boolean isVoidSink() { return isReroute && getSupplyMode() == SupplyMode.VOID_SINK; }
    public boolean isFixedDrain() { return isReroute && getSupplyMode() == SupplyMode.FIXED_DRAIN; }

    public double getExternalSupplyRate() { return externalSupplyRate; }
    public void setExternalSupplyRate(double rate) { this.externalSupplyRate = Math.max(0.0, rate); }
    public double getExternalDrainRate() { return externalDrainRate; }
    public void setExternalDrainRate(double rate) { this.externalDrainRate = Math.max(0.0, rate); }

    public int getCustomParallel() { return customParallel; }
    public void setCustomParallel(int customParallel) {
        this.customParallel = Math.max(0, customParallel);
        markOverclockDirty();
    }

    public void bindRerouteIngredient(IngredientStack stack) { NodeJunctionHelper.bindRerouteIngredient(this, stack); }
    public void unbindRerouteIngredient() { NodeJunctionHelper.unbindRerouteIngredient(this); }
    public IngredientStack getRerouteIngredient() { return !outputs.isEmpty() ? outputs.get(0) : null; }

    public double getTargetBatchAmount() { return properties.get(NodeProperties.TARGET_BATCH_AMOUNT); }
    public void setTargetBatchAmount(double amount) { properties.set(NodeProperties.TARGET_BATCH_AMOUNT, Math.max(0.0, amount)); }
    public boolean hasTargetBatch() { return getTargetBatchAmount() > 0.0001; }

    public boolean isJunctionBuffer() { return isReroute && properties.get(NodeProperties.JUNCTION_IS_BUFFER); }
    public void setJunctionBuffer(boolean isBuffer) { properties.set(NodeProperties.JUNCTION_IS_BUFFER, isBuffer); }
    public double getJunctionBufferSize() { return properties.get(NodeProperties.JUNCTION_BUFFER_SIZE); }
    public void setJunctionBufferSize(double size) { properties.set(NodeProperties.JUNCTION_BUFFER_SIZE, Math.max(0.0, size)); }
    public double getJunctionChargeDuration(FlowGraph graph) { return NodeJunctionHelper.getJunctionChargeDuration(this, graph); }

    public com.gtceu.calcboard.api.type.FlowSplitMode getJunctionSplitMode() { return properties.get(NodeProperties.JUNCTION_SPLIT_MODE); }
    public void setJunctionSplitMode(com.gtceu.calcboard.api.type.FlowSplitMode mode) {
        properties.set(NodeProperties.JUNCTION_SPLIT_MODE, mode != null ? mode : com.gtceu.calcboard.api.type.FlowSplitMode.PROPORTIONAL);
    }

    public double getTargetBatchTimeSec() {
        return properties.get(NodeProperties.TARGET_BATCH_TIME_SEC);
    }

    public void setTargetBatchTimeSec(double seconds) {
        properties.set(NodeProperties.TARGET_BATCH_TIME_SEC, Math.max(0.0, seconds));
    }

    public boolean isOutputPort() {
        return properties.get(NodeProperties.IS_OUTPUT_PORT);
    }

    public void setOutputPort(boolean isOutput) {
        properties.set(NodeProperties.IS_OUTPUT_PORT, isOutput);
    }

    public boolean isModule() {
        return isModule;
    }

    public void setModule(boolean module) {
        this.isModule = module;
    }

    public boolean isBoundaryPin() {
        return false;
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

    public String getSubPageId() {
        return subPageId != null ? subPageId : "";
    }

    public void setSubPageId(String subPageId) {
        this.subPageId = subPageId != null ? subPageId : "";
    }

    public List<String> getInputPinNodeIds() {
        return inputPinNodeIds;
    }

    public List<String> getOutputPinNodeIds() {
        return outputPinNodeIds;
    }

    public com.gtceu.calcboard.api.storage.BoardPage getDedicatedSubPage() {
        if (subPageId == null || subPageId.isEmpty()) return null;
        return com.gtceu.calcboard.api.storage.BoardManager.getInstance().getPage(subPageId).orElse(null);
    }

    public List<List<PortOrigin>> getModuleInputOrigins() {
        return portOriginManager.getInputOrigins();
    }

    public List<List<PortOrigin>> getModuleOutputOrigins() {
        return portOriginManager.getOutputOrigins();
    }

    public NodePortOriginManager getPortOriginManager() {
        return portOriginManager;
    }

    public List<IngredientStack> getInputs() { return inputs; }
    public List<IngredientStack> getOutputs() { return outputs; }
    public void addInput(IngredientStack stack) { inputs.add(stack); markOverclockDirty(); }
    public void addOutput(IngredientStack stack) { outputs.add(stack); markOverclockDirty(); }

    public NodePortVisibility getPortVisibility() { return portVisibility; }
    public boolean isInputPortHidden(int index) { return portVisibility.isInputPortHidden(index); }
    public boolean isOutputPortHidden(int index) { return portVisibility.isOutputPortHidden(index); }
    public void hideInputPort(int index) { portVisibility.hideInputPort(index, inputs.size()); }
    public void unhideInputPort(int index) { portVisibility.unhideInputPort(index); }
    public void hideOutputPort(int index) { portVisibility.hideOutputPort(index, outputs.size()); }
    public void unhideOutputPort(int index) { portVisibility.unhideOutputPort(index); }
    public void unhideAllPorts() { portVisibility.unhideAllPorts(); }
    public boolean isOutputPortVoided(int index) { return portVisibility.isOutputPortVoided(index); }
    public void setOutputPortVoided(int index, boolean voided) { portVisibility.setOutputPortVoided(index, voided, outputs.size()); }
    public void clearVoidedOutputPorts() { portVisibility.clearVoidedOutputPorts(); }
    public java.util.Set<Integer> getVoidedOutputIndices() { return portVisibility.getVoidedOutputIndices(); }
    public int getVoidedOutputCount() { return portVisibility.getVoidedOutputCount(); }
    public java.util.Set<Integer> getHiddenInputIndices() { return portVisibility.getHiddenInputIndices(); }
    public java.util.Set<Integer> getHiddenOutputIndices() { return portVisibility.getHiddenOutputIndices(); }
    public int getHiddenInputCount() { return portVisibility.getHiddenInputCount(); }
    public int getHiddenOutputCount() { return portVisibility.getHiddenOutputCount(); }
    public int getTotalHiddenCount() { return portVisibility.getTotalHiddenCount(); }
    public List<Integer> getVisibleInputIndices() { return portVisibility.getVisibleInputIndices(inputs.size()); }
    public List<Integer> getVisibleOutputIndices() { return portVisibility.getVisibleOutputIndices(outputs.size()); }

    public List<ResourceLocation> getAvailableWorkstations() {
        if (availableWorkstations.isEmpty() && recipeCategoryId != null) {
            com.gtceu.calcboard.api.catalog.CategoryCapability cap = com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId);
            if (cap != null && cap.availableWorkstations() != null && !cap.availableWorkstations().isEmpty()) {
                for (ResourceLocation ws : cap.availableWorkstations()) {
                    if (ws != null && !availableWorkstations.contains(ws)) {
                        availableWorkstations.add(ws);
                    }
                }
            }
        }
        return availableWorkstations;
    }

    public void setAvailableWorkstations(List<ResourceLocation> availableWorkstations) {
        this.availableWorkstations.clear();
        if (availableWorkstations != null) this.availableWorkstations.addAll(availableWorkstations);
    }

    public ResourceLocation getRecipeCategoryId() { return recipeCategoryId; }

    public void setRecipeCategoryId(ResourceLocation recipeCategoryId) {
        if (!Objects.equals(this.recipeCategoryId, recipeCategoryId)) {
            invalidateModAdapterCache();
            this.recipeCategoryId = recipeCategoryId;
            this.availableWorkstations.clear();
            markOverclockDirty();
        }
    }

    public static boolean isMultiblockWorkstation(ResourceLocation ws) {
        return NodeWorkstationResolver.isMultiblockWorkstation(ws);
    }

    public List<MachineAddon> getAddons() { return addons; }
    public void addAddon(MachineAddon addon) { NodeAddonHelper.addAddon(this, addons, addon); markOverclockDirty(); }
    public void removeSingleAddon(String addonId) { NodeAddonHelper.removeSingleAddon(this, addons, addonId); markOverclockDirty(); }
    public void removeAddon(String addonId) { addons.removeIf(a -> a.getId().equals(addonId)); markOverclockDirty(); }
    public boolean removeOneAddon(String addonId) {
        boolean removed = NodeAddonHelper.removeOneAddon(addons, addonId);
        if (removed) markOverclockDirty();
        return removed;
    }
    public void clearAddons() { addons.clear(); markOverclockDirty(); }
    public double getCombinedDurationMultiplier() { return NodeAddonHelper.getCombinedDurationMultiplier(addons); }
    public double getCombinedEutMultiplier() { return NodeAddonHelper.getCombinedEutMultiplier(addons); }
    public int getCombinedParallelMultiplier() { return NodeAddonHelper.getCombinedParallelMultiplier(addons); }
    public boolean hasPowerConstantAddon() { return NodeAddonHelper.hasPowerConstantAddon(addons); }

    public SteamMode getSteamMode() { return properties.get(NodeProperties.STEAM_MODE); }
    public void setSteamMode(SteamMode steamMode) { NodeSteamHelper.setSteamMode(this, steamMode); }
    public boolean supportsSteamMode() { return NodeSteamHelper.supportsSteamMode(this); }
    public boolean isLiquidBoilerRecipe() { return ModAdapterRegistry.getAdapterForNode(this).isLiquidBoilerRecipe(this); }
    public void syncSteamInputSlot(SteamMode oldMode, SteamMode newMode) { NodeSteamHelper.syncSteamInputSlot(this, oldMode, newMode); }

    public int getRecipeTemperature() { return NodeHardwarePropertyHelper.getRecipeTemperature(properties); }
    public void setRecipeTemperature(int recipeTemperature) { NodeHardwarePropertyHelper.setRecipeTemperature(properties, recipeTemperature); }
    public int getBoilerThrottle() { return NodeHardwarePropertyHelper.getBoilerThrottle(properties); }
    public void setBoilerThrottle(int throttle) { NodeHardwarePropertyHelper.setBoilerThrottle(properties, throttle); }
    public long getEuToStart() { return NodeHardwarePropertyHelper.getEuToStart(properties); }
    public void setEuToStart(long euToStart) { NodeHardwarePropertyHelper.setEuToStart(this, properties, euToStart); }
    public boolean isFusion() { return ModAdapterRegistry.getAdapterForNode(this).isFusion(this); }
    public int getFusionTier() { return ModAdapterRegistry.getAdapterForNode(this).getFusionTier(this); }
    public GTVoltageTier getMinFusionVoltageTier() { return ModAdapterRegistry.getAdapterForNode(this).getMinFusionVoltageTier(this); }
    public boolean isThreadingAvailable() { return ModAdapterRegistry.getAdapterForNode(this).isThreadingAvailable(this); }
    public boolean isExplicitThreadingMachine() { return machineIcon != null && MultiblockDetector.isThreadingMultiblock(machineIcon); }
    public boolean hasThreading() { return ModAdapterRegistry.getAdapterForNode(this).hasThreading(this); }
    public boolean isThreadingActive() { return hasThreading(); }
    public void setThreadingActive(boolean active) { ModAdapterRegistry.getAdapterForNode(this).setThreadingActive(this, active); }

    public int getRequiredReflectorTier() {
        return RecipeNodeReflectorHelper.getRequiredReflectorTier(properties);
    }

    public void setRequiredReflectorTier(int tier) {
        RecipeNodeReflectorHelper.setRequiredReflectorTier(properties, tier);
    }

    public int getInstalledReflectorTier() {
        return RecipeNodeReflectorHelper.getInstalledReflectorTier(addons);
    }

    public boolean hasValidReflector() {
        return RecipeNodeReflectorHelper.hasValidReflector(properties, addons);
    }

    public FlowGraph getParentGraph() {
        return parentGraph != null ? parentGraph : cachedOperationalGraph;
    }

    public void setParentGraph(FlowGraph parentGraph) {
        this.parentGraph = parentGraph;
        markOperationalDirty();
    }

    public boolean isOperational() {
        return isOperational(null);
    }

    public void markOperationalDirty() {
        this.cachedOperational = null;
        this.cachedOperationalGraph = null;
    }

    public boolean isOperational(FlowGraph graph) {
        if (isReroute) return true;
        if (cachedOperational != null) {
            if (Boolean.FALSE.equals(cachedOperational)) {
                return false;
            }
            if (graph == null || cachedOperationalGraph == graph) {
                return cachedOperational;
            }
        }
        if (!hasValidReflector()) {
            cachedOperational = false;
            cachedOperationalGraph = null;
            return false;
        }
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        boolean op = adapter != null ? adapter.validateNode(this, graph, null) : true;
        if (graph != null || !op) {
            cachedOperational = op;
            cachedOperationalGraph = graph;
        }
        return op;
    }

    public List<Component> getOperationalWarnings(FlowGraph graph) {
        return NodeMultiblockHelper.getOperationalWarnings(this, graph);
    }

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public void setMultiblock(boolean multiblock) {
        if (this.isMultiblock == multiblock) return;
        this.isMultiblock = multiblock;
        NodeMultiblockHelper.configureMultiblock(this, multiblock);
        markOverclockDirty();
    }

    public boolean hasMultiblockOption() { return NodeWorkstationResolver.hasMultiblockOption(this); }
    public List<ResourceLocation> getMultiblockWorkstations() { return NodeWorkstationResolver.getMultiblockWorkstations(this); }
    public ResourceLocation getMultiblockWorkstation() { return NodeWorkstationResolver.getMultiblockWorkstation(this); }
    public ResourceLocation getSingleblockWorkstation() { return NodeWorkstationResolver.getSingleblockWorkstation(this); }
    public boolean canUseCoils() { return NodeWorkstationResolver.canUseCoils(this); }
    public boolean canUseMultiblockTraits() { return NodeWorkstationResolver.canUseMultiblockTraits(this); }

    public int getRpm() { return NodeHardwarePropertyHelper.getRpm(properties); }
    public void setRpm(int rpm) { NodeHardwarePropertyHelper.setRpm(this, properties, rpm); }
    public int getRotorEfficiency() { return NodeHardwarePropertyHelper.getRotorEfficiency(properties); }
    public void setRotorEfficiency(int rotorEfficiency) { NodeHardwarePropertyHelper.setRotorEfficiency(properties, rotorEfficiency); }
    public int getRotorPower() { return NodeHardwarePropertyHelper.getRotorPower(properties); }
    public void setRotorPower(int rotorPower) { NodeHardwarePropertyHelper.setRotorPower(properties, rotorPower); }
    public String getRotorName() { return NodeHardwarePropertyHelper.getRotorName(properties); }
    public void setRotorName(String rotorName) { NodeHardwarePropertyHelper.setRotorName(properties, rotorName); }

    public boolean isLargeTurbine() { return ModAdapterRegistry.getAdapterForNode(this).isLargeTurbine(this); }
    public boolean isTurbine() { return ModAdapterRegistry.getAdapterForNode(this).isTurbine(this); }
    public double getGeneratorMaxEUt() { return ModAdapterRegistry.getAdapterForNode(this).getGeneratorMaxPower(this); }
    public void autoCalculateTurbineParallel() { ModAdapterRegistry.getAdapterForNode(this).autoTuneParallel(this); }

    public int getTierDelta() {
        if (targetTier == null || recipeTier == null) return 0;
        return ModAdapterRegistry.getAdapterForNode(this).calculateTierDelta(this, targetTier, recipeTier);
    }

    public void markOverclockDirty() {
        this.overclockDirty = true;
        this.cachedOverclockResult = null;
        this.cachedTotalParallel = -1;
        this.cachedNominalCps = -1.0;
        this.cachedSingleMachinePower = -1.0;
        this.cachedModAdapter = null;
        this.cachedOperational = null;
    }

    public IModAdapter getCachedModAdapter() { return cachedModAdapter; }
    public void setCachedModAdapter(IModAdapter adapter) { this.cachedModAdapter = adapter; }
    public void invalidateModAdapterCache() { this.cachedModAdapter = null; }

    public OverclockMode.OverclockResult getOverclockResult() {
        if (overclockDirty || cachedOverclockResult == null) {
            cachedOverclockResult = NodePerformanceHelper.computeOverclockResult(this);
            overclockDirty = false;
        }
        return cachedOverclockResult;
    }

    public double getEffectiveDurationSeconds() { return getOverclockResult().durationTicks() / 20.0; }

    public int getTotalParallel() {
        if (customParallel > 0) return customParallel;
        if (cachedTotalParallel < 1) cachedTotalParallel = NodePerformanceHelper.computeTotalParallel(this);
        return cachedTotalParallel;
    }

    public double getSingleMachineEUt() {
        if (cachedSingleMachinePower < 0.0) cachedSingleMachinePower = NodePerformanceHelper.computeSingleMachinePower(this);
        return cachedSingleMachinePower;
    }

    public double getTotalEUt() { return NodePerformanceHelper.computeTotalEUt(this); }
    public double getEffectiveTotalEUt() { return getTotalEUt() * efficiency; }

    public double getNominalCyclesPerSecond() {
        if (cachedNominalCps < 0.0) cachedNominalCps = NodePerformanceHelper.computeNominalCps(this);
        return cachedNominalCps;
    }

    public double getCyclesPerSecond() { return isOperational() ? getNominalCyclesPerSecond() : 0.0; }
    public double getEffectiveCyclesPerSecond() { return getCyclesPerSecond() * efficiency; }

    public Map<IngredientStack, Double> calculateInputRates() { return NodeRateCalculator.calculateInputRates(this); }
    public double getInputSlotRate(int index, boolean effective) { return NodeRateCalculator.getInputSlotRate(this, index, effective); }
    public double getEffectiveInputChance(int inputIndex) { return NodeRateCalculator.getEffectiveInputChance(this, inputIndex); }
    public double getEffectiveOutputChance(int outputIndex) { return NodeRateCalculator.getEffectiveOutputChance(this, outputIndex); }
    public double getOutputSlotRate(int index, boolean effective) { return NodeRateCalculator.getOutputSlotRate(this, index, effective); }
    public double getSingleOutputExpectedAmount(int index) { return NodeRateCalculator.getSingleOutputExpectedAmount(this, index); }
    public Map<IngredientStack, Double> calculateOutputRates() { return NodeRateCalculator.calculateOutputRates(this); }
    public Map<IngredientStack, Double> calculateEffectiveInputRates() { return calculateEffectiveInputRates(true); }
    public Map<IngredientStack, Double> calculateEffectiveInputRates(boolean postEvent) { return NodeRateCalculator.calculateEffectiveInputRates(this, postEvent); }
    public Map<IngredientStack, Double> calculateEffectiveOutputRates() { return calculateEffectiveOutputRates(true); }
    public Map<IngredientStack, Double> calculateEffectiveOutputRates(boolean postEvent) { return NodeRateCalculator.calculateEffectiveOutputRates(this, postEvent); }
    public double calculateSingleMachineOutputRate(IngredientStack out) { return NodeRateCalculator.calculateSingleMachineOutputRate(this, out); }
    public double calculateSingleMachineInputRate(IngredientStack in) { return NodeRateCalculator.calculateSingleMachineInputRate(this, in); }

    public boolean isCompoundNode() { return RecipeNodeCompoundHelper.isCompoundNode(properties); }
    public boolean isCompoundMaster() { return RecipeNodeCompoundHelper.isCompoundMaster(properties); }
    public String getCompoundGroupId() { return RecipeNodeCompoundHelper.getCompoundGroupId(properties); }
    public int getCompoundLayerIndex() { return RecipeNodeCompoundHelper.getCompoundLayerIndex(properties); }
    public int getCompoundTotalLayers() { return RecipeNodeCompoundHelper.getCompoundTotalLayers(properties); }
    public String getCompoundMasterNodeId() { return RecipeNodeCompoundHelper.getCompoundMasterNodeId(properties); }
    public void setCompoundMetadata(String groupId, int layerIndex, int totalLayers, String masterId) {
        RecipeNodeCompoundHelper.setCompoundMetadata(properties, groupId, layerIndex, totalLayers, masterId);
    }

    public CompoundTag serializeNBT() { return RecipeNodeSerializer.serialize(this); }
    public CompoundTag serializeNBT(Set<FlowGraph> visitedGraphs, int depth) { return RecipeNodeSerializer.serialize(this, visitedGraphs, depth); }
    public static RecipeNode deserializeNBT(CompoundTag tag) { return RecipeNodeSerializer.deserialize(tag); }
}
