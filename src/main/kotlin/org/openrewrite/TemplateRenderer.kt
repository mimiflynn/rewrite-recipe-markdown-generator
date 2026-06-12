package org.openrewrite

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Simple template renderer that supports Mustache-like variable substitution.
 * Templates use {{variableName}} for simple substitution and {{#section}}...{{/section}} for conditional blocks.
 * 
 * Templates are loaded from:
 * 1. User-specified templates directory (highest priority)
 * 2. Built-in resources/templates (default fallback)
 */
class TemplateRenderer(
    private val customTemplatesDir: File? = null
) {
    private val templateCache = mutableMapOf<String, String>()

    /**
     * Render a template with the given context variables.
     */
    fun render(templateName: String, context: Map<String, Any?>): String {
        val template = loadTemplate(templateName)
        return processTemplate(template, context)
    }

    /**
     * Check if a template exists (either custom or built-in).
     */
    fun hasTemplate(templateName: String): Boolean {
        if (customTemplatesDir != null) {
            val customFile = customTemplatesDir.resolve(templateName)
            if (customFile.exists()) return true
        }
        return this::class.java.getResource("/templates/$templateName") != null
    }

    private fun loadTemplate(templateName: String): String {
        return templateCache.getOrPut(templateName) {
            // Try custom templates directory first
            if (customTemplatesDir != null) {
                val customFile = customTemplatesDir.resolve(templateName)
                if (customFile.exists()) {
                    return@getOrPut customFile.readText()
                }
            }
            // Fall back to built-in resources
            val resource = this::class.java.getResource("/templates/$templateName")
                ?: throw IllegalArgumentException("Template not found: $templateName")
            resource.readText()
        }
    }

    companion object {
        /**
         * Process a template string with the given context.
         * Supports:
         * - {{variable}} - simple substitution
         * - {{#condition}}content{{/condition}} - conditional blocks (shown if truthy)
         * - {{^condition}}content{{/condition}} - inverted blocks (shown if falsy)
         * - {{#list}}content{{/list}} - list iteration (if value is a List)
         */
        fun processTemplate(template: String, context: Map<String, Any?>): String {
            var result = template

            // Process conditional/inverted sections
            result = processSections(result, context)

            // Process simple variable substitution
            result = processVariables(result, context)

            return result
        }

        private fun processSections(template: String, context: Map<String, Any?>): String {
            var result = template

            // Process positive sections {{#key}}...{{/key}}
            val sectionRegex = Regex("\\{\\{#(\\w+)}}(.*?)\\{\\{/(\\1)}}",  RegexOption.DOT_MATCHES_ALL)
            result = sectionRegex.replace(result) { match ->
                val key = match.groupValues[1]
                val content = match.groupValues[2]
                val value = context[key]
                when {
                    value is List<*> -> {
                        value.joinToString("") { item ->
                            if (item is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                processTemplate(content, item as Map<String, Any?>)
                            } else {
                                processTemplate(content, context + mapOf("." to item))
                            }
                        }
                    }
                    isTruthy(value) -> processTemplate(content, context)
                    else -> ""
                }
            }

            // Process inverted sections {{^key}}...{{/key}}
            val invertedRegex = Regex("\\{\\{\\^(\\w+)}}(.*?)\\{\\{/(\\1)}}", RegexOption.DOT_MATCHES_ALL)
            result = invertedRegex.replace(result) { match ->
                val key = match.groupValues[1]
                val content = match.groupValues[2]
                val value = context[key]
                if (!isTruthy(value)) processTemplate(content, context) else ""
            }

            return result
        }

        private fun processVariables(template: String, context: Map<String, Any?>): String {
            val varRegex = Regex("\\{\\{(\\w+(\\.\\w+)*)}}")
            return varRegex.replace(template) { match ->
                val key = match.groupValues[1]
                resolveValue(key, context)?.toString() ?: match.value
            }
        }

        private fun resolveValue(key: String, context: Map<String, Any?>): Any? {
            if (context.containsKey(key)) return context[key]
            // Support dotted notation
            val parts = key.split(".")
            var current: Any? = context
            for (part in parts) {
                current = when (current) {
                    is Map<*, *> -> current[part]
                    else -> return null
                }
            }
            return current
        }

        private fun isTruthy(value: Any?): Boolean = when (value) {
            null -> false
            is Boolean -> value
            is String -> value.isNotEmpty()
            is Collection<*> -> value.isNotEmpty()
            is Number -> value.toInt() != 0
            else -> true
        }
    }
}
