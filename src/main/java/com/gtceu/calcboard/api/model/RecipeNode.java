package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.NodeThreadingConfig;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.nbt.CompoundTag;
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
    private final java.util.Set<Integer> hiddenInputIndices = new java.util.LinkedHashSet<>();
    private final java.util.Set<Integer> hiddenOutputIndices = new java.util.LinkedHashSet<>();

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
    private EnergyType energyType = null;

    // Reroute / Junction Node Abstraction (RFC-001, RFC-012)
    private boolean isReroute = false;
    private SupplyMode supplyMode = SupplyMode.NONE;
    private double externalSupplyRate = 0.0;

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

    private final List<List<PortOrigin>> moduleInputOrigins = new ArrayList<>();
    private final List<List<PortOrigin>> moduleOutputOrigins = new ArrayList<>();

    // Hardware Addons, Hatches, and Augments
    private final List<MachineAddon> addons = new ArrayList<>();
    private final List<ResourceLocation> availableWorkstations = new ArrayList<>();
    private ResourceLocation recipeCategoryId;
    private SteamMode steamMode = SteamMode.NONE;
    private boolean isMultiblock = false;
    private NodeThreadingConfig threadingConfig;

    public RecipeNode(String id, String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.baseDurationTicks = Math.max(0.0, baseDurationTicks);
        this.baseEUt = Math.max(0.0, baseEUt);
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.getTierForVoltage((long) baseEUt);
        this.targetTier = this.recipeTier;
        this.machineCount = 1.0;
        this.parallel = 1;
        this.overclockMode = OverclockMode.STANDARD;
        this.isGenerator = false;
        this.efficiency = 1.0;
        this.isMultiblock = false;
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
        ResourceLocation loc = null;
        if (name != null && !name.isEmpty()) {
            if (name.contains(":")) {
                loc = ResourceLocation.tryParse(name.trim());
            } else {
                String base = name.contains(" (") ? name.substring(0, name.indexOf(" (")) : name;
                String sanitized = base.toLowerCase(Locale.ROOT).trim().replace(" ", "_");
                loc = ResourceLocation.tryParse("gtceu:" + sanitized);
            }
        }
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

    public boolean isFlipped() {
        return isFlipped;
    }

    public void setFlipped(boolean flipped) {
        this.isFlipped = flipped;
    }

    public void toggleFlipped() {
        this.isFlipped = !this.isFlipped;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRawName() {
        return name != null ? name : "";
    }

    public String getMachineDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (machineIcon != null) {
            return machineIcon.getPath();
        }
        return "Unknown Machine";
    }

    public ResourceLocation getMachineIcon() {
        return machineIcon;
    }

    public void setMachineIcon(ResourceLocation machineIcon) {
        if (Objects.equals(this.machineIcon, machineIcon)) return;
        ResourceLocation oldIcon = this.machineIcon;
        this.machineIcon = machineIcon;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        if (adapter != null) {
            adapter.onMachineIconChanged(this, oldIcon, machineIcon);
        }
    }

    public double getBaseDurationTicks() {
        return baseDurationTicks;
    }

    public void setBaseDurationTicks(double baseDurationTicks) {
        this.baseDurationTicks = Math.max(0.0, baseDurationTicks);
    }

    public double getBaseEUt() {
        return baseEUt;
    }

    public void setBaseEUt(double baseEUt) {
        this.baseEUt = Math.max(0.0, baseEUt);
    }

    public GTVoltageTier getRecipeTier() {
        return recipeTier;
    }

    public void setRecipeTier(GTVoltageTier recipeTier) {
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.ULV;
    }

    public GTVoltageTier getTargetTier() {
        return targetTier;
    }

    public void setTargetTier(GTVoltageTier targetTier) {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        this.targetTier = adapter.sanitizeTargetTier(this, targetTier);
        if (!isMultiblock && !isTurbine() && (steamMode == null || !steamMode.isSteam()) && (machineIcon == null || !MultiblockDetector.isMultiblock(machineIcon))) {
            ResourceLocation ws = getWorkstationForTier(this.targetTier);
            if (ws != null) {
                setMachineIcon(ws);
            }
        }
        if (isTurbine()) {
            autoCalculateTurbineParallel();
        }
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
        if (com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilMultiblock(machineIcon)
                || com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilRecipeCategory(recipeCategoryId)) {
            return false;
        }
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
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        return adapter != null ? adapter.getEnergyType(this) : EnergyType.ELECTRIC_EU;
    }

    public EnergyType getEnergyTypeOverride() {
        return energyType;
    }

    public void setEnergyType(EnergyType energyType) {
        this.energyType = energyType;
    }

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

    public boolean isReroute() {
        return isReroute;
    }

    public void setReroute(boolean reroute) {
        this.isReroute = reroute;
    }

    public SupplyMode getSupplyMode() {
        return supplyMode != null ? supplyMode : SupplyMode.NONE;
    }

    public void setSupplyMode(SupplyMode supplyMode) {
        this.supplyMode = supplyMode != null ? supplyMode : SupplyMode.NONE;
    }

    public boolean isExternalSupply() {
        return isReroute && getSupplyMode().isExternal();
    }

    public boolean isInfiniteSupply() {
        return isReroute && getSupplyMode() == SupplyMode.INFINITE;
    }

    public double getExternalSupplyRate() {
        return externalSupplyRate;
    }

    public void setExternalSupplyRate(double externalSupplyRate) {
        this.externalSupplyRate = Math.max(0.0, externalSupplyRate);
    }

    public int getCustomParallel() {
        return customParallel;
    }

    public void setCustomParallel(int customParallel) {
        this.customParallel = Math.max(0, customParallel);
    }

    public void bindRerouteIngredient(IngredientStack stack) {
        if (!isReroute || stack == null) return;
        inputs.clear();
        outputs.clear();
        inputs.add(stack.copy());
        outputs.add(stack.copy());
        this.name = stack.getDisplayName();
        if (stack.getAmount() > 0.0) {
            setTargetBatchAmount(stack.getAmount());
        }
    }

    public void unbindRerouteIngredient() {
        if (!isReroute) return;
        inputs.clear();
        outputs.clear();
        this.name = "Reroute";
    }

    public IngredientStack getRerouteIngredient() {
        return !outputs.isEmpty() ? outputs.get(0) : null;
    }

    public double getTargetBatchAmount() {
        return properties.get(NodeProperties.TARGET_BATCH_AMOUNT);
    }

    public void setTargetBatchAmount(double amount) {
        properties.set(NodeProperties.TARGET_BATCH_AMOUNT, Math.max(0.0, amount));
    }

    public boolean hasTargetBatch() {
        return getTargetBatchAmount() > 0.0001;
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

    public boolean isInputPortHidden(int index) {
        return hiddenInputIndices.contains(index);
    }

    public boolean isOutputPortHidden(int index) {
        return hiddenOutputIndices.contains(index);
    }

    public void hideInputPort(int index) {
        if (index >= 0 && index < inputs.size()) {
            hiddenInputIndices.add(index);
        }
    }

    public void unhideInputPort(int index) {
        hiddenInputIndices.remove(index);
    }

    public void hideOutputPort(int index) {
        if (index >= 0 && index < outputs.size()) {
            hiddenOutputIndices.add(index);
        }
    }

    public void unhideOutputPort(int index) {
        hiddenOutputIndices.remove(index);
    }

    public void unhideAllPorts() {
        hiddenInputIndices.clear();
        hiddenOutputIndices.clear();
    }

    public java.util.Set<Integer> getHiddenInputIndices() {
        return java.util.Collections.unmodifiableSet(hiddenInputIndices);
    }

    public java.util.Set<Integer> getHiddenOutputIndices() {
        return java.util.Collections.unmodifiableSet(hiddenOutputIndices);
    }

    public int getHiddenInputCount() {
        return hiddenInputIndices.size();
    }

    public int getHiddenOutputCount() {
        return hiddenOutputIndices.size();
    }

    public int getTotalHiddenCount() {
        return hiddenInputIndices.size() + hiddenOutputIndices.size();
    }

    public List<Integer> getVisibleInputIndices() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            if (!hiddenInputIndices.contains(i)) {
                list.add(i);
            }
        }
        return list;
    }

    public List<Integer> getVisibleOutputIndices() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            if (!hiddenOutputIndices.contains(i)) {
                list.add(i);
            }
        }
        return list;
    }

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

    public static boolean isThermalUpgradeKit(MachineAddon addon) {
        if (addon == null) return false;
        return addon.isThermalUpgradeKit();
    }

    public static boolean isMultiblockWorkstation(ResourceLocation ws) {
        return NodeWorkstationResolver.isMultiblockWorkstation(ws);
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

    public boolean removeOneAddon(String addonId) {
        if (addonId == null) return false;
        for (int i = 0; i < addons.size(); i++) {
            if (addons.get(i).getId().equals(addonId)) {
                addons.remove(i);
                return true;
            }
        }
        return false;
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

    public SteamMode getSteamMode() {
        return steamMode != null ? steamMode : SteamMode.NONE;
    }

    public void setSteamMode(SteamMode steamMode) {
        SteamMode oldMode = this.steamMode;
        this.steamMode = steamMode != null ? steamMode : SteamMode.NONE;
        syncSteamInputSlot(oldMode, this.steamMode);
    }

    public boolean supportsSteamMode() {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        if (adapter != null && !adapter.isGenericFallback()) {
            return adapter.supportsSteamMode(this);
        }
        for (IModAdapter a : ModAdapterRegistry.getAllLoadedAdapters()) {
            if (!a.isGenericFallback() && a.supportsSteamMode(this)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLiquidBoilerRecipe() {
        return ModAdapterRegistry.getAdapterForNode(this).isLiquidBoilerRecipe(this);
    }

    public void syncSteamInputSlot(SteamMode oldMode, SteamMode newMode) {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        if (adapter != null && !adapter.isGenericFallback()) {
            adapter.onSteamModeChanged(this, oldMode, newMode);
        } else {
            for (IModAdapter a : ModAdapterRegistry.getAllLoadedAdapters()) {
                if (!a.isGenericFallback() && a.supportsSteamMode(this)) {
                    a.onSteamModeChanged(this, oldMode, newMode);
                    break;
                }
            }
        }
    }

    public int getRecipeTemperature() {
        return properties.getById("ebf_temperature", 0);
    }

    public void setRecipeTemperature(int recipeTemperature) {
        properties.setById("ebf_temperature", Math.max(0, recipeTemperature));
    }

    public int getBoilerThrottle() {
        int t = properties.getById("boiler_throttle", 100);
        return Math.max(25, Math.min(100, t));
    }

    public void setBoilerThrottle(int throttle) {
        properties.setById("boiler_throttle", Math.max(25, Math.min(100, throttle)));
    }

    public long getEuToStart() {
        return properties.getById("fusion_start_eu", 0L);
    }

    public void setEuToStart(long euToStart) {
        properties.setById("fusion_start_eu", Math.max(0L, euToStart));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(this);
        this.targetTier = adapter.sanitizeTargetTier(this, this.targetTier);
    }

    public boolean isFusion() {
        return ModAdapterRegistry.getAdapterForNode(this).isFusion(this);
    }

    public int getFusionTier() {
        return ModAdapterRegistry.getAdapterForNode(this).getFusionTier(this);
    }

    public GTVoltageTier getMinFusionVoltageTier() {
        return ModAdapterRegistry.getAdapterForNode(this).getMinFusionVoltageTier(this);
    }

    public NodeThreadingConfig getThreadingConfig() {
        if (threadingConfig == null) {
            threadingConfig = new NodeThreadingConfig();
        }
        int max = machineIcon != null ? MultiblockDetector.getMaxHelixCount(machineIcon) : 0;
        if (max > 0 && threadingConfig.getMaxHelixCapacity() <= 0) {
            threadingConfig.setMaxHelixCapacity(max);
        }
        return threadingConfig;
    }

    public void setThreadingConfig(NodeThreadingConfig threadingConfig) {
        this.threadingConfig = threadingConfig;
    }

    public boolean isThreadingAvailable() {
        return MultiblockDetector.getMaxHelixCount(this) > 0;
    }

    public boolean isExplicitThreadingMachine() {
        if (machineIcon != null) {
            return MultiblockDetector.isThreadingMultiblock(machineIcon);
        }
        return false;
    }

    public boolean hasThreading() {
        if (isExplicitThreadingMachine()) return true;
        return threadingConfig != null && threadingConfig.isActive();
    }

    public boolean isThreadingActive() {
        return hasThreading();
    }

    public void setThreadingActive(boolean active) {
        if (active) {
            getThreadingConfig().setActive(true);
            for (ResourceLocation ws : getAvailableWorkstations()) {
                if (ws != null && MultiblockDetector.isThreadingMultiblock(ws)) {
                    setMachineIcon(ws);
                    break;
                }
            }
        } else {
            if (threadingConfig != null) {
                threadingConfig.reset();
                threadingConfig.setActive(false);
            }
            addons.removeIf(a -> a.getCategory() == AddonCategory.THREADING);
            if (machineIcon != null && MultiblockDetector.isThreadingMultiblock(machineIcon)) {
                ResourceLocation mbWs = getMultiblockWorkstation();
                if (mbWs != null && !MultiblockDetector.isThreadingMultiblock(mbWs)) {
                    setMachineIcon(mbWs);
                }
            }
        }
    }

    public int getRequiredReflectorTier() {
        return properties.getById("required_reflector_tier", 0);
    }

    public void setRequiredReflectorTier(int tier) {
        properties.setById("required_reflector_tier", Math.max(0, tier));
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
        if (this.isMultiblock == multiblock) return;
        this.isMultiblock = multiblock;
        if (multiblock) {
            if (this.steamMode != null && this.steamMode.isSteam()) {
                this.steamMode = com.gtceu.calcboard.api.type.SteamMode.NONE;
            }
            if (this.parallel <= 1) {
                int defPar = ModAdapterRegistry.getAdapterForNode(this).getDefaultParallel(this);
                if (defPar > 1) {
                    this.parallel = defPar;
                }
            }
            if (machineIcon == null || !MultiblockDetector.isMultiblock(machineIcon)) {
                ResourceLocation mbWs = getMultiblockWorkstation();
                if (mbWs != null && !Objects.equals(machineIcon, mbWs)) {
                    setMachineIcon(mbWs);
                }
            }
        } else {
            if (machineIcon != null && MultiblockDetector.isMultiblock(machineIcon)) {
                ResourceLocation sbWs = getWorkstationForTier(targetTier);
                if (sbWs == null) {
                    sbWs = getSingleblockWorkstation();
                }
                if (sbWs != null && !Objects.equals(machineIcon, sbWs)) {
                    setMachineIcon(sbWs);
                }
            }
            this.parallel = 1;
            this.addons.removeIf(a -> a.getCategory() == MachineAddon.Category.COIL 
                    || a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT
                    || a.getCategory() == MachineAddon.Category.THREADING
                    || a.getCategory() == MachineAddon.Category.PARALLEL);
        }
    }

    public boolean hasMultiblockOption() {
        return NodeWorkstationResolver.hasMultiblockOption(this);
    }

    public List<ResourceLocation> getMultiblockWorkstations() {
        return NodeWorkstationResolver.getMultiblockWorkstations(this);
    }

    public ResourceLocation getMultiblockWorkstation() {
        return NodeWorkstationResolver.getMultiblockWorkstation(this);
    }

    public ResourceLocation getSingleblockWorkstation() {
        return NodeWorkstationResolver.getSingleblockWorkstation(this);
    }

    public boolean canUseCoils() {
        return NodeWorkstationResolver.canUseCoils(this);
    }

    public boolean canUseMultiblockTraits() {
        return NodeWorkstationResolver.canUseMultiblockTraits(this);
    }

    public int getRpm() {
        int cur = properties.getById("kinetic_rpm", 32);
        return cur > 0 ? cur : 32;
    }

    public void setRpm(int rpm) {
        properties.setById("kinetic_rpm", Math.max(1, Math.min(256, rpm)));
    }

    public int getRotorEfficiency() {
        return properties.getById("rotor_efficiency", 100);
    }

    public void setRotorEfficiency(int rotorEfficiency) {
        properties.setById("rotor_efficiency", Math.max(1, Math.min(100000, rotorEfficiency)));
    }

    public int getRotorPower() {
        return properties.getById("rotor_power", 100);
    }

    public void setRotorPower(int rotorPower) {
        properties.setById("rotor_power", Math.max(1, Math.min(1000000, rotorPower)));
    }

    public String getRotorName() {
        return properties.getById("rotor_name", "Standard (100%)");
    }

    public void setRotorName(String rotorName) {
        properties.setById("rotor_name", rotorName != null ? rotorName : "Standard (100%)");
    }

    public boolean isLargeTurbine() {
        return ModAdapterRegistry.getAdapterForNode(this).isLargeTurbine(this);
    }

    public boolean isTurbine() {
        return ModAdapterRegistry.getAdapterForNode(this).isTurbine(this);
    }

    public double getGeneratorMaxEUt() {
        return ModAdapterRegistry.getAdapterForNode(this).getGeneratorMaxPower(this);
    }

    public void autoCalculateTurbineParallel() {
        ModAdapterRegistry.getAdapterForNode(this).autoTuneParallel(this);
    }

    public int getTierDelta() {
        return Math.max(0, targetTier.ordinal() - recipeTier.ordinal());
    }

    public OverclockMode.OverclockResult getOverclockResult() {
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.RecipeNodeEvent.PreCalculation(this));
        } catch (Throwable ignored) {}
        return ModAdapterRegistry.getAdapterForNode(this).computeOverclock(this, targetTier, isGenerator);
    }

    public double getEffectiveDurationSeconds() {
        return getOverclockResult().durationTicks() / 20.0;
    }

    public int getTotalParallel() {
        if (customParallel > 0) {
            return customParallel;
        }
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

    public Map<IngredientStack, Double> calculateInputRates() {
        return NodeRateCalculator.calculateInputRates(this);
    }

    public double getInputSlotRate(int index, boolean effective) {
        return NodeRateCalculator.getInputSlotRate(this, index, effective);
    }

    public double getEffectiveOutputChance(int outputIndex) {
        return NodeRateCalculator.getEffectiveOutputChance(this, outputIndex);
    }

    public double getOutputSlotRate(int index, boolean effective) {
        return NodeRateCalculator.getOutputSlotRate(this, index, effective);
    }

    public double getSingleOutputExpectedAmount(int index) {
        return NodeRateCalculator.getSingleOutputExpectedAmount(this, index);
    }

    public Map<IngredientStack, Double> calculateOutputRates() {
        return NodeRateCalculator.calculateOutputRates(this);
    }

    public Map<IngredientStack, Double> calculateEffectiveInputRates() {
        return NodeRateCalculator.calculateEffectiveInputRates(this);
    }

    public Map<IngredientStack, Double> calculateEffectiveOutputRates() {
        return NodeRateCalculator.calculateEffectiveOutputRates(this);
    }

    public double calculateSingleMachineOutputRate(IngredientStack out) {
        return NodeRateCalculator.calculateSingleMachineOutputRate(this, out);
    }

    public double calculateSingleMachineInputRate(IngredientStack in) {
        return NodeRateCalculator.calculateSingleMachineInputRate(this, in);
    }

    public boolean isCompoundNode() {
        return properties.has(NodeProperties.COMPOUND_GROUP_ID) && !properties.get(NodeProperties.COMPOUND_GROUP_ID).isEmpty();
    }

    public boolean isCompoundMaster() {
        return isCompoundNode() && getCompoundLayerIndex() == 0;
    }

    public String getCompoundGroupId() {
        return properties.get(NodeProperties.COMPOUND_GROUP_ID);
    }

    public int getCompoundLayerIndex() {
        return properties.get(NodeProperties.COMPOUND_LAYER_INDEX);
    }

    public int getCompoundTotalLayers() {
        return properties.get(NodeProperties.COMPOUND_TOTAL_LAYERS);
    }

    public String getCompoundMasterNodeId() {
        return properties.get(NodeProperties.COMPOUND_MASTER_NODE_ID);
    }

    public void setCompoundMetadata(String groupId, int layerIndex, int totalLayers, String masterId) {
        if (groupId == null || groupId.isEmpty()) {
            properties.remove(NodeProperties.COMPOUND_GROUP_ID);
            properties.remove(NodeProperties.COMPOUND_LAYER_INDEX);
            properties.remove(NodeProperties.COMPOUND_TOTAL_LAYERS);
            properties.remove(NodeProperties.COMPOUND_MASTER_NODE_ID);
        } else {
            properties.set(NodeProperties.COMPOUND_GROUP_ID, groupId);
            properties.set(NodeProperties.COMPOUND_LAYER_INDEX, Math.max(0, layerIndex));
            properties.set(NodeProperties.COMPOUND_TOTAL_LAYERS, Math.max(1, totalLayers));
            properties.set(NodeProperties.COMPOUND_MASTER_NODE_ID, masterId != null ? masterId : "");
        }
    }

    public CompoundTag serializeNBT() {
        return RecipeNodeSerializer.serialize(this);
    }

    public static RecipeNode deserializeNBT(CompoundTag tag) {
        return RecipeNodeSerializer.deserialize(tag);
    }
}
