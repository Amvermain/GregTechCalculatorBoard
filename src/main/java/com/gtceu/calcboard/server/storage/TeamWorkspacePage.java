package com.gtceu.calcboard.server.storage;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/**
 * Represents a single calculation board page within a team's shared workspace.
 */
public class TeamWorkspacePage {

    private String pageId;
    private String title;
    private int pageRevision;
    private UUID lockHolderUUID;
    private long lockExpiresTimestamp;
    private byte[] compressedGraphData;

    public TeamWorkspacePage(String pageId, String title) {
        this.pageId = pageId;
        this.title = title != null ? title : "Page";
        this.pageRevision = 1;
        this.lockHolderUUID = null;
        this.lockExpiresTimestamp = 0L;
        this.compressedGraphData = new byte[0];
    }

    public TeamWorkspacePage(String pageId, String title, int revision, byte[] compressedGraphData) {
        this.pageId = pageId;
        this.title = title != null ? title : "Page";
        this.pageRevision = revision;
        this.lockHolderUUID = null;
        this.lockExpiresTimestamp = 0L;
        this.compressedGraphData = compressedGraphData != null ? compressedGraphData : new byte[0];
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPageRevision() {
        return pageRevision;
    }

    public void setPageRevision(int pageRevision) {
        this.pageRevision = pageRevision;
    }

    public void incrementRevision() {
        this.pageRevision++;
    }

    public UUID getLockHolderUUID() {
        return lockHolderUUID;
    }

    public void setLockHolderUUID(UUID lockHolderUUID) {
        this.lockHolderUUID = lockHolderUUID;
    }

    public long getLockExpiresTimestamp() {
        return lockExpiresTimestamp;
    }

    public void setLockExpiresTimestamp(long lockExpiresTimestamp) {
        this.lockExpiresTimestamp = lockExpiresTimestamp;
    }

    public boolean isLocked() {
        return lockHolderUUID != null && System.currentTimeMillis() < lockExpiresTimestamp;
    }

    public byte[] getCompressedGraphData() {
        return compressedGraphData;
    }

    public void setCompressedGraphData(byte[] compressedGraphData) {
        this.compressedGraphData = compressedGraphData != null ? compressedGraphData : new byte[0];
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PageId", pageId);
        tag.putString("PageTitle", title != null ? title : "Page");
        tag.putInt("PageRevision", pageRevision);
        if (lockHolderUUID != null) {
            tag.putUUID("LockHolderUUID", lockHolderUUID);
            tag.putLong("LockExpires", lockExpiresTimestamp);
        }
        tag.putByteArray("CompressedGraphData", compressedGraphData != null ? compressedGraphData : new byte[0]);
        return tag;
    }

    public static TeamWorkspacePage fromNBT(CompoundTag tag) {
        String id = tag.getString("PageId");
        String title = tag.getString("PageTitle");
        TeamWorkspacePage page = new TeamWorkspacePage(id, title);
        page.setPageRevision(tag.getInt("PageRevision"));
        if (tag.hasUUID("LockHolderUUID")) {
            page.setLockHolderUUID(tag.getUUID("LockHolderUUID"));
            page.setLockExpiresTimestamp(tag.getLong("LockExpires"));
        }
        page.setCompressedGraphData(tag.getByteArray("CompressedGraphData"));
        return page;
    }
}
