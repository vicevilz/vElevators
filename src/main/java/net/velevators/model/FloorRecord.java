package net.velevators.model;

import java.math.BigDecimal;
import java.util.UUID;

public record FloorRecord(
        long id,
        long networkId,
        UUID worldId,
        String worldName,
        int x,
        int y,
        int z,
        String material,
        String customName,
        String iconMaterial,
        AccessMode accessMode,
        BigDecimal paidAmount,
        String createdAt
) {
    public FloorRecord(long id, long networkId, UUID worldId, String worldName, int x, int y, int z,
                       String material, String customName, BigDecimal paidAmount, String createdAt) {
        this(id, networkId, worldId, worldName, x, y, z, material, customName,
                "QUARTZ_BLOCK", AccessMode.PUBLIC, paidAmount, createdAt);
    }

    public FloorLocation location() {
        return new FloorLocation(worldId, worldName, x, y, z);
    }
}
