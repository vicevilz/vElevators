package net.velevators.menu;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeluxeMenusYamlTest {
    @Test
    void allMenuResourcesAreValidYaml() {
        for (String name : new String[]{"main.yml", "manage.yml", "confirm-create.yml", "confirm-delete.yml",
                "add-member.yml", "remove-member.yml", "network-members.yml", "transfer-owner.yml",
                "confirm-transfer.yml", "confirm-delete-network.yml", "network-icon.yml", "floor-icon.yml",
                "admin-main.yml", "admin-networks.yml", "admin-history.yml"}) {
            try (InputStream stream = getClass().getResourceAsStream("/deluxemenus/es_ES/" + name)) {
                assertThat(stream).as(name).isNotNull();
                Object parsed = new Yaml().load(stream);
                assertThat(parsed).as(name).isInstanceOf(Map.class);
            } catch (Exception exception) {
                throw new AssertionError("Menú YAML inválido: " + name, exception);
            }
        }
    }


    @SuppressWarnings("unchecked")
    @Test
    void memberSelectorsContainThirtySixSessionBoundHeadsAndFallbackControls() {
        for (String name : new String[]{"add-member.yml", "remove-member.yml"}) {
            try (InputStream stream = getClass().getResourceAsStream("/deluxemenus/es_ES/" + name)) {
                Map<String, Object> root = new Yaml().load(stream);
                Map<String, Object> items = (Map<String, Object>) root.get("items");
                long players = items.keySet().stream().filter(key -> key.startsWith("member_")).count();
                assertThat(players).isEqualTo(36);
                for (int index = 0; index < 36; index++) {
                    String id = "member_" + String.format("%02d", index);
                    Map<String, Object> item = (Map<String, Object>) items.get(id);
                    assertThat(item.get("slot")).isEqualTo(9 + index);
                    String expectedMaterial = name.equals("remove-member.yml")
                            ? "texture-%velevators_member_player_" + index + "_texture%"
                            : "head-%velevators_member_player_" + index + "_name%";
                    assertThat(item.toString()).contains(
                            expectedMaterial,
                            "%velevators_member_player_" + index + "_exists%",
                            "velevatorsaction memberselect " + index);
                    if (name.equals("remove-member.yml")) {
                        assertThat(item.toString()).contains(
                                "%velevators_member_player_" + index + "_member%",
                                "%velevators_member_player_" + index + "_connection%");
                    }
                }
                if (name.equals("add-member.yml")) {
                    assertThat(items.get("manual").toString()).contains("velevatorsaction memberchat", "[close]");
                } else {
                    assertThat(items.get("summary").toString()).contains("%velevators_network_member_count%");
                    assertThat(items.get("empty").toString()).contains("%velevators_network_member_count%", "output=0");
                }
                assertThat(items.get("previous_page").toString()).contains(
                        "%velevators_member_has_previous_page%", "velevatorsaction memberpage -1");
                assertThat(items.get("next_page").toString()).contains(
                        "%velevators_member_has_next_page%", "velevatorsaction memberpage 1");
                assertThat(items.get("back").toString()).contains("velevatorsaction memberback");
            } catch (Exception exception) {
                throw new AssertionError("Selector inválido: " + name, exception);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void managementMenuContainsTwentyOneSessionBoundFloorSlotsInsideTheBorders() {
        try (InputStream stream = getClass().getResourceAsStream("/deluxemenus/es_ES/manage.yml")) {
            Map<String, Object> root = new Yaml().load(stream);
            Map<String, Object> items = (Map<String, Object>) root.get("items");
            long floors = items.keySet().stream().filter(key -> key.matches("floor_\\d{2}")).count();
            int[] expectedSlots = {19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43};
            assertThat(floors).isEqualTo(21);
            for (int index = 0; index < expectedSlots.length; index++) {
                String id = "floor_" + String.format("%02d", index);
                Map<String, Object> item = (Map<String, Object>) items.get(id);
                assertThat(item.get("slot")).isEqualTo(expectedSlots[index]);
                assertThat(item.toString()).contains("velevatorsaction rename " + index, "velevatorsaction delete " + index,
                        "%velevators_floor_" + index + "_exists%");
            }
            assertThat(items.get("floor_header").toString())
                    .contains("LIME_STAINED_GLASS_PANE", "9-17");
            assertThat(items.get("floor_space").toString())
                    .contains("material=AIR", "19-25", "28-34", "37-43", "priority=90");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void managementMenuContainsCustomizationAccessMemberTransferAndDeleteControls() {
        try (InputStream stream = getClass().getResourceAsStream("/deluxemenus/es_ES/manage.yml")) {
            Map<String, Object> root = new Yaml().load(stream);
            Map<String, Object> items = (Map<String, Object>) root.get("items");

            assertThat(items.get("information").toString())
                    .contains("%velevators_network_owner%", "%velevators_network_name%",
                            "%velevators_network_member_count%", "%velevators_network_access_mode%");
            assertThat(items.get("add_member").toString())
                    .contains("velevatorsaction addmember", "[sound]");
            assertThat(items.get("remove_member").toString())
                    .contains("velevatorsaction removemember", "[sound]");
            assertThat(items.get("rename_network").toString()).contains("velevatorsaction renamenetwork");
            assertThat(items.get("network_icon").toString()).contains("velevatorsaction networkicons");
            assertThat(items.get("view_members").toString()).contains("velevatorsaction viewmembers");
            assertThat(items.get("transfer_owner").toString()).contains("velevatorsaction transferowner");
            assertThat(items.get("network_access").toString())
                    .contains("velevatorsaction networkaccess", "velevators_network_access_mode",
                            "texture-%velevators_network_access_texture%");
            assertThat(items).doesNotContainKeys("network_access_private", "network_access_owner");
            assertThat(items.get("delete_network").toString()).contains("velevatorsaction deletenetwork");
            for (int index = 0; index < 21; index++) {
                Map<String, Object> floor = (Map<String, Object>) items.get("floor_" + String.format("%02d", index));
                assertThat(floor.toString()).contains("velevatorsaction flooricons " + index,
                        "velevatorsaction flooraccess " + index,
                        "%velevators_floor_" + index + "_icon%",
                        "%velevators_floor_" + index + "_access_mode%");
            }
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void directoryTransferIconAndAdministrativeMenusExposeEverySessionBoundAction() {
        Map<String, Object> directory = items("network-members.yml");
        Map<String, Object> transfer = items("transfer-owner.yml");
        Map<String, Object> networkIcons = items("network-icon.yml");
        Map<String, Object> floorIcons = items("floor-icon.yml");
        Map<String, Object> adminPlayers = items("admin-main.yml");
        Map<String, Object> adminNetworks = items("admin-networks.yml");
        Map<String, Object> audit = items("admin-history.yml");

        assertThat(directory.keySet().stream().filter(key -> key.startsWith("member_")).count()).isEqualTo(36);
        assertThat(transfer.keySet().stream().filter(key -> key.startsWith("member_")).count()).isEqualTo(36);
        assertThat(networkIcons.keySet().stream().filter(key -> key.startsWith("icon_")).count()).isEqualTo(36);
        assertThat(floorIcons.keySet().stream().filter(key -> key.startsWith("icon_")).count()).isEqualTo(36);
        assertThat(adminPlayers.keySet().stream().filter(key -> key.startsWith("player_")).count()).isEqualTo(36);
        assertThat(adminNetworks.keySet().stream().filter(key -> key.startsWith("network_")).count()).isEqualTo(36);
        assertThat(audit.keySet().stream().filter(key -> key.startsWith("audit_")).count()).isEqualTo(36);

        for (int index = 0; index < 36; index++) {
            String twoDigits = String.format("%02d", index);
            assertThat(directory.get("member_" + twoDigits).toString())
                    .contains("%velevators_member_player_" + index + "_relation_colored%",
                            "%velevators_member_player_" + index + "_texture%");
            assertThat(transfer.get("member_" + twoDigits).toString())
                    .contains("velevatorsaction transferselect " + index);
            assertThat(networkIcons.get("icon_" + twoDigits).toString())
                    .contains("velevatorsaction setnetworkicon");
            assertThat(floorIcons.get("icon_" + twoDigits).toString())
                    .contains("velevatorsaction setflooricon");
            assertThat(adminPlayers.get("player_" + twoDigits).toString())
                    .contains("velevatorsaction adminplayerselect " + index);
            assertThat(adminNetworks.get("network_" + twoDigits).toString())
                    .contains("velevatorsaction adminnetworkmanage " + index,
                            "velevatorsaction adminnetworkteleport " + index);
            assertThat(audit.get("audit_" + twoDigits).toString())
                    .contains("%velevators_audit_" + index + "_action%",
                            "%velevators_audit_" + index + "_actor%");
        }
        assertThat(adminPlayers.get("search_owner").toString()).contains("velevatorsaction adminsearch owner");
        assertThat(adminPlayers.get("search_world").toString()).contains("velevatorsaction adminsearch world");
        assertThat(adminPlayers.get("search_coords").toString()).contains("velevatorsaction adminsearch coords");
        assertThat(adminPlayers.get("history").toString())
                .contains("velevatorsaction adminhistory", "Pagos archivados", "collect", "clear");
        assertThat(items("admin-history.yml").get("information").toString())
                .contains("Auditoría y transacciones", "collect", "clear");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> items(String resource) {
        try (InputStream stream = getClass().getResourceAsStream("/deluxemenus/es_ES/" + resource)) {
            assertThat(stream).as(resource).isNotNull();
            Map<String, Object> root = new Yaml().load(stream);
            return (Map<String, Object>) root.get("items");
        } catch (Exception exception) {
            throw new AssertionError("Menú inválido: " + resource, exception);
        }
    }
}
