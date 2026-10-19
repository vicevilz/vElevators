# PlaceholderAPI reference

Applies to vElevators 1.1.0. All names below use `%velevators_<identifier>%`.

The expansion is registered by the plugin and persists across PlaceholderAPI reloads. Identifiers are matched case-insensitively. These values are primarily designed for the bundled DeluxeMenus files.

## General values

| Identifier | Meaning |
| --- | --- |
| `price` | Creation price formatted by the elevator service |
| `elevator_block` | Configured top material name |
| `player_networks` | Number of networks owned by the supplied player UUID |

`price` and `elevator_block` do not require a player session. `player_networks` needs player context and returns `0` on a database error.

## Current network

These values use the network in the requesting player's session:

| Identifier | Meaning |
| --- | --- |
| `network_name` | Custom name or generated owner-based name |
| `network_icon` | Material used for the network icon |
| `network_owner` | Stored owner name |
| `network_members` | Comma-separated member names; `Ninguno` if empty |
| `network_member_count` | Stored member count, excluding the owner |
| `network_floors` | Number of floor views in the session snapshot |
| `network_locked` | `yes` when access is not public, otherwise `no` |
| `network_lock_status` | Access-mode display label |
| `network_access_mode` | Access-mode display label |
| `network_access_texture` | Texture hash for the access-mode head |
| `can_administer_network` | `yes` when the online requester can administer the current network |

Access-mode labels and several other dynamic labels remain Spanish in 1.1.0 regardless of the selected language.

## Pagination

| Identifiers | Context |
| --- | --- |
| `page`, `pages`, `has_previous_page`, `has_next_page` | Floor management, 21 entries per page |
| `member_page`, `member_pages`, `member_has_previous_page`, `member_has_next_page` | Member selector/directory, 36 entries per page |
| `admin_page`, `admin_pages`, `admin_has_previous_page`, `admin_has_next_page` | Administrative lists, 36 entries per page |

Displayed page numbers start at 1. Navigation flags return `yes` or `no`.

## Pending and selected items

| Identifiers | Context |
| --- | --- |
| `pending_world`, `pending_x`, `pending_y`, `pending_z` | Location selected for creation confirmation |
| `selected_floor_name` | Selected floor in the session's floor snapshot |
| `selected_member_name`, `selected_member_texture` | Member selected for a subsequent action |
| `online_player_count` | Candidate-list size; despite the name, the implementation uses the current member candidate snapshot |

## Floor entries

Pattern: `floor_<index>_<field>`, where index is **0 through 20** on the current management page.

| Field | Meaning |
| --- | --- |
| `exists` | `yes` if the entry exists |
| `name` | Custom or automatically numbered floor name |
| `y` | Floor top-block height |
| `number` | Signed number relative to the anchor |
| `current` | `yes` if this is the session's current floor |
| `world` | Stored world name |
| `icon` | Floor icon material |
| `access_mode` | Floor access-mode label |

Example: `%velevators_floor_0_name%`. These indexes are page positions, not database IDs.

## Member entries

Pattern: `member_player_<index>_<field>`, where index is **0 through 35** on the current member page.

| Field | Meaning |
| --- | --- |
| `exists` | `yes` if the entry exists |
| `name` | Player name |
| `texture` | Skin texture hash |
| `relation` | Owner/member/self/non-member label |
| `relation_colored` | Colored owner/member label |
| `member` | `yes` if a stored member |
| `owner` | `yes` if the owner |
| `self` | `yes` if the requester |
| `online` | `yes` if currently connected |
| `connection` | Connected/disconnected label |

The candidate list is session-specific: connected players for addition, stored members for removal/transfer, or owner plus members in the directory. Texture resolution can return a fallback.

## Administrative network entries

Pattern: `admin_network_<index>_<field>`, index **0 through 35** on the current administrative page.

Fields: `exists`, `name`, `icon`, `owner`, `members`, `floors`, `world`, `x`, `y`, `z`, `access_mode`, `id`.

`y` is the anchor height or `?` when no anchor is available. `id` is the stored network ID. `members` and `floors` are counts.

## Audit entries

Pattern: `audit_<index>_<field>`, index **0 through 35** on the current history page.

Fields: `exists`, `action`, `actor`, `details`, `date`, `network`.

`date` uses `dd/MM/yyyy HH:mm` in `Europe/Madrid`; `network` is `-` when there is no network ID. Audit action labels and generated details are not fully localized in this version.

## Session behavior

With no active player session, most values return an empty string and identifiers ending in `_exists` return `no`. Session-bound entries outside the current page similarly return an empty value or `no` for existence.

Open the main interface through `/velevators`, then navigate through the intended actions so the plugin can establish the required context. Opening an internal management/confirmation GUI by ID alone does not create that context.
