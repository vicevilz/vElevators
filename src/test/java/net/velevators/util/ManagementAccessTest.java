package net.velevators.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementAccessTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void ownerRequiresManagePermission() {
        assertThat(ManagementAccess.canManage(owner, owner, false, true, false)).isTrue();
        assertThat(ManagementAccess.canManage(owner, owner, false, false, false)).isFalse();
    }

    @Test
    void onlyNetworkMembersGainManagementThroughTheRegularPermission() {
        assertThat(ManagementAccess.canManage(other, owner, false, true, false)).isFalse();
        assertThat(ManagementAccess.canManage(other, owner, true, true, false)).isTrue();
        assertThat(ManagementAccess.canManage(other, owner, true, false, false)).isFalse();
    }

    @Test
    void administratorCanManageAndOnlyOwnerOrAdministratorCanAdministerNetwork() {
        assertThat(ManagementAccess.canManage(other, owner, false, false, true)).isTrue();
        assertThat(ManagementAccess.canAdminister(owner, owner, true, false)).isTrue();
        assertThat(ManagementAccess.canAdminister(other, owner, true, false)).isFalse();
        assertThat(ManagementAccess.canAdminister(other, owner, false, true)).isTrue();
    }
}
