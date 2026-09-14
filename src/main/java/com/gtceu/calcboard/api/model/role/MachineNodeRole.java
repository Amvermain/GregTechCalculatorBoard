package com.gtceu.calcboard.api.model.role;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.NodeAddonHelper;
import com.gtceu.calcboard.api.model.NodeMultiblockHelper;
import com.gtceu.calcboard.api.model.NodePerformanceHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeNodeReflectorHelper;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Operational role representing a physical singleblock or multiblock processing machine.
 * <p>
 * Manages voltage tiering, overclock mode, parallel capacity, hardware addons, and machine count.
 */
public class MachineNodeRole implements INodeRole {

    private RecipeNode owner;
    private ResourceLocation machineIcon;
    private ResourceLocation recipeCategoryId;
    private final List<ResourceLocation> availableWorkstations = new ArrayList<>();

    private double baseDurationTicks;
    private double baseEUt;
    private GTVoltageTier recipeTier;
    private GTVoltageTier targetTier;
    private double machineCount = 1.0;
    private int parallel = 1;
    private int customParallel = 0;
    private OverclockMode overclockMode = OverclockMode.STANDARD;
    private boolean isGenerator = false;
    private double efficiency = 1.0;
    private EnergyType energyType = null;
    private boolean isMultiblock = false;
    private final List<MachineAddon> addons = new ArrayList<>();

    private transient OverclockMode.OverclockResult cachedOverclockResult = null;
    private transient boolean overclockDirty = true;
    private transient IModAdapter cachedModAdapter = null;
    private transient int cachedTotalParallel = -1;
    private transient double cachedNominalCps = -1.0;
    private transient double cachedSingleMachinePower = -1.0;
    private transient Boolean cachedOperational = null;
    private transient FlowGraph cachedOperationalGraph = null;

    public MachineNodeRole() {
        this(20.0, 0.0, GTVoltageTier.ULV);
    }

    public MachineNodeRole(double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        this.baseDurationTicks = Math.max(0.0, baseDurationTicks);
        this.baseEUt = Math.abs(baseEUt);
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.getTierForVoltage((long) baseEUt);
        this.targetTier = this.recipeTier;
    }

    @Override
    public NodeRoleType getRoleType() {
        return NodeRoleType.MACHINE;
    }

    @Override
    public void attach(RecipeNode owner) {
        this.owner = owner;
        markDirty();
    }

    @Override
    public void detach() {
        this.owner = null;
        markDirty();
    }

    @Override
    public RecipeNode getOwner() {
        return owner;
    }

    @Override
    public void markDirty() {
        this.overclockDirty = true;
        this.cachedOverclockResult = null;
        this.cachedTotalParallel = -1;
        this.cachedNominalCps = -1.0;
        this.cachedSingleMachinePower = -1.0;
        this.cachedModAdapter = null;
        this.cachedOperational = null;
        this.cachedOperationalGraph = null;
    }

    @Override
    public int getDefaultCardWidth() {
        return 245;
    }

    @Override
    public int getDefaultCardHeight() {
        return 0;
    }

    @Override
    public boolean isFixedSize() {
        return false;
    }

    public ResourceLocation getMachineIcon() {
        return machineIcon;
    }

    public void setMachineIcon(ResourceLocation machineIcon) {
        if (Objects.equals(this.machineIcon, machineIcon)) return;
        this.cachedModAdapter = null;
        ResourceLocation oldIcon = this.machineIcon;
        this.machineIcon = machineIcon;
        if (owner != null) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(owner);
            if (adapter != null) {
                adapter.onMachineIconChanged(owner, oldIcon, machineIcon);
            }
        }
        markDirty();
    }

    public ResourceLocation getRecipeCategoryId() {
        return recipeCategoryId;
    }

    public void setRecipeCategoryId(ResourceLocation recipeCategoryId) {
        if (Objects.equals(this.recipeCategoryId, recipeCategoryId)) return;
        this.cachedModAdapter = null;
        this.recipeCategoryId = recipeCategoryId;
        this.availableWorkstations.clear();
        markDirty();
    }

    public List<ResourceLocation> getAvailableWorkstations() {
        if (availableWorkstations.isEmpty() && recipeCategoryId != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId);
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

    public double getBaseDurationTicks() {
        return baseDurationTicks;
    }

    public void setBaseDurationTicks(double baseDurationTicks) {
        this.baseDurationTicks = Math.max(0.0, baseDurationTicks);
        markDirty();
    }

    public double getBaseEUt() {
        return baseEUt;
    }

    public void setBaseEUt(double baseEUt) {
        this.baseEUt = Math.abs(baseEUt);
        markDirty();
    }

    public GTVoltageTier getRecipeTier() {
        return recipeTier;
    }

    public void setRecipeTier(GTVoltageTier recipeTier) {
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.ULV;
        markDirty();
    }

    public GTVoltageTier getTargetTier() {
        return targetTier;
    }

    public void setTargetTier(GTVoltageTier targetTier) {
        if (owner != null) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(owner);
            this.targetTier = adapter.sanitizeTargetTier(owner, targetTier);
            sanitizeTargetTierWorkstation();
            if (owner.isTurbine()) {
                owner.autoCalculateTurbineParallel();
            }
        } else {
            this.targetTier = targetTier;
        }
        markDirty();
    }

    private void sanitizeTargetTierWorkstation() {
        if (owner == null) return;
        if (isMultiblock || owner.isLargeTurbine()) return;
        if (owner.getSteamMode() != null && owner.getSteamMode().isSteam()) return;
        if (machineIcon != null && MultiblockDetector.isMultiblock(machineIcon)) return;
        ResourceLocation ws = owner.getWorkstationForTier(this.targetTier);
        if (ws != null) {
            setMachineIcon(ws);
        }
    }

    public double getMachineCount() {
        return machineCount;
    }

    public void setMachineCount(double machineCount) {
        this.machineCount = Math.max(0.01, machineCount);
        markDirty();
    }

    public int getParallel() {
        return parallel;
    }

    public void setParallel(int parallel) {
        this.parallel = Math.max(1, parallel);
        markDirty();
    }

    public int getCustomParallel() {
        return customParallel;
    }

    public void setCustomParallel(int customParallel) {
        this.customParallel = Math.max(0, customParallel);
        markDirty();
    }

    public OverclockMode getOverclockMode() {
        return overclockMode;
    }

    public void setOverclockMode(OverclockMode overclockMode) {
        this.overclockMode = overclockMode != null ? overclockMode : OverclockMode.STANDARD;
        markDirty();
    }

    public boolean isGenerator() {
        if (MultiblockDetector.isCoilMultiblock(machineIcon)
                || MultiblockDetector.isCoilRecipeCategory(recipeCategoryId)) {
            return false;
        }
        return isGenerator;
    }

    public void setGenerator(boolean generator) {
        this.isGenerator = generator;
        markDirty();
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = Math.max(0.0, Math.min(1.0, efficiency));
    }

    public EnergyType getEnergyType() {
        if (energyType != null) return energyType;
        if (owner != null) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(owner);
            return adapter != null ? adapter.getEnergyType(owner) : EnergyType.ELECTRIC_EU;
        }
        return EnergyType.ELECTRIC_EU;
    }

    public EnergyType getEnergyTypeOverride() {
        return energyType;
    }

    public void setEnergyType(EnergyType energyType) {
        this.energyType = energyType;
        markDirty();
    }

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public void setMultiblock(boolean multiblock) {
        if (this.isMultiblock == multiblock) return;
        this.isMultiblock = multiblock;
        if (owner != null) {
            NodeMultiblockHelper.configureMultiblock(owner, multiblock);
        }
        markDirty();
    }

    public List<MachineAddon> getAddons() {
        return addons;
    }

    public void addAddon(MachineAddon addon) {
        if (owner != null) {
            NodeAddonHelper.addAddon(owner, addons, addon);
        } else if (addon != null) {
            addons.add(addon);
        }
        markDirty();
    }

    public void removeSingleAddon(String addonId) {
        if (owner != null) {
            NodeAddonHelper.removeSingleAddon(owner, addons, addonId);
        } else {
            removeOneAddon(addonId);
        }
        markDirty();
    }

    public void removeAddon(String addonId) {
        addons.removeIf(a -> a.getId().equals(addonId));
        markDirty();
    }

    public boolean removeOneAddon(String addonId) {
        boolean removed = NodeAddonHelper.removeOneAddon(addons, addonId);
        if (removed) markDirty();
        return removed;
    }

    public void clearAddons() {
        addons.clear();
        markDirty();
    }

    public double getCombinedDurationMultiplier() {
        return NodeAddonHelper.getCombinedDurationMultiplier(addons);
    }

    public double getCombinedEutMultiplier() {
        return NodeAddonHelper.getCombinedEutMultiplier(addons);
    }

    public int getCombinedParallelMultiplier() {
        return NodeAddonHelper.getCombinedParallelMultiplier(addons);
    }

    public boolean hasPowerConstantAddon() {
        return NodeAddonHelper.hasPowerConstantAddon(addons);
    }

    public IModAdapter getCachedModAdapter() {
        return cachedModAdapter;
    }

    public void setCachedModAdapter(IModAdapter adapter) {
        this.cachedModAdapter = adapter;
    }

    public void invalidateModAdapterCache() {
        this.cachedModAdapter = null;
    }

    public boolean hasValidReflector() {
        if (owner == null) return true;
        return RecipeNodeReflectorHelper.hasValidReflector(owner.getProperties(), addons);
    }

    @Override
    public boolean isOperational(FlowGraph graph) {
        if (owner == null) return true;
        if (cachedOperational != null) {
            if (Boolean.FALSE.equals(cachedOperational)) return false;
            if (graph == null || cachedOperationalGraph == graph) return cachedOperational;
        }
        if (!hasValidReflector()) {
            cachedOperational = false;
            cachedOperationalGraph = null;
            return false;
        }
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(owner);
        boolean op = adapter != null ? adapter.validateNode(owner, graph, null) : true;
        if (graph != null || !op) {
            cachedOperational = op;
            cachedOperationalGraph = graph;
        }
        return op;
    }

    @Override
    public double getSingleMachinePower() {
        if (owner == null) return 0.0;
        if (cachedSingleMachinePower < 0.0) {
            cachedSingleMachinePower = NodePerformanceHelper.computeSingleMachinePower(owner);
        }
        return cachedSingleMachinePower;
    }

    @Override
    public double getTotalPower() {
        if (owner == null) return 0.0;
        return NodePerformanceHelper.computeTotalEUt(owner);
    }

    public OverclockMode.OverclockResult getOverclockResult() {
        if (overclockDirty || cachedOverclockResult == null) {
            if (owner != null) {
                cachedOverclockResult = NodePerformanceHelper.computeOverclockResult(owner);
            } else {
                cachedOverclockResult = new OverclockMode.OverclockResult(baseDurationTicks, baseEUt, 1.0, 0);
            }
            overclockDirty = false;
        }
        return cachedOverclockResult;
    }

    @Override
    public double getEffectiveDurationSeconds() {
        return getOverclockResult().durationTicks() / 20.0;
    }

    public int getTotalParallel() {
        if (customParallel > 0) return customParallel;
        if (cachedTotalParallel < 1 && owner != null) {
            cachedTotalParallel = NodePerformanceHelper.computeTotalParallel(owner);
        }
        return cachedTotalParallel > 0 ? cachedTotalParallel : 1;
    }

    public double getNominalCyclesPerSecond() {
        if (owner == null) return 0.0;
        if (cachedNominalCps < 0.0) {
            cachedNominalCps = NodePerformanceHelper.computeNominalCps(owner);
        }
        return cachedNominalCps;
    }

    @Override
    public double getCyclesPerSecond() {
        return isOperational(null) ? getNominalCyclesPerSecond() : 0.0;
    }

    @Override
    public void serializeRoleNBT(CompoundTag tag, Set<FlowGraph> visitedGraphs, int depth) {
        if (machineIcon != null) {
            tag.putString("icon", machineIcon.toString());
        }
        if (baseDurationTicks != 0.0) {
            tag.putDouble("baseDuration", baseDurationTicks);
        }
        if (baseEUt != 0.0) {
            tag.putDouble("baseEUt", baseEUt);
        }
        if (recipeTier != null) {
            tag.putString("recipeTier", recipeTier.name());
            if (targetTier != null && targetTier != recipeTier) {
                tag.putString("targetTier", targetTier.name());
            }
        }
        if (Math.abs(machineCount - 1.0) > 0.0001) {
            tag.putDouble("machineCount", machineCount);
        }
        if (parallel > 1) {
            tag.putInt("parallel", parallel);
        }
        if (customParallel > 0) {
            tag.putInt("customParallel", customParallel);
        }
        if (overclockMode != null) {
            tag.putString("overclockMode", overclockMode.name());
        }
        if (isGenerator) {
            tag.putBoolean("isGenerator", true);
        }
        if (isMultiblock) {
            tag.putBoolean("isMultiblock", true);
        }
        if (energyType != null) {
            tag.putString("energyType", energyType.name());
        }
        if (recipeCategoryId != null) {
            tag.putString("recipeCategoryId", recipeCategoryId.toString());
        }
        if (!addons.isEmpty()) {
            ListTag addonList = new ListTag();
            for (MachineAddon a : addons) {
                addonList.add(a.serializeNBT());
            }
            tag.put("addons", addonList);
        }
        serializeWorkstations(tag);
    }

    private void serializeWorkstations(CompoundTag tag) {
        if (availableWorkstations.isEmpty()) return;
        CategoryCapability cap = recipeCategoryId != null
                ? CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId)
                : null;
        List<ResourceLocation> capWs = (cap != null && cap.availableWorkstations() != null) ? cap.availableWorkstations() : List.of();
        if (availableWorkstations.equals(capWs)) return;
        ListTag wsList = new ListTag();
        for (ResourceLocation ws : availableWorkstations) {
            wsList.add(net.minecraft.nbt.StringTag.valueOf(ws.toString()));
        }
        tag.put("workstations", wsList);
    }

    @Override
    public void deserializeRoleNBT(CompoundTag tag) {
        if (tag == null) return;
        if (tag.contains("icon")) {
            this.machineIcon = ResourceLocation.tryParse(tag.getString("icon"));
        }
        if (tag.contains("baseDuration")) {
            this.baseDurationTicks = tag.getDouble("baseDuration");
        }
        if (tag.contains("baseEUt")) {
            this.baseEUt = tag.getDouble("baseEUt");
        }
        if (tag.contains("recipeTier")) {
            try {
                this.recipeTier = GTVoltageTier.valueOf(tag.getString("recipeTier"));
            } catch (IllegalArgumentException ignored) {}
        }
        if (this.recipeTier == null) {
            this.recipeTier = GTVoltageTier.getTierForVoltage((long) this.baseEUt);
        }
        if (tag.contains("targetTier")) {
            try {
                this.targetTier = GTVoltageTier.valueOf(tag.getString("targetTier"));
            } catch (IllegalArgumentException ignored) {}
        } else {
            this.targetTier = this.recipeTier;
        }
        if (tag.contains("machineCount")) {
            this.machineCount = tag.getDouble("machineCount");
        }
        if (tag.contains("parallel")) {
            this.parallel = tag.getInt("parallel");
        }
        if (tag.contains("customParallel")) {
            this.customParallel = tag.getInt("customParallel");
        }
        if (tag.contains("overclockMode")) {
            try {
                this.overclockMode = OverclockMode.valueOf(tag.getString("overclockMode"));
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("isGenerator")) {
            this.isGenerator = tag.getBoolean("isGenerator");
        }
        if (tag.contains("isMultiblock")) {
            this.isMultiblock = tag.getBoolean("isMultiblock");
        }
        if (tag.contains("energyType")) {
            try {
                this.energyType = EnergyType.valueOf(tag.getString("energyType"));
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("recipeCategoryId")) {
            this.recipeCategoryId = ResourceLocation.tryParse(tag.getString("recipeCategoryId"));
        }
        deserializeWorkstations(tag);
        deserializeAddons(tag);
        markDirty();
    }

    private void deserializeWorkstations(CompoundTag tag) {
        if (tag.contains("workstations")) {
            ListTag wsList = tag.getList("workstations", 8);
            availableWorkstations.clear();
            for (int i = 0; i < wsList.size(); i++) {
                ResourceLocation ws = ResourceLocation.tryParse(wsList.getString(i));
                if (ws != null) availableWorkstations.add(ws);
            }
        } else if (recipeCategoryId != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId);
            if (cap != null && cap.availableWorkstations() != null && !cap.availableWorkstations().isEmpty()) {
                availableWorkstations.clear();
                availableWorkstations.addAll(cap.availableWorkstations());
            }
        }
    }

    private void deserializeAddons(CompoundTag tag) {
        if (tag.contains("addons")) {
            addons.clear();
            ListTag addonList = tag.getList("addons", 10);
            for (int i = 0; i < addonList.size(); i++) {
                MachineAddon a = MachineAddon.deserializeNBT(addonList.getCompound(i));
                if (a != null) addons.add(a);
            }
        }
    }

    @Override
    public NodeCalculationSnapshot captureSnapshot(FlowGraph graph) {
        if (owner == null) return NodeCalculationSnapshot.EMPTY;
        boolean op = isOperational(graph);
        double nomCps = getNominalCyclesPerSecond();
        double effCps = op ? nomCps * efficiency : 0.0;
        double singlePower = getSingleMachinePower();
        double totPower = op ? getTotalPower() : 0.0;
        double effTotPower = totPower * efficiency;
        double durSec = getEffectiveDurationSeconds();
        int totPar = getTotalParallel();
        EnergyType et = getEnergyType();
        List<Component> warnings = owner.getOperationalWarnings(graph);

        Map<Integer, Double> inRates = new HashMap<>();
        Map<Integer, Double> effInChances = new HashMap<>();
        for (int i = 0; i < owner.getInputs().size(); i++) {
            inRates.put(i, owner.getInputSlotRate(i, true));
            effInChances.put(i, owner.getEffectiveInputChance(i));
        }
        Map<Integer, Double> outRates = new HashMap<>();
        Map<Integer, Double> effOutChances = new HashMap<>();
        for (int i = 0; i < owner.getOutputs().size(); i++) {
            outRates.put(i, owner.getOutputSlotRate(i, true));
            effOutChances.put(i, owner.getEffectiveOutputChance(i));
        }
        boolean isStarved = effCps < nomCps * 0.999 && op;
        return new NodeCalculationSnapshot(
            owner.getId(),
            NodeRoleType.MACHINE,
            nomCps,
            effCps,
            singlePower,
            totPower,
            effTotPower,
            durSec,
            totPar,
            et,
            op,
            isStarved,
            warnings != null ? List.copyOf(warnings) : List.of(),
            inRates,
            outRates,
            effInChances,
            effOutChances
        );
    }
}
