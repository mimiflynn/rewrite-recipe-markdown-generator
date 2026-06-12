# Contributing

## Development setup

### Requirements

- **Java 21** (required for compilation and running)
- **Gradle** (wrapper included, no separate install needed)

Optional (for full doc generation):
- Node.js (TypeScript recipes)
- Python 3.10+ with pip (Python recipes)
- .NET SDK (C# recipes)

### Quick start

```shell
# Clone the repository
git clone https://github.com/mimiflynn/rewrite-recipe-markdown-generator.git
cd rewrite-recipe-markdown-generator

# Compile
./gradlew compileKotlin

# Run tests
./gradlew test

# Run the generator (latest versions only)
./gradlew run -PlatestVersionsOnly=true
```

## Build commands

| Command | Description |
|---------|-------------|
| `./gradlew compileKotlin` | Compile main sources |
| `./gradlew compileTestKotlin` | Compile test sources |
| `./gradlew test` | Run unit tests (JUnit 5) |
| `./gradlew run` | Generate all docs (requires full toolchain) |
| `./gradlew run -PlatestVersionsOnly=true` | Generate only latest-versions files |
| `./gradlew dependencyCheckAnalyze` | Run OWASP dependency vulnerability check |

## Code organization

All source code is in Kotlin under `src/main/kotlin/org/openrewrite/`:

- **`RecipeMarkdownGenerator.kt`** — Main entry point and CLI argument parsing (Picocli). Orchestrates the overall generation workflow.
- **`RecipeLoader.kt`** — Loads JVM-based recipe descriptors from the classpath.
- **`TypeScriptRecipeLoader.kt`** / **`PythonRecipeLoader.kt`** / **`CSharpRecipeLoader.kt`** — Load recipes from external RPC processes for their respective ecosystems.
- **`RecipeMarkdownWriter.kt`** — Core logic for rendering individual recipe pages to markdown.
- **`TemplateRenderer.kt`** — Handles markdown template rendering with variable substitution.
- **`CategoryWriter.kt`** — Generates category index/navigation pages.
- **`ChangelogWriter.kt`** — Diffs current recipe descriptors against cached versions to produce changelogs.
- **`VersionWriter.kt`** — Writes version reference documents (latest versions of modules).
- **`ListsOfRecipesWriter.kt`** — Generates curated list pages.
- **`RedirectWriter.kt`** — Produces redirect mappings for moved/renamed recipes.
- **`RecipeJsonExporter.kt`** — Exports recipe data in JSON format.
- **`RecipeOrigin.kt`** — Determines whether a recipe is open-source or proprietary.
- **`Licenses.kt`** — License classification logic.
- **`config/`** — Configuration classes (`GeneratorConfig`, `BrandingConfig`, `Ecosystem`, `RecipePackageConfig`).

## Making changes

### Adding a new recipe module

To include a new recipe module in the generated docs, add it to the `recipe` configuration in `build.gradle.kts`:

```kotlin
"recipe"("org.openrewrite.recipe:rewrite-new-module:$rewriteVersion")
```

### Modifying markdown output

The markdown templates and rendering logic are in `RecipeMarkdownWriter.kt` and `TemplateRenderer.kt`. Changes there affect all generated recipe pages.

### Testing

Tests are in `src/test/kotlin/org/openrewrite/`. Run them with:

```shell
./gradlew test
```

Key test files:
- `RecipeMarkdownGeneratorTest.kt` — Integration tests for the full generation pipeline
- `RecipeLoaderTest.kt` — Tests for recipe loading logic
- `RecipeOriginTest.kt` — Tests for origin classification
- `CSharpRecipeLoaderTest.kt` — Tests for C# recipe loading

## CI/CD

- **`test.yml`** — Runs `./gradlew test` on push to `main` and on PRs
- **`pr.yml`** — Runs compile + test on PRs targeting `main` or `main-upstream`
- **`ci.yml`** — Nightly scheduled run with `-PlatestVersionsOnly=true`

## Code style

- Kotlin source follows standard Kotlin coding conventions
- No explicit linter is configured; follow the patterns in existing code
- Prefer extension functions for adding behavior to existing types
- Use Kotlin coroutines for concurrent operations (see `kotlinx-coroutines-core` dependency)
