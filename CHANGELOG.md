# Changelog — PvPZoneCycle

All notable changes to this plugin. Versions follow the project's own numbering.

## [1.3.8] — 2026-10-21 — Security update

### Security

- **Removed the player-side OP self-restoration loophole.** `/pvpzone back` and
  `/pvpzone op back` are now explicitly disabled and can never grant or restore
  OP. A clear message tells the user that no player command can grant OP and
  that OP changes must be made from the server console.
- **`/pvpzone test` is now diagnostics-only.** It reports plugin version, loaded
  arena count, and config availability. It no longer de-OPs the operator or
  creates any restore token.
- **Legacy restore markers are purged on startup.** Any old
  `op-test.pending` section left in `config.yml` is cleared at enable time, with
  a warning logged, so stale config data cannot be used to regain OP through the
  plugin after a demotion.
- **Admin audit logging.** Authorized arena/config administration commands are
  written to the server console as `[ADMIN-AUDIT] actor=… command=…`, recording
  who ran which administrative command.

### Added

- `/pvpzone info` — reports plugin state, loaded/protected arena counts, a
  protection summary, and OP-safety status.
- `/pvpzone coords <arena> y up|down <Y>` (alias `/pvpzone coordinates …`) to set
  arena vertical boundaries, with range validation (upper must not fall below
  lower) and automatic persistence with rollback on save failure.
- `pvpzone.test` permission (default `op`).
- Per-player, per-arena entry notification prompting griefing-prone arenas to
  enable block protection, with quit cleanup.
- `/pvpzone protect <arenaName>` handling via the `pvpzone.protect` permission,
  with already-protected / not-found / usage feedback, persistence, and rollback
  on save failure.
- `TNTPrimeEvent` handling: TNT priming inside a protected arena is cancelled
  (and the TNT block removed) while PvP is OFF.
- Environmental protection handlers: block spread, fluid flow, block fade / leaf
  decay, pistons, and Enderman block changes within protected arenas.
- A JUnit 5 test suite (`PvPZoneRulesTest`, 13 tests) covering name validation,
  time formatting, alias resolution, and cuboid containment.
- A committed Gradle wrapper (8.10.2) so the project builds reproducibly with
  `./gradlew build`.

### Changed

- Extracted pure decision logic into `PvPZoneRules.java`; the main plugin class
  now delegates to it. This is a behavior-preserving refactor that makes the
  core rules unit-testable — no gameplay behavior was altered to force the
  build/tests green.
- Explosion handling keeps events active and filters affected blocks
  individually by location, so explosions still affect entities and unprotected
  terrain while protected arena blocks survive.
- Protected-block checks honor the full configured X/Y/Z cuboid (both lower and
  upper Y bounds).
- Fire ignition, spread, and burn prevention apply regardless of PvP state.
- Plugin metadata, config messages, README, and compatibility notes updated to
  1.3.8.

### Build / verification (this release)

- Builds to a real `PvPZoneCycle-1.3.8.jar` (Java 17 bytecode, major 61).
- Compiles clean against Paper 1.20.1 / 1.20.6 / 1.21.4 / 1.21.11 / 26.1.2 /
  26.3.
- Loads and enables on headless Paper 1.21.4 (JDK 21) and 26.1.2 (JDK 25);
  `/pvpzone` responds; clean disable.
- In-game gameplay with a connected player is **NOT TESTED** (no Minecraft
  client on the build host). See `BUILD_TEST_REPORT.md`.
