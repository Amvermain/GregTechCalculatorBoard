package com.gtceu.calcboard.api.storage;

/**
 * Public API lifecycle listener interface for monitoring page creation, deletion, switching, and folder relocation.
 * Third-party addons and extensions can register listeners via {@link BoardManager#addPageLifecycleListener(IPageLifecycleListener)}.
 */
public interface IPageLifecycleListener {

    /**
     * Invoked when a new page is added to the board.
     *
     * @param page  the newly created page
     * @param index the index in the page list
     */
    default void onPageAdded(BoardPage page, int index) {}

    /**
     * Invoked when the user switches to a different active page.
     *
     * @param previousPage the previously active page, or null if none
     * @param newPage      the newly selected active page
     * @param newIndex     the index of the newly active page
     */
    default void onPageSwitched(BoardPage previousPage, BoardPage newPage, int newIndex) {}

    /**
     * Invoked when a page is removed from the board.
     *
     * @param removedPage the page that was removed
     * @param oldIndex    the index the page previously occupied
     */
    default void onPageRemoved(BoardPage removedPage, int oldIndex) {}

    /**
     * Invoked when a page is relocated to a different folder.
     *
     * @param page          the target page
     * @param oldFolderPath the previous folder path
     * @param newFolderPath the new folder path
     */
    default void onPageFolderChanged(BoardPage page, String oldFolderPath, String newFolderPath) {}

    /**
     * Invoked when a page is docked into the active open tabs bar.
     *
     * @param page   the opened page
     * @param pageId the page ID
     */
    default void onTabOpened(BoardPage page, String pageId) {}

    /**
     * Invoked when a page tab is undocked/closed from the top bar (not deleted).
     *
     * @param page   the closed page
     * @param pageId the page ID
     */
    default void onTabClosed(BoardPage page, String pageId) {}
}
