package net.velevators.session;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionManager {
    private final ConcurrentMap<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public PlayerSession getOrCreate(UUID playerId) {
        return sessions.computeIfAbsent(playerId, ignored -> new PlayerSession());
    }

    public Optional<PlayerSession> get(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session != null && session.expired()) {
            sessions.remove(playerId, session);
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    public Optional<PlayerSession> peek(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clear() {
        sessions.clear();
    }
}
