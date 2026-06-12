![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)

# Rewrite Recipe Markdown Generator

## What is this?

This project generates recipe documentation in markdown format for all recipes on the classpath. It produces two separate sets of output:

* **OpenRewrite docs** (`build/docs/`) — Open-source recipes only, for [docs.openrewrite.org](https://docs.openrewrite.org)
* **Moderne docs** (`build/moderne-docs/`) — All recipes including proprietary, for [docs.moderne.io](https://docs.moderne.io)

Proprietary recipes (those with a `Proprietary` license or loaded via TypeScript/Python) are written only to the Moderne docs output. Open-source recipes are written to both.

## Project structure

```
.
├── build.gradle.kts              # Build configuration (Kotlin DSL)
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Gradle properties
├── suppressions.xml              # OWASP dependency-check suppressions
├── src/
│   ├── main/
│   │   ├── kotlin/org/openrewrite/
│   │   │   ├── RecipeMarkdownGenerator.kt   # Main entry point
│   │   │   ├── RecipeMarkdownWriter.kt      # Writes markdown for individual recipes
│   │   │   ├── RecipeLoader.kt              # Loads JVM recipes from classpath
│   │   │   ├── TypeScriptRecipeLoader.kt    # Loads TS recipes via RPC
│   │   │   ├── PythonRecipeLoader.kt        # Loads Python recipes via RPC
│   │   │   ├── CSharpRecipeLoader.kt        # Loads C# recipes via RPC
│   │   │   ├── CategoryWriter.kt            # Writes category index pages
│   │   │   ├── ChangelogWriter.kt           # Generates changelog from diffs
│   │   │   ├── VersionWriter.kt             # Writes version reference files
│   │   │   ├── ListsOfRecipesWriter.kt      # Writes recipe list pages
│   │   │   ├── RedirectWriter.kt            # Generates redirect mappings
│   │   │   ├── RecipeJsonExporter.kt        # Exports recipe data as JSON
│   │   │   ├── TemplateRenderer.kt          # Renders markdown templates
│   │   │   ├── RecipeOrigin.kt              # Determines recipe source/origin
│   │   │   ├── RecipeOption.kt              # Models recipe options
│   │   │   ├── MarkdownRecipeDescriptor.kt  # Recipe descriptor model
│   │   │   ├── MarkdownRecipeArtifact.kt    # Recipe artifact model
│   │   │   ├── RecipeDescriptorExtensions.kt# Extension functions
│   │   │   ├── Licenses.kt                  # License classification
│   │   │   ├── CliVersion.kt                # CLI version utilities
│   │   │   └── config/                      # Configuration classes
│   │   │       ├── GeneratorConfig.kt
│   │   │       ├── BrandingConfig.kt
│   │   │       ├── Ecosystem.kt
│   │   │       └── RecipePackageConfig.kt
│   │   └── resources/
│   │       └── recipeDescriptors.yml        # Cached descriptors for changelog diffing
│   └── test/kotlin/org/openrewrite/         # Unit tests
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                           # Nightly latest-versions generation
│   │   ├── pr.yml                           # PR build & test
│   │   └── test.yml                         # Push/PR test workflow
│   └── copilot-instructions.md              # AI agent instructions
└── .claude/settings.json                    # Claude Code permissions
```

## Technology stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.3.0 (JVM target 21) |
| Build tool | Gradle (Kotlin DSL) |
| Framework | Picocli (CLI), Kotlin Coroutines |
| Dependencies | Jackson (YAML/Kotlin), OkHttp, java-diff-utils |
| Testing | JUnit 5, AssertJ |
| Security | OWASP dependency-check |

## Changelog

This project also builds up a CHANGELOG to track what has changed over time. The way this works is that, every time
this project is run, it looks in the `/src/main/resources` directory for a `recipeDescriptors.yml` file.
It will then parse that file and compare it to the latest information obtained. If there are differences,
they will be outlined in a CHANGELOG that will be created in `build/docs`. After the CHANGELOG is built,
the latest information will be stored in the descriptors file for future use.

Once you have the CHANGELOG created, you can copy it over to the [changelog section](https://docs.openrewrite.org/changelog/)
in the OpenRewrite docs.

## How docs are published

Doc updates to the OpenRewrite docs and Moderne docs are normally driven by scheduled GitHub Actions in the respective repos, not by humans running this generator locally:

* [`openrewrite/rewrite-docs`](https://github.com/openrewrite/rewrite-docs/blob/master/.github/workflows/update-docs.yml) — runs nightly, checks out this repo, runs `./gradlew run`, copies the OpenRewrite outputs into `rewrite-docs`, commits, and pushes.
* [`moderneinc/moderne-docs`](https://github.com/moderneinc/moderne-docs/blob/main/.github/workflows/update-docs.yml) — runs nightly, same shape, but also installs Python and .NET so it can load all RPC-backed recipes (see [Prerequisites](#prerequisites) below).

Both workflows default to `-PlatestVersionsOnly=true`, which only refreshes the latest-versions files. To regenerate the full recipe catalog, trigger the workflow manually via `workflow_dispatch` with that input unchecked.

The CI workflow in *this* repo also only runs with `-PlatestVersionsOnly=true`, so the full generation path is exercised exclusively by the two workflows above.

## Usage

### Prerequisites

The generator loads TypeScript, Python, and C# recipes by spawning external RPC processes. To produce complete docs locally you need all of these on `PATH`:

* **Java 21** — JVM-based recipes
* **Node.js** — TypeScript recipes (`rewrite-javascript`, `rewrite-nodejs`, `rewrite-angular`, `rewrite-react`)
* **Python 3.10+ and `pip`** — Python recipes (`rewrite-python`, `rewrite-migrate-python`, `openrewrite-static-analysis`)
* **.NET SDK** — C# recipes (`recipes-code-quality`, `recipes-migrate-dotnet`, `recipes-tunit`, `recipes-csharp-core`)

If a toolchain is missing, the corresponding loader prints a warning and skips those recipes. The build still succeeds, so a local `./gradlew run` without all four toolchains silently produces incomplete docs. Most contributors don't have all four installed — if you need a complete regeneration, trigger the scheduled workflows above rather than running locally.

### Build and test

```shell
# Compile the project
./gradlew compileKotlin

# Run tests
./gradlew test

# Compile and test together
./gradlew compileKotlin compileTestKotlin test
```

### Generate all docs

```shell
./gradlew run
```

This writes OpenRewrite docs to `build/docs/` and Moderne docs to `build/moderne-docs/`. See [Prerequisites](#prerequisites) — without the full toolchain set, non-Java recipes are silently omitted.

### Create only latest versions files

```shell
./gradlew run -PlatestVersionsOnly=true
cp -r build/docs/*.md ../rewrite-docs/docs/reference/
cp -r build/docs/*.js ../rewrite-docs/src/plugins/
cp -r build/moderne-docs/*.md ../moderne-docs/docs/user-documentation/recipes/
cp -r build/moderne-docs/*.js ../moderne-docs/src/plugins/
```

### Create Markdown files in a specific directory

```shell
./gradlew run --args="desired/output/path"
```

### Print additional options

```shell
./gradlew run --args="--help"
```

### Update rewrite-docs

Assumes you have `rewrite-docs` checked out in the same parent directory as `rewrite-recipe-markdown-generator`.

```shell
./gradlew run
rm -rf ../rewrite-docs/docs/recipes/
mv build/docs/*-Release.md ../rewrite-docs/docs/changelog/
cp -r build/docs/recipes ../rewrite-docs/docs/recipes
cp -r build/docs/*.md ../rewrite-docs/docs/reference/
cp -r build/docs/*.js ../rewrite-docs/src/plugins/
```

#### Manual step

Update `../rewrite-docs/sidebars.ts` to include a link to the new changelog.

### Update moderne-docs

Assumes you have `moderne-docs` checked out in the same parent directory as `rewrite-recipe-markdown-generator`.

```shell
./gradlew run
rm -rf ../moderne-docs/docs/user-documentation/recipes/recipe-catalog/
cp -r build/moderne-docs/recipe-catalog ../moderne-docs/docs/user-documentation/recipes/recipe-catalog
cp -r build/moderne-docs/lists/* ../moderne-docs/docs/user-documentation/recipes/lists
```

### Commit the updated descriptors

`./gradlew run` rewrites `src/main/resources/recipeDescriptors.yml` with the latest recipe information. Commit and push that change so the next run can diff against it to produce an accurate added/changed/removed list in the CHANGELOG.

```shell
git add src/main/resources/recipeDescriptors.yml
git commit -m "Update recipeDescriptors.yml"
git push
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.
