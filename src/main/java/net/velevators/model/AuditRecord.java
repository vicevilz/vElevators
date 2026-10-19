package net.velevators.model;

import java.util.UUID;

public record AuditRecord(
        long id,
        Long networkId,
        UUID actorId,
        String actorName,
        String action,
        String details,
        String createdAt
) {
}
