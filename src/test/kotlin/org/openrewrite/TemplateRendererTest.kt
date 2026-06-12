package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TemplateRendererTest {

    @Test
    fun simpleVariableSubstitution() {
        val result = TemplateRenderer.processTemplate(
            "Hello, {{name}}!",
            mapOf("name" to "World")
        )
        assertThat(result).isEqualTo("Hello, World!")
    }

    @Test
    fun multipleVariables() {
        val result = TemplateRenderer.processTemplate(
            "{{greeting}}, {{name}}!",
            mapOf("greeting" to "Hi", "name" to "Alice")
        )
        assertThat(result).isEqualTo("Hi, Alice!")
    }

    @Test
    fun missingVariableLeftUnchanged() {
        val result = TemplateRenderer.processTemplate(
            "Hello, {{name}}!",
            mapOf("other" to "value")
        )
        assertThat(result).isEqualTo("Hello, {{name}}!")
    }

    @Test
    fun conditionalSectionShownWhenTruthy() {
        val result = TemplateRenderer.processTemplate(
            "Start{{#show}} visible{{/show}} end",
            mapOf("show" to true)
        )
        assertThat(result).isEqualTo("Start visible end")
    }

    @Test
    fun conditionalSectionHiddenWhenFalsy() {
        val result = TemplateRenderer.processTemplate(
            "Start{{#show}} hidden{{/show}} end",
            mapOf("show" to false)
        )
        assertThat(result).isEqualTo("Start end")
    }

    @Test
    fun conditionalSectionHiddenWhenNull() {
        val result = TemplateRenderer.processTemplate(
            "Start{{#show}} hidden{{/show}} end",
            mapOf("show" to null)
        )
        assertThat(result).isEqualTo("Start end")
    }

    @Test
    fun conditionalSectionWithNonEmptyString() {
        val result = TemplateRenderer.processTemplate(
            "{{#title}}Title: {{title}}{{/title}}",
            mapOf("title" to "My Page")
        )
        assertThat(result).isEqualTo("Title: My Page")
    }

    @Test
    fun conditionalSectionWithEmptyString() {
        val result = TemplateRenderer.processTemplate(
            "{{#title}}Title: {{title}}{{/title}}",
            mapOf("title" to "")
        )
        assertThat(result).isEqualTo("")
    }

    @Test
    fun invertedSectionShownWhenFalsy() {
        val result = TemplateRenderer.processTemplate(
            "{{^show}}no content{{/show}}",
            mapOf("show" to false)
        )
        assertThat(result).isEqualTo("no content")
    }

    @Test
    fun invertedSectionHiddenWhenTruthy() {
        val result = TemplateRenderer.processTemplate(
            "{{^show}}no content{{/show}}",
            mapOf("show" to true)
        )
        assertThat(result).isEqualTo("")
    }

    @Test
    fun listIteration() {
        val result = TemplateRenderer.processTemplate(
            "{{#items}}- {{name}}\n{{/items}}",
            mapOf("items" to listOf(
                mapOf("name" to "Apple"),
                mapOf("name" to "Banana")
            ))
        )
        assertThat(result).isEqualTo("- Apple\n- Banana\n")
    }

    @Test
    fun emptyListProducesNoOutput() {
        val result = TemplateRenderer.processTemplate(
            "{{#items}}item{{/items}}",
            mapOf("items" to emptyList<String>())
        )
        assertThat(result).isEqualTo("")
    }

    @Test
    fun dottedNotationResolvesNestedValues() {
        val result = TemplateRenderer.processTemplate(
            "{{user.name}}",
            mapOf("user" to mapOf("name" to "Alice"))
        )
        assertThat(result).isEqualTo("Alice")
    }

    @Test
    fun truthyNumberIsNonZero() {
        val result = TemplateRenderer.processTemplate(
            "{{#count}}has items{{/count}}",
            mapOf("count" to 5)
        )
        assertThat(result).isEqualTo("has items")
    }

    @Test
    fun zeroNumberIsFalsy() {
        val result = TemplateRenderer.processTemplate(
            "{{#count}}has items{{/count}}",
            mapOf("count" to 0)
        )
        assertThat(result).isEqualTo("")
    }

    @Test
    fun hasTemplateReturnsFalseForNonExistent() {
        val renderer = TemplateRenderer()
        assertThat(renderer.hasTemplate("nonexistent-template.md")).isFalse()
    }

    @Test
    fun renderThrowsForMissingTemplate() {
        val renderer = TemplateRenderer()
        assertThrows<IllegalArgumentException> {
            renderer.render("nonexistent-template.md", emptyMap())
        }
    }
}
