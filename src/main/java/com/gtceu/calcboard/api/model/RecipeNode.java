package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.role.BoundaryPinNodeRole;
import com.gtceu.calcboard.api.model.role.INodeRole;
import com.gtceu.calcboard.api.model.role.JunctionNodeRole;
import com.gtceu.calcboard.api.model.role.MachineNodeRole;
import com.gtceu.calcboard.api.model.role.NodeRoleType;
import com.gtceu.calcboard.api.model.role.SubPageModuleNodeRole;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.spi.extension.IPortProjectionProvider;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class RecipeNode {

    private String id;
    private String name;
    private boolean hasCustomName = false;
    private double posX;
    private double posY;
    private int cardWidth = 245;
    private int cardHeight = 0;
    private boolean isFlipped = false;
    private boolean isBaseNode = false;

    private final List<IngredientStack> inputs = new ArrayList<>();
    private final List<IngredientStack> outputs = new ArrayList<>();
    private final NodePortVisibility portVisibility = new NodePortVisibility();
    private final NodePropertyStore properties = new NodePropertyStore();
    private transient FlowGraph parentGraph = null;
    private INodeRole role;

    private RecipeSpec baseSpec;
    private transient boolean portsDirty = true;
    private transient List<ProjectedPort> projectedInputs = Collections.emptyList();
    private transient List<ProjectedPort> projectedOutputs = Collections.emptyList();

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

    public RecipeNode(String id, String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.properties.setChangeListener(() -> {
            markPortsDirty();
            markOverclockDirty();
        });
        setRole(new MachineNodeRole(baseDurationTicks, baseEUt, recipeTier));
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
        node.setRole(new JunctionNodeRole());
        node.setPos(posX, posY);
        node.setCardWidth(32);
        node.setCardHeight(32);
        return node;
    }

    public RecipeNode(RecipeNode other) {
        this(other, UUID.randomUUID().toString());
    }

    public RecipeNode(RecipeNode other, String newId) {
        this(other, newId, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    public RecipeNode(RecipeNode other, String newId, Set<FlowGraph> visitedGraphs, int depth) {
        Objects.requireNonNull(other, "other node cannot be null");
        this.id = newId != null ? newId : UUID.randomUUID().toString();
        copyFieldsFrom(other, visitedGraphs, depth);
    }

    protected void copyFieldsFrom(RecipeNode other, Set<FlowGraph> visitedGraphs, int depth) {
        this.posX = other.posX;
        this.posY = other.posY;
        this.isFlipped = other.isFlipped;
        this.isBaseNode = other.isBaseNode;
        this.baseSpec = other.baseSpec;

        if (other.role != null) {
            setRole(other.role.copy(visitedGraphs, depth));
        }
        this.name = other.name;
        this.hasCustomName = other.hasCustomName;
        this.cardWidth = other.cardWidth;
        this.cardHeight = other.cardHeight;

        this.inputs.clear();
        for (IngredientStack stack : other.inputs) {
            if (stack != null) {
                this.inputs.add(stack.copy());
            }
        }
        this.outputs.clear();
        for (IngredientStack stack : other.outputs) {
            if (stack != null) {
                this.outputs.add(stack.copy());
            }
        }

        this.portVisibility.copyFrom(other.portVisibility);
        this.properties.copyFrom(other.properties);
        this.properties.setChangeListener(() -> {
            markPortsDirty();
            markOverclockDirty();
        });

        markPortsDirty();
        markOverclockDirty();
        syncProjectedPorts();
    }

    public RecipeNode copy() {
        return copy(UUID.randomUUID().toString());
    }

    public RecipeNode copy(String newId) {
        return copy(newId, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    public RecipeNode copy(Set<FlowGraph> visitedGraphs, int depth) {
        return copy(UUID.randomUUID().toString(), visitedGraphs, depth);
    }

    public RecipeNode copy(String newId, Set<FlowGraph> visitedGraphs, int depth) {
        if (this instanceof BoundaryPinNode pin) {
            BoundaryPinNode cp = pin.getDirection() == BoundaryPinNode.PinDirection.OUTPUT
                    ? new ModuleOutputPin(newId, pin.getPinLabel(), pin.getBoundIngredient())
                    : new ModuleInputPin(newId, pin.getPinLabel(), pin.getBoundIngredient());
            cp.copyFieldsFrom(this, visitedGraphs, depth);
            return cp;
        }
        return new RecipeNode(this, newId, visitedGraphs, depth);
    }

    public INodeRole getRole() {
        return role;
    }

    public void setRole(INodeRole newRole) {
        Objects.requireNonNull(newRole, "role cannot be null");
        boolean wasFixed = this.role != null && this.role.isFixedSize();
        if (this.role != null) {
            this.role.detach();
        }
        this.role = newRole;
        if (newRole.isFixedSize()) {
            this.cardWidth = newRole.getDefaultCardWidth();
            this.cardHeight = newRole.getDefaultCardHeight();
        } else if (wasFixed) {
            this.cardWidth = newRole.getDefaultCardWidth();
            this.cardHeight = newRole.getDefaultCardHeight();
        }
        this.role.attach(this);
        markOverclockDirty();
    }

    @SuppressWarnings("unchecked")
    public <T extends INodeRole> Optional<T> getRole(Class<T> roleClass) {
        if (roleClass != null && roleClass.isInstance(this.role)) {
            return Optional.of((T) this.role);
        }
        return Optional.empty();
    }

    public boolean isMachine() {
        return role != null && role.getRoleType() == NodeRoleType.MACHINE;
    }

    public boolean isModule() {
        return role != null && role.getRoleType() == NodeRoleType.MODULE;
    }

    public boolean isJunction() {
        return role != null && role.getRoleType() == NodeRoleType.JUNCTION;
    }

    public boolean isBoundaryPin() {
        return role != null && role.getRoleType() == NodeRoleType.BOUNDARY_PIN;
    }

    public MachineNodeRole asMachine() {
        if (role instanceof MachineNodeRole machineRole) return machineRole;
        throw new IllegalStateException("Node " + id + " is not a machine (role=" + (role != null ? role.getRoleType() : "null") + ")");
    }

    public SubPageModuleNodeRole asModule() {
        if (role instanceof SubPageModuleNodeRole moduleRole) return moduleRole;
        throw new IllegalStateException("Node " + id + " is not a module (role=" + (role != null ? role.getRoleType() : "null") + ")");
    }

    public JunctionNodeRole asJunction() {
        if (role instanceof JunctionNodeRole junctionRole) return junctionRole;
        throw new IllegalStateException("Node " + id + " is not a junction (role=" + (role != null ? role.getRoleType() : "null") + ")");
    }

    public BoundaryPinNodeRole asBoundaryPin() {
        if (role instanceof BoundaryPinNodeRole pinRole) return pinRole;
        throw new IllegalStateException("Node " + id + " is not a boundary pin (role=" + (role != null ? role.getRoleType() : "null") + ")");
    }

    public boolean isFlipped() { return isFlipped; }
    public void setFlipped(boolean flipped) { this.isFlipped = flipped; }
    public void toggleFlipped() { this.isFlipped = !this.isFlipped; }

    public String getId() { return id; }
    public void setId(String id) {
        this.id = id;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRawName() { return name != null ? name : ""; }
    public boolean hasCustomName() { return hasCustomName; }
    public void setHasCustomName(boolean hasCustomName) { this.hasCustomName = hasCustomName; }

    public String getMachineDisplayName() {
        if (isMachine() && asMachine().getMachineIcon() != null) {
            return NodeWorkstationResolver.getWorkstationDisplayName(asMachine().getMachineIcon());
        }
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return "Unknown Machine";
    }

    public ResourceLocation getMachineIcon() {
        return isMachine() ? asMachine().getMachineIcon() : null;
    }

    public void setMachineIcon(ResourceLocation machineIcon) {
        if (isMachine()) {
            asMachine().setMachineIcon(machineIcon);
            markPortsDirty();
        }
    }

    public double getBaseDurationTicks() {
        if (isMachine()) return asMachine().getBaseDurationTicks();
        if (isModule()) return asModule().getBaseDurationTicks();
        return 0.0;
    }

    public void setBaseDurationTicks(double baseDurationTicks) {
        if (isMachine()) {
            asMachine().setBaseDurationTicks(baseDurationTicks);
        } else if (isModule()) {
            asModule().setBaseDurationTicks(baseDurationTicks);
        }
        if (baseSpec != null) {
            this.baseSpec = new RecipeSpec(baseSpec.recipeId(), baseSpec.categoryId(), baseDurationTicks, baseSpec.baseEUt(), baseSpec.baseInputs(), baseSpec.baseOutputs());
        }
    }

    public double getBaseEUt() {
        if (isMachine()) return asMachine().getBaseEUt();
        if (isModule()) return asModule().getBaseEUt();
        return 0.0;
    }

    public void setBaseEUt(double baseEUt) {
        if (isMachine()) {
            asMachine().setBaseEUt(baseEUt);
        } else if (isModule()) {
            asModule().setBaseEUt(baseEUt);
        }
        if (baseSpec != null) {
            this.baseSpec = new RecipeSpec(baseSpec.recipeId(), baseSpec.categoryId(), baseSpec.baseDurationTicks(), baseEUt, baseSpec.baseInputs(), baseSpec.baseOutputs());
        }
    }

    public GTVoltageTier getRecipeTier() {
        return isMachine() ? asMachine().getRecipeTier() : GTVoltageTier.ULV;
    }

    public void setRecipeTier(GTVoltageTier recipeTier) {
        if (isMachine()) {
            asMachine().setRecipeTier(recipeTier);
        }
    }

    public GTVoltageTier getTargetTier() {
        if (isMachine()) return asMachine().getTargetTier();
        if (isModule()) return asModule().getTargetTier();
        return GTVoltageTier.ULV;
    }

    public void setTargetTier(GTVoltageTier targetTier) {
        if (isMachine()) {
            asMachine().setTargetTier(targetTier);
        } else if (isModule()) {
            asModule().setTargetTier(targetTier);
        }
    }

    public ResourceLocation getWorkstationForTier(GTVoltageTier tier) {
        return NodeWorkstationResolver.getWorkstationForTier(this, tier);
    }

    public ResourceLocation getWorkstationForTierFromList(GTVoltageTier tier) {
        return NodeWorkstationResolver.getWorkstationForTierFromList(this, tier);
    }

    public double getMachineCount() {
        if (isMachine()) return asMachine().getMachineCount();
        if (isModule()) return asModule().getScaleMultiplier();
        return 1.0;
    }

    public void setMachineCount(double machineCount) {
        if (isMachine()) {
            asMachine().setMachineCount(machineCount);
        } else if (isModule()) {
            asModule().setScaleMultiplier(machineCount);
        }
    }

    public int getParallel() {
        return isMachine() ? asMachine().getParallel() : 1;
    }

    public void setParallel(int parallel) {
        if (isMachine()) {
            asMachine().setParallel(parallel);
        }
    }

    public OverclockMode getOverclockMode() {
        return isMachine() ? asMachine().getOverclockMode() : OverclockMode.STANDARD;
    }

    public void setOverclockMode(OverclockMode overclockMode) {
        if (isMachine()) {
            asMachine().setOverclockMode(overclockMode);
        }
    }

    public boolean isBaseNode() { return isBaseNode; }
    public void setBaseNode(boolean baseNode) { this.isBaseNode = baseNode; }

    public boolean isGenerator() {
        if (isMachine()) return asMachine().isGenerator();
        if (isModule()) return asModule().isGenerator();
        return false;
    }

    public void setGenerator(boolean generator) {
        if (isMachine()) {
            asMachine().setGenerator(generator);
        } else if (isModule()) {
            asModule().setGenerator(generator);
        }
    }

    public NodePropertyStore getProperties() { return properties; }

    public EnergyType getEnergyType() {
        if (isMachine()) return asMachine().getEnergyType();
        if (isModule()) return asModule().getEnergyType();
        return EnergyType.NONE;
    }

    public EnergyType getEnergyTypeOverride() {
        return isMachine() ? asMachine().getEnergyTypeOverride() : null;
    }

    public void setEnergyType(EnergyType energyType) {
        if (isMachine()) {
            asMachine().setEnergyType(energyType);
        } else if (isModule()) {
            asModule().setEnergyType(energyType);
        }
    }

    public double getPosX() { return posX; }
    public void setPosX(double posX) { this.posX = posX; }
    public double getPosY() { return posY; }
    public void setPosY(double posY) { this.posY = posY; }
    public void setPos(double posX, double posY) { this.posX = posX; this.posY = posY; }

    public int getCardWidth() {
        if (role != null && role.isFixedSize()) {
            return role.getDefaultCardWidth();
        }
        return Math.max(245, Math.min(500, cardWidth));
    }

    public void setCardWidth(int cardWidth) {
        if (role != null && role.isFixedSize()) {
            this.cardWidth = role.getDefaultCardWidth();
            return;
        }
        this.cardWidth = Math.max(245, Math.min(500, cardWidth));
    }

    public int getCardHeight() {
        if (role != null && role.isFixedSize()) {
            return role.getDefaultCardHeight();
        }
        return Math.max(0, Math.min(600, cardHeight));
    }

    public void setCardHeight(int cardHeight) {
        if (role != null && role.isFixedSize()) {
            this.cardHeight = role.getDefaultCardHeight();
            return;
        }
        this.cardHeight = Math.max(0, Math.min(600, cardHeight));
    }

    public double getEfficiency() {
        if (isMachine()) {
            return asMachine().getEfficiency();
        }
        if (isModule()) {
            return asModule().getEfficiency();
        }
        return 1.0;
    }

    public void setEfficiency(double efficiency) {
        if (isMachine()) {
            asMachine().setEfficiency(efficiency);
        } else if (isModule()) {
            asModule().setEfficiency(efficiency);
        }
    }

    public boolean isReroute() {
        return isJunction();
    }

    public void setReroute(boolean reroute) {
        if (reroute) {
            if (!isJunction()) {
                setRole(new JunctionNodeRole());
                setCardWidth(32);
                setCardHeight(32);
            }
        } else if (isJunction()) {
            setRole(new MachineNodeRole());
            setCardWidth(245);
            setCardHeight(0);
        }
    }

    public SupplyMode getSupplyMode() {
        return isJunction() ? asJunction().getSupplyMode() : SupplyMode.NONE;
    }

    public void setSupplyMode(SupplyMode supplyMode) {
        if (isJunction()) {
            asJunction().setSupplyMode(supplyMode);
        }
    }

    public boolean isExternalSupply() {
        return isJunction() && asJunction().isExternalSupply();
    }

    public boolean isInfiniteSupply() {
        return isJunction() && asJunction().isInfiniteSupply();
    }

    public boolean isVoidSink() {
        return isJunction() && asJunction().isVoidSink();
    }

    public boolean isFixedDrain() {
        return isJunction() && asJunction().isFixedDrain();
    }

    public double getExternalSupplyRate() {
        return isJunction() ? asJunction().getExternalSupplyRate() : 0.0;
    }

    public void setExternalSupplyRate(double rate) {
        if (isJunction()) {
            asJunction().setExternalSupplyRate(rate);
        }
    }

    public double getExternalDrainRate() {
        return isJunction() ? asJunction().getExternalDrainRate() : 0.0;
    }

    public void setExternalDrainRate(double rate) {
        if (isJunction()) {
            asJunction().setExternalDrainRate(rate);
        }
    }

    public int getCustomParallel() {
        return isMachine() ? asMachine().getCustomParallel() : 0;
    }

    public void setCustomParallel(int customParallel) {
        if (isMachine()) {
            asMachine().setCustomParallel(customParallel);
        }
    }

    public void bindRerouteIngredient(IngredientStack stack) {
        if (isJunction()) {
            asJunction().bindIngredient(stack);
        } else {
            NodeJunctionHelper.bindRerouteIngredient(this, stack);
        }
    }

    public void unbindRerouteIngredient() {
        if (isJunction()) {
            asJunction().unbindIngredient();
        } else {
            NodeJunctionHelper.unbindRerouteIngredient(this);
        }
    }

    public IngredientStack getRerouteIngredient() {
        if (isJunction()) {
            IngredientStack bound = asJunction().getBoundIngredient();
            if (bound != null) return bound;
        }
        return !outputs.isEmpty() ? outputs.get(0) : null;
    }

    public double getTargetBatchAmount() { return properties.get(NodeProperties.TARGET_BATCH_AMOUNT); }
    public void setTargetBatchAmount(double amount) { properties.set(NodeProperties.TARGET_BATCH_AMOUNT, Math.max(0.0, amount)); }
    public boolean hasTargetBatch() { return getTargetBatchAmount() > 0.0001; }

    public boolean isJunctionBuffer() {
        return isJunction() ? asJunction().isBuffer() : properties.get(NodeProperties.JUNCTION_IS_BUFFER);
    }

    public void setJunctionBuffer(boolean isBuffer) {
        if (isJunction()) {
            asJunction().setBuffer(isBuffer);
        } else {
            properties.set(NodeProperties.JUNCTION_IS_BUFFER, isBuffer);
        }
    }

    public double getJunctionBufferSize() {
        return isJunction() ? asJunction().getBufferSize() : properties.get(NodeProperties.JUNCTION_BUFFER_SIZE);
    }

    public void setJunctionBufferSize(double size) {
        if (isJunction()) {
            asJunction().setBufferSize(size);
        } else {
            properties.set(NodeProperties.JUNCTION_BUFFER_SIZE, Math.max(0.0, size));
        }
    }

    public double getJunctionChargeDuration(FlowGraph graph) {
        return isJunction() ? asJunction().getChargeDuration(graph) : NodeJunctionHelper.getJunctionChargeDuration(this, graph);
    }

    public com.gtceu.calcboard.api.type.FlowSplitMode getJunctionSplitMode() {
        return isJunction() ? asJunction().getSplitMode() : properties.get(NodeProperties.JUNCTION_SPLIT_MODE);
    }

    public void setJunctionSplitMode(com.gtceu.calcboard.api.type.FlowSplitMode mode) {
        com.gtceu.calcboard.api.type.FlowSplitMode target = mode != null ? mode : com.gtceu.calcboard.api.type.FlowSplitMode.PROPORTIONAL;
        if (isJunction()) {
            asJunction().setSplitMode(target);
        } else {
            properties.set(NodeProperties.JUNCTION_SPLIT_MODE, target);
        }
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

    public void setModule(boolean module) {
        if (module) {
            if (!isModule()) {
                double baseDur = isMachine() ? asMachine().getBaseDurationTicks() : 20.0;
                double baseEu = isMachine() ? asMachine().getBaseEUt() : 0.0;
                GTVoltageTier tier = isMachine() ? asMachine().getTargetTier() : GTVoltageTier.LV;
                boolean gen = isMachine() && asMachine().isGenerator();
                SubPageModuleNodeRole moduleRole = new SubPageModuleNodeRole();
                moduleRole.setBaseDurationTicks(baseDur);
                moduleRole.setBaseEUt(baseEu);
                moduleRole.setTargetTier(tier);
                moduleRole.setGenerator(gen);
                setRole(moduleRole);
            }
        } else if (isModule()) {
            double baseDur = asModule().getBaseDurationTicks();
            double baseEu = asModule().getBaseEUt();
            GTVoltageTier tier = asModule().getTargetTier();
            boolean gen = asModule().isGenerator();
            MachineNodeRole machineRole = new MachineNodeRole(baseDur, baseEu, tier);
            machineRole.setGenerator(gen);
            setRole(machineRole);
        }
    }

    public FlowGraph getSubGraph() {
        return isModule() ? asModule().getSubGraph() : null;
    }

    public void setSubGraph(FlowGraph subGraph) {
        if (isModule()) {
            asModule().setSubGraph(subGraph);
        }
    }

    public int getContainedMachineCount() {
        return isModule() ? asModule().getContainedMachineCount() : 0;
    }

    public void setContainedMachineCount(int count) {
        if (isModule()) {
            asModule().setContainedMachineCount(count);
        }
    }

    public String getSubPageId() {
        return isModule() ? asModule().getSubPageId() : "";
    }

    public void setSubPageId(String subPageId) {
        if (isModule()) {
            asModule().setSubPageId(subPageId);
        }
    }

    public List<String> getInputPinNodeIds() {
        return isModule() ? asModule().getInputPinNodeIds() : Collections.emptyList();
    }

    public List<String> getOutputPinNodeIds() {
        return isModule() ? asModule().getOutputPinNodeIds() : Collections.emptyList();
    }

    public com.gtceu.calcboard.api.storage.BoardPage getDedicatedSubPage() {
        String pageId = getSubPageId();
        if (pageId.isEmpty()) return null;
        return com.gtceu.calcboard.api.storage.BoardManager.getInstance().getPage(pageId).orElse(null);
    }

    public List<List<PortOrigin>> getModuleInputOrigins() {
        return getPortOriginManager().getInputOrigins();
    }

    public List<List<PortOrigin>> getModuleOutputOrigins() {
        return getPortOriginManager().getOutputOrigins();
    }

    public NodePortOriginManager getPortOriginManager() {
        return isModule() ? asModule().getPortOriginManager() : new NodePortOriginManager();
    }

    public List<IngredientStack> getInputs() { return inputs; }
    public List<IngredientStack> getOutputs() { return outputs; }

    public void addInput(IngredientStack stack) {
        if (stack == null) return;
        if (hasAuxiliaryInputPorts()) {
            List<IngredientStack> coreInputs = extractCoreInputs();
            coreInputs.add(stack);
            List<IngredientStack> coreOutputs = extractCoreOutputs();
            String recipeId = baseSpec != null ? baseSpec.recipeId() : id;
            ResourceLocation catId = baseSpec != null ? baseSpec.categoryId() : getRecipeCategoryId();
            this.baseSpec = new RecipeSpec(
                    recipeId,
                    catId,
                    getBaseDurationTicks(),
                    getBaseEUt(),
                    coreInputs,
                    coreOutputs
            );
            syncProjectedPorts();
        } else {
            inputs.add(stack);
            if (baseSpec != null) {
                List<IngredientStack> newInputs = new ArrayList<>(baseSpec.baseInputs());
                newInputs.add(stack);
                this.baseSpec = new RecipeSpec(
                        baseSpec.recipeId(),
                        baseSpec.categoryId(),
                        baseSpec.baseDurationTicks(),
                        baseSpec.baseEUt(),
                        newInputs,
                        baseSpec.baseOutputs()
                );
            }
            markPortsDirty();
        }
    }

    public void addOutput(IngredientStack stack) {
        if (stack == null) return;
        if (hasAuxiliaryOutputPorts()) {
            List<IngredientStack> coreInputs = extractCoreInputs();
            List<IngredientStack> coreOutputs = extractCoreOutputs();
            coreOutputs.add(stack);
            String recipeId = baseSpec != null ? baseSpec.recipeId() : id;
            ResourceLocation catId = baseSpec != null ? baseSpec.categoryId() : getRecipeCategoryId();
            this.baseSpec = new RecipeSpec(
                    recipeId,
                    catId,
                    getBaseDurationTicks(),
                    getBaseEUt(),
                    coreInputs,
                    coreOutputs
            );
            syncProjectedPorts();
        } else {
            outputs.add(stack);
            if (baseSpec != null) {
                List<IngredientStack> newOutputs = new ArrayList<>(baseSpec.baseOutputs());
                newOutputs.add(stack);
                this.baseSpec = new RecipeSpec(
                        baseSpec.recipeId(),
                        baseSpec.categoryId(),
                        baseSpec.baseDurationTicks(),
                        baseSpec.baseEUt(),
                        baseSpec.baseInputs(),
                        newOutputs
                );
            }
            markPortsDirty();
        }
    }

    public boolean hasAuxiliaryInputPorts() {
        ensurePortsProjected();
        for (ProjectedPort p : projectedInputs) {
            if (p.isAuxiliary()) return true;
        }
        return false;
    }

    public boolean hasAuxiliaryOutputPorts() {
        ensurePortsProjected();
        for (ProjectedPort p : projectedOutputs) {
            if (p.isAuxiliary()) return true;
        }
        return false;
    }

    public RecipeSpec getBaseSpec() {
        if (baseSpec == null) {
            baseSpec = new RecipeSpec(
                    id,
                    getRecipeCategoryId(),
                    getBaseDurationTicks(),
                    getBaseEUt(),
                    extractCoreInputs(),
                    extractCoreOutputs()
            );
        }
        return baseSpec;
    }

    private List<IngredientStack> extractCoreInputs() {
        if (baseSpec != null && baseSpec.baseInputs() != null) {
            List<IngredientStack> list = new ArrayList<>(baseSpec.baseInputs().size());
            for (IngredientStack in : baseSpec.baseInputs()) {
                list.add(in.copy());
            }
            return list;
        }
        if (projectedInputs != null && !projectedInputs.isEmpty()) {
            List<IngredientStack> list = new ArrayList<>();
            for (ProjectedPort p : projectedInputs) {
                if (p.isCore()) {
                    list.add(p.stack().copy());
                }
            }
            return list;
        }
        List<IngredientStack> list = new ArrayList<>(inputs.size());
        for (IngredientStack in : inputs) {
            list.add(in.copy());
        }
        return list;
    }

    private List<IngredientStack> extractCoreOutputs() {
        if (baseSpec != null && baseSpec.baseOutputs() != null) {
            List<IngredientStack> list = new ArrayList<>(baseSpec.baseOutputs().size());
            for (IngredientStack out : baseSpec.baseOutputs()) {
                list.add(out.copy());
            }
            return list;
        }
        if (projectedOutputs != null && !projectedOutputs.isEmpty()) {
            List<IngredientStack> list = new ArrayList<>();
            for (ProjectedPort p : projectedOutputs) {
                if (p.isCore()) {
                    list.add(p.stack().copy());
                }
            }
            return list;
        }
        List<IngredientStack> list = new ArrayList<>(outputs.size());
        for (IngredientStack out : outputs) {
            list.add(out.copy());
        }
        return list;
    }

    public void setBaseSpec(RecipeSpec baseSpec) {
        setBaseSpecOnly(baseSpec);
        if (baseSpec != null) {
            this.inputs.clear();
            for (IngredientStack in : baseSpec.baseInputs()) {
                this.inputs.add(in.copy());
            }
            this.outputs.clear();
            for (IngredientStack out : baseSpec.baseOutputs()) {
                this.outputs.add(out.copy());
            }
            syncProjectedPorts();
        } else {
            this.inputs.clear();
            this.outputs.clear();
            markPortsDirty();
        }
    }

    public void setBaseSpecOnly(RecipeSpec baseSpec) {
        this.baseSpec = baseSpec;
        markPortsDirty();
    }

    public void restoreBaseRecipe() {
        RecipeSpec spec = getBaseSpec();
        if (spec != null) {
            this.inputs.clear();
            for (IngredientStack in : spec.baseInputs()) {
                this.inputs.add(in.copy());
            }
            this.outputs.clear();
            for (IngredientStack out : spec.baseOutputs()) {
                this.outputs.add(out.copy());
            }
            markPortsDirty();
        }
    }

    public void markPortsDirty() {
        this.portsDirty = true;
        markOverclockDirty();
    }

    public boolean isPortsDirty() {
        return portsDirty;
    }

    public void ensurePortsProjected() {
        if (!portsDirty && projectedInputs != null) {
            return;
        }
        RecipeSpec spec = getBaseSpec();
        IPortProjectionProvider provider = ModAdapterRegistry.findExtension(this, IPortProjectionProvider.class).orElse(null);
        if (provider == null) {
            provider = ModAdapterRegistry.getAdapterForNode(this);
        }
        List<ProjectedPort> in = provider != null ? provider.projectInputPorts(this, spec) : null;
        this.projectedInputs = in != null ? in : defaultProjectInputPorts(spec);
        List<ProjectedPort> out = provider != null ? provider.projectOutputPorts(this, spec) : null;
        this.projectedOutputs = out != null ? out : defaultProjectOutputPorts(spec);
        this.portsDirty = false;
    }

    private List<ProjectedPort> defaultProjectInputPorts(RecipeSpec spec) {
        if (spec == null || spec.baseInputs() == null) return Collections.emptyList();
        List<ProjectedPort> list = new ArrayList<>(spec.baseInputs().size());
        for (int i = 0; i < spec.baseInputs().size(); i++) {
            list.add(ProjectedPort.ofCore(spec.baseInputs().get(i), i));
        }
        return Collections.unmodifiableList(list);
    }

    private List<ProjectedPort> defaultProjectOutputPorts(RecipeSpec spec) {
        if (spec == null || spec.baseOutputs() == null) return Collections.emptyList();
        List<ProjectedPort> list = new ArrayList<>(spec.baseOutputs().size());
        for (int i = 0; i < spec.baseOutputs().size(); i++) {
            list.add(ProjectedPort.ofCore(spec.baseOutputs().get(i), i));
        }
        return Collections.unmodifiableList(list);
    }

    public List<ProjectedPort> getProjectedInputs() {
        ensurePortsProjected();
        return projectedInputs;
    }

    public List<ProjectedPort> getProjectedOutputs() {
        ensurePortsProjected();
        return projectedOutputs;
    }

    public ProjectedPort getProjectedInput(int index) {
        List<ProjectedPort> list = getProjectedInputs();
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    public ProjectedPort getProjectedOutput(int index) {
        List<ProjectedPort> list = getProjectedOutputs();
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    public boolean isAuxiliaryInputPort(int index) {
        ProjectedPort p = getProjectedInput(index);
        return p != null && p.isAuxiliary();
    }

    public boolean isAuxiliaryOutputPort(int index) {
        ProjectedPort p = getProjectedOutput(index);
        return p != null && p.isAuxiliary();
    }

    public void syncProjectedPorts() {
        markPortsDirty();
        ensurePortsProjected();
        this.inputs.clear();
        for (ProjectedPort p : this.projectedInputs) {
            this.inputs.add(p.stack().copy());
        }
        this.outputs.clear();
        for (ProjectedPort p : this.projectedOutputs) {
            this.outputs.add(p.stack().copy());
        }
        markOverclockDirty();
    }

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
    public Set<Integer> getVoidedOutputIndices() { return portVisibility.getVoidedOutputIndices(); }
    public int getVoidedOutputCount() { return portVisibility.getVoidedOutputCount(); }
    public Set<Integer> getHiddenInputIndices() { return portVisibility.getHiddenInputIndices(); }
    public Set<Integer> getHiddenOutputIndices() { return portVisibility.getHiddenOutputIndices(); }
    public int getHiddenInputCount() { return portVisibility.getHiddenInputCount(); }
    public int getHiddenOutputCount() { return portVisibility.getHiddenOutputCount(); }
    public int getTotalHiddenCount() { return portVisibility.getTotalHiddenCount(); }
    public List<Integer> getVisibleInputIndices() { return portVisibility.getVisibleInputIndices(inputs.size()); }
    public List<Integer> getVisibleOutputIndices() { return portVisibility.getVisibleOutputIndices(outputs.size()); }

    public List<ResourceLocation> getAvailableWorkstations() {
        return isMachine() ? asMachine().getAvailableWorkstations() : Collections.emptyList();
    }

    public void setAvailableWorkstations(List<ResourceLocation> availableWorkstations) {
        if (isMachine()) {
            asMachine().setAvailableWorkstations(availableWorkstations);
        }
    }

    public ResourceLocation getRecipeCategoryId() {
        return isMachine() ? asMachine().getRecipeCategoryId() : null;
    }

    public void setRecipeCategoryId(ResourceLocation recipeCategoryId) {
        if (isMachine()) {
            asMachine().setRecipeCategoryId(recipeCategoryId);
        }
        if (baseSpec != null) {
            this.baseSpec = new RecipeSpec(
                    baseSpec.recipeId(),
                    recipeCategoryId,
                    baseSpec.baseDurationTicks(),
                    baseSpec.baseEUt(),
                    baseSpec.baseInputs(),
                    baseSpec.baseOutputs()
            );
        }
    }

    public static boolean isMultiblockWorkstation(ResourceLocation ws) {
        return NodeWorkstationResolver.isMultiblockWorkstation(ws);
    }

    public List<MachineAddon> getAddons() {
        return isMachine() ? asMachine().getAddons() : Collections.emptyList();
    }

    public void addAddon(MachineAddon addon) {
        if (isMachine()) {
            asMachine().addAddon(addon);
            markPortsDirty();
        }
    }

    public void removeSingleAddon(String addonId) {
        if (isMachine()) {
            asMachine().removeSingleAddon(addonId);
            markPortsDirty();
        }
    }

    public void removeAddon(String addonId) {
        if (isMachine()) {
            asMachine().removeAddon(addonId);
            markPortsDirty();
        }
    }

    public boolean removeOneAddon(String addonId) {
        if (isMachine() && asMachine().removeOneAddon(addonId)) {
            markPortsDirty();
            return true;
        }
        return false;
    }

    public void clearAddons() {
        if (isMachine()) {
            asMachine().clearAddons();
            markPortsDirty();
        }
    }

    public double getCombinedDurationMultiplier() {
        return isMachine() ? asMachine().getCombinedDurationMultiplier() : 1.0;
    }

    public double getCombinedEutMultiplier() {
        return isMachine() ? asMachine().getCombinedEutMultiplier() : 1.0;
    }

    public int getCombinedParallelMultiplier() {
        return isMachine() ? asMachine().getCombinedParallelMultiplier() : 1;
    }

    public boolean hasPowerConstantAddon() {
        return isMachine() && asMachine().hasPowerConstantAddon();
    }

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
    public boolean isExplicitThreadingMachine() {
        ResourceLocation icon = getMachineIcon();
        return icon != null && com.gtceu.calcboard.api.catalog.MultiblockDetector.isThreadingMultiblock(icon);
    }
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
        return RecipeNodeReflectorHelper.getInstalledReflectorTier(getAddons());
    }

    public boolean hasValidReflector() {
        return RecipeNodeReflectorHelper.hasValidReflector(properties, getAddons());
    }

    public FlowGraph getParentGraph() {
        return parentGraph;
    }

    public void setParentGraph(FlowGraph parentGraph) {
        this.parentGraph = parentGraph;
        markOperationalDirty();
    }

    public boolean isOperational() {
        return isOperational(null);
    }

    public void markOperationalDirty() {
        if (role != null) role.markDirty();
    }

    public boolean isOperational(FlowGraph graph) {
        return role != null && role.isOperational(graph);
    }

    public List<Component> getOperationalWarnings(FlowGraph graph) {
        return NodeMultiblockHelper.getOperationalWarnings(this, graph);
    }

    public boolean isMultiblock() {
        return isMachine() && asMachine().isMultiblock();
    }

    public void setMultiblock(boolean multiblock) {
        if (isMachine()) {
            asMachine().setMultiblock(multiblock);
        }
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
        if (getTargetTier() == null || getRecipeTier() == null) return 0;
        return ModAdapterRegistry.getAdapterForNode(this).calculateTierDelta(this, getTargetTier(), getRecipeTier());
    }

    public void markOverclockDirty() {
        if (role != null) {
            role.markDirty();
        }
    }

    public IModAdapter getCachedModAdapter() {
        return isMachine() ? asMachine().getCachedModAdapter() : null;
    }

    public void setCachedModAdapter(IModAdapter adapter) {
        if (isMachine()) {
            asMachine().setCachedModAdapter(adapter);
        }
    }

    public void invalidateModAdapterCache() {
        if (isMachine()) {
            asMachine().invalidateModAdapterCache();
        }
    }

    public OverclockMode.OverclockResult getOverclockResult() {
        if (isMachine()) {
            return asMachine().getOverclockResult();
        }
        if (isModule()) {
            return new OverclockMode.OverclockResult(getBaseDurationTicks(), getBaseEUt(), 1.0, 0);
        }
        return new OverclockMode.OverclockResult(20.0, 0.0, 1.0, 0);
    }

    public double getEffectiveDurationSeconds() {
        return role != null ? role.getEffectiveDurationSeconds() : 1.0;
    }

    public int getTotalParallel() {
        return isMachine() ? asMachine().getTotalParallel() : 1;
    }

    public double getSingleMachineEUt() {
        return role != null ? role.getSingleMachinePower() : 0.0;
    }

    public double getTotalEUt() {
        return role != null ? role.getTotalPower() : 0.0;
    }

    public double getEffectiveTotalEUt() {
        return getTotalEUt() * getEfficiency();
    }

    public double getNominalCyclesPerSecond() {
        return isMachine() ? asMachine().getNominalCyclesPerSecond() : (role != null ? role.getCyclesPerSecond() : 1.0);
    }

    public double getCyclesPerSecond() {
        return role != null ? role.getCyclesPerSecond() : 1.0;
    }

    public double getEffectiveCyclesPerSecond() {
        return getCyclesPerSecond() * getEfficiency();
    }

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
