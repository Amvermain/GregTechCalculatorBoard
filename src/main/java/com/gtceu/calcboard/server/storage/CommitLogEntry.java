package com.gtceu.calcboard.server.storage;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/**
 * Represents a single commit entry in the workspace revision history.
 */
public class CommitLogEntry {

    private int revision;
    private UUID authorUUID;
    private String authorName;
    private long timestamp;
    private String pageId;
    private String message;
    private int addedNodes;
    private int modifiedNodes;
    private int deletedNodes;

    public CommitLogEntry(int revision, UUID authorUUID, String authorName, long timestamp, String pageId, String message, int addedNodes, int modifiedNodes, int deletedNodes) {
        this.revision = revision;
        this.authorUUID = authorUUID;
        this.authorName = authorName != null ? authorName : "Unknown";
        this.timestamp = timestamp;
        this.pageId = pageId != null ? pageId : "default";
        this.message = message != null ? message : "";
        this.addedNodes = addedNodes;
        this.modifiedNodes = modifiedNodes;
        this.deletedNodes = deletedNodes;
    }

    public int getRevision() {
        return revision;
    }

    public UUID getAuthorUUID() {
        return authorUUID;
    }

    public String getAuthorName() {
        return authorName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPageId() {
        return pageId;
    }

    public String getMessage() {
        return message;
    }

    public int getAddedNodes() {
        return addedNodes;
    }

    public int getModifiedNodes() {
        return modifiedNodes;
    }

    public int getDeletedNodes() {
        return deletedNodes;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Revision", revision);
        if (authorUUID != null) {
            tag.putUUID("AuthorUUID", authorUUID);
        }
        tag.putString("AuthorName", authorName != null ? authorName : "Unknown");
        tag.putLong("Timestamp", timestamp);
        tag.putString("PageId", pageId != null ? pageId : "default");
        tag.putString("Message", message != null ? message : "");
        tag.putInt("AddedNodes", addedNodes);
        tag.putInt("ModifiedNodes", modifiedNodes);
        tag.putInt("DeletedNodes", deletedNodes);
        return tag;
    }

    public void encode(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(revision);
        buf.writeBoolean(authorUUID != null);
        if (authorUUID != null) {
            buf.writeUUID(authorUUID);
        }
        buf.writeUtf(authorName != null ? authorName : "Unknown");
        buf.writeLong(timestamp);
        buf.writeUtf(pageId != null ? pageId : "default");
        buf.writeUtf(message != null ? message : "");
        buf.writeVarInt(addedNodes);
        buf.writeVarInt(modifiedNodes);
        buf.writeVarInt(deletedNodes);
    }

    public CommitLogEntry(net.minecraft.network.FriendlyByteBuf buf) {
        this.revision = buf.readVarInt();
        this.authorUUID = buf.readBoolean() ? buf.readUUID() : null;
        this.authorName = buf.readUtf(256);
        this.timestamp = buf.readLong();
        this.pageId = buf.readUtf(256);
        this.message = buf.readUtf(1024);
        this.addedNodes = buf.readVarInt();
        this.modifiedNodes = buf.readVarInt();
        this.deletedNodes = buf.readVarInt();
    }

    public static CommitLogEntry fromNBT(CompoundTag tag) {
        int revision = tag.getInt("Revision");
        UUID authorUUID = tag.hasUUID("AuthorUUID") ? tag.getUUID("AuthorUUID") : null;
        String authorName = tag.getString("AuthorName");
        long timestamp = tag.getLong("Timestamp");
        String pageId = tag.getString("PageId");
        String message = tag.getString("Message");
        int added = tag.getInt("AddedNodes");
        int modified = tag.getInt("ModifiedNodes");
        int deleted = tag.getInt("DeletedNodes");
        return new CommitLogEntry(revision, authorUUID, authorName, timestamp, pageId, message, added, modified, deleted);
    }
}
