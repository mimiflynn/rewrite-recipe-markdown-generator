package org.openrewrite.config

import java.io.File

/**
 * Unified configuration loader that combines all configuration sources.
 */
data class GeneratorConfig(
    val recipePackages: RecipePackageConfig = RecipePackageConfig(),
    val branding: BrandingConfig = BrandingConfig()
) {
    companion object {
        /**
         * Load configuration from a directory. Looks for:
         * - recipe-packages.yml
         * - branding.yml
         */
        fun load(configDir: File?): GeneratorConfig {
            if (configDir == null || !configDir.exists()) {
                return GeneratorConfig()
            }
            val recipePackages = RecipePackageConfig.load(
                configDir.resolve("recipe-packages.yml")
            )
            val branding = BrandingConfig.load(
                configDir.resolve("branding.yml")
            )
            return GeneratorConfig(recipePackages, branding)
        }
    }
}
