package com.gtceu.calcboard.client.search;

import com.gtceu.calcboard.api.model.RecipeFingerprint;
import com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeSearchCacheManagerTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        RecipeSearchCacheManager.clearGlobalCache();
    }

    @Test
    public void testCacheManagerFingerprintRetention() {
        RecipeFingerprint fp = new RecipeFingerprint("emi", 42, 9999L);
        RecipeSearchCacheManager.setGlobalRecipesForTesting(Collections.emptyList());
        RecipeSearchCacheManager.setCachedFingerprintForTesting(fp);

        assertTrue(RecipeSearchCacheManager.isGlobalCached());
        assertEquals(fp, RecipeSearchCacheManager.getCachedFingerprint());
        assertTrue(RecipeSearchCacheManager.getCachedFingerprint().matches(new RecipeFingerprint("emi", 42, 9999L)));

        RecipeSearchCacheManager.clearGlobalCache();
        assertFalse(RecipeSearchCacheManager.isGlobalCached());
        assertTrue(RecipeSearchCacheManager.getCachedFingerprint().isEmpty());
    }
}
