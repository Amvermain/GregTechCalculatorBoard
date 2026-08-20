package com.gtceu.calcboard.client.team;

import com.gtceu.calcboard.network.packet.s2c.S2CBroadcastPresencePacket;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;

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

    private final Map<String, TeamWorkspacePage> remotePages = new LinkedHashMap<>();
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

    public Collection<TeamWorkspacePage> getRemotePages() {
        return remotePages.values();
    }

    public TeamWorkspacePage getRemotePage(String pageId) {
        return remotePages.get(pageId);
    }

    public void updateRemotePages(List<TeamWorkspacePage> pages) {
        remotePages.clear();
        if (pages != null) {
            for (TeamWorkspacePage p : pages) {
                remotePages.put(p.getPageId(), p);
            }
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
        remotePages.clear();
        commitHistory.clear();
        activePresence.clear();
        myHeldLocks.clear();
    }
}
