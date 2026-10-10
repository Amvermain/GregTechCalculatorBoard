package com.gtceu.calcboard.api.team;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/**
 * Represents a single calculation board page within a team's shared workspace.
 */
public class TeamWorkspacePage {

    private String pageId;
    private String title;
    private String folderPath = "";
    private com.gtceu.calcboard.api.storage.PageType pageType = com.gtceu.calcboard.api.storage.PageType.STANDARD;
    private String parentPageId = "";
    private String parentModuleNodeId = "";
    private int pageRevision;
    private UUID lockHolderUUID;
    private String lockHolderName = "";
    private long lockExpiresTimestamp;
    private byte[] compressedGraphData;
    private byte defaultVoltageTierOrdinal = -1;
    private boolean autoEquipEnergyHatches = true;

    public TeamWorkspacePage(String pageId, String title) {
        this.pageId = pageId;
        this.title = title != null ? title : "Page";
        this.pageRevision = 1;
        this.lockHolderUUID = null;
        this.lockHolderName = "";
        this.lockExpiresTimestamp = 0L;
        this.compressedGraphData = new byte[0];
    }

    public TeamWorkspacePage(String pageId, String title, int revision, byte[] compressedGraphData) {
        this.pageId = pageId;
        this.title = title != null ? title : "Page";
        this.pageRevision = revision;
        this.lockHolderUUID = null;
        this.lockHolderName = "";
        this.lockExpiresTimestamp = 0L;
        this.compressedGraphData = compressedGraphData != null ? compressedGraphData : new byte[0];
    }

    public TeamWorkspacePage(String pageId, String title, String folderPath, int revision, byte[] compressedGraphData) {
        this(pageId, title, revision, compressedGraphData);
        this.folderPath = folderPath != null ? folderPath : "";
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

    public String getLockHolderName() {
        return lockHolderName;
    }

    public void setLockHolderName(String lockHolderName) {
        this.lockHolderName = lockHolderName != null ? lockHolderName : "";
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

    public String getFolderPath() {
        return folderPath != null ? folderPath : "";
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath != null ? folderPath.trim() : "";
    }

    /**
     * Gets the functional page type.
     *
     * @return the page type, never null
     */
    public com.gtceu.calcboard.api.storage.PageType getPageType() {
        return pageType != null ? pageType : com.gtceu.calcboard.api.storage.PageType.STANDARD;
    }

    /**
     * Sets the functional page type.
     *
     * @param pageType the page type to set
     */
    public void setPageType(com.gtceu.calcboard.api.storage.PageType pageType) {
        this.pageType = pageType != null ? pageType : com.gtceu.calcboard.api.storage.PageType.STANDARD;
    }

    /**
     * Checks if this page is a composite module subpage.
     *
     * @return true if this page is of type MODULE
     */
    public boolean isModuleSubPage() {
        return getPageType() == com.gtceu.calcboard.api.storage.PageType.MODULE;
    }

    /**
     * Gets the parent page identifier if this page is a subpage.
     *
     * @return the parent page id, or empty string if root page
     */
    public String getParentPageId() {
        return parentPageId != null ? parentPageId : "";
    }

    /**
     * Sets the parent page identifier.
     *
     * @param parentPageId the parent page id to set
     */
    public void setParentPageId(String parentPageId) {
        this.parentPageId = parentPageId != null ? parentPageId : "";
    }

    /**
     * Gets the parent module node identifier on the parent page.
     *
     * @return the parent module node id, or empty string
     */
    public String getParentModuleNodeId() {
        return parentModuleNodeId != null ? parentModuleNodeId : "";
    }

    /**
     * Sets the parent module node identifier.
     *
     * @param parentModuleNodeId the parent module node id to set
     */
    public void setParentModuleNodeId(String parentModuleNodeId) {
        this.parentModuleNodeId = parentModuleNodeId != null ? parentModuleNodeId : "";
    }

    public byte getDefaultVoltageTierOrdinal() {
        return defaultVoltageTierOrdinal;
    }

    public void setDefaultVoltageTierOrdinal(byte defaultVoltageTierOrdinal) {
        this.defaultVoltageTierOrdinal = defaultVoltageTierOrdinal;
    }

    public boolean isAutoEquipEnergyHatches() {
        return autoEquipEnergyHatches;
    }

    public void setAutoEquipEnergyHatches(boolean autoEquipEnergyHatches) {
        this.autoEquipEnergyHatches = autoEquipEnergyHatches;
    }

    public void applySettings(byte defaultVoltageTierOrdinal, boolean autoEquipEnergyHatches) {
        this.defaultVoltageTierOrdinal = defaultVoltageTierOrdinal;
        this.autoEquipEnergyHatches = autoEquipEnergyHatches;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PageId", pageId);
        tag.putString("PageTitle", title != null ? title : "Page");
        if (folderPath != null && !folderPath.isEmpty()) {
            tag.putString("FolderPath", folderPath);
        }
        tag.putString("PageType", getPageType().name());
        if (parentPageId != null && !parentPageId.isEmpty()) {
            tag.putString("ParentPageId", parentPageId);
        }
        if (parentModuleNodeId != null && !parentModuleNodeId.isEmpty()) {
            tag.putString("ParentModuleNodeId", parentModuleNodeId);
        }
        tag.putInt("PageRevision", pageRevision);
        if (lockHolderUUID != null) {
            tag.putUUID("LockHolderUUID", lockHolderUUID);
            tag.putString("LockHolderName", lockHolderName != null ? lockHolderName : "");
            tag.putLong("LockExpires", lockExpiresTimestamp);
        }
        tag.putByteArray("CompressedGraphData", compressedGraphData != null ? compressedGraphData : new byte[0]);
        tag.putByte("DefaultVoltageTier", defaultVoltageTierOrdinal);
        tag.putBoolean("AutoEquipEnergyHatches", autoEquipEnergyHatches);
        return tag;
    }

    public static TeamWorkspacePage fromNBT(CompoundTag tag) {
        String id = tag.getString("PageId");
        String title = tag.getString("PageTitle");
        TeamWorkspacePage page = new TeamWorkspacePage(id, title);
        page.setPageRevision(tag.getInt("PageRevision"));
        if (tag.contains("FolderPath")) {
            page.setFolderPath(tag.getString("FolderPath"));
        }
        if (tag.contains("PageType")) {
            page.setPageType(com.gtceu.calcboard.api.storage.PageType.fromNameSafe(tag.getString("PageType"), com.gtceu.calcboard.api.storage.PageType.STANDARD));
        }
        if (tag.contains("ParentPageId")) {
            page.setParentPageId(tag.getString("ParentPageId"));
        }
        if (tag.contains("ParentModuleNodeId")) {
            page.setParentModuleNodeId(tag.getString("ParentModuleNodeId"));
        }
        if (tag.hasUUID("LockHolderUUID")) {
            page.setLockHolderUUID(tag.getUUID("LockHolderUUID"));
            if (tag.contains("LockHolderName")) {
                page.setLockHolderName(tag.getString("LockHolderName"));
            }
            page.setLockExpiresTimestamp(tag.getLong("LockExpires"));
        }
        page.setCompressedGraphData(tag.getByteArray("CompressedGraphData"));
        if (tag.contains("DefaultVoltageTier")) {
            page.setDefaultVoltageTierOrdinal(tag.getByte("DefaultVoltageTier"));
        } else {
            page.setDefaultVoltageTierOrdinal((byte) -1);
        }
        if (tag.contains("AutoEquipEnergyHatches")) {
            page.setAutoEquipEnergyHatches(tag.getBoolean("AutoEquipEnergyHatches"));
        } else {
            page.setAutoEquipEnergyHatches(true);
        }
        return page;
    }
}
