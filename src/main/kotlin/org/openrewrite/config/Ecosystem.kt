package org.openrewrite.config

/**
 * Represents the programming language ecosystem a recipe targets.
 */
enum class Ecosystem(
    val displayName: String,
    val sourceExtension: String,
    val sourcePathPrefix: String,
    val packageManager: String,
    val installCommand: String
) {
    JAVA("Java", ".java", "src/main/java/", "maven", "mvn dependency:resolve"),
    PYTHON("Python", ".py", "src/", "pip", "pip install"),
    JAVASCRIPT("JavaScript", ".ts", "src/", "npm", "npm install"),
    CSHARP("C#", ".cs", "src/", "nuget", "dotnet add package");

    companion object {
        /**
         * Detect ecosystem from recipe name or artifact metadata.
         */
        fun detect(recipeName: String, artifactId: String): Ecosystem {
            return when {
                recipeName.contains(".csharp.") || recipeName.contains(".dotnet.") ||
                        artifactId.contains("csharp") || artifactId.contains("dotnet") -> CSHARP
                recipeName.contains(".python.") || artifactId.contains("python") -> PYTHON
                recipeName.contains(".nodejs.") || recipeName.contains(".javascript.") ||
                        artifactId.contains("nodejs") || artifactId.contains("javascript") ||
                        artifactId.contains("codemods") -> JAVASCRIPT
                else -> JAVA
            }
        }
    }
}
