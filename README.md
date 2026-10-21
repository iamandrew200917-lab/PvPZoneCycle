# PvPZoneCycle 1.3.8 — Smart Arena Protection & Anti-Grief System

Paper/Spigot plugin for named cuboid PvP arenas with timed PvP on/off cycles, arena effects, block protection, explosion protection, and fire prevention.

- **Developer:** Grader Clients
- **Telegram:** https://t.me/Grader_Clients
- **Package:** `com.gradercraft.pvpzonecycle`
- **Version:** `1.3.8`
- **Target API:** Paper 1.20.1+ (compiled against Paper API 1.20.1; confirm other server forks/versions with their API)
- **Java:** 17 bytecode

> This update was built from the supplied 1.3.4 source archive. It is not based on an unavailable 1.3.7 source tree.

## Installation

1. Build the project with JDK 17+ using the bundled Gradle wrapper: `./gradlew build` (Windows: `gradlew.bat build`).
2. Copy `build/libs/PvPZoneCycle-1.3.8.jar` into the server's `plugins/` directory.
3. Start/restart the server and configure arenas with `/pvpzone`.

A prebuilt `PvPZoneCycle-1.3.8.jar` is also included with this delivery.

## Quick start

1. `/pvpzone wand` — obtain the selector tool (default golden axe).
2. Left-click and right-click two corner blocks.
3. `/pvpzone create Arena1` — create the arena.
4. `/pvpzone list` or `/pvpzone status Arena1` to inspect it.
5. `/pvpzone protect Arena1` enables persistent block protection.

## Commands

| Command | Access | Description |
|---|---|---|
| `/pvpzone help` | Everyone | Show help |
| `/pvpzone info` | Everyone | Show plugin version, arena/protection counts, and OP-safety status |
| `/pvpzone test` | `pvpzone.test` / OP | Diagnostics only; does not change OP status |
| `/pvpzone coords <arena> y up <Y>` | `pvpzone.admin` / OP | Set arena upper Y boundary and save it |
| `/pvpzone coords <arena> y down <Y>` | `pvpzone.admin` / OP | Set arena lower Y boundary and save it |
| `/pvpzone list` | Everyone | List arenas |
| `/pvpzone status <name>` | Everyone | Show PvP state and timer |
| `/pvpzone wand` | `pvpzone.admin` / OP | Get arena selector |
| `/pvpzone create <name>` | `pvpzone.admin` / OP | Create arena from selection |
| `/pvpzone protect <arenaName>` | `pvpzone.protect` or admin / OP | Enable persistent block protection |
| `/pvpzone unprotect <name>` | `pvpzone.admin` / OP | Disable block protection |
| `/pvpzone delete <name>` | `pvpzone.admin` / OP | Delete arena |
| `/pvpzone save` | `pvpzone.admin` / OP | Save arena data |
| `/pvpzone effect <arena> <effect> <seconds> [level]` | `pvpzone.admin` / OP | Add an entry effect |
| `/pvpzone effects <arena>` | `pvpzone.admin` / OP | List entry effects |
| `/pvpzone effectremove <arena> <effect>` | `pvpzone.admin` / OP | Remove an entry effect |
| `/pvpzone ontime <arena> <seconds>` | `pvpzone.admin` / OP | Set PvP ON duration |
| `/pvpzone offtime <arena> <seconds>` | `pvpzone.admin` / OP | Set PvP OFF duration |
| `/pvpzone set <tool\|on\|off\|effects> <value>` | `pvpzone.admin` / OP | Change plugin defaults |
| `/pvpzone reload` | `pvpzone.admin` / OP | Reload configuration and arenas |

## Permissions

- `pvpzone.admin` — arena administration and selector, default `op`.
- `pvpzone.protect` — enable block protection with `/pvpzone protect`, default `op`. Administrators and operators also pass the existing admin check.

## Protection behavior

- **PvP OFF:** TNT priming inside protected arena space is cancelled and the TNT block is removed; explosion block damage is filtered by each affected block's location.
- **PvP ON:** TNT and explosions can still occur; protected arena blocks are removed from the explosion's affected block list so combat explosions can continue without terrain destruction.
- **Both states:** Fire ignition, spreading and burning are blocked. Additional environmental protection covers block spread, liquid flow into/out of protected space, natural block fade/leaf decay, pistons, and Enderman block changes.
- **Boundaries:** player PvP/entry checks and protected block checks use the configured cuboid X/Y/Z bounds. Use `/pvpzone coords <arena> y up <Y>` and `/pvpzone coords <arena> y down <Y>` to adjust the vertical limits; changes are saved automatically.
- **Admin bypass:** the existing admin bypass for player block break/place and bucket protection remains.
- **Notifications:** players entering an unprotected arena receive the configurable hint messages once per entry. Re-entering shows the hint again.
- **Persistence:** arena protection is stored with the existing `arenas.<name>.protected` setting in `config.yml`.

## Configuration

New configurable message keys are included in `src/main/resources/config.yml`:

```yaml
messages:
  prefix: '&8[&bPvPZone&8] '
  protection-hint: '&eWant to protect this Arena from griefing?'
  protection-hint-command: '&7Enable block protection using:'
  protection-hint-usage: '&b/pvpzone protect <arenaName>'
  protection-hint-example: '&7Example: &f/pvpzone protect {arena}'
```

The notification replaces `<arenaName>` or `{arena}` with the current arena name.

## Build and verification

From the project root:

```bash
./gradlew clean test build
```

The build output is `build/libs/PvPZoneCycle-1.3.8.jar` (Java 17 bytecode, class-file major version 61). A committed Gradle wrapper (8.10.2) is included for reproducible builds. `BUILD_TEST_REPORT.md`, `COMPATIBILITY.md`, and `SECURITY_REVIEW.md` record exactly what was built, tested, and left untested.

## Branding

Grader Clients · https://t.me/Grader_Clients


### Diagnostics and OP safety (security update)

- `/pvpzone test` reports plugin version, loaded arena count, and config availability. It is diagnostics-only and never removes or grants OP.
- `/pvpzone info` reports plugin state, loaded/protected arena counts, a protection summary, and OP-safety status.
- `/pvpzone back` and `/pvpzone op back` are deliberately disabled and never grant OP. OP changes must be performed by the server owner or console after verifying the player's access.
- Legacy pending OP-test restore markers are cleared on startup so old config data cannot be used to regain OP through the plugin.
- This avoids a self-restoration loophole where a former administrator could retain a stale restore flag after being demoted.

### Arena Y boundaries

- `/pvpzone coords <arena> y up <Y>` changes the upper Y boundary.
- `/pvpzone coords <arena> y down <Y>` changes the lower Y boundary.
- `/pvpzone coordinates ...` is an alias. Changes are saved automatically and apply to arena membership and configured region protection. The upper bound must not be below the lower bound, and vice versa.
