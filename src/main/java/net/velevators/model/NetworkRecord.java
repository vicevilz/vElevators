package net.velevators.model;

import java.util.UUID;

public record NetworkRecord(
        long id,
        UUID ownerId,
        String ownerName,
        UUID worldId,
        String worldName,
        int x,
        int z,
        Long anchorFloorId,
        String displayName,
        String iconMaterial,
        AccessMode accessMode,
        String createdAt
) {
    public NetworkRecord(long id, UUID ownerId, String ownerName, UUID worldId, String worldName,
                         int x, int z, Long anchorFloorId, boolean locked, String createdAt) {
        this(id, ownerId, ownerName, worldId, worldName, x, z, anchorFloorId, null,
                "ENDER_CHEST", locked ? AccessMode.PRIVATE : AccessMode.PUBLIC, createdAt);
    }

    public String effectiveName() {
        return displayName == null || displayName.isBlank() ? "Red de " + ownerName : displayName;
    }

    public boolean locked() {
        return accessMode != AccessMode.PUBLIC;
    }
}
