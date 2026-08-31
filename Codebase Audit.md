# Whole-Codebase Feature, Correctness, and Efficiency Audit

## Summary

Audit all first-party SiegePlugin code and produce:

- `docs/IMPLEMENTED_FEATURES.md`: the factual catalog of every player, administrator, and automatic capability.
- `docs/CODEBASE_AUDIT_FINDINGS.md`: all intent discrepancies, bugs, cross-module hazards, inefficiencies, architectural debt, documentation drift, and verification gaps.
- `tools/capture-beta-evidence.sh`: a read-only helper for collecting reproducible beta-performance evidence outside Git.

No gameplay fixes, refactors, configuration changes, database migrations, or production deployments occur during this audit.

```text
Freeze baseline → Capability census → Static/code review → Automated evidence
       → Owner intent review → Isolated live testing → Findings/report finalization
                                      ↓
                         Next beta performance addendum
```

## Audit Method

- Freeze the Git SHA and dirty state, JDK/Maven/Paper versions, effective POM, dependency tree, resolved dependency checksums, source configuration, and relevant runtime versions.
- Define capabilities by observable user/operator outcome, not by Java class. Assign stable IDs such as `CAP-ROUND-001`.
- Account for every production class, command/subcommand, permission, config key, listener, scheduled task, database table/index/query family, integration, lifecycle hook, and test by mapping it to a capability ID or an explicit internal/excluded entry.
- Give each capability separate status fields:
  - Code present
  - Reachable
  - Config enabled
  - Automated verified
  - Local runtime verified
  - Beta verified
  - Production accepted
- Trace the main cross-module flows: startup/shutdown/restart, joining and teams, spectator/lobby transitions, rotation, capture/scoring/currency/stats, death, combat restrictions, calibration during live rounds, world copying, potion storage, kits, shop, and minecarts.
- Review every capability group with the owner as `intended`, `unintended`, or `unclear`. Keep that separate from `technically correct`, `defective`, and `accepted risk`; intended behavior can still contain a bug.
- Preserve raw analyzer and runtime output under an untracked `target/audit/<run-id>/` bundle. Embed a manifest of commands, versions, timestamps, environment, exit codes, paths, and hashes in the findings report.

## Analysis and Verification

- Run a clean Maven baseline and final regression suite. The current reference baseline is 273 passing tests at commit `57fb829`.
- Collect capability-level coverage with JaCoCo; do not use one aggregate percentage as proof of readiness.
- Run Java 21-compatible SpotBugs, PMD/CPD, Maven dependency analysis, compiler warnings, and `jdeps` using fully qualified, version-pinned audit invocations without changing `pom.xml`.
- Run ShellCheck on owned scripts, inspect `deploy.sh --dry-run`, verify the shaded JAR contents, and examine quoting, temporary-directory cleanup, remote paths, secret exposure, and failure recovery.
- Review:
  - Main-thread blocking work and Paper API use from asynchronous callbacks
  - Scheduler/task/listener shutdown and stale callback handling
  - Repeated scans, allocations, duplicated validation, and excessive coupling
  - State ownership and invariants across round, calibration, storage, scoring, and player-state modules
  - SQLite transactions, indexes, migrations, and query plans
  - Permission and Towny/CombatLog integration boundaries
- On a copied database, run `PRAGMA integrity_check`, foreign-key validation, schema invariants, and `EXPLAIN QUERY PLAN` against representative sanitized data.
- Convert every failed tool, unsupported analyzer, missing test, or unavailable prerequisite into an explicit evidence gap rather than silently skipping it.

## Runtime and Beta Evidence

- Use a disposable clone of the local Paper server with isolated ports, world container, plugin data, database, configs, runtime overrides, and map templates.
- Record before/after hashes for Iron Mountain templates, potion-storage definitions, and persistent plugin data. Calibration and rotation must never point at the original data.
- Enable two valid maps only inside the clone and run the existing four-fighter, one-spectator acceptance guide, including restart, recovery, stale-copy, combat, storage, and calibration/live-round scenarios.
- Profile that run with Paper’s bundled spark profiler and capture tick time, task hotspots, heap/GC, SQLite activity, entity counts, and map lifecycle timings. Missing observability becomes a finding rather than prompting instrumentation during the audit.
- The beta helper will:
  - Accept an explicit staging-server directory and external evidence directory.
  - Capture safe version/config checksums, JVM/GC data, Spark references, SiegePlugin warnings/errors, and before/after resource measurements.
  - Never start, stop, modify, or deploy the server.
  - Keep raw logs outside Git and warn that they may contain player identifiers.
- At the next beta, run the documented 20-minute Spark profile and record actual player count and workload. Only a 25-player run can satisfy the existing capacity gate of TPS ≥19.5, tick p95 ≤40 ms, and p99 ≤50 ms. Smaller tests remain useful evidence but are labeled below-capacity.
- Publish the static and five-player audit first; append beta results later instead of withholding both reports.

## Report Contract and Acceptance

`IMPLEMENTED_FEATURES.md` will contain the architecture diagram, capability catalog, player/admin flows, commands and restrictions, configuration, persistence, integrations, status axes, test evidence, owner decisions, and a mechanical coverage appendix proving every implementation surface was accounted for.

`CODEBASE_AUDIT_FINDINGS.md` will use stable finding IDs and include category, P0–P3 severity, confidence, exact code/runtime evidence, reproduction or reasoning, player/operator impact, affected capability and modules, cross-module blast radius, recommendation, effort, verification method, and disposition.

- P0: data loss, security compromise, or server-wide failure.
- P1: major gameplay corruption, persistent-state damage, or release-blocking cross-module defect.
- P2: localized correctness problem or material performance/maintenance risk.
- P3: low-risk cleanup, documentation drift, or test gap.
- Low-confidence P0/P1 hypotheses remain in the main report with the missing evidence and next verification step. Only low-impact uncertain observations go in the appendix.
- Release recommendation requires no open P0/P1 findings and runtime verification for every acceptance-critical capability.
- Disabled or unaccepted features, including the current map pool, must never be described as production-ready merely because their code exists.
- Fixes are deferred to a separately approved remediation plan, prioritized from the findings report.

## Assumptions and Exclusions

- Reference code, bundled world data, generated build output, and live-server files are supporting evidence, not first-party modules.
- Production and original potion/map data remain untouched.
- Third-party plugins are audited only at their SiegePlugin integration boundary, version/checksum level, and observed runtime behavior.
- The audit will not rewrite the clean-copy loader or potion-storage behavior.
- All owner capability decisions are required; beta capacity evidence may remain explicitly pending in the initial report.
