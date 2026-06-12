package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.config.BrandingConfig
import org.openrewrite.config.GeneratorConfig
import org.openrewrite.config.PackageRule
import org.openrewrite.config.RecipePackageConfig
import java.io.File
import java.nio.file.Path

class ConfigTest {

    @Test
    fun brandingConfigDefaults() {
        val config = BrandingConfig()
        assertThat(config.organizationName).isEqualTo("OpenRewrite")
        assertThat(config.docsBaseUrl).isEqualTo("https://docs.openrewrite.org")
        assertThat(config.platformName).isEqualTo("Moderne")
        assertThat(config.showPlatformSections).isTrue()
        assertThat(config.githubOrgUrl).isEqualTo("https://github.com/openrewrite")
    }

    @Test
    fun brandingConfigLoadReturnsDefaultForNullFile() {
        val config = BrandingConfig.load(null)
        assertThat(config).isEqualTo(BrandingConfig())
    }

    @Test
    fun brandingConfigLoadReturnsDefaultForNonExistentFile() {
        val config = BrandingConfig.load(File("/nonexistent/path/branding.yml"))
        assertThat(config).isEqualTo(BrandingConfig())
    }

    @Test
    fun brandingConfigLoadFromYaml(@TempDir tempDir: Path) {
        val yamlFile = tempDir.resolve("branding.yml").toFile()
        yamlFile.writeText("""
            organizationName: "TestOrg"
            docsBaseUrl: "https://docs.testorg.com"
            platformName: "TestPlatform"
        """.trimIndent())

        val config = BrandingConfig.load(yamlFile)
        assertThat(config.organizationName).isEqualTo("TestOrg")
        assertThat(config.docsBaseUrl).isEqualTo("https://docs.testorg.com")
        assertThat(config.platformName).isEqualTo("TestPlatform")
    }

    @Test
    fun recipePackageConfigDefaults() {
        val config = RecipePackageConfig()
        assertThat(config.packageRules).isNotEmpty
        assertThat(config.coreLibraries).contains("rewrite-core", "rewrite-java", "rewrite-yaml")
        assertThat(config.pathRemappings).containsKey("org.openrewrite.java.testing.assertj.Assertj")
    }

    @Test
    fun recipePackageConfigLoadReturnsDefaultForNull() {
        val config = RecipePackageConfig.load(null)
        assertThat(config).isEqualTo(RecipePackageConfig())
    }

    @Test
    fun recipePackageConfigLoadReturnsDefaultForNonExistent() {
        val config = RecipePackageConfig.load(File("/nonexistent/path/recipe-packages.yml"))
        assertThat(config).isEqualTo(RecipePackageConfig())
    }

    @Test
    fun defaultPackageRulesContainOpenRewrite() {
        val rules = RecipePackageConfig.defaultPackageRules()
        assertThat(rules).anyMatch { it.prefix == "org.openrewrite" && it.stripSegments == 2 }
    }

    @Test
    fun defaultPackageRulesContainModerne() {
        val rules = RecipePackageConfig.defaultPackageRules()
        assertThat(rules).anyMatch { it.prefix == "io.moderne" && it.stripSegments == 2 }
    }

    @Test
    fun defaultCoreLibrariesIncludeExpectedArtifacts() {
        val coreLibs = RecipePackageConfig.defaultCoreLibraries()
        assertThat(coreLibs).containsAll(listOf(
            "rewrite-core", "rewrite-gradle", "rewrite-java",
            "rewrite-maven", "rewrite-yaml", "rewrite-json"
        ))
    }

    @Test
    fun generatorConfigLoadReturnsDefaultForNull() {
        val config = GeneratorConfig.load(null)
        assertThat(config).isEqualTo(GeneratorConfig())
    }

    @Test
    fun generatorConfigLoadReturnsDefaultForNonExistentDir() {
        val config = GeneratorConfig.load(File("/nonexistent/config/dir"))
        assertThat(config).isEqualTo(GeneratorConfig())
    }

    @Test
    fun generatorConfigLoadFromDirectory(@TempDir tempDir: Path) {
        val configDir = tempDir.toFile()
        configDir.resolve("branding.yml").writeText("""
            organizationName: "CustomOrg"
        """.trimIndent())

        val config = GeneratorConfig.load(configDir)
        assertThat(config.branding.organizationName).isEqualTo("CustomOrg")
        // recipe-packages.yml doesn't exist, so defaults are used
        assertThat(config.recipePackages).isEqualTo(RecipePackageConfig())
    }

    @Test
    fun packageRuleDataClass() {
        val rule = PackageRule("org.test", stripSegments = 3, pathPrefix = "test/", coreShortPath = true)
        assertThat(rule.prefix).isEqualTo("org.test")
        assertThat(rule.stripSegments).isEqualTo(3)
        assertThat(rule.pathPrefix).isEqualTo("test/")
        assertThat(rule.coreShortPath).isTrue()
    }

    @Test
    fun packageRuleDefaultValues() {
        val rule = PackageRule("org.test")
        assertThat(rule.stripSegments).isEqualTo(2)
        assertThat(rule.pathPrefix).isEqualTo("")
        assertThat(rule.coreShortPath).isFalse()
    }
}
