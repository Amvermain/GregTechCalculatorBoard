package com.gtceu.calcboard.integration.ae2;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.compat.ae2.ClientAe2CraftConfirmHook;
import com.gtceu.calcboard.client.gui.compat.ae2.ClientAe2PatternTermHook;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class ClientAe2HookReflectionTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().resetToDefault();
        PatternGraphRegistry.getInstance().clear();
    }

    @Test
    public void testCraftConfirmHookReflectionResolution() throws Exception {
        Class<?> hookClass = ClientAe2CraftConfirmHook.class;

        Field screenField = hookClass.getDeclaredField("craftConfirmScreenClass");
        screenField.setAccessible(true);
        Class<?> screenClass = (Class<?>) screenField.get(null);
        Assertions.assertNotNull(screenClass);
        Assertions.assertEquals("appeng.client.gui.me.crafting.CraftConfirmScreen", screenClass.getName());

        Field planField = hookClass.getDeclaredField("getPlanMethod");
        planField.setAccessible(true);
        Method planMethod = (Method) planField.get(null);
        Assertions.assertNotNull(planMethod);
        Assertions.assertEquals("getPlan", planMethod.getName());

        Field entriesField = hookClass.getDeclaredField("getEntriesMethod");
        entriesField.setAccessible(true);
        Method entriesMethod = (Method) entriesField.get(null);
        Assertions.assertNotNull(entriesMethod);
        Assertions.assertEquals("getEntries", entriesMethod.getName());

        Field craftAmountField = hookClass.getDeclaredField("getCraftAmountMethod");
        craftAmountField.setAccessible(true);
        Method craftAmountMethod = (Method) craftAmountField.get(null);
        Assertions.assertNotNull(craftAmountMethod);
        Assertions.assertEquals("getCraftAmount", craftAmountMethod.getName());

        Field whatField = hookClass.getDeclaredField("getWhatMethod");
        whatField.setAccessible(true);
        Method whatMethod = (Method) whatField.get(null);
        Assertions.assertNotNull(whatMethod);
        Assertions.assertEquals("getWhat", whatMethod.getName());

        Field isEncodedField = hookClass.getDeclaredField("isEncodedPatternMethod");
        isEncodedField.setAccessible(true);
        Method isEncodedMethod = (Method) isEncodedField.get(null);
        Assertions.assertNotNull(isEncodedMethod);
        Assertions.assertEquals("isEncodedPattern", isEncodedMethod.getName());
    }

    @Test
    public void testPatternTermHookReflectionResolution() throws Exception {
        Class<?> hookClass = ClientAe2PatternTermHook.class;

        Field screenField = hookClass.getDeclaredField("patternTermScreenClass");
        screenField.setAccessible(true);
        Class<?> screenClass = (Class<?>) screenField.get(null);
        Assertions.assertNotNull(screenClass);
        Assertions.assertEquals("appeng.client.gui.me.items.PatternEncodingTermScreen", screenClass.getName());
    }

    @Test
    public void testDirectBoundPatternTooltipRouting() {
        PatternGraphRegistry registry = PatternGraphRegistry.getInstance();
        BoardPage page = boardManager().addPage("[AE2] Test Rod", "ae2");
        PatternId patternId = PatternId.ofKey("test:pattern_key", "Test Rod", ItemStack.EMPTY);

        registry.bind(patternId, page);

        Optional<BoardPage> directOpt = registry.getDirectBoundPage(patternId);
        Assertions.assertTrue(directOpt.isPresent());
        Assertions.assertEquals(page.getId(), directOpt.get().getId());

        PatternId unboundPattern = PatternId.ofKey("test:unbound_key", "Unbound Rod", ItemStack.EMPTY);
        Optional<BoardPage> unboundDirectOpt = registry.getDirectBoundPage(unboundPattern);
        Assertions.assertFalse(unboundDirectOpt.isPresent());
    }

    private BoardManager boardManager() {
        return BoardManager.getInstance();
    }
}
