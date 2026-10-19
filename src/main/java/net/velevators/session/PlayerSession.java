package net.velevators.session;

import net.velevators.model.FloorLocation;
import net.velevators.model.FloorView;
import net.velevators.model.MemberCandidateView;
import net.velevators.model.NetworkSummaryView;
import net.velevators.model.AuditRecord;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSession {
    public static final int FLOORS_PER_PAGE = 21;
    public static final int MEMBERS_PER_PAGE = 36;

    private volatile SessionMode mode = SessionMode.MAIN;
    private volatile long expiresAtMillis = Long.MAX_VALUE;
    private volatile Long networkId;
    private volatile Long currentFloorId;
    private volatile Long selectedFloorId;
    private volatile FloorLocation pendingLocation;
    private volatile int page;
    private volatile List<FloorView> floors = List.of();
    private volatile int memberPage;
    private volatile List<MemberCandidateView> memberCandidates = List.of();
    private volatile java.util.UUID selectedMemberId;
    private volatile String selectedMemberName;
    private volatile String selectedMemberTexture;
    private volatile int adminPage;
    private volatile List<NetworkSummaryView> networkCandidates = List.of();
    private volatile List<AuditRecord> auditRecords = List.of();

    public SessionMode mode() {
        return mode;
    }

    public boolean expired() {
        return System.currentTimeMillis() > expiresAtMillis;
    }

    public int remainingSeconds() {
        long remainingMillis = Math.max(0L, expiresAtMillis - System.currentTimeMillis());
        return (int) Math.max(0L, (remainingMillis + 999L) / 1_000L);
    }

    public Long networkId() {
        return networkId;
    }

    public Long currentFloorId() {
        return currentFloorId;
    }

    public Long selectedFloorId() {
        return selectedFloorId;
    }

    public FloorLocation pendingLocation() {
        return pendingLocation;
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        return Math.max(1, (floors.size() + FLOORS_PER_PAGE - 1) / FLOORS_PER_PAGE);
    }

    public List<FloorView> floors() {
        return floors;
    }

    public int memberPage() {
        return memberPage;
    }

    public int memberPageCount() {
        return Math.max(1, (memberCandidates.size() + MEMBERS_PER_PAGE - 1) / MEMBERS_PER_PAGE);
    }

    public List<MemberCandidateView> memberCandidates() {
        return memberCandidates;
    }

    public java.util.UUID selectedMemberId() {
        return selectedMemberId;
    }

    public String selectedMemberName() {
        return selectedMemberName;
    }

    public String selectedMemberTexture() {
        return selectedMemberTexture;
    }

    public int adminPage() {
        return adminPage;
    }

    public int adminPageCount() {
        int size = mode == SessionMode.ADMIN_HISTORY ? auditRecords.size() : networkCandidates.size();
        return Math.max(1, (size + MEMBERS_PER_PAGE - 1) / MEMBERS_PER_PAGE);
    }

    public NetworkSummaryView networkAtSlot(int slot) {
        if (slot < 0 || slot >= MEMBERS_PER_PAGE) {
            return null;
        }
        int index = adminPage * MEMBERS_PER_PAGE + slot;
        return index >= 0 && index < networkCandidates.size() ? networkCandidates.get(index) : null;
    }

    public AuditRecord auditAtSlot(int slot) {
        if (slot < 0 || slot >= MEMBERS_PER_PAGE) {
            return null;
        }
        int index = adminPage * MEMBERS_PER_PAGE + slot;
        return index >= 0 && index < auditRecords.size() ? auditRecords.get(index) : null;
    }

    public List<NetworkSummaryView> networkCandidates() {
        return networkCandidates;
    }

    public List<AuditRecord> auditRecords() {
        return auditRecords;
    }

    public MemberCandidateView memberAtSlot(int slot) {
        if (slot < 0 || slot >= MEMBERS_PER_PAGE) {
            return null;
        }
        int index = memberPage * MEMBERS_PER_PAGE + slot;
        return index >= 0 && index < memberCandidates.size() ? memberCandidates.get(index) : null;
    }

    public boolean updateMemberPageTextures(int expectedPage, List<MemberCandidateView> resolvedPage) {
        if (expectedPage != memberPage) {
            return false;
        }
        int fromIndex = expectedPage * MEMBERS_PER_PAGE;
        int toIndex = Math.min(fromIndex + MEMBERS_PER_PAGE, memberCandidates.size());
        if (fromIndex > toIndex || resolvedPage.size() != toIndex - fromIndex) {
            return false;
        }
        List<MemberCandidateView> updated = new ArrayList<>(memberCandidates);
        for (int index = 0; index < resolvedPage.size(); index++) {
            MemberCandidateView current = updated.get(fromIndex + index);
            MemberCandidateView resolved = resolvedPage.get(index);
            if (!current.playerId().equals(resolved.playerId())) {
                return false;
            }
            updated.set(fromIndex + index, resolved);
        }
        memberCandidates = List.copyOf(updated);
        return true;
    }

    public FloorView floorAtSlot(int slot) {
        if (slot < 0 || slot >= FLOORS_PER_PAGE) {
            return null;
        }
        int index = page * FLOORS_PER_PAGE + slot;
        return index >= 0 && index < floors.size() ? floors.get(index) : null;
    }

    public void main() {
        clear();
        mode = SessionMode.MAIN;
    }

    public void selecting(int timeoutSeconds) {
        clear();
        expiresAtMillis = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        mode = SessionMode.SELECTING;
    }

    public void confirmCreate(FloorLocation location, int timeoutSeconds) {
        clear();
        pendingLocation = location;
        expiresAtMillis = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        mode = SessionMode.CONFIRM_CREATE;
    }

    public void managing(long managedNetworkId, Long sourceFloorId, List<FloorView> floorViews, int requestedPage) {
        clear();
        networkId = managedNetworkId;
        currentFloorId = sourceFloorId;
        floors = List.copyOf(floorViews);
        page = Math.max(0, Math.min(requestedPage, pageCount() - 1));
        mode = SessionMode.MANAGING;
    }

    public void confirmDelete(long floorId) {
        selectedFloorId = floorId;
        mode = SessionMode.CONFIRM_DELETE;
    }

    public void confirmDeleteNetwork() {
        selectedFloorId = null;
        mode = SessionMode.CONFIRM_DELETE_NETWORK;
    }

    public void selectingMembers(boolean adding, List<MemberCandidateView> candidates, int requestedPage) {
        selectedFloorId = null;
        expiresAtMillis = Long.MAX_VALUE;
        memberCandidates = List.copyOf(candidates);
        memberPage = Math.max(0, Math.min(requestedPage, memberPageCount() - 1));
        mode = adding ? SessionMode.SELECTING_MEMBER_ADD : SessionMode.SELECTING_MEMBER_REMOVE;
    }

    public void memberDirectory(SessionMode directoryMode, List<MemberCandidateView> candidates, int requestedPage) {
        if (directoryMode != SessionMode.VIEWING_MEMBERS && directoryMode != SessionMode.SELECTING_TRANSFER
                && directoryMode != SessionMode.ADMIN_SELECTING_PLAYER) {
            throw new IllegalArgumentException("Modo de directorio inválido");
        }
        selectedFloorId = null;
        selectedMemberId = null;
        selectedMemberName = null;
        selectedMemberTexture = null;
        expiresAtMillis = Long.MAX_VALUE;
        memberCandidates = List.copyOf(candidates);
        memberPage = Math.max(0, Math.min(requestedPage, memberPageCount() - 1));
        mode = directoryMode;
    }

    public void confirmTransfer(MemberCandidateView candidate) {
        selectedMemberId = candidate.playerId();
        selectedMemberName = candidate.playerName();
        selectedMemberTexture = candidate.textureId();
        mode = SessionMode.CONFIRM_TRANSFER;
    }

    public void renaming(long floorId, int timeoutSeconds) {
        selectedFloorId = floorId;
        expiresAtMillis = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        mode = SessionMode.RENAMING;
    }

    public void renamingNetwork(int timeoutSeconds) {
        selectedFloorId = null;
        expiresAtMillis = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        mode = SessionMode.RENAMING_NETWORK;
    }

    public void selectingNetworkIcon() {
        selectedFloorId = null;
        mode = SessionMode.SELECTING_NETWORK_ICON;
    }

    public void selectingFloorIcon(long floorId) {
        selectedFloorId = floorId;
        mode = SessionMode.SELECTING_FLOOR_ICON;
    }

    public void adminPlayers(List<MemberCandidateView> candidates, int requestedPage) {
        clear();
        memberDirectory(SessionMode.ADMIN_SELECTING_PLAYER, candidates, requestedPage);
    }

    public void adminInput(SessionMode inputMode, int timeoutSeconds) {
        if (inputMode != SessionMode.ADMIN_SEARCH_OWNER && inputMode != SessionMode.ADMIN_SEARCH_WORLD
                && inputMode != SessionMode.ADMIN_SEARCH_COORDINATES) {
            throw new IllegalArgumentException("Modo de búsqueda administrativa inválido");
        }
        clear();
        expiresAtMillis = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        mode = inputMode;
    }

    public void adminNetworks(List<NetworkSummaryView> networks, int requestedPage) {
        clear();
        networkCandidates = List.copyOf(networks);
        adminPage = Math.max(0, Math.min(requestedPage, adminPageCount() - 1));
        mode = SessionMode.ADMIN_NETWORKS;
    }

    public void adminHistory(List<AuditRecord> records, int requestedPage) {
        clear();
        auditRecords = List.copyOf(records);
        mode = SessionMode.ADMIN_HISTORY;
        adminPage = Math.max(0, Math.min(requestedPage, adminPageCount() - 1));
    }

    public void addingMember(int timeoutSeconds) {
        startMemberInput(SessionMode.ADDING_MEMBER, timeoutSeconds);
    }

    public void removingMember(int timeoutSeconds) {
        startMemberInput(SessionMode.REMOVING_MEMBER, timeoutSeconds);
    }

    private void startMemberInput(SessionMode memberMode, int timeoutSeconds) {
        selectedFloorId = null;
        expiresAtMillis = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        mode = memberMode;
    }

    private void clear() {
        expiresAtMillis = Long.MAX_VALUE;
        networkId = null;
        currentFloorId = null;
        selectedFloorId = null;
        pendingLocation = null;
        page = 0;
        floors = List.of();
        memberPage = 0;
        memberCandidates = List.of();
        selectedMemberId = null;
        selectedMemberName = null;
        selectedMemberTexture = null;
        adminPage = 0;
        networkCandidates = List.of();
        auditRecords = List.of();
        mode = SessionMode.MAIN;
    }
}
