package net.velevators.util;

import net.velevators.model.FloorRecord;
import net.velevators.model.FloorView;
import net.velevators.model.NetworkRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FloorNames {
    private FloorNames() {
    }

    public static List<FloorView> buildViews(NetworkRecord network, List<FloorRecord> source, Long currentFloorId) {
        List<FloorRecord> floors = new ArrayList<>(source);
        floors.sort(Comparator.comparingInt(FloorRecord::y).thenComparingLong(FloorRecord::id));

        int anchorIndex = 0;
        if (network.anchorFloorId() != null) {
            for (int index = 0; index < floors.size(); index++) {
                if (floors.get(index).id() == network.anchorFloorId()) {
                    anchorIndex = index;
                    break;
                }
            }
        }

        List<FloorView> views = new ArrayList<>(floors.size());
        for (int index = 0; index < floors.size(); index++) {
            FloorRecord floor = floors.get(index);
            int number = index - anchorIndex;
            String customName = floor.customName();
            String displayName = customName == null || customName.isBlank() ? "Piso " + number : customName;
            views.add(new FloorView(floor, number, displayName,
                    currentFloorId != null && currentFloorId == floor.id()));
        }
        return List.copyOf(views);
    }
}
