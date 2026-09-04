package com.gtceu.calcboard.api.storage;

/**
 * Public API lifecycle listener interface for monitoring folder modifications, movements, and deletions.
 * Third-party addons and external mods can register listeners via {@link BoardManager#addFolderChangeListener(IFolderChangeListener)}.
 */
@FunctionalInterface
public interface IFolderChangeListener {

    enum FolderAction {
        CREATED,
        RENAMED,
        MOVED,
        DELETED
    }

    record FolderChangeEvent(
            FolderAction action,
            String oldPath,
            String newPath
    ) {
        public static FolderChangeEvent created(String folderPath) {
            return new FolderChangeEvent(FolderAction.CREATED, "", folderPath);
        }

        public static FolderChangeEvent renamed(String oldPath, String newPath) {
            return new FolderChangeEvent(FolderAction.RENAMED, oldPath, newPath);
        }

        public static FolderChangeEvent moved(String sourcePath, String targetParentPath) {
            return new FolderChangeEvent(FolderAction.MOVED, sourcePath, targetParentPath);
        }

        public static FolderChangeEvent deleted(String folderPath) {
            return new FolderChangeEvent(FolderAction.DELETED, folderPath, "");
        }
    }

    /**
     * Invoked immediately when a folder is created, renamed, moved, or deleted in BoardManager.
     *
     * @param event the event payload containing action type, old path, and new path
     */
    void onFolderChanged(FolderChangeEvent event);
}
