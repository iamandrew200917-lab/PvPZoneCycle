# PvPZoneCycle 1.3.8 — Security Review

_Scope: the server-side plugin source in `com.gradercraft.pvpzonecycle`. This
review focuses on the privilege-escalation issue that motivated the 1.3.8
"security update" and documents the current access-control posture._

## 1. Headline fix — OP self-restoration loophole closed

**Risk (pre-1.3.8):** earlier revisions carried an OP "test / back" workflow
that de-OPped an operator and stored a *pending restore* marker in
`config.yml`. Because the marker survived disconnects and restarts, a player who
had been intentionally **demoted** could potentially use the player-side
`back` command to restore their own OP — a privilege-escalation / persistence
hole.

**Fix in 1.3.8:**

- `/pvpzone back` and `/pvpzone op back` are hard-disabled. The handler returns
  a message stating no player command can grant or restore OP, and performs no
  OP mutation (`PvPZoneCycle.java`, `op`/`back` branch).
- `/pvpzone test` is diagnostics-only: it reports version / arena count / config
  availability and never changes OP or writes a restore token.
- On enable, any legacy `op-test.pending` configuration section is deleted and a
  warning is logged:
  `Cleared legacy OP-test restore markers. Player-side OP restoration is
  disabled for security.`

**Result:** the plugin now has **no code path that grants, restores, or
escalates OP**. OP changes must be performed from the server console by the
owner. Verified indirectly at runtime: on both test servers the `/pvpzone` help
output ends with the security line
`Security: /pvpzone back and /pvpzone op back never grant OP.`

## 2. Access control / permission model

| Capability | Gate |
|---|---|
| Arena administration, selector `wand`, create/delete/save/reload, effects, timers, coords, set | `pvpzone.admin` **or** `isOp()` |
| Enable block protection (`/pvpzone protect`) | `pvpzone.protect` **or** admin/OP |
| Diagnostics (`/pvpzone test`) | `pvpzone.test` **or** admin/OP |
| Read-only (`help`, `info`, `list`, `status`) | everyone |

All three permissions default to `op` in `plugin.yml`. Admin command handlers
re-check the permission at execution time (not only via `plugin.yml`), so a
mis-set permission node cannot silently open an admin command to everyone.

## 3. Audit logging

Authorized administrative commands are logged to the server console as:

```
[ADMIN-AUDIT] actor=<name/uuid> command=/pvpzone <text>
```

This gives the operator an after-the-fact trail of who changed arena/config
state. (Log retention/rotation is the server operator's responsibility.)

## 4. Data-handling safety

- **Persistence with rollback:** `saveArenas()` writes a `config.yml.bak` backup
  before saving and logs a `severe` message on failure. Mutating commands
  (`protect`, `coords`, `delete`, timer/effect edits) roll back the in-memory
  change if the save fails, so on-disk and in-memory state stay consistent.
- **Input validation:** arena names are validated against a bounded regex
  (tested in `PvPZoneRulesTest`); Y-boundary edits are range-checked (upper must
  not drop below lower and vice-versa); unknown sounds/effects are caught and
  logged rather than crashing the handler.
- **No dynamic code / reflection of untrusted input, no SQL, no file paths taken
  from user input, no outbound network calls.** The plugin only reads/writes its
  own `config.yml` under the plugin data folder.
- Potion-effect and sound resolution use name-based lookups with registry
  fallbacks — no unchecked casts that would let malformed config crash enable.

## 5. Command-namespace hygiene

`plugin.yml` registers **only** the `pvpzone` command. It does not claim broad
or shared names (e.g. `//help`, `help`, `back`), so it will not collide with or
shadow commands from other plugins such as WorldEdit.

## 6. Residual risk / NOT covered by this review

- **Runtime gameplay not exercised with a real player** (no client on the build
  host). The protection/anti-grief handlers and the permission denials as
  experienced by a non-op player loaded without error but were not driven by a
  live session — validate on a staged server before production. See
  `BUILD_TEST_REPORT.md`.
- This review does not cover the security of the host server, other installed
  plugins, or the operator's own OP-granting practices.

## Summary

The privilege-escalation loophole that defined the 1.3.8 security update is
closed: no player-facing command can grant or restore OP, legacy restore
markers are purged on startup, admin actions are audited, and persistence is
rollback-safe. The remaining gap is live in-game validation, which requires a
Minecraft client the build host does not have.
