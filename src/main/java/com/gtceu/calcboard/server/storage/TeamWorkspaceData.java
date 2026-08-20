package com.gtceu.calcboard.server.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Encapsulates the entire multi-page shared workspace and history for a team.
 */
public class TeamWorkspaceData {

    public static final int CURRENT_FORMAT_VERSION = 2;

    private int formatVersion = CURRENT_FORMAT_VERSION;
    private UUID teamId;
    private String teamName;
    private long lastSavedTime;
    private int globalRevision;

    private final Map<String, TeamWorkspacePage> pages = new LinkedHashMap<>();
    private final List<CommitLogEntry> commitHistory = new ArrayList<>();

    public TeamWorkspaceData(UUID teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName != null ? teamName : "Team Workspace";
        this.lastSavedTime = System.currentTimeMillis();
        this.globalRevision = 1;

        // Initialize default main page
        TeamWorkspacePage defaultPage = new TeamWorkspacePage("page_main", "Main Workspace");
        pages.put(defaultPage.getPageId(), defaultPage);
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public long getLastSavedTime() {
        return lastSavedTime;
    }

    public void setLastSavedTime(long lastSavedTime) {
        this.lastSavedTime = lastSavedTime;
    }

    public int getGlobalRevision() {
        return globalRevision;
    }

    public void incrementGlobalRevision() {
        this.globalRevision++;
        this.lastSavedTime = System.currentTimeMillis();
    }

    public Collection<TeamWorkspacePage> getPages() {
        return pages.values();
    }

    public TeamWorkspacePage getPage(String pageId) {
        return pages.get(pageId);
    }

    public void addOrUpdatePage(TeamWorkspacePage page) {
        if (page != null) {
            pages.put(page.getPageId(), page);
            incrementGlobalRevision();
        }
    }

    public TeamWorkspacePage removePage(String pageId) {
        TeamWorkspacePage removed = pages.remove(pageId);
        if (removed != null) {
            incrementGlobalRevision();
        }
        return removed;
    }

    public List<CommitLogEntry> getCommitHistory() {
        return Collections.unmodifiableList(commitHistory);
    }

    public void addCommit(CommitLogEntry entry) {
        if (entry != null) {
            commitHistory.add(entry);
            // Cap history to last 100 commits to preserve storage
            if (commitHistory.size() > 100) {
                commitHistory.remove(0);
            }
            incrementGlobalRevision();
        }
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FormatVersion", formatVersion);
        if (teamId != null) {
            tag.putUUID("TeamUUID", teamId);
        }
        tag.putString("TeamName", teamName != null ? teamName : "Team Workspace");
        tag.putLong("LastSavedTime", lastSavedTime);
        tag.putInt("GlobalRevision", globalRevision);

        // Pages list
        ListTag pagesList = new ListTag();
        for (TeamWorkspacePage page : pages.values()) {
            pagesList.add(page.toNBT());
        }
        tag.put("Pages", pagesList);

        // Commit history list
        ListTag historyList = new ListTag();
        for (CommitLogEntry entry : commitHistory) {
            historyList.add(entry.toNBT());
        }
        tag.put("CommitHistory", historyList);

        return tag;
    }

    public static TeamWorkspaceData fromNBT(CompoundTag tag) {
        UUID teamId = tag.hasUUID("TeamUUID") ? tag.getUUID("TeamUUID") : UUID.randomUUID();
        String teamName = tag.getString("TeamName");
        TeamWorkspaceData data = new TeamWorkspaceData(teamId, teamName);
        data.formatVersion = tag.getInt("FormatVersion");
        data.lastSavedTime = tag.getLong("LastSavedTime");
        data.globalRevision = tag.getInt("GlobalRevision");

        data.pages.clear();
        if (tag.contains("Pages", Tag.TAG_LIST)) {
            ListTag pagesList = tag.getList("Pages", Tag.TAG_COMPOUND);
            for (int i = 0; i < pagesList.size(); i++) {
                TeamWorkspacePage page = TeamWorkspacePage.fromNBT(pagesList.getCompound(i));
                data.pages.put(page.getPageId(), page);
            }
        }

        data.commitHistory.clear();
        if (tag.contains("CommitHistory", Tag.TAG_LIST)) {
            ListTag historyList = tag.getList("CommitHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < historyList.size(); i++) {
                data.commitHistory.add(CommitLogEntry.fromNBT(historyList.getCompound(i)));
            }
        }

        if (data.pages.isEmpty()) {
            TeamWorkspacePage defaultPage = new TeamWorkspacePage("page_main", "Main Workspace");
            data.pages.put(defaultPage.getPageId(), defaultPage);
        }

        return data;
    }
}
