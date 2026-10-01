package com.gtceu.calcboard.api.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;

import java.util.*;

/**
 * Visual canvas group frame that encloses multiple machine/reroute nodes.
 * Supports theme colors, title headers, sticky notes, group movement, and module roundtrip conversion.
 */
public class CanvasGroupFrame {

    // Preset theme color palette (ARGB)
    public static final int COLOR_BLUE = 0xFF3B82F6;
    public static final int COLOR_EMERALD = 0xFF10B981;
    public static final int COLOR_PURPLE = 0xFF8B5CF6;
    public static final int COLOR_AMBER = 0xFFF59E0B;
    public static final int COLOR_ROSE = 0xFFF43F5E;
    public static final int COLOR_SLATE = 0xFF64748B;
    public static final int COLOR_CYAN = 0xFF06B6D4;
    public static final int COLOR_LIME = 0xFF84CC16;

    public static final int[] PALETTE = new int[]{
            COLOR_BLUE, COLOR_EMERALD, COLOR_PURPLE, COLOR_AMBER, COLOR_ROSE, COLOR_SLATE, COLOR_CYAN, COLOR_LIME
    };

    public static final double HEADER_HEIGHT = 24.0;
    public static final double MIN_WIDTH = 120.0;
    public static final double MIN_SHARED_FRAME_WIDTH = 280.0;
    public static final double MIN_HEIGHT = 80.0;
    public static final double DEFAULT_PADDING = 24.0;

    private String id;
    private String title;
    private int color;
    private double posX;
    private double posY;
    private double width;
    private double height;
    private String note;
    private final Set<String> containedNodeIds = new LinkedHashSet<>();
    private boolean isCompoundFrame = false;
    private String compoundGroupId = "";
    private boolean isSharedMachineFrame = false;
    private double targetPoolCapacity = 1.0;
    private boolean isFolded = false;
    private PoolViewMode poolViewMode = PoolViewMode.EXPANDED_FRAME;
    private ResourceLocation sharedMachineId = null;
    private ResourceLocation sharedRecipeCategoryId = null;
    private com.gtceu.calcboard.api.type.GTVoltageTier sharedTier = null;
    private double savedUnfoldedWidth = 0.0;
    private double savedUnfoldedHeight = 0.0;
    private transient com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.FoldedPortSummary cachedFoldedPortSummary = null;

    public CanvasGroupFrame(String id, String title, int color, double posX, double posY, double width, double height) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title != null ? title : "Group Frame";
        this.color = color != 0 ? color : COLOR_BLUE;
        this.posX = posX;
        this.posY = posY;
        this.width = Math.max(MIN_WIDTH, width);
        this.height = Math.max(MIN_HEIGHT, height);
        this.note = "";
    }

    public static CanvasGroupFrame createFromNodes(String title, Collection<RecipeNode> nodes, int color) {
        return createFromElements(title, nodes, Collections.emptyList(), color);
    }

    public static CanvasGroupFrame createFromElements(String title, Collection<RecipeNode> nodes, Collection<CanvasStickyNote> notes, int color) {
        String frameId = UUID.randomUUID().toString();
        CanvasGroupFrame frame = new CanvasGroupFrame(frameId, title, color, 0, 0, MIN_WIDTH, MIN_HEIGHT);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        int count = 0;

        if (nodes != null) {
            for (RecipeNode n : nodes) {
                frame.addNode(n.getId());
                frame.initSharedMetadataFromNode(n);
                minX = Math.min(minX, n.getPosX());
                minY = Math.min(minY, n.getPosY());
                int nw = n.getCardWidth() > 0 ? n.getCardWidth() : (n.isReroute() ? 32 : 180);
                int nh = n.getCardHeight() > 0 ? n.getCardHeight() : (n.isReroute() ? 32 : 160);
                maxX = Math.max(maxX, n.getPosX() + nw);
                maxY = Math.max(maxY, n.getPosY() + nh);
                count++;
            }
        }
        if (notes != null) {
            for (CanvasStickyNote note : notes) {
                minX = Math.min(minX, note.getPosX());
                minY = Math.min(minY, note.getPosY());
                maxX = Math.max(maxX, note.getPosX() + note.getWidth());
                maxY = Math.max(maxY, note.getPosY() + note.getHeight());
                count++;
            }
        }

        if (count > 0) {
            double padding = DEFAULT_PADDING;
            frame.posX = minX - padding;
            frame.posY = minY - padding - HEADER_HEIGHT;
            frame.width = Math.max(MIN_WIDTH, (maxX - minX) + padding * 2);
            frame.height = Math.max(MIN_HEIGHT, (maxY - minY) + padding * 2 + HEADER_HEIGHT);
        }
        return frame;
    }

    private void initSharedMetadataFromNode(RecipeNode node) {
        if (node == null || node.isReroute()) return;
        if (this.sharedRecipeCategoryId == null && node.getRecipeCategoryId() != null) {
            this.sharedRecipeCategoryId = node.getRecipeCategoryId();
        }
        if (this.sharedMachineId == null && node.getMachineIcon() != null) {
            this.sharedMachineId = node.getMachineIcon();
        }
        if (this.sharedTier == null && node.getTargetTier() != null) {
            this.sharedTier = node.getTargetTier();
        }
    }

    public List<RecipeNode> getEnclosedNodes(Collection<RecipeNode> nodes) {
        List<RecipeNode> result = new ArrayList<>();
        if (nodes == null) return result;
        for (RecipeNode n : nodes) {
            if (n == null) continue;
            double nw = n.getCardWidth() > 0 ? n.getCardWidth() : (n.isReroute() ? 32 : 180);
            double nh = n.getCardHeight() > 0 ? n.getCardHeight() : (n.isReroute() ? 32 : 160);
            double cx = n.getPosX() + nw / 2.0;
            double cy = n.getPosY() + nh / 2.0;
            if (isPointInside(cx, cy)) {
                result.add(n);
            }
        }
        return result;
    }

    public List<RecipeNode> getEnclosedNodes(FlowGraph graph) {
        if (graph == null) return Collections.emptyList();
        if ((isFolded() || poolViewMode == PoolViewMode.EMBEDDED_PANEL) && !containedNodeIds.isEmpty()) {
            List<RecipeNode> result = new ArrayList<>();
            for (String id : containedNodeIds) {
                RecipeNode n = graph.findNodeById(id);
                if (n != null) {
                    result.add(n);
                }
            }
            return result;
        }
        return getEnclosedNodes(graph.getNodes());
    }

    public List<CanvasStickyNote> getEnclosedNotes(FlowGraph graph) {
        List<CanvasStickyNote> result = new ArrayList<>();
        if (graph == null) return result;
        for (CanvasStickyNote note : graph.getStickyNotes()) {
            double cx = note.getPosX() + note.getWidth() / 2.0;
            double cy = note.getPosY() + note.getHeight() / 2.0;
            if (isPointInside(cx, cy)) {
                result.add(note);
            }
        }
        return result;
    }

    public void recomputeBounds(Collection<RecipeNode> nodes, double padding) {
        invalidateFoldedPortCache();
        if (nodes == null || nodes.isEmpty()) return;

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        int count = 0;
        for (RecipeNode n : nodes) {
            if (n == null) continue;
            minX = Math.min(minX, n.getPosX());
            minY = Math.min(minY, n.getPosY());
            int w = n.getCardWidth() > 0 ? n.getCardWidth() : (n.isReroute() ? 32 : 180);
            int h = n.getCardHeight() > 0 ? n.getCardHeight() : (n.isReroute() ? 32 : 160);
            maxX = Math.max(maxX, n.getPosX() + w);
            maxY = Math.max(maxY, n.getPosY() + h);
            count++;
        }

        if (count > 0) {
            this.posX = minX - padding;
            this.posY = minY - padding - HEADER_HEIGHT;
            double minW = isSharedMachineFrame ? MIN_SHARED_FRAME_WIDTH : MIN_WIDTH;
            this.width = Math.max(minW, (maxX - minX) + padding * 2);
            this.height = Math.max(MIN_HEIGHT, (maxY - minY) + padding * 2 + HEADER_HEIGHT);
        }
    }

    public boolean autoFit(FlowGraph graph, double padding) {
        if (graph == null) return false;
        if (this.poolViewMode == PoolViewMode.EMBEDDED_PANEL) {
            this.height = computeMinEmbeddedHeight(graph);
            this.width = Math.max(MIN_SHARED_FRAME_WIDTH, this.width);
            relayoutEmbeddedCards(graph);
            return true;
        }
        List<RecipeNode> targets = new ArrayList<>();
        for (String nid : containedNodeIds) {
            RecipeNode n = graph.findNodeById(nid);
            if (n != null && !targets.contains(n)) targets.add(n);
        }
        for (RecipeNode n : graph.getNodes()) {
            if (!targets.contains(n)) {
                int w = n.getCardWidth() > 0 ? n.getCardWidth() : (n.isReroute() ? 32 : 180);
                int h = n.getCardHeight() > 0 ? n.getCardHeight() : (n.isReroute() ? 32 : 160);
                double cx = n.getPosX() + w / 2.0;
                double cy = n.getPosY() + h / 2.0;
                if (isPointInside(cx, cy) || (n.getPosX() < posX + width && n.getPosX() + w > posX && n.getPosY() < posY + height && n.getPosY() + h > posY)) {
                    targets.add(n);
                }
            }
        }
        if (targets.isEmpty()) return false;
        recomputeBounds(targets, padding);
        for (RecipeNode n : targets) {
            containedNodeIds.add(n.getId());
        }
        return true;
    }

    public void moveBy(double dx, double dy) {
        this.posX += dx;
        this.posY += dy;
    }

    public boolean isPointInside(double canvasX, double canvasY) {
        return canvasX >= posX && canvasX <= posX + width && canvasY >= posY && canvasY <= posY + height;
    }

    public boolean isPointInHeader(double canvasX, double canvasY) {
        return canvasX >= posX && canvasX <= posX + width && canvasY >= posY && canvasY <= posY + HEADER_HEIGHT;
    }

    public boolean isPointInResizeGrip(double canvasX, double canvasY) {
        double gripSize = 14.0;
        return canvasX >= posX + width - gripSize && canvasX <= posX + width
                && canvasY >= posY + height - gripSize && canvasY <= posY + height;
    }

    public boolean containsNode(String nodeId) {
        return containedNodeIds.contains(nodeId);
    }

    public void addNode(String nodeId) {
        if (nodeId != null) {
            containedNodeIds.add(nodeId);
            invalidateFoldedPortCache();
        }
    }

    public void removeNode(String nodeId) {
        if (containedNodeIds.remove(nodeId)) {
            invalidateFoldedPortCache();
        }
    }

    public com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.FoldedPortSummary getCachedFoldedPortSummary() {
        return cachedFoldedPortSummary;
    }

    public void setCachedFoldedPortSummary(com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.FoldedPortSummary summary) {
        this.cachedFoldedPortSummary = summary;
    }

    public void invalidateFoldedPortCache() {
        this.cachedFoldedPortSummary = null;
    }

    public void cycleColor() {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == this.color) {
                this.color = PALETTE[(i + 1) % PALETTE.length];
                return;
            }
        }
        this.color = PALETTE[0];
    }

    // =========================================================================
    // NBT Serialization
    // =========================================================================

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("title", title);
        tag.putInt("color", color);
        tag.putDouble("posX", posX);
        tag.putDouble("posY", posY);
        tag.putDouble("width", width);
        tag.putDouble("height", height);
        tag.putString("note", note != null ? note : "");
        if (isCompoundFrame) {
            tag.putBoolean("isCompoundFrame", true);
            if (compoundGroupId != null && !compoundGroupId.isEmpty()) {
                tag.putString("compoundGroupId", compoundGroupId);
            }
        }
        if (isSharedMachineFrame) {
            tag.putBoolean("isSharedMachineFrame", true);
            tag.putDouble("targetPoolCapacity", targetPoolCapacity);
        }
        if (isFolded) {
            tag.putBoolean("isFolded", true);
            tag.putDouble("savedUnfoldedWidth", savedUnfoldedWidth);
            tag.putDouble("savedUnfoldedHeight", savedUnfoldedHeight);
        }
        if (poolViewMode != null) {
            tag.putString("poolViewMode", poolViewMode.name());
        }
        if (sharedMachineId != null) {
            tag.putString("sharedMachineId", sharedMachineId.toString());
        }
        if (sharedRecipeCategoryId != null) {
            tag.putString("sharedRecipeCategoryId", sharedRecipeCategoryId.toString());
        }
        if (sharedTier != null) {
            tag.putString("sharedTier", sharedTier.name());
        }

        ListTag nodesTag = new ListTag();
        for (String nid : containedNodeIds) {
            nodesTag.add(StringTag.valueOf(nid));
        }
        tag.put("nodes", nodesTag);
        return tag;
    }

    public static CanvasGroupFrame deserializeNBT(CompoundTag tag) {
        String id = tag.getString("id");
        String title = tag.getString("title");
        int color = tag.getInt("color");
        double posX = tag.getDouble("posX");
        double posY = tag.getDouble("posY");
        double width = tag.getDouble("width");
        double height = tag.getDouble("height");
        String note = tag.getString("note");

        CanvasGroupFrame frame = new CanvasGroupFrame(id, title, color, posX, posY, width, height);
        frame.setNote(note);
        if (tag.getBoolean("isCompoundFrame")) {
            frame.setCompoundFrame(true);
            frame.setCompoundGroupId(tag.getString("compoundGroupId"));
        }
        if (tag.getBoolean("isSharedMachineFrame")) {
            frame.setSharedMachineFrame(true);
            if (tag.contains("targetPoolCapacity")) {
                frame.setTargetPoolCapacity(tag.getDouble("targetPoolCapacity"));
            }
        }
        if (tag.getBoolean("isFolded")) {
            frame.setFolded(true);
            if (tag.contains("savedUnfoldedWidth")) {
                frame.setSavedUnfoldedWidth(tag.getDouble("savedUnfoldedWidth"));
            }
            if (tag.contains("savedUnfoldedHeight")) {
                frame.setSavedUnfoldedHeight(tag.getDouble("savedUnfoldedHeight"));
            }
        }
        if (tag.contains("poolViewMode")) {
            try {
                frame.setViewMode(PoolViewMode.valueOf(tag.getString("poolViewMode")));
            } catch (Exception ignored) {
                frame.setViewMode(PoolViewMode.EXPANDED_FRAME);
            }
        } else if (tag.getBoolean("isFolded")) {
            frame.setViewMode(PoolViewMode.FOLDED_CARD);
        } else {
            frame.setViewMode(PoolViewMode.EXPANDED_FRAME);
        }
        if (tag.contains("sharedMachineId")) {
            frame.setSharedMachineId(ResourceLocation.tryParse(tag.getString("sharedMachineId")));
        }
        if (tag.contains("sharedRecipeCategoryId")) {
            frame.setSharedRecipeCategoryId(ResourceLocation.tryParse(tag.getString("sharedRecipeCategoryId")));
        }
        if (tag.contains("sharedTier")) {
            try {
                frame.setSharedTier(com.gtceu.calcboard.api.type.GTVoltageTier.valueOf(tag.getString("sharedTier")));
            } catch (Exception ignored) {}
        }

        if (tag.contains("nodes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("nodes", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                frame.addNode(list.getString(i));
            }
        }
        return frame;
    }

    public CanvasGroupFrame copy() {
        CompoundTag tag = serializeNBT();
        return deserializeNBT(tag);
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "Group Frame";
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
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

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        double minW = isSharedMachineFrame ? MIN_SHARED_FRAME_WIDTH : MIN_WIDTH;
        this.width = Math.max(minW, width);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(MIN_HEIGHT, height);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note != null ? note : "";
    }

    public Set<String> getContainedNodeIds() {
        return containedNodeIds;
    }

    public boolean isCompoundFrame() {
        return isCompoundFrame;
    }

    public void setCompoundFrame(boolean compoundFrame) {
        isCompoundFrame = compoundFrame;
    }

    public String getCompoundGroupId() {
        return compoundGroupId;
    }

    public void setCompoundGroupId(String compoundGroupId) {
        this.compoundGroupId = compoundGroupId != null ? compoundGroupId : "";
    }

    public boolean isSharedMachineFrame() {
        return isSharedMachineFrame;
    }

    public void setSharedMachineFrame(boolean sharedMachineFrame) {
        this.isSharedMachineFrame = sharedMachineFrame;
        if (sharedMachineFrame) {
            if (this.width < MIN_SHARED_FRAME_WIDTH) {
                this.width = MIN_SHARED_FRAME_WIDTH;
            }
            if (this.poolViewMode == PoolViewMode.EXPANDED_FRAME) {
                this.poolViewMode = PoolViewMode.EMBEDDED_PANEL;
            }
        }
    }

    public double getTargetPoolCapacity() {
        return targetPoolCapacity;
    }

    public void setTargetPoolCapacity(double targetPoolCapacity) {
        this.targetPoolCapacity = Math.max(0.01, targetPoolCapacity);
    }

    /**
     * Computes the aggregated duty cycle (total machine load) for all operational recipe nodes
     * enclosed within this shared machine frame.
     */
    public double computeTotalMachineDuty(FlowGraph graph) {
        if (graph == null) return 0.0;
        return computeTotalMachineDuty(getEnclosedNodes(graph), graph);
    }

    public double computeTotalMachineDuty(Collection<RecipeNode> nodes, FlowGraph graph) {
        if (nodes == null) return 0.0;
        double totalDuty = 0.0;
        for (RecipeNode node : nodes) {
            if (node != null && !node.isReroute() && (graph == null || node.isOperational(graph))) {
                totalDuty += node.getMachineCount();
            }
        }
        return totalDuty;
    }

    /**
     * Computes the physical integer machine count required to sustain the aggregated duty cycle.
     */
    public int computeRequiredMachines(FlowGraph graph) {
        double totalDuty = computeTotalMachineDuty(graph);
        return Math.max(1, (int) Math.ceil(totalDuty - 0.00001));
    }

    /**
     * Checks whether all recipe nodes in this frame share the same machine icon / machine type AND the same voltage tier.
     */
    public boolean isMachineCompatible(FlowGraph graph) {
        if (graph == null) return true;
        List<RecipeNode> nodes = getEnclosedNodes(graph);
        if (nodes.size() <= 1) return true;

        ResourceLocation firstIcon = null;
        com.gtceu.calcboard.api.type.GTVoltageTier firstTier = null;
        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            ResourceLocation icon = node.getMachineIcon();
            if (icon == null && !node.getAvailableWorkstations().isEmpty()) {
                icon = node.getAvailableWorkstations().get(0);
            }
            com.gtceu.calcboard.api.type.GTVoltageTier tier = node.getTargetTier();

            if (firstIcon == null) {
                firstIcon = icon;
                firstTier = tier;
            } else {
                if (icon != null && !firstIcon.equals(icon)) {
                    return false;
                }
                if (firstTier != null && tier != null && !firstTier.equals(tier)) {
                    return false;
                }
            }
        }
        return true;
    }

    public RecipeNode getFirstOperationalNode(FlowGraph graph) {
        if (graph == null) return null;
        for (RecipeNode node : getEnclosedNodes(graph)) {
            if (node != null && !node.isReroute()) {
                return node;
            }
        }
        return null;
    }

    public void syncHardwareConfig(RecipeNode sourceNode, FlowGraph graph) {
        if (sourceNode == null || graph == null) return;
        if (sourceNode.getRecipeCategoryId() != null) {
            this.sharedRecipeCategoryId = sourceNode.getRecipeCategoryId();
        }
        if (sourceNode.getMachineIcon() != null) {
            this.sharedMachineId = sourceNode.getMachineIcon();
        }
        if (sourceNode.getTargetTier() != null) {
            this.sharedTier = sourceNode.getTargetTier();
        }
        for (RecipeNode target : getEnclosedNodes(graph)) {
            if (target == null || target.isReroute() || target == sourceNode) continue;
            if (sourceNode.getMachineIcon() != null) {
                target.setMachineIcon(sourceNode.getMachineIcon());
            }
            target.setMultiblock(sourceNode.isMultiblock());
            target.setTargetTier(sourceNode.getTargetTier());
            target.setSteamMode(sourceNode.getSteamMode());
            target.setOverclockMode(sourceNode.getOverclockMode());
            target.setParallel(sourceNode.getParallel());

            // Addons
            target.getAddons().clear();
            for (com.gtceu.calcboard.api.catalog.MachineAddon addon : sourceNode.getAddons()) {
                if (addon != null) {
                    target.getAddons().add(addon.copy());
                }
            }

            // Node Property Store
            target.getProperties().copyFrom(sourceNode.getProperties());

            // Turbine & Boiler Specifics
            target.setRotorName(sourceNode.getRotorName());
            target.setRotorEfficiency(sourceNode.getRotorEfficiency());
            target.setRotorPower(sourceNode.getRotorPower());
            target.setBoilerThrottle(sourceNode.getBoilerThrottle());
        }
    }

    public ResourceLocation getSharedMachineIcon(FlowGraph graph) {
        if (this.sharedMachineId != null) return this.sharedMachineId;
        if (graph == null) return null;
        return getSharedMachineIcon(getEnclosedNodes(graph));
    }

    public ResourceLocation getSharedMachineIcon(Collection<RecipeNode> nodes) {
        if (this.sharedMachineId != null) return this.sharedMachineId;
        if (nodes == null) return null;
        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            ResourceLocation icon = node.getMachineIcon();
            if (icon != null) return icon;
            if (!node.getAvailableWorkstations().isEmpty()) {
                return node.getAvailableWorkstations().get(0);
            }
        }
        return null;
    }

    public String getSharedMachineName(FlowGraph graph) {
        if (graph == null) return "";
        return getSharedMachineName(getEnclosedNodes(graph));
    }

    public String getSharedMachineName(Collection<RecipeNode> nodes) {
        if (nodes == null) return "";
        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            return node.getMachineDisplayName();
        }
        return "";
    }

    public PoolViewMode getViewMode() {
        if (this.poolViewMode != null) return this.poolViewMode;
        return this.isFolded ? PoolViewMode.FOLDED_CARD : (this.isSharedMachineFrame ? PoolViewMode.EMBEDDED_PANEL : PoolViewMode.EXPANDED_FRAME);
    }

    public void setViewMode(PoolViewMode mode) {
        setViewMode(mode, null);
    }

    public void setViewMode(PoolViewMode mode, FlowGraph graph) {
        if (mode == null) mode = PoolViewMode.EXPANDED_FRAME;
        PoolViewMode oldMode = getViewMode();
        this.poolViewMode = mode;
        this.isFolded = (mode == PoolViewMode.FOLDED_CARD);
        invalidateFoldedPortCache();

        if (mode == PoolViewMode.FOLDED_CARD) {
            transitionToFoldedCard(oldMode, graph);
        } else if (mode == PoolViewMode.EMBEDDED_PANEL) {
            transitionToEmbeddedPanel(graph);
        } else {
            transitionToExpandedFrame(graph);
        }
    }

    private void transitionToFoldedCard(PoolViewMode oldMode, FlowGraph graph) {
        if (oldMode != PoolViewMode.FOLDED_CARD) {
            this.savedUnfoldedWidth = this.width;
            this.savedUnfoldedHeight = this.height;
        }
        if (graph != null) {
            for (RecipeNode n : getEnclosedNodes(graph.getNodes())) {
                containedNodeIds.add(n.getId());
            }
        }
        this.width = Math.max(MIN_SHARED_FRAME_WIDTH, 280.0);
        int portRows = 1;
        if (graph != null) {
            FlowGraphTopologyAnalyzer.FoldedPortSummary summary = FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, this);
            portRows = Math.max(summary.maxPortCount(), 1);
        }
        this.height = 64.0 + portRows * 18.0 + 6.0;
    }

    private void transitionToEmbeddedPanel(FlowGraph graph) {
        if (this.savedUnfoldedWidth > 0) {
            this.width = Math.max(MIN_SHARED_FRAME_WIDTH, this.savedUnfoldedWidth);
        } else {
            this.width = Math.max(MIN_SHARED_FRAME_WIDTH, 320.0);
        }
        if (this.savedUnfoldedHeight > 0) {
            this.height = Math.max(MIN_HEIGHT, this.savedUnfoldedHeight);
        }
        relayoutEmbeddedCards(graph);
    }

    private void transitionToExpandedFrame(FlowGraph graph) {
        if (this.savedUnfoldedWidth > 0) {
            this.width = Math.max(MIN_SHARED_FRAME_WIDTH, this.savedUnfoldedWidth);
        }
        if (this.savedUnfoldedHeight > 0) {
            this.height = Math.max(MIN_HEIGHT, this.savedUnfoldedHeight);
        }
        if (graph != null && !containedNodeIds.isEmpty()) {
            recomputeBounds(getEnclosedNodes(graph), DEFAULT_PADDING);
        }
    }

    public ResourceLocation getSharedMachineId() {
        return sharedMachineId;
    }

    public void setSharedMachineId(ResourceLocation sharedMachineId) {
        this.sharedMachineId = sharedMachineId;
    }

    public ResourceLocation getSharedRecipeCategoryId() {
        return sharedRecipeCategoryId;
    }

    public void setSharedRecipeCategoryId(ResourceLocation sharedRecipeCategoryId) {
        this.sharedRecipeCategoryId = sharedRecipeCategoryId;
    }

    public ResourceLocation getSharedRecipeCategoryId(FlowGraph graph) {
        if (this.sharedRecipeCategoryId != null) {
            return this.sharedRecipeCategoryId;
        }
        if (graph != null) {
            for (RecipeNode node : getEnclosedNodes(graph)) {
                if (node != null && !node.isReroute() && node.getRecipeCategoryId() != null) {
                    this.sharedRecipeCategoryId = node.getRecipeCategoryId();
                    return this.sharedRecipeCategoryId;
                }
            }
        }
        if (this.sharedMachineId != null) {
            ResourceLocation deduced = ModAdapterRegistry.getRecipeCategoryIdForMachine(this.sharedMachineId);
            if (deduced != null) {
                this.sharedRecipeCategoryId = deduced;
                return deduced;
            }
        }
        return null;
    }

    public com.gtceu.calcboard.api.type.GTVoltageTier getSharedTier() {
        return sharedTier;
    }

    public void setSharedTier(com.gtceu.calcboard.api.type.GTVoltageTier sharedTier) {
        this.sharedTier = sharedTier;
    }

    public List<RecipeNode> getSubNodes(FlowGraph graph) {
        return getEnclosedNodes(graph);
    }

    public double computeMinEmbeddedHeight(FlowGraph graph) {
        List<RecipeNode> nodes = getEnclosedNodes(graph);
        double currentY = this.posY + HEADER_HEIGHT + 20.0 + 6.0;
        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            int portRows = Math.max(node.getInputs().size(), node.getOutputs().size());
            double portRowsH = portRows > 0 ? portRows * 16.0 + 4.0 : 16.0;
            double cardH = Math.max(40.0, 16.0 + portRowsH + 4.0);
            currentY += cardH + 6.0;
        }
        double addBtnH = 22.0;
        return Math.max(MIN_HEIGHT, (currentY + addBtnH + 6.0) - this.posY);
    }

    public void relayoutEmbeddedCards(FlowGraph graph) {
        if (this.poolViewMode != PoolViewMode.EMBEDDED_PANEL) return;
        if (this.width < MIN_SHARED_FRAME_WIDTH) {
            this.width = MIN_SHARED_FRAME_WIDTH;
        }
        List<RecipeNode> nodes = getEnclosedNodes(graph);
        if (nodes.isEmpty()) {
            if (this.savedUnfoldedHeight > 0) {
                this.height = Math.max(MIN_HEIGHT, this.savedUnfoldedHeight);
            }
            return;
        }
        double cardW = Math.max(200.0, this.width - 12.0);
        double cardX = this.posX + 6.0;
        double currentY = this.posY + HEADER_HEIGHT + 20.0 + 6.0;

        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            int portRows = Math.max(node.getInputs().size(), node.getOutputs().size());
            double portRowsH = portRows > 0 ? portRows * 16.0 + 4.0 : 16.0;
            double cardH = Math.max(40.0, 16.0 + portRowsH + 4.0);

            node.setPosX(cardX);
            node.setPosY(currentY);
            node.setCardWidth((int) cardW);
            node.setCardHeight((int) cardH);

            currentY += cardH + 6.0;
        }

        double minRequiredH = computeMinEmbeddedHeight(graph);
        this.height = Math.max(minRequiredH, this.height);
    }

    public void addRecipeInline(RecipeNode node, FlowGraph graph) {
        if (node == null) return;
        double currentDuty = computeTotalMachineDuty(graph);
        containedNodeIds.add(node.getId());

        if (currentDuty < this.targetPoolCapacity) {
            double remaining = this.targetPoolCapacity - currentDuty;
            double assigned = Math.max(0.1, Math.min(1.0, Math.round(remaining * 100.0) / 100.0));
            node.setMachineCount(assigned);
        } else {
            node.setMachineCount(0.1);
        }

        if (node.getRecipeCategoryId() != null) {
            if (this.sharedRecipeCategoryId == null) {
                this.sharedRecipeCategoryId = node.getRecipeCategoryId();
            }
        } else if (this.sharedRecipeCategoryId != null) {
            node.setRecipeCategoryId(this.sharedRecipeCategoryId);
        }

        if (this.sharedMachineId != null) {
            node.setMachineIcon(this.sharedMachineId);
        } else if (node.getMachineIcon() != null) {
            this.sharedMachineId = node.getMachineIcon();
        }
        if (this.sharedTier != null) {
            node.setTargetTier(this.sharedTier);
        } else if (node.getTargetTier() != null) {
            this.sharedTier = node.getTargetTier();
        }
        RecipeNode master = getFirstOperationalNode(graph);
        if (master != null && master != node) {
            syncHardwareConfig(master, graph);
        }

        if (this.poolViewMode == PoolViewMode.EMBEDDED_PANEL) {
            relayoutEmbeddedCards(graph);
        }
    }

    public void removeRecipe(String nodeId, FlowGraph graph) {
        if (nodeId == null) return;
        containedNodeIds.remove(nodeId);
        if (graph != null) {
            RecipeNode n = graph.findNodeById(nodeId);
            if (n != null) {
                graph.removeNode(n);
            }
        }
        if (this.poolViewMode == PoolViewMode.EMBEDDED_PANEL) {
            relayoutEmbeddedCards(graph);
        }
    }

    public boolean isFolded() {
        return getViewMode() == PoolViewMode.FOLDED_CARD;
    }

    public void setFolded(boolean folded, FlowGraph graph) {
        setViewMode(folded ? PoolViewMode.FOLDED_CARD : (isSharedMachineFrame ? PoolViewMode.EMBEDDED_PANEL : PoolViewMode.EXPANDED_FRAME), graph);
    }

    public void setFolded(boolean folded) {
        setFolded(folded, null);
    }

    public void toggleFolded(FlowGraph graph) {
        if (isFolded()) {
            setViewMode(isSharedMachineFrame ? PoolViewMode.EMBEDDED_PANEL : PoolViewMode.EXPANDED_FRAME, graph);
        } else {
            setViewMode(PoolViewMode.FOLDED_CARD, graph);
        }
    }

    public void toggleFolded() {
        toggleFolded(null);
    }

    public double getSavedUnfoldedWidth() {
        return savedUnfoldedWidth;
    }

    public void setSavedUnfoldedWidth(double savedUnfoldedWidth) {
        this.savedUnfoldedWidth = savedUnfoldedWidth;
    }

    public double getSavedUnfoldedHeight() {
        return savedUnfoldedHeight;
    }

    public void setSavedUnfoldedHeight(double savedUnfoldedHeight) {
        this.savedUnfoldedHeight = savedUnfoldedHeight;
    }

    public void scaleEnclosedNodes(FlowGraph graph, double factor) {
        if (graph == null || factor <= 0.0) return;
        for (RecipeNode node : getEnclosedNodes(graph)) {
            if (node == null || node.isReroute() || !node.isOperational(graph)) continue;
            double newCount = Math.round(node.getMachineCount() * factor * 10000.0) / 10000.0;
            node.setMachineCount(Math.max(0.0001, newCount));
        }
    }

    public com.gtceu.calcboard.api.type.GTVoltageTier getSharedVoltageTier(FlowGraph graph) {
        if (this.sharedTier != null) return this.sharedTier;
        if (graph == null) return com.gtceu.calcboard.api.type.GTVoltageTier.LV;
        return getSharedVoltageTier(getEnclosedNodes(graph));
    }

    public com.gtceu.calcboard.api.type.GTVoltageTier getSharedVoltageTier(Collection<RecipeNode> nodes) {
        if (this.sharedTier != null) return this.sharedTier;
        if (nodes == null) return com.gtceu.calcboard.api.type.GTVoltageTier.LV;
        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            com.gtceu.calcboard.api.type.GTVoltageTier tier = node.getTargetTier();
            if (tier != null) return tier;
        }
        return com.gtceu.calcboard.api.type.GTVoltageTier.LV;
    }

    public com.gtceu.calcboard.api.type.OverclockMode getSharedOverclockMode(FlowGraph graph) {
        if (graph == null) return com.gtceu.calcboard.api.type.OverclockMode.STANDARD;
        return getSharedOverclockMode(getEnclosedNodes(graph));
    }

    public com.gtceu.calcboard.api.type.OverclockMode getSharedOverclockMode(Collection<RecipeNode> nodes) {
        if (nodes == null) return com.gtceu.calcboard.api.type.OverclockMode.STANDARD;
        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            com.gtceu.calcboard.api.type.OverclockMode mode = node.getOverclockMode();
            if (mode != null) return mode;
        }
        return com.gtceu.calcboard.api.type.OverclockMode.STANDARD;
    }

    public double computeSharedTotalEUt(FlowGraph graph) {
        if (graph == null) return 0.0;
        return computeSharedTotalEUt(getEnclosedNodes(graph), graph);
    }

    public double computeSharedTotalEUt(Collection<RecipeNode> nodes, FlowGraph graph) {
        if (nodes == null) return 0.0;
        double total = 0.0;
        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute() || (graph != null && !node.isOperational(graph))) continue;
            total += node.getBaseEUt() * node.getMachineCount();
        }
        return total;
    }
}

