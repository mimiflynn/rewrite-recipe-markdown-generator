package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecipeOptionTest {

    @Test
    fun compareByNameFirst() {
        val opt1 = RecipeOption("alpha", "String", true)
        val opt2 = RecipeOption("beta", "String", true)
        assertThat(opt1.compareTo(opt2)).isLessThan(0)
        assertThat(opt2.compareTo(opt1)).isGreaterThan(0)
    }

    @Test
    fun compareByTypeWhenNamesSame() {
        val opt1 = RecipeOption("name", "Boolean", true)
        val opt2 = RecipeOption("name", "String", true)
        assertThat(opt1.compareTo(opt2)).isLessThan(0)
    }

    @Test
    fun compareByRequiredWhenNameAndTypeSame() {
        val opt1 = RecipeOption("name", "String", false)
        val opt2 = RecipeOption("name", "String", true)
        assertThat(opt1.compareTo(opt2)).isLessThan(0)
    }

    @Test
    fun equalOptionsCompareToZero() {
        val opt1 = RecipeOption("name", "String", true)
        val opt2 = RecipeOption("name", "String", true)
        assertThat(opt1.compareTo(opt2)).isEqualTo(0)
    }

    @Test
    fun dataClassEquality() {
        val opt1 = RecipeOption("name", "String", true)
        val opt2 = RecipeOption("name", "String", true)
        assertThat(opt1).isEqualTo(opt2)
    }

    @Test
    fun dataClassInequality() {
        val opt1 = RecipeOption("name1", "String", true)
        val opt2 = RecipeOption("name2", "String", true)
        assertThat(opt1).isNotEqualTo(opt2)
    }

    @Test
    fun sortingInTreeSet() {
        val options = sortedSetOf(
            RecipeOption("zeta", "String", true),
            RecipeOption("alpha", "String", true),
            RecipeOption("middle", "Integer", false)
        )
        assertThat(options.first().name).isEqualTo("alpha")
        assertThat(options.last().name).isEqualTo("zeta")
    }
}
