package com.gtceu.calcboard.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.FolderBlueprintCodec;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FolderBlueprintTest {

    @BeforeEach
    public void setUp() {
        BoardManager.getInstance().getPages().clear();
    }

    @Test
    public void testFolderBlueprintPackageSerialization() {
        FolderBlueprintPackage pkg = new FolderBlueprintPackage("Factory", "Automated power line", "Skkub", 1000L);

        FlowGraph graph1 = new FlowGraph();
        RecipeNode node1 = new RecipeNode("r1", "Smelting", 10.0, 10.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        graph1.addNode(node1);

        pkg.addPage(new FolderBlueprintPackage.FolderPageEntry(
                "Page 1",
                "Sub1",
                ItemStack.EMPTY,
                100.0,
                200.0,
                1.5,
                graph1
        ));

        pkg.addPage(new FolderBlueprintPackage.FolderPageEntry(
                "Page 2",
                "Sub1/Deep",
                ItemStack.EMPTY,
                40.0,
                40.0,
                1.0,
                new FlowGraph()
        ));

        pkg.addSubFolder("Sub1/EmptySub");

        CompoundTag tag = pkg.serializeNBT();
        assertNotNull(tag);
        assertEquals(FolderBlueprintPackage.FORMAT, tag.getString("format"));

        FolderBlueprintPackage deserialized = FolderBlueprintPackage.deserializeNBT(tag);
        assertNotNull(deserialized);
        assertEquals("Factory", deserialized.getRootFolderName());
        assertEquals("Automated power line", deserialized.getDescription());
        assertEquals("Skkub", deserialized.getAuthor());
        assertEquals(2, deserialized.getPages().size());
        assertTrue(deserialized.getSubFolders().contains("Sub1/EmptySub"));

        FolderBlueprintPackage.FolderPageEntry p1 = deserialized.getPages().get(0);
        assertEquals("Page 1", p1.name());
        assertEquals("Sub1", p1.relativeFolderPath());
        assertEquals(100.0, p1.panX(), 0.001);
        assertEquals(1.5, p1.zoom(), 0.001);
        assertNotNull(p1.graph());
        assertEquals(1, p1.graph().getNodes().size());

        FolderBlueprintPackage.FolderPageEntry p2 = deserialized.getPages().get(1);
        assertEquals("Page 2", p2.name());
        assertEquals("Sub1/Deep", p2.relativeFolderPath());
    }

    @Test
    public void testFolderCodecRoundTripAndImport() {
        BoardManager bm = BoardManager.getInstance();

        BoardPage rootPage = bm.addPage("Root Page", "Metals");
        BoardPage subPage = bm.addPage("Sub Page", "Metals/Refining");
        BoardPage otherPage = bm.addPage("Other Page", "Unrelated");

        FlowGraph g1 = rootPage.getGraph();
        RecipeNode n1 = new RecipeNode("m1", "Macerating", 20.0, 20.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        g1.addNode(n1);

        FolderBlueprintPackage pkg = FolderBlueprintCodec.createFolderPackage("Metals", "Metalworks", "Comprehensive metal refining", "Engineer");
        assertEquals("Metalworks", pkg.getRootFolderName());
        assertEquals(2, pkg.getPages().size());

        String encoded = FolderBlueprintCodec.exportToString(pkg);
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("GTFOLDER:Metalworks:"));

        FolderBlueprintPackage importedPkg = FolderBlueprintCodec.importPackageFromString(encoded);
        assertNotNull(importedPkg);
        assertEquals("Metalworks", importedPkg.getRootFolderName());
        assertEquals(2, importedPkg.getPages().size());

        int targetIdx = FolderBlueprintCodec.importFolderToBoardManager(importedPkg, "Imported");
        assertTrue(targetIdx >= 0);

        List<BoardPage> pages = bm.getPages();
        assertTrue(pages.stream().anyMatch(p -> "Imported/Metalworks".equals(p.getFolderPath()) && "Root Page".equals(p.getName())));
        assertTrue(pages.stream().anyMatch(p -> "Imported/Metalworks/Refining".equals(p.getFolderPath()) && "Sub Page".equals(p.getName())));

        BoardPage importedRoot = pages.stream().filter(p -> "Imported/Metalworks".equals(p.getFolderPath())).findFirst().orElse(null);
        assertNotNull(importedRoot);
        assertEquals(1, importedRoot.getGraph().getNodes().size());
    }
}
