package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FolderBlueprintPackage {
    public static final String FORMAT = "gtcalcboard_folder_blueprint";
    public static final int CURRENT_VERSION = 1;

    private String rootFolderName;
    private String description;
    private String author;
    private final long createdAt;
    private final List<FolderPageEntry> pages = new ArrayList<>();
    private final List<String> subFolders = new ArrayList<>();

    public record FolderPageEntry(
            String name,
            String relativeFolderPath,
            ItemStack icon,
            double panX,
            double panY,
            double zoom,
            FlowGraph graph
    ) {}

    public FolderBlueprintPackage(String rootFolderName, String description, String author, long createdAt) {
        this.rootFolderName = rootFolderName != null ? rootFolderName : "Folder";
        this.description = description != null ? description : "";
        this.author = author != null ? author : "";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public String getRootFolderName() {
        return rootFolderName;
    }

    public void setRootFolderName(String rootFolderName) {
        this.rootFolderName = rootFolderName != null ? rootFolderName : "Folder";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author != null ? author : "";
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<FolderPageEntry> getPages() {
        return pages;
    }

    public List<String> getSubFolders() {
        return subFolders;
    }

    public void addPage(FolderPageEntry entry) {
        if (entry != null) {
            this.pages.add(entry);
        }
    }

    public void addSubFolder(String relPath) {
        if (relPath != null && !relPath.trim().isEmpty() && !subFolders.contains(relPath.trim())) {
            this.subFolders.add(relPath.trim());
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("format", FORMAT);
        tag.putInt("version", CURRENT_VERSION);
        tag.putString("rootFolder", rootFolderName);
        tag.putString("desc", description);
        tag.putString("author", author);
        tag.putLong("created", createdAt);

        ListTag pagesList = new ListTag();
        for (FolderPageEntry page : pages) {
            CompoundTag pTag = new CompoundTag();
            pTag.putString("name", page.name());
            pTag.putString("relPath", page.relativeFolderPath() != null ? page.relativeFolderPath() : "");
            if (page.icon() != null && !page.icon().isEmpty()) {
                pTag.put("icon", page.icon().save(new CompoundTag()));
            }
            pTag.putDouble("panX", page.panX());
            pTag.putDouble("panY", page.panY());
            pTag.putDouble("zoom", page.zoom());
            if (page.graph() != null) {
                pTag.put("graph", page.graph().serializeNBT(page.panX(), page.panY(), page.zoom()));
            }
            pagesList.add(pTag);
        }
        tag.put("pages", pagesList);

        ListTag subsList = new ListTag();
        for (String sub : subFolders) {
            CompoundTag sTag = new CompoundTag();
            sTag.putString("path", sub);
            subsList.add(sTag);
        }
        tag.put("subFolders", subsList);

        return tag;
    }

    public static FolderBlueprintPackage deserializeNBT(CompoundTag tag) {
        if (tag == null) return null;
        if (!FORMAT.equals(tag.getString("format"))) return null;

        String rootFolder = tag.getString("rootFolder");
        String desc = tag.getString("desc");
        String author = tag.getString("author");
        long created = tag.getLong("created");

        FolderBlueprintPackage pkg = new FolderBlueprintPackage(rootFolder, desc, author, created);

        if (tag.contains("pages", Tag.TAG_LIST)) {
            ListTag pagesList = tag.getList("pages", Tag.TAG_COMPOUND);
            for (int i = 0; i < pagesList.size(); i++) {
                CompoundTag pTag = pagesList.getCompound(i);
                String name = pTag.getString("name");
                String relPath = pTag.getString("relPath");
                ItemStack icon = pTag.contains("icon") ? ItemStack.of(pTag.getCompound("icon")) : ItemStack.EMPTY;
                double panX = pTag.getDouble("panX");
                double panY = pTag.getDouble("panY");
                double zoom = pTag.contains("zoom") ? pTag.getDouble("zoom") : 1.0;
                FlowGraph graph = pTag.contains("graph") ? FlowGraph.deserializeNBT(pTag.getCompound("graph")) : new FlowGraph();

                pkg.addPage(new FolderPageEntry(name, relPath, icon, panX, panY, zoom, graph));
            }
        }

        if (tag.contains("subFolders", Tag.TAG_LIST)) {
            ListTag subsList = tag.getList("subFolders", Tag.TAG_COMPOUND);
            for (int i = 0; i < subsList.size(); i++) {
                CompoundTag sTag = subsList.getCompound(i);
                pkg.addSubFolder(sTag.getString("path"));
            }
        }

        return pkg;
    }
}
