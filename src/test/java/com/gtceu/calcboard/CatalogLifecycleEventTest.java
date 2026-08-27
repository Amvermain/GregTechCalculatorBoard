package com.gtceu.calcboard;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.event.CatalogLifecycleEvent;
import com.gtceu.calcboard.api.event.ModAdapterRegisterEvent;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CatalogLifecycleEventTest {

    @BeforeEach
    public void setUp() {
        MinecraftForge.EVENT_BUS.start();
        cleanupListeners();
        ModAdapterRegistry.reset();
    }

    @AfterEach
    public void tearDown() {
        cleanupListeners();
        ModAdapterRegistry.reset();
    }

    private void cleanupListeners() {
        CatalogLifecycleEvent.clearListeners();
        ModAdapterRegisterEvent.clearListeners();
    }

    @Test
    public void testRecipesReadyEventDispatch() {
        AtomicBoolean fired = new AtomicBoolean(false);
        AtomicInteger count = new AtomicInteger(0);

        MinecraftForge.EVENT_BUS.addListener((CatalogLifecycleEvent.RecipesReady event) -> {
            fired.set(true);
            count.set(event.getRecipeCount());
        });

        MinecraftForge.EVENT_BUS.post(new CatalogLifecycleEvent.RecipesReady(420, 150L));

        Assertions.assertTrue(fired.get(), "RecipesReady event should fire and be caught");
        Assertions.assertEquals(420, count.get(), "Recipe count should match");
    }

    @Test
    public void testAddonsAndMultiblocksReadyEventDispatch() {
        AtomicBoolean addonsFired = new AtomicBoolean(false);
        AtomicBoolean multiblocksFired = new AtomicBoolean(false);

        MinecraftForge.EVENT_BUS.addListener((CatalogLifecycleEvent.AddonsReady event) -> {
            addonsFired.set(true);
        });

        MinecraftForge.EVENT_BUS.addListener((CatalogLifecycleEvent.MultiblocksReady event) -> {
            multiblocksFired.set(true);
        });

        MinecraftForge.EVENT_BUS.post(new CatalogLifecycleEvent.AddonsReady(50));
        MinecraftForge.EVENT_BUS.post(new CatalogLifecycleEvent.MultiblocksReady(25));

        Assertions.assertTrue(addonsFired.get(), "AddonsReady event should fire");
        Assertions.assertTrue(multiblocksFired.get(), "MultiblocksReady event should fire");
    }

    private static class CustomTestAdapter implements IModAdapter {
        @Override
        public String getModId() {
            return "custom_mod";
        }

        @Override
        public int getPriority() {
            return 200; // higher than default GTCEu
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public boolean handlesCategory(ResourceLocation categoryId) {
            return categoryId != null && categoryId.getNamespace().equals("custom_mod");
        }

        @Override
        public boolean handlesNode(RecipeNode node) {
            return false;
        }

        @Override
        public void discoverAddons(java.util.List<com.gtceu.calcboard.api.catalog.MachineAddon> collector, java.util.List<net.minecraft.world.item.ItemStack> recipeOutputStacks) {
        }

        @Override
        public void enrichCapabilities(com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        }
    }

    @Test
    public void testModAdapterRegisterEventInjection() {
        CustomTestAdapter customAdapter = new CustomTestAdapter();

        MinecraftForge.EVENT_BUS.addListener((ModAdapterRegisterEvent event) -> {
            event.register(customAdapter);
        });

        ModAdapterRegistry.init();

        IModAdapter found = ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("custom_mod:crusher"));
        Assertions.assertEquals(customAdapter, found, "Custom adapter registered via ModAdapterRegisterEvent must handle its category");
        Assertions.assertEquals("custom_mod", found.getModId());
    }
}



