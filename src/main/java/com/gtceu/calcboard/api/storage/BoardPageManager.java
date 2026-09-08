package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages pages, open tabs, folders, and page lifecycle events for the Calculator Board.
 */
public class BoardPageManager {

    private final List<BoardPage> pages = new ArrayList<>();
    private int activePageIndex = 0;
    private final List<String> openPageIds = new ArrayList<>();

    private final List<IFolderChangeListener> folderChangeListeners = new CopyOnWriteArrayList<>();
    private final List<IPageLifecycleListener> pageLifecycleListeners = new CopyOnWriteArrayList<>();
    private Consumer<BoardPage> pageRemovalListener = null;

    public BoardPageManager() {
        resetToDefault();
    }

    public void resetToDefault() {
        this.pages.clear();
        this.pages.add(BoardPage.createDefault("Page 1"));
        this.activePageIndex = 0;
        this.openPageIds.clear();
        this.openPageIds.add(this.pages.get(0).getId());
    }

    public List<BoardPage> getPages() {
        if (pages.isEmpty()) {
            pages.add(BoardPage.createDefault("Page 1"));
        }
        return pages;
    }

    public int getActivePageIndex() {
        if (activePageIndex < 0 || activePageIndex >= pages.size()) {
            activePageIndex = 0;
        }
        return activePageIndex;
    }

    public BoardPage getActivePage() {
        if (pages.isEmpty()) {
            pages.add(BoardPage.createDefault("Page 1"));
            activePageIndex = 0;
        }
        if (activePageIndex < 0 || activePageIndex >= pages.size()) {
            activePageIndex = 0;
        }
        return pages.get(activePageIndex);
    }

    public FlowGraph getActiveGraph() {
        return getActivePage().getGraph();
    }

    public BoardPage addPage(String name) {
        return addPage(name, "");
    }

    public BoardPage addPage(String name, String folderPath) {
        String finalName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Page " + (pages.size() + 1);
        BoardPage newPage = BoardPage.createDefault(finalName);
        if (folderPath != null) {
            newPage.setFolderPath(folderPath);
        }
        addPage(newPage);
        return newPage;
    }

    public void addPage(BoardPage page) {
        if (page == null) return;
        pages.add(page);
        int idx = pages.size() - 1;
        activePageIndex = idx;
        if (!openPageIds.contains(page.getId())) {
            openPageIds.add(page.getId());
            notifyTabOpened(page, page.getId());
        }
        for (IPageLifecycleListener l : pageLifecycleListeners) {
            try {
                l.onPageAdded(page, idx);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void setActivePageIndex(int index) {
        switchPage(index);
    }

    public void setPageRemovalListener(Consumer<BoardPage> listener) {
        this.pageRemovalListener = listener;
    }

    public boolean removePage(int index) {
        if (pages.size() <= 1) {
            pages.get(0).getGraph().clear();
            return false;
        }
        if (index >= 0 && index < pages.size()) {
            BoardPage removed = pages.remove(index);
            openPageIds.remove(removed.getId());
            if (activePageIndex >= pages.size()) {
                activePageIndex = pages.size() - 1;
            }
            if (openPageIds.isEmpty() && !pages.isEmpty()) {
                openPageIds.add(getActivePage().getId());
            }
            if (pageRemovalListener != null) {
                pageRemovalListener.accept(removed);
            }
            for (IPageLifecycleListener l : pageLifecycleListeners) {
                try {
                    l.onPageRemoved(removed, index);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            return true;
        }
        return false;
    }

    public void switchPage(int index) {
        if (index >= 0 && index < pages.size() && index != activePageIndex) {
            BoardPage prev = getActivePage();
            this.activePageIndex = index;
            BoardPage cur = getActivePage();
            if (!openPageIds.contains(cur.getId())) {
                openPageIds.add(cur.getId());
                notifyTabOpened(cur, cur.getId());
            }
            for (IPageLifecycleListener l : pageLifecycleListeners) {
                try {
                    l.onPageSwitched(prev, cur, index);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void renamePage(int index, String newName) {
        if (index >= 0 && index < pages.size()) {
            pages.get(index).setName(newName);
        }
    }

    public void setFolderPath(int index, String folderPath) {
        if (index >= 0 && index < pages.size()) {
            pages.get(index).setFolderPath(folderPath);
        }
    }

    public Optional<BoardPage> getPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return Optional.empty();
        for (BoardPage page : pages) {
            if (page.getId().equals(pageId)) {
                return Optional.of(page);
            }
        }
        return Optional.empty();
    }

    public List<String> getOpenPageIds() {
        if (openPageIds.isEmpty()) {
            openPageIds.add(getActivePage().getId());
        }
        return Collections.unmodifiableList(openPageIds);
    }

    public List<BoardPage> getOpenPages() {
        List<BoardPage> list = new ArrayList<>();
        for (String id : getOpenPageIds()) {
            getPage(id).ifPresent(list::add);
        }
        if (list.isEmpty()) {
            BoardPage active = getActivePage();
            list.add(active);
            if (!openPageIds.contains(active.getId())) {
                openPageIds.add(active.getId());
            }
        }
        return list;
    }

    public boolean openPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return false;
        Optional<BoardPage> opt = getPage(pageId);
        if (opt.isEmpty()) return false;

        BoardPage page = opt.get();
        if (!openPageIds.contains(pageId)) {
            openPageIds.add(pageId);
            notifyTabOpened(page, pageId);
        }

        int index = pages.indexOf(page);
        if (index >= 0) {
            switchPage(index);
        }
        return true;
    }

    public boolean openPage(BoardPage page) {
        if (page == null) return false;
        return openPage(page.getId());
    }

    public void closeTab(String pageId) {
        if (pageId == null || pageId.isEmpty()) return;
        int idx = openPageIds.indexOf(pageId);
        if (idx < 0) return;

        openPageIds.remove(idx);
        Optional<BoardPage> closedOpt = getPage(pageId);
        closedOpt.ifPresent(p -> notifyTabClosed(p, pageId));

        if (openPageIds.isEmpty()) {
            if (!pages.isEmpty()) {
                openPage(pages.get(0).getId());
            }
            return;
        }

        if (getActivePage().getId().equals(pageId)) {
            int nextIdx = Math.min(idx, openPageIds.size() - 1);
            String nextId = openPageIds.get(nextIdx);
            openPage(nextId);
        }
    }

    public void closeOtherTabs(String keepPageId) {
        if (keepPageId == null || keepPageId.isEmpty()) return;
        List<String> toClose = new ArrayList<>(openPageIds);
        for (String id : toClose) {
            if (!id.equals(keepPageId)) {
                closeTab(id);
            }
        }
    }

    public void closeAllTabs() {
        openPageIds.clear();
        if (!pages.isEmpty()) {
            openPage(pages.get(0).getId());
        }
    }

    public boolean isTabOpen(String pageId) {
        return openPageIds.contains(pageId);
    }

    public List<String> getAllFolders() {
        Set<String> folders = new TreeSet<>();
        for (BoardPage page : pages) {
            String fp = page.getFolderPath();
            if (fp != null && !fp.trim().isEmpty()) {
                folders.add(fp.trim());
            }
        }
        return new ArrayList<>(folders);
    }

    public List<BoardPage> getPagesInFolder(String folderPath) {
        String target = folderPath != null ? folderPath.trim() : "";
        List<BoardPage> list = new ArrayList<>();
        for (BoardPage page : pages) {
            if (target.equalsIgnoreCase(page.getFolderPath().trim())) {
                list.add(page);
            }
        }
        return list;
    }

    public void movePageToFolder(int pageIndex, String newFolderPath) {
        if (pageIndex >= 0 && pageIndex < pages.size()) {
            BoardPage page = pages.get(pageIndex);
            String oldFolder = page.getFolderPath();
            String newFolder = newFolderPath != null ? newFolderPath.trim() : "";
            page.setFolderPath(newFolder);
            for (IPageLifecycleListener l : pageLifecycleListeners) {
                try {
                    l.onPageFolderChanged(page, oldFolder, newFolder);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void renameFolder(String oldFolderPath, String newFolderPath) {
        if (oldFolderPath == null || oldFolderPath.trim().isEmpty()) return;
        String oldP = oldFolderPath.trim();
        String newP = newFolderPath != null ? newFolderPath.trim() : "";
        for (BoardPage page : pages) {
            String curP = page.getFolderPath().trim();
            if (curP.equals(oldP)) {
                page.setFolderPath(newP);
            } else if (curP.startsWith(oldP + "/")) {
                page.setFolderPath(newP + curP.substring(oldP.length()));
            }
        }
        notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.renamed(oldP, newP));
    }

    public boolean moveFolder(String sourceFolderPath, String targetParentFolderPath) {
        if (sourceFolderPath == null || sourceFolderPath.trim().isEmpty()) return false;
        String src = sourceFolderPath.trim();
        String tgt = targetParentFolderPath != null ? targetParentFolderPath.trim() : "";

        if (tgt.equals(src) || tgt.startsWith(src + "/")) {
            return false;
        }

        int lastSlash = src.lastIndexOf('/');
        String simpleName = (lastSlash >= 0) ? src.substring(lastSlash + 1) : src;
        String newFolderPath = tgt.isEmpty() ? simpleName : (tgt + "/" + simpleName);

        if (newFolderPath.equals(src)) {
            return true;
        }

        renameFolder(src, newFolderPath);
        notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.moved(src, tgt));
        return true;
    }

    public void deleteFolder(String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) return;
        String target = folderPath.trim();
        for (BoardPage page : pages) {
            String curP = page.getFolderPath().trim();
            if (curP.equals(target) || curP.startsWith(target + "/")) {
                page.setFolderPath("");
            }
        }
        notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.deleted(target));
    }

    public void addFolderChangeListener(IFolderChangeListener listener) {
        if (listener != null) this.folderChangeListeners.add(listener);
    }

    public void removeFolderChangeListener(IFolderChangeListener listener) {
        if (listener != null) this.folderChangeListeners.remove(listener);
    }

    public void addPageLifecycleListener(IPageLifecycleListener listener) {
        if (listener != null) this.pageLifecycleListeners.add(listener);
    }

    public void removePageLifecycleListener(IPageLifecycleListener listener) {
        if (listener != null) this.pageLifecycleListeners.remove(listener);
    }

    public void notifyFolderCreated(String folderPath) {
        if (folderPath != null && !folderPath.trim().isEmpty()) {
            notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.created(folderPath.trim()));
        }
    }

    private void notifyFolderChanged(IFolderChangeListener.FolderChangeEvent event) {
        for (IFolderChangeListener listener : folderChangeListeners) {
            try {
                listener.onFolderChanged(event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void notifyTabOpened(BoardPage page, String pageId) {
        for (IPageLifecycleListener listener : pageLifecycleListeners) {
            try {
                listener.onTabOpened(page, pageId);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void notifyTabClosed(BoardPage page, String pageId) {
        for (IPageLifecycleListener listener : pageLifecycleListeners) {
            try {
                listener.onTabClosed(page, pageId);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void serializeNBT(CompoundTag rootTag) {
        if (rootTag == null) return;
        ListTag pageList = new ListTag();
        for (BoardPage page : pages) {
            pageList.add(page.serializeNBT());
        }
        rootTag.put("pages", pageList);
        rootTag.putInt("activePageIndex", getActivePageIndex());

        ListTag openTabsList = new ListTag();
        for (String id : getOpenPageIds()) {
            CompoundTag tabTag = new CompoundTag();
            tabTag.putString("id", id);
            openTabsList.add(tabTag);
        }
        rootTag.put("openTabs", openTabsList);
    }

    public void deserializeNBT(CompoundTag rootTag) {
        if (rootTag == null) return;
        if (rootTag.contains("pages", Tag.TAG_LIST)) {
            ListTag pageList = rootTag.getList("pages", Tag.TAG_COMPOUND);
            this.pages.clear();
            for (int i = 0; i < pageList.size(); i++) {
                this.pages.add(BoardPage.deserializeNBT(pageList.getCompound(i)));
            }
            if (this.pages.isEmpty()) {
                this.pages.add(BoardPage.createDefault("Page 1"));
            }
            this.activePageIndex = Math.max(0, Math.min(this.pages.size() - 1, rootTag.getInt("activePageIndex")));
        } else {
            FlowGraph loaded = FlowGraph.deserializeNBT(rootTag);
            this.pages.clear();
            BoardPage p = BoardPage.createDefault("Page 1");
            p.getGraph().copyFrom(loaded);
            if (rootTag.contains("panX")) p.setPanX(rootTag.getDouble("panX"));
            if (rootTag.contains("panY")) p.setPanY(rootTag.getDouble("panY"));
            if (rootTag.contains("zoom")) p.setZoom(rootTag.getDouble("zoom"));
            this.pages.add(p);
            this.activePageIndex = 0;
        }

        this.openPageIds.clear();
        if (rootTag.contains("openTabs", Tag.TAG_LIST)) {
            ListTag openTabsList = rootTag.getList("openTabs", Tag.TAG_COMPOUND);
            for (int i = 0; i < openTabsList.size(); i++) {
                String id = openTabsList.getCompound(i).getString("id");
                if (getPage(id).isPresent()) {
                    this.openPageIds.add(id);
                }
            }
        }
        if (this.openPageIds.isEmpty() && !this.pages.isEmpty()) {
            this.openPageIds.add(getActivePage().getId());
        }
    }
}
