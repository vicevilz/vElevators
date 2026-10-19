# Third-party and earlier licensing notices

The [vElevators license](LICENSE.md) applies to the Author's original work only,
subject to earlier grants. It does not change the licenses of dependencies,
tools, APIs or server plugins. This file is an inventory of directly relevant
components, not a legal clearance or a complete transitive dependency audit.

## Earlier public vElevators revisions

The initial public source commits `fc173213f0c42379157c082bf534f381f546a347` and
`1b5ebc1a183c56bedfe0e1066e13f6ffc041b8cd` included the MIT License. The complete
original notice is retained in [licenses/LEGACY-MIT.txt](licenses/LEGACY-MIT.txt).
Previously granted rights are unaffected by the current commercial terms.

## Bundled runtime dependency

- **Xerial SQLite JDBC 3.50.3.0** is included in the plugin JAR. Its upstream
  distribution includes Apache License 2.0 and a separate `LICENSE.zentus`
  notice for inherited code. Those notices were found in the existing JAR at
  `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE` and
  `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE.zentus`. Preserve those notices
  when packaging or distributing that component.
  [Upstream license](https://github.com/xerial/sqlite-jdbc/blob/3.50.3.0/LICENSE)
  · [Inherited-code notice](https://github.com/xerial/sqlite-jdbc/blob/3.50.3.0/LICENSE.zentus).

## API dependencies and required server plugins

The build declares the following API dependencies as `compileOnly`; their JARs
are not included in the vElevators runtime JAR by the current build:

| Component | Declared version | Upstream licensing reference |
| --- | --- | --- |
| Paper API | `1.21.1-R0.1-SNAPSHOT` | Paper inherits upstream GPL licensing and identifies some MIT contributions: [Paper licensing](https://github.com/PaperMC/Paper/blob/main/LICENSE.md) |
| VaultAPI | `1.7` | [GNU LGPL v3](https://github.com/MilkBowl/VaultAPI/blob/master/license.txt) |
| PlaceholderAPI | `2.12.3` | [GNU GPL v3](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/LICENSE) |

DeluxeMenus, LuckPerms, Vault, PlaceholderAPI, Paper and the economy provider are
separately installed software. Their own licenses and distribution requirements
remain applicable; no rights in those projects are granted by vElevators.

Not bundling an API does not by itself settle whether a combined distribution
or dynamic integration is subject to copyleft obligations. The Free Software
Foundation discusses this distinction in its
[GPL plugin FAQ](https://www.gnu.org/licenses/gpl-faq.html#GPLAndPlugins).
Compatibility of this plugin's commercial restrictions with its particular
runtime integrations has not been legally established. Obtain any necessary
permissions or advice before relying on those restrictions for distribution.

## Build tools and test dependencies

The Gradle Wrapper retains its upstream notices and is separately licensed by
Gradle; it is not relicensed under the vElevators commercial terms.
[Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE).

JUnit, AssertJ, Mockito, SnakeYAML and their dependencies are used by the tests.
They are not added to the plugin JAR through the current runtime configuration.
Their upstream terms continue to apply to their respective components.
