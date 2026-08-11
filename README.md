# visualR Java Runtime (java-runtime)

Java/JVM orchestration layer for visualR (/mnt/d/visualR/visualR) —
post-0.5 runtime reserve per `DEVELOPMENT_PLAN_v0.5.0.md` §8.

## Roles

| Layer | Role |
|-------|------|
| R (`visualR` package) | authoritative semantics / reference implementation |
| Java (`java-runtime`) | execution & orchestration fabric: PAL codec mirror, ABI v0.1 pipeline, kernel registry, mapping pack, projections, R-worker scheduling, cache |

Rule: **Java never redefines semantics.** Every behavior mirrors R; equivalence is
enforced by tests that byte-match R output via `Rscript` + `pkgload`.

## Topology Operator ABI v0.1 (frozen 2026-08-08)

```
TopologyCarrier -> Snapshot -> Concurrent Lanes -> Barrier -> Reconcile -> Commit
```

Implemented in `io.visualr.runtime`:
- `PalState` / `PalCodec` — PAL v0.2 record format (length-prefixed, `\x1f` separated, no eval)
- `TopologyCell` / `TopologyCarrier` / `Snapshot` — ABI §2.1 / §2.2 / §5.2
- `LaneKernel` / `LaneKernelRegistry` — 5 built-in kernels (identity/complement/mirror/rotate/gamma)
- `MappingPack` / `MappingPackRegistry` — frozen pal-jiugong-v0.2 pack
- `PalProjection` — jiugong (strict S4→3×3), square view, mirror_addr (Σ²=I)
- `TopologyOperator` — lanes (concurrent), barrier, reconcile, commit (fail-closed), cell→PAL, full pipeline

Orchestration (`io.visualr.orchestration`):
- `RWorker` — fresh-process Rscript task (reference, ~3.8 s/task)
- `PersistentRWorker` / `PersistentWorkerPool` — long-lived workers (17 ms/task, 221× speedup)
- `Orchestrator` — scheduling façade
- `PalCache` — bounded LRU keyed by task identity

## Build & test

```bash
export JAVA_HOME=$HOME/jdk/jdk-21.0.12+8
export PATH=$JAVA_HOME/bin:$HOME/jdk/apache-maven-3.9.16/bin:$PATH
cd /mnt/d/visualR/java-runtime
mvn -B test          # 51 tests; R-equivalence gates require Rscript + pkgload
```

R-equivalence tests invoke the reference package at `/mnt/d/visualR/visualR`
(override with env `visualR_R_PACKAGE`). They FAIL loudly when R is missing —
no silent degradation.

## Benchmark

```bash
javac -d bench -cp target/classes bench/Bench.java
java -cp target/classes:bench Bench
# fresh-process: 3811.8 ms/task | persistent: 17.2 ms/task | 221.6x
```

## Conventions

- R stays authoritative; Java mirrors. Divergences (e.g. R's latent pack-resolution
  NULL bug) are documented in code + `_INDEX.md`, never silently "improved" past
  the default-pack behavior.
- Fail-closed everywhere: unknown kernel/pack ids, non-reconciled commits.
- Deterministic concurrency: parallel lanes read one frozen snapshot; orchestrator
  gathers results in submission order.
