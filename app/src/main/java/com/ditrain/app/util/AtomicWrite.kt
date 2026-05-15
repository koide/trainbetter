package com.ditrain.app.util

import java.io.File
import java.io.FileOutputStream

object AtomicWrite {

    /**
     * Writes [content] to [target] as atomically as the host filesystem allows:
     *  1. write to "${target}.tmp"
     *  2. fsync (flush + sync) so the tmp file is durable before rename
     *  3. renameTo onto target — on POSIX filesystems (Android internal storage)
     *     this atomically replaces target. If the FS refuses (some SD-card
     *     layouts), fall back to delete + rename, then to copy + delete.
     *
     * java.nio.file.Files.move with ATOMIC_MOVE would be cleaner but requires
     * API 26+; minSdk 23 forces this slightly-uglier ladder.
     *
     * Parent directories are created if missing.
     */
    fun writeText(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        if (tmp.renameTo(target)) return                     // happy path: atomic on POSIX
        if (target.exists() && !target.delete()) {
            tmp.delete()
            throw java.io.IOException("Failed to replace $target")
        }
        if (tmp.renameTo(target)) return
        tmp.copyTo(target, overwrite = true)
        tmp.delete()
    }
}
