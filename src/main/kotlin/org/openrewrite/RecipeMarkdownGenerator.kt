@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.BrandingConfig
import org.openrewrite.config.DeclarativeRecipe
import org.openrewrite.config.GeneratorConfig
import org.openrewrite.config.RecipeDescriptor
import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Option
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.system.exitProcess

@Command(
    name = "rewrite-recipe-markdown-generator",
    mixinStandardHelpOptions = true,
    description = ["Generates documentation for OpenRewrite recipes in markdown format"],
    version = ["1.0.0-SNAPSHOT"]
)
class RecipeMarkdownGenerator : Runnable {
    @Parameters(index = "0", description = ["Destination directory for generated recipe markdown"])
    lateinit var destinationDirectoryName: String

    @Parameters(
        index = "1", defaultValue = "", description = ["A ';' delineated list of coordinates to search for recipes. " +
                "Each entry in the list must be of format groupId:artifactId:version:path where 'path' is a file path to the jar"]
    )
    lateinit var recipeSources: String

    @Parameters(
        index = "2", defaultValue = "", description = ["A ';' delineated list of jars that provide the full " +
                "transitive dependency list for the recipeSources"]
    )
    lateinit var recipeClasspath: String

    @Parameters(
        index = "3",
        defaultValue = "latest.release",
        description = ["The version of the rewrite-bom to display on the changelog page"]
    )
    lateinit var rewriteBomVersion: String

    @Parameters(
        index = "4",
        defaultValue = "latest.release",
        description = ["The version of the rewrite-recipe-bom to display on all module versions page"]
    )
    lateinit var rewriteRecipeBomVersion: String

    @Parameters(
        index = "5",
        defaultValue = "latest.release",
        description = ["The version of the moderne-recipe-bom to display on all module versions page"]
    )
    lateinit var moderneRecipeBomVersion: String

    @Parameters(
        index = "6",
        defaultValue = "latest.release",
        description = ["The version of the Rewrite Gradle Plugin to display in relevant samples"]
    )
    lateinit var gradlePluginVersion: String

    @Parameters(
        index = "7",
        defaultValue = "",
        description = ["The version of the Rewrite Maven Plugin to display in relevant samples"]
    )
    lateinit var mavenPluginVersion: String

    @Parameters(
        index = "8",
        defaultValue = "",
        description = ["Secondary destination directory for Moderne docs (contains all recipes including proprietary)"]
    )
    lateinit var moderneDestinationDirectoryName: String

    @Option(names = ["--latest-versions-only"])
    var latestVersionsOnly: Boolean = false

    @Option(names = ["--config-dir"], description = ["Path to configuration directory containing recipe-packages.yml and branding.yml"])
    var configDir: String? = null

    @Option(names = ["--templates-dir"], description = ["Path to custom templates directory for markdown generation"])
    var templatesDir: String? = null

    override fun run() {
        // Load configuration
        val config = GeneratorConfig.load(configDir?.let { File(it) })
        generatorConfig = config

        val outputPath = Paths.get(destinationDirectoryName)
        val recipesPath = outputPath.resolve("recipes")

        // Moderne docs output (ALL recipes including proprietary)
        val moderneOutputPath = if (moderneDestinationDirectoryName.isNotEmpty()) {
            Paths.get(moderneDestinationDirectoryName)
        } else {
            null
        }
        val moderneRecipeCatalogPath = moderneOutputPath?.resolve("recipe-catalog")
        val moderneListsPath = moderneOutputPath?.resolve("lists")

        try {
            Files.createDirectories(recipesPath)
            moderneRecipeCatalogPath?.let { Files.createDirectories(it) }
            moderneListsPath?.let { Files.createDirectories(it) }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        var recipeOrigins: Map<URI, RecipeOrigin> = RecipeOrigin.parse(recipeSources)

        // Add manifest information
        val recipeLoader = RecipeLoader(recipeClasspath, recipeOrigins)
        recipeLoader.addInfosFromManifests()

        // Write latest-versions-of-every-openrewrite-module.md, for all recipe modules
        val versionWriter = VersionWriter(config.branding)
        versionWriter.createLatestVersionsJs(
            outputPath,
            recipeOrigins.values,
            rewriteRecipeBomVersion,
            gradlePluginVersion,
            mavenPluginVersion
        )
        versionWriter.createLatestVersionsMarkdown(
            outputPath,
            recipeOrigins.values,
            rewriteBomVersion,
            rewriteRecipeBomVersion,
            moderneRecipeBomVersion,
            gradlePluginVersion,
            mavenPluginVersion
        )
        // Moderne docs
        if (moderneOutputPath != null) {
            versionWriter.createLatestVersionsJs(
                moderneOutputPath,
                recipeOrigins.values,
                rewriteRecipeBomVersion,
                gradlePluginVersion,
                mavenPluginVersion
            )
            versionWriter.createLatestVersionsMarkdown(
                moderneOutputPath,
                recipeOrigins.values,
                rewriteBomVersion,
                rewriteRecipeBomVersion,
                moderneRecipeBomVersion,
                gradlePluginVersion,
                mavenPluginVersion
            )
        }

        if (latestVersionsOnly) {
            return
        }

        // Load recipe details into memory
        val loadResult = recipeLoader.loadRecipes()
        val allRecipeDescriptors = loadResult.allRecipeDescriptors
        val allCategoryDescriptors = loadResult.allCategoryDescriptors
        val allRecipes = loadResult.allRecipes
        val recipeToSource = loadResult.recipeToSource
        val crossCategoryPaths = loadResult.crossCategoryPaths

        // Merge synthetic origins from Python/TypeScript recipe loaders
        if (loadResult.additionalOrigins.isNotEmpty()) {
            recipeOrigins = recipeOrigins + loadResult.additionalOrigins
        }

        // Python recipes are always proprietary
        recipeOrigins.values
            .filter { it.artifactId in PythonRecipeLoader.PYTHON_RECIPE_MODULES }
            .forEach { it.license = Licenses.Proprietary }

        // C# recipes are always proprietary
        recipeOrigins.values
            .filter { it.artifactId in CSharpRecipeLoader.CSHARP_RECIPE_MODULES }
            .forEach { it.license = Licenses.Proprietary }

        println("Found ${allRecipeDescriptors.size} descriptor(s).")

        // Detect conflicting paths between io.moderne and org.openrewrite recipes
        initializeConflictDetection(allRecipeDescriptors)

        val markdownArtifacts = TreeMap<String, MarkdownRecipeArtifact>()
        val moderneOnlyRecipes = TreeMap<String, MutableList<RecipeDescriptor>>()

        // Build mapping from recipe name to Recipe instance (for checking if declarative)
        val recipesByName = allRecipes.associateBy { it.name }

        // Build reverse mapping of recipe relationships (which recipes contain each recipe)
        val recipeContainedBy = mutableMapOf<String, MutableSet<RecipeDescriptor>>()
        for (parentRecipe in allRecipeDescriptors) {
            if (parentRecipe.recipeList != null) {
                for (childRecipe in parentRecipe.recipeList) {
                    recipeContainedBy.computeIfAbsent(childRecipe.name) { mutableSetOf() }.add(parentRecipe)
                }
            }
        }

        // Export JSON before markdown generation
        val categories = Category.fromDescriptors(allRecipeDescriptors, allCategoryDescriptors)
            .sortedBy { it.simpleName }
        val jsonExporter = RecipeJsonExporter(config.recipePackages, config.branding)
        jsonExporter.export(outputPath, allRecipeDescriptors, recipeOrigins, recipeContainedBy, categories)

        // Create the recipe docs
        val templateRenderer = TemplateRenderer(templatesDir?.let { File(it) })
        val recipeMarkdownWriter = RecipeMarkdownWriter(recipeContainedBy, config.branding, templateRenderer)
        for (recipeDescriptor in allRecipeDescriptors) {
            var origin: RecipeOrigin?
            var rawUri = recipeDescriptor.source.toString()
            val exclamationIndex = rawUri.indexOf('!')
            if (exclamationIndex == -1) {
                origin = recipeOrigins[recipeDescriptor.source]
            } else {
                // The recipe origin includes the path to the recipe within a jar
                // Such URIs will look something like: jar:file:/path/to/the/recipes.jar!META-INF/rewrite/some-declarative.yml
                // Strip the "jar:" prefix and the part of the URI pointing inside the jar
                rawUri = rawUri.substring(0, exclamationIndex)
                rawUri = rawUri.substring(4)
                val jarOnlyUri = URI.create(rawUri)
                origin = recipeOrigins[jarOnlyUri]
            }
            .map { it.name }
            .toSet()

        // Create the recipe docs
        // We use two writers: one for OpenRewrite docs (open-source only), one for Moderne docs (all recipes)
        val recipeMarkdownWriter = RecipeMarkdownWriter(recipeContainedBy, recipeToSource, proprietaryRecipeNames, forModerneDocs = false)
        val moderneRecipeMarkdownWriter = if (moderneRecipeCatalogPath != null) {
            RecipeMarkdownWriter(recipeContainedBy, recipeToSource, proprietaryRecipeNames, forModerneDocs = true)
        } else null

        for (recipeDescriptor in allRecipeDescriptors) {
            val recipeSource = recipeToSource[recipeDescriptor.name]
            requireNotNull(recipeSource) { "Could not find source URI for recipe " + recipeDescriptor.name }

            val origin = findOrigin(recipeSource, recipeDescriptor.name, recipeOrigins)
            requireNotNull(origin) { "Could not find GAV coordinates of recipe " + recipeDescriptor.name + " from " + recipeSource }

            // Always write to Moderne docs (ALL recipes)
            moderneRecipeMarkdownWriter?.writeRecipe(recipeDescriptor, moderneRecipeCatalogPath!!, origin)

            // Write cross-category duplicates to Moderne docs
            val extraPaths = crossCategoryPaths[recipeDescriptor.name]
            if (extraPaths != null && moderneRecipeMarkdownWriter != null) {
                for (extraPath in extraPaths) {
                    moderneRecipeMarkdownWriter.writeRecipeTo(recipeDescriptor, moderneRecipeCatalogPath!!, origin, extraPath)
                }
            }

            // Track moderne-docs-only recipes separately (for moderne-recipes.md list and redirects)
            if (isModerneDocsOnly(origin)) {
                moderneOnlyRecipes.computeIfAbsent(origin.artifactId) { mutableListOf() }.add(recipeDescriptor)
                // Skip writing moderne-docs-only recipes to rewrite-docs
                continue
            }

            // Write non-proprietary recipes to OpenRewrite docs
            recipeMarkdownWriter.writeRecipe(recipeDescriptor, recipesPath, origin)

            // Write cross-category duplicates to OpenRewrite docs
            if (extraPaths != null) {
                for (extraPath in extraPaths) {
                    recipeMarkdownWriter.writeRecipeTo(recipeDescriptor, recipesPath, origin, extraPath)
                }
            }

            val recipeOptions = TreeSet<RecipeOption>()
            if (recipeDescriptor.options != null) {
                for (recipeOption in recipeDescriptor.options) {
                    // TypeScript recipes may have null name or type
                    val name = recipeOption.name ?: "unknown"
                    val type = recipeOption.type ?: "String"
                    val ro = RecipeOption(name, type, recipeOption.isRequired)
                    recipeOptions.add(ro)
                }
            }

            // Changes something like org.openrewrite.circleci.InstallOrb to {docsBaseUrl}/recipes/circleci/installorb
            val docLink = "${config.branding.docsBaseUrl}/recipes/" + getRecipePath(recipeDescriptor)
            val recipeSource = recipeDescriptor.source.toString()
            var isImperative = true

            // Determine if recipe is imperative (Java) or declarative (YAML)
            // Used to help with time spent calculations. Imperative = 12 hours, Declarative = 4 hours
            val recipe = recipesByName[recipeDescriptor.name]
            val isImperative = recipe !is DeclarativeRecipe

            // Used to create changelogs
            val markdownRecipeDescriptor =
                MarkdownRecipeDescriptor(
                    recipeDescriptor.name,
                    recipeDescriptor.description,
                    docLink,
                    recipeOptions,
                    isImperative,
                    origin.artifactId
                )
            val markdownArtifact = markdownArtifacts.computeIfAbsent(origin.artifactId) {
                MarkdownRecipeArtifact(
                    origin.artifactId,
                    origin.version,
                    TreeMap<String, MarkdownRecipeDescriptor>(),
                )
            }
            markdownArtifact.markdownRecipeDescriptors[recipeDescriptor.name] = markdownRecipeDescriptor
        }

        // Filter to only rewrite-docs recipes (exclude moderne-docs-only modules and proprietary)
        val openSourceRecipeDescriptors = allRecipeDescriptors.filter { recipe ->
            val source = recipeToSource[recipe.name]
            val origin = findOrigin(source, recipe.name, recipeOrigins)
            origin != null && !isModerneDocsOnly(origin)
        }
        println("Filtered to ${openSourceRecipeDescriptors.size} open-source recipe(s) for rewrite-docs.")

        // Write the README.md for each category
        // OpenRewrite docs: open-source only (in "recipes" subdir)
        CategoryWriter(openSourceRecipeDescriptors, allCategoryDescriptors, "/recipes", crossCategoryPaths)
            .writeCategories(outputPath, "recipes")
        // Moderne docs: ALL recipes (in "recipe-catalog" subdir to match workflow expectations)
        if (moderneOutputPath != null) {
            CategoryWriter(allRecipeDescriptors, allCategoryDescriptors, "/user-documentation/recipes/recipe-catalog", crossCategoryPaths)
                .writeCategories(moderneOutputPath, "recipe-catalog")
        }

        // Create changelog markdown, and update tracking file
        ChangelogWriter(config.branding).createRecipeDescriptorsYaml(
            markdownArtifacts,
            openSourceRecipeDescriptors.size,
            rewriteBomVersion,
            outputPath
        )

        // Write lists of recipes into various files
        val listWriter = ListsOfRecipesWriter(allRecipeDescriptors, outputPath, config.branding)
        listWriter.createModerneRecipes(moderneProprietaryRecipes)
        listWriter.createRecipesWithDataTables()
        listWriter.createRecipeAuthors()
        listWriter.createRecipesByTag()
        listWriter.createScanningRecipes(
            allRecipes.filter { recipe ->
                recipe is ScanningRecipe<*> && recipe !is DeclarativeRecipe &&
                openSourceRecipeDescriptors.any { it.name == recipe.name }
            },
            recipeOrigins,
            recipeToSource
        )
        listWriter.createStandaloneRecipes(recipeContainedBy, recipeOrigins, recipeToSource)
        listWriter.createAllRecipesByModule(recipeOrigins, recipeToSource)

        // Moderne docs: ALL recipes (links use /user-documentation/recipes/recipe-catalog path)
        if (moderneListsPath != null) {
            val moderneListWriter = ListsOfRecipesWriter(allRecipeDescriptors, moderneListsPath, "/user-documentation/recipes/recipe-catalog")
            moderneListWriter.createRecipesWithDataTables(recipeOrigins, recipeToSource)
            moderneListWriter.createRecipesByTag()
            moderneListWriter.createScanningRecipes(
                allRecipes.filter { recipe ->
                    recipe is ScanningRecipe<*> && recipe !is DeclarativeRecipe
                },
                recipeOrigins,
                recipeToSource
            )
            moderneListWriter.createStandaloneRecipes(recipeContainedBy, recipeOrigins, recipeToSource)
            moderneListWriter.createAllRecipesByModule(recipeOrigins, recipeToSource)
        }

        // Generate redirects for proprietary recipes (from old OpenRewrite URLs to Moderne docs)
        val allModerneOnlyRecipes = moderneOnlyRecipes.values.flatten()
        if (allModerneOnlyRecipes.isNotEmpty()) {
            RedirectWriter.writeRedirectConfig(
                outputPath,
                allModerneOnlyRecipes,
                "https://docs.moderne.io",
                "/user-documentation/recipes/recipe-catalog"
            )
        }

        // Generate redirects for categories that only exist in Moderne docs
        RedirectWriter.writeCategoryRedirects(
            outputPath,
            allRecipeDescriptors,
            openSourceRecipeDescriptors,
            "https://docs.moderne.io",
            "/user-documentation/recipes/recipe-catalog"
        )
    }


    companion object {
        // Mutable config reference for use in static getRecipePath method
        internal var generatorConfig: GeneratorConfig = GeneratorConfig()

        /**
         * Call Closable.use() together with apply() to avoid adding two levels of indentation
         */
        fun BufferedWriter.useAndApply(withFun: BufferedWriter.() -> Unit): Unit = use { it.apply(withFun) }

        fun BufferedWriter.writeln(text: String) {
            write(text)
            newLine()
        }

        /**
         * Converts a recipe name to its documentation path using configured package rules.
         */
        fun getRecipePath(recipe: RecipeDescriptor): String {
            val packageConfig = generatorConfig.recipePackages

            // Check path remappings first
            if (packageConfig.pathRemappings.containsKey(recipe.name)) {
                return packageConfig.pathRemappings[recipe.name]!!
            }
        }

            // Check edition suffixes for path disambiguation
            val editionSuffixes = packageConfig.editionSuffixes
            if (recipe.name == "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4") {
                return "java/spring/boot3/upgradespringboot_3_4-moderne-edition"
            } else if (recipe.name == "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4") {
                return "java/spring/boot3/upgradespringboot_3_4-community-edition"
            }

            // Iterate through configured package rules
            for (rule in packageConfig.packageRules) {
                if (recipe.name.startsWith(rule.prefix)) {
                    return when {
                        rule.coreShortPath && recipe.name.count { it == '.' } == 2 -> {
                            val stripped = recipe.name.substring(rule.prefix.length + 1)
                            "core/" + stripped.lowercase(Locale.getDefault())
                        }
                        rule.stripSegments == 0 -> {
                            recipe.name.replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
                        }
                        else -> {
                            // Strip the configured number of segments
                            val parts = recipe.name.split(".")
                            val stripped = parts.drop(rule.stripSegments).joinToString("/")
                            (rule.pathPrefix + stripped).lowercase(Locale.getDefault())
                        }
                    }
                }
            }

            throw RuntimeException("Recipe package unrecognized: ${recipe.name}")
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(RecipeMarkdownGenerator()).execute(*args)
            exitProcess(exitCode)
        }
    }
}
