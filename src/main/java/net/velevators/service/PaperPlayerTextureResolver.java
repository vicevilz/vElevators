package net.velevators.service;

import net.velevators.model.MemberCandidateView;
import net.velevators.util.SkinTextureIds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public final class PaperPlayerTextureResolver implements MemberTextureResolver {
    private static final long TEXTURE_CACHE_MILLIS = Duration.ofHours(6).toMillis();
    private static final long FALLBACK_CACHE_MILLIS = Duration.ofMinutes(10).toMillis();

    private final JavaPlugin plugin;
    private final ConcurrentMap<String, CachedTexture> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    public PaperPlayerTextureResolver(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<List<MemberCandidateView>> resolveCandidates(List<MemberCandidateView> candidates) {
        List<CompletableFuture<MemberCandidateView>> resolutions = candidates.stream()
                .map(this::resolveCandidate)
                .toList();
        return CompletableFuture.allOf(resolutions.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> resolutions.stream().map(CompletableFuture::join).toList());
    }

    private CompletableFuture<MemberCandidateView> resolveCandidate(MemberCandidateView candidate) {
        String key = candidate.playerName().toLowerCase(Locale.ROOT);
        Optional<String> onlineTexture = onlineTexture(candidate);
        if (onlineTexture.isPresent()) {
            String textureId = onlineTexture.orElseThrow();
            cache.put(key, new CachedTexture(textureId, System.currentTimeMillis() + TEXTURE_CACHE_MILLIS));
            return CompletableFuture.completedFuture(candidate.withTextureId(textureId));
        }

        CachedTexture cached = cache.get(key);
        if (cached != null && !cached.expired()) {
            return CompletableFuture.completedFuture(candidate.withTextureId(cached.textureId()));
        }
        if (cached != null) {
            cache.remove(key, cached);
        }
        return officialTexture(key, candidate.playerName())
                .thenApply(candidate::withTextureId);
    }

    private Optional<String> onlineTexture(MemberCandidateView candidate) {
        Player online = Bukkit.getPlayer(candidate.playerId());
        if (online == null || !online.isOnline()) {
            return Optional.empty();
        }
        try {
            return textureId(online.getPlayerProfile());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE,
                    "No se pudo leer la textura activa de " + candidate.playerName(), exception);
            return Optional.empty();
        }
    }

    private CompletableFuture<String> officialTexture(String key, String playerName) {
        CompletableFuture<String> existing = inFlight.get(key);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<String> created;
        try {
            PlayerProfile nameOnlyProfile = Bukkit.createPlayerProfile(null, playerName);
            created = nameOnlyProfile.update().handle((updated, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().log(Level.FINE,
                            "No se pudo consultar la textura oficial de " + playerName, throwable);
                    return cacheFallback(key);
                }
                Optional<String> texture = textureId(updated);
                if (texture.isEmpty()) {
                    return cacheFallback(key);
                }
                String textureId = texture.orElseThrow();
                cache.put(key, new CachedTexture(textureId,
                        System.currentTimeMillis() + TEXTURE_CACHE_MILLIS));
                return textureId;
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE,
                    "No se pudo iniciar la consulta de textura de " + playerName, exception);
            return CompletableFuture.completedFuture(cacheFallback(key));
        }

        CompletableFuture<String> winner = inFlight.putIfAbsent(key, created);
        if (winner != null) {
            return winner;
        }
        created.whenComplete((ignored, throwable) -> inFlight.remove(key, created));
        return created;
    }

    private String cacheFallback(String key) {
        cache.put(key, new CachedTexture(SkinTextureIds.STEVE,
                System.currentTimeMillis() + FALLBACK_CACHE_MILLIS));
        return SkinTextureIds.STEVE;
    }

    static Optional<String> textureId(PlayerProfile profile) {
        if (profile == null || profile.getTextures().isEmpty()) {
            return Optional.empty();
        }
        URL skin = profile.getTextures().getSkin();
        if (skin == null) {
            return Optional.empty();
        }
        String path = skin.getPath();
        int marker = path.lastIndexOf("/texture/");
        if (marker < 0) {
            return Optional.empty();
        }
        String textureId = path.substring(marker + "/texture/".length());
        if (!textureId.matches("[0-9a-fA-F]{32,128}")) {
            return Optional.empty();
        }
        return Optional.of(textureId.toLowerCase(Locale.ROOT));
    }

    private record CachedTexture(String textureId, long expiresAtMillis) {
        private boolean expired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
