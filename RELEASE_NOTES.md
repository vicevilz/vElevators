# vElevators 1.1.0 — Localized Menus

Suggested tag: `v1.1.0`

Suggested asset: `vElevators-Paper-1.1.0.jar`

## Changes

- Added 15 DeluxeMenus files for each of the 15 supported locales.
- Added locale-aware menu registration: only the selected language's 15 menus are registered in DeluxeMenus.
- Preserved the Spanish menu set and existing customized menu/language files.
- Added configuration backups before updating the managed DeluxeMenus file paths.
- Retained configurable command aliases, paid floors, network/member/access management, transaction accounting and SQLite persistence.

## Installation

Requires Paper and DeluxeMenus, PlaceholderAPI, LuckPerms, Vault and a Vault-compatible economy provider. Use Java 21 for the recorded Paper 1.21/1.21.11 environments and Java 25 for Paper 26.2.

Back up data and menus, stop the server, replace the plugin JAR and restart. Set `locale` in `plugins/vElevators/config.yml`; use `/velevators reload` with its permission to update the selected language and menu registrations. Command alias changes require a restart.

## Validation and limitations

Recorded startup/menu-registration checks cover Paper 1.21, 1.21.11 and 26.2 build 119. They do not represent exhaustive gameplay or visual verification. The existing EssentialsX 2.22.0 test dependency reported unsupported-server-version warnings on 1.21 and 26.2.

Dynamic names, access/member labels and audit descriptions are still partly Spanish. Translations have not received native-speaker review. See the README for the complete requirements and behavior.

Distribution is managed by the author. This repository does not publish downloadable plugin artifacts automatically.
