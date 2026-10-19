package net.velevators.util;

import java.util.UUID;

public final class ManagementAccess {
    private ManagementAccess() {
    }

    public static boolean canManage(UUID actorId, UUID ownerId, boolean member,
                                    boolean canManageOwn, boolean canManageOthers) {
        return canManageOthers || (canManageOwn && (actorId.equals(ownerId) || member));
    }

    public static boolean canAdminister(UUID actorId, UUID ownerId,
                                        boolean canManageOwn, boolean canManageOthers) {
        return canManageOthers || (canManageOwn && actorId.equals(ownerId));
    }
}
