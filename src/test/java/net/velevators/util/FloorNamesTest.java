package net.velevators.util;

import net.velevators.model.FloorRecord;
import net.velevators.model.FloorView;
import net.velevators.model.NetworkRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FloorNamesTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();

    @Test
    void numbersFloorsAroundTheOriginalAnchor() {
        NetworkRecord network = new NetworkRecord(9, owner, "Vicevil", world, "world", 4419, 1002,
                2L, false, "now");
        List<FloorRecord> floors = List.of(
                floor(3, 70, null),
                floor(1, 28, null),
                floor(2, 54, "piso de pepito"),
                floor(4, 60, null)
        );

        List<FloorView> views = FloorNames.buildViews(network, floors, 2L);

        assertThat(views).extracting(FloorView::number).containsExactly(-1, 0, 1, 2);
        assertThat(views).extracting(FloorView::displayName)
                .containsExactly("Piso -1", "piso de pepito", "Piso 1", "Piso 2");
        assertThat(views).extracting(FloorView::current).containsExactly(false, true, false, false);
    }

    private FloorRecord floor(long id, int y, String customName) {
        return new FloorRecord(id, 9, world, "world", 4419, y, 1002, "QUARTZ_BLOCK",
                customName, BigDecimal.TEN, "now");
    }
}
