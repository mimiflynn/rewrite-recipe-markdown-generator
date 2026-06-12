package org.openrewrite

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.openrewrite.config.BrandingConfig
import org.openrewrite.config.Ecosystem
import org.openrewrite.config.RecipePackageConfig
import org.openrewrite.config.RecipeDescriptor
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports all recipe documentation data as JSON before markdown generation.
 * This intermediate format contains all information needed to regenerate documentation
 * and can be consumed by downstream tools independently.
 */
class RecipeJsonExporter(
    private val packageConfig: RecipePackageConfig,
    private val brandingConfig: BrandingConfig
) {

    data class RecipeOptionJson(
        val name: String,
        val type: String?,
        val description: String?,
        val example: String?,
        val isRequired: Boolean,
        val valid: List<String>?
    )

    data class DataTableColumnJson(
        val name: String,
        val displayName: String,
        val description: String
    )

    data class DataTableJson(
        val name: String,
        val displayName: String,
        val description: String,
        val columns: List<DataTableColumnJson>
    )

    data class ExampleSourceJson(
        val path: String?,
        val language: String?,
        val before: String?,
        val after: String?
    )

    data class ExampleJson(
        val description: String,
        val parameters: List<String>,
        val sources: List<ExampleSourceJson>
    )

    data class ContributorJson(
        val name: String,
        val email: String
    )

    data class RecipeOriginJson(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val repositoryUrl: String,
        val license: String,
        val ecosystem: String
    )

    data class RecipeJson(
        val name: String,
        val displayName: String,
        val description: String?,
        val tags: List<String>,
        val options: List<RecipeOptionJson>,
        val origin: RecipeOriginJson,
        val recipeList: List<String>,
        val containedBy: List<String>,
        val examples: List<ExampleJson>,
        val dataTables: List<DataTableJson>,
        val contributors: List<ContributorJson>,
        val isImperative: Boolean,
        val docPath: String
    )

    data class CategoryJson(
        val path: String,
        val displayName: String,
        val description: String?,
        val subcategories: List<String>,
        val recipes: List<String>
    )

    data class RecipeDocumentationJson(
        val generatedAt: String,
        val branding: BrandingConfigJson,
        val recipes: List<RecipeJson>,
        val categories: List<CategoryJson>
    )

    data class BrandingConfigJson(
        val organizationName: String,
        val docsBaseUrl: String,
        val platformName: String
    )

    /**
     * Export all recipe data to a JSON file.
     */
    fun export(
        outputPath: Path,
        allRecipeDescriptors: List<RecipeDescriptor>,
        recipeOrigins: Map<URI, RecipeOrigin>,
        recipeContainedBy: Map<String, MutableSet<RecipeDescriptor>>,
        categories: List<Category>
    ) {
        val recipes = allRecipeDescriptors.map { descriptor ->
            val origin = findOrigin(descriptor, recipeOrigins)
            val ecosystem = if (origin != null) {
                Ecosystem.detect(descriptor.name, origin.artifactId)
            } else {
                Ecosystem.JAVA
            }

            val recipeSource = descriptor.source.toString()
            val isImperative = !recipeSource.endsWith("yml")

            RecipeJson(
                name = descriptor.name,
                displayName = descriptor.displayName,
                description = descriptor.description,
                tags = descriptor.tags.toList(),
                options = descriptor.options.map { option ->
                    RecipeOptionJson(
                        name = option.name,
                        type = option.type,
                        description = option.description,
                        example = option.example,
                        isRequired = option.isRequired,
                        valid = option.valid?.toList()
                    )
                },
                origin = if (origin != null) {
                    RecipeOriginJson(
                        groupId = origin.groupId,
                        artifactId = origin.artifactId,
                        version = origin.version,
                        repositoryUrl = origin.repositoryUrl,
                        license = origin.license.name,
                        ecosystem = ecosystem.name
                    )
                } else {
                    RecipeOriginJson("", "", "", "", "", ecosystem.name)
                },
                recipeList = descriptor.recipeList.map { it.name },
                containedBy = recipeContainedBy[descriptor.name]?.map { it.name } ?: emptyList(),
                examples = descriptor.examples.map { example ->
                    ExampleJson(
                        description = example.description,
                        parameters = example.parameters.toList(),
                        sources = example.sources.map { source ->
                            ExampleSourceJson(
                                path = source.path,
                                language = source.language,
                                before = source.before,
                                after = source.after
                            )
                        }
                    )
                },
                dataTables = descriptor.dataTables.map { dt ->
                    DataTableJson(
                        name = dt.name,
                        displayName = dt.displayName,
                        description = dt.description,
                        columns = dt.columns.map { col ->
                            DataTableColumnJson(
                                name = col.name,
                                displayName = col.displayName,
                                description = col.description
                            )
                        }
                    )
                },
                contributors = descriptor.contributors.map { c ->
                    ContributorJson(name = c.name, email = c.email)
                },
                isImperative = isImperative,
                docPath = RecipeMarkdownGenerator.getRecipePath(descriptor)
            )
        }

        val categoryJsons = flattenCategories(categories)

        val documentation = RecipeDocumentationJson(
            generatedAt = java.time.Instant.now().toString(),
            branding = BrandingConfigJson(
                organizationName = brandingConfig.organizationName,
                docsBaseUrl = brandingConfig.docsBaseUrl,
                platformName = brandingConfig.platformName
            ),
            recipes = recipes,
            categories = categoryJsons
        )

        val mapper = ObjectMapper()
            .registerKotlinModule()
            .enable(SerializationFeature.INDENT_OUTPUT)

        val jsonFile = outputPath.resolve("recipes.json")
        Files.createDirectories(outputPath)
        mapper.writeValue(jsonFile.toFile(), documentation)
        println("Exported ${recipes.size} recipes to ${jsonFile.toAbsolutePath()}")
    }

    private fun findOrigin(descriptor: RecipeDescriptor, recipeOrigins: Map<URI, RecipeOrigin>): RecipeOrigin? {
        var rawUri = descriptor.source.toString()
        val exclamationIndex = rawUri.indexOf('!')
        return if (exclamationIndex == -1) {
            recipeOrigins[descriptor.source]
        } else {
            rawUri = rawUri.substring(0, exclamationIndex)
            rawUri = rawUri.substring(4)
            val jarOnlyUri = URI.create(rawUri)
            recipeOrigins[jarOnlyUri]
        }
    }

    private fun flattenCategories(categories: List<Category>): List<CategoryJson> {
        val result = mutableListOf<CategoryJson>()
        for (category in categories) {
            result.add(
                CategoryJson(
                    path = category.path,
                    displayName = category.displayName,
                    description = category.descriptor?.description,
                    subcategories = category.subcategories.map { it.path },
                    recipes = category.recipes.map { it.name }
                )
            )
            result.addAll(flattenCategories(category.subcategories))
        }
        return result
    }
}
