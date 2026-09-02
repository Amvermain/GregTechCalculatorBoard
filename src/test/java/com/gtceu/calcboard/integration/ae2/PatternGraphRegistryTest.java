package com.gtceu.calcboard.integration.ae2;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.integration.ae2.model.PatternBindingEntry;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class PatternGraphRegistryTest {

    private PatternGraphRegistry registry;
    private BoardManager boardManager;

    @BeforeEach
    public void setup() {
        boardManager = BoardManager.getInstance();
        boardManager.resetToDefault();
        registry = PatternGraphRegistry.getInstance();
        registry.clear();
    }

    @Test
    public void testPatternBindingAndQuery() {
        BoardPage page = boardManager.addPage("Titanium Page", "ae2");
        PatternId patternId = PatternId.ofKey("gtceu:titanium_ingot", "Titanium Ingot", ItemStack.EMPTY);

        Assertions.assertFalse(registry.isPageBound(page.getId()));
        Assertions.assertFalse(registry.isPatternBound(patternId));

        registry.bind(patternId, page);

        Assertions.assertTrue(registry.isPageBound(page.getId()));
        Assertions.assertTrue(registry.isPatternBound(patternId));

        Optional<BoardPage> boundOpt = registry.getBoundPage(patternId);
        Assertions.assertTrue(boundOpt.isPresent());
        Assertions.assertEquals(page.getId(), boundOpt.get().getId());

        Optional<PatternBindingEntry> entryOpt = registry.getBindingForPage(page.getId());
        Assertions.assertTrue(entryOpt.isPresent());
        Assertions.assertEquals(patternId.getKey(), entryOpt.get().patternId().getKey());
    }

    @Test
    public void testPatternUnbinding() {
        BoardPage page = boardManager.addPage("EBF Page", "ae2");
        PatternId patternId = PatternId.ofKey("gtceu:ebf_recipe", "EBF Recipe", ItemStack.EMPTY);

        registry.bind(patternId, page);
        Assertions.assertTrue(registry.isPageBound(page.getId()));

        registry.unbind(patternId);
        Assertions.assertFalse(registry.isPageBound(page.getId()));
        Assertions.assertFalse(registry.isPatternBound(patternId));

        registry.bind(patternId, page);
        Assertions.assertTrue(registry.isPageBound(page.getId()));

        registry.unbindPage(page.getId());
        Assertions.assertFalse(registry.isPageBound(page.getId()));
        Assertions.assertFalse(registry.isPatternBound(patternId));
    }

    @Test
    public void testAutomaticUnbindOnPageRemoval() {
        BoardPage page1 = boardManager.addPage("Auto Page", "ae2");
        BoardPage page2 = boardManager.addPage("Spare Page", "ae2");
        PatternId patternId = PatternId.ofKey("test:auto_pattern", "Auto Pattern", ItemStack.EMPTY);

        registry.bind(patternId, page1);
        Assertions.assertTrue(registry.isPageBound(page1.getId()));

        int pageIndex = boardManager.getPages().indexOf(page1);
        boardManager.removePage(pageIndex);

        Assertions.assertFalse(registry.isPageBound(page1.getId()));
        Assertions.assertFalse(registry.isPatternBound(patternId));
    }

    @Test
    public void testSerializationRoundtrip() {
        BoardPage page = boardManager.addPage("Serialized Page", "ae2");
        PatternId patternId = PatternId.ofKey("gtceu:superconductor", "Superconductor", ItemStack.EMPTY);

        registry.bind(patternId, page);

        CompoundTag tag = registry.serializeNBT();
        Assertions.assertNotNull(tag);

        registry.clear();
        Assertions.assertFalse(registry.isPageBound(page.getId()));

        registry.deserializeNBT(tag);
        Assertions.assertTrue(registry.isPageBound(page.getId()));
        Assertions.assertTrue(registry.isPatternBound(patternId));

        Optional<BoardPage> boundOpt = registry.getBoundPage(patternId);
        Assertions.assertTrue(boundOpt.isPresent());
        Assertions.assertEquals(page.getId(), boundOpt.get().getId());
    }
}
