package com.gtceu.calcboard.client.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IconDiskCacheTest {

    private IconDiskCache cache;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        cache = IconDiskCache.getInstance();
        tempDir = Files.createTempDirectory("calcboard_test_icons");
        cache.setCustomCacheDir(tempDir);
        cache.clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        cache.clear();
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.list(tempDir)) {
                for (Path p : stream.toList()) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testPlaceholderPngGeneration() {
        byte[] png = cache.generatePlaceholderIcon("FE", 0xFF1E293B, 0xFF38BDF8);
        assertNotNull(png);
        assertTrue(png.length > 8);

        // Verify PNG magic header bytes (0x89, 0x50, 0x4E, 0x47)
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }

    @Test
    void testPlaceholderNotPersistedToDiskToAvoidPoisoning() {
        IconDiskCache.IconResult first = cache.getOrRenderItemIconResult("gtceu:electric_blast_furnace", null);
        assertNotNull(first);
        assertTrue(first.isPlaceholder());
        assertTrue(first.data().length > 0);

        Path expectedDiskFile = tempDir.resolve("item_gtceu_electric_blast_furnace.png");
        assertFalse(Files.exists(expectedDiskFile), "Placeholders should never be persisted to disk to avoid poisoning cache");
        assertFalse(cache.isItemCached("gtceu:electric_blast_furnace", null), "Placeholders should not be marked as cached");

        IconDiskCache.IconResult second = cache.getOrRenderItemIconResult("gtceu:electric_blast_furnace", null);
        assertTrue(second.isPlaceholder());

        byte[] fakeRendered = new byte[100];
        fakeRendered[0] = (byte) 0x89;
        fakeRendered[1] = (byte) 'P';
        fakeRendered[2] = (byte) 'N';
        fakeRendered[3] = (byte) 'G';
        cache.saveRenderedItemIcon("gtceu:electric_blast_furnace", null, fakeRendered);

        IconDiskCache.IconResult afterRender = cache.getOrRenderItemIconResult("gtceu:electric_blast_furnace", null);
        assertFalse(afterRender.isPlaceholder());
        assertSame(fakeRendered, afterRender.data());
    }

    @Test
    void testRenderedIconDiskPersistence() {
        byte[] fakeRendered = new byte[1024];
        fakeRendered[0] = (byte) 0x89;
        fakeRendered[1] = (byte) 'P';
        fakeRendered[2] = (byte) 'N';
        fakeRendered[3] = (byte) 'G';

        cache.saveRenderedIcon("item", "gtceu:electric_blast_furnace", null, fakeRendered);
        Path expectedDiskFile = tempDir.resolve("item_gtceu_electric_blast_furnace.png");
        assertTrue(Files.exists(expectedDiskFile), "Rendered icon should be persisted to disk");

        cache.clear();
        // File should be deleted on clear
        assertFalse(Files.exists(expectedDiskFile));

        cache.saveRenderedIcon("item", "gtceu:electric_blast_furnace", null, fakeRendered);
        assertTrue(Files.exists(expectedDiskFile));

        // Read through getOrRenderItemIcon
        byte[] fromDisk = cache.getOrRenderItemIcon("gtceu:electric_blast_furnace", null);
        assertNotNull(fromDisk);
        assertEquals(fakeRendered.length, fromDisk.length);
    }

    @Test
    void testRejectsCorruptedOrSmallPlaceholderOnDisk() throws IOException {
        Path corruptFile = tempDir.resolve("item_minecraft_iron_ingot.png");
        Files.write(corruptFile, new byte[450]);
        assertTrue(Files.exists(corruptFile));

        byte[] icon = cache.getOrRenderItemIcon("minecraft:iron_ingot", null);
        assertNotNull(icon);
        assertFalse(Files.exists(corruptFile), "Non-PNG corrupted files should be deleted from disk");

        byte[] validSmallPng = cache.generatePlaceholderIcon("FE", 0xFF1E293B, 0xFF38BDF8);
        assertTrue(validSmallPng.length < 700, "Generated icon should be under 700 bytes");
        assertTrue(IconDiskCache.isValidPng(validSmallPng));

        Path validSmallFile = tempDir.resolve("item_minecraft_gold_ingot.png");
        Files.write(validSmallFile, validSmallPng);
        assertTrue(Files.exists(validSmallFile));

        IconDiskCache.IconResult smallResult = cache.getOrRenderItemIconResult("minecraft:gold_ingot", null);
        assertNotNull(smallResult);
        assertFalse(smallResult.isPlaceholder(), "Valid small PNG on disk should be recognized as rendered icon");
        assertTrue(Files.exists(validSmallFile), "Valid small PNG on disk must not be deleted");
    }

    @Test
    void testValidPngValidation() {
        assertFalse(IconDiskCache.isValidPng(null));
        assertFalse(IconDiskCache.isValidPng(new byte[0]));
        assertFalse(IconDiskCache.isValidPng(new byte[7]));
        assertFalse(IconDiskCache.isValidPng(new byte[]{0, 0, 0, 0, 0, 0, 0, 0}));

        byte[] valid = new byte[]{(byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertTrue(IconDiskCache.isValidPng(valid));
    }

    @Test
    void testClearCacheDeletesDiskFiles() {
        byte[] fakeRendered = new byte[800];
        cache.saveRenderedIcon("item", "minecraft:iron_ingot", null, fakeRendered);

        Path expectedDiskFile = tempDir.resolve("item_minecraft_iron_ingot.png");
        assertTrue(Files.exists(expectedDiskFile));

        cache.clear();
        assertFalse(Files.exists(expectedDiskFile), "Clear cache should delete persisted disk files");
    }

    @Test
    void testHasDiskFileRejectsCorruptOrZeroByteFiles() throws IOException {
        Path zeroByteFile = tempDir.resolve("item_minecraft_copper_ingot.png");
        Files.write(zeroByteFile, new byte[0]);
        assertFalse(cache.hasDiskFile("item_minecraft_copper_ingot.png"));
        assertFalse(cache.isItemCached("minecraft:copper_ingot", null));
        assertFalse(Files.exists(zeroByteFile));

        Path corruptFile = tempDir.resolve("item_minecraft_tin_ingot.png");
        Files.write(corruptFile, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertFalse(cache.hasDiskFile("item_minecraft_tin_ingot.png"));
        assertFalse(cache.isItemCached("minecraft:tin_ingot", null));
        assertFalse(Files.exists(corruptFile));
    }
}
