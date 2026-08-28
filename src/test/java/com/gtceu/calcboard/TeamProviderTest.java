package com.gtceu.calcboard;

import com.gtceu.calcboard.server.team.FTBTeamsProvider;
import com.gtceu.calcboard.server.team.PhoenixGuildsProvider;
import com.gtceu.calcboard.server.team.StandaloneFallbackProvider;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import com.gtceu.calcboard.server.team.VanillaScoreboardProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TeamProviderTest {

    @Test
    public void testStandaloneFallbackProvider() {
        StandaloneFallbackProvider provider = new StandaloneFallbackProvider();
        assertTrue(provider.isAvailable());
        assertEquals("standalone", provider.getProviderId());

        UUID dummy = UUID.randomUUID();
        String name = provider.getTeamDisplayName(dummy);
        assertTrue(name.startsWith("Personal"));
        assertEquals("Personal Workspace", provider.getTeamDisplayName(null));
    }

    @Test
    public void testVanillaScoreboardProviderAvailability() {
        VanillaScoreboardProvider provider = new VanillaScoreboardProvider();
        assertTrue(provider.isAvailable());
        assertEquals("vanilla_scoreboard", provider.getProviderId());
    }

    @Test
    public void testFTBTeamsProviderSafeDegradation() {
        FTBTeamsProvider provider = new FTBTeamsProvider();
        // In unit test environment without Minecraft Forge runtime, FTB Teams should report not available safely without throwing exceptions
        assertFalse(provider.isAvailable());
        assertEquals("ftbteams", provider.getProviderId());
    }

    @Test
    public void testPhoenixGuildsProviderSafeDegradation() {
        PhoenixGuildsProvider provider = new PhoenixGuildsProvider();
        // In unit test environment without Minecraft Forge runtime, Phoenix Guilds should report not available safely without throwing exceptions
        assertFalse(provider.isAvailable());
        assertEquals("phoenix_guilds", provider.getProviderId());
    }

    @Test
    public void testTeamProviderRegistryFallbackHierarchy() {
        TeamProviderRegistry registry = TeamProviderRegistry.getInstance();
        assertNotNull(registry.getActiveProvider());
        assertTrue(registry.getActiveProvider().isAvailable());
    }
}
