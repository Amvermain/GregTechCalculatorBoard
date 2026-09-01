package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class FolderBlueprintCodec {
    public static final String PREFIX = "GTFOLDER:";

    public static FolderBlueprintPackage createFolderPackage(String folderPath, String title, String description, String author) {
        String targetFolder = folderPath != null ? folderPath.trim() : "";
        String displayTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : (targetFolder.isEmpty() ? "Root" : targetFolder);

        FolderBlueprintPackage pkg = new FolderBlueprintPackage(displayTitle, description, author, System.currentTimeMillis());
        List<BoardPage> allPages = BoardManager.getInstance().getPages();

        for (BoardPage page : allPages) {
            String pFolder = page.getFolderPath() != null ? page.getFolderPath().trim() : "";
            boolean matches = false;
            String relativePath = "";

            if (targetFolder.isEmpty()) {
                matches = true;
                relativePath = pFolder;
            } else if (pFolder.equalsIgnoreCase(targetFolder)) {
                matches = true;
                relativePath = "";
            } else if (pFolder.toLowerCase().startsWith(targetFolder.toLowerCase() + "/")) {
                matches = true;
                relativePath = pFolder.substring(targetFolder.length() + 1);
            }

            if (matches) {
                pkg.addPage(new FolderBlueprintPackage.FolderPageEntry(
                        page.getName(),
                        relativePath,
                        page.getRepresentativeIcon(),
                        page.getPanX(),
                        page.getPanY(),
                        page.getZoom(),
                        page.getGraph()
                ));
            }
        }

        for (String f : BoardManager.getInstance().getAllFolders()) {
            if (targetFolder.isEmpty()) {
                pkg.addSubFolder(f);
            } else if (f.startsWith(targetFolder + "/")) {
                pkg.addSubFolder(f.substring(targetFolder.length() + 1));
            }
        }

        return pkg;
    }

    public static String exportToString(FolderBlueprintPackage pkg) {
        if (pkg == null) return "";
        try {
            CompoundTag tag = pkg.serializeNBT();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            String safeTitle = pkg.getRootFolderName().replace(":", "-").trim();
            if (!safeTitle.isEmpty()) {
                return PREFIX + safeTitle + ":" + b64;
            }
            return PREFIX + b64;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static FolderBlueprintPackage importPackageFromString(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.startsWith(PREFIX)) {
            trimmed = trimmed.substring(PREFIX.length()).trim();
        } else {
            return null;
        }

        String prefixTitle = null;
        String payload = trimmed;
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon != -1) {
            prefixTitle = trimmed.substring(0, lastColon).trim();
            payload = trimmed.substring(lastColon + 1).trim();
        }

        byte[] bytes = null;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (Exception e) {
            if (lastColon != -1) {
                try {
                    bytes = Base64.getDecoder().decode(trimmed);
                    prefixTitle = null;
                } catch (Exception ignored) {}
            }
        }

        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            CompoundTag tag = NbtIo.readCompressed(bais);
            if (tag != null) {
                FolderBlueprintPackage pkg = FolderBlueprintPackage.deserializeNBT(tag);
                if (pkg != null && prefixTitle != null && !prefixTitle.isEmpty()) {
                    if (pkg.getRootFolderName() == null || pkg.getRootFolderName().isEmpty() || "Folder".equals(pkg.getRootFolderName())) {
                        pkg.setRootFolderName(prefixTitle);
                    }
                }
                return pkg;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static int importFolderToBoardManager(FolderBlueprintPackage pkg, String targetParentFolder) {
        if (pkg == null || pkg.getPages().isEmpty()) return -1;

        BoardManager bm = BoardManager.getInstance();
        String baseFolder = (targetParentFolder != null && !targetParentFolder.trim().isEmpty())
                ? targetParentFolder.trim() + "/" + pkg.getRootFolderName()
                : pkg.getRootFolderName();

        baseFolder = resolveUniqueFolderName(baseFolder);

        int firstPageIndex = -1;
        for (FolderBlueprintPackage.FolderPageEntry entry : pkg.getPages()) {
            String fullFolder = entry.relativeFolderPath().isEmpty()
                    ? baseFolder
                    : (baseFolder + "/" + entry.relativeFolderPath());

            FlowGraph graphCopy = entry.graph() != null ? entry.graph() : new FlowGraph();
            BoardPage page = new BoardPage(java.util.UUID.randomUUID().toString(), entry.name(), graphCopy);
            page.setFolderPath(fullFolder);
            page.setRepresentativeIcon(entry.icon());
            page.setPanX(entry.panX());
            page.setPanY(entry.panY());
            page.setZoom(entry.zoom());

            bm.addPage(page);

            int idx = bm.getPages().indexOf(page);
            if (firstPageIndex == -1) {
                firstPageIndex = idx;
            }
        }

        return firstPageIndex;
    }

    private static String resolveUniqueFolderName(String baseFolder) {
        List<String> existing = BoardManager.getInstance().getAllFolders();
        if (!existing.contains(baseFolder)) {
            return baseFolder;
        }
        int counter = 1;
        while (existing.contains(baseFolder + " (" + counter + ")")) {
            counter++;
        }
        return baseFolder + " (" + counter + ")";
    }
}
