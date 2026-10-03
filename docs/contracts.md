# Supported contracts

Sources accessed 2026-10-03 before implementing public types:

* Ideogram guide and CaptionVerifier: [commit 990fe1c4e950bb9e9dc90e01c0ad98ba434f83c2](https://github.com/ideogram-oss/ideogram4/tree/990fe1c4e950bb9e9dc90e01c0ad98ba434f83c2). Relevant files: `docs/prompting.md`, `src/ideogram4/caption_verifier.py`.
* Jev [API](https://docs.typesafe.ai/api), [advanced structure](https://docs.typesafe.ai/primitives/advanced), [Python SDK question schemas](https://docs.typesafe.ai/sdk/python/api/types/questions), and [common types](https://docs.typesafe.ai/sdk/python/api/types/common). These unversioned documentation pages expose no immutable revision. Access date above is the provenance, not an invented schema version. Examples select `jev-latest` explicitly; no live inference is tested.
* Delivery adapted from [StateFlow commit 9bfa00a22d203e74731805d9072a3cf8f99203d8](https://github.com/adumeige/vaadin-stateflow/tree/9bfa00a22d203e74731805d9072a3cf8f99203d8): parent/module POMs, CI, mirroring helper and recovery tests.

## Ideogram 4 field mapping

Strings have no upstream nonempty restriction. Empty strings, empty element lists and empty palettes are preserved. Absent optional fields are omitted; explicit null is unsupported. All listed input fields are in V1; generation settings and magic prompt expansion are excluded.

| JSON path | Type / presence / bounds | Kotlin API | Order / coverage |
|---|---|---|---|
| `$.high_level_description` | optional string | `summary` | first when present; golden and fidelity |
| `$.style_description` | optional photo or art object | `style`, `PhotoStyle`, `ArtStyle` | second; both variants |
| `$.style_description.aesthetics` | required string | `aesthetics` | first; golden |
| `$.style_description.lighting` | required string | `lighting` | second; golden |
| `$.style_description.photo` | required only for photo | `PhotoStyle.photo` | third; golden |
| `$.style_description.medium` | required string | photo fixed `photograph`; art `medium` | photo fourth, art third; golden |
| `$.style_description.art_style` | required only for art | `ArtStyle.artStyle` | fourth; golden |
| `$.style_description.color_palette` | optional array of 0–16 uppercase `#RRGGBB` | `palette: List<HexColor>?` | last; boundary tests |
| `$.compositional_deconstruction` | required object | `composition` | last; missing field tests |
| `$.compositional_deconstruction.background` | required string | `background` | first; golden |
| `$.compositional_deconstruction.elements` | required array, may be empty | `element`, `obj`, `text` | second; variants and composition |
| `…elements[*].type` | required `obj` or `text` | sealed `ObjectElement`, `TextElement` | first; golden |
| `…elements[*].bbox` | optional four integers in 0–1000; minima ≤ maxima | `BoundingBox(yMin,xMin,yMax,xMax)` | second if present; all boundaries |
| `…elements[*].text` | required for text only, literal string | `TextElement.text` | before desc; fidelity |
| `…elements[*].desc` | required string | `description` | after bbox/text; golden |
| `…elements[*].color_palette` | optional array of 0–5 colors | `palette` | last; boundary tests |

Verifier discrepancies: style and element required scalar strings are checked indirectly through key order but not consistently type-checked by Python. Infermats uses the guide's string types. The verifier allows arbitrary photo medium strings; Infermats' photo variant supplies the guide's `photograph` pairing. Degenerate boxes are accepted, following verifier comparisons (`>` rather than `>=`); no extra area or overlap rule. Lowercase six-digit colors are an explicit convenience of `HexColor.of`, normalized to uppercase without trimming or accepting shorthand. Top-level order follows the guide even though the verifier does not enforce it.

## Jev request field mapping

This module exports the request body, not a caption string or chat envelope. Nested content can contain arbitrary JSON including numbers, booleans and null; only the top-level content kind is constrained. Empty strings, objects, arrays, question maps and Choice maps are accepted because no documented minimum is established. No defaults are inferred. Ordering is caller insertion order for maps and rubric order for arrays.

| JSON path | Type / presence / bounds | Kotlin API | Coverage |
|---|---|---|---|
| `$.model` | required explicit string | `jevRequest(model)` / `JevRequest.model` | minimal, missing/empty policy |
| `$.state` | required string/object/array, never null | `stateText`, `state(JsonElement)`, serializer overload | kind and fidelity |
| `$.questions` | required map of question IDs | `questions`, `question` | empty, order, duplicates |
| `…questions[id].type` | required `choice`, `score`, `noul` | sealed question variants | all goldens |
| `…questions[id].instructions` | explicit string/object/array/null | `instructions` overloads | all kinds, missing |
| `…choice.criteria` | required map, maximum 255 options | `option`, `ChoiceQuestion` | null, structured, limits, duplicates |
| `…score.criteria` | required ordered array, 2–10 entries; string/object/array/null | `level`, `ScoreQuestion` | structure, order, limits |
| `…noul.criteria` | optional object, optional true/false keys | `NoulCriteria`, `criteria` | absent, empty, one/both sides |
| `…noul.criteria.true/false` | string/object/array/null when present | `yes`, `no` | explicit null vs absent |

Discrepancy resolution: API prose calls instructions required and excludes null outside Choice descriptions. The advanced guide explicitly permits null in all content fields; published Python SDK schemas also permit optional/null instructions and nullable Noul descriptions. Infermats preserves explicit null for content per the advanced guide. It requires an explicit instructions assignment (including `JsonNull`) as a library policy to avoid accidental missing question content. Omitted instructions, which SDK models permit, are an optional upstream feature excluded in V1. Score null levels follow the advanced guide even though Python's Score sequence annotation and API prose exclude them. Score size 2–10 and Choice maximum 255 follow the HTTP API; SDK descriptions are less specific. Whole Noul criteria null, allowed by SDK, is excluded; absence and an explicit empty criteria object are supported. Model strings must be nonblank as a library policy, without imposing a model allowlist. No stable upstream schema version is asserted.

Golden fixtures are independently authored from these mappings, not produced with the encoders under test. Routine conformance uses the pinned standalone Python CaptionVerifier without importing the inference package. Jev fixtures use independently specified contract assertions, reconciled against the official documentation above. No live model quality claim is made.

## Documentation capture fingerprints

Unversioned official Jev pages were downloaded through their Markdown endpoints on the access date above. SHA-256 identifies the inspected response bytes; it is not an upstream version ID.

* [jev-api.md](https://docs.typesafe.ai/api.md): `6b760275f89341fe15cff80c28aacae94d7d0c0c99afcd987ec0bca680b5713a`
* [jev-advanced.md](https://docs.typesafe.ai/primitives/advanced.md): `650a61153fe3d16619e57c8f81fc03b570d663c40dd2fe94df6e57db6c906f62`
* [jev-questions.md](https://docs.typesafe.ai/sdk/python/api/types/questions.md): `60da0a09dc5b4115665a001344929912430f52ce381228f5a70bce8a59ee61ed`
* [jev-common.md](https://docs.typesafe.ai/sdk/python/api/types/common.md): `d93bf202cbbd2eeebb7f91af476b803400bd8ebe81b7c6013027d8b7c8c461bd`
