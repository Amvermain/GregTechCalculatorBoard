package com.gtceu.calcboard.client.team;

import com.gtceu.calcboard.client.gui.BoardScreen;

import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.network.packet.s2c.S2CBroadcastPresencePacket;
import com.gtceu.calcboard.api.team.CommitLogEntry;
import com.gtceu.calcboard.api.team.TeamWorkspacePage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side singleton maintaining the current team workspace state, remote page caches, active locks, and presence.
 */
public class ClientWorkspaceState implements com.gtceu.calcboard.api.storage.IWorkspacePageHandler {

    public enum WorkspaceMode {
        LOCAL,
        TEAM
    }

    private static ClientWorkspaceState instance;

    private WorkspaceMode currentMode = WorkspaceMode.LOCAL;
    private UUID currentTeamId = null;
    private String currentTeamName = "Team Workspace";
    private int globalRevision = 1;
    private String activeTeamPageId = "page_main";

    private final Map<String, TeamWorkspacePage> remotePages = new LinkedHashMap<>();
    private final Map<String, FlowGraph> teamGraphs = new ConcurrentHashMap<>();
    private final List<CommitLogEntry> commitHistory = new ArrayList<>();
    private final List<S2CBroadcastPresencePacket.MemberPresence> activePresence = new ArrayList<>();
    private final Map<String, Boolean> myHeldLocks = new ConcurrentHashMap<>();
    private final Map<String, TeamPageViewport> pageViewports = new ConcurrentHashMap<>();
    private final Map<String, PageSettingsSnapshot> pendingSettingsSnapshots = new ConcurrentHashMap<>();

    private boolean serverSupported = false;

    public record TeamPageViewport(double panX, double panY, double zoom) {}
    public record PageSettingsSnapshot(byte voltageTierOrdinal, boolean autoEquipEnergyHatches) {}

    private ClientWorkspaceState() {}

    public static synchronized ClientWorkspaceState getInstance() {
        if (instance == null) {
            instance = new ClientWorkspaceState();
            com.gtceu.calcboard.api.storage.WorkspacePageRegistry.registerCollaborativeHandler(instance);
        }
        return instance;
    }

    public boolean isServerSupported() {
        return serverSupported;
    }

    public void setServerSupported(boolean serverSupported) {
        this.serverSupported = serverSupported;
    }

    public boolean isCollaborationEnabled() {
        if (serverSupported) {
            return true;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && (mc.getCurrentServer() != null || (mc.getConnection() != null && !mc.hasSingleplayerServer()))) {
            return true;
        }
        return currentTeamId != null;
    }

    public WorkspaceMode getCurrentMode() {
        return currentMode;
    }

    private final Set<String> dirtyPages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void markPageDirty(String pageId) {
        if (pageId != null) {
            dirtyPages.add(pageId);
        }
    }

    public boolean isPageDirty(String pageId) {
        return pageId != null && dirtyPages.contains(pageId);
    }

    public void clearPageDirty(String pageId) {
        if (pageId != null) {
            dirtyPages.remove(pageId);
        }
    }

    public void releaseCurrentLockIfHeld() {
        String activePageId = getActiveTeamPageId();
        if (activePageId != null && doesHoldLock(activePageId)) {
            UUID teamId = getCurrentTeamId() != null ? getCurrentTeamId() : (net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getUUID() : UUID.randomUUID());
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SReleaseLockPacket(teamId, activePageId));
            setLockHeld(activePageId, false);
        }
    }

    public void autoCommitAndRelease(com.gtceu.calcboard.client.gui.api.IBoardScreenContext screen, String pageId) {
        if (pageId == null) pageId = getActiveTeamPageId();
        if (screen != null && isPageDirty(pageId)) {
            UUID teamId = getCurrentTeamId() != null ? getCurrentTeamId() : (net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getUUID() : UUID.randomUUID());
            TeamWorkspacePage remotePage = getRemotePage(pageId);
            String pageTitle = (remotePage != null && remotePage.getTitle() != null) ? remotePage.getTitle() : "Main Workspace";
            String folderPath = (remotePage != null && remotePage.getFolderPath() != null) ? remotePage.getFolderPath() : "";
            int rev = (remotePage != null) ? remotePage.getPageRevision() : 1;

            FlowGraph graph = screen.getGraph();
            net.minecraft.nbt.CompoundTag tag = graph.serializeNBT();
            byte[] compressed = com.gtceu.calcboard.api.storage.BlueprintCodec.compressTag(tag);
            int nodeCount = graph.getNodes().size();

            ClientChunkedStreamHelper.commitPageSafely(
                teamId, pageId, pageTitle, folderPath, rev, "Auto-saved changes", compressed, nodeCount, 0, 0
            );
            clearPageDirty(pageId);
            setLockHeld(pageId, false);
        } else {
            releaseCurrentLockIfHeld();
        }
    }

    public void setCurrentMode(WorkspaceMode currentMode) {
        if (this.currentMode != currentMode) {
            if (this.currentMode == WorkspaceMode.TEAM) {
                releaseCurrentLockIfHeld();
            }
            this.currentMode = currentMode;
        }
    }

    public boolean isTeamMode() {
        return currentMode == WorkspaceMode.TEAM;
    }

    public UUID getCurrentTeamId() {
        return currentTeamId;
    }

    public void setCurrentTeamId(UUID currentTeamId) {
        this.currentTeamId = currentTeamId;
    }

    public String getCurrentTeamName() {
        return currentTeamName;
    }

    public void setCurrentTeamName(String currentTeamName) {
        this.currentTeamName = currentTeamName;
    }

    public int getGlobalRevision() {
        return globalRevision;
    }

    public void setGlobalRevision(int globalRevision) {
        this.globalRevision = globalRevision;
    }

    public String getActiveTeamPageId() {
        return activeTeamPageId != null ? activeTeamPageId : "page_main";
    }

    public void setActiveTeamPageId(String activeTeamPageId) {
        if (activeTeamPageId != null && !activeTeamPageId.equals(this.activeTeamPageId)) {
            releaseCurrentLockIfHeld();
            this.activeTeamPageId = activeTeamPageId;
            requestPageDataIfNeeded(activeTeamPageId);
        }
    }

    public TeamPageViewport getPageViewport(String pageId) {
        if (pageId == null) pageId = getActiveTeamPageId();
        return pageViewports.get(pageId);
    }

    public void setPageViewport(String pageId, double panX, double panY, double zoom) {
        if (pageId == null) pageId = getActiveTeamPageId();
        if (pageId != null) {
            pageViewports.put(pageId, new TeamPageViewport(panX, panY, zoom));
        }
    }

    public Collection<TeamWorkspacePage> getRemotePages() {
        if (remotePages.isEmpty()) {
            TeamWorkspacePage defaultPage = new TeamWorkspacePage("page_main", "Main Workspace");
            remotePages.put("page_main", defaultPage);
            teamGraphs.put("page_main", new FlowGraph());
        }
        return remotePages.values();
    }

    public TeamWorkspacePage getRemotePage(String pageId) {
        return remotePages.get(pageId);
    }

    public List<BoardPage> getTeamPagesAsBoardPages() {
        List<BoardPage> list = new ArrayList<>();
        for (TeamWorkspacePage tp : getRemotePages()) {
            if (tp == null || tp.getPageId() == null) continue;
            list.add(createBoardPageFromTeamPage(tp));
        }
        return list;
    }

    public BoardPage getTeamPageAsBoardPage(String pageId) {
        if (pageId == null) return null;
        TeamWorkspacePage tp = getRemotePage(pageId);
        if (tp == null) return null;
        return createBoardPageFromTeamPage(tp);
    }

    private BoardPage createBoardPageFromTeamPage(TeamWorkspacePage tp) {
        FlowGraph graph = getTeamGraph(tp.getPageId());
        BoardPage bp = new BoardPage(tp.getPageId(), tp.getTitle(), graph);
        bp.setPageType(tp.getPageType());
        bp.setParentPageId(tp.getParentPageId());
        bp.setParentModuleNodeId(tp.getParentModuleNodeId());
        if (tp.getDefaultVoltageTierOrdinal() >= 0 && tp.getDefaultVoltageTierOrdinal() < GTVoltageTier.values().length) {
            bp.setDefaultVoltageTier(GTVoltageTier.values()[tp.getDefaultVoltageTierOrdinal()]);
        } else {
            bp.setDefaultVoltageTier(null);
        }
        bp.setAutoEquipEnergyHatches(tp.isAutoEquipEnergyHatches());
        String folder = tp.getFolderPath();
        String title = tp.getTitle();
        if ((folder == null || folder.isEmpty()) && title != null && title.contains("/")) {
            int lastSlash = title.lastIndexOf('/');
            folder = title.substring(0, lastSlash).trim();
            String simpleName = title.substring(lastSlash + 1).trim();
            bp.setName(simpleName);
            bp.setFolderPath(folder);
        } else {
            bp.setName(title);
            bp.setFolderPath(folder != null ? folder : "");
        }
        return bp;
    }

    public void syncPageSettingsIfTeamMode(BoardPage page) {
        if (!isTeamMode() || currentTeamId == null || page == null) {
            return;
        }
        TeamWorkspacePage remotePage = getRemotePage(page.getId());
        int rev = remotePage != null ? remotePage.getPageRevision() : 1;
        int tierOrdinal = page.getDefaultVoltageTier() != null ? page.getDefaultVoltageTier().ordinal() : -1;
        if (remotePage != null) {
            pendingSettingsSnapshots.putIfAbsent(page.getId(), new PageSettingsSnapshot(
                    remotePage.getDefaultVoltageTierOrdinal(),
                    remotePage.isAutoEquipEnergyHatches()
            ));
            remotePage.applySettings((byte) tierOrdinal, page.isAutoEquipEnergyHatches());
        }
        com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.UpdatePageSettingsPacket(
                        currentTeamId,
                        page.getId(),
                        rev,
                        tierOrdinal,
                        page.isAutoEquipEnergyHatches()
                )
        );
    }

    public PageSettingsSnapshot consumePendingSnapshot(String pageId) {
        return pageId != null ? pendingSettingsSnapshots.remove(pageId) : null;
    }

    public void clearPendingSnapshot(String pageId) {
        if (pageId != null) {
            pendingSettingsSnapshots.remove(pageId);
        }
    }

    public Set<String> getPendingSnapshotPageIds() {
        return new HashSet<>(pendingSettingsSnapshots.keySet());
    }

    public BoardPage getActiveBoardPage() {
        return isTeamMode()
                ? getTeamPageAsBoardPage(getActiveTeamPageId())
                : com.gtceu.calcboard.api.storage.BoardManager.getInstance().getActivePage();
    }

    public static BoardPage resolveActiveWorkspacePage(String pageId) {
        return com.gtceu.calcboard.api.storage.WorkspacePageRegistry.resolvePage(pageId);
    }

    public FlowGraph getActiveTeamGraph() {
        return getTeamGraph(getActiveTeamPageId());
    }

    public FlowGraph getTeamGraph(String pageId) {
        if (pageId == null) pageId = getActiveTeamPageId();
        FlowGraph g = teamGraphs.get(pageId);
        if (g == null) {
            TeamWorkspacePage p = remotePages.get(pageId);
            if (p != null && p.getCompressedGraphData() != null && p.getCompressedGraphData().length > 0) {
                try {
                    CompoundTag tag = BlueprintCodec.decompressTag(p.getCompressedGraphData());
                    g = FlowGraph.deserializeNBT(tag);
                    teamGraphs.put(pageId, g);
                } catch (Exception ignored) {}
            }
        }
        if (g == null) {
            g = new FlowGraph();
            teamGraphs.put(pageId, g);
        }
        return g;
    }

    private final Set<String> loadingPages = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Integer> cachedRevisions = new ConcurrentHashMap<>();

    public boolean isPageLoading(String pageId) {
        return pageId != null && loadingPages.contains(pageId);
    }

    public boolean isPageLoaded(String pageId) {
        if (pageId == null) return false;
        TeamWorkspacePage page = remotePages.get(pageId);
        if (page == null) return false;
        Integer cachedRev = cachedRevisions.get(pageId);
        return teamGraphs.containsKey(pageId) && cachedRev != null && cachedRev == page.getPageRevision();
    }

    public void requestPageDataIfNeeded(String pageId) {
        if (pageId == null || !isCollaborationEnabled()) return;
        TeamWorkspacePage page = remotePages.get(pageId);
        int serverRev = (page != null) ? page.getPageRevision() : 0;
        Integer cachedRev = cachedRevisions.get(pageId);

        // Cache-Hit: if we already have the exact revision deserialized, skip network request
        if (cachedRev != null && cachedRev == serverRev && teamGraphs.containsKey(pageId)) {
            return;
        }

        loadingPages.add(pageId);
        com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SRequestPageDataPacket(pageId, cachedRev != null ? cachedRev : 0)
        );
    }

    public void applyPageData(String pageId, int revision, byte[] compressedNBT) {
        if (pageId == null) return;
        loadingPages.remove(pageId);

        TeamWorkspacePage page = remotePages.get(pageId);
        if (page != null) {
            page.setPageRevision(revision);
            page.setCompressedGraphData(compressedNBT);
        }

        if (compressedNBT != null && compressedNBT.length > 0) {
            try {
                CompoundTag tag = BlueprintCodec.decompressTag(compressedNBT);
                teamGraphs.put(pageId, FlowGraph.deserializeNBT(tag));
                cachedRevisions.put(pageId, revision);
            } catch (Exception e) {
                teamGraphs.put(pageId, new FlowGraph());
                cachedRevisions.put(pageId, revision);
            }
        } else {
            teamGraphs.put(pageId, new FlowGraph());
            cachedRevisions.put(pageId, revision);
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.screen instanceof BoardScreen bs) {
            if (pageId.equals(activeTeamPageId)) {
                bs.rebuildWidgets();
                bs.markSummaryDirty();
            } else if (bs.getMultiblockBOMDialog() != null && bs.getMultiblockBOMDialog().isVisible()) {
                bs.getMultiblockBOMDialog().markDirty();
            }
        }
    }

    public void updateWorkspaceMeta(com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket packet) {
        serverSupported = true;
        UUID teamId = packet.getTeamId();
        if (teamId == null || (teamId.getMostSignificantBits() == 0L && teamId.getLeastSignificantBits() == 0L)) {
            setCurrentTeamId(null);
            setCurrentTeamName("Team Workspace");
            remotePages.clear();
            teamGraphs.clear();
            cachedRevisions.clear();
        } else {
            setCurrentTeamId(teamId);
            setCurrentTeamName(packet.getTeamName());
            setGlobalRevision(packet.getGlobalRevision());

            Set<String> newPageIds = new HashSet<>();
            for (var pm : packet.getPages()) {
                newPageIds.add(pm.getPageId());
                TeamWorkspacePage page = remotePages.get(pm.getPageId());
                if (page == null) {
                    page = new TeamWorkspacePage(pm.getPageId(), pm.getTitle(), pm.getRevision(), new byte[0]);
                    page.setFolderPath(pm.getFolderPath());
                    page.setPageType(com.gtceu.calcboard.api.storage.PageType.fromNameSafe(pm.getPageType(), com.gtceu.calcboard.api.storage.PageType.STANDARD));
                    page.setParentPageId(pm.getParentPageId());
                    page.setParentModuleNodeId(pm.getParentModuleNodeId());
                    remotePages.put(pm.getPageId(), page);
                } else {
                    page.setTitle(pm.getTitle());
                    page.setFolderPath(pm.getFolderPath());
                    page.setPageType(com.gtceu.calcboard.api.storage.PageType.fromNameSafe(pm.getPageType(), com.gtceu.calcboard.api.storage.PageType.STANDARD));
                    page.setParentPageId(pm.getParentPageId());
                    page.setParentModuleNodeId(pm.getParentModuleNodeId());
                    if (page.getPageRevision() != pm.getRevision()) {
                        // Invalidate cache when revision changed
                        teamGraphs.remove(pm.getPageId());
                        cachedRevisions.remove(pm.getPageId());
                    }
                    page.setPageRevision(pm.getRevision());
                }
                page.setLockHolderUUID(pm.getLockHolderUUID());
                page.setLockHolderName(pm.getLockHolderName());
                page.setLockExpiresTimestamp(pm.getLockExpiresTimestamp());

                if (page.isLocked() && isCurrentPlayer(pm.getLockHolderUUID(), pm.getLockHolderName())) {
                    myHeldLocks.put(pm.getPageId(), true);
                } else if (!page.isLocked() || (pm.getLockHolderUUID() != null && !isCurrentPlayer(pm.getLockHolderUUID(), pm.getLockHolderName()))) {
                    myHeldLocks.remove(pm.getPageId());
                }
            }

            // Remove deleted pages
            remotePages.keySet().removeIf(pid -> !newPageIds.contains(pid));
            teamGraphs.keySet().removeIf(pid -> !newPageIds.contains(pid));
            cachedRevisions.keySet().removeIf(pid -> !newPageIds.contains(pid));

            if (!remotePages.containsKey(activeTeamPageId) && !remotePages.isEmpty()) {
                activeTeamPageId = remotePages.keySet().iterator().next();
            }

            // Auto fetch active page data if not cached
            requestPageDataIfNeeded(activeTeamPageId);
        }
    }

    public void updateRemotePages(List<TeamWorkspacePage> pages) {
        remotePages.clear();
        teamGraphs.clear();
        cachedRevisions.clear();
        if (pages != null && !pages.isEmpty()) {
            for (TeamWorkspacePage p : pages) {
                remotePages.put(p.getPageId(), p);
                if (p.isLocked() && isCurrentPlayer(p.getLockHolderUUID(), p.getLockHolderName())) {
                    myHeldLocks.put(p.getPageId(), true);
                }
                if (p.getCompressedGraphData() != null && p.getCompressedGraphData().length > 0) {
                    try {
                        CompoundTag tag = BlueprintCodec.decompressTag(p.getCompressedGraphData());
                        teamGraphs.put(p.getPageId(), FlowGraph.deserializeNBT(tag));
                        cachedRevisions.put(p.getPageId(), p.getPageRevision());
                    } catch (Exception e) {
                        teamGraphs.put(p.getPageId(), new FlowGraph());
                    }
                } else {
                    teamGraphs.put(p.getPageId(), new FlowGraph());
                }
            }
            if (!remotePages.containsKey(activeTeamPageId)) {
                activeTeamPageId = remotePages.keySet().iterator().next();
            }
        } else {
            TeamWorkspacePage defaultPage = new TeamWorkspacePage("page_main", "Main Workspace");
            remotePages.put("page_main", defaultPage);
            teamGraphs.put("page_main", new FlowGraph());
            activeTeamPageId = "page_main";
        }
    }

    public List<CommitLogEntry> getCommitHistory() {
        return Collections.unmodifiableList(commitHistory);
    }

    public void updateCommitHistory(List<CommitLogEntry> commits) {
        commitHistory.clear();
        if (commits != null) {
            commitHistory.addAll(commits);
        }
    }

    public List<S2CBroadcastPresencePacket.MemberPresence> getActivePresence() {
        return Collections.unmodifiableList(activePresence);
    }

    public void updatePresence(List<S2CBroadcastPresencePacket.MemberPresence> presence) {
        activePresence.clear();
        if (presence != null) {
            activePresence.addAll(presence);
        }
    }

    public String resolvePlayerName(UUID uuid) {
        if (uuid == null) return "Teammate";
        for (S2CBroadcastPresencePacket.MemberPresence m : activePresence) {
            if (uuid.equals(m.getPlayerUUID()) && m.getPlayerName() != null && !m.getPlayerName().isEmpty()) {
                return m.getPlayerName();
            }
        }
        return "Teammate";
    }

    public boolean isCurrentPlayer(UUID lockHolderUUID, String lockHolderName) {
        if (lockHolderUUID == null && (lockHolderName == null || lockHolderName.isEmpty())) {
            return false;
        }
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                UUID myUUID = mc.player.getUUID();
                if (myUUID != null && myUUID.equals(lockHolderUUID)) {
                    return true;
                }
                String myName = mc.player.getGameProfile().getName();
                return myName != null && !myName.isEmpty() && myName.equalsIgnoreCase(lockHolderName);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean doesHoldLock(String pageId) {
        if (Boolean.TRUE.equals(myHeldLocks.get(pageId))) {
            return true;
        }
        TeamWorkspacePage page = remotePages.get(pageId);
        if (page != null && page.isLocked() && isCurrentPlayer(page.getLockHolderUUID(), page.getLockHolderName())) {
            myHeldLocks.put(pageId, true);
            return true;
        }
        return false;
    }

    public void setLockHeld(String pageId, boolean held) {
        if (held) {
            myHeldLocks.put(pageId, true);
        } else {
            myHeldLocks.remove(pageId);
        }
    }

    public void addTeamPage(TeamWorkspacePage page, FlowGraph graph) {
        if (page == null) return;
        remotePages.put(page.getPageId(), page);
        if (graph != null) {
            teamGraphs.put(page.getPageId(), graph);
            cachedRevisions.put(page.getPageId(), page.getPageRevision());
        }
        markPageDirty(page.getPageId());

        if (isCollaborationEnabled()) {
            UUID teamId = getCurrentTeamId() != null
                    ? getCurrentTeamId()
                    : (net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getUUID() : UUID.randomUUID());
            byte[] data = page.getCompressedGraphData();
            if ((data == null || data.length == 0) && graph != null) {
                data = BlueprintCodec.compressTag(graph.serializeNBT());
                page.setCompressedGraphData(data);
            }
            int nodeCount = graph != null ? graph.getNodes().size() : 0;
            ClientChunkedStreamHelper.commitPageSafely(
                    teamId, page.getPageId(), page.getTitle(), page.getFolderPath(),
                    page.getPageType().name(), page.getParentPageId(), page.getParentModuleNodeId(),
                    page.getPageRevision(), "Created " + page.getTitle(), data, nodeCount, 0, 0
            );
        }
    }

    public void removeTeamPage(String pageId) {
        if (pageId == null) return;
        remotePages.remove(pageId);
        teamGraphs.remove(pageId);
        cachedRevisions.remove(pageId);
        dirtyPages.remove(pageId);
        myHeldLocks.remove(pageId);
        pageViewports.remove(pageId);

        if (isCollaborationEnabled()) {
            UUID teamId = getCurrentTeamId() != null
                    ? getCurrentTeamId()
                    : (net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getUUID() : UUID.randomUUID());
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                    new com.gtceu.calcboard.network.packet.c2s.C2SDeleteTeamPagePacket(teamId, pageId)
            );
        }
    }

    public void restoreTeamPage(BoardPage page) {
        if (page == null) return;
        byte[] data = BlueprintCodec.compressTag(page.getGraph().serializeNBT());
        TeamWorkspacePage tp = new TeamWorkspacePage(page.getId(), page.getName(), page.getFolderPath(), 1, data);
        tp.setPageType(page.getPageType());
        tp.setParentPageId(page.getParentPageId());
        tp.setParentModuleNodeId(page.getParentModuleNodeId());
        addTeamPage(tp, page.getGraph());
    }

    @Override
    public boolean isApplicable(FlowGraph graph) {
        if (isTeamMode()) return true;
        if (graph == null) return false;
        if (getActiveTeamGraph() == graph) return true;
        for (TeamWorkspacePage tp : getRemotePages()) {
            if (teamGraphs.get(tp.getPageId()) == graph) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BoardPage resolvePage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return null;
        if (isTeamMode() || getRemotePage(pageId) != null) {
            return getTeamPageAsBoardPage(pageId);
        }
        return null;
    }

    @Override
    public BoardPage findPageForGraph(FlowGraph graph) {
        if (graph == null) return null;
        if (isTeamMode()) {
            for (TeamWorkspacePage tp : getRemotePages()) {
                if (teamGraphs.get(tp.getPageId()) == graph) {
                    return getTeamPageAsBoardPage(tp.getPageId());
                }
            }
            if (getActiveTeamGraph() == graph) {
                return getTeamPageAsBoardPage(getActiveTeamPageId());
            }
            return getTeamPageAsBoardPage(getActiveTeamPageId());
        }
        for (TeamWorkspacePage tp : getRemotePages()) {
            if (teamGraphs.get(tp.getPageId()) == graph) {
                return getTeamPageAsBoardPage(tp.getPageId());
            }
        }
        return null;
    }

    @Override
    public void addModuleSubPage(String subPageId, String title, FlowGraph subGraph, String parentPageId, String parentModuleNodeId) {
        TeamWorkspacePage subTeamPage = new TeamWorkspacePage(subPageId, title);
        subTeamPage.setPageType(com.gtceu.calcboard.api.storage.PageType.MODULE);
        subTeamPage.setParentPageId(parentPageId);
        subTeamPage.setParentModuleNodeId(parentModuleNodeId);
        byte[] compressed = BlueprintCodec.compressTag(subGraph.serializeNBT());
        subTeamPage.setCompressedGraphData(compressed);
        addTeamPage(subTeamPage, subGraph);
        if (parentPageId != null && !parentPageId.isEmpty()) {
            markPageDirty(parentPageId);
        }
    }

    @Override
    public void removeModuleSubPage(String subPageId) {
        if (subPageId == null || subPageId.isEmpty()) return;
        if (isTeamMode() || getRemotePage(subPageId) != null) {
            removeTeamPage(subPageId);
        } else {
            com.gtceu.calcboard.api.storage.BoardManager.getInstance().removePage(subPageId);
        }
    }

    @Override
    public void restoreModuleSubPage(BoardPage page) {
        if (page == null) return;
        if (isTeamMode()) {
            restoreTeamPage(page);
        } else {
            if (com.gtceu.calcboard.api.storage.BoardManager.getInstance().getPage(page.getId()).isEmpty()) {
                com.gtceu.calcboard.api.storage.BoardManager.getInstance().getPageManager().addPage(page);
            }
        }
    }

    public void clear() {
        currentMode = WorkspaceMode.LOCAL;
        currentTeamId = null;
        currentTeamName = "Team Workspace";
        activeTeamPageId = "page_main";
        serverSupported = false;
        remotePages.clear();
        teamGraphs.clear();
        commitHistory.clear();
        activePresence.clear();
        myHeldLocks.clear();
        dirtyPages.clear();
        loadingPages.clear();
        cachedRevisions.clear();
        pageViewports.clear();
        pendingSettingsSnapshots.clear();
        ChunkedPayloadAssembler.clear();
        com.gtceu.calcboard.api.storage.WorkspacePageRegistry.registerCollaborativeHandler(this);
    }
}



