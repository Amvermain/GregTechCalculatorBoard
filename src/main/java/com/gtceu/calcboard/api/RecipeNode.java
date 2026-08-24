package com.gtceu.calcboard.api;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.compat.gtceu.helper.TurbineRotorHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

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
    private int cardWidth = 245;  // Default 245, resizable 245-500
    private int cardHeight = 0;   // 0 = Auto-height, resizable up to 600

    // Module / Subgraph Abstraction
    private boolean isModule = false;
    private FlowGraph subGraph = null;
    private int containedMachineCount = 0;
    private double efficiency = 1.0;
    private EnergyType energyType = EnergyType.ELECTRIC_EU;

    public EnergyType getEnergyType() {
        if (energyType != null) {
            return energyType;
        }
        if (baseEUt <= 0.0 && !isGenerator) {
            return EnergyType.HEAT_OR_SELF;
        }
        return EnergyType.ELECTRIC_EU;
    }

    public void setEnergyType(EnergyType energyType) {
        this.energyType = energyType;
    }

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
            String sanitized = base.toLowerCase().trim().replace(" ", "_");
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
        if (isReroute) return 0.0;
        return baseDurationTicks;
    }

    public void setBaseDurationTicks(double baseDurationTicks) {
        if (isReroute) {
            this.baseDurationTicks = 0.0;
            return;
        }
        this.baseDurationTicks = Math.max(1.0, baseDurationTicks);
    }

    public double getBaseEUt() {
        if (isReroute) return 0.0;
        return baseEUt;
    }

    public void setBaseEUt(double baseEUt) {
        if (isReroute) {
            this.baseEUt = 0.0;
            return;
        }
        this.baseEUt = Math.max(0.0, baseEUt);
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
        if (targetTier.ordinal() >= minTier.ordinal()) {
            this.targetTier = targetTier;
        } else {
            this.targetTier = minTier;
        }
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

    private List<ResourceLocation> availableWorkstations = new ArrayList<>();

    public List<ResourceLocation> getAvailableWorkstations() {
        return availableWorkstations;
    }

    public void setAvailableWorkstations(List<ResourceLocation> availableWorkstations) {
        this.availableWorkstations = availableWorkstations != null ? availableWorkstations : new ArrayList<>();
    }

    private ResourceLocation recipeCategoryId;

    public ResourceLocation getRecipeCategoryId() {
        return recipeCategoryId;
    }

    public void setRecipeCategoryId(ResourceLocation recipeCategoryId) {
        this.recipeCategoryId = recipeCategoryId;
    }

    public NodePropertyStore getProperties() {
        return properties;
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
                || (name != null && name.toLowerCase().contains("fusion"));
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

    /**
     * Checks if the machine node satisfies hardware prerequisites (reflector tier, coil temp, etc.) to operate.
     */
    public boolean isOperational() {
        return isOperational(null);
    }

    /**
     * Checks if the machine node satisfies operating requirements including graph flow conditions.
     */
    public boolean isOperational(FlowGraph graph) {
        if (isReroute) return true;
        if (!hasValidReflector()) return false;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(this);
        if (adapter != null) {
            return adapter.validateNode(this, graph, null);
        }
        return true;
    }

    private boolean isMultiblock = false;

    public static final int[] STANDARD_RPMS = {4, 8, 16, 32, 64, 128, 256};

    public int getRpm() {
        int cur = properties.get(NodeProperties.KINETIC_RPM);
        return cur > 0 ? cur : 32;
    }

    public void setRpm(int rpm) {
        properties.set(NodeProperties.KINETIC_RPM, Math.max(1, Math.min(256, rpm)));
    }

    public void cycleRpm(int direction) {
        int cur = getRpm();
        int closestIdx = 3; // 32
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
        if (machineIcon != null && machineIcon.getNamespace().equals("create")) return true;
        if (recipeCategoryId != null && recipeCategoryId.getNamespace().equals("create")) return true;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null && ws.getNamespace().equals("create")) return true;
        }
        return false;
    }

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public void setMultiblock(boolean multiblock) {
        this.isMultiblock = multiblock;
    }

    public static boolean isMultiblockWorkstation(ResourceLocation ws) {
        if (ws == null) return false;
        return MultiblockDetector.isMultiblock(ws);
    }

    public boolean hasMultiblockOption() {
        if (isModule()) return false;
        if (isMultiblock() || canUseCoils()) return true;
        if (recipeCategoryId != null && CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId).hasMultiblockOption()) {
            return true;
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (isMultiblockWorkstation(ws) || MultiblockDetector.isMultiblock(ws)) {
                return true;
            }
        }

        return false;
    }

    public ResourceLocation getMultiblockWorkstation() {
        for (ResourceLocation ws : availableWorkstations) {
            if (isMultiblockWorkstation(ws)) {
                return ws;
            }
        }
        return null;
    }

    public ResourceLocation getSingleblockWorkstation() {
        for (ResourceLocation ws : availableWorkstations) {
            if (!isMultiblockWorkstation(ws)) {
                return ws;
            }
        }
        return machineIcon;
    }

    public boolean canUseCoils() {
        if (getRecipeTemperature() > 0) {
            return true;
        }
        if (recipeCategoryId != null && CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId).canUseCoils()) {
            return true;
        }
        if (machineIcon != null && MultiblockDetector.isCoilMultiblock(machineIcon)) {
            return true;
        }
        if (recipeCategoryId != null && MultiblockDetector.isCoilRecipeCategory(recipeCategoryId)) {
            return true;
        }
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isCoilMultiblock(ws)) {
                return true;
            }
        }
        return false;
    }

    public boolean canUseMultiblockTraits() {
        if (isMultiblock() || hasMultiblockOption() || canUseCoils()) return true;
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                return true;
            }
        }
        return false;
    }

    public OverclockMode getOverclockMode() {
        return overclockMode;
    }

    public void setOverclockMode(OverclockMode overclockMode) {
        this.overclockMode = overclockMode != null ? overclockMode : OverclockMode.STANDARD;
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

    public int getCardWidth() {
        if (isReroute) return 32;
        return Math.max(245, Math.min(500, cardWidth));
    }

    public void setCardWidth(int cardWidth) {
        if (isReroute) {
            this.cardWidth = 32;
            return;
        }
        this.cardWidth = Math.max(245, Math.min(500, cardWidth));
    }

    public int getCardHeight() {
        if (isReroute) return 32;
        return Math.max(0, Math.min(600, cardHeight));
    }

    public void setCardHeight(int cardHeight) {
        if (isReroute) {
            this.cardHeight = 32;
            return;
        }
        this.cardHeight = Math.max(0, Math.min(600, cardHeight));
    }

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

    public List<List<PortOrigin>> getModuleInputOrigins() {
        return moduleInputOrigins;
    }

    public List<List<PortOrigin>> getModuleOutputOrigins() {
        return moduleOutputOrigins;
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

    public void setPos(double posX, double posY) {
        this.posX = posX;
        this.posY = posY;
    }

    public int getRotorEfficiency() {
        return properties.get(NodeProperties.TURBINE_ROTOR_EFFICIENCY);
    }

    public void setRotorEfficiency(int rotorEfficiency) {
        properties.set(NodeProperties.TURBINE_ROTOR_EFFICIENCY, Math.max(10, Math.min(500, rotorEfficiency)));
    }

    public int getRotorPower() {
        return properties.get(NodeProperties.TURBINE_ROTOR_POWER);
    }

    public void setRotorPower(int rotorPower) {
        properties.set(NodeProperties.TURBINE_ROTOR_POWER, Math.max(10, Math.min(5000, rotorPower)));
    }

    public String getRotorName() {
        return properties.get(NodeProperties.TURBINE_ROTOR_NAME);
    }

    public void setRotorName(String rotorName) {
        properties.set(NodeProperties.TURBINE_ROTOR_NAME, rotorName != null ? rotorName : "Standard (100%)");
    }

    /**
     * Deductively identifies the base voltage tier for this turbine type from GTCEu Machine Definitions or Recipe Category.
     */
    public GTVoltageTier getTurbineBaseTier() {
        if (recipeCategoryId != null) {
            GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(recipeCategoryId);
            if (tier != null) return tier;
        }
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null) {
                GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(ws);
                if (tier != null) return tier;
            }
        }
        return GTVoltageTier.EV;
    }

    public int getTurbineHolderEfficiencyBonus() {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getTurbineHolderEfficiencyBonus(this);
    }

    public double getTurbineBaseProduction() {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getTurbineBaseProduction(this);
    }

    public boolean hasRotorAddon() {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.hasRotorAddon(this);
    }

    public boolean isLargeTurbine() {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isLargeTurbine(this);
    }

    public double getNodeRotorHolderBaseCapacity(GTVoltageTier tier) {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getNodeRotorHolderBaseCapacity(this, tier);
    }

    /**
     * Fallback static rotor holder base capacity (EV = 4,096 EU/t base).
     */
    public static double getRotorHolderBaseCapacity(GTVoltageTier tier) {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getRotorHolderBaseCapacity(tier);
    }

    /**
     * GTCEu Rotor Holder max throughput based on its voltage tier and rotor power %.
     */
    public double getNodeRotorHolderMaxEUt(GTVoltageTier tier, int rotorPower) {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getNodeRotorHolderMaxEUt(this, tier, rotorPower);
    }

    public static double getRotorHolderMaxEUt(GTVoltageTier tier, int rotorPower) {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getRotorHolderMaxEUt(tier, rotorPower);
    }

    public static double getRotorHolderMaxEUt(GTVoltageTier tier) {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getRotorHolderMaxEUt(tier);
    }

    /**
     * Dynamically queries GTCEu for material rotor maximum EU/t capacity / durability.
     */
    public static double getRotorMaterialMaxEUt(String rotorName) {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getRotorMaterialMaxEUt(rotorName);
    }

    /**
     * Dynamically queries GTCEu for material rotor efficiency.
     */
    public static int getRotorMaterialEfficiency(String rotorName) {
        return TurbineRotorHelper.getRotorStats(rotorName).efficiency();
    }

    /**
     * Dynamically queries GTCEu for material rotor power.
     */
    public static int getRotorMaterialPower(String rotorName) {
        return TurbineRotorHelper.getRotorStats(rotorName).power();
    }

    public static int getRotorOptimalParallel(String rotorName, double baseEUt) {
        return getRotorOptimalParallel(rotorName, baseEUt, GTVoltageTier.EV, 100);
    }

    public static int getRotorOptimalParallel(String rotorName, double baseEUt, GTVoltageTier tier) {
        return getRotorOptimalParallel(rotorName, baseEUt, tier, 100);
    }

    public static int getRotorOptimalParallel(String rotorName, double baseEUt, GTVoltageTier tier, int rotorPower) {
        if (baseEUt <= 0) return 1;
        int activePower = rotorPower > 0 && rotorPower != 100 ? rotorPower : getRotorMaterialPower(rotorName);
        double holderMax = getRotorHolderMaxEUt(tier, activePower);
        return (int) Math.max(1, Math.ceil(holderMax / baseEUt));
    }

    /**
     * Automatically calculates and applies optimal turbine parallel count 
     * based on target Rotor Holder voltage tier limit, rotor optimal throughput, and base recipe EU/t.
     */
    public void autoCalculateTurbineParallel() {
        if (!isGenerator || !isLargeTurbine()) {
            this.parallel = 1;
            return;
        }
        if (baseEUt <= 0) return;

        String rName = getRotorName();
        int rEff = getRotorEfficiency();
        int rPow = getRotorPower();
        boolean hasRotor = addons.stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"));
        if (!hasRotor && rEff <= 100 && (rPow <= 100 || rPow == 0)) {
            return;
        }

        int activePower = rPow > 0 && rPow != 100 ? rPow : getRotorMaterialPower(rName);
        for (MachineAddon addon : addons) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }

        double holderMax = getNodeRotorHolderMaxEUt(targetTier, activePower);
        this.parallel = (int) Math.max(1, Math.ceil(holderMax / baseEUt));
    }

    // Addon Management & Calculations
    public List<MachineAddon> getAddons() {
        return addons;
    }

    public static boolean isThermalUpgradeKit(MachineAddon addon) {
        if (addon == null) return false;
        if (addon.getCategory() != MachineAddon.Category.THERMAL_AUGMENT) return false;
        return addon.getParallelMultiplier() > 1 || addon.getId().contains("upgrade_kit") || addon.getId().contains("tier_upgrade");
    }

    public void addAddon(MachineAddon addon) {
        if (addon == null) return;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(this);
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
                com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(this);
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

    public int getEffectiveTurbineParallel() {
        if (!isLargeTurbine()) return Math.max(1, parallel);
        if (parallel > 1) return parallel;
        double cap = getGeneratorMaxEUt();
        double recipeEUt = Math.abs(getBaseEUt());
        if (recipeEUt <= 0 || cap >= Double.MAX_VALUE) return Math.max(1, parallel);
        return (int) Math.max(1, Math.floor(cap / recipeEUt));
    }

    public boolean isGTGenerator() {
        if (!isGenerator) return false;
        if (energyType != EnergyType.ELECTRIC_EU) return false;
        if (MachineAddon.isThermalMachine(this)) return false;
        if (recipeCategoryId != null && recipeCategoryId.getNamespace().equals("gtceu")) return true;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null && ws.getNamespace().equals("gtceu")) return true;
        }
        if (machineIcon != null && machineIcon.getNamespace().equals("gtceu")) return true;
        if (name != null && (name.contains("Turbine") || name.contains("Generator"))) return true;
        return false;
    }

    public int getEffectiveSingleblockParallel() {
        if (!isGTGenerator() || isLargeTurbine()) return Math.max(1, parallel);
        if (parallel > 1) return parallel;
        double recipeEUt = Math.abs(getBaseEUt());
        if (recipeEUt > 0 && targetTier != null && recipeEUt < targetTier.getVoltage()) {
            return (int) Math.max(1, Math.floor((double) targetTier.getVoltage() / recipeEUt));
        }
        return Math.max(1, parallel);
    }

    public int getTotalParallel() {
        if (MachineAddon.isThermalMachine(this)) {
            return Math.max(1, parallel);
        }
        if (isGenerator) {
            if (isLargeTurbine()) {
                return getEffectiveTurbineParallel() * getCombinedParallelMultiplier();
            } else if (isGTGenerator()) {
                return getEffectiveSingleblockParallel() * getCombinedParallelMultiplier();
            }
        }
        return Math.max(1, parallel * getCombinedParallelMultiplier());
    }

    public boolean hasPowerConstantAddon() {
        for (MachineAddon a : addons) {
            if (a.isPowerConstant()) return true;
        }
        return false;
    }

    // Calculation logic
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
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(this);
        return adapter.computeOverclock(this, targetTier, isGenerator);
    }

    public boolean isTurbine() {
        return com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isTurbine(this);
    }

    /**
     * Effective duration in seconds for a single recipe cycle.
     */
    public double getEffectiveDurationSeconds() {
        return getOverclockResult().durationTicks() / 20.0;
    }

    public double getGeneratorMaxEUt() {
        if (!isGenerator) return Double.MAX_VALUE;
        String rName = getRotorName();
        int rPower = getRotorPower();
        boolean hasRotor = addons.stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"));
        if (!hasRotor) return Double.MAX_VALUE;

        int activePower = rPower > 0 && rPower != 100 ? rPower : getRotorMaterialPower(rName);
        for (MachineAddon addon : addons) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }

        return getNodeRotorHolderMaxEUt(targetTier, activePower);
    }

    /**
     * Single machine EU/t consumption while running.
     */
    public double getSingleMachineEUt() {
        if (!isOperational()) return 0.0;
        if (isGenerator) {
            if (isLargeTurbine()) {
                if (parallel == 1) {
                    double cap = getGeneratorMaxEUt();
                    if (cap < Double.MAX_VALUE) {
                        return cap;
                    }
                }
                double rawGen = getOverclockResult().eut() * getTotalParallel();
                double cap = getGeneratorMaxEUt();
                return Math.min(rawGen, cap);
            } else if (isGTGenerator() && parallel == 1) {
                double recipeEUt = Math.abs(getBaseEUt());
                if (recipeEUt < targetTier.getVoltage()) {
                    double rawGen = getOverclockResult().eut() * getTotalParallel();
                    double cap = (double) targetTier.getVoltage();
                    return Math.min(rawGen, cap);
                }
            }
            return getOverclockResult().eut() * getTotalParallel();
        }
        if (hasPowerConstantAddon()) {
            return getOverclockResult().eut() * parallel;
        }
        return getOverclockResult().eut() * getTotalParallel();
    }

    /**
     * Total EU/t consumption across all machines in this node.
     */
    public double getTotalEUt() {
        if (!isOperational()) return 0.0;
        return getSingleMachineEUt() * machineCount;
    }

    /**
     * Recipe executions per second across all machines and parallel factor.
     */
    public double getNominalCyclesPerSecond() {
        double singleMachineCyclesPerSec = getOverclockResult().getCyclesPerSecond();
        return singleMachineCyclesPerSec * machineCount * getTotalParallel();
    }

    public double getCyclesPerSecond() {
        if (!isOperational()) return 0.0;
        return getNominalCyclesPerSecond();
    }

    /**
     * Calculates input consumption rates in items/s or mB/s.
     */
    public Map<IngredientStack, Double> calculateInputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getCyclesPerSecond();
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

    public double getInputSlotRate(int index, boolean effective) {
        if (index < 0 || index >= inputs.size()) return 0.0;
        IngredientStack in = inputs.get(index);
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

    /**
     * Calculates output production rates in items/s or mB/s, properly merging multiple output slots of the same item.
     */
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

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = Math.max(0.0, Math.min(1.0, efficiency));
    }

    /**
     * Effective EU/t consumption/generation reflecting bottleneck efficiency.
     */
    public double getEffectiveTotalEUt() {
        return getTotalEUt() * efficiency;
    }

    /**
     * Effective recipe executions per second reflecting bottleneck efficiency.
     */
    public double getEffectiveCyclesPerSecond() {
        return getCyclesPerSecond() * efficiency;
    }

    /**
     * Calculates effective input consumption rates in items/s or mB/s.
     */
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

    /**
     * Calculates effective output production rates in items/s or mB/s, properly merging multiple output slots of the same item.
     */
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

    /**
     * Calculates single machine production rate for a specific output ingredient.
     */
    public double calculateSingleMachineOutputRate(IngredientStack out) {
        if (out == null || !isOperational()) return 0.0;
        double singleCps = getOverclockResult().getCyclesPerSecond() * getTotalParallel();
        int tierDelta = getTierDelta();
        return out.getExpectedAmount(tierDelta) * singleCps;
    }

    /**
     * Calculates single machine consumption rate for a specific input ingredient.
     */
    public double calculateSingleMachineInputRate(IngredientStack in) {
        if (in == null) return 0.0;
        double singleCps = getOverclockResult().getCyclesPerSecond() * getTotalParallel();
        return in.getAmount() * singleCps;
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

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        if (machineIcon != null) {
            tag.putString("icon", machineIcon.toString());
        }
        tag.putDouble("baseDuration", baseDurationTicks);
        tag.putDouble("baseEUt", baseEUt);
        tag.putString("recipeTier", recipeTier.name());
        tag.putString("targetTier", targetTier.name());
        tag.putDouble("machineCount", machineCount);
        tag.putInt("parallel", parallel);
        tag.putString("overclockMode", overclockMode.name());
        tag.putBoolean("isBaseNode", isBaseNode);
        tag.putBoolean("isGenerator", isGenerator);
        tag.put("properties", properties.serializeNBT());
        tag.putDouble("posX", posX);
        tag.putDouble("posY", posY);
        tag.putInt("cardWidth", cardWidth);
        tag.putInt("cardHeight", cardHeight);
        tag.putBoolean("isModule", isModule);
        tag.putInt("containedMachineCount", containedMachineCount);
        if (subGraph != null) {
            tag.put("subGraph", subGraph.serializeNBT(0, 0, 1.0));
        }

        ListTag inList = new ListTag();
        for (IngredientStack in : inputs) {
            inList.add(in.serializeNBT());
        }
        tag.put("inputs", inList);

        ListTag outList = new ListTag();
        for (IngredientStack out : outputs) {
            outList.add(out.serializeNBT());
        }
        tag.put("outputs", outList);

        if (!addons.isEmpty()) {
            ListTag addonList = new ListTag();
            for (MachineAddon a : addons) {
                addonList.add(a.serializeNBT());
            }
            tag.put("addons", addonList);
        }

        if (!availableWorkstations.isEmpty()) {
            ListTag wsList = new ListTag();
            for (ResourceLocation ws : availableWorkstations) {
                wsList.add(net.minecraft.nbt.StringTag.valueOf(ws.toString()));
            }
            tag.put("workstations", wsList);
        }

        if (recipeCategoryId != null) {
            tag.putString("recipeCategoryId", recipeCategoryId.toString());
        }

        if (energyType != null) {
            tag.putString("energyType", energyType.name());
        }

        tag.putBoolean("isMultiblock", isMultiblock);
        tag.putBoolean("isReroute", isReroute);

        if (!moduleInputOrigins.isEmpty()) {
            ListTag inOriginsTag = new ListTag();
            for (List<PortOrigin> origins : moduleInputOrigins) {
                ListTag slotList = new ListTag();
                for (PortOrigin o : origins) {
                    slotList.add(o.serializeNBT());
                }
                inOriginsTag.add(slotList);
            }
            tag.put("moduleInputOrigins", inOriginsTag);
        }

        if (!moduleOutputOrigins.isEmpty()) {
            ListTag outOriginsTag = new ListTag();
            for (List<PortOrigin> origins : moduleOutputOrigins) {
                ListTag slotList = new ListTag();
                for (PortOrigin o : origins) {
                    slotList.add(o.serializeNBT());
                }
                outOriginsTag.add(slotList);
            }
            tag.put("moduleOutputOrigins", outOriginsTag);
        }

        return tag;
    }

    public static RecipeNode deserializeNBT(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        double baseDuration = tag.getDouble("baseDuration");
        double baseEUt = tag.getDouble("baseEUt");
        GTVoltageTier recipeTier = GTVoltageTier.valueOf(tag.getString("recipeTier"));

        RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
        if (tag.contains("properties", Tag.TAG_COMPOUND)) {
            node.properties.deserializeNBT(tag.getCompound("properties"));
        }

        // Backward compatibility for legacy saves and blueprints
        if (tag.contains("recipeTemperature") && !node.properties.has(NodeProperties.EBF_TEMPERATURE)) {
            node.setRecipeTemperature(tag.getInt("recipeTemperature"));
        }
        if (tag.contains("rpm") && !node.properties.has(NodeProperties.KINETIC_RPM)) {
            node.setRpm(tag.getInt("rpm"));
        }
        if (tag.contains("rotorEfficiency") && !node.properties.has(NodeProperties.TURBINE_ROTOR_EFFICIENCY)) {
            node.setRotorEfficiency(tag.getInt("rotorEfficiency"));
        }
        if (tag.contains("rotorPower") && !node.properties.has(NodeProperties.TURBINE_ROTOR_POWER)) {
            node.setRotorPower(tag.getInt("rotorPower"));
        }
        if (tag.contains("rotorName") && !node.properties.has(NodeProperties.TURBINE_ROTOR_NAME)) {
            node.setRotorName(tag.getString("rotorName"));
        }
        if (tag.contains("fusionStartEU") && !node.properties.has(NodeProperties.FUSION_START_EU)) {
            node.setEuToStart(tag.getLong("fusionStartEU"));
        } else if (tag.contains("euToStart") && !node.properties.has(NodeProperties.FUSION_START_EU)) {
            node.setEuToStart(tag.getLong("euToStart"));
        }

        if (tag.contains("recipeCategoryId")) {
            node.recipeCategoryId = ResourceLocation.tryParse(tag.getString("recipeCategoryId"));
        }
        if (tag.contains("isMultiblock")) {
            node.isMultiblock = tag.getBoolean("isMultiblock");
        }
        if (tag.contains("icon")) {
            node.machineIcon = ResourceLocation.tryParse(tag.getString("icon"));
        }
        if (tag.contains("targetTier")) {
            node.targetTier = GTVoltageTier.valueOf(tag.getString("targetTier"));
        }
        if (tag.contains("machineCount")) {
            node.machineCount = tag.getDouble("machineCount");
        }
        if (tag.contains("parallel")) {
            node.parallel = tag.getInt("parallel");
        }
        if (tag.contains("workstations")) {
            ListTag wsList = tag.getList("workstations", 8);
            for (int i = 0; i < wsList.size(); i++) {
                ResourceLocation ws = ResourceLocation.tryParse(wsList.getString(i));
                if (ws != null) node.availableWorkstations.add(ws);
            }
        }
        if (tag.contains("addons")) {
            ListTag addonList = tag.getList("addons", 10);
            for (int i = 0; i < addonList.size(); i++) {
                MachineAddon a = MachineAddon.deserializeNBT(addonList.getCompound(i));
                if (a != null) {
                    node.addAddon(a);
                }
            }
        }
        if (tag.contains("overclockMode")) {
            node.overclockMode = OverclockMode.valueOf(tag.getString("overclockMode"));
        }
        if (tag.contains("isBaseNode")) {
            node.isBaseNode = tag.getBoolean("isBaseNode");
        }
        if (tag.contains("isGenerator")) {
            node.isGenerator = tag.getBoolean("isGenerator");
        }
        if (tag.contains("cardWidth")) {
            node.cardWidth = tag.getInt("cardWidth");
        }
        if (tag.contains("cardHeight")) {
            node.cardHeight = tag.getInt("cardHeight");
        }
        if (tag.contains("isModule")) {
            node.isModule = tag.getBoolean("isModule");
        }
        if (tag.contains("containedMachineCount")) {
            node.containedMachineCount = tag.getInt("containedMachineCount");
        }
        if (tag.contains("energyType")) {
            try {
                node.energyType = EnergyType.valueOf(tag.getString("energyType"));
            } catch (Throwable ignored) {}
        }
        if (tag.contains("subGraph")) {
            node.subGraph = FlowGraph.deserializeNBT(tag.getCompound("subGraph"));
        }
        node.posX = tag.getDouble("posX");
        node.posY = tag.getDouble("posY");

        if (tag.contains("inputs", Tag.TAG_LIST)) {
            ListTag inList = tag.getList("inputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < inList.size(); i++) {
                node.inputs.add(IngredientStack.deserializeNBT(inList.getCompound(i)));
            }
        }
        if (tag.contains("outputs", Tag.TAG_LIST)) {
            ListTag outList = tag.getList("outputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < outList.size(); i++) {
                node.outputs.add(IngredientStack.deserializeNBT(outList.getCompound(i)));
            }
        }

        if (tag.contains("isReroute")) {
            node.isReroute = tag.getBoolean("isReroute");
            if (node.isReroute) {
                node.cardWidth = 32;
                node.cardHeight = 32;
            }
        }
        if (tag.contains("moduleInputOrigins", Tag.TAG_LIST)) {
            ListTag inOriginsTag = tag.getList("moduleInputOrigins", Tag.TAG_LIST);
            for (int i = 0; i < inOriginsTag.size(); i++) {
                ListTag slotList = inOriginsTag.getList(i);
                List<PortOrigin> origins = new ArrayList<>();
                for (int j = 0; j < slotList.size(); j++) {
                    origins.add(PortOrigin.deserializeNBT(slotList.getCompound(j)));
                }
                node.moduleInputOrigins.add(origins);
            }
        }
        if (tag.contains("moduleOutputOrigins", Tag.TAG_LIST)) {
            ListTag outOriginsTag = tag.getList("moduleOutputOrigins", Tag.TAG_LIST);
            for (int i = 0; i < outOriginsTag.size(); i++) {
                ListTag slotList = outOriginsTag.getList(i);
                List<PortOrigin> origins = new ArrayList<>();
                for (int j = 0; j < slotList.size(); j++) {
                    origins.add(PortOrigin.deserializeNBT(slotList.getCompound(j)));
                }
                node.moduleOutputOrigins.add(origins);
            }
        }

        return node;
    }
}
