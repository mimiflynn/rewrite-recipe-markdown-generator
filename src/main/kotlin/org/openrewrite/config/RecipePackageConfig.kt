package org.openrewrite.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/**
 * Configuration for recipe package routing - maps package prefixes to doc path rules.
 */
data class PackageRule(
    /** The package prefix to match (e.g., "org.openrewrite") */
    val prefix: String,
    /** Number of package segments to strip from the beginning when creating the doc path */
    val stripSegments: Int = 2,
    /** Optional path prefix to add (e.g., "core/") */
    val pathPrefix: String = "",
    /** Whether recipes with only 2 dots are treated as core recipes */
    val coreShortPath: Boolean = false
)

data class RecipePackageConfig(
    /** Ordered list of package prefix rules for path routing */
    val packageRules: List<PackageRule> = defaultPackageRules(),
    /** Artifact IDs that are core libraries (no explicit dependency needed) */
    val coreLibraries: Set<String> = defaultCoreLibraries(),
    /** Special recipe path remappings (recipe name -> custom path) */
    val pathRemappings: Map<String, String> = defaultPathRemappings(),
    /** Recipe name -> edition suffix (for recipes with multiple editions) */
    val editionSuffixes: Map<String, String> = defaultEditionSuffixes()
) {
    companion object {
        fun load(configFile: File?): RecipePackageConfig {
            if (configFile == null || !configFile.exists()) {
                return RecipePackageConfig()
            }
            val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
            return mapper.readValue(configFile)
        }

        fun defaultPackageRules(): List<PackageRule> = listOf(
            PackageRule("org.openrewrite", stripSegments = 2, coreShortPath = true),
            PackageRule("io.moderne", stripSegments = 2),
            PackageRule("ai.timefold", stripSegments = 0),
            PackageRule("com.oracle", stripSegments = 0),
            PackageRule("io.quarkus", stripSegments = 0),
            PackageRule("io.quakus", stripSegments = 0),
            PackageRule("org.apache", stripSegments = 0),
            PackageRule("org.axonframework", stripSegments = 0),
            PackageRule("software.amazon.awssdk", stripSegments = 0),
            PackageRule("tech.picnic", stripSegments = 0)
        )

        fun defaultCoreLibraries(): Set<String> = setOf(
            "rewrite-core",
            "rewrite-gradle",
            "rewrite-groovy",
            "rewrite-hcl",
            "rewrite-java",
            "rewrite-java-test",
            "rewrite-json",
            "rewrite-kotlin",
            "rewrite-maven",
            "rewrite-properties",
            "rewrite-protobuf",
            "rewrite-test",
            "rewrite-toml",
            "rewrite-xml",
            "rewrite-yaml"
        )

        fun defaultPathRemappings(): Map<String, String> = mapOf(
            "org.openrewrite.java.testing.assertj.Assertj" to "java/testing/assertj/assertj-best-practices",
            "org.openrewrite.java.migrate.javaee7" to "java/migrate/javaee7-recipe",
            "org.openrewrite.java.migrate.javaee8" to "java/migrate/javaee8-recipe"
        )

        fun defaultEditionSuffixes(): Map<String, String> = mapOf(
            "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4" to " (Moderne Edition)",
            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4" to " (Community Edition)"
        )
    }
}
