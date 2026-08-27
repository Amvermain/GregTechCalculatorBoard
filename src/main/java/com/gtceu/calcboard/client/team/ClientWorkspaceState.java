package com.gtceu.calcboard.client.team;

import com.gtceu.calcboard.client.gui.BoardScreen;

import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.network.packet.s2c.S2CBroadcastPresencePacket;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side singleton maintaining the current team workspace state, remote page caches, active locks, and presence.
 */
public class ClientWorkspaceState {

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

    private boolean serverSupported = false;

    private ClientWorkspaceState() {}

    public static synchronized ClientWorkspaceState getInstance() {
        if (instance == null) {
            instance = new ClientWorkspaceState();
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
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        if (mc.hasSingleplayerServer()) {
            var singleServer = mc.getSingleplayerServer();
            return singleServer != null && singleServer.isPublished();
        }
        return serverSupported && mc.getConnection() != null;
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

    public void autoCommitAndRelease(com.gtceu.calcboard.client.gui.BoardScreen screen, String pageId) {
        if (pageId == null) pageId = getActiveTeamPageId();
        if (screen != null && isPageDirty(pageId)) {
            UUID teamId = getCurrentTeamId() != null ? getCurrentTeamId() : (net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getUUID() : UUID.randomUUID());
            TeamWorkspacePage remotePage = getRemotePage(pageId);
            String pageTitle = (remotePage != null && remotePage.getTitle() != null) ? remotePage.getTitle() : "Main Workspace";
            int rev = getGlobalRevision();

            FlowGraph graph = screen.getGraph();
            net.minecraft.nbt.CompoundTag tag = graph.serializeNBT();
            byte[] compressed = com.gtceu.calcboard.api.storage.BlueprintCodec.compressTag(tag);
            int nodeCount = graph.getNodes().size();

            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SCommitWorkspacePacket(
                    teamId, pageId, pageTitle, rev, "Auto-saved changes", compressed, nodeCount, 0, 0
                )
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

    public void updateRemotePages(List<TeamWorkspacePage> pages) {
        remotePages.clear();
        teamGraphs.clear();
        if (pages != null && !pages.isEmpty()) {
            for (TeamWorkspacePage p : pages) {
                remotePages.put(p.getPageId(), p);
                if (p.getCompressedGraphData() != null && p.getCompressedGraphData().length > 0) {
                    try {
                        CompoundTag tag = BlueprintCodec.decompressTag(p.getCompressedGraphData());
                        teamGraphs.put(p.getPageId(), FlowGraph.deserializeNBT(tag));
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

    public boolean doesHoldLock(String pageId) {
        return Boolean.TRUE.equals(myHeldLocks.get(pageId));
    }

    public void setLockHeld(String pageId, boolean held) {
        if (held) {
            myHeldLocks.put(pageId, true);
        } else {
            myHeldLocks.remove(pageId);
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
    }
}



