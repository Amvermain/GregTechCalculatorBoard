package com.gtceu.calcboard.server.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World SavedData for storing all team workspaces persistently in the Minecraft world directory.
 */
public class TeamBoardSavedData extends SavedData {

    public static final String DATA_NAME = "gtcalcboard_workspaces";

    private final Map<UUID, TeamWorkspaceData> workspaces = new ConcurrentHashMap<>();

    public TeamBoardSavedData() {}

    public static TeamBoardSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TeamBoardSavedData data = new TeamBoardSavedData();
        if (tag.contains("Workspaces", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Workspaces", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag teamTag = list.getCompound(i);
                TeamWorkspaceData ws = TeamWorkspaceData.fromNBT(teamTag);
                data.workspaces.put(ws.getTeamId(), ws);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TeamWorkspaceData ws : workspaces.values()) {
            list.add(ws.toNBT());
        }
        tag.put("Workspaces", list);
        return tag;
    }

    public static SavedData.Factory<TeamBoardSavedData> factory() {
        return new SavedData.Factory<>(TeamBoardSavedData::new, TeamBoardSavedData::load, null);
    }

    public static TeamBoardSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) return null;
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public TeamWorkspaceData getOrCreateWorkspace(UUID teamId, String defaultName) {
        if (teamId == null) return null;
        return workspaces.computeIfAbsent(teamId, id -> {
            setDirty();
            return new TeamWorkspaceData(id, defaultName);
        });
    }

    public TeamWorkspaceData getWorkspace(UUID teamId) {
        return teamId != null ? workspaces.get(teamId) : null;
    }

    public void saveWorkspace(TeamWorkspaceData workspace) {
        if (workspace != null && workspace.getTeamId() != null) {
            workspaces.put(workspace.getTeamId(), workspace);
            setDirty();
        }
    }
}
