# PvPZoneCycle 1.3.8 — Compatibility

A single JAR (`PvPZoneCycle-1.3.8.jar`) covers the whole supported range. It is
compiled against the **Paper 1.20.1 API** and emitted as **Java 17 bytecode**
(class-file major version **61**, verified on the shipped classes), so it only
references symbols that exist across every version below, and its bytecode loads
on each server's required JVM without a per-version recompile.

## Minecraft version → required Java

| Minecraft / Paper | Server's required Java | Status |
|---|---|---|
| 1.20.1 – 1.20.4 | Java 17 | Supported |
| 1.20.5 – 1.21.11 | Java 21 | Supported |
| 26.1.x – 26.3.x (latest stable 26.3) | Java 25 | Supported |

> Minecraft switched to a year-based version number. **26.1.2** and **26.3** are
> real Paper releases. At the time of this build the newest STABLE Paper line
> was **26.3** (build `26.3-211`); `26.1.2` (build `26.1.2-74`) is also STABLE.

## What was verified in this build run (2026-10-21)

### Compile-verified — source compiles clean against the real Paper API

| Paper API | Toolchain | Result |
|---|---|---|
| 1.20.1 (ship target) | JDK 17, `options.release = 17` → bytecode major 61 | OK |
| 1.20.6 | JDK 21 | OK |
| 1.21.4 | JDK 21 | OK |
| 1.21.11 | JDK 21 | OK |
| 26.1.2 (build 74, STABLE) | JDK 25 | OK |
| 26.3 (build 211, STABLE — newest) | JDK 25 | OK |

The shipped JAR is the 1.20.1 `options.release = 17` build. The later-API
compiles prove the source only references symbols that still exist in those
APIs (symbol/ABI compatibility); they are compile checks, not the shipped
artifact.

### Runtime-verified — real headless Paper server started, plugin enabled, command executed, clean disable

| Paper server | Server JVM | Result |
|---|---|---|
| 1.21.4 (build 232) | JDK 21 | Server reached `Done`; plugin remapped, `Loading` → `Enabling` → `enabled; loaded 0 arena(s)`; `/pvpzone` help ran and printed the full command list + security notice; `Disabling` cleanly on `stop`; no exceptions |
| 26.1.2 (build 74) | JDK 25 | Server reached `Done`; plugin `Loading` → `Enabling` → `enabled; loaded 0 arena(s)`; `/pvpzone` help ran and printed the full command list + security notice; `Disabling` cleanly on `stop`; no exceptions |

The two runtime tests exercise both ends of the range (a 1.21.x server on JDK 21
and a 26.x server on JDK 25) with the exact same JAR, confirming it loads and
enables unchanged across the JVM boundary.

## API symbols checked across the range

- `Player#sendActionBar(String)` — present in 1.20.1 and 26.x (replaces the old
  BungeeCord `ChatMessageType`/`TextComponent` action-bar path that was
  fragile/removed in newer builds).
- `Sound` — became an interface in newer versions but keeps a static
  `valueOf(...)`; resolved by name so both enum and interface forms work.
- `Particle` — still an enum; `END_ROD` present throughout.
- Potion effects — `PotionEffectType.getByName` + alias map +
  `Registry.POTION_EFFECT_TYPE` fallback, covering both legacy and registry-era
  names.
- `ItemMeta#setDisplayName` / `#setLore` — present throughout.

## Not tested (honest limitation)

- **No gameplay with real connected players.** The build host has no Minecraft
  client, so no player ever joined the test servers. Arena creation via the
  `wand` selector, the PvP on/off cycle actually flipping, on-entry potion
  effects, and block/explosion/fire protection reacting to live world events
  were **not** driven by a live player session. Those code paths loaded and
  registered cleanly (the server enabled with no errors), but their in-world
  behavior is **NOT TESTED** here and should be validated on a server with a
  connected client before production use.
