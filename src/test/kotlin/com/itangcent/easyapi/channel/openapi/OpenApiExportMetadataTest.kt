package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.ExportMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Plain-JUnit tests for [OpenApiExportMetadata].
 *
 * Pins the contract:
 *  - `formatDisplay()` returns `"Format: JSON"` for JSON output and
 *    `"Format: YAML"` for YAML output.
 *  - `document` and `content` are stored unmodified (the channel layer
 *    pre-serializes the content so handleResult doesn't re-serialize).
 *  - The class implements [ExportMetadata] (carried in ExportResult.Success).
 *
 * Mirrors the [com.itangcent.easyapi.channel.hoppscotch.HoppscotchExportMetadata]
 * pattern (file at `channel/hoppscotch/HoppscotchExportMetadata.kt` is the
 * reference).
 */
class OpenApiExportMetadataTest {

    @Test
    fun `formatDisplay returns Format JSON for JSON output`() {
        val metadata = OpenApiExportMetadata(
            document = minimalDoc(),
            outputFormat = OpenApiOutputFormat.JSON,
            content = "{}",
        )
        assertEquals("Format: JSON", metadata.formatDisplay())
    }

    @Test
    fun `formatDisplay returns Format YAML for YAML output`() {
        val metadata = OpenApiExportMetadata(
            document = minimalDoc(),
            outputFormat = OpenApiOutputFormat.YAML,
            content = "openapi: \"3.0.3\"",
        )
        assertEquals("Format: YAML", metadata.formatDisplay())
    }

    @Test
    fun `document is stored unmodified`() {
        val doc = minimalDoc()
        val metadata = OpenApiExportMetadata(
            document = doc,
            outputFormat = OpenApiOutputFormat.JSON,
            content = "{}",
        )
        // Identity check — the exact same instance is returned, no copy.
        assertSame(doc, metadata.document)
    }

    @Test
    fun `content is stored unmodified`() {
        val content = """{"openapi":"3.0.3","info":{"title":"T","version":"1.0.0"}}"""
        val metadata = OpenApiExportMetadata(
            document = minimalDoc(),
            outputFormat = OpenApiOutputFormat.JSON,
            content = content,
        )
        // Equality check — content is a String (immutable), so equality is
        // the strongest guarantee we can give.
        assertEquals(content, metadata.content)
    }

    @Test
    fun `metadata implements ExportMetadata interface`() {
        // Compile-time + runtime check — ExportResult.Success carries an
        // ExportMetadata, so the channel layer must produce one.
        val metadata: ExportMetadata = OpenApiExportMetadata(
            document = minimalDoc(),
            outputFormat = OpenApiOutputFormat.JSON,
            content = "{}",
        )
        assertNotNull(metadata.formatDisplay())
    }

    @Test
    fun `data class copy preserves all fields`() {
        // Pin the data-class shape — handleResult reads `document` and
        // `content` directly. A refactor that drops either field would
        // break the file-write flow.
        val doc = minimalDoc()
        val metadata = OpenApiExportMetadata(
            document = doc,
            outputFormat = OpenApiOutputFormat.JSON,
            content = "{}",
        )
        val copy = metadata.copy(outputFormat = OpenApiOutputFormat.YAML)
        assertEquals(OpenApiOutputFormat.YAML, copy.outputFormat)
        assertSame(doc, copy.document)
        assertEquals("{}", copy.content)
    }

    private fun minimalDoc(): OpenApiDocument = OpenApiDocument(
        info = InfoObject(title = "T", version = "1.0.0"),
        paths = linkedMapOf(),
    )
}
