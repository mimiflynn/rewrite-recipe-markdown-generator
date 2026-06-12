package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class LicensesTest {

    @Test
    fun apache2LicenseMarkdown() {
        assertThat(Licenses.Apache2.markdown())
            .isEqualTo("[Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)")
    }

    @Test
    fun proprietaryLicenseMarkdown() {
        assertThat(Licenses.Proprietary.markdown())
            .isEqualTo("[Moderne Proprietary License](https://docs.moderne.io/licensing/overview)")
    }

    @Test
    fun msalLicenseMarkdown() {
        assertThat(Licenses.MSAL.markdown())
            .isEqualTo("[Moderne Source Available License](https://docs.moderne.io/licensing/moderne-source-available-license)")
    }

    @Test
    fun unknownLicenseMarkdownHasNoLink() {
        assertThat(Licenses.Unknown.markdown()).isEqualTo("License Unknown")
    }

    @Test
    fun getReturnsApache2ForApacheUrl() {
        val license = Licenses.get("https://www.apache.org/licenses/LICENSE-2.0", null)
        assertThat(license).isEqualTo(Licenses.Apache2)
    }

    @Test
    fun getReturnsMSALForMSALUrl() {
        val license = Licenses.get("https://docs.moderne.io/licensing/moderne-source-available-license", null)
        assertThat(license).isEqualTo(Licenses.MSAL)
    }

    @Test
    fun getReturnsProprietaryForProprietaryUrl() {
        val license = Licenses.get("https://docs.moderne.io/licensing/overview", null)
        assertThat(license).isEqualTo(Licenses.Proprietary)
    }

    @Test
    fun getReturnsUnknownForNullUrl() {
        val license = Licenses.get(null, "Some License")
        assertThat(license).isEqualTo(Licenses.Unknown)
    }

    @Test
    fun getReturnsCustomLicenseForUnknownUrl() {
        val license = Licenses.get("https://example.com/license", "Custom License")
        assertThat(license.name).isEqualTo("Custom License")
        assertThat(license.uri).isEqualTo(URI("https://example.com/license"))
    }

    @Test
    fun getReturnsUnknownForUnknownUrlWithNullName() {
        val license = Licenses.get("https://example.com/license", null)
        assertThat(license).isEqualTo(Licenses.Unknown)
    }

    @Test
    fun licenseMarkdownWithEmptyUri() {
        val license = License(URI(""), "No URI License")
        assertThat(license.markdown()).isEqualTo("No URI License")
    }

    @Test
    fun licenseMarkdownWithNonEmptyUri() {
        val license = License(URI("https://example.com"), "Example")
        assertThat(license.markdown()).isEqualTo("[Example](https://example.com)")
    }
}
