# vElevators

[![MIT License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE.md)

Paid, configurable elevator floors for Paper, with vertical travel, shared networks, access controls and editable DeluxeMenus interfaces.

**Current version: 1.1.0** · **Author: Vicevil** · **Artifact: `vElevators-Paper-1.1.0.jar`**

## Features

- Register a solid top block above a redstone block, confirm the purchase and connect floors at matching X/Z coordinates.
- Jump to move up and sneak to move down, with destination checks and a configurable cooldown.
- Manage floor names, icons and access; customize the network name and icon.
- Add or remove members, transfer ownership to an existing member, and switch between public, private and owner-only access.
- Browse floors with 21 entries per page, and member/administrative lists with 36 entries per page.
- Search networks by owner, world or coordinates and view administrative history.
- List, total, collect or archive pending floor payments through permission-controlled commands.
- Store networks, floors, members and administrative changes in SQLite.
- Install 15 editable DeluxeMenus menus for each of 15 locales; register only the selected locale's 15 menus.
- Configure sounds, travel particles and arrival action-bar messages.

## Platform and requirements

This is a **Paper server plugin**, not a client mod or proxy plugin. The descriptor declares `api-version: '1.21'`, and the build targets Java 21 bytecode.

| Server | Java runtime | Recorded validation for 1.1.0 |
| --- | --- | --- |
| Paper 1.21 | Java 21 | Startup and menu registration |
| Paper 1.21.11 | Java 21 | Startup and locale menu switching on restart |
| Paper 26.2 build 119 | Java 25 | Startup and menu registration |

These checks describe specific tested builds, not exhaustive gameplay or visual verification across every Paper release. Paper-compatible forks and Folia have not been validated. The plugin does not declare Folia support.

All of the following plugins are required:

| Dependency | Purpose |
| --- | --- |
| [DeluxeMenus](https://github.com/HelpChat/DeluxeMenus) | Renders all inventory menus |
| [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) | Resolves the bundled `velevators` expansion |
| [LuckPerms](https://github.com/LuckPerms/LuckPerms) | Required permission plugin |
| [Vault](https://github.com/MilkBowl/Vault) | Connects to the server's economy provider |
| A Vault-compatible economy provider | Handles withdrawals, refunds and administrator collections |

The recorded startup environments used DeluxeMenus 1.14.1-Release, PlaceholderAPI 2.12.3, LuckPerms 5.5.71, Vault 1.7.3 and EssentialsX 2.22.0. EssentialsX reported an unsupported-server-version warning on Paper 1.21 and 26.2, so these runs do not establish full economy-provider compatibility on those versions. Select dependency builds that support your server.

SQLite JDBC is bundled in the plugin JAR. SkinsRestorer and the ParseOther expansion are not required. The plugin checks for an enabled dependency set and a registered Vault economy provider during startup.

## Installation

1. Install the dependencies and configure your economy provider.
2. Obtain `vElevators-Paper-1.1.0.jar` through the author's authorized distribution channel.
3. Stop the server, put the JAR in `plugins/` and start the server.
4. Check the startup log for the vElevators enable message and DeluxeMenus reload result.
5. Grant the appropriate permissions. All permissions declared by vElevators default to `false`.

For a normal player group, for example:

```text
lp group default permission set velevators.use true
lp group default permission set velevators.create true
lp group default permission set velevators.manage true
```

For updates, back up the plugin data and customized menus while the server is stopped, replace the old JAR with the new one, and restart. Existing configuration, language files and menu files are preserved; new defaults are not merged into existing files automatically. Compare your configuration with the bundled [config.yml](src/main/resources/config.yml) when upgrading.

## Create and use an elevator

1. Place a `REDSTONE_BLOCK` and put the configured top block directly above it. The default is `QUARTZ_BLOCK`.
2. Run `/velevators` or an alias such as `/elevator`, then choose the add-floor button.
3. Right-click the top block within the selection timeout.
4. Confirm the purchase. The default cost is **10,000 economy units for each floor**.
5. Repeat at another height in the same world, X and Z as the same owner.
6. Stand on a registered top block and jump to go up, or start sneaking to go down.

Each owner/world/X/Z group forms a network. A physical floor location can be registered only once. The first floor is the numbering anchor, `Piso 0`; higher and lower floors receive relative numbers. Custom names survive renumbering. If the anchor is removed, the remaining nearest height becomes the anchor, preferring the lower floor in a tie.

The material used to create a floor is stored with that floor. Changing `elevator.block` affects new registrations. Registered structures must remain intact for travel.

With destination safety enabled, the two blocks above the destination must be passable. Travel preserves yaw and pitch and centers the player above the destination. Restricted destination floors are skipped; an invalid or obstructed selected destination stops travel.

## Network and floor management

Right-click an active top block to open its network. The owner and members need `velevators.manage`; authorized administrators can manage other networks.

| Floor interaction in the management menu | Action |
| --- | --- |
| Left click | Rename through private chat input |
| Right click | Open the floor deletion confirmation |
| Shift + left click | Select a floor icon |
| Shift + right click | Cycle the floor access mode |

The network toolbar provides name/icon customization, owner/member information, member addition/removal, ownership transfer, network access and complete network deletion. Network administration controls are restricted to the owner or authorized administrators. Members with management permission can manage individual floors, including renaming and deleting them.

The add-member menu lists connected players and provides manual name input for offline players. The removal menu lists stored members whether connected or disconnected. Ownership transfer requires an existing member; the former owner becomes a member. An ownership conflict with another network at the same world/X/Z is rejected.

Names reject color codes and control characters. The default limits are 20 Unicode code points for floors and 32 for network names. During a chat-input session, `/cancelar` cancels the operation; disconnecting or allowing the session to expire also ends it. `/cancelar` is handled only during an active input session.

| Access mode | Eligible travelers, also subject to `velevators.use` |
| --- | --- |
| Public | Any player |
| Private | Network owner and members |
| Owner only | Network owner |

Network access and destination-floor access are both checked. `velevators.bypass.access` bypasses these restrictions.

Deleting a floor or an entire network unregisters it, retains its physical blocks and does not refund the purchase price. Registered top/base blocks are protected against normal breaking, piston movement and explosions; `velevators.manage.others` bypasses the normal player-break restriction.

## Commands

The primary command is `/velevators`. Default aliases are `/elevator`, `/elevators` and `/elev`. The same subcommands work through these aliases.

| Command | Purpose | Permission | Sender |
| --- | --- | --- | --- |
| `/velevators` | Open the main menu | `velevators.use` | Player |
| `/velevators reload` | Reload config, selected language and DeluxeMenus | `velevators.reload` | Sender with permission |
| `/velevators adminlog` | Open network administration and audit history | `velevators.adminlog` | Player |
| `/velevators transactions list [page]` | List pending payments, newest first, eight per page | `velevators.admin.transactions.list` | Player or console with permission |
| `/velevators transactions total` | Show pending payment count and total | `velevators.admin.transactions.total` | Player or console with permission |
| `/velevators transactions collect` | Deposit the pending total into the executing player's account, then archive and clear it | `velevators.admin.transactions.collect` | Player |
| `/velevators transactions clear` | Archive and clear pending entries without paying anyone | `velevators.admin.transactions.clear` | Player or console with permission |

The transaction list uses clickable page navigation and hover details for players. `clear` acts immediately; it has no confirmation menu. The reload command checks its permission even for console.

`/velevatorsaction` is the internal DeluxeMenus action command. Its actions require the corresponding session and authorization; it is not a general-purpose command for manually supplying floor coordinates or IDs.

### Permissions

The complete declaration is in [plugin.yml](src/main/resources/plugin.yml).

| Permission | Effect |
| --- | --- |
| `velevators.use` | Main menu and elevator travel |
| `velevators.create` | Register paid floors |
| `velevators.manage` | Manage owned/member networks and their floors |
| `velevators.manage.others` | Manage other owners' networks and bypass normal block breaking protection |
| `velevators.bypass.cost` | Create floors without payment |
| `velevators.bypass.access` | Bypass network/floor travel access |
| `velevators.reload` | Reload configuration, messages and menus |
| `velevators.adminlog` | Global network administration and history; also grants administrative network management in the implementation |
| `velevators.admin.transactions.list` | Read pending payment pages |
| `velevators.admin.transactions.total` | Read the pending total |
| `velevators.admin.transactions.collect` | Collect pending payments |
| `velevators.admin.transactions.clear` | Archive pending payments without a deposit |
| `velevators.admin.transactions.*` | All four transaction permissions |
| `velevators.*` | All permissions declared by the plugin |

## Configuration

The complete defaults are in [src/main/resources/config.yml](src/main/resources/config.yml).

| Setting | Default | Meaning |
| --- | --- | --- |
| `locale` | `en_US` | Language file and active DeluxeMenus locale |
| `command.aliases` | `[elevator, elevators, elev]` | Additional aliases; restart required |
| `elevator.block` | `QUARTZ_BLOCK` | Solid top block for new floors; the redstone base is fixed |
| `economy.creation-price` | `10000.0` | Nonnegative per-floor cost |
| `naming.max-length` | `20` | Floor-name limit |
| `naming.network-max-length` | `32` | Network-name limit |
| `interaction.selection-timeout-seconds` | `60` | Structure-selection timeout |
| `interaction.rename-timeout-seconds` | `60` | Rename-input timeout |
| `interaction.member-timeout-seconds` | `60` | Member-related input timeout |
| `interaction.teleport-cooldown-millis` | `1000` | Travel cooldown |
| `interaction.destination-safety` | `true` | Check the two blocks above the target |
| `menus.auto-install` | `true` | Install missing locale menus and maintain active registrations |
| `sounds.*` | See config | Success/error and directional travel sounds |
| `effects.action-bar.*` | Enabled | Arrival text; `{floor}` is the destination name |
| `effects.particles.*` | Enabled | Up/down/arrival particles and count/offset/speed |

Aliases are trimmed, lowercased and deduplicated. Valid names match `[a-z0-9][a-z0-9_-]*`; do not include `/`. The primary and internal command names are reserved. Existing command conflicts are handled by Paper's command registration; choose different aliases if needed. Reloading does not re-register aliases.

## Languages and DeluxeMenus

Available locales:

`en_US`, `es_ES`, `de_DE`, `fr_FR`, `pt_BR`, `it_IT`, `pl_PL`, `ru_RU`, `tr_TR`, `zh_CN`, `ja_JP`, `nl_NL`, `cs_CZ`, `id_ID`, `ko_KR`.

Message files are installed at `plugins/vElevators/lang/<locale>.yml`. English is the default. Unsupported locale values fall back to `en_US`; missing message keys use bundled defaults.

The installer places all 225 editable menu files on disk:

```text
plugins/DeluxeMenus/gui_menus/vElevators/
├── en_US/  # 15 menu files
├── es_ES/  # 15 menu files
└── ...    # one folder per supported locale
```

Only 15 `velevators_*` entries are registered in `plugins/DeluxeMenus/config.yml`, pointing at the selected locale. Set `locale: es_ES`, for example, and run `/velevators reload` from an account with reload permission to switch paths and reload DeluxeMenus. This reload affects DeluxeMenus as a whole.

The installer preserves existing menu files and backs up DeluxeMenus' configuration before changing its managed file paths. Other menu registrations are retained. Legacy flat files can remain on disk but are not the selected paths. If `menus.auto-install` is disabled, maintain the files and registrations manually.

The menus include the main screen, floor management, creation/deletion confirmations, member selectors/directory, ownership transfer, network/floor icon selectors, network deletion and administrative screens. Materials, custom head textures, sounds, slots, requirements and commands remain editable. Preserve `%velevators_*%` identifiers and the session-bound action wiring when editing visuals.

**Localization limitations in 1.1.0:** static menu text and language-message files are supplied in all 15 locales, but several dynamically generated labels remain Spanish: default floor/network names, access modes, member roles/status and audit descriptions. The arrival action-bar template is separately configured in `config.yml`. Translations have not received native-speaker review, and every locale has not been visually checked in a Minecraft client.

## PlaceholderAPI integration

vElevators registers its own persistent `velevators` expansion. No eCloud download is needed for this expansion.

| Example | Value/context |
| --- | --- |
| `%velevators_price%` | Current configured creation price |
| `%velevators_elevator_block%` | Material required for new floors |
| `%velevators_player_networks%` | Networks owned by the supplied player |
| `%velevators_network_name%` | Current session's network name |
| `%velevators_network_owner%` | Current session's owner name |
| `%velevators_network_member_count%` | Stored member count |
| `%velevators_network_access_mode%` | Current network access label |
| `%velevators_network_access_texture%` | Head texture hash for the access control |
| `%velevators_floor_0_name%` | First floor on the current management page |
| `%velevators_member_player_0_name%` | First entry in the current member selector/directory |

Most placeholders depend on an active menu session and return empty values outside it; existence checks return `no`. Indexed entries refer to the current page, not persistent database IDs. See the [complete placeholder reference](docs/PLACEHOLDERS.md) before building custom menus.

For another DeluxeMenus main menu, use the player command to create the expected session:

```yaml
click_commands:
  - '[player] velevators'
```

## Storage and transaction accounting

| File | Content |
| --- | --- |
| `plugins/vElevators/config.yml` | Server configuration |
| `plugins/vElevators/lang/*.yml` | Editable message translations |
| `plugins/vElevators/elevators.db` | SQLite networks, floors, members and administrative audit |
| `plugins/vElevators/transactions.log` | Pending successful paid registrations |
| `plugins/vElevators/transactions_logs.log` | Archived purchases and `COLLECT`/`CLEAR` operations |

Payment lines use UTF-8 and `dd/MM/yyyy HH:mm Europe/Madrid` timestamps, followed by player name, UUID, amount, world and coordinates. Zero-cost or cost-bypass registrations do not add a paid transaction.

`collect` deposits the sum into the executing administrator's Vault account, archives the pending purchase lines with the administrator identity/time/amount and clears the pending file. `clear` archives without depositing and records the actor/time. The administrative history combines SQLite audit records with parsed permanent payment history; pending payments are inspected using `transactions list`.

Invalid pending lines are reported by the read commands and block collection. `clear` preserves them in the permanent archive with an `UNPARSED` prefix. There is no automatic log rotation.

Creation failures after withdrawal attempt to undo the registration and refund the withdrawal. Collection failures during archiving attempt to withdraw the deposit again. These are compensating operations across Vault, SQLite and files, not a distributed transaction or a guarantee against a process crash. Investigate critical rollback messages and reconcile the economy and logs before retrying an affected operation. Keep backups of the SQLite database, both logs and customized resources.

## Technical structure

| Package | Responsibility |
| --- | --- |
| `command` | Paper command registration, aliases and session-bound actions |
| `config` | Settings validation and language loading |
| `data` / `model` | SQLite persistence and stored/view records |
| `listener` | Interaction, chat input and structure protection |
| `menu` / `session` | DeluxeMenus installation, navigation and player context |
| `placeholder` | PlaceholderAPI expansion |
| `service` | Elevator operations, payment accounting and player textures |
| `util` | Naming, materials, colors and access helpers |

The descriptor is a Bukkit-style `plugin.yml`, while command registration and events use Paper APIs. Scheduling uses the Bukkit scheduler. There is no documented public Java API or plugin messaging protocol in 1.1.0; internal classes are implementation details.

## Troubleshooting and support

- **Plugin disabled on startup:** check the dependency names, enabled state and Vault economy registration in the log.
- **Commands denied, including as operator/console:** all declared permissions default to false; grant the specific permission.
- **An old menu remains after changing locale:** verify `menus.auto-install: true`, the selected locale, the `velevators_*` file paths and the DeluxeMenus reload log. Reopen the menu afterward.
- **Head textures show a fallback:** profile resolution can fail or time out. The implementation reads online profiles or resolves official profiles by name, caches results and uses a Steve fallback.
- **Unresolved placeholders in a custom menu:** confirm PlaceholderAPI registered `velevators`, use the expected player session, and preserve the original placeholder/action identifiers.
- **DeluxeMenus warns about NMS/legacy options:** use a DeluxeMenus build suited to the server and review its warnings; a successful plugin enable does not verify every menu item visually.

Report issues at [vicevilz/vElevators/issues](https://github.com/vicevilz/vElevators/issues), including server/Java/dependency versions, reproduction steps and relevant logs. Remove private server information before sharing.

## License

[MIT License](LICENSE.md) · Copyright (c) 2026 Vicevil.
