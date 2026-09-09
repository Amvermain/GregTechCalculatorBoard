package com.gtceu.calcboard.server.team;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft-dependency team provider for FTB Teams.
 * Interacts with FTB Teams API via safe reflection to prevent NoClassDefFoundError when FTB Teams is absent.
 */
public class FTBTeamsProvider implements ITeamProvider {

    private static final String FTB_TEAMS_MOD_ID = "ftbteams";
    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

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
            if (ModList.get() == null || !ModList.get().isLoaded(FTB_TEAMS_MOD_ID)) {
                return;
            }

            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Method apiGetter = findMethod(apiClass, new String[]{"api", "getAPI"}, 0);
            if (apiGetter == null) return;

            this.apiInstance = apiGetter.invoke(null);
            if (this.apiInstance == null) return;

            this.getManagerMethod = findMethod(apiInstance.getClass(), new String[]{"getManager", "getTeamManager"}, 0);
            if (this.getManagerMethod != null) {
                this.isFtbTeamsPresent = true;
                GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Successfully hooked into FTB Teams API for team workspace isolation.");
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

        Object byPlayerId = invokeLookup(manager, "getTeamForPlayerID", player.getUUID());
        if (byPlayerId != null) return byPlayerId;

        Object byPlayer = invokeLookup(manager, "getTeamForPlayer", player);
        if (byPlayer != null) return byPlayer;

        Object byPlayerUUID = invokeLookup(manager, "getTeamForPlayer", player.getUUID());
        if (byPlayerUUID != null) return byPlayerUUID;

        return invokeLookup(manager, "getTeamByID", player.getUUID());
    }

    private Object getTeamObjectById(Object manager, UUID teamId) {
        if (manager == null || teamId == null) return null;
        for (String name : new String[]{"getTeamByID", "getTeamById", "getTeam"}) {
            Object res = invokeLookup(manager, name, teamId);
            if (res != null) return res;
        }
        return null;
    }

    private UUID extractTeamId(Object team) {
        if (team == null) return null;
        Method m = getCachedMethod(team.getClass(), "getId", 0);
        if (m != null && m.getReturnType() == UUID.class) {
            try {
                return (UUID) m.invoke(team);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private String extractTeamName(Object team) {
        if (team == null) return null;
        for (String name : new String[]{"getDisplayName", "getName", "getStringName"}) {
            Method m = getCachedMethod(team.getClass(), name, 0);
            if (m == null) continue;
            try {
                Object res = m.invoke(team);
                if (res instanceof Component c) return c.getString();
                if (res != null) return res.toString();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Set<UUID> extractTeamMembers(Object team) {
        if (team == null) return Collections.emptySet();
        for (String name : new String[]{"getMembers", "getOnlineMembers", "getPlayers"}) {
            Method m = getCachedMethod(team.getClass(), name, 0);
            if (m == null) continue;
            try {
                Object res = m.invoke(team);
                if (res instanceof Collection<?> col) {
                    return collectMemberUUIDs(col);
                }
            } catch (Throwable ignored) {}
        }
        return Collections.emptySet();
    }

    private Set<UUID> collectMemberUUIDs(Collection<?> col) {
        Set<UUID> set = new HashSet<>();
        for (Object item : col) {
            if (item instanceof UUID u) {
                set.add(u);
            } else if (item instanceof net.minecraft.world.entity.player.Player p) {
                set.add(p.getUUID());
            }
        }
        return set;
    }

    private boolean isActualPartyTeam(Object team) {
        if (team == null) return false;

        Boolean partyFlag = checkBooleanMethod(team, new String[]{"isParty", "isPartyTeam"});
        if (partyFlag != null) return partyFlag;

        Boolean playerFlag = checkBooleanMethod(team, new String[]{"isPlayerTeam"});
        if (playerFlag != null && playerFlag) return false;

        Method getType = getCachedMethod(team.getClass(), "getType", 0);
        if (getType == null) return false;

        try {
            Object typeObj = getType.invoke(team);
            if (typeObj == null) return false;
            String typeName = typeObj.toString().toUpperCase(Locale.ROOT);
            if (typeName.contains("PARTY") || typeName.contains("SERVER")) return true;
            if (typeName.contains("PLAYER")) return false;
        } catch (Throwable ignored) {}

        return false;
    }

    private Boolean checkBooleanMethod(Object target, String[] names) {
        for (String name : names) {
            Method m = getCachedMethod(target.getClass(), name, 0);
            if (m != null && m.getReturnType() == boolean.class) {
                try {
                    return (boolean) m.invoke(target);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        if (!isAvailable() || player == null) return null;

        Object manager = getTeamManager();
        if (manager == null) return null;

        Object team = getTeamObjectForPlayer(manager, player);
        if (team == null || !isActualPartyTeam(team)) return null;

        return extractTeamId(team);
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
                if (name != null && !name.isEmpty()) return name;
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
                if (!members.isEmpty()) return members;
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

        if (!isAvailable()) return false;

        Object manager = getTeamManager();
        if (manager == null) return false;

        Object team = getTeamObjectById(manager, teamId);
        if (team == null || !isActualPartyTeam(team)) return false;

        return checkTeamAdminPrivileges(team, player);
    }

    private boolean checkTeamAdminPrivileges(Object team, ServerPlayer player) {
        try {
            Method getOwner = getCachedMethod(team.getClass(), "getOwner", 0);
            if (getOwner != null) {
                Object owner = getOwner.invoke(team);
                if (owner instanceof UUID ownerUUID && ownerUUID.equals(player.getUUID())) {
                    return true;
                }
            }

            Boolean rankCheck = checkPlayerRank(team, player);
            if (rankCheck != null) return rankCheck;

            return checkFallbackRoleMethods(team, player);
        } catch (Throwable ignored) {}

        return false;
    }

    private static final String[] ROLE_METHODS = {"isOfficer", "isOwner", "isAdmin"};

    private Boolean checkPlayerRank(Object team, ServerPlayer player) {
        Method m = getCachedMethod(team.getClass(), "getRankForPlayer", 1);
        if (m == null) return null;
        try {
            Class<?> pClass = m.getParameterTypes()[0];
            Object target = pClass == UUID.class ? player.getUUID() : player;
            Object rank = m.invoke(team, target);
            if (rank == null) return null;

            String rankName = rank.toString().toUpperCase(Locale.ROOT);
            return rankName.contains("OWNER") || rankName.contains("OFFICER") || rankName.contains("ADMIN");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean checkFallbackRoleMethods(Object team, ServerPlayer player) {
        for (String roleMethod : ROLE_METHODS) {
            Method m = getCachedMethod(team.getClass(), roleMethod, 1);
            if (m == null) continue;
            try {
                Class<?> pClass = m.getParameterTypes()[0];
                Object target = pClass == UUID.class ? player.getUUID() : player;
                Object res = m.invoke(team, target);
                if (Boolean.TRUE.equals(res)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private Object invokeLookup(Object target, String methodName, Object arg) {
        if (target == null || arg == null) return null;
        Method m = getCachedLookupMethod(target.getClass(), methodName, arg.getClass());
        if (m == null) return null;
        try {
            Object res = m.invoke(target, arg);
            if (res instanceof Optional<?> opt) return opt.orElse(null);
            return res;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method getCachedLookupMethod(Class<?> clazz, String methodName, Class<?> argClass) {
        String key = clazz.getName() + "#" + methodName + "(" + argClass.getName() + ")";
        return METHOD_CACHE.computeIfAbsent(key, k -> Optional.ofNullable(findLookupMethod(clazz, methodName, argClass))).orElse(null);
    }

    private static Method findLookupMethod(Class<?> clazz, String methodName, Class<?> argClass) {
        if (clazz == null || argClass == null) return null;
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                if (m.getParameterTypes()[0].isAssignableFrom(argClass)) {
                    return m;
                }
            }
        }
        return null;
    }

    private static Method getCachedMethod(Class<?> clazz, String methodName, int paramCount) {
        String key = clazz.getName() + "#" + methodName + "(" + paramCount + ")";
        return METHOD_CACHE.computeIfAbsent(key, k -> Optional.ofNullable(findMethod(clazz, methodName, paramCount))).orElse(null);
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        if (clazz == null) return null;
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String[] names, int paramCount) {
        if (clazz == null) return null;
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() != paramCount) continue;
            for (String name : names) {
                if (m.getName().equals(name)) return m;
            }
        }
        return null;
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
