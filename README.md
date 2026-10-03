# Infermats

Independent Kotlin/JVM libraries for composing model-specific structured inputs locally. Build native Kotlin values, validate them and export deterministic JSON. No inference, HTTP, telemetry, files or secret discovery occurs at runtime.

* [Ideogram 4](docs/ideogram.md): ordered caption strings, explicit photo/art and object/text variants, named bounds and palettes.
* [Jev](docs/jev.md): explicit model selection, typed Choice/Score/Noul questions and structured JSON content.
* [Contract mappings and provenance](docs/contracts.md)
* [Publishing setup and recovery](docs/releasing.md)
* [Implementation evidence](docs/implementation-notes.md)

Java 21, Kotlin 2.3.10 and Maven 3.9.9. Install the development artifacts:

```sh
./mvnw -B -ntp install
```

On Windows use `mvnw.cmd`. Each library is an ordinary independent Maven dependency; choose one or both. Current development version is `1.0.0-SNAPSHOT`; `1.0.0` is the intended first release and has not been published by this implementation task.

```xml
<dependency>
  <groupId>io.github.adumeige.infermats</groupId>
  <artifactId>infermats-ideogram</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For Jev use artifact ID `infermats-jev`. The parent is build management only. There is no common runtime prompt abstraction, and neither library depends on the other. Runtime dependencies are Kotlin stdlib and kotlinx.serialization JSON. Documentation has complete executable examples compiled as external Maven consumers by CI.

Normal verification is `./mvnw -B -ntp verify`. See [release checks](docs/releasing.md#local-verification) for source/documentation artifact checks, independent contract checks and consumer tests. All runtime work is synchronous and local. Structural validity does not promise model quality; documented exclusions and upstream discrepancies appear in the contract inventory.

Apache License 2.0. The pinned upstream Ideogram verifier used in tests retains its own [Apache 2.0 license](.github/scripts/upstream/IDEOGRAM-LICENSE.md).
