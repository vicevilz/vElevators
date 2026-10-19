package net.velevators.model;

import java.util.UUID;

public record FloorLocation(UUID worldId, String worldName, int x, int y, int z) {
}
