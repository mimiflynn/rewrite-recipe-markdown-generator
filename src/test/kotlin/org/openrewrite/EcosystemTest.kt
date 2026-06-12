package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.config.Ecosystem

class EcosystemTest {

    @Test
    fun detectJavaFromRecipeName() {
        val ecosystem = Ecosystem.detect("org.openrewrite.java.spring.AddSpringProperty", "rewrite-spring")
        assertThat(ecosystem).isEqualTo(Ecosystem.JAVA)
    }

    @Test
    fun detectPythonFromRecipeName() {
        val ecosystem = Ecosystem.detect("org.openrewrite.python.cleanup.SomeRecipe", "rewrite-python")
        assertThat(ecosystem).isEqualTo(Ecosystem.PYTHON)
    }

    @Test
    fun detectPythonFromArtifactId() {
        val ecosystem = Ecosystem.detect("org.openrewrite.some.Recipe", "rewrite-migrate-python")
        assertThat(ecosystem).isEqualTo(Ecosystem.PYTHON)
    }

    @Test
    fun detectJavaScriptFromRecipeName() {
        val ecosystem = Ecosystem.detect("org.openrewrite.javascript.SomeRecipe", "rewrite-javascript")
        assertThat(ecosystem).isEqualTo(Ecosystem.JAVASCRIPT)
    }

    @Test
    fun detectJavaScriptFromNodejsRecipeName() {
        val ecosystem = Ecosystem.detect("org.openrewrite.nodejs.SomeRecipe", "rewrite-nodejs")
        assertThat(ecosystem).isEqualTo(Ecosystem.JAVASCRIPT)
    }

    @Test
    fun detectJavaScriptFromCodemodsArtifact() {
        val ecosystem = Ecosystem.detect("org.openrewrite.some.Recipe", "rewrite-codemods")
        assertThat(ecosystem).isEqualTo(Ecosystem.JAVASCRIPT)
    }

    @Test
    fun detectCSharpFromRecipeName() {
        val ecosystem = Ecosystem.detect("org.openrewrite.csharp.cleanup.SomeRecipe", "recipes-code-quality")
        assertThat(ecosystem).isEqualTo(Ecosystem.CSHARP)
    }

    @Test
    fun detectCSharpFromDotnetRecipeName() {
        val ecosystem = Ecosystem.detect("org.openrewrite.dotnet.SomeRecipe", "recipes-migrate-dotnet")
        assertThat(ecosystem).isEqualTo(Ecosystem.CSHARP)
    }

    @Test
    fun detectCSharpFromCSharpArtifact() {
        val ecosystem = Ecosystem.detect("org.openrewrite.some.Recipe", "recipes-csharp-core")
        assertThat(ecosystem).isEqualTo(Ecosystem.CSHARP)
    }

    @Test
    fun detectCSharpFromDotnetArtifact() {
        val ecosystem = Ecosystem.detect("org.openrewrite.some.Recipe", "recipes-migrate-dotnet")
        assertThat(ecosystem).isEqualTo(Ecosystem.CSHARP)
    }

    @Test
    fun defaultsToJavaForUnknown() {
        val ecosystem = Ecosystem.detect("com.example.SomeRecipe", "some-artifact")
        assertThat(ecosystem).isEqualTo(Ecosystem.JAVA)
    }

    @Test
    fun ecosystemProperties() {
        assertThat(Ecosystem.JAVA.displayName).isEqualTo("Java")
        assertThat(Ecosystem.JAVA.sourceExtension).isEqualTo(".java")
        assertThat(Ecosystem.JAVA.packageManager).isEqualTo("maven")

        assertThat(Ecosystem.PYTHON.displayName).isEqualTo("Python")
        assertThat(Ecosystem.PYTHON.sourceExtension).isEqualTo(".py")
        assertThat(Ecosystem.PYTHON.packageManager).isEqualTo("pip")

        assertThat(Ecosystem.JAVASCRIPT.displayName).isEqualTo("JavaScript")
        assertThat(Ecosystem.JAVASCRIPT.sourceExtension).isEqualTo(".ts")
        assertThat(Ecosystem.JAVASCRIPT.packageManager).isEqualTo("npm")

        assertThat(Ecosystem.CSHARP.displayName).isEqualTo("C#")
        assertThat(Ecosystem.CSHARP.sourceExtension).isEqualTo(".cs")
        assertThat(Ecosystem.CSHARP.packageManager).isEqualTo("nuget")
    }
}
