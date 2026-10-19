package net.velevators.model;

import net.velevators.util.SkinTextureIds;

import java.util.UUID;

public record MemberCandidateView(
        UUID playerId,
        String playerName,
        boolean owner,
        boolean member,
        boolean self,
        boolean online,
        String textureId
) {
    public MemberCandidateView(UUID playerId, String playerName, boolean owner, boolean member,
                               boolean self, boolean online) {
        this(playerId, playerName, owner, member, self, online, SkinTextureIds.STEVE);
    }

    public MemberCandidateView withTextureId(String resolvedTextureId) {
        return new MemberCandidateView(playerId, playerName, owner, member, self, online, resolvedTextureId);
    }

    public String relation() {
        if (owner) {
            return "Propietario";
        }
        if (member) {
            return "Miembro";
        }
        if (self) {
            return "Tú";
        }
        return "No es miembro";
    }

    public String connectionStatus() {
        return online ? "Conectado" : "Desconectado";
    }
}
