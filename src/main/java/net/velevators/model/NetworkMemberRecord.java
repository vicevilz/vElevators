package net.velevators.model;

import java.util.UUID;

public record NetworkMemberRecord(
        long networkId,
        UUID memberId,
        String memberName,
        String addedAt
) {
}
