# Copilot Instructions

## Project overview

This is the **Rewrite Recipe Markdown Generator** — a Kotlin CLI application that generates markdown documentation for all OpenRewrite and Moderne recipes. It loads recipe descriptors from the classpath (JVM recipes) and via external RPC processes (TypeScript, Python, C#), then renders them as markdown pages.

**Main entry point:** `src/main/kotlin/org/openrewrite/RecipeMarkdownGenerator.kt`

## Build and test commands

```shell
# Compile
./gradlew compileKotlin

# Run all tests
./gradlew test

# Run the generator (lightweight mode — no external toolchains needed)
./gradlew run -PlatestVersionsOnly=true

# Run the full generator (requires Java 21, Node.js, Python 3.10+, .NET SDK)
./gradlew run
```

## Technology stack

- **Language:** Kotlin 2.3.0, targeting JVM 21
- **Build:** Gradle with Kotlin DSL (`build.gradle.kts`)
- **CLI framework:** Picocli
- **Serialization:** Jackson (YAML + Kotlin module)
- **HTTP:** OkHttp
- **Diffing:** java-diff-utils
- **Concurrency:** Kotlin Coroutines
- **Testing:** JUnit 5 + AssertJ

## Key architectural concepts

### Recipe loading pipeline

1. `RecipeLoader` — loads JVM recipes from a provided classpath (the `recipe` Gradle configuration)
2. `TypeScriptRecipeLoader` — spawns Node.js RPC processes for TS recipe packages
3. `PythonRecipeLoader` — spawns Python RPC processes for Python recipe packages
4. `CSharpRecipeLoader` — spawns .NET RPC processes for C# recipe packages

### Output generation

- `RecipeMarkdownWriter` — renders individual recipe markdown pages
- `CategoryWriter` — generates category/index pages
- `ChangelogWriter` — diffs against `src/main/resources/recipeDescriptors.yml` to produce changelogs
- `VersionWriter` — generates version reference files
- `ListsOfRecipesWriter` — generates curated recipe list pages
- `RedirectWriter` — generates redirect mappings for renamed/moved recipes
- `RecipeJsonExporter` — exports recipe metadata as JSON

### Configuration

- `config/GeneratorConfig.kt` — top-level generation settings
- `config/BrandingConfig.kt` — branding/theming for output
- `config/Ecosystem.kt` — recipe ecosystem definitions
- `config/RecipePackageConfig.kt` — package-level configuration

### Dual output

The generator writes to **two** output directories:
- `build/docs/` — OpenRewrite (open-source only)
- `build/moderne-docs/` — Moderne (all recipes including proprietary)

The `RecipeOrigin` and `Licenses` classes determine which output a recipe belongs to.

## File layout

```
src/main/kotlin/org/openrewrite/
├── RecipeMarkdownGenerator.kt    # Entry point, CLI args
├── RecipeLoader.kt               # JVM recipe loading
├── TypeScriptRecipeLoader.kt     # TS recipe RPC
├── PythonRecipeLoader.kt         # Python recipe RPC
├── CSharpRecipeLoader.kt         # C# recipe RPC
├── RecipeMarkdownWriter.kt       # Markdown rendering
├── TemplateRenderer.kt           # Template engine
├── CategoryWriter.kt             # Category pages
├── ChangelogWriter.kt            # Changelog generation
├── VersionWriter.kt              # Version docs
├── ListsOfRecipesWriter.kt       # Recipe lists
├── RedirectWriter.kt             # Redirect mappings
├── RecipeJsonExporter.kt         # JSON export
├── RecipeOrigin.kt               # Open-source vs proprietary
├── RecipeOption.kt               # Option model
├── MarkdownRecipeDescriptor.kt   # Descriptor model
├── MarkdownRecipeArtifact.kt     # Artifact model
├── RecipeDescriptorExtensions.kt # Extensions
├── Licenses.kt                   # License logic
├── CliVersion.kt                 # CLI version utils
└── config/
    ├── GeneratorConfig.kt
    ├── BrandingConfig.kt
    ├── Ecosystem.kt
    └── RecipePackageConfig.kt
```

## Important conventions

- All recipe modules are declared in `build.gradle.kts` under the `recipe` configuration
- Version resolution uses `latest.release` — builds are non-reproducible by design (always picks newest)
- The `recipeDescriptors.yml` resource file is **rewritten on every run** and should be committed after generation to enable changelog diffing
- Kotlin stdlib versions are force-resolved to 1.9.25 in `configurations.all` to avoid conflicts with the moderne-recipe-bom
- The `run` task uses 4GB heap (`maxHeapSize = "4g"`)

## Common tasks for AI agents

### Adding a new recipe dependency

1. Add the dependency to `build.gradle.kts` in the `recipe` configuration block
2. Run `./gradlew compileKotlin` to verify resolution
3. Run `./gradlew test` to verify nothing breaks

### Modifying markdown output format

1. Edit `RecipeMarkdownWriter.kt` or `TemplateRenderer.kt`
2. Run `./gradlew test` to verify existing tests pass
3. Run `./gradlew run -PlatestVersionsOnly=true` to see output changes

### Fixing build issues

1. Check `build.gradle.kts` for version conflicts
2. The `configurations.all` block forces Kotlin stdlib versions — update if Kotlin version changes
3. The OWASP dependency-check (`suppressions.xml`) may need updates for false positives

### Understanding recipe classification

- `RecipeOrigin.kt` — determines source module/artifact for a recipe
- `Licenses.kt` — classifies recipes as open-source or proprietary
- Proprietary recipes go only to `build/moderne-docs/`; open-source go to both outputs
