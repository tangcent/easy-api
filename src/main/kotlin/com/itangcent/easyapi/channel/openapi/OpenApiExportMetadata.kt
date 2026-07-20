package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.ExportMetadata

/**
 * Export metadata for the OpenAPI channel.
 *
 * Carried inside `ExportResult.Success` by `OpenApiChannel.export`. The
 * `document` and `content` are pre-computed in `export` so that
 * `handleResult` can write the file without re-serializing — this keeps
 * `handleResult` free of `OpenApiSerializer` / `OpenApiDocument` awareness
 * (it just sees a String).
 *
 * `formatDisplay()` is shown to the user via the export-result notification
 * — it returns a short label identifying which format was produced, so the
 * user can tell at a glance whether they got JSON or YAML.
 *
 * @property document the in-memory [OpenApiDocument] — preserved so future
 *   channel extensions (e.g., a "preview in editor" action) can re-serialize
 *   to a different format without re-running the formatter.
 * @property outputFormat the format that was produced (drives `formatDisplay`
 *   and the file-name extension in `handleResult`).
 * @property content the already-serialized document string (JSON or YAML).
 *   `handleResult` writes this verbatim to the output file.
 */
data class OpenApiExportMetadata(
    val document: OpenApiDocument,
    val outputFormat: OpenApiOutputFormat,
    val content: String,
) : ExportMetadata {
    override fun formatDisplay(): String? = when (outputFormat) {
        OpenApiOutputFormat.JSON -> "Format: JSON"
        OpenApiOutputFormat.YAML -> "Format: YAML"
        OpenApiOutputFormat.ALWAYS_ASK -> error("unreachable — ALWAYS_ASK is resolved to JSON/YAML at OpenApiChannel.export step 2 before this metadata is constructed")
    }
}
