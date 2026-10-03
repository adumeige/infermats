Build Infermats as a Kotlin library collection for constructing model-specific structured prompts programmatically. Each supported model has its own DSL, types, validation, and JSON encoding. Start with Ideogram 4 and Jev.

This is the implementation brief for Codex, prepared on 3 October 2026. It covers the product boundary, module design, contract verification, tests, and Maven Central delivery. Prepare the implementation and release machinery; this brief does not request triggering a live release.

## Binding decisions and implementation defaults

The user has fixed the project name as `infermats`, the stack as Kotlin with Maven, and delivery as the established Sonatype publishing workflow. Each model must have a specific DSL with minimal or no shared elements.

Consequently, do not introduce a universal Prompt interface, common model hierarchy, shared builder framework, model registry, provider SPI, or compulsory runtime core. Model modules must not depend on each other. A shared Maven parent manages the build only.

Use these implementation defaults unless the target repository already establishes a different compatible convention:

| Item | Default |
| - | - |
| Maven group | `io.github.adumeige.infermats` |
| Root artifact | `infermats-parent`, packaging `pom` |
| Libraries | `infermats-ideogram`, `infermats-jev` |
| Kotlin package root | `io.github.adumeige.infermats` |
| Packages | `.ideogram.v4` and `.jev`, with model-local subpackages |
| Development version | `1.0.0-SNAPSHOT` |
| First intended release | `1.0.0` |
| Runtime target | Kotlin/JVM, Java 21 |
| Kotlin baseline | `2.3.10`, matching the inspected StateFlow parent |
| JSON | `kotlinx.serialization`, version explicitly pinned after compatibility verification |
| Tests | JUnit Jupiter, with test tooling pinned in the parent |
| License | Apache 2.0, following the inspected sibling project |

The coordinates, module names, initial version, license, and JVM-only scope are implementation defaults proposed by this brief, not additional user decisions. Do not infer a multiplatform requirement from other projects. No Gradle files or Gradle-only build steps.

Create a small standalone parent. Do not inherit the application-oriented agentic-parent merely to obtain release configuration: its existing main branch brings Spring and application dependency management, and its Central migration PR was still open when inspected.

## Product scope

The useful result is an ordinary dependency that lets a developer discover a model's supported structure through Kotlin completion, compose prompts using functions and control flow, validate them locally, and obtain the correct serialized input.

V1 includes model-specific immutable values, DSL builders, model-specific validation errors, deterministic serialization, contract provenance, executable examples, and documentation. Implement the documented supported input contract for both initial modules. Explicitly record any unimplemented optional upstream feature rather than claiming complete coverage.

All runtime work is local and synchronous. The libraries perform no inference, HTTP calls, file writes, telemetry, automatic model downloads, prompt enhancement, or secret discovery. Consumers decide where to send the output.

Exclude HTTP SDKs, response decoding, agent orchestration, model selection, a visual editor, automatic translation between model formats, and prompt optimization. Typed Jev answer handling from the earlier discussion is a possible later feature, not a V1 obligation. JSON import and round-trip editing are also deferred; V1 constructs and exports prompts.

Structural validity does not promise model quality. Do not invent semantic restrictions such as banning contradictory descriptions or overlap between image elements.

## Repository and dependencies

Use a Maven reactor with the parent at the root and one directory per library:

| Path | Responsibility |
| - | - |
| `pom.xml` | Reactor, dependency and plugin versions, release profile |
| `infermats-ideogram/pom.xml` | Ideogram library |
| `infermats-jev/pom.xml` | Jev library |
| Each module's `src/main/kotlin` | Public types, builders, validators, encoders |
| Each module's `src/test/kotlin` | Contract tests and executable usage examples |
| Each module's `src/test/resources` | Expected JSON and documented source fixtures |
| `docs/ideogram.md` and `docs/jev.md` | Model-specific API and wire-format explanation |
| `docs/contracts.md` | Source revisions and coverage matrix |
| `docs/releasing.md` | Publishing and recovery procedure |
| `.github/workflows/ci.yml` | Build verification and manual release |
| `.github/scripts/` | Adapted artifact mirroring and tests |

Use the standard Kotlin Maven compiler and the serialization compiler plugin when needed. Pin plugin versions. Provide the Maven wrapper for a verified Maven 3.9.x release, including Windows support.

Runtime dependencies should be limited to Kotlin and the JSON implementation actually used. Do not add Spring, Vaadin, LangChain, Embabel, coroutines, logging backends, reflection, or HTTP clients without a concrete requirement. Keep a schema validator in test scope if it is used only to check fixtures.

It is acceptable for each module to declare its own DSL marker, validation result, and exception. Similar implementation details are not sufficient reason to introduce a shared runtime artifact.

## Contract discovery before implementation

Read the official model sources before coding public types. The authoritative starting points are linked at the end of this brief. Capture immutable Git commit references where available; otherwise record the access date, relevant documentation version, and a concise local contract description.

For every input field record its JSON path, allowed types, optionality, defaults if any, bounds, ordering rules, corresponding Kotlin API, and test coverage. Distinguish upstream requirements from Infermats conveniences and stricter library policy.

Inspect Ideogram's CaptionVerifier as well as its prose guide. For Jev, inspect the API and structured instructions documentation and, where needed, official SDK types. Resolve discrepancies explicitly in docs/contracts.md; do not silently choose a convenient shape.

Upstream schemas may describe an API envelope, a model prompt, or both. Keep that boundary visible in method names and documentation. Never wrap every model in an OpenAI chat message format.

Do not fabricate fields from plausible names. Do not copy the illustrative DSL from the conversation as if it were an official schema. Examples in this brief specify intended ergonomics; wire compatibility comes from the pinned sources.

## DSL design expectations

Each module should feel native to its domain. Use receiver lambdas and a model-local `@DslMarker`. Prefer ordinary Kotlin functions over a custom templating language.

Builders may mutate during construction but must produce immutable snapshots. Defensively copy caller collections; reusing or changing a builder must not alter an already built prompt. Immutable fragments may be reused across prompts. Avoid exposed mutable global configuration.

Represent meaningful alternatives with sealed types or separate builders. Required scalar arguments can be constructor or function parameters when this improves compile-time guidance. Avoid elaborate staged generic builders solely to claim that every error is caught at compilation.

Run validation at the public build boundary and before supported serialization paths that could receive manually constructed values. Make it impossible to obtain invalid output simply by bypassing a DSL through a public constructor or copy operation. Choose constructor validation or encoder validation consistently within each module.

Return actionable errors containing a model-local code, a JSON-style path, and a concise explanation. Do not dump complete input data in error messages. Missing nested fields should produce domain errors rather than NullPointerException or lateinit failures.

Offer a nonthrowing validation route where useful and a convenient throwing builder. Their representation is module-specific. Required values must not be fabricated as empty strings to appease a verifier.

Ordinary composition should work without a fragment framework: reusable values, helper functions accepting a builder receiver, loops over domain objects, and conditional additions. Document how repeated scalar assignments and collection additions behave. Duplicate identifiers must not silently overwrite earlier entries.

## Ideogram module

Target the Ideogram 4 caption contract and expose a model-specific entry point such as `ideogram4 { ... }`. Preserve the distinction between a caption and generation settings such as seed, dimensions, or sampler configuration; those settings are outside V1.

The inspected guide specifies:

- Optional summary and style, with required composition containing background and elements.

- Separate photographic and non-photographic styles, with variant-specific key ordering.

- Object and text elements, with literal text distinct from description.

- Optional bounds encoded as `[y_min, x_min, y_max, x_max]` on a 0–1000 scale.

- Uppercase six-digit hexadecimal colors; at most 16 for a style and 5 per element.

- Compact JSON, literal Unicode, and prescribed ordering for composition and element fields.

[Source: official Ideogram prompting guide](https://github.com/ideogram-oss/ideogram4/blob/main/docs/prompting.md).

Implementation requirements:

1. Represent style and element alternatives explicitly; avoid nullable fields that allow incompatible variants together.

2. Provide a named-coordinate bounding-box value to prevent accidental axis reversal. Derive precise range and ordering validation from the verifier; document any extra policy on degenerate boxes.

3. Make color normalization explicit. If lowercase colors are accepted as a convenience, normalize through a documented value constructor and test the output.

4. Provide a canonical `toPromptString()` path, backed by an ordered encoder. A debug JSON representation must not be advertised as interchangeable if it changes order or encoding.

5. Preserve literal user text, including punctuation, newlines, accents, and emoji, while escaping JSON correctly.

6. A consumer embedding the caption in a request should pass the resulting string to its JSON encoder exactly once. Include an example showing the caption string and the outer request separately.

Implement photo and illustration/poster examples, including reusable style, repeated objects from a list, and text placement. Assert emitted strings where ordering is significant. A parsed-object equality assertion alone cannot detect this class of defect.

## Jev module

Expose a Jev request or prompt builder with explicit model selection. Avoid a silent moving-model default in the core API; examples may explicitly choose an upstream alias. Keep model ID separate from the version of the input contract supported by Infermats.

The inspected API has required model, state, and a question map. State accepts string, object, or array. Questions are Choice, Score, or Noul. Instructions support structured values. Choice criteria are a map with at most 255 options; descriptions may be null. Score criteria are an ordered array with 2–10 levels. Noul may supply true/false criteria. [Source: official Jev API](https://docs.typesafe.ai/api).

Implementation requirements:

1. Separate question variants. Provide typed Kotlin builders for their envelopes while preserving structured JSON inside content-bearing fields.

2. Offer explicit overloads for text, JsonElement, and serializable Kotlin values where appropriate. Validate top-level value kinds rather than assuming every JsonElement is valid.

3. Use an explicitly supplied serializer or a reified serialization overload; avoid reflection-based `Any` conversion.

4. Preserve a string as a JSON string, even when its contents happen to resemble JSON. Structured input must use a distinct method or overload.

5. Preserve insertion order for questions and options, and rubric order for scores. Reject duplicate question IDs and duplicate option keys.

6. Require explicit stable wire labels for enum or domain-value convenience helpers. Do not serialize enum ordinals or arbitrary `toString()` results.

7. Carry structured instructions and criteria through without flattening them into prose or serializing them into nested JSON strings.

8. Keep the caller's input as data; do not rewrite it, add instructions, or infer question text from identifiers.

9. Export the request body as JsonObject and as JSON text. Authentication, endpoint configuration, retries, and execution stay outside the module.

Example target ergonomics, subject to field mapping confirmed in the contract pass:

```kotlin
val request = jevRequest(model = "jev-latest") {
    stateText(ticketText)
    questions {
        choice("department") {
            instructions("Which team should handle this ticket?")
            option("billing", "Invoices and payment problems")
            option("technical", "Software faults and integrations")
        }
        noul("urgent") {
            instructions("Does the ticket require immediate attention?")
        }
    }
}
val json = request.toJsonString()
```

Include separate examples for structured state, structured instructions, a scoring rubric, and questions generated from a Kotlin collection. A model API that accepts structured content must not be narrowed to strings by the DSL.

## Serialization and compatibility policy

Use a model-local encoder as the authority for wire output. Kotlin property names can be ergonomic, but JSON field names must exactly match the supported contract.

Do not leak Kotlin class discriminators, implementation fields, helper labels, validation metadata, or schema provenance into the payload. Configure or replace serializers where automatic encoding would violate the contract.

Define absent, null, and empty behavior for every relevant field. Omit absent optional fields unless the upstream format requires explicit null. Preserve explicit null in locations where it has documented meaning. Do not sort all JSON keys as a generic canonicalization step.

Use stable output for identical inputs. Never insert timestamps, random IDs, inferred defaults, or environment-derived content. Pretty output, if provided, is for inspection; document whether it is suitable for inference.

Treat the public Kotlin API and the emitted contract as compatibility surfaces. A change to field defaults, ordering, omission, or normalization can change model behavior even when Kotlin source still compiles. Document such changes and use semantic versioning accordingly.

Use one release version across the Maven reactor initially. Keep the Ideogram model generation explicit in the API/package. For Jev, document the supported request contract and tested model identifier without inventing an upstream schema version.

New models receive new independent modules. Similar-looking concepts must not automatically migrate into a common package.

## Test plan and acceptance evidence

Tests must establish wire compatibility and safe composition, not merely mirror builder assignments. Run the normal suite without API keys, model access, network inference, or signing credentials.

| Area | Required evidence |
| - | - |
| Contract coverage | Minimal valid input and representative complete input for each supported variant |
| Wire format | Golden output for field names, discriminators, omission, null, and Ideogram order |
| Bounds | Boundary and out-of-range cases for coordinates, palettes, option counts, and score levels |
| JSON fidelity | Quotes, backslashes, multiline content, French accents, emoji, nested objects/arrays |
| Construction | Missing required values, incompatible variants, duplicate IDs, wrong JSON value kinds |
| Immutability | Mutating source collections or reused builders cannot change previous output |
| Composition | A fragment reused in two prompts, conditional elements, and loop-generated content |
| Isolation | Each module can be consumed alone without the other model module |
| Ergonomics | Documentation examples compile; a small negative compilation test checks DSL receiver isolation |
| Distribution | Sources contain Kotlin files; documentation JAR contains real generated HTML |

Compare Ideogram fixtures with a pinned upstream verifier or equivalent independent contract checks, including ordering rules. If running the upstream Python verifier requires heavyweight inference dependencies, use a bounded optional conformance job or independently specified fixtures; do not add GPU/model dependencies to routine CI.

For Jev, compare expected request bodies against the pinned documented schema or official SDK contract. Clearly identify independently authored expected fixtures. Do not generate the expected result by calling the serializer under test.

Compile a tiny external Maven consumer after local install, with each dependency separately. Check that published POMs resolve without sibling source checkouts and do not bring application frameworks.

Live model smoke tests are optional manual follow-up. They must never block ordinary PR CI or pretend to prove output quality from schema compliance.

## Sonatype and GitHub delivery

Adapt the merged StateFlow release workflow and its mirroring helper rather than creating a different delivery scheme. The reference files and merged PR are linked below.

The established secret names are:

| Repository secret | Purpose |
| - | - |
| `CENTRAL_USERNAME` | Sonatype Central Portal token username |
| `CENTRAL_PASSWORD` | Sonatype Central Portal token password |
| `GPG_PRIVATE_KEY` | ASCII-armored signing private key |
| `GPG_PASSPHRASE` | Signing key passphrase |

The reference workflow maps the signing secrets to `MAVEN_GPG_KEY` and `MAVEN_GPG_PASSPHRASE`. GitHub publication uses the built-in GITHUB_TOKEN. Do not introduce a PAT requirement.

Repository pushes and pull requests build and verify only. Publication is a manual workflow_dispatch from main with a new explicit release version. Reuse the following sequence:

1. Validate the version, required credentials, existing tag state, and intended source commit.

2. Update all reactor POMs in an isolated release checkout and create a release commit. Main keeps its snapshot version.

3. Preserve the exact release source and manifest.

4. Build and sign the parent POM and both library artifacts once, then publish through the Central Portal publishing plugin.

5. Wait for Central to report published before starting the GitHub phase.

6. Restore the retained release source, create the matching annotated v\<version> tag, and create a draft GitHub Release.

7. Mirror the exact signed artifacts from the retained Central bundle to GitHub Packages. Verify existing/uploaded bytes.

8. Attach the release assets and manifest, then publish the GitHub Release only after mirroring succeeds.

This is a sequential release with recovery, not an atomic transaction across services. A tag can be visible while the Release remains a draft.

Retain the signed bundle, manifest, and source bundle for 90 days, including when Central publication fails or times out. Never rebuild or re-sign to recover a partly completed release. Identical registry files can be skipped; conflicting files must cause failure.

Document that a Central timeout has an uncertain outcome until its deployment status is checked. After Central succeeds, rerun only the failed GitHub job from the original run. Do not automatically redeploy an already published version.

The release profile is central-release. Ordinary verification needs no credentials. Support an unsigned release-artifact check with `mvn -B -ntp -Pcentral-release verify -Dgpg.skip=true`. Local release deploy should preserve the established manual-staging default; the manual CI release explicitly enables automatic publication and waits for published state.

Publish source JARs and Dokka-generated documentation JARs for both libraries, signatures, and all required POM metadata. Parent-only artifacts do not need fake binary or documentation JARs. Exclude examples/test utilities from publication.

The inspected reference uses Java 21, Kotlin 2.3.10, Dokka 2.2.0, GPG plugin 3.2.8, and Central publishing plugin 0.11.0. These are reproducible reference versions, not claims about the newest available releases. Verify the combination in Infermats before freezing it.

Use release-level concurrency and least-privilege job permissions. Preserve the source/release commit distinction in the manifest. Add checks for the exact intended artifact set so both modules and their parent ship and nothing else does. Ensure asset names from multiple modules cannot collide.

## Implementation milestones

Complete the work in reviewable increments; do not pause after each routine implementation decision.

1. **Contract inventory and build skeleton.** Inspect repository guidance, establish the parent and two empty modules, pin sources, write the field mapping and scope notes. Acceptance: root verify succeeds and the supported formats are specified.

2. **Ideogram vertical slice.** Implement values, builder, validation, ordered encoding, and compiled examples. Acceptance: all supported style/element variants and boundary fixtures pass.

3. **Jev vertical slice.** Implement state, question variants, structured content, validation, and encoding. Acceptance: text and structured examples match the recorded wire contract.

4. **API and consumer review.** Check module independence, immutability, DSL receiver behavior, errors, and documentation snippets. Acceptance: standalone consumer projects compile.

5. **Release preparation.** Adapt the established workflow, profiles, metadata, Dokka, mirroring helper, and recovery tests. Acceptance: unsigned release verification produces the exact expected artifact set without contacting registries.

6. **Final handoff.** Provide implementation summary, source revisions, tested commands, any contract discrepancies, and concrete release setup steps. Open a draft PR if repository access and the implementation task authorize it. Do not describe an untriggered release as published.

Keep progress in the repository implementation notes. If one upstream contract is inaccessible, complete the independent module and build work, record the precise missing evidence, and avoid inventing the blocked model's fields.

## Definition of done

- Both libraries can be installed and consumed independently through Maven.

- Each model has a native Kotlin DSL with no shared runtime prompt abstraction.

- Supported upstream input fields and constraints have a documented mapping.

- Serialization is deterministic, contract-correct, and covered by independent expected fixtures.

- Local validation produces useful errors and cannot be bypassed through supported construction paths.

- Documentation examples compile and demonstrate composition.

- CI is secret-free for normal builds and covers the release artifact contents.

- The manual release workflow implements the same-artifact Central and GitHub process.

- Publishing credentials are documented using the established names.

- No live publication is required to complete implementation preparation.

- Remaining assumptions or exclusions are explicitly reported rather than hidden behind a claim of full support.

## Reference sources

The linked conversation was used through retrieved conversation context; the delivery details were then checked against current repository files. StateFlow's migration is merged. The agentic-parent migration was open at inspection, so its proposed coordinates must not be treated as an already published dependency.

- [User publishing conversation](https://chatgpt.com/c/6abca62e-0224-83eb-b5b4-48ea0d1672ca)

- [Merged StateFlow delivery PR](https://github.com/adumeige/vaadin-stateflow/pull/1)

- [StateFlow workflow](https://github.com/adumeige/vaadin-stateflow/blob/main/.github/workflows/ci.yml)

- [StateFlow parent POM](https://github.com/adumeige/vaadin-stateflow/blob/main/pom.xml)

- [StateFlow publishing and recovery documentation](https://github.com/adumeige/vaadin-stateflow/blob/main/README.md)

- [Agentic parent migration PR](https://github.com/adumeige/agentic-parent/pull/1)

- [Ideogram 4 official prompting guide](https://github.com/ideogram-oss/ideogram4/blob/main/docs/prompting.md)

- [Jev official documentation index](https://docs.typesafe.ai/llms.txt)

- [Jev API contract](https://docs.typesafe.ai/api)

- [Jev structured instructions and criteria](https://docs.typesafe.ai/primitives/advanced)

Codex should resolve moving repository links to commit permalinks during the initial contract and build pass. Documentation access date for this brief: 3 October 2026.
