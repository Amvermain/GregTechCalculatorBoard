package com.gtceu.calcboard.api.bom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BOMDisplayNameResolverTest {

    @Test
    void testResolveItemStackNull() {
        ItemStack stack = BOMDisplayNameResolver.resolveItemStack(null);
        assertNotNull(stack);
        assertTrue(stack.isEmpty());
    }

    @Test
    void testResolveFallback() {
        String resolved = BOMDisplayNameResolver.resolve(null, "Fallback Machine");
        assertEquals("Fallback Machine", resolved);
    }

    @Test
    void testResolveUnknownWhenNull() {
        String resolved = BOMDisplayNameResolver.resolve(null, null);
        assertEquals("Unknown", resolved);
    }

    @Test
    void testIsValidDisplayName() {
        assertFalse(BOMDisplayNameResolver.isValidDisplayName(null));
        assertFalse(BOMDisplayNameResolver.isValidDisplayName(""));
        assertFalse(BOMDisplayNameResolver.isValidDisplayName("   "));
        assertFalse(BOMDisplayNameResolver.isValidDisplayName("block.minecraft.stone"));
        assertFalse(BOMDisplayNameResolver.isValidDisplayName("item.gtceu.wrench"));
        assertFalse(BOMDisplayNameResolver.isValidDisplayName("tagprefix.frame"));
        assertFalse(BOMDisplayNameResolver.isValidDisplayName("gtceu.machine.steam_miner"));
        assertFalse(BOMDisplayNameResolver.isValidDisplayName("some.gtceu.block"));

        assertTrue(BOMDisplayNameResolver.isValidDisplayName("Bronze Brick Casing"));
        assertTrue(BOMDisplayNameResolver.isValidDisplayName("ULV Machine Hull"));
        assertTrue(BOMDisplayNameResolver.isValidDisplayName("Water"));
    }

    @Test
    void testHeadlessFormattingFallback() {
        ResourceLocation id = ResourceLocation.tryParse("gtceu:solid_machine_casing");
        String resolved = BOMDisplayNameResolver.resolve(id, null);
        assertNotNull(resolved);
        assertFalse(resolved.isBlank());
        assertEquals("Solid Machine Casing", resolved);
    }
}
