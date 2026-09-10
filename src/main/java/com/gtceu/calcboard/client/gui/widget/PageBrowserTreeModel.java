package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;

import java.util.*;

public final class PageBrowserTreeModel {

    private PageBrowserTreeModel() {}

    public record TreeItemRef(boolean isFolder, String idOrPath) {}

    public record IndexedPage(int index, BoardPage page) {}

    public static class FolderTreeNode {
        public final String folderPath;
        public final String simpleName;
        public final int depth;
        public final Map<String, FolderTreeNode> subFolders = new TreeMap<>();
        public final List<IndexedPage> directPages = new ArrayList<>();

        public FolderTreeNode(String folderPath, String simpleName, int depth) {
            this.folderPath = folderPath;
            this.simpleName = simpleName;
            this.depth = depth;
        }

        public int getTotalPageCount() {
            int count = directPages.size();
            for (FolderTreeNode sub : subFolders.values()) {
                count += sub.getTotalPageCount();
            }
            return count;
        }
    }

    public static FolderTreeNode buildFolderTree(String query) {
        FolderTreeNode root = new FolderTreeNode("", "", 0);
        List<BoardPage> pages = BoardManager.getInstance().getPages();
        for (int i = 0; i < pages.size(); i++) {
            BoardPage p = pages.get(i);
            boolean isModule = p.isModuleSubPage();
            String f = isModule
                    ? ("📦 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.subpage.module_section").getString())
                    : (p.getFolderPath() != null ? p.getFolderPath().trim() : "");
            if (!query.isEmpty() && !p.getName().toLowerCase().contains(query) && !f.toLowerCase().contains(query)) {
                continue;
            }
            if (f.isEmpty()) {
                root.directPages.add(new IndexedPage(i, p));
            } else {
                insertPageIntoTree(root, f, new IndexedPage(i, p));
            }
        }
        return root;
    }

    private static void insertPageIntoTree(FolderTreeNode root, String folderPath, IndexedPage indexedPage) {
        String[] parts = folderPath.split("/");
        FolderTreeNode current = root;
        StringBuilder pathAcc = new StringBuilder();
        for (int depth = 0; depth < parts.length; depth++) {
            String part = parts[depth];
            if (pathAcc.length() > 0) pathAcc.append("/");
            pathAcc.append(part);
            String curPath = pathAcc.toString();
            int curDepth = depth + 1;
            current = current.subFolders.computeIfAbsent(part, k -> new FolderTreeNode(curPath, part, curDepth));
        }
        current.directPages.add(indexedPage);
    }

    public static void collectVisibleItems(FolderTreeNode node, List<TreeItemRef> result, String query, Set<String> collapsedFolders) {
        if (!node.folderPath.isEmpty()) {
            result.add(new TreeItemRef(true, node.folderPath));
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return;
            }
        }
        for (FolderTreeNode sub : node.subFolders.values()) {
            collectVisibleItems(sub, result, query, collapsedFolders);
        }
        for (IndexedPage ip : node.directPages) {
            result.add(new TreeItemRef(false, ip.page().getId()));
        }
    }

    public static void applyRangeSelection(
            FolderTreeNode root,
            TreeItemRef startRef,
            TreeItemRef endRef,
            String query,
            Set<String> collapsedFolders,
            Set<String> selectedFolderPaths,
            Set<String> selectedPageIds
    ) {
        List<TreeItemRef> visibleItems = new ArrayList<>();
        collectVisibleItems(root, visibleItems, query, collapsedFolders);

        int startIdx = visibleItems.indexOf(startRef);
        int endIdx = visibleItems.indexOf(endRef);
        if (startIdx >= 0 && endIdx >= 0) {
            int min = Math.min(startIdx, endIdx);
            int max = Math.max(startIdx, endIdx);
            for (int i = min; i <= max; i++) {
                TreeItemRef ref = visibleItems.get(i);
                if (ref.isFolder()) {
                    selectedFolderPaths.add(ref.idOrPath());
                } else {
                    selectedPageIds.add(ref.idOrPath());
                }
            }
        } else {
            if (endRef.isFolder()) {
                selectedFolderPaths.add(endRef.idOrPath());
            } else {
                selectedPageIds.add(endRef.idOrPath());
            }
        }
    }

    public static int calculateNodeHeight(FolderTreeNode node, int startY, String query, Set<String> collapsedFolders, int itemHeight) {
        int curY = startY;
        if (!node.folderPath.isEmpty()) {
            curY += 18;
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return curY;
            }
        }
        for (FolderTreeNode sub : node.subFolders.values()) {
            curY = calculateNodeHeight(sub, curY, query, collapsedFolders, itemHeight);
        }
        curY += node.directPages.size() * (itemHeight + 2);
        return curY;
    }

    public static String resolveFolderUnderMouse(FolderTreeNode root, int listY, double scrollY, double mouseY, String query, Set<String> collapsedFolders, int itemHeight) {
        int curY = listY + 4 - (int) scrollY;
        String found = resolveFolderUnderMouseRecursive(root, curY, mouseY, query, collapsedFolders, itemHeight);
        return found != null ? found : "";
    }

    private static String resolveFolderUnderMouseRecursive(FolderTreeNode node, int curY, double mouseY, String query, Set<String> collapsedFolders, int itemHeight) {
        if (!node.folderPath.isEmpty()) {
            if (mouseY >= curY && mouseY <= curY + 16) {
                return node.folderPath;
            }
            curY += 18;
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return null;
            }
        }
        for (FolderTreeNode sub : node.subFolders.values()) {
            String found = resolveFolderUnderMouseRecursive(sub, curY, mouseY, query, collapsedFolders, itemHeight);
            if (found != null) return found;
            curY = calculateNodeHeight(sub, curY, query, collapsedFolders, itemHeight);
        }
        for (IndexedPage ignored : node.directPages) {
            if (mouseY >= curY && mouseY <= curY + itemHeight) {
                return node.folderPath;
            }
            curY += itemHeight + 2;
        }
        return null;
    }
}
