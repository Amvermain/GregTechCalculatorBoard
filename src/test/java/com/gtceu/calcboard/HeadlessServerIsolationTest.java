package com.gtceu.calcboard;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.compat.GenericModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry;
import com.gtceu.calcboard.client.gui.compat.create.CreateModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.createnewage.CreateNewAgeModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.gtceu.GTCEuModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.systeams.SysteamsModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.thermal.ThermalModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.vanilla.VanillaModGuiHandler;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.systeams.SysteamsModAdapter;
import com.gtceu.calcboard.compat.thermal.ThermalModAdapter;
import com.gtceu.calcboard.compat.vanilla.VanillaModAdapter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Verifies RFC-002: Headless Server SPI isolation and dedicated server compatibility.
 * Ensures IModAdapter and its domain implementations contain NO references to net.minecraft.client classes.
 */
public class HeadlessServerIsolationTest {

    private static final List<Class<?>> ADAPTER_CLASSES = List.of(
            IModAdapter.class,
            GTCEuModAdapter.class,
            CreateModAdapter.class,
            CreateNewAgeModAdapter.class,
            SysteamsModAdapter.class,
            ThermalModAdapter.class,
            VanillaModAdapter.class
    );

    @Test
    @DisplayName("RFC-002: Verify IModAdapter classes contain NO net.minecraft.client references in signatures")
    void testModAdaptersHaveNoClientDependencies() {
        for (Class<?> clazz : ADAPTER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                // Check return type
                String returnTypeName = method.getReturnType().getName();
                Assertions.assertFalse(returnTypeName.startsWith("net.minecraft.client."),
                        String.format("Method %s#%s has client return type: %s", clazz.getSimpleName(), method.getName(), returnTypeName));

                // Check parameter types
                for (Class<?> paramType : method.getParameterTypes()) {
                    String paramTypeName = paramType.getName();
                    Assertions.assertFalse(paramTypeName.startsWith("net.minecraft.client."),
                            String.format("Method %s#%s has client parameter type: %s", clazz.getSimpleName(), method.getName(), paramTypeName));
                }
            }

            for (Field field : clazz.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();
                Assertions.assertFalse(fieldTypeName.startsWith("net.minecraft.client."),
                        String.format("Field %s#%s has client type: %s", clazz.getSimpleName(), field.getName(), fieldTypeName));
            }
        }
    }

    @Test
    @DisplayName("RFC-002: Verify ModGuiHandlerRegistry lookups and fallbacks")
    void testModGuiHandlerRegistryFallbackAndLookup() {
        // Known handlers
        IModGuiHandler gtceuHandler = ModGuiHandlerRegistry.getHandler("gtceu");
        Assertions.assertInstanceOf(GTCEuModGuiHandler.class, gtceuHandler);

        IModGuiHandler createHandler = ModGuiHandlerRegistry.getHandler("create");
        Assertions.assertInstanceOf(CreateModGuiHandler.class, createHandler);

        IModGuiHandler newAgeHandler = ModGuiHandlerRegistry.getHandler("create_new_age");
        Assertions.assertInstanceOf(CreateNewAgeModGuiHandler.class, newAgeHandler);

        IModGuiHandler systeamsHandler = ModGuiHandlerRegistry.getHandler("systeams");
        Assertions.assertInstanceOf(SysteamsModGuiHandler.class, systeamsHandler);

        IModGuiHandler thermalHandler = ModGuiHandlerRegistry.getHandler("thermal");
        Assertions.assertInstanceOf(ThermalModGuiHandler.class, thermalHandler);

        IModGuiHandler vanillaHandler = ModGuiHandlerRegistry.getHandler("minecraft");
        Assertions.assertInstanceOf(VanillaModGuiHandler.class, vanillaHandler);

        // Unknown / Fallback handlers
        IModGuiHandler fallbackHandler = ModGuiHandlerRegistry.getHandler("unknown_mod_123");
        Assertions.assertInstanceOf(GenericModGuiHandler.class, fallbackHandler);

        IModGuiHandler nullModHandler = ModGuiHandlerRegistry.getHandler(null);
        Assertions.assertInstanceOf(GenericModGuiHandler.class, nullModHandler);

        // Lookup by Node
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:electric_blast_furnace"), "EBF", 100, 120, null);
        IModGuiHandler nodeHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
        Assertions.assertInstanceOf(GTCEuModGuiHandler.class, nodeHandler);

        IModGuiHandler nullNodeHandler = ModGuiHandlerRegistry.getHandlerForNode(null);
        Assertions.assertInstanceOf(GenericModGuiHandler.class, nullNodeHandler);
    }
}
