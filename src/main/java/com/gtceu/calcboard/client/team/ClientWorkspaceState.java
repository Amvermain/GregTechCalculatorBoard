package com.gtceu.calcboard.client.team;

import com.gtceu.calcboard.api.BlueprintCodec;
import com.gtceu.calcboard.api.FlowGraph;
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

    private ClientWorkspaceState() {}

    public static synchronized ClientWorkspaceState getInstance() {
        if (instance == null) {
            instance = new ClientWorkspaceState();
        }
        return instance;
    }

    public WorkspaceMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(WorkspaceMode currentMode) {
        this.currentMode = currentMode;
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
        if (activeTeamPageId != null && remotePages.containsKey(activeTeamPageId)) {
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
        FlowGraph g = teamGraphs.get(getActiveTeamPageId());
        if (g == null) {
            g = new FlowGraph();
            teamGraphs.put(getActiveTeamPageId(), g);
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
        remotePages.clear();
        teamGraphs.clear();
        commitHistory.clear();
        activePresence.clear();
        myHeldLocks.clear();
    }
}
