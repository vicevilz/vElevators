package net.velevators.service;

import net.velevators.model.MemberCandidateView;
import net.velevators.util.SkinTextureIds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperPlayerTextureResolverTest {
    private static final String ONLINE_TEXTURE = "a".repeat(64);
    private static final String OFFLINE_TEXTURE = "b".repeat(64);

    @Test
    void reusesTheTextureAlreadyAppliedToAnOnlinePlayer() throws Exception {
        UUID playerId = UUID.randomUUID();
        JavaPlugin plugin = plugin();
        Player online = mock(Player.class);
        com.destroystokyo.paper.profile.PlayerProfile onlineProfile =
                profileWithTexture(com.destroystokyo.paper.profile.PlayerProfile.class, ONLINE_TEXTURE);
        when(online.isOnline()).thenReturn(true);
        when(online.getPlayerProfile()).thenReturn(onlineProfile);
        MemberCandidateView candidate = candidate(playerId, "OnlinePlayer");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(online);

            List<MemberCandidateView> result = new PaperPlayerTextureResolver(plugin)
                    .resolveCandidates(List.of(candidate)).join();

            assertThat(result).singleElement()
                    .extracting(MemberCandidateView::textureId)
                    .isEqualTo(ONLINE_TEXTURE);
        }
    }

    @Test
    void resolvesAnOfflinePremiumProfileByNameAndCachesItsTexture() throws Exception {
        UUID playerId = UUID.randomUUID();
        JavaPlugin plugin = plugin();
        PlayerProfile partial = mock(PlayerProfile.class);
        PlayerProfile updated = profileWithTexture(PlayerProfile.class, OFFLINE_TEXTURE);
        doReturn(CompletableFuture.completedFuture(updated)).when(partial).update();
        MemberCandidateView candidate = candidate(playerId, "PremiumPlayer");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);
            bukkit.when(() -> Bukkit.createPlayerProfile(null, "PremiumPlayer")).thenReturn(partial);
            PaperPlayerTextureResolver resolver = new PaperPlayerTextureResolver(plugin);

            assertThat(resolver.resolveCandidates(List.of(candidate)).join().getFirst().textureId())
                    .isEqualTo(OFFLINE_TEXTURE);
            assertThat(resolver.resolveCandidates(List.of(candidate)).join().getFirst().textureId())
                    .isEqualTo(OFFLINE_TEXTURE);

            verify(partial).update();
        }
    }

    @Test
    void fallsBackSafelyWhenTheOfficialProfileCannotBeResolved() {
        UUID playerId = UUID.randomUUID();
        JavaPlugin plugin = plugin();
        PlayerProfile partial = mock(PlayerProfile.class);
        when(partial.update()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        MemberCandidateView candidate = candidate(playerId, "UnknownPlayer");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);
            bukkit.when(() -> Bukkit.createPlayerProfile(null, "UnknownPlayer")).thenReturn(partial);

            assertThat(new PaperPlayerTextureResolver(plugin).resolveCandidates(List.of(candidate)).join().getFirst()
                    .textureId()).isEqualTo(SkinTextureIds.STEVE);
        }
    }

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PaperPlayerTextureResolverTest"));
        return plugin;
    }

    private MemberCandidateView candidate(UUID playerId, String playerName) {
        return new MemberCandidateView(playerId, playerName, false, true, false, false);
    }

    private <T extends PlayerProfile> T profileWithTexture(Class<T> type, String textureId) throws Exception {
        T profile = mock(type);
        PlayerTextures textures = mock(PlayerTextures.class);
        when(profile.getTextures()).thenReturn(textures);
        when(textures.isEmpty()).thenReturn(false);
        when(textures.getSkin()).thenReturn(new URL("https://textures.minecraft.net/texture/" + textureId));
        return profile;
    }
}
