# Implementation evidence

Implemented on 2026-10-03 from the detailed brief in the repository. The initial worktree contained only the license and brief; no applicable AGENTS.md was present. Work proceeded through contract discovery, independent model slices, external consumer review and release preparation. No runtime shared prompt abstraction or inference integration was introduced.

## Completed milestones

1. **Contract inventory and skeleton:** retrieved the official Ideogram guide/verifier before types; inspected Jev API, advanced content documentation and SDK schemas. Captured immutable repository revisions and unversioned documentation response fingerprints in `contracts.md`. Established a standalone parent and two independent modules; empty-reactor verification passed before implementing model types.
2. **Ideogram:** immutable photo/art styles, object/text elements, named coordinates and explicit color constructor; ordered compact encoder; throwing builds and nonthrowing checks. Minimal and complete independently authored goldens cover all variants, omitted/empty palettes, precise ordering and Unicode.
3. **Jev:** explicit model ID, typed questions, structured state/instructions/criteria, serializer and reified helpers, insertion ordering and duplicate rejection. Public values export only after validation; deep snapshots protect mutable JSON backing collections. Nested invalid raw JSON literals/nonfinite numbers fail validation.
4. **API/consumers:** full documentation programs compile and execute outside the reactor after install, with only their own model dependency. Dependency trees exclude the sibling and application frameworks. Separate negative compilation checks prove DSL receiver isolation. Golden tests cover fragment reuse, loops, conditionals, mutations, missing content, invalid kinds and numeric limits.
5. **Release preparation:** StateFlow's sequential Central/GitHub workflow and mirroring/recovery helper adapted to this reactor. Versions, source commit, release commit, exact signed artifact set and retained bytes are checked. Source/Dokka attachments, parent-only POM publication, manual staging defaults and 90-day recovery retention are configured. Workflow dispatch is main-only, with no publication on ordinary push/PR events.
6. **Handoff:** README, model docs, mappings and recovery/setup instructions are present. The brief authorizes a draft PR when access is available; repository read access and authenticated GitHub connector were confirmed. No release was dispatched.

## Verification on Java 21 / Maven 3.9.9

These checks passed against the implemented state:

| Command/check | Evidence |
|---|---|
| `./mvnw -B -ntp clean verify` | Clean secret-free reactor build; Kotlin/JVM target 21 |
| `./mvnw -B -ntp -Pcentral-release verify -Dgpg.skip=true` | Unsigned profile, real sources and Dokka HTML |
| `./mvnw -B -ntp -Pcentral-release install -Dgpg.skip=true` | Parent and both libraries installed, with source/documentation JARs; 19 Kotlin tests, zero failures/skips |
| `python3 .github/scripts/check_artifacts.py` | Exactly three JARs per library, real `.class`/`.kt`/generated `.html`, no tests/sibling content, no parent JAR |
| `python3 .github/scripts/check_consumers.py` | Both standalone documentation programs compile/run; both receiver negatives fail as intended; independent dependency trees |
| `python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v` | 13 tests, zero failures/skips: pinned CaptionVerifier, Jev fixtures, missing/extra artifact checks, version/signature/checksum/path rejection, byte conflicts, identical-byte skipping, real Maven mirror and partial retry, manifest hashes |
| `actionlint 1.7.7 -shellcheck= .github/workflows/ci.yml` | Workflow syntax and action expressions pass; no shellcheck executable was available |
| YAML inspection and `git diff --check` | Build → Central → GitHub dependency order, scoped write permissions, no whitespace errors |

The Maven wrapper includes Unix/Windows scripts and a SHA-256-checked 3.9.9 distribution. Windows execution was not run here. Version compatibility was verified through actual compilation, tests and generated documentation rather than assuming that reference versions were the latest.

## Requirement audit

| Brief requirement group | Authoritative implementation/evidence |
|---|---|
| Coordinates, modules, Java/Kotlin, Maven-only build, limited runtime dependencies | Parent/child POMs, checked wrapper, successful builds and external dependency trees |
| Model independence, native DSLs, alternatives and immutable snapshots | Separate packages/modules; sealed model-local alternatives; snapshot tests and negative compilation |
| Complete supported input mapping, provenance, optional exclusions | `contracts.md` field tables, pinned verifier, fixture checks and documented discrepancies |
| Ideogram requirements 1–6 | Explicit alternatives, named bounds, color normalization, ordered `toPromptString`, literal text goldens and executable outer-request example |
| Jev requirements 1–9 | Separate questions; all structured content and serialization overloads; strict kind/literal checks; order/duplicates; explicit `wireLabel`; request-object/text export and transport exclusions |
| Validation cannot be bypassed, actionable model-local errors | Both encoders validate public constructed values; fragment/build boundaries validate; codes/paths and invalid-constructor tests |
| Absent/null/empty, deterministic field names/order, no metadata/discriminators | Exact independent golden strings, repeated output checks, explicit omission/null tests; model-local encoders |
| Test-plan boundaries, Unicode, composition and mutation safety | `CaptionTest` and `RequestTest`; minimal/complete fixtures for every variant; pinned independent Python checks |
| Compiled examples and independent Maven consumers | Complete programs in model documentation extracted and executed by consumer checker; dependency tree and receiver rejection checks |
| Source/Dokka archives and required POM metadata | Unsigned release install and artifact checker; license, developer, SCM, name, description and URL in POMs |
| Same-artifact Central/GitHub delivery, preflight, recovery, retention and collisions | `ci.yml`, `record_bundle.py`, exact-set mirroring helper and recovery tests; module-qualified asset names |
| Normal secret-free CI; manual-only release; established credentials/manual staging | CI build job; central-release profile defaults; release docs and main-only dispatch guard |
| Source revisions, assumptions/exclusions and concrete setup | `contracts.md`, `releasing.md`, README and these notes |

## Discrepancies and limits

Jev sources disagree on null content and instruction optionality. V1 supports explicit content null per the advanced guide, requires an explicit instructions assignment as a library policy, and excludes omitted instructions and whole-null Noul criteria. Score sizes follow the API's 2–10 limits. Ideogram follows verifier bounds including zero-area boxes; its photo medium is fixed to the guide's `photograph`. See the contract inventory for all empty/absent/null policies.

No upstream field was invented to imitate conversational DSL sketches. No supported field is silently replaced with fabricated required text. Generation settings, magic prompt expansion, response decoding, JSON import/editing and SDK transport remain outside V1. No live inference, signing with production keys, Central publication, GitHub Packages publication or workflow dispatch was performed. Local mirroring tests use fixture signatures to exercise byte-preserving delivery; production signature generation is configured through Maven GPG and established secrets. Live integration and model-quality smoke tests are optional follow-up.
