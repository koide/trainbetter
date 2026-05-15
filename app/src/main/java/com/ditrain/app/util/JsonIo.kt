package com.ditrain.app.util

import kotlinx.serialization.json.Json

/**
 * Shared Json instance used by every repository. Configured to:
 *  - tolerate unknown fields so future schema additions don't crash older builds reading newer files;
 *  - pretty-print so files are debuggable / hand-editable;
 *  - encode default values so manually-authored JSON doesn't need to specify them once and forget the next time.
 */
object JsonIo {
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        classDiscriminator = "type"
    }
}
