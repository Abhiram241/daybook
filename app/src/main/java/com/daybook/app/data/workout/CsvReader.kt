package com.daybook.app.data.workout

/**
 * A8 (§3.9.2) — a small hand-rolled RFC4180-ish reader. No third-party dependency: adding one
 * would break this project's "every third-party pin carries a written justification comment"
 * discipline for roughly eighty lines of code.
 *
 * Handles: quoted and unquoted fields on the same line; commas inside quotes; `""` as an escaped
 * quote; a completely empty field between two commas; `CRLF` and `LF`; a leading UTF-8 BOM
 * (Hevy's export has been seen to carry one — an un-stripped BOM silently breaks only the FIRST
 * header name); a trailing newline.
 *
 * Encoding is already solved upstream (`StorageUtils.readText` decodes UTF-8 explicitly) — this
 * reader must not re-decode, only strip a BOM character if one survived as text.
 *
 * Returns `List<Map<String, String>>` keyed by header name, so a column Hevy adds or reorders
 * later does not shift every field by one.
 */
object CsvReader {

    private const val BOM = '﻿'

    fun parse(text: String): List<Map<String, String>> {
        val rows = parseRows(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.removePrefix(BOM.toString()) }
        return rows.drop(1).map { row ->
            header.indices.associate { i -> header[i] to (row.getOrElse(i) { "" }) }
        }
    }

    /** Just the header row, for the V3/V4 validation gates — no need to parse the whole file. */
    fun parseHeader(text: String): List<String> {
        val rows = parseRows(text, limitRows = 1)
        return rows.firstOrNull()?.map { it.removePrefix(BOM.toString()) } ?: emptyList()
    }

    /** Pure RFC4180-ish tokenizer: text -> rows of raw string fields (header row included). */
    private fun parseRows(text: String, limitRows: Int = Int.MAX_VALUE): List<List<String>> {
        if (text.isEmpty()) return emptyList()
        val rows = ArrayList<List<String>>()
        var field = StringBuilder()
        var row = ArrayList<String>()
        var inQuotes = false
        var i = 0
        val n = text.length

        fun endField() {
            row.add(field.toString())
            field = StringBuilder()
        }
        fun endRow() {
            endField()
            rows.add(row)
            row = ArrayList()
        }

        while (i < n && rows.size < limitRows) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                        continue
                    }
                } else {
                    field.append(c)
                    i++
                    continue
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; i++ }
                    ',' -> { endField(); i++ }
                    '\r' -> {
                        // CRLF or a lone CR — either way, ends the row.
                        if (i + 1 < n && text[i + 1] == '\n') i++ else Unit
                        endRow()
                        i++
                    }
                    '\n' -> { endRow(); i++ }
                    else -> { field.append(c); i++ }
                }
            }
        }
        // A trailing newline leaves an empty pending row — don't emit it. A file with no
        // trailing newline still has a real last row to flush.
        if (rows.size < limitRows && (field.isNotEmpty() || row.isNotEmpty())) {
            endRow()
        }
        return rows
    }
}
