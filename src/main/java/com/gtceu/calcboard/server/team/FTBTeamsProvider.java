package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

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
    private Method getTeamForPlayerMethod = null;
    private Method getTeamIdMethod = null;
    private Method getTeamNameMethod = null;
    private Method getTeamMembersMethod = null;

    public FTBTeamsProvider() {
        initReflection();
    }

    private synchronized void initReflection() {
        if (isInitialized) return;
        isInitialized = true;

        try {
            if (ModList.get() != null && ModList.get().isLoaded(FTB_TEAMS_MOD_ID)) {
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                Method apiGetter = apiClass.getMethod("api");
                this.apiInstance = apiGetter.invoke(null);

                if (this.apiInstance != null) {
                    Class<?> managerClass = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
                    this.getManagerMethod = apiInstance.getClass().getMethod("getManager");

                    Class<?> teamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
                    this.getTeamForPlayerMethod = managerClass.getMethod("getTeamForPlayer", ServerPlayer.class);
                    this.getTeamIdMethod = teamClass.getMethod("getId");
                    this.getTeamNameMethod = teamClass.getMethod("getDisplayName");
                    this.getTeamMembersMethod = teamClass.getMethod("getMembers");

                    this.isFtbTeamsPresent = true;
                }
            }
        } catch (Throwable t) {
            this.isFtbTeamsPresent = false;
        }
    }

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        if (!isAvailable() || player == null) {
            return player != null ? player.getUUID() : null;
        }

        try {
            Object manager = getManagerMethod.invoke(apiInstance);
            if (manager != null) {
                Object optionalTeam = getTeamForPlayerMethod.invoke(manager, player);
                if (optionalTeam instanceof Optional<?> opt && opt.isPresent()) {
                    Object team = opt.get();
                    Object idObj = getTeamIdMethod.invoke(team);
                    if (idObj instanceof UUID uuid) {
                        return uuid;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return player.getUUID();
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        if (!isAvailable() || teamId == null) {
            return teamId != null ? "Team " + teamId.toString().substring(0, 8) : "Unknown Team";
        }

        try {
            Object manager = getManagerMethod.invoke(apiInstance);
            if (manager != null) {
                Method getTeamById = manager.getClass().getMethod("getTeamByID", UUID.class);
                Object optionalTeam = getTeamById.invoke(manager, teamId);
                if (optionalTeam instanceof Optional<?> opt && opt.isPresent()) {
                    Object team = opt.get();
                    Object nameObj = getTeamNameMethod.invoke(team);
                    if (nameObj != null) {
                        return nameObj.toString();
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "Team (" + teamId.toString().substring(0, 8) + ")";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<UUID> getTeamMembers(UUID teamId) {
        if (!isAvailable() || teamId == null) {
            return teamId != null ? Collections.singleton(teamId) : Collections.emptySet();
        }

        try {
            Object manager = getManagerMethod.invoke(apiInstance);
            if (manager != null) {
                Method getTeamById = manager.getClass().getMethod("getTeamByID", UUID.class);
                Object optionalTeam = getTeamById.invoke(manager, teamId);
                if (optionalTeam instanceof Optional<?> opt && opt.isPresent()) {
                    Object team = opt.get();
                    Object membersObj = getTeamMembersMethod.invoke(team);
                    if (membersObj instanceof Collection<?> col) {
                        Set<UUID> res = new HashSet<>();
                        for (Object o : col) {
                            if (o instanceof UUID u) res.add(u);
                        }
                        return res;
                    }
                }
            }
        } catch (Throwable ignored) {}

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
        if (player.hasPermissions(2)) return true;

        if (!isAvailable()) {
            return player.getUUID().equals(teamId);
        }

        try {
            Object manager = getManagerMethod.invoke(apiInstance);
            if (manager != null) {
                Method getTeamById = manager.getClass().getMethod("getTeamByID", UUID.class);
                Object optionalTeam = getTeamById.invoke(manager, teamId);
                if (optionalTeam instanceof Optional<?> opt && opt.isPresent()) {
                    Object team = opt.get();
                    try {
                        Method getOwnerMethod = team.getClass().getMethod("getOwner");
                        Object ownerObj = getOwnerMethod.invoke(team);
                        if (ownerObj instanceof UUID ownerUUID && ownerUUID.equals(player.getUUID())) {
                            return true;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Method isOfficerMethod = team.getClass().getMethod("isOfficer", ServerPlayer.class);
                        Object isOfficerObj = isOfficerMethod.invoke(team, player);
                        if (Boolean.TRUE.equals(isOfficerObj)) {
                            return true;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Method isOwnerMethod = team.getClass().getMethod("isOwner", ServerPlayer.class);
                        Object isOwnerObj = isOwnerMethod.invoke(team, player);
                        if (Boolean.TRUE.equals(isOwnerObj)) {
                            return true;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return player.getUUID().equals(teamId);
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
