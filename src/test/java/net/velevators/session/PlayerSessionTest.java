package net.velevators.session;

import net.velevators.model.FloorRecord;
import net.velevators.model.FloorView;
import net.velevators.model.MemberCandidateView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSessionTest {
    @Test
    void paginatesTwentyOneFloorsAndClampsRequestedPage() {
        PlayerSession session = new PlayerSession();
        List<FloorView> floors = IntStream.range(0, 22).mapToObj(this::floor).toList();

        session.managing(12L, 1L, floors, 99);

        assertThat(session.pageCount()).isEqualTo(2);
        assertThat(session.page()).isEqualTo(1);
        assertThat(session.floorAtSlot(0)).isEqualTo(floors.get(21));
        assertThat(session.floorAtSlot(1)).isNull();
    }

    @Test
    void renameAndDeleteTransitionsKeepTheManagedNetworkContext() {
        PlayerSession session = new PlayerSession();
        session.managing(12L, 7L, List.of(floor(0)), 0);

        session.renaming(9L, 60);
        assertThat(session.networkId()).isEqualTo(12L);
        assertThat(session.selectedFloorId()).isEqualTo(9L);

        session.managing(12L, 7L, List.of(floor(0)), 0);
        session.confirmDelete(9L);
        assertThat(session.networkId()).isEqualTo(12L);
        assertThat(session.selectedFloorId()).isEqualTo(9L);
    }

    @Test
    void sessionManagerRemovesExpiredSelections() {
        SessionManager sessions = new SessionManager();
        UUID playerId = UUID.randomUUID();
        sessions.getOrCreate(playerId).selecting(-1);

        assertThat(sessions.get(playerId)).isEmpty();
        assertThat(sessions.peek(playerId)).isEmpty();
    }

    @Test
    void memberInputTransitionsKeepNetworkPageAndCurrentFloor() {
        PlayerSession session = new PlayerSession();
        session.managing(12L, 7L, List.of(floor(0)), 0);

        session.addingMember(60);
        assertThat(session.mode()).isEqualTo(SessionMode.ADDING_MEMBER);
        assertThat(session.networkId()).isEqualTo(12L);
        assertThat(session.currentFloorId()).isEqualTo(7L);
        assertThat(session.page()).isZero();

        session.managing(12L, 7L, List.of(floor(0)), 0);
        session.removingMember(60);
        assertThat(session.mode()).isEqualTo(SessionMode.REMOVING_MEMBER);
        assertThat(session.networkId()).isEqualTo(12L);
        assertThat(session.currentFloorId()).isEqualTo(7L);
    }

    @Test
    void memberSelectorPaginatesSnapshotAndKeepsManagementContext() {
        PlayerSession session = new PlayerSession();
        List<FloorView> floors = IntStream.range(0, 37).mapToObj(this::floor).toList();
        session.managing(12L, 7L, floors, 1);
        List<MemberCandidateView> candidates = IntStream.range(0, 37)
                .mapToObj(index -> new MemberCandidateView(UUID.randomUUID(), "Player" + index,
                        index == 0, index == 1, index == 2, index % 2 == 0))
                .toList();

        session.selectingMembers(true, candidates, 99);

        assertThat(session.mode()).isEqualTo(SessionMode.SELECTING_MEMBER_ADD);
        assertThat(session.networkId()).isEqualTo(12L);
        assertThat(session.currentFloorId()).isEqualTo(7L);
        assertThat(session.page()).isEqualTo(1);
        assertThat(session.memberPageCount()).isEqualTo(2);
        assertThat(session.memberPage()).isEqualTo(1);
        assertThat(session.memberAtSlot(0)).isEqualTo(candidates.get(36));
        assertThat(session.memberAtSlot(1)).isNull();

        session.selectingMembers(false, candidates, 0);
        assertThat(session.mode()).isEqualTo(SessionMode.SELECTING_MEMBER_REMOVE);
        assertThat(session.memberAtSlot(0).relation()).isEqualTo("Propietario");
        assertThat(session.memberAtSlot(1).relation()).isEqualTo("Miembro");
        assertThat(session.memberAtSlot(2).relation()).isEqualTo("Tú");
    }

    @Test
    void updatesOnlyTheTexturesOfTheActiveMemberPage() {
        PlayerSession session = new PlayerSession();
        List<MemberCandidateView> candidates = IntStream.range(0, 37)
                .mapToObj(index -> new MemberCandidateView(UUID.randomUUID(), "Player" + index,
                        false, true, false, false))
                .toList();
        session.selectingMembers(false, candidates, 1);
        MemberCandidateView resolved = candidates.get(36).withTextureId("a".repeat(64));

        assertThat(session.updateMemberPageTextures(0, List.of())).isFalse();
        assertThat(session.updateMemberPageTextures(1, List.of(resolved))).isTrue();
        assertThat(session.memberAtSlot(0).textureId()).isEqualTo("a".repeat(64));
        assertThat(session.memberCandidates().get(0)).isEqualTo(candidates.get(0));
    }

    private FloorView floor(int index) {
        FloorRecord record = new FloorRecord(index + 1L, 12L, UUID.randomUUID(), "world",
                4, index, 8, "QUARTZ_BLOCK", null, BigDecimal.TEN, "2026-08-09T12:00:00Z");
        return new FloorView(record, index, "Piso " + index, false);
    }
}
