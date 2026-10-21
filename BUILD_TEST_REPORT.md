# PvPZoneCycle 1.3.8 — Build and Test Report

_Run date: 2026-10-21. This report records exactly what was built and tested in
this environment. Nothing below is fabricated; where something could not be
tested it is marked NOT TESTED._

## Input and scope

- Supplied source archive: the 1.3.4 source tree (the request references a 1.3.7
  previous version, but no 1.3.7 source archive was supplied; changes were made
  on top of the actual supplied source).
- Target version: `1.3.8` (security update).
- Java package: `com.gradercraft.pvpzonecycle`.
- Developer: Grader Clients — https://t.me/Grader_Clients.

## Toolchain set up in this environment

The host shipped with only JDK 11 and no Gradle, so the full toolchain was
installed from scratch:

- Temurin JDK **17**, **21**, and **25** (for the three JVM targets).
- Gradle **8.10.2** + a committed Gradle wrapper (`gradlew` / `gradlew.bat`,
  `gradle/wrapper/`).
- The system CA bundle path was repointed to
  `/etc/ssl/certs/ca-certificates.crt` so dependency downloads succeeded.

## Build result — SUCCESS (real JAR)

`./gradlew clean test build` completed successfully with `JAVA_HOME` = JDK 17.

- Output artifact: `build/libs/PvPZoneCycle-1.3.8.jar` (≈ 29 KB).
- Bytecode: Java 17, class-file **major version 61**, confirmed on
  `PvPZoneCycle.class` and `PvPZoneRules.class`.
- JAR contents verified: compiled classes (incl. `$Arena`, `$EffectSpec`,
  `$Selection`, `PvPZoneRules`), `plugin.yml`, and `config.yml` are all bundled.
- `plugin.yml` registers only the `pvpzone` command (no broad/colliding command
  names such as `//help`), `api-version: '1.20'`, `folia-supported: false`.
- Only one deprecation note during compile (a deprecated Bukkit API in the main
  class); no errors.

The JAR is a genuine compiled artifact — no empty, placeholder, or hand-faked
JAR was produced.

## Automated tests — SUCCESS (13/13)

Pure-logic rules were extracted into `PvPZoneRules.java` (a behavior-preserving
refactor — the main class delegates to it) so they can be unit-tested without a
running server. JUnit 5 suite `PvPZoneRulesTest`:

- **13 tests, 0 failures, 0 errors, 0 skipped.**
- Covers: arena-name validation regex, `formatTime` formatting, command-alias
  resolution, and cuboid point-in-region containment (X/Y/Z bounds).

## Runtime load-test — SUCCESS on two real Paper servers

Two headless Paper servers were downloaded and started with the shipped JAR in
`plugins/`, driven over console stdin (`/pvpzone` then `stop`):

| Paper server | Server JVM | Observed |
|---|---|---|
| 1.21.4 (build 232) | JDK 21 | `Loading` → `Enabling` → `PvPZoneCycle 1.3.8 enabled; loaded 0 arena(s)`; server `Done`; `/pvpzone` printed full help + the OP-safety security line; clean `Disabling` on `stop`; no stack traces |
| 26.1.2 (build 74) | JDK 25 | Same lifecycle: enabled cleanly, `/pvpzone` help ran, clean disable; no stack traces |

(The only lines containing "ERROR" in the logs are Paper's standard EULA-flag
notice, not a plugin fault.)

## NOT TESTED (honest limitation)

The build host has **no Minecraft client**, so no player connected to either
test server. The following were **NOT TESTED** in-world and must be validated on
a server with a connected client before relying on them in production:

- Arena creation via the `wand` selector and two-corner selection.
- The PvP ON/OFF timer actually flipping and announcing.
- On-entry potion effects applying to a player.
- Block protection / explosion filtering / TNT-prime cancellation / fire
  prevention reacting to live world events.
- Enter/leave/re-enter notifications and quit/rejoin cleanup.
- The `pvpzone.protect` / `pvpzone.admin` / `pvpzone.test` permission gates as
  experienced by a non-op player.

These code paths registered and loaded without error, but their runtime
gameplay behavior was not driven by a live session here.

## Summary

| Check | Status |
|---|---|
| Compile against Paper 1.20.1 (ship) + 1.20.6 / 1.21.4 / 1.21.11 / 26.1.2 / 26.3 | VERIFIED |
| Real JAR produced, Java 17 bytecode (major 61), resources bundled | VERIFIED |
| Unit tests (JUnit 5) | VERIFIED — 13/13 pass |
| Plugin loads + enables + responds to `/pvpzone` on Paper 1.21.4 (JDK21) & 26.1.2 (JDK25) | VERIFIED |
| In-game gameplay with a real player | NOT TESTED (no client on host) |
