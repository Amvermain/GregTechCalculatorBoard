package com.gtceu.calcboard.server.team;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Soft-dependency team provider for Phoenix Guilds (Teams).
 * Interacts with Phoenix Guilds API via safe reflection to prevent NoClassDefFoundError when Phoenix Guilds is absent.
 */
public class PhoenixGuildsProvider implements ITeamProvider {

    private static final String PHOENIX_GUILDS_MOD_ID = "phoenix_guilds";
    private boolean isInitialized = false;
    private boolean isPhoenixGuildsPresent = false;

    private Method getGuildIdMethod = null;
    private Method getGuildNameByIdMethod = null;
    private Method getGuildMembersByIdMethod = null;
    private Method isOfficerOrAboveMethod = null;
    private Method isOwnerMethod = null;

    public PhoenixGuildsProvider() {
        initReflection();
    }

    private synchronized void initReflection() {
        if (isInitialized) return;
        isInitialized = true;

        try {
            if (ModList.get() != null && ModList.get().isLoaded(PHOENIX_GUILDS_MOD_ID)) {
                Class<?> apiClass = Class.forName("net.phoenixvine.guilds.GuildAPI");

                for (Method m : apiClass.getMethods()) {
                    if (m.getName().equals("getGuildId") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                        this.getGuildIdMethod = m;
                    } else if (m.getName().equals("getGuildNameById") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                        this.getGuildNameByIdMethod = m;
                    } else if (m.getName().equals("getGuildMembersById") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                        this.getGuildMembersByIdMethod = m;
                    } else if (m.getName().equals("isOfficerOrAbove") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                        this.isOfficerOrAboveMethod = m;
                    } else if (m.getName().equals("isOwner") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                        this.isOwnerMethod = m;
                    }
                }

                if (this.getGuildIdMethod != null) {
                    this.isPhoenixGuildsPresent = true;
                    GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Successfully hooked into Phoenix Guilds API for team workspace isolation.");
                }
            }
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Could not initialize Phoenix Guilds API reflection: {}", t.getMessage());
            this.isPhoenixGuildsPresent = false;
        }
    }

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        if (!isAvailable() || player == null) {
            return null;
        }

        try {
            if (getGuildIdMethod != null) {
                Object res = getGuildIdMethod.invoke(null, player.getUUID());
                if (res instanceof Optional<?> opt && opt.isPresent()) {
                    Object val = opt.get();
                    if (val instanceof UUID guildId) {
                        return guildId;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        if (!isAvailable() || teamId == null) {
            return null;
        }

        try {
            if (getGuildNameByIdMethod != null) {
                Object res = getGuildNameByIdMethod.invoke(null, teamId);
                if (res instanceof Optional<?> opt && opt.isPresent()) {
                    Object val = opt.get();
                    if (val instanceof String name && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    @Override
    public Set<UUID> getTeamMembers(UUID teamId) {
        if (!isAvailable() || teamId == null) {
            return Collections.emptySet();
        }

        try {
            if (getGuildMembersByIdMethod != null) {
                Object res = getGuildMembersByIdMethod.invoke(null, teamId);
                if (res instanceof Set<?> set) {
                    Set<UUID> memberUuids = new HashSet<>();
                    for (Object item : set) {
                        if (item instanceof UUID u) {
                            memberUuids.add(u);
                        }
                    }
                    if (!memberUuids.isEmpty()) {
                        return memberUuids;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return Collections.emptySet();
    }

    @Override
    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
        UUID playerGuild = getPlayerTeamId(player);
        return teamId.equals(playerGuild);
    }

    @Override
    public boolean canPlayerAdministerTeam(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;

        if (player.server != null && player.server.isSingleplayer()) {
            if (player.server.isSingleplayerOwner(player.getGameProfile())) {
                return true;
            }
        } else if (player.hasPermissions(2)) {
            return true;
        }

        if (!isAvailable()) {
            return player.getUUID().equals(teamId);
        }

        UUID playerGuild = getPlayerTeamId(player);
        if (!teamId.equals(playerGuild)) {
            return false;
        }

        try {
            if (isOfficerOrAboveMethod != null) {
                Object res = isOfficerOrAboveMethod.invoke(null, player.getUUID());
                if (Boolean.TRUE.equals(res)) {
                    return true;
                }
            } else if (isOwnerMethod != null) {
                Object res = isOwnerMethod.invoke(null, player.getUUID());
                if (Boolean.TRUE.equals(res)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    @Override
    public String getProviderId() {
        return "phoenix_guilds";
    }

    @Override
    public boolean isAvailable() {
        if (!isInitialized) initReflection();
        return isPhoenixGuildsPresent;
    }
}
