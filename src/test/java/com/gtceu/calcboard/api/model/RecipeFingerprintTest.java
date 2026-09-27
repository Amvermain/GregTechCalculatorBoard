package com.gtceu.calcboard.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeFingerprintTest {

    @Test
    public void testFingerprintEqualityAndMatching() {
        RecipeFingerprint fp1 = new RecipeFingerprint("emi", 5000, 123456789L);
        RecipeFingerprint fp2 = new RecipeFingerprint("emi", 5000, 123456789L);
        RecipeFingerprint fpDiffCount = new RecipeFingerprint("emi", 5001, 123456789L);
        RecipeFingerprint fpDiffHash = new RecipeFingerprint("emi", 5000, 987654321L);
        RecipeFingerprint fpDiffViewer = new RecipeFingerprint("jei", 5000, 123456789L);

        assertTrue(fp1.matches(fp2));
        assertTrue(fp2.matches(fp1));
        assertFalse(fp1.matches(fpDiffCount));
        assertFalse(fp1.matches(fpDiffHash));
        assertFalse(fp1.matches(fpDiffViewer));
        assertFalse(fp1.matches(null));
        assertFalse(fp1.matches(RecipeFingerprint.EMPTY));
    }

    @Test
    public void testEmptyFingerprint() {
        RecipeFingerprint empty = RecipeFingerprint.EMPTY;
        assertTrue(empty.isEmpty());
        assertFalse(empty.matches(empty));

        RecipeFingerprint zeroCount = new RecipeFingerprint("emi", 0, 100L);
        assertTrue(zeroCount.isEmpty());
        assertFalse(zeroCount.matches(zeroCount));

        RecipeFingerprint nullViewer = new RecipeFingerprint(null, 10, 100L);
        assertTrue(nullViewer.isEmpty());
        assertFalse(nullViewer.matches(nullViewer));
    }
}
