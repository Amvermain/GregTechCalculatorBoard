package com.gtceu.calcboard.server.team;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Soft-dependency team provider for FTB Teams.
 * Interacts with FTB Teams API via safe reflection to prevent NoClassDefFoundError when FTB Teams is absent.
 */
public class FTBTeamsProvider implements ITeamProvider {

    private static final String FTB_TEAMS_MOD_ID = "ftbteams";
    private boolean isInitialized = false;
    private boolean isFtbTeamsPresent = false;

    private Object apiInstance = null;
    private Method getManagerMethod = null;

    public FTBTeamsProvider() {
        initReflection();
    }

    private synchronized void initReflection() {
        if (isInitialized) return;
        isInitialized = true;

        try {
            if (ModList.get() != null && ModList.get().isLoaded(FTB_TEAMS_MOD_ID)) {
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                Method apiGetter = null;
                for (Method m : apiClass.getMethods()) {
                    if (m.getParameterCount() == 0 && (m.getName().equals("api") || m.getName().equals("getAPI"))) {
                        apiGetter = m;
                        break;
                    }
                }

                if (apiGetter != null) {
                    this.apiInstance = apiGetter.invoke(null);
                }

                if (this.apiInstance != null) {
                    for (Method m : apiInstance.getClass().getMethods()) {
                        if (m.getParameterCount() == 0 && (m.getName().equals("getManager") || m.getName().equals("getTeamManager"))) {
                            this.getManagerMethod = m;
                            break;
                        }
                    }

                    if (this.getManagerMethod != null) {
                        this.isFtbTeamsPresent = true;
                        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Successfully hooked into FTB Teams API for team workspace isolation.");
                    }
                }
            }
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Could not initialize FTB Teams API reflection: {}", t.getMessage());
            this.isFtbTeamsPresent = false;
        }
    }

    private Object getTeamManager() {
        if (!isAvailable() || apiInstance == null || getManagerMethod == null) return null;
        try {
            return getManagerMethod.invoke(apiInstance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object getTeamObjectForPlayer(Object manager, ServerPlayer player) {
        if (manager == null || player == null) return null;
        try {
            // 1. Try getTeamForPlayerID(UUID)
            for (Method m : manager.getClass().getMethods()) {
                if (m.getName().equals("getTeamForPlayerID") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                    Object res = m.invoke(manager, player.getUUID());
                    if (res instanceof Optional<?> opt) return opt.orElse(null);
                    if (res != null) return res;
                }
            }
            // 2. Try getTeamForPlayer(Player/ServerPlayer/Entity/UUID)
            for (Method m : manager.getClass().getMethods()) {
                if (m.getName().equals("getTeamForPlayer") && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType.isAssignableFrom(player.getClass()) || paramType.isAssignableFrom(ServerPlayer.class)) {
                        Object res = m.invoke(manager, player);
                        if (res instanceof Optional<?> opt) return opt.orElse(null);
                        if (res != null) return res;
                    } else if (paramType == UUID.class) {
                        Object res = m.invoke(manager, player.getUUID());
                        if (res instanceof Optional<?> opt) return opt.orElse(null);
                        if (res != null) return res;
                    }
                }
            }
            // 3. Try getTeamByID(UUID)
            for (Method m : manager.getClass().getMethods()) {
                if (m.getName().equals("getTeamByID") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                    Object res = m.invoke(manager, player.getUUID());
                    if (res instanceof Optional<?> opt) return opt.orElse(null);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object getTeamObjectById(Object manager, UUID teamId) {
        if (manager == null || teamId == null) return null;
        try {
            for (Method m : manager.getClass().getMethods()) {
                if ((m.getName().equals("getTeamByID") || m.getName().equals("getTeamById") || m.getName().equals("getTeam")) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                    Object res = m.invoke(manager, teamId);
                    if (res instanceof Optional<?> opt) return opt.orElse(null);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private UUID extractTeamId(Object team) {
        if (team == null) return null;
        try {
            for (Method m : team.getClass().getMethods()) {
                if (m.getName().equals("getId") && m.getParameterCount() == 0 && m.getReturnType() == UUID.class) {
                    return (UUID) m.invoke(team);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String extractTeamName(Object team) {
        if (team == null) return null;
        try {
            for (Method m : team.getClass().getMethods()) {
                if ((m.getName().equals("getDisplayName") || m.getName().equals("getName") || m.getName().equals("getStringName")) && m.getParameterCount() == 0) {
                    Object res = m.invoke(team);
                    if (res instanceof Component c) {
                        return c.getString();
                    } else if (res != null) {
                        return res.toString();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Set<UUID> extractTeamMembers(Object team) {
        if (team == null) return Collections.emptySet();
        try {
            for (Method m : team.getClass().getMethods()) {
                if ((m.getName().equals("getMembers") || m.getName().equals("getOnlineMembers") || m.getName().equals("getPlayers")) && m.getParameterCount() == 0) {
                    Object res = m.invoke(team);
                    if (res instanceof Collection<?> col) {
                        Set<UUID> set = new HashSet<>();
                        for (Object item : col) {
                            if (item instanceof UUID u) set.add(u);
                            else if (item instanceof net.minecraft.world.entity.player.Player p) set.add(p.getUUID());
                        }
                        return set;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Collections.emptySet();
    }

    private boolean isActualPartyTeam(Object team) {
        if (team == null) return false;
        try {
            // 1. Check isParty() or isPartyTeam() method
            for (Method m : team.getClass().getMethods()) {
                if ((m.getName().equals("isParty") || m.getName().equals("isPartyTeam")) && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    return (boolean) m.invoke(team);
                }
            }
            // 2. Check isPlayerTeam() method (if true -> NOT a shared party team)
            for (Method m : team.getClass().getMethods()) {
                if (m.getName().equals("isPlayerTeam") && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    boolean isPlayer = (boolean) m.invoke(team);
                    if (isPlayer) return false;
                }
            }
            // 3. Check getType() -> TeamType.isParty() or enum name
            for (Method m : team.getClass().getMethods()) {
                if (m.getName().equals("getType") && m.getParameterCount() == 0) {
                    Object typeObj = m.invoke(team);
                    if (typeObj != null) {
                        String typeName = typeObj.toString().toUpperCase(Locale.ROOT);
                        if (typeName.contains("PARTY") || typeName.contains("SERVER")) {
                            return true;
                        }
                        if (typeName.contains("PLAYER")) {
                            return false;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        if (!isAvailable() || player == null) {
            return null;
        }

        Object manager = getTeamManager();
        if (manager != null) {
            Object team = getTeamObjectForPlayer(manager, player);
            if (team != null && isActualPartyTeam(team)) {
                UUID id = extractTeamId(team);
                if (id != null) {
                    return id;
                }
            }
        }

        return null;
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        if (!isAvailable() || teamId == null) {
            return teamId != null ? "Team " + teamId.toString().substring(0, 8) : "Unknown Team";
        }

        Object manager = getTeamManager();
        if (manager != null) {
            Object team = getTeamObjectById(manager, teamId);
            if (team != null) {
                String name = extractTeamName(team);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        }

        return "Team (" + teamId.toString().substring(0, 8) + ")";
    }

    @Override
    public Set<UUID> getTeamMembers(UUID teamId) {
        if (!isAvailable() || teamId == null) {
            return teamId != null ? Collections.singleton(teamId) : Collections.emptySet();
        }

        Object manager = getTeamManager();
        if (manager != null) {
            Object team = getTeamObjectById(manager, teamId);
            if (team != null) {
                Set<UUID> members = extractTeamMembers(team);
                if (!members.isEmpty()) {
                    return members;
                }
            }
        }

        return Collections.singleton(teamId);
    }

    @Override
    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
        UUID playerTeam = getPlayerTeamId(player);
        return teamId.equals(playerTeam);
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
            return false;
        }

        Object manager = getTeamManager();
        if (manager != null) {
            Object team = getTeamObjectById(manager, teamId);
            if (team != null && isActualPartyTeam(team)) {
                try {
                    // 1. Direct Owner UUID check
                    for (Method m : team.getClass().getMethods()) {
                        if (m.getName().equals("getOwner") && m.getParameterCount() == 0) {
                            Object owner = m.invoke(team);
                            if (owner instanceof UUID ownerUUID && ownerUUID.equals(player.getUUID())) {
                                return true;
                            }
                        }
                    }
                    // 2. FTB Teams getRankForPlayer check
                    for (Method m : team.getClass().getMethods()) {
                        if (m.getName().equals("getRankForPlayer") && m.getParameterCount() == 1) {
                            Class<?> pClass = m.getParameterTypes()[0];
                            Object target = pClass == UUID.class ? player.getUUID() : player;
                            Object rank = m.invoke(team, target);
                            if (rank != null) {
                                String rankName = rank.toString().toUpperCase(java.util.Locale.ROOT);
                                if (rankName.contains("OWNER") || rankName.contains("OFFICER") || rankName.contains("ADMIN")) {
                                    return true;
                                } else {
                                    // Player is explicitly a regular MEMBER, ALLY, or NONE -> Deny delete!
                                    return false;
                                }
                            }
                        }
                    }
                    // 3. Fallback method checks
                    for (Method m : team.getClass().getMethods()) {
                        if ((m.getName().equals("isOfficer") || m.getName().equals("isOwner") || m.getName().equals("isAdmin")) && m.getParameterCount() == 1) {
                            Class<?> pClass = m.getParameterTypes()[0];
                            Object target = pClass == UUID.class ? player.getUUID() : player;
                            Object res = m.invoke(team, target);
                            if (Boolean.TRUE.equals(res)) {
                                return true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        return false;
    }

    @Override
    public String getProviderId() {
        return "ftbteams";
    }

    @Override
    public boolean isAvailable() {
        if (!isInitialized) initReflection();
        return isFtbTeamsPresent;
    }
}
