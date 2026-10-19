package net.velevators.model;

public record NetworkSummaryView(
        NetworkRecord network,
        int floorCount,
        int memberCount,
        FloorRecord anchorFloor
) {
}
